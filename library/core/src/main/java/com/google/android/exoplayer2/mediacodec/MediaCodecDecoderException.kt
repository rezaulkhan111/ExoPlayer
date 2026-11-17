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
package com.google.android.exoplayer2.mediacodec

import android.annotation.SuppressLint
import android.media.MediaCodec
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.decoder.DecoderException
import com.google.android.exoplayer2.util.Util

/** Thrown when a failure occurs in a [MediaCodec] decoder. */
open class MediaCodecDecoderException(
  cause: Throwable?,
  /** The [MediaCodecInfo] of the decoder that failed. Null if unknown. */
  @JvmField val codecInfo: MediaCodecInfo?,
) : DecoderException("Decoder failed: " + (codecInfo?.name), cause) {

  /** An optional developer-readable diagnostic information string. May be null. */
  @JvmField
  @field:SuppressLint("NewApi")
  val diagnosticInfo: String? = if (Util.SDK_INT >= 21) getDiagnosticInfoV21(cause) else null

  private companion object {
    @RequiresApi(21)
    @DoNotInline
    fun getDiagnosticInfoV21(cause: Throwable?): String? {
      return (cause as? MediaCodec.CodecException)?.diagnosticInfo
    }
  }
}
