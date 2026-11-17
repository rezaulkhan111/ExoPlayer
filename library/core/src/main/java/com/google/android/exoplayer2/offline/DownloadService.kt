/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You me obtaining a copy of the License at
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

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.StringRes
import com.google.android.exoplayer2.scheduler.Requirements
import com.google.android.exoplayer2.scheduler.Requirements.RequirementFlags
import com.google.android.exoplayer2.scheduler.Scheduler
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.NotificationUtil
import com.google.android.exoplayer2.util.Util
import java.util.HashMap

/** A [Service] for downloading media. */
abstract class DownloadService : Service {

  private val foregroundNotificationUpdater: ForegroundNotificationUpdater?
  private val channelId: String?
  @StringRes private val channelNameResourceId: Int
  @StringRes private val channelDescriptionResourceId: Int

  private var downloadManagerHelper: DownloadManagerHelper? = null
  private var lastStartId = 0
  private var startedInForeground = false
  private var taskRemoved = false
  private var isStopped = false
  private var isDestroyed = false

  /**
   * Creates a DownloadService.
   *
   * <p>If {@code foregroundNotificationId} is {@link #FOREGROUND_NOTIFICATION_ID_NONE} then the
   * service will only ever run in the background, and no foreground notification will be displayed.
   *
   * <p>If {@code foregroundNotificationId} is not {@link #FOREGROUND_NOTIFICATION_ID_NONE} then the
   * service will run in the foreground. The foreground notification will be updated at least as
   * often as the interval specified by {@link #DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL}.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, or {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE} if the service should only ever run in the background.
   */
  protected constructor(foregroundNotificationId: Int) : this(
      foregroundNotificationId, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL
  )

  /**
   * Creates a DownloadService.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, or {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE} if the service should only ever run in the background.
   * @param foregroundNotificationUpdateInterval The maximum interval between updates to the
   *     foreground notification, in milliseconds. Ignored if {@code foregroundNotificationId} is
   *     {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   */
  protected constructor(
      foregroundNotificationId: Int, foregroundNotificationUpdateInterval: Long
  ) : this(
      foregroundNotificationId,
      foregroundNotificationUpdateInterval,
      /* channelId= */ null,
      /* channelNameResourceId= */ 0,
      /* channelDescriptionResourceId= */ 0
  )

  /**
   * @deprecated Use {@link #DownloadService(int, long, String, int, int)}.
   */
  @Deprecated("Use DownloadService(Int, Long, String, Int, Int)")
  protected constructor(
      foregroundNotificationId: Int,
      foregroundNotificationUpdateInterval: Long,
      channelId: String?,
      @StringRes channelNameResourceId: Int
  ) : this(
      foregroundNotificationId,
      foregroundNotificationUpdateInterval,
      channelId,
      channelNameResourceId,
      /* channelDescriptionResourceId= */ 0
  )

  /**
   * Creates a DownloadService.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, or {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE} if the service should only ever run in the background.
   * @param foregroundNotificationUpdateInterval The maximum interval between updates to the
   *     foreground notification, in milliseconds. Ignored if {@code foregroundNotificationId} is
   *     {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   * @param channelId An id for a low priority notification channel to create, or {@code null} if
   *     the app will take care of creating a notification channel if needed. If specified, must be
   *     unique per package. The value may be truncated if it's too long. Ignored if {@code
   *     foregroundNotificationId} is {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   * @param channelNameResourceId A string resource identifier for the user visible name of the
   *     notification channel. The recommended maximum length is 40 characters. The value may be
   *     truncated if it's too long. Ignored if {@code channelId} is null or if {@code
   *     foregroundNotificationId} is {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   * @param channelDescriptionResourceId A string resource identifier for the user visible
   *     description of the notification channel, or 0 if no description is provided. The
   *     recommended maximum length is 300 characters. The value may be truncated if it is too long.
   *     Ignored if {@code channelId} is null or if {@code foregroundNotificationId} is {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE}.
   */
  protected constructor(
      foregroundNotificationId: Int,
      foregroundNotificationUpdateInterval: Long,
      channelId: String?,
      @StringRes channelNameResourceId: Int,
      @StringRes channelDescriptionResourceId: Int
  ) {
    if (foregroundNotificationId == FOREGROUND_NOTIFICATION_ID_NONE) {
      this.foregroundNotificationUpdater = null
      this.channelId = null
      this.channelNameResourceId = 0
      this.channelDescriptionResourceId = 0
    } else {
      this.foregroundNotificationUpdater =
          ForegroundNotificationUpdater(
              foregroundNotificationId, foregroundNotificationUpdateInterval)
      this.channelId = channelId
      this.channelNameResourceId = channelNameResourceId
      this.channelDescriptionResourceId = channelDescriptionResourceId
    }
  }

  override fun onCreate() {
    if (channelId != null) {
      NotificationUtil.createNotificationChannel(
          this,
          channelId,
          channelNameResourceId,
          channelDescriptionResourceId,
          NotificationUtil.IMPORTANCE_LOW
      )
    }
    val clazz = javaClass
    var downloadManagerHelper = downloadManagerHelpers[clazz]
    if (downloadManagerHelper == null) {
      val foregroundAllowed = foregroundNotificationUpdater != null
      // See https://developer.android.com/about/versions/12/foreground-services.
      val canStartForegroundServiceFromBackground = Util.SDK_INT < 31
      val scheduler =
          if (foregroundAllowed && canStartForegroundServiceFromBackground) getScheduler() else null
      val downloadManager = getDownloadManager()
      downloadManager.resumeDownloads()
      downloadManagerHelper =
          DownloadManagerHelper(
              applicationContext, downloadManager, foregroundAllowed, scheduler, clazz)
      downloadManagerHelpers[clazz] = downloadManagerHelper
    }
    this.downloadManagerHelper = downloadManagerHelper
    downloadManagerHelper.attachService(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    lastStartId = startId
    taskRemoved = false
    var intentAction: String? = null
    var contentId: String? = null
    if (intent != null) {
      intentAction = intent.action
      contentId = intent.getStringExtra(KEY_CONTENT_ID)
      startedInForeground =
          startedInForeground ||
              intent.getBooleanExtra(KEY_FOREGROUND, false) ||
              ACTION_RESTART == intentAction
    }
    // intentAction is null if the service is restarted or no action is specified.
    if (intentAction == null) {
      intentAction = ACTION_INIT
    }
    val downloadManager = Assertions.checkNotNull(downloadManagerHelper)!!.downloadManager
    when (intentAction) {
      ACTION_INIT, ACTION_RESTART -> {
        // Do nothing.
      }
      ACTION_ADD_DOWNLOAD -> {
        val downloadRequest: DownloadRequest? =
            Assertions.checkNotNull(intent)!!.getParcelableExtra(KEY_DOWNLOAD_REQUEST)
        if (downloadRequest == null) {
          Log.e(TAG, "Ignored ADD_DOWNLOAD: Missing $KEY_DOWNLOAD_REQUEST extra")
        } else {
          val stopReason = intent!!.getIntExtra(KEY_STOP_REASON, Download.STOP_REASON_NONE)
          downloadManager.addDownload(downloadRequest, stopReason)
        }
      }
      ACTION_REMOVE_DOWNLOAD -> {
        if (contentId == null) {
          Log.e(TAG, "Ignored REMOVE_DOWNLOAD: Missing $KEY_CONTENT_ID extra")
        } else {
          downloadManager.removeDownload(contentId)
        }
      }
      ACTION_REMOVE_ALL_DOWNLOADS -> downloadManager.removeAllDownloads()
      ACTION_RESUME_DOWNLOADS -> downloadManager.resumeDownloads()
      ACTION_PAUSE_DOWNLOADS -> downloadManager.pauseDownloads()
      ACTION_SET_STOP_REASON -> {
        if (!Assertions.checkNotNull(intent)!!.hasExtra(KEY_STOP_REASON)) {
          Log.e(TAG, "Ignored SET_STOP_REASON: Missing $KEY_STOP_REASON extra")
        } else {
          val stopReason = intent!!.getIntExtra(KEY_STOP_REASON, /* defaultValue= */ 0)
          downloadManager.setStopReason(contentId, stopReason)
        }
      }
      ACTION_SET_REQUIREMENTS -> {
        val requirements: Requirements? =
            Assertions.checkNotNull(intent)!!.getParcelableExtra(KEY_REQUIREMENTS)
        if (requirements == null) {
          Log.e(TAG, "Ignored SET_REQUIREMENTS: Missing $KEY_REQUIREMENTS extra")
        } else {
          downloadManager.setRequirements(requirements)
        }
      }
      else -> Log.e(TAG, "Ignored unrecognized action: $intentAction")
    }

    if (Util.SDK_INT >= 26 && startedInForeground && foregroundNotificationUpdater != null) {
      // From API level 26, services started in the foreground are required to show a notification.
      foregroundNotificationUpdater.showNotificationIfNotAlready()
    }

    isStopped = false
    if (downloadManager.isIdle()) {
      onIdle()
    }
    return START_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    taskRemoved = true
  }

  override fun onDestroy() {
    isDestroyed = true
    Assertions.checkNotNull(downloadManagerHelper)!!.detachService(this)
    foregroundNotificationUpdater?.stopPeriodicUpdates()
  }

  /**
   * Throws [UnsupportedOperationException] because this service is not designed to be bound.
   */
  override fun onBind(intent: Intent?): IBinder? {
    throw UnsupportedOperationException()
  }

  /**
   * Returns a [DownloadManager] to be used to downloaded content. For each concrete download
   * service subclass, this is called once in the lifecycle of the process when [onCreate] is
   * called on the first instance of the service. If the service is destroyed and a new instance is
   * created later, the new instance will use the previously returned [DownloadManager]
   * without this method being called again.
   */
  protected abstract fun getDownloadManager(): DownloadManager

  /**
   * Returns a [Scheduler] to restart the service when requirements for downloads to continue
   * are met.
   *
   * <p>This method is not called on all devices or for all service configurations. When it is
   * called, it's called only once in the life cycle of the process. If a service has unfinished
   * downloads that cannot make progress due to unmet requirements, it will behave according to the
   * first matching case below:
   *
   * <ul>
   *   <li>If the service has {@code foregroundNotificationId} set to {@link
   *       #FOREGROUND_NOTIFICATION_ID_NONE}, then this method will not be called. The service will
   *       remain in the background until the downloads are able to continue to completion or the
   *       service is killed by the platform.
   *   <li>If the device API level is less than 31, a {@link Scheduler} is returned from this
   *       method, and the returned {@link Scheduler} {@link Scheduler#getSupportedRequirements
   *       supports} all of the requirements that have been specified for downloads to continue,
   *       then the service will stop itself and the {@link Scheduler} will be used to restart it in
   *       the foreground when the requirements are met.
   *   <li>If the device API level is less than 31 and either {@code null} or a {@link Scheduler}
   *       that does not {@link Scheduler#getSupportedRequirements support} all of the requirements
   *       is returned from this method, then the service will remain in the foreground until the
   *       downloads are able to continue to completion.
   *   <li>If the device API level is 31 or above, then this method will not be called and the
   *       service will remain in the foreground until the downloads are able to continue to
   *       completion. A {@link Scheduler} cannot be used for this case due to <a
   *       href="https://developer.android.com/about/versions/12/foreground-services">Android 12
   *       foreground service launch restrictions</a>.
   *   <li>
   * </ul>
   */
  protected abstract fun getScheduler(): Scheduler?

  /**
   * Returns a notification to be displayed when this service running in the foreground.
   *
   * <p>Download services that do not wish to run in the foreground should be created by setting the
   * {@code foregroundNotificationId} constructor argument to {@link
   * #FOREGROUND_NOTIFICATION_ID_NONE}. This method is not called for such services, meaning it can
   * be implemented to throw {@link UnsupportedOperationException}.
   *
   * @param downloads The current downloads.
   * @param notMetRequirements Any requirements for downloads that are not currently met.
   * @return The foreground notification to display.
   */
  protected abstract fun getForegroundNotification(
      downloads: List<Download>, @RequirementFlags notMetRequirements: Int
  ): Notification

  /**
   * Invalidates the current foreground notification and causes [getForegroundNotification] to be
   * invoked again if the service isn't stopped.
   */
  protected fun invalidateForegroundNotification() {
    if (foregroundNotificationUpdater != null && !isDestroyed) {
      foregroundNotificationUpdater.invalidate()
    }
  }

  /**
   * Called after the service is created, once the downloads are known.
   *
   * @param downloads The current downloads.
   */
  private fun notifyDownloads(downloads: List<Download>) {
    if (foregroundNotificationUpdater != null) {
      for (i in downloads.indices) {
        if (needsStartedService(downloads[i].state)) {
          foregroundNotificationUpdater.startPeriodicUpdates()
          break
        }
      }
    }
  }

  /**
   * Called when the state of a download changes.
   *
   * @param download The state of the download.
   */
  private fun notifyDownloadChanged(download: Download) {
    if (foregroundNotificationUpdater != null) {
      if (needsStartedService(download.state)) {
        foregroundNotificationUpdater.startPeriodicUpdates()
      } else {
        foregroundNotificationUpdater.invalidate()
      }
    }
  }

  /** Called when a download is removed. */
  private fun notifyDownloadRemoved() {
    foregroundNotificationUpdater?.invalidate()
  }

  /** Returns whether the service is stopped. */
  private fun isStopped(): Boolean {
    return isStopped
  }

  private fun onIdle() {
    foregroundNotificationUpdater?.stopPeriodicUpdates()

    if (!Assertions.checkNotNull(downloadManagerHelper)!!.updateScheduler()) {
      // We failed to schedule the service to restart when requirements that the DownloadManager is
      // waiting for are met, so remain started.
      return
    }

    // Stop the service, either because the DownloadManager is not waiting for requirements to be
    // met, or because we've scheduled the service to be restarted when they are.
    if (Util.SDK_INT < 28 && taskRemoved) { // See [Internal: b/74248644].
      stopSelf()
      isStopped = true
    } else {
      isStopped = isStopped or stopSelfResult(lastStartId)
    }
  }

  private inner class ForegroundNotificationUpdater(
      private val notificationId: Int,
      private val updateInterval: Long
  ) {

    private val handler: Handler = Handler(Looper.getMainLooper())

    private var periodicUpdatesStarted = false
    private var notificationDisplayed = false

    fun startPeriodicUpdates() {
      periodicUpdatesStarted = true
      update()
    }

    fun stopPeriodicUpdates() {
      periodicUpdatesStarted = false
      handler.removeCallbacksAndMessages(null)
    }

    fun showNotificationIfNotAlready() {
      if (!notificationDisplayed) {
        update()
      }
    }

    fun invalidate() {
      if (notificationDisplayed) {
        update()
      }
    }

    private fun update() {
      val downloadManager =
          Assertions.checkNotNull(downloadManagerHelper)!!.downloadManager
      val downloads = downloadManager.currentDownloads
      val notMetRequirements = downloadManager.notMetRequirements
      val notification = getForegroundNotification(downloads, notMetRequirements)
      if (!notificationDisplayed) {
        startForeground(notificationId, notification)
        notificationDisplayed = true
      } else {
        // Update the notification via NotificationManager rather than by repeatedly calling
        // startForeground, since the latter can cause ActivityManager log spam.
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notificationId, notification)
      }
      if (periodicUpdatesStarted) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ update() }, updateInterval)
      }
    }
  }

  private class DownloadManagerHelper(
      private val context: Context,
      val downloadManager: DownloadManager,
      private val foregroundAllowed: Boolean,
      private val scheduler: Scheduler?,
      private val serviceClass: Class<out DownloadService>
  ) : DownloadManager.Listener {

    private var downloadService: DownloadService? = null
    private var scheduledRequirements: Requirements? = null

    init {
      downloadManager.addListener(this)
      updateScheduler()
    }

    fun attachService(downloadService: DownloadService) {
      Assertions.checkState(this.downloadService == null)
      this.downloadService = downloadService
      if (downloadManager.isInitialized) {
        // The call to DownloadService.notifyDownloads is posted to avoid it being called directly
        // from DownloadService.onCreate. This is a good idea because it may in turn call
        // DownloadService.getForegroundNotification, and concrete subclass implementations may
        // not anticipate the possibility of this method being called before their onCreate
        // implementation has finished executing.
        Util.createHandlerForCurrentOrMainLooper()
            .postAtFrontOfQueue {
              downloadService.notifyDownloads(downloadManager.currentDownloads)
            }
      }
    }

    fun detachService(downloadService: DownloadService) {
      Assertions.checkState(this.downloadService == downloadService)
      this.downloadService = null
    }

    /**
     * Schedules or cancels restarting the service, as needed for the current state.
     *
     * @return True if the DownloadManager is not waiting for requirements, or if it is waiting for
     *     requirements and the service has been successfully scheduled to be restarted when they
     *     are met. False if the DownloadManager is waiting for requirements and the service has not
     *     been scheduled for restart.
     */
    fun updateScheduler(): Boolean {
      val waitingForRequirements = downloadManager.isWaitingForRequirements
      if (scheduler == null) {
        return !waitingForRequirements
      }

      if (!waitingForRequirements) {
        cancelScheduler()
        return true
      }

      val requirements = downloadManager.getRequirements()
      val supportedRequirements = scheduler.getSupportedRequirements(requirements)
      if (supportedRequirements != requirements) {
        cancelScheduler()
        return false
      }

      if (!schedulerNeedsUpdate(requirements)) {
        return true
      }

      val servicePackage = context.packageName
      if (scheduler.schedule(requirements, servicePackage, ACTION_RESTART)) {
        scheduledRequirements = requirements
        return true
      } else {
        Log.w(TAG, "Failed to schedule restart")
        cancelScheduler()
        return false
      }
    }

    // DownloadManager.Listener implementation.

    override fun onInitialized(downloadManager: DownloadManager) {
      downloadService?.notifyDownloads(downloadManager.currentDownloads)
    }

    override fun onDownloadChanged(
        downloadManager: DownloadManager, download: Download, finalException: Exception?
    ) {
      downloadService?.notifyDownloadChanged(download)
      if (serviceMayNeedRestart() && needsStartedService(download.state)) {
        // This shouldn't happen unless (a) application code is changing the downloads by calling
        // the DownloadManager directly rather than sending actions through the service, or (b) if
        // the service is background only and a previous attempt to start it was prevented. Try and
        // restart the service to robust against such cases.
        Log.w(TAG, "DownloadService wasn't running. Restarting.")
        restartService()
      }
    }

    override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
      downloadService?.notifyDownloadRemoved()
    }

    override fun onIdle(downloadManager: DownloadManager) {
      downloadService?.onIdle()
    }

    override fun onRequirementsStateChanged(
        downloadManager: DownloadManager,
        requirements: Requirements,
        @RequirementFlags notMetRequirements: Int
    ) {
      updateScheduler()
    }

    override fun onWaitingForRequirementsChanged(
        downloadManager: DownloadManager, waitingForRequirements: Boolean
    ) {
      if (!waitingForRequirements &&
          !downloadManager.downloadsPaused &&
          serviceMayNeedRestart()) {
        // We're no longer waiting for requirements and downloads aren't paused, meaning the manager
        // will be able to resume downloads that are currently queued. If there exist queued
        // downloads then we should ensure the service is started.
        val downloads = downloadManager.currentDownloads
        for (i in downloads.indices) {
          if (downloads[i].state == Download.STATE_QUEUED) {
            restartService()
            return
          }
        }
      }
    }

    // Internal methods.

    private fun schedulerNeedsUpdate(requirements: Requirements): Boolean {
      return scheduledRequirements != requirements
    }

    private fun cancelScheduler() {
      val canceledRequirements = Requirements(/* requirements= */ 0)
      if (schedulerNeedsUpdate(canceledRequirements)) {
        scheduler!!.cancel()
        scheduledRequirements = canceledRequirements
      }
    }

    private fun serviceMayNeedRestart(): Boolean {
      val service = downloadService
      return service == null || service.isStopped()
    }

    private fun restartService() {
      if (foregroundAllowed) {
        try {
          val intent = getIntent(context, serviceClass, ACTION_RESTART)
          Util.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
          // The process is running in the background, and is not allowed to start a foreground
          // service due to foreground service launch restrictions
          // (https://developer.android.com/about/versions/12/foreground-services).
          Log.w(TAG, "Failed to restart (foreground launch restriction)")
        }
      } else {
        // The service is background only. Use ACTION_INIT rather than ACTION_RESTART because
        // ACTION_RESTART is handled as though KEY_FOREGROUND is set to true.
        try {
          val intent = getIntent(context, serviceClass, ACTION_INIT)
          context.startService(intent)
        } catch (e: IllegalStateException) {
          // The process is classed as idle by the platform. Starting a background service is not
          // allowed in this state.
          Log.w(TAG, "Failed to restart (process is idle)")
        }
      }
    }
  }

  companion object {

    /**
     * Starts a download service to resume any ongoing downloads. Extras:
     *
     * <ul>
     *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
     * </ul>
     */
    const val ACTION_INIT = "com.google.android.exoplayer.downloadService.action.INIT"

    /** Like [ACTION_INIT], but with [KEY_FOREGROUND] implicitly set to true. */
    private const val ACTION_RESTART =
        "com.google.android.exoplayer.downloadService.action.RESTART"

    /**
     * Adds a new download. Extras:
     *
     * <ul>
     *   <li>{@link #KEY_DOWNLOAD_REQUEST} - A {@link DownloadRequest} defining the download to be
     *       added.
     *   <li>{@link #KEY_STOP_REASON} - An initial stop reason for the download. If omitted {@link
     *       Download#STOP_REASON_NONE} is used.
     *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
     * </ul>
     */
    const val ACTION_ADD_DOWNLOAD =
        "com.google.android.exoplayer.downloadService.action.ADD_DOWNLOAD"

    /**
     * Removes a download. Extras:
     *
     * <ul>
     *   <li>{@link #KEY_CONTENT_ID} - The content id of a download to remove.
     *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
     * </ul>
     */
    const val ACTION_REMOVE_DOWNLOAD =
        "com.google.android.exoplayer.downloadService.action.REMOVE_DOWNLOAD"

    /**
     * Removes all downloads. Extras:
     *
     * <ul>
     *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
     * </ul>
     */
    const val ACTION_REMOVE_ALL_DOWNLOADS =
        "com.google.android.exoplayer.downloadService.action.REMOVE_ALL_DOWNLOADS"

    /**
     * Resumes all downloads except those that have a non-zero {@link Download#stopReason}. Extras:
     *
     * <ul>
     *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
     * </ul>
     */
    const val ACTION_RESUME_DOWNLOADS =
        "com.google.android.exoplayer.downloadService.action.RESUME_DOWNLOADS"

    /**
     * Pauses all downloads. Extras:
     *
     * <ul>
     *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
     * </ul>
     */
    const val ACTION_PAUSE_DOWNLOADS =
        "com.google.android.exoplayer.downloadService.action.PAUSE_DOWNLOADS"

    /**
     * Sets the stop reason for one or all downloads. To clear the stop reason, pass {@link
     * Download#STOP_REASON_NONE}. Extras:
     *
     * <ul>
     *   <li>{@link #KEY_CONTENT_ID} - The content id of a single download to update with the stop
     *       reason. If omitted, all downloads will be updated.
     *   <li>{@link #KEY_STOP_REASON} - An application provided reason for stopping the download or
     *       downloads, or {@link Download#STOP_REASON_NONE} to clear the stop reason.
     *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
     * </ul>
     */
    const val ACTION_SET_STOP_REASON =
        "com.google.android.exoplayer.downloadService.action.SET_STOP_REASON"

    /**
     * Sets the requirements that need to be met for downloads to progress. Extras:
     *
     * <ul>
     *   <li>{@link #KEY_REQUIREMENTS} - A {@link Requirements}.
     *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
     * </ul>
     */
    const val ACTION_SET_REQUIREMENTS =
        "com.google.android.exoplayer.downloadService.action.SET_REQUIREMENTS"

    /** Key for the [DownloadRequest] in [ACTION_ADD_DOWNLOAD] intents. */
    const val KEY_DOWNLOAD_REQUEST = "download_request"

    /**
     * Key for the [String] content id in [ACTION_SET_STOP_REASON] and [ACTION_REMOVE_DOWNLOAD]
     * intents.
     */
    const val KEY_CONTENT_ID = "content_id"

    /**
     * Key for the integer stop reason in [ACTION_SET_STOP_REASON] and [ACTION_ADD_DOWNLOAD]
     * intents.
     */
    const val KEY_STOP_REASON = "stop_reason"

    /** Key for the [Requirements] in [ACTION_SET_REQUIREMENTS] intents. */
    const val KEY_REQUIREMENTS = "requirements"

    /**
     * Key for a boolean extra that can be set on any intent to indicate whether the service was
     * started in the foreground. If set, the service is guaranteed to call [startForeground].
     */
    const val KEY_FOREGROUND = "foreground"

    /** Invalid foreground notification id that can be used to run the service in the background. */
    const val FOREGROUND_NOTIFICATION_ID_NONE = 0

    /** Default foreground notification update interval in milliseconds. */
    const val DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL: Long = 1000

    private const val TAG = "DownloadService"

    // Maps each concrete DownloadService subclass to a single DownloadManagerHelper instance. This
    // ensures getDownloadManager is only called once per subclass, even if a new instance of the
    // service is created. The DownloadManagerHelper wrapper also takes care of restarting the service
    // when there's no scheduler, and is often able to restart the service faster than the scheduler
    // even when there is one.
    private val downloadManagerHelpers = HashMap<Class<out DownloadService>, DownloadManagerHelper>()

    /**
     * Builds an [Intent] for adding a new download.
     *
     * @param context A [Context].
     * @param clazz The concrete download service being targeted by the intent.
     * @param downloadRequest The request to be executed.
     * @param foreground Whether this intent will be used to start the service in the foreground.
     * @return The created intent.
     */
    @JvmStatic
    fun buildAddDownloadIntent(
        context: Context,
        clazz: Class<out DownloadService>,
        downloadRequest: DownloadRequest,
        foreground: Boolean
    ): Intent {
      return buildAddDownloadIntent(context, clazz, downloadRequest, Download.STOP_REASON_NONE, foreground)
    }

    /**
     * Builds an [Intent] for adding a new download.
     *
     * @param context A [Context].
     * @param clazz The concrete download service being targeted by the intent.
     * @param downloadRequest The request to be executed.
     * @param stopReason An initial stop reason for the download, or [Download.STOP_REASON_NONE]
     *     if the download should be started.
     * @param foreground Whether this intent will be used to start the service in the foreground.
     * @return The created intent.
     */
    @JvmStatic
    fun buildAddDownloadIntent(
        context: Context,
        clazz: Class<out DownloadService>,
        downloadRequest: DownloadRequest,
        stopReason: Int,
        foreground: Boolean
    ): Intent {
      return getIntent(context, clazz, ACTION_ADD_DOWNLOAD, foreground)
          .putExtra(KEY_DOWNLOAD_REQUEST, downloadRequest)
          .putExtra(KEY_STOP_REASON, stopReason)
    }

    /**
     * Builds an [Intent] for removing the download with the `id`.
     *
     * @param context A [Context].
     * @param clazz The concrete download service being targeted by the intent.
     * @param id The content id.
     * @param foreground Whether this intent will be used to start the service in the foreground.
     * @return The created intent.
     */
    @JvmStatic
    fun buildRemoveDownloadIntent(
        context: Context, clazz: Class<out DownloadService>, id: String, foreground: Boolean
    ): Intent {
      return getIntent(context, clazz, ACTION_REMOVE_DOWNLOAD, foreground)
          .putExtra(KEY_CONTENT_ID, id)
    }

    /**
     * Builds an [Intent] for removing all downloads.
     *
     * @param context A [Context].
     * @param clazz The concrete download service being targeted by the intent.
     * @param foreground Whether this intent will be used to start the service in the foreground.
     * @return The created intent.
     */
    @JvmStatic
    fun buildRemoveAllDownloadsIntent(
        context: Context, clazz: Class<out DownloadService>, foreground: Boolean
    ): Intent {
      return getIntent(context, clazz, ACTION_REMOVE_ALL_DOWNLOADS, foreground)
    }

    /**
     * Builds an [Intent] for resuming all downloads.
     *
     * @param context A [Context].
     * @param clazz The concrete download service being targeted by the intent.
     * @param foreground Whether this intent will be used to start the service in the foreground.
     * @return The created intent.
     */
    @JvmStatic
    fun buildResumeDownloadsIntent(
        context: Context, clazz: Class<out DownloadService>, foreground: Boolean
    ): Intent {
      return getIntent(context, clazz, ACTION_RESUME_DOWNLOADS, foreground)
    }

    /**
     * Builds an [Intent] to pause all downloads.
     *
     * @param context A [Context].
     * @param clazz The concrete download service being targeted by the intent.
     * @param foreground Whether this intent will be used to start the service in the foreground.
     * @return The created intent.
     */
    @JvmStatic
    fun buildPauseDownloadsIntent(
        context: Context, clazz: Class<out DownloadService>, foreground: Boolean
    ): Intent {
      return getIntent(context, clazz, ACTION_PAUSE_DOWNLOADS, foreground)
    }

    /**
     * Builds an [Intent] for setting the stop reason for one or all downloads. To clear the
     * stop reason, pass [Download.STOP_REASON_NONE].
     *
     * @param context A [Context].
     * @param clazz The concrete download service being targeted by the intent.
     * @param id The content id, or `null` to set the stop reason for all downloads.
     * @param stopReason An application defined stop reason.
     * @param foreground Whether this intent will be used to start the service in the foreground.
     * @return The created intent.
     */
    @JvmStatic
    fun buildSetStopReasonIntent(
        context: Context,
        clazz: Class<out DownloadService>,
        id: String?,
        stopReason: Int,
        foreground: Boolean
    ): Intent {
      return getIntent(context, clazz, ACTION_SET_STOP_REASON, foreground)
          .putExtra(KEY_CONTENT_ID, id)
          .putExtra(KEY_STOP_REASON, stopReason)
    }

    /**
     * Builds an [Intent] for setting the requirements that need to be met for downloads to
     * progress.
     *
     * @param context A [Context].
     * @param clazz The concrete download service being targeted by the intent.
     * @param requirements A [Requirements].
     * @param foreground Whether this intent will be used to start the service in the foreground.
     * @return The created intent.
     */
    @JvmStatic
    fun buildSetRequirementsIntent(
        context: Context,
        clazz: Class<out DownloadService>,
        requirements: Requirements,
        foreground: Boolean
    ): Intent {
      return getIntent(context, clazz, ACTION_SET_REQUIREMENTS, foreground)
          .putExtra(KEY_REQUIREMENTS, requirements)
    }

    /**
     * Starts the service if not started already and adds a new download.
     *
     * @param context A [Context].
     * @param clazz The concrete download service to be started.
     * @param downloadRequest The request to be executed.
     * @param foreground Whether the service is started in the foreground.
     */
    @JvmStatic
    fun sendAddDownload(
        context: Context,
        clazz: Class<out DownloadService>,
        downloadRequest: DownloadRequest,
        foreground: Boolean
    ) {
      val intent = buildAddDownloadIntent(context, clazz, downloadRequest, foreground)
      startService(context, intent, foreground)
    }

    /**
     * Starts the service if not started already and adds a new download.
     *
     * @param context A [Context].
     * @param clazz The concrete download service to be started.
     * @param downloadRequest The request to be executed.
     * @param stopReason An initial stop reason for the download, or [Download.STOP_REASON_NONE]
     *     if the download should be started.
     * @param foreground Whether the service is started in the foreground.
     */
    @JvmStatic
    fun sendAddDownload(
        context: Context,
        clazz: Class<out DownloadService>,
        downloadRequest: DownloadRequest,
        stopReason: Int,
        foreground: Boolean
    ) {
      val intent = buildAddDownloadIntent(context, clazz, downloadRequest, stopReason, foreground)
      startService(context, intent, foreground)
    }

    /**
     * Starts the service if not started already and removes a download.
     *
     * @param context A [Context].
     * @param clazz The concrete download service to be started.
     * @param id The content id.
     * @param foreground Whether the service is started in the foreground.
     */
    @JvmStatic
    fun sendRemoveDownload(
        context: Context, clazz: Class<out DownloadService>, id: String, foreground: Boolean
    ) {
      val intent = buildRemoveDownloadIntent(context, clazz, id, foreground)
      startService(context, intent, foreground)
    }

    /**
     * Starts the service if not started already and removes all downloads.
     *
     * @param context A [Context].
     * @param clazz The concrete download service to be started.
     * @param foreground Whether the service is started in the foreground.
     */
    @JvmStatic
    fun sendRemoveAllDownloads(
        context: Context, clazz: Class<out DownloadService>, foreground: Boolean
    ) {
      val intent = buildRemoveAllDownloadsIntent(context, clazz, foreground)
      startService(context, intent, foreground)
    }

    /**
     * Starts the service if not started already and resumes all downloads.
     *
     * @param context A [Context].
     * @param clazz The concrete download service to be started.
     * @param foreground Whether the service is started in the foreground.
     */
    @JvmStatic
    fun sendResumeDownloads(
        context: Context, clazz: Class<out DownloadService>, foreground: Boolean
    ) {
      val intent = buildResumeDownloadsIntent(context, clazz, foreground)
      startService(context, intent, foreground)
    }

    /**
     * Starts the service if not started already and pauses all downloads.
     *
     * @param context A [Context].
     * @param clazz The concrete download service to be started.
     * @param foreground Whether the service is started in the foreground.
     */
    @JvmStatic
    fun sendPauseDownloads(
        context: Context, clazz: Class<out DownloadService>, foreground: Boolean
    ) {
      val intent = buildPauseDownloadsIntent(context, clazz, foreground)
      startService(context, intent, foreground)
    }

    /**
     * Starts the service if not started already and sets the stop reason for one or all downloads. To
     * clear stop reason, pass [Download.STOP_REASON_NONE].
     *
     * @param context A [Context].
     * @param clazz The concrete download service to be started.
     * @param id The content id, or `null` to set the stop reason for all downloads.
     * @param stopReason An application defined stop reason.
     * @param foreground Whether the service is started in the foreground.
     */
    @JvmStatic
    fun sendSetStopReason(
        context: Context,
        clazz: Class<out DownloadService>,
        id: String?,
        stopReason: Int,
        foreground: Boolean
    ) {
      val intent = buildSetStopReasonIntent(context, clazz, id, stopReason, foreground)
      startService(context, intent, foreground)
    }

    /**
     * Starts the service if not started already and sets the requirements that need to be met for
     * downloads to progress.
     *
     * @param context A [Context].
     * @param clazz The concrete download service to be started.
     * @param requirements A [Requirements].
     * @param foreground Whether the service is started in the foreground.
     */
    @JvmStatic
    fun sendSetRequirements(
        context: Context,
        clazz: Class<out DownloadService>,
        requirements: Requirements,
        foreground: Boolean
    ) {
      val intent = buildSetRequirementsIntent(context, clazz, requirements, foreground)
      startService(context, intent, foreground)
    }

    /**
     * Starts a download service to resume any ongoing downloads.
     *
     * @param context A [Context].
     * @param clazz The concrete download service to be started.
     * @see startForeground
     */
    @JvmStatic
    fun start(context: Context, clazz: Class<out DownloadService>) {
      context.startService(getIntent(context, clazz, ACTION_INIT))
    }

    /**
     * Starts the service in the foreground without adding a new download request. If there are any
     * not finished downloads and the requirements are met, the service resumes downloading. Otherwise
     * it stops immediately.
     *
     * @param context A [Context].
     * @param clazz The concrete download service to be started.
     * @see start
     */
    @JvmStatic
    fun startForeground(context: Context, clazz: Class<out DownloadService>) {
      val intent = getIntent(context, clazz, ACTION_INIT, true)
      Util.startForegroundService(context, intent)
    }

    private fun needsStartedService(@Download.State state: Int): Boolean {
      return state == Download.STATE_DOWNLOADING ||
          state == Download.STATE_REMOVING ||
          state == Download.STATE_RESTARTING
    }

    private fun getIntent(
        context: Context, clazz: Class<out DownloadService>, action: String, foreground: Boolean
    ): Intent {
      return getIntent(context, clazz, action).putExtra(KEY_FOREGROUND, foreground)
    }

    private fun getIntent(
        context: Context, clazz: Class<out DownloadService>, action: String
    ): Intent {
      return Intent(context, clazz).setAction(action)
    }

    private fun startService(context: Context, intent: Intent, foreground: Boolean) {
      if (foreground) {
        Util.startForegroundService(context, intent)
      } else {
        context.startService(intent)
      }
    }
  }
}
