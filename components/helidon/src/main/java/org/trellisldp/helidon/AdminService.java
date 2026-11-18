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
package org.trellisldp.helidon;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static org.trellisldp.common.HttpConstants.GET;
import static org.trellisldp.common.HttpConstants.HEAD;
import static org.trellisldp.common.HttpConstants.DELETE;
import static org.trellisldp.common.HttpConstants.OPTIONS;
import static org.trellisldp.common.HttpConstants.PATCH;
import static org.trellisldp.common.HttpConstants.PUT;
import static org.trellisldp.common.HttpConstants.POST;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

/**
 * The operational surface, which is served on its own connector.
 *
 * <p>It answers what the service is: whether it is listening, whether it is well, what it counts,
 * what its threads are doing, and which tasks it will run. Anything else on that connector is not
 * found. What each target answers to each method, and what media type it says the answer is in, is
 * what the surface has always answered; so is the page the server writes about itself, which it
 * writes for the three methods a client reads a resource with and for no other.
 */
public class AdminService implements Service {

    private static final String CACHE_CONTROL = "must-revalidate,no-cache,no-store";
    private static final String TEXT_PLAIN_WRITTEN = "text/plain;charset=iso-8859-1";
    private static final String TEXT_PLAIN = "text/plain";
    private static final String TEXT_PLAIN_UTF8 = "text/plain;charset=utf-8";
    private static final String TEXT_HTML = "text/html";
    private static final String APPLICATION_JSON = "application/json";
    private static final String ERROR_PAGE_TYPE = "text/html;charset=iso-8859-1";
    private static final DateTimeFormatter TIMESTAMP = ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final List<String> READ_METHODS = List.of(GET, HEAD, POST);
    /** The methods the surface is served through; anything else never reaches a target. */
    private static final List<String> SERVED_METHODS = List.of(GET, HEAD, POST, PUT, DELETE, OPTIONS, PATCH);
    /** The smallest answer this surface writes back compressed. */
    private static final int MINIMUM_COMPRESSED_SIZE = 256;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, HealthCheck> healthChecks = new TreeMap<>();

    /**
     * Register a health check.
     *
     * @param name the name the check answers under
     * @param check the check
     * @return this service
     */
    public AdminService register(final String name, final HealthCheck check) {
        healthChecks.put(name, check);
        return this;
    }

    @Override
    public void update(final Routing.Rules rules) {
        rules.any((Handler) this::handle);
    }

    void handle(final ServerRequest request, final ServerResponse response) {
        final String path = rawTarget(request);
        final String method = request.method().name();
        final boolean pretty = pretty(request);
        if (!SERVED_METHODS.contains(method)) {
            // A method the surface is not served through is refused before anything looks at the
            // target: there is no target on this connector that answers it.
            error(request, response, 405, "Method Not Allowed",
                    "HTTP method " + method + " is not supported by this URL");
            return;
        }
        if (PATCH.equals(method)) {
            // The surface's own targets know nothing of this method, while a target that is not one
            // of them is not found whatever the method is.
            if (isKnown(path)) {
                error(request, response, 501, "Not Implemented",
                        "HTTP method " + method + " is not supported by this URL");
            } else {
                notFound(request, response);
            }
            return;
        }
        if (OPTIONS.equals(method) && isKnown(path)) {
            response.status(Http.Status.OK_200);
            response.headers().add("Allow", path.startsWith("/tasks")
                    ? "GET, HEAD, POST, TRACE, OPTIONS" : "GET, HEAD, TRACE, OPTIONS");
            response.send();
            return;
        }
        switch (path) {
            case "/":
                read(request, response, TEXT_HTML, TEXT_HTML, menu(), true);
                break;
            case "/ping":
                read(request, response, TEXT_PLAIN_WRITTEN, TEXT_PLAIN, "pong\n".getBytes(ISO_8859_1), true);
                break;
            case "/healthcheck":
                read(request, response, APPLICATION_JSON, APPLICATION_JSON, healthcheck(pretty), true);
                break;
            case "/metrics":
                read(request, response, APPLICATION_JSON, APPLICATION_JSON, metrics(pretty), true);
                break;
            case "/threads":
                read(request, response, TEXT_PLAIN, TEXT_PLAIN, threads(), true);
                break;
            case "/tasks":
                // The list of tasks is served without saying anything about caching it, and a
                // request to run something is a request for a task that is not this target.
                if (POST.equals(method)) {
                    notFound(request, response);
                } else {
                    read(request, response, TEXT_PLAIN_WRITTEN, TEXT_PLAIN_UTF8,
                            "gc\nlog-level\n".getBytes(ISO_8859_1), false);
                }
                break;
            default:
                if (path.startsWith("/tasks/")) {
                    task(request, response, path.substring("/tasks/".length()));
                } else {
                    notFound(request, response);
                }
                break;
        }
    }

    private static final List<String> TASKS = List.of("gc", "log-level");

    private static boolean isKnown(final String path) {
        return List.of("/", "/ping", "/healthcheck", "/metrics", "/threads", "/tasks").contains(path)
            || path.startsWith("/tasks/");
    }

    /**
     * Answer a target a client reads.
     */
    private void read(final ServerRequest request, final ServerResponse response, final String writtenType,
            final String headerType, final byte[] entity, final boolean uncacheable) {
        final String method = request.method().name();
        if (!GET.equals(method) && !HEAD.equals(method)) {
            methodNotAllowed(request, response);
            return;
        }
        response.status(Http.Status.OK_200);
        response.headers().add("Content-Type", HEAD.equals(method) ? headerType : writtenType);
        if (uncacheable) {
            response.headers().add("Cache-Control", CACHE_CONTROL);
        }
        if (HEAD.equals(method)) {
            response.send();
            return;
        }
        response.headers().add("Vary", "Accept-Encoding");
        if (entity.length >= MINIMUM_COMPRESSED_SIZE && acceptsGzip(request)) {
            // An answer worth compressing is compressed here too, by the same rule the API is
            // served by: the client named the coding, and there is enough of the answer to matter.
            response.headers().add("Content-Encoding", "gzip");
            response.send(deflate(entity));
            return;
        }
        response.send(entity);
    }

    /**
     * Whether the client named the coding that deflates an answer, and did not refuse it.
     */
    private static boolean acceptsGzip(final ServerRequest request) {
        for (final String header : request.headers().all("Accept-Encoding")) {
            for (final String single : header.split(",")) {
                final String[] parts = single.trim().split(";");
                if (!"gzip".equalsIgnoreCase(parts[0].trim())) {
                    continue;
                }
                for (int i = 1; i < parts.length; i++) {
                    final String parameter = parts[i].trim();
                    if (parameter.startsWith("q=") && isZero(parameter.substring(2).trim())) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isZero(final String quality) {
        try {
            return Double.parseDouble(quality) == 0;
        } catch (final NumberFormatException ex) {
            return false;
        }
    }

    private static byte[] deflate(final byte[] entity) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(entity);
        } catch (final IOException ex) {
            throw new IllegalStateException("Unable to deflate an answer", ex);
        }
        return out.toByteArray();
    }

    /**
     * Run a task, or say the target does not answer the method.
     */
    private void task(final ServerRequest request, final ServerResponse response, final String name) {
        final String method = request.method().name();
        if (!POST.equals(method) && !GET.equals(method) && !HEAD.equals(method)) {
            // The target answers whichever task it names; what it does not answer is the method.
            error(request, response, 405, "Method Not Allowed",
                    "HTTP method " + method + " is not supported by this URL");
            return;
        }
        if (!TASKS.contains(name)) {
            notFound(request, response);
            return;
        }
        if (!POST.equals(method)) {
            // A task is run rather than read, and what it answers to anything else says only that.
            error(request, response, 405, "Method Not Allowed", "Method Not Allowed");
            return;
        }
        response.status(Http.Status.OK_200);
        response.headers().add("Content-Type", TEXT_PLAIN_UTF8);
        if ("gc".equals(name)) {
            System.gc();
            response.send("Running GC...\nDone!\n".getBytes(UTF_8));
        } else {
            response.send("".getBytes(UTF_8));
        }
    }

    private void methodNotAllowed(final ServerRequest request, final ServerResponse response) {
        error(request, response, 405, "Method Not Allowed",
                "HTTP method " + request.method().name() + " is not supported by this URL");
    }

    private void notFound(final ServerRequest request, final ServerResponse response) {
        error(request, response, 404, "Not Found", "Not Found");
    }

    /**
     * The answer for a target that does not answer: the bare status, and the page the server writes
     * about itself for the methods a client reads a resource with.
     */
    private void error(final ServerRequest request, final ServerResponse response, final int status,
            final String reason, final String message) {
        final String method = request.method().name();
        response.status(Http.ResponseStatus.create(status, reason));
        if (!READ_METHODS.contains(method)) {
            response.send();
            return;
        }
        response.headers().add("Cache-Control", CACHE_CONTROL);
        response.headers().add("Content-Type", ERROR_PAGE_TYPE);
        if (HEAD.equals(method)) {
            response.send();
            return;
        }
        final String page = "<html>\n<head>\n"
            + "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\"/>\n"
            + "<title>Error " + status + ' ' + message + "</title>\n"
            + "</head>\n<body><h2>HTTP ERROR " + status + ' ' + message + "</h2>\n"
            + "<table>\n"
            + "<tr><th>URI:</th><td>" + rawTarget(request) + "</td></tr>\n"
            + "<tr><th>STATUS:</th><td>" + status + "</td></tr>\n"
            + "<tr><th>MESSAGE:</th><td>" + message + "</td></tr>\n"
            + "<tr><th>SERVLET:</th><td>trellis-admin</td></tr>\n"
            + "</table>\n\n</body>\n</html>\n";
        response.send(page.getBytes(ISO_8859_1));
    }

    /**
     * The target of a request as it was written, without its query.
     *
     * <p>A target that starts with two slashes names no authority here; it is a path like any other.
     *
     * @param request the request
     * @return the raw target
     */
    static String rawTarget(final ServerRequest request) {
        final java.net.URI uri = request.uri();
        final String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        return uri.getRawAuthority() == null ? path : "//" + uri.getRawAuthority() + path;
    }

    private static boolean pretty(final ServerRequest request) {
        final String query = request.uri().getRawQuery();
        return query != null && query.contains("pretty=true");
    }

    private byte[] menu() {
        return ("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n"
            + "        \"http://www.w3.org/TR/html4/loose.dtd\">\n"
            + "<html>\n<head>\n  <title>Metrics</title>\n</head>\n<body>\n"
            + "  <h1>Operational Menu</h1>\n  <ul>\n"
            + "    <li><a href=\"/metrics?pretty=true\">Metrics</a></li>\n"
            + "    <li><a href=\"/ping\">Ping</a></li>\n"
            + "    <li><a href=\"/threads\">Threads</a></li>\n"
            + "    <li><a href=\"/healthcheck?pretty=true\">Healthcheck</a></li>\n"
            + "  </ul>\n</body>\n</html>\n").getBytes(UTF_8);
    }

    private byte[] healthcheck(final boolean pretty) {
        final ObjectNode document = MAPPER.createObjectNode();
        healthChecks.forEach((name, check) -> {
            final long started = System.currentTimeMillis();
            final HealthCheck.Result result = check.check();
            final ObjectNode entry = document.putObject(name);
            entry.put("healthy", result.isHealthy());
            if (result.getMessage() != null) {
                entry.put("message", result.getMessage());
            }
            entry.put("duration", System.currentTimeMillis() - started);
            entry.put("timestamp", timestamp());
        });
        return write(document, pretty);
    }

    private byte[] metrics(final boolean pretty) {
        final Runtime runtime = Runtime.getRuntime();
        final ObjectNode document = MAPPER.createObjectNode();
        document.put("version", "4.0.0");
        final ObjectNode gauges = document.putObject("gauges");
        gauges.putObject("jvm.memory.total.used").put("value", runtime.totalMemory() - runtime.freeMemory());
        gauges.putObject("jvm.memory.total.max").put("value", runtime.maxMemory());
        gauges.putObject("jvm.threads.count").put("value", Thread.activeCount());
        document.putObject("counters");
        document.putObject("histograms");
        document.putObject("meters");
        document.putObject("timers");
        return write(document, pretty);
    }

    private static byte[] write(final ObjectNode document, final boolean pretty) {
        try {
            return pretty ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(document)
                : MAPPER.writeValueAsBytes(document);
        } catch (final JsonProcessingException ex) {
            throw new IllegalStateException("Unable to write the operational document", ex);
        }
    }

    private byte[] threads() {
        final StringBuilder dump = new StringBuilder();
        for (final ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            dump.append(info.toString());
        }
        return dump.toString().getBytes(UTF_8);
    }

    private static String timestamp() {
        return TIMESTAMP.format(ZonedDateTime.ofInstant(Instant.now(), UTC));
    }
}
