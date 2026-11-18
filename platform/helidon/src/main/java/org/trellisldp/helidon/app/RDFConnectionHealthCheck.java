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

import org.apache.jena.rdfconnection.RDFConnection;
import org.trellisldp.helidon.HealthCheck;

/**
 * Check whether the connection to the RDF store is open.
 */
public class RDFConnectionHealthCheck implements HealthCheck {

    private final RDFConnection rdfConnection;

    /**
     * Create an object that checks the health of an RDF Connection.
     * @param rdfConnection the RDF Connection
     */
    public RDFConnectionHealthCheck(final RDFConnection rdfConnection) {
        this.rdfConnection = rdfConnection;
    }

    @Override
    public Result check() {
        if (rdfConnection == null) {
            return Result.unhealthy("No RDF Connection defined!");
        } else if (rdfConnection.isClosed()) {
            return Result.unhealthy("RDF Connection has been closed!");
        }
        return Result.healthy("RDF Connection is open.");
    }
}
