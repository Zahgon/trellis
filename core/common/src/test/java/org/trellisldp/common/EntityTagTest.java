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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntityTagTest {

    @Test
    void testAStrongTag() {
        final EntityTag tag = new EntityTag("abc");
        assertEquals("abc", tag.getValue());
        assertFalse(tag.isWeak());
        assertEquals("\"abc\"", tag.toString());
    }

    @Test
    void testAWeakTag() {
        final EntityTag tag = new EntityTag("abc", true);
        assertTrue(tag.isWeak());
        assertEquals("W/\"abc\"", tag.toString());
        assertNotEquals(new EntityTag("abc"), tag);
    }

    @Test
    void testReadingATag() {
        assertEquals(new EntityTag("abc"), EntityTag.valueOf("\"abc\""));
        assertEquals(new EntityTag("abc", true), EntityTag.valueOf("W/\"abc\""));
        assertEquals(new EntityTag(""), EntityTag.valueOf("\"\""));
    }

    @Test
    void testAStarIsNotATag() {
        assertThrows(IllegalArgumentException.class, () -> EntityTag.valueOf("*"));
    }

    @Test
    void testValuesTheGrammarDoesNotAllow() {
        assertThrows(IllegalArgumentException.class, () -> EntityTag.valueOf("abc"));
        assertThrows(IllegalArgumentException.class, () -> EntityTag.valueOf("W/"));
        assertThrows(IllegalArgumentException.class, () -> EntityTag.valueOf(null));
    }
}
