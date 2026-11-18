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

import static java.util.Collections.emptyMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Configuration for the relational database a deployment may keep its resources in.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DatabaseConfiguration {

    private String driverClass;
    private String user;
    private String password;
    private String url;
    private Map<String, String> properties = emptyMap();

    /**
     * Get the name of the driver class.
     * @return the driver class
     */
    @JsonProperty
    public String getDriverClass() {
        return driverClass;
    }

    /**
     * Set the name of the driver class.
     * @param driverClass the driver class
     */
    @JsonProperty
    public void setDriverClass(final String driverClass) {
        this.driverClass = driverClass;
    }

    /**
     * Get the database user.
     * @return the user
     */
    @JsonProperty
    public String getUser() {
        return user;
    }

    /**
     * Set the database user.
     * @param user the user
     */
    @JsonProperty
    public void setUser(final String user) {
        this.user = user;
    }

    /**
     * Get the database password.
     * @return the password
     */
    @JsonProperty
    public String getPassword() {
        return password;
    }

    /**
     * Set the database password.
     * @param password the password
     */
    @JsonProperty
    public void setPassword(final String password) {
        this.password = password;
    }

    /**
     * Get the database URL.
     * @return the URL
     */
    @JsonProperty
    public String getUrl() {
        return url;
    }

    /**
     * Set the database URL.
     * @param url the URL
     */
    @JsonProperty
    public void setUrl(final String url) {
        this.url = url;
    }

    /**
     * Get any further connection properties.
     * @return the properties
     */
    @JsonProperty
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Set further connection properties.
     * @param properties the properties
     */
    @JsonProperty
    public void setProperties(final Map<String, String> properties) {
        this.properties = properties;
    }
}
