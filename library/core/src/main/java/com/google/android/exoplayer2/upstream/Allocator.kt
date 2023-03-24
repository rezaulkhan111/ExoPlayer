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
package com.google.android.exoplayer2.upstream

/** A source of allocations.  */
interface Allocator {
    /** A node in a chain of [Allocations][Allocation].  */
    interface AllocationNode {
        /** Returns the [Allocation] associated to this chain node.  */
        fun getAllocation(): Allocation?

        /** Returns the next chain node, or `null` if this is the last node in the chain.  */
        operator fun next(): AllocationNode?
    }

    /**
     * Obtain an [Allocation].
     *
     *
     * When the caller has finished with the [Allocation], it should be returned by calling
     * [.release].
     *
     * @return The [Allocation].
     */
    fun allocate(): Allocation?

    /**
     * Releases an [Allocation] back to the allocator.
     *
     * @param allocation The [Allocation] being released.
     */
    fun release(allocation: Allocation?)

    /**
     * Releases all [Allocations][Allocation] in the chain starting at the given [ ].
     *
     *
     * Implementations must not make memory allocations.
     */
    fun release(allocationNode: AllocationNode?)

    /**
     * Hints to the allocator that it should make a best effort to release any excess [ ].
     */
    fun trim()

    /** Returns the total number of bytes currently allocated.  */
    fun getTotalBytesAllocated(): Int

    /** Returns the length of each individual [Allocation].  */
    fun getIndividualAllocationLength(): Int
}