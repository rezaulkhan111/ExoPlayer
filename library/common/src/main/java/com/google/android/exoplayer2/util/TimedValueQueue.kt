/*
 * Copyright (C) 2018 The Android Open Source Project
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

import java.util.*
import kotlin.LongArray

/** A utility class to keep a queue of values with timestamps. This class is thread safe.  */
class TimedValueQueue<V> @JvmOverloads constructor(initialBufferSize: Int = INITIAL_BUFFER_SIZE) {
    // Looping buffer for timestamps and values
    private var timestamps: LongArray
    private var values: Array<V?>
    private var first: Int = 0
    private var size: Int = 0

    /** Creates a TimedValueBuffer with the given initial buffer size.  */
    init {
        timestamps = LongArray(initialBufferSize)
        values = newArray<V?>(initialBufferSize)
    }

    /**
     * Associates the specified value with the specified timestamp. All new values should have a
     * greater timestamp than the previously added values. Otherwise all values are removed before
     * adding the new one.
     */
    @Synchronized
    fun add(timestamp: Long, value: V) {
        clearBufferOnTimeDiscontinuity(timestamp)
        doubleCapacityIfFull()
        addUnchecked(timestamp, value)
    }

    /** Removes all of the values.  */
    @Synchronized
    fun clear() {
        first = 0
        size = 0
        Arrays.fill(values, null)
    }

    /** Returns number of the values buffered.  */
    @Synchronized
    fun size(): Int {
        return size
    }

    /** Removes and returns the first value in the queue, or null if the queue is empty.  */
    @Synchronized
    fun pollFirst(): V? {
        return if (size == 0) null else popFirst()
    }

    /**
     * Returns the value with the greatest timestamp which is less than or equal to the given
     * timestamp. Removes all older values and the returned one from the buffer.
     *
     * @param timestamp The timestamp value.
     * @return The value with the greatest timestamp which is less than or equal to the given
     * timestamp or null if there is no such value.
     * @see .poll
     */
    @Synchronized
    fun pollFloor(timestamp: Long): V? {
        return poll(timestamp,  /* onlyOlder= */true)
    }

    /**
     * Returns the value with the closest timestamp to the given timestamp. Removes all older values
     * including the returned one from the buffer.
     *
     * @param timestamp The timestamp value.
     * @return The value with the closest timestamp or null if the buffer is empty.
     * @see .pollFloor
     */
    @Synchronized
    fun poll(timestamp: Long): V? {
        return poll(timestamp,  /* onlyOlder= */false)
    }

    /**
     * Returns the value with the closest timestamp to the given timestamp. Removes all older values
     * including the returned one from the buffer.
     *
     * @param timestamp The timestamp value.
     * @param onlyOlder Whether this method can return a new value in case its timestamp value is
     * closest to `timestamp`.
     * @return The value with the closest timestamp or null if the buffer is empty or there is no
     * older value and `onlyOlder` is true.
     */
    private fun poll(timestamp: Long, onlyOlder: Boolean): V? {
        var value: V? = null
        var previousTimeDiff: Long = Long.MAX_VALUE
        while (size > 0) {
            val timeDiff: Long = timestamp - timestamps.get(first)
            if (timeDiff < 0 && (onlyOlder || -timeDiff >= previousTimeDiff)) {
                break
            }
            previousTimeDiff = timeDiff
            value = popFirst()
        }
        return value
    }

    private fun popFirst(): V? {
        Assertions.checkState(size > 0)
        val value: V? = values.get(first)
        values.get(first) = null
        first = (first + 1) % values.size
        size--
        return value
    }

    private fun clearBufferOnTimeDiscontinuity(timestamp: Long) {
        if (size > 0) {
            val last: Int = (first + size - 1) % values.size
            if (timestamp <= timestamps.get(last)) {
                clear()
            }
        }
    }

    private fun doubleCapacityIfFull() {
        val capacity: Int = values.size
        if (size < capacity) {
            return
        }
        val newCapacity: Int = capacity * 2
        val newTimestamps: LongArray = LongArray(newCapacity)
        val newValues: Array<V?> = newArray<V?>(newCapacity)
        // Reset the loop starting index to 0 while coping to the new buffer.
        // First copy the values from 'first' index to the end of original array.
        val length: Int = capacity - first
        System.arraycopy(timestamps, first, newTimestamps, 0, length)
        System.arraycopy(values, first, newValues, 0, length)
        // Then the values from index 0 to 'first' index.
        if (first > 0) {
            System.arraycopy(timestamps, 0, newTimestamps, length, first)
            System.arraycopy(values, 0, newValues, length, first)
        }
        timestamps = newTimestamps
        values = newValues
        first = 0
    }

    private fun addUnchecked(timestamp: Long, value: V) {
        val next: Int = (first + size) % values.size
        timestamps.get(next) = timestamp
        values.get(next) = value
        size++
    }

    companion object {
        private val INITIAL_BUFFER_SIZE: Int = 10
        private fun <V> newArray(length: Int): Array<V?> {
            return arrayOfNulls<Any>(length) as Array<V?>
        }
    }
}