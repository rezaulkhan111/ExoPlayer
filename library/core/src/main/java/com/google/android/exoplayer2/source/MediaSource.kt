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
package com.google.android.exoplayer2.source

import android.os.Handler
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.drm.DrmSessionEventListener
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider
import com.google.android.exoplayer2.source.MediaSource.MediaSourceCaller
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.TransferListener
import java.io.IOException

/**
 * Defines and provides media to be played by an [ExoPlayer]. A MediaSource has two main
 * responsibilities:
 *
 *
 *  * To provide the player with a [Timeline] defining the structure of its media, and to
 * provide a new timeline whenever the structure of the media changes. The MediaSource
 * provides these timelines by calling [MediaSourceCaller.onSourceInfoRefreshed] on the
 * [MediaSourceCaller]s passed to [.prepareSource].
 *  * To provide [MediaPeriod] instances for the periods in its timeline. MediaPeriods are
 * obtained by calling [.createPeriod], and provide a
 * way for the player to load and read the media.
 *
 *
 * All methods are called on the player's internal playback thread, as described in the [ ] Javadoc. They should not be called directly from application code. Instances can be
 * re-used, but only for one [ExoPlayer] instance simultaneously.
 */
interface MediaSource {
    /** Factory for creating [MediaSources][MediaSource] from [MediaItems][MediaItem].  */
    interface Factory {
        /**
         * An instance that throws [UnsupportedOperationException] from [.createMediaSource]
         * and [.getSupportedTypes].
         */
        var UNSUPPORTED: Factory
            get() = MediaSourceFactory.UNSUPPORTED
            set(value) = TODO()

        /**
         * Sets the [DrmSessionManagerProvider] used to obtain a [DrmSessionManager] for a
         * [MediaItem].
         *
         * @return This factory, for convenience.
         */
        fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider?): Factory?

        /**
         * Sets an optional [LoadErrorHandlingPolicy].
         *
         * @return This factory, for convenience.
         */
        fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy?): Factory?

        /**
         * Returns the [content types][C.ContentType] supported by media sources created by this
         * factory.
         */
        @C.ContentType
        fun getSupportedTypes(): IntArray?

        /**
         * Creates a new [MediaSource] with the specified [MediaItem].
         *
         * @param mediaItem The media item to play.
         * @return The new [media source][MediaSource].
         */
        fun createMediaSource(mediaItem: MediaItem?): MediaSource?
    }

    /** A caller of media sources, which will be notified of source events.  */
    interface MediaSourceCaller {
        /**
         * Called when the [Timeline] has been refreshed.
         *
         *
         * Called on the playback thread.
         *
         * @param source The [MediaSource] whose info has been refreshed.
         * @param timeline The source's timeline.
         */
        fun onSourceInfoRefreshed(source: MediaSource?, timeline: Timeline?)
    }
    // TODO(b/172315872) Delete when all clients have been migrated to base class.
    /**
     * Identifier for a [MediaPeriod].
     *
     *
     * Extends for backward-compatibility [ ].
     */
    class MediaPeriodId : com.google.android.exoplayer2.source.MediaPeriodId {
        /** See [com.google.android.exoplayer2.source.MediaPeriodId.MediaPeriodId].  */
        constructor(periodUid: Any?) : super(periodUid!!) {}

        /**
         * See [com.google.android.exoplayer2.source.MediaPeriodId.MediaPeriodId].
         */
        constructor(periodUid: Any?, windowSequenceNumber: Long) : super(periodUid!!, windowSequenceNumber) {}

        /**
         * See [com.google.android.exoplayer2.source.MediaPeriodId.MediaPeriodId].
         */
        constructor(periodUid: Any?, windowSequenceNumber: Long, nextAdGroupIndex: Int) : super(periodUid!!, windowSequenceNumber, nextAdGroupIndex) {}

        /**
         * See [com.google.android.exoplayer2.source.MediaPeriodId.MediaPeriodId].
         */
        constructor(periodUid: Any?, adGroupIndex: Int, adIndexInAdGroup: Int, windowSequenceNumber: Long) : super(periodUid!!, adGroupIndex, adIndexInAdGroup, windowSequenceNumber) {
        }

        /** Wraps an [com.google.android.exoplayer2.source.MediaPeriodId] into a MediaPeriodId.  */
        constructor(mediaPeriodId: com.google.android.exoplayer2.source.MediaPeriodId?) : super(mediaPeriodId!!) {}

        /** See [com.google.android.exoplayer2.source.MediaPeriodId.copyWithPeriodUid].  */
        override fun copyWithPeriodUid(newPeriodUid: Any): MediaPeriodId? {
            return MediaPeriodId(super.copyWithPeriodUid(newPeriodUid))
        }

        /**
         * See [ ][com.google.android.exoplayer2.source.MediaPeriodId.copyWithWindowSequenceNumber].
         */
        override fun copyWithWindowSequenceNumber(windowSequenceNumber: Long): MediaPeriodId? {
            return MediaPeriodId(super.copyWithWindowSequenceNumber(windowSequenceNumber))
        }
    }

    /**
     * Adds a [MediaSourceEventListener] to the list of listeners which are notified of media
     * source events.
     *
     * @param handler A handler on the which listener events will be posted.
     * @param eventListener The listener to be added.
     */
    fun addEventListener(handler: Handler?, eventListener: MediaSourceEventListener?)

    /**
     * Removes a [MediaSourceEventListener] from the list of listeners which are notified of
     * media source events.
     *
     * @param eventListener The listener to be removed.
     */
    fun removeEventListener(eventListener: MediaSourceEventListener?)

    /**
     * Adds a [DrmSessionEventListener] to the list of listeners which are notified of DRM
     * events for this media source.
     *
     * @param handler A handler on the which listener events will be posted.
     * @param eventListener The listener to be added.
     */
    fun addDrmEventListener(handler: Handler?, eventListener: DrmSessionEventListener?)

    /**
     * Removes a [DrmSessionEventListener] from the list of listeners which are notified of DRM
     * events for this media source.
     *
     * @param eventListener The listener to be removed.
     */
    fun removeDrmEventListener(eventListener: DrmSessionEventListener?)

    /**
     * Returns the initial placeholder timeline that is returned immediately when the real timeline is
     * not yet known, or null to let the player create an initial timeline.
     *
     *
     * The initial timeline must use the same uids for windows and periods that the real timeline
     * will use. It also must provide windows which are marked as dynamic to indicate that the window
     * is expected to change when the real timeline arrives.
     *
     *
     * Any media source which has multiple windows should typically provide such an initial
     * timeline to make sure the player reports the correct number of windows immediately.
     */
    open fun getInitialTimeline(): Timeline? {
        return null
    }

    /**
     * Returns true if the media source is guaranteed to never have zero or more than one window.
     *
     *
     * The default implementation returns `true`.
     *
     * @return true if the source has exactly one window.
     */
    fun isSingleWindow(): Boolean {
        return true
    }

    /**
     * Returns the [MediaItem] whose media is provided by the source.
     */
    fun getMediaItem(): MediaItem?

    /**
     * @deprecated Implement {@link #prepareSource(MediaSourceCaller, TransferListener, PlayerId)}
     * instead.
     */
    @Deprecated("")
    fun prepareSource(
            caller: MediaSourceCaller?, mediaTransferListener: TransferListener?) {
        prepareSource(caller, mediaTransferListener, PlayerId.UNSET)
    }

    /**
     * Registers a [MediaSourceCaller]. Starts source preparation if needed and enables the
     * source for the creation of [MediaPerods][MediaPeriod].
     *
     *
     * Should not be called directly from application code.
     *
     *
     * [MediaSourceCaller.onSourceInfoRefreshed] will be called once
     * the source has a [Timeline].
     *
     *
     * For each call to this method, a call to [.releaseSource] is needed
     * to remove the caller and to release the source if no longer required.
     *
     * @param caller The [MediaSourceCaller] to be registered.
     * @param mediaTransferListener The transfer listener which should be informed of any media data
     * transfers. May be null if no listener is available. Note that this listener should be only
     * informed of transfers related to the media loads and not of auxiliary loads for manifests
     * and other data.
     * @param playerId The [PlayerId] of the player using this media source.
     */
    fun prepareSource(
            caller: MediaSourceCaller?,
            mediaTransferListener: TransferListener?,
            playerId: PlayerId?)

    /**
     * Throws any pending error encountered while loading or refreshing source information.
     *
     *
     * Should not be called directly from application code.
     *
     *
     * Must only be called after [.prepareSource].
     */
    @Throws(IOException::class)
    fun maybeThrowSourceInfoRefreshError()

    /**
     * Enables the source for the creation of [MediaPeriods][MediaPeriod].
     *
     *
     * Should not be called directly from application code.
     *
     *
     * Must only be called after [.prepareSource].
     *
     * @param caller The [MediaSourceCaller] enabling the source.
     */
    fun enable(caller: MediaSourceCaller?)

    /**
     * Returns a new [MediaPeriod] identified by `periodId`.
     *
     *
     * Should not be called directly from application code.
     *
     *
     * Must only be called if the source is enabled.
     *
     * @param id The identifier of the period.
     * @param allocator An [Allocator] from which to obtain media buffer allocations.
     * @param startPositionUs The expected start position, in microseconds.
     * @return A new [MediaPeriod].
     */
    fun createPeriod(id: MediaPeriodId?, allocator: Allocator?, startPositionUs: Long): MediaPeriod?

    /**
     * Releases the period.
     *
     *
     * Should not be called directly from application code.
     *
     * @param mediaPeriod The period to release.
     */
    fun releasePeriod(mediaPeriod: MediaPeriod?)

    /**
     * Disables the source for the creation of [MediaPeriods][MediaPeriod]. The implementation
     * should not hold onto limited resources used for the creation of media periods.
     *
     *
     * Should not be called directly from application code.
     *
     *
     * Must only be called after all [MediaPeriods][MediaPeriod] previously created by [ ][.createPeriod] have been released by [ ][.releasePeriod].
     *
     * @param caller The [MediaSourceCaller] disabling the source.
     */
    fun disable(caller: MediaSourceCaller?)

    /**
     * Unregisters a caller, and disables and releases the source if no longer required.
     *
     *
     * Should not be called directly from application code.
     *
     *
     * Must only be called if all created [MediaPeriods][MediaPeriod] have been released by
     * [.releasePeriod].
     *
     * @param caller The [MediaSourceCaller] to be unregistered.
     */
    fun releaseSource(caller: MediaSourceCaller?)
}