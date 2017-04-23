package se.crisp.codekvast.warehouse.agent.impl;

import org.junit.Test;
import se.crisp.codekvast.agent.lib.model.rest.GetConfigRequest1;
import se.crisp.codekvast.agent.lib.model.rest.GetConfigResponse1;
import se.crisp.codekvast.warehouse.agent.AgentService;
import se.crisp.codekvast.warehouse.agent.LicenseViolationException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class AgentServiceImplTest {

    private final AgentService service = new AgentServiceImpl();
    private GetConfigRequest1 request = GetConfigRequest1.sample();

    @Test(expected = LicenseViolationException.class)
    public void should_throw_for_invalid_license_key() throws Exception {
        service.getConfig(request.toBuilder().licenseKey("-----").build());

    }

    @Test
    public void should_return_sensible_defaults() throws Exception {
        GetConfigResponse1 response = service.getConfig(request);
        assertThat(response.getCodeBasePublisherName(), is("no-op"));
        assertThat(response.getCodeBasePublisherCheckIntervalSeconds(), is(600));
        assertThat(response.getCodeBasePublisherRetryIntervalSeconds(), is(600));
        assertThat(response.isCodeBasePublishingNeeded(), is(true));
        assertThat(response.getCodeBasePublisherConfig(), notNullValue());
    }
}