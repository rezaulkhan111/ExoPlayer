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
package com.google.android.exoplayer2.source.hls.playlist

import com.google.android.exoplayer2.upstream.ParsingLoadable

/** Factory for [HlsPlaylist] parsers.  */
interface HlsPlaylistParserFactory {
    /**
     * Returns a stand-alone playlist parser. Playlists parsed by the returned parser do not inherit
     * any attributes from other playlists.
     */
    fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist?>?

    /**
     * Returns a playlist parser for playlists that were referenced by the given [ ]. Returned [HlsMediaPlaylist] instances may inherit attributes
     * from `multivariantPlaylist`.
     *
     * @param multivariantPlaylist The multivariant playlist that referenced any parsed media
     * playlists.
     * @param previousMediaPlaylist The previous media playlist or null if there is no previous media
     * playlist.
     * @return A parser for HLS playlists.
     */
    fun createPlaylistParser(
        multivariantPlaylist: HlsMultivariantPlaylist?,
        previousMediaPlaylist: HlsMediaPlaylist?
    ): ParsingLoadable.Parser<HlsPlaylist?>?
}