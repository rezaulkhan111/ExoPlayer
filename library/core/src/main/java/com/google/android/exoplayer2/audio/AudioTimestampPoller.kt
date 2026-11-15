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
package com.google.android.exoplayer2.audio

import android.media.AudioTimestamp
import android.media.AudioTrack
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.util.Util

/**
 * Polls the [AudioTrack] timestamp, if the platform supports it, taking care of polling at
 * the appropriate rate to detect when the timestamp starts to advance.
 *
 * When the audio track isn't paused, call [maybePollTimestamp] regularly to check
 * for timestamp updates. If it returns `true`, call [getTimestampPositionFrames] and
 * [getTimestampSystemTimeUs] to access the updated timestamp, then call
 * [acceptTimestamp] or [rejectTimestamp] to accept or reject it.
 *
 * If [hasTimestamp] returns `true`, call [getTimestampSystemTimeUs] to
 * get the system time at which the latest timestamp was sampled and
 * [getTimestampPositionFrames] to get its position in frames. If [hasAdvancingTimestamp]
 * returns `true`, the caller should assume that the timestamp has been increasing in real
 * time since it was sampled. Otherwise, it may be stationary.
 *
 * Call [reset] when pausing or resuming the track.
 */
internal class AudioTimestampPoller(audioTrack: AudioTrack) {

  /** Timestamp polling states. */
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
  @IntDef(
    STATE_INITIALIZING,
    STATE_TIMESTAMP,
    STATE_TIMESTAMP_ADVANCING,
    STATE_NO_TIMESTAMP,
    STATE_ERROR
  )
  private annotation class State

  private val audioTimestamp: AudioTimestampV19?

  @State private var state: Int = 0
  private var initializeSystemTimeUs: Long = 0
  private var sampleIntervalUs: Long = 0
  private var lastTimestampSampleTimeUs: Long = 0
  private var initialTimestampPositionFrames: Long = 0

  init {
    if (Util.SDK_INT >= 19) {
      @Suppress("NewApi")
      audioTimestamp = AudioTimestampV19(audioTrack)
      reset()
    } else {
      audioTimestamp = null
      updateState(STATE_NO_TIMESTAMP)
    }
  }

  /**
   * Polls the timestamp if required and returns whether it was updated. If `true`, the latest
   * timestamp is available via [getTimestampSystemTimeUs] and
   * [getTimestampPositionFrames], and the caller should call [acceptTimestamp] if the
   * timestamp was valid, or [rejectTimestamp] otherwise. The values returned by
   * [hasTimestamp] and [hasAdvancingTimestamp] may be updated.
   *
   * @param systemTimeUs The current system time, in microseconds.
   * @return Whether the timestamp was updated.
   */
  @Suppress("NewApi")
  fun maybePollTimestamp(systemTimeUs: Long): Boolean {
    if (audioTimestamp == null || (systemTimeUs - lastTimestampSampleTimeUs) < sampleIntervalUs) {
      return false
    }
    lastTimestampSampleTimeUs = systemTimeUs
    var updatedTimestamp = audioTimestamp.maybeUpdateTimestamp()
    when (state) {
      STATE_INITIALIZING -> {
        if (updatedTimestamp) {
          if (audioTimestamp.getTimestampSystemTimeUs() >= initializeSystemTimeUs) {
            // We have an initial timestamp, but don't know if it's advancing yet.
            initialTimestampPositionFrames = audioTimestamp.getTimestampPositionFrames()
            updateState(STATE_TIMESTAMP)
          } else {
            // Drop the timestamp, as it was sampled before the last reset.
            updatedTimestamp = false
          }
        } else if (systemTimeUs - initializeSystemTimeUs > INITIALIZING_DURATION_US) {
          // We haven't received a timestamp for a while, so they probably aren't available for the
          // current audio route. Poll infrequently in case the route changes later.
          // TODO: Ideally we should listen for audio route changes in order to detect when a
          // timestamp becomes available again.
          updateState(STATE_NO_TIMESTAMP)
        }
      }
      STATE_TIMESTAMP -> {
        if (updatedTimestamp) {
          val timestampPositionFrames = audioTimestamp.getTimestampPositionFrames()
          if (timestampPositionFrames > initialTimestampPositionFrames) {
            updateState(STATE_TIMESTAMP_ADVANCING)
          }
        } else {
          reset()
        }
      }
      STATE_TIMESTAMP_ADVANCING -> {
        if (!updatedTimestamp) {
          // The audio route may have changed, so reset polling.
          reset()
        }
      }
      STATE_NO_TIMESTAMP -> {
        if (updatedTimestamp) {
          // The audio route may have changed, so reset polling.
          reset()
        }
      }
      STATE_ERROR -> {
        // Do nothing. If the caller accepts any new timestamp we'll reset polling.
      }
      else -> throw IllegalStateException()
    }
    return updatedTimestamp
  }

  /**
   * Rejects the timestamp last polled in [maybePollTimestamp]. The instance will enter
   * the error state and poll timestamps infrequently until the next call to
   * [acceptTimestamp].
   */
  fun rejectTimestamp() {
    updateState(STATE_ERROR)
  }

  /**
   * Accepts the timestamp last polled in [maybePollTimestamp]. If the instance is in
   * the error state, it will begin to poll timestamps frequently again.
   */
  fun acceptTimestamp() {
    if (state == STATE_ERROR) {
      reset()
    }
  }

  /**
   * Returns whether this instance has a timestamp that can be used to calculate the audio track
   * position. If `true`, call [getTimestampSystemTimeUs] and
   * [getTimestampSystemTimeUs] to access the timestamp.
   */
  fun hasTimestamp(): Boolean {
    return state == STATE_TIMESTAMP || state == STATE_TIMESTAMP_ADVANCING
  }

  /**
   * Returns whether this instance has an advancing timestamp. If `true`, call
   * [getTimestampSystemTimeUs] and [getTimestampSystemTimeUs] to access the timestamp. A
   * current position for the track can be extrapolated based on elapsed real time since the system
   * time at which the timestamp was sampled.
   */
  fun hasAdvancingTimestamp(): Boolean {
    return state == STATE_TIMESTAMP_ADVANCING
  }

  /** Resets polling. Should be called whenever the audio track is paused or resumed. */
  fun reset() {
    if (audioTimestamp != null) {
      updateState(STATE_INITIALIZING)
    }
  }

  /**
   * If [maybePollTimestamp] or [hasTimestamp] returned `true`, returns
   * the system time at which the latest timestamp was sampled, in microseconds.
   */
  @Suppress("NewApi")
  fun getTimestampSystemTimeUs(): Long {
    return audioTimestamp?.getTimestampSystemTimeUs() ?: C.TIME_UNSET
  }

  /**
   * If [maybePollTimestamp] or [hasTimestamp] returned `true`, returns
   * the latest timestamp's position in frames.
   */
  @Suppress("NewApi")
  fun getTimestampPositionFrames(): Long {
    return audioTimestamp?.getTimestampPositionFrames() ?: C.POSITION_UNSET.toLong()
  }

  private fun updateState(@State state: Int) {
    this.state = state
    when (state) {
      STATE_INITIALIZING -> {
        // Force polling a timestamp immediately, and poll quickly.
        lastTimestampSampleTimeUs = 0
        initialTimestampPositionFrames = C.POSITION_UNSET.toLong()
        initializeSystemTimeUs = System.nanoTime() / 1000
        sampleIntervalUs = FAST_POLL_INTERVAL_US.toLong()
      }
      STATE_TIMESTAMP -> {
        sampleIntervalUs = FAST_POLL_INTERVAL_US.toLong()
      }
      STATE_TIMESTAMP_ADVANCING, STATE_NO_TIMESTAMP -> {
        sampleIntervalUs = SLOW_POLL_INTERVAL_US.toLong()
      }
      STATE_ERROR -> {
        sampleIntervalUs = ERROR_POLL_INTERVAL_US.toLong()
      }
      else -> throw IllegalStateException()
    }
  }

  @RequiresApi(19)
  private class AudioTimestampV19(private val audioTrack: AudioTrack) {
    private val audioTimestamp: AudioTimestamp = AudioTimestamp()

    private var rawTimestampFramePositionWrapCount: Long = 0
    private var lastTimestampRawPositionFrames: Long = 0
    private var lastTimestampPositionFrames: Long = 0

    /**
     * Attempts to update the audio track timestamp. Returns `true` if the timestamp was
     * updated, in which case the updated timestamp system time and position can be accessed with
     * [getTimestampSystemTimeUs] and [getTimestampPositionFrames]. Returns `false`
     * if no timestamp is available, in which case those methods should not be called.
     */
    fun maybeUpdateTimestamp(): Boolean {
      val updated = audioTrack.getTimestamp(audioTimestamp)
      if (updated) {
        val rawPositionFrames = audioTimestamp.framePosition
        if (lastTimestampRawPositionFrames > rawPositionFrames) {
          // The value must have wrapped around.
          rawTimestampFramePositionWrapCount++
        }
        lastTimestampRawPositionFrames = rawPositionFrames
        lastTimestampPositionFrames =
          rawPositionFrames + (rawTimestampFramePositionWrapCount shl 32)
      }
      return updated
    }

    fun getTimestampSystemTimeUs(): Long {
      return audioTimestamp.nanoTime / 1000
    }

    fun getTimestampPositionFrames(): Long {
      return lastTimestampPositionFrames
    }
  }

  companion object {
    /** State when first initializing. */
    private const val STATE_INITIALIZING = 0

    /** State when we have a timestamp and we don't know if it's advancing. */
    private const val STATE_TIMESTAMP = 1

    /** State when we have a timestamp and we know it is advancing. */
    private const val STATE_TIMESTAMP_ADVANCING = 2

    /** State when the no timestamp is available. */
    private const val STATE_NO_TIMESTAMP = 3

    /** State when the last timestamp was rejected as invalid. */
    private const val STATE_ERROR = 4

    /** The polling interval for [STATE_INITIALIZING] and [STATE_TIMESTAMP]. */
    private const val FAST_POLL_INTERVAL_US = 10_000

    /**
     * The polling interval for [STATE_TIMESTAMP_ADVANCING] and [STATE_NO_TIMESTAMP].
     */
    private const val SLOW_POLL_INTERVAL_US = 10_000_000

    /** The polling interval for [STATE_ERROR]. */
    private const val ERROR_POLL_INTERVAL_US = 500_000

    /**
     * The minimum duration to remain in [STATE_INITIALIZING] if no timestamps are being
     * returned before transitioning to [STATE_NO_TIMESTAMP].
     */
    private const val INITIALIZING_DURATION_US = 500_000
  }
}
