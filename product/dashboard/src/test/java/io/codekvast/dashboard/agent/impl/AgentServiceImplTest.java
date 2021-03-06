package io.codekvast.dashboard.agent.impl;

import io.codekvast.common.customer.*;
import io.codekvast.dashboard.agent.AgentService;
import io.codekvast.dashboard.bootstrap.CodekvastDashboardSettings;
import io.codekvast.javaagent.model.v1.rest.GetConfigRequest1;
import io.codekvast.javaagent.model.v1.rest.GetConfigResponse1;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.Timestamp;
import java.time.Instant;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class AgentServiceImplTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private CustomerService customerService;

    private final CodekvastDashboardSettings settings = new CodekvastDashboardSettings();
    private final GetConfigRequest1 request = GetConfigRequest1.sample();

    private AgentService service;

    @Before
    public void beforeTest() {
        MockitoAnnotations.initMocks(this);

        settings.setQueuePath(temporaryFolder.getRoot());
        settings.setQueuePathPollIntervalSeconds(60);

        service = new AgentServiceImpl(settings, jdbcTemplate, customerService);

        setupCustomerData(null, null);
    }

    @Test
    public void should_return_enabled_publishers_when_below_agent_limit_no_trial_period() {
        // given
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyLong(), any(), anyString())).thenReturn(1);

        // when
        GetConfigResponse1 response = service.getConfig(request);

        // then
        assertThat(response.getCodeBasePublisherName(), is("http"));
        assertThat(response.getCodeBasePublisherConfig(), is("enabled=true"));

        assertThat(response.getInvocationDataPublisherName(), is("http"));
        assertThat(response.getInvocationDataPublisherConfig(), is("enabled=true"));
    }

    @Test
    public void should_return_enabled_publishers_when_below_agent_limit_within_trial_period() {
        // given
        Instant now = Instant.now();
        setupCustomerData(now.minus(10, DAYS), now.plus(10, DAYS));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyLong(), any(), anyString())).thenReturn(1);

        // when
        GetConfigResponse1 response = service.getConfig(request);

        // then
        assertThat(response.getCodeBasePublisherName(), is("http"));
        assertThat(response.getCodeBasePublisherConfig(), is("enabled=true"));

        assertThat(response.getInvocationDataPublisherName(), is("http"));
        assertThat(response.getInvocationDataPublisherConfig(), is("enabled=true"));
    }

    @Test
    public void should_return_disabled_publishers_when_below_agent_limit_after_trial_period_has_expired() {
        // given
        Instant now = Instant.now();
        setupCustomerData(now.minus(10, DAYS), now.minus(1, DAYS));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyLong(), any(), anyString())).thenReturn(1);

        // when
        GetConfigResponse1 response = service.getConfig(request);

        // then
        assertThat(response.getCodeBasePublisherName(), is("http"));
        assertThat(response.getCodeBasePublisherConfig(), is("enabled=false"));

        assertThat(response.getInvocationDataPublisherName(), is("http"));
        assertThat(response.getInvocationDataPublisherConfig(), is("enabled=false"));
    }

    @Test
    public void should_return_disabled_publishers_when_above_agent_limit_no_trial_period() {
        // given
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyLong(), any(), anyString())).thenReturn(10);

        // when
        GetConfigResponse1 response = service.getConfig(request);

        // then
        assertThat(response.getCodeBasePublisherName(), is("http"));
        assertThat(response.getCodeBasePublisherConfig(), is("enabled=false"));

        assertThat(response.getInvocationDataPublisherName(), is("http"));
        assertThat(response.getInvocationDataPublisherConfig(), is("enabled=false"));
    }

    @Test(expected = LicenseViolationException.class)
    public void should_have_checked_licenseKey() throws Exception {
        // given
        int publicationSize = 4711;
        doThrow(new LicenseViolationException("stub")).when(customerService).assertPublicationSize(anyString(), eq(publicationSize));

        // when
        service.savePublication(AgentService.PublicationType.CODEBASE, "key", publicationSize, null);
    }

    @Test
    public void should_save_uploaded_codebase_no_license() throws Exception {
        // given
        String contents = "Dummy Code Base Publication";

        // when
        File resultingFile = service.savePublication(AgentService.PublicationType.CODEBASE, "key", 1000,
                                                     new ByteArrayInputStream(contents.getBytes()));

        // then
        assertThat(resultingFile, notNullValue());
        assertThat(resultingFile.getName(), startsWith("codebase-"));
        assertThat(resultingFile.getName(), endsWith(".ser"));
        assertThat(resultingFile.exists(), is(true));
        assertThat(resultingFile.length(), is((long) contents.length()));
    }

    @Test
    public void should_save_uploaded_invocations_no_license() throws Exception {
        // given
        String contents = "Dummy Code Base Publication";

        // when
        File resultingFile = service.savePublication(AgentService.PublicationType.INVOCATIONS, "key", 1000,
                                                     new ByteArrayInputStream(contents.getBytes()));

        // then
        assertThat(resultingFile, notNullValue());
        assertThat(resultingFile.getName(), startsWith("invocations-"));
        assertThat(resultingFile.getName(), endsWith(".ser"));
        assertThat(resultingFile.exists(), is(true));
        assertThat(resultingFile.length(), is((long) contents.length()));
    }

    @Test(expected = NullPointerException.class)
    public void should_reject_null_licenseKey() throws Exception {
        service.savePublication(AgentService.PublicationType.CODEBASE, null, 0, null);
    }

    private void setupCustomerData(Instant collectionStartedAt, Instant trialPeriodEndsAt) {
        CustomerData customerData = CustomerData.builder()
                                                .customerId(1L)
                                                .customerName("name")
                                                .source("source")
                                                .pricePlan(PricePlan.of(PricePlanDefaults.TEST))
                                                .collectionStartedAt(collectionStartedAt)
                                                .trialPeriodEndsAt(trialPeriodEndsAt)
                                                .build();

        when(customerService.getCustomerDataByLicenseKey(anyString())).thenReturn(customerData);
        when(customerService.registerAgentDataPublication(any(CustomerData.class), any(Instant.class))).thenReturn(customerData);
    }

}