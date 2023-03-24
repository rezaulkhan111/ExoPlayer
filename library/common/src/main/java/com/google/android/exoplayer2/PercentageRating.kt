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
package com.google.android.exoplayer2

import android.os.Bundle
import androidx.annotation.FloatRange
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.util.Assertions
import com.google.common.base.Objects
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** A rating expressed as a percentage.  */
class PercentageRating : Rating {
    /**
     * Returns the percent value of this rating. Will be within the range `[0f, 100f]`, or
     * [.RATING_UNSET] if unrated.
     */
    val percent: Float

    /** Creates a unrated instance.  */
    constructor() {
        percent = Rating.Companion.RATING_UNSET
    }

    /**
     * Creates a rated instance with the given percentage.
     *
     * @param percent The percentage value of the rating.
     */
    constructor(@FloatRange(from = 0, to = 100) percent: Float) {
        Assertions.checkArgument(percent >= 0.0f && percent <= 100.0f, "percent must be in the range of [0, 100]")
        this.percent = percent
    }

    override val isRated: Boolean
        get() {
            return percent != Rating.Companion.RATING_UNSET
        }

    public override fun hashCode(): Int {
        return Objects.hashCode(percent)
    }

    public override fun equals(obj: Any?): Boolean {
        if (!(obj is PercentageRating)) {
            return false
        }
        return percent == obj.percent
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([Rating.Companion.FIELD_RATING_TYPE, PercentageRating.Companion.FIELD_PERCENT])
    private annotation class FieldNumber constructor()

    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putInt(PercentageRating.Companion.keyForField(Rating.Companion.FIELD_RATING_TYPE), PercentageRating.Companion.TYPE)
        bundle.putFloat(PercentageRating.Companion.keyForField(PercentageRating.Companion.FIELD_PERCENT), percent)
        return bundle
    }

    companion object {
        // Bundleable implementation.
        private val TYPE: @RatingType Int = Rating.Companion.RATING_TYPE_PERCENTAGE
        private val FIELD_PERCENT: Int = 1

        /** Object that can restore a [PercentageRating] from a [Bundle].  */
        val CREATOR: Bundleable.Creator<PercentageRating> = Bundleable.Creator({ bundle: Bundle? -> PercentageRating.Companion.fromBundle(bundle) })
        private fun fromBundle(bundle: Bundle): PercentageRating {
            Assertions.checkArgument((
                    bundle.getInt(PercentageRating.Companion.keyForField(Rating.Companion.FIELD_RATING_TYPE),  /* defaultValue= */Rating.Companion.RATING_TYPE_UNSET)
                            == PercentageRating.Companion.TYPE))
            val percent: Float = bundle.getFloat(PercentageRating.Companion.keyForField(PercentageRating.Companion.FIELD_PERCENT),  /* defaultValue= */Rating.Companion.RATING_UNSET)
            return if (percent == Rating.Companion.RATING_UNSET) PercentageRating() else PercentageRating(percent)
        }

        private fun keyForField(field: @PercentageRating.FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}