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
 * Something that adds to an answer after a handler has built it.
 */
@FunctionalInterface
public interface HttpResponseFilter {

    /**
     * Add to the answer.
     *
     * @param request the request the answer belongs to
     * @param response the answer, which the filter may add headers to
     */
    void filter(HttpRequestContext request, TrellisResponse response);
}
