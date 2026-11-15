/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.os.SystemClock
import android.util.Pair
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.Timeline.Period
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_AUDIO_UNDERRUN
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_BANDWIDTH_ESTIMATE
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_DOWNSTREAM_FORMAT_CHANGED
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_DRM_SESSION_MANAGER_ERROR
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_DROPPED_VIDEO_FRAMES
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_LOAD_ERROR
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_LOAD_STARTED
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_PLAYER_ERROR
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_PLAYER_RELEASED
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_POSITION_DISCONTINUITY
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_TIMELINE_CHANGED
import com.google.android.exoplayer2.analytics.AnalyticsListener.Companion.EVENT_VIDEO_SIZE_CHANGED
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventFlags
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.analytics.AnalyticsListener.Events
import com.google.android.exoplayer2.analytics.PlaybackStats.EventTimeAndException
import com.google.android.exoplayer2.analytics.PlaybackStats.EventTimeAndFormat
import com.google.android.exoplayer2.analytics.PlaybackStats.EventTimeAndPlaybackState
import com.google.android.exoplayer2.analytics.PlaybackStats.PlaybackState
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId
import com.google.android.exoplayer2.util.Assertions.checkArgument
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoSize
import java.io.IOException
import java.util.Arrays
import kotlin.math.max

/**
 * [AnalyticsListener] to gather [PlaybackStats] from the player.
 *
 * For accurate measurements, the listener should be added to the player before loading media,
 * i.e., [Player.getPlaybackState] should be [Player.STATE_IDLE].
 *
 * Playback stats are gathered separately for each playback session, i.e. each window in the
 * [Timeline] and each single ad.
 */
class PlaybackStatsListener(
  private val keepHistory: Boolean,
  private val callback: Callback?
) : AnalyticsListener, PlaybackSessionManager.Listener {

  /** A listener for [PlaybackStats] updates. */
  fun interface Callback {
    /**
     * Called when a playback session ends and its [PlaybackStats] are ready.
     *
     * @param eventTime The [EventTime] at which the playback session started. Can be used to
     *     identify the playback session.
     * @param playbackStats The [PlaybackStats] for the ended playback session.
     */
    fun onPlaybackStatsReady(eventTime: EventTime, playbackStats: PlaybackStats)
  }

  private val sessionManager: PlaybackSessionManager = DefaultPlaybackSessionManager()
  private val playbackStatsTrackers: MutableMap<String, PlaybackStatsTracker> = HashMap()
  private val sessionStartEventTimes: MutableMap<String, EventTime> = HashMap()
  private val period: Period = Period()

  private var finishedPlaybackStats: PlaybackStats = PlaybackStats.EMPTY

  private var discontinuityFromSession: String? = null
  private var discontinuityFromPositionMs: Long = 0
  @Player.DiscontinuityReason private var discontinuityReason: Int = 0
  private var droppedFrames: Int = 0
  private var nonFatalException: Exception? = null
  private var bandwidthTimeMs: Long = 0
  private var bandwidthBytes: Long = 0
  private var videoFormat: Format? = null
  private var audioFormat: Format? = null
  private var videoSize: VideoSize = VideoSize.UNKNOWN

  init {
    sessionManager.setListener(this)
  }

  /**
   * Returns the combined [PlaybackStats] for all playback sessions this listener was and is
   * listening to.
   *
   * Note that these [PlaybackStats] will not contain the full history of events.
   *
   * @return The combined [PlaybackStats] for all playback sessions.
   */
  fun getCombinedPlaybackStats(): PlaybackStats {
    val allPendingPlaybackStats = arrayOfNulls<PlaybackStats>(playbackStatsTrackers.size + 1)
    allPendingPlaybackStats[0] = finishedPlaybackStats
    var index = 1
    for (tracker in playbackStatsTrackers.values) {
      allPendingPlaybackStats[index++] = tracker.build(/* isFinal= */ false)
    }
    @Suppress("UNCHECKED_CAST")
    return PlaybackStats.merge(*(allPendingPlaybackStats as Array<PlaybackStats>))
  }

  /**
   * Returns the [PlaybackStats] for the currently playback session, or null if no session is
   * active.
   *
   * @return [PlaybackStats] for the current playback session.
   */
  fun getPlaybackStats(): PlaybackStats? {
    val activeSessionId = sessionManager.getActiveSessionId()
    val activeStatsTracker =
      if (activeSessionId == null) null else playbackStatsTrackers[activeSessionId]
    return activeStatsTracker?.build(/* isFinal= */ false)
  }

  // PlaybackSessionManager.Listener implementation.

  override fun onSessionCreated(eventTime: EventTime?, sessionId: String?) {
    val safeEventTime = checkNotNull(eventTime)!!
    val safeSessionId = checkNotNull(sessionId)!!
    val tracker = PlaybackStatsTracker(keepHistory, safeEventTime)
    playbackStatsTrackers[safeSessionId] = tracker
    sessionStartEventTimes[safeSessionId] = safeEventTime
  }

  override fun onSessionActive(eventTime: EventTime?, sessionId: String?) {
    val safeSessionId = checkNotNull(sessionId)!!
    checkNotNull(playbackStatsTrackers[safeSessionId])!!.onForeground()
  }

  override fun onAdPlaybackStarted(
    eventTime: EventTime?,
    contentSessionId: String?,
    adSessionId: String?
  ) {
    val safeContentSessionId = checkNotNull(contentSessionId)!!
    checkNotNull(playbackStatsTrackers[safeContentSessionId])!!.onInterruptedByAd()
  }

  override fun onSessionFinished(
    eventTime: EventTime?,
    sessionId: String?,
    automaticTransitionToNextPlayback: Boolean
  ) {
    val safeEventTime = checkNotNull(eventTime)!!
    val safeSessionId = checkNotNull(sessionId)!!
    val tracker = checkNotNull(playbackStatsTrackers.remove(safeSessionId))!!
    val startEventTime = checkNotNull(sessionStartEventTimes.remove(safeSessionId))!!
    val discontinuityFromPositionMs =
      if (safeSessionId == discontinuityFromSession) {
        this.discontinuityFromPositionMs
      } else {
        C.TIME_UNSET
      }
    tracker.onFinished(safeEventTime, automaticTransitionToNextPlayback, discontinuityFromPositionMs)
    val playbackStats = tracker.build(/* isFinal= */ true)
    finishedPlaybackStats = PlaybackStats.merge(finishedPlaybackStats, playbackStats)
    callback?.onPlaybackStatsReady(startEventTime, playbackStats)
  }

  // AnalyticsListener implementation.

  override fun onPositionDiscontinuity(
    eventTime: EventTime?,
    oldPosition: Player.PositionInfo?,
    newPosition: Player.PositionInfo?,
    @Player.DiscontinuityReason reason: Int
  ) {
    if (discontinuityFromSession == null) {
      discontinuityFromSession = sessionManager.getActiveSessionId()
      discontinuityFromPositionMs = checkNotNull(oldPosition)!!.positionMs
    }
    discontinuityReason = reason
  }

  override fun onDroppedVideoFrames(eventTime: EventTime?, droppedFrames: Int, elapsedMs: Long) {
    this.droppedFrames = droppedFrames
  }

  override fun onLoadError(
    eventTime: EventTime?,
    loadEventInfo: LoadEventInfo?,
    mediaLoadData: MediaLoadData?,
    error: IOException?,
    wasCanceled: Boolean
  ) {
    nonFatalException = error
  }

  override fun onDrmSessionManagerError(eventTime: EventTime?, error: Exception?) {
    nonFatalException = error
  }

  override fun onBandwidthEstimate(
    eventTime: EventTime?,
    totalLoadTimeMs: Int,
    totalBytesLoaded: Long,
    bitrateEstimate: Long
  ) {
    bandwidthTimeMs = totalLoadTimeMs.toLong()
    bandwidthBytes = totalBytesLoaded
  }

  override fun onDownstreamFormatChanged(eventTime: EventTime?, mediaLoadData: MediaLoadData?) {
    if (mediaLoadData != null) {
      if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO
        || mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT
      ) {
        videoFormat = mediaLoadData.trackFormat
      } else if (mediaLoadData.trackType == C.TRACK_TYPE_AUDIO) {
        audioFormat = mediaLoadData.trackFormat
      }
    }
  }

  override fun onVideoSizeChanged(eventTime: EventTime?, videoSize: VideoSize?) {
    if (videoSize != null) {
      this.videoSize = videoSize
    }
  }

  override fun onEvents(player: Player?, events: Events?) {
    if (player == null || events == null || events.size() == 0) {
      return
    }
    maybeAddSessions(events)
    for (session in playbackStatsTrackers.keys) {
      val eventTimeAndBelongsToPlayback = findBestEventTime(events, session)
      val tracker = checkNotNull(playbackStatsTrackers[session])!!
      val hasDiscontinuityToPlayback = hasEvent(events, session, EVENT_POSITION_DISCONTINUITY)
      val hasDroppedFrames = hasEvent(events, session, EVENT_DROPPED_VIDEO_FRAMES)
      val hasAudioUnderrun = hasEvent(events, session, EVENT_AUDIO_UNDERRUN)
      val startedLoading = hasEvent(events, session, EVENT_LOAD_STARTED)
      val hasFatalError = hasEvent(events, session, EVENT_PLAYER_ERROR)
      val hasNonFatalException =
        hasEvent(events, session, EVENT_LOAD_ERROR)
          || hasEvent(events, session, EVENT_DRM_SESSION_MANAGER_ERROR)
      val hasBandwidthData = hasEvent(events, session, EVENT_BANDWIDTH_ESTIMATE)
      val hasFormatData = hasEvent(events, session, EVENT_DOWNSTREAM_FORMAT_CHANGED)
      val hasVideoSize = hasEvent(events, session, EVENT_VIDEO_SIZE_CHANGED)
      tracker.onEvents(
        player,
        /* eventTime= */ eventTimeAndBelongsToPlayback.first,
        /* belongsToPlayback= */ eventTimeAndBelongsToPlayback.second,
        if (session == discontinuityFromSession) discontinuityFromPositionMs else C.TIME_UNSET,
        hasDiscontinuityToPlayback,
        if (hasDroppedFrames) droppedFrames else 0,
        hasAudioUnderrun,
        startedLoading,
        if (hasFatalError) player.getPlayerError() else null,
        if (hasNonFatalException) nonFatalException else null,
        if (hasBandwidthData) bandwidthTimeMs else 0,
        if (hasBandwidthData) bandwidthBytes else 0,
        if (hasFormatData) videoFormat else null,
        if (hasFormatData) audioFormat else null,
        if (hasVideoSize) videoSize else null
      )
    }
    videoFormat = null
    audioFormat = null
    discontinuityFromSession = null
    if (events.contains(EVENT_PLAYER_RELEASED)) {
      sessionManager.finishAllSessions(events.getEventTime(EVENT_PLAYER_RELEASED))
    }
  }

  private fun maybeAddSessions(events: Events) {
    for (i in 0 until events.size()) {
      @EventFlags val event = events.get(i)
      val eventTime = events.getEventTime(event)
      if (event == EVENT_TIMELINE_CHANGED) {
        sessionManager.updateSessionsWithTimelineChange(eventTime)
      } else if (event == EVENT_POSITION_DISCONTINUITY) {
        sessionManager.updateSessionsWithDiscontinuity(eventTime, discontinuityReason)
      } else {
        sessionManager.updateSessions(eventTime)
      }
    }
  }

  private fun findBestEventTime(events: Events, session: String): Pair<EventTime, Boolean> {
    var eventTime: EventTime? = null
    var belongsToPlayback = false
    for (i in 0 until events.size()) {
      @EventFlags val event = events.get(i)
      val newEventTime = events.getEventTime(event)
      val newBelongsToPlayback = sessionManager.belongsToSession(newEventTime, session)
      if (eventTime == null
        || (newBelongsToPlayback && !belongsToPlayback)
        || (newBelongsToPlayback == belongsToPlayback
          && newEventTime.realtimeMs > eventTime.realtimeMs)
      ) {
        // Prefer event times for the current playback and prefer later timestamps.
        eventTime = newEventTime
        belongsToPlayback = newBelongsToPlayback
      }
    }
    val bestEventTime = checkNotNull(eventTime)!!
    if (!belongsToPlayback && bestEventTime.mediaPeriodId != null && bestEventTime.mediaPeriodId!!.isAd) {
      // Replace ad event time with content event time unless it's for the ad playback itself.
      val mediaPeriodIdObj = checkNotNull(bestEventTime.mediaPeriodId)!!
      val periodUid = mediaPeriodIdObj.periodUid
      val adGroupIndex = mediaPeriodIdObj.adGroupIndex
      val timelineObj = checkNotNull(bestEventTime.timeline)!!
      var contentPeriodPositionUs =
        timelineObj
          .getPeriodByUid(periodUid, period)!!
          .getAdGroupTimeUs(adGroupIndex)
      if (contentPeriodPositionUs == C.TIME_END_OF_SOURCE) {
        contentPeriodPositionUs = period.durationUs
      }
      val contentWindowPositionUs = contentPeriodPositionUs + period.positionInWindowUs
      val mediaPeriodId = MediaPeriodId(
        periodUid,
        mediaPeriodIdObj.windowSequenceNumber,
        adGroupIndex
      )
      val updatedEventTime = EventTime(
        bestEventTime.realtimeMs,
        bestEventTime.timeline,
        bestEventTime.windowIndex,
        mediaPeriodId,
        /* eventPlaybackPositionMs= */ Util.usToMs(contentWindowPositionUs),
        bestEventTime.timeline,
        bestEventTime.currentWindowIndex,
        bestEventTime.currentMediaPeriodId,
        bestEventTime.currentPlaybackPositionMs,
        bestEventTime.totalBufferedDurationMs
      )
      belongsToPlayback = sessionManager.belongsToSession(updatedEventTime, session)
      return Pair.create(updatedEventTime, belongsToPlayback)
    }
    return Pair.create(bestEventTime, belongsToPlayback)
  }

  private fun hasEvent(events: Events, session: String, @EventFlags event: Int): Boolean {
    return events.contains(event)
      && sessionManager.belongsToSession(events.getEventTime(event), session)
  }

  /** Tracker for playback stats of a single playback. */
  private class PlaybackStatsTracker(
    private val keepHistory: Boolean,
    startTime: EventTime
  ) {
    // Final stats.
    private val playbackStateDurationsMs: LongArray = LongArray(PlaybackStats.PLAYBACK_STATE_COUNT)
    private val playbackStateHistory: MutableList<EventTimeAndPlaybackState> =
      if (keepHistory) ArrayList() else mutableListOf()
    private val mediaTimeHistory: MutableList<LongArray> =
      if (keepHistory) ArrayList() else mutableListOf()
    private val videoFormatHistory: MutableList<EventTimeAndFormat> =
      if (keepHistory) ArrayList() else mutableListOf()
    private val audioFormatHistory: MutableList<EventTimeAndFormat> =
      if (keepHistory) ArrayList() else mutableListOf()
    private val fatalErrorHistory: MutableList<EventTimeAndException> =
      if (keepHistory) ArrayList() else mutableListOf()
    private val nonFatalErrorHistory: MutableList<EventTimeAndException> =
      if (keepHistory) ArrayList() else mutableListOf()
    private val isAd: Boolean = startTime.mediaPeriodId != null && startTime.mediaPeriodId!!.isAd

    private var firstReportedTimeMs: Long = C.TIME_UNSET
    private var hasBeenReady: Boolean = false
    private var hasEnded: Boolean = false
    private var isJoinTimeInvalid: Boolean = false
    private var pauseCount: Int = 0
    private var pauseBufferCount: Int = 0
    private var seekCount: Int = 0
    private var rebufferCount: Int = 0
    private var maxRebufferTimeMs: Long = C.TIME_UNSET
    private var initialVideoFormatHeight: Int = C.LENGTH_UNSET
    private var initialVideoFormatBitrate: Long = C.LENGTH_UNSET.toLong()
    private var initialAudioFormatBitrate: Long = C.LENGTH_UNSET.toLong()
    private var videoFormatHeightTimeMs: Long = 0
    private var videoFormatHeightTimeProduct: Long = 0
    private var videoFormatBitrateTimeMs: Long = 0
    private var videoFormatBitrateTimeProduct: Long = 0
    private var audioFormatTimeMs: Long = 0
    private var audioFormatBitrateTimeProduct: Long = 0
    private var bandwidthTimeMs: Long = 0
    private var bandwidthBytes: Long = 0
    private var droppedFrames: Long = 0
    private var audioUnderruns: Long = 0
    private var fatalErrorCount: Int = 0
    private var nonFatalErrorCount: Int = 0

    // Current player state tracking.
    @PlaybackState private var currentPlaybackState: Int = PlaybackStats.PLAYBACK_STATE_NOT_STARTED
    private var currentPlaybackStateStartTimeMs: Long = startTime.realtimeMs
    private var isSeeking: Boolean = false
    private var isForeground: Boolean = false
    private var isInterruptedByAd: Boolean = false
    private var hasFatalError: Boolean = false
    private var startedLoading: Boolean = false
    private var lastRebufferStartTimeMs: Long = 0
    private var currentVideoFormat: Format? = null
    private var currentAudioFormat: Format? = null
    private var lastVideoFormatStartTimeMs: Long = 0
    private var lastAudioFormatStartTimeMs: Long = 0
    private var currentPlaybackSpeed: Float = 1f

    /** Notifies the tracker that the current playback became the active foreground playback. */
    fun onForeground() {
      isForeground = true
    }

    /** Notifies the tracker that the current playback is interrupted by an ad. */
    fun onInterruptedByAd() {
      isInterruptedByAd = true
      isSeeking = false
    }

    /**
     * Notifies the tracker that the current playback has finished.
     *
     * @param eventTime The [EventTime]. Does not belong to this playback.
     * @param automaticTransition Whether the playback finished because of an automatic transition
     *     to the next playback item.
     * @param discontinuityFromPositionMs The position before the discontinuity from this playback,
     *     [C.TIME_UNSET] if no discontinuity started from this playback.
     */
    fun onFinished(
      eventTime: EventTime,
      automaticTransition: Boolean,
      discontinuityFromPositionMs: Long
    ) {
      // Simulate state change to ENDED to record natural ending of playback.
      val finalPlaybackState =
        if (currentPlaybackState == PlaybackStats.PLAYBACK_STATE_ENDED || automaticTransition) {
          PlaybackStats.PLAYBACK_STATE_ENDED
        } else {
          PlaybackStats.PLAYBACK_STATE_ABANDONED
        }
      maybeUpdateMediaTimeHistory(eventTime.realtimeMs, discontinuityFromPositionMs)
      maybeRecordVideoFormatTime(eventTime.realtimeMs)
      maybeRecordAudioFormatTime(eventTime.realtimeMs)
      updatePlaybackState(finalPlaybackState, eventTime)
    }

    /**
     * Notifies the tracker of new events.
     *
     * @param player The [Player].
     * @param eventTime The [EventTime] of the events.
     * @param belongsToPlayback Whether the `eventTime` belongs to this playback.
     * @param discontinuityFromPositionMs The position before the discontinuity from this playback,
     *     or [C.TIME_UNSET] if no discontinuity started from this playback.
     * @param hasDiscontinuity Whether a discontinuity to this playback occurred.
     * @param droppedFrameCount The number of newly dropped frames for this playback.
     * @param hasAudioUnderun Whether a new audio underrun occurred for this playback.
     * @param startedLoading Whether this playback started loading.
     * @param fatalError A fatal error for this playback, or null.
     * @param nonFatalException A non-fatal exception for this playback, or null.
     * @param bandwidthTimeMs The time in milliseconds spent loading for this playback.
     * @param bandwidthBytes The number of bytes loaded for this playback.
     * @param videoFormat A reported downstream video format for this playback, or null.
     * @param audioFormat A reported downstream audio format for this playback, or null.
     * @param videoSize The reported video size for this playback, or null.
     */
    fun onEvents(
      player: Player,
      eventTime: EventTime,
      belongsToPlayback: Boolean,
      discontinuityFromPositionMs: Long,
      hasDiscontinuity: Boolean,
      droppedFrameCount: Int,
      hasAudioUnderun: Boolean,
      startedLoading: Boolean,
      fatalError: PlaybackException?,
      nonFatalException: Exception?,
      bandwidthTimeMs: Long,
      bandwidthBytes: Long,
      videoFormat: Format?,
      audioFormat: Format?,
      videoSize: VideoSize?
    ) {
      if (discontinuityFromPositionMs != C.TIME_UNSET) {
        maybeUpdateMediaTimeHistory(eventTime.realtimeMs, discontinuityFromPositionMs)
        isSeeking = true
      }
      if (player.getPlaybackState() != Player.STATE_BUFFERING) {
        isSeeking = false
      }
      val playerPlaybackState = player.getPlaybackState()
      if (playerPlaybackState == Player.STATE_IDLE
        || playerPlaybackState == Player.STATE_ENDED
        || hasDiscontinuity
      ) {
        isInterruptedByAd = false
      }
      if (fatalError != null) {
        hasFatalError = true
        fatalErrorCount++
        if (keepHistory) {
          fatalErrorHistory.add(EventTimeAndException(eventTime, fatalError))
        }
      } else if (player.getPlayerError() == null) {
        hasFatalError = false
      }
      if (isForeground && !isInterruptedByAd) {
        val currentTracks = checkNotNull(player.getCurrentTracks())!!
        if (!currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) {
          maybeUpdateVideoFormat(eventTime, /* newFormat= */ null)
        }
        if (!currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO)) {
          maybeUpdateAudioFormat(eventTime, /* newFormat= */ null)
        }
      }
      if (videoFormat != null) {
        maybeUpdateVideoFormat(eventTime, videoFormat)
      }
      if (audioFormat != null) {
        maybeUpdateAudioFormat(eventTime, audioFormat)
      }
      if (currentVideoFormat != null
        && currentVideoFormat!!.height == Format.NO_VALUE
        && videoSize != null
      ) {
        val formatWithHeightAndWidth =
          currentVideoFormat!!
            .buildUpon()
            .setWidth(videoSize.width)
            .setHeight(videoSize.height)
            .build()
        maybeUpdateVideoFormat(eventTime, formatWithHeightAndWidth)
      }
      if (startedLoading) {
        this.startedLoading = true
      }
      if (hasAudioUnderun) {
        audioUnderruns++
      }
      this.droppedFrames += droppedFrameCount
      this.bandwidthTimeMs += bandwidthTimeMs
      this.bandwidthBytes += bandwidthBytes
      if (nonFatalException != null) {
        nonFatalErrorCount++
        if (keepHistory) {
          nonFatalErrorHistory.add(EventTimeAndException(eventTime, nonFatalException))
        }
      }

      val newPlaybackState = resolveNewPlaybackState(player)
      val newPlaybackSpeed = checkNotNull(player.getPlaybackParameters())!!.speed
      if (currentPlaybackState != newPlaybackState || currentPlaybackSpeed != newPlaybackSpeed) {
        maybeUpdateMediaTimeHistory(
          eventTime.realtimeMs,
          if (belongsToPlayback) eventTime.eventPlaybackPositionMs else C.TIME_UNSET
        )
        maybeRecordVideoFormatTime(eventTime.realtimeMs)
        maybeRecordAudioFormatTime(eventTime.realtimeMs)
      }
      currentPlaybackSpeed = newPlaybackSpeed
      if (currentPlaybackState != newPlaybackState) {
        updatePlaybackState(newPlaybackState, eventTime)
      }
    }

    /**
     * Builds the playback stats.
     *
     * @param isFinal Whether this is the final build and no further events are expected.
     */
    fun build(isFinal: Boolean): PlaybackStats {
      var playbackStateDurationsMs = this.playbackStateDurationsMs
      var mediaTimeHistory: List<LongArray> = this.mediaTimeHistory
      if (!isFinal) {
        val buildTimeMs = SystemClock.elapsedRealtime()
        playbackStateDurationsMs =
          Arrays.copyOf(this.playbackStateDurationsMs, PlaybackStats.PLAYBACK_STATE_COUNT)
        val lastStateDurationMs = max(0L, buildTimeMs - currentPlaybackStateStartTimeMs)
        playbackStateDurationsMs[currentPlaybackState] += lastStateDurationMs
        maybeUpdateMaxRebufferTimeMs(buildTimeMs)
        maybeRecordVideoFormatTime(buildTimeMs)
        maybeRecordAudioFormatTime(buildTimeMs)
        mediaTimeHistory = ArrayList(this.mediaTimeHistory)
        if (keepHistory && currentPlaybackState == PlaybackStats.PLAYBACK_STATE_PLAYING) {
          mediaTimeHistory.add(guessMediaTimeBasedOnElapsedRealtime(buildTimeMs))
        }
      }
      val isJoinTimeInvalid = this.isJoinTimeInvalid || !hasBeenReady
      val validJoinTimeMs =
        if (isJoinTimeInvalid) {
          C.TIME_UNSET
        } else {
          playbackStateDurationsMs[PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND]
        }
      val hasBackgroundJoin =
        playbackStateDurationsMs[PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND] > 0
      val videoHistory = if (isFinal) videoFormatHistory else ArrayList(videoFormatHistory)
      val audioHistory = if (isFinal) audioFormatHistory else ArrayList(audioFormatHistory)
      return PlaybackStats(
        /* playbackCount= */ 1,
        playbackStateDurationsMs,
        if (isFinal) playbackStateHistory else ArrayList(playbackStateHistory),
        mediaTimeHistory,
        firstReportedTimeMs,
        /* foregroundPlaybackCount= */ if (isForeground) 1 else 0,
        /* abandonedBeforeReadyCount= */ if (hasBeenReady) 0 else 1,
        /* endedCount= */ if (hasEnded) 1 else 0,
        /* backgroundJoiningCount= */ if (hasBackgroundJoin) 1 else 0,
        validJoinTimeMs,
        /* validJoinTimeCount= */ if (isJoinTimeInvalid) 0 else 1,
        pauseCount,
        pauseBufferCount,
        seekCount,
        rebufferCount,
        maxRebufferTimeMs,
        /* adPlaybackCount= */ if (isAd) 1 else 0,
        videoHistory,
        audioHistory,
        videoFormatHeightTimeMs,
        videoFormatHeightTimeProduct,
        videoFormatBitrateTimeMs,
        videoFormatBitrateTimeProduct,
        audioFormatTimeMs,
        audioFormatBitrateTimeProduct,
        /* initialVideoFormatHeightCount= */ if (initialVideoFormatHeight == C.LENGTH_UNSET) 0 else 1,
        /* initialVideoFormatBitrateCount= */ if (initialVideoFormatBitrate == C.LENGTH_UNSET.toLong()) 0 else 1,
        initialVideoFormatHeight,
        initialVideoFormatBitrate,
        /* initialAudioFormatBitrateCount= */ if (initialAudioFormatBitrate == C.LENGTH_UNSET.toLong()) 0 else 1,
        initialAudioFormatBitrate,
        bandwidthTimeMs,
        bandwidthBytes,
        droppedFrames,
        audioUnderruns,
        /* fatalErrorPlaybackCount= */ if (fatalErrorCount > 0) 1 else 0,
        fatalErrorCount,
        nonFatalErrorCount,
        if (isFinal) fatalErrorHistory else ArrayList(fatalErrorHistory),
        if (isFinal) nonFatalErrorHistory else ArrayList(nonFatalErrorHistory)
      )
    }

    private fun updatePlaybackState(newPlaybackState: Int, eventTime: EventTime) {
      checkArgument(eventTime.realtimeMs >= currentPlaybackStateStartTimeMs)
      val stateDurationMs = eventTime.realtimeMs - currentPlaybackStateStartTimeMs
      playbackStateDurationsMs[currentPlaybackState] += stateDurationMs
      if (firstReportedTimeMs == C.TIME_UNSET) {
        firstReportedTimeMs = eventTime.realtimeMs
      }
      isJoinTimeInvalid = isJoinTimeInvalid or isInvalidJoinTransition(currentPlaybackState, newPlaybackState)
      hasBeenReady = hasBeenReady or isReadyState(newPlaybackState)
      hasEnded = hasEnded or (newPlaybackState == PlaybackStats.PLAYBACK_STATE_ENDED)
      if (!isPausedState(currentPlaybackState) && isPausedState(newPlaybackState)) {
        pauseCount++
      }
      if (newPlaybackState == PlaybackStats.PLAYBACK_STATE_SEEKING) {
        seekCount++
      }
      if (!isRebufferingState(currentPlaybackState) && isRebufferingState(newPlaybackState)) {
        rebufferCount++
        lastRebufferStartTimeMs = eventTime.realtimeMs
      }
      if (isRebufferingState(currentPlaybackState)
        && currentPlaybackState != PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING
        && newPlaybackState == PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING
      ) {
        pauseBufferCount++
      }
      maybeUpdateMaxRebufferTimeMs(eventTime.realtimeMs)

      currentPlaybackState = newPlaybackState
      currentPlaybackStateStartTimeMs = eventTime.realtimeMs
      if (keepHistory) {
        playbackStateHistory.add(EventTimeAndPlaybackState(eventTime, currentPlaybackState))
      }
    }

    private fun resolveNewPlaybackState(player: Player): Int {
      val playerPlaybackState = player.getPlaybackState()
      if (isSeeking && isForeground) {
        // Seeking takes precedence over errors such that we report a seek while in error state.
        return PlaybackStats.PLAYBACK_STATE_SEEKING
      } else if (hasFatalError) {
        return PlaybackStats.PLAYBACK_STATE_FAILED
      } else if (!isForeground) {
        // Before the playback becomes foreground, only report background joining and not started.
        return if (startedLoading) {
          PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND
        } else {
          PlaybackStats.PLAYBACK_STATE_NOT_STARTED
        }
      } else if (isInterruptedByAd) {
        return PlaybackStats.PLAYBACK_STATE_INTERRUPTED_BY_AD
      } else if (playerPlaybackState == Player.STATE_ENDED) {
        return PlaybackStats.PLAYBACK_STATE_ENDED
      } else if (playerPlaybackState == Player.STATE_BUFFERING) {
        if (currentPlaybackState == PlaybackStats.PLAYBACK_STATE_NOT_STARTED
          || currentPlaybackState == PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND
          || currentPlaybackState == PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND
          || currentPlaybackState == PlaybackStats.PLAYBACK_STATE_INTERRUPTED_BY_AD
        ) {
          return PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND
        }
        if (!player.getPlayWhenReady()) {
          return PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING
        }
        return if (player.getPlaybackSuppressionReason() != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
          PlaybackStats.PLAYBACK_STATE_SUPPRESSED_BUFFERING
        } else {
          PlaybackStats.PLAYBACK_STATE_BUFFERING
        }
      } else if (playerPlaybackState == Player.STATE_READY) {
        if (!player.getPlayWhenReady()) {
          return PlaybackStats.PLAYBACK_STATE_PAUSED
        }
        return if (player.getPlaybackSuppressionReason() != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
          PlaybackStats.PLAYBACK_STATE_SUPPRESSED
        } else {
          PlaybackStats.PLAYBACK_STATE_PLAYING
        }
      } else if (playerPlaybackState == Player.STATE_IDLE
        && currentPlaybackState != PlaybackStats.PLAYBACK_STATE_NOT_STARTED
      ) {
        // This case only applies for calls to player.stop(). All other IDLE cases are handled by
        // !isForeground, hasFatalError or isSuspended. NOT_STARTED is deliberately ignored.
        return PlaybackStats.PLAYBACK_STATE_STOPPED
      }
      return currentPlaybackState
    }

    private fun maybeUpdateMaxRebufferTimeMs(nowMs: Long) {
      if (isRebufferingState(currentPlaybackState)) {
        val rebufferDurationMs = nowMs - lastRebufferStartTimeMs
        if (maxRebufferTimeMs == C.TIME_UNSET || rebufferDurationMs > maxRebufferTimeMs) {
          maxRebufferTimeMs = rebufferDurationMs
        }
      }
    }

    private fun maybeUpdateMediaTimeHistory(realtimeMs: Long, mediaTimeMs: Long) {
      if (!keepHistory) {
        return
      }
      if (currentPlaybackState != PlaybackStats.PLAYBACK_STATE_PLAYING) {
        if (mediaTimeMs == C.TIME_UNSET) {
          return
        }
        if (mediaTimeHistory.isNotEmpty()) {
          val previousMediaTimeMs = mediaTimeHistory[mediaTimeHistory.size - 1][1]
          if (previousMediaTimeMs != mediaTimeMs) {
            mediaTimeHistory.add(longArrayOf(realtimeMs, previousMediaTimeMs))
          }
        }
      }

      if (mediaTimeMs != C.TIME_UNSET) {
        mediaTimeHistory.add(longArrayOf(realtimeMs, mediaTimeMs))
      } else if (mediaTimeHistory.isNotEmpty()) {
        mediaTimeHistory.add(guessMediaTimeBasedOnElapsedRealtime(realtimeMs))
      }
    }

    private fun guessMediaTimeBasedOnElapsedRealtime(realtimeMs: Long): LongArray {
      val previousKnownMediaTimeHistory = mediaTimeHistory[mediaTimeHistory.size - 1]
      val previousRealtimeMs = previousKnownMediaTimeHistory[0]
      val previousMediaTimeMs = previousKnownMediaTimeHistory[1]
      val elapsedMediaTimeEstimateMs =
        ((realtimeMs - previousRealtimeMs) * currentPlaybackSpeed).toLong()
      val mediaTimeEstimateMs = previousMediaTimeMs + elapsedMediaTimeEstimateMs
      return longArrayOf(realtimeMs, mediaTimeEstimateMs)
    }

    private fun maybeUpdateVideoFormat(eventTime: EventTime, newFormat: Format?) {
      if (Util.areEqual(currentVideoFormat, newFormat)) {
        return
      }
      maybeRecordVideoFormatTime(eventTime.realtimeMs)
      if (newFormat != null) {
        if (initialVideoFormatHeight == C.LENGTH_UNSET && newFormat.height != Format.NO_VALUE) {
          initialVideoFormatHeight = newFormat.height
        }
        if (initialVideoFormatBitrate == C.LENGTH_UNSET.toLong() && newFormat.bitrate != Format.NO_VALUE) {
          initialVideoFormatBitrate = newFormat.bitrate.toLong()
        }
      }
      currentVideoFormat = newFormat
      if (keepHistory) {
        videoFormatHistory.add(EventTimeAndFormat(eventTime, currentVideoFormat))
      }
    }

    private fun maybeUpdateAudioFormat(eventTime: EventTime, newFormat: Format?) {
      if (Util.areEqual(currentAudioFormat, newFormat)) {
        return
      }
      maybeRecordAudioFormatTime(eventTime.realtimeMs)
      if (newFormat != null
        && initialAudioFormatBitrate == C.LENGTH_UNSET.toLong()
        && newFormat.bitrate != Format.NO_VALUE
      ) {
        initialAudioFormatBitrate = newFormat.bitrate.toLong()
      }
      currentAudioFormat = newFormat
      if (keepHistory) {
        audioFormatHistory.add(EventTimeAndFormat(eventTime, currentAudioFormat))
      }
    }

    private fun maybeRecordVideoFormatTime(nowMs: Long) {
      if (currentPlaybackState == PlaybackStats.PLAYBACK_STATE_PLAYING
        && currentVideoFormat != null
      ) {
        val mediaDurationMs = ((nowMs - lastVideoFormatStartTimeMs) * currentPlaybackSpeed).toLong()
        if (currentVideoFormat!!.height != Format.NO_VALUE) {
          videoFormatHeightTimeMs += mediaDurationMs
          videoFormatHeightTimeProduct += mediaDurationMs * currentVideoFormat!!.height
        }
        if (currentVideoFormat!!.bitrate != Format.NO_VALUE) {
          videoFormatBitrateTimeMs += mediaDurationMs
          videoFormatBitrateTimeProduct += mediaDurationMs * currentVideoFormat!!.bitrate
        }
      }
      lastVideoFormatStartTimeMs = nowMs
    }

    private fun maybeRecordAudioFormatTime(nowMs: Long) {
      if (currentPlaybackState == PlaybackStats.PLAYBACK_STATE_PLAYING
        && currentAudioFormat != null
        && currentAudioFormat!!.bitrate != Format.NO_VALUE
      ) {
        val mediaDurationMs = ((nowMs - lastAudioFormatStartTimeMs) * currentPlaybackSpeed).toLong()
        audioFormatTimeMs += mediaDurationMs
        audioFormatBitrateTimeProduct += mediaDurationMs * currentAudioFormat!!.bitrate
      }
      lastAudioFormatStartTimeMs = nowMs
    }

    companion object {
      private fun isReadyState(@PlaybackState state: Int): Boolean {
        return state == PlaybackStats.PLAYBACK_STATE_PLAYING
          || state == PlaybackStats.PLAYBACK_STATE_PAUSED
          || state == PlaybackStats.PLAYBACK_STATE_SUPPRESSED
      }

      private fun isPausedState(@PlaybackState state: Int): Boolean {
        return state == PlaybackStats.PLAYBACK_STATE_PAUSED
          || state == PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING
      }

      private fun isRebufferingState(@PlaybackState state: Int): Boolean {
        return state == PlaybackStats.PLAYBACK_STATE_BUFFERING
          || state == PlaybackStats.PLAYBACK_STATE_PAUSED_BUFFERING
          || state == PlaybackStats.PLAYBACK_STATE_SUPPRESSED_BUFFERING
      }

      private fun isInvalidJoinTransition(
        @PlaybackState oldState: Int,
        @PlaybackState newState: Int
      ): Boolean {
        if (oldState != PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND
          && oldState != PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND
          && oldState != PlaybackStats.PLAYBACK_STATE_INTERRUPTED_BY_AD
        ) {
          return false
        }
        return newState != PlaybackStats.PLAYBACK_STATE_JOINING_BACKGROUND
          && newState != PlaybackStats.PLAYBACK_STATE_JOINING_FOREGROUND
          && newState != PlaybackStats.PLAYBACK_STATE_INTERRUPTED_BY_AD
          && newState != PlaybackStats.PLAYBACK_STATE_PLAYING
          && newState != PlaybackStats.PLAYBACK_STATE_PAUSED
          && newState != PlaybackStats.PLAYBACK_STATE_SUPPRESSED
          && newState != PlaybackStats.PLAYBACK_STATE_ENDED
      }
    }
  }
}
