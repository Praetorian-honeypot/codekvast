package se.crisp.codekvast.agent.main;

import lombok.Getter;
import se.crisp.codekvast.server.agent.model.v1.UsageConfidence;
import se.crisp.codekvast.server.agent.model.v1.UsageDataEntry;

import java.util.HashSet;
import java.util.Set;

/**
 * SignatureUsage keeps track of which usage entries have not yet been uploaded to the codekvast-server.
 *
 * @author Olle Hallin
 */
class SignatureUsage {

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Getter
    private final Set<UsageDataEntry> notUploadedSignatures = new HashSet<>();

    void put(String signature, long usedAtMillis, UsageConfidence confidence) {
        if (signature != null) {
            notUploadedSignatures.add(new UsageDataEntry(signature, usedAtMillis, confidence));
        }
    }

    void clearNotUploadedSignatures() {
        notUploadedSignatures.clear();
    }
}
