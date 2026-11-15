/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.analytics

import android.media.metrics.LogSessionId
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Assertions.checkState
import com.google.android.exoplayer2.util.Util

/** Identifier for a player instance. */
class PlayerId {

  private val logSessionIdApi31: LogSessionIdApi31?

  /** Creates an instance for API < 31. */
  constructor() : this(null as LogSessionIdApi31?) {
    checkState(Util.SDK_INT < 31)
  }

  /**
   * Creates an instance for API >= 31.
   *
   * @param logSessionId The [LogSessionId] used for this player.
   */
  @RequiresApi(31)
  constructor(logSessionId: LogSessionId) : this(LogSessionIdApi31(logSessionId))

  private constructor(logSessionIdApi31: LogSessionIdApi31?) {
    this.logSessionIdApi31 = logSessionIdApi31
  }

  /** Returns the [LogSessionId] for this player instance. */
  @RequiresApi(31)
  fun getLogSessionId(): LogSessionId {
    return checkNotNull(logSessionIdApi31)!!.logSessionId
  }

  @RequiresApi(31)
  private class LogSessionIdApi31(@JvmField val logSessionId: LogSessionId) {
    companion object {
      @JvmField
      val UNSET = LogSessionIdApi31(LogSessionId.LOG_SESSION_ID_NONE)
    }
  }

  companion object {
    /**
     * A player identifier with unset default values that can be used as a placeholder or for testing.
     */
    @JvmField
    @Suppress("NewApi")
    val UNSET: PlayerId =
      if (Util.SDK_INT < 31) PlayerId() else PlayerId(LogSessionIdApi31.UNSET)
  }
}
