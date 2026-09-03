package com.oxygenupdater.ui.update

import androidx.compose.runtime.Immutable

@Immutable
@JvmInline
value class DownloadAction private constructor(val value: Int) {

    override fun toString() = "DownloadAction." + when (this) {
        Enqueue -> "Enqueue"
        Pause -> "Pause"
        Cancel -> "Cancel"
        Delete -> "Delete"
        else -> "Invalid"
    }

    companion object {
        val Enqueue get() = DownloadAction(0)
        val Pause get() = DownloadAction(1)
        val Cancel get() = DownloadAction(2)
        val Delete get() = DownloadAction(3)
    }
}
