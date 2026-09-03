package com.oxygenupdater.ui.update

import androidx.compose.runtime.Immutable

@Immutable
@JvmInline
value class DownloadFailure private constructor(val value: Int) {

    override fun toString() = "DownloadFailure." + when (this) {
        NullUpdateDataOrDownloadUrl -> "NullUpdateDataOrDownloadUrl"
        DownloadUrlInvalidScheme -> "DownloadUrlInvalidScheme"
        ServerError -> "ServerError"
        ConnectionError -> "ConnectionError"
        UnsuccessfulResponse -> "UnsuccessfulResponse"
        CouldNotMoveTempFile -> "CouldNotMoveTempFile"
        Unknown -> "Unknown"
        else -> "Invalid"
    }

    companion object {
        /**
         * If update data or download URL is null.
         *
         * Note: we recreate [com.oxygenupdater.models.UpdateData] from [androidx.work.Data],
         * which is passed into the worker while creating the [androidx.work.OneTimeWorkRequest]
         */
        val NullUpdateDataOrDownloadUrl get() = DownloadFailure(0)

        /** If the download URL doesn't contain `http` */
        val DownloadUrlInvalidScheme get() = DownloadFailure(1)

        /**
         * If a network-related exception occurs while downloading/writing the file
         *
         * @see com.oxygenupdater.utils.isNetworkError
         */
        val ServerError get() = DownloadFailure(2)

        /** If an [java.io.IOException] is thrown while downloading/writing the file */
        val ConnectionError get() = DownloadFailure(3)

        /**
         * If the Retrofit response returns unsuccessfully, i.e. an HTTP code that does not lie between 200 and 300.
         * Most of the times this would be because of an invalid link
         * (e.g. when OPPO/OnePlus/realme pulls an update, or a human error while adding update data)
         */
        val UnsuccessfulResponse get() = DownloadFailure(4)

        /**
         * If the temporary downloaded file can't be copied to the root directory of internal storage
         */
        val CouldNotMoveTempFile get() = DownloadFailure(5)

        /** Unknown error */
        val Unknown get() = DownloadFailure(6)

        // If new failures are added, adjust show download link logic in UpdateInformationContent.kt
    }
}
