package com.oxygenupdater.ui.update

import androidx.compose.runtime.Immutable

@Immutable
@JvmInline
value class DownloadStatus private constructor(val value: Int) {

    override fun toString() = "DownloadStatus." + when (this) {
        NotDownloading -> "NotDownloading"
        DownloadQueued -> "DownloadQueued"
        Downloading -> "Downloading"
        DownloadPaused -> "DownloadPaused"
        DownloadCompleted -> "DownloadCompleted"
        DownloadFailed -> "DownloadFailed"
        Verifying -> "Verifying"
        VerificationCompleted -> "VerificationCompleted"
        VerificationFailed -> "VerificationFailed"
        else -> "Invalid"
    }

    companion object {
        /** No update is being downloaded and the update has not been downloaded yet */
        val NotDownloading get() = DownloadStatus(0)

        /** The download is in the queue of the download executor, it will start soon */
        val DownloadQueued get() = DownloadStatus(1)

        /** The download is in progress */
        val Downloading get() = DownloadStatus(2)

        /** The download has been paused by the user */
        val DownloadPaused get() = DownloadStatus(3)

        /** The file has been successfully downloaded */
        val DownloadCompleted get() = DownloadStatus(4)

        /** The file could not be downloaded */
        val DownloadFailed get() = DownloadStatus(5)

        /** The downloaded file's MD5 checksum is being verified */
        val Verifying get() = DownloadStatus(6)

        /** The downloaded file's MD5 checksum has been verified */
        val VerificationCompleted get() = DownloadStatus(7)

        /** The downloaded file's MD5 checksum could not be verified */
        val VerificationFailed get() = DownloadStatus(8)
    }

    val successful
        get() = this == DownloadCompleted || this == VerificationCompleted

    val failed
        get() = this == DownloadFailed || this == VerificationFailed

    val inProgress
        get() = this == DownloadQueued || this == Downloading
}
