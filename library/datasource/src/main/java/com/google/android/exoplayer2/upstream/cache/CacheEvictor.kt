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
package com.google.android.exoplayer2.upstream.cache

/**
 * Evicts data from a [Cache]. Implementations should call [Cache.removeSpan]
 * to evict cache entries based on their eviction policies.
 */
interface CacheEvictor : Cache.Listener {
    /**
     * Returns whether the evictor requires the [Cache] to touch [CacheSpans][CacheSpan]
     * when it accesses them. Implementations that do not use [CacheSpan.lastTouchTimestamp]
     * should return `false`.
     */
    fun requiresCacheSpanTouches(): Boolean

    /** Called when cache has been initialized.  */
    fun onCacheInitialized()

    /**
     * Called when a writer starts writing to the cache.
     *
     * @param cache The source of the event.
     * @param key The key being written.
     * @param position The starting position of the data being written.
     * @param length The length of the data being written, or [C.LENGTH_UNSET] if unknown.
     */
    fun onStartFile(cache: Cache?, key: String?, position: Long, length: Long)
}