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

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.os.Handler
import androidx.annotation.CallSuper
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
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
import com.google.android.exoplayer2.audio.AudioSink.InitializationException
import com.google.android.exoplayer2.audio.AudioSink.WriteException
import com.google.android.exoplayer2.decoder.DecoderInputBuffer
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.REUSE_RESULT_NO
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MediaClock
import com.google.android.exoplayer2.util.MediaFormatUtil
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.common.collect.ImmutableList
import java.lang.Math.max
import java.nio.ByteBuffer

/**
 * Decodes and renders audio using [MediaCodec] and an [AudioSink].
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
open class MediaCodecAudioRenderer : MediaCodecRenderer, MediaClock {

  private val context: Context
  private val eventDispatcher: EventDispatcher
  private val audioSink: AudioSink

  private var codecMaxInputSize: Int = 0
  private var codecNeedsDiscardChannelsWorkaround: Boolean = false
  private var decryptOnlyCodecFormat: Format? = null

  private var currentPositionUs: Long = 0
  private var allowFirstBufferPositionDiscontinuity: Boolean = false
  private var allowPositionDiscontinuity: Boolean = false
  private var audioSinkNeedsReset: Boolean = false

  private var experimentalKeepAudioTrackOnSeek: Boolean = false

  private var wakeupListener: WakeupListener? = null

  constructor(context: Context, mediaCodecSelector: MediaCodecSelector) : this(
    context, mediaCodecSelector, null, null
  )

  constructor(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?
  ) : this(
    context,
    mediaCodecSelector,
    eventHandler,
    eventListener,
    AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES
  )

  constructor(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioCapabilities: AudioCapabilities?,
    vararg audioProcessors: AudioProcessor
  ) : this(
    context,
    mediaCodecSelector,
    eventHandler,
    eventListener,
    DefaultAudioSink.Builder()
      .setAudioCapabilities(
        audioCapabilities ?: AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES
      )
      .setAudioProcessors(@Suppress("UNCHECKED_CAST") (audioProcessors as Array<AudioProcessor>))
      .build()
  )

  constructor(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink
  ) : this(
    context,
    MediaCodecAdapter.Factory.DEFAULT,
    mediaCodecSelector,
    /* enableDecoderFallback= */ false,
    eventHandler,
    eventListener,
    audioSink
  )

  constructor(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink
  ) : this(
    context,
    MediaCodecAdapter.Factory.DEFAULT,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink
  )

  constructor(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink
  ) : super(
    C.TRACK_TYPE_AUDIO,
    codecAdapterFactory,
    mediaCodecSelector,
    enableDecoderFallback,
    /* assumedMinimumCodecOperatingRate= */ 44100f
  ) {
    val appContext = context.applicationContext
    this.context = appContext
    this.audioSink = audioSink
    eventDispatcher = EventDispatcher(eventHandler, eventListener)
    audioSink.setListener(AudioSinkListener())
  }

  override fun getName(): String {
    return TAG
  }

  fun experimentalSetEnableKeepAudioTrackOnSeek(enableKeepAudioTrackOnSeek: Boolean) {
    this.experimentalKeepAudioTrackOnSeek = enableKeepAudioTrackOnSeek
  }

  @Throws(DecoderQueryException::class)
  override fun supportsFormat(mediaCodecSelector: MediaCodecSelector, format: Format): Int {
    if (!MimeTypes.isAudio(format.sampleMimeType)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
    }
    val tunnelingSupport =
      if (Util.SDK_INT >= 21) RendererCapabilities.TUNNELING_SUPPORTED
      else RendererCapabilities.TUNNELING_NOT_SUPPORTED
    val formatHasDrm = format.cryptoType != C.CRYPTO_TYPE_NONE
    val supportsFormatDrm = supportsFormatDrm(format)

    if (supportsFormatDrm &&
      audioSink.supportsFormat(format) &&
      (!formatHasDrm || MediaCodecUtil.getDecryptOnlyDecoderInfo() != null)
    ) {
      return RendererCapabilities.create(
        C.FORMAT_HANDLED, RendererCapabilities.ADAPTIVE_NOT_SEAMLESS, tunnelingSupport
      )
    }

    if (MimeTypes.AUDIO_RAW == format.sampleMimeType && !audioSink.supportsFormat(format)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
    }

    if (!audioSink.supportsFormat(
        Util.getPcmFormat(C.ENCODING_PCM_16BIT, format.channelCount, format.sampleRate)
      )
    ) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
    }

    val decoderInfos = getDecoderInfos(
      mediaCodecSelector, format, /* requiresSecureDecoder= */ false, audioSink
    )
    if (decoderInfos.isEmpty()) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
    }
    if (!supportsFormatDrm) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM)
    }

    var decoderInfo = decoderInfos[0]
    var isFormatSupported = decoderInfo.isFormatSupported(format)
    var isPreferredDecoder = true
    if (!isFormatSupported) {
      for (i in 1 until decoderInfos.size) {
        val otherDecoderInfo = decoderInfos[i]
        if (otherDecoderInfo.isFormatSupported(format)) {
          decoderInfo = otherDecoderInfo
          isFormatSupported = true
          isPreferredDecoder = false
          break
        }
      }
    }

    val formatSupport = if (isFormatSupported) C.FORMAT_HANDLED else C.FORMAT_EXCEEDS_CAPABILITIES
    val adaptiveSupport =
      if (isFormatSupported && decoderInfo.isSeamlessAdaptationSupported(format))
        RendererCapabilities.ADAPTIVE_SEAMLESS
      else RendererCapabilities.ADAPTIVE_NOT_SEAMLESS
    val hardwareAccelerationSupport =
      if (decoderInfo.hardwareAccelerated)
        RendererCapabilities.HARDWARE_ACCELERATION_SUPPORTED
      else RendererCapabilities.HARDWARE_ACCELERATION_NOT_SUPPORTED
    val decoderSupport =
      if (isPreferredDecoder) RendererCapabilities.DECODER_SUPPORT_PRIMARY
      else RendererCapabilities.DECODER_SUPPORT_FALLBACK
    return RendererCapabilities.create(
      formatSupport,
      adaptiveSupport,
      tunnelingSupport,
      hardwareAccelerationSupport,
      decoderSupport
    )
  }

  @Throws(DecoderQueryException::class)
  override fun getDecoderInfos(
    mediaCodecSelector: MediaCodecSelector,
    format: Format,
    requiresSecureDecoder: Boolean
  ): List<MediaCodecInfo> {
    return MediaCodecUtil.getDecoderInfosSortedByFormatSupport(
      getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder, audioSink), format
    )
  }

  override fun shouldUseBypass(format: Format): Boolean {
    return audioSink.supportsFormat(format)
  }

  override fun getMediaCodecConfiguration(
    codecInfo: MediaCodecInfo,
    format: Format,
    crypto: MediaCrypto?,
    codecOperatingRate: Float
  ): MediaCodecAdapter.Configuration {
    codecMaxInputSize = getCodecMaxInputSize(codecInfo, format, streamFormats)
    codecNeedsDiscardChannelsWorkaround = codecNeedsDiscardChannelsWorkaround(codecInfo.name)
    val mediaFormat =
      getMediaFormat(format, codecInfo.codecMimeType, codecMaxInputSize, codecOperatingRate)
    val decryptOnlyCodecEnabled =
      MimeTypes.AUDIO_RAW == codecInfo.mimeType && MimeTypes.AUDIO_RAW != format.sampleMimeType
    decryptOnlyCodecFormat = if (decryptOnlyCodecEnabled) format else null
    return MediaCodecAdapter.Configuration.createForAudioDecoding(
      codecInfo, mediaFormat, format, crypto
    )
  }

  override fun canReuseCodec(
    codecInfo: MediaCodecInfo,
    oldFormat: Format,
    newFormat: Format
  ): DecoderReuseEvaluation {
    val evaluation = codecInfo.canReuseCodec(oldFormat, newFormat)

    var discardReasons = evaluation.discardReasons
    if (getCodecMaxInputSize(codecInfo, newFormat) > codecMaxInputSize) {
      discardReasons = discardReasons or DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED
    }

    return DecoderReuseEvaluation(
      codecInfo.name,
      oldFormat,
      newFormat,
      if (discardReasons != 0) REUSE_RESULT_NO else evaluation.result,
      discardReasons
    )
  }

  override fun getMediaClock(): MediaClock? {
    return this
  }

  override fun getCodecOperatingRateV23(
    targetPlaybackSpeed: Float,
    format: Format,
    streamFormats: Array<out Format>
  ): Float {
    var maxSampleRate = -1
    for (streamFormat in streamFormats) {
      val streamSampleRate = streamFormat.sampleRate
      if (streamSampleRate != Format.NO_VALUE) {
        maxSampleRate = max(maxSampleRate, streamSampleRate)
      }
    }
    return if (maxSampleRate == -1) CODEC_OPERATING_RATE_UNSET
    else maxSampleRate * targetPlaybackSpeed
  }

  override fun onCodecInitialized(
    name: String,
    configuration: MediaCodecAdapter.Configuration,
    initializedTimestampMs: Long,
    initializationDurationMs: Long
  ) {
    eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs)
  }

  override fun onCodecReleased(name: String) {
    eventDispatcher.decoderReleased(name)
  }

  override fun onCodecError(codecError: Exception) {
    Log.e(TAG, "Audio codec error", codecError)
    eventDispatcher.audioCodecError(codecError)
  }

  @Throws(ExoPlaybackException::class)
  override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
    val evaluation = super.onInputFormatChanged(formatHolder)
    eventDispatcher.inputFormatChanged(formatHolder.format!!, evaluation)
    return evaluation
  }

  @Throws(ExoPlaybackException::class)
  override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
    val audioSinkInputFormat: Format
    var channelMap: IntArray? = null
    if (decryptOnlyCodecFormat != null) {
      audioSinkInputFormat = decryptOnlyCodecFormat!!
    } else if (codec == null) {
      audioSinkInputFormat = format
    } else {
      val mediaFmt = checkNotNull(mediaFormat)
      val pcmEncoding: Int =
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType) {
          format.pcmEncoding
        } else if (Util.SDK_INT >= 24 && mediaFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
          mediaFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else if (mediaFmt.containsKey(VIVO_BITS_PER_SAMPLE_KEY)) {
          Util.getPcmEncoding(mediaFmt.getInteger(VIVO_BITS_PER_SAMPLE_KEY))
        } else {
          C.ENCODING_PCM_16BIT
        }
      audioSinkInputFormat =
        Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_RAW)
          .setPcmEncoding(pcmEncoding)
          .setEncoderDelay(format.encoderDelay)
          .setEncoderPadding(format.encoderPadding)
          .setChannelCount(mediaFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
          .setSampleRate(mediaFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE))
          .build()
      if (codecNeedsDiscardChannelsWorkaround &&
        audioSinkInputFormat.channelCount == 6 &&
        format.channelCount < 6
      ) {
        channelMap = IntArray(format.channelCount) { i -> i }
      }
    }
    try {
      audioSink.configure(audioSinkInputFormat, /* specifiedBufferSize= */ 0, channelMap)
    } catch (e: AudioSink.ConfigurationException) {
      throw createRendererException(
        e, e.format, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
      )
    }
  }

  @CallSuper
  protected open fun onPositionDiscontinuity() {
    allowPositionDiscontinuity = true
  }

  @Throws(ExoPlaybackException::class)
  override fun onEnabled(joining: Boolean, mayRenderStartOfStream: Boolean) {
    super.onEnabled(joining, mayRenderStartOfStream)
    eventDispatcher.enabled(decoderCounters!!)
    if (configuration.tunneling) {
      audioSink.enableTunnelingV21()
    } else {
      audioSink.disableTunneling()
    }
    audioSink.setPlayerId(playerId)
  }

  @Throws(ExoPlaybackException::class)
  override fun onPositionReset(positionUs: Long, joining: Boolean) {
    super.onPositionReset(positionUs, joining)
    if (experimentalKeepAudioTrackOnSeek) {
      audioSink.experimentalFlushWithoutAudioTrackRelease()
    } else {
      audioSink.flush()
    }

    currentPositionUs = positionUs
    allowFirstBufferPositionDiscontinuity = true
    allowPositionDiscontinuity = true
  }

  override fun onStarted() {
    super.onStarted()
    audioSink.play()
  }

  override fun onStopped() {
    updateCurrentPosition()
    audioSink.pause()
    super.onStopped()
  }

  override fun onDisabled() {
    audioSinkNeedsReset = true
    try {
      audioSink.flush()
    } finally {
      try {
        super.onDisabled()
      } finally {
        eventDispatcher.disabled(decoderCounters!!)
      }
    }
  }

  override fun onReset() {
    try {
      super.onReset()
    } finally {
      if (audioSinkNeedsReset) {
        audioSinkNeedsReset = false
        audioSink.reset()
      }
    }
  }

  override fun isEnded(): Boolean {
    return super.isEnded() && audioSink.isEnded()
  }

  override fun isReady(): Boolean {
    return audioSink.hasPendingData() || super.isReady()
  }

  override fun getPositionUs(): Long {
    if (state == Renderer.STATE_STARTED) {
      updateCurrentPosition()
    }
    return currentPositionUs
  }

  override fun setPlaybackParameters(playbackParameters: PlaybackParameters?) {
    audioSink.setPlaybackParameters(playbackParameters)
  }

  override fun getPlaybackParameters(): PlaybackParameters? {
    return audioSink.getPlaybackParameters()
  }

  override fun onQueueInputBuffer(buffer: DecoderInputBuffer) {
    if (allowFirstBufferPositionDiscontinuity && !buffer.isDecodeOnly) {
      if (Math.abs(buffer.timeUs - currentPositionUs) > 500000) {
        currentPositionUs = buffer.timeUs
      }
      allowFirstBufferPositionDiscontinuity = false
    }
  }

  override fun onProcessedStreamChange() {
    super.onProcessedStreamChange()
    audioSink.handleDiscontinuity()
  }

  @Throws(ExoPlaybackException::class)
  override fun processOutputBuffer(
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
    format: Format
  ): Boolean {
    checkNotNull(buffer)

    if (decryptOnlyCodecFormat != null &&
      (bufferFlags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
    ) {
      checkNotNull(codec).releaseOutputBuffer(bufferIndex, false)
      return true
    }

    if (isDecodeOnlyBuffer) {
      codec?.releaseOutputBuffer(bufferIndex, false)
      decoderCounters!!.skippedOutputBufferCount += sampleCount
      audioSink.handleDiscontinuity()
      return true
    }

    val fullyConsumed: Boolean
    try {
      fullyConsumed = audioSink.handleBuffer(buffer, bufferPresentationTimeUs, sampleCount)
    } catch (e: InitializationException) {
      throw createRendererException(
        e, e.format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
      )
    } catch (e: WriteException) {
      throw createRendererException(
        e, format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
      )
    }

    if (fullyConsumed) {
      codec?.releaseOutputBuffer(bufferIndex, false)
      decoderCounters!!.renderedOutputBufferCount += sampleCount
      return true
    }

    return false
  }

  @Throws(ExoPlaybackException::class)
  override fun renderToEndOfStream() {
    try {
      audioSink.playToEndOfStream()
    } catch (e: AudioSink.WriteException) {
      throw createRendererException(
        e, e.format, e.isRecoverable, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
      )
    }
  }

  override fun onOutputStreamOffsetUsChanged(outputStreamOffsetUs: Long) {
    audioSink.setOutputStreamOffsetUs(outputStreamOffsetUs)
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
      Renderer.MSG_SET_PREFERRED_AUDIO_DEVICE -> {
        if (Util.SDK_INT >= 23) {
          Api23.setAudioSinkPreferredDevice(audioSink, message)
        }
      }
      Renderer.MSG_SET_SKIP_SILENCE_ENABLED -> audioSink.setSkipSilenceEnabled((message as Boolean))
      Renderer.MSG_SET_AUDIO_SESSION_ID -> audioSink.setAudioSessionId((message as Int))
      Renderer.MSG_SET_WAKEUP_LISTENER -> this.wakeupListener = message as WakeupListener?
      Renderer.MSG_SET_CAMERA_MOTION_LISTENER,
      Renderer.MSG_SET_CHANGE_FRAME_RATE_STRATEGY,
      Renderer.MSG_SET_SCALING_MODE,
      Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER,
      Renderer.MSG_SET_VIDEO_OUTPUT -> super.handleMessage(messageType, message)
      else -> super.handleMessage(messageType, message)
    }
  }

  protected open fun getCodecMaxInputSize(
    codecInfo: MediaCodecInfo,
    format: Format,
    streamFormats: Array<out Format>
  ): Int {
    var maxInputSize = getCodecMaxInputSize(codecInfo, format)
    if (streamFormats.size == 1) {
      return maxInputSize
    }
    for (streamFormat in streamFormats) {
      if (codecInfo.canReuseCodec(format, streamFormat).result != REUSE_RESULT_NO) {
        maxInputSize = max(maxInputSize, getCodecMaxInputSize(codecInfo, streamFormat))
      }
    }
    return maxInputSize
  }

  private fun getCodecMaxInputSize(codecInfo: MediaCodecInfo, format: Format): Int {
    if ("OMX.google.raw.decoder" == codecInfo.name) {
      if (Util.SDK_INT < 24 && !(Util.SDK_INT == 23 && Util.isTv(context))) {
        return Format.NO_VALUE
      }
    }
    return format.maxInputSize
  }

  @SuppressLint("InlinedApi")
  protected open fun getMediaFormat(
    format: Format,
    codecMimeType: String,
    codecMaxInputSize: Int,
    codecOperatingRate: Float
  ): MediaFormat {
    val mediaFormat = MediaFormat()
    mediaFormat.setString(MediaFormat.KEY_MIME, codecMimeType)
    mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, format.channelCount)
    mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, format.sampleRate)
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData)
    MediaFormatUtil.maybeSetInteger(mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, codecMaxInputSize)
    if (Util.SDK_INT >= 23) {
      mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0)
      if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET && !deviceDoesntSupportOperatingRate()) {
        mediaFormat.setFloat(MediaFormat.KEY_OPERATING_RATE, codecOperatingRate)
      }
    }
    if (Util.SDK_INT <= 28 && MimeTypes.AUDIO_AC4 == format.sampleMimeType) {
      mediaFormat.setInteger("ac4-is-sync", 1)
    }
    if (Util.SDK_INT >= 24 &&
      audioSink.getFormatSupport(
        Util.getPcmFormat(C.ENCODING_PCM_FLOAT, format.channelCount, format.sampleRate)
      ) == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
    ) {
      mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
    }
    if (Util.SDK_INT >= 32) {
      mediaFormat.setInteger(MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT, 99)
    }

    return mediaFormat
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
      this@MediaCodecAudioRenderer.onPositionDiscontinuity()
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

    override fun onOffloadBufferEmptying() {
      wakeupListener?.onWakeup()
    }

    override fun onOffloadBufferFull() {
      wakeupListener?.onSleep()
    }

    override fun onAudioSinkError(audioSinkError: Exception?) {
      Log.e(TAG, "Audio sink error", audioSinkError)
      eventDispatcher.audioSinkError(audioSinkError)
    }
  }

  @RequiresApi(23)
  private object Api23 {
    @DoNotInline
    fun setAudioSinkPreferredDevice(audioSink: AudioSink, messagePayload: Any?) {
      val audioDeviceInfo = messagePayload as AudioDeviceInfo?
      audioSink.setPreferredDevice(audioDeviceInfo)
    }
  }

  companion object {
    private const val TAG = "MediaCodecAudioRenderer"
    private const val VIVO_BITS_PER_SAMPLE_KEY = "v-bits-per-sample"

    private fun deviceDoesntSupportOperatingRate(): Boolean {
      return Util.SDK_INT == 23 &&
        ("ZTE B2017G" == Util.MODEL || "AXON 7 mini" == Util.MODEL)
    }

    private fun codecNeedsDiscardChannelsWorkaround(codecName: String): Boolean {
      return Util.SDK_INT < 24 &&
        "OMX.SEC.aac.dec" == codecName &&
        "samsung" == Util.MANUFACTURER &&
        (Util.DEVICE.startsWith("zeroflte") ||
          Util.DEVICE.startsWith("herolte") ||
          Util.DEVICE.startsWith("heroqlte"))
    }

    @Throws(DecoderQueryException::class)
    private fun getDecoderInfos(
      mediaCodecSelector: MediaCodecSelector,
      format: Format,
      requiresSecureDecoder: Boolean,
      audioSink: AudioSink
    ): List<MediaCodecInfo> {
      val mimeType = format.sampleMimeType ?: return ImmutableList.of()
      if (audioSink.supportsFormat(format)) {
        val codecInfo = MediaCodecUtil.getDecryptOnlyDecoderInfo()
        if (codecInfo != null) {
          return ImmutableList.of(codecInfo)
        }
      }
      val decoderInfos = mediaCodecSelector.getDecoderInfos(
        mimeType, requiresSecureDecoder, /* requiresTunnelingDecoder= */ false
      )
      val alternativeMimeType = MediaCodecUtil.getAlternativeCodecMimeType(format)
        ?: return ImmutableList.copyOf(decoderInfos)
      val alternativeDecoderInfos = mediaCodecSelector.getDecoderInfos(
        alternativeMimeType, requiresSecureDecoder, /* requiresTunnelingDecoder= */ false
      )
      return ImmutableList.builder<MediaCodecInfo>()
        .addAll(decoderInfos)
        .addAll(alternativeDecoderInfos)
        .build()
    }
  }
}
