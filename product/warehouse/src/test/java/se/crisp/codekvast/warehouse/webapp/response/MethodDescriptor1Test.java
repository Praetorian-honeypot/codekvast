package se.crisp.codekvast.warehouse.webapp.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import se.crisp.codekvast.warehouse.webapp.model.ApplicationDescriptor1;
import se.crisp.codekvast.warehouse.webapp.model.EnvironmentDescriptor1;
import se.crisp.codekvast.warehouse.webapp.model.MethodDescriptor1;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static se.crisp.codekvast.agent.lib.model.v1.SignatureStatus.*;

/**
 * @author olle.hallin@crisp.se
 */
public class MethodDescriptor1Test {

    private final long days = 24 * 60 * 60 * 1000L;
    private final long now = System.currentTimeMillis();

    private final long twoDaysAgo = now - 2 * days;
    private final long fourteenDaysAgo = now - 14 * days;
    private final long never = 0L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void should_calculate_min_max_correctly() throws Exception {
        // given
        MethodDescriptor1 md = buildMethodDescriptor(fourteenDaysAgo, twoDaysAgo, never, twoDaysAgo);

        // when

        // then
        assertThat(toDaysAgo(md.getCollectedSinceMillis()), is(toDaysAgo(fourteenDaysAgo)));
        assertThat(toDaysAgo(md.getCollectedToMillis()), is(toDaysAgo(twoDaysAgo)));
        assertThat(md.getCollectedDays(), is(12));
        assertThat(toDaysAgo(md.getLastInvokedAtMillis()), is(toDaysAgo(twoDaysAgo)));

        assertThat(md.getTrackedPercent(), is(67));
        assertThat(md.getStatuses(), containsInAnyOrder(EXACT_MATCH, NOT_INVOKED, EXCLUDED_BY_PACKAGE_NAME));
    }

    private int toDaysAgo(long timestamp) {
        return Math.toIntExact((now - timestamp) / days);
    }

    @Test
    public void should_serializable_to_JSON() throws Exception {
        // given
        MethodDescriptor1 md = buildMethodDescriptor(fourteenDaysAgo, twoDaysAgo, never, twoDaysAgo);
        long lastInvokedAtMillis = md.getLastInvokedAtMillis();

        // when
        String json = objectMapper.writeValueAsString(md);

        // then
        assertThat(json, containsString("\"lastInvokedAtMillis\":" + lastInvokedAtMillis));

        System.out.println("json = " + objectMapper.writer()
                                                   .withDefaultPrettyPrinter()
                                                   .writeValueAsString(md));
    }

    private MethodDescriptor1 buildMethodDescriptor(long collectedSinceMillis, long collectedToMillis,
                                                    long invokedAtMillis1, long invokedAtMillis2) {
        return MethodDescriptor1.builder()
                                .id(1L)
                                .declaringType("declaringType")
                                .modifiers("")
                                .occursInApplication(
                                    ApplicationDescriptor1.builder()
                                                          .name("app1")
                                                          .version("1.1")
                                                          .status(EXCLUDED_BY_PACKAGE_NAME)
                                                          .startedAtMillis(collectedSinceMillis)
                                                          .dumpedAtMillis(collectedToMillis)
                                                          .invokedAtMillis(invokedAtMillis1)
                                                          .build())
                                .occursInApplication(
                                    ApplicationDescriptor1.builder()
                                                          .name("app1")
                                                          .version("1.2")
                                                          .status(NOT_INVOKED)
                                                          .startedAtMillis(collectedSinceMillis + 10)
                                                          .dumpedAtMillis(collectedToMillis - 10)
                                                          .invokedAtMillis(invokedAtMillis1 - 10)
                                                          .build())
                                .occursInApplication(
                                    ApplicationDescriptor1.builder()
                                                          .name("app1")
                                                          .version("1.3")
                                                          .status(EXACT_MATCH)
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
                                .signature("signature")
                                .visibility("public")
                                .build();
    }

}
