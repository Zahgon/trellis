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
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class TrellisResponseTest {

    @Test
    void testStatusAndReason() {
        assertEquals(HttpStatus.OK, TrellisResponse.ok().build().getStatus());
        assertEquals(HttpStatus.NO_CONTENT, TrellisResponse.noContent().build().getStatus());
        assertEquals(HttpStatus.NOT_MODIFIED, TrellisResponse.notModified().build().getStatus());
        assertNull(TrellisResponse.ok().build().getReason());
        assertEquals("Server Error", TrellisResponse.status(500).reason("Server Error").build().getReason());
    }

    @Test
    void testASuccessfulAnswer() {
        assertTrue(TrellisResponse.status(204).build().isSuccessful());
        assertFalse(TrellisResponse.status(304).build().isSuccessful());
        assertFalse(TrellisResponse.status(404).build().isSuccessful());
    }

    @Test
    void testDatesAreWrittenTheWayAHeaderCarriesThem() {
        final TrellisResponse response = TrellisResponse.ok()
            .lastModified(Instant.parse("2020-01-02T03:04:05Z")).build();
        assertEquals(singletonList("Thu, 02 Jan 2020 03:04:05 GMT"),
                response.getHeaders().get(TrellisResponse.LAST_MODIFIED));
    }

    @Test
    void testLinksAreAddedInTheOrderTheyAreNamed() {
        final TrellisResponse response = TrellisResponse.ok()
            .link("http://example.com/a", "type")
            .links(singletonList(LinkHeader.fromUri("http://example.com/b").rel("self").build()))
            .build();
        assertEquals(asList("<http://example.com/a>; rel=\"type\"", "<http://example.com/b>; rel=\"self\""),
                response.getHeaders().get(TrellisResponse.LINK));
    }

    @Test
    void testTheMediaTypeAndTheLocationAreNamedOnce() {
        final TrellisResponse response = TrellisResponse.status(HttpStatus.CREATED)
            .type("text/turtle").type("application/ld+json")
            .location(URI.create("http://example.com/a"))
            .contentLocation(URI.create("http://example.com/b"))
            .tag(new EntityTag("abc", true)).build();
        assertEquals(singletonList("application/ld+json"),
                response.getHeaders().get(TrellisResponse.CONTENT_TYPE));
        assertEquals("http://example.com/a", response.getHeaders().getFirst(TrellisResponse.LOCATION));
        assertEquals("http://example.com/b", response.getHeaders().getFirst(TrellisResponse.CONTENT_LOCATION));
        assertEquals("W/\"abc\"", response.getHeaders().getFirst(TrellisResponse.ETAG));
    }

    @Test
    void testAnAnswerCanBeBuiltFromAnother() {
        final TrellisResponse response = TrellisResponse.status(401)
            .header(TrellisResponse.WWW_AUTHENTICATE, "Basic realm=\"trellis\"").build();
        final TrellisResponse rebuilt = response.toBuilder().entity("{}").type("application/json").build();
        assertEquals(401, rebuilt.getStatus());
        assertEquals("Basic realm=\"trellis\"", rebuilt.getHeaders().getFirst(TrellisResponse.WWW_AUTHENTICATE));
        assertEquals("{}", rebuilt.getEntity());
    }
}
