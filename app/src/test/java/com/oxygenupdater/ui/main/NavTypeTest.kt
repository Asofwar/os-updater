package com.oxygenupdater.ui.main

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.oxygenupdater.ValueClassTestHelper
import kotlin.test.Test
import kotlin.test.assertEquals

class NavTypeTest {

    @Test
    fun `check if values are unique`() = ValueClassTestHelper.ensureUniqueValues<NavType>()

    /** Double-check input->output mapping, protects against copy-paste or refactoring mistakes */
    @Test
    fun `check from windowWidthSize`() = WindowWidthSizeClass.DefaultSizeClasses.forEach {
        assertEquals(
            NavType.from(it), when (it) {
                WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> NavType.SideRail
                else -> NavType.BottomBar
            }
        )
    }
}
