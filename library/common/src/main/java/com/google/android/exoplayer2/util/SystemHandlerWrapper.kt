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

import androidx.annotation.*

android.os.*import androidx.annotation.*
import com.google.errorprone.annotations.CanIgnoreReturnValueimport

java.util.ArrayList
/** The standard implementation of [HandlerWrapper].  */ /* package */
internal class SystemHandlerWrapper constructor(private val handler: Handler) : HandlerWrapper {
    override val looper: Looper
        get() {
            return handler.getLooper()
        }

    public override fun hasMessages(what: Int): Boolean {
        return handler.hasMessages(what)
    }

    public override fun obtainMessage(what: Int): HandlerWrapper.Message {
        return obtainSystemMessage().setMessage(handler.obtainMessage(what),  /* handler= */this)
    }

    public override fun obtainMessage(what: Int, obj: Any?): HandlerWrapper.Message {
        return obtainSystemMessage().setMessage(handler.obtainMessage(what, obj),  /* handler= */this)
    }

    public override fun obtainMessage(what: Int, arg1: Int, arg2: Int): HandlerWrapper.Message {
        return obtainSystemMessage()
                .setMessage(handler.obtainMessage(what, arg1, arg2),  /* handler= */this)
    }

    public override fun obtainMessage(what: Int, arg1: Int, arg2: Int, obj: Any?): HandlerWrapper.Message {
        return obtainSystemMessage()
                .setMessage(handler.obtainMessage(what, arg1, arg2, obj),  /* handler= */this)
    }

    public override fun sendMessageAtFrontOfQueue(message: HandlerWrapper.Message?): Boolean {
        return (message as SystemMessage?)!!.sendAtFrontOfQueue(handler)
    }

    public override fun sendEmptyMessage(what: Int): Boolean {
        return handler.sendEmptyMessage(what)
    }

    public override fun sendEmptyMessageDelayed(what: Int, delayMs: Int): Boolean {
        return handler.sendEmptyMessageDelayed(what, delayMs.toLong())
    }

    public override fun sendEmptyMessageAtTime(what: Int, uptimeMs: Long): Boolean {
        return handler.sendEmptyMessageAtTime(what, uptimeMs)
    }

    public override fun removeMessages(what: Int) {
        handler.removeMessages(what)
    }

    public override fun removeCallbacksAndMessages(token: Any?) {
        handler.removeCallbacksAndMessages(token)
    }

    public override fun post(runnable: Runnable?): Boolean {
        return handler.post((runnable)!!)
    }

    public override fun postDelayed(runnable: Runnable?, delayMs: Long): Boolean {
        return handler.postDelayed((runnable)!!, delayMs)
    }

    public override fun postAtFrontOfQueue(runnable: Runnable?): Boolean {
        return handler.postAtFrontOfQueue((runnable)!!)
    }

    private class SystemMessage constructor() : HandlerWrapper.Message {
        private var message: Message? = null
        private var handler: SystemHandlerWrapper? = null
        @CanIgnoreReturnValue
        fun setMessage(message: Message?, handler: SystemHandlerWrapper?): SystemMessage {
            this.message = message
            this.handler = handler
            return this
        }

        fun sendAtFrontOfQueue(handler: Handler): Boolean {
            val success: Boolean = handler.sendMessageAtFrontOfQueue((Assertions.checkNotNull(message))!!)
            recycle()
            return success
        }

        public override fun sendToTarget() {
            Assertions.checkNotNull(message)!!.sendToTarget()
            recycle()
        }

        override val target: HandlerWrapper?
            get() {
                return Assertions.checkNotNull(handler)
            }

        private fun recycle() {
            message = null
            handler = null
            recycleMessage(this)
        }
    }

    companion object {
        private val MAX_POOL_SIZE: Int = 50

        @GuardedBy("messagePool")
        private val messagePool: MutableList<SystemMessage> = ArrayList(MAX_POOL_SIZE)
        private fun obtainSystemMessage(): SystemMessage {
            synchronized(messagePool, { return if (messagePool.isEmpty()) SystemMessage() else messagePool.removeAt(messagePool.size - 1) })
        }

        private fun recycleMessage(message: SystemMessage) {
            synchronized(messagePool, {
                if (messagePool.size < MAX_POOL_SIZE) {
                    messagePool.add(message)
                }
            })
        }
    }
}