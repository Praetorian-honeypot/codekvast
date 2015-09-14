package se.crisp.codekvast.agent.main;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import se.crisp.codekvast.agent.codebase.CodeBase;
import se.crisp.codekvast.shared.config.CollectorConfig;
import se.crisp.codekvast.shared.model.Jvm;
import se.crisp.codekvast.shared.util.FileUtils;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This is the meat of the codekvast-agent. It contains a scheduled method that periodically processes data from the collector.
 *
 * @author olle.hallin@crisp.se
 */
@Component
@Slf4j
public class AgentWorker {

    private final AgentConfig config;
    private final AppVersionResolver appVersionResolver;
    private final DataProcessor dataProcessor;

    private final Map<String, JvmState> jvmStates = new HashMap<String, JvmState>();

    @Inject
    public AgentWorker(AgentConfig config, AppVersionResolver appVersionResolver, DataProcessor dataProcessor) {
        this.config = config;
        this.appVersionResolver = appVersionResolver;
        this.dataProcessor = dataProcessor;

        log.info("{} {} started", getClass().getSimpleName(), config.getDisplayVersion());
    }

    @PreDestroy
    public void shutdownHook() {
        log.info("{} {} shuts down", getClass().getSimpleName(), config.getDisplayVersion());
    }

    @Scheduled(initialDelay = 10L, fixedDelayString = "${codekvast.serverUploadIntervalSeconds}000")
    public void analyseCollectorData() {
        String oldThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName(getClass().getSimpleName());
        try {
            doAnalyzeCollectorData(System.currentTimeMillis());
        } finally {
            Thread.currentThread().setName(oldThreadName);
        }
    }

    private void doAnalyzeCollectorData(long now) {
        log.debug("Analyzing collector data");

        findJvmStates();

        for (JvmState jvmState : jvmStates.values()) {
            if (jvmState.isFirstRun()) {
                // The agent might have crashed between consuming invocation data files and storing them in the database.
                // Make sure that invocation data is not lost...
                FileUtils.resetAllConsumedInvocationDataFiles(jvmState.getInvocationsFile());
                jvmState.setFirstRun(false);
            }
            dataProcessor.processJvmData(now, jvmState);
            dataProcessor.processCodeBase(now, jvmState, new CodeBase(jvmState.getJvm().getCollectorConfig()));
            dataProcessor.processInvocationsData(now, jvmState);
        }
    }

    private void findJvmStates() {
        findJvmState(config.getDataPath());
    }

    private void findJvmState(File dataPath) {
        log.debug("Looking for jvm.dat in {}", dataPath);

        File[] files = dataPath.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().equals(CollectorConfig.JVM_BASENAME)) {
                    addOrUpdateJvmState(file);
                } else if (file.isDirectory()) {
                    findJvmState(file);
                }
            }
        }
    }

    private void addOrUpdateJvmState(File file) {
        try {

            Jvm jvm = Jvm.readFrom(file);

            JvmState jvmState = jvmStates.get(jvm.getJvmUuid());
            if (jvmState == null) {
                jvmState = new JvmState();
                jvmStates.put(jvm.getJvmUuid(), jvmState);
            }
            jvmState.setJvm(jvm);
            appVersionResolver.resolveAppVersion(jvmState);

            jvmState.setInvocationsFile(new File(file.getParentFile(), CollectorConfig.INVOCATIONS_BASENAME));
        } catch (IOException e) {
            log.error("Cannot load " + file, e);
        }
    }
}
