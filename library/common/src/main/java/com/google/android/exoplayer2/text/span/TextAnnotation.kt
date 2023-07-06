/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.text.span

import androidx.annotation.IntDef
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Properties of a text annotation (i.e. ruby, text emphasis marks).  */
object TextAnnotation {
    /** The text annotation position is unknown.  */
    const val POSITION_UNKNOWN = -1

    /**
     * For horizontal text, the text annotation should be positioned above the base text.
     *
     *
     * For vertical text it should be positioned to the right, same as CSS's [ruby-position](https://developer.mozilla.org/en-US/docs/Web/CSS/ruby-position).
     */
    const val POSITION_BEFORE = 1

    /**
     * For horizontal text, the text annotation should be positioned below the base text.
     *
     *
     * For vertical text it should be positioned to the left, same as CSS's [ruby-position](https://developer.mozilla.org/en-US/docs/Web/CSS/ruby-position).
     */
    const val POSITION_AFTER = 2

    /**
     * The possible positions of the annotation text relative to the base text.
     *
     *
     * One of:
     *
     *
     *  * [.POSITION_UNKNOWN]
     *  * [.POSITION_BEFORE]
     *  * [.POSITION_AFTER]
     *
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [POSITION_UNKNOWN, POSITION_BEFORE, POSITION_AFTER])
    annotation class Position
}