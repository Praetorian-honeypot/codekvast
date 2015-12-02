package se.crisp.codekvast.server.codekvast_server.service.impl;

import com.google.common.eventbus.Subscribe;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import se.crisp.codekvast.server.daemon_api.model.v1.SignatureConfidence;
import se.crisp.codekvast.server.daemon_api.model.v1.SignatureData;
import se.crisp.codekvast.server.daemon_api.model.v1.SignatureEntry;
import se.crisp.codekvast.server.codekvast_server.exception.UndefinedUserException;
import se.crisp.codekvast.server.codekvast_server.model.event.display.WebSocketMessage;
import se.crisp.codekvast.server.codekvast_server.service.DaemonService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static se.crisp.codekvast.test.matchers.ApplicationStatisticsMatcher.hasApplicationStatistics;
import static se.crisp.codekvast.test.matchers.CollectorsMatcher.hasCollectors;

/**
 * @author olle.hallin@crisp.se
 */
@ContextConfiguration(classes = {DaemonServiceImpl.class, UserServiceImpl.class})
public class DaemonServiceIntegTest extends AbstractServiceIntegTest {
    @Inject
    private DaemonService daemonService;

    private WebSocketMessage lastWebSocketMessage;

    @Subscribe
    public void onWebSocketMessage(WebSocketMessage message) {
        events.add(message);
        lastWebSocketMessage = message;
    }

    @Test
    public void testStoreJvmData_fromValidDaemon() throws Exception {
        // given
        long collectionIntervalMillis = 3600_000L;
        long t0 = now - 10 * collectionIntervalMillis;
        long t1 = now - 9 * collectionIntervalMillis;
        long t2 = now - 8 * collectionIntervalMillis;
        long t3 = now - 7 * collectionIntervalMillis;
        long t4 = now - 6 * collectionIntervalMillis;
        long t5 = now - 5 * collectionIntervalMillis;
        long t6 = now - 4 * collectionIntervalMillis;

        String statsWhereClause = "application_statistics " +
                "WHERE application_id = ? " +
                "AND application_version = ? " +
                "AND first_started_at_millis = ? " +
                "AND max_started_at_millis = ? " +
                "AND last_reported_at_millis = ? ";

        // when an app starts for the first time
        daemonService.storeJvmData("daemon", createJvmData(t0, t1, "app1", "1.0", "jvm1", "hostName1"));

        // then
        assertThat(countRows(statsWhereClause, 1, "1.0", t0, t0, t1), is(1));

        assertThat(lastWebSocketMessage, hasApplicationStatistics(
                allOf(
                        hasProperty("numHostNames", is(1)),
                        hasProperty("firstDataReceivedAtMillis", is(t0)),
                        hasProperty("lastDataReceivedAtMillis", is(t1)),
                        hasProperty("upTimeSeconds", is((t1 - t0) / 1000))
                )
        ));

        // when it continues to execute in the same JVM
        daemonService.storeJvmData("daemon", createJvmData(t0, t2, "app1", "1.0", "jvm1", "hostName1"));

        // then
        assertThat(countRows(statsWhereClause, 1, "1.0", t0, t0, t1), is(0));
        assertThat(countRows(statsWhereClause, 1, "1.0", t0, t0, t2), is(1));

        assertThat(lastWebSocketMessage, hasApplicationStatistics(
                allOf(
                        hasProperty("numHostNames", is(1)),
                        hasProperty("firstDataReceivedAtMillis", is(t0)),
                        hasProperty("lastDataReceivedAtMillis", is(t2)),
                        hasProperty("upTimeSeconds", is((t2 - t0) / 1000))
                )
        ));

        // when it restarts in a new JVM in the same host
        daemonService.storeJvmData("daemon", createJvmData(t3, t4, "app1", "1.0", "jvm2", "hostName1"));

        // then
        assertThat(countRows(statsWhereClause, 1, "1.0", t0, t3, t4), is(1));

        assertThat(lastWebSocketMessage, hasApplicationStatistics(
                allOf(
                        hasProperty("numHostNames", is(1)),
                        hasProperty("firstDataReceivedAtMillis", is(t0)),
                        hasProperty("lastDataReceivedAtMillis", is(t4)),
                        hasProperty("upTimeSeconds", is((t2 - t0 + t4 - t3) / 1000))
                )
        ));

        // when a new instance starts in another host
        daemonService.storeJvmData("daemon", createJvmData(t5, t6, "app1", "1.0", "jvm3", "hostName2"));

        // then
        assertThat(countRows(statsWhereClause, 1, "1.0", t0, t5, t6), is(1));

        assertThat(lastWebSocketMessage, hasApplicationStatistics(
                allOf(
                        hasProperty("numHostNames", is(2)),
                        hasProperty("firstDataReceivedAtMillis", is(t0)),
                        hasProperty("lastDataReceivedAtMillis", is(t6)),
                        // average usage time per hostName
                        hasProperty("upTimeSeconds", is((t2 - t0 + t4 - t3 + t6 - t5) / 2 / 1000))
                )
        ));

        assertThat(lastWebSocketMessage, hasCollectors(
                allOf(
                        hasProperty("daemonHostname", is("hostName1")),
                        hasProperty("collectorStartedAtMillis", is(t3)),
                        hasProperty("dataReceivedAtMillis", is(t4))
                ),
                allOf(
                        hasProperty("daemonHostname", is("hostName2")),
                        hasProperty("collectorStartedAtMillis", is(t5)),
                        hasProperty("dataReceivedAtMillis", is(t6))
                )
        ));

        assertThat(countRows(
                "jvm_info WHERE jvm_uuid = ? AND started_at_millis = ? AND reported_at_millis = ? ",
                "jvm1", t0, t2), is(1));

        assertThat(countRows(
                "jvm_info WHERE jvm_uuid = ? AND started_at_millis = ? AND reported_at_millis = ? ",
                "jvm2", t3, t4), is(1));

        assertThat(events, hasSize(4));
    }

    @Test(expected = UndefinedUserException.class)
    public void testStoreJvmData_fromUnknownDaemon() throws Exception {
        daemonService.storeJvmData("foobar", createJvmData(now, now, "app2", "1.0", "jvm1", "daemonHostName"));
    }

    @Test
    public void testStoreInvocationData() throws Exception {
        long startedAtMillis = now - 3600_000L;

        daemonService.storeJvmData("daemon", createJvmData(startedAtMillis, now, "app1", "1.0", "jvm1.1", "daemonHostName"));
        daemonService.storeJvmData("daemon", createJvmData(startedAtMillis + 100L, now, "app1", "1.0", "jvm1.2", "daemonHostName"));
        daemonService.storeJvmData("daemon", createJvmData(startedAtMillis - 100L, now, "app2", "1.0", "jvm2.1", "daemonHostName"));
        daemonService.storeJvmData("daemon", createJvmData(startedAtMillis, now, "app2", "1.0", "jvm2.2", "daemonHostName"));

        assertThat(events, hasSize(4));

        List<SignatureEntry> signatures = new ArrayList<>();
        signatures.add(new SignatureEntry("sig1", 0L, 0L, SignatureConfidence.NOT_INVOKED));
        signatures.add(new SignatureEntry("sig2", 100L, 100L, SignatureConfidence.EXACT_MATCH));
        signatures.add(new SignatureEntry("sig1", 200L, 200L, SignatureConfidence.EXACT_MATCH));

        events.clear();
        daemonService.storeSignatureData(SignatureData.builder().jvmUuid("jvm1.1").signatures(signatures).build());
        daemonService.storeSignatureData(SignatureData.builder().jvmUuid("jvm1.2").signatures(signatures).build());
        daemonService.storeSignatureData(SignatureData.builder().jvmUuid("jvm2.1").signatures(signatures).build());
        daemonService.storeSignatureData(SignatureData.builder().jvmUuid("jvm2.2").signatures(signatures).build());

        assertThat(events, contains(instanceOf(WebSocketMessage.class),
                                    instanceOf(WebSocketMessage.class),
                                    instanceOf(WebSocketMessage.class),
                                    instanceOf(WebSocketMessage.class)));
    }

}