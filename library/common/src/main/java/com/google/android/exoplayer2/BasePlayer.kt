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
package com.google.android.exoplayer2import

import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.util.*
import com.google.common.collect.ImmutableList
import com.google.errorprone.annotations.ForOverride

androidx.annotation .IntDef
import com.google.android.exoplayer2.ui.AdOverlayInfo
import android.view.ViewGroup
import com.google.common.collect.ImmutableList
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData
import android.os.Parcelable
import android.os.Parcel
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
import com.google.android.exoplayer2.text.Cue
import androidx.annotation.ColorInt
import org.checkerframework.dataflow.qual.Pure
import android.os.Bundle
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
import android.os.Looper
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Futures
import org.checkerframework.checker.nullness.qual.PolyNull
import androidx.annotation.RequiresApi
import android.util.SparseLongArray
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.C.PcmEncoding
import android.annotation.SuppressLint
import com.google.android.exoplayer2.C.AudioUsage
import com.google.android.exoplayer2.C.AudioContentType
import android.media.MediaDrm
import android.telephony.TelephonyManager
import android.app.UiModeManager
import android.hardware.display.DisplayManager
import android.view.WindowManager
import android.database.sqlite.SQLiteDatabase
import android.database.DatabaseUtils
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
import android.os.IBinder
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
import com.google.android.exoplayer2.Player.TimelineChangeReason
import com.google.android.exoplayer2.Player.MediaItemTransitionReason
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason
import com.google.android.exoplayer2.Player.DiscontinuityReason
import android.view.SurfaceHolder
import android.view.TextureView
import com.google.android.exoplayer2.Rating.RatingType
import com.google.common.primitives.Booleans
import com.google.common.base.MoreObjects
import com.google.android.exoplayer2.MediaItem.LiveConfiguration
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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import androidx.annotation.CallSuper
import android.media.MediaPlayer

/** Abstract base [Player] which implements common implementation independent methods.  */
abstract class BasePlayer protected constructor() : Player {
    protected val window: Timeline.Window

    init {
        window = Timeline.Window()
    }

    public override fun setMediaItem(mediaItem: MediaItem?) {
        setMediaItems(ImmutableList.of(mediaItem))
    }

    public override fun setMediaItem(mediaItem: MediaItem?, startPositionMs: Long) {
        setMediaItems(ImmutableList.of(mediaItem),  /* startIndex= */0, startPositionMs)
    }

    public override fun setMediaItem(mediaItem: MediaItem?, resetPosition: Boolean) {
        setMediaItems(ImmutableList.of(mediaItem), resetPosition)
    }

    public override fun setMediaItems(mediaItems: List<MediaItem?>?) {
        setMediaItems(mediaItems,  /* resetPosition= */true)
    }

    public override fun addMediaItem(index: Int, mediaItem: MediaItem?) {
        addMediaItems(index, ImmutableList.of(mediaItem))
    }

    public override fun addMediaItem(mediaItem: MediaItem?) {
        addMediaItems(ImmutableList.of(mediaItem))
    }

    public override fun addMediaItems(mediaItems: List<MediaItem?>?) {
        addMediaItems(Int.MAX_VALUE, mediaItems)
    }

    public override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        if (currentIndex != newIndex) {
            moveMediaItems( /* fromIndex= */currentIndex,  /* toIndex= */currentIndex + 1, newIndex)
        }
    }

    public override fun removeMediaItem(index: Int) {
        removeMediaItems( /* fromIndex= */index,  /* toIndex= */index + 1)
    }

    public override fun clearMediaItems() {
        removeMediaItems( /* fromIndex= */0, Int.MAX_VALUE)
    }

    public override fun isCommandAvailable(command: @Player.Command Int): Boolean {
        return getAvailableCommands().contains(command)
    }

    /**
     * {@inheritDoc}
     *
     *
     * BasePlayer and its descendants will return `true`.
     */
    public override fun canAdvertiseSession(): Boolean {
        return true
    }

    public override fun play() {
        setPlayWhenReady(true)
    }

    public override fun pause() {
        setPlayWhenReady(false)
    }

    override val isPlaying: Boolean
        get() {
            return ((getPlaybackState() == Player.Companion.STATE_READY
                    ) && getPlayWhenReady()
                    && (getPlaybackSuppressionReason() == Player.Companion.PLAYBACK_SUPPRESSION_REASON_NONE))
        }

    public override fun seekToDefaultPosition() {
        seekToDefaultPosition(getCurrentMediaItemIndex())
    }

    public override fun seekToDefaultPosition(mediaItemIndex: Int) {
        seekTo(mediaItemIndex,  /* positionMs= */C.TIME_UNSET)
    }

    public override fun seekTo(positionMs: Long) {
        seekTo(getCurrentMediaItemIndex(), positionMs)
    }

    public override fun seekBack() {
        seekToOffset(-getSeekBackIncrement())
    }

    public override fun seekForward() {
        seekToOffset(getSeekForwardIncrement())
    }

    @Deprecated("Use {@link #hasPreviousMediaItem()} instead.")
    public override fun hasPrevious(): Boolean {
        return hasPreviousMediaItem()
    }

    @Deprecated("Use {@link #hasPreviousMediaItem()} instead.")
    public override fun hasPreviousWindow(): Boolean {
        return hasPreviousMediaItem()
    }

    public override fun hasPreviousMediaItem(): Boolean {
        return previousMediaItemIndex != C.INDEX_UNSET
    }

    @Deprecated("Use {@link #seekToPreviousMediaItem()} instead.")
    public override fun previous() {
        seekToPreviousMediaItem()
    }

    @Deprecated("Use {@link #seekToPreviousMediaItem()} instead.")
    public override fun seekToPreviousWindow() {
        seekToPreviousMediaItem()
    }

    public override fun seekToPreviousMediaItem() {
        val previousMediaItemIndex: Int = previousMediaItemIndex
        if (previousMediaItemIndex == C.INDEX_UNSET) {
            return
        }
        if (previousMediaItemIndex == getCurrentMediaItemIndex()) {
            repeatCurrentMediaItem()
        } else {
            seekToDefaultPosition(previousMediaItemIndex)
        }
    }

    public override fun seekToPrevious() {
        val timeline: Timeline? = getCurrentTimeline()
        if (timeline.isEmpty() || isPlayingAd()) {
            return
        }
        val hasPreviousMediaItem: Boolean = hasPreviousMediaItem()
        if (isCurrentMediaItemLive && !isCurrentMediaItemSeekable) {
            if (hasPreviousMediaItem) {
                seekToPreviousMediaItem()
            }
        } else if (hasPreviousMediaItem && getCurrentPosition() <= getMaxSeekToPreviousPosition()) {
            seekToPreviousMediaItem()
        } else {
            seekTo( /* positionMs= */0)
        }
    }

    @Deprecated("Use {@link #hasNextMediaItem()} instead.")
    public override fun hasNext(): Boolean {
        return hasNextMediaItem()
    }

    @Deprecated("Use {@link #hasNextMediaItem()} instead.")
    public override fun hasNextWindow(): Boolean {
        return hasNextMediaItem()
    }

    public override fun hasNextMediaItem(): Boolean {
        return nextMediaItemIndex != C.INDEX_UNSET
    }

    @Deprecated("Use {@link #seekToNextMediaItem()} instead.")
    public override fun next() {
        seekToNextMediaItem()
    }

    @Deprecated("Use {@link #seekToNextMediaItem()} instead.")
    public override fun seekToNextWindow() {
        seekToNextMediaItem()
    }

    public override fun seekToNextMediaItem() {
        val nextMediaItemIndex: Int = nextMediaItemIndex
        if (nextMediaItemIndex == C.INDEX_UNSET) {
            return
        }
        if (nextMediaItemIndex == getCurrentMediaItemIndex()) {
            repeatCurrentMediaItem()
        } else {
            seekToDefaultPosition(nextMediaItemIndex)
        }
    }

    public override fun seekToNext() {
        val timeline: Timeline? = getCurrentTimeline()
        if (timeline.isEmpty() || isPlayingAd()) {
            return
        }
        if (hasNextMediaItem()) {
            seekToNextMediaItem()
        } else if (isCurrentMediaItemLive && isCurrentMediaItemDynamic) {
            seekToDefaultPosition()
        }
    }

    public override fun setPlaybackSpeed(speed: Float) {
        setPlaybackParameters(getPlaybackParameters().withSpeed(speed))
    }

    @get:Deprecated("Use {@link #getCurrentMediaItemIndex()} instead.")
    override val currentWindowIndex: Int
        get() {
            return getCurrentMediaItemIndex()
        }

    @get:Deprecated("Use {@link #getNextMediaItemIndex()} instead.")
    override val nextWindowIndex: Int
        get() {
            return nextMediaItemIndex
        }
    override val nextMediaItemIndex: Int
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return if (timeline.isEmpty()) C.INDEX_UNSET else timeline!!.getNextWindowIndex(
                    getCurrentMediaItemIndex(), repeatModeForNavigation, getShuffleModeEnabled())
        }

    @get:Deprecated("Use {@link #getPreviousMediaItemIndex()} instead.")
    override val previousWindowIndex: Int
        get() {
            return previousMediaItemIndex
        }
    override val previousMediaItemIndex: Int
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return if (timeline.isEmpty()) C.INDEX_UNSET else timeline!!.getPreviousWindowIndex(
                    getCurrentMediaItemIndex(), repeatModeForNavigation, getShuffleModeEnabled())
        }
    override val currentMediaItem: MediaItem?
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return if (timeline.isEmpty()) null else timeline!!.getWindow(getCurrentMediaItemIndex(), window).mediaItem
        }
    override val mediaItemCount: Int
        get() {
            return getCurrentTimeline().getWindowCount()
        }

    public override fun getMediaItemAt(index: Int): MediaItem? {
        return getCurrentTimeline().getWindow(index, window).mediaItem
    }

    override val currentManifest: Any?
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return if (timeline.isEmpty()) null else timeline!!.getWindow(getCurrentMediaItemIndex(), window).manifest
        }
    override val bufferedPercentage: Int
        get() {
            val position: Long = getBufferedPosition()
            val duration: Long = getDuration()
            return if (position == C.TIME_UNSET || duration == C.TIME_UNSET) 0 else if (duration == 0L) 100 else Util.constrainValue(((position * 100) / duration).toInt(), 0, 100)
        }

    @get:Deprecated("Use {@link #isCurrentMediaItemDynamic()} instead.")
    override val isCurrentWindowDynamic: Boolean
        get() {
            return isCurrentMediaItemDynamic
        }
    override val isCurrentMediaItemDynamic: Boolean
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return !timeline.isEmpty() && timeline!!.getWindow(getCurrentMediaItemIndex(), window).isDynamic
        }

    @get:Deprecated("Use {@link #isCurrentMediaItemLive()} instead.")
    override val isCurrentWindowLive: Boolean
        get() {
            return isCurrentMediaItemLive
        }
    override val isCurrentMediaItemLive: Boolean
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return !timeline.isEmpty() && timeline!!.getWindow(getCurrentMediaItemIndex(), window).isLive()
        }
    override val currentLiveOffset: Long
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            if (timeline.isEmpty()) {
                return C.TIME_UNSET
            }
            val windowStartTimeMs: Long = timeline!!.getWindow(getCurrentMediaItemIndex(), window).windowStartTimeMs
            if (windowStartTimeMs == C.TIME_UNSET) {
                return C.TIME_UNSET
            }
            return window.getCurrentUnixTimeMs() - window.windowStartTimeMs - getContentPosition()
        }

    @get:Deprecated("Use {@link #isCurrentMediaItemSeekable()} instead.")
    override val isCurrentWindowSeekable: Boolean
        get() {
            return isCurrentMediaItemSeekable
        }
    override val isCurrentMediaItemSeekable: Boolean
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return !timeline.isEmpty() && timeline!!.getWindow(getCurrentMediaItemIndex(), window).isSeekable
        }
    override val contentDuration: Long
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return if (timeline.isEmpty()) C.TIME_UNSET else timeline!!.getWindow(getCurrentMediaItemIndex(), window).getDurationMs()
        }

    /**
     * Repeat the current media item.
     *
     *
     * The default implementation seeks to the default position in the current item, which can be
     * overridden for additional handling.
     */
    @ForOverride
    protected open fun repeatCurrentMediaItem() {
        seekToDefaultPosition()
    }

    private val repeatModeForNavigation: @Player.RepeatMode Int
        private get() {
            val repeatMode: @Player.RepeatMode Int = getRepeatMode()
            return if (repeatMode == Player.Companion.REPEAT_MODE_ONE) Player.Companion.REPEAT_MODE_OFF else repeatMode
        }

    private fun seekToOffset(offsetMs: Long) {
        var positionMs: Long = getCurrentPosition() + offsetMs
        val durationMs: Long = getDuration()
        if (durationMs != C.TIME_UNSET) {
            positionMs = Math.min(positionMs, durationMs)
        }
        positionMs = Math.max(positionMs, 0)
        seekTo(positionMs)
    }
}