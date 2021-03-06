/*
 * Copyright (c) 2015-2018 Hallin Information Technology AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.codekvast.dashboard.dashboard.impl;

import io.codekvast.common.customer.CustomerData;
import io.codekvast.common.customer.CustomerService;
import io.codekvast.common.customer.PricePlan;
import io.codekvast.common.security.CustomerIdProvider;
import io.codekvast.dashboard.dashboard.DashboardService;
import io.codekvast.dashboard.dashboard.model.methods.*;
import io.codekvast.dashboard.dashboard.model.status.AgentDescriptor;
import io.codekvast.dashboard.dashboard.model.status.ApplicationDescriptor2;
import io.codekvast.dashboard.dashboard.model.status.GetStatusResponse;
import io.codekvast.dashboard.util.TimeService;
import io.codekvast.javaagent.model.v2.SignatureStatus2;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author olle.hallin@crisp.se
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class DashboardServiceImpl implements DashboardService {

    private static final Pattern SYNTHETIC_SIGNATURE_PATTERN;

    static {
        // TODO: Make this database driven
        // See io.codekvast.dashboard.dashboard.impl.DashboardServiceImplSyntheticSignatureTest
        SYNTHETIC_SIGNATURE_PATTERN = Pattern.compile(
            ".*(\\$\\$.*|\\$\\w+\\$.*|\\.[A-Z0-9_]+\\(.*\\)$|\\$[a-z]+\\(\\)$|\\.\\.anonfun\\..*|\\.\\.(Enhancer|FastClass)" +
                "BySpringCGLIB\\.\\..*)");
    }

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final CustomerIdProvider customerIdProvider;
    private final CustomerService customerService;
    private final TimeService timeService;

    @Override
    @Transactional(readOnly = true)
    public GetMethodsResponse1 getMethods1(@Valid GetMethodsRequest request) {
        long startedAt = timeService.currentTimeMillis();

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("customerId", customerIdProvider.getCustomerId());

        String whereClause = "";
        if (request.getApplications() != null && !request.getApplications().isEmpty()) {
            params.addValue("applicationIds", translateNamesToIds("applications", request.getApplications()));
            whereClause = "i.applicationId IN (:applicationIds) AND ";
        }
        if (request.getEnvironments() != null && !request.getEnvironments().isEmpty()) {
            params.addValue("environmentIds", translateNamesToIds("environments", request.getEnvironments()));
            whereClause += "i.environmentId IN (:environmentIds) AND ";
        }
        params.addValue("signature", request.getNormalizedSignature());
        whereClause += "m.signature LIKE :signature ";

        MethodDescriptorRowCallbackHandler rowCallbackHandler = new MethodDescriptorRowCallbackHandler(whereClause,
                                                                                                       request
                                                                                                           .isSuppressSyntheticMethods());


        namedParameterJdbcTemplate.query(rowCallbackHandler.getSelectStatement(), params, rowCallbackHandler);

        List<MethodDescriptor1> methods = rowCallbackHandler.getResult(request);


        long queryTimeMillis = timeService.currentTimeMillis() - startedAt;
        logger.debug("Processed {} in {} ms. {} result set rows processed.", request, queryTimeMillis, rowCallbackHandler.getRowCount());

        return GetMethodsResponse1.builder()
                                  .timestamp(startedAt)
                                  .request(request)
                                  .numMethods(methods.size())
                                  .methods(methods)
                                  .queryTimeMillis(queryTimeMillis)
                                  .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GetMethodsResponse2 getMethods2(@Valid GetMethodsRequest request) {
        long startedAt = timeService.currentTimeMillis();
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("customerId", customerIdProvider.getCustomerId());
        params.addValue("minCollectedDays", request.getMinCollectedDays());
        params.addValue("now", new Timestamp(timeService.currentTimeMillis()));
        params.addValue("onlyInvokedAfterMillis", request.getOnlyInvokedAfterMillis());
        params.addValue("onlyInvokedBeforeMillis", request.getOnlyInvokedBeforeMillis());

        String whereClause = "i.customerId = :customerId AND ";

        if (request.getApplications() != null && !request.getApplications().isEmpty()) {
            params.addValue("applicationIds", translateNamesToIds("applications", request.getApplications()));
            whereClause += "i.applicationId IN (:applicationIds) AND ";
        }
        if (request.getEnvironments() != null && !request.getEnvironments().isEmpty()) {
            params.addValue("environmentIds", translateNamesToIds("environments", request.getEnvironments()));
            whereClause += "i.environmentId IN (:environmentIds) AND ";
        }
        params.addValue("signature", request.getNormalizedSignature());
        whereClause += "m.signature LIKE :signature ";

        String sql = "SELECT\n" +
            "    m.id, m.signature, MAX(i.status) AS status, " +
            "    ((TO_SECONDS(:now) - TO_SECONDS(MIN(j.startedAt))) DIV 86400) AS collectedDays,\n" +
            "    MAX(i.invokedAtMillis) AS lastInvokedAtMillis\n" +
            "FROM invocations i, methods m, jvms j\n" +
            "WHERE i.methodId = m.id AND i.jvmId = j.id AND " + whereClause +
            "GROUP BY m.signature\n" +
            "HAVING collectedDays >= :minCollectedDays " +
            "   AND lastInvokedAtMillis BETWEEN :onlyInvokedAfterMillis AND :onlyInvokedBeforeMillis\n" +
            "ORDER BY lastInvokedAtMillis, m.signature ";

        List<MethodDescriptor2> methods = new ArrayList<>();

        namedParameterJdbcTemplate.query(sql, params, rs -> {
            if (methods.size() >= request.getMaxResults()) {
                logger.trace("Ignoring row {}, since max result already achieved", rs.getRow());
                return;
            }

            String signature = rs.getString("signature");

            if (request.isSuppressSyntheticMethods() && isSyntheticMethod(signature)) {
                logger.trace("Suppressing synthetic method {}", signature);
                return;
            }

            SignatureStatus2 status = SignatureStatus2.valueOf(rs.getString("status"));
            if (request.isSuppressUntrackedMethods() && !status.isTracked()) {
                logger.trace("Suppressing untracked method {} with status {}", signature, status);
                return;
            }

            methods.add(
                MethodDescriptor2.builder()
                                 .id(rs.getLong("id"))
                                 .signature(signature)
                                 .trackedPercent(status.isTracked() ? 100 : 0)
                                 .collectedDays(rs.getInt("collectedDays"))
                                 .lastInvokedAtMillis(rs.getLong("lastInvokedAtMillis"))
                                 .build());

        });


        long queryTimeMillis = timeService.currentTimeMillis() - startedAt;
        logger.debug("Processed {} in {} ms.", request, queryTimeMillis);

        return GetMethodsResponse2.builder()
                                  .timestamp(startedAt)
                                  .request(request)
                                  .numMethods(methods.size())
                                  .methods(methods)
                                  .queryTimeMillis(queryTimeMillis)
                                  .build();
    }

    private Collection<Long> translateNamesToIds(final String tableName, Collection<String> names) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("customerId", customerIdProvider.getCustomerId());
        params.addValue("names", names);

        List<Long> ids = namedParameterJdbcTemplate
            .queryForList("SELECT id FROM " + tableName + " WHERE customerId = :customerId AND name IN (:names)", params, Long.class);
        logger.debug("Mapped {} {} to {} for customer {}", tableName, names, ids, customerIdProvider.getCustomerId());
        return ids;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MethodDescriptor1> getMethodById(@NotNull Long methodId) {
        GetMethodsRequest request = GetMethodsRequest.defaults().toBuilder()
                                                     .maxResults(1)
                                                     .suppressUntrackedMethods(false)
                                                     .suppressSyntheticMethods(false)
                                                     .minCollectedDays(0)
                                                     .build();

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("customerId", customerIdProvider.getCustomerId());
        params.addValue("methodId", methodId);

        MethodDescriptorRowCallbackHandler rowCallbackHandler = new MethodDescriptorRowCallbackHandler("m.id = :methodId", false);

        namedParameterJdbcTemplate.query(rowCallbackHandler.getSelectStatement(), params, rowCallbackHandler);

        return rowCallbackHandler.getResult(request).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public GetStatusResponse getStatus() {
        long startedAt = timeService.currentTimeMillis();

        Long customerId = customerIdProvider.getCustomerId();
        CustomerData customerData = customerService.getCustomerDataByCustomerId(customerId);

        PricePlan pp = customerData.getPricePlan();
        List<ApplicationDescriptor2> applications = getApplications(customerId);
        List<AgentDescriptor> agents = getAgents(customerId, pp.getPublishIntervalSeconds());

        Instant now = timeService.now();
        Instant collectionStartedAt = customerData.getCollectionStartedAt();
        Instant trialPeriodEndsAt = customerData.getTrialPeriodEndsAt();
        Duration trialPeriodDuration =
            collectionStartedAt == null || trialPeriodEndsAt == null ? null : Duration.between(collectionStartedAt, trialPeriodEndsAt);
        Duration trialPeriodProgress = trialPeriodDuration == null ? null : Duration.between(collectionStartedAt, now);

        Integer trialPeriodPercent = trialPeriodProgress == null ? null :
            Math.min(100, Math.toIntExact(trialPeriodProgress.toMillis() * 100L / trialPeriodDuration.toMillis()));

        long dayInMillis = 24 * 60 * 60 * 1000L;
        Integer collectedDays =
            collectionStartedAt == null ? null : Math.toIntExact(Duration.between(collectionStartedAt, now).toMillis() / dayInMillis);

        return GetStatusResponse.builder()
                                // query stuff
                                .timestamp(startedAt)
                                .queryTimeMillis(timeService.currentTimeMillis() - startedAt)

                                // price plan stuff
                                .pricePlan(pp.getName())
                                .collectionResolutionSeconds(pp.getPublishIntervalSeconds())
                                .maxNumberOfAgents(pp.getMaxNumberOfAgents())
                                .maxNumberOfMethods(pp.getMaxMethods())

                                // actual values
                                .collectedSinceMillis(collectionStartedAt == null ? null : collectionStartedAt.toEpochMilli())
                                .trialPeriodEndsAtMillis(trialPeriodEndsAt == null ? null : trialPeriodEndsAt.toEpochMilli())
                                .trialPeriodExpired(customerData.isTrialPeriodExpired(now))
                                .trialPeriodPercent(trialPeriodPercent)
                                .collectedDays(collectedDays)
                                .numMethods(customerService.countMethods(customerId))
                                .numAgents(agents.size())
                                .numLiveAgents((int) agents.stream().filter(AgentDescriptor::isAgentAlive).count())
                                .numLiveEnabledAgents((int) agents.stream().filter(AgentDescriptor::isAgentLiveAndEnabled).count())

                                // details
                                .applications(applications)
                                .agents(agents)
                                .build();
    }

    private List<ApplicationDescriptor2> getApplications(Long customerId) {
        List<ApplicationDescriptor2> result = new ArrayList<>();

        jdbcTemplate.query(
            "SELECT\n" +
                "  a.name AS appName, e.name AS envName, MIN(j.startedAt) AS collectedSince, MAX(j.publishedAt) AS collectedTo\n" +
                "FROM jvms j INNER JOIN applications a ON j.applicationId = a.id\n" +
                "  INNER JOIN environments e ON j.environmentId = e.id\n" +
                "WHERE j.customerId = ?\n" +
                "GROUP BY appName, envName\n" +
                "ORDER BY appName, envName\n",

            rs -> {
                result.add(
                    ApplicationDescriptor2.builder()
                                          .appName(rs.getString("appName"))
                                          .environment(rs.getString("envName"))
                                          .collectedSinceMillis(rs.getTimestamp("collectedSince").getTime())
                                          .collectedToMillis(rs.getTimestamp("collectedTo").getTime())
                                          .build().computeFields());
            }, customerId);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public @NotNull GetMethodsFormData getMethodsFormData() {
        return GetMethodsFormData
            .builder()
            .applications(
                jdbcTemplate
                    .queryForList("SELECT name FROM applications WHERE customerId = ? ", String.class, customerIdProvider.getCustomerId()))
            .environments(
                jdbcTemplate.queryForList("SELECT name FROM environments WHERE customerId = ? ", String.class,
                                          customerIdProvider.getCustomerId()))
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAgent(Long agentId, Long jvmId) {
        Long customerId = customerIdProvider.getCustomerId();

        logger.debug("Deleting agent {}:{}:{}", customerId, agentId, jvmId);
        Instant startedAt = Instant.now();

        int deletedInvocations = jdbcTemplate.update("DELETE FROM invocations WHERE customerId = ? AND jvmId = ?",
                                                     customerId, jvmId);

        int deletedJvms = jdbcTemplate.update("DELETE FROM jvms WHERE customerId = ? AND id = ?", customerId, jvmId);

        int deletedAgentState = jdbcTemplate.update("DELETE FROM agent_state WHERE customerId = ? AND id = ?", customerId, agentId);

        if (deletedInvocations + deletedJvms + deletedAgentState == 0) {
            throw new IllegalArgumentException(
                String.format("No such agent: agentId=%d, jvmId=%d for customerId=%d", agentId, jvmId, customerId));
        }

        logger.info("Deleted {} invocations rows, {} jvms rows and {} agent_state rows for agent {}:{}:{} in {}", deletedInvocations,
                    deletedJvms,
                    deletedAgentState, customerId, agentId, jvmId, Duration.between(startedAt, Instant.now()));

        // child-less methods, environments and applications are deleted by the WeedingService.
    }

    private List<AgentDescriptor> getAgents(Long customerId, int publishIntervalSeconds) {
        List<AgentDescriptor> result = new ArrayList<>();

        jdbcTemplate.query(
            "SELECT agent_state.id AS agentId, agent_state.enabled, agent_state.lastPolledAt, agent_state.nextPollExpectedAt, " +
                "jvms.id AS jvmId, jvms.startedAt, jvms.publishedAt, jvms.methodVisibility, jvms.packages, jvms.excludePackages, " +
                "jvms.hostname, jvms.agentVersion, jvms.tags, jvms.applicationVersion AS appVersion, " +
                "applications.name AS appName, environments.name AS envName " +
                "FROM agent_state, jvms, applications, environments " +
                "WHERE jvms.customerId = ? " +
                "AND jvms.uuid = agent_state.jvmUuid " +
                "AND jvms.applicationId = applications.id " +
                "AND jvms.environmentId = environments.id " +
                "ORDER BY jvms.id ",

            rs -> {
                Timestamp lastPolledAt = rs.getTimestamp("lastPolledAt");
                Timestamp nextPollExpectedAt = rs.getTimestamp("nextPollExpectedAt");
                Timestamp publishedAt = rs.getTimestamp("publishedAt");
                boolean isAlive =
                    nextPollExpectedAt != null && nextPollExpectedAt.after(Timestamp.from(timeService.now().minusSeconds(60)));
                Instant nextPublicationExpectedAt = lastPolledAt.toInstant().plusSeconds(publishIntervalSeconds);

                result.add(
                    AgentDescriptor.builder()
                                   .agentId(rs.getLong("agentId"))
                                   .agentAlive(isAlive)
                                   .agentLiveAndEnabled(isAlive && rs.getBoolean("enabled"))
                                   .agentVersion(rs.getString("agentVersion"))
                                   .appName(rs.getString("appName"))
                                   .appVersion(rs.getString("appVersion"))
                                   .environment(rs.getString("envName"))
                                   .excludePackages(rs.getString("excludePackages"))
                                   .jvmId(rs.getLong("jvmId"))
                                   .hostname(rs.getString("hostname"))
                                   .methodVisibility(rs.getString("methodVisibility"))
                                   .nextPublicationExpectedAtMillis(nextPublicationExpectedAt.toEpochMilli())
                                   .nextPollExpectedAtMillis(nextPollExpectedAt.getTime())
                                   .packages(rs.getString("packages"))
                                   .pollReceivedAtMillis(lastPolledAt.getTime())
                                   .publishedAtMillis(publishedAt.getTime())
                                   .startedAtMillis(rs.getTimestamp("startedAt").getTime())
                                   .tags(rs.getString("tags"))
                                   .build());
            }, customerId);

        return result;
    }

    private class MethodDescriptorRowCallbackHandler implements RowCallbackHandler {
        private final String whereClause;
        private final boolean suppressSyntheticMethods;

        private final List<MethodDescriptor1> result = new ArrayList<>();

        private QueryState queryState;

        @Getter
        private int rowCount;

        private MethodDescriptorRowCallbackHandler(String whereClause, boolean suppressSyntheticMethods) {
            this.whereClause = whereClause;
            this.suppressSyntheticMethods = suppressSyntheticMethods;
            queryState = new QueryState(-1L);
        }

        String getSelectStatement() {

            // This is a simpler to understand approach than trying to do everything in the database.
            // Let the database do the joining and selection, and the Java layer do the data reduction. The query will return several rows
            // for each method that matches the WHERE clause, and the RowCallbackHandler reduces them to only one MethodDescriptor1 per
            // method ID.
            // This is probably doable in pure SQL too, provided you are a black-belt SQL ninja. Unfortunately I'm not that strong at SQL.

            return String.format("SELECT i.methodId, a.name AS appName, j.applicationVersion AS appVersion,\n" +
                                     "  e.name AS envName, i.invokedAtMillis, i.status, j.startedAt, j.publishedAt, j.hostname, j.tags,\n" +
                                     "  m.visibility, m.signature, m.declaringType, m.methodName, m.bridge, m.synthetic, m.modifiers, m" +
                                     ".packageName\n" +
                                     "  FROM invocations i\n" +
                                     "  INNER JOIN applications a ON a.id = i.applicationId \n" +
                                     "  INNER JOIN environments e ON e.id = i.environmentId \n" +
                                     "  INNER JOIN methods m ON m.id = i.methodId \n" +
                                     "  INNER JOIN jvms j ON j.id = i.jvmId \n" +
                                     "  WHERE i.customerId = :customerId AND %s \n" +
                                     "  ORDER BY i.methodId ASC", whereClause);
        }

        @Override
        public void processRow(ResultSet rs) throws SQLException {
            this.rowCount += 1;

            String signature = rs.getString("signature");
            boolean bridge = rs.getBoolean("bridge");
            boolean synthetic = rs.getBoolean("synthetic");

            // Throw away unwanted synthetic signatures as early as possible
            if (suppressSyntheticMethods && (bridge || synthetic || isSyntheticMethod(signature))) {
                logger.trace("Throwing away synthetic method: {}", signature);
                return;
            }

            long id = rs.getLong("methodId");
            if (!queryState.isSameMethod(id)) {
                // The query is sorted on methodId
                logger.trace("Found method {}:{}", id, signature);
                queryState.addTo(result);
                queryState = new QueryState(id);
            }

            queryState.countRow();
            long startedAt = rs.getTimestamp("startedAt").getTime();
            long publishedAt = rs.getTimestamp("publishedAt").getTime();
            long invokedAtMillis = rs.getLong("invokedAtMillis");

            MethodDescriptor1.MethodDescriptor1Builder builder = queryState.getBuilder();
            String appName = rs.getString("appName");
            String appVersion = rs.getString("appVersion");

            queryState.saveApplication(ApplicationDescriptor
                                           .builder()
                                           .name(appName)
                                           .version(appVersion)
                                           .startedAtMillis(startedAt)
                                           .publishedAtMillis(publishedAt)
                                           .invokedAtMillis(invokedAtMillis)
                                           .status(SignatureStatus2.valueOf(rs.getString("status")))
                                           .build());

            queryState.saveEnvironment(EnvironmentDescriptor.builder()
                                                            .name(rs.getString("envName"))
                                                            .hostname(rs.getString("hostname"))
                                                            .tags(splitOnCommaOrSemicolon(rs.getString("tags")))
                                                            .collectedSinceMillis(startedAt)
                                                            .collectedToMillis(publishedAt)
                                                            .invokedAtMillis(invokedAtMillis)
                                                            .build().computeFields());

            builder.declaringType(rs.getString("declaringType"))
                   .modifiers(rs.getString("modifiers"))
                   .packageName(rs.getString("packageName"))
                   .signature(signature)
                   .visibility(rs.getString("visibility"))
                   .bridge(bridge)
                   .synthetic(synthetic);
        }

        private Set<String> splitOnCommaOrSemicolon(String tags) {
            return new HashSet<>(Arrays.asList(tags.split("\\s*[,;]\\s")));
        }

        private List<MethodDescriptor1> getResult(GetMethodsRequest request) {
            // Include the last method
            queryState.addTo(result);

            // Get rid of unwanted result
            for (Iterator<MethodDescriptor1> iterator = result.iterator(); iterator.hasNext(); ) {
                MethodDescriptor1 md = iterator.next();

                boolean keep = true;

                if (request.isSuppressUntrackedMethods() && md.getStatuses().stream().anyMatch(s -> !s.isTracked())) {
                    logger.trace("Throwing away untracked method: {}", md);
                    keep = false;
                }

                if (keep && md.getCollectedDays() < request.getMinCollectedDays()) {
                    logger.trace("Throwing away method collected too few days: {}", md);
                    keep = false;
                }

                if (keep && request.getOnlyInvokedAfterMillis() > md.getLastInvokedAtMillis()) {
                    logger.trace("Throwing away too old method: {}", md);
                    keep = false;
                }
                if (keep && request.getOnlyInvokedBeforeMillis() < md.getLastInvokedAtMillis()) {
                    logger.trace("Throwing away too new method: {}", md);
                    keep = false;
                }
                if (!keep) {
                    iterator.remove();
                } else {
                    logger.trace("Keeping {}", md);
                }
            }

            logger.debug("Result size before limiting size: {}", result.size());

            // Sort with respect to lastInvokedAt ASC (so that we keep the oldest invocations)
            result.sort(Comparator.comparing(MethodDescriptor1::getLastInvokedAtMillis));

            // Limit the result
            return result.stream().limit(request.getMaxResults()).collect(Collectors.toList());
        }

    }

    static boolean isSyntheticMethod(String signature) {
        return SYNTHETIC_SIGNATURE_PATTERN.matcher(signature).matches();
    }

    @RequiredArgsConstructor
    private class QueryState {
        private final long methodId;

        private final Map<ApplicationId, ApplicationDescriptor> applications = new HashMap<>();
        private final Map<String, EnvironmentDescriptor> environments = new HashMap<>();

        private MethodDescriptor1.MethodDescriptor1Builder builder;
        private int rows;

        MethodDescriptor1.MethodDescriptor1Builder getBuilder() {
            if (builder == null) {
                builder = MethodDescriptor1.builder().id(methodId);
            }
            return builder;
        }

        boolean isSameMethod(long id) {
            return id == this.methodId;
        }

        void saveApplication(ApplicationDescriptor applicationDescriptor) {
            ApplicationId appId = ApplicationId.of(applicationDescriptor);
            applications.put(appId, applicationDescriptor.mergeWith(applications.get(appId)));

        }

        void saveEnvironment(EnvironmentDescriptor environmentDescriptor) {
            String name = environmentDescriptor.getName();
            environments.put(name, environmentDescriptor.mergeWith(environments.get(name)));
        }

        void addTo(List<MethodDescriptor1> result) {
            if (builder != null) {
                logger.trace("Adding method {} to result (compiled from {} result set rows)", methodId, rows);
                builder.occursInApplications(new TreeSet<>(applications.values()));
                builder.collectedInEnvironments(new TreeSet<>(environments.values()));
                result.add(builder.build().computeFields());
            }
        }

        void countRow() {
            rows += 1;
        }
    }

    /**
     * @author olle.hallin@crisp.se
     */
    @Value
    static class ApplicationId implements Comparable<ApplicationId> {
        private final String name;
        private final String version;

        @Override
        public int compareTo(ApplicationId that) {
            return this.toString().compareTo(that.toString());
        }

        static ApplicationId of(ApplicationDescriptor applicationDescriptor) {
            return new ApplicationId(applicationDescriptor.getName(), applicationDescriptor.getVersion());
        }
    }
}
