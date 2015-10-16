package se.crisp.codekvast.daemon.impl.http_post;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import se.crisp.codekvast.daemon.appversion.AppVersionResolver;
import se.crisp.codekvast.daemon.beans.DaemonConfig;
import se.crisp.codekvast.daemon.beans.JvmState;
import se.crisp.codekvast.daemon.codebase.CodeBase;
import se.crisp.codekvast.daemon.codebase.CodeBaseScanner;
import se.crisp.codekvast.daemon.impl.AbstractDataProcessorImpl;
import se.crisp.codekvast.daemon.util.LogUtil;
import se.crisp.codekvast.server.daemon_api.DaemonApi;
import se.crisp.codekvast.server.daemon_api.DaemonApiException;
import se.crisp.codekvast.server.daemon_api.model.v1.JvmData;
import se.crisp.codekvast.server.daemon_api.model.v1.SignatureConfidence;
import se.crisp.codekvast.shared.model.Invocation;
import se.crisp.codekvast.shared.model.Jvm;

import javax.inject.Inject;

/**
 * An implementation of DataProcessor that uploads all data to a remote server using the HTTP POST API that is embedded in {@link
 * DaemonApi}.
 */
@Component
@Profile("httpPost")
@Slf4j
public class HttpPostDataProcessorImpl extends AbstractDataProcessorImpl {

    private final DaemonApi daemonApi;
    private final InvocationsCollector invocationsCollector;

    @Inject
    public HttpPostDataProcessorImpl(DaemonConfig config,
                                     AppVersionResolver appVersionResolver,
                                     CodeBaseScanner codeBaseScanner,
                                     DaemonApi daemonApi,
                                     InvocationsCollector invocationsCollector) {
        super(config, appVersionResolver, codeBaseScanner);
        this.daemonApi = daemonApi;
        this.invocationsCollector = invocationsCollector;
        log.info("{} created", getClass().getSimpleName());
    }

    @Override
    protected void doProcessJvmData(JvmState jvmState) {
        try {
            daemonApi.uploadJvmData(createUploadJvmData(jvmState));
            jvmState.setJvmDataProcessedAt(jvmState.getJvm().getDumpedAtMillis());
        } catch (DaemonApiException e) {
            LogUtil.logException(log, "Cannot upload JVM data to " + daemonApi.getServerUri(), e);
        }
    }

    @Override
    protected void doProcessCodebase(long now, JvmState jvmState, CodeBase codeBase) {
        try {
            daemonApi.uploadSignatureData(createUploadJvmData(jvmState), codeBase.getSignatures());
            jvmState.setCodebaseProcessedAt(now);
        } catch (DaemonApiException e) {
            LogUtil.logException(log, "Cannot upload signature data to " + daemonApi.getServerUri(), e);
        }
    }

    @Override
    protected void doStoreNormalizedSignature(JvmState jvmState, Invocation invocation, String normalizedSignature,
                                              SignatureConfidence confidence) {
        invocationsCollector.put(jvmState.getJvm().getJvmUuid(),
                                 jvmState.getJvm().getStartedAtMillis(),
                                 normalizedSignature,
                                 invocation.getInvokedAtMillis(),
                                 confidence);
    }

    @Override
    protected void doProcessUnprocessedSignatures(JvmState jvmState) {
        try {
            daemonApi.uploadInvocationData(createUploadJvmData(jvmState),
                                           invocationsCollector.getNotUploadedInvocations(jvmState.getJvm().getJvmUuid()));
            invocationsCollector.clearNotUploadedSignatures(jvmState.getJvm().getJvmUuid());
        } catch (DaemonApiException e) {
            LogUtil.logException(log, "Cannot upload invocation data to " + daemonApi.getServerUri(), e);
        }
    }


    private JvmData createUploadJvmData(JvmState jvmState) {
        Jvm jvm = jvmState.getJvm();

        return JvmData.builder()
                      .daemonComputerId(daemonComputerId)
                      .daemonHostName(daemonHostName)
                      .daemonTimeMillis(System.currentTimeMillis())
                      .daemonUploadIntervalSeconds(config.getServerUploadIntervalSeconds())
                      .daemonVcsId(config.getDaemonVcsId())
                      .daemonVersion(config.getDaemonVersion())
                      .appName(jvm.getCollectorConfig().getAppName())
                      .appVersion(jvmState.getAppVersion())
                      .collectorComputerId(jvm.getComputerId())
                      .collectorHostName(jvm.getHostName())
                      .collectorResolutionSeconds(jvm.getCollectorConfig().getCollectorResolutionSeconds())
                      .collectorVcsId(jvm.getCollectorVcsId())
                      .collectorVersion(jvm.getCollectorVersion())
                      .dumpedAtMillis(jvm.getDumpedAtMillis())
                      .jvmUuid(jvm.getJvmUuid())
                      .methodVisibility(jvm.getCollectorConfig().getMethodVisibility().toString())
                      .startedAtMillis(jvm.getStartedAtMillis())
                      .tags(jvm.getCollectorConfig().getTags())
                      .build();
    }
}
