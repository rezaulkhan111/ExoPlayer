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

import android.os.Bundle
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.*
import com.google.android.exoplayer2.audio.AudioAttributes.Builder
import com.google.android.exoplayer2.util.*

androidx.annotation .*import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.util.*
import com.google.errorprone.annotations.CanIgnoreReturnValueimport

java.lang.annotation .Documentedimport java.lang.annotation .Retentionimport java.lang.annotation .RetentionPolicy
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
class AudioAttributes private constructor(
        /** The [C.AudioContentType].  */
        val contentType: @AudioContentType Int,
        /** The [C.AudioFlags].  */
        val flags: @AudioFlags Int,
        /** The [C.AudioUsage].  */
        val usage: @AudioUsage Int,
        /** The [C.AudioAllowedCapturePolicy].  */
        val allowedCapturePolicy: @AudioAllowedCapturePolicy Int,
        /** The [C.SpatializationBehavior].  */
        val spatializationBehavior: @SpatializationBehavior Int) : Bundleable {
    /** A direct wrapper around [android.media.AudioAttributes].  */
    @RequiresApi(21)
    class AudioAttributesV21(audioAttributes: AudioAttributes) {
        val audioAttributes: android.media.AudioAttributes

        init {
            val builder: android.media.AudioAttributes.Builder = android.media.AudioAttributes.Builder()
                    .setContentType(audioAttributes.contentType)
                    .setFlags(audioAttributes.flags)
                    .setUsage(audioAttributes.usage)
            if (Util.SDK_INT >= 29) {
                Api29.setAllowedCapturePolicy(builder, audioAttributes.allowedCapturePolicy)
            }
            if (Util.SDK_INT >= 32) {
                Api32.setSpatializationBehavior(builder, audioAttributes.spatializationBehavior)
            }
            this.audioAttributes = builder.build()
        }
    }

    /** Builder for [AudioAttributes].  */
    class Builder constructor() {
        private var contentType: @AudioContentType Int
        private var flags: @AudioFlags Int
        private var usage: @AudioUsage Int
        private var allowedCapturePolicy: @AudioAllowedCapturePolicy Int
        private var spatializationBehavior: @SpatializationBehavior Int

        /**
         * Creates a new builder for [AudioAttributes].
         *
         *
         * By default the content type is [C.AUDIO_CONTENT_TYPE_UNKNOWN], usage is [ ][C.USAGE_MEDIA], capture policy is [C.ALLOW_CAPTURE_BY_ALL] and no flags are set.
         */
        init {
            contentType = C.AUDIO_CONTENT_TYPE_UNKNOWN
            flags = 0
            usage = C.USAGE_MEDIA
            allowedCapturePolicy = C.ALLOW_CAPTURE_BY_ALL
            spatializationBehavior = C.SPATIALIZATION_BEHAVIOR_AUTO
        }

        /** See [android.media.AudioAttributes.Builder.setContentType]  */
        @CanIgnoreReturnValue
        fun setContentType(contentType: @AudioContentType Int): Builder {
            this.contentType = contentType
            return this
        }

        /** See [android.media.AudioAttributes.Builder.setFlags]  */
        @CanIgnoreReturnValue
        fun setFlags(flags: @AudioFlags Int): Builder {
            this.flags = flags
            return this
        }

        /** See [android.media.AudioAttributes.Builder.setUsage]  */
        @CanIgnoreReturnValue
        fun setUsage(usage: @AudioUsage Int): Builder {
            this.usage = usage
            return this
        }

        /** See [android.media.AudioAttributes.Builder.setAllowedCapturePolicy].  */
        @CanIgnoreReturnValue
        fun setAllowedCapturePolicy(allowedCapturePolicy: @AudioAllowedCapturePolicy Int): Builder {
            this.allowedCapturePolicy = allowedCapturePolicy
            return this
        }

        /** See [android.media.AudioAttributes.Builder.setSpatializationBehavior].  */
        @CanIgnoreReturnValue
        fun setSpatializationBehavior(spatializationBehavior: @SpatializationBehavior Int): Builder {
            this.spatializationBehavior = spatializationBehavior
            return this
        }

        /** Creates an [AudioAttributes] instance from this builder.  */
        fun build(): AudioAttributes {
            return AudioAttributes(
                    contentType, flags, usage, allowedCapturePolicy, spatializationBehavior)
        }
    }

    /**
     * Returns a [AudioAttributesV21] from this instance.
     *
     *
     * Some fields are ignored if the corresponding [android.media.AudioAttributes.Builder]
     * setter is not available on the current API level.
     */
    @get:RequiresApi(21)
    var audioAttributesV21: AudioAttributesV21? = null
        get() {
            if (field == null) {
                field = AudioAttributesV21(this)
            }
            return field
        }
        private set

    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other: AudioAttributes = obj as AudioAttributes
        return (contentType == other.contentType
                ) && (flags == other.flags
                ) && (usage == other.usage
                ) && (allowedCapturePolicy == other.allowedCapturePolicy
                ) && (spatializationBehavior == other.spatializationBehavior)
    }

    public override fun hashCode(): Int {
        var result: Int = 17
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
    @IntDef([FIELD_CONTENT_TYPE, FIELD_FLAGS, FIELD_USAGE, FIELD_ALLOWED_CAPTURE_POLICY, FIELD_SPATIALIZATION_BEHAVIOR])
    private annotation class FieldNumber constructor()

    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putInt(keyForField(FIELD_CONTENT_TYPE), contentType)
        bundle.putInt(keyForField(FIELD_FLAGS), flags)
        bundle.putInt(keyForField(FIELD_USAGE), usage)
        bundle.putInt(keyForField(FIELD_ALLOWED_CAPTURE_POLICY), allowedCapturePolicy)
        bundle.putInt(keyForField(FIELD_SPATIALIZATION_BEHAVIOR), spatializationBehavior)
        return bundle
    }

    @RequiresApi(29)
    private object Api29 {
        @DoNotInline
        fun setAllowedCapturePolicy(
                builder: android.media.AudioAttributes.Builder,
                allowedCapturePolicy: @AudioAllowedCapturePolicy Int) {
            builder.setAllowedCapturePolicy(allowedCapturePolicy)
        }
    }

    @RequiresApi(32)
    private object Api32 {
        @DoNotInline
        fun setSpatializationBehavior(
                builder: android.media.AudioAttributes.Builder,
                spatializationBehavior: @SpatializationBehavior Int) {
            builder.setSpatializationBehavior(spatializationBehavior)
        }
    }

    companion object {
        /**
         * The default audio attributes, where the content type is [C.AUDIO_CONTENT_TYPE_UNKNOWN],
         * usage is [C.USAGE_MEDIA], capture policy is [C.ALLOW_CAPTURE_BY_ALL] and no flags
         * are set.
         */
        val DEFAULT: AudioAttributes = Builder().build()
        private val FIELD_CONTENT_TYPE: Int = 0
        private val FIELD_FLAGS: Int = 1
        private val FIELD_USAGE: Int = 2
        private val FIELD_ALLOWED_CAPTURE_POLICY: Int = 3
        private val FIELD_SPATIALIZATION_BEHAVIOR: Int = 4

        /** Object that can restore [AudioAttributes] from a [Bundle].  */
        val CREATOR: Bundleable.Creator<AudioAttributes> = Bundleable.Creator({ bundle: Bundle ->
            val builder: Builder = Builder()
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
                builder.setSpatializationBehavior(
                        bundle.getInt(keyForField(FIELD_SPATIALIZATION_BEHAVIOR)))
            }
            builder.build()
        })

        private fun keyForField(field: @FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}