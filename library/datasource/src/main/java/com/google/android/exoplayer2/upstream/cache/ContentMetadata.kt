/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream.cache

import android.net.Uri
import com.google.android.exoplayer2.C

/** Interface for an immutable snapshot of keyed metadata.  */
interface ContentMetadata {
    /**
     * Returns a metadata value.
     *
     * @param key Key of the metadata to be returned.
     * @param defaultValue Value to return if the metadata doesn't exist.
     * @return The metadata value.
     */
    operator fun get(key: String?, defaultValue: ByteArray?): ByteArray?

    /**
     * Returns a metadata value.
     *
     * @param key Key of the metadata to be returned.
     * @param defaultValue Value to return if the metadata doesn't exist.
     * @return The metadata value.
     */
    operator fun get(key: String?, defaultValue: String?): String?

    /**
     * Returns a metadata value.
     *
     * @param key Key of the metadata to be returned.
     * @param defaultValue Value to return if the metadata doesn't exist.
     * @return The metadata value.
     */
    operator fun get(key: String?, defaultValue: Long): Long

    /** Returns whether the metadata is available.  */
    operator fun contains(key: String?): Boolean

    companion object {
        /**
         * Returns the value stored under [.KEY_CONTENT_LENGTH], or [C.LENGTH_UNSET] if not
         * set.
         */
        @JvmStatic
        fun getContentLength(contentMetadata: ContentMetadata): Long {
            return contentMetadata[KEY_CONTENT_LENGTH, C.LENGTH_UNSET.toLong()]
        }

        /**
         * Returns the value stored under [.KEY_REDIRECTED_URI] as a [Uri], or {code null} if
         * not set.
         */
        @JvmStatic
        fun getRedirectedUri(contentMetadata: ContentMetadata): Uri? {
            val redirectedUri = contentMetadata[KEY_REDIRECTED_URI, null as String?]
            return if (redirectedUri == null) null else Uri.parse(redirectedUri)
        }

        /**
         * Prefix for custom metadata keys. Applications can use keys starting with this prefix without
         * any risk of their keys colliding with ones defined by the ExoPlayer library.
         */
        const val KEY_CUSTOM_PREFIX = "custom_"

        /** Key for redirected uri (type: String).  */
        const val KEY_REDIRECTED_URI = "exo_redir"

        /** Key for content length in bytes (type: long).  */
        const val KEY_CONTENT_LENGTH = "exo_len"
    }
}