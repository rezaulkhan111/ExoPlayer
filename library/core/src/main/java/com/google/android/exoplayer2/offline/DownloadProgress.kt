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

/** Mutable [Download] progress.  */
class DownloadProgress {
    /** The number of bytes that have been downloaded.  */
    @JvmField
    @Volatile
    var bytesDownloaded: Long = 0

    /** The percentage that has been downloaded, or [C.PERCENTAGE_UNSET] if unknown.  */
    @JvmField
    @Volatile
    var percentDownloaded: Float = 0f
}
