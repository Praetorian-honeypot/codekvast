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

import io.codekvast.agent.collector.io.CodekvastPublishingException;
import io.codekvast.agent.lib.config.CollectorConfig;
import io.codekvast.agent.lib.model.Endpoints;
import io.codekvast.agent.lib.model.v1.InvocationDataPublication;
import io.codekvast.agent.lib.util.Constants;
import io.codekvast.agent.lib.util.FileUtils;
import io.codekvast.agent.lib.util.LogUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * A HTTP implementation of InvocationDataPublisher.
 *
 * It uses the FileSystemInvocationDataPublisherImpl for creating a file, which then is POSTed to the server.
 *
 * @author olle.hallin@crisp.se
 */
@Slf4j
public class HttpInvocationDataPublisherImpl extends AbstractInvocationDataPublisher {

    public static final String NAME = "http";

    private static final MediaType APPLICATION_OCTET_STREAM = MediaType.parse("application/octet-stream");

    HttpInvocationDataPublisherImpl(CollectorConfig config) {
        super(log, config);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    boolean doSetValue(String key, String value) {
        return false;
    }

    @Override
    void doPublishInvocationData(long recordingIntervalStartedAtMillis, Set<String> invocations)
        throws CodekvastPublishingException {

        String url = getConfig().getInvocationDataUploadEndpoint();
        File file = null;
        try {
            file = FileUtils.serializeToFile(createPublication(recordingIntervalStartedAtMillis, invocations),
                                             getConfig().getFilenamePrefix("invocations-"), ".ser");

            doPost(file, url, getCodeBaseFingerprint().getSha256());

            log.debug("Uploaded {} of invocation data to {}", LogUtil.humanReadableByteCount(file.length()), url);
        } catch (Exception e) {
            throw new CodekvastPublishingException("Cannot upload invocation data to " + url, e);
        } finally {
            FileUtils.safeDelete(file);
        }

    }

    void doPost(File file, String url, String fingerprint) throws IOException {
        RequestBody requestBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(Endpoints.AGENT_V1_LICENSE_KEY_PARAM, getConfig().getLicenseKey())
            .addFormDataPart(Endpoints.AGENT_V1_FINGERPRINT_PARAM, fingerprint)
            .addFormDataPart(Endpoints.AGENT_V1_PUBLICATION_FILE_PARAM, file.getName(),
                             RequestBody.create(APPLICATION_OCTET_STREAM, file))
            .build();

        Request request = new Request.Builder().url(url).post(requestBody).build();
        Response response = executeRequest(request);

        if (!response.isSuccessful()) {
            throw new IOException(response.body().string());
        }
    }

    // Make it possible to subclass in tests
    Response executeRequest(Request request) throws IOException {
        return getConfig().getHttpClient().newCall(request).execute();
    }

    private InvocationDataPublication createPublication(long recordingIntervalStartedAtMillis, Set<String> invocations) {

        return InvocationDataPublication.builder()
                                        .appName(getConfig().getAppName())
                                        .appVersion(getConfig().getResolvedAppVersion())
                                        .codeBaseFingerprint(getCodeBaseFingerprint().getSha256())
                                        .collectorVersion(Constants.COLLECTOR_VERSION)
                                        .computerId(Constants.COMPUTER_ID)
                                        .environment(getConfig().getEnvironment())
                                        .hostName(Constants.HOST_NAME)
                                        .invocations(invocations)
                                        .jvmStartedAtMillis(Constants.JVM_STARTED_AT_MILLIS)
                                        .jvmUuid(Constants.JVM_UUID)
                                        .publicationCount(getPublicationCount())
                                        .publishedAtMillis(System.currentTimeMillis())
                                        .recordingIntervalStartedAtMillis(recordingIntervalStartedAtMillis)
                                        .tags(getConfig().getTags())
                                        .build();
    }

}
