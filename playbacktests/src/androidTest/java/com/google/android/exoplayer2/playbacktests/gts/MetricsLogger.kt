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
package com.google.android.exoplayer2.playbacktests.gts

import android.app.Instrumentation
import androidx.annotation.Size

/** Metric logging interface for playback tests.  */ /* package */
internal interface MetricsLogger {

    interface Factory {
        fun create(
            instrumentation: Instrumentation?, @Size(max = 23) tag: String?, streamName: String?
        ): MetricsLogger?
    }

    companion object {
        val DEFAULT_FACTORY = LogcatMetricsLogger.FACTORY
        const val KEY_FRAMES_DROPPED_COUNT = "frames_dropped_count"
        const val KEY_FRAMES_RENDERED_COUNT = "frames_rendered_count"
        const val KEY_FRAMES_SKIPPED_COUNT = "frames_skipped_count"
        const val KEY_MAX_CONSECUTIVE_FRAMES_DROPPED_COUNT =
            "maximum_consecutive_frames_dropped_count"
        const val KEY_TEST_NAME = "test_name"
        const val KEY_IS_CDD_LIMITED_RETRY = "is_cdd_limited_retry"
    }

    /**
     * Logs an int metric provided from a test.
     *
     * @param key The key of the metric to be logged.
     * @param value The value of the metric to be logged.
     */
    fun logMetric(key: String?, value: Int)

    /**
     * Logs a string metric provided from a test.
     *
     * @param key The key of the metric to be logged.
     * @param value The value of the metric to be logged.
     */
    fun logMetric(key: String?, value: String?)

    /**
     * Logs a boolean metric provided from a test.
     *
     * @param key The key of the metric to be logged.
     * @param value The value of the metric to be logged.
     */
    fun logMetric(key: String?, value: Boolean)

    /** Closes the logger.  */
    fun close()
}