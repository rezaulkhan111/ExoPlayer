/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer

import android.media.MediaCodecInfo
import com.google.common.collect.ImmutableList

/** Selector of [MediaCodec] encoder instances.  */
interface EncoderSelector {
    /**
     * Returns a list of encoders that can encode media in the specified `mimeType`, in priority
     * order.
     *
     * @param mimeType The [MIME type][MimeTypes] for which an encoder is required.
     * @return An immutable list of [encoders][MediaCodecInfo] that support the `mimeType`. The list may be empty.
     */
    fun selectEncoderInfos(mimeType: String?): ImmutableList<MediaCodecInfo?>?

    companion object {
        /**
         * Default implementation of [EncoderSelector], which returns the preferred encoders for the
         * given [MIME type][MimeTypes].
         */
        @JvmField
        val DEFAULT: EncoderSelector = EncoderSelector { mimeType: String? ->
            EncoderUtil.getSupportedEncoders(
                mimeType!!
            )
        }
    }
}