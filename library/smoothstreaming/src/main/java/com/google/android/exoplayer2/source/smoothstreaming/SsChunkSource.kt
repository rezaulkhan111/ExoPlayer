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
package com.google.android.exoplayer2.source.smoothstreaming

import com.google.android.exoplayer2.source.chunk.ChunkSource
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest
import com.google.android.exoplayer2.trackselection.ExoTrackSelection
import com.google.android.exoplayer2.upstream.LoaderErrorThrower
import com.google.android.exoplayer2.upstream.TransferListener

/** A [ChunkSource] for SmoothStreaming.  */
interface SsChunkSource : ChunkSource {
    /** Factory for [SsChunkSource]s.  */
    interface Factory {
        /**
         * Creates a new [SsChunkSource].
         *
         * @param manifestLoaderErrorThrower Throws errors affecting loading of manifests.
         * @param manifest The initial manifest.
         * @param streamElementIndex The index of the corresponding stream element in the manifest.
         * @param trackSelection The track selection.
         * @param transferListener The transfer listener which should be informed of any data transfers.
         * May be null if no listener is available.
         * @return The created [SsChunkSource].
         */
        fun createChunkSource(
            manifestLoaderErrorThrower: LoaderErrorThrower?,
            manifest: SsManifest?,
            streamElementIndex: Int,
            trackSelection: ExoTrackSelection?,
            transferListener: TransferListener?
        ): SsChunkSource?
    }

    /**
     * Updates the manifest.
     *
     * @param newManifest The new manifest.
     */
    fun updateManifest(newManifest: SsManifest?)

    /**
     * Updates the track selection.
     *
     * @param trackSelection The new track selection instance. Must be equivalent to the previous one.
     */
    fun updateTrackSelection(trackSelection: ExoTrackSelection?)
}