package se.crisp.codekvast.agent.config;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Builder;
import se.crisp.codekvast.agent.util.ConfigUtils;

import java.io.File;
import java.util.Properties;

/**
 * Base class for agent side configuration objects.
 *
 * @author Olle Hallin (qolha), olle.hallin@crisp.se
 */
@Value
@Builder
public class SharedConfig implements CodekvastConfig {
    @NonNull
    private final File dataPath;

    protected static SharedConfig buildSharedConfig(Properties props) {
        return builder()
                .dataPath(new File(ConfigUtils.getOptionalStringValue(props, "dataPath", System.getProperty("java.io.tmpdir") +
                        "/codekvast")))
                .build();
    }

    public static SharedConfig buildSampleSharedConfig() {
        return builder()
                .dataPath(new File(ConfigUtils.SAMPLE_DATA_PATH))
                .build();
    }
}
