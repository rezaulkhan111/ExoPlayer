/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.util

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Utility methods for displaying [Notifications][Notification].  */
@SuppressLint("InlinedApi")
object NotificationUtil {
    /**
     * @see NotificationManager.IMPORTANCE_UNSPECIFIED
     */
    const val IMPORTANCE_UNSPECIFIED: Int = NotificationManager.IMPORTANCE_UNSPECIFIED

    /**
     * @see NotificationManager.IMPORTANCE_NONE
     */
    const val IMPORTANCE_NONE: Int = NotificationManager.IMPORTANCE_NONE

    /**
     * @see NotificationManager.IMPORTANCE_MIN
     */
    const val IMPORTANCE_MIN: Int = NotificationManager.IMPORTANCE_MIN

    /**
     * @see NotificationManager.IMPORTANCE_LOW
     */
    const val IMPORTANCE_LOW: Int = NotificationManager.IMPORTANCE_LOW

    /**
     * @see NotificationManager.IMPORTANCE_DEFAULT
     */
    const val IMPORTANCE_DEFAULT: Int = NotificationManager.IMPORTANCE_DEFAULT

    /**
     * @see NotificationManager.IMPORTANCE_HIGH
     */
    const val IMPORTANCE_HIGH: Int = NotificationManager.IMPORTANCE_HIGH

    /**
     * Creates a notification channel that notifications can be posted to. See [ ] and [ ][NotificationManager.createNotificationChannel] for details.
     *
     * @param context A [Context].
     * @param id The id of the channel. Must be unique per package. The value may be truncated if it's
     * too long.
     * @param nameResourceId A string resource identifier for the user visible name of the channel.
     * The recommended maximum length is 40 characters. The string may be truncated if it's too
     * long. You can rename the channel when the system locale changes by listening for the [     ][Intent.ACTION_LOCALE_CHANGED] broadcast.
     * @param descriptionResourceId A string resource identifier for the user visible description of
     * the channel, or 0 if no description is provided. The recommended maximum length is 300
     * characters. The value may be truncated if it is too long. You can change the description of
     * the channel when the system locale changes by listening for the [     ][Intent.ACTION_LOCALE_CHANGED] broadcast.
     * @param importance The importance of the channel. This controls how interruptive notifications
     * posted to this channel are. One of [.IMPORTANCE_UNSPECIFIED], [     ][.IMPORTANCE_NONE], [.IMPORTANCE_MIN], [.IMPORTANCE_LOW], [     ][.IMPORTANCE_DEFAULT] and [.IMPORTANCE_HIGH].
     */
    fun createNotificationChannel(context: Context, id: String?, @StringRes nameResourceId: Int, @StringRes descriptionResourceId: Int, @Importance importance: Int) {
        if (Util.SDK_INT >= 26) {
            val notificationManager: NotificationManager? = Assertions.checkNotNull(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            val channel: NotificationChannel = NotificationChannel(id, context.getString(nameResourceId), importance)
            if (descriptionResourceId != 0) {
                channel.setDescription(context.getString(descriptionResourceId))
            }
            notificationManager!!.createNotificationChannel(channel)
        }
    }

    /**
     * Post a notification to be shown in the status bar. If a notification with the same id has
     * already been posted by your application and has not yet been canceled, it will be replaced by
     * the updated information. If `notification` is `null` then any notification
     * previously shown with the specified id will be cancelled.
     *
     * @param context A [Context].
     * @param id The notification id.
     * @param notification The [Notification] to post, or `null` to cancel a previously
     * shown notification.
     */
    fun setNotification(context: Context, id: Int, notification: Notification?) {
        val notificationManager: NotificationManager? = Assertions.checkNotNull(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        if (notification != null) {
            notificationManager!!.notify(id, notification)
        } else {
            notificationManager!!.cancel(id)
        }
    }

    /**
     * Notification channel importance levels. One of [.IMPORTANCE_UNSPECIFIED], [ ][.IMPORTANCE_NONE], [.IMPORTANCE_MIN], [.IMPORTANCE_LOW], [ ][.IMPORTANCE_DEFAULT] or [.IMPORTANCE_HIGH].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE, AnnotationTarget.VALUE_PARAMETER)
    @IntDef(value = [
        IMPORTANCE_UNSPECIFIED,
        IMPORTANCE_NONE,
        IMPORTANCE_MIN,
        IMPORTANCE_LOW,
        IMPORTANCE_DEFAULT,
        IMPORTANCE_HIGH
    ])
    annotation class Importance {}
}