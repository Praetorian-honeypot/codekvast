package se.crisp.codekvast.agent.main;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

import static java.lang.Math.max;

/**
 * An immutable fingerprint of a code base. Used for comparing different code bases for equality.
 *
 * @author Olle Hallin
 */
@Value
@Slf4j
class CodeBaseFingerprint {
    private final int count;
    private final long size;
    private final long lastModified;
    private final int hashCode;

    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for incrementally building a CodeBaseFingerprint
     */
    static class Builder {
        private int count;
        private long size;
        private long lastModified;
        private long hashCode;

        public void record(File file) {
            count += 1;
            size += file.length();
            lastModified = max(lastModified, file.lastModified());
            hashCode += file.hashCode();
            log.trace("Recorded {}, {}", file, this);
        }

        CodeBaseFingerprint build() {
            return new CodeBaseFingerprint(count, size, lastModified, Long.valueOf(hashCode).hashCode());
        }
    }
}
