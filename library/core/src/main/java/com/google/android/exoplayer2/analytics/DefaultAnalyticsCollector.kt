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
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.drm.DrmSession
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.HandlerWrapper
import com.google.android.exoplayer2.util.ListenerSet
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoSize
import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import java.io.IOException

/**
 * Data collector that forwards analytics events to [AnalyticsListeners][AnalyticsListener].
 */
open class DefaultAnalyticsCollector(clock: Clock) : AnalyticsCollector {

  private val clock: Clock = Assertions.checkNotNull(clock)!!
  private val period: Timeline.Period = Timeline.Period()
  private val window: Timeline.Window = Timeline.Window()
  private val mediaPeriodQueueTracker: MediaPeriodQueueTracker = MediaPeriodQueueTracker(period)
  private val eventTimes: SparseArray<EventTime> = SparseArray()

  private var listeners: ListenerSet<AnalyticsListener> =
      ListenerSet(Util.currentOrMainLooper, clock) { _, _ -> }
  private var player: Player? = null
  private var handler: HandlerWrapper? = null
  private var isSeeking = false

  @CallSuper
  override fun addListener(listener: AnalyticsListener?) {
    Assertions.checkNotNull(listener)
    listeners.add(listener!!)
  }

  @CallSuper
  override fun removeListener(listener: AnalyticsListener?) {
    listeners.remove(Assertions.checkNotNull(listener)!!)
  }

  @CallSuper
  override fun setPlayer(player: Player?, looper: Looper?) {
    Assertions.checkState((this.player == null) || mediaPeriodQueueTracker.isQueueEmpty)
    this.player = Assertions.checkNotNull(player)
    handler = clock.createHandler(looper!!, null)
    @Suppress("UNCHECKED_CAST")
    listeners =
        listeners.copy(looper) { listener, flags ->
          listener.onEvents(this.player!!, AnalyticsListener.Events(flags!!, eventTimes as SparseArray<EventTime?>))
        }
  }

  @CallSuper
  override fun release() {
    // Release lazily so that all events that got triggered as part of player.release()
    // are still delivered to all listeners and onPlayerReleased() is delivered last.
    Assertions.checkStateNotNull(handler)!!.post { releaseInternal() }
  }

  @Suppress("UNCHECKED_CAST")
  override fun updateMediaPeriodQueueInfo(
      queue: List<MediaPeriodId?>?, readingPeriod: MediaPeriodId?,
  ) {
    mediaPeriodQueueTracker.onQueueUpdated(
        queue as List<MediaPeriodId>, readingPeriod, Assertions.checkNotNull(player)!!
    )
  }

  // External events.

  override fun notifySeekStarted() {
    if (!isSeeking) {
      val eventTime = generateCurrentPlayerMediaPeriodEventTime()
      isSeeking = true
      sendEvent(eventTime, C.INDEX_UNSET) { listener -> listener.onSeekStarted(eventTime) }
    }
  }

  // Audio events.

  override fun onAudioEnabled(counters: DecoderCounters?) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_ENABLED) { listener ->
      listener.onAudioEnabled(eventTime, counters)
      listener.onDecoderEnabled(eventTime, C.TRACK_TYPE_AUDIO, counters)
    }
  }

  override fun onAudioDecoderInitialized(
      decoderName: String?, initializedTimestampMs: Long, initializationDurationMs: Long
  ) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_DECODER_INITIALIZED) { listener ->
      listener.onAudioDecoderInitialized(eventTime, decoderName!!, initializationDurationMs)
      listener.onAudioDecoderInitialized(
          eventTime, decoderName, initializedTimestampMs, initializationDurationMs
      )
      listener.onDecoderInitialized(
          eventTime, C.TRACK_TYPE_AUDIO, decoderName, initializationDurationMs
      )
    }
  }

  override fun onAudioInputFormatChanged(
      format: Format?, decoderReuseEvaluation: DecoderReuseEvaluation?
  ) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_INPUT_FORMAT_CHANGED) { listener ->
      listener.onAudioInputFormatChanged(eventTime, format)
      listener.onAudioInputFormatChanged(eventTime, format, decoderReuseEvaluation)
      listener.onDecoderInputFormatChanged(eventTime, C.TRACK_TYPE_AUDIO, format)
    }
  }

  override fun onAudioPositionAdvancing(playoutStartSystemTimeMs: Long) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_POSITION_ADVANCING) { listener ->
      listener.onAudioPositionAdvancing(eventTime, playoutStartSystemTimeMs)
    }
  }

  override fun onAudioUnderrun(
      bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long
  ) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_UNDERRUN) { listener ->
      listener.onAudioUnderrun(eventTime, bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)
    }
  }

  override fun onAudioDecoderReleased(decoderName: String?) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_DECODER_RELEASED) { listener ->
      listener.onAudioDecoderReleased(eventTime, decoderName!!)
    }
  }

  override fun onAudioDisabled(counters: DecoderCounters?) {
    val eventTime = generatePlayingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_DISABLED) { listener ->
      listener.onAudioDisabled(eventTime, counters)
      listener.onDecoderDisabled(eventTime, C.TRACK_TYPE_AUDIO, counters)
    }
  }

  override fun onAudioSinkError(audioSinkError: Exception?) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_SINK_ERROR) { listener ->
      listener.onAudioSinkError(eventTime, audioSinkError!!)
    }
  }

  override fun onAudioCodecError(audioCodecError: Exception?) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_CODEC_ERROR) { listener ->
      listener.onAudioCodecError(eventTime, audioCodecError!!)
    }
  }

  override fun onVolumeChanged(volume: Float) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_VOLUME_CHANGED) { listener ->
      listener.onVolumeChanged(eventTime, volume)
    }
  }

  // Video events.

  override fun onVideoEnabled(counters: DecoderCounters?) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_ENABLED) { listener ->
      listener.onVideoEnabled(eventTime, counters)
      listener.onDecoderEnabled(eventTime, C.TRACK_TYPE_VIDEO, counters)
    }
  }

  override fun onVideoDecoderInitialized(
      decoderName: String?, initializedTimestampMs: Long, initializationDurationMs: Long
  ) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_DECODER_INITIALIZED) { listener ->
      listener.onVideoDecoderInitialized(eventTime, decoderName!!, initializationDurationMs)
      listener.onVideoDecoderInitialized(
          eventTime, decoderName, initializedTimestampMs, initializationDurationMs
      )
      listener.onDecoderInitialized(
          eventTime, C.TRACK_TYPE_VIDEO, decoderName, initializationDurationMs
      )
    }
  }

  override fun onVideoInputFormatChanged(
      format: Format?, decoderReuseEvaluation: DecoderReuseEvaluation?
  ) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_INPUT_FORMAT_CHANGED) { listener ->
      listener.onVideoInputFormatChanged(eventTime, format)
      listener.onVideoInputFormatChanged(eventTime, format, decoderReuseEvaluation)
      listener.onDecoderInputFormatChanged(eventTime, C.TRACK_TYPE_VIDEO, format)
    }
  }

  override fun onDroppedFrames(count: Int, elapsedMs: Long) {
    val eventTime = generatePlayingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_DROPPED_VIDEO_FRAMES) { listener ->
      listener.onDroppedVideoFrames(eventTime, count, elapsedMs)
    }
  }

  override fun onVideoDecoderReleased(decoderName: String?) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_DECODER_RELEASED) { listener ->
      listener.onVideoDecoderReleased(eventTime, decoderName!!)
    }
  }

  override fun onVideoDisabled(counters: DecoderCounters?) {
    val eventTime = generatePlayingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_DISABLED) { listener ->
      listener.onVideoDisabled(eventTime, counters)
      listener.onDecoderDisabled(eventTime, C.TRACK_TYPE_VIDEO, counters)
    }
  }

  override fun onRenderedFirstFrame(output: Any?, renderTimeMs: Long) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_RENDERED_FIRST_FRAME) { listener ->
      listener.onRenderedFirstFrame(eventTime, output!!, renderTimeMs)
    }
  }

  override fun onVideoFrameProcessingOffset(totalProcessingOffsetUs: Long, frameCount: Int) {
    val eventTime = generatePlayingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_FRAME_PROCESSING_OFFSET) { listener ->
      listener.onVideoFrameProcessingOffset(eventTime, totalProcessingOffsetUs, frameCount)
    }
  }

  override fun onVideoCodecError(videoCodecError: Exception?) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_CODEC_ERROR) { listener ->
      listener.onVideoCodecError(eventTime, videoCodecError!!)
    }
  }

  override fun onSurfaceSizeChanged(width: Int, height: Int) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_SURFACE_SIZE_CHANGED) { listener ->
      listener.onSurfaceSizeChanged(eventTime, width, height)
    }
  }

  // MediaSourceEventListener implementation.

  override fun onLoadStarted(
      windowIndex: Int,
      mediaPeriodId: MediaPeriodId?,
      loadEventInfo: LoadEventInfo?,
      mediaLoadData: MediaLoadData?
  ) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_LOAD_STARTED) { listener ->
      listener.onLoadStarted(eventTime, loadEventInfo!!, mediaLoadData!!)
    }
  }

  override fun onLoadCompleted(
      windowIndex: Int,
      mediaPeriodId: MediaPeriodId?,
      loadEventInfo: LoadEventInfo?,
      mediaLoadData: MediaLoadData?
  ) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_LOAD_COMPLETED) { listener ->
      listener.onLoadCompleted(eventTime, loadEventInfo!!, mediaLoadData!!)
    }
  }

  override fun onLoadCanceled(
      windowIndex: Int,
      mediaPeriodId: MediaPeriodId?,
      loadEventInfo: LoadEventInfo?,
      mediaLoadData: MediaLoadData?
  ) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_LOAD_CANCELED) { listener ->
      listener.onLoadCanceled(eventTime, loadEventInfo!!, mediaLoadData!!)
    }
  }

  override fun onLoadError(
      windowIndex: Int,
      mediaPeriodId: MediaPeriodId?,
      loadEventInfo: LoadEventInfo?,
      mediaLoadData: MediaLoadData?,
      error: IOException?,
      wasCanceled: Boolean
  ) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_LOAD_ERROR) { listener ->
      listener.onLoadError(eventTime, loadEventInfo!!, mediaLoadData!!, error!!, wasCanceled)
    }
  }

  override fun onUpstreamDiscarded(
      windowIndex: Int, mediaPeriodId: MediaPeriodId?, mediaLoadData: MediaLoadData?
  ) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_UPSTREAM_DISCARDED) { listener ->
      listener.onUpstreamDiscarded(eventTime, mediaLoadData!!)
    }
  }

  override fun onDownstreamFormatChanged(
      windowIndex: Int, mediaPeriodId: MediaPeriodId?, mediaLoadData: MediaLoadData?
  ) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_DOWNSTREAM_FORMAT_CHANGED) { listener ->
      listener.onDownstreamFormatChanged(eventTime, mediaLoadData!!)
    }
  }

  // Player.Listener implementation.

  override fun onTimelineChanged(timeline: Timeline?, reason: Int) {
    mediaPeriodQueueTracker.onTimelineChanged(Assertions.checkNotNull(player)!!)
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_TIMELINE_CHANGED) { listener ->
      listener.onTimelineChanged(eventTime, reason)
    }
  }

  override fun onMediaItemTransition(
      mediaItem: MediaItem?, reason: Int
  ) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_MEDIA_ITEM_TRANSITION) { listener ->
      listener.onMediaItemTransition(eventTime, mediaItem, reason)
    }
  }

  override fun onTracksChanged(tracks: Tracks?) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_TRACKS_CHANGED) { listener ->
      listener.onTracksChanged(eventTime, tracks!!)
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onLoadingChanged(isLoading: Boolean) {
    // Do nothing. Handled by non-deprecated onIsLoadingChanged.
  }

  override fun onIsLoadingChanged(isLoading: Boolean) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_IS_LOADING_CHANGED) { listener ->
      listener.onLoadingChanged(eventTime, isLoading)
      listener.onIsLoadingChanged(eventTime, isLoading)
    }
  }

  override fun onAvailableCommandsChanged(availableCommands: Player.Commands?) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AVAILABLE_COMMANDS_CHANGED) { listener ->
      listener.onAvailableCommandsChanged(eventTime, availableCommands!!)
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, C.INDEX_UNSET) { listener ->
      listener.onPlayerStateChanged(eventTime, playWhenReady, playbackState)
    }
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED) { listener ->
      listener.onPlaybackStateChanged(eventTime, playbackState)
    }
  }

  override fun onPlayWhenReadyChanged(
      playWhenReady: Boolean, reason: Int
  ) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_PLAY_WHEN_READY_CHANGED) { listener ->
      listener.onPlayWhenReadyChanged(eventTime, playWhenReady, reason)
    }
  }

  override fun onPlaybackSuppressionReasonChanged(
      playbackSuppressionReason: Int
  ) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(
        eventTime, AnalyticsListener.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED
    ) { listener ->
      listener.onPlaybackSuppressionReasonChanged(eventTime, playbackSuppressionReason)
    }
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_IS_PLAYING_CHANGED) { listener ->
      listener.onIsPlayingChanged(eventTime, isPlaying)
    }
  }

  override fun onRepeatModeChanged(repeatMode: Int) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_REPEAT_MODE_CHANGED) { listener ->
      listener.onRepeatModeChanged(eventTime, repeatMode)
    }
  }

  override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) { listener ->
      listener.onShuffleModeChanged(eventTime, shuffleModeEnabled)
    }
  }

  override fun onPlayerError(error: PlaybackException?) {
    val eventTime = getEventTimeForErrorEvent(error)
    sendEvent(eventTime, AnalyticsListener.EVENT_PLAYER_ERROR) { listener ->
      listener.onPlayerError(eventTime, error!!)
    }
  }

  override fun onPlayerErrorChanged(error: PlaybackException?) {
    val eventTime = getEventTimeForErrorEvent(error)
    sendEvent(eventTime, AnalyticsListener.EVENT_PLAYER_ERROR) { listener ->
      listener.onPlayerErrorChanged(eventTime, error)
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onPositionDiscontinuity(reason: Int) {
    // Do nothing. Handled by non-deprecated onPositionDiscontinuity.
  }

  override fun onPositionDiscontinuity(
      oldPosition: Player.PositionInfo?,
      newPosition: Player.PositionInfo?,
      reason: Int
  ) {
    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
      isSeeking = false
    }
    mediaPeriodQueueTracker.onPositionDiscontinuity(Assertions.checkNotNull(player)!!)
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_POSITION_DISCONTINUITY) { listener ->
      listener.onPositionDiscontinuity(eventTime, reason)
      listener.onPositionDiscontinuity(eventTime, oldPosition!!, newPosition!!, reason)
    }
  }

  override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_PLAYBACK_PARAMETERS_CHANGED) { listener ->
      listener.onPlaybackParametersChanged(eventTime, playbackParameters!!)
    }
  }

  override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_SEEK_BACK_INCREMENT_CHANGED) { listener ->
      listener.onSeekBackIncrementChanged(eventTime, seekBackIncrementMs)
    }
  }

  override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_SEEK_FORWARD_INCREMENT_CHANGED) { listener ->
      listener.onSeekForwardIncrementChanged(eventTime, seekForwardIncrementMs)
    }
  }

  override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(
        eventTime, AnalyticsListener.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED
    ) { listener ->
      listener.onMaxSeekToPreviousPositionChanged(eventTime, maxSeekToPreviousPositionMs)
    }
  }

  override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata?) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_MEDIA_METADATA_CHANGED) { listener ->
      listener.onMediaMetadataChanged(eventTime, mediaMetadata!!)
    }
  }

  override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata?) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_PLAYLIST_METADATA_CHANGED) { listener ->
      listener.onPlaylistMetadataChanged(eventTime, mediaMetadata!!)
    }
  }

  override fun onMetadata(metadata: kotlin.Metadata?) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_METADATA) { listener ->
      listener.onMetadata(eventTime, metadata!!)
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onCues(cues: List<Cue?>?) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_CUES) { listener ->
      listener.onCues(eventTime, cues)
    }
  }

  override fun onCues(cueGroup: CueGroup?) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_CUES) { listener ->
      listener.onCues(eventTime, cueGroup!!)
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onSeekProcessed() {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, C.INDEX_UNSET) { listener -> listener.onSeekProcessed(eventTime) }
  }

  override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_SKIP_SILENCE_ENABLED_CHANGED) { listener ->
      listener.onSkipSilenceEnabledChanged(eventTime, skipSilenceEnabled)
    }
  }

  override fun onAudioSessionIdChanged(audioSessionId: Int) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_SESSION_ID) { listener ->
      listener.onAudioSessionIdChanged(eventTime, audioSessionId)
    }
  }

  override fun onAudioAttributesChanged(audioAttributes: AudioAttributes?) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_AUDIO_ATTRIBUTES_CHANGED) { listener ->
      listener.onAudioAttributesChanged(eventTime, audioAttributes!!)
    }
  }

  override fun onVideoSizeChanged(videoSize: VideoSize?) {
    val eventTime = generateReadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_VIDEO_SIZE_CHANGED) { listener ->
      listener.onVideoSizeChanged(eventTime, videoSize!!)
      listener.onVideoSizeChanged(
          eventTime,
          videoSize.width,
          videoSize.height,
          videoSize.unappliedRotationDegrees,
          videoSize.pixelWidthHeightRatio
      )
    }
  }

  override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters?) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(
        eventTime, AnalyticsListener.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED
    ) { listener ->
      listener.onTrackSelectionParametersChanged(eventTime, parameters!!)
    }
  }

  override fun onDeviceInfoChanged(deviceInfo: DeviceInfo?) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_DEVICE_INFO_CHANGED) { listener ->
      listener.onDeviceInfoChanged(eventTime, deviceInfo!!)
    }
  }

  override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_DEVICE_VOLUME_CHANGED) { listener ->
      listener.onDeviceVolumeChanged(eventTime, volume, muted)
    }
  }

  override fun onRenderedFirstFrame() {
    // Do nothing. Handled by onRenderedFirstFrame call with additional parameters.
  }

  override fun onEvents(player: Player?, events: Player.Events?) {
    // Do nothing. AnalyticsCollector issues its own onEvents.
  }

  // BandwidthMeter.EventListener implementation.

  override fun onBandwidthSample(elapsedMs: Int, bytesTransferred: Long, bitrateEstimate: Long) {
    val eventTime = generateLoadingMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_BANDWIDTH_ESTIMATE) { listener ->
      listener.onBandwidthEstimate(eventTime, elapsedMs, bytesTransferred, bitrateEstimate)
    }
  }

  // DrmSessionEventListener implementation.

  override fun onDrmSessionAcquired(
      windowIndex: Int, mediaPeriodId: MediaPeriodId?, state: Int
  ) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_DRM_SESSION_ACQUIRED) { listener ->
      listener.onDrmSessionAcquired(eventTime)
      listener.onDrmSessionAcquired(eventTime, state)
    }
  }

  override fun onDrmKeysLoaded(windowIndex: Int, mediaPeriodId: MediaPeriodId?) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_DRM_KEYS_LOADED) { listener ->
      listener.onDrmKeysLoaded(eventTime)
    }
  }

  override fun onDrmSessionManagerError(
      windowIndex: Int, mediaPeriodId: MediaPeriodId?, error: Exception?
  ) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_DRM_SESSION_MANAGER_ERROR) { listener ->
      listener.onDrmSessionManagerError(eventTime, error!!)
    }
  }

  override fun onDrmKeysRestored(windowIndex: Int, mediaPeriodId: MediaPeriodId?) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_DRM_KEYS_RESTORED) { listener ->
      listener.onDrmKeysRestored(eventTime)
    }
  }

  override fun onDrmKeysRemoved(windowIndex: Int, mediaPeriodId: MediaPeriodId?) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_DRM_KEYS_REMOVED) { listener ->
      listener.onDrmKeysRemoved(eventTime)
    }
  }

  override fun onDrmSessionReleased(windowIndex: Int, mediaPeriodId: MediaPeriodId?) {
    val eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId)
    sendEvent(eventTime, AnalyticsListener.EVENT_DRM_SESSION_RELEASED) { listener ->
      listener.onDrmSessionReleased(eventTime)
    }
  }

  // Internal methods.

  /**
   * Sends an event to registered listeners.
   *
   * @param eventTime The [EventTime] to report.
   * @param eventFlag An integer flag indicating the type of the event, or [C.INDEX_UNSET] to
   *     report this event without flag.
   * @param eventInvocation The event.
   */
  protected fun sendEvent(
      eventTime: EventTime,
      eventFlag: Int,
      eventInvocation: ListenerSet.Event<AnalyticsListener>
  ) {
    eventTimes.put(eventFlag, eventTime)
    listeners.sendEvent(eventFlag, eventInvocation)
  }

  /** Generates an [EventTime] for the currently playing item in the player. */
  protected fun generateCurrentPlayerMediaPeriodEventTime(): EventTime {
    return generateEventTime(mediaPeriodQueueTracker.currentPlayerMediaPeriod)
  }

  /** Returns a new [EventTime] for the specified timeline, window and media period id. */
  protected fun generateEventTime(
      timeline: Timeline, windowIndex: Int, mediaPeriodId: MediaPeriodId?
  ): EventTime {
    var mediaPeriodIdParam = mediaPeriodId
    if (timeline.isEmpty) {
      // Ensure media period id is only reported together with a valid timeline.
      mediaPeriodIdParam = null
    }
    val player = Assertions.checkNotNull(this.player)!!
    val realtimeMs = clock.elapsedRealtime()
    val eventPositionMs: Long
    val isInCurrentWindow =
        timeline == player.getCurrentTimeline() && windowIndex == player.getCurrentMediaItemIndex()
    if (mediaPeriodIdParam != null && mediaPeriodIdParam.isAd) {
      val isCurrentAd =
          isInCurrentWindow &&
              player.getCurrentAdGroupIndex() == mediaPeriodIdParam.adGroupIndex &&
              player.getCurrentAdIndexInAdGroup() == mediaPeriodIdParam.adIndexInAdGroup
      // Assume start position of 0 for future ads.
      eventPositionMs = if (isCurrentAd) player.getCurrentPosition() else 0
    } else if (isInCurrentWindow) {
      eventPositionMs = player.getContentPosition()
    } else {
      // Assume default start position for future content windows. If timeline is not available yet,
      // assume start position of 0.
      eventPositionMs =
          if (timeline.isEmpty) 0 else timeline.getWindow(windowIndex, window).defaultPositionMs
    }
    val currentMediaPeriodId = mediaPeriodQueueTracker.currentPlayerMediaPeriod
    return EventTime(
        realtimeMs,
        timeline,
        windowIndex,
        mediaPeriodIdParam,
        eventPositionMs,
        player.getCurrentTimeline(),
        player.getCurrentMediaItemIndex(),
        currentMediaPeriodId,
        player.getCurrentPosition(),
        player.getTotalBufferedDuration()
    )
  }

  private fun releaseInternal() {
    val eventTime = generateCurrentPlayerMediaPeriodEventTime()
    sendEvent(eventTime, AnalyticsListener.EVENT_PLAYER_RELEASED) { listener ->
      listener.onPlayerReleased(eventTime)
    }
    listeners.release()
  }

  private fun generateEventTime(mediaPeriodId: MediaPeriodId?): EventTime {
    val player = Assertions.checkNotNull(this.player)!!
    val knownTimeline =
        if (mediaPeriodId == null) null
        else mediaPeriodQueueTracker.getMediaPeriodIdTimeline(mediaPeriodId)
    if (mediaPeriodId == null || knownTimeline == null) {
      val windowIndex = player.getCurrentMediaItemIndex()
      val timeline = player.getCurrentTimeline() ?: Timeline.EMPTY
      val windowIsInTimeline = windowIndex < timeline.windowCount
      return generateEventTime(
          if (windowIsInTimeline) timeline else Timeline.EMPTY, windowIndex, /* mediaPeriodId= */ null
      )
    }
    val windowIndex = knownTimeline.getPeriodByUid(mediaPeriodId.periodUid, period)!!.windowIndex
    return generateEventTime(knownTimeline, windowIndex, mediaPeriodId)
  }

  private fun generatePlayingMediaPeriodEventTime(): EventTime {
    return generateEventTime(mediaPeriodQueueTracker.playingMediaPeriod)
  }

  private fun generateReadingMediaPeriodEventTime(): EventTime {
    return generateEventTime(mediaPeriodQueueTracker.readingMediaPeriod)
  }

  private fun generateLoadingMediaPeriodEventTime(): EventTime {
    return generateEventTime(mediaPeriodQueueTracker.loadingMediaPeriod)
  }

  private fun generateMediaPeriodEventTime(
      windowIndex: Int, mediaPeriodId: MediaPeriodId?
  ): EventTime {
    val player = Assertions.checkNotNull(this.player)!!
    if (mediaPeriodId != null) {
      val isInKnownTimeline =
          mediaPeriodQueueTracker.getMediaPeriodIdTimeline(mediaPeriodId) != null
      return if (isInKnownTimeline) {
        generateEventTime(mediaPeriodId)
      } else {
        generateEventTime(Timeline.EMPTY, windowIndex, mediaPeriodId)
      }
    }
    val timeline = player.getCurrentTimeline() ?: Timeline.EMPTY
    val windowIsInTimeline = windowIndex < timeline.windowCount
    return generateEventTime(
        if (windowIsInTimeline) timeline else Timeline.EMPTY, windowIndex, /* mediaPeriodId= */ null
    )
  }

  private fun getEventTimeForErrorEvent(error: PlaybackException?): EventTime {
    if (error is ExoPlaybackException) {
      if (error.mediaPeriodId != null) {
        return generateEventTime(MediaPeriodId(error.mediaPeriodId!!))
      }
    }
    return generateCurrentPlayerMediaPeriodEventTime()
  }

  /** Keeps track of the active media periods and currently playing and reading media period. */
  private class MediaPeriodQueueTracker(private val period: Timeline.Period) {

    private var mediaPeriodQueue: ImmutableList<MediaPeriodId> = ImmutableList.of()
    private var mediaPeriodTimelines: ImmutableMap<MediaPeriodId, Timeline> = ImmutableMap.of()
    var currentPlayerMediaPeriod: MediaPeriodId? = null
      private set
    var playingMediaPeriod: MediaPeriodId? = null
      private set
    var readingMediaPeriod: MediaPeriodId? = null
      private set

    val isQueueEmpty: Boolean
      get() = mediaPeriodQueue.isEmpty()

    val loadingMediaPeriod: MediaPeriodId?
      get() = if (mediaPeriodQueue.isEmpty()) null else Iterables.getLast(mediaPeriodQueue)

    fun getMediaPeriodIdTimeline(mediaPeriodId: MediaPeriodId): Timeline? {
      return mediaPeriodTimelines[mediaPeriodId]
    }

    fun onPositionDiscontinuity(player: Player) {
      currentPlayerMediaPeriod =
          findCurrentPlayerMediaPeriodInQueue(player, mediaPeriodQueue, playingMediaPeriod, period)
    }

    fun onTimelineChanged(player: Player) {
      currentPlayerMediaPeriod =
          findCurrentPlayerMediaPeriodInQueue(player, mediaPeriodQueue, playingMediaPeriod, period)
      updateMediaPeriodTimelines(/* preferredTimeline= */ player.getCurrentTimeline() ?: Timeline.EMPTY)
    }

    fun onQueueUpdated(
        queue: List<MediaPeriodId>, readingPeriod: MediaPeriodId?, player: Player
    ) {
      mediaPeriodQueue = ImmutableList.copyOf(queue)
      if (queue.isNotEmpty()) {
        playingMediaPeriod = queue[0]
        readingMediaPeriod = Assertions.checkNotNull(readingPeriod)
      }
      if (currentPlayerMediaPeriod == null) {
        currentPlayerMediaPeriod =
            findCurrentPlayerMediaPeriodInQueue(
                player, mediaPeriodQueue, playingMediaPeriod, period
            )
      }
      updateMediaPeriodTimelines(/* preferredTimeline= */ player.getCurrentTimeline() ?: Timeline.EMPTY)
    }

    private fun updateMediaPeriodTimelines(preferredTimeline: Timeline) {
      val builder = ImmutableMap.builder<MediaPeriodId, Timeline>()
      if (mediaPeriodQueue.isEmpty()) {
        addTimelineForMediaPeriodId(builder, playingMediaPeriod, preferredTimeline)
        if (!Objects.equal(readingMediaPeriod, playingMediaPeriod)) {
          addTimelineForMediaPeriodId(builder, readingMediaPeriod, preferredTimeline)
        }
        if (!Objects.equal(currentPlayerMediaPeriod, playingMediaPeriod) &&
            !Objects.equal(currentPlayerMediaPeriod, readingMediaPeriod)
        ) {
          addTimelineForMediaPeriodId(builder, currentPlayerMediaPeriod, preferredTimeline)
        }
      } else {
        for (i in 0 until mediaPeriodQueue.size) {
          addTimelineForMediaPeriodId(builder, mediaPeriodQueue[i], preferredTimeline)
        }
        if (!mediaPeriodQueue.contains(currentPlayerMediaPeriod)) {
          addTimelineForMediaPeriodId(builder, currentPlayerMediaPeriod, preferredTimeline)
        }
      }
      mediaPeriodTimelines = builder.buildOrThrow()
    }

    private fun addTimelineForMediaPeriodId(
        mediaPeriodTimelinesBuilder: ImmutableMap.Builder<MediaPeriodId, Timeline>,
        mediaPeriodId: MediaPeriodId?,
        preferredTimeline: Timeline
    ) {
      if (mediaPeriodId == null) {
        return
      }
      if (preferredTimeline.getIndexOfPeriod(mediaPeriodId.periodUid) != C.INDEX_UNSET) {
        mediaPeriodTimelinesBuilder.put(mediaPeriodId, preferredTimeline)
      } else {
        val existingTimeline = mediaPeriodTimelines[mediaPeriodId]
        if (existingTimeline != null) {
          mediaPeriodTimelinesBuilder.put(mediaPeriodId, existingTimeline)
        }
      }
    }

    companion object {
      private fun findCurrentPlayerMediaPeriodInQueue(
          player: Player,
          mediaPeriodQueue: ImmutableList<MediaPeriodId>,
          playingMediaPeriod: MediaPeriodId?,
          period: Timeline.Period
      ): MediaPeriodId? {
        val playerTimeline = player.getCurrentTimeline() ?: Timeline.EMPTY
        val playerPeriodIndex = player.getCurrentPeriodIndex()
        val playerPeriodUid =
            if (playerTimeline.isEmpty) null else playerTimeline.getUidOfPeriod(playerPeriodIndex)
        val playerNextAdGroupIndex =
            if (player.isPlayingAd() || playerTimeline.isEmpty) {
              C.INDEX_UNSET
            } else {
              playerTimeline
                  .getPeriod(playerPeriodIndex, period)
                  .getAdGroupIndexAfterPositionUs(
                      Util.msToUs(player.getCurrentPosition()) - period.positionInWindowUs
                  )
            }
        for (i in 0 until mediaPeriodQueue.size) {
          val mediaPeriodId = mediaPeriodQueue[i]
          if (isMatchingMediaPeriod(
                  mediaPeriodId,
                  playerPeriodUid,
                  player.isPlayingAd(),
                  player.getCurrentAdGroupIndex(),
                  player.getCurrentAdIndexInAdGroup(),
                  playerNextAdGroupIndex
              )
          ) {
            return mediaPeriodId
          }
        }
        if (mediaPeriodQueue.isEmpty() && playingMediaPeriod != null) {
          if (isMatchingMediaPeriod(
                  playingMediaPeriod,
                  playerPeriodUid,
                  player.isPlayingAd(),
                  player.getCurrentAdGroupIndex(),
                  player.getCurrentAdIndexInAdGroup(),
                  playerNextAdGroupIndex
              )
          ) {
            return playingMediaPeriod
          }
        }
        return null
      }

      private fun isMatchingMediaPeriod(
          mediaPeriodId: MediaPeriodId,
          playerPeriodUid: Any?,
          isPlayingAd: Boolean,
          playerAdGroupIndex: Int,
          playerAdIndexInAdGroup: Int,
          playerNextAdGroupIndex: Int
      ): Boolean {
        if (mediaPeriodId.periodUid != playerPeriodUid) {
          return false
        }
        // Timeline period matches. Still need to check ad information.
        return (isPlayingAd &&
            mediaPeriodId.adGroupIndex == playerAdGroupIndex &&
            mediaPeriodId.adIndexInAdGroup == playerAdIndexInAdGroup) ||
            (!isPlayingAd &&
                mediaPeriodId.adGroupIndex == C.INDEX_UNSET &&
                mediaPeriodId.nextAdGroupIndex == playerNextAdGroupIndex)
      }
    }
  }
}
