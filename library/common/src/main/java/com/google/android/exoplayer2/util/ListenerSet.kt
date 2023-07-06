/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.util

import android.os.Looper
import android.os.Message
import androidx.annotation.CheckResult
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

/**
 * A set of listeners.
 * Events are guaranteed to arrive in the order in which they happened even if a new event is
 * triggered recursively from another listener.
 * Events are also guaranteed to be only sent to the listeners registered at the time the event
 * was enqueued and haven't been removed since.
 * @param <T> The listener type.
</T> */
class ListenerSet<T : Any?> {
    /**
     * An event sent to a listener.
     * @param <T> The listener type.
    </T> */
    interface Event<T> {
        /** Invokes the event notification on the given listener.  */
        operator fun invoke(listener: T)
    }

    /**
     * An event sent to a listener when all other events sent during one [Looper] message queue
     * iteration were handled by the listener.
     * @param <T> The listener type.
    </T> */
    interface IterationFinishedEvent<T> {
        /**
         * Invokes the iteration finished event.
         *
         * @param listener The listener to invoke the event on.
         * @param eventFlags The combined event [flags][FlagSet] of all events sent in this
         * iteration.
         */
        operator fun invoke(listener: T, eventFlags: FlagSet?)
    }

    private var clock: Clock? = null
    private var handler: HandlerWrapper? = null
    private var iterationFinishedEvent: IterationFinishedEvent<T>? = null
    private var listeners: CopyOnWriteArraySet<ListenerHolder<T>>? = null
    private var flushingEvents: ArrayDeque<Runnable>? = null
    private var queuedEvents: ArrayDeque<Runnable>? = null

    private var released = false

    /**
     * Creates a new listener set.
     * @param looper A [Looper] used to call listeners on. The same [Looper] must be used
     * to call all other methods of this class.
     * @param clock A [Clock].
     * @param iterationFinishedEvent An [IterationFinishedEvent] sent when all other events sent
     * during one [Looper] message queue iteration were handled by the listeners.
     */
    constructor(looper: Looper?, clock: Clock?, iterationFinishedEvent: IterationFinishedEvent<T>?) {
        ListenerSet( /* listeners= */CopyOnWriteArraySet<ListenerHolder<T>>(), looper, clock, iterationFinishedEvent)
    }

    private constructor(listeners: CopyOnWriteArraySet<ListenerHolder<T>>?, looper: Looper?, clock: Clock?, iterationFinishedEvent: IterationFinishedEvent<T>?) {
        this.clock = clock
        this.listeners = listeners
        this.iterationFinishedEvent = iterationFinishedEvent
        flushingEvents = ArrayDeque()
        queuedEvents = ArrayDeque()
        // It's safe to use "this" because we don't send a message before exiting the constructor.
        val handler = clock!!.createHandler(looper) { message: Message -> handleMessage(message) }
        this.handler = handler
    }

    /**
     * Copies the listener set.
     * @param looper The new [Looper] for the copied listener set.
     * @param iterationFinishedEvent The new [IterationFinishedEvent] sent when all other events
     * sent during one [Looper] message queue iteration were handled by the listeners.
     * @return The copied listener set.
     */
    @CheckResult
    fun copy(looper: Looper?, iterationFinishedEvent: IterationFinishedEvent<T>?): ListenerSet<T>? {
        return copy(looper, clock, iterationFinishedEvent)
    }

    /**
     * Copies the listener set.
     * @param looper The new [Looper] for the copied listener set.
     * @param clock The new [Clock] for the copied listener set.
     * @param iterationFinishedEvent The new [IterationFinishedEvent] sent when all other events
     * sent during one [Looper] message queue iteration were handled by the listeners.
     * @return The copied listener set.
     */
    @CheckResult
    fun copy(looper: Looper?, clock: Clock?, iterationFinishedEvent: IterationFinishedEvent<T>?): ListenerSet<T>? {
        return ListenerSet(listeners, looper, clock, iterationFinishedEvent)
    }

    /**
     * Adds a listener to the set.
     * If a listener is already present, it will not be added again.
     * @param listener The listener to be added.
     */
    fun add(listener: T) {
        if (released) {
            return
        }
        checkNotNull<T>(listener)
        listeners!!.add(ListenerHolder(listener))
    }

    /**
     * Removes a listener from the set.
     * If the listener is not present, nothing happens.
     * @param listener The listener to be removed.
     */
    fun remove(listener: T) {
        for (listenerHolder in listeners!!) {
            if (listenerHolder.listener == listener) {
                listenerHolder.release(iterationFinishedEvent!!)
                listeners?.remove(listenerHolder)
            }
        }
    }

    /** Removes all listeners from the set.  */
    fun clear() {
        listeners!!.clear()
    }

    /** Returns the number of added listeners.  */
    fun size(): Int {
        return listeners!!.size
    }

    /**
     * Adds an event that is sent to the listeners when [.flushEvents] is called.
     * @param eventFlag An integer indicating the type of the event, or [C.INDEX_UNSET] to
     * report this event without flag.
     * @param event The event.
     */
    fun queueEvent(eventFlag: Int, event: Event<T>?) {
        val listenerSnapshot = CopyOnWriteArraySet(listeners)
        queuedEvents!!.add(Runnable {
            for (holder in listenerSnapshot) {
                holder.invoke(eventFlag, event!!)
            }
        })
    }

    /** Notifies listeners of events previously enqueued with [.queueEvent].  */
    fun flushEvents() {
        if (queuedEvents!!.isEmpty()) {
            return
        }
        if (!handler!!.hasMessages(MSG_ITERATION_FINISHED)) {
            handler?.sendMessageAtFrontOfQueue(handler?.obtainMessage(MSG_ITERATION_FINISHED))
        }
        val recursiveFlushInProgress = !flushingEvents!!.isEmpty()
        flushingEvents!!.addAll(queuedEvents!!)
        queuedEvents!!.clear()
        if (recursiveFlushInProgress) {
            // Recursive call to flush. Let the outer call handle the flush queue.
            return
        }
        while (!flushingEvents!!.isEmpty()) {
            flushingEvents!!.peekFirst().run()
            flushingEvents!!.removeFirst()
        }
    }

    /**
     * [Queues][.queueEvent] a single event and immediately [ flushes][.flushEvents] the event queue to notify all listeners.
     * @param eventFlag An integer flag indicating the type of the event, or [C.INDEX_UNSET] to
     * report this event without flag.
     * @param event The event.
     */
    fun sendEvent(eventFlag: Int, event: Event<T>?) {
        queueEvent(eventFlag, event)
        flushEvents()
    }

    /**
     * Releases the set of listeners immediately.
     * This will ensure no events are sent to any listener after this method has been called.
     */
    fun release() {
        for (listenerHolder in listeners!!) {
            listenerHolder.release(iterationFinishedEvent!!)
        }
        listeners?.clear()
        released = true
    }

    private fun handleMessage(message: Message): Boolean {
        for (holder in listeners!!) {
            holder.iterationFinished(iterationFinishedEvent!!)
            if (handler!!.hasMessages(MSG_ITERATION_FINISHED)) {
                // The invocation above triggered new events (and thus scheduled a new message). We need
                // to stop here because this new message will take care of informing every listener about
                // the new update (including the ones already called here).
                break
            }
        }
        return true
    }

    private class ListenerHolder<T : Any?>(val listener: T) {
        private var flagsBuilder: FlagSet.Builder
        private var needsIterationFinishedEvent = false
        private var released = false

        init {
            flagsBuilder = FlagSet.Builder()
        }

        fun release(event: IterationFinishedEvent<T>) {
            released = true
            if (needsIterationFinishedEvent) {
                needsIterationFinishedEvent = false
                event.invoke(listener, flagsBuilder.build())
            }
        }

        operator fun invoke(eventFlag: Int, event: Event<T>) {
            if (!released) {
                if (eventFlag != C.INDEX_UNSET) {
                    flagsBuilder.add(eventFlag)
                }
                needsIterationFinishedEvent = true
                event.invoke(listener)
            }
        }

        fun iterationFinished(event: IterationFinishedEvent<T>) {
            if (!released && needsIterationFinishedEvent) {
                // Reset flags before invoking the listener to ensure we keep all new flags that are set by
                // recursive events triggered from this callback.
                val flagsToNotify = flagsBuilder.build()
                flagsBuilder = FlagSet.Builder()
                needsIterationFinishedEvent = false
                event.invoke(listener, flagsToNotify)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            return if (other == null || javaClass != other.javaClass) {
                false
            } else listener == (other as ListenerHolder<*>).listener
        }

        override fun hashCode(): Int {
            return listener.hashCode()
        }
    }

    companion object {
        private const val MSG_ITERATION_FINISHED = 0
    }
}