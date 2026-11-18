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

import java.security.Principal;

/**
 * What an authentication filter found out about the agent behind a request.
 */
public interface AuthContext {

    /** The name of the scheme a client uses when it sends a username and a password. */
    String BASIC_AUTH = "BASIC";

    /**
     * Get the agent behind the request.
     *
     * @return the agent, or null when the request carried no credentials
     */
    Principal getUserPrincipal();

    /**
     * Test whether the agent holds a role.
     *
     * @param role the role
     * @return true when the agent holds it
     */
    boolean isUserInRole(String role);

    /**
     * Test whether the request arrived over a secured connection.
     *
     * @return true when it did
     */
    boolean isSecure();

    /**
     * Get the name of the scheme the agent's credentials were written in.
     *
     * @return the scheme name
     */
    String getAuthenticationScheme();
}
