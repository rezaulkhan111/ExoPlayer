/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.analytics

import android.os.Looper
import android.util.SparseArray
import androidx.annotation.CallSuper
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DeviceInfo
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.drm.DrmSession
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.FlagSet
import com.google.android.exoplayer2.util.HandlerWrapper
import com.google.android.exoplayer2.util.ListenerSet
import com.google.android.exoplayer2.util.ListenerSet.IterationFinishedEvent
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.util.Util.currentOrMainLooper
import com.google.android.exoplayer2.video.VideoSize
import com.google.common.base.Objects
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import java.io.IOException

/**
 * Data collector that forwards analytics events to [AnalyticsListeners][AnalyticsListener].
 */
class DefaultAnalyticsCollector : AnalyticsCollector {

    private val clock: Clock
    private var period: Timeline.Period
    private var window: Timeline.Window
    private var mediaPeriodQueueTracker: MediaPeriodQueueTracker
    private var eventTimes: SparseArray<EventTime?>
    private var listeners: ListenerSet<AnalyticsListener>
    private var player: @MonotonicNonNull Player? = null
    private var handler: @MonotonicNonNull HandlerWrapper? = null
    private var isSeeking = false

    /**
     * Creates an analytics collector.
     *
     * @param clock A [Clock] used to generate timestamps.
     */
  constructor(clock: Clock?) {
        this.clock = checkNotNull(clock)
        listeners = ListenerSet<T>(currentOrMainLooper, clock, IterationFinishedEvent<T> { listener: T?, flags: FlagSet? -> })
        period = Timeline.Period()
        window = Timeline.Window()
        mediaPeriodQueueTracker = MediaPeriodQueueTracker(period)
        eventTimes = SparseArray()
    }

    @CallSuper
    override fun addListener(listener: AnalyticsListener?) {
        checkNotNull(listener)
        listeners.add(listener)
    }

    @CallSuper
    override fun removeListener(listener: AnalyticsListener?) {
        listeners.remove(listener)
    }

    @CallSuper
    override fun setPlayer(player: Player?, looper: Looper?) {
        checkState(this.player == null || mediaPeriodQueueTracker.mediaPeriodQueue.isEmpty())
        this.player = checkNotNull(player)
        handler = clock.createHandler(looper, null)
        listeners = listeners.copy(looper, IterationFinishedEvent<AnalyticsListener> { listener: AnalyticsListener, flags: FlagSet? -> listener.onEvents(player, AnalyticsListener.Events(flags, eventTimes)) })
    }

    @CallSuper
    override fun release() {
        // Release lazily so that all events that got triggered as part of player.release()
        // are still delivered to all listeners and onPlayerReleased() is delivered last.
        checkStateNotNull(handler).post { releaseInternal() }
    }

    override fun updateMediaPeriodQueueInfo(queue: List<MediaSource.MediaPeriodId?>?, readingPeriod: MediaSource.MediaPeriodId?) {
        mediaPeriodQueueTracker.onQueueUpdated(queue!!, readingPeriod, checkNotNull(player))
    }

    // External events.
    // Calling deprecated listener method.
    override fun notifySeekStarted() {
        if (!isSeeking) {
            val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
            isSeeking = true
            sendEvent(eventTime,  /* eventFlag= */C.INDEX_UNSET, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onSeekStarted(eventTime) })
        }
    }

    // Audio events.
    override fun onAudioEnabled(counters: DecoderCounters?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_ENABLED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onAudioEnabled(eventTime, counters)
            listener.onDecoderEnabled(eventTime, C.TRACK_TYPE_AUDIO, counters)
        })
    }

    override fun onAudioDecoderInitialized(decoderName: String?, initializedTimestampMs: Long, initializationDurationMs: Long) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_DECODER_INITIALIZED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onAudioDecoderInitialized(eventTime, decoderName, initializationDurationMs)
            listener.onAudioDecoderInitialized(eventTime, decoderName, initializedTimestampMs, initializationDurationMs)
            listener.onDecoderInitialized(eventTime, C.TRACK_TYPE_AUDIO, decoderName, initializationDurationMs)
        })
    }

    override fun onAudioInputFormatChanged(format: Format?, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_INPUT_FORMAT_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onAudioInputFormatChanged(eventTime, format)
            listener.onAudioInputFormatChanged(eventTime, format, decoderReuseEvaluation)
            listener.onDecoderInputFormatChanged(eventTime, C.TRACK_TYPE_AUDIO, format)
        })
    }

    override fun onAudioPositionAdvancing(playoutStartSystemTimeMs: Long) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_POSITION_ADVANCING, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onAudioPositionAdvancing(eventTime, playoutStartSystemTimeMs) })
    }

    override fun onAudioUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_UNDERRUN, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onAudioUnderrun(eventTime, bufferSize, bufferSizeMs, elapsedSinceLastFeedMs) })
    }

    override fun onAudioDecoderReleased(decoderName: String?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_DECODER_RELEASED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onAudioDecoderReleased(eventTime, decoderName) })
    }

    // Calling deprecated listener method.
    override fun onAudioDisabled(counters: DecoderCounters?) {
        val eventTime: EventTime = generatePlayingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_DISABLED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onAudioDisabled(eventTime, counters)
            listener.onDecoderDisabled(eventTime, C.TRACK_TYPE_AUDIO, counters)
        })
    }

    override fun onAudioSinkError(audioSinkError: Exception?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_SINK_ERROR, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onAudioSinkError(eventTime, audioSinkError) })
    }

    override fun onAudioCodecError(audioCodecError: Exception?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_CODEC_ERROR, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onAudioCodecError(eventTime, audioCodecError) })
    }

    override fun onVolumeChanged(volume: Float) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_VOLUME_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onVolumeChanged(eventTime, volume) })
    }

    // Video events.
    // Calling deprecated listener method.
    override fun onVideoEnabled(counters: DecoderCounters?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_ENABLED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onVideoEnabled(eventTime, counters)
            listener.onDecoderEnabled(eventTime, C.TRACK_TYPE_VIDEO, counters)
        })
    }

    // Calling deprecated listener method.
    override fun onVideoDecoderInitialized(decoderName: String?, initializedTimestampMs: Long, initializationDurationMs: Long) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_DECODER_INITIALIZED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onVideoDecoderInitialized(eventTime, decoderName, initializationDurationMs)
            listener.onVideoDecoderInitialized(eventTime, decoderName, initializedTimestampMs, initializationDurationMs)
            listener.onDecoderInitialized(eventTime, C.TRACK_TYPE_VIDEO, decoderName, initializationDurationMs)
        })
    }

    // Calling deprecated listener method.
    override fun onVideoInputFormatChanged(format: Format?, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_INPUT_FORMAT_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onVideoInputFormatChanged(eventTime, format)
            listener.onVideoInputFormatChanged(eventTime, format, decoderReuseEvaluation)
            listener.onDecoderInputFormatChanged(eventTime, C.TRACK_TYPE_VIDEO, format)
        })
    }

    override fun onDroppedFrames(count: Int, elapsedMs: Long) {
        val eventTime: EventTime = generatePlayingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_DROPPED_VIDEO_FRAMES, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onDroppedVideoFrames(eventTime, count, elapsedMs) })
    }

    override fun onVideoDecoderReleased(decoderName: String?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_DECODER_RELEASED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onVideoDecoderReleased(eventTime, decoderName) })
    }

    // Calling deprecated listener method.
    override fun onVideoDisabled(counters: DecoderCounters?) {
        val eventTime: EventTime = generatePlayingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_DISABLED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onVideoDisabled(eventTime, counters)
            listener.onDecoderDisabled(eventTime, C.TRACK_TYPE_VIDEO, counters)
        })
    }

    override fun onRenderedFirstFrame(output: Any?, renderTimeMs: Long) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_RENDERED_FIRST_FRAME, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onRenderedFirstFrame(eventTime, output, renderTimeMs) })
    }

    override fun onVideoFrameProcessingOffset(totalProcessingOffsetUs: Long, frameCount: Int) {
        val eventTime: EventTime = generatePlayingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_FRAME_PROCESSING_OFFSET, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onVideoFrameProcessingOffset(eventTime, totalProcessingOffsetUs, frameCount) })
    }

    override fun onVideoCodecError(videoCodecError: Exception?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_CODEC_ERROR, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onVideoCodecError(eventTime, videoCodecError) })
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_SURFACE_SIZE_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onSurfaceSizeChanged(eventTime, width, height) })
    }

    // MediaSourceEventListener implementation.
    override fun onLoadStarted(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, loadEventInfo: LoadEventInfo?, mediaLoadData: MediaLoadData?) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_LOAD_STARTED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onLoadStarted(eventTime, loadEventInfo, mediaLoadData) })
    }

    override fun onLoadCompleted(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, loadEventInfo: LoadEventInfo?, mediaLoadData: MediaLoadData?) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_LOAD_COMPLETED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onLoadCompleted(eventTime, loadEventInfo, mediaLoadData) })
    }

    override fun onLoadCanceled(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, loadEventInfo: LoadEventInfo?, mediaLoadData: MediaLoadData?) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_LOAD_CANCELED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onLoadCanceled(eventTime, loadEventInfo, mediaLoadData) })
    }

    override fun onLoadError(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, loadEventInfo: LoadEventInfo?, mediaLoadData: MediaLoadData?, error: IOException?, wasCanceled: Boolean) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_LOAD_ERROR, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onLoadError(eventTime, loadEventInfo, mediaLoadData, error, wasCanceled) })
    }

    override fun onUpstreamDiscarded(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, mediaLoadData: MediaLoadData?) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_UPSTREAM_DISCARDED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onUpstreamDiscarded(eventTime, mediaLoadData) })
    }

    override fun onDownstreamFormatChanged(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, mediaLoadData: MediaLoadData?) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_DOWNSTREAM_FORMAT_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onDownstreamFormatChanged(eventTime, mediaLoadData) })
    }

    // Player.Listener implementation.
    // TODO: Use Player.Listener.onEvents to know when a set of simultaneous callbacks finished.
    // This helps to assign exactly the same EventTime to all of them instead of having slightly
    // different real times.
    override fun onTimelineChanged(timeline: Timeline?, reason: @TimelineChangeReason Int) {
        mediaPeriodQueueTracker.onTimelineChanged(checkNotNull(player))
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_TIMELINE_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onTimelineChanged(eventTime, reason) })
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, @MediaItemTransitionReason reason: Int) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_MEDIA_ITEM_TRANSITION, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onMediaItemTransition(eventTime, mediaItem, reason) })
    }

    override fun onTracksChanged(tracks: Tracks?) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_TRACKS_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onTracksChanged(eventTime, tracks) })
    }

    override fun onLoadingChanged(isLoading: Boolean) {
        // Do nothing. Handled by non-deprecated onIsLoadingChanged.
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_IS_LOADING_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onLoadingChanged(eventTime, isLoading)
            listener.onIsLoadingChanged(eventTime, isLoading)
        })
    }

    override fun onAvailableCommandsChanged(availableCommands: Player.Commands?) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AVAILABLE_COMMANDS_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onAvailableCommandsChanged(eventTime, availableCommands) })
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, @Player.State playbackState: Int) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime,  /* eventFlag= */
                C.INDEX_UNSET, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onPlayerStateChanged(eventTime, playWhenReady, playbackState) })
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onPlaybackStateChanged(eventTime, playbackState) })
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, @PlayWhenReadyChangeReason reason: Int) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_PLAY_WHEN_READY_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onPlayWhenReadyChanged(eventTime, playWhenReady, reason) })
    }

    override fun onPlaybackSuppressionReasonChanged(@PlaybackSuppressionReason playbackSuppressionReason: Int) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onPlaybackSuppressionReasonChanged(eventTime, playbackSuppressionReason) })
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_IS_PLAYING_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onIsPlayingChanged(eventTime, isPlaying) })
    }

    override fun onRepeatModeChanged(@Player.RepeatMode repeatMode: Int) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_REPEAT_MODE_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onRepeatModeChanged(eventTime, repeatMode) })
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onShuffleModeChanged(eventTime, shuffleModeEnabled) })
    }

    override fun onPlayerError(error: PlaybackException?) {
        val eventTime: EventTime = getEventTimeForErrorEvent(error)
        sendEvent(eventTime, AnalyticsListener.EVENT_PLAYER_ERROR, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onPlayerError(eventTime, error) })
    }

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        val eventTime: EventTime = getEventTimeForErrorEvent(error)
        sendEvent(eventTime, AnalyticsListener.EVENT_PLAYER_ERROR, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onPlayerErrorChanged(eventTime, error) })
    }

    override fun onPositionDiscontinuity(@DiscontinuityReason reason: Int) {
        // Do nothing. Handled by non-deprecated onPositionDiscontinuity.
    }

    // Calling deprecated callback.
    override fun onPositionDiscontinuity(oldPosition: PositionInfo?, newPosition: PositionInfo?, @DiscontinuityReason reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            isSeeking = false
        }
        mediaPeriodQueueTracker.onPositionDiscontinuity(checkNotNull(player))
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_POSITION_DISCONTINUITY, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onPositionDiscontinuity(eventTime, reason)
            listener.onPositionDiscontinuity(eventTime, oldPosition, newPosition, reason)
        })
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_PLAYBACK_PARAMETERS_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onPlaybackParametersChanged(eventTime, playbackParameters) })
    }

    override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_SEEK_BACK_INCREMENT_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onSeekBackIncrementChanged(eventTime, seekBackIncrementMs) })
    }

    override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_SEEK_FORWARD_INCREMENT_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onSeekForwardIncrementChanged(eventTime, seekForwardIncrementMs) })
    }

    override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onMaxSeekToPreviousPositionChanged(eventTime, maxSeekToPreviousPositionMs) })
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata?) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_MEDIA_METADATA_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onMediaMetadataChanged(eventTime, mediaMetadata) })
    }

    override fun onPlaylistMetadataChanged(playlistMetadata: MediaMetadata?) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_PLAYLIST_METADATA_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onPlaylistMetadataChanged(eventTime, playlistMetadata) })
    }

    fun onMetadata(metadata: Metadata?) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_METADATA, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onMetadata(eventTime, metadata) })
    }

    override fun onCues(cues: List<Cue?>?) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_CUES, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onCues(eventTime, cues) })
    }

    override fun onCues(cueGroup: CueGroup?) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_CUES, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onCues(eventTime, cueGroup) })
    }

    override fun onSeekProcessed() {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime,  /* eventFlag= */C.INDEX_UNSET, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onSeekProcessed(eventTime) })
    }

    override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_SKIP_SILENCE_ENABLED_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onSkipSilenceEnabledChanged(eventTime, skipSilenceEnabled) })
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_SESSION_ID, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onAudioSessionIdChanged(eventTime, audioSessionId) })
    }

    override fun onAudioAttributesChanged(audioAttributes: AudioAttributes?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_ATTRIBUTES_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onAudioAttributesChanged(eventTime, audioAttributes) })
    }

    override fun onVideoSizeChanged(videoSize: VideoSize?) {
        val eventTime: EventTime = generateReadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_SIZE_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onVideoSizeChanged(eventTime, videoSize)
            listener.onVideoSizeChanged(eventTime, videoSize!!.width, videoSize.height, videoSize.unappliedRotationDegrees, videoSize.pixelWidthHeightRatio)
        })
    }

    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters?) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onTrackSelectionParametersChanged(eventTime, parameters) })
    }

    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo?) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_DEVICE_INFO_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onDeviceInfoChanged(eventTime, deviceInfo) })
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_DEVICE_VOLUME_CHANGED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onDeviceVolumeChanged(eventTime, volume, muted) })
    }

    override fun onRenderedFirstFrame() {
        // Do nothing. Handled by onRenderedFirstFrame call with additional parameters.
    }

    override fun onEvents(player: Player?, events: Player.Events?) {
        // Do nothing. AnalyticsCollector issues its own onEvents.
    }

    // BandwidthMeter.EventListener implementation.
    override fun onBandwidthSample(elapsedMs: Int, bytesTransferred: Long, bitrateEstimate: Long) {
        val eventTime: EventTime = generateLoadingMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_BANDWIDTH_ESTIMATE, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onBandwidthEstimate(eventTime, elapsedMs, bytesTransferred, bitrateEstimate) })
    }

    // DrmSessionEventListener implementation.
    // Calls deprecated listener method.
    override fun onDrmSessionAcquired(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, @DrmSession.State state: Int) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_DRM_SESSION_ACQUIRED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener ->
            listener.onDrmSessionAcquired(eventTime)
            listener.onDrmSessionAcquired(eventTime, state)
        })
    }

    override fun onDrmKeysLoaded(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_DRM_KEYS_LOADED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onDrmKeysLoaded(eventTime) })
    }

    override fun onDrmSessionManagerError(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, error: Exception?) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_DRM_SESSION_MANAGER_ERROR, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onDrmSessionManagerError(eventTime, error) })
    }

    override fun onDrmKeysRestored(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_DRM_KEYS_RESTORED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onDrmKeysRestored(eventTime) })
    }

    override fun onDrmKeysRemoved(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_DRM_KEYS_REMOVED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onDrmKeysRemoved(eventTime) })
    }

    override fun onDrmSessionReleased(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?) {
        val eventTime: EventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
        sendEvent(eventTime, AnalyticsListener.EVENT_DRM_SESSION_RELEASED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onDrmSessionReleased(eventTime) })
    }
    // Internal methods.
    /**
     * Sends an event to registered listeners.
     *
     * @param eventTime The [EventTime] to report.
     * @param eventFlag An integer flag indicating the type of the event, or [C.INDEX_UNSET] to
     * report this event without flag.
     * @param eventInvocation The event.
     */
    protected fun sendEvent(eventTime: EventTime?, eventFlag: Int, eventInvocation: ListenerSet.Event<AnalyticsListener?>?) {
        eventTimes.put(eventFlag, eventTime)
        listeners.sendEvent(eventFlag, eventInvocation)
    }

    /** Generates an [EventTime] for the currently playing item in the player.  */
    protected fun generateCurrentPlayerMediaPeriodEventTime(): EventTime {
        return generateEventTime(mediaPeriodQueueTracker.getCurrentPlayerMediaPeriod())
    }

    /** Returns a new [EventTime] for the specified timeline, window and media period id.  */
    @RequiresNonNull("player")
    protected fun generateEventTime(timeline: Timeline?, windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?): EventTime {
        var mediaPeriodId = mediaPeriodId
        if (timeline.isEmpty) {
            // Ensure media period id is only reported together with a valid timeline.
            mediaPeriodId = null
        }
        val realtimeMs = clock.elapsedRealtime()
        val eventPositionMs: Long
        val isInCurrentWindow = (timeline.equals(player.getCurrentTimeline()) && windowIndex == player.getCurrentMediaItemIndex())
        eventPositionMs = if (mediaPeriodId != null && mediaPeriodId.isAd) {
            val isCurrentAd = isInCurrentWindow && player.getCurrentAdGroupIndex() == mediaPeriodId.adGroupIndex && player.getCurrentAdIndexInAdGroup() == mediaPeriodId.adIndexInAdGroup
            // Assume start position of 0 for future ads.
            if (isCurrentAd) player.getCurrentPosition() else 0
        } else if (isInCurrentWindow) {
            player.getContentPosition()
        } else {
            // Assume default start position for future content windows. If timeline is not available yet,
            // assume start position of 0.
            if (timeline.isEmpty) 0 else timeline.getWindow(windowIndex, window).defaultPositionMs
        }
        val currentMediaPeriodId: MediaSource.MediaPeriodId = mediaPeriodQueueTracker.getCurrentPlayerMediaPeriod()
        return EventTime(realtimeMs, timeline, windowIndex, mediaPeriodId, eventPositionMs, player.getCurrentTimeline(), player.getCurrentMediaItemIndex(), currentMediaPeriodId, player.getCurrentPosition(), player.getTotalBufferedDuration())
    }

    private fun releaseInternal() {
        val eventTime: EventTime = generateCurrentPlayerMediaPeriodEventTime()
        sendEvent(eventTime, AnalyticsListener.EVENT_PLAYER_RELEASED, ListenerSet.Event<AnalyticsListener> { listener: AnalyticsListener -> listener.onPlayerReleased(eventTime) })
        listeners.release()
    }

    private fun generateEventTime(mediaPeriodId: MediaSource.MediaPeriodId?): EventTime {
        checkNotNull(player)
        val knownTimeline: Timeline? = if (mediaPeriodId == null) null else mediaPeriodQueueTracker.getMediaPeriodIdTimeline(mediaPeriodId)
        if (mediaPeriodId == null || knownTimeline == null) {
            val windowIndex: Int = player.getCurrentMediaItemIndex()
            val timeline: Timeline = player.getCurrentTimeline()
            val windowIsInTimeline: Boolean = windowIndex < timeline.windowCount
            return generateEventTime(if (windowIsInTimeline) timeline else Timeline.EMPTY, windowIndex,  /* mediaPeriodId= */null)
        }
        val windowIndex: Int = knownTimeline.getPeriodByUid(mediaPeriodId.periodUid, period).windowIndex
        return generateEventTime(knownTimeline, windowIndex, mediaPeriodId)
    }

    private fun generatePlayingMediaPeriodEventTime(): EventTime {
        return generateEventTime(mediaPeriodQueueTracker.getPlayingMediaPeriod())
    }

    private fun generateReadingMediaPeriodEventTime(): EventTime {
        return generateEventTime(mediaPeriodQueueTracker.getReadingMediaPeriod())
    }

    private fun generateLoadingMediaPeriodEventTime(): EventTime {
        return generateEventTime(mediaPeriodQueueTracker.getLoadingMediaPeriod())
    }

    private fun generateMediaPeriodEventTime(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?): EventTime {
        checkNotNull(player)
        if (mediaPeriodId != null) {
            val isInKnownTimeline = mediaPeriodQueueTracker.getMediaPeriodIdTimeline(mediaPeriodId) != null
            return if (isInKnownTimeline) generateEventTime(mediaPeriodId) else generateEventTime(Timeline.EMPTY, windowIndex, mediaPeriodId)
        }
        val timeline: Timeline = player.getCurrentTimeline()
        val windowIsInTimeline: Boolean = windowIndex < timeline.windowCount
        return generateEventTime(if (windowIsInTimeline) timeline else Timeline.EMPTY, windowIndex,  /* mediaPeriodId= */null)
    }

    private fun getEventTimeForErrorEvent(error: PlaybackException?): EventTime {
        if (error is ExoPlaybackException) {
            val exoError: ExoPlaybackException? = error as ExoPlaybackException?
            if (exoError.mediaPeriodId != null) {
                return generateEventTime(MediaSource.MediaPeriodId(exoError.mediaPeriodId))
            }
        }
        return generateCurrentPlayerMediaPeriodEventTime()
    }

    /** Keeps track of the active media periods and currently playing and reading media period.  */
    private class MediaPeriodQueueTracker( // TODO: Investigate reporting MediaPeriodId in renderer events.
            private val period: Timeline.Period) {
        var mediaPeriodQueue: ImmutableList<MediaSource.MediaPeriodId>
        private var mediaPeriodTimelines: ImmutableMap<MediaSource.MediaPeriodId, Timeline?>

        /**
         * Returns the [MediaPeriodId] of the media period corresponding the current position of
         * the player.
         *
         *
         * May be null if no matching media period has been created yet.
         */
        var currentPlayerMediaPeriod: MediaSource.MediaPeriodId? = null
            private set

        /**
         * Returns the [MediaPeriodId] of the media period at the front of the queue. If the queue
         * is empty, this is the last media period which was at the front of the queue.
         *
         *
         * May be null, if no media period has been created yet.
         */
        var playingMediaPeriod: @MonotonicNonNull MediaSource.MediaPeriodId? = null
            private set

        /**
         * Returns the [MediaPeriodId] of the media period currently being read by the player. If
         * the queue is empty, this is the last media period which was read by the player.
         *
         *
         * May be null, if no media period has been created yet.
         */
        var readingMediaPeriod: @MonotonicNonNull MediaSource.MediaPeriodId? = null
            private set

        init {
            mediaPeriodQueue = ImmutableList.of<MediaSource.MediaPeriodId>()
            mediaPeriodTimelines = ImmutableMap.of<MediaSource.MediaPeriodId, Timeline?>()
        }

        /**
         * Returns the [MediaPeriodId] of the media period at the end of the queue which is
         * currently loading or will be the next one loading.
         *
         *
         * May be null, if no media period is active yet.
         */
        val loadingMediaPeriod: MediaSource.MediaPeriodId?
            get() = if (mediaPeriodQueue.isEmpty()) null else Iterables.getLast<MediaSource.MediaPeriodId>(mediaPeriodQueue)

        /**
         * Returns the most recent [Timeline] for the given [MediaPeriodId], or null if no
         * timeline is available.
         */
        fun getMediaPeriodIdTimeline(mediaPeriodId: MediaSource.MediaPeriodId?): Timeline? {
            return mediaPeriodTimelines[mediaPeriodId]
        }

        /** Updates the queue tracker with a reported position discontinuity.  */
        fun onPositionDiscontinuity(player: Player) {
            currentPlayerMediaPeriod = findCurrentPlayerMediaPeriodInQueue(player, mediaPeriodQueue, playingMediaPeriod, period)
        }

        /** Updates the queue tracker with a reported timeline change.  */
        fun onTimelineChanged(player: Player) {
            currentPlayerMediaPeriod = findCurrentPlayerMediaPeriodInQueue(player, mediaPeriodQueue, playingMediaPeriod, period)
            updateMediaPeriodTimelines( /* preferredTimeline= */player.getCurrentTimeline())
        }

        /** Updates the queue tracker to a new queue of media periods.  */
        fun onQueueUpdated(queue: List<MediaSource.MediaPeriodId?>, readingPeriod: MediaSource.MediaPeriodId?, player: Player) {
            mediaPeriodQueue = ImmutableList.copyOf<MediaSource.MediaPeriodId>(queue)
            if (!queue.isEmpty()) {
                playingMediaPeriod = queue[0]
                readingMediaPeriod = checkNotNull(readingPeriod)
            }
            if (currentPlayerMediaPeriod == null) {
                currentPlayerMediaPeriod = findCurrentPlayerMediaPeriodInQueue(player, mediaPeriodQueue, playingMediaPeriod, period)
            }
            updateMediaPeriodTimelines( /* preferredTimeline= */player.getCurrentTimeline())
        }

        private fun updateMediaPeriodTimelines(preferredTimeline: Timeline) {
            val builder: ImmutableMap.Builder<MediaSource.MediaPeriodId, Timeline?> = ImmutableMap.builder<MediaSource.MediaPeriodId, Timeline?>()
            if (mediaPeriodQueue.isEmpty()) {
                addTimelineForMediaPeriodId(builder, playingMediaPeriod, preferredTimeline)
                if (!Objects.equal(readingMediaPeriod, playingMediaPeriod)) {
                    addTimelineForMediaPeriodId(builder, readingMediaPeriod, preferredTimeline)
                }
                if (!Objects.equal(currentPlayerMediaPeriod, playingMediaPeriod) && !Objects.equal(currentPlayerMediaPeriod, readingMediaPeriod)) {
                    addTimelineForMediaPeriodId(builder, currentPlayerMediaPeriod, preferredTimeline)
                }
            } else {
                for (i in mediaPeriodQueue.indices) {
                    addTimelineForMediaPeriodId(builder, mediaPeriodQueue.get(i), preferredTimeline)
                }
                if (!mediaPeriodQueue.contains(currentPlayerMediaPeriod)) {
                    addTimelineForMediaPeriodId(builder, currentPlayerMediaPeriod, preferredTimeline)
                }
            }
            mediaPeriodTimelines = builder.buildOrThrow()
        }

        private fun addTimelineForMediaPeriodId(mediaPeriodTimelinesBuilder: ImmutableMap.Builder<MediaSource.MediaPeriodId, Timeline?>, mediaPeriodId: MediaSource.MediaPeriodId?, preferredTimeline: Timeline) {
            if (mediaPeriodId == null) {
                return
            }
            if (preferredTimeline.getIndexOfPeriod(mediaPeriodId.periodUid) != C.INDEX_UNSET) {
                mediaPeriodTimelinesBuilder.put(mediaPeriodId, preferredTimeline)
            } else {
                val existingTimeline: Timeline? = mediaPeriodTimelines[mediaPeriodId]
                if (existingTimeline != null) {
                    mediaPeriodTimelinesBuilder.put(mediaPeriodId, existingTimeline)
                }
            }
        }

        companion object {
            private fun findCurrentPlayerMediaPeriodInQueue(player: Player, mediaPeriodQueue: ImmutableList<MediaSource.MediaPeriodId>, playingMediaPeriod: MediaSource.MediaPeriodId?, period: Timeline.Period): MediaSource.MediaPeriodId? {
                val playerTimeline: Timeline = player.getCurrentTimeline()
                val playerPeriodIndex: Int = player.getCurrentPeriodIndex()
                val playerPeriodUid: Any? = if (playerTimeline.isEmpty) null else playerTimeline.getUidOfPeriod(playerPeriodIndex)
                val playerNextAdGroupIndex = if (player.isPlayingAd() || playerTimeline.isEmpty) C.INDEX_UNSET else playerTimeline.getPeriod(playerPeriodIndex, period).getAdGroupIndexAfterPositionUs(Util.msToUs(player.getCurrentPosition()) - period.positionInWindowUs)
                for (i in mediaPeriodQueue.indices) {
                    val mediaPeriodId: MediaSource.MediaPeriodId = mediaPeriodQueue.get(i)
                    if (isMatchingMediaPeriod(mediaPeriodId, playerPeriodUid, player.isPlayingAd(), player.getCurrentAdGroupIndex(), player.getCurrentAdIndexInAdGroup(), playerNextAdGroupIndex)) {
                        return mediaPeriodId
                    }
                }
                if (mediaPeriodQueue.isEmpty() && playingMediaPeriod != null) {
                    if (isMatchingMediaPeriod(playingMediaPeriod, playerPeriodUid, player.isPlayingAd(), player.getCurrentAdGroupIndex(), player.getCurrentAdIndexInAdGroup(), playerNextAdGroupIndex)) {
                        return playingMediaPeriod
                    }
                }
                return null
            }

            private fun isMatchingMediaPeriod(mediaPeriodId: MediaSource.MediaPeriodId, playerPeriodUid: Any?, isPlayingAd: Boolean, playerAdGroupIndex: Int, playerAdIndexInAdGroup: Int, playerNextAdGroupIndex: Int): Boolean {
                return if (mediaPeriodId.periodUid != playerPeriodUid) {
                    false
                } else (isPlayingAd && mediaPeriodId.adGroupIndex == playerAdGroupIndex && mediaPeriodId.adIndexInAdGroup == playerAdIndexInAdGroup || !isPlayingAd && mediaPeriodId.adGroupIndex == C.INDEX_UNSET && mediaPeriodId.nextAdGroupIndex) == playerNextAdGroupIndex
                // Timeline period matches. Still need to check ad information.
            }
        }
    }
}