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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.trellisldp.common.HttpStatus;
import org.trellisldp.common.TrellisHttpException;

class ContentCodingTest {

    private static final byte[] ENTITY = "<> a <http://example.com/Thing> .".getBytes(UTF_8);

    @Test
    void testAnEntityThatCarriesNoCodingIsLeftAlone() {
        assertArrayEquals(ENTITY, ContentCoding.decode(ENTITY, null));
        assertArrayEquals(ENTITY, ContentCoding.decode(ENTITY, "identity"));
        assertArrayEquals(ENTITY, ContentCoding.decode(ENTITY, "bogus"));
    }

    @Test
    void testADeflatedEntity() throws IOException {
        assertArrayEquals(ENTITY, ContentCoding.decode(deflate(ENTITY), "gzip"));
        assertArrayEquals(ENTITY, ContentCoding.decode(deflate(ENTITY), "GZIP"));
        assertArrayEquals(ENTITY, ContentCoding.decode(deflate(ENTITY), " gzip "));
    }

    @Test
    void testEveryStreamOfAnEntityBelongsToIt() throws IOException {
        final byte[] first = deflate("one".getBytes(UTF_8));
        final byte[] second = deflate("two".getBytes(UTF_8));
        final byte[] both = new byte[first.length + second.length];
        System.arraycopy(first, 0, both, 0, first.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        assertArrayEquals("onetwo".getBytes(UTF_8), ContentCoding.decode(both, "gzip"));
    }

    @Test
    void testAStreamThatStopsInTheMiddleCarriesWhatItSaid() throws IOException {
        final byte[] deflated = deflate(ENTITY);
        final byte[] truncated = new byte[deflated.length - 4];
        System.arraycopy(deflated, 0, truncated, 0, truncated.length);
        assertArrayEquals(ENTITY, ContentCoding.decode(truncated, "gzip"));
    }

    @Test
    void testBytesAfterAWholeStreamAreTheServersFault() throws IOException {
        final byte[] deflated = deflate(ENTITY);
        final byte[] trailing = new byte[deflated.length + 4];
        System.arraycopy(deflated, 0, trailing, 0, deflated.length);
        final TrellisHttpException failure = assertThrows(TrellisHttpException.class,
                () -> ContentCoding.decode(trailing, "gzip"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.getResponse().getStatus());
    }

    @Test
    void testBytesThatAreNoStreamAtAllAreTheClientsFault() {
        final TrellisHttpException failure = assertThrows(TrellisHttpException.class,
                () -> ContentCoding.decode("not a deflate stream".getBytes(UTF_8), "gzip"));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getResponse().getStatus());
    }

    @Test
    void testWhichClientsWillTakeADeflatedAnswer() {
        assertTrue(ContentCoding.acceptsGzip(singletonList("gzip")));
        assertTrue(ContentCoding.acceptsGzip(singletonList("GZIP")));
        assertTrue(ContentCoding.acceptsGzip(singletonList("deflate, gzip")));
        assertTrue(ContentCoding.acceptsGzip(asList("deflate", "gzip")));
        assertFalse(ContentCoding.acceptsGzip(singletonList("gzip;q=0")));
        assertFalse(ContentCoding.acceptsGzip(singletonList("*")));
        assertFalse(ContentCoding.acceptsGzip(singletonList("deflate")));
        assertFalse(ContentCoding.acceptsGzip(singletonList("")));
    }

    @Test
    void testWhichFormsAreWorthDeflating() {
        assertTrue(ContentCoding.worthEncoding(null));
        assertTrue(ContentCoding.worthEncoding("text/turtle"));
        assertTrue(ContentCoding.worthEncoding("image/svg+xml"));
        assertFalse(ContentCoding.worthEncoding("image/png"));
        assertFalse(ContentCoding.worthEncoding("audio/mpeg"));
        assertFalse(ContentCoding.worthEncoding("video/mp4"));
        assertFalse(ContentCoding.worthEncoding("application/zip"));
    }

    @Test
    void testAnswersAreDeflatedAndReadBackTheSame() throws IOException {
        assertArrayEquals(ENTITY, ContentCoding.decode(ContentCoding.encode(ENTITY), "gzip"));
    }

    private static byte[] deflate(final byte[] data) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }
}
