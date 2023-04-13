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

/**
 * Interface for a [GlTextureProcessor] that samples from an external texture.
 *
 *
 * Use [.setTextureTransformMatrix] to provide the texture's transformation
 * matrix.
 */
/* package */
internal interface ExternalTextureProcessor : GlTextureProcessor {
    /**
     * Sets the texture transform matrix for converting an external surface texture's coordinates to
     * sampling locations.
     *
     * @param textureTransformMatrix The external surface texture's [     ][android.graphics.SurfaceTexture.getTransformMatrix].
     */
    fun setTextureTransformMatrix(textureTransformMatrix: FloatArray?)
}