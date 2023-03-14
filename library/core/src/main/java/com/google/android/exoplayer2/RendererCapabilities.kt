/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.SuppressLint
import androidx.annotation.IntDef
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.ExoPlaybackException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * Defines the capabilities of a [Renderer].
 */
interface RendererCapabilities {

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([FORMAT_HANDLED, FORMAT_EXCEEDS_CAPABILITIES, FORMAT_UNSUPPORTED_DRM, FORMAT_UNSUPPORTED_SUBTYPE, FORMAT_UNSUPPORTED_TYPE])
    @Deprecated("Use {@link C.FormatSupport} instead.")
    annotation class FormatSupport

    /**
     * Level of renderer support for adaptive format switches. One of [.ADAPTIVE_SEAMLESS],
     * [.ADAPTIVE_NOT_SEAMLESS] or [.ADAPTIVE_NOT_SUPPORTED].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([ADAPTIVE_SEAMLESS, ADAPTIVE_NOT_SEAMLESS, ADAPTIVE_NOT_SUPPORTED])
    annotation class AdaptiveSupport

    /**
     * Level of renderer support for tunneling. One of [.TUNNELING_SUPPORTED] or [ ][.TUNNELING_NOT_SUPPORTED].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([TUNNELING_SUPPORTED, TUNNELING_NOT_SUPPORTED])
    annotation class TunnelingSupport

    /**
     * Level of renderer support for hardware acceleration. One of [ ][.HARDWARE_ACCELERATION_SUPPORTED] and [.HARDWARE_ACCELERATION_NOT_SUPPORTED].
     *
     *
     * For video renderers, the level of support is indicated for non-tunneled output.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([HARDWARE_ACCELERATION_SUPPORTED, HARDWARE_ACCELERATION_NOT_SUPPORTED])
    annotation class HardwareAccelerationSupport

    /**
     * Level of decoder support. One of [.DECODER_SUPPORT_FALLBACK_MIMETYPE], [ ][.DECODER_SUPPORT_FALLBACK], and [.DECODER_SUPPORT_PRIMARY].
     *
     *
     * For video renderers, the level of support is indicated for non-tunneled output.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([DECODER_SUPPORT_FALLBACK_MIMETYPE, DECODER_SUPPORT_PRIMARY, DECODER_SUPPORT_FALLBACK])
    annotation class DecoderSupport

    /**
     * Combined renderer capabilities.
     *
     *
     * This is a bitwise OR of [C.FormatSupport], [AdaptiveSupport], [ ], [HardwareAccelerationSupport] and [DecoderSupport]. Use [ ][.getFormatSupport], [.getAdaptiveSupport], [.getTunnelingSupport], [ ][.getHardwareAccelerationSupport] and [.getDecoderSupport] to obtain individual
     * components. Use [.create], [.create] or [.create] to create combined capabilities from individual components.
     *
     *
     * Possible values:
     *
     *
     *  * [C.FormatSupport]: The level of support for the format itself. One of [       ][C.FORMAT_HANDLED], [C.FORMAT_EXCEEDS_CAPABILITIES], [       ][C.FORMAT_UNSUPPORTED_DRM], [C.FORMAT_UNSUPPORTED_SUBTYPE] and [       ][C.FORMAT_UNSUPPORTED_TYPE].
     *  * [AdaptiveSupport]: The level of support for adapting from the format to another
     * format of the same mime type. One of [.ADAPTIVE_SEAMLESS], [       ][.ADAPTIVE_NOT_SEAMLESS] and [.ADAPTIVE_NOT_SUPPORTED]. Only set if the level of
     * support for the format itself is [C.FORMAT_HANDLED] or [       ][C.FORMAT_EXCEEDS_CAPABILITIES].
     *  * [TunnelingSupport]: The level of support for tunneling. One of [       ][.TUNNELING_SUPPORTED] and [.TUNNELING_NOT_SUPPORTED]. Only set if the level of
     * support for the format itself is [C.FORMAT_HANDLED] or [       ][C.FORMAT_EXCEEDS_CAPABILITIES].
     *  * [HardwareAccelerationSupport]: The level of support for hardware acceleration. One
     * of [.HARDWARE_ACCELERATION_SUPPORTED] and [       ][.HARDWARE_ACCELERATION_NOT_SUPPORTED].
     *  * [DecoderSupport]: The level of decoder support. One of [       ][.DECODER_SUPPORT_PRIMARY] and [.DECODER_SUPPORT_FALLBACK].
     *
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE) // Intentionally empty to prevent assignment or comparison with individual flags without masking.
    @Target(TYPE_USE)
    @IntDef([])
    annotation class Capabilities

    /**
     * Returns the name of the [Renderer].
     */
    open fun getName(): String?

    /**
     * Returns the track type that the [Renderer] handles. For example, a video renderer will
     * return [C.TRACK_TYPE_VIDEO], an audio renderer will return [C.TRACK_TYPE_AUDIO], a
     * text renderer will return [C.TRACK_TYPE_TEXT], and so on.
     *
     * @return The [track type][C.TrackType].
     * @see Renderer.getTrackType
     */
    @TrackType
    open fun getTrackType(): Int

    /**
     * Returns the extent to which the [Renderer] supports a given format.
     *
     * @param format The format.
     * @return The [Capabilities] for this format.
     * @throws ExoPlaybackException If an error occurs.
     */
    @Throws(ExoPlaybackException::class)
    fun supportsFormat(format: Format?): @Capabilities Int

    /**
     * Returns the extent to which the [Renderer] supports adapting between supported formats
     * that have different MIME types.
     *
     * @return The [AdaptiveSupport] for adapting between supported formats that have different
     * MIME types.
     * @throws ExoPlaybackException If an error occurs.
     */
    @Throws(ExoPlaybackException::class)
    fun supportsMixedMimeTypeAdaptation(): @AdaptiveSupport Int

    companion object {
        /**
         * Returns [Capabilities] combining the given [C.FormatSupport], [ ] and [TunnelingSupport].
         *
         *
         * [HardwareAccelerationSupport] is set to [.HARDWARE_ACCELERATION_NOT_SUPPORTED]
         * and [DecoderSupport] is set to [.DECODER_SUPPORT_PRIMARY].
         *
         * @param formatSupport    The [C.FormatSupport].
         * @param adaptiveSupport  The [AdaptiveSupport].
         * @param tunnelingSupport The [TunnelingSupport].
         * @return The combined [Capabilities].
         */
        /**
         * Returns [Capabilities] for the given [C.FormatSupport].
         *
         *
         * [AdaptiveSupport] is set to [.ADAPTIVE_NOT_SUPPORTED], [TunnelingSupport]
         * is set to [.TUNNELING_NOT_SUPPORTED], [HardwareAccelerationSupport] is set to
         * [.HARDWARE_ACCELERATION_NOT_SUPPORTED] and [DecoderSupport] is set to [ ][.DECODER_SUPPORT_PRIMARY].
         *
         * @param formatSupport The [C.FormatSupport].
         * @return The combined [Capabilities] of the given [C.FormatSupport], [ ][.ADAPTIVE_NOT_SUPPORTED] and [.TUNNELING_NOT_SUPPORTED].
         */
        @JvmOverloads
        fun create(
                @C.FormatSupport formatSupport: Int,
                adaptiveSupport: @AdaptiveSupport Int = ADAPTIVE_NOT_SUPPORTED,
                tunnelingSupport: @TunnelingSupport Int = TUNNELING_NOT_SUPPORTED): @Capabilities Int {
            return create(
                    formatSupport,
                    adaptiveSupport,
                    tunnelingSupport,
                    HARDWARE_ACCELERATION_NOT_SUPPORTED,
                    DECODER_SUPPORT_PRIMARY)
        }

        /**
         * Returns [Capabilities] combining the given [C.FormatSupport], [ ], [TunnelingSupport], [HardwareAccelerationSupport] and [ ].
         *
         * @param formatSupport               The [C.FormatSupport].
         * @param adaptiveSupport             The [AdaptiveSupport].
         * @param tunnelingSupport            The [TunnelingSupport].
         * @param hardwareAccelerationSupport The [HardwareAccelerationSupport].
         * @param decoderSupport              The [DecoderSupport].
         * @return The combined [Capabilities].
         */
        // Suppression needed for IntDef casting.
        @JvmStatic
        @SuppressLint("WrongConstant")
        fun create(
                @C.FormatSupport formatSupport: Int,
                adaptiveSupport: @AdaptiveSupport Int,
                tunnelingSupport: @TunnelingSupport Int,
                hardwareAccelerationSupport: @HardwareAccelerationSupport Int,
                decoderSupport: @DecoderSupport Int): @Capabilities Int {
            return (formatSupport
                    or adaptiveSupport
                    or tunnelingSupport
                    or hardwareAccelerationSupport
                    or decoderSupport)
        }

        /**
         * Returns the [C.FormatSupport] from the combined [Capabilities].
         *
         * @param supportFlags The combined [Capabilities].
         * @return The [C.FormatSupport] only.
         */
        // Suppression needed for IntDef casting.
        @JvmStatic
        @SuppressLint("WrongConstant")
        @C.FormatSupport
        fun getFormatSupport(supportFlags: @Capabilities Int): Int {
            return supportFlags and FORMAT_SUPPORT_MASK
        }

        /**
         * Returns the [AdaptiveSupport] from the combined [Capabilities].
         *
         * @param supportFlags The combined [Capabilities].
         * @return The [AdaptiveSupport] only.
         */
        // Suppression needed for IntDef casting.
        @JvmStatic
        @SuppressLint("WrongConstant")
        fun getAdaptiveSupport(supportFlags: @Capabilities Int): @AdaptiveSupport Int {
            return supportFlags and ADAPTIVE_SUPPORT_MASK
        }

        /**
         * Returns the [TunnelingSupport] from the combined [Capabilities].
         *
         * @param supportFlags The combined [Capabilities].
         * @return The [TunnelingSupport] only.
         */
        // Suppression needed for IntDef casting.
        @JvmStatic
        @SuppressLint("WrongConstant")
        fun getTunnelingSupport(supportFlags: @Capabilities Int): @TunnelingSupport Int {
            return supportFlags and TUNNELING_SUPPORT_MASK
        }

        /**
         * Returns the [HardwareAccelerationSupport] from the combined [Capabilities].
         *
         * @param supportFlags The combined [Capabilities].
         * @return The [HardwareAccelerationSupport] only.
         */
        // Suppression needed for IntDef casting.
        @JvmStatic
        @SuppressLint("WrongConstant")
        fun getHardwareAccelerationSupport(
                supportFlags: @Capabilities Int): @HardwareAccelerationSupport Int {
            return supportFlags and HARDWARE_ACCELERATION_SUPPORT_MASK
        }

        /**
         * Returns the [DecoderSupport] from the combined [Capabilities].
         *
         * @param supportFlags The combined [Capabilities].
         * @return The [DecoderSupport] only.
         */
        // Suppression needed for IntDef casting.
        @JvmStatic
        @SuppressLint("WrongConstant")
        fun getDecoderSupport(supportFlags: @Capabilities Int): @DecoderSupport Int {
            return supportFlags and MODE_SUPPORT_MASK
        }

        /**
         * A mask to apply to [Capabilities] to obtain the [C.FormatSupport] only.
         */
        const val FORMAT_SUPPORT_MASK = 7

        @JvmField
        @Deprecated("Use {@link C#FORMAT_HANDLED} instead.")
        val FORMAT_HANDLED = C.FORMAT_HANDLED

        @Deprecated("Use {@link C#FORMAT_EXCEEDS_CAPABILITIES} instead.")
        val FORMAT_EXCEEDS_CAPABILITIES = C.FORMAT_EXCEEDS_CAPABILITIES

        @Deprecated("Use {@link C#FORMAT_UNSUPPORTED_DRM} instead.")
        val FORMAT_UNSUPPORTED_DRM = C.FORMAT_UNSUPPORTED_DRM

        @Deprecated("Use {@link C#FORMAT_UNSUPPORTED_SUBTYPE} instead.")
        val FORMAT_UNSUPPORTED_SUBTYPE = C.FORMAT_UNSUPPORTED_SUBTYPE

        @Deprecated("Use {@link C#FORMAT_UNSUPPORTED_TYPE} instead.")
        val FORMAT_UNSUPPORTED_TYPE = C.FORMAT_UNSUPPORTED_TYPE

        /**
         * A mask to apply to [Capabilities] to obtain the [AdaptiveSupport] only.
         */
        const val ADAPTIVE_SUPPORT_MASK = 3 shl 3

        /**
         * The [Renderer] can seamlessly adapt between formats.
         */
        const val ADAPTIVE_SEAMLESS = 2 shl 3

        /**
         * The [Renderer] can adapt between formats, but may suffer a brief discontinuity
         * (~50-100ms) when adaptation occurs.
         */
        const val ADAPTIVE_NOT_SEAMLESS = 1 shl 3

        /**
         * The [Renderer] does not support adaptation between formats.
         */
        const val ADAPTIVE_NOT_SUPPORTED = 0

        /**
         * A mask to apply to [Capabilities] to obtain [TunnelingSupport] only.
         */
        const val TUNNELING_SUPPORT_MASK = 1 shl 5

        /**
         * The [Renderer] supports tunneled output.
         */
        const val TUNNELING_SUPPORTED = 1 shl 5

        /**
         * The [Renderer] does not support tunneled output.
         */
        const val TUNNELING_NOT_SUPPORTED = 0

        /**
         * A mask to apply to [Capabilities] to obtain [HardwareAccelerationSupport] only.
         */
        const val HARDWARE_ACCELERATION_SUPPORT_MASK = 1 shl 6

        /**
         * The renderer is able to use hardware acceleration.
         */
        const val HARDWARE_ACCELERATION_SUPPORTED = 1 shl 6

        /**
         * The renderer is not able to use hardware acceleration.
         */
        const val HARDWARE_ACCELERATION_NOT_SUPPORTED = 0

        /**
         * A mask to apply to [Capabilities] to obtain [DecoderSupport] only.
         */
        const val MODE_SUPPORT_MASK = 3 shl 7

        /**
         * The renderer will use a decoder for fallback mimetype if possible as format's MIME type is
         * unsupported
         */
        const val DECODER_SUPPORT_FALLBACK_MIMETYPE = 2 shl 7

        /**
         * The renderer is able to use the primary decoder for the format's MIME type.
         */
        const val DECODER_SUPPORT_PRIMARY = 1 shl 7

        /**
         * The renderer will use a fallback decoder.
         */
        const val DECODER_SUPPORT_FALLBACK = 0
    }
}