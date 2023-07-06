/*
 * Copyright (C) 2020 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.util

import androidx.annotation.*
import java.util.*

/**
 * An unordered collection of elements that allows duplicates, but also allows access to a set of
 * unique elements.
 *
 *
 * This class is thread-safe using the same method as [ ]. Mutation methods cause the underlying data to be
 * copied. [.elementSet] and [.iterator] return snapshots that are unaffected by
 * subsequent mutations.
 *
 *
 * Iterating directly on this class reveals duplicate elements. Unique elements can be accessed
 * via [.elementSet]. Iteration order for both of these is not defined.
 *
 * @param <E> The type of element being stored.
</E> */
// Intentionally extending @NonNull-by-default Object to disallow @Nullable E types.
class CopyOnWriteMultiset<E : Any?> : Iterable<E> {

    private var lock: Any

    @GuardedBy("lock")
    private var elementCounts: MutableMap<E, Int>

    @GuardedBy("lock")
    private var elementSet: Set<E>

    @GuardedBy("lock")
    private var elements: MutableList<E>

    constructor() {
        lock = Any()
        elementCounts = HashMap()
        elementSet = emptySet()
        elements = emptyList<E>().toMutableList()
    }

    /**
     * Adds `element` to the multiset.
     *
     * @param element The element to be added.
     */
    fun add(element: E) {
        synchronized(lock) {
            val elements: MutableList<E> = ArrayList(elements)
            elements.add(element)
            this.elements = Collections.unmodifiableList(elements)
            val count: Int? = elementCounts[element]
            if (count == null) {
                val elementSet: MutableSet<E> = HashSet(elementSet)
                elementSet.add(element)
                this.elementSet = Collections.unmodifiableSet(elementSet)
            }
            elementCounts.put(element, if (count != null) count + 1 else 1)
        }
    }

    /**
     * Removes `element` from the multiset.
     *
     * @param element The element to be removed.
     */
    fun remove(element: E) {
        synchronized(lock) {
            val count: Int? = elementCounts[element]
            if (count == null) {
                return
            }
            val elements: MutableList<E> = ArrayList(elements)
            elements.remove(element)
            this.elements = Collections.unmodifiableList(elements)
            if (count == 1) {
                elementCounts.remove(element)
                val elementSet: MutableSet<E> = HashSet(elementSet)
                elementSet.remove(element)
                this.elementSet = Collections.unmodifiableSet(elementSet)
            } else {
                elementCounts.put(element, count - 1)
            }
        }
    }

    /**
     * Returns a snapshot of the unique elements currently in this multiset.
     *
     *
     * Changes to the underlying multiset are not reflected in the returned value.
     *
     * @return An unmodifiable set containing the unique elements in this multiset.
     */
    fun elementSet(): Set<E> {
        synchronized(lock) { return elementSet }
    }

    /**
     * Returns an iterator over a snapshot of all the elements currently in this multiset (including
     * duplicates).
     *
     *
     * Changes to the underlying multiset are not reflected in the returned value.
     *
     * @return An unmodifiable iterator over all the elements in this multiset (including duplicates).
     */
    override fun iterator(): MutableIterator<E> {
        synchronized(lock) { return elements.iterator() }
    }

    /** Returns the number of occurrences of an element in this multiset.  */
    fun count(element: E): Int {
        synchronized(lock) { return if (elementCounts.containsKey(element)) (elementCounts.get(element))!! else 0 }
    }
}