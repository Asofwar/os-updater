package com.oxygenupdater.ui.main

import androidx.collection.IntIntPair
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.oxygenupdater.ComposeBaseTest
import org.junit.Test

class AppUpdateInfoTest : ComposeBaseTest() {

    private var status by mutableStateOf(InstallStatus.DOWNLOADING)
    private var availability by mutableStateOf(UpdateAvailability.UPDATE_AVAILABLE)
    private var snackbarText: IntIntPair? = null

    @Test
    fun appUpdateInfo() {
        setContent(true) {
            AppUpdateInfo(
                status = status,
                availability = { availability },
                snackbarMessageId = { snackbarText?.first },
                updateSnackbarText = { snackbarText = it },
                resetAppUpdateIgnoreCount = {
                    trackCallback("resetAppUpdateIgnoreCount")
                },
                incrementAppUpdateIgnoreCount = {
                    trackCallback("incrementAppUpdateIgnoreCount")
                },
                unregisterAppUpdateListener = {
                    trackCallback("unregisterAppUpdateListener")
                },
                requestUpdate = {
                    trackCallback("requestUpdate")
                },
                requestImmediateUpdate = {
                    trackCallback("requestImmediateUpdate")
                },
            )
        }

        /** First we test for the initial value of status = [InstallStatus.DOWNLOADING] */
        snackbarText.check(null) // should be dismissed
        ensureCallbackInvokedExactlyOnce("requestUpdate")

        // Then for other values
        validateForOtherUpdateAvailabilities(
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            "requestImmediateUpdate",
        )
        validateForOtherUpdateAvailabilities(UpdateAvailability.UPDATE_NOT_AVAILABLE)
        validateForOtherUpdateAvailabilities(UpdateAvailability.UNKNOWN)

        /** Then for the status = [InstallStatus.DOWNLOADED] */
        status = InstallStatus.DOWNLOADED
        advanceFrame(); snackbarText.check(AppUpdateDownloadedSnackbarData)
        ensureCallbackInvokedExactlyOnce("unregisterAppUpdateListener")
    }

    private fun validateForOtherUpdateAvailabilities(
        @UpdateAvailability newAvailability: Int,
        callbackToCheck: String? = null,
    ) {
        availability = newAvailability
        advanceFrame(); snackbarText.check(null) // should not change
        callbackToCheck?.let { ensureCallbackInvokedExactlyOnce(it) } ?: ensureNoCallbacksWereInvoked()
    }

    private fun IntIntPair?.check(expected: IntIntPair?) = assert(this == expected) {
        "Snackbar text did not match. Expected: $expected, actual: $this."
    }
}
