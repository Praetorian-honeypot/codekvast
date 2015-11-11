/*
 * Copyright (c) 2015 Crisp AB
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

package se.crisp.codekvast.daemon.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import se.crisp.codekvast.daemon.appversion.AppVersionResolver;
import se.crisp.codekvast.daemon.beans.DaemonConfig;
import se.crisp.codekvast.daemon.beans.JvmState;
import se.crisp.codekvast.daemon.codebase.CodeBase;
import se.crisp.codekvast.daemon.util.LogUtil;
import se.crisp.codekvast.shared.config.CollectorConfig;
import se.crisp.codekvast.shared.model.Jvm;
import se.crisp.codekvast.shared.util.FileUtils;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static java.time.Instant.now;

/**
 * This is the meat of the codekvast-daemon. It contains a scheduled method that periodically processes data from the collector.
 *
 * @author olle.hallin@crisp.se
 */
@Component
@Slf4j
public class DaemonWorker {

    private final DaemonConfig config;
    private final AppVersionResolver appVersionResolver;
    private final CollectorDataProcessor collectorDataProcessor;

    private final Map<String, JvmState> jvmStates = new HashMap<String, JvmState>();
    private final DataExporter dataExporter;
    private Instant exportedAt = Instant.MIN;

    @Inject
    public DaemonWorker(DaemonConfig config, AppVersionResolver appVersionResolver, CollectorDataProcessor collectorDataProcessor,
                        DataExporter dataExporter) {
        this.config = config;
        this.appVersionResolver = appVersionResolver;
        this.collectorDataProcessor = collectorDataProcessor;
        this.dataExporter = dataExporter;

        log.info("{} {} started", getClass().getSimpleName(), config.getDisplayVersion());
    }

    @PreDestroy
    public void shutdownHook() {
        log.info("{} {} shuts down", getClass().getSimpleName(), config.getDisplayVersion());
    }

    @Scheduled(initialDelay = 10L, fixedDelayString = "${codekvast.dataProcessingIntervalSeconds}000")
    public void analyseCollectorData() {
        String oldThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName(getClass().getSimpleName());
        try {
            findAndAnalyzeCollectorData();
            exportDataIfNeeded();
        } finally {
            Thread.currentThread().setName(oldThreadName);
        }
    }

    private void findAndAnalyzeCollectorData() {
        log.debug("Analyzing collector data");

        findJvmStates(config.getDataPath());

        for (JvmState jvmState : jvmStates.values()) {
            if (jvmState.isFirstRun()) {
                // The daemon might have crashed between consuming invocation data files and storing them in the database.
                // Make sure that invocation data is not lost...
                FileUtils.resetAllConsumedInvocationDataFiles(jvmState.getInvocationsFile());
                jvmState.setFirstRun(false);
            }
            try {
                collectorDataProcessor.processCollectorData(jvmState, new CodeBase(jvmState.getJvm().getCollectorConfig()));
            } catch (DataProcessingException e) {
                LogUtil.logException(log, "Could not process data for " + jvmState.getJvm().getCollectorConfig().getAppName(), e);
            }
        }
    }

    private void exportDataIfNeeded() {
        Instant lastCollectorDataProcessedAt = collectorDataProcessor.getLastCollectorDataProcessedAt();
        log.debug("lastCollectorDataProcessedAt={}, exportedAt={}", lastCollectorDataProcessedAt, exportedAt);

        if (lastCollectorDataProcessedAt.isAfter(exportedAt)) {
            doExportData();
            exportedAt = now();
        }
    }

    private void doExportData() {
        try {
            dataExporter.exportData();
        } catch (DataExportException e) {
            LogUtil.logException(log, "Could not export data: " + e, e);
        }
    }

    private void findJvmStates(File dataPath) {
        log.debug("Looking for jvm.dat in {}", dataPath);

        File[] files = dataPath.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().equals(CollectorConfig.JVM_BASENAME)) {
                    addOrUpdateJvmState(file);
                } else if (file.isDirectory()) {
                    findJvmStates(file);
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
