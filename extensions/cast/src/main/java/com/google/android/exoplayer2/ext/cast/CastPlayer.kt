/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast

import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.IntRange
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.util.*
import com.google.android.exoplayer2.video.VideoSize
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.ResultCallback
import com.google.common.collect.ImmutableList
import org.checkerframework.checker.nullness.qual.RequiresNonNull

/**
 * [Player] implementation that communicates with a Cast receiver app.
 *
 *
 * The behavior of this class depends on the underlying Cast session, which is obtained from the
 * injected [CastContext]. To keep track of the session, [.isCastSessionAvailable] can
 * be queried and [SessionAvailabilityListener] can be implemented and attached to the player.
 *
 *
 * If no session is available, the player state will remain unchanged and calls to methods that
 * alter it will be ignored. Querying the player state is possible even when no session is
 * available, in which case, the last observed receiver app state is reported.
 *
 *
 * Methods should be called on the application's main thread.
 */
class CastPlayer @JvmOverloads constructor(
        castContext: CastContext,
        mediaItemConverter: MediaItemConverter = DefaultMediaItemConverter(),
        @IntRange(from = 1) seekBackIncrementMs: Long =
                C.DEFAULT_SEEK_BACK_INCREMENT_MS,
        @IntRange(from = 1) seekForwardIncrementMs: Long =
                C.DEFAULT_SEEK_FORWARD_INCREMENT_MS) : BasePlayer() {
    private val castContext: CastContext
    private val mediaItemConverter: MediaItemConverter
    private val seekBackIncrementMs: Long
    private val seekForwardIncrementMs: Long

    // TODO: Allow custom implementations of CastTimelineTracker.
    private val timelineTracker: CastTimelineTracker
    private val period: Timeline.Period

    // Result callbacks.
    private val statusListener: StatusListener
    private val seekResultCallback: SeekResultCallback

    // Listeners and notification.
    private val listeners: ListenerSet<Player.Listener>
    private var sessionAvailabilityListener: SessionAvailabilityListener? = null

    // Internal state.
    private val playWhenReady: StateHolder<Boolean>
    private val repeatMode: StateHolder<Int>
    private val playbackParameters: StateHolder<PlaybackParameters>
    private var remoteMediaClient: RemoteMediaClient? = null
    private var currentTimeline: CastTimeline
    private var currentTracks: Tracks
    private var availableCommands: Player.Commands
    private var playbackState: @Player.State Int
    private var currentWindowIndex = 0
    private var lastReportedPositionMs: Long = 0
    private var pendingSeekCount = 0
    private var pendingSeekWindowIndex: Int
    private var pendingSeekPositionMs: Long
    private var pendingMediaItemRemovalPosition: PositionInfo? = null
    private var mediaMetadata: MediaMetadata
    /**
     * Creates a new cast player.
     *
     * @param castContext The context from which the cast session is obtained.
     * @param mediaItemConverter The [MediaItemConverter] to use.
     * @param seekBackIncrementMs The [.seekBack] increment, in milliseconds.
     * @param seekForwardIncrementMs The [.seekForward] increment, in milliseconds.
     * @throws IllegalArgumentException If `seekBackIncrementMs` or `seekForwardIncrementMs` is non-positive.
     */
    /**
     * Creates a new cast player.
     *
     *
     * `seekBackIncrementMs` is set to [C.DEFAULT_SEEK_BACK_INCREMENT_MS] and `seekForwardIncrementMs` is set to [C.DEFAULT_SEEK_FORWARD_INCREMENT_MS].
     *
     * @param castContext The context from which the cast session is obtained.
     * @param mediaItemConverter The [MediaItemConverter] to use.
     */
    /**
     * Creates a new cast player.
     *
     *
     * The returned player uses a [DefaultMediaItemConverter] and
     *
     *
     * `mediaItemConverter` is set to a [DefaultMediaItemConverter], `seekBackIncrementMs` is set to [C.DEFAULT_SEEK_BACK_INCREMENT_MS] and `seekForwardIncrementMs` is set to [C.DEFAULT_SEEK_FORWARD_INCREMENT_MS].
     *
     * @param castContext The context from which the cast session is obtained.
     */
    init {
        Assertions.checkArgument(seekBackIncrementMs > 0 && seekForwardIncrementMs > 0)
        this.castContext = castContext
        this.mediaItemConverter = mediaItemConverter
        this.seekBackIncrementMs = seekBackIncrementMs
        this.seekForwardIncrementMs = seekForwardIncrementMs
        timelineTracker = CastTimelineTracker(mediaItemConverter)
        period = Timeline.Period()
        statusListener = StatusListener()
        seekResultCallback = SeekResultCallback()
        listeners = ListenerSet(
                Looper.getMainLooper(),
                Clock.DEFAULT
        ) { listener: Player.Listener, flags: FlagSet? -> listener.onEvents( /* player= */this, Player.Events(flags!!)) }
        playWhenReady = StateHolder(false)
        repeatMode = StateHolder(REPEAT_MODE_OFF)
        playbackParameters = StateHolder(PlaybackParameters.DEFAULT)
        playbackState = STATE_IDLE
        currentTimeline = CastTimeline.Companion.EMPTY_CAST_TIMELINE
        mediaMetadata = MediaMetadata.EMPTY
        currentTracks = Tracks.EMPTY
        availableCommands = Player.Commands.Builder().addAll(PERMANENT_AVAILABLE_COMMANDS).build()
        pendingSeekWindowIndex = C.INDEX_UNSET
        pendingSeekPositionMs = C.TIME_UNSET
        val sessionManager = castContext.sessionManager
        sessionManager.addSessionManagerListener(statusListener, CastSession::class.java)
        val session = sessionManager.currentCastSession
        setRemoteMediaClient(session?.remoteMediaClient)
        updateInternalStateAndNotifyIfChanged()
    }

    /**
     * Returns the item that corresponds to the period with the given id, or null if no media queue or
     * period with id `periodId` exist.
     *
     * @param periodId The id of the period ([.getCurrentTimeline]) that corresponds to the item
     * to get.
     * @return The item that corresponds to the period with the given id, or null if no media queue or
     * period with id `periodId` exist.
     */
    fun getItem(periodId: Int): MediaQueueItem? {
        val mediaStatus = mediaStatus
        return if (mediaStatus != null && currentTimeline.getIndexOfPeriod(periodId) != C.INDEX_UNSET) mediaStatus.getItemById(periodId) else null
    }
    // CastSession methods.
    /** Returns whether a cast session is available.  */
    val isCastSessionAvailable: Boolean
        get() = remoteMediaClient != null

    /**
     * Sets a listener for updates on the cast session availability.
     *
     * @param listener The [SessionAvailabilityListener], or null to clear the listener.
     */
    fun setSessionAvailabilityListener(listener: SessionAvailabilityListener?) {
        sessionAvailabilityListener = listener
    }

    // Player implementation.
    override fun getApplicationLooper(): Looper {
        return Looper.getMainLooper()
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        val mediaItemIndex = if (resetPosition) 0 else currentMediaItemIndex
        val startPositionMs = if (resetPosition) C.TIME_UNSET else contentPosition
        setMediaItems(mediaItems, mediaItemIndex, startPositionMs)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        setMediaItemsInternal(mediaItems, startIndex, startPositionMs, repeatMode.value)
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        Assertions.checkArgument(index >= 0)
        var uid = MediaQueueItem.INVALID_ITEM_ID
        if (index < currentTimeline.windowCount) {
            uid = currentTimeline.getWindow( /* windowIndex= */index, window).uid as Int
        }
        addMediaItemsInternal(mediaItems, uid)
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        var newIndex = newIndex
        Assertions.checkArgument(
                fromIndex >= 0 && fromIndex <= toIndex && toIndex <= currentTimeline.windowCount && newIndex >= 0 && newIndex < currentTimeline.windowCount)
        newIndex = Math.min(newIndex, currentTimeline.windowCount - (toIndex - fromIndex))
        if (fromIndex == toIndex || fromIndex == newIndex) {
            // Do nothing.
            return
        }
        val uids = IntArray(toIndex - fromIndex)
        for (i in uids.indices) {
            uids[i] = currentTimeline.getWindow( /* windowIndex= */i + fromIndex, window).uid as Int
        }
        moveMediaItemsInternal(uids, fromIndex, newIndex)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        var toIndex = toIndex
        Assertions.checkArgument(fromIndex >= 0 && toIndex >= fromIndex)
        toIndex = Math.min(toIndex, currentTimeline.windowCount)
        if (fromIndex == toIndex) {
            // Do nothing.
            return
        }
        val uids = IntArray(toIndex - fromIndex)
        for (i in uids.indices) {
            uids[i] = currentTimeline.getWindow( /* windowIndex= */i + fromIndex, window).uid as Int
        }
        removeMediaItemsInternal(uids)
    }

    override fun getAvailableCommands(): Player.Commands {
        return availableCommands
    }

    override fun prepare() {
        // Do nothing.
    }

    override fun getPlaybackState(): @Player.State Int {
        return playbackState
    }

    override fun getPlaybackSuppressionReason(): @PlaybackSuppressionReason Int {
        return PLAYBACK_SUPPRESSION_REASON_NONE
    }

    override fun getPlayerError(): PlaybackException? {
        return null
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (remoteMediaClient == null) {
            return
        }
        // We update the local state and send the message to the receiver app, which will cause the
        // operation to be perceived as synchronous by the user. When the operation reports a result,
        // the local state will be updated to reflect the state reported by the Cast SDK.
        setPlayerStateAndNotifyIfChanged(
                playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST, playbackState)
        listeners.flushEvents()
        val pendingResult = if (playWhenReady) remoteMediaClient!!.play() else remoteMediaClient!!.pause()
        this.playWhenReady.pendingResultCallback = object : ResultCallback<RemoteMediaClient.MediaChannelResult?> {
            override fun onResult(mediaChannelResult: RemoteMediaClient.MediaChannelResult?) {
                if (remoteMediaClient != null) {
                    updatePlayerStateAndNotifyIfChanged(this)
                    listeners.flushEvents()
                }
            }
        }
        pendingResult.setResultCallback(this.playWhenReady.pendingResultCallback)
    }

    override fun getPlayWhenReady(): Boolean {
        return playWhenReady.value
    }

    // We still call Listener#onSeekProcessed() for backwards compatibility with listeners that
    // don't implement onPositionDiscontinuity().
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        var positionMs = positionMs
        val mediaStatus = mediaStatus
        // We assume the default position is 0. There is no support for seeking to the default position
        // in RemoteMediaClient.
        positionMs = if (positionMs != C.TIME_UNSET) positionMs else 0
        if (mediaStatus != null) {
            if (currentMediaItemIndex != mediaItemIndex) {
                remoteMediaClient
                        .queueJumpToItem(
                                currentTimeline.getPeriod(mediaItemIndex, period).uid as Int, positionMs, null)
                        .setResultCallback(seekResultCallback)
            } else {
                remoteMediaClient!!.seek(positionMs).setResultCallback(seekResultCallback)
            }
            val oldPosition = currentPositionInfo
            pendingSeekCount++
            pendingSeekWindowIndex = mediaItemIndex
            pendingSeekPositionMs = positionMs
            val newPosition = currentPositionInfo
            listeners.queueEvent(
                    EVENT_POSITION_DISCONTINUITY
            ) { listener: Player.Listener ->
                listener.onPositionDiscontinuity(DISCONTINUITY_REASON_SEEK)
                listener.onPositionDiscontinuity(oldPosition, newPosition, DISCONTINUITY_REASON_SEEK)
            }
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                // TODO(internal b/182261884): queue `onMediaItemTransition` event when the media item is
                // repeated.
                val mediaItem = getCurrentTimeline().getWindow(mediaItemIndex, window).mediaItem
                listeners.queueEvent(
                        EVENT_MEDIA_ITEM_TRANSITION
                ) { listener: Player.Listener -> listener.onMediaItemTransition(mediaItem, MEDIA_ITEM_TRANSITION_REASON_SEEK) }
                val oldMediaMetadata = mediaMetadata
                mediaMetadata = mediaMetadataInternal
                if (oldMediaMetadata != mediaMetadata) {
                    listeners.queueEvent(
                            EVENT_MEDIA_METADATA_CHANGED
                    ) { listener: Player.Listener -> listener.onMediaMetadataChanged(mediaMetadata) }
                }
            }
            updateAvailableCommandsAndNotifyIfChanged()
        } else if (pendingSeekCount == 0) {
            listeners.queueEvent( /* eventFlag= */C.INDEX_UNSET) { obj: Player.Listener -> obj.onSeekProcessed() }
        }
        listeners.flushEvents()
    }

    override fun getSeekBackIncrement(): Long {
        return seekBackIncrementMs
    }

    override fun getSeekForwardIncrement(): Long {
        return seekForwardIncrementMs
    }

    override fun getMaxSeekToPreviousPosition(): Long {
        return C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return playbackParameters.value
    }

    override fun stop() {
        stop( /* reset= */false)
    }

    @Deprecated("""Use {@link #stop()} and {@link #clearMediaItems()} (if {@code reset} is true) or
        just {@link #stop()} (if {@code reset} is false). Any player error will be cleared when
        {@link #prepare() re-preparing} the player.""")
    override fun stop(reset: Boolean) {
        playbackState = STATE_IDLE
        if (remoteMediaClient != null) {
            // TODO(b/69792021): Support or emulate stop without position reset.
            remoteMediaClient!!.stop()
        }
    }

    override fun release() {
        val sessionManager = castContext.sessionManager
        sessionManager.removeSessionManagerListener(statusListener, CastSession::class.java)
        sessionManager.endCurrentSession(false)
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        if (remoteMediaClient == null) {
            return
        }
        val actualPlaybackParameters = PlaybackParameters(
                Util.constrainValue(
                        playbackParameters.speed, MIN_SPEED_SUPPORTED, MAX_SPEED_SUPPORTED))
        setPlaybackParametersAndNotifyIfChanged(actualPlaybackParameters)
        listeners.flushEvents()
        val pendingResult = remoteMediaClient!!.setPlaybackRate(actualPlaybackParameters.speed.toDouble(),  /* customData= */null)
        this.playbackParameters.pendingResultCallback = object : ResultCallback<RemoteMediaClient.MediaChannelResult?> {
            override fun onResult(mediaChannelResult: RemoteMediaClient.MediaChannelResult?) {
                if (remoteMediaClient != null) {
                    updatePlaybackRateAndNotifyIfChanged(this)
                    listeners.flushEvents()
                }
            }
        }
        pendingResult.setResultCallback(this.playbackParameters.pendingResultCallback)
    }

    override fun setRepeatMode(repeatMode: @Player.RepeatMode Int) {
        if (remoteMediaClient == null) {
            return
        }
        // We update the local state and send the message to the receiver app, which will cause the
        // operation to be perceived as synchronous by the user. When the operation reports a result,
        // the local state will be updated to reflect the state reported by the Cast SDK.
        setRepeatModeAndNotifyIfChanged(repeatMode)
        listeners.flushEvents()
        val pendingResult = remoteMediaClient!!.queueSetRepeatMode(getCastRepeatMode(repeatMode),  /* customData= */null)
        this.repeatMode.pendingResultCallback = object : ResultCallback<RemoteMediaClient.MediaChannelResult?> {
            override fun onResult(mediaChannelResult: RemoteMediaClient.MediaChannelResult?) {
                if (remoteMediaClient != null) {
                    updateRepeatModeAndNotifyIfChanged(this)
                    listeners.flushEvents()
                }
            }
        }
        pendingResult.setResultCallback(this.repeatMode.pendingResultCallback)
    }

    override fun getRepeatMode(): @Player.RepeatMode Int {
        return repeatMode.value
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        // TODO: Support shuffle mode.
    }

    override fun getShuffleModeEnabled(): Boolean {
        // TODO: Support shuffle mode.
        return false
    }

    override fun getCurrentTracks(): Tracks {
        return currentTracks
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}
    override fun getMediaMetadata(): MediaMetadata {
        return mediaMetadata
    }

    val mediaMetadataInternal: MediaMetadata
        get() {
            val currentMediaItem = currentMediaItem
            return currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
        }

    override fun getPlaylistMetadata(): MediaMetadata {
        // CastPlayer does not currently support metadata.
        return MediaMetadata.EMPTY
    }

    /** This method is not supported and does nothing.  */
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        // CastPlayer does not currently support metadata.
    }

    override fun getCurrentTimeline(): Timeline {
        return currentTimeline
    }

    override fun getCurrentPeriodIndex(): Int {
        return currentMediaItemIndex
    }

    override fun getCurrentMediaItemIndex(): Int {
        return if (pendingSeekWindowIndex != C.INDEX_UNSET) pendingSeekWindowIndex else currentWindowIndex
    }

    // TODO: Fill the cast timeline information with ProgressListener's duration updates.
    // See [Internal: b/65152553].
    override fun getDuration(): Long {
        return contentDuration
    }

    override fun getCurrentPosition(): Long {
        return if (pendingSeekPositionMs != C.TIME_UNSET) pendingSeekPositionMs else if (remoteMediaClient != null) remoteMediaClient!!.approximateStreamPosition else lastReportedPositionMs
    }

    override fun getBufferedPosition(): Long {
        return currentPosition
    }

    override fun getTotalBufferedDuration(): Long {
        val bufferedPosition = bufferedPosition
        val currentPosition = currentPosition
        return if (bufferedPosition == C.TIME_UNSET || currentPosition == C.TIME_UNSET) 0 else bufferedPosition - currentPosition
    }

    override fun isPlayingAd(): Boolean {
        return false
    }

    override fun getCurrentAdGroupIndex(): Int {
        return C.INDEX_UNSET
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return C.INDEX_UNSET
    }

    override fun isLoading(): Boolean {
        return false
    }

    override fun getContentPosition(): Long {
        return currentPosition
    }

    override fun getContentBufferedPosition(): Long {
        return bufferedPosition
    }

    /** This method is not supported and returns [AudioAttributes.DEFAULT].  */
    override fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.DEFAULT
    }

    /** This method is not supported and does nothing.  */
    override fun setVolume(volume: Float) {}

    /** This method is not supported and returns 1.  */
    override fun getVolume(): Float {
        return 1
    }

    /** This method is not supported and does nothing.  */
    override fun clearVideoSurface() {}

    /** This method is not supported and does nothing.  */
    override fun clearVideoSurface(surface: Surface?) {}

    /** This method is not supported and does nothing.  */
    override fun setVideoSurface(surface: Surface?) {}

    /** This method is not supported and does nothing.  */
    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}

    /** This method is not supported and does nothing.  */
    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}

    /** This method is not supported and does nothing.  */
    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {}

    /** This method is not supported and does nothing.  */
    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {}

    /** This method is not supported and does nothing.  */
    override fun setVideoTextureView(textureView: TextureView?) {}

    /** This method is not supported and does nothing.  */
    override fun clearVideoTextureView(textureView: TextureView?) {}

    /** This method is not supported and returns [VideoSize.UNKNOWN].  */
    override fun getVideoSize(): VideoSize {
        return VideoSize.UNKNOWN
    }

    /** This method is not supported and returns [Size.UNKNOWN].  */
    override fun getSurfaceSize(): Size {
        return Size.UNKNOWN
    }

    /** This method is not supported and returns an empty [CueGroup].  */
    override fun getCurrentCues(): CueGroup {
        return CueGroup.EMPTY_TIME_ZERO
    }

    /** This method always returns [CastPlayer.DEVICE_INFO].  */
    override fun getDeviceInfo(): DeviceInfo {
        return DEVICE_INFO
    }

    /** This method is not supported and always returns `0`.  */
    override fun getDeviceVolume(): Int {
        return 0
    }

    /** This method is not supported and always returns `false`.  */
    override fun isDeviceMuted(): Boolean {
        return false
    }

    /** This method is not supported and does nothing.  */
    override fun setDeviceVolume(volume: Int) {}

    /** This method is not supported and does nothing.  */
    override fun increaseDeviceVolume() {}

    /** This method is not supported and does nothing.  */
    override fun decreaseDeviceVolume() {}

    /** This method is not supported and does nothing.  */
    override fun setDeviceMuted(muted: Boolean) {}

    // Internal methods.
    // Call deprecated callbacks.
    private fun updateInternalStateAndNotifyIfChanged() {
        if (remoteMediaClient == null) {
            // There is no session. We leave the state of the player as it is now.
            return
        }
        val oldWindowIndex = currentWindowIndex
        val oldMediaMetadata = mediaMetadata
        val oldPeriodUid = if (!getCurrentTimeline().isEmpty) getCurrentTimeline().getPeriod(oldWindowIndex, period,  /* setIds= */true).uid else null
        updatePlayerStateAndNotifyIfChanged( /* resultCallback= */null)
        updateRepeatModeAndNotifyIfChanged( /* resultCallback= */null)
        updatePlaybackRateAndNotifyIfChanged( /* resultCallback= */null)
        val playingPeriodChangedByTimelineChange = updateTimelineAndNotifyIfChanged()
        val currentTimeline = getCurrentTimeline()
        currentWindowIndex = fetchCurrentWindowIndex(remoteMediaClient, currentTimeline)
        mediaMetadata = mediaMetadataInternal
        val currentPeriodUid = if (!currentTimeline.isEmpty) currentTimeline.getPeriod(currentWindowIndex, period,  /* setIds= */true).uid else null
        if ((!playingPeriodChangedByTimelineChange
                        && !Util.areEqual(oldPeriodUid, currentPeriodUid)) && pendingSeekCount == 0) {
            // Report discontinuity and media item auto transition.
            currentTimeline.getPeriod(oldWindowIndex, period,  /* setIds= */true)
            currentTimeline.getWindow(oldWindowIndex, window)
            val windowDurationMs = window.durationMs
            val oldPosition = PositionInfo(
                    window.uid,
                    period.windowIndex,
                    window.mediaItem,
                    period.uid,
                    period.windowIndex,  /* positionMs= */
                    windowDurationMs,  /* contentPositionMs= */
                    windowDurationMs,  /* adGroupIndex= */
                    C.INDEX_UNSET,  /* adIndexInAdGroup= */
                    C.INDEX_UNSET)
            currentTimeline.getPeriod(currentWindowIndex, period,  /* setIds= */true)
            currentTimeline.getWindow(currentWindowIndex, window)
            val newPosition = PositionInfo(
                    window.uid,
                    period.windowIndex,
                    window.mediaItem,
                    period.uid,
                    period.windowIndex,  /* positionMs= */
                    window.defaultPositionMs,  /* contentPositionMs= */
                    window.defaultPositionMs,  /* adGroupIndex= */
                    C.INDEX_UNSET,  /* adIndexInAdGroup= */
                    C.INDEX_UNSET)
            listeners.queueEvent(
                    EVENT_POSITION_DISCONTINUITY
            ) { listener: Player.Listener ->
                listener.onPositionDiscontinuity(DISCONTINUITY_REASON_AUTO_TRANSITION)
                listener.onPositionDiscontinuity(
                        oldPosition, newPosition, DISCONTINUITY_REASON_AUTO_TRANSITION)
            }
            listeners.queueEvent(
                    EVENT_MEDIA_ITEM_TRANSITION
            ) { listener: Player.Listener ->
                listener.onMediaItemTransition(
                        currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_AUTO)
            }
        }
        if (updateTracksAndSelectionsAndNotifyIfChanged()) {
            listeners.queueEvent(
                    EVENT_TRACKS_CHANGED) { listener: Player.Listener -> listener.onTracksChanged(currentTracks) }
        }
        if (oldMediaMetadata != mediaMetadata) {
            listeners.queueEvent(
                    EVENT_MEDIA_METADATA_CHANGED
            ) { listener: Player.Listener -> listener.onMediaMetadataChanged(mediaMetadata) }
        }
        updateAvailableCommandsAndNotifyIfChanged()
        listeners.flushEvents()
    }

    /**
     * Updates [.playWhenReady] and [.playbackState] to match the Cast `remoteMediaClient` state, and notifies listeners of any state changes.
     *
     *
     * This method will only update values whose [StateHolder.pendingResultCallback] matches
     * the given `resultCallback`.
     */
    @RequiresNonNull("remoteMediaClient")
    private fun updatePlayerStateAndNotifyIfChanged(resultCallback: ResultCallback<*>?) {
        var newPlayWhenReadyValue = playWhenReady.value
        if (playWhenReady.acceptsUpdate(resultCallback)) {
            newPlayWhenReadyValue = !remoteMediaClient!!.isPaused
            playWhenReady.clearPendingResultCallback()
        }
        val playWhenReadyChangeReason = if (newPlayWhenReadyValue != playWhenReady.value) PLAY_WHEN_READY_CHANGE_REASON_REMOTE else PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        // We do not mask the playback state, so try setting it regardless of the playWhenReady masking.
        setPlayerStateAndNotifyIfChanged(
                newPlayWhenReadyValue, playWhenReadyChangeReason, fetchPlaybackState(remoteMediaClient))
    }

    @RequiresNonNull("remoteMediaClient")
    private fun updatePlaybackRateAndNotifyIfChanged(resultCallback: ResultCallback<*>?) {
        if (playbackParameters.acceptsUpdate(resultCallback)) {
            val mediaStatus = remoteMediaClient!!.mediaStatus
            val speed = mediaStatus?.playbackRate?.toFloat() ?: PlaybackParameters.DEFAULT.speed
            if (speed > 0.0f) {
                // Set the speed if not paused.
                setPlaybackParametersAndNotifyIfChanged(PlaybackParameters(speed))
            }
            playbackParameters.clearPendingResultCallback()
        }
    }

    @RequiresNonNull("remoteMediaClient")
    private fun updateRepeatModeAndNotifyIfChanged(resultCallback: ResultCallback<*>?) {
        if (repeatMode.acceptsUpdate(resultCallback)) {
            setRepeatModeAndNotifyIfChanged(fetchRepeatMode(remoteMediaClient))
            repeatMode.clearPendingResultCallback()
        }
    }

    /**
     * Updates the timeline and notifies [event listeners][Player.Listener] if required.
     *
     * @return Whether the timeline change has caused a change of the period currently being played.
     */
    // Calling deprecated listener method.
    private fun updateTimelineAndNotifyIfChanged(): Boolean {
        val oldTimeline: Timeline = currentTimeline
        val oldWindowIndex = currentWindowIndex
        var playingPeriodChanged = false
        if (updateTimeline()) {
            // TODO: Differentiate TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED and
            //     TIMELINE_CHANGE_REASON_SOURCE_UPDATE [see internal: b/65152553].
            val timeline: Timeline = currentTimeline
            // Call onTimelineChanged.
            listeners.queueEvent(
                    EVENT_TIMELINE_CHANGED
            ) { listener: Player.Listener -> listener.onTimelineChanged(timeline, TIMELINE_CHANGE_REASON_SOURCE_UPDATE) }

            // Call onPositionDiscontinuity if required.
            val currentTimeline = getCurrentTimeline()
            var playingPeriodRemoved = false
            if (!oldTimeline.isEmpty) {
                val oldPeriodUid = Util.castNonNull(oldTimeline.getPeriod(oldWindowIndex, period,  /* setIds= */true).uid)
                playingPeriodRemoved = currentTimeline.getIndexOfPeriod(oldPeriodUid) == C.INDEX_UNSET
            }
            if (playingPeriodRemoved) {
                val oldPosition: PositionInfo
                if (pendingMediaItemRemovalPosition != null) {
                    oldPosition = pendingMediaItemRemovalPosition
                    pendingMediaItemRemovalPosition = null
                } else {
                    // If the media item has been removed by another client, we don't know the removal
                    // position. We use the current position as a fallback.
                    oldTimeline.getPeriod(oldWindowIndex, period,  /* setIds= */true)
                    oldTimeline.getWindow(period.windowIndex, window)
                    oldPosition = PositionInfo(
                            window.uid,
                            period.windowIndex,
                            window.mediaItem,
                            period.uid,
                            period.windowIndex,
                            currentPosition,
                            contentPosition,  /* adGroupIndex= */
                            C.INDEX_UNSET,  /* adIndexInAdGroup= */
                            C.INDEX_UNSET)
                }
                val newPosition = currentPositionInfo
                listeners.queueEvent(
                        EVENT_POSITION_DISCONTINUITY
                ) { listener: Player.Listener ->
                    listener.onPositionDiscontinuity(DISCONTINUITY_REASON_REMOVE)
                    listener.onPositionDiscontinuity(
                            oldPosition, newPosition, DISCONTINUITY_REASON_REMOVE)
                }
            }

            // Call onMediaItemTransition if required.
            playingPeriodChanged = currentTimeline.isEmpty != oldTimeline.isEmpty || playingPeriodRemoved
            if (playingPeriodChanged) {
                listeners.queueEvent(
                        EVENT_MEDIA_ITEM_TRANSITION
                ) { listener: Player.Listener ->
                    listener.onMediaItemTransition(
                            currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
                }
            }
            updateAvailableCommandsAndNotifyIfChanged()
        }
        return playingPeriodChanged
    }

    /**
     * Updates the current timeline. The current window index may change as a result.
     *
     * @return Whether the current timeline has changed.
     */
    private fun updateTimeline(): Boolean {
        val oldTimeline = currentTimeline
        val status = mediaStatus
        currentTimeline = if (status != null) timelineTracker.getCastTimeline(remoteMediaClient) else CastTimeline.Companion.EMPTY_CAST_TIMELINE
        val timelineChanged = oldTimeline != currentTimeline
        if (timelineChanged) {
            currentWindowIndex = fetchCurrentWindowIndex(remoteMediaClient, currentTimeline)
        }
        return timelineChanged
    }

    /** Updates the internal tracks and selection and returns whether they have changed.  */
    private fun updateTracksAndSelectionsAndNotifyIfChanged(): Boolean {
        if (remoteMediaClient == null) {
            // There is no session. We leave the state of the player as it is now.
            return false
        }
        val mediaStatus = mediaStatus
        val mediaInfo = mediaStatus?.mediaInfo
        val castMediaTracks = mediaInfo?.mediaTracks
        if (castMediaTracks == null || castMediaTracks.isEmpty()) {
            val hasChanged = Tracks.EMPTY != currentTracks
            currentTracks = Tracks.EMPTY
            return hasChanged
        }
        var activeTrackIds = mediaStatus!!.activeTrackIds
        if (activeTrackIds == null) {
            activeTrackIds = EMPTY_TRACK_ID_ARRAY
        }
        val trackGroups = arrayOfNulls<Tracks.Group>(castMediaTracks.size)
        for (i in castMediaTracks.indices) {
            val mediaTrack = castMediaTracks[i]
            val trackGroup = TrackGroup( /* id= */Integer.toString(i), CastUtils.mediaTrackToFormat(mediaTrack))
            val trackSupport = intArrayOf(C.FORMAT_HANDLED)
            val trackSelected = booleanArrayOf(isTrackActive(mediaTrack.id, activeTrackIds))
            trackGroups[i] = Tracks.Group(trackGroup,  /* adaptiveSupported= */false, trackSupport, trackSelected)
        }
        val newTracks = Tracks(ImmutableList.copyOf(trackGroups))
        if (newTracks != currentTracks) {
            currentTracks = newTracks
            return true
        }
        return false
    }

    private fun updateAvailableCommandsAndNotifyIfChanged() {
        val previousAvailableCommands = availableCommands
        availableCommands = Util.getAvailableCommands( /* player= */this, PERMANENT_AVAILABLE_COMMANDS)
        if (availableCommands != previousAvailableCommands) {
            listeners.queueEvent(
                    EVENT_AVAILABLE_COMMANDS_CHANGED
            ) { listener: Player.Listener -> listener.onAvailableCommandsChanged(availableCommands) }
        }
    }

    private fun setMediaItemsInternal(
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
            repeatMode: @Player.RepeatMode Int) {
        var startIndex = startIndex
        var startPositionMs = startPositionMs
        if (remoteMediaClient == null || mediaItems.isEmpty()) {
            return
        }
        startPositionMs = if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs
        if (startIndex == C.INDEX_UNSET) {
            startIndex = currentMediaItemIndex
            startPositionMs = currentPosition
        }
        val currentTimeline = getCurrentTimeline()
        if (!currentTimeline.isEmpty) {
            pendingMediaItemRemovalPosition = currentPositionInfo
        }
        val mediaQueueItems = toMediaQueueItems(mediaItems)
        timelineTracker.onMediaItemsSet(mediaItems, mediaQueueItems)
        remoteMediaClient!!.queueLoad(
                mediaQueueItems,
                Math.min(startIndex, mediaItems.size - 1),
                getCastRepeatMode(repeatMode),
                startPositionMs,  /* customData= */
                null)
    }

    private fun addMediaItemsInternal(mediaItems: List<MediaItem>, uid: Int) {
        if (remoteMediaClient == null || mediaStatus == null) {
            return
        }
        val itemsToInsert = toMediaQueueItems(mediaItems)
        timelineTracker.onMediaItemsAdded(mediaItems, itemsToInsert)
        remoteMediaClient!!.queueInsertItems(itemsToInsert, uid,  /* customData= */null)
    }

    private fun moveMediaItemsInternal(uids: IntArray, fromIndex: Int, newIndex: Int) {
        if (remoteMediaClient == null || mediaStatus == null) {
            return
        }
        val insertBeforeIndex = if (fromIndex < newIndex) newIndex + uids.size else newIndex
        var insertBeforeItemId = MediaQueueItem.INVALID_ITEM_ID
        if (insertBeforeIndex < currentTimeline.windowCount) {
            insertBeforeItemId = currentTimeline.getWindow(insertBeforeIndex, window).uid as Int
        }
        remoteMediaClient!!.queueReorderItems(uids, insertBeforeItemId,  /* customData= */null)
    }

    private fun removeMediaItemsInternal(uids: IntArray): PendingResult<RemoteMediaClient.MediaChannelResult>? {
        if (remoteMediaClient == null || mediaStatus == null) {
            return null
        }
        val timeline = getCurrentTimeline()
        if (!timeline.isEmpty) {
            val periodUid = Util.castNonNull(timeline.getPeriod(currentPeriodIndex, period,  /* setIds= */true).uid)
            for (uid in uids) {
                if (periodUid == uid) {
                    pendingMediaItemRemovalPosition = currentPositionInfo
                    break
                }
            }
        }
        return remoteMediaClient!!.queueRemoveItems(uids,  /* customData= */null)
    }/* adGroupIndex= */  /* adIndexInAdGroup= */

    /* setIds= */
    private val currentPositionInfo: PositionInfo
        private get() {
            val currentTimeline = getCurrentTimeline()
            var newPeriodUid: Any? = null
            var newWindowUid: Any? = null
            var newMediaItem: MediaItem? = null
            if (!currentTimeline.isEmpty) {
                newPeriodUid = currentTimeline.getPeriod(currentPeriodIndex, period,  /* setIds= */true).uid
                newWindowUid = currentTimeline.getWindow(period.windowIndex, window).uid
                newMediaItem = window.mediaItem
            }
            return PositionInfo(
                    newWindowUid,
                    currentMediaItemIndex,
                    newMediaItem,
                    newPeriodUid,
                    currentPeriodIndex,
                    currentPosition,
                    contentPosition,  /* adGroupIndex= */
                    C.INDEX_UNSET,  /* adIndexInAdGroup= */
                    C.INDEX_UNSET)
        }

    private fun setRepeatModeAndNotifyIfChanged(repeatMode: @Player.RepeatMode Int) {
        if (this.repeatMode.value != repeatMode) {
            this.repeatMode.value = repeatMode
            listeners.queueEvent(
                    EVENT_REPEAT_MODE_CHANGED) { listener: Player.Listener -> listener.onRepeatModeChanged(repeatMode) }
            updateAvailableCommandsAndNotifyIfChanged()
        }
    }

    private fun setPlaybackParametersAndNotifyIfChanged(playbackParameters: PlaybackParameters) {
        if (this.playbackParameters.value == playbackParameters) {
            return
        }
        this.playbackParameters.value = playbackParameters
        listeners.queueEvent(
                EVENT_PLAYBACK_PARAMETERS_CHANGED
        ) { listener: Player.Listener -> listener.onPlaybackParametersChanged(playbackParameters) }
        updateAvailableCommandsAndNotifyIfChanged()
    }

    private fun setPlayerStateAndNotifyIfChanged(
            playWhenReady: Boolean,
            playWhenReadyChangeReason: @PlayWhenReadyChangeReason Int,
            playbackState: @Player.State Int) {
        val wasPlaying = this.playbackState == STATE_READY && this.playWhenReady.value
        val playWhenReadyChanged = this.playWhenReady.value != playWhenReady
        val playbackStateChanged = this.playbackState != playbackState
        if (playWhenReadyChanged || playbackStateChanged) {
            this.playbackState = playbackState
            this.playWhenReady.value = playWhenReady
            listeners.queueEvent( /* eventFlag= */
                    C.INDEX_UNSET
            ) { listener: Player.Listener -> listener.onPlayerStateChanged(playWhenReady, playbackState) }
            if (playbackStateChanged) {
                listeners.queueEvent(
                        EVENT_PLAYBACK_STATE_CHANGED
                ) { listener: Player.Listener -> listener.onPlaybackStateChanged(playbackState) }
            }
            if (playWhenReadyChanged) {
                listeners.queueEvent(
                        EVENT_PLAY_WHEN_READY_CHANGED
                ) { listener: Player.Listener -> listener.onPlayWhenReadyChanged(playWhenReady, playWhenReadyChangeReason) }
            }
            val isPlaying = playbackState == STATE_READY && playWhenReady
            if (wasPlaying != isPlaying) {
                listeners.queueEvent(
                        EVENT_IS_PLAYING_CHANGED) { listener: Player.Listener -> listener.onIsPlayingChanged(isPlaying) }
            }
        }
    }

    private fun setRemoteMediaClient(remoteMediaClient: RemoteMediaClient?) {
        if (this.remoteMediaClient === remoteMediaClient) {
            // Do nothing.
            return
        }
        if (this.remoteMediaClient != null) {
            this.remoteMediaClient!!.unregisterCallback(statusListener)
            this.remoteMediaClient!!.removeProgressListener(statusListener)
        }
        this.remoteMediaClient = remoteMediaClient
        if (remoteMediaClient != null) {
            if (sessionAvailabilityListener != null) {
                sessionAvailabilityListener!!.onCastSessionAvailable()
            }
            remoteMediaClient.registerCallback(statusListener)
            remoteMediaClient.addProgressListener(statusListener, PROGRESS_REPORT_PERIOD_MS)
            updateInternalStateAndNotifyIfChanged()
        } else {
            updateTimelineAndNotifyIfChanged()
            if (sessionAvailabilityListener != null) {
                sessionAvailabilityListener!!.onCastSessionUnavailable()
            }
        }
    }

    private val mediaStatus: MediaStatus?
        private get() = if (remoteMediaClient != null) remoteMediaClient!!.mediaStatus else null

    private fun toMediaQueueItems(mediaItems: List<MediaItem>): Array<MediaQueueItem?> {
        val mediaQueueItems = arrayOfNulls<MediaQueueItem>(mediaItems.size)
        for (i in mediaItems.indices) {
            mediaQueueItems[i] = mediaItemConverter.toMediaQueueItem(mediaItems[i])
        }
        return mediaQueueItems
    }

    // Internal classes.
    private inner class StatusListener : RemoteMediaClient.Callback(), SessionManagerListener<CastSession>, RemoteMediaClient.ProgressListener {
        // RemoteMediaClient.ProgressListener implementation.
        override fun onProgressUpdated(progressMs: Long, unusedDurationMs: Long) {
            lastReportedPositionMs = progressMs
        }

        // RemoteMediaClient.Callback implementation.
        override fun onStatusUpdated() {
            updateInternalStateAndNotifyIfChanged()
        }

        override fun onMetadataUpdated() {}
        override fun onQueueStatusUpdated() {
            updateTimelineAndNotifyIfChanged()
            listeners.flushEvents()
        }

        override fun onPreloadStatusUpdated() {}
        override fun onSendingRemoteMediaRequest() {}
        override fun onAdBreakStatusUpdated() {}

        // SessionManagerListener implementation.
        override fun onSessionStarted(castSession: CastSession, s: String) {
            setRemoteMediaClient(castSession.remoteMediaClient)
        }

        override fun onSessionResumed(castSession: CastSession, b: Boolean) {
            setRemoteMediaClient(castSession.remoteMediaClient)
        }

        override fun onSessionEnded(castSession: CastSession, i: Int) {
            setRemoteMediaClient(null)
        }

        override fun onSessionSuspended(castSession: CastSession, i: Int) {
            setRemoteMediaClient(null)
        }

        override fun onSessionResumeFailed(castSession: CastSession, statusCode: Int) {
            Log.e(
                    TAG,
                    "Session resume failed. Error code "
                            + statusCode
                            + ": "
                            + CastUtils.getLogString(statusCode))
        }

        override fun onSessionStarting(castSession: CastSession) {
            // Do nothing.
        }

        override fun onSessionStartFailed(castSession: CastSession, statusCode: Int) {
            Log.e(
                    TAG,
                    "Session start failed. Error code "
                            + statusCode
                            + ": "
                            + CastUtils.getLogString(statusCode))
        }

        override fun onSessionEnding(castSession: CastSession) {
            // Do nothing.
        }

        override fun onSessionResuming(castSession: CastSession, s: String) {
            // Do nothing.
        }
    }

    private inner class SeekResultCallback : ResultCallback<RemoteMediaClient.MediaChannelResult> {
        // We still call Listener#onSeekProcessed() for backwards compatibility with listeners that
        // don't implement onPositionDiscontinuity().
        override fun onResult(result: RemoteMediaClient.MediaChannelResult) {
            val statusCode = result.status.statusCode
            if (statusCode != CastStatusCodes.SUCCESS && statusCode != CastStatusCodes.REPLACED) {
                Log.e(
                        TAG,
                        "Seek failed. Error code " + statusCode + ": " + CastUtils.getLogString(statusCode))
            }
            if (--pendingSeekCount == 0) {
                currentWindowIndex = pendingSeekWindowIndex
                pendingSeekWindowIndex = C.INDEX_UNSET
                pendingSeekPositionMs = C.TIME_UNSET
                listeners.sendEvent( /* eventFlag= */C.INDEX_UNSET) { obj: Player.Listener -> obj.onSeekProcessed() }
            }
        }
    }

    /** Holds the value and the masking status of a specific part of the [CastPlayer] state.  */
    private class StateHolder<T>(
            /** The user-facing value of a specific part of the [CastPlayer] state.  */
            var value: T) {
        /**
         * If [.value] is being masked, holds the result callback for the operation that triggered
         * the masking. Or null if [.value] is not being masked.
         */
        var pendingResultCallback: ResultCallback<RemoteMediaClient.MediaChannelResult>? = null
        fun clearPendingResultCallback() {
            pendingResultCallback = null
        }

        /**
         * Returns whether this state holder accepts updates coming from the given result callback.
         *
         *
         * A null `resultCallback` means that the update is a regular receiver state update, in
         * which case the update will only be accepted if [.value] is not being masked. If [ ][.value] is being masked, the update will only be accepted if `resultCallback` is the
         * same as the [.pendingResultCallback].
         *
         * @param resultCallback A result callback. May be null if the update comes from a regular
         * receiver status update.
         */
        fun acceptsUpdate(resultCallback: ResultCallback<*>?): Boolean {
            return pendingResultCallback === resultCallback
        }
    }

    companion object {
        /** The [DeviceInfo] returned by [this player][.getDeviceInfo].  */
        @JvmField
        val DEVICE_INFO = DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE,  /* minVolume= */0,  /* maxVolume= */0)

        init {
            ExoPlayerLibraryInfo.registerModule("goog.exo.cast")
        }

        @JvmField
        @VisibleForTesting /* package */
        val PERMANENT_AVAILABLE_COMMANDS = Player.Commands.Builder()
                .addAll(
                        COMMAND_PLAY_PAUSE,
                        COMMAND_PREPARE,
                        COMMAND_STOP,
                        COMMAND_SEEK_TO_DEFAULT_POSITION,
                        COMMAND_SEEK_TO_MEDIA_ITEM,
                        COMMAND_SET_REPEAT_MODE,
                        COMMAND_SET_SPEED_AND_PITCH,
                        COMMAND_GET_CURRENT_MEDIA_ITEM,
                        COMMAND_GET_TIMELINE,
                        COMMAND_GET_MEDIA_ITEMS_METADATA,
                        COMMAND_SET_MEDIA_ITEMS_METADATA,
                        COMMAND_SET_MEDIA_ITEM,
                        COMMAND_CHANGE_MEDIA_ITEMS,
                        COMMAND_GET_TRACKS)
                .build()
        const val MIN_SPEED_SUPPORTED = 0.5f
        const val MAX_SPEED_SUPPORTED = 2.0f
        private const val TAG = "CastPlayer"
        private const val PROGRESS_REPORT_PERIOD_MS: Long = 1000
        private val EMPTY_TRACK_ID_ARRAY = LongArray(0)

        /**
         * Retrieves the playback state from `remoteMediaClient` and maps it into a [Player]
         * state
         */
        private fun fetchPlaybackState(remoteMediaClient: RemoteMediaClient?): Int {
            val receiverAppStatus = remoteMediaClient!!.playerState
            return when (receiverAppStatus) {
                MediaStatus.PLAYER_STATE_BUFFERING -> STATE_BUFFERING
                MediaStatus.PLAYER_STATE_PLAYING, MediaStatus.PLAYER_STATE_PAUSED -> STATE_READY
                MediaStatus.PLAYER_STATE_IDLE, MediaStatus.PLAYER_STATE_UNKNOWN -> STATE_IDLE
                else -> STATE_IDLE
            }
        }

        /**
         * Retrieves the repeat mode from `remoteMediaClient` and maps it into a [ ].
         */
        private fun fetchRepeatMode(remoteMediaClient: RemoteMediaClient?): @Player.RepeatMode Int {
            val mediaStatus = remoteMediaClient!!.mediaStatus
                    ?: // No media session active, yet.
                    return REPEAT_MODE_OFF
            val castRepeatMode = mediaStatus.queueRepeatMode
            return when (castRepeatMode) {
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> REPEAT_MODE_ONE
                MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> REPEAT_MODE_ALL
                MediaStatus.REPEAT_MODE_REPEAT_OFF -> REPEAT_MODE_OFF
                else -> throw IllegalStateException()
            }
        }

        private fun fetchCurrentWindowIndex(
                remoteMediaClient: RemoteMediaClient?, timeline: Timeline): Int {
            if (remoteMediaClient == null) {
                return 0
            }
            var currentWindowIndex = C.INDEX_UNSET
            val currentItem = remoteMediaClient.currentItem
            if (currentItem != null) {
                currentWindowIndex = timeline.getIndexOfPeriod(currentItem.itemId)
            }
            if (currentWindowIndex == C.INDEX_UNSET) {
                // The timeline is empty. Fall back to index 0.
                currentWindowIndex = 0
            }
            return currentWindowIndex
        }

        private fun isTrackActive(id: Long, activeTrackIds: LongArray?): Boolean {
            for (activeTrackId in activeTrackIds!!) {
                if (activeTrackId == id) {
                    return true
                }
            }
            return false
        }

        private fun getCastRepeatMode(repeatMode: @Player.RepeatMode Int): Int {
            return when (repeatMode) {
                REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                REPEAT_MODE_OFF -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                else -> throw IllegalArgumentException()
            }
        }
    }
}