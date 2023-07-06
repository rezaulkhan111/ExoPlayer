/*
 * Copyright (C) 2017 The Android Open Source Project
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
import androidx.annotation.*
import com.google.android.exoplayer2.util.*
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Parameters that apply to playback, including speed setting.  */
class PlaybackParameters @JvmOverloads constructor(@FloatRange(from = 0.0, fromInclusive = false) speed: Float, @FloatRange(from = 0.0, fromInclusive = false) pitch: Float =  /* pitch= */1f) : Bundleable {
    /** The factor by which playback will be sped up.  */
    val speed: Float

    /** The factor by which pitch will be shifted.  */
    val pitch: Float
    private val scaledUsPerMs: Int

    /**
     * Returns the media time in microseconds that will elapse in `timeMs` milliseconds of
     * wallclock time.
     *
     * @param timeMs The time to scale, in milliseconds.
     * @return The scaled time, in microseconds.
     */
    fun getMediaTimeUsForPlayoutTimeMs(timeMs: Long): Long {
        return timeMs * scaledUsPerMs
    }

    /**
     * Returns a copy with the given speed.
     *
     * @param speed The new speed. Must be greater than zero.
     * @return The copied playback parameters.
     */
    @CheckResult
    fun withSpeed(@FloatRange(from = 0, fromInclusive = false) speed: Float): PlaybackParameters {
        return PlaybackParameters(speed, pitch)
    }

    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other: PlaybackParameters = obj as PlaybackParameters
        return speed == other.speed && pitch == other.pitch
    }

    public override fun hashCode(): Int {
        var result: Int = 17
        result = 31 * result + java.lang.Float.floatToRawIntBits(speed)
        result = 31 * result + java.lang.Float.floatToRawIntBits(pitch)
        return result
    }

    public override fun toString(): String {
        return Util.formatInvariant("PlaybackParameters(speed=%.2f, pitch=%.2f)", speed, pitch)
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([PlaybackParameters.Companion.FIELD_SPEED, PlaybackParameters.Companion.FIELD_PITCH])
    private annotation class FieldNumber constructor()

    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putFloat(PlaybackParameters.Companion.keyForField(PlaybackParameters.Companion.FIELD_SPEED), speed)
        bundle.putFloat(PlaybackParameters.Companion.keyForField(PlaybackParameters.Companion.FIELD_PITCH), pitch)
        return bundle
    }
    /**
     * Creates new playback parameters that set the playback speed/pitch.
     *
     * @param speed The factor by which playback will be sped up. Must be greater than zero.
     * @param pitch The factor by which the pitch of audio will be adjusted. Must be greater than
     * zero. Useful values are `1` (to time-stretch audio) and the same value as passed in
     * as the `speed` (to resample audio, which is useful for slow-motion videos).
     */
    /**
     * Creates new playback parameters that set the playback speed. The pitch of audio will not be
     * adjusted, so the effect is to time-stretch the audio.
     *
     * @param speed The factor by which playback will be sped up. Must be greater than zero.
     */
    init {
        Assertions.checkArgument(speed > 0)
        Assertions.checkArgument(pitch > 0)
        this.speed = speed
        this.pitch = pitch
        scaledUsPerMs = Math.round(speed * 1000f)
    }

    companion object {
        /** The default playback parameters: real-time playback with no silence skipping.  */
        val DEFAULT: PlaybackParameters = PlaybackParameters( /* speed= */1f)
        private val FIELD_SPEED: Int = 0
        private val FIELD_PITCH: Int = 1

        /** Object that can restore [PlaybackParameters] from a [Bundle].  */
        val CREATOR: Bundleable.Creator<PlaybackParameters> = Bundleable.Creator({ bundle: Bundle ->
            val speed: Float = bundle.getFloat(PlaybackParameters.Companion.keyForField(PlaybackParameters.Companion.FIELD_SPEED),  /* defaultValue= */1f)
            val pitch: Float = bundle.getFloat(PlaybackParameters.Companion.keyForField(PlaybackParameters.Companion.FIELD_PITCH),  /* defaultValue= */1f)
            PlaybackParameters(speed, pitch)
        })

        private fun keyForField(field: @PlaybackParameters.FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}