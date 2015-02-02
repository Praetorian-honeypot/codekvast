package se.crisp.codekvast.agent.main;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import se.crisp.codekvast.agent.config.AgentConfig;
import se.crisp.codekvast.agent.config.CollectorConfig;
import se.crisp.codekvast.agent.config.SharedConfig;
import se.crisp.codekvast.agent.main.appversion.AppVersionStrategy;
import se.crisp.codekvast.agent.main.codebase.CodeBaseScanner;
import se.crisp.codekvast.agent.model.Jvm;
import se.crisp.codekvast.server.agent_api.AgentApi;
import se.crisp.codekvast.server.agent_api.AgentApiException;
import se.crisp.codekvast.server.agent_api.model.v1.JvmData;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.anyCollection;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AgentWorkerIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final CodeBaseScanner scanner = new CodeBaseScanner();
    private SharedConfig sharedConfig;
    private long jvmStartedAtMillis = System.currentTimeMillis() - 60_000L;
    private long now = System.currentTimeMillis();

    @Mock
    private AgentApi agentApi;

    private AgentWorker worker;

    @Before
    public void before() throws Exception {
        when(agentApi.getServerUri()).thenReturn(new URI("http://server"));

        List<AppVersionStrategy> appVersionStrategies = new ArrayList<>();
        sharedConfig = SharedConfig.builder().dataPath(temporaryFolder.getRoot()).build();
        AgentConfig agentConfig = createAgentConfig(sharedConfig);

        worker = new AgentWorker("codekvastVersion", "gitHash", agentApi, agentConfig, scanner, appVersionStrategies);
    }

    @Test
    public void testUploadSignatureData_uploadSameCodeBaseOnlyOnce() throws AgentApiException, IOException {
        // given
        thereIsCollectorDataFromJvm("fingerprint1", "codebase1", now - 4711L);

        // when
        worker.analyseCollectorData();
        worker.analyseCollectorData();

        // then
        verify(agentApi, times(1)).uploadSignatureData(any(JvmData.class), anyCollection());
    }

    @Test
    public void testUploadSignatureData_shouldRetryOnFailure() throws AgentApiException, IOException {
        // given
        thereIsCollectorDataFromJvm("fingerprint1", "codebase1", now - 4711L);

        doThrow(new AgentApiException("Failed to contact server")).doNothing()
                                                                  .when(agentApi).uploadSignatureData(any(JvmData.class), anyCollection());

        // when
        worker.analyseCollectorData();
        worker.analyseCollectorData();
        worker.analyseCollectorData();

        // then
        verify(agentApi, times(2)).uploadSignatureData(any(JvmData.class), anyCollection());
    }

    private void thereIsCollectorDataFromJvm(String jvmUuid, String codebase, long dumpedAtMillis) throws IOException {
        CollectorConfig cc = CollectorConfig.builder()
                                            .appName("appName")
                                            .appVersion("appVersion")
                                            .codeBase("src/test/resources/agentWorkerTest/" + codebase)
                                            .packagePrefixes("org, sample")
                                            .methodExecutionPointcut("methodExecutionPointcut")
                                            .sharedConfig(sharedConfig)
                                            .tags("tags")
                                            .build();

        Jvm jvm = Jvm.builder()
                     .collectorConfig(cc)
                     .computerId("computerId")
                     .dumpedAtMillis(dumpedAtMillis)
                     .hostName("hostName")
                     .jvmUuid(jvmUuid)
                     .startedAtMillis(jvmStartedAtMillis)
                     .build();

        jvm.saveTo(cc.getJvmFile());
    }

    private AgentConfig createAgentConfig(SharedConfig sharedConfig) {
        try {
            return AgentConfig.builder().sharedConfig(sharedConfig)
                              .apiAccessID("accessId")
                              .apiAccessSecret("secret")
                              .serverUploadIntervalSeconds(60)
                              .serverUri(new URI("http://localhost:8090"))
                              .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
