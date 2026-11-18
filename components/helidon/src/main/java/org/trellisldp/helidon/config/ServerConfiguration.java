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
package org.trellisldp.helidon.config;

import static java.util.Collections.singletonList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Configuration for the connectors the service listens on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerConfiguration {

    private List<ConnectorConfiguration> applicationConnectors = singletonList(new ConnectorConfiguration(8080));

    private List<ConnectorConfiguration> adminConnectors = singletonList(new ConnectorConfiguration(8081));

    /**
     * Get the connectors the API is served on.
     * @return the connectors
     */
    @JsonProperty
    public List<ConnectorConfiguration> getApplicationConnectors() {
        return applicationConnectors;
    }

    /**
     * Set the connectors the API is served on.
     * @param connectors the connectors
     */
    @JsonProperty
    public void setApplicationConnectors(final List<ConnectorConfiguration> connectors) {
        this.applicationConnectors = connectors;
    }

    /**
     * Get the connectors the operational surface is served on.
     * @return the connectors
     */
    @JsonProperty
    public List<ConnectorConfiguration> getAdminConnectors() {
        return adminConnectors;
    }

    /**
     * Set the connectors the operational surface is served on.
     * @param connectors the connectors
     */
    @JsonProperty
    public void setAdminConnectors(final List<ConnectorConfiguration> connectors) {
        this.adminConnectors = connectors;
    }

    /**
     * Get the port the API is served on.
     * @return the port
     */
    public int getApplicationPort() {
        return applicationConnectors.isEmpty() ? 8080 : applicationConnectors.get(0).getPort();
    }

    /**
     * Get the port the operational surface is served on.
     * @return the port
     */
    public int getAdminPort() {
        return adminConnectors.isEmpty() ? 8081 : adminConnectors.get(0).getPort();
    }

    /**
     * One connector.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConnectorConfiguration {

        private int port;

        /**
         * Create a connector on a port.
         */
        public ConnectorConfiguration() {
            this(8080);
        }

        /**
         * Create a connector on a port.
         * @param port the port
         */
        public ConnectorConfiguration(final int port) {
            this.port = port;
        }

        /**
         * Get the port.
         * @return the port
         */
        @JsonProperty
        public int getPort() {
            return port;
        }

        /**
         * Set the port.
         * @param port the port
         */
        @JsonProperty
        public void setPort(final int port) {
            this.port = port;
        }
    }
}
