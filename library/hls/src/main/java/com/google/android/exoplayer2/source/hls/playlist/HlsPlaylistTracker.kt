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

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.source.MediaSourceEventListener
import com.google.android.exoplayer2.source.hls.HlsDataSourceFactory
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import java.io.IOException

/**
 * Tracks playlists associated to an HLS stream and provides snapshots.
 *
 *
 * The playlist tracker is responsible for exposing the seeking window, which is defined by the
 * segments that one of the playlists exposes. This playlist is called primary and needs to be
 * periodically refreshed in the case of live streams. Note that the primary playlist is one of the
 * media playlists while the multivariant playlist is an optional kind of playlist defined by the
 * HLS specification (RFC 8216).
 *
 *
 * Playlist loads might encounter errors. The tracker may choose to exclude them to ensure a
 * primary playlist is always available.
 */
interface HlsPlaylistTracker {

    /** Factory for [HlsPlaylistTracker] instances.  */
    interface Factory {
        /**
         * Creates a new tracker instance.
         *
         * @param dataSourceFactory The [HlsDataSourceFactory] to use for playlist loading.
         * @param loadErrorHandlingPolicy The [LoadErrorHandlingPolicy] for playlist load errors.
         * @param playlistParserFactory The [HlsPlaylistParserFactory] for playlist parsing.
         */
        fun createTracker(
            dataSourceFactory: HlsDataSourceFactory?,
            loadErrorHandlingPolicy: LoadErrorHandlingPolicy?,
            playlistParserFactory: HlsPlaylistParserFactory?
        ): HlsPlaylistTracker?
    }

    /** Listener for primary playlist changes.  */
    interface PrimaryPlaylistListener {
        /**
         * Called when the primary playlist changes.
         *
         * @param mediaPlaylist The primary playlist new snapshot.
         */
        fun onPrimaryPlaylistRefreshed(mediaPlaylist: HlsMediaPlaylist?)
    }

    /** Called on playlist loading events.  */
    interface PlaylistEventListener {
        /** Called a playlist changes.  */
        fun onPlaylistChanged()

        /**
         * Called if an error is encountered while loading a playlist.
         *
         * @param url The loaded url that caused the error.
         * @param loadErrorInfo The load error info.
         * @param forceRetry Whether retry should be forced without considering exclusion.
         * @return True if excluding did not encounter errors. False otherwise.
         */
        fun onPlaylistError(
            url: Uri?, loadErrorInfo: LoadErrorInfo?, forceRetry: Boolean
        ): Boolean
    }

    /** Thrown when a playlist is considered to be stuck due to a server side error.  */
    class PlaylistStuckException : IOException {

        /** The url of the stuck playlist.  */
        var url: Uri? = null

        /**
         * Creates an instance.
         *
         * @param url See [.url].
         */
        constructor(url: Uri?) {
            this.url = url
        }
    }

    /** Thrown when the media sequence of a new snapshot indicates the server has reset.  */
    class PlaylistResetException : IOException {

        /** The url of the reset playlist.  */
        var url: Uri? = null

        /**
         * Creates an instance.
         *
         * @param url See [.url].
         */
        constructor(url: Uri?) {
            this.url = url
        }
    }

    /**
     * Starts the playlist tracker.
     *
     *
     * Must be called from the playback thread. A tracker may be restarted after a [.stop]
     * call.
     *
     * @param initialPlaylistUri Uri of the HLS stream. Can point to a media playlist or a
     * multivariant playlist.
     * @param eventDispatcher A dispatcher to notify of events.
     * @param primaryPlaylistListener A callback for the primary playlist change events.
     */
    fun start(
        initialPlaylistUri: Uri?,
        eventDispatcher: MediaSourceEventListener.EventDispatcher?,
        primaryPlaylistListener: PrimaryPlaylistListener?
    )

    /**
     * Stops the playlist tracker and releases any acquired resources.
     *
     *
     * Must be called once per [.start] call.
     */
    fun stop()

    /**
     * Registers a listener to receive events from the playlist tracker.
     *
     * @param listener The listener.
     */
    fun addListener(listener: PlaylistEventListener?)

    /**
     * Unregisters a listener.
     *
     * @param listener The listener to unregister.
     */
    fun removeListener(listener: PlaylistEventListener?)

    /**
     * Returns the multivariant playlist.
     *
     *
     * If the uri passed to [.start] points to a media playlist, an [ ] with a single variant for said media playlist is returned.
     *
     * @return The multivariant playlist. Null if the initial playlist has yet to be loaded.
     */
    fun getMultivariantPlaylist(): HlsMultivariantPlaylist?

    /**
     * Returns the most recent snapshot available of the playlist referenced by the provided [ ].
     *
     * @param url The [Uri] corresponding to the requested media playlist.
     * @param isForPlayback Whether the caller might use the snapshot to request media segments for
     * playback. If true, the primary playlist may be updated to the one requested.
     * @return The most recent snapshot of the playlist referenced by the provided [Uri]. May be
     * null if no snapshot has been loaded yet.
     */
    fun getPlaylistSnapshot(url: Uri?, isForPlayback: Boolean): HlsMediaPlaylist?

    /**
     * Returns the start time of the first loaded primary playlist, or [C.TIME_UNSET] if no
     * media playlist has been loaded.
     */
    fun getInitialStartTimeUs(): Long

    /**
     * Returns whether the snapshot of the playlist referenced by the provided [Uri] is valid,
     * meaning all the segments referenced by the playlist are expected to be available. If the
     * playlist is not valid then some of the segments may no longer be available.
     *
     * @param url The [Uri].
     * @return Whether the snapshot of the playlist referenced by the provided [Uri] is valid.
     */
    fun isSnapshotValid(url: Uri?): Boolean

    /**
     * If the tracker is having trouble refreshing the multivariant playlist or the primary playlist,
     * this method throws the underlying error. Otherwise, does nothing.
     *
     * @throws IOException The underlying error.
     */
    @Throws(IOException::class)
    fun maybeThrowPrimaryPlaylistRefreshError()

    /**
     * If the playlist is having trouble refreshing the playlist referenced by the given [Uri],
     * this method throws the underlying error.
     *
     * @param url The [Uri].
     * @throws IOException The underyling error.
     */
    @Throws(IOException::class)
    fun maybeThrowPlaylistRefreshError(url: Uri?)

    /**
     * Excludes the given media playlist for the given duration, in milliseconds.
     *
     * @param playlistUrl The URL of the media playlist.
     * @param exclusionDurationMs The duration for which to exclude the playlist.
     * @return Whether exclusion was successful.
     */
    fun excludeMediaPlaylist(playlistUrl: Uri?, exclusionDurationMs: Long): Boolean

    /**
     * Requests a playlist refresh and removes it from the exclusion list.
     *
     *
     * The playlist tracker may choose to delay the playlist refresh. The request is discarded if a
     * refresh was already pending.
     *
     * @param url The [Uri] of the playlist to be refreshed.
     */
    fun refreshPlaylist(url: Uri?)

    /**
     * Returns whether the tracked playlists describe a live stream.
     *
     * @return True if the content is live. False otherwise.
     */
    fun isLive(): Boolean
}