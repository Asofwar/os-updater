package com.oxygenupdater.ui

import com.oxygenupdater.R
import com.oxygenupdater.ValueClassTestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeTest {

    @Test
    fun `check if values are unique`() = ValueClassTestHelper.ensureUniqueValues<Theme>()

    /** Double-check input->output mapping, protects against copy-paste or refactoring mistakes */
    @Test
    fun `check static construction from value`() = repeat(4) {
        assertEquals(Theme.from(it).value, it)
    }

    @Test
    fun `check title & subtitle getters`() {
        Theme.Light.let {
            assertEquals(it.titleResId, R.string.theme_light)
            assertEquals(it.subtitleResId, R.string.theme_light_subtitle)
        }
        Theme.Dark.let {
            assertEquals(it.titleResId, R.string.theme_dark)
            assertEquals(it.subtitleResId, R.string.theme_dark_subtitle)
        }
        Theme.System.let {
            assertEquals(it.titleResId, R.string.theme_system)
            assertEquals(it.subtitleResId, R.string.theme_system_subtitle)
        }
        Theme.Auto.let {
            assertEquals(it.titleResId, R.string.theme_auto)
            assertEquals(it.subtitleResId, R.string.theme_auto_subtitle)
        }
    }

    @Test
    fun `check dark getter`() {
        Theme.Light.dark.let {
            assertNotNull(it)
            assertFalse(it)
        }
        Theme.Dark.dark.let {
            assertNotNull(it)
            assertTrue(it)
        }
        assertNull(Theme.System.dark)
        assertNull(Theme.Auto.dark)
    }
}
