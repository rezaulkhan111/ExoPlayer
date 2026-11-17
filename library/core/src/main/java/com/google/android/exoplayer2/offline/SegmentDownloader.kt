/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.offline

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.ParsingLoadable
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory
import com.google.android.exoplayer2.upstream.cache.CacheWriter
import com.google.android.exoplayer2.upstream.cache.ContentMetadata
import com.google.android.exoplayer2.util.PriorityTaskManager
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException
import com.google.android.exoplayer2.util.RunnableFutureTask
import com.google.android.exoplayer2.util.Util
import java.io.IOException
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/**
 * Base class for multi segment stream downloaders.
 *
 * @param <M> The type of the manifest object.
 */
abstract class SegmentDownloader<M : FilterableManifest<M>> : Downloader {

  /** Smallest unit of content to be downloaded. */
  open class Segment(
      /** The start time of the segment in microseconds. */
      @JvmField val startTimeUs: Long,
      /** The [DataSpec] of the segment. */
      @JvmField val dataSpec: DataSpec
  ) : Comparable<Segment> {

    override fun compareTo(other: Segment): Int {
      return Util.compareLong(startTimeUs, other.startTimeUs)
    }
  }

  private val manifestDataSpec: DataSpec
  private val manifestParser: ParsingLoadable.Parser<M>
  private val streamKeys: ArrayList<StreamKey>
  private val cacheDataSourceFactory: CacheDataSource.Factory
  private val cache: Cache
  private val cacheKeyFactory: CacheKeyFactory
  private val priorityTaskManager: PriorityTaskManager?
  private val executor: Executor

  /**
   * The currently active runnables.
   *
   * <p>Note: Only the {@link #download} thread is permitted to modify this list. Modifications, as
   * well as the iteration on the {@link #cancel} thread, must be synchronized on the instance for
   * thread safety. Iterations on the {@link #download} thread do not need to be synchronized, and
   * should not be synchronized because doing so can erroneously block {@link #cancel}.
   */
  private val activeRunnables: ArrayList<RunnableFutureTask<*, *>>

  @Volatile private var isCanceled = false

  /**
   * @param mediaItem The [MediaItem] to be downloaded.
   * @param manifestParser A parser for manifests belonging to the media to be downloaded.
   * @param cacheDataSourceFactory A [CacheDataSource.Factory] for the cache into which the
   *     download will be written.
   * @param executor An [Executor] used to make requests for the media being downloaded.
   *     Providing an [Executor] that uses multiple threads will speed up the download by
   *     allowing parts of it to be executed in parallel.
   */
  constructor(
      mediaItem: MediaItem,
      manifestParser: ParsingLoadable.Parser<M>,
      cacheDataSourceFactory: CacheDataSource.Factory,
      executor: Executor
  ) {
    val localConfiguration = checkNotNull(mediaItem.localConfiguration)
    this.manifestDataSpec = getCompressibleDataSpec(localConfiguration.uri)
    this.manifestParser = manifestParser
    this.streamKeys = ArrayList(localConfiguration.streamKeys)
    this.cacheDataSourceFactory = cacheDataSourceFactory
    this.executor = executor
    this.cache = checkNotNull(cacheDataSourceFactory.cache)
    this.cacheKeyFactory = cacheDataSourceFactory.cacheKeyFactory
    this.priorityTaskManager = cacheDataSourceFactory.upstreamPriorityTaskManager
    this.activeRunnables = ArrayList()
  }

  @Throws(IOException::class, InterruptedException::class)
  override fun download(progressListener: Downloader.ProgressListener?) {
    val pendingSegments = ArrayDeque<Segment>()
    val recycledRunnables = ArrayDeque<SegmentDownloadRunnable>()
    priorityTaskManager?.add(C.PRIORITY_DOWNLOAD)
    try {
      val dataSource = cacheDataSourceFactory.createDataSourceForDownloading()
      // Get the manifest and all of the segments.
      var manifest = getManifest(dataSource, manifestDataSpec, /* removing= */ false)
      if (streamKeys.isNotEmpty()) {
        manifest = manifest.copy(streamKeys)
      }
      val segments = getSegments(dataSource, manifest, /* removing= */ false)

      // Sort the segments so that we download media in the right order from the start of the
      // content, and merge segments where possible to minimize the number of server round trips.
      Collections.sort(segments)
      mergeSegments(segments, cacheKeyFactory)

      // Scan the segments, removing any that are fully downloaded.
      val totalSegments = segments.size
      var segmentsDownloaded = 0
      var contentLength = 0L
      var bytesDownloaded = 0L
      for (i in segments.size - 1 downTo 0) {
        val dataSpec = segments[i].dataSpec
        val cacheKey = cacheKeyFactory.buildCacheKey(dataSpec)
        var segmentLength = dataSpec.length
        if (segmentLength == C.LENGTH_UNSET.toLong()) {
          val metadata = cache.getContentMetadata(cacheKey)
          val resourceLength = ContentMetadata.getContentLength(checkNotNull(metadata))
          if (resourceLength != C.LENGTH_UNSET.toLong()) {
            segmentLength = resourceLength - dataSpec.position
          }
        }
        val segmentBytesDownloaded = cache.getCachedBytes(cacheKey, dataSpec.position, segmentLength)
        bytesDownloaded += segmentBytesDownloaded
        if (segmentLength != C.LENGTH_UNSET.toLong()) {
          if (segmentLength == segmentBytesDownloaded) {
            // The segment is fully downloaded.
            segmentsDownloaded++
            segments.removeAt(i)
          }
          if (contentLength != C.LENGTH_UNSET.toLong()) {
            contentLength += segmentLength
          }
        } else {
          contentLength = C.LENGTH_UNSET.toLong()
        }
      }

      // Download the segments.
      val progressNotifier =
          if (progressListener != null) {
            ProgressNotifier(
                progressListener,
                contentLength,
                totalSegments,
                bytesDownloaded,
                segmentsDownloaded
            )
          } else {
            null
          }
      pendingSegments.addAll(segments)
      while (!isCanceled && pendingSegments.isNotEmpty()) {
        // Block until there aren't any higher priority tasks.
        priorityTaskManager?.proceed(C.PRIORITY_DOWNLOAD)

        // Create and execute a runnable to download the next segment.
        val segmentDataSource: CacheDataSource
        val temporaryBuffer: ByteArray
        if (recycledRunnables.isNotEmpty()) {
          val recycledRunnable = recycledRunnables.removeFirst()
          segmentDataSource = recycledRunnable.dataSource
          temporaryBuffer = recycledRunnable.temporaryBuffer
        } else {
          segmentDataSource = cacheDataSourceFactory.createDataSourceForDownloading()
          temporaryBuffer = ByteArray(BUFFER_SIZE_BYTES)
        }
        val segment = pendingSegments.removeFirst()
        val downloadRunnable =
            SegmentDownloadRunnable(segment, segmentDataSource, progressNotifier, temporaryBuffer)
        addActiveRunnable(downloadRunnable)
        executor.execute(downloadRunnable)

        // Clean up runnables that have finished.
        for (j in activeRunnables.size - 1 downTo 0) {
          val activeRunnable = activeRunnables[j] as SegmentDownloadRunnable
          // Only block until the runnable has finished if we don't have any more pending segments
          // to start. If we do have pending segments to start then only process the runnable if
          // it's already finished.
          if (pendingSegments.isEmpty() || activeRunnable.isDone) {
            try {
              activeRunnable.get()
              removeActiveRunnable(j)
              recycledRunnables.addLast(activeRunnable)
            } catch (e: ExecutionException) {
              val cause = checkNotNull(e.cause)
              if (cause is PriorityTooLowException) {
                // We need to schedule this segment again in a future loop iteration.
                pendingSegments.addFirst(activeRunnable.segment)
                removeActiveRunnable(j)
                recycledRunnables.addLast(activeRunnable)
              } else if (cause is IOException) {
                throw cause
              } else {
                // The cause must be an uncaught Throwable type.
                Util.sneakyThrow(cause)
              }
            }
          }
        }

        // Don't move on to the next segment until the runnable for this segment has started. This
        // drip feeds runnables to the executor, rather than providing them all up front.
        downloadRunnable.blockUntilStarted()
      }
    } finally {
      // If one of the runnables has thrown an exception, then it's possible there are other active
      // runnables still doing work. We need to wait until they finish before exiting this method.
      // Cancel them to speed this up.
      for (i in activeRunnables.indices) {
        activeRunnables[i].cancel(/* interruptIfRunning= */ true)
      }
      // Wait until the runnables have finished. In addition to the failure case, we also need to
      // do this for the case where the main download thread was interrupted as part of cancelation.
      for (i in activeRunnables.size - 1 downTo 0) {
        activeRunnables[i].blockUntilFinished()
        removeActiveRunnable(i)
      }
      priorityTaskManager?.remove(C.PRIORITY_DOWNLOAD)
    }
  }

  override fun cancel() {
    synchronized(activeRunnables) {
      isCanceled = true
      for (i in activeRunnables.indices) {
        activeRunnables[i].cancel(/* interruptIfRunning= */ true)
      }
    }
  }

  override fun remove() {
    val dataSource = cacheDataSourceFactory.createDataSourceForRemovingDownload()
    try {
      val manifest = getManifest(dataSource, manifestDataSpec, /* removing= */ true)
      val segments = getSegments(dataSource, manifest, /* removing= */ true)
      for (i in segments.indices) {
        cache.removeResource(cacheKeyFactory.buildCacheKey(segments[i].dataSpec))
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    } catch (e: Exception) {
      // Ignore exceptions when removing.
    } finally {
      // Always attempt to remove the manifest.
      cache.removeResource(cacheKeyFactory.buildCacheKey(manifestDataSpec))
    }
  }

  // Internal methods.

  /**
   * Loads and parses a manifest.
   *
   * @param dataSpec The manifest [DataSpec].
   * @param removing Whether the manifest is being loaded as part of the download being removed.
   * @return The loaded manifest.
   * @throws InterruptedException If the thread on which the method is called is interrupted.
   * @throws IOException If an error occurs during execution.
   */
  @Throws(InterruptedException::class, IOException::class)
  protected fun getManifest(dataSource: DataSource, dataSpec: DataSpec, removing: Boolean): M {
    return execute(
        object : RunnableFutureTask<M, IOException>() {
          @Throws(IOException::class)
          override fun doWork(): M {
            return ParsingLoadable.load(dataSource, manifestParser, dataSpec, C.DATA_TYPE_MANIFEST)
          }
        },
        removing
    )
  }

  /**
   * Executes the provided [RunnableFutureTask].
   *
   * @param runnable The [RunnableFutureTask] to execute.
   * @param removing Whether the execution is part of the download being removed.
   * @return The result.
   * @throws InterruptedException If the thread on which the method is called is interrupted.
   * @throws IOException If an error occurs during execution.
   */
  @Throws(InterruptedException::class, IOException::class)
  protected fun <T> execute(runnable: RunnableFutureTask<T, *>, removing: Boolean): T {
    if (removing) {
      runnable.run()
      try {
        @Suppress("UNCHECKED_CAST")
        return runnable.get() as T
      } catch (e: ExecutionException) {
        val cause = checkNotNull(e.cause)
        if (cause is IOException) {
          throw cause
        } else {
          // The cause must be an uncaught Throwable type.
          Util.sneakyThrow(cause)
        }
      }
    }
    while (true) {
      if (isCanceled) {
        throw InterruptedException()
      }
      // Block until there aren't any higher priority tasks.
      priorityTaskManager?.proceed(C.PRIORITY_DOWNLOAD)
      addActiveRunnable(runnable)
      executor.execute(runnable)
      try {
        @Suppress("UNCHECKED_CAST")
        return runnable.get() as T
      } catch (e: ExecutionException) {
        val cause = checkNotNull(e.cause)
        if (cause is PriorityTooLowException) {
          // The next loop iteration will block until the task is able to proceed.
        } else if (cause is IOException) {
          throw cause
        } else {
          // The cause must be an uncaught Throwable type.
          Util.sneakyThrow(cause)
        }
      } finally {
        // We don't want to return for as long as the runnable might still be doing work.
        runnable.blockUntilFinished()
        removeActiveRunnable(runnable)
      }
    }
  }

  /**
   * Returns a list of all downloadable [Segment]s for a given manifest. Any required data
   * should be loaded using [getManifest] or [execute].
   *
   * @param dataSource The [DataSource] through which to load any required data.
   * @param manifest The manifest containing the segments.
   * @param removing Whether the segments are being obtained as part of a removal. If true then a
   *     partial segment list is returned in the case that a load error prevents all segments from
   *     being listed. If false then an [IOException] will be thrown in this case.
   * @return The list of downloadable [Segment]s.
   * @throws IOException Thrown if `allowPartialIndex` is false and an execution error occurs,
   *     or if the media is not in a form that allows for its segments to be listed.
   */
  @Throws(IOException::class, InterruptedException::class)
  protected abstract fun getSegments(
      dataSource: DataSource, manifest: M, removing: Boolean
  ): MutableList<Segment>

  @Throws(InterruptedException::class)
  private fun <T> addActiveRunnable(runnable: RunnableFutureTask<T, *>) {
    synchronized(activeRunnables) {
      if (isCanceled) {
        throw InterruptedException()
      }
      activeRunnables.add(runnable)
    }
  }

  private fun removeActiveRunnable(runnable: RunnableFutureTask<*, *>) {
    synchronized(activeRunnables) {
      activeRunnables.remove(runnable)
    }
  }

  private fun removeActiveRunnable(index: Int) {
    synchronized(activeRunnables) {
      activeRunnables.removeAt(index)
    }
  }

  private class SegmentDownloadRunnable(
      val segment: Segment,
      val dataSource: CacheDataSource,
      private val progressNotifier: ProgressNotifier?,
      val temporaryBuffer: ByteArray
  ) : RunnableFutureTask<Void?, IOException>() {

    private val cacheWriter = CacheWriter(dataSource, segment.dataSpec, temporaryBuffer, progressNotifier)

    @Throws(IOException::class)
    override fun doWork(): Void? {
      cacheWriter.cache()
      progressNotifier?.onSegmentDownloaded()
      return null
    }

    override fun cancelWork() {
      cacheWriter.cancel()
    }
  }

  private class ProgressNotifier(
      private val progressListener: Downloader.ProgressListener,
      private val contentLength: Long,
      private val totalSegments: Int,
      private var bytesDownloaded: Long,
      private var segmentsDownloaded: Int
  ) : CacheWriter.ProgressListener {

    override fun onProgress(requestLength: Long, bytesCached: Long, newBytesCached: Long) {
      bytesDownloaded += newBytesCached
      progressListener.onProgress(contentLength, bytesDownloaded, getPercentDownloaded())
    }

    fun onSegmentDownloaded() {
      segmentsDownloaded++
      progressListener.onProgress(contentLength, bytesDownloaded, getPercentDownloaded())
    }

    private fun getPercentDownloaded(): Float {
      return if (contentLength != C.LENGTH_UNSET.toLong() && contentLength != 0L) {
        (bytesDownloaded * 100f) / contentLength
      } else if (totalSegments != 0) {
        (segmentsDownloaded * 100f) / totalSegments
      } else {
        C.PERCENTAGE_UNSET.toFloat()
      }
    }
  }

  companion object {

    private const val BUFFER_SIZE_BYTES = 128 * 1024
    private const val MAX_MERGED_SEGMENT_START_TIME_DIFF_US = 20 * C.MICROS_PER_SECOND

    @JvmStatic
    protected fun getCompressibleDataSpec(uri: Uri): DataSpec {
      return DataSpec.Builder().setUri(uri).setFlags(DataSpec.FLAG_ALLOW_GZIP).build()
    }

    private fun mergeSegments(segments: MutableList<Segment>, keyFactory: CacheKeyFactory) {
      val lastIndexByCacheKey = HashMap<String, Int>()
      var nextOutIndex = 0
      for (i in segments.indices) {
        val segment = segments[i]
        val cacheKey = checkNotNull(keyFactory.buildCacheKey(segment.dataSpec))
        val lastIndex = lastIndexByCacheKey[cacheKey]
        val lastSegment = if (lastIndex == null) null else segments[lastIndex]
        if (lastSegment == null ||
            segment.startTimeUs > lastSegment.startTimeUs + MAX_MERGED_SEGMENT_START_TIME_DIFF_US ||
            !canMergeSegments(lastSegment.dataSpec, segment.dataSpec)) {
          lastIndexByCacheKey[cacheKey] = nextOutIndex
          segments[nextOutIndex] = segment
          nextOutIndex++
        } else {
          val mergedLength =
              if (segment.dataSpec.length == C.LENGTH_UNSET.toLong()) {
                C.LENGTH_UNSET.toLong()
              } else {
                lastSegment.dataSpec.length + segment.dataSpec.length
              }
          val mergedDataSpec = lastSegment.dataSpec.subrange(/* offset= */ 0, mergedLength)
          segments[lastIndex!!] = Segment(lastSegment.startTimeUs, mergedDataSpec)
        }
      }
      Util.removeRange(segments, /* fromIndex= */ nextOutIndex, /* toIndex= */ segments.size)
    }

    private fun canMergeSegments(dataSpec1: DataSpec, dataSpec2: DataSpec): Boolean {
      return dataSpec1.uri == dataSpec2.uri &&
          dataSpec1.length != C.LENGTH_UNSET.toLong() &&
          dataSpec1.position + dataSpec1.length == dataSpec2.position &&
          Util.areEqual(dataSpec1.key, dataSpec2.key) &&
          dataSpec1.flags == dataSpec2.flags &&
          dataSpec1.httpMethod == dataSpec2.httpMethod &&
          dataSpec1.httpRequestHeaders == dataSpec2.httpRequestHeaders
    }
  }
}
