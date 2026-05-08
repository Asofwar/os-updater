package com.oxygenupdater.services

import android.content.SharedPreferences
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExperimentalWorkRequestBuilderApi
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.oxygenupdater.dao.ArticleDao
import com.oxygenupdater.extensions.set
import com.oxygenupdater.internal.settings.KeyFirebaseToken
import com.oxygenupdater.utils.FcmUtils
import com.oxygenupdater.utils.NotifUtils.NotifElementNewsItemId
import com.oxygenupdater.utils.NotifUtils.NotifElementNewsItemImage
import com.oxygenupdater.utils.NotifUtils.NotifElementNewsItemIsBump
import com.oxygenupdater.utils.NotifUtils.NotifElementType
import com.oxygenupdater.utils.NotifUtils.NotifTypeNews
import com.oxygenupdater.utils.NotifUtils.handleImmediateNotifs
import com.oxygenupdater.utils.logDebug
import com.oxygenupdater.utils.logError
import com.oxygenupdater.utils.logWarning
import com.oxygenupdater.workers.DisplayNewsNotificationWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class FirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    @Inject
    lateinit var articleDao: ArticleDao

    @Inject
    lateinit var fcmUtils: FcmUtils

    @Inject
    lateinit var crashlytics: FirebaseCrashlytics

    override fun onNewToken(token: String) {
        logDebug(TAG, "Received new Firebase token: $token")
        sharedPreferences[KeyFirebaseToken] = token
        fcmUtils.resubscribe()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        if (data.isEmpty()) {
            remoteMessage.notification?.let {
                logDebug(TAG, "Message contains 'notification' object")
            }
            // Nothing to do, we only handle 'data' messages, because FCM automatically handles 'notification'.
            return
        }

        val type = data[NotifElementType] ?: ""
        val newsItemId = data[NotifElementNewsItemId]?.toLongOrNull()

        // Always show notifications immediately, even for news notifs (they'll initially be in BigText style)
        notificationManager.handleImmediateNotifs(
            // No processing needed, show notification immediately
            context = this,
            type = type,
            data = data,
            checkNewsRead = {
                try {
                    @Suppress("DEPRECATION")
                    articleDao.getById(newsItemId)?.read == true
                } catch (_: Exception) {
                    false
                }
            },
            unknownTypeError = {
                crashlytics.logWarning(TAG, it)
            }
        )

        // Then, we optionally spawn a worker to download the news image in BigPicture style
        if (type == NotifTypeNews) try {
            // Ensure 'bump' notifications are only shown to people who haven't yet read the article. However, only
            // app versions from v4.1.0 onwards properly support this, even though a broken implementation was added
            // in v4.0.0 (Kotlin rebuild).
            @Suppress("DEPRECATION")
            if (data[NotifElementNewsItemIsBump]?.toBoolean() == true
                && articleDao.getById(newsItemId)?.read == true
            ) return

            val imageUrl = data[NotifElementNewsItemImage]?.trim() ?: ""
            if (imageUrl.isEmpty()) return // nothing to do

            workManager.enqueue(
                @OptIn(ExperimentalWorkRequestBuilderApi::class)
                OneTimeWorkRequestBuilder<DisplayNewsNotificationWorker>()
                    .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                    .setBackoffForSystemInterruptions()
                    .setInputData(Data.Builder().apply {
                        // Passthrough all data, even though we only care about news-specific keys
                        data.forEach { putString(it.key, it.value) }
                    }.build()).apply {
                        /**
                         * If a notification is marked as high priority, we can run it as an expedited job.
                         * Firebase [docs](https://firebase.google.com/docs/cloud-messaging/android-message-priority#message-processing)
                         * state that "There is a brief exemption for expedited jobs scheduled immediately after a high
                         * priority FCM [onMessageReceived] is dispatched."
                         */
                        if (remoteMessage.priority != PRIORITY_HIGH) return@apply
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }.build()
            )
        } catch (e: Exception) {
            crashlytics.logError(TAG, "Error creating worker for news notification", e)
        }
    }

    companion object {
        private const val TAG = "FirebaseMessagingService"
    }
}
