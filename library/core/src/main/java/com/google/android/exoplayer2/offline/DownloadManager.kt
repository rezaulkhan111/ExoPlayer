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

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import androidx.annotation.IntRange
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.offline.Download.Companion.FAILURE_REASON_NONE
import com.google.android.exoplayer2.offline.Download.Companion.FAILURE_REASON_UNKNOWN
import com.google.android.exoplayer2.offline.Download.Companion.STATE_COMPLETED
import com.google.android.exoplayer2.offline.Download.Companion.STATE_DOWNLOADING
import com.google.android.exoplayer2.offline.Download.Companion.STATE_FAILED
import com.google.android.exoplayer2.offline.Download.Companion.STATE_QUEUED
import com.google.android.exoplayer2.offline.Download.Companion.STATE_REMOVING
import com.google.android.exoplayer2.offline.Download.Companion.STATE_RESTARTING
import com.google.android.exoplayer2.offline.Download.Companion.STATE_STOPPED
import com.google.android.exoplayer2.offline.Download.Companion.STOP_REASON_NONE
import com.google.android.exoplayer2.scheduler.Requirements
import com.google.android.exoplayer2.scheduler.RequirementsWatcher
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor

/**
 * Manages downloads.
 *
 * Normally a download manager should be accessed via a [DownloadService]. When a download
 * manager is used directly instead, downloads will be initially paused and so must be resumed by
 * calling [resumeDownloads].
 *
 * A download manager instance must be accessed only from the thread that created it, unless that
 * thread does not have a [Looper]. In that case, it must be accessed only from the
 * application's main thread. Registered listeners will be called on the same thread. In all cases
 * the `Looper` of the thread from which the manager must be accessed can be queried using
 * [getApplicationLooper].
 */
class DownloadManager {

  /** Listener for [DownloadManager] events. */
  interface Listener {
    /**
     * Called when all downloads have been restored.
     *
     * @param downloadManager The reporting instance.
     */
    fun onInitialized(downloadManager: DownloadManager) {}

    /**
     * Called when downloads are [paused][pauseDownloads] or [resumed][resumeDownloads].
     *
     * @param downloadManager The reporting instance.
     * @param downloadsPaused Whether downloads are currently paused.
     */
    fun onDownloadsPausedChanged(
        downloadManager: DownloadManager,
        downloadsPaused: Boolean
    ) {}

    /**
     * Called when the state of a download changes.
     *
     * @param downloadManager The reporting instance.
     * @param download The state of the download.
     * @param finalException If the download is transitioning to [Download.STATE_FAILED], this is the
     *     final exception that resulted in the failure.
     */
    fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?
    ) {}

    /**
     * Called when a download is removed.
     *
     * @param downloadManager The reporting instance.
     * @param download The last state of the download before it was removed.
     */
    fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {}

    /**
     * Called when there is no active download left.
     *
     * @param downloadManager The reporting instance.
     */
    fun onIdle(downloadManager: DownloadManager) {}

    /**
     * Called when the download requirements state changed.
     *
     * @param downloadManager The reporting instance.
     * @param requirements Requirements needed to be met to start downloads.
     * @param notMetRequirements [RequirementFlags][Requirements.RequirementFlags] that are not met,
     *     or 0.
     */
    fun onRequirementsStateChanged(
        downloadManager: DownloadManager,
        requirements: Requirements,
        @Requirements.RequirementFlags notMetRequirements: Int
    ) {}

    /**
     * Called when there is a change in whether this manager has one or more downloads that are not
     * progressing for the sole reason that the [Requirements] are not met. See
     * [isWaitingForRequirements] for more information.
     *
     * @param downloadManager The reporting instance.
     * @param waitingForRequirements Whether this manager has one or more downloads that are not
     *     progressing for the sole reason that the [Requirements] are not met.
     */
    fun onWaitingForRequirementsChanged(
        downloadManager: DownloadManager,
        waitingForRequirements: Boolean
    ) {}
  }

  private val context: Context
  val downloadIndex: WritableDownloadIndex
  private val applicationHandler: Handler
  private val internalHandler: InternalHandler
  private val requirementsListener: RequirementsWatcher.Listener
  private val listeners: CopyOnWriteArraySet<Listener>

  private var pendingMessages: Int = 0
  private var activeTaskCount: Int = 0

  /** Returns whether the manager has completed initialization. */
  var isInitialized: Boolean = false
    private set

  /** Returns whether downloads are currently paused. */
  var downloadsPaused: Boolean = true
    private set

  /** Returns the maximum number of parallel downloads. */
  var maxParallelDownloads: Int = DEFAULT_MAX_PARALLEL_DOWNLOADS
    private set

  /**
   * Returns the minimum number of times that a download will be retried. A download will fail if
   * the specified number of retries is exceeded without any progress being made.
   */
  var minRetryCount: Int = DEFAULT_MIN_RETRY_COUNT
    private set

  /**
   * Returns the requirements needed for downloads to progress that are not currently met.
   *
   * @return The not met [Requirements.RequirementFlags], or 0 if all requirements are met.
   */
  @get:Requirements.RequirementFlags
  var notMetRequirements: Int = 0
    private set

  /**
   * Returns whether this manager has one or more downloads that are not progressing for the sole
   * reason that the [Requirements] are not met. This is true if:
   *
   * * The [Requirements] are not met.
   * * The downloads are not paused (i.e. [downloadsPaused] is `false`).
   * * There are downloads in the [Download.STATE_QUEUED] state.
   */
  var isWaitingForRequirements: Boolean = false
    private set

  /**
   * Returns current downloads. Downloads that are in terminal states (i.e. completed or failed) are
   * not included. To query all downloads including those in terminal states, use [downloadIndex] instead.
   */
  var currentDownloads: List<Download> = Collections.emptyList()
    private set

  private var requirementsWatcher: RequirementsWatcher

  /**
   * Constructs a [DownloadManager].
   *
   * @param context Any context.
   * @param databaseProvider Provides the SQLite database in which downloads are persisted.
   * @param cache A cache to be used to store downloaded data. The cache should be configured with
   *     an [CacheEvictor][com.google.android.exoplayer2.upstream.cache.CacheEvictor] that will not evict downloaded content, for example
   *     [NoOpCacheEvictor][com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor].
   * @param upstreamFactory A [Factory][DataSource.Factory] for creating [DataSource]s for downloading data.
   */
  @Deprecated("Use DownloadManager(Context, DatabaseProvider, Cache, Factory, Executor).")
  constructor(
      context: Context,
      databaseProvider: DatabaseProvider,
      cache: Cache,
      upstreamFactory: DataSource.Factory
  ) : this(context, databaseProvider, cache, upstreamFactory, Executor { it.run() })

  /**
   * Constructs a [DownloadManager].
   *
   * @param context Any context.
   * @param databaseProvider Provides the SQLite database in which downloads are persisted.
   * @param cache A cache to be used to store downloaded data. The cache should be configured with
   *     an [CacheEvictor][com.google.android.exoplayer2.upstream.cache.CacheEvictor] that will not evict downloaded content, for example
   *     [NoOpCacheEvictor][com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor].
   * @param upstreamFactory A [Factory][DataSource.Factory] for creating [DataSource]s for downloading data.
   * @param executor An [Executor] used to download data. Passing `Runnable::run` will cause each
   *     download task to download data on its own thread. Passing an [Executor] that uses multiple
   *     threads will speed up download tasks that can be split into smaller parts for parallel
   *     execution.
   */
  constructor(
      context: Context,
      databaseProvider: DatabaseProvider,
      cache: Cache,
      upstreamFactory: DataSource.Factory,
      executor: Executor
  ) : this(
      context,
      DefaultDownloadIndex(databaseProvider),
      DefaultDownloaderFactory(
          CacheDataSource.Factory()
              .setCache(cache)
              .setUpstreamDataSourceFactory(upstreamFactory),
          executor
      )
  )

  /**
   * Constructs a [DownloadManager].
   *
   * @param context Any context.
   * @param downloadIndex The download index used to hold the download information.
   * @param downloaderFactory A factory for creating [Downloader]s.
   */
  constructor(
      context: Context,
      downloadIndex: WritableDownloadIndex,
      downloaderFactory: DownloaderFactory
  ) {
    this.context = context.applicationContext
    this.downloadIndex = downloadIndex

    maxParallelDownloads = DEFAULT_MAX_PARALLEL_DOWNLOADS
    minRetryCount = DEFAULT_MIN_RETRY_COUNT
    downloadsPaused = true
    currentDownloads = Collections.emptyList()
    listeners = CopyOnWriteArraySet()

    val mainHandler = Util.createHandlerForCurrentOrMainLooper(::handleMainMessage)
    this.applicationHandler = mainHandler
    val internalThread = HandlerThread("ExoPlayer:DownloadManager")
    internalThread.start()
    internalHandler =
        InternalHandler(
            internalThread,
            downloadIndex,
            downloaderFactory,
            mainHandler,
            maxParallelDownloads,
            minRetryCount,
            downloadsPaused
        )

    val requirementsListener = RequirementsWatcher.Listener { watcher, notMetRequirements ->
      onRequirementsStateChanged(watcher, notMetRequirements)
    }
    this.requirementsListener = requirementsListener
    requirementsWatcher =
        RequirementsWatcher(context, requirementsListener, DEFAULT_REQUIREMENTS)
    notMetRequirements = requirementsWatcher.start()

    pendingMessages = 1
    internalHandler
        .obtainMessage(MSG_INITIALIZE, notMetRequirements, /* unused */ 0)
        .sendToTarget()
  }

  /**
   * Returns the [Looper] associated with the application thread that's used to access the
   * manager, and on which the manager will call its [Listeners][Listener].
   */
  fun getApplicationLooper(): Looper {
    return applicationHandler.looper
  }

  /**
   * Returns whether the manager is currently idle. The manager is idle if all downloads are in a
   * terminal state (i.e. completed or failed), or if no progress can be made (e.g. because the
   * download requirements are not met).
   */
  fun isIdle(): Boolean {
    return activeTaskCount == 0 && pendingMessages == 0
  }

  /**
   * Adds a [Listener].
   *
   * @param listener The listener to be added.
   */
  fun addListener(listener: Listener) {
    Assertions.checkNotNull(listener)
    listeners.add(listener)
  }

  /**
   * Removes a [Listener].
   *
   * @param listener The listener to be removed.
   */
  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  /** Returns the requirements needed to be met to progress. */
  fun getRequirements(): Requirements {
    return requirementsWatcher.requirements
  }

  /**
   * Sets the requirements that need to be met for downloads to progress.
   *
   * @param requirements A [Requirements].
   */
  fun setRequirements(requirements: Requirements) {
    if (requirements == requirementsWatcher.requirements) {
      return
    }
    requirementsWatcher.stop()
    requirementsWatcher = RequirementsWatcher(context, requirementsListener, requirements)
    val notMetRequirements = requirementsWatcher.start()
    onRequirementsStateChanged(requirementsWatcher, notMetRequirements)
  }

  /**
   * Sets the maximum number of parallel downloads.
   *
   * @param maxParallelDownloads The maximum number of parallel downloads. Must be greater than 0.
   */
  fun setMaxParallelDownloads(@IntRange(from = 1) maxParallelDownloads: Int) {
    Assertions.checkArgument(maxParallelDownloads > 0)
    if (this.maxParallelDownloads == maxParallelDownloads) {
      return
    }
    this.maxParallelDownloads = maxParallelDownloads
    pendingMessages++
    internalHandler
        .obtainMessage(MSG_SET_MAX_PARALLEL_DOWNLOADS, maxParallelDownloads, /* unused */ 0)
        .sendToTarget()
  }

  /**
   * Sets the minimum number of times that a download will be retried. A download will fail if the
   * specified number of retries is exceeded without any progress being made.
   *
   * @param minRetryCount The minimum number of times that a download will be retried.
   */
  fun setMinRetryCount(minRetryCount: Int) {
    Assertions.checkArgument(minRetryCount >= 0)
    if (this.minRetryCount == minRetryCount) {
      return
    }
    this.minRetryCount = minRetryCount
    pendingMessages++
    internalHandler
        .obtainMessage(MSG_SET_MIN_RETRY_COUNT, minRetryCount, /* unused */ 0)
        .sendToTarget()
  }

  /**
   * Resumes downloads.
   *
   * If the [Requirements][setRequirements] are met up to [maxParallelDownloads] will be started,
   * excluding those with non-zero [stopReasons][Download.stopReason].
   */
  fun resumeDownloads() {
    setDownloadsPaused(/* downloadsPaused= */ false)
  }

  /**
   * Pauses downloads. Downloads that would otherwise be making progress will transition to [Download.STATE_QUEUED].
   */
  fun pauseDownloads() {
    setDownloadsPaused(/* downloadsPaused= */ true)
  }

  /**
   * Sets the stop reason for one or all downloads. To clear the stop reason, pass [Download.STOP_REASON_NONE].
   *
   * @param id The content id of the download to update, or `null` to set the stop reason for all
   *     downloads.
   * @param stopReason The stop reason, or [Download.STOP_REASON_NONE].
   */
  fun setStopReason(id: String?, stopReason: Int) {
    pendingMessages++
    internalHandler
        .obtainMessage(MSG_SET_STOP_REASON, stopReason, /* unused */ 0, id)
        .sendToTarget()
  }

  /**
   * Adds a download defined by the given request.
   *
   * @param request The download request.
   */
  fun addDownload(request: DownloadRequest) {
    addDownload(request, STOP_REASON_NONE)
  }

  /**
   * Adds a download defined by the given request and with the specified stop reason.
   *
   * @param request The download request.
   * @param stopReason An initial stop reason for the download, or [Download.STOP_REASON_NONE] if the
   *     download should be started.
   */
  fun addDownload(request: DownloadRequest, stopReason: Int) {
    pendingMessages++
    internalHandler
        .obtainMessage(MSG_ADD_DOWNLOAD, stopReason, /* unused */ 0, request)
        .sendToTarget()
  }

  /**
   * Cancels the download with the [id] and removes all downloaded data.
   *
   * @param id The unique content id of the download to be started.
   */
  fun removeDownload(id: String) {
    pendingMessages++
    internalHandler.obtainMessage(MSG_REMOVE_DOWNLOAD, id).sendToTarget()
  }

  /** Cancels all pending downloads and removes all downloaded data. */
  fun removeAllDownloads() {
    pendingMessages++
    internalHandler.obtainMessage(MSG_REMOVE_ALL_DOWNLOADS).sendToTarget()
  }

  /**
   * Stops the downloads and releases resources. Waits until the downloads are persisted to the
   * download index. The manager must not be accessed after this method has been called.
   */
  fun release() {
    synchronized(internalHandler) {
      if (internalHandler.released) {
        return
      }
      internalHandler.sendEmptyMessage(MSG_RELEASE)
      var wasInterrupted = false
      while (!internalHandler.released) {
        try {
          (internalHandler as java.lang.Object).wait()
        } catch (_: InterruptedException) {
          wasInterrupted = true
        }
      }
      if (wasInterrupted) {
        Thread.currentThread().interrupt()
      }
      applicationHandler.removeCallbacksAndMessages(/* token= */ null)
      requirementsWatcher.stop()
      currentDownloads = Collections.emptyList()
      pendingMessages = 0
      activeTaskCount = 0
      isInitialized = false
      notMetRequirements = 0
      isWaitingForRequirements = false
    }
  }

  private fun setDownloadsPaused(downloadsPaused: Boolean) {
    if (this.downloadsPaused == downloadsPaused) {
      return
    }
    this.downloadsPaused = downloadsPaused
    pendingMessages++
    internalHandler
        .obtainMessage(MSG_SET_DOWNLOADS_PAUSED, if (downloadsPaused) 1 else 0, /* unused */ 0)
        .sendToTarget()
    val waitingForRequirementsChanged = updateWaitingForRequirements()
    for (listener in listeners) {
      listener.onDownloadsPausedChanged(this, downloadsPaused)
    }
    if (waitingForRequirementsChanged) {
      notifyWaitingForRequirementsChanged()
    }
  }

  private fun onRequirementsStateChanged(
      requirementsWatcher: RequirementsWatcher,
      @Requirements.RequirementFlags notMetRequirements: Int
  ) {
    val requirements = requirementsWatcher.requirements
    if (this.notMetRequirements != notMetRequirements) {
      this.notMetRequirements = notMetRequirements
      pendingMessages++
      internalHandler
          .obtainMessage(MSG_SET_NOT_MET_REQUIREMENTS, notMetRequirements, /* unused */ 0)
          .sendToTarget()
    }
    val waitingForRequirementsChanged = updateWaitingForRequirements()
    for (listener in listeners) {
      listener.onRequirementsStateChanged(this, requirements, notMetRequirements)
    }
    if (waitingForRequirementsChanged) {
      notifyWaitingForRequirementsChanged()
    }
  }

  private fun updateWaitingForRequirements(): Boolean {
    var waitingForRequirements = false
    if (!downloadsPaused && notMetRequirements != 0) {
      for (i in currentDownloads.indices) {
        if (currentDownloads[i].state == STATE_QUEUED) {
          waitingForRequirements = true
          break
        }
      }
    }
    val waitingForRequirementsChanged = this.isWaitingForRequirements != waitingForRequirements
    this.isWaitingForRequirements = waitingForRequirements
    return waitingForRequirementsChanged
  }

  private fun notifyWaitingForRequirementsChanged() {
    for (listener in listeners) {
      listener.onWaitingForRequirementsChanged(this, isWaitingForRequirements)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun handleMainMessage(message: Message): Boolean {
    when (message.what) {
      MSG_INITIALIZED -> {
        val downloads = message.obj as List<Download>
        onInitialized(downloads)
      }
      MSG_DOWNLOAD_UPDATE -> {
        val update = message.obj as DownloadUpdate
        onDownloadUpdate(update)
      }
      MSG_PROCESSED -> {
        val processedMessageCount = message.arg1
        val activeTaskCount = message.arg2
        onMessageProcessed(processedMessageCount, activeTaskCount)
      }
      else -> throw IllegalStateException()
    }
    return true
  }

  private fun onInitialized(downloads: List<Download>) {
    isInitialized = true
    currentDownloads = Collections.unmodifiableList(downloads)
    val waitingForRequirementsChanged = updateWaitingForRequirements()
    for (listener in listeners) {
      listener.onInitialized(this)
    }
    if (waitingForRequirementsChanged) {
      notifyWaitingForRequirementsChanged()
    }
  }

  private fun onDownloadUpdate(update: DownloadUpdate) {
    currentDownloads = Collections.unmodifiableList(update.downloads)
    val updatedDownload = update.download
    val waitingForRequirementsChanged = updateWaitingForRequirements()
    if (update.isRemove) {
      for (listener in listeners) {
        listener.onDownloadRemoved(this, updatedDownload)
      }
    } else {
      for (listener in listeners) {
        listener.onDownloadChanged(this, updatedDownload, update.finalException)
      }
    }
    if (waitingForRequirementsChanged) {
      notifyWaitingForRequirementsChanged()
    }
  }

  private fun onMessageProcessed(processedMessageCount: Int, activeTaskCount: Int) {
    this.pendingMessages -= processedMessageCount
    this.activeTaskCount = activeTaskCount
    if (isIdle()) {
      for (listener in listeners) {
        listener.onIdle(this)
      }
    }
  }

  private class InternalHandler(
      private val thread: HandlerThread,
      private val downloadIndex: WritableDownloadIndex,
      private val downloaderFactory: DownloaderFactory,
      private val mainHandler: Handler,
      private var maxParallelDownloads: Int,
      private var minRetryCount: Int,
      private var downloadsPaused: Boolean
  ) : Handler(thread.looper) {

    @Volatile var released: Boolean = false

    private val downloads: ArrayList<Download> = ArrayList()
    private val activeTasks: HashMap<String, Task> = HashMap()
    private var notMetRequirements: Int = 0
    private var activeDownloadTaskCount: Int = 0
    private var hasActiveRemoveTask: Boolean = false

    override fun handleMessage(message: Message) {
      var processedExternalMessage = true
      when (message.what) {
        MSG_INITIALIZE -> {
          val notMetRequirements = message.arg1
          initialize(notMetRequirements)
        }
        MSG_SET_DOWNLOADS_PAUSED -> {
          val downloadsPaused = message.arg1 != 0
          setDownloadsPaused(downloadsPaused)
        }
        MSG_SET_NOT_MET_REQUIREMENTS -> {
          val notMetRequirements = message.arg1
          setNotMetRequirements(notMetRequirements)
        }
        MSG_SET_STOP_REASON -> {
          val id = message.obj as String?
          val stopReason = message.arg1
          setStopReason(id, stopReason)
        }
        MSG_SET_MAX_PARALLEL_DOWNLOADS -> {
          val maxParallelDownloads = message.arg1
          setMaxParallelDownloads(maxParallelDownloads)
        }
        MSG_SET_MIN_RETRY_COUNT -> {
          val minRetryCount = message.arg1
          setMinRetryCount(minRetryCount)
        }
        MSG_ADD_DOWNLOAD -> {
          val request = message.obj as DownloadRequest
          val stopReason = message.arg1
          addDownload(request, stopReason)
        }
        MSG_REMOVE_DOWNLOAD -> {
          val id = message.obj as String
          removeDownload(id)
        }
        MSG_REMOVE_ALL_DOWNLOADS -> {
          removeAllDownloads()
        }
        MSG_TASK_STOPPED -> {
          val task = message.obj as Task
          onTaskStopped(task)
          processedExternalMessage = false
        }
        MSG_CONTENT_LENGTH_CHANGED -> {
          val task = message.obj as Task
          onContentLengthChanged(task, Util.toLong(message.arg1, message.arg2))
          return
        }
        MSG_UPDATE_PROGRESS -> {
          updateProgress()
          return
        }
        MSG_RELEASE -> {
          release()
          return
        }
        else -> throw IllegalStateException()
      }
      mainHandler
          .obtainMessage(MSG_PROCESSED, if (processedExternalMessage) 1 else 0, activeTasks.size)
          .sendToTarget()
    }

    private fun initialize(notMetRequirements: Int) {
      this.notMetRequirements = notMetRequirements
      var cursor: DownloadCursor? = null
      try {
        downloadIndex.setDownloadingStatesToQueued()
        cursor =
            downloadIndex.getDownloads(
                STATE_QUEUED, STATE_STOPPED, STATE_DOWNLOADING, STATE_REMOVING, STATE_RESTARTING
            )
        if (cursor != null) {
          while (cursor.moveToNext()) {
            val download = cursor.getDownload()
            if (download != null) {
              downloads.add(download)
            }
          }
        }
      } catch (e: IOException) {
        Log.e(TAG, "Failed to load index.", e)
        downloads.clear()
      } finally {
        Util.closeQuietly(cursor)
      }
      val downloadsForMessage = ArrayList(downloads)
      mainHandler.obtainMessage(MSG_INITIALIZED, downloadsForMessage).sendToTarget()
      syncTasks()
    }

    private fun setDownloadsPaused(downloadsPaused: Boolean) {
      this.downloadsPaused = downloadsPaused
      syncTasks()
    }

    private fun setNotMetRequirements(@Requirements.RequirementFlags notMetRequirements: Int) {
      this.notMetRequirements = notMetRequirements
      syncTasks()
    }

    private fun setStopReason(id: String?, stopReason: Int) {
      if (id == null) {
        for (i in downloads.indices) {
          setStopReason(downloads[i], stopReason)
        }
        try {
          downloadIndex.setStopReason(stopReason)
        } catch (e: IOException) {
          Log.e(TAG, "Failed to set manual stop reason", e)
        }
      } else {
        val download = getDownload(id, /* loadFromIndex= */ false)
        if (download != null) {
          setStopReason(download, stopReason)
        } else {
          try {
            downloadIndex.setStopReason(id, stopReason)
          } catch (e: IOException) {
            Log.e(TAG, "Failed to set manual stop reason: $id", e)
          }
        }
      }
      syncTasks()
    }

    private fun setStopReason(download: Download, stopReason: Int) {
      if (stopReason == STOP_REASON_NONE) {
        if (download.state == STATE_STOPPED) {
          putDownloadWithState(download, STATE_QUEUED, STOP_REASON_NONE)
        }
      } else if (stopReason != download.stopReason) {
        @Download.State var state = download.state
        if (state == STATE_QUEUED || state == STATE_DOWNLOADING) {
          state = STATE_STOPPED
        }
        putDownload(
            Download(
                download.request,
                state,
                download.startTimeMs,
                /* updateTimeMs= */ System.currentTimeMillis(),
                download.contentLength,
                stopReason,
                FAILURE_REASON_NONE,
                download.progress
            )
        )
      }
    }

    private fun setMaxParallelDownloads(maxParallelDownloads: Int) {
      this.maxParallelDownloads = maxParallelDownloads
      syncTasks()
    }

    private fun setMinRetryCount(minRetryCount: Int) {
      this.minRetryCount = minRetryCount
    }

    private fun addDownload(request: DownloadRequest, stopReason: Int) {
      val download = getDownload(request.id, /* loadFromIndex= */ true)
      val nowMs = System.currentTimeMillis()
      if (download != null) {
        putDownload(mergeRequest(download, request, stopReason, nowMs))
      } else {
        putDownload(
            Download(
                request,
                if (stopReason != STOP_REASON_NONE) STATE_STOPPED else STATE_QUEUED,
                /* startTimeMs= */ nowMs,
                /* updateTimeMs= */ nowMs,
                /* contentLength= */ C.LENGTH_UNSET.toLong(),
                stopReason,
                FAILURE_REASON_NONE
            )
        )
      }
      syncTasks()
    }

    private fun removeDownload(id: String) {
      val download = getDownload(id, /* loadFromIndex= */ true)
      if (download == null) {
        Log.e(TAG, "Failed to remove nonexistent download: $id")
        return
      }
      putDownloadWithState(download, STATE_REMOVING, STOP_REASON_NONE)
      syncTasks()
    }

    private fun removeAllDownloads() {
      val terminalDownloads = ArrayList<Download>()
      try {
        val cursor = downloadIndex.getDownloads(STATE_COMPLETED, STATE_FAILED)
        if (cursor != null) {
          cursor.use {
            while (it.moveToNext()) {
              val download = it.getDownload()
              if (download != null) {
                terminalDownloads.add(download)
              }
            }
          }
        }
      } catch (e: IOException) {
        Log.e(TAG, "Failed to load downloads.")
      }
      for (i in downloads.indices) {
        downloads[i] = copyDownloadWithState(downloads[i], STATE_REMOVING, STOP_REASON_NONE)
      }
      for (i in terminalDownloads.indices) {
        downloads.add(
            copyDownloadWithState(terminalDownloads[i], STATE_REMOVING, STOP_REASON_NONE)
        )
      }
      Collections.sort(downloads, Companion::compareStartTimes)
      try {
        downloadIndex.setStatesToRemoving()
      } catch (e: IOException) {
        Log.e(TAG, "Failed to update index.", e)
      }
      val updateList = ArrayList(downloads)
      for (i in downloads.indices) {
        val update =
            DownloadUpdate(
                downloads[i], /* isRemove= */ false, updateList, /* finalException= */ null
            )
        mainHandler.obtainMessage(MSG_DOWNLOAD_UPDATE, update).sendToTarget()
      }
      syncTasks()
    }

    private fun release() {
      for (task in activeTasks.values) {
        task.cancel(/* released= */ true)
      }
      try {
        downloadIndex.setDownloadingStatesToQueued()
      } catch (e: IOException) {
        Log.e(TAG, "Failed to update index.", e)
      }
      downloads.clear()
      thread.quit()
      synchronized(this) {
        released = true
        (this as Object).notifyAll()
      }
    }

    private fun syncTasks() {
      var accumulatingDownloadTaskCount = 0
      for (i in downloads.indices) {
        val download = downloads[i]
        var activeTask = activeTasks[download.request.id]
        when (download.state) {
          STATE_STOPPED -> syncStoppedDownload(activeTask)
          STATE_QUEUED -> activeTask = syncQueuedDownload(activeTask, download)
          STATE_DOWNLOADING -> {
            Assertions.checkNotNull(activeTask)
            syncDownloadingDownload(activeTask!!, download, accumulatingDownloadTaskCount)
          }
          STATE_REMOVING, STATE_RESTARTING -> syncRemovingDownload(activeTask, download)
          STATE_COMPLETED, STATE_FAILED -> throw IllegalStateException()
          else -> throw IllegalStateException()
        }
        if (activeTask != null && !activeTask.isRemove) {
          accumulatingDownloadTaskCount++
        }
      }
    }

    private fun syncStoppedDownload(activeTask: Task?) {
      if (activeTask != null) {
        Assertions.checkState(!activeTask.isRemove)
        activeTask.cancel(/* released= */ false)
      }
    }

    private fun syncQueuedDownload(activeTask: Task?, download: Download): Task? {
      var activeTask = activeTask
      if (activeTask != null) {
        Assertions.checkState(!activeTask.isRemove)
        activeTask.cancel(/* released= */ false)
        return activeTask
      }

      if (!canDownloadsRun() || activeDownloadTaskCount >= maxParallelDownloads) {
        return null
      }

      val updatedDownload = putDownloadWithState(download, STATE_DOWNLOADING, STOP_REASON_NONE)
      val downloader = downloaderFactory.createDownloader(updatedDownload.request)
      if (downloader == null) {
        return null
      }
      activeTask =
          Task(
              updatedDownload.request,
              downloader,
              updatedDownload.progress,
              /* isRemove= */ false,
              minRetryCount,
              /* internalHandler= */ this
          )
      activeTasks[updatedDownload.request.id] = activeTask
      if (activeDownloadTaskCount++ == 0) {
        sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, UPDATE_PROGRESS_INTERVAL_MS.toLong())
      }
      activeTask.start()
      return activeTask
    }

    private fun syncDownloadingDownload(
        activeTask: Task,
        download: Download,
        accumulatingDownloadTaskCount: Int
    ) {
      Assertions.checkState(!activeTask.isRemove)
      if (!canDownloadsRun() || accumulatingDownloadTaskCount >= maxParallelDownloads) {
        putDownloadWithState(download, STATE_QUEUED, STOP_REASON_NONE)
        activeTask.cancel(/* released= */ false)
      }
    }

    private fun syncRemovingDownload(activeTask: Task?, download: Download) {
      var activeTask = activeTask
      if (activeTask != null) {
        if (!activeTask.isRemove) {
          activeTask.cancel(/* released= */ false)
        }
        return
      }

      if (hasActiveRemoveTask) {
        return
      }

      val downloader = downloaderFactory.createDownloader(download.request)
      if (downloader == null) {
        return
      }
      activeTask =
          Task(
              download.request,
              downloader,
              download.progress,
              /* isRemove= */ true,
              minRetryCount,
              /* internalHandler= */ this
          )
      activeTasks[download.request.id] = activeTask
      hasActiveRemoveTask = true
      activeTask.start()
    }

    private fun onContentLengthChanged(task: Task, contentLength: Long) {
      val downloadId = task.request.id
      val download = Assertions.checkNotNull(getDownload(downloadId, /* loadFromIndex= */ false))
      if (download == null || contentLength == download.contentLength || contentLength == C.LENGTH_UNSET.toLong()) {
        return
      }
      putDownload(
          Download(
              download.request,
              download.state,
              download.startTimeMs,
              /* updateTimeMs= */ System.currentTimeMillis(),
              contentLength,
              download.stopReason,
              download.failureReason,
              download.progress
          )
      )
    }

    private fun onTaskStopped(task: Task) {
      val downloadId = task.request.id
      activeTasks.remove(downloadId)

      val isRemove = task.isRemove
      if (isRemove) {
        hasActiveRemoveTask = false
      } else if (--activeDownloadTaskCount == 0) {
        removeMessages(MSG_UPDATE_PROGRESS)
      }

      if (task.isCanceled) {
        syncTasks()
        return
      }

      val finalException = task.finalException
      if (finalException != null) {
        Log.e(TAG, "Task failed: " + task.request + ", " + isRemove, finalException)
      }

      val download = Assertions.checkNotNull(getDownload(downloadId, /* loadFromIndex= */ false))!!
      when (download.state) {
        STATE_DOWNLOADING -> {
          Assertions.checkState(!isRemove)
          onDownloadTaskStopped(download, finalException)
        }
        STATE_REMOVING, STATE_RESTARTING -> {
          Assertions.checkState(isRemove)
          onRemoveTaskStopped(download)
        }
        STATE_QUEUED, STATE_STOPPED, STATE_COMPLETED, STATE_FAILED -> throw IllegalStateException()
        else -> throw IllegalStateException()
      }

      syncTasks()
    }

    private fun onDownloadTaskStopped(download: Download, finalException: Exception?) {
      val updatedDownload =
          Download(
              download.request,
              if (finalException == null) STATE_COMPLETED else STATE_FAILED,
              download.startTimeMs,
              /* updateTimeMs= */ System.currentTimeMillis(),
              download.contentLength,
              download.stopReason,
              if (finalException == null) FAILURE_REASON_NONE else FAILURE_REASON_UNKNOWN,
              download.progress
          )
      val removeIndex = getDownloadIndex(updatedDownload.request.id)
      if (removeIndex != C.INDEX_UNSET) {
        downloads.removeAt(removeIndex)
      }
      try {
        downloadIndex.putDownload(updatedDownload)
      } catch (e: IOException) {
        Log.e(TAG, "Failed to update index.", e)
      }
      val update =
          DownloadUpdate(
              updatedDownload, /* isRemove= */ false, ArrayList(downloads), finalException
          )
      mainHandler.obtainMessage(MSG_DOWNLOAD_UPDATE, update).sendToTarget()
    }

    private fun onRemoveTaskStopped(download: Download) {
      if (download.state == STATE_RESTARTING) {
        @Download.State
        val state = if (download.stopReason == STOP_REASON_NONE) STATE_QUEUED else STATE_STOPPED
        putDownloadWithState(download, state, download.stopReason)
        syncTasks()
      } else {
        val removeIndex = getDownloadIndex(download.request.id)
        if (removeIndex != C.INDEX_UNSET) {
          downloads.removeAt(removeIndex)
        }
        try {
          downloadIndex.removeDownload(download.request.id)
        } catch (e: IOException) {
          Log.e(TAG, "Failed to remove from database")
        }
        val update =
            DownloadUpdate(
                download,
                /* isRemove= */ true,
                ArrayList(downloads),
                /* finalException= */ null
            )
        mainHandler.obtainMessage(MSG_DOWNLOAD_UPDATE, update).sendToTarget()
      }
    }

    private fun updateProgress() {
      for (i in downloads.indices) {
        val download = downloads[i]
        if (download.state == STATE_DOWNLOADING) {
          try {
            downloadIndex.putDownload(download)
          } catch (e: IOException) {
            Log.e(TAG, "Failed to update index.", e)
          }
        }
      }
      sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, UPDATE_PROGRESS_INTERVAL_MS.toLong())
    }

    private fun canDownloadsRun(): Boolean {
      return !downloadsPaused && notMetRequirements == 0
    }

    private fun putDownloadWithState(
        download: Download,
        @Download.State state: Int,
        stopReason: Int
    ): Download {
      Assertions.checkState(state != STATE_COMPLETED && state != STATE_FAILED)
      return putDownload(copyDownloadWithState(download, state, stopReason))
    }

    private fun putDownload(download: Download): Download {
      Assertions.checkState(download.state != STATE_COMPLETED && download.state != STATE_FAILED)
      val changedIndex = getDownloadIndex(download.request.id)
      if (changedIndex == C.INDEX_UNSET) {
        downloads.add(download)
        Collections.sort(downloads, Companion::compareStartTimes)
      } else {
        val needsSort = download.startTimeMs != downloads[changedIndex].startTimeMs
        downloads[changedIndex] = download
        if (needsSort) {
          Collections.sort(downloads, Companion::compareStartTimes)
        }
      }
      try {
        downloadIndex.putDownload(download)
      } catch (e: IOException) {
        Log.e(TAG, "Failed to update index.", e)
      }
      val update =
          DownloadUpdate(
              download,
              /* isRemove= */ false,
              ArrayList(downloads),
              /* finalException= */ null
          )
      mainHandler.obtainMessage(MSG_DOWNLOAD_UPDATE, update).sendToTarget()
      return download
    }

    private fun getDownload(id: String, loadFromIndex: Boolean): Download? {
      val index = getDownloadIndex(id)
      if (index != C.INDEX_UNSET) {
        return downloads[index]
      }
      if (loadFromIndex) {
        try {
          return downloadIndex.getDownload(id)
        } catch (e: IOException) {
          Log.e(TAG, "Failed to load download: $id", e)
        }
      }
      return null
    }

    private fun getDownloadIndex(id: String): Int {
      for (i in downloads.indices) {
        val download = downloads[i]
        if (download.request.id == id) {
          return i
        }
      }
      return C.INDEX_UNSET
    }

    private companion object {
      private const val UPDATE_PROGRESS_INTERVAL_MS = 5000

      private fun copyDownloadWithState(
          download: Download,
          @Download.State state: Int,
          stopReason: Int
      ): Download {
        return Download(
            download.request,
            state,
            download.startTimeMs,
            /* updateTimeMs= */ System.currentTimeMillis(),
            download.contentLength,
            stopReason,
            FAILURE_REASON_NONE,
            download.progress
        )
      }

      private fun compareStartTimes(first: Download, second: Download): Int {
        return Util.compareLong(first.startTimeMs, second.startTimeMs)
      }
    }
  }

  private class Task(
      val request: DownloadRequest,
      private val downloader: Downloader,
      private val downloadProgress: DownloadProgress,
      val isRemove: Boolean,
      private val minRetryCount: Int,
      @Volatile private var internalHandler: InternalHandler?
  ) : Thread(), Downloader.ProgressListener {

    @Volatile var isCanceled: Boolean = false
      private set
    var finalException: Exception? = null
      private set
    private var contentLength: Long = C.LENGTH_UNSET.toLong()

    fun cancel(released: Boolean) {
      if (released) {
        internalHandler = null
      }
      if (!isCanceled) {
        isCanceled = true
        downloader.cancel()
        interrupt()
      }
    }

    override fun run() {
      try {
        if (isRemove) {
          downloader.remove()
        } else {
          var errorCount = 0
          var errorPosition = C.LENGTH_UNSET.toLong()
          while (!isCanceled) {
            try {
              downloader.download(this)
              break
            } catch (e: IOException) {
              if (!isCanceled) {
                val bytesDownloaded = downloadProgress.bytesDownloaded
                if (bytesDownloaded != errorPosition) {
                  errorPosition = bytesDownloaded
                  errorCount = 0
                }
                if (++errorCount > minRetryCount) {
                  throw e
                }
                sleep(getRetryDelayMillis(errorCount).toLong())
              }
            }
          }
        }
      } catch (e: InterruptedException) {
        currentThread().interrupt()
      } catch (e: Exception) {
        finalException = e
      }
      val handler = internalHandler
      handler?.obtainMessage(MSG_TASK_STOPPED, this)?.sendToTarget()
    }

    override fun onProgress(contentLength: Long, bytesDownloaded: Long, percentDownloaded: Float) {
      downloadProgress.bytesDownloaded = bytesDownloaded
      downloadProgress.percentDownloaded = percentDownloaded
      if (contentLength != this.contentLength) {
        this.contentLength = contentLength
        val handler = internalHandler
        handler
            ?.obtainMessage(
                MSG_CONTENT_LENGTH_CHANGED,
                (contentLength shr 32).toInt(),
                contentLength.toInt(),
                this
            )
            ?.sendToTarget()
      }
    }

    private companion object {
      private fun getRetryDelayMillis(errorCount: Int): Int {
        return minOf((errorCount - 1) * 1000, 5000)
      }
    }
  }

  private class DownloadUpdate(
      val download: Download,
      val isRemove: Boolean,
      val downloads: List<Download>,
      val finalException: Exception?
  )

  companion object {
    /** The default maximum number of parallel downloads. */
    const val DEFAULT_MAX_PARALLEL_DOWNLOADS = 3

    /** The default minimum number of times a download must be retried before failing. */
    const val DEFAULT_MIN_RETRY_COUNT = 5

    /** The default requirement is that the device has network connectivity. */
    @JvmField
    val DEFAULT_REQUIREMENTS: Requirements = Requirements(Requirements.NETWORK)

    private const val TAG = "DownloadManager"

    // Messages posted to the main handler.
    private const val MSG_INITIALIZED = 0
    private const val MSG_PROCESSED = 1
    private const val MSG_DOWNLOAD_UPDATE = 2

    // Messages posted to the background handler.
    private const val MSG_INITIALIZE = 0
    private const val MSG_SET_DOWNLOADS_PAUSED = 1
    private const val MSG_SET_NOT_MET_REQUIREMENTS = 2
    private const val MSG_SET_STOP_REASON = 3
    private const val MSG_SET_MAX_PARALLEL_DOWNLOADS = 4
    private const val MSG_SET_MIN_RETRY_COUNT = 5
    private const val MSG_ADD_DOWNLOAD = 6
    private const val MSG_REMOVE_DOWNLOAD = 7
    private const val MSG_REMOVE_ALL_DOWNLOADS = 8
    private const val MSG_TASK_STOPPED = 9
    private const val MSG_CONTENT_LENGTH_CHANGED = 10
    private const val MSG_UPDATE_PROGRESS = 11
    private const val MSG_RELEASE = 12

    @JvmStatic
    internal fun mergeRequest(
        download: Download,
        request: DownloadRequest,
        stopReason: Int,
        nowMs: Long
    ): Download {
      @Download.State var state = download.state
      val startTimeMs =
          if (state == STATE_REMOVING || download.isTerminalState()) nowMs else download.startTimeMs
      state = if (state == STATE_RESTARTING || state == STATE_REMOVING) {
        STATE_RESTARTING
      } else if (stopReason != STOP_REASON_NONE) {
        STATE_STOPPED
      } else {
        STATE_QUEUED
      }
      return Download(
          download.request.copyWithMergedRequest(request),
          state,
          startTimeMs,
          /* updateTimeMs= */ nowMs,
          /* contentLength= */ C.LENGTH_UNSET.toLong(),
          stopReason,
          FAILURE_REASON_NONE
      )
    }
  }
}
