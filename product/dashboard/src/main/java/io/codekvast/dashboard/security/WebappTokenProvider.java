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
package io.codekvast.dashboard.security;

/**
 * Business interface dealing with webapp SSO tokens.
 *
 * @author olle.hallin@crisp.se
 */
public interface WebappTokenProvider {

    /**
     * Used for converting a successful Single Sign-On to a webapp token.
     *
     * @param customerId  The internal customerId
     * @param credentials The credentials to convert to a token
     * @return A (JWT) token to use when launching the webapp.
     */
    String createWebappToken(Long customerId, WebappCredentials credentials);

    /**
     * Extends the lifetime of a webapp token.
     *
     * @return A new webapp token to use in future webapp API requests.
     */
    String renewWebappToken();
}
