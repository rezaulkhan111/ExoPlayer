/*
 * Copyright (C) 2019 The Android Open Source Project
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

import androidx.annotation.WorkerThread
import java.io.IOException

/** A writable index of [Downloads][Download].  */
@WorkerThread
interface WritableDownloadIndex : DownloadIndex {
    /**
     * Adds or replaces a [Download].
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @param download The [Download] to be added.
     * @throws IOException If an error occurs setting the state.
     */
    @Throws(IOException::class)
    fun putDownload(download: Download?)

    /**
     * Removes the download with the given ID. Does nothing if a download with the given ID does not
     * exist.
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @param id The ID of the download to remove.
     * @throws IOException If an error occurs removing the state.
     */
    @Throws(IOException::class)
    fun removeDownload(id: String?)

    /**
     * Sets all [Download.STATE_DOWNLOADING] states to [Download.STATE_QUEUED].
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @throws IOException If an error occurs updating the state.
     */
    @Throws(IOException::class)
    fun setDownloadingStatesToQueued()

    /**
     * Sets all states to [Download.STATE_REMOVING].
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @throws IOException If an error occurs updating the state.
     */
    @Throws(IOException::class)
    fun setStatesToRemoving()

    /**
     * Sets the stop reason of the downloads in a terminal state ([Download.STATE_COMPLETED],
     * [Download.STATE_FAILED]).
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @param stopReason The stop reason.
     * @throws IOException If an error occurs updating the state.
     */
    @Throws(IOException::class)
    fun setStopReason(stopReason: Int)

    /**
     * Sets the stop reason of the download with the given ID in a terminal state ([ ][Download.STATE_COMPLETED], [Download.STATE_FAILED]). Does nothing if a download with the
     * given ID does not exist, or if it's not in a terminal state.
     *
     *
     * This method may be slow and shouldn't normally be called on the main thread.
     *
     * @param id The ID of the download to update.
     * @param stopReason The stop reason.
     * @throws IOException If an error occurs updating the state.
     */
    @Throws(IOException::class)
    fun setStopReason(id: String?, stopReason: Int)
}