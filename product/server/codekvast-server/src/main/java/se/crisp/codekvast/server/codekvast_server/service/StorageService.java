package se.crisp.codekvast.server.codekvast_server.service;

import se.crisp.codekvast.server.agent.model.v1.JvmRunData;
import se.crisp.codekvast.server.agent.model.v1.SignatureData;
import se.crisp.codekvast.server.agent.model.v1.UsageData;
import se.crisp.codekvast.server.agent.model.v1.UsageDataEntry;

import java.util.Collection;

/**
 * The storage API.
 *
 * @author Olle Hallin
 */
public interface StorageService {

    /**
     * Stores sensor data received from an agent.
     *
     * @param data The received sensor data
     */
    void storeSensorData(JvmRunData data);

    /**
     * Stores signature data received from an agent.
     *
     * @param data The received signature data
     */
    void storeSignatureData(SignatureData data);

    /**
     * Stores usage data received from an agent.
     *
     * @param data The received usage data
     */
    void storeUsageData(UsageData data);

    Collection<UsageDataEntry> getSignatures();
}
