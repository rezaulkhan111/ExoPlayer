/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.audio

import android.media.AudioTrack
import android.media.audiofx.AudioEffect

/**
 * Represents auxiliary effect information, which can be used to attach an auxiliary effect to an
 * underlying [AudioTrack].
 *
 *
 * Auxiliary effects can only be applied if the application has the `android.permission.MODIFY_AUDIO_SETTINGS` permission. Apps are responsible for retaining the
 * associated audio effect instance and releasing it when it's no longer needed. See the
 * documentation of [AudioEffect] for more information.
 */
class AuxEffectInfo {
    /** Value for [.effectId] representing no auxiliary effect.  */
    companion object {
        const val NO_AUX_EFFECT_ID = 0
    }

    /**
     * The identifier of the effect, or [.NO_AUX_EFFECT_ID] if there is no effect.
     *
     * @see android.media.AudioTrack.attachAuxEffect
     */
    var effectId = 0

    /**
     * The send level for the effect.
     *
     * @see android.media.AudioTrack.setAuxEffectSendLevel
     */
    var sendLevel = 0f

    /**
     * Creates an instance with the given effect identifier and send level.
     *
     * @param effectId The effect identifier. This is the value returned by [     ][AudioEffect.getId] on the effect, or {@value #NO_AUX_EFFECT_ID} which represents no
     * effect. This value is passed to [AudioTrack.attachAuxEffect] on the underlying
     * audio track.
     * @param sendLevel The send level for the effect, where 0 represents no effect and a value of 1
     * is full send. If `effectId` is not {@value #NO_AUX_EFFECT_ID}, this value is passed
     * to [AudioTrack.setAuxEffectSendLevel] on the underlying audio track.
     */
    constructor(effectId: Int, sendLevel: Float) {
        this.effectId = effectId
        this.sendLevel = sendLevel
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val auxEffectInfo = o as AuxEffectInfo
        return (effectId == auxEffectInfo.effectId && auxEffectInfo.sendLevel.compareTo(sendLevel) == 0)
    }

    override fun hashCode(): Int {
        var result = 17
        result = 31 * result + effectId
        result = 31 * result + java.lang.Float.floatToIntBits(sendLevel)
        return result
    }
}