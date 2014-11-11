package se.crisp.codekvast.agent.collector;

import org.aspectj.lang.Signature;
import se.crisp.codekvast.agent.config.CollectorConfig;
import se.crisp.codekvast.agent.model.Jvm;
import se.crisp.codekvast.agent.util.FileUtils;
import se.crisp.codekvast.agent.util.SignatureUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * This is the target of the method execution recording aspects.
 * <p/>
 * It holds data about method invocations, and methods for outputting the invocation data to disk.
 *
 * @author Olle Hallin
 */
@SuppressWarnings("Singleton")
public class InvocationRegistry {

    public static final boolean SHOULD_STRIP_MODIFIERS_AND_RETURN_TYPE_NOW = false;

    public static InvocationRegistry instance;

    private final CollectorConfig config;
    private final Jvm jvm;
    private final File jvmFile;

    // Toggle between two invocation sets to avoid synchronisation
    private final Set[] invocations = new Set[2];
    private volatile int currentInvocationIndex = 0;
    private long recordingIntervalStartedAtMillis = System.currentTimeMillis();

    public InvocationRegistry(CollectorConfig config, Jvm jvm) {
        this.config = config;
        this.jvm = jvm;
        this.jvmFile = config.getJvmFile();

        for (int i = 0; i < invocations.length; i++) {
            this.invocations[i] = new ConcurrentSkipListSet<String>();
        }
    }

    /**
     * Must be called before handing over to the AspectJ load-time weaver.
     */
    public static void initialize(CollectorConfig config) {
        InvocationRegistry.instance = new InvocationRegistry(config,
                                                   Jvm.builder()
                                                      .collectorConfig(config)
                                                         .hostName(getHostName())
                                                         .jvmFingerprint(UUID.randomUUID().toString())
                                                         .startedAtMillis(System.currentTimeMillis())
                                                         .build());
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    /**
     * Record that this method signature was invoked at current recording interval.
     * <p/>
     * Thread-safe.
     */
    public void registerMethodInvocation(Signature signature) {
        //noinspection unchecked
        invocations[currentInvocationIndex].add(SignatureUtils.signatureToString(signature, SHOULD_STRIP_MODIFIERS_AND_RETURN_TYPE_NOW));
    }

    /**
     * Record that this JPS page was invoked at current recording interval.
     * <p/>
     * Thread-safe.
     */
    public void registerJspPageExecution(String pageName) {
        //noinspection unchecked
        invocations[currentInvocationIndex].add(pageName);
    }

    /**
     * Dumps method invocations to a file on disk.
     * <p/>
     * Thread-safe.
     */
    public void dumpDataToDisk(int dumpCount) {
        File outputPath = config.getInvocationsFile().getParentFile();
        outputPath.mkdirs();
        if (!outputPath.exists()) {
            CodekvastCollector.out.println("Cannot dump invocation data, " + outputPath + " cannot be created");
        } else {
            long oldRecordingIntervalStartedAtMillis = recordingIntervalStartedAtMillis;
            int oldIndex = currentInvocationIndex;

            toggleInvocationsIndex();

            dumpJvmData();

            //noinspection unchecked
            FileUtils.writeInvocationDataTo(config.getInvocationsFile(), dumpCount, oldRecordingIntervalStartedAtMillis,
                                            invocations[oldIndex], !SHOULD_STRIP_MODIFIERS_AND_RETURN_TYPE_NOW);

            invocations[oldIndex].clear();
        }
    }

    private void toggleInvocationsIndex() {
        recordingIntervalStartedAtMillis = System.currentTimeMillis();
        currentInvocationIndex = currentInvocationIndex == 0 ? 1 : 0;
    }

    public CollectorConfig getConfig() {
        return config;
    }

    /**
     * Dumps data about this JVM run to a disk file.
     */
    private void dumpJvmData() {
        File tmpFile = null;
        try {
            tmpFile = File.createTempFile("codekvast", ".tmp", jvmFile.getParentFile());
            jvm.saveTo(tmpFile);
            FileUtils.renameFile(tmpFile, jvmFile);
        } catch (IOException e) {
            CodekvastCollector.out.println(CodekvastCollector.NAME + " cannot save " + jvmFile + ": " + e);
        } finally {
            FileUtils.safeDelete(tmpFile);
        }
    }

}
