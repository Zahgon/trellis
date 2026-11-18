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

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An HTTP media type: a type, a subtype and any number of parameters.
 *
 * <p>Parameter names are held without regard to case and in the order the HTTP grammar sorts them
 * in; parameter values are kept as they were written. A media type read from an {@code Accept}
 * header also carries the quality the client gave it, which is what orders a list of them.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7231#section-3.1.1.1">RFC 7231</a>
 */
public final class MediaType {

    /** The type and subtype that stand for any media type. */
    public static final String WILDCARD = "*/*";

    /** The media type that stands for any media type. */
    public static final MediaType WILDCARD_TYPE = new MediaType("*", "*");

    /** The name of the quality parameter. */
    public static final String QUALITY = "q";

    /** The media type of an entity whose form the server was not told. */
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    /** The media type of a plain text entity. */
    public static final String TEXT_PLAIN = "text/plain";

    /** The media type of a plain text entity. */
    public static final MediaType TEXT_PLAIN_TYPE = new MediaType("text", "plain");

    /** The media type of an HTML entity. */
    public static final String TEXT_HTML = "text/html";

    private static final String WILDCARD_PART = "*";

    /**
     * Orders the media types of an {@code Accept} header: the one the client wants most first.
     *
     * <p>A higher quality comes first, and at equal quality a named type comes before a wildcard
     * and a named subtype before a wildcard subtype. Media types equal under all of that keep the
     * order they were written in.
     */
    public static final Comparator<MediaType> ACCEPT_ORDER = Comparator
        .comparingInt(MediaType::getQuality).reversed()
        .thenComparingInt(type -> type.isWildcardType() ? 1 : 0)
        .thenComparingInt(type -> type.isWildcardSubtype() ? 1 : 0);

    private final String type;
    private final String subtype;
    private final Map<String, String> parameters;
    private final int quality;

    /**
     * Create a media type with no parameters.
     *
     * @param type the type
     * @param subtype the subtype
     */
    public MediaType(final String type, final String subtype) {
        this(type, subtype, emptyMap());
    }

    /**
     * Create a media type with a character set.
     *
     * @param type the type
     * @param subtype the subtype
     * @param charset the character set
     */
    public MediaType(final String type, final String subtype, final String charset) {
        this(type, subtype, Map.of("charset", charset));
    }

    /**
     * Create a media type.
     *
     * @param type the type
     * @param subtype the subtype
     * @param parameters the parameters
     */
    public MediaType(final String type, final String subtype, final Map<String, String> parameters) {
        this.type = type == null ? WILDCARD_PART : type;
        this.subtype = subtype == null ? WILDCARD_PART : subtype;
        final Map<String, String> sorted = new TreeMap<>(CASE_INSENSITIVE_ORDER);
        parameters.forEach((name, value) -> sorted.put(name.toLowerCase(), value));
        this.parameters = unmodifiableMap(sorted);
        this.quality = readQuality(this.parameters.get(QUALITY));
    }

    /**
     * Read a media type.
     *
     * <p>A value with no subtype stands for every subtype of its type, and a bare wildcard stands
     * for every media type.
     *
     * @param value the media type
     * @return the media type
     * @throws IllegalArgumentException when the value is not a media type the grammar allows
     */
    public static MediaType valueOf(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("A media type may not be null");
        }
        final List<String> parts = splitOn(value, ';');
        final String full = parts.get(0).trim();
        final String type;
        final String subtype;
        final int slash = full.indexOf('/');
        if (slash < 0) {
            if (!isToken(full)) {
                throw new IllegalArgumentException("Unable to parse media type " + value);
            }
            type = WILDCARD_PART.equals(full) ? WILDCARD_PART : full;
            subtype = WILDCARD_PART;
        } else {
            // The value as a whole may be written with spaces around it, but neither half of it
            // may carry one: a type and a subtype are tokens.
            type = full.substring(0, slash);
            subtype = full.substring(slash + 1);
            if (!isToken(type) || !isToken(subtype)) {
                throw new IllegalArgumentException("Unable to parse media type " + value);
            }
        }
        final Map<String, String> parameters = new TreeMap<>(CASE_INSENSITIVE_ORDER);
        for (final String parameter : parts.subList(1, parts.size())) {
            final String trimmed = parameter.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int equals = trimmed.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("Unable to parse media type parameter " + parameter);
            }
            final String name = trimmed.substring(0, equals).trim();
            // A parameter's value is kept as it was written: a space after the equals sign is part
            // of the value, which is how a quality written with one comes to be no quality at all,
            // while a value written as a pair of quotes with nothing between them is a value.
            final String parameterValue = trimmed.substring(equals + 1);
            if (parameterValue.isEmpty()) {
                throw new IllegalArgumentException("A media type parameter may not be empty: " + parameter);
            }
            if (!isQuoted(parameterValue) && !isToken(parameterValue)) {
                // A value is a token or a string in quotes and nothing else, which is how a value
                // with a space, a colon or a slash in it comes to be no media type at all.
                throw new IllegalArgumentException("Unable to parse media type parameter " + parameter);
            }
            parameters.put(name, unquote(parameterValue));
        }
        return new MediaType(type, subtype, parameters);
    }

    /**
     * Read the media types a client will take, the one it wants most first.
     *
     * @param values the values of the {@code Accept} header, in the order they were sent
     * @return the acceptable media types
     * @throws IllegalArgumentException when a value is not a media type the grammar allows
     */
    public static List<MediaType> readAccept(final List<String> values) {
        final List<MediaType> accepted = new ArrayList<>();
        for (final String value : values) {
            if (value.trim().isEmpty()) {
                // A header with nothing in it names no media type at all.
                continue;
            }
            final List<String> singles = splitOn(value, ',');
            for (int i = 0; i < singles.size(); i++) {
                final String single = singles.get(i).trim();
                if (single.isEmpty()) {
                    // A header may end in a comma; a gap between two commas is not a media type.
                    if (i < singles.size() - 1) {
                        throw new IllegalArgumentException("Unable to parse Accept header " + value);
                    }
                    continue;
                }
                final MediaType type = valueOf(single);
                // A quality is only a quality where a client states one; anywhere else it is a
                // parameter like any other.
                if (!isQuality(type.getParameters().get(QUALITY))) {
                    throw new IllegalArgumentException("Error parsing quality value " + single);
                }
                accepted.add(type);
            }
        }
        accepted.sort(ACCEPT_ORDER);
        return accepted;
    }

    /**
     * Get the type.
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Get the subtype.
     *
     * @return the subtype
     */
    public String getSubtype() {
        return subtype;
    }

    /**
     * Get the parameters.
     *
     * @return the parameters
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * Get the quality the client gave this media type, as a thousandth.
     *
     * @return the quality, 1000 when the client gave none
     */
    public int getQuality() {
        return quality;
    }

    /**
     * Test whether the type is a wildcard.
     *
     * @return true when the type is a wildcard
     */
    public boolean isWildcardType() {
        return WILDCARD_PART.equals(type);
    }

    /**
     * Test whether the subtype is a wildcard.
     *
     * @return true when the subtype is a wildcard
     */
    public boolean isWildcardSubtype() {
        return WILDCARD_PART.equals(subtype);
    }

    /**
     * Test whether two media types can stand for one another, wildcards included.
     *
     * @param other the other media type
     * @return true when the two are compatible
     */
    public boolean isCompatible(final MediaType other) {
        if (other == null) {
            return false;
        }
        if (isWildcardType() || other.isWildcardType()) {
            return true;
        }
        if (!type.equalsIgnoreCase(other.type)) {
            return false;
        }
        return isWildcardSubtype() || other.isWildcardSubtype() || subtype.equalsIgnoreCase(other.subtype);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof MediaType)) {
            return false;
        }
        final MediaType other = (MediaType) obj;
        return type.equalsIgnoreCase(other.type) && subtype.equalsIgnoreCase(other.subtype)
            && parameters.equals(other.parameters);
    }

    @Override
    public int hashCode() {
        return (type.toLowerCase() + '/' + subtype.toLowerCase()).hashCode() + parameters.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(type).append('/').append(subtype);
        parameters.forEach((name, value) -> builder.append(';').append(name).append('=').append(value));
        return builder.toString();
    }

    private static int readQuality(final String value) {
        if (value == null) {
            return 1000;
        }
        final StringBuilder digits = new StringBuilder("000");
        final int dot = value.indexOf('.');
        if (dot >= 0) {
            final String fraction = value.substring(dot + 1);
            for (int i = 0; i < fraction.length() && i < 3; i++) {
                digits.setCharAt(i, fraction.charAt(i));
            }
        }
        final int whole = value.startsWith("1") ? 1000 : 0;
        return whole > 0 ? whole : Integer.parseInt(digits.toString());
    }

    /**
     * Test a quality value: one decimal digit, zero or one, and at most three more after a point,
     * written in no more than five characters.
     */
    private static boolean isQuality(final String value) {
        if (value == null) {
            return true;
        }
        if (value.isEmpty() || value.length() > 5) {
            return false;
        }
        final int dot = value.indexOf('.');
        final String whole = dot < 0 ? value : value.substring(0, dot);
        final String fraction = dot < 0 ? "" : value.substring(dot + 1);
        if (whole.length() > 1 || (whole.isEmpty() && (dot != 0 || fraction.isEmpty()))) {
            return false;
        }
        if (!whole.isEmpty() && !"0".equals(whole) && !"1".equals(whole)) {
            return false;
        }
        for (final char ch : fraction.toCharArray()) {
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isQuoted(final String value) {
        return value.length() > 1 && value.startsWith("\"") && value.endsWith("\"");
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

    private static List<String> splitOn(final String value, final char separator) {
        final List<String> parts = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (final char ch : value.toCharArray()) {
            if (ch == '"') {
                quoted = !quoted;
                current.append(ch);
            } else if (ch == separator && !quoted) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static String unquote(final String value) {
        if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
