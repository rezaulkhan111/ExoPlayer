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

import android.media.AudioTrack
import android.os.SystemClock
import androidx.annotation.IntDef
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Util
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Wraps an [AudioTrack], exposing a position based on [AudioTrack.getPlaybackHeadPosition] and
 * [AudioTrack.getTimestamp].
 *
 * Call [setAudioTrack] to set the audio track to wrap. Call [mayHandleBuffer] if there is input
 * data to write to the track. If it returns false, the audio track position is stabilizing and no
 * data may be written. Call [start] immediately before calling [AudioTrack.play]. Call [pause] when
 * pausing the track. Call [handleEndOfStream] when no more data will be written to the track. When
 * the audio track will no longer be used, call [reset].
 */
internal class AudioTrackPositionTracker(listener: Listener) {

  /** Listener for position tracker events. */
  interface Listener {
    /**
     * Called when the position tracker's position has increased for the first time since it was
     * last paused or reset.
     *
     * @param playoutStartSystemTimeMs The approximate derived [System.currentTimeMillis] at
     *     which playout started.
     */
    fun onPositionAdvancing(playoutStartSystemTimeMs: Long)

    /**
     * Called when the frame position is too far from the expected frame position.
     *
     * @param audioTimestampPositionFrames The frame position of the last known audio track
     *     timestamp.
     * @param audioTimestampSystemTimeUs The system time associated with the last known audio track
     *     timestamp, in microseconds.
     * @param systemTimeUs The current time.
     * @param playbackPositionUs The current playback head position in microseconds.
     */
    fun onPositionFramesMismatch(
      audioTimestampPositionFrames: Long,
      audioTimestampSystemTimeUs: Long,
      systemTimeUs: Long,
      playbackPositionUs: Long
    )

    /**
     * Called when the system time associated with the last known audio track timestamp is
     * unexpectedly far from the current time.
     *
     * @param audioTimestampPositionFrames The frame position of the last known audio track
     *     timestamp.
     * @param audioTimestampSystemTimeUs The system time associated with the last known audio track
     *     timestamp, in microseconds.
     * @param systemTimeUs The current time.
     * @param playbackPositionUs The current playback head position in microseconds.
     */
    fun onSystemTimeUsMismatch(
      audioTimestampPositionFrames: Long,
      audioTimestampSystemTimeUs: Long,
      systemTimeUs: Long,
      playbackPositionUs: Long
    )

    /**
     * Called when the audio track has provided an invalid latency.
     *
     * @param latencyUs The reported latency in microseconds.
     */
    fun onInvalidLatency(latencyUs: Long)

    /**
     * Called when the audio track runs out of data to play.
     *
     * @param bufferSize The size of the sink's buffer, in bytes.
     * @param bufferSizeMs The size of the sink's buffer, in milliseconds, if it is configured for
     *     PCM output. [C.TIME_UNSET] if it is configured for encoded audio output, as the
     *     buffered media can have a variable bitrate so the duration may be unknown.
     */
    fun onUnderrun(bufferSize: Int, bufferSizeMs: Long)
  }

  /** [AudioTrack] playback states. */
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
  @IntDef(PLAYSTATE_STOPPED, PLAYSTATE_PAUSED, PLAYSTATE_PLAYING)
  private annotation class PlayState

  private val listener: Listener = checkNotNull(listener)!!
  private val playheadOffsets: LongArray = LongArray(MAX_PLAYHEAD_OFFSET_COUNT)

  private var audioTrack: AudioTrack? = null
  private var outputPcmFrameSize: Int = 0
  private var bufferSize: Int = 0
  private var audioTimestampPoller: AudioTimestampPoller? = null
  private var outputSampleRate: Int = 0
  private var needsPassthroughWorkarounds: Boolean = false
  private var bufferSizeUs: Long = 0
  private var audioTrackPlaybackSpeed: Float = 1f
  private var notifiedPositionIncreasing: Boolean = false

  private var smoothedPlayheadOffsetUs: Long = 0
  private var lastPlayheadSampleTimeUs: Long = 0

  private var getLatencyMethod: Method? = null
  private var latencyUs: Long = 0
  private var hasData: Boolean = false

  private var isOutputPcm: Boolean = false
  private var lastLatencySampleTimeUs: Long = 0
  private var lastRawPlaybackHeadPosition: Long = 0
  private var rawPlaybackHeadWrapCount: Long = 0
  private var passthroughWorkaroundPauseOffset: Long = 0
  private var nextPlayheadOffsetIndex: Int = 0
  private var playheadOffsetCount: Int = 0
  private var stopTimestampUs: Long = 0
  private var forceResetWorkaroundTimeMs: Long = 0
  private var stopPlaybackHeadPosition: Long = 0
  private var endPlaybackHeadPosition: Long = 0

  // Results from the previous call to getCurrentPositionUs.
  private var lastPositionUs: Long = 0
  private var lastSystemTimeUs: Long = 0
  private var lastSampleUsedGetTimestampMode: Boolean = false

  // Results from the last call to getCurrentPositionUs that used a different sample mode.
  private var previousModePositionUs: Long = 0
  private var previousModeSystemTimeUs: Long = 0

  init {
    if (Util.SDK_INT >= 18) {
      try {
        getLatencyMethod = AudioTrack::class.java.getMethod("getLatency")
      } catch (e: NoSuchMethodException) {
        // There's no guarantee this method exists. Do nothing.
      }
    }
  }

  /**
   * Sets the [AudioTrack] to wrap. Subsequent method calls on this instance relate to this
   * track's position, until the next call to [reset].
   *
   * @param audioTrack The audio track to wrap.
   * @param isPassthrough Whether passthrough mode is being used.
   * @param outputEncoding The encoding of the audio track.
   * @param outputPcmFrameSize For PCM output encodings, the frame size. The value is ignored
   *     otherwise.
   * @param bufferSize The audio track buffer size in bytes.
   */
  fun setAudioTrack(
    audioTrack: AudioTrack,
    isPassthrough: Boolean,
    outputEncoding: Int,
    outputPcmFrameSize: Int,
    bufferSize: Int
  ) {
    this.audioTrack = audioTrack
    this.outputPcmFrameSize = outputPcmFrameSize
    this.bufferSize = bufferSize
    audioTimestampPoller = AudioTimestampPoller(audioTrack)
    outputSampleRate = audioTrack.sampleRate
    needsPassthroughWorkarounds = isPassthrough && needsPassthroughWorkarounds(outputEncoding)
    isOutputPcm = Util.isEncodingLinearPcm(outputEncoding)
    bufferSizeUs = if (isOutputPcm) framesToDurationUs(bufferSize.toLong() / outputPcmFrameSize) else C.TIME_UNSET
    lastRawPlaybackHeadPosition = 0
    rawPlaybackHeadWrapCount = 0
    passthroughWorkaroundPauseOffset = 0
    hasData = false
    stopTimestampUs = C.TIME_UNSET
    forceResetWorkaroundTimeMs = C.TIME_UNSET
    lastLatencySampleTimeUs = 0
    latencyUs = 0
    audioTrackPlaybackSpeed = 1f
  }

  fun setAudioTrackPlaybackSpeed(audioTrackPlaybackSpeed: Float) {
    this.audioTrackPlaybackSpeed = audioTrackPlaybackSpeed
    // Extrapolation from the last audio timestamp relies on the audio rate being constant, so we
    // reset audio timestamp tracking and wait for a new timestamp.
    audioTimestampPoller?.reset()
  }

  fun getCurrentPositionUs(sourceEnded: Boolean): Long {
    val track = checkNotNull(audioTrack)!!
    if (track.playState == PLAYSTATE_PLAYING) {
      maybeSampleSyncParams()
    }

    // If the device supports it, use the playback timestamp from AudioTrack.getTimestamp.
    // Otherwise, derive a smoothed position by sampling the track's frame position.
    val systemTimeUs = System.nanoTime() / 1000
    var positionUs: Long
    val poller = checkNotNull(audioTimestampPoller)!!
    val useGetTimestampMode = poller.hasAdvancingTimestamp()
    if (useGetTimestampMode) {
      // Calculate the speed-adjusted position using the timestamp (which may be in the future).
      val timestampPositionFrames = poller.getTimestampPositionFrames()
      val timestampPositionUs = framesToDurationUs(timestampPositionFrames)
      var elapsedSinceTimestampUs = systemTimeUs - poller.getTimestampSystemTimeUs()
      elapsedSinceTimestampUs =
        Util.getMediaDurationForPlayoutDuration(elapsedSinceTimestampUs, audioTrackPlaybackSpeed)
      positionUs = timestampPositionUs + elapsedSinceTimestampUs
    } else {
      if (playheadOffsetCount == 0) {
        // The AudioTrack has started, but we don't have any samples to compute a smoothed position.
        positionUs = getPlaybackHeadPositionUs()
      } else {
        // getPlaybackHeadPositionUs() only has a granularity of ~20 ms, so we base the position off
        // the system clock (and a smoothed offset between it and the playhead position) so as to
        // prevent jitter in the reported positions.
        positionUs = systemTimeUs + smoothedPlayheadOffsetUs
      }
      if (!sourceEnded) {
        positionUs = max(0L, positionUs - latencyUs)
      }
    }

    if (lastSampleUsedGetTimestampMode != useGetTimestampMode) {
      // We've switched sampling mode.
      previousModeSystemTimeUs = lastSystemTimeUs
      previousModePositionUs = lastPositionUs
    }
    val elapsedSincePreviousModeUs = systemTimeUs - previousModeSystemTimeUs
    if (elapsedSincePreviousModeUs < MODE_SWITCH_SMOOTHING_DURATION_US) {
      // Use a ramp to smooth between the old mode and the new one to avoid introducing a sudden
      // jump if the two modes disagree.
      val previousModeProjectedPositionUs =
        previousModePositionUs +
          Util.getMediaDurationForPlayoutDuration(
            elapsedSincePreviousModeUs, audioTrackPlaybackSpeed
          )
      // A ramp consisting of 1000 points distributed over MODE_SWITCH_SMOOTHING_DURATION_US.
      val rampPoint = (elapsedSincePreviousModeUs * 1000) / MODE_SWITCH_SMOOTHING_DURATION_US
      positionUs *= rampPoint
      positionUs += (1000 - rampPoint) * previousModeProjectedPositionUs
      positionUs /= 1000
    }

    if (!notifiedPositionIncreasing && positionUs > lastPositionUs) {
      notifiedPositionIncreasing = true
      val mediaDurationSinceLastPositionUs = Util.usToMs(positionUs - lastPositionUs)
      val playoutDurationSinceLastPositionUs =
        Util.getPlayoutDurationForMediaDuration(
          mediaDurationSinceLastPositionUs, audioTrackPlaybackSpeed
        )
      val playoutStartSystemTimeMs =
        System.currentTimeMillis() - Util.usToMs(playoutDurationSinceLastPositionUs)
      listener.onPositionAdvancing(playoutStartSystemTimeMs)
    }

    lastSystemTimeUs = systemTimeUs
    lastPositionUs = positionUs
    lastSampleUsedGetTimestampMode = useGetTimestampMode

    return positionUs
  }

  /** Starts position tracking. Must be called immediately before [AudioTrack.play]. */
  fun start() {
    checkNotNull(audioTimestampPoller)!!.reset()
  }

  /** Returns whether the audio track is in the playing state. */
  fun isPlaying(): Boolean {
    return checkNotNull(audioTrack)!!.playState == PLAYSTATE_PLAYING
  }

  /**
   * Checks the state of the audio track and returns whether the caller can write data to the track.
   * Notifies [Listener.onUnderrun] if the track has underrun.
   *
   * @param writtenFrames The number of frames that have been written.
   * @return Whether the caller can write data to the track.
   */
  fun mayHandleBuffer(writtenFrames: Long): Boolean {
    val playState = checkNotNull(audioTrack)!!.playState
    if (needsPassthroughWorkarounds) {
      // An AC-3 audio track continues to play data written while it is paused. Stop writing so its
      // buffer empties. See [Internal: b/18899620].
      if (playState == PLAYSTATE_PAUSED) {
        // We force an underrun to pause the track, so don't notify the listener in this case.
        hasData = false
        return false
      }

      // A new AC-3 audio track's playback position continues to increase from the old track's
      // position for a short time after is has been released. Avoid writing data until the playback
      // head position actually returns to zero.
      if (playState == PLAYSTATE_STOPPED && getPlaybackHeadPosition() == 0L) {
        return false
      }
    }

    val hadData = hasData
    hasData = hasPendingData(writtenFrames)
    if (hadData && !hasData && playState != PLAYSTATE_STOPPED) {
      listener.onUnderrun(bufferSize, Util.usToMs(bufferSizeUs))
    }

    return true
  }

  /**
   * Returns an estimate of the number of additional bytes that can be written to the audio track's
   * buffer without running out of space.
   *
   * May only be called if the output encoding is one of the PCM encodings.
   *
   * @param writtenBytes The number of bytes written to the audio track so far.
   * @return An estimate of the number of bytes that can be written.
   */
  fun getAvailableBufferSize(writtenBytes: Long): Int {
    val bytesPending = (writtenBytes - (getPlaybackHeadPosition() * outputPcmFrameSize)).toInt()
    return bufferSize - bytesPending
  }

  /** Returns whether the track is in an invalid state and must be recreated. */
  fun isStalled(writtenFrames: Long): Boolean {
    return forceResetWorkaroundTimeMs != C.TIME_UNSET
      && writtenFrames > 0
      && SystemClock.elapsedRealtime() - forceResetWorkaroundTimeMs >= FORCE_RESET_WORKAROUND_TIMEOUT_MS
  }

  /**
   * Records the writing position at which the stream ended, so that the reported position can
   * continue to increment while remaining data is played out.
   *
   * @param writtenFrames The number of frames that have been written.
   */
  fun handleEndOfStream(writtenFrames: Long) {
    stopPlaybackHeadPosition = getPlaybackHeadPosition()
    stopTimestampUs = SystemClock.elapsedRealtime() * 1000
    endPlaybackHeadPosition = writtenFrames
  }

  /**
   * Returns whether the audio track has any pending data to play out at its current position.
   *
   * @param writtenFrames The number of frames written to the audio track.
   * @return Whether the audio track has any pending data to play out.
   */
  fun hasPendingData(writtenFrames: Long): Boolean {
    return writtenFrames > getPlaybackHeadPosition() || forceHasPendingData()
  }

  /**
   * Pauses the audio track position tracker, returning whether the audio track needs to be paused
   * to cause playback to pause. If `false` is returned the audio track will pause without
   * further interaction, as the end of stream has been handled.
   */
  fun pause(): Boolean {
    resetSyncParams()
    if (stopTimestampUs == C.TIME_UNSET) {
      // The audio track is going to be paused, so reset the timestamp poller to ensure it doesn't
      // supply an advancing position.
      checkNotNull(audioTimestampPoller)!!.reset()
      return true
    }
    // We've handled the end of the stream already, so there's no need to pause the track.
    return false
  }

  /**
   * Resets the position tracker. Should be called when the audio track previously passed to
   * [setAudioTrack] is no longer in use.
   */
  fun reset() {
    resetSyncParams()
    audioTrack = null
    audioTimestampPoller = null
  }

  private fun maybeSampleSyncParams() {
    val playbackPositionUs = getPlaybackHeadPositionUs()
    if (playbackPositionUs == 0L) {
      // The AudioTrack hasn't output anything yet.
      return
    }
    val systemTimeUs = System.nanoTime() / 1000
    if (systemTimeUs - lastPlayheadSampleTimeUs >= MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US) {
      // Take a new sample and update the smoothed offset between the system clock and the playhead.
      playheadOffsets[nextPlayheadOffsetIndex] = playbackPositionUs - systemTimeUs
      nextPlayheadOffsetIndex = (nextPlayheadOffsetIndex + 1) % MAX_PLAYHEAD_OFFSET_COUNT
      if (playheadOffsetCount < MAX_PLAYHEAD_OFFSET_COUNT) {
        playheadOffsetCount++
      }
      lastPlayheadSampleTimeUs = systemTimeUs
      smoothedPlayheadOffsetUs = 0
      for (i in 0 until playheadOffsetCount) {
        smoothedPlayheadOffsetUs += playheadOffsets[i] / playheadOffsetCount
      }
    }

    if (needsPassthroughWorkarounds) {
      // Don't sample the timestamp and latency if this is an AC-3 passthrough AudioTrack on
      // platform API versions 21/22, as incorrect values are returned. See [Internal: b/21145353].
      return
    }

    maybePollAndCheckTimestamp(systemTimeUs, playbackPositionUs)
    maybeUpdateLatency(systemTimeUs)
  }

  private fun maybePollAndCheckTimestamp(systemTimeUs: Long, playbackPositionUs: Long) {
    val poller = checkNotNull(audioTimestampPoller)!!
    if (!poller.maybePollTimestamp(systemTimeUs)) {
      return
    }

    // Check the timestamp and accept/reject it.
    val audioTimestampSystemTimeUs = poller.getTimestampSystemTimeUs()
    val audioTimestampPositionFrames = poller.getTimestampPositionFrames()
    if (abs(audioTimestampSystemTimeUs - systemTimeUs) > MAX_AUDIO_TIMESTAMP_OFFSET_US) {
      listener.onSystemTimeUsMismatch(
        audioTimestampPositionFrames,
        audioTimestampSystemTimeUs,
        systemTimeUs,
        playbackPositionUs
      )
      poller.rejectTimestamp()
    } else if (abs(framesToDurationUs(audioTimestampPositionFrames) - playbackPositionUs)
      > MAX_AUDIO_TIMESTAMP_OFFSET_US
    ) {
      listener.onPositionFramesMismatch(
        audioTimestampPositionFrames,
        audioTimestampSystemTimeUs,
        systemTimeUs,
        playbackPositionUs
      )
      poller.rejectTimestamp()
    } else {
      poller.acceptTimestamp()
    }
  }

  private fun maybeUpdateLatency(systemTimeUs: Long) {
    if (isOutputPcm
      && getLatencyMethod != null
      && systemTimeUs - lastLatencySampleTimeUs >= MIN_LATENCY_SAMPLE_INTERVAL_US
    ) {
      try {
        // Compute the audio track latency, excluding the latency due to the buffer (leaving
        // latency due to the mixer and audio hardware driver).
        val rawLatencyMs = getLatencyMethod!!.invoke(checkNotNull(audioTrack)!!) as Int
        latencyUs = rawLatencyMs.toLong() * 1000L - bufferSizeUs
        // Check that the latency is non-negative.
        latencyUs = max(latencyUs, 0L)
        // Check that the latency isn't too large.
        if (latencyUs > MAX_LATENCY_US) {
          listener.onInvalidLatency(latencyUs)
          latencyUs = 0
        }
      } catch (e: Exception) {
        // The method existed, but doesn't work. Don't try again.
        getLatencyMethod = null
      }
      lastLatencySampleTimeUs = systemTimeUs
    }
  }

  private fun framesToDurationUs(frameCount: Long): Long {
    return (frameCount * C.MICROS_PER_SECOND) / outputSampleRate
  }

  private fun resetSyncParams() {
    smoothedPlayheadOffsetUs = 0
    playheadOffsetCount = 0
    nextPlayheadOffsetIndex = 0
    lastPlayheadSampleTimeUs = 0
    lastSystemTimeUs = 0
    previousModeSystemTimeUs = 0
    notifiedPositionIncreasing = false
  }

  /**
   * If passthrough workarounds are enabled, pausing is implemented by forcing the AudioTrack to
   * underrun. In this case, still behave as if we have pending data, otherwise writing won't
   * resume.
   */
  private fun forceHasPendingData(): Boolean {
    return needsPassthroughWorkarounds
      && checkNotNull(audioTrack)!!.playState == AudioTrack.PLAYSTATE_PAUSED
      && getPlaybackHeadPosition() == 0L
  }

  private fun getPlaybackHeadPositionUs(): Long {
    return framesToDurationUs(getPlaybackHeadPosition())
  }

  /**
   * [AudioTrack.getPlaybackHeadPosition] returns a value intended to be interpreted as an
   * unsigned 32 bit integer, which also wraps around periodically. This method returns the playback
   * head position as a long that will only wrap around if the value exceeds [Long.MAX_VALUE]
   * (which in practice will never happen).
   *
   * @return The playback head position, in frames.
   */
  private fun getPlaybackHeadPosition(): Long {
    val track = checkNotNull(audioTrack)!!
    if (stopTimestampUs != C.TIME_UNSET) {
      // Simulate the playback head position up to the total number of frames submitted.
      val elapsedTimeSinceStopUs = (SystemClock.elapsedRealtime() * 1000) - stopTimestampUs
      val framesSinceStop = (elapsedTimeSinceStopUs * outputSampleRate) / C.MICROS_PER_SECOND
      return min(endPlaybackHeadPosition, stopPlaybackHeadPosition + framesSinceStop)
    }

    val state = track.playState
    if (state == PLAYSTATE_STOPPED) {
      // The audio track hasn't been started.
      return 0
    }

    var rawPlaybackHeadPosition = 0xFFFFFFFFL and track.playbackHeadPosition.toLong()
    if (needsPassthroughWorkarounds) {
      // Work around an issue with passthrough/direct AudioTracks on platform API versions 21/22
      // where the playback head position jumps back to zero on paused passthrough/direct audio
      // tracks. See [Internal: b/19187573].
      if (state == PLAYSTATE_PAUSED && rawPlaybackHeadPosition == 0L) {
        passthroughWorkaroundPauseOffset = lastRawPlaybackHeadPosition
      }
      rawPlaybackHeadPosition += passthroughWorkaroundPauseOffset
    }

    if (Util.SDK_INT <= 29) {
      if (rawPlaybackHeadPosition == 0L
        && lastRawPlaybackHeadPosition > 0
        && state == PLAYSTATE_PLAYING
      ) {
        // If connecting a Bluetooth audio device fails, the AudioTrack may be left in a state
        // where its Java API is in the playing state, but the native track is stopped. When this
        // happens the playback head position gets stuck at zero. In this case, return the old
        // playback head position and force the track to be reset after
        // [FORCE_RESET_WORKAROUND_TIMEOUT_MS] has elapsed.
        if (forceResetWorkaroundTimeMs == C.TIME_UNSET) {
          forceResetWorkaroundTimeMs = SystemClock.elapsedRealtime()
        }
        return lastRawPlaybackHeadPosition
      } else {
        forceResetWorkaroundTimeMs = C.TIME_UNSET
      }
    }

    if (lastRawPlaybackHeadPosition > rawPlaybackHeadPosition) {
      // The value must have wrapped around.
      rawPlaybackHeadWrapCount++
    }
    lastRawPlaybackHeadPosition = rawPlaybackHeadPosition
    return rawPlaybackHeadPosition + (rawPlaybackHeadWrapCount shl 32)
  }

  companion object {
    /**
     * @see AudioTrack.PLAYSTATE_STOPPED
     */
    private const val PLAYSTATE_STOPPED = AudioTrack.PLAYSTATE_STOPPED

    /**
     * @see AudioTrack.PLAYSTATE_PAUSED
     */
    private const val PLAYSTATE_PAUSED = AudioTrack.PLAYSTATE_PAUSED

    /**
     * @see AudioTrack.PLAYSTATE_PLAYING
     */
    private const val PLAYSTATE_PLAYING = AudioTrack.PLAYSTATE_PLAYING

    /**
     * AudioTrack timestamps are deemed spurious if they are offset from the system clock by more than
     * this amount.
     *
     * This is a fail safe that should not be required on correctly functioning devices.
     */
    private const val MAX_AUDIO_TIMESTAMP_OFFSET_US = 5 * C.MICROS_PER_SECOND

    /**
     * AudioTrack latencies are deemed impossibly large if they are greater than this amount.
     *
     * This is a fail safe that should not be required on correctly functioning devices.
     */
    private const val MAX_LATENCY_US = 5 * C.MICROS_PER_SECOND

    /** The duration of time used to smooth over an adjustment between position sampling modes. */
    private const val MODE_SWITCH_SMOOTHING_DURATION_US = C.MICROS_PER_SECOND

    private const val FORCE_RESET_WORKAROUND_TIMEOUT_MS: Long = 200

    private const val MAX_PLAYHEAD_OFFSET_COUNT = 10
    private const val MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US = 30_000
    private const val MIN_LATENCY_SAMPLE_INTERVAL_US = 50_0000

    /**
     * Returns whether to work around problems with passthrough audio tracks. See [Internal:
     * b/18899620, b/19187573, b/21145353].
     */
    private fun needsPassthroughWorkarounds(outputEncoding: Int): Boolean {
      return Util.SDK_INT < 23
        && (outputEncoding == C.ENCODING_AC3 || outputEncoding == C.ENCODING_E_AC3)
    }
  }
}
