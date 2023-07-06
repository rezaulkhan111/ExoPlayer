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
package com.google.android.exoplayer2.util

import android.os.Looper

/**
 * An interface to call through to a [Handler]. Instances must be created by calling [ ][Clock.createHandler] on [Clock.DEFAULT] for all non-test cases.
 */
interface HandlerWrapper {
    /** A message obtained from the handler.  */
    interface Message {
        /** See [android.os.Message.sendToTarget].  */
        fun sendToTarget()

        /** See [android.os.Message.getTarget].  */
        fun getTarget(): HandlerWrapper?
    }

    /** See [Handler.getLooper].  */
    fun getLooper(): Looper?

    /** See [Handler.hasMessages].  */
    fun hasMessages(what: Int): Boolean

    /** See [Handler.obtainMessage].  */
    fun obtainMessage(what: Int): Message?

    /** See [Handler.obtainMessage].  */
    fun obtainMessage(what: Int, obj: Any?): Message?

    /** See [Handler.obtainMessage].  */
    fun obtainMessage(what: Int, arg1: Int, arg2: Int): Message?

    /** See [Handler.obtainMessage].  */
    fun obtainMessage(what: Int, arg1: Int, arg2: Int, obj: Any?): Message?

    /** See [Handler.sendMessageAtFrontOfQueue].  */
    fun sendMessageAtFrontOfQueue(message: Message?): Boolean

    /** See [Handler.sendEmptyMessage].  */
    fun sendEmptyMessage(what: Int): Boolean

    /** See [Handler.sendEmptyMessageDelayed].  */
    fun sendEmptyMessageDelayed(what: Int, delayMs: Int): Boolean

    /** See [Handler.sendEmptyMessageAtTime].  */
    fun sendEmptyMessageAtTime(what: Int, uptimeMs: Long): Boolean

    /** See [Handler.removeMessages].  */
    fun removeMessages(what: Int)

    /** See [Handler.removeCallbacksAndMessages].  */
    fun removeCallbacksAndMessages(token: Any?)

    /** See [Handler.post].  */
    fun post(runnable: Runnable?): Boolean

    /** See [Handler.postDelayed].  */
    fun postDelayed(runnable: Runnable?, delayMs: Long): Boolean

    /** See [android.os.Handler.postAtFrontOfQueue].  */
    fun postAtFrontOfQueue(runnable: Runnable?): Boolean
}