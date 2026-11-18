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
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.Collections.emptyList;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.common.HttpConstants.DELETE;
import static org.trellisldp.common.HttpConstants.GET;
import static org.trellisldp.common.HttpConstants.HEAD;
import static org.trellisldp.common.HttpConstants.OPTIONS;
import static org.trellisldp.common.HttpConstants.PATCH;
import static org.trellisldp.common.HttpConstants.POST;
import static org.trellisldp.common.HttpConstants.PUT;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.trellisldp.common.Headers;
import org.trellisldp.common.HttpRequestFilter;
import org.trellisldp.common.HttpResponseFilter;
import org.trellisldp.common.HttpStatus;
import org.trellisldp.common.MediaType;
import org.trellisldp.common.StreamWriter;
import org.trellisldp.common.TrellisHttpException;
import org.trellisldp.common.TrellisResponse;

/**
 * The web server's view of the LDP layer: it turns a request into the shape the handlers read,
 * runs the filters around them, and writes what they answer.
 *
 * <p>The service claims every target below the path it is registered on, because in LDP any path
 * may name a resource. A request whose method the layer does not implement is answered the same way
 * one whose method a resource does not allow is.
 */
public class TrellisHttpService implements Service {

    private static final Logger LOGGER = getLogger(TrellisHttpService.class);
    /** The octets a request target may carry that no URI may hold. */
    private static final String NOT_IN_A_URI = "\"<>\\^`{|}";
    private static final List<String> METHODS = List.of(GET, HEAD, OPTIONS, PATCH, PUT, DELETE, POST);
    /** The methods whose answers the server writes its own page for, and deflates. */
    private static final List<String> WRAPPED_METHODS = List.of(GET, HEAD, POST);
    /** The methods a handler answers; the routing refuses anything else. */
    private static final List<String> SERVED_METHODS = List.of(GET, HEAD, OPTIONS, PATCH, PUT, POST, DELETE);

    private final TrellisHttpResource resource;
    private final List<HttpRequestFilter> requestFilters = new ArrayList<>();
    private final List<HttpResponseFilter> responseFilters = new ArrayList<>();

    /**
     * Create the serving layer for an LDP resource handler.
     *
     * @param resource the LDP resource handler
     */
    public TrellisHttpService(final TrellisHttpResource resource) {
        this.resource = resource;
    }

    /**
     * Add a filter that looks at a request before a handler does.
     *
     * <p>Request filters run in the order they are added.
     *
     * @param filter the filter
     * @return this service
     */
    public TrellisHttpService addRequestFilter(final HttpRequestFilter filter) {
        requestFilters.add(filter);
        return this;
    }

    /**
     * Add a filter that adds to an answer after a handler has built it.
     *
     * <p>Response filters run in the order they are added.
     *
     * @param filter the filter
     * @return this service
     */
    public TrellisHttpService addResponseFilter(final HttpResponseFilter filter) {
        responseFilters.add(filter);
        return this;
    }

    @Override
    public void update(final Routing.Rules rules) {
        rules.any((Handler) this::handle);
    }

    /**
     * Get the answer the web server writes for a request it could not read far enough to route.
     *
     * @return the handler
     */
    public io.helidon.webserver.DirectHandler unreadableRequestHandler() {
        return new UnreadableRequestHandler();
    }

    /**
     * Answer a request the web server could not even read.
     *
     * <p>A header the HTTP grammar does not allow leaves the server with no request to route, and
     * it says so with the bare status rather than with anything about itself.
     *
     * @param request the request
     * @param response the answer
     * @param error what went wrong
     */
    public void handleFailure(final ServerRequest request, final ServerResponse response,
            final Throwable error) {
        LOGGER.debug("Unreadable request: {}", error.getMessage());
        write(request, response, null, TrellisResponse.status(HttpStatus.BAD_REQUEST).build());
    }

    void handle(final ServerRequest request, final ServerResponse response) {
        // The entity is read as the bytes that arrived, not through anything that would look at the
        // media type the request declared: what that media type means is the LDP layer's to decide,
        // and a value it cannot read is an answer rather than a refusal to read the request at all.
        Multi.create(request.content()).collectList()
            .thenAccept(chunks -> dispatch(request, response, join(chunks)))
            .exceptionally(err -> {
                LOGGER.error("Unable to read the request entity", err);
                write(request, response, null, serverError());
                return null;
            });
    }

    /**
     * What the server answers when it, rather than anything it serves, is at fault.
     *
     * @return the answer
     */
    static TrellisResponse serverError() {
        return TrellisResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).reason(HttpStatus.SERVER_ERROR).build();
    }

    private static byte[] join(final List<DataChunk> chunks) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (final DataChunk chunk : chunks) {
            bytes.writeBytes(chunk.bytes());
            chunk.release();
        }
        return bytes.toByteArray();
    }

    private void dispatch(final ServerRequest request, final ServerResponse response, final byte[] rawEntity) {
        final TrellisRequestContext ctx = context(request);
        if (refusedQuality(ctx.getHeaders().get(TrellisResponse.ACCEPT))) {
            // A quality written as a negative number is refused while the request is still being
            // read, whatever its method and whatever it asked for, so the answer carries nothing
            // the service would otherwise put around a failure: no word about caching it, and no
            // answer of the server's own.
            LOGGER.debug("A quality was written as a negative number");
            response.status(Http.ResponseStatus.create(HttpStatus.BAD_REQUEST,
                        HttpStatus.reason(HttpStatus.BAD_REQUEST)));
            response.send(Multi.empty());
            return;
        }
        // What the web server refuses while it is still reading the request, before anything about
        // the target or the method is looked at: an expectation it will not meet, an asking to
        // change protocol that the request does not also carry in its connection options, and - for
        // any method but OPTIONS - the target written as an asterisk, which names the server
        // itself. Each is answered with what the server says about itself rather than with anything
        // about the resource, and the first two in the page the server writes for a message it
        // cannot act on.
        final String expectation = ctx.getHeaderString("Expect");
        if (expectation != null && !expectation.trim().isEmpty()
                && !"100-continue".equalsIgnoreCase(expectation.trim())) {
            LOGGER.debug("An expectation the service will not meet: {}", expectation);
            badMessage(request, response, HttpStatus.EXPECTATION_FAILED, true);
            return;
        }
        if (asksToUpgrade(ctx)) {
            LOGGER.debug("A request asking to change protocol without saying so in its connection");
            badMessage(request, response, HttpStatus.BAD_REQUEST, true);
            return;
        }
        if ("*".equals(ctx.getRawPath()) && !OPTIONS.equals(ctx.getMethod())) {
            LOGGER.debug("An asterisk target named by a {} request", ctx.getMethod());
            badMessage(request, response, HttpStatus.BAD_REQUEST, false);
            return;
        }
        try {
            // What a client will take, and what it says it is sending, are read before anything
            // else: a value the grammar does not allow leaves the service with no request it can
            // answer, whatever the target is. A client that repeats the media type of its entity
            // has named one value with a comma in it, which is not a media type.
            ctx.getAcceptableMediaTypes();
            final String declared = ctx.getHeaderString(TrellisResponse.CONTENT_TYPE);
            if (declared != null) {
                MediaType.valueOf(declared);
            }
        } catch (final IllegalArgumentException ex) {
            LOGGER.debug("Unreadable request header: {}", ex.getMessage());
            write(request, response, ctx, TrellisResponse.status(HttpStatus.BAD_REQUEST).build());
            return;
        }
        try {
            final byte[] entity = ContentCoding.decode(rawEntity,
                    ctx.getHeaders().getFirst("Content-Encoding"));
            answer(ctx, entity).thenAccept(answer -> write(request, response, ctx, answer))
                .exceptionally(err -> {
                    LOGGER.error("Error answering request", err);
                    write(request, response, ctx, serverError());
                    return null;
                });
        } catch (final TrellisHttpException ex) {
            write(request, response, ctx, ex.getResponse());
        }
    }

    private CompletionStage<TrellisResponse> answer(final TrellisRequestContext ctx, final byte[] entity) {
        if (!SERVED_METHODS.contains(ctx.getMethod())) {
            // A method no handler answers is refused where the request is routed, which is before
            // any filter sees it: what the request asked for never comes up.
            return completedFuture(TrellisResponse.status(HttpStatus.METHOD_NOT_ALLOWED).build());
        }
        for (final HttpRequestFilter filter : requestFilters) {
            try {
                filter.filter(ctx);
            } catch (final TrellisHttpException ex) {
                return completedFuture(mapFilterError(ex));
            }
            if (ctx.getAbortResponse() != null) {
                return completedFuture(ctx.getAbortResponse());
            }
        }
        switch (ctx.getMethod()) {
            case GET:
                return resource.getResource(ctx);
            case HEAD:
                return resource.getResourceHeaders(ctx);
            case OPTIONS:
                return resource.options(ctx);
            case PATCH:
                return resource.updateResource(ctx, new String(entity, UTF_8));
            case PUT:
                return resource.setResource(ctx, new ByteArrayInputStream(entity));
            case POST:
                return resource.createResource(ctx, new ByteArrayInputStream(entity));
            case DELETE:
                return resource.deleteResource(ctx);
            default:
                return completedFuture(
                        TrellisResponse.status(HttpStatus.METHOD_NOT_ALLOWED).build());
        }
    }

    /**
     * Turn an error a filter raised into the answer the service writes.
     *
     * <p>A filter's failure is not a handler's: it carries a message, and the service says what
     * went wrong in a document rather than leaving the answer empty.
     */
    private TrellisResponse mapFilterError(final TrellisHttpException ex) {
        final TrellisResponse original = ex.getResponse();
        final String message = ex.getMessage() == null ? "" : ex.getMessage();
        final String document = "{\"code\":" + original.getStatus() + ",\"message\":\"" + escape(message) + "\"}";
        return original.toBuilder().type("application/json").entity(document).build();
    }

    private static String escape(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * The target of a request as it was written, without its query.
     *
     * <p>A target that starts with two slashes names no authority here; it is a path like any other.
     */
    private static String rawTarget(final ServerRequest request) {
        try {
            final URI uri = request.uri();
            final String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            // A target written in full names the resource its path names: the authority in it says
            // which service the request was made to, not which resource it is about. A target that
            // only starts with two slashes names no authority here; it is a path like any other.
            if (uri.getRawAuthority() == null || uri.getScheme() != null) {
                return path;
            }
            return "//" + uri.getRawAuthority() + path;
        } catch (final RuntimeException ex) {
            // A target that carries an octet no URI may carry still names a resource: it names the
            // one the octets name, with the octets a URI cannot hold written as the
            // percent-encodings the old stack wrote them as.
            return percentEncoded(request.path().toRawString());
        }
    }

    /**
     * The query of a request as it was written.
     */
    private static String rawQuery(final ServerRequest request) {
        try {
            return request.uri().getRawQuery();
        } catch (final RuntimeException ex) {
            final String query = request.query();
            return query == null || query.isEmpty() ? null : percentEncoded(query);
        }
    }

    /**
     * One part of a request target, with the octets no URI may carry written as percent-encodings.
     *
     * @param part the part as it arrived
     * @return the part as a URI may carry it
     */
    private static String percentEncoded(final String part) {
        final StringBuilder out = new StringBuilder(part.length());
        for (final byte raw : part.getBytes(UTF_8)) {
            final int octet = raw & 0xff;
            if (octet <= 0x20 || octet >= 0x7f || NOT_IN_A_URI.indexOf((char) octet) >= 0) {
                out.append('%').append(String.format("%02X", octet));
            } else {
                out.append((char) octet);
            }
        }
        return out.toString();
    }

    private TrellisRequestContext context(final ServerRequest request) {
        final Headers headers = Headers.headers();
        request.headers().toMap().forEach((name, values) -> values.forEach(value -> headers.add(name, value)));
        // A client that repeats the authority is answered against the last one it named; one that
        // names none, and one that names nothing at all, is answered against the connector itself,
        // which says nothing about the port when the client did not name one.
        final List<String> hosts = headers.get("Host");
        final String named = hosts.isEmpty() ? null : hosts.get(hosts.size() - 1);
        final String authority = named == null ? request.localAddress() + ":" + request.localPort()
            : named.isEmpty() ? request.localAddress() : named;
        final URI baseUri = URI.create((request.isSecure() ? "https://" : "http://") + authority + "/");
        return new TrellisRequestContext(request.method().name(), rawTarget(request),
                rawQuery(request), baseUri, headers);
    }

    private void write(final ServerRequest request, final ServerResponse response,
            final TrellisRequestContext ctx, final TrellisResponse answer) {
        TrellisResponse written = answer;
        if (ctx != null) {
            for (final HttpResponseFilter filter : responseFilters) {
                filter.filter(ctx, written);
            }
        }
        final String method = ctx == null ? request.method().name() : ctx.getMethod();
        if (HEAD.equals(method) && written.getStatus() == HttpStatus.NOT_ACCEPTABLE
                && written.getEntity() == null && wantsOpenSubtype(ctx)) {
            // A client that asks only for the headers of a type whose subtype it left open names a
            // form the service cannot write, and it is told so in a document rather than by the
            // bare status the same request without the wildcard is answered with.
            written = mapFilterError(new TrellisHttpException("HTTP 406 Not Acceptable", written));
        }
        byte[] entity = materialise(written.getEntity());
        if (entity.length == 0 && written.getStatus() >= 400 && WRAPPED_METHODS.contains(method)) {
            written = containerError(written, ctx, request);
            entity = materialise(written.getEntity());
        } else if (GET.equals(method) && written.getStatus() == HttpStatus.OK
                && ContentCoding.worthEncoding(written.getHeaders().getFirst(TrellisResponse.CONTENT_TYPE))) {
            // What a cached answer depends on, and the deflated form of it, are only offered where
            // there is a representation worth deflating.
            written.getHeaders().add(TrellisResponse.VARY, "Accept-Encoding");
            if (entity.length >= ContentCoding.MINIMUM_COMPRESSED_SIZE
                    && ContentCoding.acceptsGzip(requestHeaders(ctx, request, "Accept-Encoding"))) {
                entity = ContentCoding.encode(entity);
                written.getHeaders().add("Content-Encoding", ContentCoding.GZIP);
                // A deflated answer is not the same bytes as the one the resource names, so the tag
                // it carries says which of the two it is.
                final String tag = written.getHeaders().getFirst(TrellisResponse.ETAG);
                if (tag != null && tag.endsWith("\"")) {
                    written.getHeaders().putSingle(TrellisResponse.ETAG,
                            tag.substring(0, tag.length() - 1) + "--" + ContentCoding.GZIP + '"');
                }
            }
        }
        written.getHeaders().asMap().forEach((name, values) -> response.headers().add(name, values));
        final String reason = written.getReason() != null ? written.getReason()
            : HttpStatus.reason(written.getStatus());
        response.status(reason == null ? Http.ResponseStatus.create(written.getStatus())
                : Http.ResponseStatus.create(written.getStatus(), reason));
        // The entity is handed over as the bytes it is, rather than through anything that would
        // negotiate a form for it: the LDP layer has already said what form the answer is in.
        if (HEAD.equals(method) || entity.length == 0) {
            response.send(Multi.empty());
        } else {
            response.send(Multi.singleton(DataChunk.create(entity)));
        }
    }

    /**
     * Whether the request asks to change protocol without saying so in its connection options.
     *
     * <p>A client that names an upgrade has to name it in its connection options too; one that does
     * not has written a message the server will not act on.
     *
     * @param ctx the request
     * @return true when the request names an upgrade its connection options do not
     */
    private static boolean asksToUpgrade(final TrellisRequestContext ctx) {
        final List<String> upgrades = ctx.getHeaders().get("Upgrade");
        if (upgrades.isEmpty()) {
            return false;
        }
        for (final String connection : ctx.getHeaders().get("Connection")) {
            for (final String option : connection.split(",")) {
                if ("upgrade".equalsIgnoreCase(option.trim())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Answer a message the server will not act on.
     *
     * <p>The answer says what the server itself made of the message and nothing about any resource:
     * no word about caching it, and no answer written against a target.
     *
     * @param request the request
     * @param response the answer
     * @param status the status
     * @param page whether the answer carries the page the server writes for such a message
     */
    private static void badMessage(final ServerRequest request, final ServerResponse response,
            final int status, final boolean page) {
        final String reason = HttpStatus.reason(status);
        response.status(Http.ResponseStatus.create(status, reason));
        if (!page) {
            response.send(Multi.empty());
            return;
        }
        response.headers().add("Content-Type", "text/html;charset=iso-8859-1");
        if (HEAD.equals(request.method().name())) {
            response.send(Multi.empty());
            return;
        }
        final byte[] entity = ("<h1>Bad Message " + status + "</h1><pre>reason: " + reason
                + "</pre>").getBytes(ISO_8859_1);
        response.send(Multi.singleton(DataChunk.create(entity)));
    }

    /**
     * Whether a quality in the header was written as a negative number.
     *
     * <p>A number below nothing is not a quality the grammar allows anywhere, and the web server
     * says so while it is still reading the request rather than when the layer comes to answer it.
     *
     * @param accept the values of the request's {@code Accept} header
     * @return true when one of them carries such a quality
     */
    private static boolean refusedQuality(final List<String> accept) {
        for (final String header : accept) {
            for (final String single : header.split(",", -1)) {
                final String[] parts = single.split(";", -1);
                for (int i = 1; i < parts.length; i++) {
                    final String parameter = parts[i].trim();
                    final int equals = parameter.indexOf('=');
                    if (equals <= 0 || !MediaType.QUALITY.equalsIgnoreCase(
                                parameter.substring(0, equals).trim())) {
                        continue;
                    }
                    String value = parameter.substring(equals + 1).trim();
                    if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1).trim();
                    }
                    if (value.startsWith("-") && isNumber(value)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isNumber(final String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (final NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Whether the client named a media type of a known type whose subtype it left open.
     */
    private static boolean wantsOpenSubtype(final TrellisRequestContext ctx) {
        if (ctx == null) {
            return false;
        }
        try {
            return ctx.getAcceptableMediaTypes().stream()
                .anyMatch(type -> !type.isWildcardType() && type.isWildcardSubtype());
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    private List<String> requestHeaders(final TrellisRequestContext ctx, final ServerRequest request,
            final String name) {
        if (ctx != null) {
            return ctx.getHeaders().get(name);
        }
        return request.headers().all(name);
    }

    /**
     * The answer the server writes for itself when a failure carries no document of its own.
     */
    private TrellisResponse containerError(final TrellisResponse answer, final TrellisRequestContext ctx,
            final ServerRequest request) {
        final TrellisResponse.Builder builder = answer.toBuilder();
        builder.header(TrellisResponse.CACHE_CONTROL, ErrorPage.CACHE_CONTROL);
        // The server chooses the form of its own answer by reading the Accept header its own way,
        // which is not the reading the layer negotiates a representation with: a header the layer
        // refuses still names the form the server says so in.
        final ErrorPage.Form form = ErrorPage.chosen(requestHeaders(ctx, request, TrellisResponse.ACCEPT));
        if (form != null) {
            final String target = ctx == null ? rawTarget(request) : ctx.getRawPath();
            builder.type(form.getContentType()).entity(ErrorPage.render(form, answer.getStatus(), target));
        }
        return builder.build();
    }

    private static byte[] materialise(final Object entity) {
        if (entity == null) {
            return new byte[0];
        }
        if (entity instanceof byte[]) {
            return (byte[]) entity;
        }
        if (entity instanceof StreamWriter) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                ((StreamWriter) entity).write(out);
            } catch (final IOException ex) {
                throw new IllegalStateException("Unable to write the answer", ex);
            }
            return out.toByteArray();
        }
        if (entity instanceof InputStream) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = (InputStream) entity) {
                in.transferTo(out);
            } catch (final IOException ex) {
                throw new IllegalStateException("Unable to read the answer", ex);
            }
            return out.toByteArray();
        }
        return entity.toString().getBytes(UTF_8);
    }

    /**
     * Get the methods the LDP layer implements.
     *
     * @return the method names
     */
    public static List<String> methods() {
        return METHODS;
    }
}
