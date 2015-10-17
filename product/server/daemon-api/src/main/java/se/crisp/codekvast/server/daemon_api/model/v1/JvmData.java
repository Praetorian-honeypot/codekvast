package se.crisp.codekvast.server.daemon_api.model.v1;

import lombok.*;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

/**
 * REST data about one instrumented JVM.
 *
 * Should be uploaded regularly during the lifetime of the JVM.
 *
 * @author olle.hallin@crisp.se
 */
@Data
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class JvmData {
    @NonNull
    @NotBlank
    @Size(max = Constraints.MAX_APP_NAME_LENGTH)
    private String appName;

    @NonNull
    @NotBlank
    @Size(max = Constraints.MAX_APP_VERSION_LENGTH)
    private String appVersion;

    @NonNull
    @NotBlank
    @Size(min = Constraints.MIN_JVM_UUID_LENGTH, max = Constraints.MAX_FINGERPRINT_LENGTH)
    private String jvmUuid;

    @NonNull
    @Size(max = Constraints.MAX_TAGS_LENGTH)
    private String tags;

    @NonNull
    @NotBlank
    @Size(max = Constraints.MAX_HOST_NAME_LENGTH)
    private String collectorHostName;

    @NonNull
    @NotBlank
    @Size(max = Constraints.MAX_COMPUTER_ID_LENGTH)
    private String collectorComputerId;

    @Min(1)
    private int collectorResolutionSeconds;

    @NonNull
    @NotBlank
    @Size(max = Constraints.MAX_METHOD_VISIBILITY_LENGTH)
    private String methodVisibility;

    @NonNull
    @NotBlank
    @Size(max = Constraints.MAX_HOST_NAME_LENGTH)
    private String daemonHostName;

    @NonNull
    @NotBlank
    @Size(max = Constraints.MAX_COMPUTER_ID_LENGTH)
    private String daemonComputerId;

    @Min(1)
    private int daemonUploadIntervalSeconds;

    @NonNull
    @Size(max = Constraints.MAX_CODEKVAST_VERSION_LENGTH)
    private String daemonVersion;

    @NonNull
    @Size(max = Constraints.MAX_CODEKVAST_VCS_ID_LENGTH)
    private String daemonVcsId;

    @NonNull
    @Size(max = Constraints.MAX_CODEKVAST_VERSION_LENGTH)
    private String collectorVersion;


    @NonNull
    @Size(max = Constraints.MAX_CODEKVAST_VCS_ID_LENGTH)
    private String collectorVcsId;

    @NonNull
    @Min(1)
    private Long startedAtMillis;

    @NonNull
    @Min(1)
    private Long dumpedAtMillis;

    /* Disable clock skew compensation during tests by setting -1 */
    @NonNull
    @Min(-1)
    private Long daemonTimeMillis;
}