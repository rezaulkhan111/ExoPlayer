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
package com.google.android.exoplayer2.util

/**
 * An interruptible condition variable. This class provides a number of benefits over [ ]:
 *
 *
 *  * Consistent use of ([Clock.elapsedRealtime] for timing [.block] timeout
 * intervals. [android.os.ConditionVariable] used [System.currentTimeMillis]
 * prior to Android 10, which is not a correct clock to use for interval timing because it's
 * not guaranteed to be monotonic.
 *  * Support for injecting a custom [Clock].
 *  * The ability to query the variable's current state, by calling [.isOpen].
 *  * [.open] and [.close] return whether they changed the variable's state.
 *
 */
class ConditionVariable {
    private var clock: Clock? = null
    private var isOpen: Boolean = false

    /**
     * Creates an instance, which starts closed.
     *
     * @param clock The [Clock] whose [Clock.elapsedRealtime] method is used to
     * determine when [.block] should time out.
     */
    constructor(clock: Clock?) {
        this.clock = clock
    }

    /** Creates an instance using [Clock.DEFAULT].  */
    constructor() {
        ConditionVariable(Clock.DEFAULT)
    }

    /**
     * Opens the condition and releases all threads that are blocked.
     *
     * @return True if the condition variable was opened. False if it was already open.
     */
    @Synchronized
    fun open(): Boolean {
        if (isOpen) {
            return false
        }
        isOpen = true
        notifyAll()
        return true
    }

    /**
     * Closes the condition.
     *
     * @return True if the condition variable was closed. False if it was already closed.
     */
    @Synchronized
    fun close(): Boolean {
        val wasOpen = isOpen
        isOpen = false
        return wasOpen
    }

    /**
     * Blocks until the condition is opened.
     *
     * @throws InterruptedException If the thread is interrupted.
     */
    @Synchronized
    @Throws(InterruptedException::class)
    fun block() {
        while (!isOpen) {
            wait()
        }
    }

    /**
     * Blocks until the condition is opened or until `timeoutMs` have passed.
     *
     * @param timeoutMs The maximum time to wait in milliseconds. If `timeoutMs <= 0` then the
     * call will return immediately without blocking.
     * @return True if the condition was opened, false if the call returns because of the timeout.
     * @throws InterruptedException If the thread is interrupted.
     */
    @Synchronized
    @Throws(InterruptedException::class)
    fun block(timeoutMs: Long): Boolean {
        if (timeoutMs <= 0) {
            return isOpen
        }
        var nowMs = clock!!.elapsedRealtime()
        val endMs = nowMs + timeoutMs
        if (endMs < nowMs) {
            // timeoutMs is large enough for (nowMs + timeoutMs) to rollover. Block indefinitely.
            block()
        } else {
            while (!isOpen && nowMs < endMs) {
                wait(endMs - nowMs)
                nowMs = clock!!.elapsedRealtime()
            }
        }
        return isOpen
    }

    /**
     * Blocks until the condition is open. Unlike [.block], this method will continue to block
     * if the calling thread is interrupted. If the calling thread was interrupted then its [ ][Thread.isInterrupted] will be set when the method returns.
     */
    @Synchronized
    fun blockUninterruptible() {
        var wasInterrupted = false
        while (!isOpen) {
            try {
                wait()
            } catch (e: InterruptedException) {
                wasInterrupted = true
            }
        }
        if (wasInterrupted) {
            // Restore the interrupted status.
            Thread.currentThread().interrupt()
        }
    }

    /** Returns whether the condition is opened.  */
    @Synchronized
    fun isOpen(): Boolean {
        return isOpen
    }
}