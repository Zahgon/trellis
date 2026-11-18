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
package org.trellisldp.http;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.trellisldp.common.Headers;
import org.trellisldp.common.TrellisResponse;

class TrellisRequestContextTest {

    @Test
    void testThePathIsReadWithoutItsEncoding() {
        assertEquals("a b", context("/a%20b", null).getPath());
        assertEquals("Ünique", context("/%C3%9Cnique", null).getPath());
        assertEquals("a/b", context("/a%2Fb", null).getPath());
        assertEquals("a%zz", context("/a%zz", null).getPath());
    }

    @Test
    void testTheTargetIsKeptAsItWasWritten() {
        assertEquals("/a%20b", context("/a%20b", null).getRawPath());
    }

    @Test
    void testQueryParametersAreReadWithoutTheirEncoding() {
        final TrellisRequestContext context = context("/a", "ext=acl&x=1&x=2&flag&v=a%20b");
        assertEquals("acl", context.getQueryParameters().getFirst("ext"));
        assertEquals(asList("1", "2"), context.getQueryParameters().get("x"));
        assertEquals("", context.getQueryParameters().getFirst("flag"));
        assertEquals("a b", context.getQueryParameters().getFirst("v"));
    }

    @Test
    void testARepeatedHeaderIsReadAsOneValueWithCommasBetweenIt() {
        final TrellisRequestContext context = context("/a", null);
        context.getHeaders().add("Link", "<a>");
        context.getHeaders().add("Link", "<b>");
        assertEquals("<a>,<b>", context.getHeaderString("Link"));
        assertEquals("<a>", context.getHeaders().getFirst("Link"));
        assertNull(context.getHeaderString("Slug"));
    }

    @Test
    void testAFilterMayEndTheRequest() {
        final TrellisRequestContext context = context("/a", null);
        assertNull(context.getAbortResponse());
        context.abortWith(TrellisResponse.status(400).build());
        assertEquals(400, context.getAbortResponse().getStatus());
    }

    @Test
    void testAFilterMayLeaveSomethingBehind() {
        final TrellisRequestContext context = context("/a", null);
        assertNull(context.getProperty("modes"));
        context.setProperty("modes", "read");
        assertEquals("read", context.getProperty("modes"));
    }

    @Test
    void testTheMediaTypesAClientWillTake() {
        final TrellisRequestContext context = context("/a", null);
        context.getHeaders().add("Accept", "text/turtle;q=0.5, application/ld+json");
        assertEquals("application/ld+json", context.getAcceptableMediaTypes().get(0).toString());
        assertTrue(context("/a", null).getAcceptableMediaTypes().isEmpty());
    }

    private static TrellisRequestContext context(final String path, final String query) {
        return new TrellisRequestContext("GET", path, query, URI.create("http://example.com/"),
                Headers.headers());
    }
}
