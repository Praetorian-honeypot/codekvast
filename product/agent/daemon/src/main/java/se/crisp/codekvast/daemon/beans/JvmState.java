package se.crisp.codekvast.daemon.beans;

import lombok.Data;
import se.crisp.codekvast.daemon.codebase.CodeBase;
import se.crisp.codekvast.shared.model.Jvm;

import java.io.File;

/**
 * Mutable state for a {@link Jvm} object.
 */
@Data
public class JvmState {
    private Jvm jvm;
    private File invocationsFile;
    private CodeBase codeBase;
    private String appVersion;
    private long jvmDataUploadedAt;
    private long codebaseUploadedAt;
    private long invocationDataUploadedAt;
    private boolean firstRun = true;
}
