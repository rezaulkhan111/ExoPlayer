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
package com.google.android.exoplayer2.source

import com.google.android.exoplayer2.C
import java.util.*

/**
 * Shuffled order of indices.
 *
 *
 * The shuffle order must be immutable to ensure thread safety.
 *
 *
 * The order must be consistent when traversed both [forwards][.getNextIndex] and
 * [backwards][.getPreviousIndex].
 */
interface ShuffleOrder {
    /** The default [ShuffleOrder] implementation for random shuffle order.  */
    class DefaultShuffleOrder : ShuffleOrder {

        private var random: Random? = null
        private val shuffled: IntArray
        private val indexInShuffled: IntArray

        /**
         * Creates an instance with a specified length.
         *
         * @param length The length of the shuffle order.
         */
        constructor(length: Int) : this(length, Random()) {}

        /**
         * Creates an instance with a specified length and the specified random seed. Shuffle orders of
         * the same length initialized with the same random seed are guaranteed to be equal.
         *
         * @param length The length of the shuffle order.
         * @param randomSeed A random seed.
         */
        constructor(length: Int, randomSeed: Long) : this(length, Random(randomSeed)) {}

        /**
         * Creates an instance with a specified shuffle order and the specified random seed. The random
         * seed is used for [.cloneAndInsert] invocations.
         *
         * @param shuffledIndices The shuffled indices to use as order.
         * @param randomSeed A random seed.
         */
        constructor(shuffledIndices: IntArray, randomSeed: Long) : this(Arrays.copyOf(shuffledIndices, shuffledIndices.size), Random(randomSeed)) {}

        private constructor(length: Int, random: Random) : this(createShuffledList(length, random), random) {}

        private constructor(shuffled: IntArray, random: Random) {
            this.shuffled = shuffled
            this.random = random
            this.indexInShuffled = IntArray(shuffled.size)
            for (i in shuffled.indices) {
                indexInShuffled[shuffled[i]] = i
            }
        }

        override fun getLength(): Int {
            return shuffled.size
        }

        override fun getNextIndex(index: Int): Int {
            var shuffledIndex = indexInShuffled[index]
            return if (++shuffledIndex < shuffled.size) shuffled[shuffledIndex] else C.INDEX_UNSET
        }

        override fun getPreviousIndex(index: Int): Int {
            var shuffledIndex = indexInShuffled[index]
            return if (--shuffledIndex >= 0) shuffled[shuffledIndex] else C.INDEX_UNSET
        }

        override fun getLastIndex(): Int {
            return if (shuffled.size > 0) shuffled[shuffled.size - 1] else C.INDEX_UNSET
        }

        override fun getFirstIndex(): Int {
            return if (shuffled.size > 0) shuffled[0] else C.INDEX_UNSET
        }

        override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
            val insertionPoints = IntArray(insertionCount)
            val insertionValues = IntArray(insertionCount)
            for (i in 0 until insertionCount) {
                insertionPoints[i] = random!!.nextInt(shuffled.size + 1)
                val swapIndex = random!!.nextInt(i + 1)
                insertionValues[i] = insertionValues[swapIndex]
                insertionValues[swapIndex] = i + insertionIndex
            }
            Arrays.sort(insertionPoints)
            val newShuffled = IntArray(shuffled.size + insertionCount)
            var indexInOldShuffled = 0
            var indexInInsertionList = 0
            for (i in 0 until shuffled.size + insertionCount) {
                if (indexInInsertionList < insertionCount
                        && indexInOldShuffled == insertionPoints[indexInInsertionList]) {
                    newShuffled[i] = insertionValues[indexInInsertionList++]
                } else {
                    newShuffled[i] = shuffled[indexInOldShuffled++]
                    if (newShuffled[i] >= insertionIndex) {
                        newShuffled[i] += insertionCount
                    }
                }
            }
            return DefaultShuffleOrder(newShuffled, Random(random!!.nextLong()))
        }

        override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
            val numberOfElementsToRemove = indexToExclusive - indexFrom
            val newShuffled = IntArray(shuffled.size - numberOfElementsToRemove)
            var foundElementsCount = 0
            for (i in shuffled.indices) {
                if (shuffled[i] >= indexFrom && shuffled[i] < indexToExclusive) {
                    foundElementsCount++
                } else {
                    newShuffled[i - foundElementsCount] = if (shuffled[i] >= indexFrom) shuffled[i] - numberOfElementsToRemove else shuffled[i]
                }
            }
            return DefaultShuffleOrder(newShuffled, Random(random!!.nextLong()))
        }

        override fun cloneAndClear(): ShuffleOrder {
            return DefaultShuffleOrder( /* length= */0, Random(random!!.nextLong()))
        }

        companion object {
            private fun createShuffledList(length: Int, random: Random): IntArray {
                val shuffled = IntArray(length)
                for (i in 0 until length) {
                    val swapIndex = random.nextInt(i + 1)
                    shuffled[i] = shuffled[swapIndex]
                    shuffled[swapIndex] = i
                }
                return shuffled
            }
        }
    }

    /** A [ShuffleOrder] implementation which does not shuffle.  */
    class UnshuffledShuffleOrder : ShuffleOrder {

        private var length = 0

        /**
         * Creates an instance with a specified length.
         *
         * @param length The length of the shuffle order.
         */
        constructor(length: Int) {
            this.length = length
        }

        override fun getLength(): Int {
            return length
        }

        override fun getNextIndex(index: Int): Int {
            var index = index
            return if (++index < length) index else C.INDEX_UNSET
        }

        override fun getPreviousIndex(index: Int): Int {
            var index = index
            return if (--index >= 0) index else C.INDEX_UNSET
        }

        override fun getLastIndex(): Int {
            return if (length > 0) length - 1 else C.INDEX_UNSET
        }

        override fun getFirstIndex(): Int {
            return if (length > 0) 0 else C.INDEX_UNSET
        }

        override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
            return UnshuffledShuffleOrder(length + insertionCount)
        }

        override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
            return UnshuffledShuffleOrder(length - indexToExclusive + indexFrom)
        }

        override fun cloneAndClear(): ShuffleOrder {
            return UnshuffledShuffleOrder( /* length= */0)
        }
    }

    /** Returns length of shuffle order.  */
    fun getLength(): Int

    /**
     * Returns the next index in the shuffle order.
     *
     * @param index An index.
     * @return The index after `index`, or [C.INDEX_UNSET] if `index` is the last
     * element.
     */
    fun getNextIndex(index: Int): Int

    /**
     * Returns the previous index in the shuffle order.
     *
     * @param index An index.
     * @return The index before `index`, or [C.INDEX_UNSET] if `index` is the first
     * element.
     */
    fun getPreviousIndex(index: Int): Int

    /**
     * Returns the last index in the shuffle order, or [C.INDEX_UNSET] if the shuffle order is
     * empty.
     */
    fun getLastIndex(): Int

    /**
     * Returns the first index in the shuffle order, or [C.INDEX_UNSET] if the shuffle order is
     * empty.
     */
    fun getFirstIndex(): Int

    /**
     * Returns a copy of the shuffle order with newly inserted elements.
     *
     * @param insertionIndex The index in the unshuffled order at which elements are inserted.
     * @param insertionCount The number of elements inserted at `insertionIndex`.
     * @return A copy of this [ShuffleOrder] with newly inserted elements.
     */
    fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder

    /**
     * Returns a copy of the shuffle order with a range of elements removed.
     *
     * @param indexFrom The starting index in the unshuffled order of the range to remove.
     * @param indexToExclusive The smallest index (must be greater or equal to `indexFrom`) that
     * will not be removed.
     * @return A copy of this [ShuffleOrder] without the elements in the removed range.
     */
    fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder

    /** Returns a copy of the shuffle order with all elements removed.  */
    fun cloneAndClear(): ShuffleOrder
}