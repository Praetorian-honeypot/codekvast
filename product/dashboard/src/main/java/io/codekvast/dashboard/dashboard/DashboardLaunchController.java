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
package io.codekvast.dashboard.dashboard;

import io.codekvast.common.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;

import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * This is a REST controller that transforms a request from codekvast-login (i.e. another domain)
 * with a request parameter containing a JWT token to a sessionToken cookie.
 *
 * It redirects to the dashboard's start page, with the token in a cookie.
 *
 * @author olle.hallin@crisp.se
 */
@RestController
@CrossOrigin(origins = {
    "http://localhost:8080", "http://localhost:8088",
    "https://login-staging.codekvast.io", "https://login.codekvast.io"})
@Slf4j
@RequiredArgsConstructor
public class DashboardLaunchController {

    private final SecurityService securityService;

    @RequestMapping(path = "/dashboard/launch", method = POST)
    public ResponseEntity<String> launchDashboard(
        @RequestParam("code") String code,
        @RequestParam(value = "navData", required = false) String navData) {

        logger.debug("Handling launch request, code={}", code);

        String sessionToken = securityService.tradeCodeToWebappToken(code);
        if (sessionToken == null) {
            return ResponseEntity
                .badRequest()
                .location(URI.create("/not-logged-in")) // TODO from settings
                .header(SET_COOKIE, createOrRemoveSessionTokenCookie(null).toString())
                .header(SET_COOKIE, createOrRemoveNavDataCookie(null).toString())
                .build();
        }

        return ResponseEntity
            .ok()
            .location(URI.create("/home"))
            .header(SET_COOKIE, createOrRemoveSessionTokenCookie(sessionToken).toString())
            .header(SET_COOKIE, createOrRemoveNavDataCookie(navData).toString())
            .build();

    }

    @SneakyThrows(UnsupportedEncodingException.class)
    Cookie createOrRemoveSessionTokenCookie(String token) {
        Cookie cookie = new Cookie(CookieNames.SESSION_TOKEN, token == null ? "" : URLEncoder.encode(token, "UTF-8"));
        cookie.setPath("/");
        cookie.setMaxAge(token == null ? 0 : -1); // Remove when browser exits.
        cookie.setHttpOnly(false);
        return cookie;
    }


    private Cookie createOrRemoveNavDataCookie(String navData) {
        Cookie cookie = new Cookie(CookieNames.NAV_DATA, navData == null ? "" : navData);
        cookie.setPath("/");
        cookie.setMaxAge(navData == null ? 0 : -1);
        cookie.setHttpOnly(false);
        return cookie;
    }
}
