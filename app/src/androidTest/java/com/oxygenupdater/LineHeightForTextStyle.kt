package com.oxygenupdater

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Keep in sync with [com.oxygenupdater.ui.theme.appTypography] */
@JvmInline
value class LineHeightForTextStyle private constructor(val value: TextUnit) {

    override fun toString() = "LineHeightForTextStyle." + when (this) {
        displayLarge -> "displayLarge"
        displayMedium -> "displayMedium"
        displaySmall -> "displaySmall"
        headlineLarge -> "headlineLarge"
        headlineMedium -> "headlineMedium"
        headlineSmall -> "headlineSmall"
        titleLarge -> "titleLarge"
        titleMedium -> "titleMedium"
        titleSmall -> "titleSmall"
        bodyLarge -> "bodyLarge"
        bodyMedium -> "bodyMedium"
        bodySmall -> "bodySmall"
        labelLarge -> "labelLarge"
        labelMedium -> "labelMedium"
        labelSmall -> "labelSmall"
        else -> "Invalid"
    }

    companion object {
        val displayLarge get() = LineHeightForTextStyle(64.sp)
        val displayMedium get() = LineHeightForTextStyle(52.sp)
        val displaySmall get() = LineHeightForTextStyle(44.sp)
        val headlineLarge get() = LineHeightForTextStyle(40.sp)
        val headlineMedium get() = LineHeightForTextStyle(36.sp)
        val headlineSmall get() = LineHeightForTextStyle(32.sp)
        val titleLarge get() = LineHeightForTextStyle(28.sp)
        val titleMedium get() = LineHeightForTextStyle(24.sp)
        val titleSmall get() = LineHeightForTextStyle(20.sp)
        val bodyLarge get() = LineHeightForTextStyle(24.sp)
        val bodyMedium get() = LineHeightForTextStyle(20.sp)
        val bodySmall get() = LineHeightForTextStyle(16.sp)
        val labelLarge get() = LineHeightForTextStyle(20.sp)
        val labelMedium get() = LineHeightForTextStyle(16.sp)
        val labelSmall get() = LineHeightForTextStyle(16.sp)
    }
}
