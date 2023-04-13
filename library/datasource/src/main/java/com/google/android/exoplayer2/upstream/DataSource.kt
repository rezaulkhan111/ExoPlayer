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
package com.google.android.exoplayer2.upstream

import android.net.Uri
import com.google.android.exoplayer2.C
import java.io.IOException

/** Reads data from URI-identified resources.  */
interface DataSource : DataReader {

    /** A factory for [DataSource] instances.  */
    interface Factory {
        /** Creates a [DataSource] instance.  */
        fun createDataSource(): DataSource?
    }

    /**
     * Adds a [TransferListener] to listen to data transfers. This method is not thread-safe.
     *
     * @param transferListener A [TransferListener].
     */
    fun addTransferListener(transferListener: TransferListener?)

    /**
     * Opens the source to read the specified data. If an [IOException] is thrown, callers must
     * still call [.close] to ensure that any partial effects of the invocation are cleaned
     * up.
     *
     *
     * The following edge case behaviors apply:
     *
     *
     *  * If the [requested position][DataSpec.position] is within the resource, but the
     * [requested length][DataSpec.length] extends beyond the end of the resource, then
     * [.open] will succeed and data from the requested position to the end of the
     * resource will be made available through [.read].
     *  * If the [requested position][DataSpec.position] is equal to the length of the
     * resource, then [.open] will succeed, and [.read] will immediately return
     * [C.RESULT_END_OF_INPUT].
     *  * If the [requested position][DataSpec.position] is greater than the length of the
     * resource, then [.open] will throw an [IOException] for which [       ][DataSourceException.isCausedByPositionOutOfRange] will be `true`.
     *
     *
     * @param dataSpec Defines the data to be read.
     * @throws IOException If an error occurs opening the source. [DataSourceException] can be
     * thrown or used as a cause of the thrown exception to specify the reason of the error.
     * @return The number of bytes that can be read from the opened source. For unbounded requests
     * (i.e., requests where [DataSpec.length] equals [C.LENGTH_UNSET]) this value is
     * the resolved length of the request, or [C.LENGTH_UNSET] if the length is still
     * unresolved. For all other requests, the value returned will be equal to the request's
     * [DataSpec.length].
     */
    @Throws(IOException::class)
    fun open(dataSpec: DataSpec?): Long

    /**
     * When the source is open, returns the [Uri] from which data is being read. The returned
     * [Uri] will be identical to the one passed [.open] in the [DataSpec]
     * unless redirection has occurred. If redirection has occurred, the [Uri] after redirection
     * is returned.
     *
     * @return The [Uri] from which data is being read, or null if the source is not open.
     */
    fun getUri(): Uri?

    /**
     * When the source is open, returns the response headers associated with the last [.open]
     * call. Otherwise, returns an empty map.
     *
     *
     * Key look-up in the returned map is case-insensitive.
     */
    fun getResponseHeaders(): Map<String?, List<String?>?>? {
        return emptyMap<String?, List<String?>>()
    }

    /**
     * Closes the source. This method must be called even if the corresponding call to [ ][.open] threw an [IOException].
     *
     * @throws IOException If an error occurs closing the source.
     */
    @Throws(IOException::class)
    fun close()
}