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
package com.google.android.exoplayer2.audio

import android.media.AudioDeviceInfo
import android.os.Handler
import android.os.SystemClock
import androidx.annotation.CallSuper
import androidx.annotation.DoNotInline
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.BaseRenderer
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.FormatHolder
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.RendererCapabilities
import com.google.android.exoplayer2.audio.AudioRendererEventListener.EventDispatcher
import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.decoder.Decoder
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.decoder.DecoderException
import com.google.android.exoplayer2.decoder.DecoderInputBuffer
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.decoder.SimpleDecoderOutputBuffer
import com.google.android.exoplayer2.drm.DrmSession
import com.google.android.exoplayer2.source.SampleStream
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MediaClock
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.TraceUtil
import com.google.android.exoplayer2.util.Util
import com.google.errorprone.annotations.ForOverride
import kotlin.math.abs
import java.lang.Math.max

/**
 * Decodes and renders audio using a [Decoder].
 *
 * <p>This renderer accepts the following messages sent via [ExoPlayer.createMessage]
 * on the playback thread:
 *
 * <ul>
 *   <li>Message with type [Renderer.MSG_SET_VOLUME] to set the volume. The message payload should be
 *       a [Float] with 0 being silence and 1 being unity gain.
 *   <li>Message with type [Renderer.MSG_SET_AUDIO_ATTRIBUTES] to set the audio attributes. The
 *       message payload should be an [AudioAttributes] instance that will configure the
 *       underlying audio track.
 *   <li>Message with type [Renderer.MSG_SET_AUX_EFFECT_INFO] to set the auxiliary effect. The message
 *       payload should be an [AuxEffectInfo] instance that will configure the underlying
 *       audio track.
 *   <li>Message with type [Renderer.MSG_SET_SKIP_SILENCE_ENABLED] to enable or disable skipping
 *       silences. The message payload should be a [Boolean].
 *   <li>Message with type [Renderer.MSG_SET_AUDIO_SESSION_ID] to set the audio session ID. The
 *       message payload should be a session ID [Integer] that will be attached to the
 *       underlying audio track.
 * </ul>
 */
abstract class DecoderAudioRenderer<
    T : Decoder<DecoderInputBuffer, out SimpleDecoderOutputBuffer, out DecoderException>>(
  eventHandler: Handler?,
  eventListener: AudioRendererEventListener?,
  audioSink: AudioSink
) : BaseRenderer(C.TRACK_TYPE_AUDIO), MediaClock {

  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(
    AnnotationTarget.TYPE,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER
  )
  @IntDef(
    REINITIALIZATION_STATE_NONE,
    REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM,
    REINITIALIZATION_STATE_WAIT_END_OF_STREAM
  )
  private annotation class ReinitializationState

  private val eventDispatcher: EventDispatcher = EventDispatcher(eventHandler, eventListener)
  private val audioSink: AudioSink = audioSink
  private val flagsOnlyBuffer: DecoderInputBuffer = DecoderInputBuffer.newNoDataInstance()

  private var decoderCounters: DecoderCounters? = null
  private var inputFormat: Format? = null
  private var encoderDelay: Int = 0
  private var encoderPadding: Int = 0

  private var experimentalKeepAudioTrackOnSeek: Boolean = false
  private var firstStreamSampleRead: Boolean = false

  private var decoder: T? = null

  private var inputBuffer: DecoderInputBuffer? = null
  private var outputBuffer: SimpleDecoderOutputBuffer? = null
  private var decoderDrmSession: DrmSession? = null
  private var sourceDrmSession: DrmSession? = null

  @ReinitializationState private var decoderReinitializationState: Int = REINITIALIZATION_STATE_NONE
  private var decoderReceivedBuffers: Boolean = false
  private var audioTrackNeedsConfigure: Boolean = true

  private var currentPositionUs: Long = 0
  private var allowFirstBufferPositionDiscontinuity: Boolean = false
  private var allowPositionDiscontinuity: Boolean = false
  private var inputStreamEnded: Boolean = false
  private var outputStreamEnded: Boolean = false
  private var outputStreamOffsetUs: Long = C.TIME_UNSET
  private val pendingOutputStreamOffsetsUs: LongArray = LongArray(MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT)
  private var pendingOutputStreamOffsetCount: Int = 0

  init {
    audioSink.setListener(AudioSinkListener())
  }

  constructor() : this(/* eventHandler= */ null, /* eventListener= */ null)

  /**
   * @param eventHandler A handler to use when delivering events to `eventListener`. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional [AudioProcessor]s that will process audio before output.
   */
  constructor(
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    vararg audioProcessors: AudioProcessor
  ) : this(eventHandler, eventListener, /* audioCapabilities= */ null, *audioProcessors)

  /**
   * @param eventHandler A handler to use when delivering events to `eventListener`. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioCapabilities The audio capabilities for playback on this device. Use [
   *     AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES] if default capabilities (no encoded audio
   *     passthrough support) should be assumed.
   * @param audioProcessors Optional [AudioProcessor]s that will process audio before output.
   */
  constructor(
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioCapabilities: AudioCapabilities?,
    vararg audioProcessors: AudioProcessor
  ) : this(
    eventHandler,
    eventListener,
    DefaultAudioSink.Builder()
      .setAudioCapabilities(
        audioCapabilities ?: AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES
      )
      .setAudioProcessors(@Suppress("UNCHECKED_CAST") (audioProcessors as Array<AudioProcessor>))
      .build()
  )

  /**
   * Sets whether to enable the experimental feature that keeps and flushes the [android.media.AudioTrack] when a seek occurs, as opposed to releasing and reinitialising. Off
   * by default.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param enableKeepAudioTrackOnSeek Whether to keep the [android.media.AudioTrack] on seek.
   */
  fun experimentalSetEnableKeepAudioTrackOnSeek(enableKeepAudioTrackOnSeek: Boolean) {
    this.experimentalKeepAudioTrackOnSeek = enableKeepAudioTrackOnSeek
  }

  override fun getMediaClock(): MediaClock? {
    return this
  }

  @Throws(ExoPlaybackException::class)
  override fun supportsFormat(format: Format?): Int {
    if (format == null || !MimeTypes.isAudio(format.sampleMimeType)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
    }
    val formatSupport = supportsFormatInternal(format)
    if (formatSupport <= C.FORMAT_UNSUPPORTED_DRM) {
      return RendererCapabilities.create(formatSupport)
    }
    val tunnelingSupport =
      if (Util.SDK_INT >= 21) RendererCapabilities.TUNNELING_SUPPORTED
      else RendererCapabilities.TUNNELING_NOT_SUPPORTED
    return RendererCapabilities.create(
      formatSupport,
      RendererCapabilities.ADAPTIVE_NOT_SEAMLESS,
      tunnelingSupport
    )
  }

  /**
   * Returns the [C.FormatSupport] for the given [Format].
   *
   * @param format The format, which has an audio [Format.sampleMimeType].
   * @return The [C.FormatSupport] for this [Format].
   */
  @ForOverride
  protected abstract fun supportsFormatInternal(format: Format): Int

  /**
   * Returns whether the renderer's [AudioSink] supports a given [Format].
   *
   * @see AudioSink.supportsFormat
   */
  protected fun sinkSupportsFormat(format: Format): Boolean {
    return audioSink.supportsFormat(format)
  }

  /**
   * Returns the level of support that the renderer's [AudioSink] provides for a given [Format].
   *
   * @see AudioSink.getFormatSupport
   */
  protected fun getSinkFormatSupport(format: Format): Int {
    return audioSink.getFormatSupport(format)
  }

  @Throws(ExoPlaybackException::class)
  override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
    if (outputStreamEnded) {
      try {
        audioSink.playToEndOfStream()
      } catch (e: AudioSink.WriteException) {
        throw createRendererException(
          e, e.format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        )
      }
      return
    }

    // Try and read a format if we don't have one already.
    if (inputFormat == null) {
      // We don't have a format yet, so try and read one.
      val formatHolder = formatHolder
      flagsOnlyBuffer.clear()
      val result = readSource(formatHolder, flagsOnlyBuffer, SampleStream.FLAG_REQUIRE_FORMAT)
      if (result == C.RESULT_FORMAT_READ) {
        onInputFormatChanged(formatHolder)
      } else if (result == C.RESULT_BUFFER_READ) {
        // End of stream read having not read a format.
        Assertions.checkState(flagsOnlyBuffer.isEndOfStream)
        inputStreamEnded = true
        try {
          processEndOfStream()
        } catch (e: AudioSink.WriteException) {
          throw createRendererException(
            e, /* format= */ null, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
          )
        }
        return
      } else {
        // We still don't have a format and can't make progress without one.
        return
      }
    }

    // If we don't have a decoder yet, we need to instantiate one.
    maybeInitDecoder()

    if (decoder != null) {
      try {
        // Rendering loop.
        TraceUtil.beginSection("drainAndFeed")
        while (drainOutputBuffer()) {}
        while (feedInputBuffer()) {}
        TraceUtil.endSection()
      } catch (e: DecoderException) {
        // Can happen with dequeueOutputBuffer, dequeueInputBuffer, queueInputBuffer
        Log.e(TAG, "Audio codec error", e)
        eventDispatcher.audioCodecError(e)
        throw createRendererException(e, inputFormat, PlaybackException.ERROR_CODE_DECODING_FAILED)
      } catch (e: AudioSink.ConfigurationException) {
        throw createRendererException(
          e, e.format, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
        )
      } catch (e: AudioSink.InitializationException) {
        throw createRendererException(
          e, e.format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
        )
      } catch (e: AudioSink.WriteException) {
        throw createRendererException(
          e, e.format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        )
      }
      decoderCounters!!.ensureUpdated()
    }
  }

  /** See [AudioSink.Listener.onPositionDiscontinuity]. */
  @CallSuper
  @ForOverride
  protected open fun onPositionDiscontinuity() {
    // We are out of sync so allow currentPositionUs to jump backwards.
    allowPositionDiscontinuity = true
  }

  /**
   * Creates a decoder for the given format.
   *
   * @param format The format for which a decoder is required.
   * @param cryptoConfig The [CryptoConfig] object required for decoding encrypted content.
   *     May be null and can be ignored if decoder does not handle encrypted content.
   * @return The decoder.
   * @throws DecoderException If an error occurred creating a suitable decoder.
   */
  @ForOverride
  @Throws(DecoderException::class)
  protected abstract fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): T

  /**
   * Returns the format of audio buffers output by the decoder. Will not be called until the first
   * output buffer has been dequeued, so the decoder may use input data to determine the format.
   *
   * @param decoder The decoder.
   */
  @ForOverride
  protected abstract fun getOutputFormat(decoder: T): Format

  /**
   * Evaluates whether the existing decoder can be reused for a new [Format].
   *
   * <p>The default implementation does not allow decoder reuse.
   *
   * @param decoderName The name of the decoder.
   * @param oldFormat The previous format.
   * @param newFormat The new format.
   * @return The result of the evaluation.
   */
  @ForOverride
  protected open fun canReuseDecoder(
    decoderName: String, oldFormat: Format, newFormat: Format
  ): DecoderReuseEvaluation {
    return DecoderReuseEvaluation(
      decoderName, oldFormat, newFormat, DecoderReuseEvaluation.REUSE_RESULT_NO, DecoderReuseEvaluation.DISCARD_REASON_REUSE_NOT_IMPLEMENTED
    )
  }

  @Throws(
    ExoPlaybackException::class,
    DecoderException::class,
    AudioSink.ConfigurationException::class,
    AudioSink.InitializationException::class,
    AudioSink.WriteException::class
  )
  private fun drainOutputBuffer(): Boolean {
    val decoder = this.decoder!!
    if (outputBuffer == null) {
      outputBuffer = decoder.dequeueOutputBuffer()
      if (outputBuffer == null) {
        return false
      }
      val outputBuffer = this.outputBuffer!!
      if (outputBuffer.skippedOutputBufferCount > 0) {
        decoderCounters!!.skippedOutputBufferCount += outputBuffer.skippedOutputBufferCount
        audioSink.handleDiscontinuity()
      }
      if (outputBuffer.isFirstSample) {
        processFirstSampleOfStream()
      }
    }

    val outputBuffer = this.outputBuffer!!
    if (outputBuffer.isEndOfStream) {
      if (decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
        // We're waiting to re-initialize the decoder, and have now processed all final buffers.
        releaseDecoder()
        maybeInitDecoder()
        // The audio track may need to be recreated once the new output format is known.
        audioTrackNeedsConfigure = true
      } else {
        outputBuffer.release()
        this.outputBuffer = null
        try {
          processEndOfStream()
        } catch (e: AudioSink.WriteException) {
          throw createRendererException(
            e, e.format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
          )
        }
      }
      return false
    }

    if (audioTrackNeedsConfigure) {
      val outputFormat =
        getOutputFormat(decoder)
          .buildUpon()
          .setEncoderDelay(encoderDelay)
          .setEncoderPadding(encoderPadding)
          .build()
      audioSink.configure(outputFormat, /* specifiedBufferSize= */ 0, /* outputChannels= */ null)
      audioTrackNeedsConfigure = false
    }

    if (
      audioSink.handleBuffer(
        outputBuffer.data!!, outputBuffer.timeUs, /* encodedAccessUnitCount= */ 1
      )
    ) {
      decoderCounters!!.renderedOutputBufferCount++
      outputBuffer.release()
      this.outputBuffer = null
      return true
    }

    return false
  }

  private fun processFirstSampleOfStream() {
    audioSink.handleDiscontinuity()
    if (pendingOutputStreamOffsetCount != 0) {
      setOutputStreamOffsetUs(pendingOutputStreamOffsetsUs[0])
      pendingOutputStreamOffsetCount--
      System.arraycopy(
        pendingOutputStreamOffsetsUs,
        /* srcPos= */ 1,
        pendingOutputStreamOffsetsUs,
        /* destPos= */ 0,
        pendingOutputStreamOffsetCount
      )
    }
  }

  private fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
    this.outputStreamOffsetUs = outputStreamOffsetUs
    if (outputStreamOffsetUs != C.TIME_UNSET) {
      audioSink.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }
  }

  @Throws(DecoderException::class, ExoPlaybackException::class)
  private fun feedInputBuffer(): Boolean {
    val decoder = this.decoder
    if (
      decoder == null ||
      decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM ||
      inputStreamEnded
    ) {
      // We need to reinitialize the decoder or the input stream has ended.
      return false
    }

    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer()
      if (inputBuffer == null) {
        return false
      }
    }

    val inputBuffer = this.inputBuffer!!
    if (decoderReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM) {
      inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
      decoder.queueInputBuffer(inputBuffer)
      this.inputBuffer = null
      decoderReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM
      return false
    }

    val formatHolder = formatHolder
    return when (readSource(formatHolder, inputBuffer, /* readFlags= */ 0)) {
      C.RESULT_NOTHING_READ -> false
      C.RESULT_FORMAT_READ -> {
        onInputFormatChanged(formatHolder)
        true
      }
      C.RESULT_BUFFER_READ -> {
        if (inputBuffer.isEndOfStream) {
          inputStreamEnded = true
          decoder.queueInputBuffer(inputBuffer)
          this.inputBuffer = null
          return false
        }
        if (!firstStreamSampleRead) {
          firstStreamSampleRead = true
          inputBuffer.addFlag(C.BUFFER_FLAG_FIRST_SAMPLE)
        }
        inputBuffer.flip()
        inputBuffer.format = inputFormat
        onQueueInputBuffer(inputBuffer)
        decoder.queueInputBuffer(inputBuffer)
        decoderReceivedBuffers = true
        decoderCounters!!.queuedInputBufferCount++
        this.inputBuffer = null
        true
      }
      else -> throw IllegalStateException()
    }
  }

  @Throws(AudioSink.WriteException::class)
  private fun processEndOfStream() {
    outputStreamEnded = true
    audioSink.playToEndOfStream()
  }

  @Throws(ExoPlaybackException::class)
  private fun flushDecoder() {
    if (decoderReinitializationState != REINITIALIZATION_STATE_NONE) {
      releaseDecoder()
      maybeInitDecoder()
    } else {
      inputBuffer = null
      if (outputBuffer != null) {
        outputBuffer!!.release()
        outputBuffer = null
      }
      decoder!!.flush()
      decoderReceivedBuffers = false
    }
  }

  override fun isEnded(): Boolean {
    return outputStreamEnded && audioSink.isEnded()
  }

  override fun isReady(): Boolean {
    return audioSink.hasPendingData() ||
      (inputFormat != null && (isSourceReady || outputBuffer != null))
  }

  override fun getPositionUs(): Long {
    if (getState() == Renderer.STATE_STARTED) {
      updateCurrentPosition()
    }
    return currentPositionUs
  }

  override fun setPlaybackParameters(playbackParameters: PlaybackParameters?) {
    audioSink.setPlaybackParameters(playbackParameters ?: PlaybackParameters.DEFAULT)
  }

  override fun getPlaybackParameters(): PlaybackParameters? {
    return audioSink.getPlaybackParameters()
  }

  @Throws(ExoPlaybackException::class)
  override fun onEnabled(joining: Boolean, mayRenderStartOfStream: Boolean) {
    val counters = DecoderCounters()
    decoderCounters = counters
    eventDispatcher.enabled(counters)
    if (configuration.tunneling) {
      audioSink.enableTunnelingV21()
    } else {
      audioSink.disableTunneling()
    }
    audioSink.setPlayerId(playerId)
  }

  @Throws(ExoPlaybackException::class)
  override fun onPositionReset(positionUs: Long, joining: Boolean) {
    if (experimentalKeepAudioTrackOnSeek) {
      audioSink.experimentalFlushWithoutAudioTrackRelease()
    } else {
      audioSink.flush()
    }

    currentPositionUs = positionUs
    allowFirstBufferPositionDiscontinuity = true
    allowPositionDiscontinuity = true
    inputStreamEnded = false
    outputStreamEnded = false
    if (decoder != null) {
      flushDecoder()
    }
  }

  override fun onStarted() {
    audioSink.play()
  }

  override fun onStopped() {
    updateCurrentPosition()
    audioSink.pause()
  }

  override fun onDisabled() {
    inputFormat = null
    audioTrackNeedsConfigure = true
    setOutputStreamOffsetUs(C.TIME_UNSET)
    try {
      setSourceDrmSession(null)
      releaseDecoder()
      audioSink.reset()
    } finally {
      eventDispatcher.disabled(decoderCounters!!)
    }
  }

  @Throws(ExoPlaybackException::class)
  override fun onStreamChanged(formats: Array<out Format>, startPositionUs: Long, offsetUs: Long) {
    super.onStreamChanged(formats, startPositionUs, offsetUs)
    firstStreamSampleRead = false
    if (outputStreamOffsetUs == C.TIME_UNSET) {
      setOutputStreamOffsetUs(offsetUs)
    } else {
      if (pendingOutputStreamOffsetCount == pendingOutputStreamOffsetsUs.size) {
        Log.w(
          TAG,
          "Too many stream changes, so dropping offset: " +
            pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1]
        )
      } else {
        pendingOutputStreamOffsetCount++
      }
      pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1] = offsetUs
    }
  }

  @Throws(ExoPlaybackException::class)
  override fun handleMessage(messageType: Int, message: Any?) {
    when (messageType) {
      Renderer.MSG_SET_VOLUME -> audioSink.setVolume((message as Float))
      Renderer.MSG_SET_AUDIO_ATTRIBUTES -> {
        val audioAttributes = message as AudioAttributes
        audioSink.setAudioAttributes(audioAttributes)
      }
      Renderer.MSG_SET_AUX_EFFECT_INFO -> {
        val auxEffectInfo = message as AuxEffectInfo
        audioSink.setAuxEffectInfo(auxEffectInfo)
      }
      Renderer.MSG_SET_SKIP_SILENCE_ENABLED -> audioSink.setSkipSilenceEnabled((message as Boolean))
      Renderer.MSG_SET_AUDIO_SESSION_ID -> audioSink.setAudioSessionId((message as Int))
      Renderer.MSG_SET_PREFERRED_AUDIO_DEVICE -> {
        if (Util.SDK_INT >= 23) {
          Api23.setAudioSinkPreferredDevice(audioSink, message)
        }
      }
      Renderer.MSG_SET_CAMERA_MOTION_LISTENER,
      Renderer.MSG_SET_CHANGE_FRAME_RATE_STRATEGY,
      Renderer.MSG_SET_SCALING_MODE,
      Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER,
      Renderer.MSG_SET_VIDEO_OUTPUT,
      Renderer.MSG_SET_WAKEUP_LISTENER -> super.handleMessage(messageType, message)
      else -> super.handleMessage(messageType, message)
    }
  }

  @Throws(ExoPlaybackException::class)
  private fun maybeInitDecoder() {
    if (decoder != null) {
      return
    }

    setDecoderDrmSession(sourceDrmSession)

    var cryptoConfig: CryptoConfig? = null
    if (decoderDrmSession != null) {
      cryptoConfig = decoderDrmSession!!.getCryptoConfig()
      if (cryptoConfig == null) {
        val drmError = decoderDrmSession!!.getError()
        if (drmError != null) {
          // Continue for now. We may be able to avoid failure if a new input format causes the
          // session to be replaced without it having been used.
        } else {
          // The drm session isn't open yet.
          return
        }
      }
    }

    val inputFormat = this.inputFormat
    try {
      val codecInitializingTimestamp = SystemClock.elapsedRealtime()
      TraceUtil.beginSection("createAudioDecoder")
      val decoder = createDecoder(inputFormat!!, cryptoConfig)
      this.decoder = decoder
      TraceUtil.endSection()
      val codecInitializedTimestamp = SystemClock.elapsedRealtime()
      eventDispatcher.decoderInitialized(
        decoder.getName(),
        codecInitializedTimestamp,
        codecInitializedTimestamp - codecInitializingTimestamp
      )
      decoderCounters!!.decoderInitCount++
    } catch (e: DecoderException) {
      Log.e(TAG, "Audio codec error", e)
      eventDispatcher.audioCodecError(e)
      throw createRendererException(
        e, inputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
      )
    } catch (e: OutOfMemoryError) {
      throw createRendererException(
        e, inputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
      )
    }
  }

  private fun releaseDecoder() {
    inputBuffer = null
    outputBuffer = null
    decoderReinitializationState = REINITIALIZATION_STATE_NONE
    decoderReceivedBuffers = false
    if (decoder != null) {
      decoderCounters!!.decoderReleaseCount++
      decoder!!.release()
      eventDispatcher.decoderReleased(decoder!!.getName())
      decoder = null
    }
    setDecoderDrmSession(null)
  }

  private fun setSourceDrmSession(session: DrmSession?) {
    DrmSession.replaceSession(sourceDrmSession, session)
    sourceDrmSession = session
  }

  private fun setDecoderDrmSession(session: DrmSession?) {
    DrmSession.replaceSession(decoderDrmSession, session)
    decoderDrmSession = session
  }

  @Throws(ExoPlaybackException::class)
  private fun onInputFormatChanged(formatHolder: FormatHolder) {
    val newFormat = Assertions.checkNotNull(formatHolder.format)!!
    setSourceDrmSession(formatHolder.drmSession)
    val oldFormat = inputFormat
    inputFormat = newFormat
    encoderDelay = newFormat.encoderDelay
    encoderPadding = newFormat.encoderPadding

    val decoder = this.decoder
    if (decoder == null) {
      maybeInitDecoder()
      eventDispatcher.inputFormatChanged(inputFormat!!, /* decoderReuseEvaluation= */ null)
      return
    }

    val evaluation: DecoderReuseEvaluation =
      if (sourceDrmSession != decoderDrmSession) {
        DecoderReuseEvaluation(
          decoder.getName()!!,
          oldFormat!!,
          newFormat,
          DecoderReuseEvaluation.REUSE_RESULT_NO,
          DecoderReuseEvaluation.DISCARD_REASON_DRM_SESSION_CHANGED
        )
      } else {
        canReuseDecoder(decoder.getName()!!, oldFormat!!, newFormat)
      }

    if (evaluation.result == DecoderReuseEvaluation.REUSE_RESULT_NO) {
      if (decoderReceivedBuffers) {
        // Signal end of stream and wait for any final output buffers before re-initialization.
        decoderReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM
      } else {
        // There aren't any final output buffers, so release the decoder immediately.
        releaseDecoder()
        maybeInitDecoder()
        audioTrackNeedsConfigure = true
      }
    }
    eventDispatcher.inputFormatChanged(inputFormat!!, evaluation)
  }

  protected open fun onQueueInputBuffer(buffer: DecoderInputBuffer) {
    if (allowFirstBufferPositionDiscontinuity && !buffer.isDecodeOnly) {
      // TODO: Remove this hack once we have a proper fix for [Internal: b/71876314].
      // Allow the position to jump if the first presentable input buffer has a timestamp that
      // differs significantly from what was expected.
      if (abs(buffer.timeUs - currentPositionUs) > 500000) {
        currentPositionUs = buffer.timeUs
      }
      allowFirstBufferPositionDiscontinuity = false
    }
  }

  private fun updateCurrentPosition() {
    val newCurrentPositionUs = audioSink.getCurrentPositionUs(isEnded())
    if (newCurrentPositionUs != AudioSink.CURRENT_POSITION_NOT_SET) {
      currentPositionUs =
        if (allowPositionDiscontinuity) newCurrentPositionUs
        else max(currentPositionUs, newCurrentPositionUs)
      allowPositionDiscontinuity = false
    }
  }

  private inner class AudioSinkListener : AudioSink.Listener {
    override fun onPositionDiscontinuity() {
      this@DecoderAudioRenderer.onPositionDiscontinuity()
    }

    override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) {
      eventDispatcher.positionAdvancing(playoutStartSystemTimeMs)
    }

    override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
      eventDispatcher.underrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)
    }

    override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
      eventDispatcher.skipSilenceEnabledChanged(skipSilenceEnabled)
    }

    override fun onAudioSinkError(audioSinkError: Exception?) {
      Log.e(TAG, "Audio sink error", audioSinkError)
      eventDispatcher.audioSinkError(audioSinkError)
    }
  }

  @RequiresApi(23)
  private object Api23 {
    @JvmStatic
    @DoNotInline
    fun setAudioSinkPreferredDevice(audioSink: AudioSink, messagePayload: Any?) {
      val audioDeviceInfo = messagePayload as AudioDeviceInfo?
      audioSink.setPreferredDevice(audioDeviceInfo)
    }
  }

  companion object {
    private const val TAG = "DecoderAudioRenderer"

    /** The decoder does not need to be re-initialized. */
    private const val REINITIALIZATION_STATE_NONE = 0

    /**
     * The input format has changed in a way that requires the decoder to be re-initialized, but we
     * haven't yet signaled an end of stream to the existing decoder. We need to do so in order to
     * ensure that it outputs any remaining buffers before we release it.
     */
    private const val REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM = 1

    /**
     * The input format has changed in a way that requires the decoder to be re-initialized, and we've
     * signaled an end of stream to the existing decoder. We're waiting for the decoder to output an
     * end of stream signal to indicate that it has output any remaining buffers before we release it.
     */
    private const val REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 2

    /**
     * Generally there is zero or one pending output stream offset. We track more offsets to allow for
     * pending output streams that have fewer frames than the codec latency.
     */
    private const val MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT = 10
  }
}
