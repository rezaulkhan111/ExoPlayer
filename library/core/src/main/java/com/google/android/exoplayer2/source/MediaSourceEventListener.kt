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
package com.google.android.exoplayer2.source

import android.os.Handler
import androidx.annotation.CheckResult
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.SelectionReason
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Util.usToMs
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Interface for callbacks to be notified of [MediaSource] events.
 */
interface MediaSourceEventListener {
    /**
     * Called when a load begins.
     *
     * @param windowIndex   The window index in the timeline of the media source this load belongs to.
     * @param mediaPeriodId The [MediaPeriodId] this load belongs to. Null if the load does not
     * belong to a specific media period.
     * @param loadEventInfo The [LoadEventInfo] corresponding to the event. The value of [                      ][LoadEventInfo.uri] won't reflect potential redirection yet and [                      ][LoadEventInfo.responseHeaders] will be empty.
     * @param mediaLoadData The [MediaLoadData] defining the data being loaded.
     */
    fun onLoadStarted(
        windowIndex: Int,
        mediaPeriodId: MediaPeriodId?,
        loadEventInfo: LoadEventInfo?,
        mediaLoadData: MediaLoadData?
    ) {
    }

    /**
     * Called when a load ends.
     *
     * @param windowIndex   The window index in the timeline of the media source this load belongs to.
     * @param mediaPeriodId The [MediaPeriodId] this load belongs to. Null if the load does not
     * belong to a specific media period.
     * @param loadEventInfo The [LoadEventInfo] corresponding to the event. The values of [                      ][LoadEventInfo.elapsedRealtimeMs] and [LoadEventInfo.bytesLoaded] are relative to the
     * corresponding [.onLoadStarted]
     * event.
     * @param mediaLoadData The [MediaLoadData] defining the data being loaded.
     */
    fun onLoadCompleted(
        windowIndex: Int,
        mediaPeriodId: MediaPeriodId?,
        loadEventInfo: LoadEventInfo?,
        mediaLoadData: MediaLoadData?
    ) {
    }

    /**
     * Called when a load is canceled.
     *
     * @param windowIndex   The window index in the timeline of the media source this load belongs to.
     * @param mediaPeriodId The [MediaPeriodId] this load belongs to. Null if the load does not
     * belong to a specific media period.
     * @param loadEventInfo The [LoadEventInfo] corresponding to the event. The values of [                      ][LoadEventInfo.elapsedRealtimeMs] and [LoadEventInfo.bytesLoaded] are relative to the
     * corresponding [.onLoadStarted]
     * event.
     * @param mediaLoadData The [MediaLoadData] defining the data being loaded.
     */
    fun onLoadCanceled(
        windowIndex: Int,
        mediaPeriodId: MediaPeriodId?,
        loadEventInfo: LoadEventInfo?,
        mediaLoadData: MediaLoadData?
    ) {
    }

    /**
     * Called when a load error occurs.
     *
     *
     * The error may or may not have resulted in the load being canceled, as indicated by the
     * `wasCanceled` parameter. If the load was canceled, [.onLoadCanceled] will
     * *not* be called in addition to this method.
     *
     *
     * This method being called does not indicate that playback has failed, or that it will fail.
     * The player may be able to recover from the error. Hence applications should *not*
     * implement this method to display a user visible error or initiate an application level retry.
     * [Player.Listener.onPlayerError] is the appropriate place to implement such behavior. This
     * method is called to provide the application with an opportunity to log the error if it wishes
     * to do so.
     *
     * @param windowIndex   The window index in the timeline of the media source this load belongs to.
     * @param mediaPeriodId The [MediaPeriodId] this load belongs to. Null if the load does not
     * belong to a specific media period.
     * @param loadEventInfo The [LoadEventInfo] corresponding to the event. The values of [                      ][LoadEventInfo.elapsedRealtimeMs] and [LoadEventInfo.bytesLoaded] are relative to the
     * corresponding [.onLoadStarted]
     * event.
     * @param mediaLoadData The [MediaLoadData] defining the data being loaded.
     * @param error         The load error.
     * @param wasCanceled   Whether the load was canceled as a result of the error.
     */
    fun onLoadError(
        windowIndex: Int,
        mediaPeriodId: MediaPeriodId?,
        loadEventInfo: LoadEventInfo?,
        mediaLoadData: MediaLoadData?,
        error: IOException?,
        wasCanceled: Boolean
    ) {
    }

    /**
     * Called when data is removed from the back of a media buffer, typically so that it can be
     * re-buffered in a different format.
     *
     * @param windowIndex   The window index in the timeline of the media source this load belongs to.
     * @param mediaPeriodId The [MediaPeriodId] the media belongs to.
     * @param mediaLoadData The [MediaLoadData] defining the media being discarded.
     */
    fun onUpstreamDiscarded(
        windowIndex: Int, mediaPeriodId: MediaPeriodId?, mediaLoadData: MediaLoadData?
    ) {
    }

    /**
     * Called when a downstream format change occurs (i.e. when the format of the media being read
     * from one or more [SampleStream]s provided by the source changes).
     *
     * @param windowIndex   The window index in the timeline of the media source this load belongs to.
     * @param mediaPeriodId The [MediaPeriodId] the media belongs to.
     * @param mediaLoadData The [MediaLoadData] defining the newly selected downstream data.
     */
    fun onDownstreamFormatChanged(
        windowIndex: Int, mediaPeriodId: MediaPeriodId?, mediaLoadData: MediaLoadData?
    ) {
    }

    /**
     * Dispatches events to [MediaSourceEventListeners][MediaSourceEventListener].
     */
    class EventDispatcher {

        /** The timeline window index reported with the events.  */
        var windowIndex = 0

        /** The [MediaPeriodId] reported with the events.  */
        var mediaPeriodId: MediaPeriodId? = null

        private var listenerAndHandlers: CopyOnWriteArrayList<ListenerAndHandler>? = null
        private var mediaTimeOffsetMs: Long = 0

        /**
         * Creates an event dispatcher.
         */
        constructor() {
            EventDispatcher( /* listenerAndHandlers= */
                CopyOnWriteArrayList<ListenerAndHandler>(),  /* windowIndex= */
                0,  /* mediaPeriodId= */
                null,  /* mediaTimeOffsetMs= */
                0
            )
        }

        private constructor(
            listenerAndHandlers: CopyOnWriteArrayList<ListenerAndHandler>?,
            windowIndex: Int,
            mediaPeriodId: MediaPeriodId?,
            mediaTimeOffsetMs: Long
        ) {
            this.listenerAndHandlers = listenerAndHandlers
            this.windowIndex = windowIndex
            this.mediaPeriodId = mediaPeriodId
            this.mediaTimeOffsetMs = mediaTimeOffsetMs
        }

        /**
         * Creates a view of the event dispatcher with pre-configured window index, media period id, and
         * media time offset.
         *
         * @param windowIndex       The timeline window index to be reported with the events.
         * @param mediaPeriodId     The [MediaPeriodId] to be reported with the events.
         * @param mediaTimeOffsetMs The offset to be added to all media times, in milliseconds.
         * @return A view of the event dispatcher with the pre-configured parameters.
         */
        @CheckResult
        fun withParameters(
            windowIndex: Int, mediaPeriodId: MediaPeriodId?, mediaTimeOffsetMs: Long
        ): EventDispatcher {
            return EventDispatcher(
                this.listenerAndHandlers, windowIndex, mediaPeriodId, mediaTimeOffsetMs
            )
        }

        /**
         * Adds a listener to the event dispatcher.
         *
         * @param handler       A handler on the which listener events will be posted.
         * @param eventListener The listener to be added.
         */
        fun addEventListener(handler: Handler?, eventListener: MediaSourceEventListener) {
            checkNotNull(handler)
            checkNotNull(eventListener)
            listenerAndHandlers?.add(ListenerAndHandler(handler, eventListener))
        }

        /**
         * Removes a listener from the event dispatcher.
         *
         * @param eventListener The listener to be removed.
         */
        fun removeEventListener(eventListener: MediaSourceEventListener) {
            for (listenerAndHandler in listenerAndHandlers!!) {
                if (listenerAndHandler.listener === eventListener) {
                    listenerAndHandlers?.remove(listenerAndHandler)
                }
            }
        }
        /**
         * Dispatches [.onLoadStarted].
         */
        /**
         * Dispatches [.onLoadStarted].
         */
        @JvmOverloads
        fun loadStarted(
            loadEventInfo: LoadEventInfo?,
            @C.DataType dataType: Int,
            @TrackType trackType: Int =  /* trackType= */
                C.TRACK_TYPE_UNKNOWN,
            trackFormat: Format? =  /* trackFormat= */
                null,
            @SelectionReason trackSelectionReason: Int =  /* trackSelectionReason= */
                C.SELECTION_REASON_UNKNOWN,
            trackSelectionData: Any? =  /* trackSelectionData= */
                null,
            mediaStartTimeUs: Long =  /* mediaStartTimeUs= */
                C.TIME_UNSET,
            mediaEndTimeUs: Long =  /* mediaEndTimeUs= */
                C.TIME_UNSET
        ) {
            loadStarted(
                loadEventInfo, MediaLoadData(
                    dataType,
                    trackType,
                    trackFormat,
                    trackSelectionReason,
                    trackSelectionData,
                    adjustMediaTime(mediaStartTimeUs),
                    adjustMediaTime(mediaEndTimeUs)
                )
            )
        }

        /**
         * Dispatches [.onLoadStarted].
         */
        fun loadStarted(loadEventInfo: LoadEventInfo?, mediaLoadData: MediaLoadData?) {
            for (listenerAndHandler in listenerAndHandlers) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) {
                    listener.onLoadStarted(
                        windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData
                    )
                }
            }
        }
        /**
         * Dispatches [.onLoadCompleted].
         */
        /**
         * Dispatches [.onLoadCompleted].
         */
        @JvmOverloads
        fun loadCompleted(
            loadEventInfo: LoadEventInfo?,
            @C.DataType dataType: Int,
            @TrackType trackType: Int =  /* trackType= */
                C.TRACK_TYPE_UNKNOWN,
            trackFormat: Format? =  /* trackFormat= */
                null,
            @SelectionReason trackSelectionReason: Int =  /* trackSelectionReason= */
                C.SELECTION_REASON_UNKNOWN,
            trackSelectionData: Any? =  /* trackSelectionData= */
                null,
            mediaStartTimeUs: Long =  /* mediaStartTimeUs= */
                C.TIME_UNSET,
            mediaEndTimeUs: Long =  /* mediaEndTimeUs= */
                C.TIME_UNSET
        ) {
            loadCompleted(
                loadEventInfo, MediaLoadData(
                    dataType,
                    trackType,
                    trackFormat,
                    trackSelectionReason,
                    trackSelectionData,
                    adjustMediaTime(mediaStartTimeUs),
                    adjustMediaTime(mediaEndTimeUs)
                )
            )
        }

        /**
         * Dispatches [.onLoadCompleted].
         */
        fun loadCompleted(loadEventInfo: LoadEventInfo?, mediaLoadData: MediaLoadData?) {
            for (listenerAndHandler in listenerAndHandlers) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) {
                    listener.onLoadCompleted(
                        windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData
                    )
                }
            }
        }
        /**
         * Dispatches [.onLoadCanceled].
         */
        /**
         * Dispatches [.onLoadCanceled].
         */
        @JvmOverloads
        fun loadCanceled(
            loadEventInfo: LoadEventInfo?,
            @C.DataType dataType: Int,
            @TrackType trackType: Int =  /* trackType= */
                C.TRACK_TYPE_UNKNOWN,
            trackFormat: Format? =  /* trackFormat= */
                null,
            @SelectionReason trackSelectionReason: Int =  /* trackSelectionReason= */
                C.SELECTION_REASON_UNKNOWN,
            trackSelectionData: Any? =  /* trackSelectionData= */
                null,
            mediaStartTimeUs: Long =  /* mediaStartTimeUs= */
                C.TIME_UNSET,
            mediaEndTimeUs: Long =  /* mediaEndTimeUs= */
                C.TIME_UNSET
        ) {
            loadCanceled(
                loadEventInfo, MediaLoadData(
                    dataType,
                    trackType,
                    trackFormat,
                    trackSelectionReason,
                    trackSelectionData,
                    adjustMediaTime(mediaStartTimeUs),
                    adjustMediaTime(mediaEndTimeUs)
                )
            )
        }

        /**
         * Dispatches [.onLoadCanceled].
         */
        fun loadCanceled(loadEventInfo: LoadEventInfo?, mediaLoadData: MediaLoadData?) {
            for (listenerAndHandler in listenerAndHandlers) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) {
                    listener.onLoadCanceled(
                        windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData
                    )
                }
            }
        }

        /**
         * Dispatches [.onLoadError].
         */
        fun loadError(
            loadEventInfo: LoadEventInfo?,
            @C.DataType dataType: Int,
            error: IOException?,
            wasCanceled: Boolean
        ) {
            loadError(
                loadEventInfo, dataType,  /* trackType= */
                C.TRACK_TYPE_UNKNOWN,  /* trackFormat= */
                null,  /* trackSelectionReason= */
                C.SELECTION_REASON_UNKNOWN,  /* trackSelectionData= */
                null,  /* mediaStartTimeUs= */
                C.TIME_UNSET,  /* mediaEndTimeUs= */
                C.TIME_UNSET, error, wasCanceled
            )
        }

        /**
         * Dispatches [.onLoadError].
         */
        fun loadError(
            loadEventInfo: LoadEventInfo?,
            @C.DataType dataType: Int,
            @TrackType trackType: Int,
            trackFormat: Format?,
            @SelectionReason trackSelectionReason: Int,
            trackSelectionData: Any?,
            mediaStartTimeUs: Long,
            mediaEndTimeUs: Long,
            error: IOException?,
            wasCanceled: Boolean
        ) {
            loadError(
                loadEventInfo, MediaLoadData(
                    dataType,
                    trackType,
                    trackFormat,
                    trackSelectionReason,
                    trackSelectionData,
                    adjustMediaTime(mediaStartTimeUs),
                    adjustMediaTime(mediaEndTimeUs)
                ), error, wasCanceled
            )
        }

        /**
         * Dispatches [.onLoadError].
         */
        fun loadError(
            loadEventInfo: LoadEventInfo?,
            mediaLoadData: MediaLoadData?,
            error: IOException?,
            wasCanceled: Boolean
        ) {
            for (listenerAndHandler in listenerAndHandlers) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) {
                    listener.onLoadError(
                        windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData, error, wasCanceled
                    )
                }
            }
        }

        /**
         * Dispatches [.onUpstreamDiscarded].
         */
        fun upstreamDiscarded(trackType: Int, mediaStartTimeUs: Long, mediaEndTimeUs: Long) {
            upstreamDiscarded(
                MediaLoadData(
                    C.DATA_TYPE_MEDIA, trackType,  /* trackFormat= */
                    null, C.SELECTION_REASON_ADAPTIVE,  /* trackSelectionData= */
                    null, adjustMediaTime(mediaStartTimeUs), adjustMediaTime(mediaEndTimeUs)
                )
            )
        }

        /**
         * Dispatches [.onUpstreamDiscarded].
         */
        fun upstreamDiscarded(mediaLoadData: MediaLoadData?) {
            val mediaPeriodId = checkNotNull(mediaPeriodId)
            for (listenerAndHandler in listenerAndHandlers) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) { listener.onUpstreamDiscarded(windowIndex, mediaPeriodId, mediaLoadData) }
            }
        }

        /**
         * Dispatches [.onDownstreamFormatChanged].
         */
        fun downstreamFormatChanged(
            @TrackType trackType: Int,
            trackFormat: Format?,
            @SelectionReason trackSelectionReason: Int,
            trackSelectionData: Any?,
            mediaTimeUs: Long
        ) {
            downstreamFormatChanged(
                MediaLoadData(
                    C.DATA_TYPE_MEDIA,
                    trackType,
                    trackFormat,
                    trackSelectionReason,
                    trackSelectionData,
                    adjustMediaTime(mediaTimeUs),  /* mediaEndTimeMs= */
                    C.TIME_UNSET
                )
            )
        }

        /**
         * Dispatches [.onDownstreamFormatChanged].
         */
        fun downstreamFormatChanged(mediaLoadData: MediaLoadData?) {
            for (listenerAndHandler in listenerAndHandlers) {
                val listener = listenerAndHandler.listener
                postOrRun(
                    listenerAndHandler.handler
                ) { listener.onDownstreamFormatChanged(windowIndex, mediaPeriodId, mediaLoadData) }
            }
        }

        private fun adjustMediaTime(mediaTimeUs: Long): Long {
            val mediaTimeMs = usToMs(mediaTimeUs)
            return if (mediaTimeMs == C.TIME_UNSET) C.TIME_UNSET else mediaTimeOffsetMs + mediaTimeMs
        }

        private class ListenerAndHandler(
            var handler: Handler?, var listener: MediaSourceEventListener
        )
    }
}