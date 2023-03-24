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

import android.os.Handler
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import java.util.concurrent.CopyOnWriteArrayList

/** Provides estimates of the currently available bandwidth.  */
interface BandwidthMeter {
    /** A listener of [BandwidthMeter] events.  */
    interface EventListener {
        /**
         * Called periodically to indicate that bytes have been transferred or the estimated bitrate has
         * changed.
         *
         *
         * Note: The estimated bitrate is typically derived from more information than just `bytesTransferred` and `elapsedMs`.
         *
         * @param elapsedMs The time taken to transfer `bytesTransferred`, in milliseconds. This
         * is at most the elapsed time since the last callback, but may be less if there were
         * periods during which data was not being transferred.
         * @param bytesTransferred The number of bytes transferred since the last callback.
         * @param bitrateEstimate The estimated bitrate in bits/sec.
         */
        fun onBandwidthSample(elapsedMs: Int, bytesTransferred: Long, bitrateEstimate: Long)

        /** Event dispatcher which allows listener registration.  */
        class EventDispatcher {
            private var listeners: CopyOnWriteArrayList<HandlerAndListener>

            /** Creates an event dispatcher.  */
            constructor() {
                listeners = CopyOnWriteArrayList()
            }

            /** Adds a listener to the event dispatcher.  */
            fun addListener(eventHandler: Handler, eventListener: EventListener) {
                checkNotNull(eventHandler)
                checkNotNull(eventListener)
                removeListener(eventListener)
                listeners.add(HandlerAndListener(eventHandler, eventListener))
            }

            /** Removes a listener from the event dispatcher.  */
            fun removeListener(eventListener: EventListener) {
                for (handlerAndListener in listeners) {
                    if (handlerAndListener.listener === eventListener) {
                        handlerAndListener.release()
                        listeners.remove(handlerAndListener)
                    }
                }
            }

            fun bandwidthSample(elapsedMs: Int, bytesTransferred: Long, bitrateEstimate: Long) {
                for (handlerAndListener in listeners) {
                    if (!handlerAndListener.released) {
                        handlerAndListener.handler?.post {
                            handlerAndListener.listener?.onBandwidthSample(
                                    elapsedMs, bytesTransferred, bitrateEstimate)
                        }
                    }
                }
            }

            private class HandlerAndListener {
                var handler: Handler? = null
                var listener: EventListener? = null

                var released = false

                constructor(handler: Handler?, eventListener: EventListener?) {
                    this.handler = handler
                    this.listener = eventListener
                }

                fun release() {
                    released = true
                }
            }
        }
    }

    /** Returns the estimated bitrate.  */
    fun getBitrateEstimate(): Long

    /**
     * Returns the estimated time to first byte, in microseconds, or [C.TIME_UNSET] if no
     * estimate is available.
     */
    fun getTimeToFirstByteEstimateUs(): Long {
        return C.TIME_UNSET
    }

    /**
     * Returns the [TransferListener] that this instance uses to gather bandwidth information
     * from data transfers. May be null if the implementation does not listen to data transfers.
     */
    fun getTransferListener(): TransferListener?

    /**
     * Adds an [EventListener].
     *
     * @param eventHandler A handler for events.
     * @param eventListener A listener of events.
     */
    fun addEventListener(eventHandler: Handler?, eventListener: EventListener?)

    /**
     * Removes an [EventListener].
     *
     * @param eventListener The listener to be removed.
     */
    fun removeEventListener(eventListener: EventListener?)
}