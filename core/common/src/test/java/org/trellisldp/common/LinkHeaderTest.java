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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LinkHeaderTest {

    @Test
    void testTheRelationTypeIsWrittenFirst() {
        assertEquals("<http://example.com/>; rel=\"timemap\"; type=\"application/link-format\"",
                LinkHeader.fromUri("http://example.com/").rel("timemap")
                    .type("application/link-format").build().toString());
    }

    @Test
    void testSeveralRelationTypesAreOneValue() {
        final LinkHeader link = LinkHeader.fromUri("http://example.com/").rel("original").rel("timegate").build();
        assertEquals("original timegate", link.getRel());
        assertEquals(asList("original", "timegate"), link.getRels());
    }

    @Test
    void testATargetWithNothingAfterItsQuestionMarkCarriesNone() {
        assertEquals("<http://example.com/a>; rel=\"self\"",
                LinkHeader.fromUri("http://example.com/a?").rel("self").build().toString());
    }

    @Test
    void testTheTargetIsResolvedAgainstItself() {
        assertEquals("<http://example.com/a/b>; rel=\"self\"",
                LinkHeader.fromUri("http://example.com/a/./b").rel("self").build().toString());
        assertEquals("<http://example.com/b>; rel=\"self\"",
                LinkHeader.fromUri("http://example.com/a/../b").rel("self").build().toString());
        assertEquals("<http://example.com/a//b>; rel=\"self\"",
                LinkHeader.fromUri("http://example.com/a//b").rel("self").build().toString());
    }

    @Test
    void testReadingALink() {
        final LinkHeader link = LinkHeader.valueOf("<http://example.com/>; rel=\"type\"");
        assertEquals("http://example.com/", link.getUri().toString());
        assertEquals("type", link.getRel());
    }

    @Test
    void testReadingALinkWithNoRelation() {
        assertNull(LinkHeader.valueOf("<http://example.com/>").getRel());
    }

    @Test
    void testValuesTheGrammarDoesNotAllow() {
        for (final String value : asList("nonsense", "<http://example.com/>; rel", "",
                "<http://a>; rel=\"type\",<http://b>; rel=\"type\"")) {
            assertThrows(IllegalArgumentException.class, () -> LinkHeader.valueOf(value), value);
        }
    }
}
