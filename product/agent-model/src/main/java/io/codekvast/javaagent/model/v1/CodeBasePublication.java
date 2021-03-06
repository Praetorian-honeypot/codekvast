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
package io.codekvast.javaagent.model.v1;

import lombok.*;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * Output of the CodeBasePublisher implementations.
 *
 * @author olle.hallin@crisp.se
 * @deprecated Use {@link io.codekvast.javaagent.model.v2.CodeBasePublication2} instead.
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Setter(AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Deprecated
public class CodeBasePublication implements Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private CommonPublicationData commonData;

    @NonNull
    private Collection<CodeBaseEntry> entries;

    @NonNull
    private Map<String, String> overriddenSignatures;

    /**
     * "strange" signatures, i.e., signatures with unnatural names that indicate that they are synthesized either by a compiler or at
     * runtime by some
     * byte-code library.
     *
     * key: strangeSignature
     * value: normalized strange signature
     */
    @NonNull
    private Map<String, String> strangeSignatures;

    @Override
    public String toString() {
        return String
            .format("%s(commonData=%s, entries.size()=%d, strangeSignatures.size()=%d)", this.getClass().getSimpleName(), commonData,
                    entries.size(),
                    strangeSignatures.size());
    }
}
