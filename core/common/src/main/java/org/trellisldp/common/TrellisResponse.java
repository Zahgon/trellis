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

import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * An HTTP response as the LDP layer builds it: a status, a set of headers and an entity.
 *
 * <p>The entity is either absent, a string, a block of bytes or something that writes itself to
 * the connection. Nothing here knows how the answer travels; that is the serving layer's work.
 */
public final class TrellisResponse {

    /** The name of the header naming the methods a resource allows. */
    public static final String ALLOW = "Allow";

    /** The name of the header naming the media type of an entity. */
    public static final String CONTENT_TYPE = "Content-Type";

    /** The name of the header naming where an entity may also be found. */
    public static final String CONTENT_LOCATION = "Content-Location";

    /** The name of the header carrying a resource's entity tag. */
    public static final String ETAG = "ETag";

    /** The name of the header naming when a resource last changed. */
    public static final String LAST_MODIFIED = "Last-Modified";

    /** The name of the header carrying typed links. */
    public static final String LINK = "Link";

    /** The name of the header naming where a request's answer lives. */
    public static final String LOCATION = "Location";

    /** The name of the header naming what a cached answer depends on. */
    public static final String VARY = "Vary";

    /** The name of the header naming how a cached answer may be used. */
    public static final String CACHE_CONTROL = "Cache-Control";

    /** The name of the header carrying an authentication challenge. */
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    /** The name of the header carrying a client's credentials. */
    public static final String AUTHORIZATION = "Authorization";

    /** The name of the conditional header matching on an entity tag. */
    public static final String IF_MATCH = "If-Match";

    /** The name of the conditional header matching on anything but an entity tag. */
    public static final String IF_NONE_MATCH = "If-None-Match";

    /** The name of the conditional header matching on a modification date. */
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";

    /** The name of the conditional header matching on anything but a modification date. */
    public static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";

    /** The name of the header naming the media types a client will take. */
    public static final String ACCEPT = "Accept";

    private final int status;
    private final String reason;
    private final Headers headers;
    private final Object entity;

    private TrellisResponse(final int status, final String reason, final Headers headers, final Object entity) {
        this.status = status;
        this.reason = reason;
        this.headers = headers;
        this.entity = entity;
    }

    /**
     * Start building a response with the given status.
     *
     * @param status the status code
     * @return a builder
     */
    public static Builder status(final int status) {
        return new Builder(status);
    }

    /**
     * Start building a response that says the request succeeded.
     *
     * @return a builder
     */
    public static Builder ok() {
        return new Builder(HttpStatus.OK);
    }

    /**
     * Start building a response that carries no entity.
     *
     * @return a builder
     */
    public static Builder noContent() {
        return new Builder(HttpStatus.NO_CONTENT);
    }

    /**
     * Start building a response that says a client's copy is still good.
     *
     * @return a builder
     */
    public static Builder notModified() {
        return new Builder(HttpStatus.NOT_MODIFIED);
    }

    /**
     * Get the status code.
     *
     * @return the status code
     */
    public int getStatus() {
        return status;
    }

    /**
     * Get the phrase that goes with the status.
     *
     * @return the reason phrase, or null for the one the status always carries
     */
    public String getReason() {
        return reason;
    }

    /**
     * Get the headers.
     *
     * @return the headers
     */
    public Headers getHeaders() {
        return headers;
    }

    /**
     * Get the entity.
     *
     * @return the entity, or null when the response carries none
     */
    public Object getEntity() {
        return entity;
    }

    /**
     * Test whether the status says the request succeeded.
     *
     * @return true for a status between 200 and 299
     */
    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }

    /**
     * Start building a response from this one.
     *
     * @return a builder carrying this response's status, headers and entity
     */
    public Builder toBuilder() {
        final Builder builder = new Builder(status).reason(reason);
        headers.asMap().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        return builder.entity(entity);
    }

    /**
     * Write an instant the way a header carries a date.
     *
     * @param instant the instant
     * @return the date in the format HTTP uses
     */
    public static String formatDate(final Instant instant) {
        return RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(instant, UTC));
    }

    /**
     * A builder of responses.
     */
    public static final class Builder {

        private int status;
        private String reason;
        private final Headers headers = Headers.headers();
        private Object entity;

        private Builder(final int status) {
            this.status = status;
        }

        /**
         * Set the status.
         *
         * @param status the status code
         * @return this builder
         */
        public Builder status(final int status) {
            this.status = status;
            return this;
        }

        /**
         * Set the phrase that goes with the status.
         *
         * @param reason the reason phrase, or null for the one the status always carries
         * @return this builder
         */
        public Builder reason(final String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Add a header.
         *
         * @param name the header name
         * @param value the header value
         * @return this builder
         */
        public Builder header(final String name, final Object value) {
            if (value instanceof Instant) {
                headers.add(name, formatDate((Instant) value));
            } else {
                headers.add(name, value.toString());
            }
            return this;
        }

        /**
         * Add a typed link.
         *
         * @param uri the link target
         * @param rel the relation type
         * @return this builder
         */
        public Builder link(final String uri, final String rel) {
            return header(LINK, LinkHeader.fromUri(uri).rel(rel).build());
        }

        /**
         * Add typed links.
         *
         * @param links the links
         * @return this builder
         */
        public Builder links(final List<LinkHeader> links) {
            links.forEach(link -> header(LINK, link));
            return this;
        }

        /**
         * Set the media type of the entity.
         *
         * @param type the media type
         * @return this builder
         */
        public Builder type(final String type) {
            headers.putSingle(CONTENT_TYPE, type);
            return this;
        }

        /**
         * Set when the resource last changed.
         *
         * @param instant the modification date
         * @return this builder
         */
        public Builder lastModified(final Instant instant) {
            headers.putSingle(LAST_MODIFIED, formatDate(instant));
            return this;
        }

        /**
         * Set the resource's entity tag.
         *
         * @param tag the entity tag
         * @return this builder
         */
        public Builder tag(final EntityTag tag) {
            headers.putSingle(ETAG, tag.toString());
            return this;
        }

        /**
         * Set where the answer lives.
         *
         * @param location the location
         * @return this builder
         */
        public Builder location(final URI location) {
            headers.putSingle(LOCATION, location.toString());
            return this;
        }

        /**
         * Set where the entity may also be found.
         *
         * @param location the location
         * @return this builder
         */
        public Builder contentLocation(final URI location) {
            headers.putSingle(CONTENT_LOCATION, location.toString());
            return this;
        }

        /**
         * Set the entity.
         *
         * @param entity the entity: a string, a block of bytes or something that writes itself
         * @return this builder
         */
        public Builder entity(final Object entity) {
            this.entity = entity;
            return this;
        }

        /**
         * Get the headers added so far.
         *
         * @return the headers
         */
        public Headers getHeaders() {
            return headers;
        }

        /**
         * Get the status set so far.
         *
         * @return the status code
         */
        public int getStatus() {
            return status;
        }

        /**
         * Build the response.
         *
         * @return the response
         */
        public TrellisResponse build() {
            return new TrellisResponse(status, reason, headers, entity);
        }
    }
}
