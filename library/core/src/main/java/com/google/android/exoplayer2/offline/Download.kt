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

import androidx.annotation.IntDef
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.util.Assertions

/** Represents state of a download. */
class Download @JvmOverloads constructor(
    /** The download request. */
    @JvmField val request: DownloadRequest,
    /** The state of the download. */
    @param:State @get:State @JvmField val state: Int,
    /** The first time when download entry is created. */
    @JvmField val startTimeMs: Long,
    /** The last update time. */
    @JvmField val updateTimeMs: Long,
    /** The total size of the content in bytes, or [C.LENGTH_UNSET] if unknown. */
    @JvmField val contentLength: Long,
    /** The reason the download is stopped, or [STOP_REASON_NONE]. */
    @JvmField val stopReason: Int,
    /**
     * If [state] is [STATE_FAILED] then this is the cause, otherwise [FAILURE_REASON_NONE].
     */
    @param:FailureReason @get:FailureReason @JvmField val failureReason: Int,
    /* package */ @JvmField internal val progress: DownloadProgress = DownloadProgress(),
) {

  /**
   * Download states. One of [STATE_QUEUED], [STATE_STOPPED], [STATE_DOWNLOADING], [STATE_COMPLETED],
   * [STATE_FAILED], [STATE_REMOVING] or [STATE_RESTARTING].
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(
      AnnotationTarget.FIELD,
      AnnotationTarget.FUNCTION,
      AnnotationTarget.PROPERTY_GETTER,
      AnnotationTarget.PROPERTY_SETTER,
      AnnotationTarget.VALUE_PARAMETER,
      AnnotationTarget.LOCAL_VARIABLE,
      AnnotationTarget.TYPE
  )
  @IntDef(
      STATE_QUEUED,
      STATE_STOPPED,
      STATE_DOWNLOADING,
      STATE_COMPLETED,
      STATE_FAILED,
      STATE_REMOVING,
      STATE_RESTARTING
  )
  annotation class State

  /** Failure reasons. Either [FAILURE_REASON_NONE] or [FAILURE_REASON_UNKNOWN]. */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @Target(
      AnnotationTarget.FIELD,
      AnnotationTarget.FUNCTION,
      AnnotationTarget.PROPERTY_GETTER,
      AnnotationTarget.PROPERTY_SETTER,
      AnnotationTarget.VALUE_PARAMETER,
      AnnotationTarget.LOCAL_VARIABLE,
      AnnotationTarget.TYPE
  )
  @IntDef(FAILURE_REASON_NONE, FAILURE_REASON_UNKNOWN)
  annotation class FailureReason

  init {
    Assertions.checkNotNull(progress)
    Assertions.checkArgument((failureReason == FAILURE_REASON_NONE) == (state != STATE_FAILED))
    if (stopReason != 0) {
      Assertions.checkArgument((state != STATE_DOWNLOADING) && (state != STATE_QUEUED))
    }
  }

  /** Returns whether the download is completed or failed. These are terminal states. */
  fun isTerminalState(): Boolean {
    return state == STATE_COMPLETED || state == STATE_FAILED
  }

  /** Returns the total number of downloaded bytes. */
  fun getBytesDownloaded(): Long {
    return progress.bytesDownloaded
  }

  /**
   * Returns the estimated download percentage, or [C.PERCENTAGE_UNSET] if no estimate is
   * available.
   */
  fun getPercentDownloaded(): Float {
    return progress.percentDownloaded
  }

  companion object {
    // Important: These constants are persisted into DownloadIndex. Do not change them.
    /**
     * The download is waiting to be started. A download may be queued because the [DownloadManager]
     *
     * * Is [paused][DownloadManager.getDownloadsPaused]
     * * Has [Requirements][DownloadManager.getRequirements] that are not met
     * * Has already started [maxParallelDownloads][DownloadManager.getMaxParallelDownloads]
     */
    const val STATE_QUEUED = 0

    /** The download is stopped for a specified [stopReason]. */
    const val STATE_STOPPED = 1

    /** The download is currently started. */
    const val STATE_DOWNLOADING = 2

    /** The download completed. */
    const val STATE_COMPLETED = 3

    /** The download failed. */
    const val STATE_FAILED = 4

    /** The download is being removed. */
    const val STATE_REMOVING = 5

    /** The download will restart after all downloaded data is removed. */
    const val STATE_RESTARTING = 7

    /** The download isn't failed. */
    const val FAILURE_REASON_NONE = 0

    /** The download is failed because of unknown reason. */
    const val FAILURE_REASON_UNKNOWN = 1

    /** The download isn't stopped. */
    const val STOP_REASON_NONE = 0
  }
}
