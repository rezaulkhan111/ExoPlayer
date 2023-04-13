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
package com.google.android.exoplayer2.source.dash

import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.source.chunk.ChunkSource
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerTrackEmsgHandler
import com.google.android.exoplayer2.source.dash.manifest.DashManifest
import com.google.android.exoplayer2.trackselection.ExoTrackSelection
import com.google.android.exoplayer2.upstream.LoaderErrorThrower
import com.google.android.exoplayer2.upstream.TransferListener

/** A [ChunkSource] for DASH streams.  */
interface DashChunkSource : ChunkSource {
    /** Factory for [DashChunkSource]s.  */
    interface Factory {
        /**
         * @param manifestLoaderErrorThrower Throws errors affecting loading of manifests.
         * @param manifest The initial manifest.
         * @param baseUrlExclusionList The base URL exclusion list.
         * @param periodIndex The index of the corresponding period in the manifest.
         * @param adaptationSetIndices The indices of the corresponding adaptation sets in the period.
         * @param trackSelection The track selection.
         * @param trackType The [track type][C.TrackType].
         * @param elapsedRealtimeOffsetMs If known, an estimate of the instantaneous difference between
         * server-side unix time and [SystemClock.elapsedRealtime] in milliseconds,
         * specified as the server's unix time minus the local elapsed time. Or [C.TIME_UNSET]
         * if unknown.
         * @param enableEventMessageTrack Whether to output an event message track.
         * @param closedCaptionFormats The [Formats][Format] of closed caption tracks to be output.
         * @param transferListener The transfer listener which should be informed of any data transfers.
         * May be null if no listener is available.
         * @param playerId The [PlayerId] of the player using this chunk source.
         * @return The created [DashChunkSource].
         */
        fun createDashChunkSource(
            manifestLoaderErrorThrower: LoaderErrorThrower?,
            manifest: DashManifest?,
            baseUrlExclusionList: BaseUrlExclusionList?,
            periodIndex: Int,
            adaptationSetIndices: IntArray?,
            trackSelection: ExoTrackSelection?,
            trackType: @TrackType Int,
            elapsedRealtimeOffsetMs: Long,
            enableEventMessageTrack: Boolean,
            closedCaptionFormats: List<Format?>?,
            playerEmsgHandler: PlayerTrackEmsgHandler?,
            transferListener: TransferListener?,
            playerId: PlayerId?
        ): DashChunkSource?
    }

    /**
     * Updates the manifest.
     *
     * @param newManifest The new manifest.
     */
    fun updateManifest(newManifest: DashManifest?, periodIndex: Int)

    /**
     * Updates the track selection.
     *
     * @param trackSelection The new track selection instance. Must be equivalent to the previous one.
     */
    fun updateTrackSelection(trackSelection: ExoTrackSelection?)
}