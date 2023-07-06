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
package com.google.android.exoplayer2.video

import android.os.Bundle
import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import com.google.android.exoplayer2.Bundleable
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Represents the video size.  */
class VideoSize : Bundleable {

    /** The video width in pixels, 0 when unknown.  */
    @IntRange(from = 0)
    var width = 0

    /** The video height in pixels, 0 when unknown.  */
    @IntRange(from = 0)
    var height = 0

    /**
     * Clockwise rotation in degrees that the application should apply for the video for it to be
     * rendered in the correct orientation.
     *
     *
     * Is 0 if unknown or if no rotation is needed.
     *
     *
     * Player should apply video rotation internally, in which case unappliedRotationDegrees is 0.
     * But when a player can't apply the rotation, for example before API level 21, the unapplied
     * rotation is reported by this field for application to handle.
     *
     *
     * Applications that use [android.view.TextureView] can apply the rotation by calling
     * [android.view.TextureView.setTransform].
     */
    @IntRange(from = 0, to = 359)
    var unappliedRotationDegrees = 0

    /**
     * The width to height ratio of each pixel, 1 if unknown.
     *
     *
     * For the normal case of square pixels this will be equal to 1.0. Different values are
     * indicative of anamorphic content.
     */
    @FloatRange(from = 0.0, fromInclusive = false)
    var pixelWidthHeightRatio = 0.0f

    /**
     * Creates a VideoSize without unapplied rotation or anamorphic content.
     *
     * @param width The video width in pixels.
     * @param height The video height in pixels.
     */
    constructor(@IntRange(from = 0) width: Int, @IntRange(from = 0) height: Int) {
        VideoSize(width, height, DEFAULT_UNAPPLIED_ROTATION_DEGREES, DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO)
    }

    /**
     * Creates a VideoSize.
     *
     * @param width The video width in pixels.
     * @param height The video height in pixels.
     * @param unappliedRotationDegrees Clockwise rotation in degrees that the application should apply
     * for the video for it to be rendered in the correct orientation. See [     ][.unappliedRotationDegrees].
     * @param pixelWidthHeightRatio The width to height ratio of each pixel. For the normal case of
     * square pixels this will be equal to 1.0. Different values are indicative of anamorphic
     * content.
     */
    constructor(@IntRange(from = 0) width: Int,
                @IntRange(from = 0) height: Int,
                @IntRange(from = 0, to = 359) unappliedRotationDegrees: Int,
                @FloatRange(from = 0.0,
                        fromInclusive = false
                ) pixelWidthHeightRatio: Float) {
        this.width = width
        this.height = height
        this.unappliedRotationDegrees = unappliedRotationDegrees
        this.pixelWidthHeightRatio = pixelWidthHeightRatio
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj is VideoSize) {
            val other = obj
            return width == other.width && height == other.height && unappliedRotationDegrees == other.unappliedRotationDegrees && pixelWidthHeightRatio == other.pixelWidthHeightRatio
        }
        return false
    }

    override fun hashCode(): Int {
        var result = 7
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + unappliedRotationDegrees
        result = 31 * result + java.lang.Float.floatToRawIntBits(pixelWidthHeightRatio)
        return result
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [FIELD_WIDTH, FIELD_HEIGHT, FIELD_UNAPPLIED_ROTATION_DEGREES, FIELD_PIXEL_WIDTH_HEIGHT_RATIO])
    private annotation class FieldNumber {}

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(keyForField(FIELD_WIDTH), width)
        bundle.putInt(keyForField(FIELD_HEIGHT), height)
        bundle.putInt(keyForField(FIELD_UNAPPLIED_ROTATION_DEGREES), unappliedRotationDegrees)
        bundle.putFloat(keyForField(FIELD_PIXEL_WIDTH_HEIGHT_RATIO), pixelWidthHeightRatio)
        return bundle
    }

    /**
     * Creates a VideoSize.
     *
     * @param width The video width in pixels.
     * @param height The video height in pixels.
     * @param unappliedRotationDegrees Clockwise rotation in degrees that the application should apply
     * for the video for it to be rendered in the correct orientation. See [     ][.unappliedRotationDegrees].
     * @param pixelWidthHeightRatio The width to height ratio of each pixel. For the normal case of
     * square pixels this will be equal to 1.0. Different values are indicative of anamorphic
     * content.
     */
    companion object {
        private const val DEFAULT_WIDTH: Int = 0
        private const val DEFAULT_HEIGHT: Int = 0
        private const val DEFAULT_UNAPPLIED_ROTATION_DEGREES: Int = 0
        private const val DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO: Float = 1f

        val UNKNOWN: VideoSize = VideoSize(DEFAULT_WIDTH, DEFAULT_HEIGHT)

        private const val FIELD_WIDTH: Int = 0
        private const val FIELD_HEIGHT: Int = 1
        private const val FIELD_UNAPPLIED_ROTATION_DEGREES: Int = 2
        private const val FIELD_PIXEL_WIDTH_HEIGHT_RATIO: Int = 3

        val CREATOR: Bundleable.Creator<VideoSize> = Bundleable.Creator { bundle: Bundle ->
            val width: Int = bundle.getInt(keyForField(FIELD_WIDTH), DEFAULT_WIDTH)
            val height: Int = bundle.getInt(keyForField(FIELD_HEIGHT), DEFAULT_HEIGHT)
            val unappliedRotationDegrees: Int = bundle.getInt(keyForField(FIELD_UNAPPLIED_ROTATION_DEGREES), DEFAULT_UNAPPLIED_ROTATION_DEGREES)
            val pixelWidthHeightRatio: Float = bundle.getFloat(keyForField(FIELD_PIXEL_WIDTH_HEIGHT_RATIO), DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO)
            VideoSize(width, height, unappliedRotationDegrees, pixelWidthHeightRatio)
        }

        private fun keyForField(@FieldNumber field: Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}