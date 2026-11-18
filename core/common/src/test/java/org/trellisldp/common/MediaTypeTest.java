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
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MediaTypeTest {

    @Test
    void testParseTypeAndSubtype() {
        final MediaType type = MediaType.valueOf("text/turtle");
        assertEquals("text", type.getType());
        assertEquals("turtle", type.getSubtype());
        assertTrue(type.getParameters().isEmpty());
    }

    @Test
    void testAValueWithNoSubtypeStandsForEverySubtype() {
        assertEquals("*", MediaType.valueOf("nonsense").getSubtype());
        assertEquals("nonsense", MediaType.valueOf("nonsense").getType());
        assertTrue(MediaType.valueOf("*").isWildcardType());
        assertTrue(MediaType.valueOf("*").isWildcardSubtype());
    }

    @Test
    void testParametersAreNamedWithoutRegardToCase() {
        final MediaType type = MediaType.valueOf("text/turtle;Charset=UTF-8");
        assertEquals("UTF-8", type.getParameters().get("charset"));
        assertEquals("text/turtle;charset=UTF-8", type.toString());
    }

    @Test
    void testAnEmptyParameterIsNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> MediaType.valueOf("text/turtle;x="));
    }

    @Test
    void testValuesTheGrammarDoesNotAllow() {
        for (final String value : asList("/turtle", "text/", "a/b/c", ";q=0.5", "text/turtle,x")) {
            assertThrows(IllegalArgumentException.class, () -> MediaType.valueOf(value), value);
        }
    }

    @Test
    void testAQualityIsOnlyCheckedWhereAClientStatesOne() {
        assertEquals("abc", MediaType.valueOf("text/turtle;q=abc").getParameters().get("q"));
        assertThrows(IllegalArgumentException.class,
                () -> MediaType.readAccept(singletonList("text/turtle;q=abc")));
        assertThrows(IllegalArgumentException.class,
                () -> MediaType.readAccept(singletonList("text/turtle;q=2")));
        assertThrows(IllegalArgumentException.class,
                () -> MediaType.readAccept(singletonList("text/turtle;q=0.0001")));
    }

    @Test
    void testTheMostWantedComesFirst() {
        assertEquals(asList("text/turtle", "application/ld+json"),
                names(MediaType.readAccept(singletonList("application/ld+json;q=0.5, text/turtle;q=0.9"))));
        assertEquals(asList("text/turtle", "text/*", "*/*"),
                names(MediaType.readAccept(singletonList("*/*;q=0.5,text/*;q=0.5,text/turtle;q=0.5"))));
        assertEquals(asList("*/*", "text/turtle"),
                names(MediaType.readAccept(singletonList("text/turtle;q=0.5,*/*"))));
    }

    @Test
    void testMediaTypesEqualUnderAllOfThatKeepTheirOrder() {
        assertEquals(asList("application/n-triples", "text/turtle"),
                names(MediaType.readAccept(singletonList("application/n-triples;q=0.9,text/turtle;q=0.9"))));
    }

    @Test
    void testAHeaderMayEndInACommaButNotStartWithOne() {
        assertEquals(singletonList("text/turtle"), names(MediaType.readAccept(singletonList("text/turtle,"))));
        assertThrows(IllegalArgumentException.class, () -> MediaType.readAccept(singletonList(",")));
        assertThrows(IllegalArgumentException.class,
                () -> MediaType.readAccept(singletonList("text/turtle,,application/ld+json")));
    }

    @Test
    void testAHeaderWithNothingInItNamesNothing() {
        assertTrue(MediaType.readAccept(singletonList("")).isEmpty());
    }

    @Test
    void testCompatibility() {
        assertTrue(MediaType.valueOf("text/turtle").isCompatible(MediaType.WILDCARD_TYPE));
        assertTrue(MediaType.valueOf("text/turtle").isCompatible(MediaType.valueOf("text/*")));
        assertTrue(MediaType.valueOf("TEXT/TURTLE").isCompatible(MediaType.valueOf("text/turtle")));
        assertFalse(MediaType.valueOf("text/turtle").isCompatible(MediaType.valueOf("application/xml")));
        assertFalse(MediaType.valueOf("text/turtle").isCompatible(null));
    }

    @Test
    void testQualityIsReadAsAThousandth() {
        assertEquals(1000, MediaType.valueOf("text/turtle").getQuality());
        assertEquals(500, MediaType.valueOf("text/turtle;q=0.5").getQuality());
        assertEquals(333, MediaType.valueOf("text/turtle;q=0.333").getQuality());
        assertEquals(0, MediaType.valueOf("text/turtle;q=0").getQuality());
        assertEquals(1000, MediaType.valueOf("text/turtle;q=1.").getQuality());
    }

    private static List<String> names(final List<MediaType> types) {
        return types.stream().map(type -> type.getType() + '/' + type.getSubtype()).collect(toList());
    }
}
