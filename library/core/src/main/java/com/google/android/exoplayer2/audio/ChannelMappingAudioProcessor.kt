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
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.util.Assertions
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.nio.ByteBuffer

/**
 * An [AudioProcessor] that applies a mapping from input channels onto specified output
 * channels. This can be used to reorder, duplicate or discard channels.
 */
internal class ChannelMappingAudioProcessor : BaseAudioProcessor() {

  private var pendingOutputChannels: IntArray? = null
  private var outputChannels: IntArray? = null

  /**
   * Resets the channel mapping. After calling this method, call [configure] to
   * start using the new channel map.
   *
   * <p>See [AudioSink.configure].
   *
   * @param outputChannels The mapping from input to output channel indices, or `null` to
   *     leave the input unchanged.
   */
  fun setChannelMap(outputChannels: IntArray?) {
    pendingOutputChannels = outputChannels
  }

  @CanIgnoreReturnValue
  @Throws(UnhandledAudioFormatException::class)
  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    val outputChannels = pendingOutputChannels ?: return AudioFormat.NOT_SET

    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw UnhandledAudioFormatException(inputAudioFormat)
    }

    var active = inputAudioFormat.channelCount != outputChannels.size
    for (i in outputChannels.indices) {
      val channelIndex = outputChannels[i]
      if (channelIndex >= inputAudioFormat.channelCount) {
        throw UnhandledAudioFormatException(inputAudioFormat)
      }
      active = active or (channelIndex != i)
    }
    return if (active)
      AudioFormat(inputAudioFormat.sampleRate, outputChannels.size, C.ENCODING_PCM_16BIT)
    else AudioFormat.NOT_SET
  }

  override fun queueInput(inputBuffer: ByteBuffer?) {
    val outputChannels = Assertions.checkNotNull(this.outputChannels)!!
    val bufferToProcess = inputBuffer!!
    var position = bufferToProcess.position()
    val limit = bufferToProcess.limit()
    val frameCount = (limit - position) / inputAudioFormat.bytesPerFrame
    val outputSize = frameCount * outputAudioFormat.bytesPerFrame
    val buffer = replaceOutputBuffer(outputSize)
    while (position < limit) {
      for (channelIndex in outputChannels) {
        buffer.putShort(bufferToProcess.getShort(position + (2 * channelIndex)))
      }
      position += inputAudioFormat.bytesPerFrame
    }
    bufferToProcess.position(limit)
    buffer.flip()
  }

  override fun onFlush() {
    outputChannels = pendingOutputChannels
  }

  override fun onReset() {
    outputChannels = null
    pendingOutputChannels = null
  }
}
