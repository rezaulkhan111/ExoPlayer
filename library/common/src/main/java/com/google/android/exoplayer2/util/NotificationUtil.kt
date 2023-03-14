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

import android.annotation.SuppressLintimport

android.content.Contextimport android.content.Intentimport androidx.annotation .IntDefimport androidx.annotation .StringRes android.app.*
import android.security.NetworkSecurityPolicy
import com.google.android.exoplayer2.util.HandlerWrapper
import android.opengl.EGL14
import com.google.android.exoplayer2.util.GlUtil
import javax.microedition.khronos.egl.EGL10
import android.opengl.GLES20
import com.google.android.exoplayer2.util.GlUtil.GlException
import com.google.android.exoplayer2.util.GlUtil.Api17
import android.opengl.GLU
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.SparseBooleanArray
import com.google.android.exoplayer2.util.FlagSet
import com.google.android.exoplayer2.util.UriUtil
import com.google.android.exoplayer2.util.FileTypes
import com.google.android.exoplayer2.util.GlProgram.Uniform
import com.google.android.exoplayer2.util.GlProgram
import com.google.android.exoplayer2.util.MimeTypes.CustomMimeType
import com.google.android.exoplayer2.util.MimeTypes.Mp4aObjectType
import com.google.android.exoplayer2.util.TraceUtil
import com.google.android.exoplayer2.util.AtomicFile.AtomicFileOutputStream
import android.os.IBinder
import com.google.android.exoplayer2.util.BundleUtil
import kotlin.annotations.jvm.UnderMigration
import kotlin.annotations.jvm.MigrationStatus
import com.google.android.exoplayer2.util.ColorParser
import com.google.android.exoplayer2.util.ListenerSet.IterationFinishedEvent
import com.google.android.exoplayer2.util.ListenerSet
import com.google.android.exoplayer2.util.SurfaceInfo
import com.google.android.exoplayer2.util.SystemHandlerWrapper
import android.util.SparseArray
import com.google.android.exoplayer2.util.FrameProcessingException
import com.google.android.exoplayer2.util.DebugViewProvider
import com.google.android.exoplayer2.video.ColorInfo
import com.google.android.exoplayer2.util.RepeatModeUtil
import android.media.MediaFormat
import com.google.android.exoplayer2.util.MediaFormatUtil
import com.google.android.exoplayer2.util.TimedValueQueue
import androidx.annotation.StringRes
import com.google.android.exoplayer2.util.NotificationUtil.Importance
import android.content.Context
import com.google.android.exoplayer2.util.NotificationUtil
import android.view.SurfaceView
import com.google.android.exoplayer2.util.EGLSurfaceTexture.TextureImageListener
import android.graphics.SurfaceTexture
import com.google.android.exoplayer2.util.EGLSurfaceTexture
import com.google.android.exoplayer2.util.EGLSurfaceTexture.SecureMode
import com.google.android.exoplayer2.util.ParsableBitArray
import com.google.android.exoplayer2.util.TimestampAdjuster
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParser
import com.google.android.exoplayer2.util.XmlPullParserUtil
import com.google.android.exoplayer2.util.UnknownNull
import android.net.ConnectivityManager
import com.google.android.exoplayer2.util.NetworkTypeObserver
import com.google.android.exoplayer2.util.NetworkTypeObserver.Api31.DisplayInfoCallback
import android.telephony.TelephonyCallback
import android.telephony.TelephonyCallback.DisplayInfoListener
import android.telephony.TelephonyDisplayInfo
import android.net.NetworkInfo
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException
import com.google.android.exoplayer2.util.SystemHandlerWrapper.SystemMessage
import com.google.android.exoplayer2.util.CodecSpecificDataUtil
import com.google.android.exoplayer2.audio.AuxEffectInfo
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.C.AudioFlags
import com.google.android.exoplayer2.C.AudioAllowedCapturePolicy
import com.google.android.exoplayer2.C.SpatializationBehavior
import com.google.android.exoplayer2.audio.AudioAttributes.Api32
import com.google.android.exoplayer2.audio.AudioAttributes.AudioAttributesV21
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.C.ColorRange
import com.google.android.exoplayer2.C.ColorTransfer
import androidx.annotation.FloatRange
import android.media.MediaCodec
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdGroup
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdState
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.C.RoleFlags
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.C.SelectionFlags
import com.google.android.exoplayer2.C.StereoMode
import com.google.android.exoplayer2.C.CryptoType
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.Player.TimelineChangeReason
import com.google.android.exoplayer2.Player.MediaItemTransitionReason
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason
import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.DeviceInfo
import android.view.SurfaceHolder
import android.view.TextureView
import com.google.android.exoplayer2.Rating.RatingType
import com.google.common.primitives.Booleans
import com.google.common.base.MoreObjects
import com.google.android.exoplayer2.MediaItem.LiveConfiguration
import com.google.android.exoplayer2.BundleListRetriever
import com.google.android.exoplayer2.Timeline.RemotableTimeline
import com.google.android.exoplayer2.MediaItem.ClippingProperties
import com.google.android.exoplayer2.MediaItem.PlaybackProperties
import com.google.android.exoplayer2.MediaItem.RequestMetadata
import com.google.android.exoplayer2.MediaItem.AdsConfiguration
import com.google.android.exoplayer2.MediaItem.LocalConfiguration
import com.google.android.exoplayer2.MediaItem.ClippingConfiguration
import com.google.android.exoplayer2.MediaItem.DrmConfiguration
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.common.primitives.Ints
import android.view.accessibility.CaptioningManager
import com.google.android.exoplayer2.DeviceInfo.PlaybackType
import com.google.android.exoplayer2.MediaMetadata.PictureType
import com.google.android.exoplayer2.MediaMetadata.FolderType
import com.google.android.exoplayer2.ForwardingPlayer
import com.google.android.exoplayer2.BasePlayer
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import com.google.android.exoplayer2.SimpleBasePlayer
import androidx.annotation.CallSuper
import android.media.MediaPlayerimport

java.lang.annotation .Documentedimport java.lang.annotation .Retentionimport java.lang.annotation .RetentionPolicy
/** Utility methods for displaying [Notifications][Notification].  */
@SuppressLint("InlinedApi")
object NotificationUtil {
    /**
     * @see NotificationManager.IMPORTANCE_UNSPECIFIED
     */
    val IMPORTANCE_UNSPECIFIED: Int = NotificationManager.IMPORTANCE_UNSPECIFIED

    /**
     * @see NotificationManager.IMPORTANCE_NONE
     */
    val IMPORTANCE_NONE: Int = NotificationManager.IMPORTANCE_NONE

    /**
     * @see NotificationManager.IMPORTANCE_MIN
     */
    val IMPORTANCE_MIN: Int = NotificationManager.IMPORTANCE_MIN

    /**
     * @see NotificationManager.IMPORTANCE_LOW
     */
    val IMPORTANCE_LOW: Int = NotificationManager.IMPORTANCE_LOW

    /**
     * @see NotificationManager.IMPORTANCE_DEFAULT
     */
    val IMPORTANCE_DEFAULT: Int = NotificationManager.IMPORTANCE_DEFAULT

    /**
     * @see NotificationManager.IMPORTANCE_HIGH
     */
    val IMPORTANCE_HIGH: Int = NotificationManager.IMPORTANCE_HIGH

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
    fun createNotificationChannel(
            context: Context,
            id: String?,
            @StringRes nameResourceId: Int,
            @StringRes descriptionResourceId: Int,
            importance: @Importance Int) {
        if (Util.SDK_INT >= 26) {
            val notificationManager: NotificationManager? = Assertions.checkNotNull(
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
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
    @Target(TYPE_USE)
    @IntDef([IMPORTANCE_UNSPECIFIED, IMPORTANCE_NONE, IMPORTANCE_MIN, IMPORTANCE_LOW, IMPORTANCE_DEFAULT, IMPORTANCE_HIGH])
    annotation class Importance constructor()
}