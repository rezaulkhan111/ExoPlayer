/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat
import com.google.android.exoplayer2.audio.AudioProcessor.Companion.EMPTY_BUFFER
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.abs

/**
 * An [AudioProcessor] that uses the Sonic library to modify audio speed/pitch/sample rate.
 */
class SonicAudioProcessor : AudioProcessor {

  private var pendingOutputSampleRate: Int = SAMPLE_RATE_NO_CHANGE
  private var speed: Float = 1f
  private var pitch: Float = 1f

  private var pendingInputAudioFormat: AudioFormat = AudioFormat.NOT_SET
  private var pendingOutputAudioFormat: AudioFormat = AudioFormat.NOT_SET
  private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
  private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET

  private var pendingSonicRecreation: Boolean = false
  private var sonic: Sonic? = null
  private var buffer: ByteBuffer = EMPTY_BUFFER
  private var shortBuffer: ShortBuffer = buffer.asShortBuffer()
  private var outputBuffer: ByteBuffer = EMPTY_BUFFER
  private var inputBytes: Long = 0
  private var outputBytes: Long = 0
  private var inputEnded: Boolean = false

  /**
   * Sets the target playback speed. This method may only be called after draining data through the
   * processor. The value returned by [isActive] may change, and the processor must be
   * [flush] flushed before queueing more data.
   *
   * @param speed The target factor by which playback should be sped up.
   */
  fun setSpeed(speed: Float) {
    if (this.speed != speed) {
      this.speed = speed
      pendingSonicRecreation = true
    }
  }

  /**
   * Sets the target playback pitch. This method may only be called after draining data through the
   * processor. The value returned by [isActive] may change, and the processor must be
   * [flush] flushed before queueing more data.
   *
   * @param pitch The target pitch.
   */
  fun setPitch(pitch: Float) {
    if (this.pitch != pitch) {
      this.pitch = pitch
      pendingSonicRecreation = true
    }
  }

  /**
   * Sets the sample rate for output audio, in Hertz. Pass [SAMPLE_RATE_NO_CHANGE] to output
   * audio at the same sample rate as the input. After calling this method, call
   * [configure] to configure the processor with the new sample rate.
   *
   * @param sampleRateHz The sample rate for output audio, in Hertz.
   * @see configure
   */
  fun setOutputSampleRateHz(sampleRateHz: Int) {
    pendingOutputSampleRate = sampleRateHz
  }

  /**
   * Returns the media duration corresponding to the specified playout duration, taking speed
   * adjustment into account.
   *
   * <p>The scaling performed by this method will use the actual playback speed achieved by the
   * audio processor, on average, since it was last flushed. This may differ very slightly from the
   * target playback speed.
   *
   * @param playoutDuration The playout duration to scale.
   * @return The corresponding media duration, in the same units as `duration`.
   */
  fun getMediaDuration(playoutDuration: Long): Long {
    return if (outputBytes >= MIN_BYTES_FOR_DURATION_SCALING_CALCULATION) {
      val processedInputBytes = inputBytes - Assertions.checkNotNull(sonic)!!.getPendingInputBytes()
      if (outputAudioFormat.sampleRate == inputAudioFormat.sampleRate)
        Util.scaleLargeTimestamp(playoutDuration, processedInputBytes, outputBytes)
      else
        Util.scaleLargeTimestamp(
          playoutDuration,
          processedInputBytes * outputAudioFormat.sampleRate,
          outputBytes * inputAudioFormat.sampleRate
        )
    } else {
      (speed * playoutDuration).toLong()
    }
  }

  @CanIgnoreReturnValue
  @Throws(UnhandledAudioFormatException::class)
  override fun configure(inputAudioFormat: AudioFormat?): AudioFormat {
    if (inputAudioFormat == null || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw UnhandledAudioFormatException(inputAudioFormat ?: AudioFormat.NOT_SET)
    }
    val outputSampleRateHz =
      if (pendingOutputSampleRate == SAMPLE_RATE_NO_CHANGE)
        inputAudioFormat.sampleRate
      else pendingOutputSampleRate
    pendingInputAudioFormat = inputAudioFormat
    pendingOutputAudioFormat =
      AudioFormat(outputSampleRateHz, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT)
    pendingSonicRecreation = true
    return pendingOutputAudioFormat
  }

  override fun isActive(): Boolean {
    return pendingOutputAudioFormat.sampleRate != Format.NO_VALUE &&
      (abs(speed - 1f) >= CLOSE_THRESHOLD ||
        abs(pitch - 1f) >= CLOSE_THRESHOLD ||
        pendingOutputAudioFormat.sampleRate != pendingInputAudioFormat.sampleRate)
  }

  override fun queueInput(inputBuffer: ByteBuffer?) {
    if (inputBuffer == null || !inputBuffer.hasRemaining()) {
      return
    }
    val sonic = Assertions.checkNotNull(this.sonic)!!
    val shortBuffer = inputBuffer.asShortBuffer()
    val inputSize = inputBuffer.remaining()
    inputBytes += inputSize.toLong()
    sonic.queueInput(shortBuffer)
    inputBuffer.position(inputBuffer.position() + inputSize)
  }

  override fun queueEndOfStream() {
    // TODO(internal b/174554082): assert sonic is non-null here and in getOutput.
    sonic?.queueEndOfStream()
    inputEnded = true
  }

  override fun getOutput(): ByteBuffer {
    val sonic = this.sonic
    if (sonic != null) {
      val outputSize = sonic.getOutputSize()
      if (outputSize > 0) {
        if (buffer.capacity() < outputSize) {
          buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder())
          shortBuffer = buffer.asShortBuffer()
        } else {
          buffer.clear()
          shortBuffer.clear()
        }
        sonic.getOutput(shortBuffer)
        outputBytes += outputSize.toLong()
        buffer.limit(outputSize)
        outputBuffer = buffer
      }
    }
    val outputBuffer = this.outputBuffer
    this.outputBuffer = EMPTY_BUFFER
    return outputBuffer
  }

  override fun isEnded(): Boolean {
    return inputEnded && (sonic == null || sonic!!.getOutputSize() == 0)
  }

  override fun flush() {
    if (isActive()) {
      inputAudioFormat = pendingInputAudioFormat
      outputAudioFormat = pendingOutputAudioFormat
      if (pendingSonicRecreation) {
        sonic =
          Sonic(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            speed,
            pitch,
            outputAudioFormat.sampleRate
          )
      } else {
        sonic?.flush()
      }
    }
    outputBuffer = EMPTY_BUFFER
    inputBytes = 0
    outputBytes = 0
    inputEnded = false
  }

  override fun reset() {
    speed = 1f
    pitch = 1f
    pendingInputAudioFormat = AudioFormat.NOT_SET
    pendingOutputAudioFormat = AudioFormat.NOT_SET
    inputAudioFormat = AudioFormat.NOT_SET
    outputAudioFormat = AudioFormat.NOT_SET
    buffer = EMPTY_BUFFER
    shortBuffer = buffer.asShortBuffer()
    outputBuffer = EMPTY_BUFFER
    pendingOutputSampleRate = SAMPLE_RATE_NO_CHANGE
    pendingSonicRecreation = false
    sonic = null
    inputBytes = 0
    outputBytes = 0
    inputEnded = false
  }

  companion object {
    /** Indicates that the output sample rate should be the same as the input. */
    const val SAMPLE_RATE_NO_CHANGE: Int = -1

    /** The threshold below which the difference between two pitch/speed factors is negligible. */
    private const val CLOSE_THRESHOLD = 0.0001f

    /**
     * The minimum number of output bytes required for duration scaling to be calculated using the
     * input and output byte counts, rather than using the current playback speed.
     */
    private const val MIN_BYTES_FOR_DURATION_SCALING_CALCULATION = 1024
  }
}
