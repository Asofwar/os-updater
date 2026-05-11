package com.oxygenupdater.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import androidx.annotation.Size
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.GROUP_ALERT_CHILDREN
import androidx.core.app.NotificationCompat.PRIORITY_DEFAULT
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.oxygenupdater.R
import com.oxygenupdater.activities.MainActivity
import com.oxygenupdater.extensions.setBigTextStyle
import com.oxygenupdater.extensions.tryNotify
import com.oxygenupdater.internal.NotSet
import com.oxygenupdater.ui.currentLocale
import com.oxygenupdater.ui.main.DeepLinkArticle
import com.oxygenupdater.ui.main.DeepLinkNewsList
import com.oxygenupdater.ui.main.ExternalArg
import com.oxygenupdater.ui.main.OuSchemeSuffixed
import com.oxygenupdater.utils.NotifUtils.NotifTypeGeneral
import com.oxygenupdater.utils.NotifUtils.NotifTypeNewDevice
import com.oxygenupdater.utils.NotifUtils.NotifTypeNews
import com.oxygenupdater.utils.NotifUtils.getNotifId
import com.oxygenupdater.utils.NotificationChannels.DownloadAndInstallationGroup
import com.oxygenupdater.utils.NotificationChannels.MiscellaneousGroup
import com.oxygenupdater.utils.NotificationChannels.PushNotificationsGroup
import com.oxygenupdater.utils.NotificationChannels.PushNotificationsGroup.DeviceNotifChannelId
import com.oxygenupdater.utils.NotificationChannels.PushNotificationsGroup.GeneralNotifChannelId
import com.oxygenupdater.utils.NotificationChannels.PushNotificationsGroup.NewsNotifChannelId
import com.oxygenupdater.utils.NotificationChannels.PushNotificationsGroup.UpdateNotifChannelId
import kotlin.random.Random

object NotifUtils {

    const val NotifElementType = "TYPE"
    const val NotifElementTitle = "TITLE" // added in app v7.2.0, only English
    const val NotifElementEnglishMessage = "ENGLISH_MESSAGE"
    const val NotifElementDutchMessage = "DUTCH_MESSAGE"
    const val NotifElementNewDeviceName = "NEW_DEVICE_NAME"
    const val NotifElementDeviceName = "DEVICE_NAME"
    const val NotifElementNewVersionNumber = "NEW_VERSION_NUMBER"
    const val NotifElementNewsItemId = "NEWS_ITEM_ID"
    const val NotifElementNewsItemIsBump = "NEWS_ITEM_IS_BUMP"
    const val NotifElementNewsItemImage = "NEWS_ITEM_IMAGE"

    const val NotifTypeNewDevice = "NEW_DEVICE"
    const val NotifTypeNewVersion = "NEW_VERSION"
    const val NotifTypeGeneral = "GENERAL_NOTIFICATION"
    const val NotifTypeNews = "NEWS"

    inline fun NotificationManagerCompat.handleImmediateNotifs(
        context: Context,
        type: String,
        data: Map<String, String?>,
        checkNewsRead: () -> Boolean,
        unknownTypeError: (String) -> Unit,
    ) {
        val title = data[NotifElementTitle]?.trim() // added in app v7.2.0, only English
        val message = data[
            if (currentLocale.language.lowercase() == "nl") NotifElementDutchMessage
            else NotifElementEnglishMessage
        ]?.trim()
        val builder = when (type) {
            NotifTypeNewDevice -> NotificationCompat.Builder(context, DeviceNotifChannelId)
                .setPriority(PRIORITY_DEFAULT)
                .setTitleSubtext(context, title, R.string.device_notification_channel_name)
                .setBigTextStyle(
                    context.getString(
                        R.string.notification_new_device_text,
                        data[NotifElementNewDeviceName]?.trim() ?: context.getString(R.string.device_information_unknown),
                    )
                )

            NotifTypeNewVersion -> NotificationCompat.Builder(context, UpdateNotifChannelId)
                .setPriority(PRIORITY_HIGH)
                .setTitleSubtext(context, title, R.string.update_notification_channel_name)
                .setBigTextStyle(
                    context.getString(
                        R.string.notification_version,
                        data[NotifElementNewVersionNumber]?.trim(),
                        data[NotifElementDeviceName]?.trim() ?: context.getString(R.string.device_information_unknown),
                    )
                )

            NotifTypeNews -> {
                // Ensure 'bump' notifications are only shown to people who haven't yet read the article. However, only
                // app versions from v4.1.0 onwards properly support this, even though a broken implementation was added
                // in v4.0.0 (Kotlin rebuild).
                if (data[NotifElementNewsItemIsBump]?.toBoolean() == true && checkNewsRead()) return

                // Start with a simple BigTextStyle. Image will be loaded later in a worker.
                newsNotifBuilder(
                    context = context,
                    title = title,
                    message = message,
                )
            }

            else -> {
                if (type != NotifTypeGeneral) unknownTypeError(
                    "Unknown notification type: $type, falling back to '$NotifTypeGeneral'"
                )
                // General is used as a catch-all channel
                NotificationCompat.Builder(context, GeneralNotifChannelId)
                    .setPriority(PRIORITY_DEFAULT)
                    .setTitleSubtext(context, title, R.string.general_notification_channel_name)
                    .setBigTextStyle(message)
            }
        }

        showNotification(context, type, data, builder)
    }

    fun newsNotifBuilder(
        context: Context,
        title: String?,
        message: String?,
    ) = NotificationCompat.Builder(context, NewsNotifChannelId)
        .setPriority(PRIORITY_HIGH)
        .setTitleSubtext(context, title, R.string.news_notification_channel_name)
        .setBigTextStyle(message)
        .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)

    /**
     * Primarily only for news notifications, but configured for all notifications,
     * in case we decide to use it for those as well, in the future.
     *
     * Added in app v7.2.0. The `if` branch acts as the fallback to preserve older/default behaviour.
     */
    inline fun NotificationCompat.Builder.setTitleSubtext(
        context: Context,
        title: String?,
        @StringRes subtextResId: Int,
    ) = if (title.isNullOrEmpty()) setContentTitle(context.getString(subtextResId)) else {
        setContentTitle(title)
        setSubText(context.getString(subtextResId))
    }

    /**
     * @return [Int] Notification ID from [getNotifId] for later reuse
     */
    fun NotificationManagerCompat.showNotification(
        context: Context,
        type: String,
        data: Map<String, String?>,
        builder: NotificationCompat.Builder,
    ) {
        val newsItemId = data[NotifElementNewsItemId]?.toIntOrNull() ?: 0
        val notifId = getNotifId(type, newsItemId)
        val notifGroupKey = getNotifGroupKey(type)

        builder.defaults(
            context = context,
            type = type,
            notifId = notifId,
            newsItemId = newsItemId,
        )

        tryNotify(notifId, builder.build())

        // Summary notification is not shown for API < 24 because notification
        // groups aren't supported anyway (meaning multiple notifications will
        // be shown separately instead of using InboxStyle to emulate a "group").
        if (type != NotifTypeNewVersion && SDK_INT >= VERSION_CODES.N) {
            val summaryNotification = NotificationCompat.Builder(context, notifGroupKey)
                .setSmallIcon(R.drawable.logo_notification)
                // Dismiss this notification when all its children are also dismissed
                .setAutoCancel(true)
                // Specify which group this notification belongs to
                .setGroup(notifGroupKey)
                // Set this notification as the summary for the group
                .setGroupSummary(true)
                // Mute this summary notification in favour of children notifications
                .setGroupAlertBehavior(GROUP_ALERT_CHILDREN)
                .build()

            tryNotify(getNotifGroupId(type), summaryNotification)
        }
    }

    fun newsNotifIntent(newsItemId: Int, context: Context): Intent = Intent(
        Intent.ACTION_VIEW,
        // Open article if ID is valid, and mark it "external" so
        // that interstitial ad is shown after a delay for better UX.
        // Otherwise, open NewsListScreen.
        if (newsItemId != NotSet) {
            "$OuSchemeSuffixed$DeepLinkArticle/$newsItemId?${ExternalArg}=true".toUri()
        } else (OuSchemeSuffixed + DeepLinkNewsList).toUri(),
        context,
        MainActivity::class.java
    )

    fun NotificationCompat.Builder.defaults(
        context: Context,
        type: String,
        notifId: Int,
        newsItemId: Int,
    ) = setSmallIcon(R.drawable.logo_notification)
        .setAutoCancel(true)
        .setDefaults(Notification.DEFAULT_ALL)
        .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(
            notifContentIntent(
                context, notifId,
                if (type == NotifTypeNews) newsNotifIntent(
                    newsItemId, context
                ) else Intent(context, MainActivity::class.java),
            )
        ).apply {
            if (type != NotifTypeNewVersion) setGroup(getNotifGroupKey(type))
        }

    fun notifContentIntent(
        context: Context,
        notifId: Int,
        intent: Intent,
    ): PendingIntent = PendingIntent.getActivity(
        context, notifId, intent,
        FLAG_UPDATE_CURRENT or (if (SDK_INT >= VERSION_CODES.S) FLAG_IMMUTABLE else 0),
    )

    /**
     * Generates notification IDs from predefined [NotificationIds], but also
     * guarantees uniqueness for each type that may show multiple notifications:
     *
     * - [NotifTypeNews]: add the news item ID to guarantee a unique notification ID
     * - [NotifTypeNewDevice] & [NotifTypeGeneral]: Since there isn't any ID for
     *   these notifications, rely on [Random.nextInt]. Upper limit is 100000
     *   to ensure it never overflows to the next `REMOTE_NOTIFICATION_` value.
     * - Other notifications are always unique, and should never be grouped
     */
    fun getNotifId(type: String, newsItemId: Int) = when (type) {
        NotifTypeNews -> NotificationIds.RemoteNews
        NotifTypeNewVersion -> NotificationIds.RemoteNewUpdate
        NotifTypeNewDevice -> NotificationIds.RemoteNewDevice
        NotifTypeGeneral -> NotificationIds.RemoteGeneral
        else -> NotificationIds.RemoteUnknown
    } + when (type) {
        NotifTypeNews -> newsItemId
        NotifTypeNewDevice, NotifTypeGeneral -> Random.nextInt(1, 100000)
        else -> 0
    }

    private fun getNotifGroupId(type: String) = when (type) {
        NotifTypeNewDevice -> NotificationIds.RemoteNewDeviceGroup
        NotifTypeNews -> NotificationIds.RemoteNewsGroup
        NotifTypeGeneral -> NotificationIds.RemoteGeneralGroup
        else -> NotificationIds.RemoteUnknownGroup
    }

    /**
     * Used both as a group key, and the channel ID for the summary notification
     *
     * @see NotificationCompat.Builder.setGroup
     */
    fun getNotifGroupKey(type: String) = when (type) {
        NotifTypeNewVersion -> UpdateNotifChannelId
        NotifTypeNews -> NewsNotifChannelId
        NotifTypeNewDevice -> DeviceNotifChannelId
        NotifTypeGeneral -> GeneralNotifChannelId
        else -> GeneralNotifChannelId // default
    }

    private fun NotificationManagerCompat.isDisabled(
        channelId: String,
    ) = !areNotificationsEnabled() || getNotificationChannel(channelId)?.run {
        if (SDK_INT >= VERSION_CODES.O) {
            importance == NotificationManager.IMPORTANCE_NONE ||
                    (SDK_INT >= VERSION_CODES.P && getNotificationChannelGroup(group)?.isBlocked == true)
        } else false
    } == true

    fun toNotifStatus(context: Context) = with(NotificationManagerCompat.from(context)) {
        if (areNotificationsEnabled()) NotifStatus(
            listOf(
                isDisabled(UpdateNotifChannelId),
                isDisabled(NewsNotifChannelId),
                isDisabled(DownloadAndInstallationGroup.DownloadStatusNotifChannelId),
            )
        ) else NotifStatus()
    }

    /**
     * First deletes old channels, then creates current groups & their corresponding channels.
     */
    @RequiresApi(VERSION_CODES.O)
    fun refreshNotificationChannels(context: Context) = with(NotificationManagerCompat.from(context)) {
        deleteOldNotificationChannels()
        createAllNotificationGroups(context)
        createAllNotificationChannels(context)
    }

    /** Deletes all old notification channels */
    @Suppress("DEPRECATION")
    @RequiresApi(VERSION_CODES.O)
    private inline fun NotificationManagerCompat.deleteOldNotificationChannels() {
        deleteNotificationChannel(NotificationChannels.OldPushNotifChannelId)
        deleteNotificationChannel(NotificationChannels.OldProgressNotifChannelId)
        // No longer used in v5.9.0+
        deleteNotificationChannel(MiscellaneousGroup.OtaFilenameSubmittedNotifChannelId)
    }

    /**
     * Creates all notification channel groups
     *
     * @see NotificationChannels
     */
    @RequiresApi(VERSION_CODES.O)
    private inline fun NotificationManagerCompat.createAllNotificationGroups(context: Context) = createNotificationChannelGroups(
        listOf(
            NotificationChannelGroup(
                DownloadAndInstallationGroup.ID,
                context.getString(R.string.download_and_installation_notifications_group_name)
            ).apply {
                if (SDK_INT >= VERSION_CODES.P) {
                    description = context.getString(R.string.download_and_installation_notifications_group_description)
                }
            },
            NotificationChannelGroup(
                PushNotificationsGroup.ID,
                context.getString(R.string.push_notifications_group_name)
            ).apply {
                if (SDK_INT >= VERSION_CODES.P) {
                    description = context.getString(R.string.push_notifications_group_description)
                }
            },
            NotificationChannelGroup(
                MiscellaneousGroup.ID,
                context.getString(R.string.miscellaneous_notifications_group_name)
            )
        )
    )

    /**
     * Creates all notification channels and assigns them to their groups
     *
     * @see DownloadAndInstallationGroup
     * @see PushNotificationsGroup
     * @see MiscellaneousGroup
     */
    @RequiresApi(VERSION_CODES.O)
    private inline fun NotificationManagerCompat.createAllNotificationChannels(context: Context) = createNotificationChannels(
        listOf(
            //// BEGIN: DownloadAndInstallationGroup
            createNotificationChannel(
                DownloadAndInstallationGroup.DownloadStatusNotifChannelId,
                DownloadAndInstallationGroup.ID,
                context.getString(R.string.download_status_notification_channel_name),
                context.getString(R.string.download_status_notification_channel_description),
                NotificationManager.IMPORTANCE_LOW,
                lightsEnabled = false
            ),
            createNotificationChannel(
                DownloadAndInstallationGroup.VerificationStatusNotifChannelId,
                DownloadAndInstallationGroup.ID,
                context.getString(R.string.verification_status_notification_channel_name),
                context.getString(R.string.verification_status_notification_channel_description),
                NotificationManager.IMPORTANCE_LOW,
                lightsEnabled = false
            ),
            createNotificationChannel(
                DownloadAndInstallationGroup.InstallationStatusNotifChannelId,
                DownloadAndInstallationGroup.ID,
                context.getString(R.string.installation_status_notification_channel_name),
                context.getString(R.string.installation_status_notification_channel_description),
                NotificationManager.IMPORTANCE_LOW,
                lightsEnabled = false
            ),
            //// END: DownloadAndInstallationGroup

            //// BEGIN: PushNotificationsGroup
            createNotificationChannel(
                UpdateNotifChannelId,
                PushNotificationsGroup.ID,
                context.getString(R.string.update_notification_channel_name),
                context.getString(R.string.update_notification_channel_description),
                NotificationManager.IMPORTANCE_HIGH,
                vibrationEnabled = true
            ),
            createNotificationChannel(
                NewsNotifChannelId,
                PushNotificationsGroup.ID,
                context.getString(R.string.news_notification_channel_name),
                context.getString(R.string.news_notification_channel_description),
                NotificationManager.IMPORTANCE_HIGH,
                vibrationEnabled = true
            ),
            createNotificationChannel(
                DeviceNotifChannelId,
                PushNotificationsGroup.ID,
                context.getString(R.string.device_notification_channel_name),
                context.getString(R.string.device_notification_channel_description),
                NotificationManager.IMPORTANCE_DEFAULT
            ),
            createNotificationChannel(
                GeneralNotifChannelId,
                PushNotificationsGroup.ID,
                context.getString(R.string.general_notification_channel_name),
                context.getString(R.string.general_notification_channel_description),
                NotificationManager.IMPORTANCE_DEFAULT,
                vibrationEnabled = true
            ),
            //// END: PushNotificationsGroup

            //// BEGIN: MiscellaneousGroup
            createNotificationChannel(
                MiscellaneousGroup.OtaUrlSubmittedNotifChannelId,
                MiscellaneousGroup.ID,
                context.getString(R.string.url_submitted_notification_channel_name),
                context.getString(R.string.url_submitted_notification_channel_description),
                NotificationManager.IMPORTANCE_LOW
            ),
            //// END: MiscellaneousGroup
        )
    )

    /**
     * Utility function to configure and create a notification channel.
     *
     * @param lightsEnabled defaults to true
     * @param vibrationEnabled defaults to false
     */
    @RequiresApi(VERSION_CODES.O)
    private fun createNotificationChannel(
        channelId: String,
        groupId: String,
        channelName: String,
        channelDescription: String,
        importance: Int,
        lightsEnabled: Boolean = true,
        vibrationEnabled: Boolean = false,
    ) = NotificationChannel(channelId, channelName, importance).apply {
        group = groupId
        description = channelDescription

        // Force-disable sound for LOW & MIN importance notifications
        if (importance < NotificationManager.IMPORTANCE_DEFAULT) setSound(null, null)

        // Sets the notification light color for notifications posted to this
        // channel, if the device supports this feature.
        lightColor = Color.RED
        enableLights(lightsEnabled)
        enableVibration(vibrationEnabled)
    }
}

/** @param disabled if null or empty, it's assumed notifications are disabled as a whole */
@Immutable
data class NotifStatus(@Size(3) val disabled: List<Boolean>? = null)

/**
 * Generally, the format is:
 * * Remote: Multiples of 1000000
 * * Local: Multiples of 10
 * * Foreground: Multiples of 100
 *
 * Intermediate values (e.g. 11, 102, 1005, etc) can be used to further differentiate between the same category
 */
object NotificationIds {
    private const val LocalIdMultiplier = 10
    private const val RemoteIdMultiplier = 1000000

    const val LocalDownload = 1 * LocalIdMultiplier
    const val LocalDownloadForeground = 10 * LocalIdMultiplier

    const val LocalMd5Verification = 2 * LocalIdMultiplier
    const val LocalContribution = 3 * LocalIdMultiplier
    const val LocalInstallationStatus = 4 * LocalIdMultiplier

    const val RemoteNewUpdate = 1 * RemoteIdMultiplier

    const val RemoteNewDevice = 2 * RemoteIdMultiplier
    const val RemoteNewDeviceGroup = 20 * RemoteIdMultiplier

    const val RemoteNews = 3 * RemoteIdMultiplier
    const val RemoteNewsGroup = 30 * RemoteIdMultiplier

    const val RemoteGeneral = 4 * RemoteIdMultiplier
    const val RemoteGeneralGroup = 40 * RemoteIdMultiplier

    const val RemoteUnknown = 5 * RemoteIdMultiplier
    const val RemoteUnknownGroup = 50 * RemoteIdMultiplier
}

/**
 * Prepare for better, fine-grained notification channels
 */
object NotificationChannels {

    private const val PREFIX = "com.oxygenupdater.notifications"
    private const val GroupPrefix = "$PREFIX.group"
    private const val ChannelPrefix = "$PREFIX.channel"

    object DownloadAndInstallationGroup {

        /**
         * This group's ID
         */
        const val ID = "$GroupPrefix.download&installation"

        /**
         * Name: Download status
         * Description: Notifications that reflect the current state of a download: pending, active, completed, or failed
         */
        const val DownloadStatusNotifChannelId = "$ChannelPrefix.download"

        /**
         * Name: Verification status
         * Description: Notifications that reflect the current state of a file verification task: in-progress, completed, or failed.
         * MD5 checksums for downloaded files are verified to ensure the file hasn't been tampered with, and is a valid update file.
         */
        const val VerificationStatusNotifChannelId = "$ChannelPrefix.verification"

        /**
         * Name: Installation status
         * Description: Get notified if an update was successfully installed or not
         */
        const val InstallationStatusNotifChannelId = "$ChannelPrefix.installation"
    }

    object PushNotificationsGroup {

        /**
         * This group's ID
         */
        const val ID = "$GroupPrefix.push"

        /**
         * Name: System update available
         * Description: Get notified when there's a new update available for your selected device & update method
         */
        const val UpdateNotifChannelId = "$ChannelPrefix.update"

        /**
         * Name: News article
         * Description: Get notified when we publish a news article
         */
        const val NewsNotifChannelId = "$ChannelPrefix.news"

        /**
         * Name: New device supported
         * Description: Get notified when the app adds support for a new device
         */
        const val DeviceNotifChannelId = "$ChannelPrefix.device"

        /**
         * Name: General
         * Description: General notifications that don't fit into other categories
         */
        const val GeneralNotifChannelId = "$ChannelPrefix.general"
    }

    object MiscellaneousGroup {

        /**
         * This group's ID
         */
        const val ID = "$GroupPrefix.miscellaneous"

        /**
         * Name: OTA filename submitted
         * Description: Get notified when you've submitted OTA filenames successfully to our team.
         * You can opt-out of automatically submitting filenames in the app's settings.
         */
        @Deprecated(
            message = """
                No longer used in v5.9.0+, because all OnePlus phones now use either
                Google OTA (doesn't save files anywhere), or Component OTA (saves in
                a directory accessible only via root), but URL in this case can't be
                constructed via filenames alone.
            """,
            level = DeprecationLevel.WARNING
        )
        const val OtaFilenameSubmittedNotifChannelId = "$ChannelPrefix.filename"

        const val OtaUrlSubmittedNotifChannelId = "$ChannelPrefix.url"
    }

    /**
     * **Note: don't ever change this string's value**
     */
    @Deprecated(
        message = """
            No longer used in v5.0.0+ (redesign release), except for deleting
            old channels. Was used for push notifications (general, new device
            supported, new update available, news article published), as well
            as to display installation status notifications on rooted devices.
        """,
        level = DeprecationLevel.WARNING
    )
    const val OldPushNotifChannelId = "com.oxygenupdater.internal.notifications"

    /**
     * **Note: don't ever change this string's value**
     */
    @Deprecated(
        message = """
            No longer used in v5.0.0+ (redesign release), except for deleting
            old channels. Was used for the entire download flow, as well as
            successful OTA filename contribution notifications.
        """,
        level = DeprecationLevel.WARNING
    )
    const val OldProgressNotifChannelId = "com.oxygenupdater.progress"
}
