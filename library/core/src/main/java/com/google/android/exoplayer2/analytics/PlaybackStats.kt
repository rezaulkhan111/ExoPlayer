/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 ( his "License");
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
import androidx.annotation.IntDef
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import kotlin.math.max
import kotlin.math.min

/** Statistics about playbacks. */
class PlaybackStats internal constructor(
  /** The number of individual playbacks for which these stats were collected. */
  @JvmField val playbackCount: Int,
  private val playbackStateDurationsMs: LongArray,
  /**
   * The playback state history as [EventTimeAndPlaybackState]
   * ordered by `EventTime.realTimeMs`.
   */
  @JvmField val playbackStateHistory: List<EventTimeAndPlaybackState>,
  /**
   * The media time history as an ordered list of long[2] arrays with [0] being the realtime as
   * returned by `SystemClock.elapsedRealtime()` and [1] being the media time at this
   * realtime, in milliseconds.
   */
  @JvmField val mediaTimeHistory: List<LongArray>,
  /**
   * The elapsed real-time as returned by `SystemClock.elapsedRealtime()` of the first
   * reported playback event, or [C.TIME_UNSET] if no event has been reported.
   */
  @JvmField val firstReportedTimeMs: Long,
  /** The number of playbacks which were the active foreground playback at some point. */
  @JvmField val foregroundPlaybackCount: Int,
  /** The number of playbacks which were abandoned before they were ready to play. */
  @JvmField val abandonedBeforeReadyCount: Int,
  /** The number of playbacks which reached the ended state at least once. */
  @JvmField val endedCount: Int,
  /** The number of playbacks which were pre-buffered in the background. */
  @JvmField val backgroundJoiningCount: Int,
  /**
   * The total time spent joining the playback, in milliseconds, or [C.TIME_UNSET] if no valid
   * join time could be determined.
   *
   * Note that this does not include background joining time. A join time may be invalid if the
   * playback never reached [PLAYBACK_STATE_PLAYING] or [PLAYBACK_STATE_PAUSED], or
   * joining was interrupted by a seek, stop, or error state.
   */
  @JvmField val totalValidJoinTimeMs: Long,
  /**
   * The number of playbacks with a valid join time as documented in [totalValidJoinTimeMs].
   */
  @JvmField val validJoinTimeCount: Int,
  /** The total number of times a playback has been paused. */
  @JvmField val totalPauseCount: Int,
  /** The total number of times a playback has been paused while rebuffering. */
  @JvmField val totalPauseBufferCount: Int,
  /**
   * The total number of times a seek occurred. This includes seeks happening before playback
   * resumed after another seek.
   */
  @JvmField val totalSeekCount: Int,
  /**
   * The total number of times a rebuffer occurred. This excludes initial joining and buffering
   * after seek.
   */
  @JvmField val totalRebufferCount: Int,
  /**
   * The maximum time spent during a single rebuffer, in milliseconds, or [C.TIME_UNSET] if no
   * rebuffer occurred.
   */
  @JvmField val maxRebufferTimeMs: Long,
  /** The number of ad playbacks. */
  @JvmField val adPlaybackCount: Int,
  /**
   * The video format history as [EventTimeAndFormat] ordered by `EventTime.realTimeMs`.
   * The [Format] may be null if no video format was used.
   */
  @JvmField val videoFormatHistory: List<EventTimeAndFormat>,
  /**
   * The audio format history as [EventTimeAndFormat] ordered by `EventTime.realTimeMs`.
   * The [Format] may be null if no audio format was used.
   */
  @JvmField val audioFormatHistory: List<EventTimeAndFormat>,
  /** The total media time for which video format height data is available, in milliseconds. */
  @JvmField val totalVideoFormatHeightTimeMs: Long,
  /**
   * The accumulated sum of all video format heights, in pixels, times the time the format was used
   * for playback, in milliseconds.
   */
  @JvmField val totalVideoFormatHeightTimeProduct: Long,
  /** The total media time for which video format bitrate data is available, in milliseconds. */
  @JvmField val totalVideoFormatBitrateTimeMs: Long,
  /**
   * The accumulated sum of all video format bitrates, in bits per second, times the time the format
   * was used for playback, in milliseconds.
   */
  @JvmField val totalVideoFormatBitrateTimeProduct: Long,
  /** The total media time for which audio format data is available, in milliseconds. */
  @JvmField val totalAudioFormatTimeMs: Long,
  /**
   * The accumulated sum of all audio format bitrates, in bits per second, times the time the format
   * was used for playback, in milliseconds.
   */
  @JvmField val totalAudioFormatBitrateTimeProduct: Long,
  /** The number of playbacks with initial video format height data. */
  @JvmField val initialVideoFormatHeightCount: Int,
  /** The number of playbacks with initial video format bitrate data. */
  @JvmField val initialVideoFormatBitrateCount: Int,
  /**
   * The total initial video format height for all playbacks, in pixels, or [C.LENGTH_UNSET]
   * if no initial video format data is available.
   */
  @JvmField val totalInitialVideoFormatHeight: Int,
  /**
   * The total initial video format bitrate for all playbacks, in bits per second, or
   * [C.LENGTH_UNSET] if no initial video format data is available.
   */
  @JvmField val totalInitialVideoFormatBitrate: Long,
  /** The number of playbacks with initial audio format bitrate data. */
  @JvmField val initialAudioFormatBitrateCount: Int,
  /**
   * The total initial audio format bitrate for all playbacks, in bits per second, or
   * [C.LENGTH_UNSET] if no initial audio format data is available.
   */
  @JvmField val totalInitialAudioFormatBitrate: Long,
  /** The total time for which bandwidth measurement data is available, in milliseconds. */
  @JvmField val totalBandwidthTimeMs: Long,
  /** The total bytes transferred during [totalBandwidthTimeMs]. */
  @JvmField val totalBandwidthBytes: Long,
  /** The total number of dropped video frames. */
  @JvmField val totalDroppedFrames: Long,
  /** The total number of audio underruns. */
  @JvmField val totalAudioUnderruns: Long,
  /**
   * The total number of playback with at least one fatal error. Errors are fatal if playback
   * stopped due to this error.
   */
  @JvmField val fatalErrorPlaybackCount: Int,
  /** The total number of fatal errors. Errors are fatal if playback stopped due to this error. */
  @JvmField val fatalErrorCount: Int,
  /**
   * The total number of non-fatal errors. Error are non-fatal if playback can recover from the
   * error without stopping.
   */
  @JvmField val nonFatalErrorCount: Int,
  /**
   * The history of fatal errors as [EventTimeAndException] ordered by `EventTime.realTimeMs`.
   * Errors are fatal if playback stopped due to this error.
   */
  @JvmField val fatalErrorHistory: List<EventTimeAndException>,
  /**
   * The history of non-fatal errors as [EventTimeAndException] ordered by `EventTime.realTimeMs`.
   * Errors are non-fatal if playback can recover from the error without stopping.
   */
  @JvmField val nonFatalErrorHistory: List<EventTimeAndException>
) {

  /** Stores a playback state with the event time at which it became active. */
  class EventTimeAndPlaybackState(
    /** The event time at which the playback state became active. */
    @JvmField val eventTime: EventTime,
    /** The playback state that became active. */
    @JvmField @field:PlaybackState val playbackState: Int
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is EventTimeAndPlaybackState) return false
      return playbackState == other.playbackState && eventTime == other.eventTime
    }

    override fun hashCode(): Int {
      var result = eventTime.hashCode()
      result = 31 * result + playbackState
      return result
    }
  }

  /**
   * Stores a format with the event time at which it started being used, or `null` to indicate
   * that no format was used.
   */
  class EventTimeAndFormat(
    /** The event time associated with [format]. */
    @JvmField val eventTime: EventTime,
    /** The format that started being used, or `null` if no format was used. */
    @JvmField val format: Format?
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is EventTimeAndFormat) return false
      if (eventTime != other.eventTime) return false
      return format == other.format
    }

    override fun hashCode(): Int {
      var result = eventTime.hashCode()
      result = 31 * result + (format?.hashCode() ?: 0)
      return result
    }
  }

  /** Stores an exception with the event time at which it occurred. */
  class EventTimeAndException(
    /** The event time at which the exception occurred. */
    @JvmField val eventTime: EventTime,
    /** The exception that was thrown. */
    @JvmField val exception: Exception
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is EventTimeAndException) return false
      if (eventTime != other.eventTime) return false
      return exception == other.exception
    }

    override fun hashCode(): Int {
      var result = eventTime.hashCode()
      result = 31 * result + exception.hashCode()
      return result
    }
  }

  /**
   * State of a playback. One of [PLAYBACK_STATE_NOT_STARTED],
   * [PLAYBACK_STATE_JOINING_FOREGROUND], [PLAYBACK_STATE_JOINING_BACKGROUND],
   * [PLAYBACK_STATE_PLAYING], [PLAYBACK_STATE_PAUSED], [PLAYBACK_STATE_SEEKING],
   * [PLAYBACK_STATE_BUFFERING], [PLAYBACK_STATE_PAUSED_BUFFERING],
   * [PLAYBACK_STATE_SUPPRESSED], [PLAYBACK_STATE_SUPPRESSED_BUFFERING],
   * [PLAYBACK_STATE_ENDED], [PLAYBACK_STATE_STOPPED], [PLAYBACK_STATE_FAILED],
   * [PLAYBACK_STATE_INTERRUPTED_BY_AD] or [PLAYBACK_STATE_ABANDONED].
   */
  @Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
  @Retention(AnnotationRetention.SOURCE)
  @MustBeDocumented
  @IntDef(
    PLAYBACK_STATE_NOT_STARTED,
    PLAYBACK_STATE_JOINING_BACKGROUND,
    PLAYBACK_STATE_JOINING_FOREGROUND,
    PLAYBACK_STATE_PLAYING,
    PLAYBACK_STATE_PAUSED,
    PLAYBACK_STATE_SEEKING,
    PLAYBACK_STATE_BUFFERING,
    PLAYBACK_STATE_PAUSED_BUFFERING,
    PLAYBACK_STATE_SUPPRESSED,
    PLAYBACK_STATE_SUPPRESSED_BUFFERING,
    PLAYBACK_STATE_ENDED,
    PLAYBACK_STATE_STOPPED,
    PLAYBACK_STATE_FAILED,
    PLAYBACK_STATE_INTERRUPTED_BY_AD,
    PLAYBACK_STATE_ABANDONED
  )
  annotation class PlaybackState

  companion object {
    /** Playback has not started (initial state). */
    const val PLAYBACK_STATE_NOT_STARTED = 0

    /** Playback is buffering in the background for initial playback start. */
    const val PLAYBACK_STATE_JOINING_BACKGROUND = 1

    /** Playback is buffering in the foreground for initial playback start. */
    const val PLAYBACK_STATE_JOINING_FOREGROUND = 2

    /** Playback is actively playing. */
    const val PLAYBACK_STATE_PLAYING = 3

    /** Playback is paused but ready to play. */
    const val PLAYBACK_STATE_PAUSED = 4

    /** Playback is handling a seek. */
    const val PLAYBACK_STATE_SEEKING = 5

    /** Playback is buffering to resume active playback. */
    const val PLAYBACK_STATE_BUFFERING = 6

    /** Playback is buffering while paused. */
    const val PLAYBACK_STATE_PAUSED_BUFFERING = 7

    /** Playback is suppressed (e.g. due to audio focus loss). */
    const val PLAYBACK_STATE_SUPPRESSED = 9

    /** Playback is suppressed (e.g. due to audio focus loss) while buffering to resume a playback. */
    const val PLAYBACK_STATE_SUPPRESSED_BUFFERING = 10

    /** Playback has reached the end of the media. */
    const val PLAYBACK_STATE_ENDED = 11

    /** Playback is stopped and can be restarted. */
    const val PLAYBACK_STATE_STOPPED = 12

    /** Playback is stopped due a fatal error and can be retried. */
    const val PLAYBACK_STATE_FAILED = 13

    /** Playback is interrupted by an ad. */
    const val PLAYBACK_STATE_INTERRUPTED_BY_AD = 14

    /** Playback is abandoned before reaching the end of the media. */
    const val PLAYBACK_STATE_ABANDONED = 15

    /** Total number of playback states. */
    internal const val PLAYBACK_STATE_COUNT = 16

    /** Empty playback stats. */
    @JvmField
    val EMPTY: PlaybackStats = merge()

    /**
     * Returns the combined [PlaybackStats] for all input [PlaybackStats].
     *
     * Note that the full history of events is not kept as the history only makes sense in the
     * context of a single playback.
     *
     * @param playbackStats Array of [PlaybackStats] to combine.
     * @return The combined [PlaybackStats].
     */
    @JvmStatic
    fun merge(vararg playbackStats: PlaybackStats): PlaybackStats {
      var playbackCount = 0
      val playbackStateDurationsMs = LongArray(PLAYBACK_STATE_COUNT)
      var firstReportedTimeMs = C.TIME_UNSET
      var foregroundPlaybackCount = 0
      var abandonedBeforeReadyCount = 0
      var endedCount = 0
      var backgroundJoiningCount = 0
      var totalValidJoinTimeMs = C.TIME_UNSET
      var validJoinTimeCount = 0
      var totalPauseCount = 0
      var totalPauseBufferCount = 0
      var totalSeekCount = 0
      var totalRebufferCount = 0
      var maxRebufferTimeMs = C.TIME_UNSET
      var adPlaybackCount = 0
      var totalVideoFormatHeightTimeMs = 0L
      var totalVideoFormatHeightTimeProduct = 0L
      var totalVideoFormatBitrateTimeMs = 0L
      var totalVideoFormatBitrateTimeProduct = 0L
      var totalAudioFormatTimeMs = 0L
      var totalAudioFormatBitrateTimeProduct = 0L
      var initialVideoFormatHeightCount = 0
      var initialVideoFormatBitrateCount = 0
      var totalInitialVideoFormatHeight = C.LENGTH_UNSET
      var totalInitialVideoFormatBitrate = C.LENGTH_UNSET.toLong()
      var initialAudioFormatBitrateCount = 0
      var totalInitialAudioFormatBitrate = C.LENGTH_UNSET.toLong()
      var totalBandwidthTimeMs = 0L
      var totalBandwidthBytes = 0L
      var totalDroppedFrames = 0L
      var totalAudioUnderruns = 0L
      var fatalErrorPlaybackCount = 0
      var fatalErrorCount = 0
      var nonFatalErrorCount = 0
      for (stats in playbackStats) {
        playbackCount += stats.playbackCount
        for (i in 0 until PLAYBACK_STATE_COUNT) {
          playbackStateDurationsMs[i] += stats.playbackStateDurationsMs[i]
        }
        if (firstReportedTimeMs == C.TIME_UNSET) {
          firstReportedTimeMs = stats.firstReportedTimeMs
        } else if (stats.firstReportedTimeMs != C.TIME_UNSET) {
          firstReportedTimeMs = min(firstReportedTimeMs, stats.firstReportedTimeMs)
        }
        foregroundPlaybackCount += stats.foregroundPlaybackCount
        abandonedBeforeReadyCount += stats.abandonedBeforeReadyCount
        endedCount += stats.endedCount
        backgroundJoiningCount += stats.backgroundJoiningCount
        if (totalValidJoinTimeMs == C.TIME_UNSET) {
          totalValidJoinTimeMs = stats.totalValidJoinTimeMs
        } else if (stats.totalValidJoinTimeMs != C.TIME_UNSET) {
          totalValidJoinTimeMs += stats.totalValidJoinTimeMs
        }
        validJoinTimeCount += stats.validJoinTimeCount
        totalPauseCount += stats.totalPauseCount
        totalPauseBufferCount += stats.totalPauseBufferCount
        totalSeekCount += stats.totalSeekCount
        totalRebufferCount += stats.totalRebufferCount
        if (maxRebufferTimeMs == C.TIME_UNSET) {
          maxRebufferTimeMs = stats.maxRebufferTimeMs
        } else if (stats.maxRebufferTimeMs != C.TIME_UNSET) {
          maxRebufferTimeMs = max(maxRebufferTimeMs, stats.maxRebufferTimeMs)
        }
        adPlaybackCount += stats.adPlaybackCount
        totalVideoFormatHeightTimeMs += stats.totalVideoFormatHeightTimeMs
        totalVideoFormatHeightTimeProduct += stats.totalVideoFormatHeightTimeProduct
        totalVideoFormatBitrateTimeMs += stats.totalVideoFormatBitrateTimeMs
        totalVideoFormatBitrateTimeProduct += stats.totalVideoFormatBitrateTimeProduct
        totalAudioFormatTimeMs += stats.totalAudioFormatTimeMs
        totalAudioFormatBitrateTimeProduct += stats.totalAudioFormatBitrateTimeProduct
        initialVideoFormatHeightCount += stats.initialVideoFormatHeightCount
        initialVideoFormatBitrateCount += stats.initialVideoFormatBitrateCount
        if (totalInitialVideoFormatHeight == C.LENGTH_UNSET) {
          totalInitialVideoFormatHeight = stats.totalInitialVideoFormatHeight
        } else if (stats.totalInitialVideoFormatHeight != C.LENGTH_UNSET) {
          totalInitialVideoFormatHeight += stats.totalInitialVideoFormatHeight
        }
        if (totalInitialVideoFormatBitrate == C.LENGTH_UNSET.toLong()) {
          totalInitialVideoFormatBitrate = stats.totalInitialVideoFormatBitrate
        } else if (stats.totalInitialVideoFormatBitrate != C.LENGTH_UNSET.toLong()) {
          totalInitialVideoFormatBitrate += stats.totalInitialVideoFormatBitrate
        }
        initialAudioFormatBitrateCount += stats.initialAudioFormatBitrateCount
        if (totalInitialAudioFormatBitrate == C.LENGTH_UNSET.toLong()) {
          totalInitialAudioFormatBitrate = stats.totalInitialAudioFormatBitrate
        } else if (stats.totalInitialAudioFormatBitrate != C.LENGTH_UNSET.toLong()) {
          totalInitialAudioFormatBitrate += stats.totalInitialAudioFormatBitrate
        }
        totalBandwidthTimeMs += stats.totalBandwidthTimeMs
        totalBandwidthBytes += stats.totalBandwidthBytes
        totalDroppedFrames += stats.totalDroppedFrames
        totalAudioUnderruns += stats.totalAudioUnderruns
        fatalErrorPlaybackCount += stats.fatalErrorPlaybackCount
        fatalErrorCount += stats.fatalErrorCount
        nonFatalErrorCount += stats.nonFatalErrorCount
      }
      return PlaybackStats(
        playbackCount = playbackCount,
        playbackStateDurationsMs = playbackStateDurationsMs,
        playbackStateHistory = emptyList(),
        mediaTimeHistory = emptyList(),
        firstReportedTimeMs = firstReportedTimeMs,
        foregroundPlaybackCount = foregroundPlaybackCount,
        abandonedBeforeReadyCount = abandonedBeforeReadyCount,
        endedCount = endedCount,
        backgroundJoiningCount = backgroundJoiningCount,
        totalValidJoinTimeMs = totalValidJoinTimeMs,
        validJoinTimeCount = validJoinTimeCount,
        totalPauseCount = totalPauseCount,
        totalPauseBufferCount = totalPauseBufferCount,
        totalSeekCount = totalSeekCount,
        totalRebufferCount = totalRebufferCount,
        maxRebufferTimeMs = maxRebufferTimeMs,
        adPlaybackCount = adPlaybackCount,
        videoFormatHistory = emptyList(),
        audioFormatHistory = emptyList(),
        totalVideoFormatHeightTimeMs = totalVideoFormatHeightTimeMs,
        totalVideoFormatHeightTimeProduct = totalVideoFormatHeightTimeProduct,
        totalVideoFormatBitrateTimeMs = totalVideoFormatBitrateTimeMs,
        totalVideoFormatBitrateTimeProduct = totalVideoFormatBitrateTimeProduct,
        totalAudioFormatTimeMs = totalAudioFormatTimeMs,
        totalAudioFormatBitrateTimeProduct = totalAudioFormatBitrateTimeProduct,
        initialVideoFormatHeightCount = initialVideoFormatHeightCount,
        initialVideoFormatBitrateCount = initialVideoFormatBitrateCount,
        totalInitialVideoFormatHeight = totalInitialVideoFormatHeight,
        totalInitialVideoFormatBitrate = totalInitialVideoFormatBitrate,
        initialAudioFormatBitrateCount = initialAudioFormatBitrateCount,
        totalInitialAudioFormatBitrate = totalInitialAudioFormatBitrate,
        totalBandwidthTimeMs = totalBandwidthTimeMs,
        totalBandwidthBytes = totalBandwidthBytes,
        totalDroppedFrames = totalDroppedFrames,
        totalAudioUnderruns = totalAudioUnderruns,
        fatalErrorPlaybackCount = fatalErrorPlaybackCount,
        fatalErrorCount = fatalErrorCount,
        nonFatalErrorCount = nonFatalErrorCount,
        fatalErrorHistory = emptyList(),
        nonFatalErrorHistory = emptyList()
      )
    }
  }

  /**
   * Returns the total time spent in a given [PlaybackState], in milliseconds.
   *
   * @param playbackState A [PlaybackState].
   * @return Total spent in the given playback state, in milliseconds
   */
  fun getPlaybackStateDurationMs(@PlaybackState playbackState: Int): Long {
    return playbackStateDurationsMs[playbackState]
  }

  /**
   * Returns the [PlaybackState] at the given time.
   *
   * @param realtimeMs The time as returned by [SystemClock.elapsedRealtime].
   * @return The [PlaybackState] at that time, or [PLAYBACK_STATE_NOT_STARTED] if the
   *     given time is before the first known playback state in the history.
   */
  @PlaybackState
  fun getPlaybackStateAtTime(realtimeMs: Long): Int {
    var state = PLAYBACK_STATE_NOT_STARTED
    for (timeAndState in playbackStateHistory) {
      if (timeAndState.eventTime.realtimeMs > realtimeMs) {
        break
      }
      state = timeAndState.playbackState
    }
    return state
  }

  /**
   * Returns the estimated media time at the given realtime, in milliseconds, or [C.TIME_UNSET] if
   * the media time history is unknown.
   *
   * @param realtimeMs The realtime as returned by [SystemClock.elapsedRealtime].
   * @return The estimated media time in milliseconds at this realtime, [C.TIME_UNSET] if no
   *     estimate can be given.
   */
  fun getMediaTimeMsAtRealtimeMs(realtimeMs: Long): Long {
    if (mediaTimeHistory.isEmpty()) {
      return C.TIME_UNSET
    }
    var nextIndex = 0
    while (nextIndex < mediaTimeHistory.size && mediaTimeHistory[nextIndex][0] <= realtimeMs) {
      nextIndex++
    }
    if (nextIndex == 0) {
      return mediaTimeHistory[0][1]
    }
    if (nextIndex == mediaTimeHistory.size) {
      return mediaTimeHistory[mediaTimeHistory.size - 1][1]
    }
    val prevRealtimeMs = mediaTimeHistory[nextIndex - 1][0]
    val prevMediaTimeMs = mediaTimeHistory[nextIndex - 1][1]
    val nextRealtimeMs = mediaTimeHistory[nextIndex][0]
    val nextMediaTimeMs = mediaTimeHistory[nextIndex][1]
    val realtimeDurationMs = nextRealtimeMs - prevRealtimeMs
    if (realtimeDurationMs == 0L) {
      return prevMediaTimeMs
    }
    val fraction = (realtimeMs - prevRealtimeMs).toFloat() / realtimeDurationMs
    return prevMediaTimeMs + ((nextMediaTimeMs - prevMediaTimeMs) * fraction).toLong()
  }

  /**
   * Returns the mean time spent joining the playback, in milliseconds, or [C.TIME_UNSET] if
   * no valid join time is available. Only includes playbacks with valid join times as documented in
   * [totalValidJoinTimeMs].
   */
  fun getMeanJoinTimeMs(): Long {
    return if (validJoinTimeCount == 0) C.TIME_UNSET else totalValidJoinTimeMs / validJoinTimeCount
  }

  /**
   * Returns the total time spent joining the playback in foreground, in milliseconds. This does
   * include invalid join times where the playback never reached [PLAYBACK_STATE_PLAYING] or
   * [PLAYBACK_STATE_PAUSED], or joining was interrupted by a seek, stop, or error state.
   */
  fun getTotalJoinTimeMs(): Long {
    return getPlaybackStateDurationMs(PLAYBACK_STATE_JOINING_FOREGROUND)
  }

  /** Returns the total time spent actively playing, in milliseconds. */
  fun getTotalPlayTimeMs(): Long {
    return getPlaybackStateDurationMs(PLAYBACK_STATE_PLAYING)
  }

  /**
   * Returns the mean time spent actively playing per foreground playback, in milliseconds, or
   * [C.TIME_UNSET] if no playback has been in foreground.
   */
  fun getMeanPlayTimeMs(): Long {
    return if (foregroundPlaybackCount == 0) C.TIME_UNSET else getTotalPlayTimeMs() / foregroundPlaybackCount
  }

  /** Returns the total time spent in a paused state, in milliseconds. */
  fun getTotalPausedTimeMs(): Long {
    return (getPlaybackStateDurationMs(PLAYBACK_STATE_PAUSED)
      + getPlaybackStateDurationMs(PLAYBACK_STATE_PAUSED_BUFFERING))
  }

  /**
   * Returns the mean time spent in a paused state per foreground playback, in milliseconds, or
   * [C.TIME_UNSET] if no playback has been in foreground.
   */
  fun getMeanPausedTimeMs(): Long {
    return if (foregroundPlaybackCount == 0) C.TIME_UNSET else getTotalPausedTimeMs() / foregroundPlaybackCount
  }

  /**
   * Returns the total time spent rebuffering, in milliseconds. This excludes initial join times,
   * buffer times after a seek and buffering while paused.
   */
  fun getTotalRebufferTimeMs(): Long {
    return getPlaybackStateDurationMs(PLAYBACK_STATE_BUFFERING)
  }

  /**
   * Returns the mean time spent rebuffering per foreground playback, in milliseconds, or [C.TIME_UNSET]
   * if no playback has been in foreground. This excludes initial join times, buffer times after a seek
   * and buffering while paused.
   */
  fun getMeanRebufferTimeMs(): Long {
    return if (foregroundPlaybackCount == 0) C.TIME_UNSET else getTotalRebufferTimeMs() / foregroundPlaybackCount
  }

  /**
   * Returns the mean time spent during a single rebuffer, in milliseconds, or [C.TIME_UNSET]
   * if no rebuffer was recorded. This excludes initial join times and buffer times after a seek.
   */
  fun getMeanSingleRebufferTimeMs(): Long {
    return if (totalRebufferCount == 0) {
      C.TIME_UNSET
    } else {
      (getPlaybackStateDurationMs(PLAYBACK_STATE_BUFFERING)
        + getPlaybackStateDurationMs(PLAYBACK_STATE_PAUSED_BUFFERING)) / totalRebufferCount
    }
  }

  /**
   * Returns the total time spent from the start of a seek until playback is ready again, in
   * milliseconds.
   */
  fun getTotalSeekTimeMs(): Long {
    return getPlaybackStateDurationMs(PLAYBACK_STATE_SEEKING)
  }

  /**
   * Returns the mean time spent per foreground playback from the start of a seek until playback is
   * ready again, in milliseconds, or [C.TIME_UNSET] if no playback has been in foreground.
   */
  fun getMeanSeekTimeMs(): Long {
    return if (foregroundPlaybackCount == 0) C.TIME_UNSET else getTotalSeekTimeMs() / foregroundPlaybackCount
  }

  /**
   * Returns the mean time spent from the start of a single seek until playback is ready again, in
   * milliseconds, or [C.TIME_UNSET] if no seek occurred.
   */
  fun getMeanSingleSeekTimeMs(): Long {
    return if (totalSeekCount == 0) C.TIME_UNSET else getTotalSeekTimeMs() / totalSeekCount
  }

  /**
   * Returns the total time spent actively waiting for playback, in milliseconds. This includes all
   * join times, rebuffer times and seek times, but excludes times without user intention to play,
   * e.g. all paused states.
   */
  fun getTotalWaitTimeMs(): Long {
    return (getPlaybackStateDurationMs(PLAYBACK_STATE_JOINING_FOREGROUND)
      + getPlaybackStateDurationMs(PLAYBACK_STATE_BUFFERING)
      + getPlaybackStateDurationMs(PLAYBACK_STATE_SEEKING))
  }

  /**
   * Returns the mean time spent actively waiting for playback per foreground playback, in
   * milliseconds, or [C.TIME_UNSET] if no playback has been in foreground. This includes all
   * join times, rebuffer times and seek times, but excludes times without user intention to play,
   * e.g. all paused states.
   */
  fun getMeanWaitTimeMs(): Long {
    return if (foregroundPlaybackCount == 0) C.TIME_UNSET else getTotalWaitTimeMs() / foregroundPlaybackCount
  }

  /** Returns the total time spent playing or actively waiting for playback, in milliseconds. */
  fun getTotalPlayAndWaitTimeMs(): Long {
    return getTotalPlayTimeMs() + getTotalWaitTimeMs()
  }

  /**
   * Returns the mean time spent playing or actively waiting for playback per foreground playback,
   * in milliseconds, or [C.TIME_UNSET] if no playback has been in foreground.
   */
  fun getMeanPlayAndWaitTimeMs(): Long {
    return if (foregroundPlaybackCount == 0) C.TIME_UNSET else getTotalPlayAndWaitTimeMs() / foregroundPlaybackCount
  }

  /** Returns the total time covered by any playback state, in milliseconds. */
  fun getTotalElapsedTimeMs(): Long {
    var totalTimeMs = 0L
    for (i in 0 until PLAYBACK_STATE_COUNT) {
      totalTimeMs += playbackStateDurationsMs[i]
    }
    return totalTimeMs
  }

  /**
   * Returns the mean time covered by any playback state per playback, in milliseconds, or [C.TIME_UNSET]
   * if no playback was recorded.
   */
  fun getMeanElapsedTimeMs(): Long {
    return if (playbackCount == 0) C.TIME_UNSET else getTotalElapsedTimeMs() / playbackCount
  }

  /**
   * Returns the ratio of foreground playbacks which were abandoned before they were ready to play,
   * or `0.0` if no playback has been in foreground.
   */
  fun getAbandonedBeforeReadyRatio(): Float {
    val foregroundAbandonedBeforeReady = abandonedBeforeReadyCount - (playbackCount - foregroundPlaybackCount)
    return if (foregroundPlaybackCount == 0) {
      0f
    } else {
      foregroundAbandonedBeforeReady.toFloat() / foregroundPlaybackCount
    }
  }

  /**
   * Returns the ratio of foreground playbacks which reached the ended state at least once, or
   * `0.0` if no playback has been in foreground.
   */
  fun getEndedRatio(): Float {
    return if (foregroundPlaybackCount == 0) 0f else endedCount.toFloat() / foregroundPlaybackCount
  }

  /**
   * Returns the mean number of times a playback has been paused per foreground playback, or `0.0`
   * if no playback has been in foreground.
   */
  fun getMeanPauseCount(): Float {
    return if (foregroundPlaybackCount == 0) 0f else totalPauseCount.toFloat() / foregroundPlaybackCount
  }

  /**
   * Returns the mean number of times a playback has been paused while rebuffering per foreground
   * playback, or `0.0` if no playback has been in foreground.
   */
  fun getMeanPauseBufferCount(): Float {
    return if (foregroundPlaybackCount == 0) 0f else totalPauseBufferCount.toFloat() / foregroundPlaybackCount
  }

  /**
   * Returns the mean number of times a seek occurred per foreground playback, or `0.0` if no
   * playback has been in foreground. This includes seeks happening before playback resumed after
   * another seek.
   */
  fun getMeanSeekCount(): Float {
    return if (foregroundPlaybackCount == 0) 0f else totalSeekCount.toFloat() / foregroundPlaybackCount
  }

  /**
   * Returns the mean number of times a rebuffer occurred per foreground playback, or `0.0` if
   * no playback has been in foreground. This excludes initial joining and buffering after seek.
   */
  fun getMeanRebufferCount(): Float {
    return if (foregroundPlaybackCount == 0) 0f else totalRebufferCount.toFloat() / foregroundPlaybackCount
  }

  /**
   * Returns the ratio of wait times to the total time spent playing and waiting, or `0.0` if
   * no time was spend playing or waiting. This is equivalent to [getTotalWaitTimeMs] /
   * [getTotalPlayAndWaitTimeMs] and also to [getJoinTimeRatio] + [getRebufferTimeRatio] + [getSeekTimeRatio].
   */
  fun getWaitTimeRatio(): Float {
    val playAndWaitTimeMs = getTotalPlayAndWaitTimeMs()
    return if (playAndWaitTimeMs == 0L) 0f else getTotalWaitTimeMs().toFloat() / playAndWaitTimeMs
  }

  /**
   * Returns the ratio of foreground join time to the total time spent playing and waiting, or
   * `0.0` if no time was spend playing or waiting. This is equivalent to [getTotalJoinTimeMs] / [getTotalPlayAndWaitTimeMs].
   */
  fun getJoinTimeRatio(): Float {
    val playAndWaitTimeMs = getTotalPlayAndWaitTimeMs()
    return if (playAndWaitTimeMs == 0L) 0f else getTotalJoinTimeMs().toFloat() / playAndWaitTimeMs
  }

  /**
   * Returns the ratio of rebuffer time to the total time spent playing and waiting, or `0.0`
   * if no time was spend playing or waiting. This is equivalent to [getTotalRebufferTimeMs] / [getTotalPlayAndWaitTimeMs].
   */
  fun getRebufferTimeRatio(): Float {
    val playAndWaitTimeMs = getTotalPlayAndWaitTimeMs()
    return if (playAndWaitTimeMs == 0L) 0f else getTotalRebufferTimeMs().toFloat() / playAndWaitTimeMs
  }

  /**
   * Returns the ratio of seek time to the total time spent playing and waiting, or `0.0` if
   * no time was spend playing or waiting. This is equivalent to [getTotalSeekTimeMs] / [getTotalPlayAndWaitTimeMs].
   */
  fun getSeekTimeRatio(): Float {
    val playAndWaitTimeMs = getTotalPlayAndWaitTimeMs()
    return if (playAndWaitTimeMs == 0L) 0f else getTotalSeekTimeMs().toFloat() / playAndWaitTimeMs
  }

  /**
   * Returns the rate of rebuffer events, in rebuffers per play time second, or `0.0` if no
   * time was spend playing. This is equivalent to 1.0 / [getMeanTimeBetweenRebuffers].
   */
  fun getRebufferRate(): Float {
    val playTimeMs = getTotalPlayTimeMs()
    return if (playTimeMs == 0L) 0f else 1000f * totalRebufferCount / playTimeMs
  }

  /**
   * Returns the mean play time between rebuffer events, in seconds. This is equivalent to 1.0 /
   * [getRebufferRate]. Note that this may return [Float.POSITIVE_INFINITY].
   */
  fun getMeanTimeBetweenRebuffers(): Float {
    return 1f / getRebufferRate()
  }

  /**
   * Returns the mean initial video format height, in pixels, or [C.LENGTH_UNSET] if no video
   * format data is available.
   */
  fun getMeanInitialVideoFormatHeight(): Int {
    return if (initialVideoFormatHeightCount == 0) {
      C.LENGTH_UNSET
    } else {
      totalInitialVideoFormatHeight / initialVideoFormatHeightCount
    }
  }

  /**
   * Returns the mean initial video format bitrate, in bits per second, or [C.LENGTH_UNSET] if
   * no video format data is available.
   */
  fun getMeanInitialVideoFormatBitrate(): Int {
    return if (initialVideoFormatBitrateCount == 0) {
      C.LENGTH_UNSET
    } else {
      (totalInitialVideoFormatBitrate / initialVideoFormatBitrateCount).toInt()
    }
  }

  /**
   * Returns the mean initial audio format bitrate, in bits per second, or [C.LENGTH_UNSET] if
   * no audio format data is available.
   */
  fun getMeanInitialAudioFormatBitrate(): Int {
    return if (initialAudioFormatBitrateCount == 0) {
      C.LENGTH_UNSET
    } else {
      (totalInitialAudioFormatBitrate / initialAudioFormatBitrateCount).toInt()
    }
  }

  /**
   * Returns the mean video format height, in pixels, or [C.LENGTH_UNSET] if no video format
   * data is available. This is a weighted average taking the time the format was used for playback
   * into account.
   */
  fun getMeanVideoFormatHeight(): Int {
    return if (totalVideoFormatHeightTimeMs == 0L) {
      C.LENGTH_UNSET
    } else {
      (totalVideoFormatHeightTimeProduct / totalVideoFormatHeightTimeMs).toInt()
    }
  }

  /**
   * Returns the mean video format bitrate, in bits per second, or [C.LENGTH_UNSET] if no
   * video format data is available. This is a weighted average taking the time the format was used
   * for playback into account.
   */
  fun getMeanVideoFormatBitrate(): Int {
    return if (totalVideoFormatBitrateTimeMs == 0L) {
      C.LENGTH_UNSET
    } else {
      (totalVideoFormatBitrateTimeProduct / totalVideoFormatBitrateTimeMs).toInt()
    }
  }

  /**
   * Returns the mean audio format bitrate, in bits per second, or [C.LENGTH_UNSET] if no
   * audio format data is available. This is a weighted average taking the time the format was used
   * for playback into account.
   */
  fun getMeanAudioFormatBitrate(): Int {
    return if (totalAudioFormatTimeMs == 0L) {
      C.LENGTH_UNSET
    } else {
      (totalAudioFormatBitrateTimeProduct / totalAudioFormatTimeMs).toInt()
    }
  }

  /**
   * Returns the mean network bandwidth based on transfer measurements, in bits per second, or
   * [C.LENGTH_UNSET] if no transfer data is available.
   */
  fun getMeanBandwidth(): Int {
    return if (totalBandwidthTimeMs == 0L) {
      C.LENGTH_UNSET
    } else {
      (totalBandwidthBytes * 8000 / totalBandwidthTimeMs).toInt()
    }
  }

  /**
   * Returns the mean rate at which video frames are dropped, in dropped frames per play time
   * second, or `0.0` if no time was spent playing.
   */
  fun getDroppedFramesRate(): Float {
    val playTimeMs = getTotalPlayTimeMs()
    return if (playTimeMs == 0L) 0f else 1000f * totalDroppedFrames / playTimeMs
  }

  /**
   * Returns the mean rate at which audio underruns occurred, in underruns per play time second, or
   * `0.0` if no time was spent playing.
   */
  fun getAudioUnderrunRate(): Float {
    val playTimeMs = getTotalPlayTimeMs()
    return if (playTimeMs == 0L) 0f else 1000f * totalAudioUnderruns / playTimeMs
  }

  /**
   * Returns the ratio of foreground playbacks which experienced fatal errors, or `0.0` if no
   * playback has been in foreground.
   */
  fun getFatalErrorRatio(): Float {
    return if (foregroundPlaybackCount == 0) {
      0f
    } else {
      fatalErrorPlaybackCount.toFloat() / foregroundPlaybackCount
    }
  }

  /**
   * Returns the rate of fatal errors, in errors per play time second, or `0.0` if no time was
   * spend playing. This is equivalent to 1.0 / [getMeanTimeBetweenFatalErrors].
   */
  fun getFatalErrorRate(): Float {
    val playTimeMs = getTotalPlayTimeMs()
    return if (playTimeMs == 0L) 0f else 1000f * fatalErrorCount / playTimeMs
  }

  /**
   * Returns the mean play time between fatal errors, in seconds. This is equivalent to 1.0 /
   * [getFatalErrorRate]. Note that this may return [Float.POSITIVE_INFINITY].
   */
  fun getMeanTimeBetweenFatalErrors(): Float {
    return 1f / getFatalErrorRate()
  }

  /**
   * Returns the mean number of non-fatal errors per foreground playback, or `0.0` if no
   * playback has been in foreground.
   */
  fun getMeanNonFatalErrorCount(): Float {
    return if (foregroundPlaybackCount == 0) 0f else nonFatalErrorCount.toFloat() / foregroundPlaybackCount
  }

  /**
   * Returns the rate of non-fatal errors, in errors per play time second, or `0.0` if no time
   * was spend playing. This is equivalent to 1.0 / [getMeanTimeBetweenNonFatalErrors].
   */
  fun getNonFatalErrorRate(): Float {
    val playTimeMs = getTotalPlayTimeMs()
    return if (playTimeMs == 0L) 0f else 1000f * nonFatalErrorCount / playTimeMs
  }

  /**
   * Returns the mean play time between non-fatal errors, in seconds. This is equivalent to 1.0 /
   * [getNonFatalErrorRate]. Note that this may return [Float.POSITIVE_INFINITY].
   */
  fun getMeanTimeBetweenNonFatalErrors(): Float {
    return 1f / getNonFatalErrorRate()
  }
}
