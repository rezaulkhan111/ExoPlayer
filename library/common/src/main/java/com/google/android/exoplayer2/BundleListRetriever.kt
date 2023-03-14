/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2import

import android.os.*
import com.google.android.exoplayer2.BundleListRetriever
import com.google.android.exoplayer2.util.*
import com.google.common.collect.ImmutableList

androidx.annotation .IntDef
import com.google.android.exoplayer2.ui.AdOverlayInfo
import android.view.ViewGroup
import com.google.common.collect.ImmutableList
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData
import android.text.TextUtils
import com.google.android.exoplayer2.text.span.TextAnnotation
import com.google.android.exoplayer2.text.span.LanguageFeatureSpan
import android.text.Spannable
import com.google.android.exoplayer2.text.span.TextEmphasisSpan.MarkFill
import com.google.android.exoplayer2.text.span.TextEmphasisSpan
import android.graphics.Bitmap
import com.google.android.exoplayer2.text.Cue.LineType
import com.google.android.exoplayer2.text.Cue.TextSizeType
import com.google.android.exoplayer2.text.Cue.VerticalType
import com.google.android.exoplayer2.Bundleable
import com.google.android.exoplayer2.text.Cue
import androidx.annotation.ColorInt
import org.checkerframework.dataflow.qual.Pure
import android.text.Spanned
import android.text.SpannedString
import com.google.android.exoplayer2.text.CueGroup
import android.content.IntentFilter
import android.content.Intent
import android.content.ComponentName
import android.app.Activity
import com.google.android.exoplayer2.MediaItem.SubtitleConfiguration
import android.provider.MediaStore
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import org.checkerframework.checker.initialization.qual.UnknownInitialization
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Futures
import org.checkerframework.checker.nullness.qual.PolyNull
import androidx.annotation.RequiresApi
import android.util.SparseLongArray
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.C.PcmEncoding
import android.annotation.SuppressLint
import com.google.android.exoplayer2.C.AudioUsage
import com.google.android.exoplayer2.C.AudioContentType
import android.media.MediaDrm
import com.google.android.exoplayer2.PlaybackException
import android.telephony.TelephonyManager
import android.app.UiModeManager
import android.hardware.display.DisplayManager
import android.view.WindowManager
import android.database.sqlite.SQLiteDatabase
import android.database.DatabaseUtils
import com.google.android.exoplayer2.Player
import android.Manifest.permission
import android.security.NetworkSecurityPolicy
import android.opengl.EGL14
import javax.microedition.khronos.egl.EGL10
import android.opengl.GLES20
import com.google.android.exoplayer2.util.GlUtil.GlException
import com.google.android.exoplayer2.util.GlUtil.Api17
import android.opengl.GLU
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.SparseBooleanArray
import com.google.android.exoplayer2.util.GlProgram.Uniform
import com.google.android.exoplayer2.util.MimeTypes.CustomMimeType
import com.google.android.exoplayer2.util.MimeTypes.Mp4aObjectType
import com.google.android.exoplayer2.util.AtomicFile.AtomicFileOutputStream
import kotlin.annotations.jvm.UnderMigration
import kotlin.annotations.jvm.MigrationStatus
import com.google.android.exoplayer2.util.ListenerSet.IterationFinishedEvent
import android.util.SparseArray
import com.google.android.exoplayer2.video.ColorInfo
import android.media.MediaFormat
import android.app.NotificationManager
import androidx.annotation.StringRes
import com.google.android.exoplayer2.util.NotificationUtil.Importance
import android.app.NotificationChannel
import android.view.SurfaceView
import com.google.android.exoplayer2.util.EGLSurfaceTexture.TextureImageListener
import android.graphics.SurfaceTexture
import com.google.android.exoplayer2.util.EGLSurfaceTexture.SecureMode
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParser
import android.net.ConnectivityManager
import com.google.android.exoplayer2.util.NetworkTypeObserver.Api31.DisplayInfoCallback
import android.telephony.TelephonyCallback
import android.telephony.TelephonyCallback.DisplayInfoListener
import android.telephony.TelephonyDisplayInfo
import android.net.NetworkInfo
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException
import com.google.android.exoplayer2.util.SystemHandlerWrapper.SystemMessage
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
import android.media.MediaPlayer

/**
 * A [Binder] to transfer a list of [Bundles][Bundle] across processes by splitting the
 * list into multiple transactions.
 *
 *
 * Note: Using this class causes synchronous binder calls in the opposite direction regardless of
 * the "oneway" property.
 *
 *
 * Example usage:
 *
 * <pre>`// Sender
 * List<Bundle> list = ...;
 * IBinder binder = new BundleListRetriever(list);
 * Bundle bundle = new Bundle();
 * bundle.putBinder("list", binder);
 *
 * // Receiver
 * Bundle bundle = ...; // Received from the sender
 * IBinder binder = bundle.getBinder("list");
 * List<Bundle> list = BundleListRetriever.getList(binder);
`</pre> *
 */
class BundleListRetriever constructor(list: List<Bundle>?) : Binder() {
    private val list: ImmutableList<Bundle>

    /** Creates a [Binder] to send a list of [Bundles][Bundle] to another process.  */
    init {
        this.list = ImmutableList.copyOf(list)
    }

    @Throws(RemoteException::class)
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != FIRST_CALL_TRANSACTION) {
            return super.onTransact(code, data, reply, flags)
        }
        if (reply == null) {
            return false
        }
        val count: Int = list.size
        var index: Int = data.readInt()
        while (index < count && reply.dataSize() < BundleListRetriever.Companion.SUGGESTED_MAX_IPC_SIZE) {
            reply.writeInt(BundleListRetriever.Companion.REPLY_CONTINUE)
            reply.writeBundle(list.get(index))
            index++
        }
        reply.writeInt(if (index < count) BundleListRetriever.Companion.REPLY_BREAK else BundleListRetriever.Companion.REPLY_END_OF_LIST)
        return true
    }

    companion object {
        // Soft limit of an IPC buffer size
        private val SUGGESTED_MAX_IPC_SIZE: Int = if (Util.SDK_INT >= 30) IBinder.getSuggestedMaxIpcSizeBytes() else 64 * 1024
        private val REPLY_END_OF_LIST: Int = 0
        private val REPLY_CONTINUE: Int = 1
        private val REPLY_BREAK: Int = 2

        /**
         * Gets a list of [Bundles][Bundle] from a [BundleListRetriever].
         *
         * @param binder A binder interface backed by [BundleListRetriever].
         * @return The list of [Bundles][Bundle].
         */
        fun getList(binder: IBinder): ImmutableList<Bundle?> {
            val builder: ImmutableList.Builder<Bundle?> = ImmutableList.builder()
            var index: Int = 0
            var replyCode: Int = BundleListRetriever.Companion.REPLY_CONTINUE
            while (replyCode != BundleListRetriever.Companion.REPLY_END_OF_LIST) {
                val data: Parcel = Parcel.obtain()
                val reply: Parcel = Parcel.obtain()
                try {
                    data.writeInt(index)
                    try {
                        binder.transact(FIRST_CALL_TRANSACTION, data, reply,  /* flags= */0)
                    } catch (e: RemoteException) {
                        throw RuntimeException(e)
                    }
                    while ((reply.readInt().also({ replyCode = it })) == BundleListRetriever.Companion.REPLY_CONTINUE) {
                        builder.add(Assertions.checkNotNull(reply.readBundle()))
                        index++
                    }
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
            return builder.build()
        }
    }
}