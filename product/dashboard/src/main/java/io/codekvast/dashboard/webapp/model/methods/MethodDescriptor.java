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
package io.codekvast.dashboard.webapp.model.methods;

import io.codekvast.javaagent.model.v2.SignatureStatus2;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author olle.hallin@crisp.se
 */
@Value
@Builder(toBuilder = true)
public class MethodDescriptor {
    @NonNull
    private final Long id;

    @NonNull
    private final String signature;

    /**
     * public, protected, package-private or private
     */
    @NonNull
    private final String visibility;

    /**
     * static, final, etc
     */
    private final String modifiers;

    private final Boolean bridge;

    private final Boolean synthetic;

    private final String packageName;

    private final String declaringType;

    @Singular
    private final SortedSet<ApplicationDescriptor> occursInApplications;

    @Singular
    private final SortedSet<EnvironmentDescriptor> collectedInEnvironments;

    /**
     * Calculates in how many apps this method is tracked.
     * @return A number in the range [0..100]
     */
    public int getTrackedPercent() {
        long tracked = occursInApplications.stream().map(ApplicationDescriptor::getStatus).filter(SignatureStatus2::isTracked).count();
        return (int) Math.round (tracked * 100D / occursInApplications.size());
    }

    /**
     * @return The set of signature statuses this method has across all applications.
     */
    public Set<SignatureStatus2> getStatuses() {
        return occursInApplications.stream().map(ApplicationDescriptor::getStatus).collect(Collectors.toSet());
    }

    /**
     * @return The maximum value of occursInApplications.invokedAtMillis;
     */
    public Long getLastInvokedAtMillis() {
        return occursInApplications.stream().map(ApplicationDescriptor::getInvokedAtMillis).reduce(Math::max).orElse(0L);
    }

    /**
     * @return The minimum value of occursInApplications.startedAtMillis
     */
    public Long getCollectedSinceMillis() {
        return occursInApplications.stream().map(ApplicationDescriptor::getStartedAtMillis).reduce(Math::min).orElse(0L);
    }

    /**
     * @return The maximum value of occursInApplications.getPublishedAtMillis
     */
    public Long getCollectedToMillis() {
        return occursInApplications.stream().map(ApplicationDescriptor::getPublishedAtMillis).reduce(Math::max).orElse(0L);
    }

    /**
     * @return The difference between {@link #getCollectedToMillis()} and {@link #getCollectedSinceMillis()} expressed as days.
     */
    @SuppressWarnings("unused")
    public int getCollectedDays() {
        int dayInMillis = 24 * 60 * 60 * 1000;
        return Math.toIntExact((getCollectedToMillis() - getCollectedSinceMillis()) / dayInMillis);
    }

    /**
     * @return The union of tags from all environments
     */
    @SuppressWarnings("unused")
    public Set<String> getTags() {
        Set<String> result = new TreeSet<>();
        collectedInEnvironments.stream().map(EnvironmentDescriptor::getTags).forEach(result::addAll);
        return result;
    }
}
