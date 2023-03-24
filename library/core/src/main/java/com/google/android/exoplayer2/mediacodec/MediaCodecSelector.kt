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

import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException

/** Selector of [MediaCodec] instances.  */
interface MediaCodecSelector {
    companion object {
        /**
         * Default implementation of [MediaCodecSelector], which returns the preferred decoder for
         * the given format.
         */
        @JvmField
        val DEFAULT: MediaCodecSelector = MediaCodecSelector { mimeType: String?, secure: Boolean, tunneling: Boolean -> MediaCodecUtil.getDecoderInfos(mimeType!!, secure, tunneling) }
    }

    /**
     * Returns a list of decoders that can decode media in the specified MIME type, in priority order.
     *
     * @param mimeType The MIME type for which a decoder is required.
     * @param requiresSecureDecoder Whether a secure decoder is required.
     * @param requiresTunnelingDecoder Whether a tunneling decoder is required.
     * @return An unmodifiable list of [MediaCodecInfo]s corresponding to decoders. May be
     * empty.
     * @throws DecoderQueryException Thrown if there was an error querying decoders.
     */
    @Throws(DecoderQueryException::class)
    fun getDecoderInfos(mimeType: String?, requiresSecureDecoder: Boolean, requiresTunnelingDecoder: Boolean): List<MediaCodecInfo?>?
}