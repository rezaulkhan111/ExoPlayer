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

package com.google.android.exoplayer2.mediacodec

import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PersistableBundle
import android.view.Surface
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.decoder.CryptoInfo
import com.google.android.exoplayer2.util.TraceUtil
import com.google.common.base.Supplier
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget
import kotlin.annotation.MustBeDocumented

/**
 * A [MediaCodecAdapter] that operates the underlying [MediaCodec] in asynchronous mode,
 * routes [MediaCodec.Callback] callbacks on a dedicated thread that is managed internally,
 * and queues input buffers asynchronously.
 */
@RequiresApi(23)
internal class AsynchronousMediaCodecAdapter private constructor(
  private val codec: MediaCodec,
  callbackThread: HandlerThread,
  enqueueingThread: HandlerThread,
  private val synchronizeCodecInteractionsWithQueueing: Boolean,
) : MediaCodecAdapter {

  /** A factory for [AsynchronousMediaCodecAdapter] instances. */
  class Factory @VisibleForTesting internal constructor(
    private val callbackThreadSupplier: Supplier<HandlerThread>,
    private val queueingThreadSupplier: Supplier<HandlerThread>,
    private val synchronizeCodecInteractionsWithQueueing: Boolean,
  ) : MediaCodecAdapter.Factory {

    /**
     * Creates a factory for [AsynchronousMediaCodecAdapter] instances.
     *
     * @param trackType One of [C.TRACK_TYPE_AUDIO] or [C.TRACK_TYPE_VIDEO]. Used for
     *     labelling the internal thread accordingly.
     * @param synchronizeCodecInteractionsWithQueueing Whether the adapter should synchronize [
     *     MediaCodec] interactions with asynchronous buffer queueing. When `true`, codec
     *     interactions will wait until all input buffers pending queueing wil be submitted to the
     *     [MediaCodec].
     */
    constructor(
      @C.TrackType trackType: Int,
      synchronizeCodecInteractionsWithQueueing: Boolean,
    ) : this(
      Supplier { HandlerThread(createCallbackThreadLabel(trackType)) },
      Supplier { HandlerThread(createQueueingThreadLabel(trackType)) },
      synchronizeCodecInteractionsWithQueueing,
    )

    @Throws(IOException::class)
    override fun createAdapter(
      configuration: MediaCodecAdapter.Configuration?,
    ): AsynchronousMediaCodecAdapter {
      val codecName = configuration!!.codecInfo!!.name
      var codecAdapter: AsynchronousMediaCodecAdapter? = null
      var codec: MediaCodec? = null
      try {
        TraceUtil.beginSection("createCodec:$codecName")
        codec = MediaCodec.createByCodecName(codecName)
        codecAdapter =
          AsynchronousMediaCodecAdapter(
            codec,
            callbackThreadSupplier.get(),
            queueingThreadSupplier.get(),
            synchronizeCodecInteractionsWithQueueing,
          )
        TraceUtil.endSection()
        codecAdapter.initialize(
          configuration.mediaFormat,
          configuration.surface,
          configuration.crypto,
          configuration.flags,
        )
        return codecAdapter
      } catch (e: Exception) {
        if (codecAdapter != null) {
          codecAdapter.release()
        } else if (codec != null) {
          codec.release()
        }
        throw e
      }
    }
  }

  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.FIELD)
  @IntDef(STATE_CREATED, STATE_INITIALIZED, STATE_SHUT_DOWN)
  private annotation class State

  private val asynchronousMediaCodecCallback: AsynchronousMediaCodecCallback =
    AsynchronousMediaCodecCallback(callbackThread)
  private val bufferEnqueuer: AsynchronousMediaCodecBufferEnqueuer =
    AsynchronousMediaCodecBufferEnqueuer(codec, enqueueingThread)
  private var codecReleased = false

  private var state: @State Int = STATE_CREATED

  private fun initialize(
    mediaFormat: MediaFormat?,
    surface: Surface?,
    crypto: MediaCrypto?,
    flags: Int,
  ) {
    asynchronousMediaCodecCallback.initialize(codec)
    TraceUtil.beginSection("configureCodec")
    codec.configure(mediaFormat, surface, crypto, flags)
    TraceUtil.endSection()
    bufferEnqueuer.start()
    TraceUtil.beginSection("startCodec")
    codec.start()
    TraceUtil.endSection()
    state = STATE_INITIALIZED
  }

  override fun needsReconfiguration(): Boolean {
    return false
  }

  override fun queueInputBuffer(
    index: Int,
    offset: Int,
    size: Int,
    presentationTimeUs: Long,
    flags: Int,
  ) {
    bufferEnqueuer.queueInputBuffer(index, offset, size, presentationTimeUs, flags)
  }

  override fun queueSecureInputBuffer(
    index: Int,
    offset: Int,
    info: CryptoInfo?,
    presentationTimeUs: Long,
    flags: Int,
  ) {
    bufferEnqueuer.queueSecureInputBuffer(index, offset, info!!, presentationTimeUs, flags)
  }

  override fun releaseOutputBuffer(index: Int, render: Boolean) {
    codec.releaseOutputBuffer(index, render)
  }

  override fun releaseOutputBuffer(index: Int, renderTimeStampNs: Long) {
    codec.releaseOutputBuffer(index, renderTimeStampNs)
  }

  override fun dequeueInputBufferIndex(): Int {
    return asynchronousMediaCodecCallback.dequeueInputBufferIndex()
  }

  override fun dequeueOutputBufferIndex(bufferInfo: MediaCodec.BufferInfo?): Int {
    return asynchronousMediaCodecCallback.dequeueOutputBufferIndex(bufferInfo!!)
  }

  override fun getOutputFormat(): MediaFormat {
    return asynchronousMediaCodecCallback.outputFormat
  }

  override fun getInputBuffer(index: Int): ByteBuffer? {
    return codec.getInputBuffer(index)
  }

  override fun getOutputBuffer(index: Int): ByteBuffer? {
    return codec.getOutputBuffer(index)
  }

  override fun flush() {
    // The order of calls is important:
    // 1. Flush the bufferEnqueuer to stop queueing input buffers.
    // 2. Flush the codec to stop producing available input/output buffers.
    // 3. Flush the callback so that in-flight callbacks are discarded.
    // 4. Start the codec. The asynchronous callback will drop pending callbacks and we can start
    //    the codec now.
    bufferEnqueuer.flush()
    codec.flush()
    asynchronousMediaCodecCallback.flush()
    codec.start()
  }

  override fun release() {
    try {
      if (state == STATE_INITIALIZED) {
        bufferEnqueuer.shutdown()
        asynchronousMediaCodecCallback.shutdown()
      }
      state = STATE_SHUT_DOWN
    } finally {
      if (!codecReleased) {
        codec.release()
        codecReleased = true
      }
    }
  }

  override fun setOnFrameRenderedListener(
    listener: MediaCodecAdapter.OnFrameRenderedListener?,
    handler: Handler?,
  ) {
    maybeBlockOnQueueing()
    codec.setOnFrameRenderedListener(
      if (listener == null) {
        null
      } else {
        MediaCodec.OnFrameRenderedListener { _, presentationTimeUs, nanoTime ->
          listener.onFrameRendered(this, presentationTimeUs, nanoTime)
        }
      },
      handler,
    )
  }

  override fun setOutputSurface(surface: Surface?) {
    maybeBlockOnQueueing()
    codec.setOutputSurface(surface!!)
  }

  override fun setParameters(params: Bundle?) {
    maybeBlockOnQueueing()
    codec.setParameters(params)
  }

  @Suppress("WrongConstant")
  override fun setVideoScalingMode(@C.VideoScalingMode scalingMode: Int) {
    maybeBlockOnQueueing()
    codec.setVideoScalingMode(scalingMode)
  }

  @RequiresApi(26)
  override fun getMetrics(): PersistableBundle? {
    maybeBlockOnQueueing()
    return codec.metrics
  }

  @VisibleForTesting
  internal fun onError(error: MediaCodec.CodecException) {
    asynchronousMediaCodecCallback.onError(codec, error)
  }

  @VisibleForTesting
  internal fun onOutputFormatChanged(format: MediaFormat) {
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, format)
  }

  private fun maybeBlockOnQueueing() {
    if (synchronizeCodecInteractionsWithQueueing) {
      try {
        bufferEnqueuer.waitUntilQueueingComplete()
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        // The playback thread should not be interrupted. Raising this as an
        // IllegalStateException.
        throw IllegalStateException(e)
      }
    }
  }

  private companion object {
    private const val STATE_CREATED = 0
    private const val STATE_INITIALIZED = 1
    private const val STATE_SHUT_DOWN = 2

    private fun createCallbackThreadLabel(@C.TrackType trackType: Int): String {
      return createThreadLabel(trackType, /* prefix= */ "ExoPlayer:MediaCodecAsyncAdapter:")
    }

    private fun createQueueingThreadLabel(@C.TrackType trackType: Int): String {
      return createThreadLabel(trackType, /* prefix= */ "ExoPlayer:MediaCodecQueueingThread:")
    }

    private fun createThreadLabel(@C.TrackType trackType: Int, prefix: String): String {
      val labelBuilder = StringBuilder(prefix)
      if (trackType == C.TRACK_TYPE_AUDIO) {
        labelBuilder.append("Audio")
      } else if (trackType == C.TRACK_TYPE_VIDEO) {
        labelBuilder.append("Video")
      } else {
        labelBuilder.append("Unknown(").append(trackType).append(")")
      }
      return labelBuilder.toString()
    }
  }
}
