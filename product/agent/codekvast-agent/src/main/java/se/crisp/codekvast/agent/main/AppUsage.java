package se.crisp.codekvast.agent.main;

import lombok.Getter;
import se.crisp.codekvast.server.agent.model.v1.UsageDataEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Holds a map of usage data entries and which entries have not yet been uploaded to the codekvast-server.
 *
 * @author Olle Hallin
 */
class AppUsage {

    private final Map<String, UsageDataEntry> entries = new HashMap<>();

    @Getter
    private final Set<UsageDataEntry> notUploadedSignatures = new HashSet<>();

    void put(String signature, long usedAtMillis, int confidence) {
        if (signature != null) {
            UsageDataEntry oldEntry = entries.get(signature);
            UsageDataEntry newEntry = new UsageDataEntry(signature, usedAtMillis, confidence);

            entries.put(signature, newEntry);

            if (!newEntry.equals(oldEntry)) {
                notUploadedSignatures.add(newEntry);
            }
        }
    }

    void allSignaturesAreUploaded() {
        notUploadedSignatures.clear();
    }
}
