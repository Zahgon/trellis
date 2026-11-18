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
package org.trellisldp.helidon.app;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.trellisldp.helidon.config.DatabaseConfiguration;
import org.trellisldp.helidon.config.TrellisConfiguration;

class AppConfiguration extends TrellisConfiguration {

    private String mementos;

    private String binaries;

    private String namespaces;

    private boolean isVersioningEnabled = true;

    private int levels = 3;

    private int length =  2;

    private String resourceLocation;

    private DatabaseConfiguration database = new DatabaseConfiguration();

    /**
     * Set the database configuration.
     * @param database the database configuration
     */
    @JsonProperty("database")
    public void setDatabase(final DatabaseConfiguration database) {
        this.database = database;
    }

    /**
     * Get the database configuration.
     * @return the database configuration
     */
    @JsonProperty("database")
    public DatabaseConfiguration getDatabase() {
        return database;
    }

    /**
     * Get the Memento configuration.
     * @return the Memento resource location
     */
    @JsonProperty
    public String getMementos() {
        return mementos;
    }

    /**
     * Set the Memento resource configuration.
     * @param config the Memento resource location
     */
    @JsonProperty
    public void setMementos(final String config) {
        this.mementos = config;
    }

    /**
     * Get whether versioning is enabled.
     * @return true if memento versioning is enabled; false otherwise
     */
    @JsonProperty
    public boolean getIsVersioningEnabled() {
        return isVersioningEnabled;
    }

    /**
     * Set whether mementos are enabled.
     * @param isVersioningEnabled whether versioning is enabled
     */
    @JsonProperty
    public void setIsVersioningEnabled(final boolean isVersioningEnabled) {
        this.isVersioningEnabled = isVersioningEnabled;
    }

    /**
     * Get the binary configuration.
     * @return the binary configuration
     */
    @JsonProperty
    public String getBinaries() {
        return binaries;
    }

    /**
     * Set the binary configuration.
     * @param config the binary configuration
     */
    @JsonProperty
    public void setBinaries(final String config) {
        this.binaries = config;
    }

    /**
     * Get the namespaces filename.
     * @return the namespaces filename
     */
    @JsonProperty
    public String getNamespaces() {
        return namespaces;
    }

    /**
     * Set the namespace filename.
     * @param filename the namespaces filename
     */
    @JsonProperty
    public void setNamespaces(final String filename) {
        this.namespaces = filename;
    }

    /**
     * Get the number of levels of hierarchy for binaries.
     * @return the number of levels of hierarchy
     */
    @JsonProperty
    public int getBinaryHierarchyLevels() {
        return levels;
    }

    /**
     * Set the number of levels of hierarchy for binaries.
     * @param levels the number of levels of hierarchy
     */
    @JsonProperty
    public void setBinaryHierarchyLevels(final int levels) {
        this.levels = levels;
    }

    /**
     * Get the length of each level of hierarchy for binaries.
     * @return the length of each hierarchy segment
     */
    @JsonProperty
    public int getBinaryHierarchyLength() {
        return length;
    }

    /**
     * Set the length of each level of hierarchy for binaries.
     * @param length the length of each hierarchy segment
     */
    @JsonProperty
    public void setBinaryHierarchyLength(final int length) {
        this.length = length;
    }

    /**
     * Get the resource location, which may name a local dataset or a remote triplestore.
     * @return the resource location, or null when the resources live in the database
     */
    @JsonProperty("resources")
    public String getResources() {
        return resourceLocation;
    }

    /**
     * Set the resource location.
     * @param resourceLocation the resource location
     */
    @JsonProperty("resources")
    public void setResources(final String resourceLocation) {
        this.resourceLocation = resourceLocation;
    }
}
