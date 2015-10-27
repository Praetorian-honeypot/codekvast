package se.crisp.codekvast.daemon.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import se.crisp.codekvast.daemon.DataProcessor;
import se.crisp.codekvast.daemon.appversion.AppVersionResolver;
import se.crisp.codekvast.daemon.beans.DaemonConfig;
import se.crisp.codekvast.daemon.beans.JvmState;
import se.crisp.codekvast.daemon.codebase.CodeBase;
import se.crisp.codekvast.daemon.codebase.CodeBaseScanner;
import se.crisp.codekvast.daemon.util.LogUtil;
import se.crisp.codekvast.server.daemon_api.model.v1.JvmData;
import se.crisp.codekvast.server.daemon_api.model.v1.SignatureConfidence;
import se.crisp.codekvast.shared.model.Invocation;
import se.crisp.codekvast.shared.model.Jvm;
import se.crisp.codekvast.shared.util.ComputerID;
import se.crisp.codekvast.shared.util.FileUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Common behaviour for all data processors.
 *
 * @author olle.hallin@crisp.se
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractDataProcessorImpl implements DataProcessor {
    private final DaemonConfig daemonConfig;
    private final AppVersionResolver appVersionResolver;
    private final CodeBaseScanner codeBaseScanner;

    private final String daemonComputerId = ComputerID.compute().toString();
    private final String daemonHostName = getHostName();

    @Override
    @Transactional
    public void processData(long now, JvmState jvmState, CodeBase codeBase) throws DataProcessingException {
        appVersionResolver.resolveAppVersion(jvmState);
        processJvmData(jvmState);
        processCodeBase(jvmState, codeBase);
        processInvocationsData(jvmState);
    }

    private void processJvmData(JvmState jvmState) throws DataProcessingException {
        if (jvmState.getJvmDataProcessedAt() < jvmState.getJvm().getDumpedAtMillis()) {
            try {
                doProcessJvmData(jvmState);
                jvmState.setJvmDataProcessedAt(jvmState.getJvm().getDumpedAtMillis());
            } catch (Exception e) {
                LogUtil.logException(log, "Cannot process JVM data", e);
            }
        }
    }

    private void processCodeBase(JvmState jvmState, CodeBase codeBase) {
        if (jvmState.getCodebaseProcessedAt() == 0 || !codeBase.equals(jvmState.getCodeBase())) {
            if (jvmState.getCodebaseProcessedAt() == 0) {
                log.debug("Codebase has not yet been processed");
            } else {
                appVersionResolver.resolveAppVersion(jvmState);
                log.info("Codebase has changed, it will now be re-scanned and processed");
            }

            codeBaseScanner.scanSignatures(codeBase);
            jvmState.setCodeBase(codeBase);

            try {
                doProcessCodebase(jvmState, codeBase);
                jvmState.setCodebaseProcessedAt(jvmState.getJvm().getDumpedAtMillis());
            } catch (Exception e) {
                LogUtil.logException(log, "Cannot process code base", e);
                jvmState.setCodebaseProcessedAt(0);
            }
        }
    }

    private void processInvocationsData(JvmState jvmState) {
        List<Invocation> invocations = FileUtils.consumeAllInvocationDataFiles(jvmState.getInvocationsFile());
        if (jvmState.getCodeBase() != null && !invocations.isEmpty()) {
            normalizeAndProcessInvocations(jvmState, invocations);
            doProcessUnprocessedSignatures(jvmState);
        }
    }

    protected abstract void doProcessJvmData(JvmState jvmState) throws DataProcessingException;

    protected abstract void doProcessCodebase(JvmState jvmState, CodeBase codeBase);

    protected abstract void doProcessUnprocessedSignatures(JvmState jvmState);

    protected abstract void doStoreNormalizedSignature(JvmState jvmState, long invokedAtMillis, String signature,
                                                       SignatureConfidence confidence);

    private void normalizeAndProcessInvocations(JvmState jvmState, List<Invocation> invocations) {
        CodeBase codeBase = jvmState.getCodeBase();

        int recognized = 0;
        int unrecognized = 0;
        int ignored = 0;
        int overridden = 0;

        for (Invocation invocation : invocations) {
            String rawSignature = invocation.getSignature();
            String normalizedSignature = codeBase.normalizeSignature(rawSignature);
            String baseSignature = codeBase.getBaseSignature(normalizedSignature);

            SignatureConfidence confidence = null;
            if (normalizedSignature == null) {
                ignored += 1;
            } else if (codeBase.hasSignature(normalizedSignature)) {
                recognized += 1;
                confidence = SignatureConfidence.EXACT_MATCH;
            } else if (baseSignature != null) {
                overridden += 1;
                confidence = SignatureConfidence.FOUND_IN_PARENT_CLASS;
                log.debug("Signature '{}' is replaced by '{}'", normalizedSignature, baseSignature);
                normalizedSignature = baseSignature;
            } else if (normalizedSignature.equals(rawSignature)) {
                unrecognized += 1;
                confidence = SignatureConfidence.NOT_FOUND_IN_CODE_BASE;
                log.debug("Unrecognized signature: '{}'", normalizedSignature);
            } else {
                unrecognized += 1;
                confidence = SignatureConfidence.NOT_FOUND_IN_CODE_BASE;
                log.debug("Unrecognized signature: '{}' (was '{}')", normalizedSignature, rawSignature);
            }

            if (normalizedSignature != null) {
                doStoreNormalizedSignature(jvmState, invocation.getInvokedAtMillis(), normalizedSignature, confidence);
            }
        }

        FileUtils.deleteAllConsumedInvocationDataFiles(jvmState.getInvocationsFile());

        // For debugging...
        codeBase.writeSignaturesToDisk();

        if (unrecognized > 0) {
            log.warn("{} recognized, {} overridden, {} unrecognized and {} ignored method invocations applied", recognized, overridden,
                     unrecognized, ignored);
        } else {
            log.debug("{} signature invocations applied ({} overridden, {} ignored)", recognized, overridden, ignored);
        }
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.error("Cannot get name of localhost");
            return "-- unknown --";
        }
    }

    protected JvmData createUploadJvmData(JvmState jvmState) {
        Jvm jvm = jvmState.getJvm();

        return JvmData.builder()
                      .appName(jvm.getCollectorConfig().getAppName())
                      .appVersion(jvmState.getAppVersion())
                      .collectorComputerId(jvm.getComputerId())
                      .collectorHostName(jvm.getHostName())
                      .collectorResolutionSeconds(jvm.getCollectorConfig().getCollectorResolutionSeconds())
                      .collectorVcsId(jvm.getCollectorVcsId())
                      .collectorVersion(jvm.getCollectorVersion())
                      .daemonComputerId(daemonComputerId)
                      .daemonHostName(daemonHostName)
                      .daemonTimeMillis(System.currentTimeMillis())
                      .daemonUploadIntervalSeconds(daemonConfig.getServerUploadIntervalSeconds())
                      .daemonVcsId(daemonConfig.getDaemonVcsId())
                      .daemonVersion(daemonConfig.getDaemonVersion())
                      .dumpedAtMillis(jvm.getDumpedAtMillis())
                      .jvmUuid(jvm.getJvmUuid())
                      .methodVisibility(jvm.getCollectorConfig().getMethodFilter().toString())
                      .startedAtMillis(jvm.getStartedAtMillis())
                      .tags(jvm.getCollectorConfig().getTags())
                      .build();
    }
}
