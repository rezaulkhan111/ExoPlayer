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
import com.google.android.exoplayer2.util.Util
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.nio.ByteBuffer

/** Audio processor for trimming samples from the start/end of data. */
internal class TrimmingAudioProcessor : BaseAudioProcessor() {

  private var trimStartFrames: Int = 0
  private var trimEndFrames: Int = 0
  private var reconfigurationPending: Boolean = false

  private var pendingTrimStartBytes: Int = 0
  private var endBuffer: ByteArray = Util.EMPTY_BYTE_ARRAY
  private var endBufferSize: Int = 0

  /**
   * Returns the number of audio frames trimmed since the last call to [resetTrimmedFrameCount].
   */
  var trimmedFrameCount: Long = 0
    private set

  /**
   * Sets the number of audio frames to trim from the start and end of audio passed to this
   * processor. After calling this method, call [configure] to apply the new
   * trimming frame counts.
   *
   * <p>See [AudioSink.configure].
   *
   * @param trimStartFrames The number of audio frames to trim from the start of audio.
   * @param trimEndFrames The number of audio frames to trim from the end of audio.
   */
  fun setTrimFrameCount(trimStartFrames: Int, trimEndFrames: Int) {
    this.trimStartFrames = trimStartFrames
    this.trimEndFrames = trimEndFrames
  }

  /** Sets the trimmed frame count returned by [trimmedFrameCount] to zero. */
  fun resetTrimmedFrameCount() {
    trimmedFrameCount = 0
  }

  @CanIgnoreReturnValue
  @Throws(UnhandledAudioFormatException::class)
  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    if (inputAudioFormat.encoding != OUTPUT_ENCODING) {
      throw UnhandledAudioFormatException(inputAudioFormat)
    }
    reconfigurationPending = true
    return if (trimStartFrames != 0 || trimEndFrames != 0) inputAudioFormat else AudioFormat.NOT_SET
  }

  override fun queueInput(inputBuffer: ByteBuffer?) {
    if (inputBuffer == null) {
      return
    }
    val position = inputBuffer.position()
    val limit = inputBuffer.limit()
    var remaining = limit - position

    if (remaining == 0) {
      return
    }

    // Trim any pending start bytes from the input buffer.
    val trimBytes = minOf(remaining, pendingTrimStartBytes)
    trimmedFrameCount += (trimBytes / inputAudioFormat.bytesPerFrame).toLong()
    pendingTrimStartBytes -= trimBytes
    inputBuffer.position(position + trimBytes)
    if (pendingTrimStartBytes > 0) {
      // Nothing to output yet.
      return
    }
    remaining -= trimBytes

    // endBuffer must be kept as full as possible, so that we trim the right amount of media if we
    // don't receive any more input. After taking into account the number of bytes needed to keep
    // endBuffer as full as possible, the output should be any surplus bytes currently in endBuffer
    // followed by any surplus bytes in the new inputBuffer.
    var remainingBytesToOutput = endBufferSize + remaining - endBuffer.size
    val buffer = replaceOutputBuffer(remainingBytesToOutput)

    // Output from endBuffer.
    val endBufferBytesToOutput = Util.constrainValue(remainingBytesToOutput, 0, endBufferSize)
    buffer.put(endBuffer, 0, endBufferBytesToOutput)
    remainingBytesToOutput -= endBufferBytesToOutput

    // Output from inputBuffer, restoring its limit afterwards.
    val inputBufferBytesToOutput = Util.constrainValue(remainingBytesToOutput, 0, remaining)
    inputBuffer.limit(inputBuffer.position() + inputBufferBytesToOutput)
    buffer.put(inputBuffer)
    inputBuffer.limit(limit)
    remaining -= inputBufferBytesToOutput

    // Compact endBuffer, then repopulate it using the new input.
    endBufferSize -= endBufferBytesToOutput
    System.arraycopy(endBuffer, endBufferBytesToOutput, endBuffer, 0, endBufferSize)
    inputBuffer.get(endBuffer, endBufferSize, remaining)
    endBufferSize += remaining

    buffer.flip()
  }

  override fun getOutput(): ByteBuffer {
    if (super.isEnded() && endBufferSize > 0) {
      // Because audio processors may be drained in the middle of the stream we assume that the
      // contents of the end buffer need to be output. For gapless transitions, configure will
      // always be called, so the end buffer is cleared in onQueueEndOfStream.
      replaceOutputBuffer(endBufferSize).put(endBuffer, 0, endBufferSize).flip()
      endBufferSize = 0
    }
    return super.getOutput()
  }

  override fun isEnded(): Boolean {
    return super.isEnded() && endBufferSize == 0
  }

  override fun onQueueEndOfStream() {
    if (reconfigurationPending) {
      // Trim audio in the end buffer.
      if (endBufferSize > 0) {
        trimmedFrameCount += (endBufferSize / inputAudioFormat.bytesPerFrame).toLong()
      }
      endBufferSize = 0
    }
  }

  override fun onFlush() {
    if (reconfigurationPending) {
      // Flushing activates the new configuration, so prepare to trim bytes from the start/end.
      reconfigurationPending = false
      endBuffer = ByteArray(trimEndFrames * inputAudioFormat.bytesPerFrame)
      pendingTrimStartBytes = trimStartFrames * inputAudioFormat.bytesPerFrame
    }

    // TODO(internal b/77292509): Flushing occurs to activate a configuration (handled above) but
    // also when seeking within a stream. This implementation currently doesn't handle seek to start
    // (where we need to trim at the start again), nor seeks to non-zero positions before start
    // trimming has occurred (where we should set pendingTrimStartBytes to zero). These cases can be
    // fixed by trimming in queueInput based on timestamp, once that information is available.

    // Any data in the end buffer should no longer be output if we are playing from a different
    // position, so discard it and refill the buffer using new input.
    endBufferSize = 0
  }

  override fun onReset() {
    endBuffer = Util.EMPTY_BYTE_ARRAY
  }

  companion object {
    private const val OUTPUT_ENCODING = C.ENCODING_PCM_16BIT
  }
}
