/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trellisldp.common;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

class TrellisHttpExceptionTest {

    @Test
    void testTheMessageNamesTheStatusAndItsReason() {
        assertEquals("HTTP 404 Not Found", TrellisHttpException.notFound().getMessage());
        assertEquals("HTTP 410 Gone", TrellisHttpException.gone().getMessage());
        assertEquals("HTTP 406 Not Acceptable", TrellisHttpException.notAcceptable().getMessage());
        assertEquals("HTTP 415 Unsupported Media Type",
                TrellisHttpException.unsupportedMediaType().getMessage());
    }

    @Test
    void testTheStatusTheAnswerCarries() {
        assertEquals(HttpStatus.NOT_FOUND, TrellisHttpException.notFound().getResponse().getStatus());
        assertEquals(HttpStatus.FORBIDDEN, TrellisHttpException.forbidden().getResponse().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, TrellisHttpException.badRequest("why").getResponse().getStatus());
        assertEquals("why", TrellisHttpException.badRequest("why").getMessage());
    }

    @Test
    void testTheMethodsAResourceAllowsAreNamedOnce() {
        final TrellisResponse response = TrellisHttpException
            .methodNotAllowed("GET", asList("HEAD", "OPTIONS")).getResponse();
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatus());
        assertEquals(1, response.getHeaders().get(TrellisResponse.ALLOW).size());
        assertEquals("GET", response.getHeaders().getFirst(TrellisResponse.ALLOW).split(",")[2]);
    }

    @Test
    void testEveryChallengeIsOffered() {
        final TrellisResponse response = TrellisHttpException
            .notAuthorized(asList("Bearer realm=\"trellis\"", "Basic realm=\"trellis\"")).getResponse();
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus());
        assertEquals(asList("Bearer realm=\"trellis\"", "Basic realm=\"trellis\""),
                response.getHeaders().get(TrellisResponse.WWW_AUTHENTICATE));
    }

    @Test
    void testARedirection() {
        final TrellisResponse response = TrellisHttpException
            .redirect(HttpStatus.SEE_OTHER, URI.create("http://example.com/a/")).getResponse();
        assertEquals(HttpStatus.SEE_OTHER, response.getStatus());
        assertEquals("http://example.com/a/", response.getHeaders().getFirst(TrellisResponse.LOCATION));
    }
}
