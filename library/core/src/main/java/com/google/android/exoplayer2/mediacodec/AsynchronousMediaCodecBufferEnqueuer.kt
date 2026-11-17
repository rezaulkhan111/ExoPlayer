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
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.decoder.CryptoInfo
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.ConditionVariable
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.util.Util.castNonNull
import java.util.ArrayDeque
import java.util.Arrays
import java.util.concurrent.atomic.AtomicReference

/**
 * Performs [MediaCodec] input buffer queueing on a background thread.
 *
 * <p>The implementation of this class assumes that its public methods will be called from the same
 * thread.
 */
@RequiresApi(23)
internal class AsynchronousMediaCodecBufferEnqueuer @VisibleForTesting internal constructor(
  private val codec: MediaCodec,
  private val handlerThread: HandlerThread,
  private val conditionVariable: ConditionVariable,
) {

  private var handler: Handler? = null
  private val pendingRuntimeException: AtomicReference<RuntimeException?> = AtomicReference()
  private var started = false

  /**
   * Creates a new instance that submits input buffers on the specified [MediaCodec].
   *
   * @param codec The [MediaCodec] to submit input buffers to.
   * @param queueingThread The [HandlerThread] to use for queueing buffers.
   */
  constructor(codec: MediaCodec, queueingThread: HandlerThread) : this(
    codec,
    queueingThread,
    ConditionVariable(),
  )

  /**
   * Starts this instance.
   *
   * <p>Call this method after creating an instance and before queueing input buffers.
   */
  fun start() {
    if (!started) {
      handlerThread.start()
      handler =
        object : Handler(handlerThread.looper) {
          override fun handleMessage(msg: Message) {
            doHandleMessage(msg)
          }
        }
      started = true
    }
  }

  /**
   * Submits an input buffer for decoding.
   *
   * @see android.media.MediaCodec.queueInputBuffer
   */
  fun queueInputBuffer(
    index: Int,
    offset: Int,
    size: Int,
    presentationTimeUs: Long,
    flags: Int,
  ) {
    maybeThrowException()
    val messageParams = getMessageParams()
    messageParams.setQueueParams(index, offset, size, presentationTimeUs, flags)
    val message = castNonNull(handler)!!.obtainMessage(MSG_QUEUE_INPUT_BUFFER, messageParams)
    message.sendToTarget()
  }

  /**
   * Submits an input buffer that potentially contains encrypted data for decoding.
   *
   * <p>Note: This method behaves as [MediaCodec.queueSecureInputBuffer] with the difference
   * that `info` is of type [CryptoInfo] and not [android.media.MediaCodec.CryptoInfo].
   *
   * @see android.media.MediaCodec.queueSecureInputBuffer
   */
  fun queueSecureInputBuffer(
    index: Int,
    offset: Int,
    info: CryptoInfo,
    presentationTimeUs: Long,
    flags: Int,
  ) {
    maybeThrowException()
    val messageParams = getMessageParams()
    messageParams.setQueueParams(index, offset, /* size= */ 0, presentationTimeUs, flags)
    copy(info, messageParams.cryptoInfo)
    val message =
      castNonNull(handler)!!.obtainMessage(MSG_QUEUE_SECURE_INPUT_BUFFER, messageParams)
    message.sendToTarget()
  }

  /** Flushes the instance. */
  fun flush() {
    if (started) {
      try {
        flushHandlerThread()
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        // The playback thread should not be interrupted. Raising this as an
        // IllegalStateException.
        throw IllegalStateException(e)
      }
    }
  }

  /** Shuts down the instance. Make sure to call this method to release its internal resources. */
  fun shutdown() {
    if (started) {
      flush()
      handlerThread.quit()
    }
    started = false
  }

  /** Blocks the current thread until all input buffers pending queueing are submitted. */
  @Throws(InterruptedException::class)
  fun waitUntilQueueingComplete() {
    blockUntilHandlerThreadIsIdle()
  }

  private fun maybeThrowException() {
    val exception = pendingRuntimeException.getAndSet(null)
    if (exception != null) {
      throw exception
    }
  }

  /**
   * Empties all tasks enqueued on the [handlerThread] via the [handler]. This method
   * blocks until the [handlerThread] is idle.
   */
  @Throws(InterruptedException::class)
  private fun flushHandlerThread() {
    checkNotNull(handler)!!.removeCallbacksAndMessages(null)
    blockUntilHandlerThreadIsIdle()
  }

  @Throws(InterruptedException::class)
  private fun blockUntilHandlerThreadIsIdle() {
    conditionVariable.close()
    checkNotNull(handler)!!.obtainMessage(MSG_OPEN_CV).sendToTarget()
    conditionVariable.block()
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  internal fun setPendingRuntimeException(exception: RuntimeException) {
    pendingRuntimeException.set(exception)
  }

  // Called from the handler thread

  private fun doHandleMessage(msg: Message) {
    var params: MessageParams? = null
    when (msg.what) {
      MSG_QUEUE_INPUT_BUFFER -> {
        params = msg.obj as MessageParams
        doQueueInputBuffer(
          params.index,
          params.offset,
          params.size,
          params.presentationTimeUs,
          params.flags,
        )
      }
      MSG_QUEUE_SECURE_INPUT_BUFFER -> {
        params = msg.obj as MessageParams
        doQueueSecureInputBuffer(
          params.index,
          params.offset,
          params.cryptoInfo,
          params.presentationTimeUs,
          params.flags,
        )
      }
      MSG_OPEN_CV -> conditionVariable.open()
      else ->
        pendingRuntimeException.compareAndSet(
          null,
          IllegalStateException(msg.what.toString()),
        )
    }
    if (params != null) {
      recycleMessageParams(params)
    }
  }

  private fun doQueueInputBuffer(
    index: Int,
    offset: Int,
    size: Int,
    presentationTimeUs: Long,
    flag: Int,
  ) {
    try {
      codec.queueInputBuffer(index, offset, size, presentationTimeUs, flag)
    } catch (e: RuntimeException) {
      pendingRuntimeException.compareAndSet(null, e)
    }
  }

  private fun doQueueSecureInputBuffer(
    index: Int,
    offset: Int,
    info: MediaCodec.CryptoInfo,
    presentationTimeUs: Long,
    flags: Int,
  ) {
    try {
      // Synchronize calls to MediaCodec.queueSecureInputBuffer() to avoid race conditions inside
      // the crypto module when audio and video are sharing the same DRM session
      // (see [Internal: b/149908061]).
      synchronized(QUEUE_SECURE_LOCK) {
        codec.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags)
      }
    } catch (e: RuntimeException) {
      pendingRuntimeException.compareAndSet(null, e)
    }
  }

  /** Parameters for queue input buffer and queue secure input buffer tasks. */
  private class MessageParams {
    @JvmField var index: Int = 0
    @JvmField var offset: Int = 0
    @JvmField var size: Int = 0
    @JvmField val cryptoInfo: MediaCodec.CryptoInfo = MediaCodec.CryptoInfo()
    @JvmField var presentationTimeUs: Long = 0
    @JvmField var flags: Int = 0

    /** Convenience method for setting the queueing parameters. */
    fun setQueueParams(
      index: Int,
      offset: Int,
      size: Int,
      presentationTimeUs: Long,
      flags: Int,
    ) {
      this.index = index
      this.offset = offset
      this.size = size
      this.presentationTimeUs = presentationTimeUs
      this.flags = flags
    }
  }

  private companion object {
    private const val MSG_QUEUE_INPUT_BUFFER = 0
    private const val MSG_QUEUE_SECURE_INPUT_BUFFER = 1
    private const val MSG_OPEN_CV = 2

    @GuardedBy("MESSAGE_PARAMS_INSTANCE_POOL")
    private val MESSAGE_PARAMS_INSTANCE_POOL = ArrayDeque<MessageParams>()

    private val QUEUE_SECURE_LOCK = Any()

    private fun getMessageParams(): MessageParams {
      synchronized(MESSAGE_PARAMS_INSTANCE_POOL) {
        return if (MESSAGE_PARAMS_INSTANCE_POOL.isEmpty()) {
          MessageParams()
        } else {
          MESSAGE_PARAMS_INSTANCE_POOL.removeFirst()
        }
      }
    }

    private fun recycleMessageParams(params: MessageParams) {
      synchronized(MESSAGE_PARAMS_INSTANCE_POOL) {
        MESSAGE_PARAMS_INSTANCE_POOL.add(params)
      }
    }

    /** Performs a deep copy of [cryptoInfo] to [frameworkCryptoInfo]. */
    @android.annotation.SuppressLint("NewApi")
    private fun copy(
      cryptoInfo: CryptoInfo,
      frameworkCryptoInfo: MediaCodec.CryptoInfo,
    ) {
      // Update frameworkCryptoInfo fields directly because CryptoInfo.set performs an unnecessary
      // object allocation on Android N.
      frameworkCryptoInfo.numSubSamples = cryptoInfo.numSubSamples
      frameworkCryptoInfo.numBytesOfClearData =
        copy(cryptoInfo.numBytesOfClearData, frameworkCryptoInfo.numBytesOfClearData)
      frameworkCryptoInfo.numBytesOfEncryptedData =
        copy(cryptoInfo.numBytesOfEncryptedData, frameworkCryptoInfo.numBytesOfEncryptedData)
      frameworkCryptoInfo.key = checkNotNull(copy(cryptoInfo.key, frameworkCryptoInfo.key))!!
      frameworkCryptoInfo.iv = checkNotNull(copy(cryptoInfo.iv, frameworkCryptoInfo.iv))!!
      frameworkCryptoInfo.mode = cryptoInfo.mode
      if (Util.SDK_INT >= 24) {
        setPatternV24(cryptoInfo, frameworkCryptoInfo)
      }
    }

    @android.annotation.SuppressLint("NewApi")
    @RequiresApi(24)
    private fun setPatternV24(
      cryptoInfo: CryptoInfo,
      frameworkCryptoInfo: MediaCodec.CryptoInfo,
    ) {
      val pattern =
        MediaCodec.CryptoInfo.Pattern(
          cryptoInfo.encryptedBlocks,
          cryptoInfo.clearBlocks,
        )
      frameworkCryptoInfo.setPattern(pattern)
    }

    /**
     * Copies [src], reusing [dst] if it's at least as long as [src].
     *
     * @param src The source array.
     * @param dst The destination array, which will be reused if it's at least as long as [src].
     * @return The copy, which may be [dst] if it was reused.
     */
    private fun copy(src: IntArray?, dst: IntArray?): IntArray? {
      if (src == null) {
        return dst
      }

      return if (dst == null || dst.size < src.size) {
        Arrays.copyOf(src, src.size)
      } else {
        System.arraycopy(src, 0, dst, 0, src.size)
        dst
      }
    }

    /**
     * Copies [src], reusing [dst] if it's at least as long as [src].
     *
     * @param src The source array.
     * @param dst The destination array, which will be reused if it's at least as long as [src].
     * @return The copy, which may be [dst] if it was reused.
     */
    private fun copy(src: ByteArray?, dst: ByteArray?): ByteArray? {
      if (src == null) {
        return dst
      }

      return if (dst == null || dst.size < src.size) {
        Arrays.copyOf(src, src.size)
      } else {
        System.arraycopy(src, 0, dst, 0, src.size)
        dst
      }
    }
  }
}
