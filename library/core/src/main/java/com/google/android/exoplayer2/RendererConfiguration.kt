/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2

/** The configuration of a [Renderer].  */
class RendererConfiguration
/**
 * @param tunneling Whether to enable tunneling.
 */(
        /** Whether to enable tunneling.  */
        val tunneling: Boolean) {
    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as RendererConfiguration
        return tunneling == other.tunneling
    }

    override fun hashCode(): Int {
        return if (tunneling) 0 else 1
    }

    companion object {
        /** The default configuration.  */
        @JvmField
        val DEFAULT = RendererConfiguration( /* tunneling= */false)
    }
}