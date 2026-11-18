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

import static java.util.Collections.unmodifiableMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A representation of a single value of an HTTP {@code Link} header.
 *
 * <p>A link is a target URI together with a set of parameters. The {@code rel} parameter is special:
 * a link may carry several relation types, and they are written as one space-separated value.
 *
 * @see <a href="https://tools.ietf.org/html/rfc8288">RFC 8288</a>
 */
public final class LinkHeader {

    /** The name of the relation-type parameter. */
    public static final String REL = "rel";

    /** The relation type used for the LDP interaction models of a resource. */
    public static final String TYPE = "type";

    private final URI uri;
    private final Map<String, String> params;

    private LinkHeader(final URI uri, final Map<String, String> params) {
        this.uri = uri;
        this.params = unmodifiableMap(params);
    }

    /**
     * Start building a link with the given target.
     *
     * @param uri the target URI
     * @return a builder
     */
    public static Builder fromUri(final String uri) {
        // A target with nothing after its question mark or its hash carries neither, the way a URI
        // built out of its parts would not carry an empty one.
        String text = uri;
        if (text.endsWith("#")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith("?")) {
            text = text.substring(0, text.length() - 1);
        }
        // The target is resolved against itself, which takes out the segments that name a path
        // rather than a resource, the way a URI built out of its parts would be.
        return new Builder(Uris.resolveDotSegments(URI.create(text)));
    }

    /**
     * Start building a link with the given target.
     *
     * @param uri the target URI
     * @return a builder
     */
    public static Builder fromUri(final URI uri) {
        return new Builder(Uris.resolveDotSegments(uri));
    }

    /**
     * Parse a single link value.
     *
     * @param value the header value
     * @return the link
     * @throws IllegalArgumentException when the value is not a well-formed link
     */
    public static LinkHeader valueOf(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("A link value may not be null");
        }
        final String trimmed = value.trim();
        final int end = trimmed.indexOf('>');
        if (!trimmed.startsWith("<") || end < 0) {
            throw new IllegalArgumentException("Unable to parse link " + value);
        }
        final Builder builder = new Builder(URI.create(trimmed.substring(1, end).trim()));
        for (final String parameter : splitParameters(trimmed.substring(end + 1))) {
            final String text = parameter.trim();
            if (text.isEmpty()) {
                continue;
            }
            final int equals = text.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("Unable to parse link " + value);
            }
            final String name = text.substring(0, equals).trim();
            final String parameterValue = text.substring(equals + 1).trim();
            if (!isToken(name) || !isParameterValue(parameterValue)) {
                throw new IllegalArgumentException("Unable to parse link " + value);
            }
            builder.param(name, unquote(parameterValue));
        }
        return builder.build();
    }

    /**
     * Get the target of the link.
     *
     * @return the target URI
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Get the value of the relation-type parameter, or null when the link carries none.
     *
     * @return the relation types as a single space-separated value
     */
    public String getRel() {
        return params.get(REL);
    }

    /**
     * Get the relation types of the link.
     *
     * @return the relation types, one per entry
     */
    public List<String> getRels() {
        final String rel = params.get(REL);
        if (rel == null || rel.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(rel.split("\\s+")));
    }

    /**
     * Get all of the link's parameters.
     *
     * @return the parameters
     */
    public Map<String, String> getParams() {
        return params;
    }

    /**
     * Write the link the way it travels in a header.
     *
     * <p>The relation type is written first, then every other parameter.
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("<").append(uri).append('>');
        final String rel = params.get(REL);
        if (rel != null) {
            builder.append("; ").append(REL).append("=\"").append(rel).append('"');
        }
        params.forEach((key, value) -> {
            if (!REL.equals(key)) {
                builder.append("; ").append(key).append("=\"").append(value).append('"');
            }
        });
        return builder.toString();
    }

    private static List<String> splitParameters(final String tail) {
        // Parameter values may be quoted, and a quoted value may contain a semicolon.
        final List<String> parameters = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (final char ch : tail.toCharArray()) {
            if (ch == '"') {
                quoted = !quoted;
                current.append(ch);
            } else if (ch == ';' && !quoted) {
                parameters.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parameters.add(current.toString());
        return parameters;
    }

    /**
     * Whether a parameter value is one the grammar allows: a token, or a string in quotes.
     */
    private static boolean isParameterValue(final String value) {
        if (value.startsWith("\"")) {
            return value.length() > 1 && value.endsWith("\"") && value.indexOf('"', 1) == value.length() - 1;
        }
        return isToken(value);
    }

    private static boolean isToken(final String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (final char ch : value.toCharArray()) {
            if (ch <= ' ' || ch >= 127 || "()<>@,;:\\\"/[]?={}".indexOf(ch) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static String unquote(final String value) {
        if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * A builder for {@link LinkHeader} values.
     */
    public static final class Builder {

        private final URI uri;
        private final Map<String, String> params = new HashMap<>();

        private Builder(final URI uri) {
            this.uri = uri;
        }

        /**
         * Add a relation type to the link.
         *
         * @param rel the relation type
         * @return this builder
         */
        public Builder rel(final String rel) {
            final String existing = params.get(REL);
            params.put(REL, existing == null ? rel : existing + " " + rel);
            return this;
        }

        /**
         * Set the media type the link's target is expected to have.
         *
         * @param type the media type
         * @return this builder
         */
        public Builder type(final String type) {
            return param(TYPE, type);
        }

        /**
         * Set a link parameter.
         *
         * @param name the parameter name
         * @param value the parameter value
         * @return this builder
         */
        public Builder param(final String name, final String value) {
            if (REL.equals(name)) {
                return rel(value);
            }
            params.put(name, value);
            return this;
        }

        /**
         * Build the link.
         *
         * @return the link
         */
        public LinkHeader build() {
            return new LinkHeader(uri, new HashMap<>(params));
        }
    }
}
