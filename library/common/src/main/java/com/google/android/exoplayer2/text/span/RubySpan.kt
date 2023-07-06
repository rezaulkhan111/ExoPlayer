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
package com.google.android.exoplayer2.text.span

/**
 * A styling span for ruby text.
 *
 *
 * The text covered by this span is known as the "base text", and the ruby text is stored in
 * [.rubyText].
 *
 *
 * More information on [ruby characters](https://en.wikipedia.org/wiki/Ruby_character)
 * and [span styling](https://developer.android.com/guide/topics/text/spans).
 */
// NOTE: There's no Android layout support for rubies, so this span currently doesn't extend any
// styling superclasses (e.g. MetricAffectingSpan). The only way to render these rubies is to
// extract the spans and do the layout manually.
// TODO: Consider adding support for parenthetical text to be used when rendering doesn't support
// rubies (e.g. HTML <rp> tag).
class RubySpan : LanguageFeatureSpan {
    /** The ruby text, i.e. the smaller explanatory characters.  */
    var rubyText: String? = null

    /** The position of the ruby text relative to the base text.  */
    @TextAnnotation.Position
    var position = 0

    fun RubySpan(rubyText: String?, @TextAnnotation.Position position: Int) {
        this.rubyText = rubyText
        this.position = position
    }
}