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

import androidx.annotation.IntRange
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.decoder.DecoderInputBuffer
import com.google.android.exoplayer2.util.Assertions.checkArgument

/** Buffer to which multiple sample buffers can be appended for batch processing */
internal class BatchBuffer : DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DIRECT) {

  /**
   * Returns the timestamp of the last sample in the buffer. The return value is undefined if [hasSamples] is `false`.
   */
  var lastSampleTimeUs: Long = 0
    private set

  /** Returns the number of samples in the buffer. */
  var sampleCount: Int = 0
    private set

  private var maxSampleCount = DEFAULT_MAX_SAMPLE_COUNT

  override fun clear() {
    super.clear()
    sampleCount = 0
  }

  /** Sets the maximum number of samples that can be appended before the buffer is full. */
  fun setMaxSampleCount(@IntRange(from = 1) maxSampleCount: Int) {
    checkArgument(maxSampleCount > 0)
    this.maxSampleCount = maxSampleCount
  }

  /**
   * Returns the timestamp of the first sample in the buffer. The return value is undefined if
   * [hasSamples] is `false`.
   */
  fun getFirstSampleTimeUs(): Long {
    return timeUs
  }

  /** Returns whether the buffer contains one or more samples. */
  fun hasSamples(): Boolean {
    return sampleCount > 0
  }

  /**
   * Attempts to append the provided buffer.
   *
   * @param buffer The buffer to try and append.
   * @return Whether the buffer was successfully appended.
   * @throws IllegalArgumentException If the [buffer] is encrypted, has supplemental data, or
   *     is an end of stream buffer, none of which are supported.
   */
  fun append(buffer: DecoderInputBuffer): Boolean {
    checkArgument(!buffer.isEncrypted())
    checkArgument(!buffer.hasSupplementalData())
    checkArgument(!buffer.isEndOfStream())
    if (!canAppendSampleBuffer(buffer)) {
      return false
    }
    if (sampleCount++ == 0) {
      timeUs = buffer.timeUs
      if (buffer.isKeyFrame()) {
        setFlags(C.BUFFER_FLAG_KEY_FRAME)
      }
    }
    if (buffer.isDecodeOnly()) {
      setFlags(C.BUFFER_FLAG_DECODE_ONLY)
    }
    val bufferData = buffer.data
    if (bufferData != null) {
      ensureSpaceForWrite(bufferData.remaining())
      data!!.put(bufferData)
    }
    lastSampleTimeUs = buffer.timeUs
    return true
  }

  private fun canAppendSampleBuffer(buffer: DecoderInputBuffer): Boolean {
    if (!hasSamples()) {
      // Always allow appending when the buffer is empty, else no progress can be made.
      return true
    }
    if (sampleCount >= maxSampleCount) {
      return false
    }
    if (buffer.isDecodeOnly() != isDecodeOnly()) {
      return false
    }
    val bufferData = buffer.data
    if (bufferData != null &&
      data != null &&
      data!!.position() + bufferData.remaining() > MAX_SIZE_BYTES
    ) {
      return false
    }
    return true
  }

  companion object {
    /** The default maximum number of samples that can be appended before the buffer is full. */
    const val DEFAULT_MAX_SAMPLE_COUNT = 32

    /**
     * The maximum size of the buffer in bytes. This prevents excessive memory usage for high bitrate
     * streams. The limit is equivalent of 75s of mp3 at highest bitrate (320kb/s) and 30s of AAC LC
     * at highest bitrate (800kb/s). That limit is ignored for the first sample.
     */
    @VisibleForTesting
    internal const val MAX_SIZE_BYTES = 3 * 1000 * 1024
  }
}
