package se.crisp.codekvast.warehouse.api.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import se.crisp.codekvast.agent.lib.model.v1.SignatureStatus;
import se.crisp.codekvast.warehouse.api.model.ApplicationDescriptor1;
import se.crisp.codekvast.warehouse.api.model.EnvironmentDescriptor1;
import se.crisp.codekvast.warehouse.api.model.MethodDescriptor1;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author olle.hallin@crisp.se
 */
public class MethodDescriptor1Test {

    private final long days = 24 * 60 * 60 * 1000L;
    private final long now = System.currentTimeMillis();

    private final long oneDayAgo = now - days;
    private final long twoDaysAgo = now - 2 * days;
    private final long fourteenDaysAgo = now - 14 * days;
    private final long fifteenDaysAgo = now - 15 * days;
    private final long never = 0L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void should_calculate_min_max_correctly() throws Exception {
        // given
        MethodDescriptor1 md = buildMethodDescriptor(1L, "signature", fourteenDaysAgo, twoDaysAgo, never, twoDaysAgo);

        // when

        // then
        assertThat(toDaysAgo(md.getCollectedSinceMillis()), is(toDaysAgo(fourteenDaysAgo)));
        assertThat(toDaysAgo(md.getCollectedToMillis()), is(toDaysAgo(twoDaysAgo)));
        assertThat(md.getCollectedDays(), is(12));
        assertThat(toDaysAgo(md.getLastInvokedAtMillis()), is(toDaysAgo(twoDaysAgo)));
    }

    private int toDaysAgo(long timestamp) {
        return Math.toIntExact((now - timestamp) / days);
    }

    @Test
    public void should_serializable_to_JSON() throws Exception {
        // given
        MethodDescriptor1 md = buildMethodDescriptor(1L, "signature", fourteenDaysAgo, twoDaysAgo, never, twoDaysAgo);
        long lastInvokedAtMillis = md.getLastInvokedAtMillis();

        // when
        String json = objectMapper.writeValueAsString(md);

        // then
        assertThat(json, containsString("\"lastInvokedAtMillis\":" + lastInvokedAtMillis));

        System.out.println("json = " + objectMapper.writer()
                                                   .withDefaultPrettyPrinter()
                                                   .writeValueAsString(md));
    }

    private MethodDescriptor1 buildMethodDescriptor(long methodId, String signature, long collectedSinceMillis, long collectedToMillis,
                                                    long invokedAtMillis1, long invokedAtMillis2) {
        return MethodDescriptor1.builder()
                                .id(methodId)
                                .declaringType("declaringType")
                                .modifiers("")
                                .occursInApplication(
                                        ApplicationDescriptor1.builder()
                                                              .name("app1")
                                                              .version("1.2")
                                                              .status(SignatureStatus
                                                                              .EXCLUDED_BY_PACKAGE_NAME)
                                                              .startedAtMillis(collectedSinceMillis)
                                                              .dumpedAtMillis(collectedToMillis)
                                                              .invokedAtMillis(invokedAtMillis1)
                                                              .build())
                                .occursInApplication(
                                        ApplicationDescriptor1.builder()
                                                              .name("app1")
                                                              .version("1.3")
                                                              .status(SignatureStatus.EXACT_MATCH)
                                                              .startedAtMillis(collectedSinceMillis)
                                                              .dumpedAtMillis(collectedToMillis)
                                                              .invokedAtMillis(invokedAtMillis2)
                                                              .build())
                                .collectedInEnvironment(
                                        EnvironmentDescriptor1.builder()
                                                              .name("test")
                                                              .collectedSinceMillis(collectedSinceMillis)
                                                              .collectedToMillis(collectedToMillis)
                                                              .invokedAtMillis(invokedAtMillis2)
                                                              .tag("tag2=2")
                                                              .tag("tag1=1")
                                                              .build())
                                .collectedInEnvironment(
                                        EnvironmentDescriptor1.builder()
                                                              .name("customer1")
                                                              .collectedSinceMillis(collectedSinceMillis)
                                                              .collectedToMillis(collectedToMillis)
                                                              .invokedAtMillis(invokedAtMillis2)
                                                              .hostName("server1.customer1.com")
                                                              .hostName("server2.customer1.com")
                                                              .tag("foo=1")
                                                              .tag("bar=2")
                                                              .tag("baz")
                                                              .build())
                                .packageName("packageName")
                                .signature(signature)
                                .visibility("public")
                                .build();
    }

}
