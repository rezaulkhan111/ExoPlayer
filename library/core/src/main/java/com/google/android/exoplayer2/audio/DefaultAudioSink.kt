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
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import android.media.metrics.LogSessionId
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Pair
import androidx.annotation.DoNotInline
import androidx.annotation.GuardedBy
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer.AudioOffloadListener
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.audio.AudioCapabilities.Companion.DEFAULT_AUDIO_CAPABILITIES
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.ConditionVariable
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.util.Util.constrainValue
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.lang.Math.max
import java.lang.Math.min
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ExecutorService

/**
 * Plays audio data. The implementation delegates to an [AudioTrack] and handles playback
 * position smoothing, non-blocking writes and reconfiguration.
 *
 * <p>If tunneling mode is enabled, care must be taken that audio processors do not output buffers
 * with a different duration than their input, and buffer processors must produce output
 * corresponding to their last input immediately after that input is queued. This means that, for
 * example, speed adjustment is not possible while using tunneling.
 */
class DefaultAudioSink private constructor(builder: Builder) : AudioSink {

  /**
   * Thrown when the audio track has provided a spurious timestamp, if [failOnSpuriousAudioTimestamp] is set.
   */
  class InvalidAudioTrackTimestampException(message: String) : RuntimeException(message)

  /**
   * @deprecated Use [com.google.android.exoplayer2.audio.AudioProcessorChain].
   */
  @Deprecated("Use com.google.android.exoplayer2.audio.AudioProcessorChain.")
  interface AudioProcessorChain : com.google.android.exoplayer2.audio.AudioProcessorChain

  /**
   * The default audio processor chain, which applies a (possibly empty) chain of user-defined audio
   * processors followed by [SilenceSkippingAudioProcessor] and [SonicAudioProcessor].
   */
  @Suppress("DEPRECATION")
  open class DefaultAudioProcessorChain(
    audioProcessors: Array<AudioProcessor>,
    private val silenceSkippingAudioProcessor: SilenceSkippingAudioProcessor,
    private val sonicAudioProcessor: SonicAudioProcessor
  ) : AudioProcessorChain {

    private val audioProcessors: Array<AudioProcessor>

    /**
     * Creates a new default chain of audio processors, with the user-defined `audioProcessors` applied before silence skipping and speed adjustment processors.
     */
    constructor(vararg audioProcessors: AudioProcessor) : this(
      @Suppress("UNCHECKED_CAST") (audioProcessors as Array<AudioProcessor>),
      SilenceSkippingAudioProcessor(),
      SonicAudioProcessor()
    )

    init {
      this.audioProcessors = Array(audioProcessors.size + 2) { i ->
        if (i < audioProcessors.size) audioProcessors[i]
        else if (i == audioProcessors.size) silenceSkippingAudioProcessor
        else sonicAudioProcessor
      }
    }

    override fun getAudioProcessors(): Array<AudioProcessor?>? {
      @Suppress("UNCHECKED_CAST")
      return audioProcessors as Array<AudioProcessor?>
    }

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters?): PlaybackParameters? {
      if (playbackParameters != null) {
        sonicAudioProcessor.setSpeed(playbackParameters.speed)
        sonicAudioProcessor.setPitch(playbackParameters.pitch)
      }
      return playbackParameters
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
      silenceSkippingAudioProcessor.setEnabled(skipSilenceEnabled)
      return skipSilenceEnabled
    }

    override fun getMediaDuration(playoutDuration: Long): Long {
      return sonicAudioProcessor.getMediaDuration(playoutDuration)
    }

    override fun getSkippedOutputFrameCount(): Long {
      return silenceSkippingAudioProcessor.skippedFrames
    }
  }

  /** Provides the buffer size to use when creating an [AudioTrack]. */
  interface AudioTrackBufferSizeProvider {

    /**
     * Returns the buffer size to use when creating an [AudioTrack] for a specific format and output mode.
     */
    fun getBufferSizeInBytes(
      minBufferSizeInBytes: Int,
      encoding: Int,
      outputMode: Int,
      pcmFrameSize: Int,
      sampleRate: Int,
      maxAudioTrackPlaybackSpeed: Double
    ): Int

    companion object {
      /** Default instance. */
      val DEFAULT: AudioTrackBufferSizeProvider =
        DefaultAudioTrackBufferSizeProvider.Builder().build()
    }
  }

  /** A builder to create [DefaultAudioSink] instances. */
  class Builder {

    internal var audioCapabilities: AudioCapabilities = DEFAULT_AUDIO_CAPABILITIES
    internal var audioProcessorChain: com.google.android.exoplayer2.audio.AudioProcessorChain? = null
    internal var enableFloatOutput: Boolean = false
    internal var enableAudioTrackPlaybackParams: Boolean = false
    internal var offloadMode: Int = OFFLOAD_MODE_DISABLED
    internal var audioTrackBufferSizeProvider: AudioTrackBufferSizeProvider = AudioTrackBufferSizeProvider.DEFAULT
    internal var audioOffloadListener: AudioOffloadListener? = null

    /**
     * Sets audio capabilities for playback on this device. May be `null` if the default
     * capabilities (no encoded audio passthrough support) should be assumed.
     */
    @CanIgnoreReturnValue
    fun setAudioCapabilities(audioCapabilities: AudioCapabilities): Builder {
      this.audioCapabilities = audioCapabilities
      return this
    }

    /**
     * Sets an array of [AudioProcessor]s that will process PCM audio before output.
     */
    @CanIgnoreReturnValue
    fun setAudioProcessors(audioProcessors: Array<AudioProcessor>): Builder {
      return setAudioProcessorChain(DefaultAudioProcessorChain(*audioProcessors))
    }

    /**
     * Sets the [com.google.android.exoplayer2.audio.AudioProcessorChain] to process audio before playback.
     */
    @CanIgnoreReturnValue
    fun setAudioProcessorChain(
      audioProcessorChain: com.google.android.exoplayer2.audio.AudioProcessorChain
    ): Builder {
      this.audioProcessorChain = audioProcessorChain
      return this
    }

    /**
     * Sets whether to enable 32-bit float output or integer output.
     */
    @CanIgnoreReturnValue
    fun setEnableFloatOutput(enableFloatOutput: Boolean): Builder {
      this.enableFloatOutput = enableFloatOutput
      return this
    }

    /**
     * Sets whether to control the playback speed using the platform implementation.
     */
    @CanIgnoreReturnValue
    fun setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams: Boolean): Builder {
      this.enableAudioTrackPlaybackParams = enableAudioTrackPlaybackParams
      return this
    }

    /**
     * Sets the offload mode.
     */
    @CanIgnoreReturnValue
    fun setOffloadMode(offloadMode: Int): Builder {
      this.offloadMode = offloadMode
      return this
    }

    /**
     * Sets an [AudioTrackBufferSizeProvider].
     */
    @CanIgnoreReturnValue
    fun setAudioTrackBufferSizeProvider(
      audioTrackBufferSizeProvider: AudioTrackBufferSizeProvider
    ): Builder {
      this.audioTrackBufferSizeProvider = audioTrackBufferSizeProvider
      return this
    }

    /**
     * Sets an optional [AudioOffloadListener].
     */
    @CanIgnoreReturnValue
    fun setExperimentalAudioOffloadListener(
      audioOffloadListener: AudioOffloadListener?
    ): Builder {
      this.audioOffloadListener = audioOffloadListener
      return this
    }

    /** Builds the [DefaultAudioSink]. Must only be called once per Builder instance. */
    fun build(): DefaultAudioSink {
      if (audioProcessorChain == null) {
        audioProcessorChain = DefaultAudioProcessorChain()
      }
      return DefaultAudioSink(this)
    }
  }

  /** Audio offload mode configuration. */
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
  @IntDef(
    OFFLOAD_MODE_DISABLED,
    OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED,
    OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED,
    OFFLOAD_MODE_ENABLED_GAPLESS_DISABLED
  )
  annotation class OffloadMode

  /** Output mode of the audio sink. */
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
  @IntDef(OUTPUT_MODE_PCM, OUTPUT_MODE_OFFLOAD, OUTPUT_MODE_PASSTHROUGH)
  annotation class OutputMode

  private val audioCapabilities: AudioCapabilities
  private val audioProcessorChain: com.google.android.exoplayer2.audio.AudioProcessorChain
  private val enableFloatOutput: Boolean
  private val channelMappingAudioProcessor: ChannelMappingAudioProcessor
  private val trimmingAudioProcessor: TrimmingAudioProcessor
  private val toIntPcmAvailableAudioProcessors: Array<AudioProcessor>
  private val toFloatPcmAvailableAudioProcessors: Array<AudioProcessor>
  private val releasingConditionVariable: ConditionVariable
  private val audioTrackPositionTracker: AudioTrackPositionTracker
  private val mediaPositionParametersCheckpoints: ArrayDeque<MediaPositionParameters>
  private val enableAudioTrackPlaybackParams: Boolean
  private val offloadMode: Int
  private var offloadStreamEventCallbackV29: StreamEventCallbackV29? = null
  private val initializationExceptionPendingExceptionHolder: PendingExceptionHolder<AudioSink.InitializationException>
  private val writeExceptionPendingExceptionHolder: PendingExceptionHolder<AudioSink.WriteException>
  private val audioTrackBufferSizeProvider: AudioTrackBufferSizeProvider
  private val audioOffloadListener: AudioOffloadListener?

  private var playerId: PlayerId? = null
  private var listener: AudioSink.Listener? = null
  private var pendingConfiguration: Configuration? = null
  private var configuration: Configuration? = null
  private var audioTrack: AudioTrack? = null

  private var audioAttributes: AudioAttributes
  private var afterDrainParameters: MediaPositionParameters? = null
  private var mediaPositionParameters: MediaPositionParameters
  private var audioTrackPlaybackParameters: PlaybackParameters

  private var avSyncHeader: ByteBuffer? = null
  private var bytesUntilNextAvSync: Int = 0

  private var submittedPcmBytes: Long = 0
  private var submittedEncodedFrames: Long = 0
  private var writtenPcmBytes: Long = 0
  private var writtenEncodedFrames: Long = 0
  private var framesPerEncodedSample: Int = 0
  private var startMediaTimeUsNeedsSync: Boolean = false
  private var startMediaTimeUsNeedsInit: Boolean = false
  private var startMediaTimeUs: Long = 0
  private var volume: Float = 1f

  private var activeAudioProcessors: Array<AudioProcessor>
  private var outputBuffers: Array<ByteBuffer>
  private var inputBuffer: ByteBuffer? = null
  private var inputBufferAccessUnitCount: Int = 0
  private var outputBuffer: ByteBuffer? = null
  private var preV21OutputBuffer: ByteArray? = null
  private var preV21OutputBufferOffset: Int = 0
  private var drainingAudioProcessorIndex: Int = C.INDEX_UNSET
  private var handledEndOfStream: Boolean = false
  private var stoppedAudioTrack: Boolean = false

  private var playing: Boolean = false
  private var externalAudioSessionIdProvided: Boolean = false
  private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
  private var auxEffectInfo: AuxEffectInfo
  private var preferredDevice: AudioDeviceInfoApi23? = null
  private var tunneling: Boolean = false
  private var lastFeedElapsedRealtimeMs: Long = 0
  private var offloadDisabledUntilNextConfiguration: Boolean = false
  private var isWaitingForOffloadEndOfStreamHandled: Boolean = false

  @Deprecated("Use Builder.")
  constructor(
    audioCapabilities: AudioCapabilities?,
    audioProcessors: Array<AudioProcessor>
  ) : this(
    Builder()
      .setAudioCapabilities(audioCapabilities ?: DEFAULT_AUDIO_CAPABILITIES)
      .setAudioProcessors(audioProcessors)
  )

  @Deprecated("Use Builder.")
  constructor(
    audioCapabilities: AudioCapabilities?,
    audioProcessors: Array<AudioProcessor>,
    enableFloatOutput: Boolean
  ) : this(
    Builder()
      .setAudioCapabilities(audioCapabilities ?: DEFAULT_AUDIO_CAPABILITIES)
      .setAudioProcessors(audioProcessors)
      .setEnableFloatOutput(enableFloatOutput)
  )

  @Deprecated("Use Builder.")
  constructor(
    audioCapabilities: AudioCapabilities?,
    audioProcessorChain: com.google.android.exoplayer2.audio.AudioProcessorChain,
    enableFloatOutput: Boolean,
    enableAudioTrackPlaybackParams: Boolean,
    offloadMode: Int
  ) : this(
    Builder()
      .setAudioCapabilities(audioCapabilities ?: DEFAULT_AUDIO_CAPABILITIES)
      .setAudioProcessorChain(audioProcessorChain)
      .setEnableFloatOutput(enableFloatOutput)
      .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
      .setOffloadMode(offloadMode)
  )

  init {
    audioCapabilities = builder.audioCapabilities
    audioProcessorChain = builder.audioProcessorChain!!
    enableFloatOutput = Util.SDK_INT >= 21 && builder.enableFloatOutput
    enableAudioTrackPlaybackParams = Util.SDK_INT >= 23 && builder.enableAudioTrackPlaybackParams
    offloadMode = if (Util.SDK_INT >= 29) builder.offloadMode else OFFLOAD_MODE_DISABLED
    audioTrackBufferSizeProvider = builder.audioTrackBufferSizeProvider
    releasingConditionVariable = ConditionVariable(Clock.DEFAULT)
    releasingConditionVariable.open()
    audioTrackPositionTracker = AudioTrackPositionTracker(PositionTrackerListener())
    channelMappingAudioProcessor = ChannelMappingAudioProcessor()
    trimmingAudioProcessor = TrimmingAudioProcessor()
    val toIntPcmAudioProcessors = ArrayList<AudioProcessor>()
    Collections.addAll(
      toIntPcmAudioProcessors,
      ResamplingAudioProcessor(),
      channelMappingAudioProcessor,
      trimmingAudioProcessor
    )
    val chainProcessors = audioProcessorChain.getAudioProcessors()
    if (chainProcessors != null) {
      for (processor in chainProcessors) {
        if (processor != null) {
          toIntPcmAudioProcessors.add(processor)
        }
      }
    }
    toIntPcmAvailableAudioProcessors = toIntPcmAudioProcessors.toTypedArray()
    toFloatPcmAvailableAudioProcessors = arrayOf(FloatResamplingAudioProcessor())
    volume = 1f
    audioAttributes = AudioAttributes.DEFAULT
    audioSessionId = C.AUDIO_SESSION_ID_UNSET
    auxEffectInfo = AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f)
    mediaPositionParameters = MediaPositionParameters(
      PlaybackParameters.DEFAULT,
      DEFAULT_SKIP_SILENCE,
      /* mediaTimeUs= */ 0,
      /* audioTrackPositionUs= */ 0
    )
    audioTrackPlaybackParameters = PlaybackParameters.DEFAULT
    drainingAudioProcessorIndex = C.INDEX_UNSET
    activeAudioProcessors = emptyArray()
    outputBuffers = emptyArray()
    mediaPositionParametersCheckpoints = ArrayDeque()
    initializationExceptionPendingExceptionHolder =
      PendingExceptionHolder(AUDIO_TRACK_RETRY_DURATION_MS.toLong())
    writeExceptionPendingExceptionHolder =
      PendingExceptionHolder(AUDIO_TRACK_RETRY_DURATION_MS.toLong())
    audioOffloadListener = builder.audioOffloadListener
  }

  // AudioSink implementation.

  override fun setListener(listener: AudioSink.Listener?) {
    this.listener = listener
  }

  override fun setPlayerId(playerId: PlayerId?) {
    this.playerId = playerId
  }

  override fun supportsFormat(format: Format?): Boolean {
    return format != null && getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
  }

  override fun getFormatSupport(format: Format?): Int {
    if (format == null) {
      return AudioSink.SINK_FORMAT_UNSUPPORTED
    }
    if (MimeTypes.AUDIO_RAW == format.sampleMimeType) {
      if (!Util.isEncodingLinearPcm(format.pcmEncoding)) {
        Log.w(TAG, "Invalid PCM encoding: ${format.pcmEncoding}")
        return AudioSink.SINK_FORMAT_UNSUPPORTED
      }
      if (format.pcmEncoding == C.ENCODING_PCM_16BIT ||
        (enableFloatOutput && format.pcmEncoding == C.ENCODING_PCM_FLOAT)
      ) {
        return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
      }
      return AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
    }
    if (!offloadDisabledUntilNextConfiguration && useOffloadedPlayback(format, audioAttributes)) {
      return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
    }
    if (audioCapabilities.isPassthroughPlaybackSupported(format)) {
      return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
    }
    return AudioSink.SINK_FORMAT_UNSUPPORTED
  }

  override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
    if (!isAudioTrackInitialized() || startMediaTimeUsNeedsInit) {
      return AudioSink.CURRENT_POSITION_NOT_SET
    }
    var positionUs = audioTrackPositionTracker.getCurrentPositionUs(sourceEnded)
    positionUs = min(positionUs, configuration!!.framesToDurationUs(getWrittenFrames()))
    return applySkipping(applyMediaPositionParameters(positionUs))
  }

  @Throws(AudioSink.ConfigurationException::class)
  override fun configure(
    inputFormat: Format?,
    specifiedBufferSize: Int,
    outputChannels: IntArray?
  ) {
    if (inputFormat == null) {
      return
    }
    var outputChannels = outputChannels
    val inputPcmFrameSize: Int
    val availableAudioProcessors: Array<AudioProcessor>

    val outputMode: Int
    val outputEncoding: Int
    val outputSampleRate: Int
    val outputChannelConfig: Int
    val outputPcmFrameSize: Int

    if (MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType) {
      Assertions.checkArgument(Util.isEncodingLinearPcm(inputFormat.pcmEncoding))

      inputPcmFrameSize = Util.getPcmFrameSize(inputFormat.pcmEncoding, inputFormat.channelCount)
      availableAudioProcessors =
        if (shouldUseFloatOutput(inputFormat.pcmEncoding))
          toFloatPcmAvailableAudioProcessors
        else toIntPcmAvailableAudioProcessors

      trimmingAudioProcessor.setTrimFrameCount(
        inputFormat.encoderDelay, inputFormat.encoderPadding
      )

      if (Util.SDK_INT < 21 && inputFormat.channelCount == 8 && outputChannels == null) {
        outputChannels = IntArray(6) { i -> i }
      }
      channelMappingAudioProcessor.setChannelMap(outputChannels)

      var outputFormat = AudioProcessor.AudioFormat(
        inputFormat.sampleRate, inputFormat.channelCount, inputFormat.pcmEncoding
      )
      for (audioProcessor in availableAudioProcessors) {
        try {
          val nextFormat = audioProcessor.configure(outputFormat)
          if (audioProcessor.isActive()) {
            outputFormat = nextFormat!!
          }
        } catch (e: UnhandledAudioFormatException) {
          throw AudioSink.ConfigurationException(e, inputFormat)
        }
      }

      outputMode = OUTPUT_MODE_PCM
      outputEncoding = outputFormat.encoding
      outputSampleRate = outputFormat.sampleRate
      outputChannelConfig = Util.getAudioTrackChannelConfig(outputFormat.channelCount)
      outputPcmFrameSize = Util.getPcmFrameSize(outputEncoding, outputFormat.channelCount)
    } else {
      inputPcmFrameSize = C.LENGTH_UNSET
      availableAudioProcessors = emptyArray()
      outputSampleRate = inputFormat.sampleRate
      outputPcmFrameSize = C.LENGTH_UNSET
      if (useOffloadedPlayback(inputFormat, audioAttributes)) {
        outputMode = OUTPUT_MODE_OFFLOAD
        outputEncoding = MimeTypes.getEncoding(
          checkNotNull(inputFormat.sampleMimeType), inputFormat.codecs
        )
        outputChannelConfig = Util.getAudioTrackChannelConfig(inputFormat.channelCount)
      } else {
        outputMode = OUTPUT_MODE_PASSTHROUGH
        val encodingAndChannelConfig: Pair<Int, Int>? =
          audioCapabilities.getEncodingAndChannelConfigForPassthrough(inputFormat)
        if (encodingAndChannelConfig == null) {
          throw AudioSink.ConfigurationException(
            "Unable to configure passthrough for: $inputFormat", inputFormat
          )
        }
        outputEncoding = encodingAndChannelConfig.first
        outputChannelConfig = encodingAndChannelConfig.second
      }
    }

    if (outputEncoding == C.ENCODING_INVALID) {
      throw AudioSink.ConfigurationException(
        "Invalid output encoding (mode=$outputMode) for: $inputFormat", inputFormat
      )
    }
    if (outputChannelConfig == AudioFormat.CHANNEL_INVALID) {
      throw AudioSink.ConfigurationException(
        "Invalid output channel config (mode=$outputMode) for: $inputFormat", inputFormat
      )
    }
    val bufferSize =
      if (specifiedBufferSize != 0) specifiedBufferSize
      else audioTrackBufferSizeProvider.getBufferSizeInBytes(
        getAudioTrackMinBufferSize(outputSampleRate, outputChannelConfig, outputEncoding),
        outputEncoding,
        outputMode,
        outputPcmFrameSize,
        outputSampleRate,
        if (enableAudioTrackPlaybackParams) MAX_PLAYBACK_SPEED.toDouble() else DEFAULT_PLAYBACK_SPEED.toDouble()
      )

    offloadDisabledUntilNextConfiguration = false
    val pendingConfiguration = Configuration(
      inputFormat,
      inputPcmFrameSize,
      outputMode,
      outputPcmFrameSize,
      outputSampleRate,
      outputChannelConfig,
      outputEncoding,
      bufferSize,
      availableAudioProcessors
    )
    if (isAudioTrackInitialized()) {
      this.pendingConfiguration = pendingConfiguration
    } else {
      configuration = pendingConfiguration
    }
  }

  private fun setupAudioProcessors() {
    val audioProcessors = configuration!!.availableAudioProcessors
    val newAudioProcessors = ArrayList<AudioProcessor>()
    for (audioProcessor in audioProcessors) {
      if (audioProcessor.isActive()) {
        newAudioProcessors.add(audioProcessor)
      } else {
        audioProcessor.flush()
      }
    }
    val count = newAudioProcessors.size
    activeAudioProcessors = newAudioProcessors.toTypedArray()
    outputBuffers = Array(count) { AudioProcessor.EMPTY_BUFFER }
    flushAudioProcessors()
  }

  private fun flushAudioProcessors() {
    for (i in activeAudioProcessors.indices) {
      val audioProcessor = activeAudioProcessors[i]
      audioProcessor.flush()
      outputBuffers[i] = audioProcessor.getOutput()!!
    }
  }

  @Throws(AudioSink.InitializationException::class)
  private fun initializeAudioTrack(): Boolean {
    if (!releasingConditionVariable.isOpen) {
      return false
    }

    val audioTrack = buildAudioTrackWithRetry()
    this.audioTrack = audioTrack
    if (isOffloadedPlayback(audioTrack)) {
      if (Util.SDK_INT >= 29) {
        registerStreamEventCallbackV29(audioTrack)
        if (offloadMode != OFFLOAD_MODE_ENABLED_GAPLESS_DISABLED) {
          audioTrack.setOffloadDelayPadding(
            configuration!!.inputFormat.encoderDelay, configuration!!.inputFormat.encoderPadding
          )
        }
      }
    }
    if (Util.SDK_INT >= 31 && playerId != null) {
      Api31.setLogSessionIdOnAudioTrack(audioTrack, playerId!!)
    }
    audioSessionId = audioTrack.audioSessionId
    audioTrackPositionTracker.setAudioTrack(
      audioTrack,
      /* isPassthrough= */ configuration!!.outputMode == OUTPUT_MODE_PASSTHROUGH,
      configuration!!.outputEncoding,
      configuration!!.outputPcmFrameSize,
      configuration!!.bufferSize
    )
    setVolumeInternal()

    if (auxEffectInfo.effectId != AuxEffectInfo.NO_AUX_EFFECT_ID) {
      audioTrack.attachAuxEffect(auxEffectInfo.effectId)
      audioTrack.setAuxEffectSendLevel(auxEffectInfo.sendLevel)
    }
    if (preferredDevice != null && Util.SDK_INT >= 23) {
      Api23.setPreferredDeviceOnAudioTrack(audioTrack, preferredDevice)
    }

    startMediaTimeUsNeedsInit = true
    return true
  }

  override fun play() {
    playing = true
    if (isAudioTrackInitialized()) {
      audioTrackPositionTracker.start()
      audioTrack!!.play()
    }
  }

  override fun handleDiscontinuity() {
    startMediaTimeUsNeedsSync = true
  }

  @Throws(AudioSink.InitializationException::class, AudioSink.WriteException::class)
  override fun handleBuffer(
    buffer: ByteBuffer?,
    presentationTimeUs: Long,
    encodedAccessUnitCount: Int
  ): Boolean {
    if (buffer == null) {
      return true
    }
    Assertions.checkArgument(inputBuffer == null || buffer === inputBuffer)

    if (pendingConfiguration != null) {
      if (!drainToEndOfStream()) {
        return false
      } else if (!pendingConfiguration!!.canReuseAudioTrack(configuration!!)) {
        playPendingData()
        if (hasPendingData()) {
          return false
        }
        flush()
      } else {
        configuration = pendingConfiguration
        pendingConfiguration = null
        if (isOffloadedPlayback(audioTrack!!) &&
          offloadMode != OFFLOAD_MODE_ENABLED_GAPLESS_DISABLED
        ) {
          if (Util.SDK_INT >= 29) {
            if (audioTrack!!.playState == AudioTrack.PLAYSTATE_PLAYING) {
              audioTrack!!.setOffloadEndOfStream()
            }
            audioTrack!!.setOffloadDelayPadding(
              configuration!!.inputFormat.encoderDelay, configuration!!.inputFormat.encoderPadding
            )
          }
          isWaitingForOffloadEndOfStreamHandled = true
        }
      }
      applyAudioProcessorPlaybackParametersAndSkipSilence(presentationTimeUs)
    }

    if (!isAudioTrackInitialized()) {
      try {
        if (!initializeAudioTrack()) {
          return false
        }
      } catch (e: AudioSink.InitializationException) {
        if (e.isRecoverable) {
          throw e
        }
        initializationExceptionPendingExceptionHolder.throwExceptionIfDeadlineIsReached(e)
        return false
      }
    }
    initializationExceptionPendingExceptionHolder.clear()

    if (startMediaTimeUsNeedsInit) {
      startMediaTimeUs = max(0L, presentationTimeUs)
      startMediaTimeUsNeedsSync = false
      startMediaTimeUsNeedsInit = false

      if (enableAudioTrackPlaybackParams && Util.SDK_INT >= 23) {
        setAudioTrackPlaybackParametersV23(audioTrackPlaybackParameters)
      }
      applyAudioProcessorPlaybackParametersAndSkipSilence(presentationTimeUs)

      if (playing) {
        play()
      }
    }

    if (!audioTrackPositionTracker.mayHandleBuffer(getWrittenFrames())) {
      return false
    }

    if (inputBuffer == null) {
      Assertions.checkArgument(buffer.order() == ByteOrder.LITTLE_ENDIAN)
      if (!buffer.hasRemaining()) {
        return true
      }

      if (configuration!!.outputMode != OUTPUT_MODE_PCM && framesPerEncodedSample == 0) {
        framesPerEncodedSample = getFramesPerEncodedSample(configuration!!.outputEncoding, buffer)
        if (framesPerEncodedSample == 0) {
          return true
        }
      }

      if (afterDrainParameters != null) {
        if (!drainToEndOfStream()) {
          return false
        }
        applyAudioProcessorPlaybackParametersAndSkipSilence(presentationTimeUs)
        afterDrainParameters = null
      }

      val expectedPresentationTimeUs = startMediaTimeUs + configuration!!.inputFramesToDurationUs(
        getSubmittedFrames() - trimmingAudioProcessor.trimmedFrameCount
      )
      if (!startMediaTimeUsNeedsSync &&
        Math.abs(expectedPresentationTimeUs - presentationTimeUs) > 200000
      ) {
        listener?.onAudioSinkError(
          AudioSink.UnexpectedDiscontinuityException(
            presentationTimeUs, expectedPresentationTimeUs
          )
        )
        startMediaTimeUsNeedsSync = true
      }
      if (startMediaTimeUsNeedsSync) {
        if (!drainToEndOfStream()) {
          return false
        }
        val adjustmentUs = presentationTimeUs - expectedPresentationTimeUs
        startMediaTimeUs += adjustmentUs
        startMediaTimeUsNeedsSync = false
        applyAudioProcessorPlaybackParametersAndSkipSilence(presentationTimeUs)
        if (listener != null && adjustmentUs != 0L) {
          listener!!.onPositionDiscontinuity()
        }
      }

      if (configuration!!.outputMode == OUTPUT_MODE_PCM) {
        submittedPcmBytes += buffer.remaining().toLong()
      } else {
        submittedEncodedFrames += framesPerEncodedSample.toLong() * encodedAccessUnitCount
      }

      inputBuffer = buffer
      inputBufferAccessUnitCount = encodedAccessUnitCount
    }

    processBuffers(presentationTimeUs)

    if (!inputBuffer!!.hasRemaining()) {
      inputBuffer = null
      inputBufferAccessUnitCount = 0
      return true
    }

    if (audioTrackPositionTracker.isStalled(getWrittenFrames())) {
      Log.w(TAG, "Resetting stalled audio track")
      flush()
      return true
    }

    return false
  }

  @Throws(AudioSink.InitializationException::class)
  private fun buildAudioTrackWithRetry(): AudioTrack {
    try {
      return buildAudioTrack(configuration!!)
    } catch (initialFailure: AudioSink.InitializationException) {
      if (configuration!!.bufferSize > AUDIO_TRACK_SMALLER_BUFFER_RETRY_SIZE) {
        val retryConfiguration =
          configuration!!.copyWithBufferSize(AUDIO_TRACK_SMALLER_BUFFER_RETRY_SIZE)
        try {
          val audioTrack = buildAudioTrack(retryConfiguration)
          configuration = retryConfiguration
          return audioTrack
        } catch (retryFailure: AudioSink.InitializationException) {
          initialFailure.addSuppressed(retryFailure)
        }
      }
      maybeDisableOffload()
      throw initialFailure
    }
  }

  @Throws(AudioSink.InitializationException::class)
  private fun buildAudioTrack(configuration: Configuration): AudioTrack {
    try {
      val audioTrack = configuration.buildAudioTrack(tunneling, audioAttributes, audioSessionId)
      audioOffloadListener?.onExperimentalOffloadedPlayback(isOffloadedPlayback(audioTrack))
      return audioTrack
    } catch (e: AudioSink.InitializationException) {
      listener?.onAudioSinkError(e)
      throw e
    }
  }

  @RequiresApi(29)
  private fun registerStreamEventCallbackV29(audioTrack: AudioTrack) {
    if (offloadStreamEventCallbackV29 == null) {
      offloadStreamEventCallbackV29 = StreamEventCallbackV29()
    }
    offloadStreamEventCallbackV29!!.register(audioTrack)
  }

  @Throws(AudioSink.WriteException::class)
  private fun processBuffers(avSyncPresentationTimeUs: Long) {
    val count = activeAudioProcessors.size
    var index = count
    while (index >= 0) {
      val input =
        if (index > 0) outputBuffers[index - 1]
        else inputBuffer ?: AudioProcessor.EMPTY_BUFFER
      if (index == count) {
        writeBuffer(input, avSyncPresentationTimeUs)
      } else {
        val audioProcessor = activeAudioProcessors[index]
        if (index > drainingAudioProcessorIndex) {
          audioProcessor.queueInput(input)
        }
        val output = audioProcessor.getOutput()!!
        outputBuffers[index] = output
        if (output.hasRemaining()) {
          index++
          continue
        }
      }

      if (input.hasRemaining()) {
        return
      }

      index--
    }
  }

  @Throws(AudioSink.WriteException::class)
  private fun writeBuffer(buffer: ByteBuffer, avSyncPresentationTimeUs: Long) {
    if (!buffer.hasRemaining()) {
      return
    }
    if (outputBuffer != null) {
      Assertions.checkArgument(outputBuffer === buffer)
    } else {
      outputBuffer = buffer
      if (Util.SDK_INT < 21) {
        val bytesRemaining = buffer.remaining()
        if (preV21OutputBuffer == null || preV21OutputBuffer!!.size < bytesRemaining) {
          preV21OutputBuffer = ByteArray(bytesRemaining)
        }
        val originalPosition = buffer.position()
        buffer.get(preV21OutputBuffer!!, 0, bytesRemaining)
        buffer.position(originalPosition)
        preV21OutputBufferOffset = 0
      }
    }
    val bytesRemaining = buffer.remaining()
    var bytesWrittenOrError = 0
    if (Util.SDK_INT < 21) {
      var bytesToWrite = audioTrackPositionTracker.getAvailableBufferSize(writtenPcmBytes)
      if (bytesToWrite > 0) {
        bytesToWrite = min(bytesRemaining, bytesToWrite)
        bytesWrittenOrError =
          audioTrack!!.write(preV21OutputBuffer!!, preV21OutputBufferOffset, bytesToWrite)
        if (bytesWrittenOrError > 0) {
          preV21OutputBufferOffset += bytesWrittenOrError
          buffer.position(buffer.position() + bytesWrittenOrError)
        }
      }
    } else if (tunneling) {
      Assertions.checkState(avSyncPresentationTimeUs != C.TIME_UNSET)
      bytesWrittenOrError =
        writeNonBlockingWithAvSyncV21(
          audioTrack!!, buffer, bytesRemaining, avSyncPresentationTimeUs
        )
    } else {
      bytesWrittenOrError = writeNonBlockingV21(audioTrack!!, buffer, bytesRemaining)
    }

    lastFeedElapsedRealtimeMs = SystemClock.elapsedRealtime()

    if (bytesWrittenOrError < 0) {
      val error = bytesWrittenOrError
      val isRecoverable = isAudioTrackDeadObject(error) && writtenEncodedFrames > 0
      val e = AudioSink.WriteException(error, configuration!!.inputFormat, isRecoverable)
      listener?.onAudioSinkError(e)
      if (e.isRecoverable) {
        throw e
      }
      writeExceptionPendingExceptionHolder.throwExceptionIfDeadlineIsReached(e)
      return
    }
    writeExceptionPendingExceptionHolder.clear()

    val bytesWritten = bytesWrittenOrError

    if (isOffloadedPlayback(audioTrack!!)) {
      if (writtenEncodedFrames > 0) {
        isWaitingForOffloadEndOfStreamHandled = false
      }

      if (playing &&
        listener != null &&
        bytesWritten < bytesRemaining &&
        !isWaitingForOffloadEndOfStreamHandled
      ) {
        listener!!.onOffloadBufferFull()
      }
    }

    if (configuration!!.outputMode == OUTPUT_MODE_PCM) {
      writtenPcmBytes += bytesWritten.toLong()
    }
    if (bytesWritten == bytesRemaining) {
      if (configuration!!.outputMode != OUTPUT_MODE_PCM) {
        Assertions.checkState(buffer === inputBuffer)
        writtenEncodedFrames += framesPerEncodedSample.toLong() * inputBufferAccessUnitCount
      }
      outputBuffer = null
    }
  }

  @Throws(AudioSink.WriteException::class)
  override fun playToEndOfStream() {
    if (!handledEndOfStream && isAudioTrackInitialized() && drainToEndOfStream()) {
      playPendingData()
      handledEndOfStream = true
    }
  }

  private fun maybeDisableOffload() {
    if (!configuration!!.outputModeIsOffload()) {
      return
    }
    offloadDisabledUntilNextConfiguration = true
  }

  @Throws(AudioSink.WriteException::class)
  private fun drainToEndOfStream(): Boolean {
    var audioProcessorNeedsEndOfStream = false
    if (drainingAudioProcessorIndex == C.INDEX_UNSET) {
      drainingAudioProcessorIndex = 0
      audioProcessorNeedsEndOfStream = true
    }
    while (drainingAudioProcessorIndex < activeAudioProcessors.size) {
      val audioProcessor = activeAudioProcessors[drainingAudioProcessorIndex]
      if (audioProcessorNeedsEndOfStream) {
        audioProcessor.queueEndOfStream()
      }
      processBuffers(C.TIME_UNSET)
      if (!audioProcessor.isEnded()) {
        return false
      }
      audioProcessorNeedsEndOfStream = true
      drainingAudioProcessorIndex++
    }

    if (outputBuffer != null) {
      writeBuffer(outputBuffer!!, C.TIME_UNSET)
      if (outputBuffer != null) {
        return false
      }
    }
    drainingAudioProcessorIndex = C.INDEX_UNSET
    return true
  }

  override fun isEnded(): Boolean {
    return !isAudioTrackInitialized() || (handledEndOfStream && !hasPendingData())
  }

  override fun hasPendingData(): Boolean {
    return isAudioTrackInitialized() &&
      audioTrackPositionTracker.hasPendingData(getWrittenFrames())
  }

  override fun setPlaybackParameters(playbackParameters: PlaybackParameters?) {
    val params = playbackParameters ?: PlaybackParameters.DEFAULT
    val constrainedParameters = PlaybackParameters(
      constrainValue(params.speed, MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED),
      constrainValue(params.pitch, MIN_PITCH, MAX_PITCH)
    )
    if (enableAudioTrackPlaybackParams && Util.SDK_INT >= 23) {
      setAudioTrackPlaybackParametersV23(constrainedParameters)
    } else {
      setAudioProcessorPlaybackParametersAndSkipSilence(
        constrainedParameters, getSkipSilenceEnabled()
      )
    }
  }

  override fun getPlaybackParameters(): PlaybackParameters {
    return if (enableAudioTrackPlaybackParams) audioTrackPlaybackParameters
    else getAudioProcessorPlaybackParameters()
  }

  override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
    setAudioProcessorPlaybackParametersAndSkipSilence(
      getAudioProcessorPlaybackParameters(), skipSilenceEnabled
    )
  }

  override fun getSkipSilenceEnabled(): Boolean {
    return getMediaPositionParameters().skipSilence
  }

  override fun setAudioAttributes(audioAttributes: AudioAttributes?) {
    val attrs = audioAttributes ?: AudioAttributes.DEFAULT
    if (this.audioAttributes == attrs) {
      return
    }
    this.audioAttributes = attrs
    if (tunneling) {
      return
    }
    flush()
  }

  override fun getAudioAttributes(): AudioAttributes? {
    return audioAttributes
  }

  override fun setAudioSessionId(audioSessionId: Int) {
    if (this.audioSessionId != audioSessionId) {
      this.audioSessionId = audioSessionId
      externalAudioSessionIdProvided = audioSessionId != C.AUDIO_SESSION_ID_UNSET
      flush()
    }
  }

  override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo?) {
    val auxInfo = auxEffectInfo ?: AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f)
    if (this.auxEffectInfo == auxInfo) {
      return
    }
    val effectId = auxInfo.effectId
    val sendLevel = auxInfo.sendLevel
    if (audioTrack != null) {
      if (this.auxEffectInfo.effectId != effectId) {
        audioTrack!!.attachAuxEffect(effectId)
      }
      if (effectId != AuxEffectInfo.NO_AUX_EFFECT_ID) {
        audioTrack!!.setAuxEffectSendLevel(sendLevel)
      }
    }
    this.auxEffectInfo = auxInfo
  }

  @RequiresApi(23)
  override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
    this.preferredDevice =
      if (audioDeviceInfo == null) null else AudioDeviceInfoApi23(audioDeviceInfo)
    if (audioTrack != null) {
      Api23.setPreferredDeviceOnAudioTrack(audioTrack!!, preferredDevice)
    }
  }

  override fun enableTunnelingV21() {
    Assertions.checkState(Util.SDK_INT >= 21)
    Assertions.checkState(externalAudioSessionIdProvided)
    if (!tunneling) {
      tunneling = true
      flush()
    }
  }

  override fun disableTunneling() {
    if (tunneling) {
      tunneling = false
      flush()
    }
  }

  override fun setVolume(volume: Float) {
    if (this.volume != volume) {
      this.volume = volume
      setVolumeInternal()
    }
  }

  private fun setVolumeInternal() {
    if (!isAudioTrackInitialized()) {
      // Do nothing.
    } else if (Util.SDK_INT >= 21) {
      setVolumeInternalV21(audioTrack!!, volume)
    } else {
      setVolumeInternalV3(audioTrack!!, volume)
    }
  }

  override fun pause() {
    playing = false
    if (isAudioTrackInitialized() && audioTrackPositionTracker.pause()) {
      audioTrack!!.pause()
    }
  }

  override fun flush() {
    if (isAudioTrackInitialized()) {
      resetSinkStateForFlush()

      if (audioTrackPositionTracker.isPlaying()) {
        audioTrack!!.pause()
      }
      if (isOffloadedPlayback(audioTrack!!)) {
        if (Util.SDK_INT >= 29) {
          offloadStreamEventCallbackV29?.unregister(audioTrack!!)
        }
      }
      if (Util.SDK_INT < 21 && !externalAudioSessionIdProvided) {
        audioSessionId = C.AUDIO_SESSION_ID_UNSET
      }
      if (pendingConfiguration != null) {
        configuration = pendingConfiguration
        pendingConfiguration = null
      }
      audioTrackPositionTracker.reset()
      releaseAudioTrackAsync(audioTrack!!, releasingConditionVariable)
      audioTrack = null
    }
    writeExceptionPendingExceptionHolder.clear()
    initializationExceptionPendingExceptionHolder.clear()
  }

  override fun experimentalFlushWithoutAudioTrackRelease() {
    if (Util.SDK_INT < 25) {
      flush()
      return
    }

    writeExceptionPendingExceptionHolder.clear()
    initializationExceptionPendingExceptionHolder.clear()

    if (!isAudioTrackInitialized()) {
      return
    }

    resetSinkStateForFlush()
    if (audioTrackPositionTracker.isPlaying()) {
      audioTrack!!.pause()
    }
    audioTrack!!.flush()

    audioTrackPositionTracker.reset()
    audioTrackPositionTracker.setAudioTrack(
      audioTrack!!,
      /* isPassthrough= */ configuration!!.outputMode == OUTPUT_MODE_PASSTHROUGH,
      configuration!!.outputEncoding,
      configuration!!.outputPcmFrameSize,
      configuration!!.bufferSize
    )

    startMediaTimeUsNeedsInit = true
  }

  override fun reset() {
    flush()
    for (audioProcessor in toIntPcmAvailableAudioProcessors) {
      audioProcessor.reset()
    }
    for (audioProcessor in toFloatPcmAvailableAudioProcessors) {
      audioProcessor.reset()
    }
    playing = false
    offloadDisabledUntilNextConfiguration = false
  }

  private fun resetSinkStateForFlush() {
    submittedPcmBytes = 0
    submittedEncodedFrames = 0
    writtenPcmBytes = 0
    writtenEncodedFrames = 0
    isWaitingForOffloadEndOfStreamHandled = false
    framesPerEncodedSample = 0
    mediaPositionParameters = MediaPositionParameters(
      getAudioProcessorPlaybackParameters(),
      getSkipSilenceEnabled(),
      /* mediaTimeUs= */ 0,
      /* audioTrackPositionUs= */ 0
    )
    startMediaTimeUs = 0
    afterDrainParameters = null
    mediaPositionParametersCheckpoints.clear()
    inputBuffer = null
    inputBufferAccessUnitCount = 0
    outputBuffer = null
    stoppedAudioTrack = false
    handledEndOfStream = false
    drainingAudioProcessorIndex = C.INDEX_UNSET
    avSyncHeader = null
    bytesUntilNextAvSync = 0
    trimmingAudioProcessor.resetTrimmedFrameCount()
    flushAudioProcessors()
  }

  @RequiresApi(23)
  private fun setAudioTrackPlaybackParametersV23(audioTrackPlaybackParameters: PlaybackParameters) {
    var audioTrackPlaybackParameters = audioTrackPlaybackParameters
    if (isAudioTrackInitialized()) {
      val playbackParams = PlaybackParams()
        .allowDefaults()
        .setSpeed(audioTrackPlaybackParameters.speed)
        .setPitch(audioTrackPlaybackParameters.pitch)
        .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_FAIL)
      try {
        audioTrack!!.playbackParams = playbackParams
      } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Failed to set playback params", e)
      }
      audioTrackPlaybackParameters = PlaybackParameters(
        audioTrack!!.playbackParams.speed, audioTrack!!.playbackParams.pitch
      )
      audioTrackPositionTracker.setAudioTrackPlaybackSpeed(audioTrackPlaybackParameters.speed)
    }
    this.audioTrackPlaybackParameters = audioTrackPlaybackParameters
  }

  private fun setAudioProcessorPlaybackParametersAndSkipSilence(
    playbackParameters: PlaybackParameters,
    skipSilence: Boolean
  ) {
    val currentMediaPositionParameters = getMediaPositionParameters()
    if (playbackParameters != currentMediaPositionParameters.playbackParameters ||
      skipSilence != currentMediaPositionParameters.skipSilence
    ) {
      val mediaPositionParameters = MediaPositionParameters(
        playbackParameters,
        skipSilence,
        /* mediaTimeUs= */ C.TIME_UNSET,
        /* audioTrackPositionUs= */ C.TIME_UNSET
      )
      if (isAudioTrackInitialized()) {
        this.afterDrainParameters = mediaPositionParameters
      } else {
        this.mediaPositionParameters = mediaPositionParameters
      }
    }
  }

  private fun getAudioProcessorPlaybackParameters(): PlaybackParameters {
    return getMediaPositionParameters().playbackParameters
  }

  private fun getMediaPositionParameters(): MediaPositionParameters {
    return afterDrainParameters
      ?: if (!mediaPositionParametersCheckpoints.isEmpty())
        mediaPositionParametersCheckpoints.last
      else mediaPositionParameters
  }

  private fun applyAudioProcessorPlaybackParametersAndSkipSilence(presentationTimeUs: Long) {
    val playbackParameters = if (shouldApplyAudioProcessorPlaybackParameters())
      audioProcessorChain.applyPlaybackParameters(getAudioProcessorPlaybackParameters())!!
    else PlaybackParameters.DEFAULT
    val skipSilenceEnabled = if (shouldApplyAudioProcessorPlaybackParameters())
      audioProcessorChain.applySkipSilenceEnabled(getSkipSilenceEnabled())
    else DEFAULT_SKIP_SILENCE
    mediaPositionParametersCheckpoints.add(
      MediaPositionParameters(
        playbackParameters,
        skipSilenceEnabled,
        /* mediaTimeUs= */ max(0L, presentationTimeUs),
        /* audioTrackPositionUs= */ configuration!!.framesToDurationUs(getWrittenFrames())
      )
    )
    setupAudioProcessors()
    listener?.onSkipSilenceEnabledChanged(skipSilenceEnabled)
  }

  private fun shouldApplyAudioProcessorPlaybackParameters(): Boolean {
    return !tunneling &&
      MimeTypes.AUDIO_RAW == configuration!!.inputFormat.sampleMimeType &&
      !shouldUseFloatOutput(configuration!!.inputFormat.pcmEncoding)
  }

  private fun shouldUseFloatOutput(pcmEncoding: Int): Boolean {
    return enableFloatOutput && Util.isEncodingHighResolutionPcm(pcmEncoding)
  }

  private fun applyMediaPositionParameters(positionUs: Long): Long {
    while (!mediaPositionParametersCheckpoints.isEmpty() &&
      positionUs >= mediaPositionParametersCheckpoints.first.audioTrackPositionUs
    ) {
      mediaPositionParameters = mediaPositionParametersCheckpoints.remove()
    }

    val playoutDurationSinceLastCheckpointUs =
      positionUs - mediaPositionParameters.audioTrackPositionUs
    if (mediaPositionParameters.playbackParameters == PlaybackParameters.DEFAULT) {
      return mediaPositionParameters.mediaTimeUs + playoutDurationSinceLastCheckpointUs
    } else if (mediaPositionParametersCheckpoints.isEmpty()) {
      val mediaDurationSinceLastCheckpointUs =
        audioProcessorChain.getMediaDuration(playoutDurationSinceLastCheckpointUs)
      return mediaPositionParameters.mediaTimeUs + mediaDurationSinceLastCheckpointUs
    } else {
      val nextMediaPositionParameters = mediaPositionParametersCheckpoints.first
      val playoutDurationUntilNextCheckpointUs =
        nextMediaPositionParameters.audioTrackPositionUs - positionUs
      val mediaDurationUntilNextCheckpointUs = Util.getMediaDurationForPlayoutDuration(
        playoutDurationUntilNextCheckpointUs,
        mediaPositionParameters.playbackParameters.speed
      )
      return nextMediaPositionParameters.mediaTimeUs - mediaDurationUntilNextCheckpointUs
    }
  }

  private fun applySkipping(positionUs: Long): Long {
    return positionUs +
      configuration!!.framesToDurationUs(audioProcessorChain.getSkippedOutputFrameCount())
  }

  private fun isAudioTrackInitialized(): Boolean {
    return audioTrack != null
  }

  private fun getSubmittedFrames(): Long {
    return if (configuration!!.outputMode == OUTPUT_MODE_PCM)
      submittedPcmBytes / configuration!!.inputPcmFrameSize
    else submittedEncodedFrames
  }

  private fun getWrittenFrames(): Long {
    return if (configuration!!.outputMode == OUTPUT_MODE_PCM)
      writtenPcmBytes / configuration!!.outputPcmFrameSize
    else writtenEncodedFrames
  }

  private fun useOffloadedPlayback(format: Format, audioAttributes: AudioAttributes): Boolean {
    if (Util.SDK_INT < 29 || offloadMode == OFFLOAD_MODE_DISABLED) {
      return false
    }
    val encoding = MimeTypes.getEncoding(
      checkNotNull(format.sampleMimeType), format.codecs
    )
    if (encoding == C.ENCODING_INVALID) {
      return false
    }
    val channelConfig = Util.getAudioTrackChannelConfig(format.channelCount)
    if (channelConfig == AudioFormat.CHANNEL_INVALID) {
      return false
    }
    val audioFormat = getAudioFormat(format.sampleRate, channelConfig, encoding)

    return when (
      getOffloadedPlaybackSupport(
        audioFormat, audioAttributes.audioAttributesV21!!.audioAttributes
      )
    ) {
      AudioManager.PLAYBACK_OFFLOAD_NOT_SUPPORTED -> false
      AudioManager.PLAYBACK_OFFLOAD_SUPPORTED -> {
        val isGapless = format.encoderDelay != 0 || format.encoderPadding != 0
        val gaplessSupportRequired = offloadMode == OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
        !isGapless || !gaplessSupportRequired
      }
      AudioManager.PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED -> true
      else -> throw IllegalStateException()
    }
  }

  @RequiresApi(29)
  @SuppressLint("InlinedApi")
  private fun getOffloadedPlaybackSupport(
    audioFormat: AudioFormat,
    audioAttributes: android.media.AudioAttributes
  ): Int {
    if (Util.SDK_INT >= 31) {
      return AudioManager.getPlaybackOffloadSupport(audioFormat, audioAttributes)
    }
    if (!AudioManager.isOffloadedPlaybackSupported(audioFormat, audioAttributes)) {
      return AudioManager.PLAYBACK_OFFLOAD_NOT_SUPPORTED
    }
    if (Util.SDK_INT == 30 && Util.MODEL.startsWith("Pixel")) {
      return AudioManager.PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED
    }
    return AudioManager.PLAYBACK_OFFLOAD_SUPPORTED
  }

  private fun playPendingData() {
    if (!stoppedAudioTrack) {
      stoppedAudioTrack = true
      audioTrackPositionTracker.handleEndOfStream(getWrittenFrames())
      audioTrack!!.stop()
      bytesUntilNextAvSync = 0
    }
  }

  @RequiresApi(29)
  private inner class StreamEventCallbackV29 {
    private val handler: Handler = Handler(Looper.myLooper()!!)
    private val callback: AudioTrack.StreamEventCallback = object : AudioTrack.StreamEventCallback() {
      override fun onDataRequest(track: AudioTrack, size: Int) {
        if (track != audioTrack) {
          return
        }
        if (listener != null && playing) {
          listener!!.onOffloadBufferEmptying()
        }
      }

      override fun onTearDown(track: AudioTrack) {
        if (track != audioTrack) {
          return
        }
        if (listener != null && playing) {
          listener!!.onOffloadBufferEmptying()
        }
      }
    }

    fun register(audioTrack: AudioTrack) {
      audioTrack.registerStreamEventCallback(handler::post, callback)
    }

    fun unregister(audioTrack: AudioTrack) {
      audioTrack.unregisterStreamEventCallback(callback)
      handler.removeCallbacksAndMessages(null)
    }
  }

  private class MediaPositionParameters(
    val playbackParameters: PlaybackParameters,
    val skipSilence: Boolean,
    val mediaTimeUs: Long,
    val audioTrackPositionUs: Long
  )

  private inner class PositionTrackerListener : AudioTrackPositionTracker.Listener {
    override fun onPositionFramesMismatch(
      audioTimestampPositionFrames: Long,
      audioTimestampSystemTimeUs: Long,
      systemTimeUs: Long,
      playbackPositionUs: Long
    ) {
      val message =
        "Spurious audio timestamp (frame position mismatch): " +
          "$audioTimestampPositionFrames, " +
          "$audioTimestampSystemTimeUs, " +
          "$systemTimeUs, " +
          "$playbackPositionUs, " +
          "${getSubmittedFrames()}, " +
          "${getWrittenFrames()}"
      if (failOnSpuriousAudioTimestamp) {
        throw InvalidAudioTrackTimestampException(message)
      }
      Log.w(TAG, message)
    }

    override fun onSystemTimeUsMismatch(
      audioTimestampPositionFrames: Long,
      audioTimestampSystemTimeUs: Long,
      systemTimeUs: Long,
      playbackPositionUs: Long
    ) {
      val message =
        "Spurious audio timestamp (system clock mismatch): " +
          "$audioTimestampPositionFrames, " +
          "$audioTimestampSystemTimeUs, " +
          "$systemTimeUs, " +
          "$playbackPositionUs, " +
          "${getSubmittedFrames()}, " +
          "${getWrittenFrames()}"
      if (failOnSpuriousAudioTimestamp) {
        throw InvalidAudioTrackTimestampException(message)
      }
      Log.w(TAG, message)
    }

    override fun onInvalidLatency(latencyUs: Long) {
      Log.w(TAG, "Ignoring impossibly large audio latency: $latencyUs")
    }

    override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) {
      listener?.onPositionAdvancing(playoutStartSystemTimeMs)
    }

    override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long) {
      if (listener != null) {
        val elapsedSinceLastFeedMs = SystemClock.elapsedRealtime() - lastFeedElapsedRealtimeMs
        listener!!.onUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)
      }
    }
  }

  private class Configuration(
    val inputFormat: Format,
    val inputPcmFrameSize: Int,
    @OutputMode val outputMode: Int,
    val outputPcmFrameSize: Int,
    val outputSampleRate: Int,
    val outputChannelConfig: Int,
    @C.Encoding val outputEncoding: Int,
    val bufferSize: Int,
    val availableAudioProcessors: Array<AudioProcessor>
  ) {

    fun copyWithBufferSize(bufferSize: Int): Configuration {
      return Configuration(
        inputFormat,
        inputPcmFrameSize,
        outputMode,
        outputPcmFrameSize,
        outputSampleRate,
        outputChannelConfig,
        outputEncoding,
        bufferSize,
        availableAudioProcessors
      )
    }

    fun canReuseAudioTrack(audioTrackConfiguration: Configuration): Boolean {
      return audioTrackConfiguration.outputMode == outputMode &&
        audioTrackConfiguration.outputEncoding == outputEncoding &&
        audioTrackConfiguration.outputSampleRate == outputSampleRate &&
        audioTrackConfiguration.outputChannelConfig == outputChannelConfig &&
        audioTrackConfiguration.outputPcmFrameSize == outputPcmFrameSize
    }

    fun inputFramesToDurationUs(frameCount: Long): Long {
      return (frameCount * C.MICROS_PER_SECOND) / inputFormat.sampleRate
    }

    fun framesToDurationUs(frameCount: Long): Long {
      return (frameCount * C.MICROS_PER_SECOND) / outputSampleRate
    }

    @Throws(AudioSink.InitializationException::class)
    fun buildAudioTrack(
      tunneling: Boolean,
      audioAttributes: AudioAttributes,
      audioSessionId: Int
    ): AudioTrack {
      val audioTrack: AudioTrack = try {
        createAudioTrack(tunneling, audioAttributes, audioSessionId)
      } catch (e: Exception) {
        if (e is UnsupportedOperationException || e is IllegalArgumentException) {
          throw AudioSink.InitializationException(
            AudioTrack.STATE_UNINITIALIZED,
            outputSampleRate,
            outputChannelConfig,
            bufferSize,
            inputFormat,
            /* isRecoverable= */ outputModeIsOffload(),
            e
          )
        } else {
          throw e
        }
      }

      val state = audioTrack.state
      if (state != AudioTrack.STATE_INITIALIZED) {
        try {
          audioTrack.release()
        } catch (e: Exception) {
          // Swallow exception
        }
        throw AudioSink.InitializationException(
          state,
          outputSampleRate,
          outputChannelConfig,
          bufferSize,
          inputFormat,
          /* isRecoverable= */ outputModeIsOffload(),
          /* audioTrackException= */ null
        )
      }
      return audioTrack
    }

    private fun createAudioTrack(
      tunneling: Boolean,
      audioAttributes: AudioAttributes,
      audioSessionId: Int
    ): AudioTrack {
      return if (Util.SDK_INT >= 29) {
        createAudioTrackV29(tunneling, audioAttributes, audioSessionId)
      } else if (Util.SDK_INT >= 21) {
        createAudioTrackV21(tunneling, audioAttributes, audioSessionId)
      } else {
        createAudioTrackV9(audioAttributes, audioSessionId)
      }
    }

    @RequiresApi(29)
    private fun createAudioTrackV29(
      tunneling: Boolean,
      audioAttributes: AudioAttributes,
      audioSessionId: Int
    ): AudioTrack {
      val audioFormat = getAudioFormat(outputSampleRate, outputChannelConfig, outputEncoding)
      val audioTrackAttributes = getAudioTrackAttributesV21(audioAttributes, tunneling)
      return AudioTrack.Builder()
        .setAudioAttributes(audioTrackAttributes)
        .setAudioFormat(audioFormat)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(bufferSize)
        .setSessionId(audioSessionId)
        .setOffloadedPlayback(outputMode == OUTPUT_MODE_OFFLOAD)
        .build()
    }

    @RequiresApi(21)
    private fun createAudioTrackV21(
      tunneling: Boolean,
      audioAttributes: AudioAttributes,
      audioSessionId: Int
    ): AudioTrack {
      return AudioTrack(
        getAudioTrackAttributesV21(audioAttributes, tunneling),
        getAudioFormat(outputSampleRate, outputChannelConfig, outputEncoding),
        bufferSize,
        AudioTrack.MODE_STREAM,
        audioSessionId
      )
    }

    @Suppress("DEPRECATION")
    private fun createAudioTrackV9(
      audioAttributes: AudioAttributes,
      audioSessionId: Int
    ): AudioTrack {
      val streamType = Util.getStreamTypeForAudioUsage(audioAttributes.usage)
      return if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
        AudioTrack(
          streamType,
          outputSampleRate,
          outputChannelConfig,
          outputEncoding,
          bufferSize,
          AudioTrack.MODE_STREAM
        )
      } else {
        AudioTrack(
          streamType,
          outputSampleRate,
          outputChannelConfig,
          outputEncoding,
          bufferSize,
          AudioTrack.MODE_STREAM,
          audioSessionId
        )
      }
    }

    fun outputModeIsOffload(): Boolean {
      return outputMode == OUTPUT_MODE_OFFLOAD
    }

    companion object {
      @RequiresApi(21)
      private fun getAudioTrackAttributesV21(
        audioAttributes: AudioAttributes,
        tunneling: Boolean
      ): android.media.AudioAttributes {
        return if (tunneling) {
          getAudioTrackTunnelingAttributesV21()
        } else {
          audioAttributes.audioAttributesV21!!.audioAttributes
        }
      }

      @RequiresApi(21)
      private fun getAudioTrackTunnelingAttributesV21(): android.media.AudioAttributes {
        return android.media.AudioAttributes.Builder()
          .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
          .setFlags(android.media.AudioAttributes.FLAG_HW_AV_SYNC)
          .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
          .build()
      }
    }
  }

  private class PendingExceptionHolder<T : Exception>(private val throwDelayMs: Long) {
    private var pendingException: T? = null
    private var throwDeadlineMs: Long = 0

    @Throws(Exception::class)
    fun throwExceptionIfDeadlineIsReached(exception: T) {
      val nowMs = SystemClock.elapsedRealtime()
      if (pendingException == null) {
        pendingException = exception
        throwDeadlineMs = nowMs + throwDelayMs
      }
      if (nowMs >= throwDeadlineMs) {
        if (pendingException !== exception) {
          pendingException!!.addSuppressed(exception)
        }
        val pendingException = this.pendingException!!
        clear()
        throw pendingException
      }
    }

    fun clear() {
      pendingException = null
    }
  }

  @RequiresApi(23)
  private class AudioDeviceInfoApi23(val audioDeviceInfo: AudioDeviceInfo)

  @RequiresApi(23)
  private object Api23 {
    @DoNotInline
    fun setPreferredDeviceOnAudioTrack(
      audioTrack: AudioTrack,
      audioDeviceInfo: AudioDeviceInfoApi23?
    ) {
      audioTrack.setPreferredDevice(audioDeviceInfo?.audioDeviceInfo)
    }
  }

  @RequiresApi(31)
  private object Api31 {
    @DoNotInline
    fun setLogSessionIdOnAudioTrack(audioTrack: AudioTrack, playerId: PlayerId) {
      val logSessionId = playerId.getLogSessionId()
      if (logSessionId != LogSessionId.LOG_SESSION_ID_NONE) {
        audioTrack.setLogSessionId(logSessionId)
      }
    }
  }

  companion object {
    private const val AUDIO_TRACK_SMALLER_BUFFER_RETRY_SIZE = 1_000_000
    const val DEFAULT_PLAYBACK_SPEED: Float = 1f
    const val MIN_PLAYBACK_SPEED: Float = 0.1f
    const val MAX_PLAYBACK_SPEED: Float = 8f
    const val MIN_PITCH: Float = 0.1f
    const val MAX_PITCH: Float = 8f
    private const val DEFAULT_SKIP_SILENCE = false

    const val OFFLOAD_MODE_DISABLED: Int = 0
    const val OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED: Int = 1
    const val OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED: Int = 2
    const val OFFLOAD_MODE_ENABLED_GAPLESS_DISABLED: Int = 3

    const val OUTPUT_MODE_PCM: Int = 0
    const val OUTPUT_MODE_OFFLOAD: Int = 1
    const val OUTPUT_MODE_PASSTHROUGH: Int = 2

    private const val ERROR_NATIVE_DEAD_OBJECT = -32
    private const val AUDIO_TRACK_RETRY_DURATION_MS = 100
    private const val TAG = "DefaultAudioSink"

    @JvmField
    var failOnSpuriousAudioTimestamp: Boolean = false

    private val releaseExecutorLock = Any()

    @GuardedBy("releaseExecutorLock")
    private var releaseExecutor: ExecutorService? = null

    @GuardedBy("releaseExecutorLock")
    private var pendingReleaseCount: Int = 0

    @RequiresApi(21)
    private fun getAudioFormat(sampleRate: Int, channelConfig: Int, encoding: Int): AudioFormat {
      return AudioFormat.Builder()
        .setSampleRate(sampleRate)
        .setChannelMask(channelConfig)
        .setEncoding(encoding)
        .build()
    }

    private fun getAudioTrackMinBufferSize(
      sampleRateInHz: Int,
      channelConfig: Int,
      encoding: Int
    ): Int {
      val minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, encoding)
      Assertions.checkState(minBufferSize != AudioTrack.ERROR_BAD_VALUE)
      return minBufferSize
    }

    private fun isAudioTrackDeadObject(status: Int): Boolean {
      return (Util.SDK_INT >= 24 && status == AudioTrack.ERROR_DEAD_OBJECT) ||
        status == ERROR_NATIVE_DEAD_OBJECT
    }

    private fun isOffloadedPlayback(audioTrack: AudioTrack): Boolean {
      return Util.SDK_INT >= 29 && audioTrack.isOffloadedPlayback
    }

    private fun getFramesPerEncodedSample(encoding: Int, buffer: ByteBuffer): Int {
      return when (encoding) {
        C.ENCODING_MP3 -> {
          val headerDataInBigEndian = Util.getBigEndianInt(buffer, buffer.position())
          val frameCount = MpegAudioUtil.parseMpegAudioFrameSampleCount(headerDataInBigEndian)
          if (frameCount == C.LENGTH_UNSET) {
            throw IllegalArgumentException()
          }
          frameCount
        }
        C.ENCODING_AAC_LC -> AacUtil.AAC_LC_AUDIO_SAMPLE_COUNT
        C.ENCODING_AAC_HE_V1, C.ENCODING_AAC_HE_V2 -> AacUtil.AAC_HE_AUDIO_SAMPLE_COUNT
        C.ENCODING_AAC_XHE -> AacUtil.AAC_XHE_AUDIO_SAMPLE_COUNT
        C.ENCODING_AAC_ELD -> AacUtil.AAC_LD_AUDIO_SAMPLE_COUNT
        C.ENCODING_DTS, C.ENCODING_DTS_HD -> DtsUtil.parseDtsAudioSampleCount(buffer)
        C.ENCODING_AC3, C.ENCODING_E_AC3, C.ENCODING_E_AC3_JOC -> Ac3Util.parseAc3SyncframeAudioSampleCount(buffer)
        C.ENCODING_AC4 -> Ac4Util.parseAc4SyncframeAudioSampleCount(buffer)
        C.ENCODING_DOLBY_TRUEHD -> {
          val syncframeOffset = Ac3Util.findTrueHdSyncframeOffset(buffer)
          if (syncframeOffset == C.INDEX_UNSET) 0
          else Ac3Util.parseTrueHdSyncframeAudioSampleCount(buffer, syncframeOffset) * Ac3Util.TRUEHD_RECHUNK_SAMPLE_COUNT
        }
        C.ENCODING_PCM_16BIT,
        C.ENCODING_PCM_16BIT_BIG_ENDIAN,
        C.ENCODING_PCM_24BIT,
        C.ENCODING_PCM_32BIT,
        C.ENCODING_PCM_8BIT,
        C.ENCODING_PCM_FLOAT,
        C.ENCODING_AAC_ER_BSAC,
        C.ENCODING_INVALID,
        Format.NO_VALUE -> throw IllegalStateException("Unexpected audio encoding: $encoding")
        else -> throw IllegalStateException("Unexpected audio encoding: $encoding")
      }
    }

    @RequiresApi(21)
    private fun writeNonBlockingV21(audioTrack: AudioTrack, buffer: ByteBuffer, size: Int): Int {
      return audioTrack.write(buffer, size, AudioTrack.WRITE_NON_BLOCKING)
    }

    @RequiresApi(21)
    private fun writeNonBlockingWithAvSyncV21(
      audioTrack: AudioTrack,
      buffer: ByteBuffer,
      size: Int,
      presentationTimeUs: Long
    ): Int {
      if (Util.SDK_INT >= 26) {
        return audioTrack.write(
          buffer, size, AudioTrack.WRITE_NON_BLOCKING, presentationTimeUs * 1000
        )
      }
      // Handled in instance or helper method
      return writeNonBlockingV21(audioTrack, buffer, size)
    }

    @RequiresApi(21)
    private fun setVolumeInternalV21(audioTrack: AudioTrack, volume: Float) {
      audioTrack.setVolume(volume)
    }

    @Suppress("DEPRECATION")
    private fun setVolumeInternalV3(audioTrack: AudioTrack, volume: Float) {
      audioTrack.setStereoVolume(volume, volume)
    }

    private fun releaseAudioTrackAsync(
      audioTrack: AudioTrack,
      releasedConditionVariable: ConditionVariable
    ) {
      releasedConditionVariable.close()
      synchronized(releaseExecutorLock) {
        if (releaseExecutor == null) {
          releaseExecutor = Util.newSingleThreadExecutor("ExoPlayer:AudioTrackReleaseThread")
        }
        pendingReleaseCount++
        releaseExecutor!!.execute {
          try {
            audioTrack.flush()
            audioTrack.release()
          } finally {
            releasedConditionVariable.open()
            synchronized(releaseExecutorLock) {
              pendingReleaseCount--
              if (pendingReleaseCount == 0) {
                releaseExecutor!!.shutdown()
                releaseExecutor = null
              }
            }
          }
        }
      }
    }
  }
}
