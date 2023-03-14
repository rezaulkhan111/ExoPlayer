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

import java.util.*

/** Configurable loader for native libraries.  */
abstract class LibraryLoader constructor(vararg libraries: String) {
    private var nativeLibraries: Array<String>
    private var loadAttempted: Boolean = false// Log a warning as an attempt to check for the library indicates that the app depends on an
    // extension and generally would expect its native libraries to be available.
    /** Returns whether the underlying libraries are available, loading them if necessary.  */
    @get:Synchronized
    var isAvailable: Boolean = false
        get() {
            if (loadAttempted) {
                return field
            }
            loadAttempted = true
            try {
                for (lib: String? in nativeLibraries) {
                    loadLibrary(lib)
                }
                field = true
            } catch (exception: UnsatisfiedLinkError) {
                // Log a warning as an attempt to check for the library indicates that the app depends on an
                // extension and generally would expect its native libraries to be available.
                Log.w(TAG, "Failed to load " + Arrays.toString(nativeLibraries))
            }
            return field
        }
        private set

    /**
     * @param libraries The names of the libraries to load.
     */
    init {
        nativeLibraries = libraries
    }

    /**
     * Overrides the names of the libraries to load. Must be called before any call to [ ][.isAvailable].
     */
    @Synchronized
    fun setLibraries(vararg libraries: String) {
        Assertions.checkState(!loadAttempted, "Cannot set libraries after loading")
        nativeLibraries = libraries
    }

    /**
     * Should be implemented to call `System.loadLibrary(name)`.
     *
     *
     * It's necessary for each subclass to implement this method because [ ][System.loadLibrary] uses reflection to obtain the calling class, which is then used to
     * obtain the class loader to use when loading the native library. If this class were to implement
     * the method directly, and if a subclass were to have a different class loader, then loading of
     * the native library would fail.
     *
     * @param name The name of the library to load.
     */
    protected abstract fun loadLibrary(name: String?)

    companion object {
        private val TAG: String = "LibraryLoader"
    }
}