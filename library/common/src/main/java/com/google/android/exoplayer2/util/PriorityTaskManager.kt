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

import com.google.android.exoplayer2.util.Util.castNonNull
import java.io.IOException
import java.util.*
import kotlin.math.max

/**
 * Allows tasks with associated priorities to control how they proceed relative to one another.
 *
 *
 * A task should call [.add] to register with the manager and [.remove] to
 * unregister. A registered task will prevent tasks of lower priority from proceeding, and should
 * call [.proceed], [.proceedNonBlocking] or [.proceedOrThrow] each
 * time it wishes to check whether it is itself allowed to proceed.
 */
class PriorityTaskManager {
    /** Thrown when task attempts to proceed when another registered task has a higher priority.  */
    class PriorityTooLowException(priority: Int, highestPriority: Int) : IOException("Priority too low [priority=$priority, highest=$highestPriority]")

    private val lock = Any()

    // Guarded by lock.
    private var queue: PriorityQueue<Int>? = null
    private var highestPriority = 0

    constructor() {
        queue = PriorityQueue(10, Collections.reverseOrder())
        highestPriority = Int.MIN_VALUE
    }

    /**
     * Register a new task. The task must call [.remove] when done.
     *
     * @param priority The priority of the task. Larger values indicate higher priorities.
     */
    fun add(priority: Int) {
        synchronized(lock) {
            queue!!.add(priority)
            highestPriority = max(highestPriority, priority)
        }
    }

    /**
     * Blocks until the task is allowed to proceed.
     *
     * @param priority The priority of the task.
     * @throws InterruptedException If the thread is interrupted.
     */
    @Throws(InterruptedException::class)
    fun proceed(priority: Int) {
        synchronized(lock) {
            while (highestPriority != priority) {
                lock.wait()
            }
        }
    }

    /**
     * A non-blocking variant of [.proceed].
     *
     * @param priority The priority of the task.
     * @return Whether the task is allowed to proceed.
     */
    fun proceedNonBlocking(priority: Int): Boolean {
        synchronized(lock) { return highestPriority == priority }
    }

    /**
     * A throwing variant of [.proceed].
     *
     * @param priority The priority of the task.
     * @throws PriorityTooLowException If the task is not allowed to proceed.
     */
    @Throws(PriorityTooLowException::class)
    fun proceedOrThrow(priority: Int) {
        synchronized(lock) {
            if (highestPriority != priority) {
                throw PriorityTooLowException(priority, highestPriority)
            }
        }
    }

    /**
     * Unregister a task.
     *
     * @param priority The priority of the task.
     */
    fun remove(priority: Int) {
        synchronized(lock) {
            queue!!.remove(priority)
            highestPriority = if (queue!!.isEmpty()) Int.MIN_VALUE else castNonNull(queue!!.peek())!!
            lock.notifyAll()
        }
    }
}