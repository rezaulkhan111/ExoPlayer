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

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.util.Assertions.checkState
import com.google.android.exoplayer2.util.Assertions.checkStateNotNull
import com.google.android.exoplayer2.util.Util.castNonNull
import java.util.ArrayDeque

/** A [MediaCodec.Callback] that routes callbacks on a separate thread. */
@RequiresApi(23)
internal class AsynchronousMediaCodecCallback(
  private val callbackThread: HandlerThread,
) : MediaCodec.Callback() {

  private val lock = Any()

  @GuardedBy("lock")
  private val availableInputBuffers: IntArrayQueue = IntArrayQueue()

  @GuardedBy("lock")
  private val availableOutputBuffers: IntArrayQueue = IntArrayQueue()

  @GuardedBy("lock")
  private val bufferInfos: ArrayDeque<MediaCodec.BufferInfo> = ArrayDeque()

  @GuardedBy("lock")
  private val formats: ArrayDeque<MediaFormat> = ArrayDeque()

  private var handler: Handler? = null

  @GuardedBy("lock")
  private var currentFormat: MediaFormat? = null

  @GuardedBy("lock")
  private var pendingOutputFormat: MediaFormat? = null

  @GuardedBy("lock")
  private var mediaCodecException: MediaCodec.CodecException? = null

  @GuardedBy("lock")
  private var pendingFlushCount: Long = 0

  @GuardedBy("lock")
  private var shutDown = false

  @GuardedBy("lock")
  private var internalException: IllegalStateException? = null

  /**
   * Sets the callback on `codec` and starts the background callback thread.
   *
   * <p>Make sure to call [shutdown] to stop the background thread and release its
   * resources.
   *
   * @see MediaCodec.setCallback
   */
  fun initialize(codec: MediaCodec) {
    checkState(handler == null)

    callbackThread.start()
    val handler = Handler(callbackThread.looper)
    codec.setCallback(this, handler)
    // Initialize this.handler at the very end ensuring the callback in not considered configured
    // if MediaCodec raises an exception.
    this.handler = handler
  }

  /**
   * Shuts down this instance.
   *
   * <p>This method will stop the callback thread. After calling it, callbacks will no longer be
   * handled and dequeue methods will return [MediaCodec.INFO_TRY_AGAIN_LATER].
   */
  fun shutdown() {
    synchronized(lock) {
      shutDown = true
      callbackThread.quit()
      flushInternal()
    }
  }

  /**
   * Returns the next available input buffer index or [MediaCodec.INFO_TRY_AGAIN_LATER] if no
   * such buffer exists.
   */
  fun dequeueInputBufferIndex(): Int {
    synchronized(lock) {
      return if (isFlushingOrShutdown) {
        MediaCodec.INFO_TRY_AGAIN_LATER
      } else {
        maybeThrowException()
        if (availableInputBuffers.isEmpty) {
          MediaCodec.INFO_TRY_AGAIN_LATER
        } else {
          availableInputBuffers.remove()
        }
      }
    }
  }

  /**
   * Returns the next available output buffer index. If the next available output is a MediaFormat
   * change, it will return [MediaCodec.INFO_OUTPUT_FORMAT_CHANGED] and you should call [getOutputFormat]
   * to get the format. If there is no available output, this method will return
   * [MediaCodec.INFO_TRY_AGAIN_LATER].
   */
  fun dequeueOutputBufferIndex(bufferInfo: MediaCodec.BufferInfo): Int {
    synchronized(lock) {
      if (isFlushingOrShutdown) {
        return MediaCodec.INFO_TRY_AGAIN_LATER
      } else {
        maybeThrowException()
        if (availableOutputBuffers.isEmpty) {
          return MediaCodec.INFO_TRY_AGAIN_LATER
        } else {
          val bufferIndex = availableOutputBuffers.remove()
          if (bufferIndex >= 0) {
            checkStateNotNull(currentFormat)
            val nextBufferInfo = bufferInfos.remove()
            bufferInfo.set(
              nextBufferInfo.offset,
              nextBufferInfo.size,
              nextBufferInfo.presentationTimeUs,
              nextBufferInfo.flags,
            )
          } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            currentFormat = formats.remove()
          }
          return bufferIndex
        }
      }
    }
  }

  /**
   * Returns the [MediaFormat] signalled by the underlying [MediaCodec].
   *
   * <p>Call this <b>after</b> [dequeueOutputBufferIndex] returned [MediaCodec.INFO_OUTPUT_FORMAT_CHANGED].
   *
   * @throws IllegalStateException If called before [dequeueOutputBufferIndex] has returned
   *     [MediaCodec.INFO_OUTPUT_FORMAT_CHANGED].
   */
  fun getOutputFormat(): MediaFormat {
    synchronized(lock) {
      if (currentFormat == null) {
        throw IllegalStateException()
      }
      return currentFormat!!
    }
  }

  /**
   * Initiates a flush asynchronously, which will be completed on the callback thread. When the
   * flush is complete, it will trigger `onFlushCompleted` from the callback thread.
   */
  fun flush() {
    synchronized(lock) {
      ++pendingFlushCount
      castNonNull(handler)!!.post { onFlushCompleted() }
    }
  }

  // Called from the callback thread.

  override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
    synchronized(lock) {
      availableInputBuffers.add(index)
    }
  }

  override fun onOutputBufferAvailable(
    codec: MediaCodec,
    index: Int,
    info: MediaCodec.BufferInfo,
  ) {
    synchronized(lock) {
      if (pendingOutputFormat != null) {
        addOutputFormat(pendingOutputFormat!!)
        pendingOutputFormat = null
      }
      availableOutputBuffers.add(index)
      bufferInfos.add(info)
    }
  }

  override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
    synchronized(lock) {
      mediaCodecException = e
    }
  }

  override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
    synchronized(lock) {
      addOutputFormat(format)
      pendingOutputFormat = null
    }
  }

  private fun onFlushCompleted() {
    synchronized(lock) {
      if (shutDown) {
        return
      }

      --pendingFlushCount
      if (pendingFlushCount > 0) {
        // Another flush() has been called.
        return
      } else if (pendingFlushCount < 0) {
        // This should never happen.
        setInternalException(IllegalStateException())
        return
      }
      flushInternal()
    }
  }

  /** Flushes all available input and output buffers and any error that was previously set. */
  @GuardedBy("lock")
  private fun flushInternal() {
    if (!formats.isEmpty()) {
      pendingOutputFormat = formats.last
    }
    // else, pendingOutputFormat may already be non-null following a previous flush, and remains
    // set in this case.

    availableInputBuffers.clear()
    availableOutputBuffers.clear()
    bufferInfos.clear()
    formats.clear()
    mediaCodecException = null
  }

  @get:GuardedBy("lock")
  private val isFlushingOrShutdown: Boolean
    get() = pendingFlushCount > 0 || shutDown

  @GuardedBy("lock")
  private fun addOutputFormat(mediaFormat: MediaFormat) {
    availableOutputBuffers.add(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
    formats.add(mediaFormat)
  }

  @GuardedBy("lock")
  private fun maybeThrowException() {
    maybeThrowInternalException()
    maybeThrowMediaCodecException()
  }

  @GuardedBy("lock")
  private fun maybeThrowInternalException() {
    if (internalException != null) {
      val e = internalException
      internalException = null
      throw e!!
    }
  }

  @GuardedBy("lock")
  private fun maybeThrowMediaCodecException() {
    if (mediaCodecException != null) {
      val codecException = mediaCodecException
      mediaCodecException = null
      throw codecException!!
    }
  }

  private fun setInternalException(e: IllegalStateException) {
    synchronized(lock) {
      internalException = e
    }
  }
}
