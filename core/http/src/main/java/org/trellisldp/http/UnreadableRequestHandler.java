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

import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.common.HttpConstants.GET;
import static org.trellisldp.common.HttpConstants.HEAD;
import static org.trellisldp.common.HttpConstants.POST;

import io.helidon.common.http.Http;
import io.helidon.webserver.DirectHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.trellisldp.common.HttpStatus;

/**
 * The answer for a request the web server could not read far enough to route.
 *
 * <p>A header the HTTP grammar does not allow - a media type that is not one, say - leaves the
 * server with nothing to hand the LDP layer. The answer is then the server's own: the bare status,
 * the word that a cached copy of it is worth nothing, and what the server says about itself in
 * whichever of its own forms the client named.
 */
class UnreadableRequestHandler implements DirectHandler {

    private static final Logger LOGGER = getLogger(UnreadableRequestHandler.class);
    private static final List<String> WRAPPED_METHODS = List.of(GET, HEAD, POST);

    @Override
    public TransportResponse handle(final TransportRequest request, final EventType eventType,
            final Http.ResponseStatus status, final String message) {
        LOGGER.debug("Unreadable request: {}", message);
        final TransportResponse.Builder builder = TransportResponse.builder()
            .status(Http.ResponseStatus.create(HttpStatus.BAD_REQUEST,
                        HttpStatus.reason(HttpStatus.BAD_REQUEST)));
        if (!WRAPPED_METHODS.contains(request.method())) {
            return builder.build();
        }
        builder.header(org.trellisldp.common.TrellisResponse.CACHE_CONTROL, ErrorPage.CACHE_CONTROL);
        final ErrorPage.Form form = ErrorPage.chosen(accepted(request.headers()));
        if (form != null) {
            builder.header(org.trellisldp.common.TrellisResponse.CONTENT_TYPE, form.getContentType());
            if (!HEAD.equals(request.method())) {
                builder.entity(ErrorPage.render(form, HttpStatus.BAD_REQUEST, target(request.uri())));
            }
        }
        return builder.build();
    }

    private static List<String> accepted(final Map<String, List<String>> headers) {
        return headers.getOrDefault("Accept", headers.getOrDefault("accept", Collections.emptyList()));
    }

    private static String target(final String uri) {
        final int query = uri.indexOf('?');
        return query < 0 ? uri : uri.substring(0, query);
    }
}
