package se.crisp.codekvast.server.codekvast_server.dao.impl;

import com.google.common.eventbus.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import se.crisp.codekvast.server.agent_api.model.v1.InvocationData;
import se.crisp.codekvast.server.agent_api.model.v1.InvocationEntry;
import se.crisp.codekvast.server.agent_api.model.v1.JvmData;
import se.crisp.codekvast.server.codekvast_server.dao.AgentDAO;
import se.crisp.codekvast.server.codekvast_server.dao.CollectorTimestamp;
import se.crisp.codekvast.server.codekvast_server.event.internal.ApplicationCreatedEvent;
import se.crisp.codekvast.server.codekvast_server.event.internal.CollectorUptimeEvent;
import se.crisp.codekvast.server.codekvast_server.exception.UndefinedApplicationException;
import se.crisp.codekvast.server.codekvast_server.model.AppId;
import se.crisp.codekvast.server.codekvast_server.model.Application;

import javax.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * DAO for signature data.
 *
 * @author Olle Hallin
 */
@Repository
@Slf4j
public class AgentDAOImpl extends AbstractDAOImpl implements AgentDAO {

    @Inject
    public AgentDAOImpl(EventBus eventBus, JdbcTemplate jdbcTemplate) {
        super(eventBus, jdbcTemplate);
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

        Application app = new Application(AppId.builder().organisationId(organisationId).appId(appId).build(), appName);
        eventBus.post(new ApplicationCreatedEvent(app, appVersion, getUsernamesInOrganisation(organisationId)));
        log.info("Created {} {}", app, appVersion);
        return appId;
    }


    @Override
    public InvocationData storeInvocationData(AppId appId, InvocationData invocationData) {

        List<Object[]> args = new ArrayList<>();

        for (InvocationEntry entry : invocationData.getInvocations()) {
            args.add(new Object[]{
                    appId.getOrganisationId(),
                    appId.getAppId(),
                    appId.getJvmId(),
                    entry.getSignature(),
                    entry.getInvokedAtMillis(),
                    entry.getConfidence() == null ? null : entry.getConfidence().ordinal()
            });
        }

        int[] inserted = jdbcTemplate.batchUpdate(
                "INSERT INTO signatures(organisation_id, application_id, jvm_id, signature, invoked_at, confidence) " +
                        "VALUES(?, ?, ?, ?, ?, ?)", args);

        // Now check what really made it into the table...
        List<InvocationEntry> result = new ArrayList<>();
        int i = 0;
        for (InvocationEntry entry : invocationData.getInvocations()) {
            if (inserted[i] > 0) {
                result.add(entry);
            }
            i += 1;
        }
        return InvocationData.builder().jvmFingerprint(invocationData.getJvmFingerprint()).invocations(result).build();
    }

    @Override
    public void storeJvmData(long organisationId, long appId, JvmData data) {
        int updated =
                jdbcTemplate
                        .update("UPDATE jvm_runs SET dumped_at = ? WHERE application_id = ? AND jvm_fingerprint = ?",
                                data.getDumpedAtMillis(), appId, data.getJvmFingerprint());
        if (updated > 0) {
            log.debug("Updated dumped_at={} for JVM run {}", new Date(data.getDumpedAtMillis()), data.getJvmFingerprint());
            return;
        }

        updated = jdbcTemplate
                .update("INSERT INTO jvm_runs(organisation_id, application_id, application_version, computer_id, host_name, jvm_fingerprint, " +
                                "codekvast_version, " +
                                "codekvast_vcs_id, started_at, dumped_at)" +
                                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        organisationId, appId, data.getAppVersion(), data.getComputerId(), data.getHostName(), data.getJvmFingerprint(),
                        data.getCodekvastVersion(), data.getCodekvastVcsId(), data.getStartedAtMillis(),
                        data.getDumpedAtMillis());

        if (updated == 1) {
            log.debug("Stored new JVM run {}", data);
        } else {
            log.warn("Cannot store JVM run {}", data);
        }
    }

    @Override
    public CollectorUptimeEvent createCollectorUpTimeEvent(long organisationId) {
        Collection<String> usernames = getUsernamesInOrganisation(organisationId);

        CollectorTimestamp timestamp =
                jdbcTemplate.queryForObject("SELECT MIN(started_at), MAX(dumped_at) FROM jvm_runs WHERE organisation_id = ? ",
                                            new CollectorTimestampRowMapper(), organisationId);
        return new CollectorUptimeEvent(timestamp, usernames);
    }

    private static class CollectorTimestampRowMapper implements RowMapper<CollectorTimestamp> {
        @Override
        public CollectorTimestamp mapRow(ResultSet rs, int rowNum) throws SQLException {
            // SELECT MIN(started_at), MAX(dumped_at) FROM jvm_runs
            return CollectorTimestamp.builder()
                                     .startedAtMillis(rs.getLong(1))
                                     .dumpedAtMillis(rs.getLong(2))
                                     .build();
        }
    }
}
