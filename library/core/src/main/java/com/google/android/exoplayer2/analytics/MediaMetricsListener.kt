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
package com.google.android.exoplayer2.analytics

import android.annotation.SuppressLint
import android.content.Context
import android.media.DeniedByServerException
import android.media.MediaCodec
import android.media.MediaDrm
import android.media.MediaDrmResetException
import android.media.NotProvisionedException
import android.media.metrics.LogSessionId
import android.media.metrics.MediaMetricsManager
import android.media.metrics.NetworkEvent
import android.media.metrics.PlaybackErrorEvent
import android.media.metrics.PlaybackMetrics
import android.media.metrics.PlaybackSession
import android.media.metrics.PlaybackStateEvent
import android.media.metrics.TrackChangeEvent
import android.os.SystemClock
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Pair
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.ContentType
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ParserException
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager
import com.google.android.exoplayer2.drm.DrmInitData
import com.google.android.exoplayer2.drm.DrmSession
import com.google.android.exoplayer2.drm.UnsupportedDrmException
import com.google.android.exoplayer2.mediacodec.MediaCodecDecoderException
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.UdpDataSource
import com.google.android.exoplayer2.util.NetworkTypeObserver
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoSize
import com.google.common.collect.ImmutableList
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.HashMap

/**
 * An [AnalyticsListener] that interacts with the Android [MediaMetricsManager].
 *
 * It listens to playback events and forwards them to a [PlaybackSession]. The [LogSessionId] of
 * the playback session can be obtained with [getLogSessionId].
 */
@RequiresApi(31)
class MediaMetricsListener private constructor(
    context: Context,
    private val playbackSession: PlaybackSession
) : AnalyticsListener, PlaybackSessionManager.Listener {

  private val context: Context = context.applicationContext
  private val sessionManager: PlaybackSessionManager
  private val startTimeMs: Long
  private val window: Timeline.Window = Timeline.Window()
  private val period: Timeline.Period = Timeline.Period()
  private val bandwidthTimeMs: HashMap<String, Long> = HashMap()
  private val bandwidthBytes: HashMap<String, Long> = HashMap()

  private var activeSessionId: String? = null
  private var metricsBuilder: PlaybackMetrics.Builder? = null
  @Player.DiscontinuityReason private var discontinuityReason: Int = 0
  private var currentPlaybackState: Int = PlaybackStateEvent.STATE_NOT_STARTED
  private var currentNetworkType: Int = NetworkEvent.NETWORK_TYPE_UNKNOWN
  private var pendingPlayerError: PlaybackException? = null
  private var pendingVideoFormat: PendingFormatUpdate? = null
  private var pendingAudioFormat: PendingFormatUpdate? = null
  private var pendingTextFormat: PendingFormatUpdate? = null
  private var currentVideoFormat: Format? = null
  private var currentAudioFormat: Format? = null
  private var currentTextFormat: Format? = null
  private var isSeeking: Boolean = false
  private var ioErrorType: Int = 0
  private var hasFatalError: Boolean = false
  private var droppedFrames: Int = 0
  private var playedFrames: Int = 0
  private var audioUnderruns: Int = 0
  private var reportedEventsForCurrentSession: Boolean = false

  init {
    startTimeMs = SystemClock.elapsedRealtime()
    sessionManager = DefaultPlaybackSessionManager()
    sessionManager.setListener(this)
  }

  /** Returns the [LogSessionId] used by this listener. */
  fun getLogSessionId(): LogSessionId {
    return playbackSession.sessionId
  }

  // PlaybackSessionManager.Listener implementation.

  override fun onSessionCreated(eventTime: AnalyticsListener.EventTime?, sessionId: String?) {}

  override fun onSessionActive(eventTime: AnalyticsListener.EventTime?, sessionId: String?) {
    if (eventTime == null || sessionId == null) {
      return
    }
    if (eventTime.mediaPeriodId != null && eventTime.mediaPeriodId!!.isAd) {
      // Ignore ad sessions.
      return
    }
    finishCurrentSession()
    activeSessionId = sessionId
    metricsBuilder =
        PlaybackMetrics.Builder()
            .setPlayerName(ExoPlayerLibraryInfo.TAG)
            .setPlayerVersion(ExoPlayerLibraryInfo.VERSION)
    if (eventTime.timeline != null) {
      maybeUpdateTimelineMetadata(eventTime.timeline, eventTime.mediaPeriodId)
    }
  }

  override fun onAdPlaybackStarted(
      eventTime: AnalyticsListener.EventTime?,
      contentSessionId: String?,
      adSessionId: String?
  ) {}

  override fun onSessionFinished(
      eventTime: AnalyticsListener.EventTime?,
      sessionId: String?,
      automaticTransitionToNextPlayback: Boolean
  ) {
    if (eventTime == null || sessionId == null) {
      return
    }
    if ((eventTime.mediaPeriodId != null && eventTime.mediaPeriodId!!.isAd) ||
        sessionId != activeSessionId) {
      // Ignore ad sessions and other sessions that are finished before becoming active.
    } else {
      finishCurrentSession()
    }
    bandwidthTimeMs.remove(sessionId)
    bandwidthBytes.remove(sessionId)
  }

  // AnalyticsListener implementation.

  override fun onPositionDiscontinuity(
      eventTime: AnalyticsListener.EventTime?,
      oldPosition: Player.PositionInfo?,
      newPosition: Player.PositionInfo?,
      reason: Int
  ) {
    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
      isSeeking = true
    }
    discontinuityReason = reason
  }

  override fun onVideoDisabled(
      eventTime: AnalyticsListener.EventTime?,
      decoderCounters: DecoderCounters?
  ) {
    if (decoderCounters != null) {
      // TODO(b/181122234): DecoderCounters are not re-reported at period boundaries.
      droppedFrames += decoderCounters.droppedBufferCount
      playedFrames += decoderCounters.renderedOutputBufferCount
    }
  }

  override fun onBandwidthEstimate(
      eventTime: AnalyticsListener.EventTime?,
      totalLoadTimeMs: Int,
      totalBytesLoaded: Long,
      bitrateEstimate: Long
  ) {
    if (eventTime != null && eventTime.mediaPeriodId != null && eventTime.timeline != null) {
      val sessionId =
          sessionManager.getSessionForMediaPeriodId(
              eventTime.timeline, eventTime.mediaPeriodId!!)
      if (sessionId != null) {
        val prevBandwidthBytes = bandwidthBytes[sessionId]
        val prevBandwidthTimeMs = bandwidthTimeMs[sessionId]
        bandwidthBytes[sessionId] = (prevBandwidthBytes ?: 0) + totalBytesLoaded
        bandwidthTimeMs[sessionId] = (prevBandwidthTimeMs ?: 0) + totalLoadTimeMs.toLong()
      }
    }
  }

  override fun onDownstreamFormatChanged(
      eventTime: AnalyticsListener.EventTime?,
      mediaLoadData: MediaLoadData?
  ) {
    if (eventTime == null || eventTime.mediaPeriodId == null || mediaLoadData == null || mediaLoadData.trackFormat == null || eventTime.timeline == null) {
      // This event arrived after the media has been removed from the playlist or a custom
      // MediaSource forgot to set the right id. Ignore the track change in these cases.
      return
    }
    val sessionId =
        sessionManager.getSessionForMediaPeriodId(
            eventTime.timeline, eventTime.mediaPeriodId!!) ?: return
    val update =
        PendingFormatUpdate(
            mediaLoadData.trackFormat,
            mediaLoadData.trackSelectionReason,
            sessionId)
    when (mediaLoadData.trackType) {
      C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_DEFAULT -> pendingVideoFormat = update
      C.TRACK_TYPE_AUDIO -> pendingAudioFormat = update
      C.TRACK_TYPE_TEXT -> pendingTextFormat = update
      else -> {} // Other track type. Ignore.
    }
  }

  override fun onVideoSizeChanged(
      eventTime: AnalyticsListener.EventTime?,
      videoSize: VideoSize?
  ) {
    val pendingVideoFormat = this.pendingVideoFormat
    if (pendingVideoFormat != null && pendingVideoFormat.format.height == Format.NO_VALUE && videoSize != null) {
      val formatWithHeightAndWidth =
          pendingVideoFormat
              .format
              .buildUpon()
              .setWidth(videoSize.width)
              .setHeight(videoSize.height)
              .build()
      this.pendingVideoFormat =
          PendingFormatUpdate(
              formatWithHeightAndWidth,
              pendingVideoFormat.selectionReason,
              pendingVideoFormat.sessionId)
    }
  }

  override fun onLoadError(
      eventTime: AnalyticsListener.EventTime?,
      loadEventInfo: LoadEventInfo?,
      mediaLoadData: MediaLoadData?,
      error: IOException?,
      wasCanceled: Boolean
  ) {
    if (mediaLoadData != null) {
      ioErrorType = mediaLoadData.dataType
    }
  }

  override fun onPlayerError(
      eventTime: AnalyticsListener.EventTime?,
      error: PlaybackException?
  ) {
    pendingPlayerError = error
  }

  override fun onEvents(player: Player?, events: AnalyticsListener.Events?) {
    if (player == null || events == null || events.size() == 0) {
      return
    }
    maybeAddSessions(events)

    val realtimeMs = SystemClock.elapsedRealtime()
    maybeUpdateMetricsBuilderValues(player, events)
    maybeReportPlaybackError(realtimeMs)
    maybeReportTrackChanges(player, events, realtimeMs)
    maybeReportNetworkChange(realtimeMs)
    maybeReportPlaybackStateChange(player, events, realtimeMs)

    if (events.contains(AnalyticsListener.EVENT_PLAYER_RELEASED)) {
      sessionManager.finishAllSessions(events.getEventTime(AnalyticsListener.EVENT_PLAYER_RELEASED))
    }
  }

  private fun maybeAddSessions(events: AnalyticsListener.Events) {
    for (i in 0 until events.size()) {
      @AnalyticsListener.EventFlags val event = events.get(i)
      val eventTime = events.getEventTime(event)
      if (event == AnalyticsListener.EVENT_TIMELINE_CHANGED) {
        sessionManager.updateSessionsWithTimelineChange(eventTime)
      } else if (event == AnalyticsListener.EVENT_POSITION_DISCONTINUITY) {
        sessionManager.updateSessionsWithDiscontinuity(eventTime, discontinuityReason)
      } else {
        sessionManager.updateSessions(eventTime)
      }
    }
  }

  private fun maybeUpdateMetricsBuilderValues(player: Player, events: AnalyticsListener.Events) {
    if (events.contains(AnalyticsListener.EVENT_TIMELINE_CHANGED)) {
      val eventTime = events.getEventTime(AnalyticsListener.EVENT_TIMELINE_CHANGED)
      if (metricsBuilder != null && eventTime.timeline != null) {
        maybeUpdateTimelineMetadata(eventTime.timeline, eventTime.mediaPeriodId)
      }
    }
    if (events.contains(AnalyticsListener.EVENT_TRACKS_CHANGED) && metricsBuilder != null) {
      val tracks = player.getCurrentTracks()
      if (tracks != null) {
        val drmInitData = getDrmInitData(tracks.groups)
        if (drmInitData != null) {
          metricsBuilder!!.setDrmType(getDrmType(drmInitData))
        }
      }
    }
    if (events.contains(AnalyticsListener.EVENT_AUDIO_UNDERRUN)) {
      audioUnderruns++
    }
  }

  private fun maybeReportPlaybackError(realtimeMs: Long) {
    val error = pendingPlayerError ?: return
    val errorInfo =
        getErrorInfo(
            error, context, /* lastIoErrorForManifest= */ ioErrorType == C.DATA_TYPE_MANIFEST)
    playbackSession.reportPlaybackErrorEvent(
        PlaybackErrorEvent.Builder()
            .setTimeSinceCreatedMillis(realtimeMs - startTimeMs)
            .setErrorCode(errorInfo.errorCode)
            .setSubErrorCode(errorInfo.subErrorCode)
            .setException(error)
            .build())
    reportedEventsForCurrentSession = true
    pendingPlayerError = null
  }

  private fun maybeReportTrackChanges(
      player: Player,
      events: AnalyticsListener.Events,
      realtimeMs: Long
  ) {
    if (events.contains(AnalyticsListener.EVENT_TRACKS_CHANGED)) {
      val tracks = player.getCurrentTracks()
      if (tracks != null) {
        val isVideoSelected = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
        val isAudioSelected = tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)
        val isTextSelected = tracks.isTypeSelected(C.TRACK_TYPE_TEXT)
        if (isVideoSelected || isAudioSelected || isTextSelected) {
          // Ignore updates with insufficient information where no tracks are selected.
          if (!isVideoSelected) {
            maybeUpdateVideoFormat(realtimeMs, /* videoFormat= */ null, C.SELECTION_REASON_UNKNOWN)
          }
          if (!isAudioSelected) {
            maybeUpdateAudioFormat(realtimeMs, /* audioFormat= */ null, C.SELECTION_REASON_UNKNOWN)
          }
          if (!isTextSelected) {
            maybeUpdateTextFormat(realtimeMs, /* textFormat= */ null, C.SELECTION_REASON_UNKNOWN)
          }
        }
      }
    }
    val pendingVideoFormat = pendingVideoFormat
    if (canReportPendingFormatUpdate(pendingVideoFormat) &&
        pendingVideoFormat!!.format.height != Format.NO_VALUE) {
      maybeUpdateVideoFormat(
          realtimeMs, pendingVideoFormat.format, pendingVideoFormat.selectionReason)
      this.pendingVideoFormat = null
    }
    val pendingAudioFormat = pendingAudioFormat
    if (canReportPendingFormatUpdate(pendingAudioFormat)) {
      maybeUpdateAudioFormat(
          realtimeMs, pendingAudioFormat!!.format, pendingAudioFormat.selectionReason)
      this.pendingAudioFormat = null
    }
    val pendingTextFormat = pendingTextFormat
    if (canReportPendingFormatUpdate(pendingTextFormat)) {
      maybeUpdateTextFormat(
          realtimeMs, pendingTextFormat!!.format, pendingTextFormat.selectionReason)
      this.pendingTextFormat = null
    }
  }

  private fun canReportPendingFormatUpdate(pendingFormatUpdate: PendingFormatUpdate?): Boolean {
    return pendingFormatUpdate != null &&
        pendingFormatUpdate.sessionId == sessionManager.getActiveSessionId()
  }

  private fun maybeReportNetworkChange(realtimeMs: Long) {
    val networkType = getNetworkType(context)
    if (networkType != currentNetworkType) {
      currentNetworkType = networkType
      playbackSession.reportNetworkEvent(
          NetworkEvent.Builder()
              .setNetworkType(networkType)
              .setTimeSinceCreatedMillis(realtimeMs - startTimeMs)
              .build())
    }
  }

  private fun maybeReportPlaybackStateChange(
      player: Player,
      events: AnalyticsListener.Events,
      realtimeMs: Long
  ) {
    if (player.getPlaybackState() != Player.STATE_BUFFERING) {
      isSeeking = false
    }
    if (player.getPlayerError() == null) {
      hasFatalError = false
    } else if (events.contains(AnalyticsListener.EVENT_PLAYER_ERROR)) {
      hasFatalError = true
    }
    val newPlaybackState = resolveNewPlaybackState(player)
    if (currentPlaybackState != newPlaybackState) {
      currentPlaybackState = newPlaybackState
      reportedEventsForCurrentSession = true
      playbackSession.reportPlaybackStateEvent(
          PlaybackStateEvent.Builder()
              .setState(currentPlaybackState)
              .setTimeSinceCreatedMillis(realtimeMs - startTimeMs)
              .build())
    }
  }

  private fun resolveNewPlaybackState(player: Player): Int {
    @Player.State val playerPlaybackState = player.getPlaybackState()
    if (isSeeking) {
      // Seeking takes precedence over errors such that we report a seek while in error state.
      return PlaybackStateEvent.STATE_SEEKING
    } else if (hasFatalError) {
      return PlaybackStateEvent.STATE_FAILED
    } else if (playerPlaybackState == Player.STATE_ENDED) {
      return PlaybackStateEvent.STATE_ENDED
    } else if (playerPlaybackState == Player.STATE_BUFFERING) {
      if (currentPlaybackState == PlaybackStateEvent.STATE_NOT_STARTED ||
          currentPlaybackState == PlaybackStateEvent.STATE_JOINING_FOREGROUND) {
        return PlaybackStateEvent.STATE_JOINING_FOREGROUND
      }
      if (!player.getPlayWhenReady()) {
        return PlaybackStateEvent.STATE_PAUSED_BUFFERING
      }
      return if (player.getPlaybackSuppressionReason() != Player.PLAYBACK_SUPPRESSION_REASON_NONE)
        PlaybackStateEvent.STATE_SUPPRESSED_BUFFERING
      else
        PlaybackStateEvent.STATE_BUFFERING
    } else if (playerPlaybackState == Player.STATE_READY) {
      if (!player.getPlayWhenReady()) {
        return PlaybackStateEvent.STATE_PAUSED
      }
      return if (player.getPlaybackSuppressionReason() != Player.PLAYBACK_SUPPRESSION_REASON_NONE)
        PlaybackStateEvent.STATE_SUPPRESSED
      else
        PlaybackStateEvent.STATE_PLAYING
    } else if (playerPlaybackState == Player.STATE_IDLE &&
        currentPlaybackState != PlaybackStateEvent.STATE_NOT_STARTED) {
      // This case only applies for calls to player.stop(). All other IDLE cases are handled by
      // !isForeground, hasFatalError or isSuspended. NOT_STARTED is deliberately ignored.
      return PlaybackStateEvent.STATE_STOPPED
    }
    return currentPlaybackState
  }

  private fun maybeUpdateVideoFormat(
      realtimeMs: Long,
      videoFormat: Format?,
      trackSelectionReason: Int
  ) {
    var reason = trackSelectionReason
    if (Util.areEqual(currentVideoFormat, videoFormat)) {
      return
    }
    if (currentVideoFormat == null && reason == C.SELECTION_REASON_UNKNOWN) {
      reason = C.SELECTION_REASON_INITIAL
    }
    currentVideoFormat = videoFormat
    reportTrackChangeEvent(
        TrackChangeEvent.TRACK_TYPE_VIDEO, realtimeMs, videoFormat, reason)
  }

  private fun maybeUpdateAudioFormat(
      realtimeMs: Long,
      audioFormat: Format?,
      trackSelectionReason: Int
  ) {
    var reason = trackSelectionReason
    if (Util.areEqual(currentAudioFormat, audioFormat)) {
      return
    }
    if (currentAudioFormat == null && reason == C.SELECTION_REASON_UNKNOWN) {
      reason = C.SELECTION_REASON_INITIAL
    }
    currentAudioFormat = audioFormat
    reportTrackChangeEvent(
        TrackChangeEvent.TRACK_TYPE_AUDIO, realtimeMs, audioFormat, reason)
  }

  private fun maybeUpdateTextFormat(
      realtimeMs: Long,
      textFormat: Format?,
      trackSelectionReason: Int
  ) {
    var reason = trackSelectionReason
    if (Util.areEqual(currentTextFormat, textFormat)) {
      return
    }
    if (currentTextFormat == null && reason == C.SELECTION_REASON_UNKNOWN) {
      reason = C.SELECTION_REASON_INITIAL
    }
    currentTextFormat = textFormat
    reportTrackChangeEvent(
        TrackChangeEvent.TRACK_TYPE_TEXT, realtimeMs, textFormat, reason)
  }

  private fun reportTrackChangeEvent(
      type: Int,
      realtimeMs: Long,
      format: Format?,
      trackSelectionReason: Int
  ) {
    val builder =
        TrackChangeEvent.Builder(type).setTimeSinceCreatedMillis(realtimeMs - startTimeMs)
    if (format != null) {
      builder.setTrackState(TrackChangeEvent.TRACK_STATE_ON)
      builder.setTrackChangeReason(getTrackChangeReason(trackSelectionReason))
      if (format.containerMimeType != null) {
        // TODO(b/181121074): Progressive container mime type is not filled in by MediaSource.
        builder.setContainerMimeType(format.containerMimeType)
      }
      if (format.sampleMimeType != null) {
        builder.setSampleMimeType(format.sampleMimeType)
      }
      if (format.codecs != null) {
        builder.setCodecName(format.codecs)
      }
      if (format.bitrate != Format.NO_VALUE) {
        builder.setBitrate(format.bitrate)
      }
      if (format.width != Format.NO_VALUE) {
        builder.setWidth(format.width)
      }
      if (format.height != Format.NO_VALUE) {
        builder.setHeight(format.height)
      }
      if (format.channelCount != Format.NO_VALUE) {
        builder.setChannelCount(format.channelCount)
      }
      if (format.sampleRate != Format.NO_VALUE) {
        builder.setAudioSampleRate(format.sampleRate)
      }
      if (format.language != null) {
        val languageAndRegion = getLanguageAndRegion(format.language!!)
        builder.setLanguage(languageAndRegion.first)
        if (languageAndRegion.second != null) {
          builder.setLanguageRegion(languageAndRegion.second)
        }
      }
      if (format.frameRate != Format.NO_VALUE.toFloat()) {
        builder.setVideoFrameRate(format.frameRate)
      }
    } else {
      builder.setTrackState(TrackChangeEvent.TRACK_STATE_OFF)
    }
    reportedEventsForCurrentSession = true
    playbackSession.reportTrackChangeEvent(builder.build())
  }

  private fun maybeUpdateTimelineMetadata(
      timeline: Timeline,
      mediaPeriodId: MediaSource.MediaPeriodId?
  ) {
    val metricsBuilder = this.metricsBuilder ?: return
    if (mediaPeriodId == null) {
      return
    }
    val periodIndex = timeline.getIndexOfPeriod(mediaPeriodId.periodUid)
    if (periodIndex == C.INDEX_UNSET) {
      return
    }
    timeline.getPeriod(periodIndex, period)
    timeline.getWindow(period.windowIndex, window)
    if (window.mediaItem != null) {
      metricsBuilder.setStreamType(getStreamType(window.mediaItem!!))
    }
    if (window.durationUs != C.TIME_UNSET &&
        !window.isPlaceholder &&
        !window.isDynamic &&
        !window.isLive()) {
      metricsBuilder.setMediaDurationMillis(window.durationMs)
    }
    metricsBuilder.setPlaybackType(
        if (window.isLive()) PlaybackMetrics.PLAYBACK_TYPE_LIVE else PlaybackMetrics.PLAYBACK_TYPE_VOD)
    reportedEventsForCurrentSession = true
  }

  private fun finishCurrentSession() {
    val metricsBuilder = this.metricsBuilder
    if (metricsBuilder != null && reportedEventsForCurrentSession) {
      metricsBuilder.setAudioUnderrunCount(audioUnderruns)
      metricsBuilder.setVideoFramesDropped(droppedFrames)
      metricsBuilder.setVideoFramesPlayed(playedFrames)
      val networkTimeMs = bandwidthTimeMs[activeSessionId]
      metricsBuilder.setNetworkTransferDurationMillis(networkTimeMs ?: 0)
      // TODO(b/181121847): Report localBytesRead. This requires additional callbacks or plumbing.
      val networkBytes = bandwidthBytes[activeSessionId]
      metricsBuilder.setNetworkBytesRead(networkBytes ?: 0)
      // TODO(b/181121847): Detect stream sources mixed and local depending on localBytesRead.
      metricsBuilder.setStreamSource(
          if (networkBytes != null && networkBytes > 0)
            PlaybackMetrics.STREAM_SOURCE_NETWORK
          else
            PlaybackMetrics.STREAM_SOURCE_UNKNOWN)
      playbackSession.reportPlaybackMetrics(metricsBuilder.build())
    }
    this.metricsBuilder = null
    activeSessionId = null
    audioUnderruns = 0
    droppedFrames = 0
    playedFrames = 0
    currentVideoFormat = null
    currentAudioFormat = null
    currentTextFormat = null
    reportedEventsForCurrentSession = false
  }

  private class ErrorInfo(val errorCode: Int, val subErrorCode: Int)

  private class PendingFormatUpdate(
      val format: Format,
      val selectionReason: Int,
      val sessionId: String
  )

  companion object {
    /**
     * Creates a media metrics listener.
     *
     * @param context A context.
     * @return The [MediaMetricsListener], or null if the [Context.MEDIA_METRICS_SERVICE
     *     media metrics service] isn't available.
     */
    @JvmStatic
    fun create(context: Context): MediaMetricsListener? {
      val mediaMetricsManager =
          context.getSystemService(Context.MEDIA_METRICS_SERVICE) as MediaMetricsManager?
      return if (mediaMetricsManager == null) null
      else MediaMetricsListener(context, mediaMetricsManager.createPlaybackSession())
    }

    private fun getTrackChangeReason(trackSelectionReason: Int): Int {
      return when (trackSelectionReason) {
        C.SELECTION_REASON_INITIAL -> TrackChangeEvent.TRACK_CHANGE_REASON_INITIAL
        C.SELECTION_REASON_ADAPTIVE -> TrackChangeEvent.TRACK_CHANGE_REASON_ADAPTIVE
        C.SELECTION_REASON_MANUAL -> TrackChangeEvent.TRACK_CHANGE_REASON_MANUAL
        C.SELECTION_REASON_TRICK_PLAY, C.SELECTION_REASON_UNKNOWN ->
          TrackChangeEvent.TRACK_CHANGE_REASON_OTHER
        else -> TrackChangeEvent.TRACK_CHANGE_REASON_OTHER
      }
    }

    private fun getLanguageAndRegion(languageCode: String): Pair<String, String?> {
      val parts = Util.split(languageCode, "-")
      return Pair.create(parts[0], if (parts.size >= 2) parts[1] else null)
    }

    private fun getNetworkType(context: Context): Int {
      val networkTypeObserver = NetworkTypeObserver.getInstance(context)
      return when (networkTypeObserver!!.getNetworkType()) {
        C.NETWORK_TYPE_WIFI -> NetworkEvent.NETWORK_TYPE_WIFI
        C.NETWORK_TYPE_2G -> NetworkEvent.NETWORK_TYPE_2G
        C.NETWORK_TYPE_3G -> NetworkEvent.NETWORK_TYPE_3G
        C.NETWORK_TYPE_4G -> NetworkEvent.NETWORK_TYPE_4G
        C.NETWORK_TYPE_5G_SA -> NetworkEvent.NETWORK_TYPE_5G_SA
        C.NETWORK_TYPE_5G_NSA -> NetworkEvent.NETWORK_TYPE_5G_NSA
        C.NETWORK_TYPE_ETHERNET -> NetworkEvent.NETWORK_TYPE_ETHERNET
        C.NETWORK_TYPE_OFFLINE -> NetworkEvent.NETWORK_TYPE_OFFLINE
        C.NETWORK_TYPE_UNKNOWN -> NetworkEvent.NETWORK_TYPE_UNKNOWN
        else -> NetworkEvent.NETWORK_TYPE_OTHER
      }
    }

    private fun getStreamType(mediaItem: MediaItem): Int {
      val localConfiguration = mediaItem.localConfiguration
          ?: return PlaybackMetrics.STREAM_TYPE_UNKNOWN
      @ContentType
      val contentType =
          Util.inferContentTypeForUriAndMimeType(
              localConfiguration.uri, localConfiguration.mimeType)
      return when (contentType) {
        C.CONTENT_TYPE_HLS -> PlaybackMetrics.STREAM_TYPE_HLS
        C.CONTENT_TYPE_DASH -> PlaybackMetrics.STREAM_TYPE_DASH
        C.CONTENT_TYPE_SS -> PlaybackMetrics.STREAM_TYPE_SS
        C.CONTENT_TYPE_RTSP -> PlaybackMetrics.STREAM_TYPE_OTHER
        else -> PlaybackMetrics.STREAM_TYPE_OTHER
      }
    }

    private fun getErrorInfo(
        error: PlaybackException,
        context: Context,
        lastIoErrorForManifest: Boolean
    ): ErrorInfo {
      if (error.errorCode == PlaybackException.ERROR_CODE_REMOTE_ERROR) {
        return ErrorInfo(PlaybackErrorEvent.ERROR_PLAYER_REMOTE, /* subErrorCode= */ 0)
      }
      // Unpack the PlaybackException.
      // TODO(b/190203080): Use error codes instead of the Exception's cause where possible.
      var isRendererExoPlaybackException = false
      var rendererFormatSupport = C.FORMAT_UNSUPPORTED_TYPE
      if (error is ExoPlaybackException) {
        isRendererExoPlaybackException =
            error.type == ExoPlaybackException.TYPE_RENDERER
        rendererFormatSupport = error.rendererFormatSupport
      }
      var cause: Throwable = error.cause ?: return ErrorInfo(PlaybackErrorEvent.ERROR_PLAYER_OTHER, 0)
      if (cause is IOException) {
        if (cause is HttpDataSource.InvalidResponseCodeException) {
          val responseCode = cause.responseCode
          return ErrorInfo(
              PlaybackErrorEvent.ERROR_IO_BAD_HTTP_STATUS, /* subErrorCode= */ responseCode)
        } else if (cause is HttpDataSource.InvalidContentTypeException || cause is ParserException) {
          return ErrorInfo(
              if (lastIoErrorForManifest)
                PlaybackErrorEvent.ERROR_PARSING_MANIFEST_MALFORMED
              else
                PlaybackErrorEvent.ERROR_PARSING_CONTAINER_MALFORMED,
              /* subErrorCode= */ 0)
        } else if (cause is HttpDataSource.HttpDataSourceException ||
            cause is UdpDataSource.UdpDataSourceException) {
          if (NetworkTypeObserver.getInstance(context)!!.getNetworkType() == C.NETWORK_TYPE_OFFLINE) {
            return ErrorInfo(
                PlaybackErrorEvent.ERROR_IO_NETWORK_UNAVAILABLE, /* subErrorCode= */ 0)
          } else {
            val detailedCause = cause.cause
            if (detailedCause is UnknownHostException) {
              return ErrorInfo(PlaybackErrorEvent.ERROR_IO_DNS_FAILED, /* subErrorCode= */ 0)
            } else if (detailedCause is SocketTimeoutException) {
              return ErrorInfo(
                  PlaybackErrorEvent.ERROR_IO_CONNECTION_TIMEOUT, /* subErrorCode= */ 0)
            } else if (cause is HttpDataSource.HttpDataSourceException &&
                cause.type == HttpDataSource.HttpDataSourceException.TYPE_OPEN) {
              return ErrorInfo(
                  PlaybackErrorEvent.ERROR_IO_NETWORK_CONNECTION_FAILED, /* subErrorCode= */ 0)
            } else {
              return ErrorInfo(
                  PlaybackErrorEvent.ERROR_IO_CONNECTION_CLOSED, /* subErrorCode= */ 0)
            }
          }
        } else if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
          return ErrorInfo(
              PlaybackErrorEvent.ERROR_PLAYER_BEHIND_LIVE_WINDOW, /* subErrorCode= */ 0)
        } else if (cause is DrmSession.DrmSessionException) {
          // Unpack DrmSessionException.
          cause = cause.cause ?: return ErrorInfo(PlaybackErrorEvent.ERROR_DRM_OTHER, 0)
          if (Util.SDK_INT >= 21 && cause is MediaDrm.MediaDrmStateException) {
            val diagnosticsInfo = cause.diagnosticInfo
            val subErrorCode = Util.getErrorCodeFromPlatformDiagnosticsInfo(diagnosticsInfo)
            val errorCode = getDrmErrorCode(subErrorCode)
            return ErrorInfo(errorCode, subErrorCode)
          } else if (Util.SDK_INT >= 23 && cause is MediaDrmResetException) {
            return ErrorInfo(PlaybackErrorEvent.ERROR_DRM_SYSTEM_ERROR, /* subErrorCode= */ 0)
          } else if (Util.SDK_INT >= 18 && cause is NotProvisionedException) {
            return ErrorInfo(
                PlaybackErrorEvent.ERROR_DRM_PROVISIONING_FAILED, /* subErrorCode= */ 0)
          } else if (Util.SDK_INT >= 18 && cause is DeniedByServerException) {
            return ErrorInfo(PlaybackErrorEvent.ERROR_DRM_DEVICE_REVOKED, /* subErrorCode= */ 0)
          } else if (cause is UnsupportedDrmException) {
            return ErrorInfo(
                PlaybackErrorEvent.ERROR_DRM_SCHEME_UNSUPPORTED, /* subErrorCode= */ 0)
          } else if (cause is DefaultDrmSessionManager.MissingSchemeDataException) {
            return ErrorInfo(PlaybackErrorEvent.ERROR_DRM_CONTENT_ERROR, /* subErrorCode= */ 0)
          } else {
            return ErrorInfo(PlaybackErrorEvent.ERROR_DRM_OTHER, /* subErrorCode= */ 0)
          }
        } else if (cause is FileDataSource.FileDataSourceException &&
            cause.cause is FileNotFoundException) {
          val fileNotFoundException = cause.cause as FileNotFoundException
          val notFoundCause = fileNotFoundException.cause
          if (Util.SDK_INT >= 21 &&
              notFoundCause is ErrnoException &&
              notFoundCause.errno == OsConstants.EACCES) {
            return ErrorInfo(PlaybackErrorEvent.ERROR_IO_NO_PERMISSION, /* subErrorCode= */ 0)
          } else {
            return ErrorInfo(PlaybackErrorEvent.ERROR_IO_FILE_NOT_FOUND, /* subErrorCode= */ 0)
          }
        } else {
          return ErrorInfo(PlaybackErrorEvent.ERROR_IO_OTHER, /* subErrorCode= */ 0)
        }
      } else if (isRendererExoPlaybackException &&
          (rendererFormatSupport == C.FORMAT_UNSUPPORTED_TYPE ||
              rendererFormatSupport == C.FORMAT_UNSUPPORTED_SUBTYPE)) {
        return ErrorInfo(
            PlaybackErrorEvent.ERROR_DECODING_FORMAT_UNSUPPORTED, /* subErrorCode= */ 0)
      } else if (isRendererExoPlaybackException &&
          rendererFormatSupport == C.FORMAT_EXCEEDS_CAPABILITIES) {
        return ErrorInfo(
            PlaybackErrorEvent.ERROR_DECODING_FORMAT_EXCEEDS_CAPABILITIES, /* subErrorCode= */ 0)
      } else if (isRendererExoPlaybackException &&
          rendererFormatSupport == C.FORMAT_UNSUPPORTED_DRM) {
        return ErrorInfo(PlaybackErrorEvent.ERROR_DRM_SCHEME_UNSUPPORTED, /* subErrorCode= */ 0)
      } else if (cause is MediaCodecRenderer.DecoderInitializationException) {
        val diagnosticsInfo = cause.diagnosticInfo
        val subErrorCode = Util.getErrorCodeFromPlatformDiagnosticsInfo(diagnosticsInfo)
        return ErrorInfo(PlaybackErrorEvent.ERROR_DECODER_INIT_FAILED, subErrorCode)
      } else if (cause is MediaCodecDecoderException) {
        val diagnosticsInfo = cause.diagnosticInfo
        val subErrorCode = Util.getErrorCodeFromPlatformDiagnosticsInfo(diagnosticsInfo)
        return ErrorInfo(PlaybackErrorEvent.ERROR_DECODING_FAILED, subErrorCode)
      } else if (cause is OutOfMemoryError) {
        return ErrorInfo(PlaybackErrorEvent.ERROR_DECODING_FAILED, /* subErrorCode= */ 0)
      } else if (cause is AudioSink.InitializationException) {
        val subErrorCode = cause.audioTrackState
        return ErrorInfo(PlaybackErrorEvent.ERROR_AUDIO_TRACK_INIT_FAILED, subErrorCode)
      } else if (cause is AudioSink.WriteException) {
        val subErrorCode = cause.errorCode
        return ErrorInfo(PlaybackErrorEvent.ERROR_AUDIO_TRACK_WRITE_FAILED, subErrorCode)
      } else if (Util.SDK_INT >= 16 && cause is MediaCodec.CryptoException) {
        val subErrorCode = cause.errorCode
        val errorCode = getDrmErrorCode(subErrorCode)
        return ErrorInfo(errorCode, subErrorCode)
      } else {
        return ErrorInfo(PlaybackErrorEvent.ERROR_PLAYER_OTHER, /* subErrorCode= */ 0)
      }
    }

    private fun getDrmInitData(trackGroups: ImmutableList<Tracks.Group>): DrmInitData? {
      for (trackGroup in trackGroups) {
        for (trackIndex in 0 until trackGroup.length) {
          if (trackGroup.isTrackSelected(trackIndex)) {
            val trackFormat = trackGroup.getTrackFormat(trackIndex)
            val drmInitData = trackFormat?.drmInitData
            if (drmInitData != null) {
              return drmInitData
            }
          }
        }
      }
      return null
    }

    private fun getDrmType(drmInitData: DrmInitData): Int {
      for (i in 0 until drmInitData.schemeDataCount) {
        val uuid = drmInitData.get(i).uuid
        if (uuid == C.WIDEVINE_UUID) {
          // TODO(b/77625596): Forward MediaDrm metrics to distinguish between L1 and L3 and to set
          //  the drm session id.
          return PlaybackMetrics.DRM_TYPE_WIDEVINE_L1
        }
        if (uuid == C.PLAYREADY_UUID) {
          return PlaybackMetrics.DRM_TYPE_PLAY_READY
        }
        if (uuid == C.CLEARKEY_UUID) {
          return PlaybackMetrics.DRM_TYPE_CLEARKEY
        }
      }
      return PlaybackMetrics.DRM_TYPE_OTHER
    }

    @SuppressLint("SwitchIntDef") // Only DRM error codes are relevant here.
    private fun getDrmErrorCode(mediaDrmErrorCode: Int): Int {
      return when (Util.getErrorCodeForMediaDrmErrorCode(mediaDrmErrorCode)) {
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED ->
          PlaybackErrorEvent.ERROR_DRM_PROVISIONING_FAILED
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED ->
          PlaybackErrorEvent.ERROR_DRM_LICENSE_ACQUISITION_FAILED
        PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION ->
          PlaybackErrorEvent.ERROR_DRM_DISALLOWED_OPERATION
        PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR ->
          PlaybackErrorEvent.ERROR_DRM_CONTENT_ERROR
        PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR ->
          PlaybackErrorEvent.ERROR_DRM_SYSTEM_ERROR
        else -> PlaybackErrorEvent.ERROR_DRM_SYSTEM_ERROR
      }
    }
  }
}
