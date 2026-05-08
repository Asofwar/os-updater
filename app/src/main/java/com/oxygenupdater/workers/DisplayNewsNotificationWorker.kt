package com.oxygenupdater.workers

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.oxygenupdater.dao.ArticleDao
import com.oxygenupdater.extensions.tryNotify
import com.oxygenupdater.ui.currentLocale
import com.oxygenupdater.utils.NotifUtils.NotifElementDutchMessage
import com.oxygenupdater.utils.NotifUtils.NotifElementEnglishMessage
import com.oxygenupdater.utils.NotifUtils.NotifElementNewsItemId
import com.oxygenupdater.utils.NotifUtils.NotifElementNewsItemImage
import com.oxygenupdater.utils.NotifUtils.NotifElementNewsItemIsBump
import com.oxygenupdater.utils.NotifUtils.NotifElementType
import com.oxygenupdater.utils.NotifUtils.NotifTypeNews
import com.oxygenupdater.utils.NotifUtils.defaults
import com.oxygenupdater.utils.NotifUtils.getNotifId
import com.oxygenupdater.utils.NotifUtils.newsNotifBuilder
import com.oxygenupdater.utils.Utils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Enqueued from [com.oxygenupdater.services.FirebaseMessagingService]
 * to display a notification to the user after a specified delay
 *
 * @author [Adhiraj Singh Chauhan](https://github.com/adhirajsinghchauhan)
 */
@HiltWorker
class DisplayNewsNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val articleDao: ArticleDao,
    private val notificationManager: NotificationManagerCompat,
) : CoroutineWorker(context, parameters) {

    private val data = parameters.inputData

    override suspend fun doWork(): Result {
        if (data == Data.EMPTY) return Result.failure()

        val type = data.getString(NotifElementType) ?: ""
        if (type != NotifTypeNews) return Result.failure()

        val newsItemId = data.getString(NotifElementNewsItemId)?.toIntOrNull()
        // Ensure 'bump' notifications are only shown to people who haven't yet read the article. However, only
        // app versions from v4.1.0 onwards properly support this, even though a broken implementation was added
        // in v4.0.0 (Kotlin rebuild).
        @Suppress("DEPRECATION")
        if (data.getString(NotifElementNewsItemIsBump)?.toBoolean() == true
            && articleDao.getById(newsItemId?.toLong())?.read == true
        ) return Result.success()

        val imageUrl = data.getString(NotifElementNewsItemImage)?.trim() ?: ""
        if (imageUrl.isEmpty()) return Result.failure()

        val message = data.getString(
            if (currentLocale.language.lowercase() == "nl") NotifElementDutchMessage
            else NotifElementEnglishMessage
        )?.trim()

        // Update an existing notification when image loads (avoids indefinite waits)
        // Note: we're setting only the large icon; not changing style to BigPicture
        //       because that doesn't show full text when expanded like BigText does
        val notifId = getNotifId(type, newsItemId ?: 0)
        reNotifyWithLargeIcon(
            notifId = notifId,
            imageUrl = imageUrl,
            builder = newsNotifBuilder(context, message).defaults(
                context = context,
                type = type,
                notifId = notifId,
                newsItemId = newsItemId ?: 0,
            ),
        )

        return Result.success()
    }

    private fun reNotifyWithLargeIcon(
        notifId: Int,
        imageUrl: String,
        builder: NotificationCompat.Builder,
    ) = context.imageLoader.enqueue(
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Utils.dpToPx(context, 64f).toInt()) // memory optimization
            .target {
                notificationManager.tryNotify(
                    notifId,
                    builder.setLargeIcon(it.toBitmap()).build()
                )
            }.build()
    )
}
