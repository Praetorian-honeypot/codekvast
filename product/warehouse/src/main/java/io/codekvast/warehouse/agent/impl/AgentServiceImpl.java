/*
 * Copyright (c) 2015-2017 Hallin Information Technology AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.codekvast.warehouse.agent.impl;

import io.codekvast.javaagent.model.v1.rest.GetConfigRequest1;
import io.codekvast.javaagent.model.v1.rest.GetConfigResponse1;
import io.codekvast.warehouse.agent.AgentService;
import io.codekvast.warehouse.bootstrap.CodekvastSettings;
import io.codekvast.warehouse.customer.CustomerData;
import io.codekvast.warehouse.customer.CustomerService;
import io.codekvast.warehouse.customer.LicenseViolationException;
import io.codekvast.warehouse.customer.PricePlan;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Timestamp;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Handler for javaagent REST requests.
 *
 * @author olle.hallin@crisp.se
 */
@Service
@Slf4j
public class AgentServiceImpl implements AgentService {

    private final CodekvastSettings settings;
    private final JdbcTemplate jdbcTemplate;
    private final CustomerService customerService;

    @Inject
    public AgentServiceImpl(CodekvastSettings settings, JdbcTemplate jdbcTemplate,
                            CustomerService customerService) {
        this.settings = settings;
        this.jdbcTemplate = jdbcTemplate;
        this.customerService = customerService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GetConfigResponse1 getConfig(GetConfigRequest1 request) throws LicenseViolationException {
        CustomerData customerData = customerService.getCustomerDataByLicenseKey(request.getLicenseKey());
        boolean isAgentEnabled = updateAgentState(customerData, request.getJvmUuid());
        String publisherConfig = isAgentEnabled ? "enabled=true" : "enabled=false";
        PricePlan pp = customerData.getPricePlan();
        return GetConfigResponse1
            .builder()
            .codeBasePublisherCheckIntervalSeconds(pp.getPublishIntervalSeconds())
            .codeBasePublisherConfig(publisherConfig)
            .codeBasePublisherName("http")
            .codeBasePublisherRetryIntervalSeconds(pp.getRetryIntervalSeconds())
            .configPollIntervalSeconds(pp.getPollIntervalSeconds())
            .configPollRetryIntervalSeconds(pp.getRetryIntervalSeconds())
            .customerId(customerData.getCustomerId())
            .invocationDataPublisherConfig(publisherConfig)
            .invocationDataPublisherIntervalSeconds(pp.getPublishIntervalSeconds())
            .invocationDataPublisherName("http")
            .invocationDataPublisherRetryIntervalSeconds(pp.getRetryIntervalSeconds())
            .build();
    }

    private boolean updateAgentState(CustomerData customerData, String jvmUuid) {

        long customerId = customerData.getCustomerId();
        long now = System.currentTimeMillis();

        Timestamp nowTimestamp = new Timestamp(now);
        Timestamp nextExpectedPollTimestamp = new Timestamp(now + customerData.getPricePlan().getPollIntervalSeconds() * 1000L);

        int updated =
            jdbcTemplate.update("UPDATE agent_state SET lastPolledAt = ?, nextPollExpectedAt = ? WHERE customerId = ? AND jvmUuid = ?",
                                nowTimestamp, nextExpectedPollTimestamp, customerId, jvmUuid);
        if (updated == 0) {
            log.info("The agent {}:{} has started", customerId, jvmUuid);

            jdbcTemplate.update("INSERT INTO agent_state(customerId, jvmUuid, lastPolledAt, nextPollExpectedAt, enabled) VALUES (?, ?, ?, ?, ?)",
                                customerId, jvmUuid, nowTimestamp, nextExpectedPollTimestamp, Boolean.TRUE);
        } else {
            log.debug("The agent {}:{} has polled", customerId, jvmUuid);
        }

        Timestamp cutoffTimestamp = new Timestamp(now - 10_000L);
        Integer numLiveAgents = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM agent_state WHERE customerId = ? AND nextPollExpectedAt > ?",
                                                     Integer.class, customerId, cutoffTimestamp);

        String planName = customerData.getPlanName();
        int maxNumberOfAgents = customerData.getPricePlan().getMaxNumberOfAgents();
        boolean enabled = numLiveAgents <= maxNumberOfAgents;
        if (!enabled) {
            log.warn("Customer {} has {} live agents (max for price plan '{}' is {})", customerId, numLiveAgents, planName, maxNumberOfAgents);
        } else {
            log.debug("Customer {} has {} live agents (max for price plan '{}' is {})", customerId, numLiveAgents, planName, maxNumberOfAgents);
        }

        jdbcTemplate.update("UPDATE agent_state SET enabled = ? WHERE jvmUuid = ?", enabled, jvmUuid);
        return enabled;
    }

    @Override
    public File saveCodeBasePublication(@NonNull String licenseKey, String codeBaseFingerprint, int publicationSize, InputStream inputStream)
        throws LicenseViolationException, IOException {
        customerService.assertPublicationSize(licenseKey, publicationSize);

        return doSaveInputStream(inputStream, "codebase-");
    }

    @Override
    public File saveInvocationDataPublication(@NonNull String licenseKey, String codeBaseFingerprint, int publicationSize, InputStream inputStream)
        throws LicenseViolationException, IOException {
        customerService.assertPublicationSize(licenseKey, publicationSize);

        return doSaveInputStream(inputStream, "invocations-");
    }

    private File doSaveInputStream(InputStream inputStream, String prefix) throws IOException {
        createDirectory(settings.getQueuePath());

        File result = File.createTempFile(prefix, ".ser", settings.getQueuePath());
        Files.copy(inputStream, result.toPath(), REPLACE_EXISTING);

        log.debug("Saved uploaded publication to {}", result);
        return result;
    }

    private void createDirectory(File queuePath) throws IOException {
        if (!queuePath.isDirectory()) {
            log.debug("Creating {}", settings.getQueuePath());
            settings.getQueuePath().mkdirs();
            if (!settings.getQueuePath().isDirectory()) {
                throw new IOException("Could not create import directory");
            }
            log.info("Created {}", queuePath);
        }
    }

}
