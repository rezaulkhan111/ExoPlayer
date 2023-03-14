/*
 * Copyright 2022 The Android Open Source Project
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

android.view.*
/** Immutable value class for a [Surface] and supporting information.  */
class SurfaceInfo @JvmOverloads constructor(surface: Surface, width: Int, height: Int, orientationDegrees: Int =  /* orientationDegrees= */0) {
    /** The [Surface].  */
    val surface: Surface

    /** The width of frames rendered to the [.surface], in pixels.  */
    val width: Int

    /** The height of frames rendered to the [.surface], in pixels.  */
    val height: Int

    /**
     * A counter-clockwise rotation to apply to frames before rendering them to the [.surface].
     *
     *
     * Must be 0, 90, 180, or 270 degrees. Default is 0.
     */
    val orientationDegrees: Int
    /** Creates a new instance.  */
    /** Creates a new instance.  */
    init {
        Assertions.checkArgument(
                (orientationDegrees == 0
                        ) || (orientationDegrees == 90
                        ) || (orientationDegrees == 180
                        ) || (orientationDegrees == 270),
                "orientationDegrees must be 0, 90, 180, or 270")
        this.surface = surface
        this.width = width
        this.height = height
        this.orientationDegrees = orientationDegrees
    }

    public override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (!(o is SurfaceInfo)) {
            return false
        }
        val that: SurfaceInfo = o
        return (width == that.width
                ) && (height == that.height
                ) && (orientationDegrees == that.orientationDegrees
                ) && (surface == that.surface)
    }

    public override fun hashCode(): Int {
        var result: Int = surface.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + orientationDegrees
        return result
    }
}