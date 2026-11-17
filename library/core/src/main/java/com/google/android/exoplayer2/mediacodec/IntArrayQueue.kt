/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.mediacodec

import java.util.NoSuchElementException

/**
 * Array-based unbounded queue for int primitives with amortized O(1) add and remove.
 *
 * <p>Use this class instead of a [java.util.Deque] to avoid boxing int primitives to [Integer] instances.
 */
internal class IntArrayQueue {

  private var headIndex = 0
  private var tailIndex = -1
  private var size = 0
  private var data = IntArray(DEFAULT_INITIAL_CAPACITY)
  private var wrapAroundMask = data.size - 1

  /** Add a new item to the queue. */
  fun add(value: Int) {
    if (size == data.size) {
      doubleArraySize()
    }

    tailIndex = (tailIndex + 1) and wrapAroundMask
    data[tailIndex] = value
    size++
  }

  /**
   * Remove an item from the queue.
   *
   * @throws NoSuchElementException if the queue is empty.
   */
  fun remove(): Int {
    if (size == 0) {
      throw NoSuchElementException()
    }

    val value = data[headIndex]
    headIndex = (headIndex + 1) and wrapAroundMask
    size--

    return value
  }

  /** Returns the number of items in the queue. */
  fun size(): Int {
    return size
  }

  /** Returns whether the queue is empty. */
  val isEmpty: Boolean
    get() = size == 0

  /** Returns whether the queue is empty. */
  fun isEmpty(): Boolean {
    return size == 0
  }

  /** Clears the queue. */
  fun clear() {
    headIndex = 0
    tailIndex = -1
    size = 0
  }

  /** Returns the length of the backing array. */
  fun capacity(): Int {
    return data.size
  }

  private void doubleArraySize() {
    val newCapacity = data.size shl 1
    if (newCapacity < 0) {
      throw IllegalStateException()
    }

    val newData = IntArray(newCapacity)
    val itemsToRight = data.size - headIndex
    val itemsToLeft = headIndex
    System.arraycopy(data, headIndex, newData, 0, itemsToRight)
    System.arraycopy(data, 0, newData, itemsToRight, itemsToLeft)

    headIndex = 0
    tailIndex = size - 1
    data = newData
    wrapAroundMask = data.size - 1
  }

  companion object {
    /** Default capacity needs to be a power of 2. */
    private const val DEFAULT_INITIAL_CAPACITY = 16
  }
}
