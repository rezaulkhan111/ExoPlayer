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
package com.google.android.exoplayer2.extractor

import android.net.Uri
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import kotlin.Array
import kotlin.String
import kotlin.arrayOf
import kotlin.invoke

/** Factory for arrays of [Extractor] instances.  */
interface ExtractorsFactory {
    /** Returns an array of new [Extractor] instances.  */
    fun createExtractors(): Array<Extractor?>?

    /**
     * Returns an array of new [Extractor] instances.
     *
     * @param uri The [Uri] of the media to extract.
     * @param responseHeaders The response headers of the media to extract, or an empty map if there
     * are none. The map lookup should be case-insensitive.
     * @return The [Extractor] instances.
     */
    fun createExtractors(
        uri: Uri?,
        responseHeaders: Map<String?, List<String?>?>?
    ): Array<Extractor?>? {
        return createExtractors()
    }

    companion object {
        /**
         * Extractor factory that returns an empty list of extractors. Can be used whenever [ ] are not required.
         */
        @JvmField
        val EMPTY: ExtractorsFactory = ExtractorsFactory { arrayOf<Extractor?>() }
    }
}