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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ErrorPageTest {

    private static ErrorPage.Form chosen(final String... accept) {
        return ErrorPage.chosen(asList(accept));
    }

    @Test
    void testAClientThatNamedNothingWillTakeThePage() {
        assertEquals(ErrorPage.Form.HTML, ErrorPage.chosen(emptyList()));
    }

    @Test
    void testAHeaderThatNamesNoFormIsWrittenNothing() {
        assertNull(chosen(""));
        assertNull(chosen(","));
        assertNull(chosen("application/xml"));
        assertNull(chosen("nonsense"));
        assertNull(chosen("*"));
        assertNull(chosen("TEXT/HTML"));
    }

    @Test
    void testTheFormsAMemberMayName() {
        assertEquals(ErrorPage.Form.HTML, chosen("text/html"));
        assertEquals(ErrorPage.Form.HTML, chosen("text/*"));
        assertEquals(ErrorPage.Form.HTML, chosen("*/*"));
        assertEquals(ErrorPage.Form.PLAIN, chosen("text/plain"));
        assertEquals(ErrorPage.Form.TEXT_JSON, chosen("text/json"));
        assertEquals(ErrorPage.Form.APPLICATION_JSON, chosen("application/json"));
    }

    @Test
    void testOnlyAQualityMayBeWrittenOnAMember() {
        assertEquals(ErrorPage.Form.HTML, chosen("text/html;q=0.5"));
        assertEquals(ErrorPage.Form.HTML, chosen("text/html ; q=1"));
        assertEquals(ErrorPage.Form.HTML, chosen("text/html;;"));
        assertEquals(ErrorPage.Form.HTML, chosen("text/html;q=00.5"));
        assertEquals(ErrorPage.Form.HTML, chosen("text/html;q=\"0.5\""));
        assertNull(chosen("text/html;q=0"));
        assertNull(chosen("text/html;q=-1"));
        assertNull(chosen("text/html;q=abc"));
        assertNull(chosen("text/html;charset=utf-8"));
        assertNull(chosen("text/html;q=1;x=2"));
        assertNull(chosen("text/html;q=1;q=0"));
    }

    @Test
    void testTheFormTheClientWantsMostIsTheOneItGets() {
        assertEquals(ErrorPage.Form.APPLICATION_JSON, chosen("application/json, text/html"));
        assertEquals(ErrorPage.Form.HTML, chosen("text/html, application/json"));
        assertEquals(ErrorPage.Form.PLAIN, chosen("*/*, text/plain"));
        assertEquals(ErrorPage.Form.PLAIN, chosen("text/html;q=0.8, text/plain;q=0.9"));
        assertEquals(ErrorPage.Form.APPLICATION_JSON, chosen("text/*, application/json"));
        assertEquals(ErrorPage.Form.HTML, chosen("text/html", "application/json"));
        assertEquals(ErrorPage.Form.APPLICATION_JSON, chosen("application/json", "text/html"));
    }

    @Test
    void testThePageNamesTheStatusTheTargetAndWhatWentWrong() {
        final String page = new String(ErrorPage.render(ErrorPage.Form.HTML, 404, "/a'b"), ISO_8859_1);
        for (final String expected : asList("<title>Error 404 Not Found</title>",
                "<tr><th>URI:</th><td>/a&apos;b</td></tr>",
                "<tr><th>STATUS:</th><td>404</td></tr>",
                "<tr><th>MESSAGE:</th><td>Not Found</td></tr>")) {
            assertTrue(page.contains(expected), expected);
        }
    }

    @Test
    void testTheOtherFormsSayTheSameThing() {
        final String plain = new String(ErrorPage.render(ErrorPage.Form.PLAIN, 404, "/a"), ISO_8859_1);
        assertEquals("HTTP ERROR 404 Not Found\nURI: /a\nSTATUS: 404\nMESSAGE: Not Found\n"
                + "SERVLET: trellis\n", plain);
        final String document = new String(ErrorPage.render(ErrorPage.Form.APPLICATION_JSON, 500, "/\"b"),
                ISO_8859_1);
        assertEquals("{\n\"servlet\":\"trellis\",\n\"message\":\"Internal Server Error\",\n"
                + "\"url\":\"/\\\"b\",\n\"status\":\"500\"\n}", document);
    }

    @Test
    void testAFormIsWrittenUnderTheMediaTypeItIsNamedBy() {
        assertEquals("text/html;charset=iso-8859-1", ErrorPage.Form.HTML.getContentType());
        assertEquals("text/plain;charset=iso-8859-1", ErrorPage.Form.PLAIN.getContentType());
        assertEquals("text/json", ErrorPage.Form.TEXT_JSON.getContentType());
        assertEquals("application/json", ErrorPage.Form.APPLICATION_JSON.getContentType());
        assertEquals(ErrorPage.Form.HTML, chosen(singletonList("text/html").get(0)));
    }
}
