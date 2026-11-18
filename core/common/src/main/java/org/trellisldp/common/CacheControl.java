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

/**
 * The value of an HTTP {@code Cache-Control} response header.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2">RFC 7234</a>
 */
public final class CacheControl {

    private boolean noCache;
    private boolean noStore;
    private boolean noTransform = true;
    private boolean mustRevalidate;
    private boolean proxyRevalidate;
    private boolean isPrivate;
    private int maxAge = -1;
    private int sMaxAge = -1;

    /**
     * Set the no-cache directive.
     *
     * @param noCache true to set it
     */
    public void setNoCache(final boolean noCache) {
        this.noCache = noCache;
    }

    /**
     * Set the no-store directive.
     *
     * @param noStore true to set it
     */
    public void setNoStore(final boolean noStore) {
        this.noStore = noStore;
    }

    /**
     * Set the no-transform directive, which is set by default.
     *
     * @param noTransform true to set it
     */
    public void setNoTransform(final boolean noTransform) {
        this.noTransform = noTransform;
    }

    /**
     * Set the must-revalidate directive.
     *
     * @param mustRevalidate true to set it
     */
    public void setMustRevalidate(final boolean mustRevalidate) {
        this.mustRevalidate = mustRevalidate;
    }

    /**
     * Set the proxy-revalidate directive.
     *
     * @param proxyRevalidate true to set it
     */
    public void setProxyRevalidate(final boolean proxyRevalidate) {
        this.proxyRevalidate = proxyRevalidate;
    }

    /**
     * Set the private directive.
     *
     * @param isPrivate true to set it
     */
    public void setPrivate(final boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    /**
     * Set the age, in seconds, a cached answer may reach.
     *
     * @param maxAge the age, or a negative value to leave the directive out
     */
    public void setMaxAge(final int maxAge) {
        this.maxAge = maxAge;
    }

    /**
     * Set the age, in seconds, a shared cache's answer may reach.
     *
     * @param sMaxAge the age, or a negative value to leave the directive out
     */
    public void setSMaxAge(final int sMaxAge) {
        this.sMaxAge = sMaxAge;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        append(builder, isPrivate, "private");
        append(builder, noCache, "no-cache");
        append(builder, noStore, "no-store");
        append(builder, noTransform, "no-transform");
        append(builder, mustRevalidate, "must-revalidate");
        append(builder, proxyRevalidate, "proxy-revalidate");
        if (maxAge != -1) {
            append(builder, true, "max-age=" + maxAge);
        }
        if (sMaxAge != -1) {
            append(builder, true, "s-maxage=" + sMaxAge);
        }
        return builder.toString();
    }

    private static void append(final StringBuilder builder, final boolean present, final String directive) {
        if (present) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(directive);
        }
    }
}
