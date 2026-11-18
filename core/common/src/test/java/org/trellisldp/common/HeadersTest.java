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
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeadersTest {

    @Test
    void testHeaderNamesAreMatchedWithoutRegardToCase() {
        final Headers headers = Headers.headers();
        headers.add("Content-Type", "text/turtle");
        assertEquals("text/turtle", headers.getFirst("content-type"));
        assertTrue(headers.containsKey("CONTENT-TYPE"));
    }

    @Test
    void testARepeatedNameKeepsEveryValueInOrder() {
        final Headers headers = Headers.headers();
        headers.add("Link", "<a>");
        headers.add("link", "<b>");
        assertEquals(asList("<a>", "<b>"), headers.get("Link"));
        assertEquals("<a>", headers.getFirst("Link"));
    }

    @Test
    void testReplacingEveryValue() {
        final Headers headers = Headers.headers();
        headers.add("Prefer", "return=minimal");
        headers.putSingle("prefer", "return=representation");
        assertEquals(singletonList("return=representation"), headers.get("Prefer"));
    }

    @Test
    void testANameThatCarriesNothing() {
        final Headers headers = Headers.headers();
        assertNull(headers.getFirst("Slug"));
        assertEquals(emptyList(), headers.get("Slug"));
        assertEquals(singletonList("none"), headers.getOrDefault("Slug", singletonList("none")));
        assertFalse(headers.containsKey("Slug"));
    }

    @Test
    void testQueryParameterNamesAreMatchedExactly() {
        final Headers parameters = Headers.parameters();
        parameters.add("ext", "acl");
        assertEquals("acl", parameters.getFirst("ext"));
        assertNull(parameters.getFirst("EXT"));
    }

    @Test
    void testTheNamesKeepTheOrderTheyWereAddedIn() {
        final Headers headers = Headers.headers();
        headers.add("B", "1");
        headers.add("A", "2");
        assertEquals(asList("B", "A"), new java.util.ArrayList<>(headers.keySet()));
    }
}
