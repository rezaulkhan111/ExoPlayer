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
package com.google.android.exoplayer2.drm

import android.os.Handler
import androidx.annotation.CheckResult
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import java.util.concurrent.CopyOnWriteArrayList

/** Listener of [DrmSessionManager] events.  */
interface DrmSessionEventListener {

    @Deprecated("Implement {@link #onDrmSessionAcquired(int, MediaPeriodId, int)} instead.")
    fun onDrmSessionAcquired(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?) {
    }

    /**
     * Called each time a drm session is acquired.
     *
     * @param windowIndex The window index in the timeline this media period belongs to.
     * @param mediaPeriodId The [MediaPeriodId] associated with the drm session.
     * @param state The [DrmSession.State] of the session when the acquisition completed.
     */
    fun onDrmSessionAcquired(
        windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, state: @DrmSession.State Int
    ) {
    }

    /**
     * Called each time keys are loaded.
     *
     * @param windowIndex The window index in the timeline this media period belongs to.
     * @param mediaPeriodId The [MediaPeriodId] associated with the drm session.
     */
    fun onDrmKeysLoaded(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?) {}

    /**
     * Called when a drm error occurs.
     *
     *
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error and continue. Hence applications should
     * *not* implement this method to display a user visible error or initiate an application
     * level retry ([Player.Listener.onPlayerError] is the appropriate place to implement such
     * behavior). This method is called to provide the application with an opportunity to log the
     * error if it wishes to do so.
     *
     * @param windowIndex The window index in the timeline this media period belongs to.
     * @param mediaPeriodId The [MediaPeriodId] associated with the drm session.
     * @param error The corresponding exception.
     */
    fun onDrmSessionManagerError(
        windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, error: Exception?
    ) {
    }

    /**
     * Called each time offline keys are restored.
     *
     * @param windowIndex The window index in the timeline this media period belongs to.
     * @param mediaPeriodId The [MediaPeriodId] associated with the drm session.
     */
    fun onDrmKeysRestored(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?) {}

    /**
     * Called each time offline keys are removed.
     *
     * @param windowIndex The window index in the timeline this media period belongs to.
     * @param mediaPeriodId The [MediaPeriodId] associated with the drm session.
     */
    fun onDrmKeysRemoved(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?) {}

    /**
     * Called each time a drm session is released.
     *
     * @param windowIndex The window index in the timeline this media period belongs to.
     * @param mediaPeriodId The [MediaPeriodId] associated with the drm session.
     */
    fun onDrmSessionReleased(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?) {}

    /** Dispatches events to [DrmSessionEventListeners][DrmSessionEventListener].  */
    class EventDispatcher {
        /**
         * The timeline window index reported with the events.
         */
        var windowIndex = 0

        /**
         * The [MediaPeriodId] reported with the events.
         */
        var mediaPeriodId: MediaSource.MediaPeriodId? = null

        private var listenerAndHandlers: CopyOnWriteArrayList<ListenerAndHandler>? = null

        /**
         * Creates an event dispatcher.
         */
        constructor() {
            EventDispatcher( /* listenerAndHandlers= */
                CopyOnWriteArrayList<ListenerAndHandler>(),  /* windowIndex= */
                0,  /* mediaPeriodId= */
                null
            )
        }

        private constructor(
            listenerAndHandlers: CopyOnWriteArrayList<ListenerAndHandler>?,
            windowIndex: Int,
            mediaPeriodId: MediaPeriodId?
        ) {
            this.listenerAndHandlers = listenerAndHandlers
            this.windowIndex = windowIndex
            this.mediaPeriodId = mediaPeriodId
        }

        /**
         * Creates a view of the event dispatcher with the provided window index and media period id.
         *
         * @param windowIndex   The timeline window index to be reported with the events.
         * @param mediaPeriodId The [MediaPeriodId] to be reported with the events.
         * @return A view of the event dispatcher with the pre-configured parameters.
         */
        @CheckResult
        fun withParameters(windowIndex: Int, mediaPeriodId: MediaPeriodId?): EventDispatcher? {
            return DrmSessionEventListener.EventDispatcher(
                listenerAndHandlers, windowIndex, mediaPeriodId
            )
        }

        /**
         * Adds a listener to the event dispatcher.
         *
         * @param handler       A handler on the which listener events will be posted.
         * @param eventListener The listener to be added.
         */
        fun addEventListener(handler: Handler?, eventListener: DrmSessionEventListener?) {
            checkNotNull(handler)
            checkNotNull(eventListener)
            listenerAndHandlers!!.add(
                ListenerAndHandler(
                    handler, eventListener!!
                )
            )
        }

        /**
         * Removes a listener from the event dispatcher.
         *
         * @param eventListener The listener to be removed.
         */
        fun removeEventListener(eventListener: DrmSessionEventListener) {
            for (listenerAndHandler in listenerAndHandlers!!) {
                if (listenerAndHandler.listener === eventListener) {
                    listenerAndHandlers!!.remove(listenerAndHandler)
                }
            }
        }

        /**
         * Dispatches [.onDrmSessionAcquired] and [ ][.onDrmSessionAcquired].
         */
        // Calls deprecated listener method.
        fun drmSessionAcquired(state: @DrmSession.State Int) {
            for (listenerAndHandler in listenerAndHandlers!!) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) {
                    listener.onDrmSessionAcquired(windowIndex, mediaPeriodId)
                    listener.onDrmSessionAcquired(windowIndex, mediaPeriodId, state)
                }
            }
        }

        /**
         * Dispatches [.onDrmKeysLoaded].
         */
        fun drmKeysLoaded() {
            for (listenerAndHandler in listenerAndHandlers!!) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) { listener.onDrmKeysLoaded(windowIndex, mediaPeriodId) }
            }
        }

        /**
         * Dispatches [.onDrmSessionManagerError].
         */
        fun drmSessionManagerError(error: Exception?) {
            for (listenerAndHandler in listenerAndHandlers!!) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) { listener.onDrmSessionManagerError(windowIndex, mediaPeriodId, error) }
            }
        }

        /**
         * Dispatches [.onDrmKeysRestored].
         */
        fun drmKeysRestored() {
            for (listenerAndHandler in listenerAndHandlers!!) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) { listener.onDrmKeysRestored(windowIndex, mediaPeriodId) }
            }
        }

        /**
         * Dispatches [.onDrmKeysRemoved].
         */
        fun drmKeysRemoved() {
            for (listenerAndHandler in listenerAndHandlers!!) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) { listener.onDrmKeysRemoved(windowIndex, mediaPeriodId) }
            }
        }

        /**
         * Dispatches [.onDrmSessionReleased].
         */
        fun drmSessionReleased() {
            for (listenerAndHandler in listenerAndHandlers!!) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) { listener.onDrmSessionReleased(windowIndex, mediaPeriodId) }
            }
        }


        private class ListenerAndHandler {
            var handler: Handler? = null
            var listener: DrmSessionEventListener? = null

            constructor(handler: Handler?, listener: DrmSessionEventListener?) {
                this.handler = handler
                this.listener = listener
            }
        }
    }
}