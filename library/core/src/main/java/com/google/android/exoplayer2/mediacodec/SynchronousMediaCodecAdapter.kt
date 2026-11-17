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
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import android.view.Surface
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.decoder.CryptoInfo
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.TraceUtil
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.util.Util.castNonNull
import java.io.IOException
import java.nio.ByteBuffer

/**
 * A [MediaCodecAdapter] that operates the underlying [MediaCodec] in synchronous mode.
 */
class SynchronousMediaCodecAdapter private constructor(
  private val codec: MediaCodec
) : MediaCodecAdapter {

  /** A factory for [SynchronousMediaCodecAdapter] instances. */
  open class Factory : MediaCodecAdapter.Factory {

    @Throws(IOException::class)
    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter {
      var codec: MediaCodec? = null
      try {
        codec = createCodec(configuration)
        TraceUtil.beginSection("configureCodec")
        codec.configure(
          configuration.mediaFormat,
          configuration.surface,
          configuration.crypto,
          configuration.flags
        )
        TraceUtil.endSection()
        TraceUtil.beginSection("startCodec")
        codec.start()
        TraceUtil.endSection()
        return SynchronousMediaCodecAdapter(codec)
      } catch (e: Exception) {
        if (codec != null) {
          codec.release()
        }
        throw e
      }
    }

    /** Creates a new [MediaCodec] instance. */
    @Throws(IOException::class)
    protected open fun createCodec(configuration: MediaCodecAdapter.Configuration): MediaCodec {
      checkNotNull(configuration.codecInfo)
      val codecName = configuration.codecInfo!!.name
      TraceUtil.beginSection("createCodec:$codecName")
      val mediaCodec = MediaCodec.createByCodecName(codecName)
      TraceUtil.endSection()
      return mediaCodec
    }
  }

  private var inputByteBuffers: Array<ByteBuffer>? = null
  private var outputByteBuffers: Array<ByteBuffer>? = null

  init {
    if (Util.SDK_INT < 21) {
      inputByteBuffers = codec.inputBuffers
      outputByteBuffers = codec.outputBuffers
    }
  }

  override fun needsReconfiguration(): Boolean {
    return false
  }

  override fun dequeueInputBufferIndex(): Int {
    return codec.dequeueInputBuffer(0)
  }

  override fun dequeueOutputBufferIndex(bufferInfo: MediaCodec.BufferInfo): Int {
    var index: Int
    do {
      index = codec.dequeueOutputBuffer(bufferInfo, 0)
      if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED && Util.SDK_INT < 21) {
        outputByteBuffers = codec.outputBuffers
      }
    } while (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)

    return index
  }

  override fun getOutputFormat(): MediaFormat {
    return codec.outputFormat
  }

  override fun getInputBuffer(index: Int): ByteBuffer? {
    return if (Util.SDK_INT >= 21) {
      codec.getInputBuffer(index)
    } else {
      castNonNull(inputByteBuffers)!![index]
    }
  }

  override fun getOutputBuffer(index: Int): ByteBuffer? {
    return if (Util.SDK_INT >= 21) {
      codec.getOutputBuffer(index)
    } else {
      castNonNull(outputByteBuffers)!![index]
    }
  }

  override fun queueInputBuffer(
    index: Int,
    offset: Int,
    size: Int,
    presentationTimeUs: Long,
    flags: Int
  ) {
    codec.queueInputBuffer(index, offset, size, presentationTimeUs, flags)
  }

  override fun queueSecureInputBuffer(
    index: Int,
    offset: Int,
    info: CryptoInfo,
    presentationTimeUs: Long,
    flags: Int
  ) {
    codec.queueSecureInputBuffer(
      index, offset, info.frameworkCryptoInfo, presentationTimeUs, flags
    )
  }

  override fun releaseOutputBuffer(index: Int, render: Boolean) {
    codec.releaseOutputBuffer(index, render)
  }

  @RequiresApi(21)
  override fun releaseOutputBuffer(index: Int, renderTimeStampNs: Long) {
    codec.releaseOutputBuffer(index, renderTimeStampNs)
  }

  override fun flush() {
    codec.flush()
  }

  override fun release() {
    inputByteBuffers = null
    outputByteBuffers = null
    codec.release()
  }

  @RequiresApi(23)
  override fun setOnFrameRenderedListener(
    listener: MediaCodecAdapter.OnFrameRenderedListener,
    handler: Handler
  ) {
    codec.setOnFrameRenderedListener(
      { _, presentationTimeUs, nanoTime ->
        listener.onFrameRendered(
          this@SynchronousMediaCodecAdapter, presentationTimeUs, nanoTime
        )
      },
      handler
    )
  }

  @RequiresApi(23)
  override fun setOutputSurface(surface: Surface) {
    codec.setOutputSurface(surface)
  }

  @RequiresApi(19)
  override fun setParameters(params: Bundle) {
    codec.setParameters(params)
  }

  override fun setVideoScalingMode(@C.VideoScalingMode scalingMode: Int) {
    codec.setVideoScalingMode(scalingMode)
  }

  @RequiresApi(26)
  override fun getMetrics(): PersistableBundle? {
    return codec.metrics
  }
}
