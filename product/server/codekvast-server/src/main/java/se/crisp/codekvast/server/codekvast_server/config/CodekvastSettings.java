package se.crisp.codekvast.server.codekvast_server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Wrapper for environment properties codekvast.*
 *
 * @author olle.hallin@crisp.se
 */
@Component
@ConfigurationProperties(prefix = "codekvast")
@Data
public class CodekvastSettings {
    private boolean multiTenant = false;
    private int defaultTrulyDeadAfterSeconds = 30 * 24 * 60 * 60;
    private File backupPath = new File("/var/backups/codekvast");
    private int backupSaveGenerations = 5;

    public File[] getBackupPaths() {
        File fallbackPath = new File(System.getProperty("java.io.tmpdir"), "codekvast/.backup");
        return new File[]{backupPath, fallbackPath};
    }
}
