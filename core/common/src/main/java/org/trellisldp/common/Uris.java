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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Building URIs out of the parts a response is written from.
 *
 * <p>A path or a query value may already carry percent-encoded characters; those are left as they
 * are, and everything the relevant part of the URI grammar does not allow is encoded.
 *
 * @see <a href="https://tools.ietf.org/html/rfc3986">RFC 3986</a>
 */
public final class Uris {

    private static final String UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        + "0123456789-._~";
    private static final String SUB_DELIMS = "!$&'()*+,;=";
    private static final String PATH_ALLOWED = UNRESERVED + SUB_DELIMS + ":@/";
    private static final String AUTHORITY_ALLOWED = UNRESERVED + SUB_DELIMS + ":[]";
    private static final String QUERY_ALLOWED = UNRESERVED + "!$'()*,;:@/?";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /**
     * Append a path to a URI.
     *
     * @param base the URI to start from
     * @param path the path to append
     * @return the URI
     */
    /**
     * Encode the authority a request named.
     *
     * <p>What a client or a proxy writes as the authority need not be something an authority may
     * carry: everything the authority part of the grammar does not allow is encoded, so that the
     * base URL an answer is written against is a URI.
     *
     * @param authority the authority as it was written
     * @return the authority, encoded
     */
    public static String encodeAuthority(final String authority) {
        return encode(authority, AUTHORITY_ALLOWED);
    }

    public static URI resolve(final String base, final String path) {
        final StringBuilder builder = new StringBuilder(base);
        final String encoded = encode(path, PATH_ALLOWED);
        if (encoded.isEmpty()) {
            return URI.create(builder.toString());
        }
        final boolean baseEndsWithSlash = base.endsWith("/");
        final boolean pathStartsWithSlash = encoded.startsWith("/");
        if (baseEndsWithSlash && pathStartsWithSlash) {
            builder.setLength(builder.length() - 1);
        } else if (!baseEndsWithSlash && !pathStartsWithSlash) {
            builder.append('/');
        }
        return URI.create(builder.append(encoded).toString());
    }

    /**
     * Read a URI that may carry characters the grammar does not allow, encoding those.
     *
     * @param uri the URI
     * @return the URI
     */
    public static URI parse(final String uri) {
        final int query = uri.indexOf('?');
        if (query < 0) {
            return URI.create(encode(uri, PATH_ALLOWED + "#"));
        }
        return URI.create(encode(uri.substring(0, query), PATH_ALLOWED)
                + '?' + encode(uri.substring(query + 1), QUERY_ALLOWED + "=&"));
    }

    /**
     * Drop the parameters that trail each segment of a path, which do not name the resource.
     *
     * @param path the raw path of the request target
     * @return the path without its segment parameters
     */
    public static String stripMatrixParameters(final String path) {
        if (path.indexOf(';') < 0) {
            return path;
        }
        final List<String> segments = new ArrayList<>();
        for (final String raw : path.split("/", -1)) {
            final int matrix = raw.indexOf(';');
            segments.add(matrix < 0 ? raw : raw.substring(0, matrix));
        }
        return String.join("/", segments);
    }

    /**
     * Resolve away the segments of a URI's path that name this one and the one above it.
     *
     * @param uri the URI
     * @return the URI, with those segments resolved away
     */
    public static URI resolveDotSegments(final URI uri) {
        final String path = uri.getRawPath();
        if (path == null || path.isEmpty() || !path.contains("/.")) {
            return uri;
        }
        final Deque<String> resolved = new ArrayDeque<>();
        for (final String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                resolved.pollLast();
            } else {
                resolved.offer(segment);
            }
        }
        final StringBuilder builder = new StringBuilder();
        if (uri.getRawAuthority() != null) {
            builder.append(uri.getScheme() == null ? "" : uri.getScheme() + ':')
                .append("//").append(uri.getRawAuthority());
        } else if (uri.getScheme() != null) {
            builder.append(uri.getScheme()).append(':');
        }
        resolved.forEach(segment -> builder.append('/').append(segment));
        if (uri.getRawQuery() != null) {
            builder.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            builder.append('#').append(uri.getRawFragment());
        }
        return URI.create(builder.toString());
    }

    /**
     * Resolve away the segments of a path that name this one and the one above it.
     *
     * @param path the raw path of the request target
     * @return the normalised raw path
     */
    public static String normalizeTarget(final String path) {
        final List<String> segments = new ArrayList<>();
        final boolean trailing = path.endsWith("/") || path.endsWith("/.") || path.endsWith("/..")
            || ".".equals(path) || "..".equals(path);
        for (final String segment : path.split("/", -1)) {
            if (".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                }
                continue;
            }
            segments.add(segment);
        }
        while (segments.size() > 1 && segments.get(segments.size() - 1).isEmpty()) {
            segments.remove(segments.size() - 1);
        }
        final String joined = String.join("/", segments);
        if (trailing && !joined.endsWith("/")) {
            return joined + "/";
        }
        return joined.isEmpty() ? "/" : joined;
    }

    /**
     * Build a URI out of a path alone.
     *
     * @param path the path
     * @return the URI
     */
    public static URI fromPath(final String path) {
        return URI.create(encode(path, PATH_ALLOWED));
    }

    /**
     * Append a query parameter to a URI.
     *
     * @param uri the URI to start from
     * @param name the parameter name
     * @param value the parameter value
     * @return the URI
     */
    public static URI queryParam(final URI uri, final String name, final String value) {
        final String text = uri.toString();
        return URI.create(text + (text.indexOf('?') < 0 ? '?' : '&')
                + encode(name, QUERY_ALLOWED) + '=' + encode(value, QUERY_ALLOWED));
    }

    /**
     * Undo the percent-encoding of a part of a URI.
     *
     * <p>A percent sign that does not start a well-formed escape stands for itself, and the bytes
     * an escape names are read as UTF-8.
     *
     * @param value the value
     * @return the value with its escapes undone
     */
    public static String decode(final String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        int i = 0;
        while (i < value.length()) {
            final char ch = value.charAt(i);
            if (ch == '%' && i + 2 < value.length() + 1 && hex(value, i + 1) && hex(value, i + 2)) {
                bytes.write(Integer.parseInt(value.substring(i + 1, i + 3), 16));
                i += 3;
            } else {
                bytes.writeBytes(String.valueOf(ch).getBytes(UTF_8));
                i++;
            }
        }
        return new String(bytes.toByteArray(), UTF_8);
    }

    private static boolean hex(final String value, final int index) {
        if (index >= value.length()) {
            return false;
        }
        final char ch = value.charAt(index);
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    /**
     * Percent-encode everything a part of a URI does not allow, leaving anything already encoded
     * as it is.
     *
     * @param value the value
     * @param allowed the characters the part allows
     * @return the encoded value
     */
    public static String encode(final String value, final String allowed) {
        final StringBuilder builder = new StringBuilder();
        final byte[] bytes = value.getBytes(UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            final char ch = (char) (bytes[i] & 0xff);
            if (ch == '%' && i + 2 < bytes.length && isHex(bytes[i + 1]) && isHex(bytes[i + 2])) {
                builder.append('%').append((char) bytes[i + 1]).append((char) bytes[i + 2]);
                i += 2;
            } else if (allowed.indexOf(ch) >= 0) {
                builder.append(ch);
            } else {
                builder.append('%').append(HEX[(ch >> 4) & 0xf]).append(HEX[ch & 0xf]);
            }
        }
        return builder.toString();
    }

    private static boolean isHex(final byte value) {
        final char ch = (char) (value & 0xff);
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    private Uris() {
        // prevent instantiation
    }
}
