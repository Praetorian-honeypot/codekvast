package se.crisp.codekvast.agent.config;

import lombok.*;
import lombok.experimental.Builder;
import se.crisp.codekvast.agent.util.ConfigUtils;
import se.crisp.codekvast.agent.util.FileUtils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * Encapsulates the configuration that is used by codekvast-collector.
 * <p/>
 * It also contains methods for reading and writing collector configuration files.
 *
 * @author Olle Hallin
 */
@SuppressWarnings({"UnusedDeclaration", "ClassWithTooManyFields", "ClassWithTooManyMethods"})
@Value
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectorConfig implements CodekvastConfig {
    public static final String INVOCATIONS_BASENAME = "invocations.dat";
    public static final String JVM_BASENAME = "jvm.dat";

    public static final boolean DEFAULT_CLOBBER_AOP_XML = true;
    public static final boolean DEFAULT_INVOKE_ASPECTJ_WEAVER = true;
    public static final String DEFAULT_ASPECTJ_OPTIONS = "";
    public static final int DEFAULT_COLLECTOR_RESOLUTION_INTERVAL_SECONDS = 600;
    public static final boolean DEFAULT_VERBOSE = false;
    public static final String SAMPLE_ASPECTJ_OPTIONS = "-verbose -showWeaveInfo";
    public static final String SAMPLE_CODEBASE_URI = "file:path/to/codebase/";
    public static final String OVERRIDE_SEPARATOR = ";";
    public static final String UNSPECIFIED_VERSION = "unspecified";

    @NonNull
    private final SharedConfig sharedConfig;
    @NonNull
    private final String aspectjOptions;
    private final int collectorResolutionSeconds;
    private final boolean clobberAopXml;
    private final boolean verbose;
    private final boolean invokeAspectjWeaver;
    @NonNull
    private final String appName;
    @NonNull
    private final String appVersion;
    @NonNull
    private final URI codeBaseUri;

    public File getAspectFile() {
        return new File(sharedConfig.myDataPath(appName), "aop.xml");
    }

    public File getJvmFile() {
        return new File(sharedConfig.myDataPath(appName), JVM_BASENAME);
    }

    public File getCollectorLogFile() {
        return new File(sharedConfig.myDataPath(appName), "codekvast-collector.log");
    }

    public File getInvocationsFile() {
        return new File(sharedConfig.myDataPath(appName), INVOCATIONS_BASENAME);
    }

    public void saveTo(File file) {
        FileUtils.writePropertiesTo(file, this, "Codekvast CollectorConfig");
    }

    public static CollectorConfig parseCollectorConfig(String args) throws URISyntaxException {
        String parts[] = args.split(";");
        URI uri = new URI(parts[0]);
        String[] overrides = new String[parts.length - 1];
        System.arraycopy(parts, 1, overrides, 0, overrides.length);
        return parseCollectorConfig(uri, overrides);
    }

    public static CollectorConfig parseCollectorConfig(URI uri, String... overrides) {
        try {
            Properties props = FileUtils.readPropertiesFrom(uri);

            for (String override : overrides) {
                String parts[] = override.split("=");
                props.setProperty(parts[0], parts[1]);
            }

            return buildCollectorConfig(props);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Cannot parse %s: %s", uri, e.getMessage()), e);
        }
    }

    public static CollectorConfig buildCollectorConfig(Properties props) {
        return CollectorConfig.builder()
                              .sharedConfig(SharedConfig.buildSharedConfig(props))
                              .aspectjOptions(ConfigUtils.getOptionalStringValue(props, "aspectjOptions", DEFAULT_ASPECTJ_OPTIONS))
                              .appName(ConfigUtils.getMandatoryStringValue(props, "appName"))
                              .appVersion(ConfigUtils.getOptionalStringValue(props, "appVersion", UNSPECIFIED_VERSION))
                              .codeBaseUri(ConfigUtils.getMandatoryUriValue(props, "codeBaseUri", false))
                              .collectorResolutionSeconds(ConfigUtils.getOptionalIntValue(props, "collectorResolutionSeconds",
                                                                                          DEFAULT_COLLECTOR_RESOLUTION_INTERVAL_SECONDS))
                              .verbose(Boolean.valueOf(
                                      ConfigUtils.getOptionalStringValue(props, "verbose", Boolean.toString(DEFAULT_VERBOSE))))
                              .clobberAopXml(Boolean.valueOf(ConfigUtils.getOptionalStringValue(props, "clobberAopXml",
                                                                                                Boolean.toString(
                                                                                                        DEFAULT_CLOBBER_AOP_XML))))
                              .invokeAspectjWeaver(Boolean.valueOf(ConfigUtils.getOptionalStringValue(props,
                                                                                                      "invokeAspectjWeaver",
                                                                                                      Boolean.toString(
                                                                                                              DEFAULT_INVOKE_ASPECTJ_WEAVER))))
                              .build();
    }

    @SneakyThrows(URISyntaxException.class)
    public static CollectorConfig createSampleCollectorConfig() {
        return CollectorConfig.builder()
                              .sharedConfig(SharedConfig.buildSampleSharedConfig())
                              .aspectjOptions(SAMPLE_ASPECTJ_OPTIONS)
                              .appName("Sample Application Name")
                              .appVersion(UNSPECIFIED_VERSION)
                              .codeBaseUri(new URI(SAMPLE_CODEBASE_URI))
                              .collectorResolutionSeconds(DEFAULT_COLLECTOR_RESOLUTION_INTERVAL_SECONDS)
                              .verbose(DEFAULT_VERBOSE)
                              .clobberAopXml(DEFAULT_CLOBBER_AOP_XML)
                              .invokeAspectjWeaver(DEFAULT_INVOKE_ASPECTJ_WEAVER)
                              .build();
    }

}
