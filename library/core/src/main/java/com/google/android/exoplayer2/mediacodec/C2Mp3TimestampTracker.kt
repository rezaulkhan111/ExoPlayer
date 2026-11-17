/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.mediacodec

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.audio.MpegAudioUtil
import com.google.android.exoplayer2.decoder.DecoderInputBuffer
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Log
import kotlin.math.max

/**
 * Tracks the number of processed samples to calculate an accurate current timestamp, matching the
 * calculations made in the Codec2 Mp3 decoder.
 */
internal class C2Mp3TimestampTracker {

  private var anchorTimestampUs: Long = 0
  private var processedFrames: Long = 0
  private var seenInvalidMpegAudioHeader = false

  /**
   * Resets the timestamp tracker.
   *
   * <p>This should be done when the codec is flushed.
   */
  fun reset() {
    anchorTimestampUs = 0
    processedFrames = 0
    seenInvalidMpegAudioHeader = false
  }

  /**
   * Updates the tracker with the given input buffer and returns the expected output timestamp.
   *
   * @param format The format associated with the buffer.
   * @param buffer The current input buffer.
   * @return The expected output presentation time, in microseconds.
   */
  fun updateAndGetPresentationTimeUs(format: Format, buffer: DecoderInputBuffer): Long {
    if (processedFrames == 0L) {
      anchorTimestampUs = buffer.timeUs
    }

    if (seenInvalidMpegAudioHeader) {
      return buffer.timeUs
    }

    val data = checkNotNull(buffer.data)!!
    var sampleHeaderData = 0
    for (i in 0 until 4) {
      sampleHeaderData = sampleHeaderData shl 8
      sampleHeaderData = sampleHeaderData or (data.get(i).toInt() and 0xFF)
    }

    val frameCount = MpegAudioUtil.parseMpegAudioFrameSampleCount(sampleHeaderData)
    if (frameCount == C.LENGTH_UNSET) {
      seenInvalidMpegAudioHeader = true
      processedFrames = 0
      anchorTimestampUs = buffer.timeUs
      Log.w(TAG, "MPEG audio header is invalid.")
      return buffer.timeUs
    }
    val currentBufferTimestampUs = getBufferTimestampUs(format.sampleRate.toLong())
    processedFrames += frameCount
    return currentBufferTimestampUs
  }

  /**
   * Returns the timestamp of the last buffer that will be produced if the stream ends at the
   * current position, in microseconds.
   *
   * @param format The format associated with input buffers.
   * @return The timestamp of the last buffer that will be produced if the stream ends at the
   *     current position, in microseconds.
   */
  fun getLastOutputBufferPresentationTimeUs(format: Format): Long {
    return getBufferTimestampUs(format.sampleRate.toLong())
  }

  private fun getBufferTimestampUs(sampleRate: Long): Long {
    // This calculation matches the timestamp calculation in the Codec2 Mp3 Decoder.
    // https://cs.android.com/android/platform/superproject/+/main:frameworks/av/media/codec2/components/mp3/C2SoftMp3Dec.cpp;l=464;drc=ed134640332fea70ca4b05694289d91a5265bb46
    return anchorTimestampUs +
      max(0L, (processedFrames - DECODER_DELAY_FRAMES) * C.MICROS_PER_SECOND / sampleRate)
  }

  companion object {
    private const val DECODER_DELAY_FRAMES: Long = 529
    private const val TAG = "C2Mp3TimestampTracker"
  }
}
