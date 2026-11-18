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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * An answer a handler reaches for before it is done: the request cannot be carried out, or its
 * answer lives somewhere else.
 *
 * <p>The exception carries the whole response, so a handler that has something to say about a
 * failure - a link to the constraint a request broke, say - says it here.
 */
public class TrellisHttpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient TrellisResponse response;

    /**
     * Create an exception carrying a response.
     *
     * @param response the response
     */
    public TrellisHttpException(final TrellisResponse response) {
        super(describe(response.getStatus()));
        this.response = response;
    }

    private static String describe(final int status) {
        final String reason = HttpStatus.reason(status);
        return reason == null ? "HTTP " + status : "HTTP " + status + " " + reason;
    }

    /**
     * Create an exception carrying a response and a message.
     *
     * @param message the message
     * @param response the response
     */
    public TrellisHttpException(final String message, final TrellisResponse response) {
        super(message);
        this.response = response;
    }

    /**
     * Create an exception carrying a response, a message and a cause.
     *
     * @param message the message
     * @param response the response
     * @param cause the cause
     */
    public TrellisHttpException(final String message, final TrellisResponse response, final Throwable cause) {
        super(message, cause);
        this.response = response;
    }

    /**
     * Get the response.
     *
     * @return the response
     */
    public TrellisResponse getResponse() {
        return response;
    }

    /**
     * An exception carrying a bare status.
     *
     * @param status the status code
     * @return the exception
     */
    public static TrellisHttpException of(final int status) {
        return new TrellisHttpException(TrellisResponse.status(status).build());
    }

    /**
     * An exception saying there is no resource at the request's target.
     *
     * @return the exception
     */
    public static TrellisHttpException notFound() {
        return of(HttpStatus.NOT_FOUND);
    }

    /**
     * An exception saying the resource was deleted.
     *
     * @return the exception
     */
    public static TrellisHttpException gone() {
        return of(HttpStatus.GONE);
    }

    /**
     * An exception saying the resource has no form the client will take.
     *
     * @return the exception
     */
    public static TrellisHttpException notAcceptable() {
        return of(HttpStatus.NOT_ACCEPTABLE);
    }

    /**
     * An exception saying the entity is in a form the resource does not read.
     *
     * @return the exception
     */
    public static TrellisHttpException unsupportedMediaType() {
        return of(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * An exception saying the request was not one the server could read.
     *
     * @param message the message
     * @return the exception
     */
    public static TrellisHttpException badRequest(final String message) {
        return new TrellisHttpException(message, TrellisResponse.status(HttpStatus.BAD_REQUEST).build());
    }

    /**
     * An exception saying the resource does not allow the request's method.
     *
     * <p>The methods are named once, in a single value: the ones after the first are a set, and the
     * order they are written in is the order a set of those names has; the first is named last.
     *
     * @param method a method the resource does allow
     * @param others the other methods the resource allows
     * @return the exception
     */
    public static TrellisHttpException methodNotAllowed(final String method, final List<String> others) {
        final String allow = String.join(",", new LinkedHashSet<>(new HashSet<>(others))) + "," + method;
        return new TrellisHttpException(TrellisResponse.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(TrellisResponse.ALLOW, allow).build());
    }

    /**
     * An exception saying the request carried no credentials the server would take.
     *
     * @param challenges the challenges to offer the client
     * @return the exception
     */
    public static TrellisHttpException notAuthorized(final List<String> challenges) {
        final TrellisResponse.Builder builder = TrellisResponse.status(HttpStatus.UNAUTHORIZED);
        challenges.forEach(challenge -> builder.header(TrellisResponse.WWW_AUTHENTICATE, challenge));
        return new TrellisHttpException(builder.build());
    }

    /**
     * An exception saying the credentials the request carried do not allow what it asked for.
     *
     * @return the exception
     */
    public static TrellisHttpException forbidden() {
        return of(HttpStatus.FORBIDDEN);
    }

    /**
     * An exception saying the answer lives somewhere else.
     *
     * @param status the status code
     * @param location where the answer lives
     * @return the exception
     */
    public static TrellisHttpException redirect(final int status, final java.net.URI location) {
        return new TrellisHttpException(TrellisResponse.status(status).location(location).build());
    }
}
