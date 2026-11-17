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
import android.graphics.Point
import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecInfo.VideoCapabilities
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint
import androidx.annotation.DoNotInline
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_AUDIO_CHANNEL_COUNT_CHANGED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_AUDIO_ENCODING_CHANGED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_AUDIO_SAMPLE_RATE_CHANGED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_INITIALIZATION_DATA_CHANGED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_MIME_TYPE_CHANGED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_VIDEO_RESOLUTION_CHANGED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_VIDEO_ROTATION_CHANGED
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.DISCARD_REASON_WORKAROUND
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.REUSE_RESULT_NO
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.REUSE_RESULT_YES_WITH_FLUSH
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.Companion.REUSE_RESULT_YES_WITH_RECONFIGURATION
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import kotlin.annotation.MustBeDocumented

/** Information about a [MediaCodec] for a given MIME type. */
class MediaCodecInfo @VisibleForTesting internal constructor(
  /**
   * The name of the decoder.
   *
   * <p>May be passed to [MediaCodec.createByCodecName] to create an instance of the decoder.
   */
  @JvmField val name: String,
  /** The MIME type handled by the codec. */
  @JvmField val mimeType: String,
  /**
   * The MIME type that the codec uses for media of type [mimeType]. Equal to [mimeType] unless the
   * codec is known to use a non-standard MIME type alias.
   */
  @JvmField val codecMimeType: String,
  /**
   * The capabilities of the decoder, like the profiles/levels it supports, or `null` if not known.
   */
  @JvmField val capabilities: CodecCapabilities?,
  /**
   * Whether the codec is hardware accelerated.
   *
   * <p>This could be an approximation as the exact information is only provided in API levels 29+.
   *
   * @see android.media.MediaCodecInfo.isHardwareAccelerated
   */
  @JvmField val hardwareAccelerated: Boolean,
  /**
   * Whether the codec is software only.
   *
   * <p>This could be an approximation as the exact information is only provided in API levels 29+.
   *
   * @see android.media.MediaCodecInfo.isSoftwareOnly
   */
  @JvmField val softwareOnly: Boolean,
  /**
   * Whether the codec is from the vendor.
   *
   * <p>This could be an approximation as the exact information is only provided in API levels 29+.
   *
   * @see android.media.MediaCodecInfo.isVendor
   */
  @JvmField val vendor: Boolean,
  /**
   * Whether the decoder supports seamless resolution switches.
   *
   * @see CodecCapabilities.isFeatureSupported
   * @see CodecCapabilities.FEATURE_AdaptivePlayback
   */
  @JvmField val adaptive: Boolean,
  /**
   * Whether the decoder supports tunneling.
   *
   * @see CodecCapabilities.isFeatureSupported
   * @see CodecCapabilities.FEATURE_TunneledPlayback
   */
  @JvmField val tunneling: Boolean,
  /**
   * Whether the decoder is secure.
   *
   * @see CodecCapabilities.isFeatureSupported
   * @see CodecCapabilities.FEATURE_SecurePlayback
   */
  @JvmField val secure: Boolean,
) {

  private val isVideo: Boolean = MimeTypes.isVideo(mimeType)

  init {
    Assertions.checkNotNull(name)
  }

  override fun toString(): String {
    return name
  }

  /**
   * The profile levels supported by the decoder.
   *
   * @return The profile levels supported by the decoder.
   */
  fun getProfileLevels(): Array<CodecProfileLevel> {
    return capabilities?.profileLevels ?: arrayOf()
  }

  /**
   * Returns an upper bound on the maximum number of supported instances, or [ ] if unknown. Applications should not expect to operate more
   * instances than the returned maximum.
   *
   * @see CodecCapabilities.getMaxSupportedInstances
   */
  @SuppressLint("NewApi")
  fun getMaxSupportedInstances(): Int {
    if (Util.SDK_INT < 23 || capabilities == null) {
      return MAX_SUPPORTED_INSTANCES_UNKNOWN
    }
    return getMaxSupportedInstancesV23(capabilities)
  }

  /**
   * Returns whether the decoder may support decoding the given `format`.
   *
   * @param format The input media format.
   * @return Whether the decoder may support decoding the given `format`.
   * @throws MediaCodecUtil.DecoderQueryException Thrown if an error occurs while querying decoders.
   */
  @Throws(MediaCodecUtil.DecoderQueryException::class)
  @SuppressLint("NewApi")
  fun isFormatSupported(format: Format): Boolean {
    if (!isSampleMimeTypeSupported(format)) {
      return false
    }

    if (!isCodecProfileAndLevelSupported(format)) {
      return false
    }

    if (isVideo) {
      if (format.width <= 0 || format.height <= 0) {
        return true
      }
      if (Util.SDK_INT >= 21) {
        return isVideoSizeAndRateSupportedV21(format.width, format.height, format.frameRate.toDouble())
      } else {
        val isFormatSupported =
          format.width * format.height <= MediaCodecUtil.maxH264DecodableFrameSize()
        if (!isFormatSupported) {
          logNoSupport("legacyFrameSize, " + format.width + "x" + format.height)
        }
        return isFormatSupported
      }
    } else { // Audio
      return Util.SDK_INT < 21 ||
        ((format.sampleRate == Format.NO_VALUE ||
          isAudioSampleRateSupportedV21(format.sampleRate)) &&
          (format.channelCount == Format.NO_VALUE ||
            isAudioChannelCountSupportedV21(format.channelCount)))
    }
  }

  private fun isSampleMimeTypeSupported(format: Format): Boolean {
    return mimeType == format.sampleMimeType ||
      mimeType == MediaCodecUtil.getAlternativeCodecMimeType(format)
  }

  @SuppressLint("NewApi")
  private fun isCodecProfileAndLevelSupported(format: Format): Boolean {
    if (format.codecs == null) {
      return true
    }
    val codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(format) ?: return true
    var profile = codecProfileAndLevel.first
    var level = codecProfileAndLevel.second
    if (MimeTypes.VIDEO_DOLBY_VISION == format.sampleMimeType) {
      // If this codec is H264 or H265, we only support the Dolby Vision base layer and need to map
      // the Dolby Vision profile to the corresponding base layer profile. Also assume all levels of
      // this base layer profile are supported.
      if (MimeTypes.VIDEO_H264 == mimeType) {
        profile = CodecProfileLevel.AVCProfileHigh
        level = 0
      } else if (MimeTypes.VIDEO_H265 == mimeType) {
        profile = CodecProfileLevel.HEVCProfileMain10
        level = 0
      }
    }

    if (!isVideo && profile != CodecProfileLevel.AACObjectXHE) {
      // Some devices/builds underreport audio capabilities, so assume support except for xHE-AAC
      // which may not be widely supported. See https://github.com/google/ExoPlayer/issues/5145.
      return true
    }

    var profileLevels = getProfileLevels()
    if (Util.SDK_INT <= 23 && MimeTypes.VIDEO_VP9 == mimeType && profileLevels.isEmpty()) {
      // Some older devices don't report profile levels for VP9. Estimate them using other data in
      // the codec capabilities.
      profileLevels = estimateLegacyVp9ProfileLevels(capabilities)
    }

    for (profileLevel in profileLevels) {
      if (profileLevel.profile == profile &&
        profileLevel.level >= level &&
        !needsProfileExcludedWorkaround(mimeType, profile)
      ) {
        return true
      }
    }
    logNoSupport("codec.profileLevel, " + format.codecs + ", " + codecMimeType)
    return false
  }

  /** Whether the codec handles HDR10+ out-of-band metadata. */
  fun isHdr10PlusOutOfBandMetadataSupported(): Boolean {
    if (Util.SDK_INT >= 29 && MimeTypes.VIDEO_VP9 == mimeType) {
      for (capabilities in getProfileLevels()) {
        if (capabilities.profile == CodecProfileLevel.VP9Profile2HDR10Plus) {
          return true
        }
      }
    }
    return false
  }

  /**
   * Returns whether it may be possible to adapt an instance of this decoder to playing a different
   * format when the codec is configured to play media in the specified `format`.
   *
   * <p>For adaptation to succeed, the codec must also be configured with appropriate maximum values
   * and [isSeamlessAdaptationSupported] must return `true` for the old/new formats.
   *
   * @param format The format of media for which the decoder will be configured.
   * @return Whether adaptation may be possible
   */
  @SuppressLint("NewApi")
  fun isSeamlessAdaptationSupported(format: Format): Boolean {
    if (isVideo) {
      return adaptive
    } else {
      val profileLevel = MediaCodecUtil.getCodecProfileAndLevel(format)
      return profileLevel != null && profileLevel.first == CodecProfileLevel.AACObjectXHE
    }
  }

  /**
   * Returns whether it is possible to adapt an instance of this decoder seamlessly from `oldFormat` to `newFormat`. If `newFormat` may not be completely populated, pass
   * `false` for `isNewFormatComplete`.
   *
   * <p>For adaptation to succeed, the codec must also be configured with maximum values that are
   * compatible with the new format.
   *
   * @param oldFormat The format being decoded.
   * @param newFormat The new format.
   * @param isNewFormatComplete Whether `newFormat` is populated with format-specific
   *     metadata.
   * @return Whether it is possible to adapt the decoder seamlessly.
   * @deprecated Use [canReuseCodec].
   */
  @Deprecated("Use canReuseCodec.")
  fun isSeamlessAdaptationSupported(
    oldFormat: Format,
    newFormat: Format,
    isNewFormatComplete: Boolean,
  ): Boolean {
    var updatedNewFormat = newFormat
    if (!isNewFormatComplete && oldFormat.colorInfo != null && updatedNewFormat.colorInfo == null) {
      updatedNewFormat = updatedNewFormat.buildUpon().setColorInfo(oldFormat.colorInfo).build()
    }
    val reuseResult = canReuseCodec(oldFormat, updatedNewFormat).result
    return reuseResult == REUSE_RESULT_YES_WITH_RECONFIGURATION ||
      reuseResult == REUSE_RESULT_YES_WITHOUT_RECONFIGURATION
  }

  /**
   * Evaluates whether it's possible to reuse an instance of this decoder that's currently decoding
   * `oldFormat` to decode `newFormat` instead.
   *
   * <p>For adaptation to succeed, the codec must also be configured with maximum values that are
   * compatible with the new format.
   *
   * @param oldFormat The format being decoded.
   * @param newFormat The new format.
   * @return The result of the evaluation.
   */
  fun canReuseCodec(oldFormat: Format, newFormat: Format): DecoderReuseEvaluation {
    var discardReasons = 0
    if (!Util.areEqual(oldFormat.sampleMimeType, newFormat.sampleMimeType)) {
      discardReasons = discardReasons or DISCARD_REASON_MIME_TYPE_CHANGED
    }

    if (isVideo) {
      if (oldFormat.rotationDegrees != newFormat.rotationDegrees) {
        discardReasons = discardReasons or DISCARD_REASON_VIDEO_ROTATION_CHANGED
      }
      if (!adaptive &&
        (oldFormat.width != newFormat.width || oldFormat.height != newFormat.height)
      ) {
        discardReasons = discardReasons or DISCARD_REASON_VIDEO_RESOLUTION_CHANGED
      }
      if (!Util.areEqual(oldFormat.colorInfo, newFormat.colorInfo)) {
        discardReasons = discardReasons or DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED
      }
      if (needsAdaptationReconfigureWorkaround(name) &&
        !oldFormat.initializationDataEquals(newFormat)
      ) {
        discardReasons = discardReasons or DISCARD_REASON_WORKAROUND
      }

      if (discardReasons == 0) {
        return DecoderReuseEvaluation(
          name,
          oldFormat,
          newFormat,
          if (oldFormat.initializationDataEquals(newFormat)) {
            REUSE_RESULT_YES_WITHOUT_RECONFIGURATION
          } else {
            REUSE_RESULT_YES_WITH_RECONFIGURATION
          },
          /* discardReasons= */ 0,
        )
      }
    } else {
      if (oldFormat.channelCount != newFormat.channelCount) {
        discardReasons = discardReasons or DISCARD_REASON_AUDIO_CHANNEL_COUNT_CHANGED
      }
      if (oldFormat.sampleRate != newFormat.sampleRate) {
        discardReasons = discardReasons or DISCARD_REASON_AUDIO_SAMPLE_RATE_CHANGED
      }
      if (oldFormat.pcmEncoding != newFormat.pcmEncoding) {
        discardReasons = discardReasons or DISCARD_REASON_AUDIO_ENCODING_CHANGED
      }

      // Check whether we're adapting between two xHE-AAC formats, for which adaptation is possible
      // without reconfiguration or flushing.
      if (discardReasons == 0 && MimeTypes.AUDIO_AAC == mimeType) {
        val oldCodecProfileLevel = MediaCodecUtil.getCodecProfileAndLevel(oldFormat)
        val newCodecProfileLevel = MediaCodecUtil.getCodecProfileAndLevel(newFormat)
        if (oldCodecProfileLevel != null && newCodecProfileLevel != null) {
          val oldProfile = oldCodecProfileLevel.first
          val newProfile = newCodecProfileLevel.first
          if (oldProfile == CodecProfileLevel.AACObjectXHE &&
            newProfile == CodecProfileLevel.AACObjectXHE
          ) {
            return DecoderReuseEvaluation(
              name,
              oldFormat,
              newFormat,
              REUSE_RESULT_YES_WITHOUT_RECONFIGURATION,
              /* discardReasons= */ 0,
            )
          }
        }
      }

      if (!oldFormat.initializationDataEquals(newFormat)) {
        discardReasons = discardReasons or DISCARD_REASON_INITIALIZATION_DATA_CHANGED
      }
      if (needsAdaptationFlushWorkaround(mimeType)) {
        discardReasons = discardReasons or DISCARD_REASON_WORKAROUND
      }

      if (discardReasons == 0) {
        return DecoderReuseEvaluation(
          name, oldFormat, newFormat, REUSE_RESULT_YES_WITH_FLUSH, /* discardReasons= */ 0,
        )
      }
    }

    return DecoderReuseEvaluation(name, oldFormat, newFormat, REUSE_RESULT_NO, discardReasons)
  }

  /**
   * Whether the decoder supports video with a given width, height and frame rate.
   *
   * @param width Width in pixels.
   * @param height Height in pixels.
   * @param frameRate Optional frame rate in frames per second. Ignored if set to [ ] or any value less than or equal to 0.
   * @return Whether the decoder supports video with the given width, height and frame rate.
   */
  @RequiresApi(21)
  @SuppressLint("NewApi")
  fun isVideoSizeAndRateSupportedV21(width: Int, height: Int, frameRate: Double): Boolean {
    if (capabilities == null) {
      logNoSupport("sizeAndRate.caps")
      return false
    }
    val videoCapabilities = capabilities.videoCapabilities
    if (videoCapabilities == null) {
      logNoSupport("sizeAndRate.vCaps")
      return false
    }

    if (Util.SDK_INT >= 29) {
      val evaluation =
        Api29.areResolutionAndFrameRateCovered(videoCapabilities, width, height, frameRate)
      if (evaluation == COVERAGE_RESULT_YES) {
        return true
      } else if (evaluation == COVERAGE_RESULT_NO) {
        logNoSupport("sizeAndRate.cover, " + width + "x" + height + "@" + frameRate)
        return false
      }
      // COVERAGE_RESULT_NO_EMPTY_LIST falls through to API 21+ code below
    }

    if (!areSizeAndRateSupportedV21(videoCapabilities, width, height, frameRate)) {
      if (width >= height ||
        !needsRotatedVerticalResolutionWorkaround(name) ||
        !areSizeAndRateSupportedV21(videoCapabilities, height, width, frameRate)
      ) {
        logNoSupport("sizeAndRate.support, " + width + "x" + height + "@" + frameRate)
        return false
      }
      logAssumedSupport("sizeAndRate.rotated, " + width + "x" + height + "@" + frameRate)
    }
    return true
  }

  /**
   * Returns the smallest video size greater than or equal to a specified size that also satisfies
   * the [MediaCodec]'s width and height alignment requirements.
   *
   * <p>Must not be called if the device SDK version is less than 21.
   *
   * @param width Width in pixels.
   * @param height Height in pixels.
   * @return The smallest video size greater than or equal to the specified size that also satisfies
   *     the [MediaCodec]'s width and height alignment requirements, or null if not a video
   *     codec.
   */
  @RequiresApi(21)
  @SuppressLint("NewApi")
  fun alignVideoSizeV21(width: Int, height: Int): Point? {
    if (capabilities == null) {
      return null
    }
    val videoCapabilities = capabilities.videoCapabilities ?: return null
    return alignVideoSizeV21(videoCapabilities, width, height)
  }

  /**
   * Whether the decoder supports audio with a given sample rate.
   *
   * <p>Must not be called if the device SDK version is less than 21.
   *
   * @param sampleRate The sample rate in Hz.
   * @return Whether the decoder supports audio with the given sample rate.
   */
  @RequiresApi(21)
  @SuppressLint("NewApi")
  fun isAudioSampleRateSupportedV21(sampleRate: Int): Boolean {
    if (capabilities == null) {
      logNoSupport("sampleRate.caps")
      return false
    }
    val audioCapabilities = capabilities.audioCapabilities
    if (audioCapabilities == null) {
      logNoSupport("sampleRate.aCaps")
      return false
    }
    if (!audioCapabilities.isSampleRateSupported(sampleRate)) {
      logNoSupport("sampleRate.support, " + sampleRate)
      return false
    }
    return true
  }

  /**
   * Whether the decoder supports audio with a given channel count.
   *
   * <p>Must not be called if the device SDK version is less than 21.
   *
   * @param channelCount The channel count.
   * @return Whether the decoder supports audio with the given channel count.
   */
  @RequiresApi(21)
  @SuppressLint("NewApi")
  fun isAudioChannelCountSupportedV21(channelCount: Int): Boolean {
    if (capabilities == null) {
      logNoSupport("channelCount.caps")
      return false
    }
    val audioCapabilities = capabilities.audioCapabilities
    if (audioCapabilities == null) {
      logNoSupport("channelCount.aCaps")
      return false
    }
    val maxInputChannelCount =
      adjustMaxInputChannelCount(name, mimeType, audioCapabilities.maxInputChannelCount)
    if (maxInputChannelCount < channelCount) {
      logNoSupport("channelCount.support, " + channelCount)
      return false
    }
    return true
  }

  private fun logNoSupport(message: String) {
    Log.d(
      TAG,
      "NoSupport [" +
        message +
        "] [" +
        name +
        ", " +
        mimeType +
        "] [" +
        Util.DEVICE_DEBUG_INFO +
        "]",
    )
  }

  private fun logAssumedSupport(message: String) {
    Log.d(
      TAG,
      "AssumedSupport [" +
        message +
        "] [" +
        name +
        ", " +
        mimeType +
        "] [" +
        Util.DEVICE_DEBUG_INFO +
        "]",
    )
  }

  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
  @IntDef(COVERAGE_RESULT_YES, COVERAGE_RESULT_NO, COVERAGE_RESULT_NO_EMPTY_LIST)
  private annotation class PerformancePointCoverageResult

  @RequiresApi(29)
  private object Api29 {
    @DoNotInline
    fun areResolutionAndFrameRateCovered(
      videoCapabilities: VideoCapabilities,
      width: Int,
      height: Int,
      frameRate: Double,
    ): @PerformancePointCoverageResult Int {
      val performancePointList = videoCapabilities.supportedPerformancePoints
      if (performancePointList.isNullOrEmpty()) {
        return COVERAGE_RESULT_NO_EMPTY_LIST
      }

      val targetPerformancePoint = PerformancePoint(width, height, frameRate.toInt())
      for (i in performancePointList.indices) {
        if (performancePointList[i].covers(targetPerformancePoint)) {
          return COVERAGE_RESULT_YES
        }
      }
      return COVERAGE_RESULT_NO
    }
  }

  companion object {
    const val TAG = "MediaCodecInfo"

    /**
     * The value returned by [getMaxSupportedInstances] if the upper bound on the maximum number
     * of supported instances is unknown.
     */
    const val MAX_SUPPORTED_INSTANCES_UNKNOWN = -1

    private const val COVERAGE_RESULT_YES = 2
    private const val COVERAGE_RESULT_NO = 1
    private const val COVERAGE_RESULT_NO_EMPTY_LIST = 0

    /**
     * Creates an instance.
     *
     * @param name The name of the [MediaCodec].
     * @param mimeType A mime type supported by the [MediaCodec].
     * @param codecMimeType The MIME type that the codec uses for media of type `#mimeType`.
     *     Equal to `mimeType` unless the codec is known to use a non-standard MIME type alias.
     * @param capabilities The capabilities of the [MediaCodec] for the specified mime type, or
     *     `null` if not known.
     * @param hardwareAccelerated Whether the [MediaCodec] is hardware accelerated.
     * @param softwareOnly Whether the [MediaCodec] is software only.
     * @param vendor Whether the [MediaCodec] is provided by the vendor.
     * @param forceDisableAdaptive Whether [adaptive] should be forced to `false`.
     * @param forceSecure Whether [secure] should be forced to `true`.
     * @return The created instance.
     */
    @JvmStatic
    @SuppressLint("NewApi")
    fun newInstance(
      name: String,
      mimeType: String,
      codecMimeType: String,
      capabilities: CodecCapabilities?,
      hardwareAccelerated: Boolean,
      softwareOnly: Boolean,
      vendor: Boolean,
      forceDisableAdaptive: Boolean,
      forceSecure: Boolean,
    ): MediaCodecInfo {
      return MediaCodecInfo(
        name,
        mimeType,
        codecMimeType,
        capabilities,
        hardwareAccelerated,
        softwareOnly,
        vendor,
        /* adaptive= */ !forceDisableAdaptive &&
          capabilities != null &&
          isAdaptive(capabilities) &&
          !needsDisableAdaptationWorkaround(name),
        /* tunneling= */ capabilities != null && isTunneling(capabilities),
        /* secure= */ forceSecure || (capabilities != null && isSecure(capabilities)),
      )
    }

    private fun adjustMaxInputChannelCount(
      name: String,
      mimeType: String?,
      maxChannelCount: Int,
    ): Int {
      if (maxChannelCount > 1 || (Util.SDK_INT >= 26 && maxChannelCount > 0)) {
        // The maximum channel count looks like it's been set correctly.
        return maxChannelCount
      }
      if (MimeTypes.AUDIO_MPEG == mimeType ||
        MimeTypes.AUDIO_AMR_NB == mimeType ||
        MimeTypes.AUDIO_AMR_WB == mimeType ||
        MimeTypes.AUDIO_AAC == mimeType ||
        MimeTypes.AUDIO_VORBIS == mimeType ||
        MimeTypes.AUDIO_OPUS == mimeType ||
        MimeTypes.AUDIO_RAW == mimeType ||
        MimeTypes.AUDIO_FLAC == mimeType ||
        MimeTypes.AUDIO_ALAW == mimeType ||
        MimeTypes.AUDIO_MLAW == mimeType ||
        MimeTypes.AUDIO_MSGSM == mimeType
      ) {
        // Platform code should have set a default.
        return maxChannelCount
      }
      // The maximum channel count looks incorrect. Adjust it to an assumed default.
      val assumedMaxChannelCount: Int =
        if (MimeTypes.AUDIO_AC3 == mimeType) {
          6
        } else if (MimeTypes.AUDIO_E_AC3 == mimeType) {
          16
        } else {
          // Default to the platform limit, which is 30.
          30
        }
      Log.w(
        TAG,
        "AssumedMaxChannelAdjustment: " +
          name +
          ", [" +
          maxChannelCount +
          " to " +
          assumedMaxChannelCount +
          "]",
      )
      return assumedMaxChannelCount
    }

    @SuppressLint("NewApi")
    private fun isAdaptive(capabilities: CodecCapabilities): Boolean {
      return Util.SDK_INT >= 19 && isAdaptiveV19(capabilities)
    }

    @RequiresApi(19)
    @DoNotInline
    private fun isAdaptiveV19(capabilities: CodecCapabilities): Boolean {
      return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback)
    }

    @SuppressLint("NewApi")
    private fun isTunneling(capabilities: CodecCapabilities): Boolean {
      return Util.SDK_INT >= 21 && isTunnelingV21(capabilities)
    }

    @RequiresApi(21)
    @DoNotInline
    private fun isTunnelingV21(capabilities: CodecCapabilities): Boolean {
      return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_TunneledPlayback)
    }

    @SuppressLint("NewApi")
    private fun isSecure(capabilities: CodecCapabilities): Boolean {
      return Util.SDK_INT >= 21 && isSecureV21(capabilities)
    }

    @RequiresApi(21)
    @DoNotInline
    private fun isSecureV21(capabilities: CodecCapabilities): Boolean {
      return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback)
    }

    @RequiresApi(21)
    @DoNotInline
    @SuppressLint("NewApi")
    private fun areSizeAndRateSupportedV21(
      capabilities: VideoCapabilities,
      width: Int,
      height: Int,
      frameRate: Double,
    ): Boolean {
      var alignedWidth = width
      var alignedHeight = height
      // Don't ever fail due to alignment. See: https://github.com/google/ExoPlayer/issues/6551.
      val alignedSize = alignVideoSizeV21(capabilities, alignedWidth, alignedHeight)
      alignedWidth = alignedSize.x
      alignedHeight = alignedSize.y

      // VideoCapabilities.areSizeAndRateSupported incorrectly returns false if frameRate < 1 on some
      // versions of Android, so we only check the size in this case [Internal ref: b/153940404].
      if (frameRate == Format.NO_VALUE.toDouble() || frameRate < 1) {
        return capabilities.isSizeSupported(alignedWidth, alignedHeight)
      } else {
        // The signaled frame rate may be slightly higher than the actual frame rate, so we take the
        // floor to avoid situations where a range check in areSizeAndRateSupported fails due to
        // slightly exceeding the limits for a standard format (e.g., 1080p at 30 fps).
        val floorFrameRate = Math.floor(frameRate)
        return capabilities.areSizeAndRateSupported(alignedWidth, alignedHeight, floorFrameRate)
      }
    }

    @RequiresApi(21)
    @DoNotInline
    private fun alignVideoSizeV21(
      capabilities: VideoCapabilities,
      width: Int,
      height: Int,
    ): Point {
      val widthAlignment = capabilities.widthAlignment
      val heightAlignment = capabilities.heightAlignment
      return Point(
        Util.ceilDivide(width, widthAlignment) * widthAlignment,
        Util.ceilDivide(height, heightAlignment) * heightAlignment,
      )
    }

    @RequiresApi(23)
    @DoNotInline
    private fun getMaxSupportedInstancesV23(capabilities: CodecCapabilities): Int {
      return capabilities.maxSupportedInstances
    }

    /**
     * Called on devices with [Util.SDK_INT] 23 and below, for VP9 decoders whose [ ] do not correctly report profile levels. The returned [ ]s are estimated based on other data in the [ ].
     *
     * @param capabilities The [CodecCapabilities] for a VP9 decoder, or `null` if not
     *     known.
     * @return The estimated [CodecProfileLevel]s for the decoder.
     */
    @SuppressLint("NewApi")
    private fun estimateLegacyVp9ProfileLevels(
      capabilities: CodecCapabilities?,
    ): Array<CodecProfileLevel> {
      var maxBitrate = 0
      if (capabilities != null) {
        val videoCapabilities = capabilities.videoCapabilities
        if (videoCapabilities != null) {
          maxBitrate = videoCapabilities.bitrateRange.upper
        }
      }

      // Values taken from https://www.webmproject.org/vp9/levels.
      val level: Int =
        if (maxBitrate >= 180_000_000) {
          CodecProfileLevel.VP9Level52
        } else if (maxBitrate >= 120_000_000) {
          CodecProfileLevel.VP9Level51
        } else if (maxBitrate >= 60_000_000) {
          CodecProfileLevel.VP9Level5
        } else if (maxBitrate >= 30_000_000) {
          CodecProfileLevel.VP9Level41
        } else if (maxBitrate >= 18_000_000) {
          CodecProfileLevel.VP9Level4
        } else if (maxBitrate >= 12_000_000) {
          CodecProfileLevel.VP9Level31
        } else if (maxBitrate >= 7_200_000) {
          CodecProfileLevel.VP9Level3
        } else if (maxBitrate >= 3_600_000) {
          CodecProfileLevel.VP9Level21
        } else if (maxBitrate >= 1_800_000) {
          CodecProfileLevel.VP9Level2
        } else if (maxBitrate >= 800_000) {
          CodecProfileLevel.VP9Level11
        } else { // Assume level 1 is always supported.
          CodecProfileLevel.VP9Level1
        }

      val profileLevel = CodecProfileLevel()
      // Since this method is for legacy devices only, assume that only profile 0 is supported.
      profileLevel.profile = CodecProfileLevel.VP9Profile0
      profileLevel.level = level

      return arrayOf(profileLevel)
    }

    /**
     * Returns whether the decoder is known to fail when adapting, despite advertising itself as an
     * adaptive decoder.
     *
     * @param name The decoder name.
     * @return True if the decoder is known to fail when adapting.
     */
    private fun needsDisableAdaptationWorkaround(name: String): Boolean {
      return Util.SDK_INT <= 22 &&
        ("ODROID-XU3" == Util.MODEL || "Nexus 10" == Util.MODEL) &&
        ("OMX.Exynos.AVC.Decoder" == name || "OMX.Exynos.AVC.Decoder.secure" == name)
    }

    /**
     * Returns whether the decoder is known to fail when an attempt is made to reconfigure it with a
     * new format's configuration data.
     *
     * @param name The name of the decoder.
     * @return Whether the decoder is known to fail when an attempt is made to reconfigure it with a
     *     new format's configuration data.
     */
    private fun needsAdaptationReconfigureWorkaround(name: String): Boolean {
      return Util.MODEL.startsWith("SM-T230") && "OMX.MARVELL.VIDEO.HW.CODA7542DECODER" == name
    }

    /**
     * Returns whether the decoder is known to behave incorrectly if flushed to adapt to a new format.
     *
     * @param mimeType The name of the MIME type.
     * @return Whether the decoder is known to to behave incorrectly if flushed to adapt to a new
     *     format.
     */
    private fun needsAdaptationFlushWorkaround(mimeType: String?): Boolean {
      // For Opus, we don't flush and reuse the codec because the decoder may discard samples after
      // flushing, which would result in audio being dropped just after a stream change (see
      // [Internal: b/143450854]). For other formats, we allow reuse after flushing if the codec
      // initialization data is unchanged.
      return MimeTypes.AUDIO_OPUS == mimeType
    }

    /**
     * Capabilities are known to be inaccurately reported for vertical resolutions on some devices.
     * [Internal ref: b/31387661]. When this workaround is enabled, we also check whether the
     * capabilities indicate support if the width and height are swapped. If they do, we assume that
     * the vertical resolution is also supported.
     *
     * @param name The name of the codec.
     * @return Whether to enable the workaround.
     */
    private fun needsRotatedVerticalResolutionWorkaround(name: String): Boolean {
      if ("OMX.MTK.VIDEO.DECODER.HEVC" == name && "mcv5a" == Util.DEVICE) {
        // See https://github.com/google/ExoPlayer/issues/6612.
        return false
      }
      return true
    }

    /**
     * Whether a profile is excluded from the list of supported profiles. This may happen when a
     * device declares support for a profile it doesn't actually support.
     */
    @SuppressLint("NewApi")
    private fun needsProfileExcludedWorkaround(mimeType: String?, profile: Int): Boolean {
      // See https://github.com/google/ExoPlayer/issues/3537
      return MimeTypes.VIDEO_H265 == mimeType &&
        CodecProfileLevel.HEVCProfileMain10 == profile &&
        ("sailfish" == Util.DEVICE || "marlin" == Util.DEVICE)
    }
  }
}
