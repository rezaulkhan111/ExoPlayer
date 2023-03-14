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
 */
package com.google.android.exoplayer2.util

import android.util.SparseBooleanArray

com.google.errorprone.annotations.CanIgnoreReturnValue
/**
 * A set of integer flags.
 *
 *
 * Intended for usages where the number of flags may exceed 32 and can no longer be represented
 * by an IntDef.
 *
 *
 * Instances are immutable.
 */
class FlagSet private constructor(  // A SparseBooleanArray is used instead of a Set to avoid auto-boxing the flag values.
        private val flags: SparseBooleanArray) {
    /** A builder for [FlagSet] instances.  */
    class Builder constructor() {
        private val flags: SparseBooleanArray
        private var buildCalled: Boolean = false

        /** Creates a builder.  */
        init {
            flags = SparseBooleanArray()
        }

        /**
         * Adds a flag.
         *
         * @param flag A flag.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun add(flag: Int): Builder {
            Assertions.checkState(!buildCalled)
            flags.append(flag,  /* value= */true)
            return this
        }

        /**
         * Adds a flag if the provided condition is true. Does nothing otherwise.
         *
         * @param flag A flag.
         * @param condition A condition.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun addIf(flag: Int, condition: Boolean): Builder {
            if (condition) {
                return add(flag)
            }
            return this
        }

        /**
         * Adds flags.
         *
         * @param flags The flags to add.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun addAll(vararg flags: Int): Builder {
            for (flag: Int in flags) {
                add(flag)
            }
            return this
        }

        /**
         * Adds [flags][FlagSet].
         *
         * @param flags The set of flags to add.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun addAll(flags: FlagSet?): Builder {
            for (i in 0 until flags!!.size()) {
                add(flags.get(i))
            }
            return this
        }

        /**
         * Removes a flag.
         *
         * @param flag A flag.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun remove(flag: Int): Builder {
            Assertions.checkState(!buildCalled)
            flags.delete(flag)
            return this
        }

        /**
         * Removes a flag if the provided condition is true. Does nothing otherwise.
         *
         * @param flag A flag.
         * @param condition A condition.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun removeIf(flag: Int, condition: Boolean): Builder {
            if (condition) {
                return remove(flag)
            }
            return this
        }

        /**
         * Removes flags.
         *
         * @param flags The flags to remove.
         * @return This builder.
         * @throws IllegalStateException If [.build] has already been called.
         */
        @CanIgnoreReturnValue
        fun removeAll(vararg flags: Int): Builder {
            for (flag: Int in flags) {
                remove(flag)
            }
            return this
        }

        /**
         * Builds an [FlagSet] instance.
         *
         * @throws IllegalStateException If this method has already been called.
         */
        fun build(): FlagSet {
            Assertions.checkState(!buildCalled)
            buildCalled = true
            return FlagSet(flags)
        }
    }

    /**
     * Returns whether the set contains the given flag.
     *
     * @param flag The flag.
     * @return Whether the set contains the flag.
     */
    operator fun contains(flag: Int): Boolean {
        return flags.get(flag)
    }

    /**
     * Returns whether the set contains at least one of the given flags.
     *
     * @param flags The flags.
     * @return Whether the set contains at least one of the flags.
     */
    fun containsAny(vararg flags: Int): Boolean {
        for (flag: Int in flags) {
            if (contains(flag)) {
                return true
            }
        }
        return false
    }

    /** Returns the number of flags in this set.  */
    fun size(): Int {
        return flags.size()
    }

    /**
     * Returns the flag at the given index.
     *
     * @param index The index. Must be between 0 (inclusive) and [.size] (exclusive).
     * @return The flag at the given index.
     * @throws IndexOutOfBoundsException If index is outside the allowed range.
     */
    operator fun get(index: Int): Int {
        Assertions.checkIndex(index,  /* start= */0,  /* limit= */size())
        return flags.keyAt(index)
    }

    public override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (!(o is FlagSet)) {
            return false
        }
        val that: FlagSet = o
        if (Util.SDK_INT < 24) {
            // SparseBooleanArray.equals() is not implemented on API levels below 24.
            if (size() != that.size()) {
                return false
            }
            for (i in 0 until size()) {
                if (get(i) != that.get(i)) {
                    return false
                }
            }
            return true
        } else {
            return (flags == that.flags)
        }
    }

    public override fun hashCode(): Int {
        if (Util.SDK_INT < 24) {
            // SparseBooleanArray.hashCode() is not implemented on API levels below 24.
            var hashCode: Int = size()
            for (i in 0 until size()) {
                hashCode = 31 * hashCode + get(i)
            }
            return hashCode
        } else {
            return flags.hashCode()
        }
    }
}