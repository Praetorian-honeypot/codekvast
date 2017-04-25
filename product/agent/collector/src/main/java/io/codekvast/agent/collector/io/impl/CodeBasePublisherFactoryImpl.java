/*
 * Copyright (c) 2015-2017 Crisp AB
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
package io.codekvast.agent.collector.io.impl;

import io.codekvast.agent.collector.io.CodeBasePublisher;
import io.codekvast.agent.collector.io.CodeBasePublisherFactory;
import io.codekvast.agent.collector.io.impl.NoOpCodeBasePublisherImpl;
import io.codekvast.agent.lib.config.CollectorConfig;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for CodeBasePublisher implementations.
 *
 * @author olle.hallin@crisp.se
 */
@Slf4j
public class CodeBasePublisherFactoryImpl implements CodeBasePublisherFactory {

    /**
     * Creates an instance of the CodeBasePublisher strategy.
     *
     * @param name   The name of the strategy to create.
     * @param config Is passed to the created strategy.
     * @return A configured implementation of CodeBasePublisher
     */
    @Override
    public CodeBasePublisher create(String name, CollectorConfig config) {
        if (name.equals(NoOpCodeBasePublisherImpl.NAME)) {
            return new NoOpCodeBasePublisherImpl(config);
        }

        log.warn("Unrecognized code base publisher name: '{}', will use {}", name, NoOpCodeBasePublisherImpl.NAME);
        return new NoOpCodeBasePublisherImpl(config);
    }

}
