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

import java.net.URI;
import java.util.List;

/**
 * One request as the LDP layer and the filters around it see it.
 *
 * <p>A filter may change the headers, say who the agent is, leave something behind for a later
 * filter, or end the request with an answer of its own.
 */
public interface HttpRequestContext {

    /**
     * Get the request method.
     *
     * @return the method name
     */
    String getMethod();

    /**
     * Get the path the request asked for, below the base URI and with its percent-encoding undone.
     *
     * @return the path, without a leading slash
     */
    String getPath();

    /**
     * Get the path the request asked for, below the base URI and as it was written.
     *
     * <p>A resource's name is taken from this rather than from the path with its percent-encoding
     * undone, so that a separator a client wrote as an escape is part of the name while one it
     * wrote as itself is not.
     *
     * @return the path as it was written, starting with a slash
     */
    String getRawPath();

    /**
     * Get the base URI the service answers on.
     *
     * @return the base URI, ending in a slash
     */
    URI getBaseUri();

    /**
     * Get the request headers.
     *
     * @return the headers
     */
    Headers getHeaders();

    /**
     * Get a header as one value.
     *
     * <p>A client may repeat a header, and the values it repeated are read here as one value with
     * commas between them, the way the HTTP grammar says a repeated header may always be written.
     *
     * @param name the header name
     * @return the value, or null when the request carries none
     */
    default String getHeaderString(String name) {
        final java.util.List<String> values = getHeaders().get(name);
        return values.isEmpty() ? null : String.join(",", values);
    }

    /**
     * Get the media types the client said it would take, the one it wants most first.
     *
     * @return the acceptable media types
     * @throws IllegalArgumentException when the header is not one the grammar allows
     */
    List<MediaType> getAcceptableMediaTypes();

    /**
     * Get the query parameters.
     *
     * @return the parameters
     */
    Headers getQueryParameters();

    /**
     * Get what an authentication filter found out about the agent.
     *
     * @return the authentication context, or null when no filter claimed the request
     */
    AuthContext getAuthContext();

    /**
     * Say who the agent behind the request is.
     *
     * @param context the authentication context
     */
    void setAuthContext(AuthContext context);

    /**
     * Get something a filter left behind.
     *
     * @param name the name
     * @return the value, or null when nothing was left under that name
     */
    Object getProperty(String name);

    /**
     * Leave something behind for a later filter.
     *
     * @param name the name
     * @param value the value
     */
    void setProperty(String name, Object value);

    /**
     * End the request with this answer, without reaching a handler.
     *
     * @param response the answer
     */
    void abortWith(TrellisResponse response);

    /**
     * Get the answer a filter ended the request with.
     *
     * @return the answer, or null when no filter ended the request
     */
    TrellisResponse getAbortResponse();
}
