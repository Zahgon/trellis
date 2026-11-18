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
package org.trellisldp.helidon;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading the configuration file the service is started with.
 *
 * <p>A value may name an environment variable, written as <code>${NAME:-default}</code>; the
 * variable's value is put in its place, or the default when the environment does not set it. A
 * variable with no default that the environment does not set becomes an empty value, which is how
 * the configuration files this service ships are written.
 */
public final class ConfigurationLoader {

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?\\}");

    /**
     * Read a configuration file.
     *
     * @param <T> the type of the configuration
     * @param path the path of the file
     * @param type the type of the configuration
     * @return the configuration
     */
    public static <T> T load(final Path path, final Class<T> type) {
        try {
            return mapper().readValue(substitute(Files.readString(path, UTF_8)), type);
        } catch (final IOException ex) {
            throw new UncheckedIOException("Unable to read the configuration at " + path, ex);
        }
    }

    /**
     * Put the value of every environment variable a document names in its place.
     *
     * @param document the document
     * @return the document, with every variable replaced
     */
    public static String substitute(final String document) {
        final Matcher matcher = VARIABLE.matcher(document);
        final StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            final String value = System.getenv(matcher.group(1));
            final String replacement = value != null ? value : matcher.group(2) == null ? "" : matcher.group(2);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    static ObjectMapper mapper() {
        return new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private ConfigurationLoader() {
        // prevent instantiation
    }
}
