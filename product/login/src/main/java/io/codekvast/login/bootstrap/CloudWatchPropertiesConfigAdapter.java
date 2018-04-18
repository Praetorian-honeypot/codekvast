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
package io.codekvast.login.bootstrap;

import io.micrometer.cloudwatch.CloudWatchConfig;
import org.springframework.boot.actuate.autoconfigure.metrics.export.properties.PropertiesConfigAdapter;

import java.time.Duration;

/**
 * Adapter to convert {@link CloudWatchProperties} to a {@link CloudWatchConfig}.
 *
 * @author Jon Schneider
 */
class CloudWatchPropertiesConfigAdapter extends PropertiesConfigAdapter<CloudWatchProperties>
		implements CloudWatchConfig {

	CloudWatchPropertiesConfigAdapter(CloudWatchProperties properties) {
		super(properties);
	}

	@Override
	public String get(String key) {
        return null;
	}

	@Override
	public String namespace() {
        return super.get(CloudWatchProperties::getNamespace, CloudWatchConfig.super::namespace);
	}

	@Override
	public boolean enabled() {
        return super.get(CloudWatchProperties::isEnabled, CloudWatchConfig.super::enabled);
	}

	@Override
	public Duration step() {
		return get(CloudWatchProperties::getStep, CloudWatchConfig.super::step);
	}
}
