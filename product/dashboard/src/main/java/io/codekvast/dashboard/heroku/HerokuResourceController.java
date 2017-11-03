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
package io.codekvast.dashboard.heroku;

import io.codekvast.dashboard.bootstrap.CodekvastSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.xml.bind.DatatypeConverter;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.*;

/**
 * @author olle.hallin@crisp.se
 */
@RestController
@Slf4j
public class HerokuResourceController {

    private final CodekvastSettings settings;
    private final HerokuService herokuService;

    @Inject
    public HerokuResourceController(CodekvastSettings settings, HerokuService herokuService) {
        this.settings = settings;
        this.herokuService = herokuService;
    }

    @ExceptionHandler
    public ResponseEntity<String> onAuthenticationException(AuthenticationException e) {
        logger.warn("Invalid credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @RequestMapping(path = "/heroku/resources", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<HerokuProvisionResponse> provision(@Valid @RequestBody HerokuProvisionRequest request,
                                                             @RequestHeader(AUTHORIZATION) String auth) {
        logger.debug("request={}", request);

        validateCredentials(auth);

        return ResponseEntity.ok(herokuService.provision(request));
    }

    @RequestMapping(path = "/heroku/resources/{id}", method = PUT)
    public ResponseEntity<String> changePlan(@PathVariable("id") String id,
                                             @Valid @RequestBody HerokuChangePlanRequest request,
                                             @RequestHeader(AUTHORIZATION) String auth) {
        logger.debug("id={}, request={}", id, request);

        validateCredentials(auth);

        herokuService.changePlan(id, request);

        return ResponseEntity.ok("{}");
    }

    @RequestMapping(path = "/heroku/resources/{id}", method = DELETE)
    public ResponseEntity<String> deprovision(@PathVariable("id") String id,
                                              @RequestHeader(AUTHORIZATION) String auth) {
        logger.debug("id={}", id);

        validateCredentials(auth);

        herokuService.deprovision(id);

        return ResponseEntity.ok("{}");
    }

    private void validateCredentials(String auth) throws AuthenticationException {
        logger.debug("auth={}", auth);

        // The password is defined in <rootDir>/provisioning/vars/secrets.yml, and it has been pushed to Heroku by means
        // of <rootDir>/provisioning/push-addon-manifest-to-heroku.sh

        String credentials = "codekvast:" + settings.getHerokuApiPassword();
        String expected = "Basic " + DatatypeConverter.printBase64Binary(credentials.getBytes());

        if (!auth.equals(expected)) {
            throw new BadCredentialsException("Invalid credentials: " + auth);
        }
    }
}