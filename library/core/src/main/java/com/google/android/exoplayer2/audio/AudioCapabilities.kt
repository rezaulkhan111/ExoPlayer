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
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.provider.Settings.Global
import android.util.Pair
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.primitives.Ints
import java.util.Arrays

/** Represents the set of audio formats that a device is capable of playing. */
class AudioCapabilities {

  private val supportedEncodings: IntArray
  val maxChannelCount: Int

  /**
   * Constructs new audio capabilities based on a set of supported encodings and a maximum channel
   * count.
   *
   * Applications should generally call [getCapabilities] to obtain an instance
   * based on the capabilities advertised by the platform, rather than calling this constructor.
   *
   * @param supportedEncodings Supported audio encodings from [AudioFormat]'s
   *     `ENCODING_*` constants. Passing `null` indicates that no encodings are
   *     supported.
   * @param maxChannelCount The maximum number of audio channels that can be played simultaneously.
   */
  constructor(supportedEncodings: IntArray?, maxChannelCount: Int) {
    if (supportedEncodings != null) {
      this.supportedEncodings = supportedEncodings.copyOf(supportedEncodings.size)
      this.supportedEncodings.sort()
    } else {
      this.supportedEncodings = intArrayOf()
    }
    this.maxChannelCount = maxChannelCount
  }

  /**
   * Returns whether this device supports playback of the specified audio `encoding`.
   *
   * @param encoding One of [C.Encoding]'s `ENCODING_*` constants.
   * @return Whether this device supports playback the specified audio `encoding`.
   */
  fun supportsEncoding(encoding: Int): Boolean {
    return Arrays.binarySearch(supportedEncodings, encoding) >= 0
  }

  /** Returns whether the device can do passthrough playback for `format`. */
  fun isPassthroughPlaybackSupported(format: Format): Boolean {
    return getEncodingAndChannelConfigForPassthrough(format) != null
  }

  /**
   * Returns the encoding and channel config to use when configuring an [AudioTrack] in
   * passthrough mode for the specified [Format]. Returns `null` if passthrough of the
   * format is unsupported.
   *
   * @param format The [Format].
   * @return The encoding and channel config to use, or `null` if passthrough of the format is
   *     unsupported.
   */
  fun getEncodingAndChannelConfigForPassthrough(format: Format): Pair<Int, Int>? {
    var encoding = MimeTypes.getEncoding(checkNotNull(format.sampleMimeType), format.codecs)
    if (!ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS.containsKey(encoding)) {
      return null
    }

    if (encoding == C.ENCODING_E_AC3_JOC && !supportsEncoding(C.ENCODING_E_AC3_JOC)) {
      encoding = C.ENCODING_E_AC3
    } else if (encoding == C.ENCODING_DTS_HD && !supportsEncoding(C.ENCODING_DTS_HD)) {
      encoding = C.ENCODING_DTS
    }
    if (!supportsEncoding(encoding)) {
      return null
    }
    val channelCount: Int
    if (format.channelCount == Format.NO_VALUE || encoding == C.ENCODING_E_AC3_JOC) {
      val sampleRate =
        if (format.sampleRate != Format.NO_VALUE) format.sampleRate else DEFAULT_SAMPLE_RATE_HZ
      channelCount = getMaxSupportedChannelCountForPassthrough(encoding, sampleRate)
    } else {
      channelCount = format.channelCount
      if (channelCount > maxChannelCount) {
        return null
      }
    }
    val channelConfig = getChannelConfigForPassthrough(channelCount)
    if (channelConfig == AudioFormat.CHANNEL_INVALID) {
      return null
    }
    return Pair.create(encoding, channelConfig)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is AudioCapabilities) {
      return false
    }
    return supportedEncodings.contentEquals(other.supportedEncodings)
      && maxChannelCount == other.maxChannelCount
  }

  override fun hashCode(): Int {
    return maxChannelCount + 31 * supportedEncodings.contentHashCode()
  }

  override fun toString(): String {
    return "AudioCapabilities[maxChannelCount=" +
      maxChannelCount +
      ", supportedEncodings=" +
      supportedEncodings.contentToString() +
      "]"
  }

  @RequiresApi(29)
  private object Api29 {
    private val DEFAULT_AUDIO_ATTRIBUTES =
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .setFlags(0)
        .build()

    @DoNotInline
    @JvmStatic
    @Suppress("WrongConstant")
    fun getDirectPlaybackSupportedEncodings(): IntArray {
      val supportedEncodingsListBuilder = ImmutableList.builder<Int>()
      for (encoding in ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS.keys) {
        if (AudioTrack.isDirectPlaybackSupported(
            AudioFormat.Builder()
              .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
              .setEncoding(encoding)
              .setSampleRate(DEFAULT_SAMPLE_RATE_HZ)
              .build(),
            DEFAULT_AUDIO_ATTRIBUTES
          )
        ) {
          supportedEncodingsListBuilder.add(encoding)
        }
      }
      supportedEncodingsListBuilder.add(AudioFormat.ENCODING_PCM_16BIT)
      return Ints.toArray(supportedEncodingsListBuilder.build())
    }

    @DoNotInline
    @JvmStatic
    @Suppress("WrongConstant")
    fun getMaxSupportedChannelCountForPassthrough(
      encoding: Int, sampleRate: Int
    ): Int {
      for (channelCount in DEFAULT_MAX_CHANNEL_COUNT downTo 1) {
        val audioFormat =
          AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(Util.getAudioTrackChannelConfig(channelCount))
            .build()
        if (AudioTrack.isDirectPlaybackSupported(audioFormat, DEFAULT_AUDIO_ATTRIBUTES)) {
          return channelCount
        }
      }
      return 0
    }
  }

  companion object {
    private const val DEFAULT_MAX_CHANNEL_COUNT = 8
    private const val DEFAULT_SAMPLE_RATE_HZ = 48_000

    /** The minimum audio capabilities supported by all devices. */
    @JvmField
    val DEFAULT_AUDIO_CAPABILITIES =
      AudioCapabilities(intArrayOf(AudioFormat.ENCODING_PCM_16BIT), DEFAULT_MAX_CHANNEL_COUNT)

    /** Audio capabilities when the device specifies external surround sound. */
    @SuppressLint("InlinedApi")
    private val EXTERNAL_SURROUND_SOUND_CAPABILITIES =
      AudioCapabilities(
        intArrayOf(
          AudioFormat.ENCODING_PCM_16BIT,
          AudioFormat.ENCODING_AC3,
          AudioFormat.ENCODING_E_AC3
        ),
        DEFAULT_MAX_CHANNEL_COUNT
      )

    /**
     * All surround sound encodings that a device may be capable of playing mapped to a maximum
     * channel count.
     */
    private val ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS =
      ImmutableMap.Builder<Int, Int>()
        .put(C.ENCODING_AC3, 6)
        .put(C.ENCODING_AC4, 6)
        .put(C.ENCODING_DTS, 6)
        .put(C.ENCODING_E_AC3_JOC, 6)
        .put(C.ENCODING_E_AC3, 8)
        .put(C.ENCODING_DTS_HD, 8)
        .put(C.ENCODING_DOLBY_TRUEHD, 8)
        .buildOrThrow()

    /** Global settings key for devices that can specify external surround sound. */
    private const val EXTERNAL_SURROUND_SOUND_KEY = "external_surround_sound_enabled"

    /**
     * Returns the current audio capabilities for the device.
     *
     * @param context A context for obtaining the current audio capabilities.
     * @return The current audio capabilities for the device.
     */
    @JvmStatic
    @SuppressLint("InlinedApi")
    fun getCapabilities(context: Context): AudioCapabilities {
      val intent =
        Util.registerReceiverNotExported(
          context, /* receiver= */ null, IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG)
        )
      return getCapabilities(context, intent)
    }

    @JvmStatic
    @SuppressLint("InlinedApi")
    @Suppress("NewApi")
    internal fun getCapabilities(context: Context, intent: Intent?): AudioCapabilities {
      if (deviceMaySetExternalSurroundSoundGlobalSetting()
        && Global.getInt(context.contentResolver, EXTERNAL_SURROUND_SOUND_KEY, 0) == 1
      ) {
        return EXTERNAL_SURROUND_SOUND_CAPABILITIES
      }
      if (Util.SDK_INT >= 29 && (Util.isTv(context) || Util.isAutomotive(context))) {
        return AudioCapabilities(
          Api29.getDirectPlaybackSupportedEncodings(), DEFAULT_MAX_CHANNEL_COUNT
        )
      }
      if (intent == null || intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 0) {
        return DEFAULT_AUDIO_CAPABILITIES
      }
      return AudioCapabilities(
        intent.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS),
        intent.getIntExtra(
          AudioManager.EXTRA_MAX_CHANNEL_COUNT, /* defaultValue= */ DEFAULT_MAX_CHANNEL_COUNT
        )
      )
    }

    /**
     * Returns the global settings [Uri] used by the device to specify external surround sound,
     * or null if the device does not support this functionality.
     */
    @JvmStatic
    @Suppress("NewApi")
    internal fun getExternalSurroundSoundGlobalSettingUri(): Uri? {
      return if (deviceMaySetExternalSurroundSoundGlobalSetting()) {
        Global.getUriFor(EXTERNAL_SURROUND_SOUND_KEY)
      } else {
        null
      }
    }

    private fun deviceMaySetExternalSurroundSoundGlobalSetting(): Boolean {
      return Util.SDK_INT >= 17 && ("Amazon" == Util.MANUFACTURER || "Xiaomi" == Util.MANUFACTURER)
    }

    /**
     * Returns the maximum number of channels supported for passthrough playback of audio in the given
     * encoding, or `0` if the format is unsupported.
     */
    @JvmStatic
    @Suppress("NewApi")
    private fun getMaxSupportedChannelCountForPassthrough(
      encoding: Int, sampleRate: Int
    ): Int {
      if (Util.SDK_INT >= 29) {
        return Api29.getMaxSupportedChannelCountForPassthrough(encoding, sampleRate)
      }
      return checkNotNull(ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS[encoding] ?: 0)!!
    }

    private fun getChannelConfigForPassthrough(channelCount: Int): Int {
      var count = channelCount
      if (Util.SDK_INT <= 28) {
        if (count == 7) {
          count = 8
        } else if (count == 3 || count == 4 || count == 5) {
          count = 6
        }
      }
      if (Util.SDK_INT <= 26 && "fugu" == Util.DEVICE && count == 1) {
        count = 2
      }
      return Util.getAudioTrackChannelConfig(count)
    }
  }
}
