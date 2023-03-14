/*
 * Copyright (C) 2022 The Android Open Source Project
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

com.google.android.exoplayer2.*
/** Immutable class for describing width and height dimensions in pixels.  */
class Size(width: Int, height: Int) {
    /** Returns the width of the size (in pixels), or [C.LENGTH_UNSET] if unknown.  */
    val width: Int
    /** Returns the height of the size (in pixels), or [C.LENGTH_UNSET] if unknown.  */
    val height: Int

    /**
     * Creates a new immutable Size instance.
     *
     * @param width The width of the size, in pixels, or [C.LENGTH_UNSET] if unknown.
     * @param height The height of the size, in pixels, or [C.LENGTH_UNSET] if unknown.
     * @throws IllegalArgumentException if an invalid `width` or `height` is specified.
     */
    init {
        Assertions.checkArgument(
                (width == C.LENGTH_UNSET || width >= 0) && (height == C.LENGTH_UNSET || height >= 0))
        this.width = width
        this.height = height
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (this === obj) {
            return true
        }
        if (obj is Size) {
            val other = obj
            return width == other.width && height == other.height
        }
        return false
    }

    override fun toString(): String {
        return width.toString() + "x" + height
    }

    override fun hashCode(): Int {
        // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        return height xor (width shl Integer.SIZE / 2 or (width ushr Integer.SIZE)) / 2
    }

    companion object {
        /** A static instance to represent an unknown size value.  */
        val UNKNOWN = Size( /* width= */C.LENGTH_UNSET,  /* height= */C.LENGTH_UNSET)
    }
}