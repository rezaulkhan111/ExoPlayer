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

/**
 * A styling span for text emphasis marks.
 *
 *
 * These are pronunciation aids such as [Japanese boutens](https://www.w3.org/TR/jlreq/?lang=en#term.emphasis-dots) which can be
 * rendered using the [
 * text-emphasis](https://developer.mozilla.org/en-US/docs/Web/CSS/text-emphasis) CSS property.
 */
// NOTE: There's no Android layout support for text emphasis, so this span currently doesn't extend
// any styling superclasses (e.g. MetricAffectingSpan). The only way to render this emphasis is to
// extract the spans and do the layout manually.
class TextEmphasisSpan : LanguageFeatureSpan {
    companion object {
        const val MARK_SHAPE_NONE = 0
        const val MARK_SHAPE_CIRCLE = 1
        const val MARK_SHAPE_DOT = 2
        const val MARK_SHAPE_SESAME = 3
        const val MARK_FILL_UNKNOWN = 0
        const val MARK_FILL_FILLED = 1
        const val MARK_FILL_OPEN = 2
    }

    /**
     * The possible mark shapes that can be used.
     *
     *
     * One of:
     *
     *
     *  * [.MARK_SHAPE_NONE]
     *  * [.MARK_SHAPE_CIRCLE]
     *  * [.MARK_SHAPE_DOT]
     *  * [.MARK_SHAPE_SESAME]
     *
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [MARK_SHAPE_NONE, MARK_SHAPE_CIRCLE, MARK_SHAPE_DOT, MARK_SHAPE_SESAME])
    annotation class MarkShape {}

    /**
     * The possible mark fills that can be used.
     *
     *
     * One of:
     *
     *
     *  * [.MARK_FILL_UNKNOWN]
     *  * [.MARK_FILL_FILLED]
     *  * [.MARK_FILL_OPEN]
     *
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [MARK_FILL_UNKNOWN, MARK_FILL_FILLED, MARK_FILL_OPEN])
    annotation class MarkFill {}
}