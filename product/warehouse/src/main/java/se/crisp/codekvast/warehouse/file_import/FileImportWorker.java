package se.crisp.codekvast.warehouse.file_import;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.crisp.codekvast.agent.lib.model.ExportFileMetaInfo;
import se.crisp.codekvast.agent.lib.model.v1.ExportFileEntry;
import se.crisp.codekvast.agent.lib.model.v1.ExportFileFormat;
import se.crisp.codekvast.agent.lib.model.v1.SignatureConfidence;
import se.crisp.codekvast.warehouse.config.CodekvastSettings;

import javax.inject.Inject;
import java.io.*;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.time.Instant.now;
import static se.crisp.codekvast.warehouse.file_import.ImportService.*;

/**
 * Scans a certain directory for files produced by the Codekvast daemon, and attempts to import them to the database.
 *
 * @author olle.hallin@crisp.se
 */
@Service
@Slf4j
public class FileImportWorker {

    private final CodekvastSettings codekvastSettings;
    private final ImportService importService;
    private final Charset charset = Charset.forName("UTF-8");

    @Inject
    public FileImportWorker(CodekvastSettings codekvastSettings, ImportService importService) {
        this.codekvastSettings = codekvastSettings;
        this.importService = importService;
        log.info("Created");
    }

    @Scheduled(fixedDelayString = "${codekvast.importPollIntervalSeconds}000")
    public void importDaemonFiles() {
        String oldThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName("FileImport");
        try {
            log.trace("Looking for import files in {}", codekvastSettings.getImportPath());
            walkDirectory(codekvastSettings.getImportPath());
        } finally {
            Thread.currentThread().setName(oldThreadName);
        }
    }

    private void walkDirectory(File path) {
        if (path != null) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        walkDirectory(file);
                    } else if (!file.getName().endsWith(ExportFileFormat.ZIP.getSuffix())) {
                        log.debug("Ignoring {}, can only handle {} files", file, ExportFileFormat.ZIP);
                    } else {
                        importZipFile(file);

                        if (codekvastSettings.isDeleteImportedFiles()) {
                            deleteFile(file);
                        }
                    }
                }
            }
        }
    }

    private void deleteFile(File file) {
        boolean deleted = file.delete();
        if (deleted) {
            log.info("Deleted {}", file);
        } else {
            log.warn("Could not delete {}", file);
        }
    }

    @Transactional
    protected void importZipFile(File file) {
        log.debug("Importing {}", file);
        Instant startedAt = now();

        ExportFileMetaInfo metaInfo = null;
        ImportContext context = new ImportContext();

        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)));
             InputStreamReader reader = new InputStreamReader(zipInputStream, charset)) {

            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                ExportFileEntry exportFileEntry = ExportFileEntry.fromString(zipEntry.getName());
                log.debug("Reading {} ...", zipEntry.getName());

                switch (exportFileEntry) {
                case META_INFO:
                    metaInfo = ExportFileMetaInfo.fromInputStream(zipInputStream);
                    if (importService.isFileImported(metaInfo)) {
                        log.debug("{} with uuid {} has already been imported", file, metaInfo.getUuid());
                        return;
                    }
                    break;
                case APPLICATIONS:
                    readApplications(reader, context);
                    break;
                case METHODS:
                    readMethods(reader, context);
                    break;
                case JVMS:
                    readJvms(reader, context);
                    break;
                case INVOCATIONS:
                    readInvocations(reader, context);
                    break;
                }
            }
            if (metaInfo != null) {
                importService.recordFileAsImported(metaInfo,
                                                   ImportStatistics.builder()
                                                                   .importFile(file)
                                                                   .processingTime(Duration.between(startedAt, now()))
                                                                   .build());
            }
        } catch (IllegalArgumentException | IOException e) {
            log.error("Cannot import " + file, e);
        }
    }

    private void doReadCsv(InputStreamReader reader, String what, Function<String[], Void> lineProcessor) {
        CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build();
        int count = 0;
        Instant startedAt = now();
        for (String[] columns : csvReader) {
            lineProcessor.apply(columns);
            count += 1;
        }
        log.debug("Imported {} {} in {} ms", count, what, Duration.between(startedAt, now()).toMillis());
    }

    private void readApplications(InputStreamReader reader, ImportContext context) {
        doReadCsv(reader, "applications", (String[] columns) -> {
            Application app = Application.builder()
                                         .localId(Long.valueOf(columns[0]))
                                         .name(columns[1])
                                         .version(columns[2])
                                         .createdAtMillis(Long.valueOf(columns[3]))
                                         .build();

            importService.saveApplication(app, context);
            return null;
        });
    }

    private void readMethods(InputStreamReader reader, ImportContext context) {
        doReadCsv(reader, "methods", (String[] columns) -> {
            Method method = Method.builder()
                                  .localId(Long.valueOf(columns[0]))
                                  .visibility(columns[1])
                                  .signature(columns[2])
                                  .createdAtMillis(Long.valueOf(columns[3]))
                                  .declaringType(columns[4])
                                  .exceptionTypes(columns[5])
                                  .methodName(columns[6])
                                  .modifiers(columns[7])
                                  .packageName(columns[8])
                                  .parameterTypes(columns[9])
                                  .returnType(columns[10])
                                  .build();
            importService.saveMethod(method, context);
            return null;
        });
    }

    private void readJvms(InputStreamReader reader, ImportContext context) {
        doReadCsv(reader, "JVMs", (String[] columns) -> {
            Jvm jvm = Jvm.builder()
                         .localId(Long.valueOf(columns[0]))
                         .uuid(columns[1])
                         .startedAtMillis(Long.valueOf(columns[2]))
                         .dumpedAtMillis(Long.valueOf(columns[3]))
                         .jvmDataJson(columns[4])
                         .build();
            importService.saveJvm(jvm, context);
            return null;
        });
    }

    private void readInvocations(InputStreamReader reader, ImportContext context) {
        doReadCsv(reader, "invocations", (String[] columns) -> {
            Invocation invocation = Invocation.builder()
                                              .localApplicationId(Long.valueOf(columns[0]))
                                              .localMethodId(Long.valueOf(columns[1]))
                                              .localJvmId(Long.valueOf(columns[2]))
                                              .invokedAtMillis(Long.valueOf(columns[3]))
                                              .invocationCount(Long.valueOf(columns[4]))
                                              .confidence(SignatureConfidence.fromOrdinal(Integer.valueOf(columns[5])))
                                              .build();
            importService.saveInvocation(invocation, context);
            return null;
        });
    }

}
