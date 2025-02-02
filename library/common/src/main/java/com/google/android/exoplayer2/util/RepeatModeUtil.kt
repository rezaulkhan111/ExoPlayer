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
package com.google.android.exoplayer2.util

import androidx.annotation.IntDef
import com.google.android.exoplayer2.Player
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Util class for repeat mode handling.  */
object RepeatModeUtil {
    /** All repeat mode buttons disabled.  */
    const val REPEAT_TOGGLE_MODE_NONE: Int = 0

    /** "Repeat One" button enabled.  */
    const val REPEAT_TOGGLE_MODE_ONE: Int = 1

    /** "Repeat All" button enabled.  */
    const val REPEAT_TOGGLE_MODE_ALL: Int = 1 shl 1 // 2

    /**
     * Gets the next repeat mode out of `enabledModes` starting from `currentMode`.
     *
     * @param currentMode The current repeat mode.
     * @param enabledModes Bitmask of enabled modes.
     * @return The next repeat mode.
     */
    @Player.RepeatMode
    fun getNextRepeatMode(@Player.RepeatMode currentMode: Int, enabledModes: Int): Int {
        for (offset in 1..2) {
            @Player.RepeatMode val proposedMode: Int = (currentMode + offset) % 3
            if (isRepeatModeEnabled(proposedMode, enabledModes)) {
                return proposedMode
            }
        }
        return currentMode
    }

    /**
     * Verifies whether a given `repeatMode` is enabled in the bitmask `enabledModes`.
     *
     * @param repeatMode The mode to check.
     * @param enabledModes The bitmask representing the enabled modes.
     * @return `true` if enabled.
     */
    fun isRepeatModeEnabled(@Player.RepeatMode repeatMode: Int, enabledModes: Int): Boolean {
        return when (repeatMode) {
            Player.REPEAT_MODE_OFF -> true
            Player.REPEAT_MODE_ONE -> (enabledModes and REPEAT_TOGGLE_MODE_ONE) != 0
            Player.REPEAT_MODE_ALL -> (enabledModes and REPEAT_TOGGLE_MODE_ALL) != 0
            else -> false
        }
    }

    /**
     * Set of repeat toggle modes. Can be combined using bit-wise operations. Possible flag values are
     * [.REPEAT_TOGGLE_MODE_NONE], [.REPEAT_TOGGLE_MODE_ONE] and [ ][.REPEAT_TOGGLE_MODE_ALL].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef(flag = true, value = [REPEAT_TOGGLE_MODE_NONE, REPEAT_TOGGLE_MODE_ONE, REPEAT_TOGGLE_MODE_ALL])
    annotation class RepeatToggleModes constructor()
}