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
package com.google.android.exoplayer2.offline

import android.util.SparseArray
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import java.lang.reflect.Constructor
import java.util.concurrent.Executor

/**
 * Default [DownloaderFactory], supporting creation of progressive, DASH, HLS and
 * SmoothStreaming downloaders. Note that for the latter three, the corresponding library module
 * must be built into the application.
 */
class DefaultDownloaderFactory @JvmOverloads constructor(
  private val cacheDataSourceFactory: CacheDataSource.Factory,
  private val executor: Executor = Executor { command -> command.run() },
) : DownloaderFactory {

  init {
    Assertions.checkNotNull(cacheDataSourceFactory)
    Assertions.checkNotNull(executor)
  }

  /**
   * Creates an instance.
   *
   * @param cacheDataSourceFactory A [CacheDataSource.Factory] for the cache into which
   *     downloads will be written.
   * @deprecated Use [DefaultDownloaderFactory(CacheDataSource.Factory, Executor)][DefaultDownloaderFactory].
   */
  @Deprecated("Use DefaultDownloaderFactory(CacheDataSource.Factory, Executor).")
  constructor(cacheDataSourceFactory: CacheDataSource.Factory) : this(
    cacheDataSourceFactory,
    Executor { command -> command.run() },
  )

  override fun createDownloader(request: DownloadRequest?): Downloader {
    val req = Assertions.checkNotNull(request)!!
    @C.ContentType
    val contentType = Util.inferContentTypeForUriAndMimeType(req.uri, req.mimeType)
    return when (contentType) {
      C.CONTENT_TYPE_DASH,
      C.CONTENT_TYPE_HLS,
      C.CONTENT_TYPE_SS,
      -> createDownloader(req, contentType)
      C.CONTENT_TYPE_OTHER ->
        ProgressiveDownloader(
          MediaItem.Builder()
            .setUri(req.uri)
            .setCustomCacheKey(req.customCacheKey)
            .build(),
          cacheDataSourceFactory,
          executor,
        )
      else -> throw IllegalArgumentException("Unsupported type: $contentType")
    }
  }

  private fun createDownloader(request: DownloadRequest, @C.ContentType contentType: Int): Downloader {
    val constructor = CONSTRUCTORS[contentType]
      ?: throw IllegalStateException("Module missing for content type $contentType")
    val mediaItem =
      MediaItem.Builder()
        .setUri(request.uri)
        .setStreamKeys(request.streamKeys)
        .setCustomCacheKey(request.customCacheKey)
        .build()
    return try {
      constructor.newInstance(mediaItem, cacheDataSourceFactory, executor)
    } catch (_: Exception) {
      throw IllegalStateException("Failed to instantiate downloader for content type $contentType")
    }
  }

  companion object {
    private val CONSTRUCTORS: SparseArray<Constructor<out Downloader>> = createDownloaderConstructors()

    private fun createDownloaderConstructors(): SparseArray<Constructor<out Downloader>> {
      val array = SparseArray<Constructor<out Downloader>>()
      try {
        array.put(
          C.CONTENT_TYPE_DASH,
          getDownloaderConstructor(
            Class.forName("com.google.android.exoplayer2.source.dash.offline.DashDownloader"),
          ),
        )
      } catch (_: ClassNotFoundException) {
        // Expected if the app was built without the DASH module.
      }

      try {
        array.put(
          C.CONTENT_TYPE_HLS,
          getDownloaderConstructor(
            Class.forName("com.google.android.exoplayer2.source.hls.offline.HlsDownloader"),
          ),
        )
      } catch (_: ClassNotFoundException) {
        // Expected if the app was built without the HLS module.
      }

      try {
        array.put(
          C.CONTENT_TYPE_SS,
          getDownloaderConstructor(
            Class.forName(
              "com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloader",
            ),
          ),
        )
      } catch (_: ClassNotFoundException) {
        // Expected if the app was built without the SmoothStreaming module.
      }

      return array
    }

    private fun getDownloaderConstructor(clazz: Class<*>): Constructor<out Downloader> {
      return try {
        clazz
          .asSubclass(Downloader::class.java)
          .getConstructor(MediaItem::class.java, CacheDataSource.Factory::class.java, Executor::class.java)
      } catch (e: NoSuchMethodException) {
        // The downloader is present, but the expected constructor is missing.
        throw IllegalStateException("Downloader constructor missing", e)
      }
    }
  }
}
