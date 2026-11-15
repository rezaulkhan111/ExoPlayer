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

import androidx.annotation.IntDef
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min

/**
 * An [AudioProcessor] that skips silence in the input stream. Input and output are 16-bit
 * PCM.
 */
class SilenceSkippingAudioProcessor
@JvmOverloads
constructor(
  /**
   * The minimum duration of audio that must be below [silenceThresholdLevel] to classify that part
   * of audio as silent, in microseconds.
   */
  private val minimumSilenceDurationUs: Long = DEFAULT_MINIMUM_SILENCE_DURATION_US,
  /**
   * The duration of silence by which to extend non-silent sections, in microseconds. The value
   * must not exceed [minimumSilenceDurationUs].
   */
  private val paddingSilenceUs: Long = DEFAULT_PADDING_SILENCE_US,
  /**
   * The absolute level below which an individual PCM sample is classified as silent.
   */
  private val silenceThresholdLevel: Short = DEFAULT_SILENCE_THRESHOLD_LEVEL
) : BaseAudioProcessor() {

  /** Trimming states. */
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
  @IntDef(STATE_NOISY, STATE_MAYBE_SILENT, STATE_SILENT)
  private annotation class State

  private var bytesPerFrame: Int = 0
  private var enabled: Boolean = false

  /**
   * Buffers audio data that may be classified as silence while in [STATE_MAYBE_SILENT]. If
   * the input becomes noisy before the buffer has filled, it will be output. Otherwise, the buffer
   * contents will be dropped and the state will transition to [STATE_SILENT].
   */
  private var maybeSilenceBuffer: ByteArray = Util.EMPTY_BYTE_ARRAY

  /**
   * Stores the latest part of the input while silent. It will be output as padding if the next
   * input is noisy.
   */
  private var paddingBuffer: ByteArray = Util.EMPTY_BYTE_ARRAY

  @State private var state: Int = STATE_NOISY
  private var maybeSilenceBufferSize: Int = 0
  private var paddingSize: Int = 0
  private var hasOutputNoise: Boolean = false

  /**
   * Returns the total number of frames of input audio that were skipped due to being classified as
   * silence since the last call to [flush].
   */
  var skippedFrames: Long = 0
    private set

  init {
    Assertions.checkArgument(paddingSilenceUs <= minimumSilenceDurationUs)
  }

  /**
   * Sets whether to skip silence in the input. This method may only be called after draining data
   * through the processor. The value returned by [isActive] may change, and the processor
   * must be [flush] flushed before queueing more data.
   *
   * @param enabled Whether to skip silence in the input.
   */
  fun setEnabled(enabled: Boolean) {
    this.enabled = enabled
  }

  // AudioProcessor implementation.

  @CanIgnoreReturnValue
  @Throws(UnhandledAudioFormatException::class)
  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw UnhandledAudioFormatException(inputAudioFormat)
    }
    return if (enabled) inputAudioFormat else AudioFormat.NOT_SET
  }

  override fun isActive(): Boolean {
    return enabled
  }

  override fun queueInput(inputBuffer: ByteBuffer?) {
    if (inputBuffer == null) {
      return
    }
    while (inputBuffer.hasRemaining() && !hasPendingOutput()) {
      when (state) {
        STATE_NOISY -> processNoisy(inputBuffer)
        STATE_MAYBE_SILENT -> processMaybeSilence(inputBuffer)
        STATE_SILENT -> processSilence(inputBuffer)
        else -> throw IllegalStateException()
      }
    }
  }

  override fun onQueueEndOfStream() {
    if (maybeSilenceBufferSize > 0) {
      // We haven't received enough silence to transition to the silent state, so output the buffer.
      output(maybeSilenceBuffer, maybeSilenceBufferSize)
    }
    if (!hasOutputNoise) {
      skippedFrames += (paddingSize / bytesPerFrame).toLong()
    }
  }

  override fun onFlush() {
    if (enabled) {
      bytesPerFrame = inputAudioFormat.bytesPerFrame
      val maybeSilenceBufferSize = durationUsToFrames(minimumSilenceDurationUs) * bytesPerFrame
      if (maybeSilenceBuffer.size != maybeSilenceBufferSize) {
        maybeSilenceBuffer = ByteArray(maybeSilenceBufferSize)
      }
      paddingSize = durationUsToFrames(paddingSilenceUs) * bytesPerFrame
      if (paddingBuffer.size != paddingSize) {
        paddingBuffer = ByteArray(paddingSize)
      }
    }
    state = STATE_NOISY
    skippedFrames = 0
    maybeSilenceBufferSize = 0
    hasOutputNoise = false
  }

  override fun onReset() {
    enabled = false
    paddingSize = 0
    maybeSilenceBuffer = Util.EMPTY_BYTE_ARRAY
    paddingBuffer = Util.EMPTY_BYTE_ARRAY
  }

  // Internal methods.

  /**
   * Incrementally processes new input from `inputBuffer` while in [STATE_NOISY],
   * updating the state if needed.
   */
  private fun processNoisy(inputBuffer: ByteBuffer) {
    val limit = inputBuffer.limit()

    // Check if there's any noise within the maybe silence buffer duration.
    inputBuffer.limit(min(limit, inputBuffer.position() + maybeSilenceBuffer.size))
    val noiseLimit = findNoiseLimit(inputBuffer)
    if (noiseLimit == inputBuffer.position()) {
      // The buffer contains the start of possible silence.
      state = STATE_MAYBE_SILENT
    } else {
      inputBuffer.limit(noiseLimit)
      output(inputBuffer)
    }

    // Restore the limit.
    inputBuffer.limit(limit)
  }

  /**
   * Incrementally processes new input from `inputBuffer` while in [STATE_MAYBE_SILENT],
   * updating the state if needed.
   */
  private fun processMaybeSilence(inputBuffer: ByteBuffer) {
    val limit = inputBuffer.limit()
    val noisePosition = findNoisePosition(inputBuffer)
    val maybeSilenceInputSize = noisePosition - inputBuffer.position()
    val maybeSilenceBufferRemaining = maybeSilenceBuffer.size - maybeSilenceBufferSize
    if (noisePosition < limit && maybeSilenceInputSize < maybeSilenceBufferRemaining) {
      // The maybe silence buffer isn't full, so output it and switch back to the noisy state.
      output(maybeSilenceBuffer, maybeSilenceBufferSize)
      maybeSilenceBufferSize = 0
      state = STATE_NOISY
    } else {
      // Fill as much of the maybe silence buffer as possible.
      val bytesToWrite = min(maybeSilenceInputSize, maybeSilenceBufferRemaining)
      inputBuffer.limit(inputBuffer.position() + bytesToWrite)
      inputBuffer.get(maybeSilenceBuffer, maybeSilenceBufferSize, bytesToWrite)
      maybeSilenceBufferSize += bytesToWrite
      if (maybeSilenceBufferSize == maybeSilenceBuffer.size) {
        // We've reached a period of silence, so skip it, taking in to account padding for both
        // the noisy to silent transition and any future silent to noisy transition.
        if (hasOutputNoise) {
          output(maybeSilenceBuffer, paddingSize)
          skippedFrames += ((maybeSilenceBufferSize - paddingSize * 2) / bytesPerFrame).toLong()
        } else {
          skippedFrames += ((maybeSilenceBufferSize - paddingSize) / bytesPerFrame).toLong()
        }
        updatePaddingBuffer(inputBuffer, maybeSilenceBuffer, maybeSilenceBufferSize)
        maybeSilenceBufferSize = 0
        state = STATE_SILENT
      }

      // Restore the limit.
      inputBuffer.limit(limit)
    }
  }

  /**
   * Incrementally processes new input from `inputBuffer` while in [STATE_SILENT],
   * updating the state if needed.
   */
  private fun processSilence(inputBuffer: ByteBuffer) {
    val limit = inputBuffer.limit()
    val noisyPosition = findNoisePosition(inputBuffer)
    inputBuffer.limit(noisyPosition)
    skippedFrames += (inputBuffer.remaining() / bytesPerFrame).toLong()
    updatePaddingBuffer(inputBuffer, paddingBuffer, paddingSize)
    if (noisyPosition < limit) {
      // Output the padding, which may include previous input as well as new input, then transition
      // back to the noisy state.
      output(paddingBuffer, paddingSize)
      state = STATE_NOISY

      // Restore the limit.
      inputBuffer.limit(limit)
    }
  }

  /**
   * Copies `length` elements from `data` to populate a new output buffer from the
   * processor.
   */
  private fun output(data: ByteArray, length: Int) {
    replaceOutputBuffer(length).put(data, 0, length).flip()
    if (length > 0) {
      hasOutputNoise = true
    }
  }

  /**
   * Copies remaining bytes from `data` to populate a new output buffer from the processor.
   */
  private fun output(data: ByteBuffer) {
    val length = data.remaining()
    replaceOutputBuffer(length).put(data).flip()
    if (length > 0) {
      hasOutputNoise = true
    }
  }

  /**
   * Fills [paddingBuffer] using data from `input`, plus any additional buffered data
   * at the end of `buffer` (up to its `size`) required to fill it, advancing the input
   * position.
   */
  private fun updatePaddingBuffer(input: ByteBuffer, buffer: ByteArray, size: Int) {
    val fromInputSize = min(input.remaining(), paddingSize)
    val fromBufferSize = paddingSize - fromInputSize
    System.arraycopy(
      /* src = */ buffer,
      /* srcPos = */ size - fromBufferSize,
      /* dest = */ paddingBuffer,
      /* destPos = */ 0,
      /* length = */ fromBufferSize
    )
    input.position(input.limit() - fromInputSize)
    input.get(paddingBuffer, fromBufferSize, fromInputSize)
  }

  /**
   * Returns the number of input frames corresponding to `durationUs` microseconds of audio.
   */
  private fun durationUsToFrames(durationUs: Long): Int {
    return ((durationUs * inputAudioFormat.sampleRate) / C.MICROS_PER_SECOND).toInt()
  }

  /**
   * Returns the earliest byte position in [position, limit) of `buffer` that contains a frame
   * classified as a noisy frame, or the limit of the buffer if no such frame exists.
   */
  private fun findNoisePosition(buffer: ByteBuffer): Int {
    // The input is in ByteOrder.nativeOrder(), which is little endian on Android.
    for (i in buffer.position() until buffer.limit() step 2) {
      if (abs(buffer.getShort(i).toInt()) > silenceThresholdLevel) {
        // Round to the start of the frame.
        return bytesPerFrame * (i / bytesPerFrame)
      }
    }
    return buffer.limit()
  }

  /**
   * Returns the earliest byte position in [position, limit) of `buffer` such that all frames
   * from the byte position to the limit are classified as silent.
   */
  private fun findNoiseLimit(buffer: ByteBuffer): Int {
    // The input is in ByteOrder.nativeOrder(), which is little endian on Android.
    var i = buffer.limit() - 2
    while (i >= buffer.position()) {
      if (abs(buffer.getShort(i).toInt()) > silenceThresholdLevel) {
        // Return the start of the next frame.
        return bytesPerFrame * (i / bytesPerFrame) + bytesPerFrame
      }
      i -= 2
    }
    return buffer.position()
  }

  companion object {
    /**
     * The default value for [minimumSilenceDurationUs].
     */
    const val DEFAULT_MINIMUM_SILENCE_DURATION_US: Long = 150_000

    /**
     * The default value for [paddingSilenceUs].
     */
    const val DEFAULT_PADDING_SILENCE_US: Long = 20_000

    /**
     * The default value for [silenceThresholdLevel].
     */
    const val DEFAULT_SILENCE_THRESHOLD_LEVEL: Short = 1024

    /** State when the input is not silent. */
    private const val STATE_NOISY = 0

    /** State when the input may be silent but we haven't read enough yet to know. */
    private const val STATE_MAYBE_SILENT = 1

    /** State when the input is silent. */
    private const val STATE_SILENT = 2
  }
}
