package com.oxygenupdater.workers

import android.content.Context
import android.graphics.Bitmap
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Size
import coil3.toBitmap
import com.oxygenupdater.dao.ArticleDao
import com.oxygenupdater.extensions.tryNotify
import com.oxygenupdater.ui.currentLocale
import com.oxygenupdater.utils.NotifUtils.NotifElementDutchMessage
import com.oxygenupdater.utils.NotifUtils.NotifElementEnglishMessage
import com.oxygenupdater.utils.NotifUtils.NotifElementNewsItemId
import com.oxygenupdater.utils.NotifUtils.NotifElementNewsItemImage
import com.oxygenupdater.utils.NotifUtils.NotifElementNewsItemIsBump
import com.oxygenupdater.utils.NotifUtils.NotifElementTitle
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

        // Update an existing notification when image loads (avoids indefinite waits).
        // Note: BigPicture doesn't show as much text as BigText. Long messages will be ellipsized.
        val notifId = getNotifId(type, newsItemId ?: 0)
        reNotifyAsBigPicture(
            notifId = notifId,
            imageUrl = imageUrl,
            text = message,
            builder = newsNotifBuilder(
                context = context,
                title = data.getString(NotifElementTitle),
                message = message,
            ).defaults(
                context = context,
                type = type,
                notifId = notifId,
                newsItemId = newsItemId ?: 0,
            ),
        )

        return Result.success()
    }

    private fun reNotifyAsBigPicture(
        notifId: Int,
        imageUrl: String,
        text: String?,
        builder: NotificationCompat.Builder,
    ) = context.imageLoader.enqueue(
        ImageRequest.Builder(context)
            .data(imageUrl)
            /**
             * Fix StrictMode policy violation; ~duration=15 ms Warning: attempt to read pixels from hardware bitmap,
             * which is very slow operation. Reported during [Bitmap.writeToParcel] for hardware bitmaps. Also
             * applicable for [Bitmap.copy], [Bitmap.createBitmap], [Bitmap.extractAlpha], [Bitmap.sameAs]:
             * https://bumptech.github.io/glide/doc/hardwarebitmaps.html#whats-less-efficient-when-we-use-hardware-bitmaps
             */
            // commented out because software rendering is slower than hw, need to measure both before committing
            // .allowHardware(false).bitmapConfig(Bitmap.Config.ARGB_8888)
            // Memory optimization
            .maxBitmapSize(Dimension(Utils.dpToPx(context, 500f).toInt()).let {
                Size(it, it)
            })
            .target { image ->
                val bitmap = image.toBitmap()
                val bigPictureStyle = NotificationCompat.BigPictureStyle()
                    .setSummaryText(text)
                    .bigPicture(bitmap).run {
                        if (SDK_INT >= VERSION_CODES.S) showBigPictureWhenCollapsed(true)
                        // Ensure large icon is not shown when in BigPicture expanded state
                        else bigLargeIcon(null as? Bitmap?) // handled below
                    }

                notificationManager.tryNotify(notifId, builder.run {
                    // Fallback for above, show image as large icon when collapsed
                    if (SDK_INT >= VERSION_CODES.S) this else setLargeIcon(bitmap)
                }.setStyle(bigPictureStyle).build())
            }.build()
    )
}
