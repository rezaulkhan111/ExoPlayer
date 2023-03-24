/*
 * Copyright (C) 2021 The Android Open Source Project
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

/** Provides an estimate of the time to first byte of a transfer.  */
interface TimeToFirstByteEstimator {
    /**
     * Returns the estimated time to first byte of the response body, in microseconds, or [ ][C.TIME_UNSET] if no estimate is available.
     */
    val timeToFirstByteEstimateUs: Long

    /** Resets the estimator.  */
    fun reset()

    /**
     * Called when a transfer is being initialized.
     *
     * @param dataSpec Describes the data for which the transfer is initialized.
     */
    fun onTransferInitializing(dataSpec: DataSpec?)

    /**
     * Called when a transfer starts.
     *
     * @param dataSpec Describes the data being transferred.
     */
    fun onTransferStart(dataSpec: DataSpec?)
}