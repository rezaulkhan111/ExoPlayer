/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.ui

import android.view.*
import androidx.annotation.IntDef
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.lang.annotation.Documented
import java.lang.annotation.RetentionPolicy

/** Provides information about an overlay view shown on top of an ad view group.  */
class AdOverlayInfo {
    /**
     * The purpose of the overlay. One of [.PURPOSE_CONTROLS], [.PURPOSE_CLOSE_AD], [ ][.PURPOSE_OTHER] or [.PURPOSE_NOT_VISIBLE].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef(value = [PURPOSE_CONTROLS, PURPOSE_CLOSE_AD, PURPOSE_OTHER, PURPOSE_NOT_VISIBLE])
    annotation class Purpose {}

    /** A builder for [AdOverlayInfo] instances.  */
    class Builder {
        private var view: View? = null

        @Purpose
        private var purpose: Int = 0

        private var detailedReason: String? = null

        /**
         * Creates a new builder.
         *
         * @param view The view that is overlaying the player.
         * @param purpose The purpose of the view.
         */
        constructor(view: View?, @Purpose purpose: Int) {
            this.view = view
            this.purpose = purpose
        }

        /**
         * Sets an optional, detailed reason that the view is on top of the player.
         *
         * @return This builder, for convenience.
         */
        @CanIgnoreReturnValue
        fun setDetailedReason(detailedReason: String?): Builder? {
            this.detailedReason = detailedReason
            return this
        }

        /** Returns a new [AdOverlayInfo] instance with the current builder values.  */ // Using deprecated constructor while it still exists.
        fun build(): AdOverlayInfo? {
            return AdOverlayInfo(view, purpose, detailedReason)
        }
    }

    /** The overlay view.  */
    var view: View? = null

    /** The purpose of the overlay view.  */
    @Purpose
    var purpose: Int = 0

    /** An optional, detailed reason that the overlay view is needed.  */
    var reasonDetail: String? = null


    @Deprecated("Use {@link Builder} instead.")
    constructor(view: View?, @Purpose purpose: Int) {
        AdOverlayInfo(view, purpose,  /* detailedReason= */null)
    }


    @Deprecated("Use {@link Builder} instead.")
    constructor(view: View?, @Purpose purpose: Int, detailedReason: String?) {
        this.view = view
        this.purpose = purpose
        reasonDetail = detailedReason
    }

    companion object {
        /** Purpose for playback controls overlaying the player.  */
        const val PURPOSE_CONTROLS = 1

        /** Purpose for ad close buttons overlaying the player.  */
        const val PURPOSE_CLOSE_AD = 2

        /** Purpose for other overlays.  */
        const val PURPOSE_OTHER = 3

        /** Purpose for overlays that are not visible.  */
        const val PURPOSE_NOT_VISIBLE = 4
    }
}