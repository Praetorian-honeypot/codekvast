package se.crisp.duck.agent.util;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Builder;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Olle Hallin
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "UnusedDeclaration"})
@Value
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Configuration {
    public static final int DEFAULT_DUMP_INTERVAL_SECONDS = 600;
    public static final int DEFAULT_UPLOAD_INTERVAL_SECONDS = 3600;
    public static final String DEFAULT_ASPECTJ_OPTIONS = "";
    public static final String SAMPLE_ASPECTJ_OPTIONS = "-verbose -showWeaveInfo";
    public static final boolean DEFAULT_VERBOSE = false;

    private final boolean verbose;
    private final String customerName;
    private final String appName;
    private final String environment;
    private final URI codeBaseUri;
    private final String packagePrefix;
    private final String aspectjOptions;
    private final File dataPath;
    private final int sensorDumpIntervalSeconds;
    private final int serverUploadIntervalSeconds;
    private final URI serverUri;

    public File getDataFile() {
        return new File(getSensorsPath(), appName + ".dat");
    }

    public File getSensorFile() {
        return new File(getSensorsPath(), appName + ".properties");
    }

    public File getSignatureFile() {
        return new File(dataPath, "signatures.dat");
    }

    public File getAspectFile() {
        return new File(dataPath, "aop.xml");
    }

    public void saveTo(File file) {
        SensorUtils.writePropertiesTo(file, this, "Duck Configuration");
    }

    public File getSensorsPath() {
        return new File(dataPath, "sensors");
    }

    public static Configuration parseConfigFile(String configFile) {
        File file = new File(configFile);
        try {
            Properties props = SensorUtils.readPropertiesFrom(file);

            String customerName = getMandatoryStringValue(props, "customerName");

            String appName = System.getProperty("duck.appName", getMandatoryStringValue(props, "appName"));
            if (!appName.equals(normalizePathName(appName))) {
                throw new IllegalArgumentException("Illegal appName '" + appName + "': only digits, letters, '-' and '_' allowed");
            }

            return Configuration.builder()
                                .customerName(customerName)
                                .appName(appName)
                                .environment(getMandatoryStringValue(props, "environment"))
                                .codeBaseUri(getMandatoryUriValue(props, "codeBaseUri"))
                                .packagePrefix(getMandatoryStringValue(props, "packagePrefix"))
                                .aspectjOptions(getOptionalStringValue(props, "aspectjOptions", DEFAULT_ASPECTJ_OPTIONS))
                                .sensorDumpIntervalSeconds(getOptionalIntValue(props, "sensorDumpIntervalSeconds",
                                                                               DEFAULT_DUMP_INTERVAL_SECONDS))
                                .dataPath(new File(getOptionalStringValue(props, "dataPath", getDefaultDataPath(customerName))))
                                .serverUploadIntervalSeconds(getOptionalIntValue(props, "serverUploadIntervalSeconds",
                                                                                 DEFAULT_UPLOAD_INTERVAL_SECONDS))
                                .serverUri(getMandatoryUriValue(props, "serverUri"))
                                .verbose(Boolean.parseBoolean(getOptionalStringValue(props, "verbose", Boolean.toString(DEFAULT_VERBOSE))))
                                .build();
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Cannot parse %s: %s", file.getAbsolutePath(), e.getMessage()));
        }
    }

    @SneakyThrows(URISyntaxException.class)
    public static Configuration createSampleConfiguration() {
        String customerName = "Customer Name";
        return Configuration.builder()
                            .customerName(customerName)
                            .appName("app-name")
                            .environment("environment")
                            .packagePrefix("com.acme")
                            .codeBaseUri(new URI("file:/path/to/my/code/base"))
                            .aspectjOptions(SAMPLE_ASPECTJ_OPTIONS)
                            .dataPath(new File("/var/lib/duck", normalizePathName(customerName)))
                            .sensorDumpIntervalSeconds(DEFAULT_DUMP_INTERVAL_SECONDS)
                            .serverUploadIntervalSeconds(DEFAULT_UPLOAD_INTERVAL_SECONDS)
                            .serverUri(new URI("http://some-duck-server"))
                            .verbose(DEFAULT_VERBOSE)
                            .build();

    }

    private static String getOptionalStringValue(Properties props, String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    private static String getDefaultDataPath(String customerName) {
        File basePath = new File("/var/lib");
        if (!basePath.canWrite()) {
            basePath = new File(System.getProperty("java.io.tmpdir"));
        }
        return new File(basePath, "duck/" + normalizePathName(customerName)).getAbsolutePath();
    }

    private static String normalizePathName(String path) {
        return path.replaceAll("[^a-zA-Z0-9_\\-]", "").toLowerCase();
    }

    private static URI getMandatoryUriValue(Properties props, String key) {
        String value = getMandatoryStringValue(props, key);
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(String.format("Illegal URI value for %s: %s", key, value));
        }
    }

    private static int getOptionalIntValue(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(String.format("Illegal integer value for %s: %s", key, value));
            }
        }
        return defaultValue;
    }

    private static String getMandatoryStringValue(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return value;
    }
}
