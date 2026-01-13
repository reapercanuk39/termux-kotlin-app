package com.termux.shared.termux.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import com.termux.kotlin.shared.R
import com.termux.shared.android.resource.ResourceUtils
import com.termux.shared.notification.NotificationUtils
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
import com.termux.shared.termux.TermuxConstants

object TermuxNotificationUtils {

    /**
     * Try to get the next unique notification id that isn't already being used by the app.
     *
     * Termux app and its plugin must use unique notification ids from the same pool due to usage of android:sharedUserId.
     * https://commonsware.com/blog/2017/06/07/jobscheduler-job-ids-libraries.html
     *
     * @param context The [Context] for operations.
     * @return Returns the notification id that should be safe to use.
     */
    @JvmStatic
    @Synchronized
    fun getNextNotificationId(context: Context?): Int {
        if (context == null) return TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID

        val preferences = TermuxAppSharedPreferences.build(context)
            ?: return TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID

        val lastNotificationId = preferences.getLastNotificationId()

        var nextNotificationId = lastNotificationId + 1
        while (nextNotificationId == TermuxConstants.TERMUX_APP_NOTIFICATION_ID || 
               nextNotificationId == TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_ID) {
            nextNotificationId++
        }

        if (nextNotificationId == Int.MAX_VALUE || nextNotificationId < 0) {
            nextNotificationId = TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID
        }

        preferences.setLastNotificationId(nextNotificationId)
        return nextNotificationId
    }

    /**
     * Get [Notification.Builder] for termux app or its plugin.
     *
     * @param currentPackageContext The [Context] of current package.
     * @param termuxPackageContext The [Context] of termux package.
     * @param channelId The channel id for the notification.
     * @param priority The priority for the notification.
     * @param title The title for the notification.
     * @param notificationText The second line text of the notification.
     * @param notificationBigText The full text of the notification that may optionally be styled.
     * @param contentIntent The [PendingIntent] which should be sent when notification is clicked.
     * @param deleteIntent The [PendingIntent] which should be sent when notification is deleted.
     * @param notificationMode The notification mode. It must be one of `NotificationUtils.NOTIFICATION_MODE_*`.
     * @return Returns the [Notification.Builder].
     */
    @JvmStatic
    fun getTermuxOrPluginAppNotificationBuilder(
        currentPackageContext: Context,
        termuxPackageContext: Context,
        channelId: String?,
        priority: Int,
        title: CharSequence?,
        notificationText: CharSequence?,
        notificationBigText: CharSequence?,
        contentIntent: PendingIntent?,
        deleteIntent: PendingIntent?,
        notificationMode: Int
    ): Notification.Builder? {
        val builder = NotificationUtils.geNotificationBuilder(
            termuxPackageContext,
            channelId, priority,
            title, notificationText, notificationBigText, contentIntent, deleteIntent, notificationMode
        ) ?: return null

        // Enable timestamp
        builder.setShowWhen(true)

        // Set notification icon
        // If a notification is to be shown by a termux plugin app, then we can't use the drawable
        // resource id for the plugin app with setSmallIcon(@DrawableRes int icon) since notification
        // is shown with termuxPackageContext and termux-app package would have a different id and
        // when android tries to load the drawable an exception would be thrown and notification will
        // not be thrown.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Set Icon instead of drawable resource id
            builder.setSmallIcon(Icon.createWithResource(currentPackageContext, R.drawable.ic_error_notification))
        } else {
            // Set drawable resource id used by termux-app package
            val iconResId = ResourceUtils.getDrawableResourceId(
                termuxPackageContext, "ic_error_notification",
                termuxPackageContext.packageName, true
            )
            if (iconResId != null) {
                builder.setSmallIcon(iconResId)
            }
        }

        // Set background color for small notification icon
        builder.setColor(0xFF607D8B.toInt())

        // Dismiss on click
        builder.setAutoCancel(true)

        return builder
    }
}
