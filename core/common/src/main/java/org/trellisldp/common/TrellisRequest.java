/*
 * Copyright (c) 2020 Aaron Coburn and individual contributors
 *
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
import static java.util.Collections.emptyList;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;
import static org.trellisldp.common.HttpConstants.*;
import static org.trellisldp.common.TrellisResponse.CONTENT_TYPE;
import static org.trellisldp.common.TrellisResponse.LINK;

import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.trellisldp.vocabulary.LDP;

/**
 * A class representing an HTTP request with various LDP-related headers and query parameters.
 *
 * @author acoburn
 */
public class TrellisRequest {

    private static final String ESCAPE_CHARACTERS = getConfig()
        .getOptionalValue(CONFIG_HTTP_PATH_CHARACTERS_ENCODE, String.class).orElse(UNWISE_CHARACTERS);
    private static final String[] ESCAPE_SEARCH = getSearchArray(ESCAPE_CHARACTERS);
    private static final String[] ESCAPE_REPLACE = getReplaceArray(ESCAPE_CHARACTERS);

    private final boolean trailingSlash;
    private final String path;
    private final String baseUrl;
    private final String method;
    private final List<MediaType> acceptableMediaTypes;

    private final Headers headers;
    private final Headers parameters;
    private final AuthContext secCtx;

    /**
     * Bundle together some request contexts.
     * @param ctx the request context
     */
    public TrellisRequest(final HttpRequestContext ctx) {
        this(ctx, ctx.getAuthContext());
    }

    /**
     * Bundle together some request contexts.
     * @param ctx the request context
     * @param secCtx the security context
     */
    public TrellisRequest(final HttpRequestContext ctx, final AuthContext secCtx) {
        // Extract header values
        this.headers = ctx.getHeaders();
        this.acceptableMediaTypes = ctx.getAcceptableMediaTypes();

        // Extract URI values
        this.parameters = ctx.getQueryParameters();
        this.baseUrl = buildBaseUrl(ctx.getBaseUri(), this.headers);
        this.trailingSlash = ctx.getPath().endsWith("/");

        // The parameters that trail a path segment say something about how to reach the resource
        // rather than naming it, so the resource's path is the path without them.
        // The parameters that trail a segment are dropped from the target as it was written, so
        // that a semicolon a client wrote as an escape stays part of the resource's name.
        final String written = Uris.stripMatrixParameters(ctx.getRawPath());
        this.path = StringUtils.replaceEach(
                Uris.decode(written.startsWith("/") ? written.substring(1) : written),
                ESCAPE_SEARCH, ESCAPE_REPLACE);

        // Extract request method
        this.method = ctx.getMethod();

        // Security context value
        this.secCtx = secCtx;
    }

    /**
     * Get the Content-Type header.
     *
     * @return the Content-Type header
     */
    public String getContentType() {
        return headers.getFirst(CONTENT_TYPE);
    }

    /**
     * Get the slug header.
     *
     * @return the decoded value of the slug header
     */
    public String getSlug() {
        final Slug slug = Slug.valueOf(headers.getFirst(SLUG));
        if (slug != null && !slug.getValue().isEmpty()) {
            return slug.getValue();
        }
        return null;
    }

    /**
     * Get the first LDP Link header.
     *
     * @return the first LDP Link header
     */
    public LinkHeader getLink() {
        for (final String header : headers.getOrDefault(LINK, emptyList())) {
            final LinkHeader link = LinkHeader.valueOf(header);
            if (LinkHeader.TYPE.equals(link.getRel())) {
                final String uri = link.getUri().toString();
                if (uri.startsWith(LDP.getNamespace()) && !uri.equals(LDP.Resource.getIRIString())) {
                    return link;
                }
            }
        }
        return null;
    }

    /**
     * Get the Accept-Datetime value.
     *
     * @return the accept-datetime header
     */
    public AcceptDatetime getDatetime() {
        return AcceptDatetime.valueOf(headers.getFirst(ACCEPT_DATETIME));
    }

    /**
     * Get the prefer header.
     *
     * @return the Prefer header
     */
    public Prefer getPrefer() {
        return Prefer.valueOf(headers.getFirst(PREFER));
    }

    /**
     * Get the range header.
     *
     * @return the range header
     */
    public Range getRange() {
        return Range.valueOf(headers.getFirst(RANGE));
    }

    /**
     * Get the path.
     *
     * <p>This method returns a normalized resource path for use with internal
     * identifiers. That means that any trailing slash will not be present. Please
     * see the {@link #hasTrailingSlash} method to check for a trailing slash.
     *
     * @return the normalized path
     */
    public String getPath() {
        if (path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * Test whether the path has a trailing slash.
     *
     * @return true if the request path ends in a slash
     */
    public boolean hasTrailingSlash() {
        return trailingSlash;
    }

    /**
     * Get the version value.
     *
     * @return the version query parameter
     */
    public Version getVersion() {
        return Version.valueOf(parameters.getFirst("version"));
    }

    /**
     * Get the ext value.
     *
     * @return the ext query parameter
     */
    public String getExt() {
        return parameters.getFirst("ext");
    }

    /**
     * Get a base url value.
     *
     * @return the baseUrl as a string
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Get the security context.
     *
     * @return the security context
     */
    public AuthContext getSecurityContext() {
        return secCtx;
    }

    /**
     * Get the HTTP method.
     * @return the method name
     */
    public String getMethod() {
        return method;
    }

    /**
     * Get all of the headers.
     * @return the headers
     */
    public Headers getHeaders() {
        return headers;
    }

    /**
     * Get the media types that the client will accept, in the order it prefers them.
     *
     * @return the acceptable media types
     */
    public List<MediaType> getAcceptableMediaTypes() {
        return acceptableMediaTypes;
    }

    /**
     * Build the base URL a request is answered against, honouring any forwarding headers.
     *
     * @param uri the base URI the connection carried
     * @param headers the request headers
     * @return the base URL
     */
    public static String buildBaseUrl(final URI uri, final Headers headers) {
        // Start with the baseURI from the request
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();

        // Adjust the protocol, using the non-spec X-Forwarded-* header, if present
        final Forwarded nonSpec = new Forwarded(null, headers.getFirst("X-Forwarded-For"),
                headers.getFirst("X-Forwarded-Host"), headers.getFirst("X-Forwarded-Proto"));
        if (nonSpec.getHostname().isPresent()) {
            host = nonSpec.getHostname().get();
        }
        if (nonSpec.getPort().isPresent()) {
            port = nonSpec.getPort().getAsInt();
        }
        if (nonSpec.getProto().isPresent()) {
            scheme = nonSpec.getProto().get();
        }

        // The standard Forwarded header overrides these values
        final Forwarded forwarded = Forwarded.valueOf(headers.getFirst("Forwarded"));
        if (forwarded != null) {
            if (forwarded.getHostname().isPresent()) {
                host = forwarded.getHostname().get();
            }
            if (forwarded.getPort().isPresent()) {
                port = forwarded.getPort().getAsInt();
            }
            if (forwarded.getProto().isPresent()) {
                scheme = forwarded.getProto().get();
            }
        }

        if (host == null || host.isEmpty()) {
            // A proxy that names the authority as nothing leaves no base URL to answer against,
            // and the service says so about itself rather than about the request.
            throw new IllegalStateException("The authority the request was made to is empty");
        }
        // What was written as the authority may be something no authority may carry - a proxy that
        // names two of them, say - and the base URL is a URI either way.
        final StringBuilder builder = new StringBuilder(scheme).append("://")
            .append(Uris.encodeAuthority(host));
        if (port >= 0) {
            builder.append(':').append(port);
        }
        return builder.append(uri.getRawPath()).toString();
    }

    static String[] getSearchArray(final String escapeChars) {
        final List<String> search = new ArrayList<>();
        for (char ch : escapeChars.toCharArray()) {
            search.add(String.valueOf(ch));
        }
        return search.toArray(String[]::new);
    }

    static String[] getReplaceArray(final String escapeChars) {
        final List<String> replace = new ArrayList<>();
        for (char ch : escapeChars.toCharArray()) {
            replace.add(URLEncoder.encode(String.valueOf(ch), UTF_8));
        }
        return replace.toArray(String[]::new);
    }
}
