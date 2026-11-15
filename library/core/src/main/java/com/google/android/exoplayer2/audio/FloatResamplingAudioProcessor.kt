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

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.util.Util
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.nio.ByteBuffer

/**
 * An [AudioProcessor] that converts high resolution PCM audio to 32-bit float. The following
 * encodings are supported as input:
 *
 * <ul>
 *   <li>[C.ENCODING_PCM_24BIT]
 *   <li>[C.ENCODING_PCM_32BIT]
 *   <li>[C.ENCODING_PCM_FLOAT] ([isActive] will return `false`)
 * </ul>
 */
internal class FloatResamplingAudioProcessor : BaseAudioProcessor() {

  @CanIgnoreReturnValue
  @Throws(UnhandledAudioFormatException::class)
  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    val encoding = inputAudioFormat.encoding
    if (!Util.isEncodingHighResolutionPcm(encoding)) {
      throw UnhandledAudioFormatException(inputAudioFormat)
    }
    return if (encoding != C.ENCODING_PCM_FLOAT)
      AudioFormat(
        inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT
      )
    else AudioFormat.NOT_SET
  }

  override fun queueInput(inputBuffer: ByteBuffer?) {
    if (inputBuffer == null) {
      return
    }
    val position = inputBuffer.position()
    val limit = inputBuffer.limit()
    val size = limit - position

    val buffer: ByteBuffer
    when (inputAudioFormat.encoding) {
      C.ENCODING_PCM_24BIT -> {
        buffer = replaceOutputBuffer((size / 3) * 4)
        var i = position
        while (i < limit) {
          val pcm32BitInteger =
            ((inputBuffer.get(i).toInt() and 0xFF) shl 8) or
              ((inputBuffer.get(i + 1).toInt() and 0xFF) shl 16) or
              ((inputBuffer.get(i + 2).toInt() and 0xFF) shl 24)
          writePcm32BitFloat(pcm32BitInteger, buffer)
          i += 3
        }
      }
      C.ENCODING_PCM_32BIT -> {
        buffer = replaceOutputBuffer(size)
        var i = position
        while (i < limit) {
          val pcm32BitInteger =
            (inputBuffer.get(i).toInt() and 0xFF) or
              ((inputBuffer.get(i + 1).toInt() and 0xFF) shl 8) or
              ((inputBuffer.get(i + 2).toInt() and 0xFF) shl 16) or
              ((inputBuffer.get(i + 3).toInt() and 0xFF) shl 24)
          writePcm32BitFloat(pcm32BitInteger, buffer)
          i += 4
        }
      }
      C.ENCODING_PCM_8BIT,
      C.ENCODING_PCM_16BIT,
      C.ENCODING_PCM_16BIT_BIG_ENDIAN,
      C.ENCODING_PCM_FLOAT,
      C.ENCODING_INVALID,
      Format.NO_VALUE -> throw IllegalStateException()
      else -> throw IllegalStateException()
    }

    inputBuffer.position(inputBuffer.limit())
    buffer.flip()
  }

  companion object {
    private val FLOAT_NAN_AS_INT: Int = java.lang.Float.floatToIntBits(Float.NaN)
    private const val PCM_32_BIT_INT_TO_PCM_32_BIT_FLOAT_FACTOR: Double = 1.0 / 0x7FFFFFFF

    /**
     * Converts the provided 32-bit integer to a 32-bit float value and writes it to `buffer`.
     *
     * @param pcm32BitInt The 32-bit integer value to convert to 32-bit float in [-1.0, 1.0].
     * @param buffer The output buffer.
     */
    private fun writePcm32BitFloat(pcm32BitInt: Int, buffer: ByteBuffer) {
      val pcm32BitFloat = (PCM_32_BIT_INT_TO_PCM_32_BIT_FLOAT_FACTOR * pcm32BitInt).toFloat()
      var floatBits = java.lang.Float.floatToIntBits(pcm32BitFloat)
      if (floatBits == FLOAT_NAN_AS_INT) {
        floatBits = java.lang.Float.floatToIntBits(0.0f)
      }
      buffer.putInt(floatBits)
    }
  }
}
