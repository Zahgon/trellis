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

/**
 * Something the operational surface asks whether it is well.
 */
@FunctionalInterface
public interface HealthCheck {

    /**
     * Ask whether it is well.
     *
     * @return what it answered
     */
    Result check();

    /**
     * What a check answered: whether it is well, and why, when it has something to say.
     */
    final class Result {

        private final boolean healthy;
        private final String message;

        private Result(final boolean healthy, final String message) {
            this.healthy = healthy;
            this.message = message;
        }

        /**
         * A check that is well and has nothing to say.
         *
         * @return the result
         */
        public static Result healthy() {
            return new Result(true, null);
        }

        /**
         * A check that is well and has something to say.
         *
         * @param message what it has to say
         * @return the result
         */
        public static Result healthy(final String message) {
            return new Result(true, message);
        }

        /**
         * A check that is not well.
         *
         * @param message why it is not
         * @return the result
         */
        public static Result unhealthy(final String message) {
            return new Result(false, message);
        }

        /**
         * Test whether the check is well.
         *
         * @return true when it is
         */
        public boolean isHealthy() {
            return healthy;
        }

        /**
         * Get what the check had to say.
         *
         * @return the message, or null when it had nothing to say
         */
        public String getMessage() {
            return message;
        }
    }
}
