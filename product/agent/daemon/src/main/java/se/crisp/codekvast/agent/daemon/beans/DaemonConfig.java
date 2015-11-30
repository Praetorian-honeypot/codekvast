package se.crisp.codekvast.agent.daemon.beans;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Encapsulates the configuration of the codekvast-daemon.
 *
 * @author olle.hallin@crisp.se
 */
@Component
@ConfigurationProperties(prefix = "codekvast")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DaemonConfig {
    @NonNull
    private File dataPath;
    @NonNull
    private Integer dataProcessingIntervalSeconds;
    @NonNull
    private String daemonVersion;
    @NonNull
    private String daemonVcsId;
    @NonNull
    private String environment;
    @NonNull
    private File exportFile;

    private String uploadToHost;
    private String uploadToPath;

    public boolean isUploadEnabled() {
        return uploadToHost != null && !uploadToHost.isEmpty() && uploadToPath != null && !uploadToPath.isEmpty();
    }

    public String getDisplayVersion() {
        return daemonVersion + "-" + daemonVcsId;
    }
}
