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


import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.trellisldp.common.AuthContext;
import org.trellisldp.common.Headers;
import org.trellisldp.common.HttpRequestContext;
import org.trellisldp.common.MediaType;
import org.trellisldp.common.TrellisResponse;
import org.trellisldp.common.Uris;

/**
 * One request, as the filters and the LDP layer see it.
 */
class TrellisRequestContext implements HttpRequestContext {

    private final String method;
    private final String path;
    private final String rawPath;
    private final URI baseUri;
    private final Headers headers;
    private final Headers parameters;
    private final Map<String, Object> properties = new HashMap<>();

    private AuthContext authContext;
    private TrellisResponse abortResponse;
    private List<MediaType> acceptable;

    TrellisRequestContext(final String method, final String rawPath, final String query, final URI baseUri,
            final Headers headers) {
        this.method = method;
        this.rawPath = rawPath;
        this.path = Uris.decode(rawPath.startsWith("/") ? rawPath.substring(1) : rawPath);
        this.baseUri = baseUri;
        this.headers = headers;
        this.parameters = parseQuery(query);
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getRawPath() {
        return rawPath;
    }

    @Override
    public URI getBaseUri() {
        return baseUri;
    }

    @Override
    public Headers getHeaders() {
        return headers;
    }

    @Override
    public List<MediaType> getAcceptableMediaTypes() {
        if (acceptable == null) {
            acceptable = MediaType.readAccept(headers.get(TrellisResponse.ACCEPT));
        }
        return acceptable;
    }

    @Override
    public Headers getQueryParameters() {
        return parameters;
    }

    @Override
    public AuthContext getAuthContext() {
        return authContext;
    }

    @Override
    public void setAuthContext(final AuthContext context) {
        this.authContext = context;
    }

    @Override
    public Object getProperty(final String name) {
        return properties.get(name);
    }

    @Override
    public void setProperty(final String name, final Object value) {
        properties.put(name, value);
    }

    @Override
    public void abortWith(final TrellisResponse response) {
        this.abortResponse = response;
    }

    @Override
    public TrellisResponse getAbortResponse() {
        return abortResponse;
    }

    static Headers parseQuery(final String query) {
        final Headers parameters = Headers.parameters();
        if (query == null || query.isEmpty()) {
            return parameters;
        }
        for (final String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            final int equals = pair.indexOf('=');
            if (equals < 0) {
                parameters.add(Uris.decode(pair), "");
            } else {
                parameters.add(Uris.decode(pair.substring(0, equals)), Uris.decode(pair.substring(equals + 1)));
            }
        }
        return parameters;
    }

}
