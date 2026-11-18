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

import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;

/**
 * The things a running service holds open, and which it closes when it stops.
 *
 * <p>They are closed in the reverse of the order they were handed over, so that something which
 * was built on top of another is closed first.
 */
public class Lifecycle {

    private static final Logger LOGGER = getLogger(Lifecycle.class);

    private final Deque<AutoCloseable> managed = new ArrayDeque<>();

    /**
     * Hand something over to be closed when the service stops.
     *
     * @param closeable the thing to close
     */
    public void manage(final AutoCloseable closeable) {
        managed.push(closeable);
    }

    /**
     * Close everything handed over, newest first.
     */
    public void stop() {
        while (!managed.isEmpty()) {
            final AutoCloseable closeable = managed.pop();
            try {
                closeable.close();
            } catch (final Exception ex) {
                LOGGER.warn("Unable to close {}: {}", closeable.getClass().getName(), ex.getMessage());
            }
        }
    }
}
