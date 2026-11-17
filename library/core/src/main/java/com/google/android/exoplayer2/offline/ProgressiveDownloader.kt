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

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheWriter
import com.google.android.exoplayer2.util.PriorityTaskManager
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException
import com.google.android.exoplayer2.util.RunnableFutureTask
import com.google.android.exoplayer2.util.Util
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/** A downloader for progressive media streams. */
class ProgressiveDownloader @JvmOverloads constructor(
    mediaItem: MediaItem,
    cacheDataSourceFactory: CacheDataSource.Factory,
    private val executor: Executor = Executor { it.run() }
) : Downloader {

  private val dataSpec: DataSpec
  private val dataSource: CacheDataSource
  private val cacheWriter: CacheWriter
  private val priorityTaskManager: PriorityTaskManager?

  private var progressListener: Downloader.ProgressListener? = null
  @Volatile private var downloadRunnable: RunnableFutureTask<Void?, IOException>? = null
  @Volatile private var isCanceled = false

  init {
    val localConfiguration = checkNotNull(mediaItem.localConfiguration)
    dataSpec =
        DataSpec.Builder()
            .setUri(localConfiguration.uri)
            .setKey(localConfiguration.customCacheKey)
            .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
            .build()
    dataSource = cacheDataSourceFactory.createDataSourceForDownloading()
    cacheWriter =
        CacheWriter(
            dataSource,
            dataSpec,
            /* temporaryBuffer= */ null,
            ::onProgress
        )
    priorityTaskManager = cacheDataSourceFactory.upstreamPriorityTaskManager
  }

  @Throws(IOException::class, InterruptedException::class)
  override fun download(progressListener: Downloader.ProgressListener?) {
    this.progressListener = progressListener
    priorityTaskManager?.add(C.PRIORITY_DOWNLOAD)
    try {
      var finished = false
      while (!finished && !isCanceled) {
        // Recreate downloadRunnable on each loop iteration to avoid rethrowing a previous error.
        val runnable =
            object : RunnableFutureTask<Void?, IOException>() {
              @Throws(IOException::class)
              override fun doWork(): Void? {
                cacheWriter.cache()
                return null
              }

              override fun cancelWork() {
                cacheWriter.cancel()
              }
            }
        downloadRunnable = runnable
        priorityTaskManager?.proceed(C.PRIORITY_DOWNLOAD)
        executor.execute(runnable)
        try {
          runnable.get()
          finished = true
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
        }
      }
    } finally {
      checkNotNull(downloadRunnable).blockUntilFinished()
      priorityTaskManager?.remove(C.PRIORITY_DOWNLOAD)
    }
  }

  override fun cancel() {
    isCanceled = true
    val downloadRunnable = this.downloadRunnable
    downloadRunnable?.cancel(/* interruptIfRunning= */ true)
  }

  override fun remove() {
    dataSource.cache.removeResource(dataSource.cacheKeyFactory.buildCacheKey(dataSpec))
  }

  private fun onProgress(contentLength: Long, bytesCached: Long, newBytesCached: Long) {
    val progressListener = this.progressListener ?: return
    val percentDownloaded: Float =
        if (contentLength == C.LENGTH_UNSET.toLong() || contentLength == 0L) {
          C.PERCENTAGE_UNSET.toFloat()
        } else {
          (bytesCached * 100f) / contentLength
        }
    progressListener.onProgress(contentLength, bytesCached, percentDownloaded)
  }
}
