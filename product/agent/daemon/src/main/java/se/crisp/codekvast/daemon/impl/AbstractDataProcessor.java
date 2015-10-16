package se.crisp.codekvast.daemon.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import se.crisp.codekvast.daemon.appversion.AppVersionResolver;
import se.crisp.codekvast.daemon.beans.DaemonConfig;
import se.crisp.codekvast.daemon.beans.JvmState;
import se.crisp.codekvast.daemon.codebase.CodeBase;
import se.crisp.codekvast.daemon.codebase.CodeBaseScanner;
import se.crisp.codekvast.server.daemon_api.model.v1.SignatureConfidence;
import se.crisp.codekvast.shared.model.Invocation;
import se.crisp.codekvast.shared.util.ComputerID;
import se.crisp.codekvast.shared.util.FileUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Common behavoiur for all data processors.
 *
 * @author olle.hallin@crisp.se
 */
@Slf4j
public abstract class AbstractDataProcessor {
    protected final DaemonConfig config;
    protected final AppVersionResolver appVersionResolver;
    protected final CodeBaseScanner codeBaseScanner;
    protected final String daemonComputerId = ComputerID.compute().toString();
    protected final String daemonHostName = getHostName();

    public AbstractDataProcessor(DaemonConfig config, AppVersionResolver appVersionResolver, CodeBaseScanner codeBaseScanner) {
        this.config = config;
        this.appVersionResolver = appVersionResolver;
        this.codeBaseScanner = codeBaseScanner;
    }

    protected String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.error("Cannot get name of localhost");
            return "-- unknown --";
        }
    }

    public void processJvmData(long now, JvmState jvmState) {
        appVersionResolver.resolveAppVersion(jvmState);

        if (jvmState.getJvmDataProcessedAt() < jvmState.getJvm().getDumpedAtMillis()) {
            doProcessJvmData(jvmState);
        }
    }

    public void processCodeBase(long now, JvmState jvmState, CodeBase codeBase) {
        if (jvmState.getCodebaseProcessedAt() == 0 || !codeBase.equals(jvmState.getCodeBase())) {
            if (jvmState.getCodebaseProcessedAt() == 0) {
                log.debug("Codebase has not yet been processed");
            } else {
                appVersionResolver.resolveAppVersion(jvmState);
                log.info("Codebase has changed, it will now be re-scanned and processed");
            }

            codeBaseScanner.scanSignatures(codeBase);
            jvmState.setCodeBase(codeBase);

            doProcessCodebase(now, jvmState, codeBase);
        }
    }

    @Transactional
    public void processInvocationsData(long now, JvmState jvmState) {
        List<Invocation> invocations = FileUtils.consumeAllInvocationDataFiles(jvmState.getInvocationsFile());
        if (jvmState.getCodeBase() != null && !invocations.isEmpty()) {
            doProcessInvocationsData(jvmState, invocations);
        }
    }

    protected void storeNormalizedInvocations(JvmState jvmState, List<Invocation> invocations) {
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
                log.debug("{} replaced by {}", normalizedSignature, baseSignature);
                normalizedSignature = baseSignature;
            } else if (normalizedSignature.equals(rawSignature)) {
                unrecognized += 1;
                confidence = SignatureConfidence.NOT_FOUND_IN_CODE_BASE;
                log.debug("Unrecognized signature: {}", normalizedSignature);
            } else {
                unrecognized += 1;
                confidence = SignatureConfidence.NOT_FOUND_IN_CODE_BASE;
                log.debug("Unrecognized signature: {} (was {})", normalizedSignature, rawSignature);
            }

            if (normalizedSignature != null) {
                doStoreNormalizedSignature(jvmState, invocation, normalizedSignature, confidence);
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

    protected abstract void doProcessJvmData(JvmState jvmState);

    protected abstract void doProcessCodebase(long now, JvmState jvmState, CodeBase codeBase);

    protected abstract void doProcessInvocationsData(JvmState jvmState, List<Invocation> invocations);

    protected abstract void doStoreNormalizedSignature(JvmState jvmState, Invocation invocation, String normalizedSignature,
                                                       SignatureConfidence confidence);
}
