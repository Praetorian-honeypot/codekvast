package se.crisp.codekvast.server.codekvast_server.model.event.display;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * A display object for one collector.
 *
 * @author olle.hallin@crisp.se
 */
@Value
@Builder
public class CollectorDisplay {
    @NonNull
    String appName;
    @NonNull
    String appVersion;
    @NonNull
    String agentHostname;
    @NonNull
    String daemonVersion;
    int daemonUploadIntervalSeconds;
    long agentClockSkewMillis;
    @NonNull
    String collectorHostname;
    @NonNull
    String collectorVersion;
    long collectorStartedAtMillis;
    int collectorResolutionSeconds;
    @NonNull
    String methodVisibility;
    long dataReceivedAtMillis;
}
