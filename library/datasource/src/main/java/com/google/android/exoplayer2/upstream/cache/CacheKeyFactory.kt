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

import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory
import kotlin.String
import kotlin.invoke

/** Factory for cache keys.  */
interface CacheKeyFactory {
    /**
     * Returns the cache key of the resource containing the data defined by a [DataSpec].
     *
     *
     * Note that since the returned cache key corresponds to the whole resource, implementations
     * must not return different cache keys for [DataSpecs][DataSpec] that define different
     * ranges of the same resource. As a result, implementations should not use fields such as [ ][DataSpec.position] and [DataSpec.length].
     *
     * @param dataSpec The [DataSpec].
     * @return The cache key of the resource.
     */
    fun buildCacheKey(dataSpec: DataSpec?): String?

    companion object {
        /** Default [CacheKeyFactory].  */
        @JvmField
        val DEFAULT: CacheKeyFactory =
            CacheKeyFactory { dataSpec: DataSpec -> dataSpec.key ?: dataSpec.uri.toString() }
    }
}