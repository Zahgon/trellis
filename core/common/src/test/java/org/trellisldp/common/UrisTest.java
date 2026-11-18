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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

class UrisTest {

    @Test
    void testAppendingAPath() {
        assertEquals("http://example.com/a/b", Uris.resolve("http://example.com/", "a/b").toString());
        assertEquals("http://example.com/a/b", Uris.resolve("http://example.com", "a/b").toString());
        assertEquals("http://example.com/a/b", Uris.resolve("http://example.com/", "/a/b").toString());
        assertEquals("http://example.com/", Uris.resolve("http://example.com/", "").toString());
    }

    @Test
    void testWhatAPathDoesNotAllowIsEncoded() {
        assertEquals("http://example.com/a%20b", Uris.resolve("http://example.com/", "a b").toString());
        assertEquals("http://example.com/%C3%9C", Uris.resolve("http://example.com/", "Ü").toString());
        assertEquals("http://example.com/a%3Fb", Uris.resolve("http://example.com/", "a?b").toString());
    }

    @Test
    void testWhatIsAlreadyEncodedIsLeftAlone() {
        assertEquals("http://example.com/a%20b", Uris.resolve("http://example.com/", "a%20b").toString());
        assertEquals("http://example.com/a%2Fb", Uris.resolve("http://example.com/", "a%2Fb").toString());
    }

    @Test
    void testAQueryParameterIsAppended() {
        assertEquals("/a?ext=acl", Uris.queryParam(Uris.fromPath("/a"), "ext", "acl").toString());
        assertEquals("/a?x=1&ext=acl",
                Uris.queryParam(URI.create("/a?x=1"), "ext", "acl").toString());
    }

    @Test
    void testTheParametersThatTrailASegmentAreNotPartOfIt() {
        assertEquals("/a", Uris.stripMatrixParameters("/a;b"));
        assertEquals("/a/c", Uris.stripMatrixParameters("/a;b/c;d"));
        assertEquals("/a/b", Uris.stripMatrixParameters("/a/b"));
    }

    @Test
    void testResolvingASegmentThatNamesAPath() {
        assertEquals("/a/b", Uris.resolveDotSegments(URI.create("/a/./b")).toString());
        assertEquals("/b", Uris.resolveDotSegments(URI.create("/a/../b")).toString());
        assertEquals("?ext=acl", Uris.resolveDotSegments(URI.create("/./?ext=acl")).toString());
        assertEquals("/a//b", Uris.resolveDotSegments(URI.create("/a//b")).toString());
        assertEquals("http://example.com/a", Uris.resolveDotSegments(URI.create("http://example.com/./a"))
                .toString());
    }
}
