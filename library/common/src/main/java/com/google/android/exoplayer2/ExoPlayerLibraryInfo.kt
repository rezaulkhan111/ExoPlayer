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
package com.google.android.exoplayer2


import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.TraceUtil

/** Information about the media libraries.  */
object ExoPlayerLibraryInfo {
    /** A tag to use when logging library information.  */
    val TAG: String = "ExoPlayerLib"

    /** The version of the library expressed as a string, for example "1.2.3".  */ // Intentionally hardcoded. Do not derive from other constants (e.g. VERSION_INT) or vice versa.
    val VERSION: String = "2.18.2"

    /** The version of the library expressed as `TAG + "/" + VERSION`.  */ // Intentionally hardcoded. Do not derive from other constants (e.g. VERSION) or vice versa.
    val VERSION_SLASHY: String = "ExoPlayerLib/2.18.2"

    /**
     * The version of the library expressed as an integer, for example 1002003.
     *
     *
     * Three digits are used for each component of [.VERSION]. For example "1.2.3" has the
     * corresponding integer version 1002003 (001-002-003), and "123.45.6" has the corresponding
     * integer version 123045006 (123-045-006).
     */
    // Intentionally hardcoded. Do not derive from other constants (e.g. VERSION) or vice versa.
    val VERSION_INT: Int = 2018002

    /** Whether the library was compiled with [Assertions] checks enabled.  */
    val ASSERTIONS_ENABLED: Boolean = true

    /** Whether the library was compiled with [TraceUtil] trace enabled.  */
    val TRACE_ENABLED: Boolean = true
    private val registeredModules: HashSet<String> = HashSet()
    private val registeredModulesString: String = "goog.exo.core"

    /** Returns a string consisting of registered module names separated by ", ".  */
    @Synchronized
    fun registeredModules(): String {
        return ExoPlayerLibraryInfo.registeredModulesString
    }

    /**
     * Registers a module to be returned in the [.registeredModules] string.
     *
     * @param name The name of the module being registered.
     */
    @Synchronized
    fun registerModule(name: String) {
        if (ExoPlayerLibraryInfo.registeredModules.add(name)) {
            ExoPlayerLibraryInfo.registeredModulesString = ExoPlayerLibraryInfo.registeredModulesString + ", " + name
        }
    }
}