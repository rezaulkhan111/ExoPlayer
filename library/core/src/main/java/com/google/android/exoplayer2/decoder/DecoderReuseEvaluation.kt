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
package com.google.android.exoplayer2.decoder

import androidx.annotation.IntDef
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.video.ColorInfo

/**
 * The result of an evaluation to determine whether a decoder can be reused for a new input format.
 */
class DecoderReuseEvaluation(
  /** The name of the decoder. */
  @JvmField val decoderName: String,
  /** The [Format] for which the decoder was previously configured. */
  @JvmField val oldFormat: Format,
  /** The new [Format] being evaluated. */
  @JvmField val newFormat: Format,
  /** The [DecoderReuseResult] result of the evaluation. */
  @param:DecoderReuseResult @field:DecoderReuseResult @JvmField val result: Int,
  /**
   * [DecoderDiscardReasons] reasons why the decoder cannot be reused. Always `0` if
   * reuse is possible. May also be `0` if reuse is not possible for an unspecified reason.
   */
  @param:DecoderDiscardReasons @field:DecoderDiscardReasons @JvmField val discardReasons: Int
) {

  /** Possible outcomes of the evaluation. */
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
  @IntDef(
    REUSE_RESULT_NO,
    REUSE_RESULT_YES_WITH_FLUSH,
    REUSE_RESULT_YES_WITH_RECONFIGURATION,
    REUSE_RESULT_YES_WITHOUT_RECONFIGURATION
  )
  annotation class DecoderReuseResult

  /** Possible reasons why reuse is not possible. */
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
  @IntDef(
    flag = true,
    value = [
      DISCARD_REASON_REUSE_NOT_IMPLEMENTED,
      DISCARD_REASON_WORKAROUND,
      DISCARD_REASON_APP_OVERRIDE,
      DISCARD_REASON_MIME_TYPE_CHANGED,
      DISCARD_REASON_OPERATING_RATE_CHANGED,
      DISCARD_REASON_INITIALIZATION_DATA_CHANGED,
      DISCARD_REASON_DRM_SESSION_CHANGED,
      DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED,
      DISCARD_REASON_VIDEO_MAX_RESOLUTION_EXCEEDED,
      DISCARD_REASON_VIDEO_RESOLUTION_CHANGED,
      DISCARD_REASON_VIDEO_ROTATION_CHANGED,
      DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED,
      DISCARD_REASON_AUDIO_CHANNEL_COUNT_CHANGED,
      DISCARD_REASON_AUDIO_SAMPLE_RATE_CHANGED,
      DISCARD_REASON_AUDIO_ENCODING_CHANGED
    ]
  )
  annotation class DecoderDiscardReasons

  init {
    Assertions.checkArgument(result == REUSE_RESULT_NO || discardReasons == 0)
    Assertions.checkNotEmpty(decoderName)
    Assertions.checkNotNull(oldFormat)
    Assertions.checkNotNull(newFormat)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }
    val otherEvaluation = other as DecoderReuseEvaluation
    return result == otherEvaluation.result &&
      discardReasons == otherEvaluation.discardReasons &&
      decoderName == otherEvaluation.decoderName &&
      oldFormat == otherEvaluation.oldFormat &&
      newFormat == otherEvaluation.newFormat
  }

  override fun hashCode(): Int {
    var hashCode = 17
    hashCode = 31 * hashCode + result
    hashCode = 31 * hashCode + discardReasons
    hashCode = 31 * hashCode + decoderName.hashCode()
    hashCode = 31 * hashCode + oldFormat.hashCode()
    hashCode = 31 * hashCode + newFormat.hashCode()
    return hashCode
  }

  companion object {
    /** The decoder cannot be reused. */
    const val REUSE_RESULT_NO = 0

    /** The decoder can be reused, but must be flushed. */
    const val REUSE_RESULT_YES_WITH_FLUSH = 1

    /**
     * The decoder can be reused. It does not need to be flushed, but must be reconfigured by
     * prefixing the next input buffer with the new format's configuration data.
     */
    const val REUSE_RESULT_YES_WITH_RECONFIGURATION = 2

    /** The decoder can be kept. It does not need to be flushed and no reconfiguration is required. */
    const val REUSE_RESULT_YES_WITHOUT_RECONFIGURATION = 3

    /** Decoder reuse is not implemented. */
    const val DISCARD_REASON_REUSE_NOT_IMPLEMENTED = 1 shl 0

    /** Decoder reuse is disabled by a workaround. */
    const val DISCARD_REASON_WORKAROUND = 1 shl 1

    /** Decoder reuse is disabled by overriding behavior in application code. */
    const val DISCARD_REASON_APP_OVERRIDE = 1 shl 2

    /** The sample MIME type is changing. */
    const val DISCARD_REASON_MIME_TYPE_CHANGED = 1 shl 3

    /** The codec's operating rate is changing. */
    const val DISCARD_REASON_OPERATING_RATE_CHANGED = 1 shl 4

    /** The format initialization data is changing. */
    const val DISCARD_REASON_INITIALIZATION_DATA_CHANGED = 1 shl 5

    /** The new format may exceed the decoder's configured maximum sample size, in bytes. */
    const val DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED = 1 shl 6

    /** The DRM session is changing. */
    const val DISCARD_REASON_DRM_SESSION_CHANGED = 1 shl 7

    /** The new format may exceed the decoder's configured maximum resolution. */
    const val DISCARD_REASON_VIDEO_MAX_RESOLUTION_EXCEEDED = 1 shl 8

    /** The video resolution is changing. */
    const val DISCARD_REASON_VIDEO_RESOLUTION_CHANGED = 1 shl 9

    /** The video rotation is changing. */
    const val DISCARD_REASON_VIDEO_ROTATION_CHANGED = 1 shl 10

    /** The video [ColorInfo] is changing. */
    const val DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED = 1 shl 11

    /** The audio channel count is changing. */
    const val DISCARD_REASON_AUDIO_CHANNEL_COUNT_CHANGED = 1 shl 12

    /** The audio sample rate is changing. */
    const val DISCARD_REASON_AUDIO_SAMPLE_RATE_CHANGED = 1 shl 13

    /** The audio encoding is changing. */
    const val DISCARD_REASON_AUDIO_ENCODING_CHANGED = 1 shl 14
  }
}
