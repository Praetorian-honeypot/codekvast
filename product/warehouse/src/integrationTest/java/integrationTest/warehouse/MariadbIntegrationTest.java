package integrationTest.warehouse;

import integrationTest.warehouse.testdata.TestDataGenerator;
import io.codekvast.javaagent.model.v1.SignatureStatus;
import io.codekvast.javaagent.model.v1.rest.GetConfigRequest1;
import io.codekvast.javaagent.model.v1.rest.GetConfigResponse1;
import io.codekvast.testsupport.docker.DockerContainer;
import io.codekvast.testsupport.docker.MariaDbContainerReadyChecker;
import io.codekvast.warehouse.CodekvastWarehouse;
import io.codekvast.warehouse.agent.AgentService;
import io.codekvast.warehouse.customer.*;
import io.codekvast.warehouse.customer.CustomerService.InteractiveActivity;
import io.codekvast.warehouse.customer.CustomerService.LoginRequest;
import io.codekvast.warehouse.webapp.WebappService;
import io.codekvast.warehouse.webapp.model.methods.GetMethodsRequest1;
import io.codekvast.warehouse.webapp.model.methods.GetMethodsResponse1;
import io.codekvast.warehouse.webapp.model.methods.MethodDescriptor1;
import io.codekvast.warehouse.webapp.model.status.AgentDescriptor1;
import io.codekvast.warehouse.webapp.model.status.GetStatusResponse1;
import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.validation.ConstraintViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author olle.hallin@crisp.se
 */
@SuppressWarnings("SpringAutowiredFieldsWarningInspection")
@SpringBootTest(
    classes = {CodekvastWarehouse.class, TestDataGenerator.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integrationTest")
@Transactional(rollbackFor = Exception.class)
public class MariadbIntegrationTest {

    private final long now = System.currentTimeMillis();

    private static final int PORT = 3306;
    private static final String DATABASE = "codekvast";
    private static final String USERNAME = "codekvast";
    private static final String PASSWORD = "codekvast";

    @ClassRule
    public static DockerContainer mariadb = DockerContainer
        .builder()
        .imageName("mariadb:10")
        .port("" + PORT)

        .env("MYSQL_ROOT_PASSWORD=root")
        .env("MYSQL_DATABASE=" + DATABASE)
        .env("MYSQL_USER=" + USERNAME)
        .env("MYSQL_PASSWORD=" + PASSWORD)

        .readyChecker(
            MariaDbContainerReadyChecker.builder()
                                        .host("localhost")
                                        .internalPort(PORT)
                                        .database(DATABASE)
                                        .username(USERNAME)
                                        .password(PASSWORD)
                                        .timeoutSeconds(120)
                                        .assignJdbcUrlToSystemProperty("spring.datasource.url")
                                        .build())
        .build();

    @ClassRule
    public static final SpringClassRule springClassRule = new SpringClassRule();

    @Rule
    public SpringMethodRule springMethodRule = new SpringMethodRule();

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    private Flyway flyway;


    @Inject
    private CustomerService customerService;

    @Inject
    private WebappService webappService;

    @Inject
    private AgentService agentService;

    @Inject
    private TestDataGenerator testDataGenerator;

    @Before
    public void beforeTest() throws Exception {
        assumeTrue(mariadb.isRunning());
    }

    @Test
    public void should_have_applied_all_flyway_migrations_to_an_empty_database() throws Exception {
        // given

        // when

        // then
        assertThat("Wrong number of pending Flyway migrations", flyway.info().pending().length, is(0));
        assertThat(countRowsInTable("schema_version WHERE success != 1"), is(0));
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_store_all_signature_status_enum_values_correctly() throws Exception {
        // given

        // when
        int methodId = 0;
        for (SignatureStatus status : SignatureStatus.values()) {
            methodId += 1;
            jdbcTemplate.update("INSERT INTO invocations(customerId, applicationId, methodId, jvmId, invokedAtMillis, " +
                                    "invocationCount, status) VALUES(1, 11, ?, 1, ?, 0, ?)",
                                methodId, now, status.toString());
        }

        // then
        assertThat("Wrong number of invocations rows", countRowsInTable("invocations"), is(SignatureStatus.values().length));
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_accept_valid_getCustomerDataByCustomerId() {
        CustomerData customerData = customerService.getCustomerDataByCustomerId(1L);
        assertThat(customerData.getCustomerId(), is(1L));
        assertThat(customerData.getCustomerName(), is("Demo"));
        assertThat(customerData.getPricePlan().getName(), is("demo"));
    }

    @Test(expected = AuthenticationCredentialsNotFoundException.class)
    @Sql(scripts = "/sql/base-data.sql")
    public void should_reject_invalid_getCustomerDataByCustomerId() {
        CustomerData customerData = customerService.getCustomerDataByCustomerId(0L);
        assertThat(customerData.getCustomerId(), is(1L));
        assertThat(customerData.getCustomerName(), is("Demo"));
        assertThat(customerData.getPricePlan().getName(), is("demo"));
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_accept_valid_getCustomerDataByLicenseKey() {
        CustomerData customerData = customerService.getCustomerDataByLicenseKey("");
        assertThat(customerData.getCustomerId(), is(1L));
        assertThat(customerData.getCustomerName(), is("Demo"));
        assertThat(customerData.getPricePlan().getName(), is("demo"));
    }

    @Test(expected = AuthenticationCredentialsNotFoundException.class)
    @Sql(scripts = "/sql/base-data.sql")
    public void should_reject_invalid_getCustomerDataByLicenseKey() {
        CustomerData customerData = customerService.getCustomerDataByLicenseKey("undefined");
        assertThat(customerData.getCustomerId(), is(1L));
        assertThat(customerData.getCustomerName(), is("Demo"));
        assertThat(customerData.getPricePlan().getName(), is("demo"));
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_accept_valid_getCustomerDataByExternalId() {
        CustomerData customerData = customerService.getCustomerDataByExternalId("external-1");
        assertThat(customerData.getCustomerId(), is(1L));
        assertThat(customerData.getCustomerName(), is("Demo"));
        assertThat(customerData.getPricePlan().getName(), is("demo"));
    }

    @Test(expected = AuthenticationCredentialsNotFoundException.class)
    @Sql(scripts = "/sql/base-data.sql")
    public void should_reject_invalid_getCustomerDataByExternalId() {
        CustomerData customerData = customerService.getCustomerDataByExternalId("undefined");
        assertThat(customerData.getCustomerId(), is(1L));
        assertThat(customerData.getCustomerName(), is("Demo"));
        assertThat(customerData.getPricePlan().getName(), is("demo"));
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_handle_add_delete_customer() {
        String licenseKey = customerService.addCustomer(CustomerService.AddCustomerRequest
                                                            .builder()
                                                            .source("test")
                                                            .externalId("externalId")
                                                            .name("customerName")
                                                            .plan("test")
                                                            .build());

        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM customers WHERE externalId = ?", Long.class, "externalId");

        assertThat(count, is(1L));

        assertThat(licenseKey, notNullValue());

        customerService.deleteCustomerByExternalId("externalId");

        count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM customers WHERE externalId = ?", Long.class, "externalId");
        assertThat(count, is(0L));
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_handle_delete_customer() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM customers", Long.class);
        assertThat(count, is(1L));

        customerService.deleteCustomerByExternalId("external-1");

        count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM customers", Long.class);
        assertThat(count, is(0L));
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_handle_register_login_twice() {

        // given
        jdbcTemplate.update("DELETE FROM users");

        // when
        customerService.registerLogin(LoginRequest.builder()
                                                  .customerId(1L)
                                                  .source("source1")
                                                  .email("email")
                                                  .build());

        customerService.registerLogin(LoginRequest.builder()
                                                  .customerId(1L)
                                                  .source("source2")
                                                  .email("email")
                                                  .build());

        // then
        assertThat(countRowsInTable("users"), is(1));
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_register_interactive_activity() {

        // given
        jdbcTemplate.update("DELETE FROM users");

        // when
        customerService.registerLogin(LoginRequest.builder()
                                                  .customerId(1L)
                                                  .source("source1")
                                                  .email("email")
                                                  .build());

        customerService.registerInteractiveActivity(InteractiveActivity.builder()
                                                                       .customerId(1L)
                                                                       .email("email")
                                                                       .build());
        // then
        assertThat(countRowsInTable("users"), is(1));
    }

    @Test(expected = AuthenticationCredentialsNotFoundException.class)
    @Sql(scripts = "/sql/base-data.sql")
    public void should_reject_publication_invalid_licenseKey() {
        customerService.assertPublicationSize("undefined", 10);
    }

    @Test(expected = LicenseViolationException.class)
    @Sql(scripts = "/sql/base-data.sql")
    public void should_reject_publication_too_large() {
        customerService.assertPublicationSize("", 100_000);
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_accept_publication() {
        customerService.assertPublicationSize("", 10);
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_assertDatabaseSize() {
        customerService.assertDatabaseSize(1L);
    }

    // TODO: add tests for CodeBasePublication import

    // TODO: add tests for InvocationDataPublication import

    @Test
    public void should_query_by_IDEA_signature_correctly() {
        // given

        // when

        // then
    }

    @Test
    public void should_query_by_signature_suffix_correctly() {
        // given

        // when find substring

        // then
    }

    @Test
    public void should_query_by_signature_not_normalize_but_no_match() {
        // given

        // when find by signature

        // then
    }

    @Test
    public void should_query_signatures_and_respect_max_results() {
        // given

        // when

        // then
    }

    @Test(expected = ConstraintViolationException.class)
    public void should_throw_when_querying_signature_with_too_short_signature() {
        // given

        // when query with too short signature
        webappService.getMethods(GetMethodsRequest1.defaults().toBuilder().signature("").build());
    }

    @Test
    public void should_query_unknown_signature_correctly() {
        // given

        // when find exact signature
        GetMethodsResponse1 response = webappService.getMethods(
            GetMethodsRequest1.defaults().toBuilder().signature("foobar").build());

        // then
        assertThat(response.getMethods(), hasSize(0));
    }

    @Test
    public void should_query_by_known_id() {
        // given
        // generateQueryTestData();

        // List<Long> validIds = jdbcTemplate.query("SELECT id FROM methods", (rs, rowNum) -> rs.getLong(1));

        // when
        // Optional<MethodDescriptor1> result = webappService.getMethodById(validIds.get(0));

        // then
        // assertThat(result.isPresent(), is(true));
    }

    @Test
    public void should_query_by_unknown_id() {
        // given
        // generateQueryTestData();

        // when
        Optional<MethodDescriptor1> result = webappService.getMethodById(-1L);

        // then
        assertThat(result.isPresent(), is(false));
    }


    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_getConfig_for_enabled_agent() {
        // given
        new Timestamps().invoke();

        // when
        GetConfigResponse1 response = agentService.getConfig(
            GetConfigRequest1.sample().toBuilder()
                             .jvmUuid("uuid1")
                             .licenseKey("")
                             .startedAtMillis(Instant.now().minus(2, HOURS).toEpochMilli())
                             .build());

        // then
        assertConfigPollResponse(response, "enabled=true");

        // Assert all dead agents are marked as disabled as well
        assertAgentEnabled("uuid1", TRUE);
        assertAgentEnabled("uuid2", FALSE);
        assertAgentEnabled("uuid3", FALSE);
        assertAgentEnabled("uuid4", FALSE);
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_getConfig_for_disabled_agent() {
        // given
        new Timestamps().invoke();

        // when
        GetConfigResponse1 response = agentService.getConfig(
            GetConfigRequest1.sample().toBuilder()
                             .jvmUuid("uuid2")
                             .licenseKey("")
                             .startedAtMillis(Instant.now().minus(2, HOURS).toEpochMilli())
                             .build());

        // then
        assertConfigPollResponse(response, "enabled=false");

        assertAgentEnabled("uuid1", TRUE);
        assertAgentEnabled("uuid2", FALSE);
        assertAgentEnabled("uuid3", FALSE);
        assertAgentEnabled("uuid4", FALSE);
    }

    @Test
    @Sql(scripts = "/sql/base-data.sql")
    public void should_getStatus_correctly() {
        // given
        Timestamps timestamps = new Timestamps().invoke();

        // when
        GetStatusResponse1 status = webappService.getStatus();

        // then
        assertThat(status.getPricePlan(), is("DEMO"));
        assertThat(status.getCollectionResolutionSeconds(), is(PricePlanDefaults.DEMO.getPublishIntervalSeconds()));
        assertThat(status.getMaxNumberOfAgents(), is(PricePlanDefaults.DEMO.getMaxNumberOfAgents()));
        assertThat(status.getMaxCollectionPeriodDays(), is(PricePlanDefaults.DEMO.getMaxCollectionPeriodDays()));
        assertThat(status.getMaxNumberOfMethods(), is(PricePlanDefaults.DEMO.getMaxMethods()));

        assertThat(status.getNumAgents(), is(4));
        assertThat(status.getNumLiveAgents(), is(2));
        assertThat(status.getNumLiveEnabledAgents(), is(1));

        assertThat(status.getAgents().get(0), is(AgentDescriptor1.builder()
                                                                 .agentAlive(true)
                                                                 .agentLiveAndEnabled(true)
                                                                 .agentVersion("agentVersion1")
                                                                 .appName("app1")
                                                                 .appVersion("v1")
                                                                 .environment("env1")
                                                                 .excludePackages("com.foobar.excluded1")
                                                                 .id(1L)
                                                                 .methodVisibility("public")
                                                                 .nextPollExpectedAtMillis(cutMillis(timestamps.plusOneMinute))
                                                                 .nextPublicationExpectedAtMillis(cutMillis(
                                                                     Timestamp.from(timestamps.minusTenMinutes.toInstant().plusSeconds(
                                                                         PricePlanDefaults.DEMO.getPublishIntervalSeconds()))))
                                                                 .packages("com.foobar1")
                                                                 .pollReceivedAtMillis(cutMillis(timestamps.minusTenMinutes))
                                                                 .publishedAtMillis(cutMillis(timestamps.minusTwoMinutes))
                                                                 .startedAtMillis(cutMillis(timestamps.minusThreeDaysPlus))
                                                                 .tags("tag1=t1,tag2=t2")
                                                                 .build()));

        assertThat(status.getNumMethods(), is(10));

        assertThat(status.getCollectedSinceMillis(), is(cutMillis(timestamps.minusThreeDaysPlus)));
        assertThat(status.getCollectedDays(), is(3));

        assertThat(status.getUsers(), hasSize(2));
    }

    private void assertAgentEnabled(String jvmUuid, Boolean expectedEnabled) {
        Boolean enabled = jdbcTemplate.queryForObject("SELECT enabled FROM agent_state WHERE jvmUuid = ? ", Boolean.class, jvmUuid);
        assertThat(enabled, is(expectedEnabled));
    }

    private void assertConfigPollResponse(GetConfigResponse1 response, String publisherConfig) {
        PricePlanDefaults pp = PricePlanDefaults.DEMO;
        assertThat(response, is(GetConfigResponse1.sample().toBuilder()
                                                  .codeBasePublisherCheckIntervalSeconds(pp.getPublishIntervalSeconds())
                                                  .codeBasePublisherConfig(publisherConfig)
                                                  .codeBasePublisherName("http")
                                                  .codeBasePublisherRetryIntervalSeconds(pp.getRetryIntervalSeconds())
                                                  .configPollIntervalSeconds(pp.getPollIntervalSeconds())
                                                  .configPollRetryIntervalSeconds(pp.getRetryIntervalSeconds())
                                                  .invocationDataPublisherConfig(publisherConfig)
                                                  .invocationDataPublisherIntervalSeconds(pp.getPublishIntervalSeconds())
                                                  .invocationDataPublisherName("http")
                                                  .invocationDataPublisherRetryIntervalSeconds(pp.getRetryIntervalSeconds())
                                                  .build()));
    }

    private long cutMillis(Timestamp timestamp) {
        return Instant.ofEpochMilli(timestamp.getTime()).getEpochSecond() * 1000L;
    }

    private int countRowsInTable(String tableName) {
        return JdbcTestUtils.countRowsInTable(jdbcTemplate, tableName);
    }

    private class Timestamps {
        Timestamp minusThreeDaysPlus;
        Timestamp minusTenMinutes;
        Timestamp minusTwoMinutes;
        Timestamp plusOneMinute;

        Timestamps invoke() {
            // Set the timestamps from Java. It's impossible to write time-zone agnostic code in a static sql script invoked by @Sql.

            Instant now = Instant.now();
            minusThreeDaysPlus = Timestamp.from(now.minus(3, DAYS).minus(5, HOURS));
            minusTenMinutes = Timestamp.from(now.minus(10, MINUTES));
            minusTwoMinutes = Timestamp.from(now.minus(2, MINUTES));
            plusOneMinute = Timestamp.from(now.plus(1, MINUTES));

            jdbcTemplate.update("UPDATE agent_state SET lastPolledAt = ?, nextPollExpectedAt = ?, enabled = ? WHERE jvmUuid = ? ",
                                minusTenMinutes, plusOneMinute, TRUE, "uuid1");

            jdbcTemplate.update("UPDATE agent_state SET lastPolledAt = ?, nextPollExpectedAt = ?, enabled = ? WHERE jvmUuid = ? ",
                                minusTenMinutes, plusOneMinute, FALSE, "uuid2");

            jdbcTemplate.update("UPDATE agent_state SET lastPolledAt = ?, nextPollExpectedAt = ?, enabled = ? WHERE jvmUuid = ? ",
                                minusTenMinutes, minusTwoMinutes, TRUE, "uuid3");

            jdbcTemplate.update("UPDATE agent_state SET lastPolledAt = ?, nextPollExpectedAt = ?, enabled = ? WHERE jvmUuid = ? ",
                                minusTenMinutes, minusTwoMinutes, FALSE, "uuid4");

            jdbcTemplate.update("UPDATE jvms SET startedAt = ?, publishedAt = ?", minusTenMinutes, minusTwoMinutes);
            jdbcTemplate.update("UPDATE jvms SET startedAt = ? WHERE uuid = ?", minusThreeDaysPlus, "uuid1");
            return this;
        }
    }
}
