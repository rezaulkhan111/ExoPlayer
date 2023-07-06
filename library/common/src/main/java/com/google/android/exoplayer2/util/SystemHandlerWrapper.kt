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

import android.os.*
import androidx.annotation.*
import com.google.errorprone.annotations.CanIgnoreReturnValue

/** The standard implementation of [HandlerWrapper].  */ /* package */
internal class SystemHandlerWrapper : HandlerWrapper {

    private var handler: Handler? = null

    constructor(handler: Handler?) {
        this.handler = handler
    }

    override fun getLooper(): Looper {
        return handler!!.looper
    }

    override fun hasMessages(what: Int): Boolean {
        return handler!!.hasMessages(what)
    }

    override fun obtainMessage(what: Int): HandlerWrapper.Message? {
        return obtainSystemMessage().setMessage(handler!!.obtainMessage(what),  /* handler= */this)
    }

    override fun obtainMessage(what: Int, obj: Any?): HandlerWrapper.Message? {
        return obtainSystemMessage().setMessage(handler!!.obtainMessage(what, obj),  /* handler= */this)
    }

    override fun obtainMessage(what: Int, arg1: Int, arg2: Int): HandlerWrapper.Message? {
        return obtainSystemMessage().setMessage(handler!!.obtainMessage(what, arg1, arg2),  /* handler= */this)
    }

    override fun obtainMessage(what: Int, arg1: Int, arg2: Int, obj: Any?): HandlerWrapper.Message? {
        return obtainSystemMessage().setMessage(handler!!.obtainMessage(what, arg1, arg2, obj),  /* handler= */this)
    }

    override fun sendMessageAtFrontOfQueue(message: HandlerWrapper.Message?): Boolean {
        return (message as SystemMessage).sendAtFrontOfQueue(handler!!)
    }

    override fun sendEmptyMessage(what: Int): Boolean {
        return handler!!.sendEmptyMessage(what)
    }

    override fun sendEmptyMessageDelayed(what: Int, delayMs: Int): Boolean {
        return handler!!.sendEmptyMessageDelayed(what, delayMs.toLong())
    }

    override fun sendEmptyMessageAtTime(what: Int, uptimeMs: Long): Boolean {
        return handler!!.sendEmptyMessageAtTime(what, uptimeMs)
    }

    override fun removeMessages(what: Int) {
        handler!!.removeMessages(what)
    }

    override fun removeCallbacksAndMessages(token: Any?) {
        handler!!.removeCallbacksAndMessages(token)
    }

    override fun post(runnable: Runnable?): Boolean {
        return handler!!.post(runnable!!)
    }

    override fun postDelayed(runnable: Runnable?, delayMs: Long): Boolean {
        return handler!!.postDelayed(runnable!!, delayMs)
    }

    override fun postAtFrontOfQueue(runnable: Runnable?): Boolean {
        return handler!!.postAtFrontOfQueue(runnable!!)
    }

    private class SystemMessage : HandlerWrapper.Message {

        private var message: Message? = null
        private var handler: SystemHandlerWrapper? = null

        @CanIgnoreReturnValue
        fun setMessage(message: Message?, handler: SystemHandlerWrapper?): SystemMessage? {
            this.message = message
            this.handler = handler
            return this
        }

        fun sendAtFrontOfQueue(handler: Handler): Boolean {
            val success = handler.sendMessageAtFrontOfQueue(checkNotNull(message))
            recycle()
            return success
        }

        override fun sendToTarget() {
            checkNotNull(message).sendToTarget()
            recycle()
        }

        override fun getTarget(): HandlerWrapper? {
            return checkNotNull(handler)
        }

        private fun recycle() {
            message = null
            handler = null
            recycleMessage(this)
        }
    }

    companion object {
        private const val MAX_POOL_SIZE: Int = 50

        @GuardedBy("messagePool")
        private val messagePool: MutableList<SystemMessage> = ArrayList(MAX_POOL_SIZE)
        private fun obtainSystemMessage(): SystemMessage {
            synchronized(messagePool) { return if (messagePool.isEmpty()) SystemMessage() else messagePool.removeAt(messagePool.size - 1) }
        }

        private fun recycleMessage(message: SystemMessage) {
            synchronized(messagePool) {
                if (messagePool.size < MAX_POOL_SIZE) {
                    messagePool.add(message)
                }
            }
        }
    }
}