/*
 * Copyright 2022 The Android Open Source Project
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
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.util.*
import com.google.android.exoplayer2.util.ListenerSet.IterationFinishedEvent
import com.google.android.exoplayer2.video.VideoSize
import com.google.android.exoplayer2import.SimpleBasePlayer.State
import com.google.common.base.Supplier
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.errorprone.annotations.ForOverride
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import java.util.concurrent.Executor

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
 * A base implementation for [Player] that reduces the number of methods to implement to a
 * minimum.
 *
 *
 * Implementation notes:
 *
 *
 *  * Subclasses must override [.getState] to populate the current player state on
 * request.
 *  * The [State] should set the [available][State.Builder.setAvailableCommands] to indicate which [Player] methods are supported.
 *  * All setter-like player methods (for example, [.setPlayWhenReady]) forward to
 * overridable methods (for example, [.handleSetPlayWhenReady]) that can be used to
 * handle these requests. These methods return a [ListenableFuture] to indicate when the
 * request has been handled and is fully reflected in the values returned from [       ][.getState]. This class will automatically request a state update once the request is done.
 * If the state changes can be handled synchronously, these methods can return Guava's [       ][Futures.immediateVoidFuture].
 *  * Subclasses can manually trigger state updates with [.invalidateState], for example if
 * something changes independent of [Player] method calls.
 *
 *
 * This base class handles various aspects of the player implementation to simplify the subclass:
 *
 *
 *  * The [State] can only be created with allowed combinations of state values, avoiding
 * any invalid player states.
 *  * Only functionality that is declared as [available][Player.Command] needs to be
 * implemented. Other methods are automatically ignored.
 *  * Listener handling and informing listeners of state changes is handled automatically.
 *  * The base class provides a framework for asynchronous handling of method calls. It changes
 * the visible playback state immediately to the most likely outcome to ensure the
 * user-visible state changes look like synchronous operations. The state is then updated
 * again once the asynchronous method calls have been fully handled.
 *
 */
abstract class SimpleBasePlayer protected constructor(private val applicationLooper: Looper, clock: Clock = Clock.Companion.DEFAULT) : BasePlayer() {
    /** An immutable state description of the player.  */
    class State private constructor(builder: SimpleBasePlayer.State.Builder) {
        /** A builder for [State] objects.  */
        class Builder {
            private var availableCommands: Player.Commands
            private var playWhenReady: Boolean
            private var playWhenReadyChangeReason: @PlayWhenReadyChangeReason Int

            /** Creates the builder.  */
            constructor() {
                availableCommands = Player.Commands.Companion.EMPTY
                playWhenReady = false
                playWhenReadyChangeReason = Player.Companion.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            }

            private constructor(state: SimpleBasePlayer.State) {
                availableCommands = state.availableCommands
                playWhenReady = state.playWhenReady
                playWhenReadyChangeReason = state.playWhenReadyChangeReason
            }

            /**
             * Sets the available [Commands].
             *
             * @param availableCommands The available [Commands].
             * @return This builder.
             */
            @CanIgnoreReturnValue
            fun setAvailableCommands(availableCommands: Player.Commands): SimpleBasePlayer.State.Builder {
                this.availableCommands = availableCommands
                return this
            }

            /**
             * Sets whether playback should proceed when ready and not suppressed.
             *
             * @param playWhenReady Whether playback should proceed when ready and not suppressed.
             * @param playWhenReadyChangeReason The [reason][PlayWhenReadyChangeReason] for
             * changing the value.
             * @return This builder.
             */
            @CanIgnoreReturnValue
            fun setPlayWhenReady(
                    playWhenReady: Boolean, playWhenReadyChangeReason: @PlayWhenReadyChangeReason Int): SimpleBasePlayer.State.Builder {
                this.playWhenReady = playWhenReady
                this.playWhenReadyChangeReason = playWhenReadyChangeReason
                return this
            }

            /** Builds the [State].  */
            fun build(): SimpleBasePlayer.State {
                return SimpleBasePlayer.State(this)
            }
        }

        /** The available [Commands].  */
        val availableCommands: Player.Commands

        /** Whether playback should proceed when ready and not suppressed.  */
        val playWhenReady: Boolean

        /** The last reason for changing [.playWhenReady].  */
        val playWhenReadyChangeReason: @PlayWhenReadyChangeReason Int

        init {
            availableCommands = builder.availableCommands
            playWhenReady = builder.playWhenReady
            playWhenReadyChangeReason = builder.playWhenReadyChangeReason
        }

        /** Returns a [Builder] pre-populated with the current state values.  */
        fun buildUpon(): SimpleBasePlayer.State.Builder {
            return SimpleBasePlayer.State.Builder(this)
        }

        public override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (!(o is SimpleBasePlayer.State)) {
                return false
            }
            val state: SimpleBasePlayer.State = o
            return (playWhenReady == state.playWhenReady
                    ) && (playWhenReadyChangeReason == state.playWhenReadyChangeReason
                    ) && (availableCommands == state.availableCommands)
        }

        public override fun hashCode(): Int {
            var result: Int = 7
            result = 31 * result + availableCommands.hashCode()
            result = 31 * result + (if (playWhenReady) 1 else 0)
            result = 31 * result + playWhenReadyChangeReason
            return result
        }
    }

    private val listeners: ListenerSet<Player.Listener>
    private val applicationHandler: HandlerWrapper?
    private val pendingOperations: HashSet<ListenableFuture<*>>
    private var state: @MonotonicNonNull SimpleBasePlayer.State? = null
    /**
     * Creates the base class.
     *
     * @param applicationLooper The [Looper] that must be used for all calls to the player and
     * that is used to call listeners on.
     * @param clock The [Clock] that will be used by the player.
     */
    /**
     * Creates the base class.
     *
     * @param applicationLooper The [Looper] that must be used for all calls to the player and
     * that is used to call listeners on.
     */
    init {
        applicationHandler = clock.createHandler(applicationLooper,  /* callback= */null)
        pendingOperations = HashSet()
        val listenerSet:  // Using this in constructor.
                ListenerSet<Player.Listener> = ListenerSet(
                applicationLooper,
                clock,
                IterationFinishedEvent({ listener: Player.Listener, flags: FlagSet -> listener.onEvents( /* player= */this, Player.Events(flags)) }))
        listeners = listenerSet
    }

    public override fun addListener(listener: Player.Listener?) {
        // Don't verify application thread. We allow calls to this method from any thread.
        listeners.add((Assertions.checkNotNull(listener))!!)
    }

    public override fun removeListener(listener: Player.Listener?) {
        // Don't verify application thread. We allow calls to this method from any thread.
        Assertions.checkNotNull(listener)
        listeners.remove((listener)!!)
    }

    public override fun getApplicationLooper(): Looper {
        // Don't verify application thread. We allow calls to this method from any thread.
        return applicationLooper
    }

    public override fun getAvailableCommands(): Player.Commands {
        verifyApplicationThreadAndInitState()
        return state!!.availableCommands
    }

    public override fun setPlayWhenReady(playWhenReady: Boolean) {
        verifyApplicationThreadAndInitState()
        val state: SimpleBasePlayer.State? = state
        if (!state!!.availableCommands.contains(Player.Companion.COMMAND_PLAY_PAUSE)) {
            return
        }
        updateStateForPendingOperation( /* pendingOperation= */
                handleSetPlayWhenReady(playWhenReady),  /* placeholderStateSupplier= */
                Supplier({
                    state
                            .buildUpon()
                            .setPlayWhenReady(playWhenReady, Player.Companion.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                            .build()
                }))
    }

    public override fun getPlayWhenReady(): Boolean {
        verifyApplicationThreadAndInitState()
        return state!!.playWhenReady
    }

    public override fun setMediaItems(mediaItems: List<MediaItem?>?, resetPosition: Boolean) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setMediaItems(
            mediaItems: List<MediaItem?>?, startIndex: Int, startPositionMs: Long) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun addMediaItems(index: Int, mediaItems: List<MediaItem?>?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun prepare() {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getPlaybackState(): Int {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getPlaybackSuppressionReason(): Int {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getPlayerError(): PlaybackException? {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setRepeatMode(repeatMode: Int) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getRepeatMode(): Int {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getShuffleModeEnabled(): Boolean {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun isLoading(): Boolean {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getSeekBackIncrement(): Long {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getSeekForwardIncrement(): Long {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getMaxSeekToPreviousPosition(): Long {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setPlaybackParameters(playbackParameters: PlaybackParameters?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getPlaybackParameters(): PlaybackParameters {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun stop() {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun stop(reset: Boolean) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun release() {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getCurrentTracks(): Tracks {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getTrackSelectionParameters(): TrackSelectionParameters {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setTrackSelectionParameters(parameters: TrackSelectionParameters?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getMediaMetadata(): MediaMetadata {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getPlaylistMetadata(): MediaMetadata {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setPlaylistMetadata(mediaMetadata: MediaMetadata?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getCurrentTimeline(): Timeline {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getCurrentPeriodIndex(): Int {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getCurrentMediaItemIndex(): Int {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getDuration(): Long {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getCurrentPosition(): Long {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getBufferedPosition(): Long {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getTotalBufferedDuration(): Long {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun isPlayingAd(): Boolean {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getCurrentAdGroupIndex(): Int {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getCurrentAdIndexInAdGroup(): Int {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getContentPosition(): Long {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getContentBufferedPosition(): Long {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getAudioAttributes(): AudioAttributes {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setVolume(volume: Float) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getVolume(): Float {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun clearVideoSurface() {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun clearVideoSurface(surface: Surface?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setVideoSurface(surface: Surface?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setVideoTextureView(textureView: TextureView?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun clearVideoTextureView(textureView: TextureView?) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getVideoSize(): VideoSize {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getSurfaceSize(): Size {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getCurrentCues(): CueGroup {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getDeviceInfo(): DeviceInfo {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun getDeviceVolume(): Int {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun isDeviceMuted(): Boolean {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setDeviceVolume(volume: Int) {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun increaseDeviceVolume() {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun decreaseDeviceVolume() {
        // TODO: implement.
        throw IllegalStateException()
    }

    public override fun setDeviceMuted(muted: Boolean) {
        // TODO: implement.
        throw IllegalStateException()
    }

    /**
     * Invalidates the current state.
     *
     *
     * Triggers a call to [.getState] and informs listeners if the state changed.
     *
     *
     * Note that this may not have an immediate effect while there are still player methods being
     * handled asynchronously. The state will be invalidated automatically once these pending
     * synchronous operations are finished and there is no need to call this method again.
     */
    fun invalidateState() {
        verifyApplicationThreadAndInitState()
        if (!pendingOperations.isEmpty()) {
            return
        }
        updateStateAndInformListeners(getState())
    }

    /**
     * Returns the current [State] of the player.
     *
     *
     * The [State] should include all [ ][State.Builder.setAvailableCommands] indicating which player
     * methods are allowed to be called.
     *
     *
     * Note that this method won't be called while asynchronous handling of player methods is in
     * progress. This means that the implementation doesn't need to handle state changes caused by
     * these asynchronous operations until they are done and can return the currently known state
     * directly. The placeholder state used while these asynchronous operations are in progress can be
     * customized by overriding [.getPlaceholderState] if required.
     */
    @ForOverride
    protected abstract fun getState(): SimpleBasePlayer.State

    /**
     * Returns the placeholder state used while a player method is handled asynchronously.
     *
     *
     * The `suggestedPlaceholderState` already contains the most likely state update, for
     * example setting [State.playWhenReady] to true if `player.setPlayWhenReady(true)` is
     * called, and an implementations only needs to override this method if it can determine a more
     * accurate placeholder state.
     *
     * @param suggestedPlaceholderState The suggested placeholder [State], including the most
     * likely outcome of handling all pending asynchronous operations.
     * @return The placeholder [State] to use while asynchronous operations are pending.
     */
    @ForOverride
    protected fun getPlaceholderState(suggestedPlaceholderState: SimpleBasePlayer.State): SimpleBasePlayer.State {
        return suggestedPlaceholderState
    }

    /**
     * Handles calls to set [State.playWhenReady].
     *
     *
     * Will only be called if [Player.Command.COMMAND_PLAY_PAUSE] is available.
     *
     * @param playWhenReady The requested [State.playWhenReady]
     * @return A [ListenableFuture] indicating the completion of all immediate [State]
     * changes caused by this call.
     * @see Player.setPlayWhenReady
     * @see Player.play
     * @see Player.pause
     */
    @ForOverride
    protected open fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        throw IllegalStateException()
    }

    // Calling deprecated listener methods.
    @RequiresNonNull("state")
    private fun updateStateAndInformListeners(newState: SimpleBasePlayer.State) {
        val previousState: SimpleBasePlayer.State? = state
        // Assign new state immediately such that all getters return the right values, but use a
        // snapshot of the previous and new state so that listener invocations are triggered correctly.
        state = newState
        val playWhenReadyChanged: Boolean = previousState!!.playWhenReady != newState.playWhenReady
        if (playWhenReadyChanged /* TODO: || playbackStateChanged */) {
            listeners.queueEvent( /* eventFlag= */
                    C.INDEX_UNSET,
                    ListenerSet.Event({ listener: Player.Listener -> listener.onPlayerStateChanged(newState.playWhenReady,  /* TODO */Player.Companion.STATE_IDLE) }))
        }
        if ((playWhenReadyChanged
                        || previousState.playWhenReadyChangeReason != newState.playWhenReadyChangeReason)) {
            listeners.queueEvent(
                    Player.Companion.EVENT_PLAY_WHEN_READY_CHANGED,
                    ListenerSet.Event({ listener: Player.Listener ->
                        listener.onPlayWhenReadyChanged(
                                newState.playWhenReady, newState.playWhenReadyChangeReason)
                    }))
        }
        if (SimpleBasePlayer.Companion.isPlaying(previousState) != SimpleBasePlayer.Companion.isPlaying(newState)) {
            listeners.queueEvent(
                    Player.Companion.EVENT_IS_PLAYING_CHANGED,
                    ListenerSet.Event({ listener: Player.Listener -> listener.onIsPlayingChanged(SimpleBasePlayer.Companion.isPlaying(newState)) }))
        }
        if (!(previousState.availableCommands == newState.availableCommands)) {
            listeners.queueEvent(
                    Player.Companion.EVENT_AVAILABLE_COMMANDS_CHANGED,
                    ListenerSet.Event({ listener: Player.Listener -> listener.onAvailableCommandsChanged(newState.availableCommands) }))
        }
        listeners.flushEvents()
    }

    @EnsuresNonNull("state")
    private fun verifyApplicationThreadAndInitState() {
        if (Thread.currentThread() !== applicationLooper.getThread()) {
            val message: String? = Util.formatInvariant(
                    ("Player is accessed on the wrong thread.\n"
                            + "Current thread: '%s'\n"
                            + "Expected thread: '%s'\n"
                            + "See https://exoplayer.dev/issues/player-accessed-on-wrong-thread"),
                    Thread.currentThread().getName(), applicationLooper.getThread().getName())
            throw IllegalStateException(message)
        }
        if (state == null) {
            // First time accessing state.
            state = getState()
        }
    }

    @RequiresNonNull("state")
    private fun updateStateForPendingOperation(
            pendingOperation: ListenableFuture<*>, placeholderStateSupplier: Supplier<SimpleBasePlayer.State>) {
        if (pendingOperation.isDone() && pendingOperations.isEmpty()) {
            updateStateAndInformListeners(getState())
        } else {
            pendingOperations.add(pendingOperation)
            val suggestedPlaceholderState: SimpleBasePlayer.State = placeholderStateSupplier.get()
            updateStateAndInformListeners(getPlaceholderState(suggestedPlaceholderState))
            pendingOperation.addListener(
                    Runnable({
                        Util.castNonNull(state) // Already check by method @RequiresNonNull pre-condition.
                        pendingOperations.remove(pendingOperation)
                        if (pendingOperations.isEmpty()) {
                            updateStateAndInformListeners(getState())
                        }
                    }), Executor({ runnable: Runnable -> postOrRunOnApplicationHandler(runnable) }))
        }
    }

    private fun postOrRunOnApplicationHandler(runnable: Runnable) {
        if (applicationHandler.getLooper() == Looper.myLooper()) {
            runnable.run()
        } else {
            applicationHandler!!.post(runnable)
        }
    }

    companion object {
        private fun isPlaying(state: SimpleBasePlayer.State): Boolean {
            return state.playWhenReady && false
            // TODO: && state.playbackState == Player.STATE_READY
            //       && state.playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE
        }
    }
}