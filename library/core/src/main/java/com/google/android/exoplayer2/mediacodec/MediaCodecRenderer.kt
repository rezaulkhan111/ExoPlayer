/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodec.CodecException
import android.media.MediaCodec.CryptoException
import android.media.MediaCrypto
import android.media.MediaCryptoException
import android.media.MediaFormat
import android.media.metrics.LogSessionId
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.CallSuper
import androidx.annotation.CheckResult
import androidx.annotation.DoNotInline
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.BaseRenderer
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.FormatHolder
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Renderer.Companion.STATE_DISABLED
import com.google.android.exoplayer2.Renderer.Companion.STATE_STARTED
import com.google.android.exoplayer2.RendererCapabilities.Companion.ADAPTIVE_NOT_SEAMLESS
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.decoder.DecoderInputBuffer
import com.google.android.exoplayer2.decoder.DecoderInputBuffer.InsufficientCapacityException
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_DRM_SESSION_CHANGED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_OPERATING_RATE_CHANGED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_REUSE_NOT_IMPLEMENTED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_WORKAROUND
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.REUSE_RESULT_NO
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.REUSE_RESULT_YES_WITH_FLUSH
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.REUSE_RESULT_YES_WITH_RECONFIGURATION
import com.google.android.exoplayer2.drm.DrmSession
import com.google.android.exoplayer2.drm.FrameworkCryptoConfig
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException
import com.google.android.exoplayer2.source.SampleStream.Companion.FLAG_OMIT_SAMPLE_DATA
import com.google.android.exoplayer2.source.SampleStream.Companion.FLAG_PEEK
import com.google.android.exoplayer2.source.SampleStream.Companion.FLAG_REQUIRE_FORMAT
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.NalUnitUtil
import com.google.android.exoplayer2.util.TimedValueQueue
import com.google.android.exoplayer2.util.TraceUtil
import com.google.android.exoplayer2.util.Util
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.ArrayList
import kotlin.annotation.MustBeDocumented
import kotlin.math.max

/** An abstract renderer that uses [MediaCodec] to decode samples for rendering. */
abstract class MediaCodecRenderer(
  @C.TrackType trackType: Int,
  private val codecAdapterFactory: MediaCodecAdapter.Factory,
  private val mediaCodecSelector: MediaCodecSelector,
  private val enableDecoderFallback: Boolean,
  private val assumedMinimumCodecOperatingRate: Float,
) : BaseRenderer(trackType) {

  /** Thrown when a failure occurs instantiating a decoder. */
  class DecoderInitializationException @SuppressLint("NewApi") private constructor(
    message: String?,
    cause: Throwable?,
    /** The mime type for which a decoder was being initialized. */
    @JvmField val mimeType: String?,
    /** Whether it was required that the decoder support a secure output path. */
    @JvmField val secureDecoderRequired: Boolean,
    /**
     * The [MediaCodecInfo] of the decoder that failed to initialize. Null if no suitable
     * decoder was found.
     */
    @JvmField val codecInfo: MediaCodecInfo?,
    /** An optional developer-readable diagnostic information string. May be null. */
    @JvmField val diagnosticInfo: String?,
    /**
     * If the decoder failed to initialize and another decoder being used as a fallback also failed
     * to initialize, the [DecoderInitializationException] for the fallback decoder. Null if
     * there was no fallback decoder or no suitable decoders were found.
     */
    @JvmField val fallbackDecoderInitializationException: DecoderInitializationException?,
  ) : Exception(message, cause) {

    constructor(
      format: Format,
      cause: Throwable?,
      secureDecoderRequired: Boolean,
      errorCode: Int,
    ) : this(
      "Decoder init failed: [$errorCode], $format",
      cause,
      format.sampleMimeType,
      secureDecoderRequired,
      /* mediaCodecInfo= */ null,
      buildCustomDiagnosticInfo(errorCode),
      /* fallbackDecoderInitializationException= */ null,
    )

    @SuppressLint("NewApi")
    constructor(
      format: Format,
      cause: Throwable?,
      secureDecoderRequired: Boolean,
      mediaCodecInfo: MediaCodecInfo,
    ) : this(
      "Decoder init failed: ${mediaCodecInfo.name}, $format",
      cause,
      format.sampleMimeType,
      secureDecoderRequired,
      mediaCodecInfo,
      if (Util.SDK_INT >= 21) Api21.getDiagnosticInfoV21(cause) else null,
      /* fallbackDecoderInitializationException= */ null,
    )

    @CheckResult
    internal fun copyWithFallbackException(
      fallbackException: DecoderInitializationException,
    ): DecoderInitializationException {
      return DecoderInitializationException(
        message,
        cause,
        mimeType,
        secureDecoderRequired,
        codecInfo,
        diagnosticInfo,
        fallbackException,
      )
    }

    companion object {
      private const val CUSTOM_ERROR_CODE_BASE = -50000
      const val NO_SUITABLE_DECODER_ERROR = CUSTOM_ERROR_CODE_BASE + 1
      const val DECODER_QUERY_ERROR = CUSTOM_ERROR_CODE_BASE + 2

      private fun buildCustomDiagnosticInfo(errorCode: Int): String {
        val sign = if (errorCode < 0) "neg_" else ""
        return "com.google.android.exoplayer2.mediacodec.MediaCodecRenderer_$sign${Math.abs(errorCode)}"
      }
    }

    @RequiresApi(21)
    private object Api21 {
      @DoNotInline
      fun getDiagnosticInfoV21(cause: Throwable?): String? {
        if (cause is CodecException) {
          return cause.diagnosticInfo
        }
        return null
      }
    }
  }

  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
  @IntDef(
    RECONFIGURATION_STATE_NONE,
    RECONFIGURATION_STATE_WRITE_PENDING,
    RECONFIGURATION_STATE_QUEUE_PENDING,
  )
  private annotation class ReconfigurationState

  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
  @IntDef(DRAIN_STATE_NONE, DRAIN_STATE_SIGNAL_END_OF_STREAM, DRAIN_STATE_WAIT_END_OF_STREAM)
  private annotation class DrainState

  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
  @IntDef(
    DRAIN_ACTION_NONE,
    DRAIN_ACTION_FLUSH,
    DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION,
    DRAIN_ACTION_REINITIALIZE,
  )
  private annotation class DrainAction

  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
  @IntDef(
    ADAPTATION_WORKAROUND_MODE_NEVER,
    ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION,
    ADAPTATION_WORKAROUND_MODE_ALWAYS,
  )
  private annotation class AdaptationWorkaroundMode

  private val noDataBuffer: DecoderInputBuffer = DecoderInputBuffer.newNoDataInstance()
  private val buffer: DecoderInputBuffer =
    DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED)
  private val bypassSampleBuffer: DecoderInputBuffer =
    DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)
  private val bypassBatchBuffer: BatchBuffer = BatchBuffer()
  private val formatQueue: TimedValueQueue<Format> = TimedValueQueue()
  private val decodeOnlyPresentationTimestamps: ArrayList<Long> = ArrayList()
  private val outputBufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
  private val pendingOutputStreamStartPositionsUs: LongArray =
    LongArray(MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT)
  private val pendingOutputStreamOffsetsUs: LongArray =
    LongArray(MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT)
  private val pendingOutputStreamSwitchTimesUs: LongArray =
    LongArray(MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT)

  private var inputFormat: Format? = null
  private var outputFormat: Format? = null
  private var codecDrmSession: DrmSession? = null
  private var sourceDrmSession: DrmSession? = null
  private var mediaCrypto: MediaCrypto? = null
  private var mediaCryptoRequiresSecureDecoder: Boolean = false
  private var renderTimeLimitMs: Long = C.TIME_UNSET
  private var currentPlaybackSpeed: Float = 1f
  private var targetPlaybackSpeed: Float = 1f
  private var codec: MediaCodecAdapter? = null
  private var codecInputFormat: Format? = null
  private var codecOutputMediaFormat: MediaFormat? = null
  private var codecOutputMediaFormatChanged: Boolean = false
  private var codecOperatingRate: Float = CODEC_OPERATING_RATE_UNSET
  private var availableCodecInfos: ArrayDeque<MediaCodecInfo>? = null
  private var preferredDecoderInitializationException: DecoderInitializationException? = null
  private var codecInfo: MediaCodecInfo? = null
  @AdaptationWorkaroundMode
  private var codecAdaptationWorkaroundMode: Int = ADAPTATION_WORKAROUND_MODE_NEVER
  private var codecNeedsDiscardToSpsWorkaround: Boolean = false
  private var codecNeedsFlushWorkaround: Boolean = false
  private var codecNeedsSosFlushWorkaround: Boolean = false
  private var codecNeedsEosFlushWorkaround: Boolean = false
  private var codecNeedsEosOutputExceptionWorkaround: Boolean = false
  private var codecNeedsEosBufferTimestampWorkaround: Boolean = false
  private var codecNeedsMonoChannelCountWorkaround: Boolean = false
  private var codecNeedsAdaptationWorkaroundBuffer: Boolean = false
  private var shouldSkipAdaptationWorkaroundOutputBuffer: Boolean = false
  private var codecNeedsEosPropagation: Boolean = false
  private var c2Mp3TimestampTracker: C2Mp3TimestampTracker? = null
  private var codecHotswapDeadlineMs: Long = C.TIME_UNSET
  private var inputIndex: Int = C.INDEX_UNSET
  private var outputIndex: Int = C.INDEX_UNSET
  private var outputBuffer: ByteBuffer? = null
  private var isDecodeOnlyOutputBuffer: Boolean = false
  private var isLastOutputBuffer: Boolean = false
  private var bypassEnabled: Boolean = false
  private var bypassSampleBufferPending: Boolean = false
  private var bypassDrainAndReinitialize: Boolean = false
  private var codecReconfigured: Boolean = false
  @ReconfigurationState
  private var codecReconfigurationState: Int = RECONFIGURATION_STATE_NONE
  @DrainState private var codecDrainState: Int = DRAIN_STATE_NONE
  @DrainAction private var codecDrainAction: Int = DRAIN_ACTION_NONE
  private var codecReceivedBuffers: Boolean = false
  private var codecReceivedEos: Boolean = false
  private var codecHasOutputMediaFormat: Boolean = false
  private var largestQueuedPresentationTimeUs: Long = C.TIME_UNSET
  private var lastBufferInStreamPresentationTimeUs: Long = C.TIME_UNSET
  private var inputStreamEnded: Boolean = false
  private var outputStreamEnded: Boolean = false
  private var waitingForFirstSampleInFormat: Boolean = false
  private var pendingOutputEndOfStream: Boolean = false
  private var pendingPlaybackException: ExoPlaybackException? = null

  @JvmField protected var decoderCounters: DecoderCounters = DecoderCounters()

  private var outputStreamStartPositionUs: Long = C.TIME_UNSET
  private var outputStreamOffsetUs: Long = C.TIME_UNSET
  private var pendingOutputStreamOffsetCount: Int = 0

  init {
    Assertions.checkNotNull(mediaCodecSelector)
    outputStreamStartPositionUs = C.TIME_UNSET
    setOutputStreamOffsetUs(C.TIME_UNSET)
    bypassBatchBuffer.ensureSpaceForWrite(/* length= */ 0)
    bypassBatchBuffer.data!!.order(ByteOrder.nativeOrder())
  }

  /**
   * Sets a limit on the time a single [render] call can spend draining and filling the decoder.
   *
   * <p>This method should be called right after creating an instance of this class.
   *
   * @param renderTimeLimitMs The render time limit in milliseconds, or [C.TIME_UNSET] for no
   *     limit.
   */
  open fun setRenderTimeLimitMs(renderTimeLimitMs: Long) {
    this.renderTimeLimitMs = renderTimeLimitMs
  }

  override fun supportsMixedMimeTypeAdaptation(): Int {
    return ADAPTIVE_NOT_SEAMLESS
  }

  @Throws(ExoPlaybackException::class)
  override fun supportsFormat(format: Format?): Int {
    if (format == null) {
      return C.FORMAT_HANDLED
    }
    return try {
      supportsFormat(mediaCodecSelector, format)
    } catch (e: DecoderQueryException) {
      throw createRendererException(e, format, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED)
    }
  }

  /**
   * Returns the [com.google.android.exoplayer2.RendererCapabilities.Capabilities] for the given [Format].
   *
   * @param mediaCodecSelector The decoder selector.
   * @param format The [Format].
   * @return The [com.google.android.exoplayer2.RendererCapabilities.Capabilities] for this [Format].
   * @throws DecoderQueryException If there was an error querying decoders.
   */
  @Throws(DecoderQueryException::class)
  protected abstract fun supportsFormat(
    mediaCodecSelector: MediaCodecSelector,
    format: Format,
  ): Int

  /**
   * Returns a list of decoders that can decode media in the specified format, in priority order.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param format The [Format] for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @return A list of [MediaCodecInfo]s corresponding to decoders. May be empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  @Throws(DecoderQueryException::class)
  protected abstract fun getDecoderInfos(
    mediaCodecSelector: MediaCodecSelector,
    format: Format,
    requiresSecureDecoder: Boolean,
  ): List<MediaCodecInfo>

  /**
   * Returns the [MediaCodecAdapter.Configuration] that will be used to create and configure a
   * [MediaCodec] to decode the given [Format] for a playback.
   *
   * @param codecInfo Information about the [MediaCodec] being configured.
   * @param format The [Format] for which the codec is being configured.
   * @param crypto For drm protected playbacks, a [MediaCrypto] to use for decryption.
   * @param codecOperatingRate The codec operating rate, or [CODEC_OPERATING_RATE_UNSET] if
   *     no codec operating rate should be set.
   * @return The parameters needed to call [MediaCodec.configure].
   */
  protected abstract fun getMediaCodecConfiguration(
    codecInfo: MediaCodecInfo,
    format: Format,
    crypto: MediaCrypto?,
    codecOperatingRate: Float,
  ): MediaCodecAdapter.Configuration

  @Throws(ExoPlaybackException::class)
  @SuppressLint("NewApi")
  protected fun maybeInitCodecOrBypass() {
    if (codec != null || bypassEnabled || inputFormat == null) {
      return
    }

    val currentInputFormat = inputFormat!!
    if (sourceDrmSession == null && shouldUseBypass(currentInputFormat)) {
      initBypass(currentInputFormat)
      return
    }

    setCodecDrmSession(sourceDrmSession)

    val mimeType = currentInputFormat.sampleMimeType
    if (codecDrmSession != null) {
      if (mediaCrypto == null) {
        val sessionCryptoConfig = getFrameworkCryptoConfig(codecDrmSession!!)
        if (sessionCryptoConfig == null) {
          val drmError = codecDrmSession!!.getError()
          if (drmError != null) {
            // Continue for now.
          } else {
            return
          }
        } else {
          try {
            mediaCrypto = MediaCrypto(sessionCryptoConfig.uuid, sessionCryptoConfig.sessionId)
          } catch (e: MediaCryptoException) {
            throw createRendererException(
              e, currentInputFormat, PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR
            )
          }
          mediaCryptoRequiresSecureDecoder =
            !sessionCryptoConfig.forceAllowInsecureDecoderComponents &&
              mediaCrypto!!.requiresSecureDecoderComponent(mimeType!!)
        }
      }
      if (FrameworkCryptoConfig.WORKAROUND_DEVICE_NEEDS_KEYS_TO_CONFIGURE_CODEC) {
        val drmSessionState = codecDrmSession!!.getState()
        if (drmSessionState == DrmSession.STATE_ERROR) {
          val drmSessionException = Assertions.checkNotNull(codecDrmSession!!.getError())!!
          throw createRendererException(
            drmSessionException, currentInputFormat, drmSessionException.errorCode
          )
        } else if (drmSessionState != DrmSession.STATE_OPENED_WITH_KEYS) {
          return
        }
      }
    }

    try {
      maybeInitCodecWithFallback(mediaCrypto, mediaCryptoRequiresSecureDecoder)
    } catch (e: DecoderInitializationException) {
      throw createRendererException(
        e, currentInputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
      )
    }
  }

  /**
   * Returns whether buffers in the input format can be processed without a codec.
   *
   * <p>This method is only called if the content is not DRM protected, because if the content is
   * DRM protected use of bypass is never possible.
   *
   * @param format The input [Format].
   * @return Whether playback bypassing [MediaCodec] is supported.
   */
  protected open fun shouldUseBypass(format: Format): Boolean {
    return false
  }

  protected open fun shouldInitCodec(codecInfo: MediaCodecInfo): Boolean {
    return true
  }

  /**
   * Returns whether the renderer needs to re-initialize the codec, possibly as a result of a change
   * in device capabilities.
   */
  protected open fun shouldReinitCodec(): Boolean {
    return false
  }

  /**
   * Returns whether the codec needs the renderer to propagate the end-of-stream signal directly,
   * rather than by using an end-of-stream buffer queued to the codec.
   */
  protected open fun getCodecNeedsEosPropagation(): Boolean {
    return false
  }

  /**
   * Sets an exception to be re-thrown by render.
   *
   * @param exception The exception.
   */
  protected fun setPendingPlaybackException(exception: ExoPlaybackException) {
    pendingPlaybackException = exception
  }

  /**
   * Updates the output formats for the specified output buffer timestamp, calling [ ] if a change has occurred.
   *
   * <p>Subclasses should only call this method if operating in a mode where buffers are not
   * dequeued from the decoder, for example when using video tunneling).
   *
   * @throws ExoPlaybackException Thrown if an error occurs as a result of the output format change.
   */
  @Throws(ExoPlaybackException::class)
  protected fun updateOutputFormatForTime(presentationTimeUs: Long) {
    var outputFormatChanged = false
    var format = formatQueue.pollFloor(presentationTimeUs)
    if (format == null && codecOutputMediaFormatChanged) {
      format = formatQueue.pollFirst()
    }
    if (format != null) {
      outputFormat = format
      outputFormatChanged = true
    }
    if (outputFormatChanged || (codecOutputMediaFormatChanged && outputFormat != null)) {
      onOutputFormatChanged(outputFormat!!, codecOutputMediaFormat)
      codecOutputMediaFormatChanged = false
    }
  }

  protected fun getCodec(): MediaCodecAdapter? {
    return codec
  }

  protected fun getCodecOutputMediaFormat(): MediaFormat? {
    return codecOutputMediaFormat
  }

  protected fun getCodecInfo(): MediaCodecInfo? {
    return codecInfo
  }

  @Throws(ExoPlaybackException::class)
  override fun onEnabled(joining: Boolean, mayRenderStartOfStream: Boolean) {
    decoderCounters = DecoderCounters()
  }

  @Throws(ExoPlaybackException::class)
  override fun onStreamChanged(formats: Array<out Format>, startPositionUs: Long, offsetUs: Long) {
    if (this.outputStreamOffsetUs == C.TIME_UNSET) {
      Assertions.checkState(this.outputStreamStartPositionUs == C.TIME_UNSET)
      this.outputStreamStartPositionUs = startPositionUs
      setOutputStreamOffsetUs(offsetUs)
    } else {
      if (pendingOutputStreamOffsetCount == pendingOutputStreamOffsetsUs.size) {
        Log.w(
          TAG,
          "Too many stream changes, so dropping offset: " +
            pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1],
        )
      } else {
        pendingOutputStreamOffsetCount++
      }
      pendingOutputStreamStartPositionsUs[pendingOutputStreamOffsetCount - 1] = startPositionUs
      pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1] = offsetUs
      pendingOutputStreamSwitchTimesUs[pendingOutputStreamOffsetCount - 1] =
        largestQueuedPresentationTimeUs
    }
  }

  @Throws(ExoPlaybackException::class)
  override fun onPositionReset(positionUs: Long, joining: Boolean) {
    inputStreamEnded = false
    outputStreamEnded = false
    pendingOutputEndOfStream = false
    if (bypassEnabled) {
      bypassBatchBuffer.clear()
      bypassSampleBuffer.clear()
      bypassSampleBufferPending = false
    } else {
      flushOrReinitializeCodec()
    }
    if (formatQueue.size() > 0) {
      waitingForFirstSampleInFormat = true
    }
    formatQueue.clear()
    if (pendingOutputStreamOffsetCount != 0) {
      setOutputStreamOffsetUs(pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1])
      outputStreamStartPositionUs =
        pendingOutputStreamStartPositionsUs[pendingOutputStreamOffsetCount - 1]
      pendingOutputStreamOffsetCount = 0
    }
  }

  @Throws(ExoPlaybackException::class)
  override fun setPlaybackSpeed(currentPlaybackSpeed: Float, targetPlaybackSpeed: Float) {
    this.currentPlaybackSpeed = currentPlaybackSpeed
    this.targetPlaybackSpeed = targetPlaybackSpeed
    updateCodecOperatingRate(codecInputFormat)
  }

  override fun onDisabled() {
    inputFormat = null
    outputStreamStartPositionUs = C.TIME_UNSET
    setOutputStreamOffsetUs(C.TIME_UNSET)
    pendingOutputStreamOffsetCount = 0
    flushOrReleaseCodec()
  }

  override fun onReset() {
    try {
      disableBypass()
      releaseCodec()
    } finally {
      setSourceDrmSession(null)
    }
  }

  private fun disableBypass() {
    bypassDrainAndReinitialize = false
    bypassBatchBuffer.clear()
    bypassSampleBuffer.clear()
    bypassSampleBufferPending = false
    bypassEnabled = false
  }

  protected open fun releaseCodec() {
    try {
      if (codec != null) {
        codec!!.release()
        decoderCounters.decoderReleaseCount++
        onCodecReleased(codecInfo!!.name)
      }
    } finally {
      codec = null
      try {
        if (mediaCrypto != null) {
          mediaCrypto!!.release()
        }
      } finally {
        mediaCrypto = null
        setCodecDrmSession(null)
        resetCodecStateForRelease()
      }
    }
  }

  override fun onStarted() {
    // Do nothing. Overridden to remove throws clause.
  }

  override fun onStopped() {
    // Do nothing. Overridden to remove throws clause.
  }

  @Throws(ExoPlaybackException::class)
  @SuppressLint("NewApi")
  override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
    if (pendingOutputEndOfStream) {
      pendingOutputEndOfStream = false
      processEndOfStream()
    }
    if (pendingPlaybackException != null) {
      val playbackException = pendingPlaybackException
      pendingPlaybackException = null
      throw playbackException!!
    }

    try {
      if (outputStreamEnded) {
        renderToEndOfStream()
        return
      }
      if (inputFormat == null && !readSourceOmittingSampleData(FLAG_REQUIRE_FORMAT)) {
        return
      }
      maybeInitCodecOrBypass()
      if (bypassEnabled) {
        TraceUtil.beginSection("bypassRender")
        while (bypassRender(positionUs, elapsedRealtimeUs)) {}
        TraceUtil.endSection()
      } else if (codec != null) {
        val renderStartTimeMs = SystemClock.elapsedRealtime()
        TraceUtil.beginSection("drainAndFeed")
        while (drainOutputBuffer(positionUs, elapsedRealtimeUs) &&
          shouldContinueRendering(renderStartTimeMs)
        ) {}
        while (feedInputBuffer() && shouldContinueRendering(renderStartTimeMs)) {}
        TraceUtil.endSection()
      } else {
        decoderCounters.skippedInputBufferCount += skipSource(positionUs)
        readSourceOmittingSampleData(FLAG_PEEK)
      }
      decoderCounters.ensureUpdated()
    } catch (e: IllegalStateException) {
      if (isMediaCodecException(e)) {
        onCodecError(e)
        val isRecoverable = Util.SDK_INT >= 21 && isRecoverableMediaCodecExceptionV21(e)
        if (isRecoverable) {
          releaseCodec()
        }
        throw createRendererException(
          createDecoderException(e, getCodecInfo()),
          inputFormat,
          isRecoverable,
          PlaybackException.ERROR_CODE_DECODING_FAILED,
        )
      }
      throw e
    }
  }

  /**
   * Flushes the codec. If flushing is not possible, the codec will be released and re-instantiated.
   * This method is a no-op if the codec is `null`.
   *
   * <p>The implementation of this method calls [flushOrReleaseCodec], and [ ] if the codec needs to be re-instantiated.
   *
   * @return Whether the codec was released and reinitialized, rather than being flushed.
   * @throws ExoPlaybackException If an error occurs re-instantiating the codec.
   */
  @Throws(ExoPlaybackException::class)
  protected fun flushOrReinitializeCodec(): Boolean {
    val released = flushOrReleaseCodec()
    if (released) {
      maybeInitCodecOrBypass()
    }
    return released
  }

  /**
   * Flushes the codec. If flushing is not possible, the codec will be released. This method is a
   * no-op if the codec is `null`.
   *
   * @return Whether the codec was released.
   */
  @SuppressLint("NewApi")
  protected open fun flushOrReleaseCodec(): Boolean {
    if (codec == null) {
      return false
    }
    if (codecDrainAction == DRAIN_ACTION_REINITIALIZE ||
      codecNeedsFlushWorkaround ||
      (codecNeedsSosFlushWorkaround && !codecHasOutputMediaFormat) ||
      (codecNeedsEosFlushWorkaround && codecReceivedEos)
    ) {
      releaseCodec()
      return true
    }
    if (codecDrainAction == DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION) {
      Assertions.checkState(Util.SDK_INT >= 23)
      if (Util.SDK_INT >= 23) {
        try {
          updateDrmSessionV23()
        } catch (e: ExoPlaybackException) {
          Log.w(TAG, "Failed to update the DRM session, releasing the codec instead.", e)
          releaseCodec()
          return true
        }
      }
    }
    flushCodec()
    return false
  }

  private fun flushCodec() {
    try {
      codec!!.flush()
    } finally {
      resetCodecStateForFlush()
    }
  }

  /** Resets the renderer internal state after a codec flush. */
  @CallSuper
  protected open fun resetCodecStateForFlush() {
    resetInputBuffer()
    resetOutputBuffer()
    codecHotswapDeadlineMs = C.TIME_UNSET
    codecReceivedEos = false
    codecReceivedBuffers = false
    codecNeedsAdaptationWorkaroundBuffer = false
    shouldSkipAdaptationWorkaroundOutputBuffer = false
    isDecodeOnlyOutputBuffer = false
    isLastOutputBuffer = false
    decodeOnlyPresentationTimestamps.clear()
    largestQueuedPresentationTimeUs = C.TIME_UNSET
    lastBufferInStreamPresentationTimeUs = C.TIME_UNSET
    if (c2Mp3TimestampTracker != null) {
      c2Mp3TimestampTracker!!.reset()
    }
    codecDrainState = DRAIN_STATE_NONE
    codecDrainAction = DRAIN_ACTION_NONE
    codecReconfigurationState =
      if (codecReconfigured) RECONFIGURATION_STATE_WRITE_PENDING else RECONFIGURATION_STATE_NONE
  }

  /**
   * Resets the renderer internal state after a codec release.
   *
   * <p>Note that this only needs to reset state variables that are changed in addition to those
   * already changed in [resetCodecStateForFlush].
   */
  @CallSuper
  protected open fun resetCodecStateForRelease() {
    resetCodecStateForFlush()

    pendingPlaybackException = null
    c2Mp3TimestampTracker = null
    availableCodecInfos = null
    codecInfo = null
    codecInputFormat = null
    codecOutputMediaFormat = null
    codecOutputMediaFormatChanged = false
    codecHasOutputMediaFormat = false
    codecOperatingRate = CODEC_OPERATING_RATE_UNSET
    codecAdaptationWorkaroundMode = ADAPTATION_WORKAROUND_MODE_NEVER
    codecNeedsDiscardToSpsWorkaround = false
    codecNeedsFlushWorkaround = false
    codecNeedsSosFlushWorkaround = false
    codecNeedsEosFlushWorkaround = false
    codecNeedsEosOutputExceptionWorkaround = false
    codecNeedsEosBufferTimestampWorkaround = false
    codecNeedsMonoChannelCountWorkaround = false
    codecNeedsEosPropagation = false
    codecReconfigured = false
    codecReconfigurationState = RECONFIGURATION_STATE_NONE
    mediaCryptoRequiresSecureDecoder = false
  }

  protected open fun createDecoderException(
    cause: Throwable,
    codecInfo: MediaCodecInfo?,
  ): MediaCodecDecoderException {
    return MediaCodecDecoderException(cause, codecInfo)
  }

  @Throws(ExoPlaybackException::class)
  @SuppressLint("NewApi")
  private fun readSourceOmittingSampleData(readFlags: Int): Boolean {
    val formatHolder = formatHolder
    noDataBuffer.clear()
    val result = readSource(formatHolder, noDataBuffer, readFlags or FLAG_OMIT_SAMPLE_DATA)
    if (result == C.RESULT_FORMAT_READ) {
      onInputFormatChanged(formatHolder)
      return true
    } else if (result == C.RESULT_BUFFER_READ && noDataBuffer.isEndOfStream) {
      inputStreamEnded = true
      processEndOfStream()
    }
    return false
  }

  @Throws(DecoderInitializationException::class)
  private fun maybeInitCodecWithFallback(
    crypto: MediaCrypto?,
    mediaCryptoRequiresSecureDecoder: Boolean,
  ) {
    if (availableCodecInfos == null) {
      try {
        val allAvailableCodecInfos =
          getAvailableCodecInfos(mediaCryptoRequiresSecureDecoder)
        availableCodecInfos = ArrayDeque()
        if (enableDecoderFallback) {
          availableCodecInfos!!.addAll(allAvailableCodecInfos)
        } else if (allAvailableCodecInfos.isNotEmpty()) {
          availableCodecInfos!!.add(allAvailableCodecInfos[0])
        }
        preferredDecoderInitializationException = null
      } catch (e: DecoderQueryException) {
        throw DecoderInitializationException(
          inputFormat!!,
          e,
          mediaCryptoRequiresSecureDecoder,
          DecoderInitializationException.DECODER_QUERY_ERROR,
        )
      }
    }

    if (availableCodecInfos!!.isEmpty()) {
      throw DecoderInitializationException(
        inputFormat!!,
        /* cause= */ null,
        mediaCryptoRequiresSecureDecoder,
        DecoderInitializationException.NO_SUITABLE_DECODER_ERROR,
      )
    }

    val preferredCodecInfo = availableCodecInfos!!.peekFirst()
    while (codec == null) {
      val codecInfo = availableCodecInfos!!.peekFirst() ?: break
      if (!shouldInitCodec(codecInfo)) {
        return
      }
      try {
        try {
          initCodec(codecInfo, crypto)
        } catch (e: Exception) {
          if (codecInfo == preferredCodecInfo) {
            Log.w(TAG, "Preferred decoder instantiation failed. Sleeping for 50ms then retrying.")
            Thread.sleep(/* millis= */ 50)
            initCodec(codecInfo, crypto)
          } else {
            throw e
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to initialize decoder: $codecInfo", e)
        availableCodecInfos!!.removeFirst()
        val exception =
          DecoderInitializationException(
            inputFormat!!, e, mediaCryptoRequiresSecureDecoder, codecInfo
          )
        onCodecError(exception)
        if (preferredDecoderInitializationException == null) {
          preferredDecoderInitializationException = exception
        } else {
          preferredDecoderInitializationException =
            preferredDecoderInitializationException!!.copyWithFallbackException(exception)
        }
        if (availableCodecInfos!!.isEmpty()) {
          throw preferredDecoderInitializationException!!
        }
      }
    }

    availableCodecInfos = null
  }

  @Throws(DecoderQueryException::class)
  private fun getAvailableCodecInfos(mediaCryptoRequiresSecureDecoder: Boolean): List<MediaCodecInfo> {
    var codecInfos =
      getDecoderInfos(mediaCodecSelector, inputFormat!!, mediaCryptoRequiresSecureDecoder)
    if (codecInfos.isEmpty() && mediaCryptoRequiresSecureDecoder) {
      codecInfos =
        getDecoderInfos(mediaCodecSelector, inputFormat!!, /* requiresSecureDecoder= */ false)
      if (codecInfos.isNotEmpty()) {
        Log.w(
          TAG,
          "Drm session requires secure decoder for " +
            inputFormat!!.sampleMimeType +
            ", but no secure decoder available. Trying to proceed with " +
            codecInfos +
            ".",
        )
      }
    }
    return codecInfos
  }

  private fun initBypass(format: Format) {
    disableBypass()

    val mimeType = format.sampleMimeType
    if (MimeTypes.AUDIO_AAC != mimeType &&
      MimeTypes.AUDIO_MPEG != mimeType &&
      MimeTypes.AUDIO_OPUS != mimeType
    ) {
      bypassBatchBuffer.setMaxSampleCount(1)
    } else {
      bypassBatchBuffer.setMaxSampleCount(BatchBuffer.DEFAULT_MAX_SAMPLE_COUNT)
    }
    bypassEnabled = true
  }

  @Throws(Exception::class)
  @SuppressLint("NewApi")
  private fun initCodec(codecInfo: MediaCodecInfo, crypto: MediaCrypto?) {
    val codecInitializingTimestamp: Long
    val codecInitializedTimestamp: Long
    val codecName = codecInfo.name
    var codecOperatingRate =
      if (Util.SDK_INT < 23) {
        CODEC_OPERATING_RATE_UNSET
      } else {
        getCodecOperatingRateV23(targetPlaybackSpeed, inputFormat!!, streamFormats!!)
      }
    if (codecOperatingRate <= assumedMinimumCodecOperatingRate) {
      codecOperatingRate = CODEC_OPERATING_RATE_UNSET
    }
    codecInitializingTimestamp = SystemClock.elapsedRealtime()
    val configuration =
      getMediaCodecConfiguration(codecInfo, inputFormat!!, crypto, codecOperatingRate)
    if (Util.SDK_INT >= 31) {
      Api31.setLogSessionIdToMediaCodecFormat(configuration, playerId)
    }
    try {
      TraceUtil.beginSection("createCodec:$codecName")
      codec = codecAdapterFactory.createAdapter(configuration)
    } finally {
      TraceUtil.endSection()
    }
    codecInitializedTimestamp = SystemClock.elapsedRealtime()

    this.codecInfo = codecInfo
    this.codecOperatingRate = codecOperatingRate
    codecInputFormat = inputFormat
    codecAdaptationWorkaroundMode = getCodecAdaptationWorkaroundMode(codecName)
    codecNeedsDiscardToSpsWorkaround =
      codecNeedsDiscardToSpsWorkaround(codecName, codecInputFormat!!)
    codecNeedsFlushWorkaround = codecNeedsFlushWorkaround(codecName)
    codecNeedsSosFlushWorkaround = codecNeedsSosFlushWorkaround(codecName)
    codecNeedsEosFlushWorkaround = codecNeedsEosFlushWorkaround(codecName)
    codecNeedsEosOutputExceptionWorkaround = codecNeedsEosOutputExceptionWorkaround(codecName)
    codecNeedsEosBufferTimestampWorkaround = codecNeedsEosBufferTimestampWorkaround(codecName)
    codecNeedsMonoChannelCountWorkaround =
      codecNeedsMonoChannelCountWorkaround(codecName, codecInputFormat!!)
    codecNeedsEosPropagation =
      codecNeedsEosPropagationWorkaround(codecInfo) || getCodecNeedsEosPropagation()
    if (codec!!.needsReconfiguration()) {
      this.codecReconfigured = true
      this.codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING
      this.codecNeedsAdaptationWorkaroundBuffer =
        codecAdaptationWorkaroundMode != ADAPTATION_WORKAROUND_MODE_NEVER
    }
    if ("c2.android.mp3.decoder" == codecInfo.name) {
      c2Mp3TimestampTracker = C2Mp3TimestampTracker()
    }

    if (getState() == STATE_STARTED) {
      codecHotswapDeadlineMs = SystemClock.elapsedRealtime() + MAX_CODEC_HOTSWAP_TIME_MS
    }

    decoderCounters.decoderInitCount++
    val elapsed = codecInitializedTimestamp - codecInitializingTimestamp
    onCodecInitialized(codecName, configuration, codecInitializedTimestamp, elapsed)
  }

  private fun shouldContinueRendering(renderStartTimeMs: Long): Boolean {
    return renderTimeLimitMs == C.TIME_UNSET ||
      SystemClock.elapsedRealtime() - renderStartTimeMs < renderTimeLimitMs
  }

  private fun hasOutputBuffer(): Boolean {
    return outputIndex >= 0
  }

  private fun resetInputBuffer() {
    inputIndex = C.INDEX_UNSET
    buffer.data = null
  }

  private fun resetOutputBuffer() {
    outputIndex = C.INDEX_UNSET
    outputBuffer = null
  }

  private fun setSourceDrmSession(session: DrmSession?) {
    DrmSession.replaceSession(sourceDrmSession, session)
    sourceDrmSession = session
  }

  private fun setCodecDrmSession(session: DrmSession?) {
    DrmSession.replaceSession(codecDrmSession, session)
    codecDrmSession = session
  }

  @Throws(ExoPlaybackException::class)
  @SuppressLint("NewApi")
  private fun feedInputBuffer(): Boolean {
    val codec = codec ?: return false
    if (codecDrainState == DRAIN_STATE_WAIT_END_OF_STREAM || inputStreamEnded) {
      return false
    }
    if (codecDrainState == DRAIN_STATE_NONE && shouldReinitCodec()) {
      drainAndReinitializeCodec()
    }

    if (inputIndex < 0) {
      inputIndex = codec.dequeueInputBufferIndex()
      if (inputIndex < 0) {
        return false
      }
      buffer.data = codec.getInputBuffer(inputIndex)
      buffer.clear()
    }

    if (codecDrainState == DRAIN_STATE_SIGNAL_END_OF_STREAM) {
      if (!codecNeedsEosPropagation) {
        codecReceivedEos = true
        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        resetInputBuffer()
      }
      codecDrainState = DRAIN_STATE_WAIT_END_OF_STREAM
      return false
    }

    if (codecNeedsAdaptationWorkaroundBuffer) {
      codecNeedsAdaptationWorkaroundBuffer = false
      buffer.data!!.put(ADAPTATION_WORKAROUND_BUFFER)
      codec.queueInputBuffer(inputIndex, 0, ADAPTATION_WORKAROUND_BUFFER.size, 0, 0)
      resetInputBuffer()
      codecReceivedBuffers = true
      return true
    }

    if (codecReconfigurationState == RECONFIGURATION_STATE_WRITE_PENDING) {
      for (i in 0 until codecInputFormat!!.initializationData.size) {
        val data = codecInputFormat!!.initializationData[i]
        buffer.data!!.put(data)
      }
      codecReconfigurationState = RECONFIGURATION_STATE_QUEUE_PENDING
    }
    val adaptiveReconfigurationBytes = buffer.data!!.position()

    val formatHolder = formatHolder

    val result: Int
    try {
      result = readSource(formatHolder, buffer, /* readFlags= */ 0)
    } catch (e: InsufficientCapacityException) {
      onCodecError(e)
      readSourceOmittingSampleData(/* readFlags= */ 0)
      flushCodec()
      return true
    }

    if (hasReadStreamToEnd()) {
      lastBufferInStreamPresentationTimeUs = largestQueuedPresentationTimeUs
    }

    if (result == C.RESULT_NOTHING_READ) {
      return false
    }
    if (result == C.RESULT_FORMAT_READ) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        buffer.clear()
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING
      }
      onInputFormatChanged(formatHolder)
      return true
    }

    if (buffer.isEndOfStream) {
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        buffer.clear()
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING
      }
      inputStreamEnded = true
      if (!codecReceivedBuffers) {
        processEndOfStream()
        return false
      }
      try {
        if (!codecNeedsEosPropagation) {
          codecReceivedEos = true
          codec.queueInputBuffer(
            inputIndex,
            /* offset= */ 0,
            /* size= */ 0,
            /* presentationTimeUs= */ 0,
            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
          )
          resetInputBuffer()
        }
      } catch (e: CryptoException) {
        throw createRendererException(
          e, inputFormat, Util.getErrorCodeForMediaDrmErrorCode(e.errorCode)
        )
      }
      return false
    }

    if (!codecReceivedBuffers && !buffer.isKeyFrame) {
      buffer.clear()
      if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
        codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING
      }
      return true
    }

    val bufferEncrypted = buffer.isEncrypted
    if (bufferEncrypted) {
      buffer.cryptoInfo.increaseClearDataFirstSubSampleBy(adaptiveReconfigurationBytes)
    }
    if (codecNeedsDiscardToSpsWorkaround && !bufferEncrypted) {
      NalUnitUtil.discardToSps(buffer.data)
      if (buffer.data!!.position() == 0) {
        return true
      }
      codecNeedsDiscardToSpsWorkaround = false
    }

    var presentationTimeUs = buffer.timeUs

    if (c2Mp3TimestampTracker != null) {
      presentationTimeUs =
        c2Mp3TimestampTracker!!.updateAndGetPresentationTimeUs(inputFormat!!, buffer)
      largestQueuedPresentationTimeUs =
        max(
          largestQueuedPresentationTimeUs,
          c2Mp3TimestampTracker!!.getLastOutputBufferPresentationTimeUs(inputFormat!!),
        )
    }

    if (buffer.isDecodeOnly) {
      decodeOnlyPresentationTimestamps.add(presentationTimeUs)
    }
    if (waitingForFirstSampleInFormat) {
      formatQueue.add(presentationTimeUs, inputFormat!!)
      waitingForFirstSampleInFormat = false
    }
    largestQueuedPresentationTimeUs = max(largestQueuedPresentationTimeUs, presentationTimeUs)
    buffer.flip()
    if (buffer.hasSupplementalData()) {
      handleInputBufferSupplementalData(buffer)
    }

    onQueueInputBuffer(buffer)
    try {
      if (bufferEncrypted) {
        codec.queueSecureInputBuffer(
          inputIndex, /* offset= */ 0, buffer.cryptoInfo, presentationTimeUs, /* flags= */ 0
        )
      } else {
        codec.queueInputBuffer(
          inputIndex, /* offset= */ 0, buffer.data!!.limit(), presentationTimeUs, /* flags= */ 0
        )
      }
    } catch (e: CryptoException) {
      throw createRendererException(
        e, inputFormat, Util.getErrorCodeForMediaDrmErrorCode(e.errorCode)
      )
    }

    resetInputBuffer()
    codecReceivedBuffers = true
    codecReconfigurationState = RECONFIGURATION_STATE_NONE
    decoderCounters.queuedInputBufferCount++
    return true
  }

  protected open fun onCodecInitialized(
    name: String,
    configuration: MediaCodecAdapter.Configuration,
    initializedTimestampMs: Long,
    initializationDurationMs: Long,
  ) {}

  protected open fun onCodecReleased(name: String) {}

  protected open fun onCodecError(codecError: Exception) {}

  @CallSuper
  @Throws(ExoPlaybackException::class)
  @SuppressLint("NewApi")
  protected open fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
    waitingForFirstSampleInFormat = true
    val newFormat = Assertions.checkNotNull(formatHolder.format)!!
    if (newFormat.sampleMimeType == null) {
      throw createRendererException(
        IllegalArgumentException(),
        newFormat,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
      )
    }
    setSourceDrmSession(formatHolder.drmSession)
    inputFormat = newFormat

    if (bypassEnabled) {
      bypassDrainAndReinitialize = true
      return null
    }

    if (codec == null) {
      availableCodecInfos = null
      maybeInitCodecOrBypass()
      return null
    }

    val codec = this.codec!!
    val codecInfo = this.codecInfo!!

    val oldFormat = codecInputFormat!!
    if (drmNeedsCodecReinitialization(codecInfo, newFormat, codecDrmSession, sourceDrmSession)) {
      drainAndReinitializeCodec()
      return DecoderReuseEvaluation(
        codecInfo.name,
        oldFormat,
        newFormat,
        REUSE_RESULT_NO,
        DISCARD_REASON_DRM_SESSION_CHANGED,
      )
    }
    val drainAndUpdateCodecDrmSession = sourceDrmSession != codecDrmSession
    Assertions.checkState(!drainAndUpdateCodecDrmSession || Util.SDK_INT >= 23)

    val evaluation = canReuseCodec(codecInfo, oldFormat, newFormat)
    var overridingDiscardReasons = 0
    when (evaluation.result) {
      REUSE_RESULT_NO -> drainAndReinitializeCodec()
      REUSE_RESULT_YES_WITH_FLUSH -> {
        if (!updateCodecOperatingRate(newFormat)) {
          overridingDiscardReasons = overridingDiscardReasons or DISCARD_REASON_OPERATING_RATE_CHANGED
        } else {
          codecInputFormat = newFormat
          if (drainAndUpdateCodecDrmSession) {
            if (!drainAndUpdateCodecDrmSessionV23()) {
              overridingDiscardReasons = overridingDiscardReasons or DISCARD_REASON_WORKAROUND
            }
          } else if (!drainAndFlushCodec()) {
            overridingDiscardReasons = overridingDiscardReasons or DISCARD_REASON_WORKAROUND
          }
        }
      }
      REUSE_RESULT_YES_WITH_RECONFIGURATION -> {
        if (!updateCodecOperatingRate(newFormat)) {
          overridingDiscardReasons = overridingDiscardReasons or DISCARD_REASON_OPERATING_RATE_CHANGED
        } else {
          codecReconfigured = true
          codecReconfigurationState = RECONFIGURATION_STATE_WRITE_PENDING
          codecNeedsAdaptationWorkaroundBuffer =
            codecAdaptationWorkaroundMode == ADAPTATION_WORKAROUND_MODE_ALWAYS ||
              (codecAdaptationWorkaroundMode == ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION &&
                newFormat.width == oldFormat.width &&
                newFormat.height == oldFormat.height)
          codecInputFormat = newFormat
          if (drainAndUpdateCodecDrmSession && !drainAndUpdateCodecDrmSessionV23()) {
            overridingDiscardReasons = overridingDiscardReasons or DISCARD_REASON_WORKAROUND
          }
        }
      }
      REUSE_RESULT_YES_WITHOUT_RECONFIGURATION -> {
        if (!updateCodecOperatingRate(newFormat)) {
          overridingDiscardReasons = overridingDiscardReasons or DISCARD_REASON_OPERATING_RATE_CHANGED
        } else {
          codecInputFormat = newFormat
          if (drainAndUpdateCodecDrmSession && !drainAndUpdateCodecDrmSessionV23()) {
            overridingDiscardReasons = overridingDiscardReasons or DISCARD_REASON_WORKAROUND
          }
        }
      }
      else -> throw IllegalStateException()
    }

    if (evaluation.result != REUSE_RESULT_NO &&
      (this.codec != codec || codecDrainAction == DRAIN_ACTION_REINITIALIZE)
    ) {
      return DecoderReuseEvaluation(
        codecInfo.name, oldFormat, newFormat, REUSE_RESULT_NO, overridingDiscardReasons
      )
    }

    return evaluation
  }

  @Throws(ExoPlaybackException::class)
  protected open fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {}

  @Throws(ExoPlaybackException::class)
  protected open fun handleInputBufferSupplementalData(buffer: DecoderInputBuffer) {}

  @Throws(ExoPlaybackException::class)
  protected open fun onQueueInputBuffer(buffer: DecoderInputBuffer) {}

  @CallSuper
  protected open fun onProcessedOutputBuffer(presentationTimeUs: Long) {
    while (pendingOutputStreamOffsetCount != 0 &&
      presentationTimeUs >= pendingOutputStreamSwitchTimesUs[0]
    ) {
      outputStreamStartPositionUs = pendingOutputStreamStartPositionsUs[0]
      setOutputStreamOffsetUs(pendingOutputStreamOffsetsUs[0])
      pendingOutputStreamOffsetCount--
      System.arraycopy(
        pendingOutputStreamStartPositionsUs,
        /* srcPos= */ 1,
        pendingOutputStreamStartPositionsUs,
        /* destPos= */ 0,
        pendingOutputStreamOffsetCount,
      )
      System.arraycopy(
        pendingOutputStreamOffsetsUs,
        /* srcPos= */ 1,
        pendingOutputStreamOffsetsUs,
        /* destPos= */ 0,
        pendingOutputStreamOffsetCount,
      )
      System.arraycopy(
        pendingOutputStreamSwitchTimesUs,
        /* srcPos= */ 1,
        pendingOutputStreamSwitchTimesUs,
        /* destPos= */ 0,
        pendingOutputStreamOffsetCount,
      )
      onProcessedStreamChange()
    }
  }

  protected open fun onProcessedStreamChange() {}

  protected open fun canReuseCodec(
    codecInfo: MediaCodecInfo,
    oldFormat: Format,
    newFormat: Format,
  ): DecoderReuseEvaluation {
    return DecoderReuseEvaluation(
      codecInfo.name,
      oldFormat,
      newFormat,
      REUSE_RESULT_NO,
      DISCARD_REASON_REUSE_NOT_IMPLEMENTED,
    )
  }

  protected open fun onOutputStreamOffsetUsChanged(outputStreamOffsetUs: Long) {}

  override fun isEnded(): Boolean {
    return outputStreamEnded
  }

  override fun isReady(): Boolean {
    return inputFormat != null &&
      (isSourceReady ||
        hasOutputBuffer() ||
        (codecHotswapDeadlineMs != C.TIME_UNSET &&
          SystemClock.elapsedRealtime() < codecHotswapDeadlineMs))
  }

  protected fun getPlaybackSpeed(): Float {
    return currentPlaybackSpeed
  }

  protected fun getCodecOperatingRate(): Float {
    return codecOperatingRate
  }

  protected open fun getCodecOperatingRateV23(
    targetPlaybackSpeed: Float,
    format: Format,
    streamFormats: Array<out Format>,
  ): Float {
    return CODEC_OPERATING_RATE_UNSET
  }

  @Throws(ExoPlaybackException::class)
  protected fun updateCodecOperatingRate(): Boolean {
    return updateCodecOperatingRate(codecInputFormat)
  }

  @Throws(ExoPlaybackException::class)
  @SuppressLint("NewApi")
  private fun updateCodecOperatingRate(format: Format?): Boolean {
    if (Util.SDK_INT < 23) {
      return true
    }

    if (codec == null ||
      codecDrainAction == DRAIN_ACTION_REINITIALIZE ||
      getState() == STATE_DISABLED
    ) {
      return true
    }

    val newCodecOperatingRate =
      getCodecOperatingRateV23(targetPlaybackSpeed, format!!, streamFormats!!)
    if (codecOperatingRate == newCodecOperatingRate) {
      return true
    } else if (newCodecOperatingRate == CODEC_OPERATING_RATE_UNSET) {
      drainAndReinitializeCodec()
      return false
    } else if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET ||
      newCodecOperatingRate > assumedMinimumCodecOperatingRate
    ) {
      val codecParameters = Bundle()
      codecParameters.putFloat(MediaFormat.KEY_OPERATING_RATE, newCodecOperatingRate)
      codec!!.setParameters(codecParameters)
      codecOperatingRate = newCodecOperatingRate
      return true
    }

    return true
  }

  private fun drainAndFlushCodec(): Boolean {
    if (codecReceivedBuffers) {
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM
      if (codecNeedsFlushWorkaround || codecNeedsEosFlushWorkaround) {
        codecDrainAction = DRAIN_ACTION_REINITIALIZE
        return false
      } else {
        codecDrainAction = DRAIN_ACTION_FLUSH
      }
    }
    return true
  }

  @RequiresApi(23)
  @Throws(ExoPlaybackException::class)
  @SuppressLint("NewApi")
  private fun drainAndUpdateCodecDrmSessionV23(): Boolean {
    if (codecReceivedBuffers) {
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM
      if (codecNeedsFlushWorkaround || codecNeedsEosFlushWorkaround) {
        codecDrainAction = DRAIN_ACTION_REINITIALIZE
        return false
      } else {
        codecDrainAction = DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION
      }
    } else {
      updateDrmSessionV23()
    }
    return true
  }

  @Throws(ExoPlaybackException::class)
  private fun drainAndReinitializeCodec() {
    if (codecReceivedBuffers) {
      codecDrainState = DRAIN_STATE_SIGNAL_END_OF_STREAM
      codecDrainAction = DRAIN_ACTION_REINITIALIZE
    } else {
      reinitializeCodec()
    }
  }

  @Throws(ExoPlaybackException::class)
  @SuppressLint("NewApi")
  private fun drainOutputBuffer(positionUs: Long, elapsedRealtimeUs: Long): Boolean {
    if (!hasOutputBuffer()) {
      val outputIndex: Int
      if (codecNeedsEosOutputExceptionWorkaround && codecReceivedEos) {
        try {
          outputIndex = codec!!.dequeueOutputBufferIndex(outputBufferInfo)
        } catch (e: IllegalStateException) {
          processEndOfStream()
          if (outputStreamEnded) {
            releaseCodec()
          }
          return false
        }
      } else {
        outputIndex = codec!!.dequeueOutputBufferIndex(outputBufferInfo)
      }

      if (outputIndex < 0) {
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED /* (-2) */) {
          processOutputMediaFormatChanged()
          return true
        }
        if (codecNeedsEosPropagation &&
          (inputStreamEnded || codecDrainState == DRAIN_STATE_WAIT_END_OF_STREAM)
        ) {
          processEndOfStream()
        }
        return false
      }

      if (shouldSkipAdaptationWorkaroundOutputBuffer) {
        shouldSkipAdaptationWorkaroundOutputBuffer = false
        codec!!.releaseOutputBuffer(outputIndex, false)
        return true
      } else if (outputBufferInfo.size == 0 &&
        (outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
      ) {
        processEndOfStream()
        return false
      }

      this.outputIndex = outputIndex
      outputBuffer = codec!!.getOutputBuffer(outputIndex)

      if (outputBuffer != null) {
        outputBuffer!!.position(outputBufferInfo.offset)
        outputBuffer!!.limit(outputBufferInfo.offset + outputBufferInfo.size)
      }
      if (codecNeedsEosBufferTimestampWorkaround &&
        outputBufferInfo.presentationTimeUs == 0L &&
        (outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 &&
        largestQueuedPresentationTimeUs != C.TIME_UNSET
      ) {
        outputBufferInfo.presentationTimeUs = largestQueuedPresentationTimeUs
      }
      isDecodeOnlyOutputBuffer = isDecodeOnlyBuffer(outputBufferInfo.presentationTimeUs)
      isLastOutputBuffer =
        lastBufferInStreamPresentationTimeUs == outputBufferInfo.presentationTimeUs
      updateOutputFormatForTime(outputBufferInfo.presentationTimeUs)
    }

    val processedOutputBuffer: Boolean
    if (codecNeedsEosOutputExceptionWorkaround && codecReceivedEos) {
      try {
        processedOutputBuffer =
          processOutputBuffer(
            positionUs,
            elapsedRealtimeUs,
            codec,
            outputBuffer,
            outputIndex,
            outputBufferInfo.flags,
            /* sampleCount= */ 1,
            outputBufferInfo.presentationTimeUs,
            isDecodeOnlyOutputBuffer,
            isLastOutputBuffer,
            outputFormat,
          )
      } catch (e: IllegalStateException) {
        processEndOfStream()
        if (outputStreamEnded) {
          releaseCodec()
        }
        return false
      }
    } else {
      processedOutputBuffer =
        processOutputBuffer(
          positionUs,
          elapsedRealtimeUs,
          codec,
          outputBuffer,
          outputIndex,
          outputBufferInfo.flags,
          /* sampleCount= */ 1,
          outputBufferInfo.presentationTimeUs,
          isDecodeOnlyOutputBuffer,
          isLastOutputBuffer,
          outputFormat,
        )
    }

    if (processedOutputBuffer) {
      onProcessedOutputBuffer(outputBufferInfo.presentationTimeUs)
      val isEndOfStream = (outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
      resetOutputBuffer()
      if (!isEndOfStream) {
        return true
      }
      processEndOfStream()
    }

    return false
  }

  private fun processOutputMediaFormatChanged() {
    codecHasOutputMediaFormat = true
    val mediaFormat = codec!!.getOutputFormat()!!
    if (codecAdaptationWorkaroundMode != ADAPTATION_WORKAROUND_MODE_NEVER &&
      mediaFormat.getInteger(MediaFormat.KEY_WIDTH) == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT &&
      mediaFormat.getInteger(MediaFormat.KEY_HEIGHT) == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT
    ) {
      shouldSkipAdaptationWorkaroundOutputBuffer = true
      return
    }
    if (codecNeedsMonoChannelCountWorkaround) {
      mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
    }
    codecOutputMediaFormat = mediaFormat
    codecOutputMediaFormatChanged = true
  }

  @Throws(ExoPlaybackException::class)
  protected abstract fun processOutputBuffer(
    positionUs: Long,
    elapsedRealtimeUs: Long,
    codec: MediaCodecAdapter?,
    buffer: ByteBuffer?,
    bufferIndex: Int,
    bufferFlags: Int,
    sampleCount: Int,
    bufferPresentationTimeUs: Long,
    isDecodeOnlyBuffer: Boolean,
    isLastBuffer: Boolean,
    format: Format?,
  ): Boolean

  @Throws(ExoPlaybackException::class)
  protected open fun renderToEndOfStream() {}

  @RequiresApi(23)
  @Throws(ExoPlaybackException::class)
  @SuppressLint("NewApi")
  private fun processEndOfStream() {
    when (codecDrainAction) {
      DRAIN_ACTION_REINITIALIZE -> reinitializeCodec()
      DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION -> {
        flushCodec()
        updateDrmSessionV23()
      }
      DRAIN_ACTION_FLUSH -> flushCodec()
      DRAIN_ACTION_NONE -> {
        outputStreamEnded = true
        renderToEndOfStream()
      }
      else -> {
        outputStreamEnded = true
        renderToEndOfStream()
      }
    }
  }

  protected fun setPendingOutputEndOfStream() {
    pendingOutputEndOfStream = true
  }

  protected fun getOutputStreamOffsetUs(): Long {
    return outputStreamOffsetUs
  }

  private fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
    this.outputStreamOffsetUs = outputStreamOffsetUs
    if (outputStreamOffsetUs != C.TIME_UNSET) {
      onOutputStreamOffsetUsChanged(outputStreamOffsetUs)
    }
  }

  @Throws(ExoPlaybackException::class)
  @SuppressLint("NewApi")
  private fun drmNeedsCodecReinitialization(
    codecInfo: MediaCodecInfo,
    newFormat: Format,
    oldSession: DrmSession?,
    newSession: DrmSession?,
  ): Boolean {
    if (oldSession == newSession) {
      return false
    }

    if (newSession == null || oldSession == null) {
      return true
    }

    if (newSession.getSchemeUuid() != oldSession.getSchemeUuid()) {
      return true
    }

    if (Util.SDK_INT < 23) {
      return true
    }
    if (C.PLAYREADY_UUID == oldSession.getSchemeUuid() || C.PLAYREADY_UUID == newSession.getSchemeUuid()) {
      return true
    }
    val newCryptoConfig = getFrameworkCryptoConfig(newSession) ?: return true

    val requiresSecureDecoder: Boolean =
      if (newCryptoConfig.forceAllowInsecureDecoderComponents) {
        false
      } else {
        newSession.requiresSecureDecoder(newFormat.sampleMimeType!!)
      }
    if (!codecInfo.secure && requiresSecureDecoder) {
      return true
    }

    return false
  }

  @Throws(ExoPlaybackException::class)
  private fun reinitializeCodec() {
    releaseCodec()
    maybeInitCodecOrBypass()
  }

  private fun isDecodeOnlyBuffer(presentationTimeUs: Long): Boolean {
    val size = decodeOnlyPresentationTimestamps.size
    for (i in 0 until size) {
      if (decodeOnlyPresentationTimestamps[i] == presentationTimeUs) {
        decodeOnlyPresentationTimestamps.removeAt(i)
        return true
      }
    }
    return false
  }

  @RequiresApi(23)
  @SuppressLint("NewApi")
  private fun updateDrmSessionV23() {
    try {
      mediaCrypto!!.setMediaDrmSession(getFrameworkCryptoConfig(sourceDrmSession!!)!!.sessionId)
    } catch (e: MediaCryptoException) {
      throw createRendererException(e, inputFormat, PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR)
    }
    setCodecDrmSession(sourceDrmSession)
    codecDrainState = DRAIN_STATE_NONE
    codecDrainAction = DRAIN_ACTION_NONE
  }

  @SuppressLint("NewApi")
  private fun getFrameworkCryptoConfig(drmSession: DrmSession): FrameworkCryptoConfig? {
    val cryptoConfig = drmSession.getCryptoConfig()
    if (cryptoConfig != null && cryptoConfig !is FrameworkCryptoConfig) {
      throw createRendererException(
        IllegalArgumentException("Expecting FrameworkCryptoConfig but found: $cryptoConfig"),
        inputFormat,
        PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
      )
    }
    return cryptoConfig as FrameworkCryptoConfig?
  }

  @Throws(ExoPlaybackException::class)
  private fun bypassRender(positionUs: Long, elapsedRealtimeUs: Long): Boolean {
    Assertions.checkState(!outputStreamEnded)
    if (bypassBatchBuffer.hasSamples()) {
      if (processOutputBuffer(
          positionUs,
          elapsedRealtimeUs,
          /* codec= */ null,
          bypassBatchBuffer.data,
          outputIndex,
          /* bufferFlags= */ 0,
          bypassBatchBuffer.sampleCount,
          bypassBatchBuffer.getFirstSampleTimeUs(),
          bypassBatchBuffer.isDecodeOnly,
          bypassBatchBuffer.isEndOfStream,
          outputFormat,
        )
      ) {
        onProcessedOutputBuffer(bypassBatchBuffer.lastSampleTimeUs)
        bypassBatchBuffer.clear()
      } else {
        return false
      }
    }

    if (inputStreamEnded) {
      outputStreamEnded = true
      return false
    }

    if (bypassSampleBufferPending) {
      Assertions.checkState(bypassBatchBuffer.append(bypassSampleBuffer))
      bypassSampleBufferPending = false
    }

    if (bypassDrainAndReinitialize) {
      if (bypassBatchBuffer.hasSamples()) {
        return true
      }
      disableBypass()
      bypassDrainAndReinitialize = false
      maybeInitCodecOrBypass()
      if (!bypassEnabled) {
        return false
      }
    }

    bypassRead()

    if (bypassBatchBuffer.hasSamples()) {
      bypassBatchBuffer.flip()
    }

    return bypassBatchBuffer.hasSamples() || inputStreamEnded || bypassDrainAndReinitialize
  }

  @Throws(ExoPlaybackException::class)
  private fun bypassRead() {
    Assertions.checkState(!inputStreamEnded)
    val formatHolder = formatHolder
    bypassSampleBuffer.clear()
    while (true) {
      bypassSampleBuffer.clear()
      val result = readSource(formatHolder, bypassSampleBuffer, /* readFlags= */ 0)
      when (result) {
        C.RESULT_FORMAT_READ -> {
          onInputFormatChanged(formatHolder)
          return
        }
        C.RESULT_NOTHING_READ -> return
        C.RESULT_BUFFER_READ -> {
          if (bypassSampleBuffer.isEndOfStream) {
            inputStreamEnded = true
            return
          }
          if (waitingForFirstSampleInFormat) {
            outputFormat = Assertions.checkNotNull(inputFormat)
            onOutputFormatChanged(outputFormat!!, /* mediaFormat= */ null)
            waitingForFirstSampleInFormat = false
          }
          bypassSampleBuffer.flip()
          if (!bypassBatchBuffer.append(bypassSampleBuffer)) {
            bypassSampleBufferPending = true
            return
          }
        }
        else -> throw IllegalStateException()
      }
    }
  }

  @RequiresApi(31)
  private object Api31 {
    @DoNotInline
    fun setLogSessionIdToMediaCodecFormat(
      codecConfiguration: MediaCodecAdapter.Configuration,
      playerId: PlayerId,
    ) {
      val logSessionId = playerId.getLogSessionId()
      if (logSessionId != LogSessionId.LOG_SESSION_ID_NONE) {
        codecConfiguration.mediaFormat!!.setString("log-session-id", logSessionId.stringId)
      }
    }
  }

  companion object {
    /** Indicates no codec operating rate should be set. */
    const val CODEC_OPERATING_RATE_UNSET: Float = -1f

    private const val TAG = "MediaCodecRenderer"
    private const val MAX_CODEC_HOTSWAP_TIME_MS: Long = 1000
    private const val MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT = 10

    private const val RECONFIGURATION_STATE_NONE = 0
    private const val RECONFIGURATION_STATE_WRITE_PENDING = 1
    private const val RECONFIGURATION_STATE_QUEUE_PENDING = 2

    private const val DRAIN_STATE_NONE = 0
    private const val DRAIN_STATE_SIGNAL_END_OF_STREAM = 1
    private const val DRAIN_STATE_WAIT_END_OF_STREAM = 2

    private const val DRAIN_ACTION_NONE = 0
    private const val DRAIN_ACTION_FLUSH = 1
    private const val DRAIN_ACTION_FLUSH_AND_UPDATE_DRM_SESSION = 2
    private const val DRAIN_ACTION_REINITIALIZE = 3

    private const val ADAPTATION_WORKAROUND_MODE_NEVER = 0
    private const val ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION = 1
    private const val ADAPTATION_WORKAROUND_MODE_ALWAYS = 2

    private val ADAPTATION_WORKAROUND_BUFFER =
      byteArrayOf(
        0, 0, 1, 103, 66, -64, 11, -38, 37, -112, 0, 0, 1, 104, -50, 15, 19, 32, 0, 0, 1, 101, -120,
        -124, 13, -50, 113, 24, -96, 0, 47, -65, 28, 49, -61, 39, 93, 120
      )

    private const val ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT = 32

    @JvmStatic
    protected fun supportsFormatDrm(format: Format): Boolean {
      return format.cryptoType == C.CRYPTO_TYPE_NONE || format.cryptoType == C.CRYPTO_TYPE_FRAMEWORK
    }

    @SuppressLint("NewApi")
    private fun isMediaCodecException(error: IllegalStateException): Boolean {
      if (Util.SDK_INT >= 21 && isMediaCodecExceptionV21(error)) {
        return true
      }
      val stackTrace = error.stackTrace
      return stackTrace.isNotEmpty() && stackTrace[0].className == "android.media.MediaCodec"
    }

    @RequiresApi(21)
    private fun isMediaCodecExceptionV21(error: IllegalStateException): Boolean {
      return error is CodecException
    }

    @RequiresApi(21)
    private fun isRecoverableMediaCodecExceptionV21(error: IllegalStateException): Boolean {
      if (error is CodecException) {
        return error.isRecoverable
      }
      return false
    }

    private fun codecNeedsFlushWorkaround(name: String): Boolean {
      return Util.SDK_INT < 18 ||
        (Util.SDK_INT == 18 &&
          ("OMX.SEC.avc.dec" == name || "OMX.SEC.avc.dec.secure" == name)) ||
        (Util.SDK_INT == 19 &&
          Util.MODEL.startsWith("SM-G800") &&
          ("OMX.Exynos.avc.dec" == name || "OMX.Exynos.avc.dec.secure" == name))
    }

    private fun getCodecAdaptationWorkaroundMode(name: String): Int {
      if (Util.SDK_INT <= 25 &&
        "OMX.Exynos.avc.dec.secure" == name &&
        (Util.MODEL.startsWith("SM-T585") ||
          Util.MODEL.startsWith("SM-A510") ||
          Util.MODEL.startsWith("SM-A520") ||
          Util.MODEL.startsWith("SM-J700"))
      ) {
        return ADAPTATION_WORKAROUND_MODE_ALWAYS
      } else if (Util.SDK_INT < 24 &&
        ("OMX.Nvidia.h264.decode" == name || "OMX.Nvidia.h264.decode.secure" == name) &&
        ("flounder" == Util.DEVICE ||
          "flounder_lte" == Util.DEVICE ||
          "grouper" == Util.DEVICE ||
          "tilapia" == Util.DEVICE)
      ) {
        return ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION
      } else {
        return ADAPTATION_WORKAROUND_MODE_NEVER
      }
    }

    private fun codecNeedsDiscardToSpsWorkaround(name: String, format: Format): Boolean {
      return Util.SDK_INT < 21 &&
        format.initializationData.isEmpty() &&
        "OMX.MTK.VIDEO.DECODER.AVC" == name
    }

    private fun codecNeedsSosFlushWorkaround(name: String): Boolean {
      return Util.SDK_INT == 29 && "c2.android.aac.decoder" == name
    }

    private fun codecNeedsEosPropagationWorkaround(codecInfo: MediaCodecInfo): Boolean {
      val name = codecInfo.name
      return (Util.SDK_INT <= 25 && "OMX.rk.video_decoder.avc" == name) ||
        (Util.SDK_INT <= 17 && "OMX.allwinner.video.decoder.avc" == name) ||
        (Util.SDK_INT <= 29 &&
          ("OMX.broadcom.video_decoder.tunnel" == name ||
            "OMX.broadcom.video_decoder.tunnel.secure" == name)) ||
        ("Amazon" == Util.MANUFACTURER && "AFTS" == Util.MODEL && codecInfo.secure)
    }

    private fun codecNeedsEosFlushWorkaround(name: String): Boolean {
      return (Util.SDK_INT <= 23 && "OMX.google.vorbis.decoder" == name) ||
        (Util.SDK_INT <= 19 &&
          ("hb2000" == Util.DEVICE || "stvm8" == Util.DEVICE) &&
          ("OMX.amlogic.avc.decoder.awesome" == name ||
            "OMX.amlogic.avc.decoder.awesome.secure" == name))
    }

    private fun codecNeedsEosBufferTimestampWorkaround(codecName: String): Boolean {
      return Util.SDK_INT < 21 &&
        "OMX.SEC.mp3.dec" == codecName &&
        "samsung" == Util.MANUFACTURER &&
        (Util.DEVICE.startsWith("baffin") ||
          Util.DEVICE.startsWith("grand") ||
          Util.DEVICE.startsWith("fortuna") ||
          Util.DEVICE.startsWith("gprimelte") ||
          Util.DEVICE.startsWith("j2y18lte") ||
          Util.DEVICE.startsWith("ms01"))
    }

    private fun codecNeedsEosOutputExceptionWorkaround(name: String): Boolean {
      return Util.SDK_INT == 21 && "OMX.google.aac.decoder" == name
    }

    private fun codecNeedsMonoChannelCountWorkaround(name: String, format: Format): Boolean {
      return Util.SDK_INT <= 18 &&
        format.channelCount == 1 &&
        "OMX.MTK.AUDIO.DECODER.MP3" == name
    }
  }
}
