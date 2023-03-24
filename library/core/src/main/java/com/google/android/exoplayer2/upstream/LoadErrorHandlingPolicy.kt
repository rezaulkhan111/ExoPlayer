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
package com.google.android.exoplayer2.upstream

import androidx.annotation.IntDef
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.FallbackSelection
import com.google.android.exoplayer2.util.Assertions.checkArgument
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * A policy that defines how load errors are handled.
 *
 *
 * Some loaders are able to choose between a number of alternate resources. Such loaders will
 * call [.getFallbackSelectionFor] when a load error occurs.
 * The [FallbackSelection] returned by the policy defines whether the loader should fall back
 * to using another resource, and if so the duration for which the failing resource should be
 * excluded.
 *
 *
 * When fallback does not take place, a loader will call [ ][.getRetryDelayMsFor]. The value returned by the policy defines whether the failed
 * load can be retried, and if so the duration to wait before retrying. If the policy indicates that
 * a load error should not be retried, it will be considered fatal by the loader. The loader may
 * also consider load errors that can be retried fatal if at least [ ][.getMinimumLoadableRetryCount] retries have been attempted.
 *
 *
 * Methods are invoked on the playback thread.
 */
interface LoadErrorHandlingPolicy {
    /** Fallback type. One of [.FALLBACK_TYPE_LOCATION] or [.FALLBACK_TYPE_TRACK].  */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [FALLBACK_TYPE_LOCATION, FALLBACK_TYPE_TRACK])
    annotation class FallbackType

    companion object {
        /**
         * Fallback to the same resource at a different location (i.e., a different URL through which the
         * exact same data can be requested).
         */
        const val FALLBACK_TYPE_LOCATION = 1

        /**
         * Fallback to a different track (i.e., a different representation of the same content; for
         * example the same video encoded at a different bitrate or resolution).
         */
        const val FALLBACK_TYPE_TRACK = 2
    }

    /** Holds information about a load task error.  */
    class LoadErrorInfo
    /** Creates an instance with the given values.  */(
            /** The [LoadEventInfo] associated with the load that encountered an error.  */
            val loadEventInfo: LoadEventInfo,
            /** [MediaLoadData] associated with the load that encountered an error.  */
            val mediaLoadData: MediaLoadData,
            /** The exception associated to the load error.  */
            val exception: IOException,
            /** The number of errors this load task has encountered, including this one.  */
            val errorCount: Int)

    /** Holds information about the available fallback options.  */
    class FallbackOptions
    /** Creates an instance.  */(
            /** The number of available locations.  */
            private val numberOfLocations: Int,
            /** The number of locations that are already excluded.  */
            private val numberOfExcludedLocations: Int,
            /** The number of tracks.  */
            val numberOfTracks: Int,
            /** The number of tracks that are already excluded.  */
            private val numberOfExcludedTracks: Int) {
        /** Returns whether a fallback is available for the given [fallback type][FallbackType].  */
        fun isFallbackAvailable(@FallbackType type: Int): Boolean {
            return if (type == FALLBACK_TYPE_LOCATION) numberOfLocations - numberOfExcludedLocations > 1 else numberOfTracks - numberOfExcludedTracks > 1
        }
    }

    /** A selected fallback option.  */
    class FallbackSelection {
        /**
         * The type of fallback.
         */
        var type = 0

        /**
         * The duration for which the failing resource should be excluded, in milliseconds.
         */
        var exclusionDurationMs: Long = 0

        /**
         * Creates an instance.
         *
         * @param type                The type of fallback.
         * @param exclusionDurationMs The duration for which the failing resource should be excluded, in
         * milliseconds. Must be non-negative.
         */
        constructor(@FallbackType type: Int, exclusionDurationMs: Long) {
            checkArgument(exclusionDurationMs >= 0)
            this.type = type
            this.exclusionDurationMs = exclusionDurationMs
        }
    }

    /**
     * Returns whether a loader should fall back to using another resource on encountering an error,
     * and if so the duration for which the failing resource should be excluded.
     *
     *
     * If the returned [fallback type][FallbackSelection.type] was not [ ][FallbackOptions.isFallbackAvailable], then the loader will not
     * fall back.
     *
     * @param fallbackOptions The available fallback options.
     * @param loadErrorInfo A [LoadErrorInfo] holding information about the load error.
     * @return The selected fallback, or `null` if the calling loader should not fall back.
     */
    fun getFallbackSelectionFor(fallbackOptions: FallbackOptions?, loadErrorInfo: LoadErrorInfo?): FallbackSelection?

    /**
     * Returns whether a loader can retry on encountering an error, and if so the duration to wait
     * before retrying. A return value of [C.TIME_UNSET] indicates that the error is fatal and
     * should not be retried.
     *
     *
     * For loads that can be retried, loaders may ignore the retry delay returned by this method in
     * order to wait for a specific event before retrying.
     *
     * @param loadErrorInfo A [LoadErrorInfo] holding information about the load error.
     * @return The duration to wait before retrying in milliseconds, or [C.TIME_UNSET] if the
     * error is fatal and should not be retried.
     */
    fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo?): Long

    /**
     * Called once `loadTaskId` will not be associated with any more load errors.
     *
     *
     * Implementations should clean up any resources associated with `loadTaskId` when this
     * method is called.
     */
    fun onLoadTaskConcluded(loadTaskId: Long) {}

    /**
     * Returns the minimum number of times to retry a load before a load error that can be retried may
     * be considered fatal.
     *
     * @param dataType One of the [C.DATA_TYPE_*][C] constants indicating the type of data being
     * loaded.
     * @return The minimum number of times to retry a load before a load error that can be retried may
     * be considered fatal.
     * @see Loader.startLoading
     */
    fun getMinimumLoadableRetryCount(dataType: Int): Int
}