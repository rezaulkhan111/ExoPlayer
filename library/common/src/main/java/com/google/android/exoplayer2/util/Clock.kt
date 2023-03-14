/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.os.*

/**
 * An interface through which system clocks can be read and [HandlerWrapper]s created. The
 * [.DEFAULT] implementation must be used for all non-test cases.
 */
interface Clock {
    /**
     * Returns the current time in milliseconds since the Unix Epoch.
     *
     * @see System.currentTimeMillis
     */
    fun currentTimeMillis(): Long

    /**
     * @see android.os.SystemClock.elapsedRealtime
     */
    fun elapsedRealtime(): Long

    /**
     * @see android.os.SystemClock.uptimeMillis
     */
    fun uptimeMillis(): Long

    /**
     * Creates a [HandlerWrapper] using a specified looper and a specified callback for handling
     * messages.
     *
     * @see Handler.Handler
     */
    fun createHandler(looper: Looper?, callback: Handler.Callback?): HandlerWrapper

    /**
     * Notifies the clock that the current thread is about to be blocked and won't return until a
     * condition on another thread becomes true.
     *
     *
     * Should be a no-op for all non-test cases.
     */
    fun onThreadBlocked()

    companion object {
        /** Default [Clock] to use for all non-test cases.  */
        val DEFAULT: Clock = SystemClock()
    }
}