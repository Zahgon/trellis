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

import java.util.Map;

/**
 * The HTTP status codes the LDP layer answers with, and the reason phrases that go with them.
 */
public final class HttpStatus {

    /** The request succeeded. */
    public static final int OK = 200;

    /** The request created a resource. */
    public static final int CREATED = 201;

    /** The request succeeded and the answer carries no entity. */
    public static final int NO_CONTENT = 204;

    /** The answer lives at another location, which a client should fetch with a GET. */
    public static final int SEE_OTHER = 303;

    /** The answer lives, for now, at another location. */
    public static final int FOUND = 302;

    /** The client's copy is still good. */
    public static final int NOT_MODIFIED = 304;

    /** The request was not one the server could read. */
    public static final int BAD_REQUEST = 400;

    /** The request carried no credentials the server would take. */
    public static final int UNAUTHORIZED = 401;

    /** The credentials the request carried do not allow what it asked for. */
    public static final int FORBIDDEN = 403;

    /** There is no resource at the request's target. */
    public static final int NOT_FOUND = 404;

    /** The resource does not allow the request's method. */
    public static final int METHOD_NOT_ALLOWED = 405;

    /** The resource has no form the client said it would take. */
    public static final int NOT_ACCEPTABLE = 406;

    /** The request's target is in a state the request cannot be applied to. */
    public static final int CONFLICT = 409;

    /** The resource was deleted. */
    public static final int GONE = 410;

    /** A condition the request carried was not met. */
    public static final int PRECONDITION_FAILED = 412;

    /** The entity is in a form the resource does not read. */
    public static final int UNSUPPORTED_MEDIA_TYPE = 415;

    /** An expectation the request carried is one the service will not meet. */
    public static final int EXPECTATION_FAILED = 417;

    /** The request has to carry a condition. */
    public static final int PRECONDITION_REQUIRED = 428;

    /** Something went wrong inside the server. */
    public static final int INTERNAL_SERVER_ERROR = 500;

    /** What the server itself calls a failure of its own, rather than of anything it is serving. */
    public static final String SERVER_ERROR = "Server Error";

    private static final Map<Integer, String> REASONS = Map.ofEntries(
            Map.entry(OK, "OK"),
            Map.entry(CREATED, "Created"),
            Map.entry(NO_CONTENT, "No Content"),
            Map.entry(FOUND, "Found"),
            Map.entry(SEE_OTHER, "See Other"),
            Map.entry(NOT_MODIFIED, "Not Modified"),
            Map.entry(BAD_REQUEST, "Bad Request"),
            Map.entry(UNAUTHORIZED, "Unauthorized"),
            Map.entry(FORBIDDEN, "Forbidden"),
            Map.entry(NOT_FOUND, "Not Found"),
            Map.entry(METHOD_NOT_ALLOWED, "Method Not Allowed"),
            Map.entry(NOT_ACCEPTABLE, "Not Acceptable"),
            Map.entry(CONFLICT, "Conflict"),
            Map.entry(GONE, "Gone"),
            Map.entry(PRECONDITION_FAILED, "Precondition Failed"),
            Map.entry(UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type"),
            Map.entry(EXPECTATION_FAILED, "Expectation Failed"),
            Map.entry(PRECONDITION_REQUIRED, "Precondition Required"),
            Map.entry(INTERNAL_SERVER_ERROR, "Internal Server Error"));

    /**
     * Get the reason phrase that goes with a status code.
     *
     * @param status the status code
     * @return the reason phrase, or null when the code has none
     */
    public static String reason(final int status) {
        return REASONS.get(status);
    }

    private HttpStatus() {
        // prevent instantiation
    }
}
