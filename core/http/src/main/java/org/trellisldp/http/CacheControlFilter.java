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
package org.trellisldp.http;

import static org.eclipse.microprofile.config.ConfigProvider.getConfig;
import static org.trellisldp.common.HttpConstants.GET;
import static org.trellisldp.common.HttpConstants.HEAD;
import static org.trellisldp.common.TrellisResponse.CACHE_CONTROL;

import org.eclipse.microprofile.config.Config;
import org.trellisldp.common.CacheControl;
import org.trellisldp.common.HttpRequestContext;
import org.trellisldp.common.HttpResponseFilter;
import org.trellisldp.common.TrellisResponse;

/**
 * An {@link HttpResponseFilter} that adds Cache-Control headers to all
 * {@code GET} responses.
 *
 * @author acoburn
 */
public class CacheControlFilter implements HttpResponseFilter {

    /** The configuration key for setting a cache-control max-age header. */
    public static final String CONFIG_HTTP_CACHE_MAX_AGE = "trellis.http.cache-max-age";
    /** The configuration key for setting a cache-control must-revalidate header. */
    public static final String CONFIG_HTTP_CACHE_REVALIDATE = "trellis.http.cache-revalidate";
    /** The configuration key for setting a cache-control no-cache header. */
    public static final String CONFIG_HTTP_CACHE_NOCACHE = "trellis.http.cache-nocache";

    private int maxAge;
    private boolean mustRevalidate;
    private boolean noCache;

    /**
     * Create a CacheControl decorator.
     */
    public CacheControlFilter() {
        final Config config = getConfig();
        this.maxAge = config.getOptionalValue(CONFIG_HTTP_CACHE_MAX_AGE, Integer.class).orElse(86400);
        this.mustRevalidate = config.getOptionalValue(CONFIG_HTTP_CACHE_REVALIDATE, Boolean.class).orElse(Boolean.TRUE);
        this.noCache = config.getOptionalValue(CONFIG_HTTP_CACHE_NOCACHE, Boolean.class).orElse(Boolean.FALSE);
    }

    /**
     * Set the cache age.
     * @param maxAge the cache age in seconds
     */
    public void setMaxAge(final int maxAge) {
        this.maxAge = maxAge;
    }

    /**
     * Set the revalidate flag.
     * @param mustRevalidate true if cliens must revalidate
     */
    public void setMustRevalidate(final boolean mustRevalidate) {
        this.mustRevalidate = mustRevalidate;
    }

    /**
     * Set the no-cache flag.
     * @param noCache true if the no-cache flag is to be returned
     */
    public void setNoCache(final boolean noCache) {
        this.noCache = noCache;
    }

    @Override
    public void filter(final HttpRequestContext req, final TrellisResponse res) {
        if ((GET.equals(req.getMethod()) || HEAD.equals(req.getMethod()))
                && res.isSuccessful() && maxAge > 0) {
            final CacheControl cc = new CacheControl();
            cc.setMaxAge(maxAge);
            cc.setMustRevalidate(mustRevalidate);
            cc.setNoCache(noCache);
            res.getHeaders().add(CACHE_CONTROL, cc.toString());
        }
    }
}
