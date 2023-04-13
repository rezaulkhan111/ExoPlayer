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

import androidx.annotation.WorkerThread
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException
import java.io.File
import java.io.IOException
import java.util.*

/**
 * A cache that supports partial caching of resources.
 *
 * <h2>Terminology</h2>
 *
 *
 *  * A *resource* is a complete piece of logical data, for example a complete media file.
 *  * A *cache key* uniquely identifies a resource. URIs are often suitable for use as
 * cache keys, however this is not always the case. URIs are not suitable when caching
 * resources obtained from a service that generates multiple URIs for the same underlying
 * resource, for example because the service uses expiring URIs as a form of access control.
 *  * A *cache span* is a byte range within a resource, which may or may not be cached. A
 * cache span that's not cached is called a *hole span*. A cache span that is cached
 * corresponds to a single underlying file in the cache.
 *
 */
interface Cache {

    /** Listener of [Cache] events.  */
    interface Listener {
        /**
         * Called when a [CacheSpan] is added to the cache.
         *
         * @param cache The source of the event.
         * @param span The added [CacheSpan].
         */
        fun onSpanAdded(cache: Cache?, span: CacheSpan?)

        /**
         * Called when a [CacheSpan] is removed from the cache.
         *
         * @param cache The source of the event.
         * @param span The removed [CacheSpan].
         */
        fun onSpanRemoved(cache: Cache?, span: CacheSpan?)

        /**
         * Called when an existing [CacheSpan] is touched, causing it to be replaced. The new
         * [CacheSpan] is guaranteed to represent the same data as the one it replaces, however
         * [CacheSpan.file] and [CacheSpan.lastTouchTimestamp] may have changed.
         *
         *
         * Note that for span replacement, [.onSpanAdded] and [ ][.onSpanRemoved] are not called in addition to this method.
         *
         * @param cache The source of the event.
         * @param oldSpan The old [CacheSpan], which has been removed from the cache.
         * @param newSpan The new [CacheSpan], which has been added to the cache.
         */
        fun onSpanTouched(cache: Cache?, oldSpan: CacheSpan?, newSpan: CacheSpan?)
    }

    /** Thrown when an error is encountered when writing data.  */
    open class CacheException : IOException {
        constructor(message: String?) : super(message) {}
        constructor(cause: Throwable?) : super(cause) {}
        constructor(message: String?, cause: Throwable?) : super(message, cause) {}
    }

    /**
     * Returned by [.getUid] if initialization failed before the unique identifier was read or
     * generated.
     */
    companion object {
        const val UID_UNSET: Long = -1
    }

    /**
     * Returns a non-negative unique identifier for the cache, or [.UID_UNSET] if initialization
     * failed before the unique identifier was determined.
     *
     *
     * Implementations are expected to generate and store the unique identifier alongside the
     * cached content. If the location of the cache is deleted or swapped, it is expected that a new
     * unique identifier will be generated when the cache is recreated.
     */
    fun getUid(): Long

    /**
     * Releases the cache. This method must be called when the cache is no longer required. The cache
     * must not be used after calling this method.
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     */
    @WorkerThread
    fun release()

    /**
     * Registers a listener to listen for changes to a given resource.
     *
     *
     * No guarantees are made about the thread or threads on which the listener is called, but it
     * is guaranteed that listener methods will be called in a serial fashion (i.e. one at a time) and
     * in the same order as events occurred.
     *
     * @param key The cache key of the resource.
     * @param listener The listener to add.
     * @return The current spans for the resource.
     */
    fun addListener(key: String?, listener: Listener?): NavigableSet<CacheSpan?>?

    /**
     * Unregisters a listener.
     *
     * @param key The cache key of the resource.
     * @param listener The listener to remove.
     */
    fun removeListener(key: String?, listener: Listener?)

    /**
     * Returns the cached spans for a given resource.
     *
     * @param key The cache key of the resource.
     * @return The spans for the key.
     */
    fun getCachedSpans(key: String?): NavigableSet<CacheSpan?>?

    /** Returns the cache keys of all of the resources that are at least partially cached.  */
    fun getKeys(): Set<String?>?

    /** Returns the total disk space in bytes used by the cache.  */
    fun getCacheSpace(): Long

    /**
     * A caller should invoke this method when they require data starting from a given position in a
     * given resource.
     *
     *
     * If there is a cache entry that overlaps the position, then the returned [CacheSpan]
     * defines the file in which the data is stored. [CacheSpan.isCached] is true. The caller
     * may read from the cache file, but does not acquire any locks.
     *
     *
     * If there is no cache entry overlapping `position`, then the returned [CacheSpan]
     * defines a hole in the cache starting at `position` into which the caller may write as it
     * obtains the data from some other source. The returned [CacheSpan] serves as a lock.
     * Whilst the caller holds the lock it may write data into the hole. It may split data into
     * multiple files. When the caller has finished writing a file it should commit it to the cache by
     * calling [.commitFile]. When the caller has finished writing, it must release
     * the lock by calling [.releaseHoleSpan].
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @param key The cache key of the resource.
     * @param position The starting position in the resource from which data is required.
     * @param length The length of the data being requested, or [C.LENGTH_UNSET] if unbounded.
     * The length is ignored if there is a cache entry that overlaps the position. Else, it
     * defines the maximum length of the hole [CacheSpan] that's returned. Cache
     * implementations may support parallel writes into non-overlapping holes, and so passing the
     * actual required length should be preferred to passing [C.LENGTH_UNSET] when possible.
     * @return The [CacheSpan].
     * @throws InterruptedException If the thread was interrupted.
     * @throws CacheException If an error is encountered.
     */
    @WorkerThread
    @Throws(InterruptedException::class, CacheException::class)
    fun startReadWrite(key: String?, position: Long, length: Long): CacheSpan?

    /**
     * Same as [.startReadWrite]. However, if the cache entry is locked,
     * then instead of blocking, this method will return null as the [CacheSpan].
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @param key The cache key of the resource.
     * @param position The starting position in the resource from which data is required.
     * @param length The length of the data being requested, or [C.LENGTH_UNSET] if unbounded.
     * The length is ignored if there is a cache entry that overlaps the position. Else, it
     * defines the range of data locked by the returned [CacheSpan].
     * @return The [CacheSpan]. Or null if the cache entry is locked.
     * @throws CacheException If an error is encountered.
     */
    @WorkerThread
    @Throws(CacheException::class)
    fun startReadWriteNonBlocking(key: String?, position: Long, length: Long): CacheSpan?

    /**
     * Obtains a cache file into which data can be written. Must only be called when holding a
     * corresponding hole [CacheSpan] obtained from [.startReadWrite].
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @param key The cache key of the resource being written.
     * @param position The starting position in the resource from which data will be written.
     * @param length The length of the data being written, or [C.LENGTH_UNSET] if unknown. Used
     * only to ensure that there is enough space in the cache.
     * @return The file into which data should be written.
     * @throws CacheException If an error is encountered.
     */
    @WorkerThread
    @Throws(CacheException::class)
    fun startFile(key: String?, position: Long, length: Long): File?

    /**
     * Commits a file into the cache. Must only be called when holding a corresponding hole [ ] obtained from [.startReadWrite].
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @param file A newly written cache file.
     * @param length The length of the newly written cache file in bytes.
     * @throws CacheException If an error is encountered.
     */
    @WorkerThread
    @Throws(CacheException::class)
    fun commitFile(file: File?, length: Long)

    /**
     * Releases a [CacheSpan] obtained from [.startReadWrite] which
     * corresponded to a hole in the cache.
     *
     * @param holeSpan The [CacheSpan] being released.
     */
    fun releaseHoleSpan(holeSpan: CacheSpan?)

    /**
     * Removes all [CacheSpans][CacheSpan] for a resource, deleting the underlying files.
     *
     * @param key The cache key of the resource being removed.
     */
    @WorkerThread
    fun removeResource(key: String?)

    /**
     * Removes a cached [CacheSpan] from the cache, deleting the underlying file.
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @param span The [CacheSpan] to remove.
     */
    @WorkerThread
    fun removeSpan(span: CacheSpan?)

    /**
     * Returns whether the specified range of data in a resource is fully cached.
     *
     * @param key The cache key of the resource.
     * @param position The starting position of the data in the resource.
     * @param length The length of the data.
     * @return true if the data is available in the Cache otherwise false;
     */
    fun isCached(key: String?, position: Long, length: Long): Boolean

    /**
     * Returns the length of continuously cached data starting from `position`, up to a maximum
     * of `maxLength`, of a resource. If `position` isn't cached then `-holeLength`
     * is returned, where `holeLength` is the length of continuously uncached data starting from
     * `position`, up to a maximum of `maxLength`.
     *
     * @param key The cache key of the resource.
     * @param position The starting position of the data in the resource.
     * @param length The maximum length of the data or hole to be returned. [C.LENGTH_UNSET] is
     * permitted, and is equivalent to passing [Long.MAX_VALUE].
     * @return The length of the continuously cached data, or `-holeLength` if `position`
     * isn't cached.
     */
    fun getCachedLength(key: String?, position: Long, length: Long): Long

    /**
     * Returns the total number of cached bytes between `position` (inclusive) and `(position + length)` (exclusive) of a resource.
     *
     * @param key The cache key of the resource.
     * @param position The starting position of the data in the resource.
     * @param length The length of the data to check. [C.LENGTH_UNSET] is permitted, and is
     * equivalent to passing [Long.MAX_VALUE].
     * @return The total number of cached bytes.
     */
    fun getCachedBytes(key: String?, position: Long, length: Long): Long

    /**
     * Applies `mutations` to the [ContentMetadata] for the given resource. A new [ ] is added if there isn't one already for the resource.
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @param key The cache key of the resource.
     * @param mutations Contains mutations to be applied to the metadata.
     * @throws CacheException If an error is encountered.
     */
    @WorkerThread
    @Throws(CacheException::class)
    fun applyContentMetadataMutations(key: String?, mutations: ContentMetadataMutations?)

    /**
     * Returns a [ContentMetadata] for the given resource.
     *
     * @param key The cache key of the resource.
     * @return The [ContentMetadata] for the resource.
     */
    fun getContentMetadata(key: String?): ContentMetadata?
}