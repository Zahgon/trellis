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
 * An HTTP entity tag: the value a server gives a particular state of a resource.
 *
 * <p>A tag is either strong or weak. A weak tag is written with a {@code W/} in front of it and
 * says only that two states mean the same thing, not that they are the same bytes.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7232#section-2.3">RFC 7232</a>
 */
public final class EntityTag {

    private final String value;
    private final boolean weak;

    /**
     * Create a strong entity tag.
     *
     * @param value the tag value, without its quotes
     */
    public EntityTag(final String value) {
        this(value, false);
    }

    /**
     * Create an entity tag.
     *
     * @param value the tag value, without its quotes
     * @param weak true for a weak tag
     */
    public EntityTag(final String value, final boolean weak) {
        if (value == null) {
            throw new IllegalArgumentException("An entity tag value may not be null");
        }
        this.value = value;
        this.weak = weak;
    }

    /**
     * Read an entity tag.
     *
     * <p>A bare {@code *} is not an entity tag: it stands for every one of them, and a caller that
     * means to accept it has to look for it before asking for a tag.
     *
     * @param value the header value
     * @return the entity tag
     * @throws IllegalArgumentException when the value is not an entity tag the grammar allows
     */
    public static EntityTag valueOf(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("An entity tag may not be null");
        }
        final String trimmed = value.trim();
        final boolean weak = trimmed.startsWith("W/");
        final String tag = weak ? trimmed.substring(2) : trimmed;
        if (tag.length() < 2 || !tag.startsWith("\"") || !tag.endsWith("\"")) {
            throw new IllegalArgumentException("Unable to parse entity tag " + value);
        }
        return new EntityTag(tag.substring(1, tag.length() - 1), weak);
    }

    /**
     * Get the tag value.
     *
     * @return the value, without its quotes
     */
    public String getValue() {
        return value;
    }

    /**
     * Test whether the tag is weak.
     *
     * @return true for a weak tag
     */
    public boolean isWeak() {
        return weak;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof EntityTag)) {
            return false;
        }
        final EntityTag other = (EntityTag) obj;
        return weak == other.weak && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode() + (weak ? 1 : 0);
    }

    @Override
    public String toString() {
        return (weak ? "W/\"" : "\"") + value + '"';
    }
}
