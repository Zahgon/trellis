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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CacheControlTest {

    @Test
    void testTheDirectivesAreWrittenInOrder() {
        final CacheControl cacheControl = new CacheControl();
        cacheControl.setMaxAge(86400);
        cacheControl.setMustRevalidate(true);
        assertEquals("no-transform, must-revalidate, max-age=86400", cacheControl.toString());
    }

    @Test
    void testNoCacheComesBeforeNoTransform() {
        final CacheControl cacheControl = new CacheControl();
        cacheControl.setNoCache(true);
        cacheControl.setMaxAge(0);
        assertEquals("no-cache, no-transform, max-age=0", cacheControl.toString());
    }

    @Test
    void testAnAgeBelowZeroIsNotWritten() {
        final CacheControl cacheControl = new CacheControl();
        cacheControl.setNoTransform(false);
        cacheControl.setMustRevalidate(true);
        assertEquals("must-revalidate", cacheControl.toString());
    }
}
