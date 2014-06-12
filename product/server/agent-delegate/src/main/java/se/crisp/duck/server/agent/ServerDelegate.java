package se.crisp.duck.server.agent;

import se.crisp.duck.server.agent.model.v1.UsageDataEntry;

import java.util.Collection;
import java.util.UUID;

/**
 * This is the business delegate interface used by a Duck agent for communicating with the server.
 *
 * @author Olle Hallin
 */
public interface ServerDelegate {
    /**
     * Uploads data about a sensor to the server.
     *
     * @param hostName        The host name of the sensor
     * @param startedAtMillis The instant the sensor was started
     * @param dumpedAtMillis  The instant the latest usage dump was made
     * @param uuid            The UUID of the sensor
     * @throws ServerDelegateException
     */
    void uploadSensorData(String hostName, long startedAtMillis, long dumpedAtMillis, UUID uuid) throws ServerDelegateException;

    /**
     * Upload a collection of signatures to the server.
     * <p/>
     * This is typically done when the agent starts and then each time it detects a change in the code base.
     *
     * @param signatures The complete set of signatures found in the application
     * @throws ServerDelegateException Should the upload fail for some reason.
     */
    void uploadSignatureData(Collection<String> signatures) throws ServerDelegateException;

    /**
     * Upload method usage to the server.
     * <p/>
     * This is done as soon as a new usage file is produced by the sensor.
     *
     * @param usage A collection of usage data entries
     * @throws ServerDelegateException
     */
    void uploadUsageData(Collection<UsageDataEntry> usage) throws ServerDelegateException;

}
