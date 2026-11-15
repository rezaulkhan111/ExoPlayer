/*
 * Copyright (C) 2019 The Android Open Source Project
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

import androidx.annotation.CallSuper
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat
import com.google.android.exoplayer2.audio.AudioProcessor.Companion.EMPTY_BUFFER
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Base class for audio processors that keep an output buffer and an internal buffer that is reused
 * whenever input is queued. Subclasses should override [onConfigure] to return
 * the output audio format for the processor if it's active.
 */
abstract class BaseAudioProcessor : AudioProcessor {

  /** The current input audio format. */
  @JvmField
  protected var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET

  /** The current output audio format. */
  @JvmField
  protected var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET

  private var pendingInputAudioFormat: AudioFormat = AudioFormat.NOT_SET
  private var pendingOutputAudioFormat: AudioFormat = AudioFormat.NOT_SET
  private var buffer: ByteBuffer = EMPTY_BUFFER
  private var outputBuffer: ByteBuffer = EMPTY_BUFFER
  private var inputEnded: Boolean = false

  @CanIgnoreReturnValue
  @Throws(UnhandledAudioFormatException::class)
  override fun configure(inputAudioFormat: AudioFormat?): AudioFormat {
    val format = inputAudioFormat ?: AudioFormat.NOT_SET
    pendingInputAudioFormat = format
    pendingOutputAudioFormat = onConfigure(format)
    return if (isActive()) pendingOutputAudioFormat else AudioFormat.NOT_SET
  }

  override fun isActive(): Boolean {
    return pendingOutputAudioFormat != AudioFormat.NOT_SET
  }

  override fun queueEndOfStream() {
    inputEnded = true
    onQueueEndOfStream()
  }

  @CallSuper
  override fun getOutput(): ByteBuffer {
    val outputBuffer = this.outputBuffer
    this.outputBuffer = EMPTY_BUFFER
    return outputBuffer
  }

  @CallSuper
  override fun isEnded(): Boolean {
    return inputEnded && outputBuffer === EMPTY_BUFFER
  }

  override fun flush() {
    outputBuffer = EMPTY_BUFFER
    inputEnded = false
    inputAudioFormat = pendingInputAudioFormat
    outputAudioFormat = pendingOutputAudioFormat
    onFlush()
  }

  override fun reset() {
    flush()
    buffer = EMPTY_BUFFER
    pendingInputAudioFormat = AudioFormat.NOT_SET
    pendingOutputAudioFormat = AudioFormat.NOT_SET
    inputAudioFormat = AudioFormat.NOT_SET
    outputAudioFormat = AudioFormat.NOT_SET
    onReset()
  }

  /**
   * Replaces the current output buffer with a buffer of at least `size` bytes and returns it.
   * Callers should write to the returned buffer then [ByteBuffer.flip] it so it can be read
   * via [getOutput].
   */
  protected fun replaceOutputBuffer(size: Int): ByteBuffer {
    if (buffer.capacity() < size) {
      buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
    } else {
      buffer.clear()
    }
    outputBuffer = buffer
    return buffer
  }

  /** Returns whether the current output buffer has any data remaining. */
  protected fun hasPendingOutput(): Boolean {
    return outputBuffer.hasRemaining()
  }

  /** Called when the processor is configured for a new input format. */
  @CanIgnoreReturnValue
  @Throws(UnhandledAudioFormatException::class)
  protected open fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    return AudioFormat.NOT_SET
  }

  /** Called when the end-of-stream is queued to the processor. */
  protected open fun onQueueEndOfStream() {
    // Do nothing.
  }

  /** Called when the processor is flushed, directly or as part of resetting. */
  protected open fun onFlush() {
    // Do nothing.
  }

  /** Called when the processor is reset. */
  protected open fun onReset() {
    // Do nothing.
  }
}
