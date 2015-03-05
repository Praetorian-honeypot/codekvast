package se.crisp.codekvast.server.codekvast_server;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Random;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static se.crisp.codekvast.server.agent_api.model.v1.Constraints.*;
import static se.crisp.codekvast.server.codekvast_server.controller.RegistrationController.*;

@RunWith(SpringJUnit4ClassRunner.class)
@EmbeddedCodekvastServerIntegTest
public class RegistrationControllerIntegTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();
    private URI registrationUri;
    private final Random random = new Random();

    @Before
    public void before() throws Exception {
        registrationUri = new URI(String.format("http://localhost:%d%s", port, REGISTER_PATH));
    }

    @Test
    public void testRegistration_success() {
        String fullName = randomString(MAX_FULL_NAME_LENGTH);
        // @formatter:off
        RegistrationRequest request = RegistrationRequest.builder()
                                                         .fullName(fullName)
                                                         .username(randomString(MAX_USER_NAME_LENGTH))
                                                         .emailAddress(randomEmailAddress(MAX_EMAIL_ADDRESS_LENGTH))
                                                         .password(randomString(100))
                                                         .organisationName(randomString(MAX_ORGANISATION_NAME_LENGTH))
                                                         .build();
        // @formatter:on
        RegistrationResponse response = restTemplate.postForEntity(registrationUri, request, RegistrationResponse.class).getBody();
        assertThat(response.getGreeting(), is("Welcome " + fullName + "!"));
    }

    @Test(expected = HttpClientErrorException.class)
    public void testRegistration_too_long_full_name() {
        // @formatter:off
        RegistrationRequest request = RegistrationRequest.builder()
                                                         .fullName(randomString(MAX_FULL_NAME_LENGTH + 1))
                                                         .username(randomString(MAX_USER_NAME_LENGTH))
                                                         .emailAddress(randomEmailAddress(MAX_EMAIL_ADDRESS_LENGTH))
                                                         .password(randomString(100))
                                                         .organisationName(randomString(MAX_ORGANISATION_NAME_LENGTH))
                                                         .build();
        // @formatter:on
        restTemplate.postForEntity(registrationUri, request, RegistrationResponse.class).getBody();
    }

    @Test(expected = HttpClientErrorException.class)
    public void testRegistration_duplicate_username() {
        // @formatter:off
        RegistrationRequest request = RegistrationRequest.builder()
                                                         .fullName(randomString(MAX_FULL_NAME_LENGTH))
                                                         .username("user")
                                                         .emailAddress(randomEmailAddress(MAX_EMAIL_ADDRESS_LENGTH))
                                                         .password(randomString(100))
                                                         .organisationName(randomString(MAX_ORGANISATION_NAME_LENGTH))
                                                         .build();
        // @formatter:on
        restTemplate.postForEntity(registrationUri, request, RegistrationResponse.class).getBody();
    }

    @Test(expected = HttpClientErrorException.class)
    public void testRegistration_duplicate_emailAddress() {
        // @formatter:off
        RegistrationRequest request = RegistrationRequest.builder()
                                                         .fullName(randomString(MAX_FULL_NAME_LENGTH))
                                                         .username(randomString(MAX_USER_NAME_LENGTH))
                                                         .emailAddress("user@demo.com")
                                                         .password(randomString(100))
                                                         .organisationName(randomString(MAX_ORGANISATION_NAME_LENGTH))
                                                         .build();
        // @formatter:on
        restTemplate.postForEntity(registrationUri, request, RegistrationResponse.class).getBody();
    }

    private String randomString(int length) {
        char s[] = new char[length];
        char min = 'a';
        int bound = 'z' - min;
        for (int i = 0; i < length; i++) {
            s[i] = (char) (random.nextInt(bound) + min);
        }
        String result = new String(s);
        assertThat(result.length(), is(length));
        return result;
    }

    private String randomEmailAddress(int length) {
        int boxLength = length / 2;
        int atLength = 1;
        int tldLength = 4;
        int domainLength = length - boxLength - atLength - tldLength;
        return randomString(boxLength) + "@" + randomString(domainLength) + ".com";
    }
}