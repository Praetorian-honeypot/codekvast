package se.crisp.codekvast.server.agent.impl;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import se.crisp.codekvast.server.agent.AgentRestEndpoints;
import se.crisp.codekvast.server.agent.ServerDelegate;
import se.crisp.codekvast.server.agent.ServerDelegateConfig;
import se.crisp.codekvast.server.agent.ServerDelegateException;
import se.crisp.codekvast.server.agent.model.test.Ping;
import se.crisp.codekvast.server.agent.model.test.Pong;
import se.crisp.codekvast.server.agent.model.v1.*;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;

/**
 * The implementation of the ServerDelegate.
 * <p/>
 * Uses a Spring RestTemplate for doing the REST calls.
 *
 * @author Olle Hallin
 */
@Slf4j
@Component
public class ServerDelegateImpl implements ServerDelegate {

    private final ServerDelegateConfig config;
    private final Header header;

    @Getter
    private final RestTemplate restTemplate;

    @Inject
    public ServerDelegateImpl(ServerDelegateConfig config) {
        this.config = config;
        this.header = Header.builder()
                            .customerName(config.getCustomerName())
                            .appName(config.getAppName())
                            .appVersion(config.getAppVersion())
                            .environment(config.getEnvironment())
                            .build();
        this.restTemplate = new RestTemplate(createBasicAuthHttpClient(config.getApiUsername(), config.getApiPassword()));
    }

    HttpComponentsClientHttpRequestFactory createBasicAuthHttpClient(final String username, final String password) {
        CredentialsProvider cp = new BasicCredentialsProvider();
        cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        return new HttpComponentsClientHttpRequestFactory(HttpClients.custom().setDefaultCredentialsProvider(cp).build());
    }

    @Override
    public void uploadJvmRunData(String hostName, long startedAtMillis, long dumpedAtMillis, String jvmFingerprint)
            throws ServerDelegateException {
        String endPoint = config.getServerUri() + AgentRestEndpoints.UPLOAD_V1_JVM_RUN;

        log.debug("Uploading JVM run data to {}", endPoint);

        try {
            long startedAt = System.currentTimeMillis();

            JvmRunData data = JvmRunData.builder().header(header)
                                        .hostName(hostName)
                                        .startedAtMillis(startedAtMillis)
                                        .dumpedAtMillis(dumpedAtMillis)
                                        .jvmFingerprint(jvmFingerprint)
                                        .build();

            restTemplate.postForEntity(new URI(endPoint), data, Void.class);

            log.info("Uploaded {} to {} in {}s", data, endPoint, elapsedSeconds(startedAt));
        } catch (URISyntaxException e) {
            throw new ServerDelegateException("Illegal REST endpoint: " + endPoint, e);
        } catch (RestClientException e) {
            throw new ServerDelegateException("Failed to post JVM run data", e);
        }
    }

    private int elapsedSeconds(long startedAtMillis) {
        return Math.round((System.currentTimeMillis() - startedAtMillis) / 1000f);
    }

    @Override
    public void uploadSignatureData(Collection<String> signatures) throws ServerDelegateException {
        if (signatures.isEmpty()) {
            log.debug("Not uploading empty signatures");
            return;
        }

        String endPoint = config.getServerUri() + AgentRestEndpoints.UPLOAD_V1_SIGNATURES;
        log.debug("Uploading {} signatures to {}", signatures.size(), endPoint);

        try {
            long startedAtMillis = System.currentTimeMillis();

            SignatureData data = SignatureData.builder().header(header).signatures(signatures).build();

            restTemplate.postForEntity(new URI(endPoint), data, Void.class);

            log.info("Uploaded {} signatures to {} in {}s", signatures.size(), endPoint, elapsedSeconds(startedAtMillis));
        } catch (URISyntaxException e) {
            throw new ServerDelegateException("Illegal REST endpoint: " + endPoint, e);
        } catch (RestClientException e) {
            throw new ServerDelegateException("Failed to post signature data", e);
        }
    }

    @Override
    public void uploadUsageData(@NonNull String jvmFingerprint, Collection<UsageDataEntry> usage) throws ServerDelegateException {
        if (usage.isEmpty()) {
            log.debug("Not uploading empty usage");
            return;
        }

        String endPoint = config.getServerUri() + AgentRestEndpoints.UPLOAD_V1_USAGE;
        log.debug("Uploading {} signatures to {}", usage.size(), endPoint);

        try {
            long startedAtMillis = System.currentTimeMillis();

            UsageData data = UsageData.builder().header(header).jvmFingerprint(jvmFingerprint).usage(usage).build();

            restTemplate.postForEntity(new URI(endPoint), data, Void.class);

            log.info("Uploaded {} signatures to {} in {}s", usage.size(), endPoint, elapsedSeconds(startedAtMillis));
        } catch (URISyntaxException e) {
            throw new ServerDelegateException("Illegal REST endpoint: " + endPoint, e);
        } catch (RestClientException e) {
            throw new ServerDelegateException("Failed to post usage data", e);
        }
    }

    @Override
    public String ping(String message) throws ServerDelegateException {
        String endPoint = config.getServerUri() + AgentRestEndpoints.PING;
        log.debug("Sending ping '{}' to {}", message, endPoint);
        try {

            ResponseEntity<Pong> response =
                    restTemplate.postForEntity(new URI(endPoint), Ping.builder().message(message).build(), Pong.class);

            String pongMessage = response.getBody().getMessage();
            log.debug("Server responded with '{}'", pongMessage);
            return pongMessage;
        } catch (URISyntaxException e) {
            throw new ServerDelegateException("Illegal REST endpoint: " + endPoint, e);
        } catch (RestClientException e) {
            throw new ServerDelegateException("Failed to post usage data", e);
        }
    }

}
