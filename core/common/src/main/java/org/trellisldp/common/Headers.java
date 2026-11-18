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

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A set of named values where a name may carry more than one value.
 *
 * <p>It is used both for the headers of a request or a response, where names are matched without
 * regard to case and the order the names were added in is the order they are written in, and for
 * query parameters, where names are matched exactly.
 */
public final class Headers {

    private final Map<String, List<String>> values;
    private final Map<String, String> names;

    private Headers(final boolean ignoreCase) {
        this.values = new LinkedHashMap<>();
        this.names = ignoreCase ? new TreeMap<>(CASE_INSENSITIVE_ORDER) : null;
    }

    /**
     * Create a set of header names and values, matched without regard to case.
     *
     * @return the headers
     */
    public static Headers headers() {
        return new Headers(true);
    }

    /**
     * Create a set of query parameter names and values, matched exactly.
     *
     * @return the parameters
     */
    public static Headers parameters() {
        return new Headers(false);
    }

    /**
     * Add a value under a name.
     *
     * @param name the name
     * @param value the value
     * @return these headers
     */
    public Headers add(final String name, final String value) {
        values.computeIfAbsent(key(name, true), k -> new ArrayList<>()).add(value);
        return this;
    }

    /**
     * Replace every value under a name with a single value.
     *
     * @param name the name
     * @param value the value
     * @return these headers
     */
    public Headers putSingle(final String name, final String value) {
        final List<String> list = new ArrayList<>();
        list.add(value);
        values.put(key(name, true), list);
        return this;
    }

    /**
     * Remove every value under a name.
     *
     * @param name the name
     * @return these headers
     */
    public Headers remove(final String name) {
        final String key = key(name, false);
        if (key != null) {
            values.remove(key);
            if (names != null) {
                names.remove(name);
            }
        }
        return this;
    }

    /**
     * Get every value under a name.
     *
     * @param name the name
     * @return the values, empty when the name carries none
     */
    public List<String> get(final String name) {
        final String key = key(name, false);
        return key == null ? emptyList() : values.getOrDefault(key, emptyList());
    }

    /**
     * Get the first value under a name.
     *
     * @param name the name
     * @return the first value, or null when the name carries none
     */
    public String getFirst(final String name) {
        final List<String> list = get(name);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Get every value under a name, or a given list when the name carries none.
     *
     * @param name the name
     * @param defaultValue the list to answer when the name carries no value
     * @return the values
     */
    public List<String> getOrDefault(final String name, final List<String> defaultValue) {
        final List<String> list = get(name);
        return list.isEmpty() ? defaultValue : list;
    }

    /**
     * Test whether a name carries any value.
     *
     * @param name the name
     * @return true when it does
     */
    public boolean containsKey(final String name) {
        return !get(name).isEmpty();
    }

    /**
     * Get the names, in the order they were added.
     *
     * @return the names
     */
    public Set<String> keySet() {
        return values.keySet();
    }

    /**
     * Get the names and their values, in the order the names were added.
     *
     * @return the entries
     */
    public Map<String, List<String>> asMap() {
        return values;
    }

    private String key(final String name, final boolean create) {
        if (names == null) {
            return name;
        }
        final String existing = names.get(name);
        if (existing != null) {
            return existing;
        }
        if (create) {
            names.put(name, name);
        }
        return create ? name : null;
    }
}
