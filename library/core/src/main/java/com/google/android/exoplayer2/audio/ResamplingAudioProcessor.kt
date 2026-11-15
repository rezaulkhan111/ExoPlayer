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
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.util.Util
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.nio.ByteBuffer

/**
 * An [AudioProcessor] that converts different PCM audio encodings to 16-bit integer PCM. The
 * following encodings are supported as input:
 *
 * <ul>
 *   <li>[C.ENCODING_PCM_8BIT]
 *   <li>[C.ENCODING_PCM_16BIT] ([isActive] will return `false`)
 *   <li>[C.ENCODING_PCM_16BIT_BIG_ENDIAN]
 *   <li>[C.ENCODING_PCM_24BIT]
 *   <li>[C.ENCODING_PCM_32BIT]
 *   <li>[C.ENCODING_PCM_FLOAT]
 * </ul>
 */
internal class ResamplingAudioProcessor : BaseAudioProcessor() {

  @CanIgnoreReturnValue
  @Throws(UnhandledAudioFormatException::class)
  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    val encoding = inputAudioFormat.encoding
    if (encoding != C.ENCODING_PCM_8BIT &&
      encoding != C.ENCODING_PCM_16BIT &&
      encoding != C.ENCODING_PCM_16BIT_BIG_ENDIAN &&
      encoding != C.ENCODING_PCM_24BIT &&
      encoding != C.ENCODING_PCM_32BIT &&
      encoding != C.ENCODING_PCM_FLOAT
    ) {
      throw UnhandledAudioFormatException(inputAudioFormat)
    }
    return if (encoding != C.ENCODING_PCM_16BIT)
      AudioFormat(
        inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT
      )
    else AudioFormat.NOT_SET
  }

  override fun queueInput(inputBuffer: ByteBuffer?) {
    if (inputBuffer == null) {
      return
    }
    // Prepare the output buffer.
    val position = inputBuffer.position()
    val limit = inputBuffer.limit()
    val size = limit - position
    val resampledSize: Int =
      when (inputAudioFormat.encoding) {
        C.ENCODING_PCM_8BIT -> size * 2
        C.ENCODING_PCM_16BIT_BIG_ENDIAN -> size
        C.ENCODING_PCM_24BIT -> (size / 3) * 2
        C.ENCODING_PCM_32BIT,
        C.ENCODING_PCM_FLOAT -> size / 2
        C.ENCODING_PCM_16BIT,
        C.ENCODING_INVALID,
        Format.NO_VALUE -> throw IllegalStateException()
        else -> throw IllegalStateException()
      }

    // Resample the little endian input and update the input/output buffers.
    val buffer = replaceOutputBuffer(resampledSize)
    when (inputAudioFormat.encoding) {
      C.ENCODING_PCM_8BIT ->
        // 8 -> 16 bit resampling. Shift each byte from [0, 256) to [-128, 128) and scale up.
        for (i in position until limit) {
          buffer.put(0.toByte())
          buffer.put(((inputBuffer.get(i).toInt() and 0xFF) - 128).toByte())
        }
      C.ENCODING_PCM_16BIT_BIG_ENDIAN ->
        // Big endian to little endian resampling. Swap the byte order.
        for (i in position until limit step 2) {
          buffer.put(inputBuffer.get(i + 1))
          buffer.put(inputBuffer.get(i))
        }
      C.ENCODING_PCM_24BIT ->
        // 24 -> 16 bit resampling. Drop the least significant byte.
        for (i in position until limit step 3) {
          buffer.put(inputBuffer.get(i + 1))
          buffer.put(inputBuffer.get(i + 2))
        }
      C.ENCODING_PCM_32BIT ->
        // 32 -> 16 bit resampling. Drop the two least significant bytes.
        for (i in position until limit step 4) {
          buffer.put(inputBuffer.get(i + 2))
          buffer.put(inputBuffer.get(i + 3))
        }
      C.ENCODING_PCM_FLOAT ->
        // 32 bit floating point -> 16 bit resampling. Floating point values are in the range
        // [-1.0, 1.0], so need to be scaled by Short.MAX_VALUE.
        for (i in position until limit step 4) {
          // Clamp to avoid integer overflow if the floating point values exceed their nominal range
          // [Internal ref: b/161204847].
          val floatValue =
            Util.constrainValue(inputBuffer.getFloat(i), /* min= */ -1f, /* max= */ 1f)
          val shortValue = (floatValue * Short.MAX_VALUE).toInt().toShort()
          buffer.put((shortValue.toInt() and 0xFF).toByte())
          buffer.put(((shortValue.toInt() shr 8) and 0xFF).toByte())
        }
      C.ENCODING_PCM_16BIT,
      C.ENCODING_INVALID,
      Format.NO_VALUE ->
        // Never happens.
        throw IllegalStateException()
      else ->
        // Never happens.
        throw IllegalStateException()
    }
    inputBuffer.position(inputBuffer.limit())
    buffer.flip()
  }
}
