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
package com.google.android.exoplayer2.source.hls

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.DataSource

/** Creates [DataSource]s for HLS playlists, encryption and media chunks.  */
interface HlsDataSourceFactory {
    /**
     * Creates a [DataSource] for the given data type.
     *
     * @param dataType The [C.DataType] for which the [DataSource] will be used.
     * @return A [DataSource] for the given data type.
     */
    fun createDataSource(@C.DataType dataType: Int): DataSource?
}