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

import android.os.Looper
import android.view.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.util.*
import com.google.android.exoplayer2.video.VideoSize

androidx.annotation .IntDef
import com.google.android.exoplayer2.ui.AdOverlayInfo
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

/**
 * A [Player] that forwards operations to another [Player]. Applications can use this
 * class to suppress or modify specific operations, by overriding the respective methods.
 */
open class ForwardingPlayer
/** Creates a new instance that forwards all operations to `player`.  */ constructor(
        /** Returns the [Player] to which operations are forwarded.  */
        val wrappedPlayer: Player) : Player {

    /** Calls [Player.getApplicationLooper] on the delegate and returns the result.  */
    override val applicationLooper: Looper?
        get() {
            return wrappedPlayer.getApplicationLooper()
        }

    /** Calls [Player.addListener] on the delegate.  */
    public override fun addListener(listener: Player.Listener?) {
        wrappedPlayer.addListener(ForwardingPlayer.ForwardingListener(this, (listener)!!))
    }

    /** Calls [Player.removeListener] on the delegate.  */
    public override fun removeListener(listener: Player.Listener?) {
        wrappedPlayer.removeListener(ForwardingPlayer.ForwardingListener(this, (listener)!!))
    }

    /** Calls [Player.setMediaItems] on the delegate.  */
    public override fun setMediaItems(mediaItems: List<MediaItem?>?) {
        wrappedPlayer.setMediaItems(mediaItems)
    }

    /** Calls [Player.setMediaItems] ()} on the delegate.  */
    public override fun setMediaItems(mediaItems: List<MediaItem?>?, resetPosition: Boolean) {
        wrappedPlayer.setMediaItems(mediaItems, resetPosition)
    }

    /** Calls [Player.setMediaItems] on the delegate.  */
    public override fun setMediaItems(mediaItems: List<MediaItem?>?, startIndex: Int, startPositionMs: Long) {
        wrappedPlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    /** Calls [Player.setMediaItem] on the delegate.  */
    public override fun setMediaItem(mediaItem: MediaItem?) {
        wrappedPlayer.setMediaItem(mediaItem)
    }

    /** Calls [Player.setMediaItem] on the delegate.  */
    public override fun setMediaItem(mediaItem: MediaItem?, startPositionMs: Long) {
        wrappedPlayer.setMediaItem(mediaItem, startPositionMs)
    }

    /** Calls [Player.setMediaItem] on the delegate.  */
    public override fun setMediaItem(mediaItem: MediaItem?, resetPosition: Boolean) {
        wrappedPlayer.setMediaItem(mediaItem, resetPosition)
    }

    /** Calls [Player.addMediaItem] on the delegate.  */
    public override fun addMediaItem(mediaItem: MediaItem?) {
        wrappedPlayer.addMediaItem(mediaItem)
    }

    /** Calls [Player.addMediaItem] on the delegate.  */
    public override fun addMediaItem(index: Int, mediaItem: MediaItem?) {
        wrappedPlayer.addMediaItem(index, mediaItem)
    }

    /** Calls [Player.addMediaItems] on the delegate.  */
    public override fun addMediaItems(mediaItems: List<MediaItem?>?) {
        wrappedPlayer.addMediaItems(mediaItems)
    }

    /** Calls [Player.addMediaItems] on the delegate.  */
    public override fun addMediaItems(index: Int, mediaItems: List<MediaItem?>?) {
        wrappedPlayer.addMediaItems(index, mediaItems)
    }

    /** Calls [Player.moveMediaItem] on the delegate.  */
    public override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        wrappedPlayer.moveMediaItem(currentIndex, newIndex)
    }

    /** Calls [Player.moveMediaItems] on the delegate.  */
    public override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        wrappedPlayer.moveMediaItems(fromIndex, toIndex, newIndex)
    }

    /** Calls [Player.removeMediaItem] on the delegate.  */
    public override fun removeMediaItem(index: Int) {
        wrappedPlayer.removeMediaItem(index)
    }

    /** Calls [Player.removeMediaItems] on the delegate.  */
    public override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        wrappedPlayer.removeMediaItems(fromIndex, toIndex)
    }

    /** Calls [Player.clearMediaItems] on the delegate.  */
    public override fun clearMediaItems() {
        wrappedPlayer.clearMediaItems()
    }

    /** Calls [Player.isCommandAvailable] on the delegate and returns the result.  */
    public override fun isCommandAvailable(command: @Player.Command Int): Boolean {
        return wrappedPlayer.isCommandAvailable(command)
    }

    /** Calls [Player.canAdvertiseSession] on the delegate and returns the result.  */
    public override fun canAdvertiseSession(): Boolean {
        return wrappedPlayer.canAdvertiseSession()
    }

    /** Calls [Player.getAvailableCommands] on the delegate and returns the result.  */
    override val availableCommands: Player.Commands?
        get() {
            return wrappedPlayer.getAvailableCommands()
        }

    /** Calls [Player.prepare] on the delegate.  */
    public override fun prepare() {
        wrappedPlayer.prepare()
    }

    /** Calls [Player.getPlaybackState] on the delegate and returns the result.  */
    override val playbackState: @Player.State Int
        get() {
            return wrappedPlayer.getPlaybackState()
        }

    /** Calls [Player.getPlaybackSuppressionReason] on the delegate and returns the result.  */
    override val playbackSuppressionReason: @PlaybackSuppressionReason Int
        get() {
            return wrappedPlayer.getPlaybackSuppressionReason()
        }

    /** Calls [Player.isPlaying] on the delegate and returns the result.  */
    override val isPlaying: Boolean
        get() {
            return wrappedPlayer.isPlaying()
        }

    /** Calls [Player.getPlayerError] on the delegate and returns the result.  */
    override val playerError: PlaybackException?
        get() {
            return wrappedPlayer.getPlayerError()
        }

    /** Calls [Player.play] on the delegate.  */
    public override fun play() {
        wrappedPlayer.play()
    }

    /** Calls [Player.pause] on the delegate.  */
    public override fun pause() {
        wrappedPlayer.pause()
    }
    /** Calls [Player.getPlayWhenReady] on the delegate and returns the result.  */
    /** Calls [Player.setPlayWhenReady] on the delegate.  */
    override var playWhenReady: Boolean
        get() {
            return wrappedPlayer.getPlayWhenReady()
        }
        set(playWhenReady) {
            wrappedPlayer.setPlayWhenReady(playWhenReady)
        }
    /** Calls [Player.getRepeatMode] on the delegate and returns the result.  */
    /** Calls [Player.setRepeatMode] on the delegate.  */
    override var repeatMode: @Player.RepeatMode Int
        get() {
            return wrappedPlayer.getRepeatMode()
        }
        set(repeatMode) {
            wrappedPlayer.setRepeatMode(repeatMode)
        }
    /** Calls [Player.getShuffleModeEnabled] on the delegate and returns the result.  */
    /** Calls [Player.setShuffleModeEnabled] on the delegate.  */
    override var shuffleModeEnabled: Boolean
        get() {
            return wrappedPlayer.getShuffleModeEnabled()
        }
        set(shuffleModeEnabled) {
            wrappedPlayer.setShuffleModeEnabled(shuffleModeEnabled)
        }

    /** Calls [Player.isLoading] on the delegate and returns the result.  */
    override val isLoading: Boolean
        get() {
            return wrappedPlayer.isLoading()
        }

    /** Calls [Player.seekToDefaultPosition] on the delegate.  */
    public override fun seekToDefaultPosition() {
        wrappedPlayer.seekToDefaultPosition()
    }

    /** Calls [Player.seekToDefaultPosition] on the delegate.  */
    public override fun seekToDefaultPosition(mediaItemIndex: Int) {
        wrappedPlayer.seekToDefaultPosition(mediaItemIndex)
    }

    /** Calls [Player.seekTo] on the delegate.  */
    public override fun seekTo(positionMs: Long) {
        wrappedPlayer.seekTo(positionMs)
    }

    /** Calls [Player.seekTo] on the delegate.  */
    public override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        wrappedPlayer.seekTo(mediaItemIndex, positionMs)
    }

    /** Calls [Player.getSeekBackIncrement] on the delegate and returns the result.  */
    override val seekBackIncrement: Long
        get() {
            return wrappedPlayer.getSeekBackIncrement()
        }

    /** Calls [Player.seekBack] on the delegate.  */
    public override fun seekBack() {
        wrappedPlayer.seekBack()
    }

    /** Calls [Player.getSeekForwardIncrement] on the delegate and returns the result.  */
    override val seekForwardIncrement: Long
        get() {
            return wrappedPlayer.getSeekForwardIncrement()
        }

    /** Calls [Player.seekForward] on the delegate.  */
    public override fun seekForward() {
        wrappedPlayer.seekForward()
    }

    /**
     * Calls [Player.hasPrevious] on the delegate and returns the result.
     *
     */
    @Deprecated("Use {@link #hasPreviousMediaItem()} instead.")
    public override fun hasPrevious(): Boolean {
        return wrappedPlayer.hasPrevious()
    }

    /**
     * Calls [Player.hasPreviousWindow] on the delegate and returns the result.
     *
     */
    @Deprecated("Use {@link #hasPreviousMediaItem()} instead.")
    public override fun hasPreviousWindow(): Boolean {
        return wrappedPlayer.hasPreviousWindow()
    }

    /** Calls [Player.hasPreviousMediaItem] on the delegate and returns the result.  */
    public override fun hasPreviousMediaItem(): Boolean {
        return wrappedPlayer.hasPreviousMediaItem()
    }

    /**
     * Calls [Player.previous] on the delegate.
     *
     */
    @Deprecated("Use {@link #seekToPreviousMediaItem()} instead.")
    public override fun previous() {
        wrappedPlayer.previous()
    }

    /**
     * Calls [Player.seekToPreviousWindow] on the delegate.
     *
     */
    @Deprecated("Use {@link #seekToPreviousMediaItem()} instead.")
    public override fun seekToPreviousWindow() {
        wrappedPlayer.seekToPreviousWindow()
    }

    /** Calls [Player.seekToPreviousMediaItem] on the delegate.  */
    public override fun seekToPreviousMediaItem() {
        wrappedPlayer.seekToPreviousMediaItem()
    }

    /** Calls [Player.seekToPrevious] on the delegate.  */
    public override fun seekToPrevious() {
        wrappedPlayer.seekToPrevious()
    }

    /** Calls [Player.getMaxSeekToPreviousPosition] on the delegate and returns the result.  */
    override val maxSeekToPreviousPosition: Long
        get() {
            return wrappedPlayer.getMaxSeekToPreviousPosition()
        }

    /**
     * Calls [Player.hasNext] on the delegate and returns the result.
     *
     */
    @Deprecated("Use {@link #hasNextMediaItem()} instead.")
    public override fun hasNext(): Boolean {
        return wrappedPlayer.hasNext()
    }

    /**
     * Calls [Player.hasNextWindow] on the delegate and returns the result.
     *
     */
    @Deprecated("Use {@link #hasNextMediaItem()} instead.")
    public override fun hasNextWindow(): Boolean {
        return wrappedPlayer.hasNextWindow()
    }

    /** Calls [Player.hasNextMediaItem] on the delegate and returns the result.  */
    public override fun hasNextMediaItem(): Boolean {
        return wrappedPlayer.hasNextMediaItem()
    }

    /**
     * Calls [Player.next] on the delegate.
     *
     */
    @Deprecated("Use {@link #seekToNextMediaItem()} instead.")
    public override fun next() {
        wrappedPlayer.next()
    }

    /**
     * Calls [Player.seekToNextWindow] on the delegate.
     *
     */
    @Deprecated("Use {@link #seekToNextMediaItem()} instead.")
    public override fun seekToNextWindow() {
        wrappedPlayer.seekToNextWindow()
    }

    /** Calls [Player.seekToNextMediaItem] on the delegate.  */
    public override fun seekToNextMediaItem() {
        wrappedPlayer.seekToNextMediaItem()
    }

    /** Calls [Player.seekToNext] on the delegate.  */
    public override fun seekToNext() {
        wrappedPlayer.seekToNext()
    }

    /** Calls [Player.setPlaybackSpeed] on the delegate.  */
    public override fun setPlaybackSpeed(speed: Float) {
        wrappedPlayer.setPlaybackSpeed(speed)
    }
    /** Calls [Player.getPlaybackParameters] on the delegate and returns the result.  */
    /** Calls [Player.setPlaybackParameters] on the delegate.  */
    override var playbackParameters: PlaybackParameters?
        get() {
            return wrappedPlayer.getPlaybackParameters()
        }
        set(playbackParameters) {
            wrappedPlayer.setPlaybackParameters(playbackParameters)
        }

    /** Calls [Player.stop] on the delegate.  */
    public override fun stop() {
        wrappedPlayer.stop()
    }

    /**
     * Calls [Player.stop] on the delegate.
     *
     */
    @Deprecated("Use {@link #stop()} and {@link #clearMediaItems()} (if {@code reset} is true) or\n" + "        just {@link #stop()} (if {@code reset} is false). Any player error will be cleared when\n" + "        {@link #prepare() re-preparing} the player.")
    public override fun stop(reset: Boolean) {
        wrappedPlayer.stop(reset)
    }

    /** Calls [Player.release] on the delegate.  */
    public override fun release() {
        wrappedPlayer.release()
    }

    /** Calls [Player.getCurrentTracks] on the delegate and returns the result.  */
    override val currentTracks: Tracks?
        get() {
            return wrappedPlayer.getCurrentTracks()
        }
    /** Calls [Player.getTrackSelectionParameters] on the delegate and returns the result.  */
    /** Calls [Player.setTrackSelectionParameters] on the delegate.  */
    override var trackSelectionParameters: TrackSelectionParameters?
        get() {
            return wrappedPlayer.getTrackSelectionParameters()
        }
        set(parameters) {
            wrappedPlayer.setTrackSelectionParameters(parameters)
        }

    /** Calls [Player.getMediaMetadata] on the delegate and returns the result.  */
    override val mediaMetadata: MediaMetadata?
        get() {
            return wrappedPlayer.getMediaMetadata()
        }
    /** Calls [Player.getPlaylistMetadata] on the delegate and returns the result.  */
    /** Calls [Player.setPlaylistMetadata] on the delegate.  */
    override var playlistMetadata: MediaMetadata?
        get() {
            return wrappedPlayer.getPlaylistMetadata()
        }
        set(mediaMetadata) {
            wrappedPlayer.setPlaylistMetadata(mediaMetadata)
        }

    /** Calls [Player.getCurrentManifest] on the delegate and returns the result.  */
    override val currentManifest: Any?
        get() {
            return wrappedPlayer.getCurrentManifest()
        }

    /** Calls [Player.getCurrentTimeline] on the delegate and returns the result.  */
    override val currentTimeline: Timeline?
        get() {
            return wrappedPlayer.getCurrentTimeline()
        }

    /** Calls [Player.getCurrentPeriodIndex] on the delegate and returns the result.  */
    override val currentPeriodIndex: Int
        get() {
            return wrappedPlayer.getCurrentPeriodIndex()
        }

    /**
     * Calls [Player.getCurrentWindowIndex] on the delegate and returns the result.
     *
     */
    @get:Deprecated("Use {@link #getCurrentMediaItemIndex()} instead.")
    override val currentWindowIndex: Int
        get() {
            return wrappedPlayer.getCurrentWindowIndex()
        }

    /** Calls [Player.getCurrentMediaItemIndex] on the delegate and returns the result.  */
    override val currentMediaItemIndex: Int
        get() {
            return wrappedPlayer.getCurrentMediaItemIndex()
        }

    /**
     * Calls [Player.getNextWindowIndex] on the delegate and returns the result.
     *
     */
    @get:Deprecated("Use {@link #getNextMediaItemIndex()} instead.")
    override val nextWindowIndex: Int
        get() {
            return wrappedPlayer.getNextWindowIndex()
        }

    /** Calls [Player.getNextMediaItemIndex] on the delegate and returns the result.  */
    override val nextMediaItemIndex: Int
        get() {
            return wrappedPlayer.getNextMediaItemIndex()
        }

    /**
     * Calls [Player.getPreviousWindowIndex] on the delegate and returns the result.
     *
     */
    @get:Deprecated("Use {@link #getPreviousMediaItemIndex()} instead.")
    override val previousWindowIndex: Int
        get() {
            return wrappedPlayer.getPreviousWindowIndex()
        }

    /** Calls [Player.getPreviousMediaItemIndex] on the delegate and returns the result.  */
    override val previousMediaItemIndex: Int
        get() {
            return wrappedPlayer.getPreviousMediaItemIndex()
        }

    /** Calls [Player.getCurrentMediaItem] on the delegate and returns the result.  */
    override val currentMediaItem: MediaItem?
        get() {
            return wrappedPlayer.getCurrentMediaItem()
        }

    /** Calls [Player.getMediaItemCount] on the delegate and returns the result.  */
    override val mediaItemCount: Int
        get() {
            return wrappedPlayer.getMediaItemCount()
        }

    /** Calls [Player.getMediaItemAt] on the delegate and returns the result.  */
    public override fun getMediaItemAt(index: Int): MediaItem? {
        return wrappedPlayer.getMediaItemAt(index)
    }

    /** Calls [Player.getDuration] on the delegate and returns the result.  */
    override val duration: Long
        get() {
            return wrappedPlayer.getDuration()
        }

    /** Calls [Player.getCurrentPosition] on the delegate and returns the result.  */
    override val currentPosition: Long
        get() {
            return wrappedPlayer.getCurrentPosition()
        }

    /** Calls [Player.getBufferedPosition] on the delegate and returns the result.  */
    override val bufferedPosition: Long
        get() {
            return wrappedPlayer.getBufferedPosition()
        }

    /** Calls [Player.getBufferedPercentage] on the delegate and returns the result.  */
    override val bufferedPercentage: Int
        get() {
            return wrappedPlayer.getBufferedPercentage()
        }

    /** Calls [Player.getTotalBufferedDuration] on the delegate and returns the result.  */
    override val totalBufferedDuration: Long
        get() {
            return wrappedPlayer.getTotalBufferedDuration()
        }

    /**
     * Calls [Player.isCurrentWindowDynamic] on the delegate and returns the result.
     *
     */
    @get:Deprecated("Use {@link #isCurrentMediaItemDynamic()} instead.")
    override val isCurrentWindowDynamic: Boolean
        get() {
            return wrappedPlayer.isCurrentWindowDynamic()
        }

    /** Calls [Player.isCurrentMediaItemDynamic] on the delegate and returns the result.  */
    override val isCurrentMediaItemDynamic: Boolean
        get() {
            return wrappedPlayer.isCurrentMediaItemDynamic()
        }

    /**
     * Calls [Player.isCurrentWindowLive] on the delegate and returns the result.
     *
     */
    @get:Deprecated("Use {@link #isCurrentMediaItemLive()} instead.")
    override val isCurrentWindowLive: Boolean
        get() {
            return wrappedPlayer.isCurrentWindowLive()
        }

    /** Calls [Player.isCurrentMediaItemLive] on the delegate and returns the result.  */
    override val isCurrentMediaItemLive: Boolean
        get() {
            return wrappedPlayer.isCurrentMediaItemLive()
        }

    /** Calls [Player.getCurrentLiveOffset] on the delegate and returns the result.  */
    override val currentLiveOffset: Long
        get() {
            return wrappedPlayer.getCurrentLiveOffset()
        }

    /**
     * Calls [Player.isCurrentWindowSeekable] on the delegate and returns the result.
     *
     */
    @get:Deprecated("Use {@link #isCurrentMediaItemSeekable()} instead.")
    override val isCurrentWindowSeekable: Boolean
        get() {
            return wrappedPlayer.isCurrentWindowSeekable()
        }

    /** Calls [Player.isCurrentMediaItemSeekable] on the delegate and returns the result.  */
    override val isCurrentMediaItemSeekable: Boolean
        get() {
            return wrappedPlayer.isCurrentMediaItemSeekable()
        }

    /** Calls [Player.isPlayingAd] on the delegate and returns the result.  */
    override val isPlayingAd: Boolean
        get() {
            return wrappedPlayer.isPlayingAd()
        }

    /** Calls [Player.getCurrentAdGroupIndex] on the delegate and returns the result.  */
    override val currentAdGroupIndex: Int
        get() {
            return wrappedPlayer.getCurrentAdGroupIndex()
        }

    /** Calls [Player.getCurrentAdIndexInAdGroup] on the delegate and returns the result.  */
    override val currentAdIndexInAdGroup: Int
        get() {
            return wrappedPlayer.getCurrentAdIndexInAdGroup()
        }

    /** Calls [Player.getContentDuration] on the delegate and returns the result.  */
    override val contentDuration: Long
        get() {
            return wrappedPlayer.getContentDuration()
        }

    /** Calls [Player.getContentPosition] on the delegate and returns the result.  */
    override val contentPosition: Long
        get() {
            return wrappedPlayer.getContentPosition()
        }

    /** Calls [Player.getContentBufferedPosition] on the delegate and returns the result.  */
    override val contentBufferedPosition: Long
        get() {
            return wrappedPlayer.getContentBufferedPosition()
        }

    /** Calls [Player.getAudioAttributes] on the delegate and returns the result.  */
    override val audioAttributes: AudioAttributes?
        get() {
            return wrappedPlayer.getAudioAttributes()
        }
    /** Calls [Player.getVolume] on the delegate and returns the result.  */
    /** Calls [Player.setVolume] on the delegate.  */
    override var volume: Float
        get() {
            return wrappedPlayer.getVolume()
        }
        set(volume) {
            wrappedPlayer.setVolume(volume)
        }

    /** Calls [Player.getVideoSize] on the delegate and returns the result.  */
    override val videoSize: VideoSize?
        get() {
            return wrappedPlayer.getVideoSize()
        }

    /** Calls [Player.getSurfaceSize] on the delegate and returns the result.  */
    override val surfaceSize: Size?
        get() {
            return wrappedPlayer.getSurfaceSize()
        }

    /** Calls [Player.clearVideoSurface] on the delegate.  */
    public override fun clearVideoSurface() {
        wrappedPlayer.clearVideoSurface()
    }

    /** Calls [Player.clearVideoSurface] on the delegate.  */
    public override fun clearVideoSurface(surface: Surface?) {
        wrappedPlayer.clearVideoSurface(surface)
    }

    /** Calls [Player.setVideoSurface] on the delegate.  */
    public override fun setVideoSurface(surface: Surface?) {
        wrappedPlayer.setVideoSurface(surface)
    }

    /** Calls [Player.setVideoSurfaceHolder] on the delegate.  */
    public override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        wrappedPlayer.setVideoSurfaceHolder(surfaceHolder)
    }

    /** Calls [Player.clearVideoSurfaceHolder] on the delegate.  */
    public override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        wrappedPlayer.clearVideoSurfaceHolder(surfaceHolder)
    }

    /** Calls [Player.setVideoSurfaceView] on the delegate.  */
    public override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        wrappedPlayer.setVideoSurfaceView(surfaceView)
    }

    /** Calls [Player.clearVideoSurfaceView] on the delegate.  */
    public override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        wrappedPlayer.clearVideoSurfaceView(surfaceView)
    }

    /** Calls [Player.setVideoTextureView] on the delegate.  */
    public override fun setVideoTextureView(textureView: TextureView?) {
        wrappedPlayer.setVideoTextureView(textureView)
    }

    /** Calls [Player.clearVideoTextureView] on the delegate.  */
    public override fun clearVideoTextureView(textureView: TextureView?) {
        wrappedPlayer.clearVideoTextureView(textureView)
    }

    /** Calls [Player.getCurrentCues] on the delegate and returns the result.  */
    override val currentCues: CueGroup?
        get() {
            return wrappedPlayer.getCurrentCues()
        }

    /** Calls [Player.getDeviceInfo] on the delegate and returns the result.  */
    override val deviceInfo: DeviceInfo?
        get() {
            return wrappedPlayer.getDeviceInfo()
        }
    /** Calls [Player.getDeviceVolume] on the delegate and returns the result.  */
    /** Calls [Player.setDeviceVolume] on the delegate.  */
    override var deviceVolume: Int
        get() {
            return wrappedPlayer.getDeviceVolume()
        }
        set(volume) {
            wrappedPlayer.setDeviceVolume(volume)
        }
    /** Calls [Player.isDeviceMuted] on the delegate and returns the result.  */
    /** Calls [Player.setDeviceMuted] on the delegate.  */
    override var isDeviceMuted: Boolean
        get() {
            return wrappedPlayer.isDeviceMuted()
        }
        set(muted) {
            wrappedPlayer.setDeviceMuted(muted)
        }

    /** Calls [Player.increaseDeviceVolume] on the delegate.  */
    public override fun increaseDeviceVolume() {
        wrappedPlayer.increaseDeviceVolume()
    }

    /** Calls [Player.decreaseDeviceVolume] on the delegate.  */
    public override fun decreaseDeviceVolume() {
        wrappedPlayer.decreaseDeviceVolume()
    }

    private class ForwardingListener constructor(private val forwardingPlayer: ForwardingPlayer, private val listener: Player.Listener) : Player.Listener {
        public override fun onEvents(player: Player?, events: Player.Events?) {
            // Replace player with forwarding player.
            listener.onEvents(forwardingPlayer, events)
        }

        public override fun onTimelineChanged(timeline: Timeline?, reason: @TimelineChangeReason Int) {
            listener.onTimelineChanged(timeline, reason)
        }

        public override fun onMediaItemTransition(
                mediaItem: MediaItem?, reason: @MediaItemTransitionReason Int) {
            listener.onMediaItemTransition(mediaItem, reason)
        }

        public override fun onTracksChanged(tracks: Tracks?) {
            listener.onTracksChanged(tracks)
        }

        public override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata?) {
            listener.onMediaMetadataChanged(mediaMetadata)
        }

        public override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata?) {
            listener.onPlaylistMetadataChanged(mediaMetadata)
        }

        public override fun onIsLoadingChanged(isLoading: Boolean) {
            listener.onIsLoadingChanged(isLoading)
        }

        public override fun onLoadingChanged(isLoading: Boolean) {
            listener.onIsLoadingChanged(isLoading)
        }

        public override fun onAvailableCommandsChanged(availableCommands: Player.Commands?) {
            listener.onAvailableCommandsChanged(availableCommands)
        }

        public override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters?) {
            listener.onTrackSelectionParametersChanged(parameters)
        }

        public override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: @Player.State Int) {
            listener.onPlayerStateChanged(playWhenReady, playbackState)
        }

        public override fun onPlaybackStateChanged(playbackState: @Player.State Int) {
            listener.onPlaybackStateChanged(playbackState)
        }

        public override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean, reason: @PlayWhenReadyChangeReason Int) {
            listener.onPlayWhenReadyChanged(playWhenReady, reason)
        }

        public override fun onPlaybackSuppressionReasonChanged(
                playbackSuppressionReason: @PlayWhenReadyChangeReason Int) {
            listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        }

        public override fun onIsPlayingChanged(isPlaying: Boolean) {
            listener.onIsPlayingChanged(isPlaying)
        }

        public override fun onRepeatModeChanged(repeatMode: @Player.RepeatMode Int) {
            listener.onRepeatModeChanged(repeatMode)
        }

        public override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            listener.onShuffleModeEnabledChanged(shuffleModeEnabled)
        }

        public override fun onPlayerError(error: PlaybackException?) {
            listener.onPlayerError(error)
        }

        public override fun onPlayerErrorChanged(error: PlaybackException?) {
            listener.onPlayerErrorChanged(error)
        }

        public override fun onPositionDiscontinuity(reason: @DiscontinuityReason Int) {
            listener.onPositionDiscontinuity(reason)
        }

        public override fun onPositionDiscontinuity(
                oldPosition: PositionInfo?, newPosition: PositionInfo?, reason: @DiscontinuityReason Int) {
            listener.onPositionDiscontinuity(oldPosition, newPosition, reason)
        }

        public override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
            listener.onPlaybackParametersChanged(playbackParameters)
        }

        public override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
            listener.onSeekBackIncrementChanged(seekBackIncrementMs)
        }

        public override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
            listener.onSeekForwardIncrementChanged(seekForwardIncrementMs)
        }

        public override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) {
            listener.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
        }

        public override fun onSeekProcessed() {
            listener.onSeekProcessed()
        }

        public override fun onVideoSizeChanged(videoSize: VideoSize?) {
            listener.onVideoSizeChanged(videoSize)
        }

        public override fun onSurfaceSizeChanged(width: Int, height: Int) {
            listener.onSurfaceSizeChanged(width, height)
        }

        public override fun onRenderedFirstFrame() {
            listener.onRenderedFirstFrame()
        }

        public override fun onAudioSessionIdChanged(audioSessionId: Int) {
            listener.onAudioSessionIdChanged(audioSessionId)
        }

        public override fun onAudioAttributesChanged(audioAttributes: AudioAttributes?) {
            listener.onAudioAttributesChanged(audioAttributes)
        }

        public override fun onVolumeChanged(volume: Float) {
            listener.onVolumeChanged(volume)
        }

        public override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
            listener.onSkipSilenceEnabledChanged(skipSilenceEnabled)
        }

        public override fun onCues(cues: List<Cue?>?) {
            listener.onCues(cues)
        }

        public override fun onCues(cueGroup: CueGroup?) {
            listener.onCues(cueGroup)
        }

        public override fun onMetadata(metadata: Metadata?) {
            listener.onMetadata(metadata)
        }

        public override fun onDeviceInfoChanged(deviceInfo: DeviceInfo?) {
            listener.onDeviceInfoChanged(deviceInfo)
        }

        public override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            listener.onDeviceVolumeChanged(volume, muted)
        }

        public override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (!(o is ForwardingPlayer.ForwardingListener)) {
                return false
            }
            val that: ForwardingPlayer.ForwardingListener = o
            if (!(forwardingPlayer == that.forwardingPlayer)) {
                return false
            }
            return (listener == that.listener)
        }

        public override fun hashCode(): Int {
            var result: Int = forwardingPlayer.hashCode()
            result = 31 * result + listener.hashCode()
            return result
        }
    }
}