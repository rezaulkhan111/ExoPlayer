/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.google.android.exoplayer2.audio.DefaultAudioSink.Companion.OUTPUT_MODE_OFFLOAD
import com.google.android.exoplayer2.audio.DefaultAudioSink.Companion.OUTPUT_MODE_PASSTHROUGH
import com.google.android.exoplayer2.audio.DefaultAudioSink.Companion.OUTPUT_MODE_PCM
import com.google.android.exoplayer2.util.Util.constrainValue
import com.google.common.primitives.Ints.checkedCast
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.lang.Math.max

/** Provide the buffer size to use when creating an [android.media.AudioTrack]. */
open class DefaultAudioTrackBufferSizeProvider protected constructor(builder: Builder) :
  DefaultAudioSink.AudioTrackBufferSizeProvider {

  /** A builder to create [DefaultAudioTrackBufferSizeProvider] instances. */
  open class Builder {
    internal var minPcmBufferDurationUs: Int = MIN_PCM_BUFFER_DURATION_US
    internal var maxPcmBufferDurationUs: Int = MAX_PCM_BUFFER_DURATION_US
    internal var pcmBufferMultiplicationFactor: Int = PCM_BUFFER_MULTIPLICATION_FACTOR
    internal var passthroughBufferDurationUs: Int = PASSTHROUGH_BUFFER_DURATION_US
    internal var offloadBufferDurationUs: Int = OFFLOAD_BUFFER_DURATION_US
    internal var ac3BufferMultiplicationFactor: Int = AC3_BUFFER_MULTIPLICATION_FACTOR

    /**
     * Sets the minimum length for PCM [android.media.AudioTrack] buffers, in microseconds.
     */
    @CanIgnoreReturnValue
    fun setMinPcmBufferDurationUs(minPcmBufferDurationUs: Int): Builder {
      this.minPcmBufferDurationUs = minPcmBufferDurationUs
      return this
    }

    /**
     * Sets the maximum length for PCM [android.media.AudioTrack] buffers, in microseconds.
     */
    @CanIgnoreReturnValue
    fun setMaxPcmBufferDurationUs(maxPcmBufferDurationUs: Int): Builder {
      this.maxPcmBufferDurationUs = maxPcmBufferDurationUs
      return this
    }

    /**
     * Sets the multiplication factor to apply to the minimum buffer size requested.
     */
    @CanIgnoreReturnValue
    fun setPcmBufferMultiplicationFactor(pcmBufferMultiplicationFactor: Int): Builder {
      this.pcmBufferMultiplicationFactor = pcmBufferMultiplicationFactor
      return this
    }

    /**
     * Sets the length for passthrough [android.media.AudioTrack] buffers, in microseconds.
     */
    @CanIgnoreReturnValue
    fun setPassthroughBufferDurationUs(passthroughBufferDurationUs: Int): Builder {
      this.passthroughBufferDurationUs = passthroughBufferDurationUs
      return this
    }

    /**
     * The length for offload [android.media.AudioTrack] buffers, in microseconds.
     */
    @CanIgnoreReturnValue
    fun setOffloadBufferDurationUs(offloadBufferDurationUs: Int): Builder {
      this.offloadBufferDurationUs = offloadBufferDurationUs
      return this
    }

    /**
     * Sets the multiplication factor to apply to the passthrough buffer for AC3 to avoid underruns
     * on some devices (e.g., Broadcom 7271).
     */
    @CanIgnoreReturnValue
    fun setAc3BufferMultiplicationFactor(ac3BufferMultiplicationFactor: Int): Builder {
      this.ac3BufferMultiplicationFactor = ac3BufferMultiplicationFactor
      return this
    }

    /** Build the [DefaultAudioTrackBufferSizeProvider]. */
    fun build(): DefaultAudioTrackBufferSizeProvider {
      return DefaultAudioTrackBufferSizeProvider(this)
    }
  }

  /** The minimum length for PCM [android.media.AudioTrack] buffers, in microseconds. */
  @JvmField
  protected val minPcmBufferDurationUs: Int = builder.minPcmBufferDurationUs

  /** The maximum length for PCM [android.media.AudioTrack] buffers, in microseconds. */
  @JvmField
  protected val maxPcmBufferDurationUs: Int = builder.maxPcmBufferDurationUs

  /** The multiplication factor to apply to the minimum buffer size requested. */
  @JvmField
  protected val pcmBufferMultiplicationFactor: Int = builder.pcmBufferMultiplicationFactor

  /** The length for passthrough [android.media.AudioTrack] buffers, in microseconds. */
  @JvmField
  protected val passthroughBufferDurationUs: Int = builder.passthroughBufferDurationUs

  /** The length for offload [android.media.AudioTrack] buffers, in microseconds. */
  @JvmField
  protected val offloadBufferDurationUs: Int = builder.offloadBufferDurationUs

  /**
   * The multiplication factor to apply to AC3 passthrough buffer to avoid underruns on some devices
   * (e.g., Broadcom 7271).
   */
  @JvmField
  val ac3BufferMultiplicationFactor: Int = builder.ac3BufferMultiplicationFactor

  override fun getBufferSizeInBytes(
    minBufferSizeInBytes: Int,
    encoding: Int,
    outputMode: Int,
    pcmFrameSize: Int,
    sampleRate: Int,
    maxAudioTrackPlaybackSpeed: Double
  ): Int {
    var bufferSize =
      get1xBufferSizeInBytes(minBufferSizeInBytes, encoding, outputMode, pcmFrameSize, sampleRate)
    bufferSize = (bufferSize * maxAudioTrackPlaybackSpeed).toInt()
    bufferSize = max(minBufferSizeInBytes, bufferSize)
    return (bufferSize + pcmFrameSize - 1) / pcmFrameSize * pcmFrameSize
  }

  /** Returns the buffer size for playback at 1x speed. */
  protected open fun get1xBufferSizeInBytes(
    minBufferSizeInBytes: Int,
    encoding: Int,
    outputMode: Int,
    pcmFrameSize: Int,
    sampleRate: Int
  ): Int {
    return when (outputMode) {
      OUTPUT_MODE_PCM -> getPcmBufferSizeInBytes(minBufferSizeInBytes, sampleRate, pcmFrameSize)
      OUTPUT_MODE_PASSTHROUGH -> getPassthroughBufferSizeInBytes(encoding)
      OUTPUT_MODE_OFFLOAD -> getOffloadBufferSizeInBytes(encoding)
      else -> throw IllegalArgumentException()
    }
  }

  /** Returns the buffer size for PCM playback. */
  protected open fun getPcmBufferSizeInBytes(
    minBufferSizeInBytes: Int,
    samplingRate: Int,
    frameSize: Int
  ): Int {
    val targetBufferSize = minBufferSizeInBytes * pcmBufferMultiplicationFactor
    val minAppBufferSize = durationUsToBytes(minPcmBufferDurationUs, samplingRate, frameSize)
    val maxAppBufferSize = durationUsToBytes(maxPcmBufferDurationUs, samplingRate, frameSize)
    return constrainValue(targetBufferSize, minAppBufferSize, maxAppBufferSize)
  }

  /** Returns the buffer size for passthrough playback. */
  protected open fun getPassthroughBufferSizeInBytes(encoding: Int): Int {
    var bufferSizeUs = passthroughBufferDurationUs
    if (encoding == C.ENCODING_AC3) {
      bufferSizeUs *= ac3BufferMultiplicationFactor
    }
    val maxByteRate = getMaximumEncodedRateBytesPerSecond(encoding)
    return checkedCast(bufferSizeUs.toLong() * maxByteRate / C.MICROS_PER_SECOND)
  }

  /** Returns the buffer size for offload playback. */
  protected open fun getOffloadBufferSizeInBytes(encoding: Int): Int {
    val maxByteRate = getMaximumEncodedRateBytesPerSecond(encoding)
    return checkedCast(offloadBufferDurationUs.toLong() * maxByteRate / C.MICROS_PER_SECOND)
  }

  companion object {
    private const val MIN_PCM_BUFFER_DURATION_US = 250_000
    private const val MAX_PCM_BUFFER_DURATION_US = 750_000
    private const val PCM_BUFFER_MULTIPLICATION_FACTOR = 4
    private const val PASSTHROUGH_BUFFER_DURATION_US = 250_000
    private const val OFFLOAD_BUFFER_DURATION_US = 50_000_000
    private const val AC3_BUFFER_MULTIPLICATION_FACTOR = 2

    @JvmStatic
    protected fun durationUsToBytes(durationUs: Int, samplingRate: Int, frameSize: Int): Int {
      return checkedCast(durationUs.toLong() * samplingRate * frameSize / C.MICROS_PER_SECOND)
    }

    @JvmStatic
    protected fun getMaximumEncodedRateBytesPerSecond(encoding: Int): Int {
      return when (encoding) {
        C.ENCODING_MP3 -> MpegAudioUtil.MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_AAC_LC -> AacUtil.AAC_LC_MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_AAC_HE_V1 -> AacUtil.AAC_HE_V1_MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_AAC_HE_V2 -> AacUtil.AAC_HE_V2_MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_AAC_XHE -> AacUtil.AAC_XHE_MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_AAC_ELD -> AacUtil.AAC_ELD_MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_AC3 -> Ac3Util.AC3_MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_E_AC3, C.ENCODING_E_AC3_JOC -> Ac3Util.E_AC3_MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_AC4 -> Ac4Util.MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_DTS -> DtsUtil.DTS_MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_DTS_HD -> DtsUtil.DTS_HD_MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_DOLBY_TRUEHD -> Ac3Util.TRUEHD_MAX_RATE_BYTES_PER_SECOND
        C.ENCODING_PCM_16BIT,
        C.ENCODING_PCM_16BIT_BIG_ENDIAN,
        C.ENCODING_PCM_24BIT,
        C.ENCODING_PCM_32BIT,
        C.ENCODING_PCM_8BIT,
        C.ENCODING_PCM_FLOAT,
        C.ENCODING_AAC_ER_BSAC,
        C.ENCODING_INVALID,
        Format.NO_VALUE -> throw IllegalArgumentException()
        else -> throw IllegalArgumentException()
      }
    }
  }
}
