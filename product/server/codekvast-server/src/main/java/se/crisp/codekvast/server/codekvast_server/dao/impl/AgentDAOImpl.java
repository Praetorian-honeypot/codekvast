package se.crisp.codekvast.server.codekvast_server.dao.impl;

import com.google.common.eventbus.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import se.crisp.codekvast.server.agent_api.model.v1.JvmData;
import se.crisp.codekvast.server.agent_api.model.v1.SignatureData;
import se.crisp.codekvast.server.agent_api.model.v1.SignatureEntry;
import se.crisp.codekvast.server.codekvast_server.config.CodekvastSettings;
import se.crisp.codekvast.server.codekvast_server.dao.AgentDAO;
import se.crisp.codekvast.server.codekvast_server.event.internal.CollectorDataEvent;
import se.crisp.codekvast.server.codekvast_server.exception.UndefinedApplicationException;
import se.crisp.codekvast.server.codekvast_server.model.AppId;

import javax.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * DAO for signature data.
 *
 * @author olle.hallin@crisp.se
 */
@Repository
@Slf4j
public class AgentDAOImpl extends AbstractDAOImpl implements AgentDAO {

    private final CodekvastSettings codekvastSettings;

    @Inject
    public AgentDAOImpl(EventBus eventBus, JdbcTemplate jdbcTemplate, CodekvastSettings codekvastSettings) {
        super(eventBus, jdbcTemplate);
        this.codekvastSettings = codekvastSettings;
    }

    @Override
    @Cacheable("agent")
    public long getAppId(long organisationId, String appName, String appVersion) throws UndefinedApplicationException {
        log.debug("Looking up app id for {}:{}", organisationId, appName);
        return doGetOrCreateApp(organisationId, appName, appVersion);
    }

    private Long doGetOrCreateApp(long organisationId, String appName, String appVersion)
            throws UndefinedApplicationException {
        try {
            return jdbcTemplate.queryForObject("SELECT id FROM applications " +
                                                       "WHERE organisation_id = ? AND name = ? ",
                                               Long.class, organisationId, appName);
        } catch (EmptyResultDataAccessException ignored) {
        }

        long appId = doInsertRow("INSERT INTO applications(organisation_id, name) VALUES(?, ?)", organisationId, appName);
        doInsertRow("INSERT INTO application_settings(application_id, truly_dead_after_hours) VALUES(?, ?)", appId,
                    codekvastSettings.getTrulyDeadAfterHours());

        log.info("Created application {}: '{} {}'", appId, appName, appVersion);
        return appId;
    }

    @Override
    @Transactional(readOnly = true)
    public AppId getAppIdByJvmUuid(String jvmUuid) {
        log.debug("Looking up AppId for JVM {}...", jvmUuid);
        try {
            AppId result = jdbcTemplate
                    .queryForObject("SELECT id, organisation_id, application_id FROM jvm_info WHERE jvm_uuid = ?",
                                    new AppIdRowMapper(),
                                    jvmUuid);
            log.debug("Result = {}", result);
            return result;
        } catch (EmptyResultDataAccessException e) {
            log.info("No AppId found for JVM {}, probably an agent that uploaded stale data", jvmUuid);
            return null;
        }
    }

    private static class AppIdRowMapper implements RowMapper<AppId> {
        @Override
        public AppId mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AppId.builder().jvmId(rs.getLong(1)).organisationId(rs.getLong(2)).appId(rs.getLong(3)).build();
        }
    }


    @Override
    public SignatureData storeInvocationData(AppId appId, SignatureData signatureData) {

        List<Object[]> args = new ArrayList<>();

        for (SignatureEntry entry : signatureData.getSignatures()) {
            args.add(new Object[]{
                    appId.getOrganisationId(),
                    entry.getSignature(),
                    entry.getInvokedAtMillis(),
                    entry.getMillisSinceJvmStart(),
                    appId.getAppId(),
                    appId.getJvmId(),
                    entry.getConfidence() == null ? null : entry.getConfidence().ordinal()
            });
        }

        int[] inserted = jdbcTemplate.batchUpdate(
                "INSERT INTO signatures(organisation_id, signature, invoked_at_millis, millis_since_jvm_start, application_id, jvm_id, " +
                        "confidence) " +
                        "VALUES(?, ?, ?, ?, ?, ?, ?)", args);

        // Now check what really made it into the table...
        List<SignatureEntry> result = new ArrayList<>();
        int i = 0;
        for (SignatureEntry entry : signatureData.getSignatures()) {
            if (inserted[i] > 0) {
                result.add(entry);
            }
            i += 1;
        }
        return SignatureData.builder().jvmUuid(signatureData.getJvmUuid()).signatures(result).build();
    }

    @Override
    public void storeJvmData(long organisationId, long appId, JvmData data) {
        int updated =
                jdbcTemplate
                        .update("UPDATE jvm_info SET dumped_at_millis= ? WHERE application_id = ? AND jvm_uuid = ?",
                                data.getDumpedAtMillis(), appId, data.getJvmUuid());
        if (updated > 0) {
            log.debug("Updated JVM info for {} {}", data.getAppName(), data.getAppVersion());
            return;
        }

        updated = jdbcTemplate
                .update("INSERT INTO jvm_info(organisation_id, application_id, application_version, jvm_uuid, " +
                                "collector_resolution_seconds, method_execution_pointcut, " +
                                "collector_computer_id, collector_host_name, agent_computer_id, agent_host_name, " +
                                "agent_upload_interval_seconds, " +
                                "codekvast_version, codekvast_vcs_id, started_at_millis, dumped_at_millis)" +
                                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        organisationId, appId, data.getAppVersion(), data.getJvmUuid(),
                        data.getCollectorResolutionSeconds(), data.getMethodExecutionPointcut(),
                        data.getCollectorComputerId(), data.getCollectorHostName(),
                        data.getAgentComputerId(), data.getAgentHostName(), data.getAgentUploadIntervalSeconds(),
                        data.getCodekvastVersion(), data.getCodekvastVcsId(), data.getStartedAtMillis(), data.getDumpedAtMillis());

        if (updated == 1) {
            log.debug("Stored JVM info for {} {}", data.getAppName(), data.getAppVersion());
        } else {
            log.warn("Cannot store JVM info {}", data);
        }
    }

    @Override
    public CollectorDataEvent createCollectorDataEvent(long organisationId) {
        Collection<String> usernames = getInteractiveUsernamesInOrganisation(organisationId);

        Collection<CollectorDataEvent.CollectorEntry> collectors =
                jdbcTemplate.query("SELECT " +
                                           "a.name, " +
                                           "ase.truly_dead_after_hours, " +
                                           "jvm.application_version, MIN(jvm.started_at_millis), MAX(jvm.dumped_at_millis) " +
                                           "FROM applications a, application_settings ase, jvm_info jvm " +
                                           "WHERE a.id = ase.application_id " +
                                           "AND a.id = jvm.application_id " +
                                           "AND a.organisation_id = ? " +
                                           "GROUP BY a.name, ase.truly_dead_after_hours, jvm.application_version ",
                                   new CollectorEntryRowMapper(), organisationId);
        return new CollectorDataEvent(collectors, usernames);
    }

    private static class CollectorEntryRowMapper implements RowMapper<CollectorDataEvent.CollectorEntry> {
        @Override
        public CollectorDataEvent.CollectorEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            // name, version, started_at_millis, dumped_at_millis
            return CollectorDataEvent.CollectorEntry.builder()
                                                    .name(rs.getString(1))
                                                    .signatureDeadAfterHours(rs.getInt(2))
                                                    .version(rs.getString(3))
                                                    .startedAtMillis(rs.getLong(4))
                                                    .dumpedAtMillis(rs.getLong(5))
                                                    .build();
        }
    }
}
