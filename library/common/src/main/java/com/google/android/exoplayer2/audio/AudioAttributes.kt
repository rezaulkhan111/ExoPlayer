/*
 * Copyright 2017 The Android Open Source Project
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

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.annotation.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.*
import com.google.android.exoplayer2.C.AudioAllowedCapturePolicy
import com.google.android.exoplayer2.C.AudioContentType
import com.google.android.exoplayer2.C.AudioFlags
import com.google.android.exoplayer2.C.AudioUsage
import com.google.android.exoplayer2.C.SpatializationBehavior
import com.google.android.exoplayer2.audio.AudioAttributes.Builder
import com.google.android.exoplayer2.util.*
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * Attributes for audio playback, which configure the underlying platform [ ].
 *
 *
 * To set the audio attributes, create an instance using the [Builder] and either pass it
 * to the player or send a message of type `Renderer#MSG_SET_AUDIO_ATTRIBUTES` to the audio
 * renderers.
 *
 *
 * This class is based on [android.media.AudioAttributes], but can be used on all supported
 * API versions.
 */
class AudioAttributes : Bundleable {

    /**
     * The default audio attributes, where the content type is [C.AUDIO_CONTENT_TYPE_UNKNOWN],
     * usage is [C.USAGE_MEDIA], capture policy is [C.ALLOW_CAPTURE_BY_ALL] and no flags
     * are set.
     */
    val DEFAULT: AudioAttributes = Builder().build()

    /** Builder for [AudioAttributes].  */
    class Builder {
        @AudioContentType
        private var contentType: Int

        @AudioFlags
        private var flags: Int

        @AudioUsage
        private var usage: Int

        @AudioAllowedCapturePolicy
        private var allowedCapturePolicy: Int

        @SpatializationBehavior
        private var spatializationBehavior: Int

        /**
         * Creates a new builder for [AudioAttributes].
         *
         *
         * By default the content type is [C.AUDIO_CONTENT_TYPE_UNKNOWN], usage is [ ][C.USAGE_MEDIA], capture policy is [C.ALLOW_CAPTURE_BY_ALL] and no flags are set.
         */
        constructor() {
            contentType = C.AUDIO_CONTENT_TYPE_UNKNOWN
            flags = 0
            usage = C.USAGE_MEDIA
            allowedCapturePolicy = C.ALLOW_CAPTURE_BY_ALL
            spatializationBehavior = C.SPATIALIZATION_BEHAVIOR_AUTO
        }

        /** See [android.media.AudioAttributes.Builder.setContentType]  */
        @CanIgnoreReturnValue
        fun setContentType(@AudioContentType contentType: Int): Builder {
            this.contentType = contentType
            return this
        }

        /** See [android.media.AudioAttributes.Builder.setFlags]  */
        @CanIgnoreReturnValue
        fun setFlags(@AudioFlags flags: Int): Builder {
            this.flags = flags
            return this
        }

        /** See [android.media.AudioAttributes.Builder.setUsage]  */
        @CanIgnoreReturnValue
        fun setUsage(@AudioUsage usage: Int): Builder {
            this.usage = usage
            return this
        }

        /** See [android.media.AudioAttributes.Builder.setAllowedCapturePolicy].  */
        @CanIgnoreReturnValue
        fun setAllowedCapturePolicy(@AudioAllowedCapturePolicy allowedCapturePolicy: Int): Builder {
            this.allowedCapturePolicy = allowedCapturePolicy
            return this
        }

        /** See [android.media.AudioAttributes.Builder.setSpatializationBehavior].  */
        @CanIgnoreReturnValue
        fun setSpatializationBehavior(@SpatializationBehavior spatializationBehavior: Int): Builder {
            this.spatializationBehavior = spatializationBehavior
            return this
        }

        /** Creates an [AudioAttributes] instance from this builder.  */
        fun build(): AudioAttributes {
            return AudioAttributes(contentType, flags, usage, allowedCapturePolicy, spatializationBehavior)
        }
    }

    /** The [C.AudioContentType].  */
    @AudioContentType
    var contentType = 0

    /** The [C.AudioFlags].  */
    @AudioFlags
    var flags = 0

    /** The [C.AudioUsage].  */
    @AudioUsage
    var usage = 0

    /** The [C.AudioAllowedCapturePolicy].  */
    @AudioAllowedCapturePolicy
    var allowedCapturePolicy = 0

    /** The [C.SpatializationBehavior].  */
    @SpatializationBehavior
    var spatializationBehavior = 0

    private var audioAttributesV21: AudioAttributesV21? = null

    private constructor(@AudioContentType contentType: Int, @AudioFlags flags: Int, @AudioUsage usage: Int, @AudioAllowedCapturePolicy allowedCapturePolicy: Int, @SpatializationBehavior spatializationBehavior: Int) {
        this.contentType = contentType
        this.flags = flags
        this.usage = usage
        this.allowedCapturePolicy = allowedCapturePolicy
        this.spatializationBehavior = spatializationBehavior
    }

    /**
     * Returns a [AudioAttributesV21] from this instance.
     *
     *
     * Some fields are ignored if the corresponding [android.media.AudioAttributes.Builder]
     * setter is not available on the current API level.
     */
    @RequiresApi(21)
    fun getAudioAttributesV21(): AudioAttributesV21? {
        if (audioAttributesV21 == null) {
            audioAttributesV21 = AudioAttributesV21(this)
        }
        return audioAttributesV21
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as AudioAttributes
        return contentType == other.contentType && flags == other.flags && usage == other.usage && allowedCapturePolicy == other.allowedCapturePolicy && spatializationBehavior == other.spatializationBehavior
    }

    override fun hashCode(): Int {
        var result = 17
        result = 31 * result + contentType
        result = 31 * result + flags
        result = 31 * result + usage
        result = 31 * result + allowedCapturePolicy
        result = 31 * result + spatializationBehavior
        return result
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [FIELD_CONTENT_TYPE, FIELD_FLAGS, FIELD_USAGE, FIELD_ALLOWED_CAPTURE_POLICY, FIELD_SPATIALIZATION_BEHAVIOR])
    private annotation class FieldNumber {}

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(keyForField(FIELD_CONTENT_TYPE), contentType)
        bundle.putInt(keyForField(FIELD_FLAGS), flags)
        bundle.putInt(keyForField(FIELD_USAGE), usage)
        bundle.putInt(keyForField(FIELD_ALLOWED_CAPTURE_POLICY), allowedCapturePolicy)
        bundle.putInt(keyForField(FIELD_SPATIALIZATION_BEHAVIOR), spatializationBehavior)
        return bundle
    }

    /** Object that can restore [AudioAttributes] from a [Bundle].  */
    val CREATOR: Bundleable.Creator<AudioAttributes> = Bundleable.Creator<AudioAttributes> { bundle: Bundle ->
        val builder = Builder()
        if (bundle.containsKey(keyForField(FIELD_CONTENT_TYPE))) {
            builder.setContentType(bundle.getInt(keyForField(FIELD_CONTENT_TYPE)))
        }
        if (bundle.containsKey(keyForField(FIELD_FLAGS))) {
            builder.setFlags(bundle.getInt(keyForField(FIELD_FLAGS)))
        }
        if (bundle.containsKey(keyForField(FIELD_USAGE))) {
            builder.setUsage(bundle.getInt(keyForField(FIELD_USAGE)))
        }
        if (bundle.containsKey(keyForField(FIELD_ALLOWED_CAPTURE_POLICY))) {
            builder.setAllowedCapturePolicy(bundle.getInt(keyForField(FIELD_ALLOWED_CAPTURE_POLICY)))
        }
        if (bundle.containsKey(keyForField(FIELD_SPATIALIZATION_BEHAVIOR))) {
            builder.setSpatializationBehavior(bundle.getInt(keyForField(FIELD_SPATIALIZATION_BEHAVIOR)))
        }
        builder.build()
    }

    private fun keyForField(@FieldNumber field: Int): String? {
        return Integer.toString(field, Character.MAX_RADIX)
    }

    @RequiresApi(29)
    private object Api29 {
        @DoNotInline
        fun setAllowedCapturePolicy(builder: android.media.AudioAttributes.Builder, @AudioAllowedCapturePolicy allowedCapturePolicy: Int) {
            builder.setAllowedCapturePolicy(allowedCapturePolicy)
        }
    }

    @RequiresApi(32)
    private object Api32 {
        @DoNotInline
        fun setSpatializationBehavior(builder: android.media.AudioAttributes.Builder, @SpatializationBehavior spatializationBehavior: Int) {
            builder.setSpatializationBehavior(spatializationBehavior)
        }
    }

    companion object {
        private const val FIELD_CONTENT_TYPE = 0
        private const val FIELD_FLAGS = 1
        private const val FIELD_USAGE = 2
        private const val FIELD_ALLOWED_CAPTURE_POLICY = 3
        private const val FIELD_SPATIALIZATION_BEHAVIOR = 4

        /** A direct wrapper around [android.media.AudioAttributes].  */
        @RequiresApi(21)
        @SuppressLint("NewApi")
        class AudioAttributesV21 {
            var audioAttributes: android.media.AudioAttributes? = null

            constructor(audioAttributes: AudioAttributes) {
                val builder = android.media.AudioAttributes.Builder().setContentType(audioAttributes.contentType).setFlags(audioAttributes.flags).setUsage(audioAttributes.usage)
                if (Util.SDK_INT >= 29) {
                    Api29.setAllowedCapturePolicy(builder, audioAttributes.allowedCapturePolicy)
                }
                if (Util.SDK_INT >= 32) {
                    Api32.setSpatializationBehavior(builder, audioAttributes.spatializationBehavior)
                }
                this.audioAttributes = builder.build()
            }
        }
    }
}