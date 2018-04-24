/*
 * Copyright (c) 2015-2018 Hallin Information Technology AB
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
package io.codekvast.dashboard.bootstrap;

import com.amazonaws.SDKGlobalConfiguration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

/**
 * {@link ConfigurationProperties} for configuring CloudWatch metrics export.
 *
 */
@Component
@ConfigurationProperties(prefix = "management.metrics.export.cloudwatch")
@Data
public class CloudWatchProperties {

	/**
	 * Step size (i.e. reporting frequency) to use.
	 */
	private Duration step = Duration.ofMinutes(1);

    private String namespace;
    private boolean enabled;
    private int batchSize = 20;
    private String awsAccessKeyId;
    private String awsSecretKey;
    private String awsRegion;

    /**
     * Publish AWS credentials as system properties (if present) so that AmazonCloudWatchAsyncClient can find them.
     */
    @PostConstruct
    public void setAwsSystemPropertiesIfPresent() {
        if (awsAccessKeyId != null && !awsAccessKeyId.trim().isEmpty()) {
            System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, awsAccessKeyId);
        }
        if (awsSecretKey != null && !awsSecretKey.trim().isEmpty()) {
            System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, awsSecretKey);
        }
    }
}
