package se.crisp.codekvast.server.daemon_api.impl;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import se.crisp.codekvast.server.daemon_api.DaemonApi;
import se.crisp.codekvast.server.daemon_api.DaemonApiConfig;
import se.crisp.codekvast.server.daemon_api.DaemonApiException;
import se.crisp.codekvast.server.daemon_api.DaemonRestEndpoints;
import se.crisp.codekvast.server.daemon_api.model.test.Ping;
import se.crisp.codekvast.server.daemon_api.model.test.Pong;
import se.crisp.codekvast.server.daemon_api.model.v1.JvmData;
import se.crisp.codekvast.server.daemon_api.model.v1.SignatureConfidence;
import se.crisp.codekvast.server.daemon_api.model.v1.SignatureData;
import se.crisp.codekvast.server.daemon_api.model.v1.SignatureEntry;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The implementation of the DaemonApi.
 *
 * Uses a Spring RestTemplate for doing the REST calls.
 *
 * @author olle.hallin@crisp.se
 */
@Slf4j
@Component
@Profile(DaemonApi.SPRING_PROFILE_NAME)
public class DaemonApiImpl implements DaemonApi {

    private static final int UPLOAD_CHUNK_SIZE = 1000;

    private final DaemonApiConfig config;
    private final Validator validator;

    @Getter
    private final RestTemplate restTemplate;

    @Inject
    public DaemonApiImpl(DaemonApiConfig config, Validator validator) {
        this.config = config;
        this.validator = validator;
        this.restTemplate = new RestTemplate(createBasicAuthHttpClient(config.getApiAccessID(), config.getApiAccessSecret()));
    }

    HttpComponentsClientHttpRequestFactory createBasicAuthHttpClient(final String username, final String password) {
        CredentialsProvider cp = new BasicCredentialsProvider();
        cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        return new HttpComponentsClientHttpRequestFactory(HttpClients.custom().setDefaultCredentialsProvider(cp).build());
    }

    @Override
    public void uploadJvmData(JvmData jvmData) throws DaemonApiException {
        String endPoint = config.getServerUri() + DaemonRestEndpoints.UPLOAD_V1_JVM_DATA;
        log.debug("Uploading JVM data to {}", endPoint);

        try {
            long startedAt = System.currentTimeMillis();

            restTemplate.postForEntity(new URI(endPoint), validate(jvmData), Void.class);

            log.info("Uploaded JVM data from {} to {} in {}s", jvmData.getAppName(), endPoint, elapsedSeconds(startedAt));
        } catch (URISyntaxException e) {
            throw new DaemonApiException("Illegal REST endpoint: " + endPoint, e);
        } catch (RestClientException e) {
            throw new DaemonApiException("Failed to post JVM run data", e);
        }
    }

    @Override
    public void uploadSignatureData(JvmData jvmData, Collection<String> signatures) throws DaemonApiException {
        if (signatures.isEmpty()) {
            log.debug("Not uploading empty signatures");
            return;
        }

        List<SignatureEntry> signatureEntries = new ArrayList<SignatureEntry>(signatures.size());
        for (String s : signatures) {
            signatureEntries.add(new SignatureEntry(s, 0L, 0L, SignatureConfidence.NOT_INVOKED));
        }

        uploadInvocationData(jvmData, signatureEntries);
    }

    @Override
    public void uploadInvocationData(JvmData jvmData, List<SignatureEntry> signatures)
            throws DaemonApiException {
        if (signatures.isEmpty()) {
            log.debug("Not uploading empty invocations");
            return;
        }

        String endPoint = config.getServerUri() + DaemonRestEndpoints.UPLOAD_V1_SIGNATURES;
        log.debug("Uploading {} invocations from {} to {}", signatures.size(), jvmData.getAppName(), endPoint);
        long startedAtMillis = System.currentTimeMillis();

        try {
            URI uri = new URI(endPoint);

            int chunkNumber = 1;
            for (List<SignatureEntry> chunk : Lists.partition(signatures, UPLOAD_CHUNK_SIZE)) {
                uploadSignatureChunk(uri, jvmData.getJvmUuid(), chunkNumber, chunk);
                chunkNumber += 1;
            }

            log.info("Uploaded {} signatures from {} to {} in {}s", signatures.size(), jvmData.getAppName(), endPoint,
                     elapsedSeconds(startedAtMillis));
        } catch (URISyntaxException e) {
            throw new DaemonApiException("Illegal REST endpoint: " + endPoint, e);
        }
    }

    public void uploadSignatureChunk(URI uri, String jvmUuid, int chunkNumber, List<SignatureEntry> chunk) throws DaemonApiException {
        try {
            long startedAt = System.currentTimeMillis();
            log.debug("Uploading chunk #{} of size {}", chunkNumber, chunk.size());

            SignatureData data =
                    SignatureData.builder().jvmUuid(jvmUuid).daemonTimeMillis(System.currentTimeMillis()).signatures(chunk).build();
            restTemplate.postForEntity(uri, validate(data), Void.class);

            log.debug("Uploaded chunk #{} in {} ms", chunkNumber, System.currentTimeMillis() - startedAt);
        } catch (RestClientException e) {
            throw new DaemonApiException("Failed to post signatures data", e);
        }
    }

    @Override
    public String ping(String message) throws DaemonApiException {
        String endPoint = config.getServerUri() + DaemonRestEndpoints.PING;
        log.debug("Sending ping '{}' to {}", message, endPoint);
        try {
            Ping data = Ping.builder().message(message).build();

            ResponseEntity<Pong> response = restTemplate.postForEntity(new URI(endPoint), data, Pong.class);

            String pongMessage = response.getBody().getMessage();
            log.debug("Server responded with '{}'", pongMessage);
            return pongMessage;
        } catch (URISyntaxException e) {
            throw new DaemonApiException("Illegal REST endpoint: " + endPoint, e);
        } catch (RestClientException e) {
            throw new DaemonApiException("Failed to ping server", e);
        }
    }

    @Override
    public URI getServerUri() {
        return config.getServerUri();
    }

    private <T> T validate(T data) throws DaemonApiException {
        Set<ConstraintViolation<T>> violations = validator.validate(data);

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Invalid ").append(data.getClass().getSimpleName());
            String delimiter = ": ";
            for (ConstraintViolation<T> violation : violations) {
                sb.append(delimiter).append(violation.getPropertyPath()).append(" ").append(violation.getInvalidValue()).append(" ").append
                        (violation.getMessage());
                delimiter = ", ";
            }

            throw new DaemonApiException(sb.toString());
        }

        return data;
    }

    private int elapsedSeconds(long startedAtMillis) {
        return Math.round((System.currentTimeMillis() - startedAtMillis) / 1000f);
    }

}
