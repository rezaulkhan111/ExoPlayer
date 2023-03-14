/*
 * Copyright 2020 The Android Open Source Project
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

import java.util.concurrent.*

/**
 * A [RunnableFuture] that supports additional uninterruptible operations to query whether
 * execution has started and finished.
 *
 * @param <R> The type of the result.
 * @param <E> The type of any [ExecutionException] cause.
</E></R> */
abstract class RunnableFutureTask<R, E : Exception?> protected constructor() : RunnableFuture<R?> {
    private val started: ConditionVariable
    private val finished: ConditionVariable
    private val cancelLock: Any
    private var exception: Exception? = null
    private var result: R? = null
    private var workThread: Thread? = null
    private var canceled: Boolean = false

    init {
        started = ConditionVariable()
        finished = ConditionVariable()
        cancelLock = Any()
    }

    /** Blocks until the task has started, or has been canceled without having been started.  */
    fun blockUntilStarted() {
        started.blockUninterruptible()
    }

    /** Blocks until the task has finished, or has been canceled without having been started.  */
    fun blockUntilFinished() {
        finished.blockUninterruptible()
    }

    // Future implementation.
    @UnknownNull
    @Throws(ExecutionException::class, InterruptedException::class)
    public override fun get(): R? {
        finished.block()
        return getResult()
    }

    @UnknownNull
    @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
    public override fun get(timeout: Long, unit: TimeUnit): R? {
        val timeoutMs: Long = TimeUnit.MILLISECONDS.convert(timeout, unit)
        if (!finished.block(timeoutMs)) {
            throw TimeoutException()
        }
        return getResult()
    }

    public override fun cancel(interruptIfRunning: Boolean): Boolean {
        synchronized(cancelLock, {
            if (canceled || finished.isOpen()) {
                return false
            }
            canceled = true
            cancelWork()
            val workThread: Thread? = workThread
            if (workThread != null) {
                if (interruptIfRunning) {
                    workThread.interrupt()
                }
            } else {
                started.open()
                finished.open()
            }
            return true
        })
    }

    public override fun isDone(): Boolean {
        return finished.isOpen()
    }

    public override fun isCancelled(): Boolean {
        return canceled
    }

    // Runnable implementation.
    public override fun run() {
        synchronized(cancelLock, {
            if (canceled) {
                return
            }
            workThread = Thread.currentThread()
        })
        started.open()
        try {
            result = doWork()
        } catch (e: Exception) {
            // Must be an instance of E or RuntimeException.
            exception = e
        } finally {
            synchronized(cancelLock, {
                finished.open()
                workThread = null
                // Clear the interrupted flag if set, to avoid it leaking into any subsequent tasks executed
                // using the calling thread.
                Thread.interrupted()
            })
        }
    }
    // Internal methods.
    /**
     * Performs the work or computation.
     *
     * @return The computed result.
     * @throws E If an error occurred.
     */
    @UnknownNull
    @Throws(E::class)
    protected abstract fun doWork(): R

    /**
     * Cancels any work being done by [.doWork]. If [.doWork] is currently executing
     * then the thread on which it's executing may be interrupted immediately after this method
     * returns.
     *
     *
     * The default implementation does nothing.
     */
    protected open fun cancelWork() {
        // Do nothing.
    }

    // The return value is guaranteed to be non-null if and only if R is a non-null type, but there's
    // no way to assert this. Suppress the warning instead.
    @UnknownNull
    @Throws(ExecutionException::class)
    private fun getResult(): R? {
        if (canceled) {
            throw CancellationException()
        } else if (exception != null) {
            throw ExecutionException(exception)
        }
        return result
    }
}