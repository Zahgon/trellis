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
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.trellisldp.common.HttpStatus;

/**
 * The answer the server writes for itself when a request fails and no handler had anything to say.
 *
 * <p>The server knows three forms to say it in - a page, the same thing as lines of text, and the
 * same thing as a document - and it reads the client's {@code Accept} header its own way to choose
 * between them, which is not the way the layer reads it to negotiate a representation. A member of
 * that header names a form only when it is written exactly as one of the forms' media types, with
 * nothing on it but a quality: a member that carries any other parameter, one whose quality is not
 * a number or is nothing at all, and one that names a media type no form is written in, name no
 * form. Of the forms the members do name, the client is written the one it wants most, by quality
 * first and then by how specific the media type naming it is. A request that carries no
 * {@code Accept} header at all is written the page; one whose header names no form is written
 * nothing. Either way the answer says it may not be cached.
 */
final class ErrorPage {

    /** What a cached copy of a failure is worth. */
    static final String CACHE_CONTROL = "must-revalidate,no-cache,no-store";

    /** The name the answer gives to what handled the request. */
    private static final String SERVLET = "trellis";

    /** A form the server writes a failure in. */
    enum Form {

        /** A page. */
        HTML("text/html;charset=iso-8859-1"),

        /** The same thing as lines of text. */
        PLAIN("text/plain;charset=iso-8859-1"),

        /** The same thing as a document, under the media type a client named it by. */
        TEXT_JSON("text/json"),

        /** The same thing as a document. */
        APPLICATION_JSON("application/json");

        private final String contentType;

        Form(final String contentType) {
            this.contentType = contentType;
        }

        /**
         * Get the media type the answer is written under.
         *
         * @return the media type
         */
        String getContentType() {
            return contentType;
        }
    }

    /** The media types a client names each form by, written exactly as the server knows them. */
    private static final Map<String, Form> FORMS = Map.of(
        "text/html", Form.HTML,
        "text/*", Form.HTML,
        "*/*", Form.HTML,
        "text/plain", Form.PLAIN,
        "text/json", Form.TEXT_JSON,
        "application/json", Form.APPLICATION_JSON);

    /** One form a client named, with what it says about wanting it. */
    private static final class Named {
        private final Form form;
        private final double quality;
        private final int specificity;
        private final int at;

        private Named(final Form form, final double quality, final String type, final int at) {
            this.form = form;
            this.quality = quality;
            this.specificity = "*/*".equals(type) ? 2 : type.endsWith("/*") ? 1 : 0;
            this.at = at;
        }
    }

    private static final Comparator<Named> WANTED = Comparator
        .comparingDouble((Named named) -> named.quality).reversed()
        .thenComparingInt(named -> named.specificity)
        .thenComparingInt(named -> named.at);

    /**
     * Choose the form the answer is written in.
     *
     * @param accept the values of the request's {@code Accept} header, in the order they were sent
     * @return the form, or null when the client named none and did name something
     */
    static Form chosen(final List<String> accept) {
        if (accept.isEmpty()) {
            // Nothing was said about what the client will take, so it is written the page.
            return Form.HTML;
        }
        final List<Named> named = new ArrayList<>();
        int at = 0;
        for (final String header : accept) {
            for (final String single : header.split(",", -1)) {
                final Named one = read(single, at++);
                if (one != null) {
                    named.add(one);
                }
            }
        }
        return named.stream().min(WANTED).map(one -> one.form).orElse(null);
    }

    /**
     * Read one member of the header: the form it names, or null when it names none.
     */
    private static Named read(final String member, final int at) {
        final String[] parts = member.split(";", -1);
        final String type = parts[0].trim();
        double quality = 1;
        boolean read = false;
        for (int i = 1; i < parts.length; i++) {
            final String parameter = parts[i].trim();
            if (parameter.isEmpty()) {
                // A parameter with nothing in it is passed over, as the grammar allows.
                continue;
            }
            final int equals = parameter.indexOf('=');
            final String name = equals < 0 ? parameter : parameter.substring(0, equals).trim();
            if (!read && equals > 0 && "q".equalsIgnoreCase(name)) {
                quality = quality(parameter.substring(equals + 1).trim());
                read = true;
                continue;
            }
            // Anything else on the member means it does not name a form.
            return null;
        }
        final Form form = FORMS.get(type);
        return form == null || quality <= 0 ? null : new Named(form, quality, type, at);
    }

    /**
     * Read a quality the server's own way: a number, however it is written, and nothing when it is
     * not one.
     */
    private static double quality(final String value) {
        final String unquoted = value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")
            ? value.substring(1, value.length() - 1).trim() : value;
        try {
            return Double.parseDouble(unquoted);
        } catch (final NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Write the answer.
     *
     * @param form the form it is written in
     * @param status the status of the answer
     * @param target the target of the request, as it was written
     * @return the entity
     */
    static byte[] render(final Form form, final int status, final String target) {
        final String reason = HttpStatus.reason(status);
        final String message = reason == null ? String.valueOf(status) : reason;
        switch (form) {
            case PLAIN:
                return plain(status, target, message).getBytes(ISO_8859_1);
            case TEXT_JSON:
            case APPLICATION_JSON:
                return document(status, target, message).getBytes(UTF_8);
            default:
                return page(status, target, message).getBytes(ISO_8859_1);
        }
    }

    private static String page(final int status, final String target, final String message) {
        return new StringBuilder("<html>\n<head>\n")
            .append("<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\"/>\n")
            .append("<title>Error ").append(status).append(' ').append(message).append("</title>\n")
            .append("</head>\n<body><h2>HTTP ERROR ").append(status).append(' ').append(message).append("</h2>\n")
            .append("<table>\n")
            .append("<tr><th>URI:</th><td>").append(escape(target)).append("</td></tr>\n")
            .append("<tr><th>STATUS:</th><td>").append(status).append("</td></tr>\n")
            .append("<tr><th>MESSAGE:</th><td>").append(escape(message)).append("</td></tr>\n")
            .append("<tr><th>SERVLET:</th><td>").append(SERVLET).append("</td></tr>\n")
            .append("</table>\n\n</body>\n</html>\n")
            .toString();
    }

    private static String plain(final int status, final String target, final String message) {
        return new StringBuilder("HTTP ERROR ").append(status).append(' ').append(message).append('\n')
            .append("URI: ").append(target).append('\n')
            .append("STATUS: ").append(status).append('\n')
            .append("MESSAGE: ").append(message).append('\n')
            .append("SERVLET: ").append(SERVLET).append('\n')
            .toString();
    }

    private static String document(final int status, final String target, final String message) {
        return new StringBuilder("{\n")
            .append("\"servlet\":\"").append(quote(SERVLET)).append("\",\n")
            .append("\"message\":\"").append(quote(message)).append("\",\n")
            .append("\"url\":\"").append(quote(target)).append("\",\n")
            .append("\"status\":\"").append(status).append("\"\n}")
            .toString();
    }

    private static String escape(final String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("'", "&apos;").replace("\"", "&quot;");
    }

    private static String quote(final String value) {
        final StringBuilder quoted = new StringBuilder();
        for (final char ch : value.toCharArray()) {
            if (ch == '"' || ch == '\\') {
                quoted.append('\\').append(ch);
            } else if (ch < 0x20) {
                quoted.append(String.format("\\u%04x", (int) ch));
            } else {
                quoted.append(ch);
            }
        }
        return quoted.toString();
    }

    private ErrorPage() {
        // prevent instantiation
    }
}
