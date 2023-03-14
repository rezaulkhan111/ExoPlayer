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
import androidx.annotation.IntDef
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * A rating for media content. The style of a rating can be one of [HeartRating], [ ], [StarRating], or [ThumbRating].
 */
abstract class Rating  // Default package-private constructor to prevent extending Rating class outside this package.
/* package */
internal constructor() : Bundleable {
    /** Whether the rating exists or not.  */
    abstract val isRated: Boolean

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([RATING_TYPE_UNSET, RATING_TYPE_HEART, RATING_TYPE_PERCENTAGE, RATING_TYPE_STAR, RATING_TYPE_THUMB])
    internal annotation class RatingType constructor()

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([FIELD_RATING_TYPE])
    private annotation class FieldNumber constructor()
    companion object {
        /** A float value that denotes the rating is unset.  */ /* package */
        val RATING_UNSET: Float = -1.0f

        /* package */
        val RATING_TYPE_UNSET: Int = -1

        /* package */
        val RATING_TYPE_HEART: Int = 0

        /* package */
        val RATING_TYPE_PERCENTAGE: Int = 1

        /* package */
        val RATING_TYPE_STAR: Int = 2

        /* package */
        val RATING_TYPE_THUMB: Int = 3

        /* package */
        val FIELD_RATING_TYPE: Int = 0

        /** Object that can restore a [Rating] from a [Bundle].  */
        val CREATOR: Bundleable.Creator<Rating> = Bundleable.Creator({ bundle: Bundle -> fromBundle(bundle) })
        private fun fromBundle(bundle: Bundle): Rating {
            val ratingType: @RatingType Int = bundle.getInt(keyForField(FIELD_RATING_TYPE),  /* defaultValue= */RATING_TYPE_UNSET)
            when (ratingType) {
                RATING_TYPE_HEART -> return HeartRating.Companion.CREATOR.fromBundle(bundle)
                RATING_TYPE_PERCENTAGE -> return PercentageRating.Companion.CREATOR.fromBundle(bundle)
                RATING_TYPE_STAR -> return StarRating.Companion.CREATOR.fromBundle(bundle)
                RATING_TYPE_THUMB -> return ThumbRating.Companion.CREATOR.fromBundle(bundle)
                RATING_TYPE_UNSET -> throw IllegalArgumentException("Unknown RatingType: " + ratingType)
                else -> throw IllegalArgumentException("Unknown RatingType: " + ratingType)
            }
        }

        private fun keyForField(field: @FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}