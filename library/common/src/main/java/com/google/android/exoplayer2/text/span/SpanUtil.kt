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
package com.google.android.exoplayer2.text.span

import android.text.Spannable

/**
 * Utility methods for Android [span
 * styling](https://developer.android.com/guide/topics/text/spans).
 */
object SpanUtil {
    /**
     * Adds `span` to `spannable` between `start` and `end`, removing any
     * existing spans of the same type and with the same indices and flags.
     *
     *
     * This is useful for types of spans that don't make sense to duplicate and where the
     * evaluation order might have an unexpected impact on the final text, e.g. [ ].
     *
     * @param spannable The [Spannable] to add `span` to.
     * @param span The span object to be added.
     * @param start The start index to add the new span at.
     * @param end The end index to add the new span at.
     * @param spanFlags The flags to pass to [Spannable.setSpan].
     */
    fun addOrReplaceSpan(
            spannable: Spannable, span: Any, start: Int, end: Int, spanFlags: Int) {
        val existingSpans = spannable.getSpans(start, end, span.javaClass)
        for (existingSpan in existingSpans) {
            if (spannable.getSpanStart(existingSpan) == start && spannable.getSpanEnd(existingSpan) == end && spannable.getSpanFlags(existingSpan) == spanFlags) {
                spannable.removeSpan(existingSpan)
            }
        }
        spannable.setSpan(span, start, end, spanFlags)
    }
}