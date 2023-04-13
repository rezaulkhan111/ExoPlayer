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
package com.google.android.exoplayer2.effect

import android.content.Context
import com.google.android.exoplayer2.util.FrameProcessingException
import com.google.common.collect.ImmutableList

/**
 * Specifies a 4x4 RGB color transformation matrix to apply to each frame in the fragment shader.
 */
interface RgbMatrix : GlEffect {
    /**
     * Returns the 4x4 RGB transformation [matrix][android.opengl.Matrix] to apply to the
     * color values of each pixel in the frame with the given timestamp.
     *
     * @param presentationTimeUs The timestamp of the frame to apply the matrix on.
     * @param useHdr If `true`, colors will be in linear RGB BT.2020. If `false`, colors
     * will be in linear RGB BT.709. Must be consistent with `useHdr` in [     ][.toGlTextureProcessor].
     * @return The `RgbMatrix` to apply to the frame.
     */
    fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray?
    @Throws(FrameProcessingException::class)
    override fun toGlTextureProcessor(
        context: Context,
        useHdr: Boolean
    ): SingleFrameGlTextureProcessor {
        return MatrixTextureProcessor.create(
            context,  /* matrixTransformations= */
            ImmutableList.of(),  /* rgbMatrices= */
            ImmutableList.of(this),
            useHdr
        )
    }
}