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

import android.os.Looperimport

android.text.TextUtilsimport com.google.android.exoplayer2.ExoPlayerLibraryInfoimport org.checkerframework.checker.nullness.qual.EnsuresNonNullimport org.checkerframework.dataflow.qual.Pure
/** Provides methods for asserting the truth of expressions and properties.  */
object Assertions {
    /**
     * Throws [IllegalArgumentException] if `expression` evaluates to false.
     *
     * @param expression The expression to evaluate.
     * @throws IllegalArgumentException If `expression` is false.
     */
    @Pure
    fun checkArgument(expression: Boolean) {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && !expression) {
            throw IllegalArgumentException()
        }
    }

    /**
     * Throws [IllegalArgumentException] if `expression` evaluates to false.
     *
     * @param expression The expression to evaluate.
     * @param errorMessage The exception message if an exception is thrown. The message is converted
     * to a [String] using [String.valueOf].
     * @throws IllegalArgumentException If `expression` is false.
     */
    @Pure
    fun checkArgument(expression: Boolean, errorMessage: Any) {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && !expression) {
            throw IllegalArgumentException(errorMessage.toString())
        }
    }

    /**
     * Throws [IndexOutOfBoundsException] if `index` falls outside the specified bounds.
     *
     * @param index The index to test.
     * @param start The start of the allowed range (inclusive).
     * @param limit The end of the allowed range (exclusive).
     * @return The `index` that was validated.
     * @throws IndexOutOfBoundsException If `index` falls outside the specified bounds.
     */
    @Pure
    fun checkIndex(index: Int, start: Int, limit: Int): Int {
        if (index < start || index >= limit) {
            throw IndexOutOfBoundsException()
        }
        return index
    }

    /**
     * Throws [IllegalStateException] if `expression` evaluates to false.
     *
     * @param expression The expression to evaluate.
     * @throws IllegalStateException If `expression` is false.
     */
    @Pure
    fun checkState(expression: Boolean) {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && !expression) {
            throw IllegalStateException()
        }
    }

    /**
     * Throws [IllegalStateException] if `expression` evaluates to false.
     *
     * @param expression The expression to evaluate.
     * @param errorMessage The exception message if an exception is thrown. The message is converted
     * to a [String] using [String.valueOf].
     * @throws IllegalStateException If `expression` is false.
     */
    @Pure
    fun checkState(expression: Boolean, errorMessage: Any) {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && !expression) {
            throw IllegalStateException(errorMessage.toString())
        }
    }

    /**
     * Throws [IllegalStateException] if `reference` is null.
     *
     * @param <T> The type of the reference.
     * @param reference The reference.
     * @return The non-null reference that was validated.
     * @throws IllegalStateException If `reference` is null.
    </T> */
    @EnsuresNonNull("#1")
    @Pure
    fun <T> checkStateNotNull(reference: T?): T? {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && reference == null) {
            throw IllegalStateException()
        }
        return reference
    }

    /**
     * Throws [IllegalStateException] if `reference` is null.
     *
     * @param <T> The type of the reference.
     * @param reference The reference.
     * @param errorMessage The exception message to use if the check fails. The message is converted
     * to a string using [String.valueOf].
     * @return The non-null reference that was validated.
     * @throws IllegalStateException If `reference` is null.
    </T> */
    @EnsuresNonNull("#1")
    @Pure
    fun <T> checkStateNotNull(reference: T?, errorMessage: Any): T? {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && reference == null) {
            throw IllegalStateException(errorMessage.toString())
        }
        return reference
    }

    /**
     * Throws [NullPointerException] if `reference` is null.
     *
     * @param <T> The type of the reference.
     * @param reference The reference.
     * @return The non-null reference that was validated.
     * @throws NullPointerException If `reference` is null.
    </T> */
    @EnsuresNonNull("#1")
    @Pure
    fun <T> checkNotNull(reference: T?): T? {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && reference == null) {
            throw NullPointerException()
        }
        return reference
    }

    /**
     * Throws [NullPointerException] if `reference` is null.
     *
     * @param <T> The type of the reference.
     * @param reference The reference.
     * @param errorMessage The exception message to use if the check fails. The message is converted
     * to a string using [String.valueOf].
     * @return The non-null reference that was validated.
     * @throws NullPointerException If `reference` is null.
    </T> */
    @EnsuresNonNull("#1")
    @Pure
    fun <T> checkNotNull(reference: T?, errorMessage: Any): T? {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && reference == null) {
            throw NullPointerException(errorMessage.toString())
        }
        return reference
    }

    /**
     * Throws [IllegalArgumentException] if `string` is null or zero length.
     *
     * @param string The string to check.
     * @return The non-null, non-empty string that was validated.
     * @throws IllegalArgumentException If `string` is null or 0-length.
     */
    @EnsuresNonNull("#1")
    @Pure
    fun checkNotEmpty(string: String?): String? {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && TextUtils.isEmpty(string)) {
            throw IllegalArgumentException()
        }
        return string
    }

    /**
     * Throws [IllegalArgumentException] if `string` is null or zero length.
     *
     * @param string The string to check.
     * @param errorMessage The exception message to use if the check fails. The message is converted
     * to a string using [String.valueOf].
     * @return The non-null, non-empty string that was validated.
     * @throws IllegalArgumentException If `string` is null or 0-length.
     */
    @EnsuresNonNull("#1")
    @Pure
    fun checkNotEmpty(string: String?, errorMessage: Any): String? {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && TextUtils.isEmpty(string)) {
            throw IllegalArgumentException(errorMessage.toString())
        }
        return string
    }

    /**
     * Throws [IllegalStateException] if the calling thread is not the application's main
     * thread.
     *
     * @throws IllegalStateException If the calling thread is not the application's main thread.
     */
    @Pure
    fun checkMainThread() {
        if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException("Not in applications main thread")
        }
    }
}