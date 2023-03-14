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

import android.os.Bundle
import androidx.annotation.IntDef
import com.google.android.exoplayer2.C.*
import com.google.android.exoplayer2.drm.DrmInitData
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.util.*
import com.google.android.exoplayer2.video.ColorInfo
import com.google.common.base.Joiner
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/**
 * Represents a media format.
 *
 *
 * When building formats, populate all fields whose values are known and relevant to the type of
 * format being constructed. For information about different types of format, see ExoPlayer's [Supported formats page](https://exoplayer.dev/supported-formats.html).
 *
 * <h2>Fields commonly relevant to all formats</h2>
 *
 *
 *  * [.id]
 *  * [.label]
 *  * [.language]
 *  * [.selectionFlags]
 *  * [.roleFlags]
 *  * [.averageBitrate]
 *  * [.peakBitrate]
 *  * [.codecs]
 *  * [.metadata]
 *
 *
 * <h2 id="container-formats">Fields relevant to container formats</h2>
 *
 *
 *  * [.containerMimeType]
 *  * If the container only contains a single media track, [fields
 * relevant to sample formats](#sample-formats) can are also be relevant and can be set to describe the
 * sample format of that track.
 *  * If the container only contains one track of a given type (possibly alongside tracks of
 * other types), then fields relevant to that track type can be set to describe the properties
 * of the track. See the sections below for [video](#video-formats), [audio](#audio-formats) and [text](#text-formats) formats.
 *
 *
 * <h2 id="sample-formats">Fields relevant to sample formats</h2>
 *
 *
 *  * [.sampleMimeType]
 *  * [.maxInputSize]
 *  * [.initializationData]
 *  * [.drmInitData]
 *  * [.subsampleOffsetUs]
 *  * Fields relevant to the sample format's track type are also relevant. See the sections below
 * for [video](#video-formats), [audio](#audio-formats) and [text](#text-formats) formats.
 *
 *
 * <h2 id="video-formats">Fields relevant to video formats</h2>
 *
 *
 *  * [.width]
 *  * [.height]
 *  * [.frameRate]
 *  * [.rotationDegrees]
 *  * [.pixelWidthHeightRatio]
 *  * [.projectionData]
 *  * [.stereoMode]
 *  * [.colorInfo]
 *
 *
 * <h2 id="audio-formats">Fields relevant to audio formats</h2>
 *
 *
 *  * [.channelCount]
 *  * [.sampleRate]
 *  * [.pcmEncoding]
 *  * [.encoderDelay]
 *  * [.encoderPadding]
 *
 *
 * <h2 id="text-formats">Fields relevant to text formats</h2>
 *
 *
 *  * [.accessibilityChannel]
 *
 */
class Format private constructor(builder: Builder) : Bundleable {
    /**
     * Builds [Format] instances.
     *
     *
     * Use Format#buildUpon() to obtain a builder representing an existing [Format].
     *
     *
     * When building formats, populate all fields whose values are known and relevant to the type
     * of format being constructed. See the [Format] Javadoc for information about which fields
     * should be set for different types of format.
     */
    class Builder {
        var id: String? = null
        var label: String? = null
        var language: String? = null
        var selectionFlags: @SelectionFlags Int = 0
        var roleFlags: @RoleFlags Int = 0
        var averageBitrate: Int
        var peakBitrate: Int
        var codecs: String? = null
        var metadata: Metadata? = null

        // Container specific.
        var containerMimeType: String? = null

        // Sample specific.
        var sampleMimeType: String? = null
        var maxInputSize: Int
        var initializationData: List<ByteArray>? = null
        var drmInitData: DrmInitData? = null
        var subsampleOffsetUs: Long

        // Video specific.
        var width: Int
        var height: Int
        var frameRate: Float
        var rotationDegrees: Int = 0
        var pixelWidthHeightRatio: Float
        var projectionData: ByteArray?
        var stereoMode: @StereoMode Int
        var colorInfo: ColorInfo? = null

        // Audio specific.
        var channelCount: Int
        var sampleRate: Int
        var pcmEncoding: @PcmEncoding Int
        var encoderDelay: Int = 0
        var encoderPadding: Int = 0

        // Text specific.
        var accessibilityChannel: Int

        // Provided by the source.
        var cryptoType: @CryptoType Int

        /** Creates a new instance with default values.  */
        constructor() {
            averageBitrate = NO_VALUE
            peakBitrate = NO_VALUE
            // Sample specific.
            maxInputSize = NO_VALUE
            subsampleOffsetUs = OFFSET_SAMPLE_RELATIVE
            // Video specific.
            width = NO_VALUE
            height = NO_VALUE
            frameRate = NO_VALUE.toFloat()
            pixelWidthHeightRatio = 1.0f
            stereoMode = NO_VALUE
            // Audio specific.
            channelCount = NO_VALUE
            sampleRate = NO_VALUE
            pcmEncoding = NO_VALUE
            // Text specific.
            accessibilityChannel = NO_VALUE
            // Provided by the source.
            cryptoType = C.CRYPTO_TYPE_NONE
        }

        /**
         * Creates a new instance to build upon the provided [Format].
         *
         * @param format The [Format] to build upon.
         */
        constructor(format: Format) {
            id = format.id
            label = format.label
            language = format.language
            selectionFlags = format.selectionFlags
            roleFlags = format.roleFlags
            averageBitrate = format.averageBitrate
            peakBitrate = format.peakBitrate
            codecs = format.codecs
            metadata = format.metadata
            // Container specific.
            containerMimeType = format.containerMimeType
            // Sample specific.
            sampleMimeType = format.sampleMimeType
            maxInputSize = format.maxInputSize
            initializationData = format.initializationData
            drmInitData = format.drmInitData
            subsampleOffsetUs = format.subsampleOffsetUs
            // Video specific.
            width = format.width
            height = format.height
            frameRate = format.frameRate
            rotationDegrees = format.rotationDegrees
            pixelWidthHeightRatio = format.pixelWidthHeightRatio
            projectionData = format.projectionData
            stereoMode = format.stereoMode
            colorInfo = format.colorInfo
            // Audio specific.
            channelCount = format.channelCount
            sampleRate = format.sampleRate
            pcmEncoding = format.pcmEncoding
            encoderDelay = format.encoderDelay
            encoderPadding = format.encoderPadding
            // Text specific.
            accessibilityChannel = format.accessibilityChannel
            // Provided by the source.
            cryptoType = format.cryptoType
        }

        /**
         * Sets [Format.id]. The default value is `null`.
         *
         * @param id The [Format.id].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setId(id: String?): Builder {
            this.id = id
            return this
        }

        /**
         * Sets [Format.id] to [Integer.toString(id)][Integer.toString]. The default value
         * is `null`.
         *
         * @param id The [Format.id].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setId(id: Int): Builder {
            this.id = Integer.toString(id)
            return this
        }

        /**
         * Sets [Format.label]. The default value is `null`.
         *
         * @param label The [Format.label].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setLabel(label: String?): Builder {
            this.label = label
            return this
        }

        /**
         * Sets [Format.language]. The default value is `null`.
         *
         * @param language The [Format.language].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setLanguage(language: String?): Builder {
            this.language = language
            return this
        }

        /**
         * Sets [Format.selectionFlags]. The default value is 0.
         *
         * @param selectionFlags The [Format.selectionFlags].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setSelectionFlags(selectionFlags: @SelectionFlags Int): Builder {
            this.selectionFlags = selectionFlags
            return this
        }

        /**
         * Sets [Format.roleFlags]. The default value is 0.
         *
         * @param roleFlags The [Format.roleFlags].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setRoleFlags(roleFlags: @RoleFlags Int): Builder {
            this.roleFlags = roleFlags
            return this
        }

        /**
         * Sets [Format.averageBitrate]. The default value is [.NO_VALUE].
         *
         * @param averageBitrate The [Format.averageBitrate].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setAverageBitrate(averageBitrate: Int): Builder {
            this.averageBitrate = averageBitrate
            return this
        }

        /**
         * Sets [Format.peakBitrate]. The default value is [.NO_VALUE].
         *
         * @param peakBitrate The [Format.peakBitrate].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setPeakBitrate(peakBitrate: Int): Builder {
            this.peakBitrate = peakBitrate
            return this
        }

        /**
         * Sets [Format.codecs]. The default value is `null`.
         *
         * @param codecs The [Format.codecs].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setCodecs(codecs: String?): Builder {
            this.codecs = codecs
            return this
        }

        /**
         * Sets [Format.metadata]. The default value is `null`.
         *
         * @param metadata The [Format.metadata].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setMetadata(metadata: Metadata?): Builder {
            this.metadata = metadata
            return this
        }
        // Container specific.
        /**
         * Sets [Format.containerMimeType]. The default value is `null`.
         *
         * @param containerMimeType The [Format.containerMimeType].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setContainerMimeType(containerMimeType: String?): Builder {
            this.containerMimeType = containerMimeType
            return this
        }
        // Sample specific.
        /**
         * Sets [Format.sampleMimeType]. The default value is `null`.
         *
         * @param sampleMimeType [Format.sampleMimeType].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setSampleMimeType(sampleMimeType: String?): Builder {
            this.sampleMimeType = sampleMimeType
            return this
        }

        /**
         * Sets [Format.maxInputSize]. The default value is [.NO_VALUE].
         *
         * @param maxInputSize The [Format.maxInputSize].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setMaxInputSize(maxInputSize: Int): Builder {
            this.maxInputSize = maxInputSize
            return this
        }

        /**
         * Sets [Format.initializationData]. The default value is `null`.
         *
         * @param initializationData The [Format.initializationData].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setInitializationData(initializationData: List<ByteArray>?): Builder {
            this.initializationData = initializationData
            return this
        }

        /**
         * Sets [Format.drmInitData]. The default value is `null`.
         *
         * @param drmInitData The [Format.drmInitData].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setDrmInitData(drmInitData: DrmInitData?): Builder {
            this.drmInitData = drmInitData
            return this
        }

        /**
         * Sets [Format.subsampleOffsetUs]. The default value is [.OFFSET_SAMPLE_RELATIVE].
         *
         * @param subsampleOffsetUs The [Format.subsampleOffsetUs].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setSubsampleOffsetUs(subsampleOffsetUs: Long): Builder {
            this.subsampleOffsetUs = subsampleOffsetUs
            return this
        }
        // Video specific.
        /**
         * Sets [Format.width]. The default value is [.NO_VALUE].
         *
         * @param width The [Format.width].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setWidth(width: Int): Builder {
            this.width = width
            return this
        }

        /**
         * Sets [Format.height]. The default value is [.NO_VALUE].
         *
         * @param height The [Format.height].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setHeight(height: Int): Builder {
            this.height = height
            return this
        }

        /**
         * Sets [Format.frameRate]. The default value is [.NO_VALUE].
         *
         * @param frameRate The [Format.frameRate].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setFrameRate(frameRate: Float): Builder {
            this.frameRate = frameRate
            return this
        }

        /**
         * Sets [Format.rotationDegrees]. The default value is 0.
         *
         * @param rotationDegrees The [Format.rotationDegrees].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setRotationDegrees(rotationDegrees: Int): Builder {
            this.rotationDegrees = rotationDegrees
            return this
        }

        /**
         * Sets [Format.pixelWidthHeightRatio]. The default value is 1.0f.
         *
         * @param pixelWidthHeightRatio The [Format.pixelWidthHeightRatio].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setPixelWidthHeightRatio(pixelWidthHeightRatio: Float): Builder {
            this.pixelWidthHeightRatio = pixelWidthHeightRatio
            return this
        }

        /**
         * Sets [Format.projectionData]. The default value is `null`.
         *
         * @param projectionData The [Format.projectionData].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setProjectionData(projectionData: ByteArray?): Builder {
            this.projectionData = projectionData
            return this
        }

        /**
         * Sets [Format.stereoMode]. The default value is [.NO_VALUE].
         *
         * @param stereoMode The [Format.stereoMode].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setStereoMode(stereoMode: @StereoMode Int): Builder {
            this.stereoMode = stereoMode
            return this
        }

        /**
         * Sets [Format.colorInfo]. The default value is `null`.
         *
         * @param colorInfo The [Format.colorInfo].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setColorInfo(colorInfo: ColorInfo?): Builder {
            this.colorInfo = colorInfo
            return this
        }
        // Audio specific.
        /**
         * Sets [Format.channelCount]. The default value is [.NO_VALUE].
         *
         * @param channelCount The [Format.channelCount].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setChannelCount(channelCount: Int): Builder {
            this.channelCount = channelCount
            return this
        }

        /**
         * Sets [Format.sampleRate]. The default value is [.NO_VALUE].
         *
         * @param sampleRate The [Format.sampleRate].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setSampleRate(sampleRate: Int): Builder {
            this.sampleRate = sampleRate
            return this
        }

        /**
         * Sets [Format.pcmEncoding]. The default value is [.NO_VALUE].
         *
         * @param pcmEncoding The [Format.pcmEncoding].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setPcmEncoding(pcmEncoding: @PcmEncoding Int): Builder {
            this.pcmEncoding = pcmEncoding
            return this
        }

        /**
         * Sets [Format.encoderDelay]. The default value is 0.
         *
         * @param encoderDelay The [Format.encoderDelay].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setEncoderDelay(encoderDelay: Int): Builder {
            this.encoderDelay = encoderDelay
            return this
        }

        /**
         * Sets [Format.encoderPadding]. The default value is 0.
         *
         * @param encoderPadding The [Format.encoderPadding].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setEncoderPadding(encoderPadding: Int): Builder {
            this.encoderPadding = encoderPadding
            return this
        }
        // Text specific.
        /**
         * Sets [Format.accessibilityChannel]. The default value is [.NO_VALUE].
         *
         * @param accessibilityChannel The [Format.accessibilityChannel].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setAccessibilityChannel(accessibilityChannel: Int): Builder {
            this.accessibilityChannel = accessibilityChannel
            return this
        }
        // Provided by source.
        /**
         * Sets [Format.cryptoType]. The default value is [C.CRYPTO_TYPE_NONE].
         *
         * @param cryptoType The [C.CryptoType].
         * @return The builder.
         */
        @CanIgnoreReturnValue
        fun setCryptoType(cryptoType: @CryptoType Int): Builder {
            this.cryptoType = cryptoType
            return this
        }

        // Build.
        fun build(): Format {
            return Format( /* builder= */this)
        }
    }

    /** An identifier for the format, or null if unknown or not applicable.  */
    val id: String?

    /** The human readable label, or null if unknown or not applicable.  */
    val label: String?

    /** The language as an IETF BCP 47 conformant tag, or null if unknown or not applicable.  */
    val language: String?

    /** Track selection flags.  */
    val selectionFlags: @SelectionFlags Int

    /** Track role flags.  */
    val roleFlags: @RoleFlags Int

    /**
     * The average bitrate in bits per second, or [.NO_VALUE] if unknown or not applicable. The
     * way in which this field is populated depends on the type of media to which the format
     * corresponds:
     *
     *
     *  * DASH representations: Always [Format.NO_VALUE].
     *  * HLS variants: The `AVERAGE-BANDWIDTH` attribute defined on the corresponding `EXT-X-STREAM-INF` tag in the multivariant playlist, or [Format.NO_VALUE] if not
     * present.
     *  * SmoothStreaming track elements: The `Bitrate` attribute defined on the
     * corresponding `TrackElement` in the manifest, or [Format.NO_VALUE] if not
     * present.
     *  * Progressive container formats: Often [Format.NO_VALUE], but may be populated with
     * the average bitrate of the container if known.
     *  * Sample formats: Often [Format.NO_VALUE], but may be populated with the average
     * bitrate of the stream of samples with type [.sampleMimeType] if known. Note that if
     * [.sampleMimeType] is a compressed format (e.g., [MimeTypes.AUDIO_AAC]), then
     * this bitrate is for the stream of still compressed samples.
     *
     */
    val averageBitrate: Int

    /**
     * The peak bitrate in bits per second, or [.NO_VALUE] if unknown or not applicable. The way
     * in which this field is populated depends on the type of media to which the format corresponds:
     *
     *
     *  * DASH representations: The `@bandwidth` attribute of the corresponding `Representation` element in the manifest.
     *  * HLS variants: The `BANDWIDTH` attribute defined on the corresponding `EXT-X-STREAM-INF` tag.
     *  * SmoothStreaming track elements: Always [Format.NO_VALUE].
     *  * Progressive container formats: Often [Format.NO_VALUE], but may be populated with
     * the peak bitrate of the container if known.
     *  * Sample formats: Often [Format.NO_VALUE], but may be populated with the peak bitrate
     * of the stream of samples with type [.sampleMimeType] if known. Note that if [       ][.sampleMimeType] is a compressed format (e.g., [MimeTypes.AUDIO_AAC]), then this
     * bitrate is for the stream of still compressed samples.
     *
     */
    val peakBitrate: Int

    /**
     * The bitrate in bits per second. This is the peak bitrate if known, or else the average bitrate
     * if known, or else [Format.NO_VALUE]. Equivalent to: `peakBitrate != NO_VALUE ?
     * peakBitrate : averageBitrate`.
     */
    val bitrate: Int

    /** Codecs of the format as described in RFC 6381, or null if unknown or not applicable.  */
    val codecs: String?

    /** Metadata, or null if unknown or not applicable.  */
    val metadata: Metadata?
    // Container specific.
    /** The mime type of the container, or null if unknown or not applicable.  */
    val containerMimeType: String?
    // Sample specific.
    /** The sample mime type, or null if unknown or not applicable.  */
    val sampleMimeType: String?

    /**
     * The maximum size of a buffer of data (typically one sample), or [.NO_VALUE] if unknown or
     * not applicable.
     */
    val maxInputSize: Int

    /**
     * Initialization data that must be provided to the decoder. Will not be null, but may be empty if
     * initialization data is not required.
     */
    val initializationData: List<ByteArray>

    /** DRM initialization data if the stream is protected, or null otherwise.  */
    val drmInitData: DrmInitData?

    /**
     * For samples that contain subsamples, this is an offset that should be added to subsample
     * timestamps. A value of [.OFFSET_SAMPLE_RELATIVE] indicates that subsample timestamps are
     * relative to the timestamps of their parent samples.
     */
    val subsampleOffsetUs: Long
    // Video specific.
    /** The width of the video in pixels, or [.NO_VALUE] if unknown or not applicable.  */
    val width: Int

    /** The height of the video in pixels, or [.NO_VALUE] if unknown or not applicable.  */
    val height: Int

    /** The frame rate in frames per second, or [.NO_VALUE] if unknown or not applicable.  */
    val frameRate: Float

    /**
     * The clockwise rotation that should be applied to the video for it to be rendered in the correct
     * orientation, or 0 if unknown or not applicable. Only 0, 90, 180 and 270 are supported.
     */
    val rotationDegrees: Int

    /** The width to height ratio of pixels in the video, or 1.0 if unknown or not applicable.  */
    val pixelWidthHeightRatio: Float

    /** The projection data for 360/VR video, or null if not applicable.  */
    val projectionData: ByteArray?

    /**
     * The stereo layout for 360/3D/VR video, or [.NO_VALUE] if not applicable. Valid stereo
     * modes are [C.STEREO_MODE_MONO], [C.STEREO_MODE_TOP_BOTTOM], [ ][C.STEREO_MODE_LEFT_RIGHT], [C.STEREO_MODE_STEREO_MESH].
     */
    val stereoMode: @StereoMode Int

    /** The color metadata associated with the video, or null if not applicable.  */
    val colorInfo: ColorInfo?
    // Audio specific.
    /** The number of audio channels, or [.NO_VALUE] if unknown or not applicable.  */
    val channelCount: Int

    /** The audio sampling rate in Hz, or [.NO_VALUE] if unknown or not applicable.  */
    val sampleRate: Int

    /** The [C.PcmEncoding] for PCM audio. Set to [.NO_VALUE] for other media types.  */
    val pcmEncoding: @PcmEncoding Int

    /**
     * The number of frames to trim from the start of the decoded audio stream, or 0 if not
     * applicable.
     */
    val encoderDelay: Int

    /**
     * The number of frames to trim from the end of the decoded audio stream, or 0 if not applicable.
     */
    val encoderPadding: Int
    // Text specific.
    /** The Accessibility channel, or [.NO_VALUE] if not known or applicable.  */
    val accessibilityChannel: Int
    // Provided by source.
    /**
     * The type of crypto that must be used to decode samples associated with this format, or [ ][C.CRYPTO_TYPE_NONE] if the content is not encrypted. Cannot be [C.CRYPTO_TYPE_NONE] if
     * [.drmInitData] is non-null, but may be [C.CRYPTO_TYPE_UNSUPPORTED] to indicate that
     * the samples are encrypted using an unsupported crypto type.
     */
    var cryptoType: @CryptoType Int = 0

    // Lazily initialized hashcode.
    private var hashCode: Int = 0

    /** Returns a [Format.Builder] initialized with the values of this instance.  */
    fun buildUpon(): Builder {
        return Builder(this)
    }

    @Deprecated("Use {@link #buildUpon()} and {@link Builder#setMaxInputSize(int)}.")
    fun copyWithMaxInputSize(maxInputSize: Int): Format {
        return buildUpon().setMaxInputSize(maxInputSize).build()
    }

    @Deprecated("Use {@link #buildUpon()} and {@link Builder#setSubsampleOffsetUs(long)}.")
    fun copyWithSubsampleOffsetUs(subsampleOffsetUs: Long): Format {
        return buildUpon().setSubsampleOffsetUs(subsampleOffsetUs).build()
    }

    @Deprecated("Use {@link #buildUpon()} and {@link Builder#setLabel(String)} .")
    fun copyWithLabel(label: String?): Format {
        return buildUpon().setLabel(label).build()
    }

    @Deprecated("Use {@link #withManifestFormatInfo(Format)}.")
    fun copyWithManifestFormatInfo(manifestFormat: Format): Format {
        return withManifestFormatInfo(manifestFormat)
    }

    fun withManifestFormatInfo(manifestFormat: Format): Format {
        if (this === manifestFormat) {
            // No need to copy from ourselves.
            return this
        }
        val trackType: @TrackType Int = MimeTypes.getTrackType(sampleMimeType)

        // Use manifest value only.
        val id: String? = manifestFormat.id

        // Prefer manifest values, but fill in from sample format if missing.
        val label: String? = if (manifestFormat.label != null) manifestFormat.label else label
        var language: String? = language
        if (((trackType == C.TRACK_TYPE_TEXT || trackType == C.TRACK_TYPE_AUDIO)
                        && manifestFormat.language != null)) {
            language = manifestFormat.language
        }

        // Prefer sample format values, but fill in from manifest if missing.
        val averageBitrate: Int = if (averageBitrate == NO_VALUE) manifestFormat.averageBitrate else averageBitrate
        val peakBitrate: Int = if (peakBitrate == NO_VALUE) manifestFormat.peakBitrate else peakBitrate
        var codecs: String? = codecs
        if (codecs == null) {
            // The manifest format may be muxed, so filter only codecs of this format's type. If we still
            // have more than one codec then we're unable to uniquely identify which codec to fill in.
            val codecsOfType: String? = Util.getCodecsOfType(manifestFormat.codecs, trackType)
            if (Util.splitCodecs(codecsOfType).size == 1) {
                codecs = codecsOfType
            }
        }
        val metadata: Metadata? = if (metadata == null) manifestFormat.metadata else metadata.copyWithAppendedEntriesFrom(manifestFormat.metadata)
        var frameRate: Float = frameRate
        if (frameRate == NO_VALUE.toFloat() && trackType == C.TRACK_TYPE_VIDEO) {
            frameRate = manifestFormat.frameRate
        }

        // Merge manifest and sample format values.
        val selectionFlags: @SelectionFlags Int = selectionFlags or manifestFormat.selectionFlags
        val roleFlags: @RoleFlags Int = roleFlags or manifestFormat.roleFlags
        val drmInitData: DrmInitData? = DrmInitData.Companion.createSessionCreationData(manifestFormat.drmInitData, drmInitData)
        return buildUpon()
                .setId(id)
                .setLabel(label)
                .setLanguage(language)
                .setSelectionFlags(selectionFlags)
                .setRoleFlags(roleFlags)
                .setAverageBitrate(averageBitrate)
                .setPeakBitrate(peakBitrate)
                .setCodecs(codecs)
                .setMetadata(metadata)
                .setDrmInitData(drmInitData)
                .setFrameRate(frameRate)
                .build()
    }

    @Deprecated("Use {@link #buildUpon()}, {@link Builder#setEncoderDelay(int)} and {@link\n" + "   *     Builder#setEncoderPadding(int)}.")
    fun copyWithGaplessInfo(encoderDelay: Int, encoderPadding: Int): Format {
        return buildUpon().setEncoderDelay(encoderDelay).setEncoderPadding(encoderPadding).build()
    }

    @Deprecated("Use {@link #buildUpon()} and {@link Builder#setFrameRate(float)}.")
    fun copyWithFrameRate(frameRate: Float): Format {
        return buildUpon().setFrameRate(frameRate).build()
    }

    @Deprecated("Use {@link #buildUpon()} and {@link Builder#setDrmInitData(DrmInitData)}.")
    fun copyWithDrmInitData(drmInitData: DrmInitData?): Format {
        return buildUpon().setDrmInitData(drmInitData).build()
    }

    @Deprecated("Use {@link #buildUpon()} and {@link Builder#setMetadata(Metadata)}.")
    fun copyWithMetadata(metadata: Metadata?): Format {
        return buildUpon().setMetadata(metadata).build()
    }

    @Deprecated("Use {@link #buildUpon()} and {@link Builder#setAverageBitrate(int)} and {@link\n" + "   *     Builder#setPeakBitrate(int)}.")
    fun copyWithBitrate(bitrate: Int): Format {
        return buildUpon().setAverageBitrate(bitrate).setPeakBitrate(bitrate).build()
    }

    @Deprecated("Use {@link #buildUpon()}, {@link Builder#setWidth(int)} and {@link\n" + "   *     Builder#setHeight(int)}.")
    fun copyWithVideoSize(width: Int, height: Int): Format {
        return buildUpon().setWidth(width).setHeight(height).build()
    }

    /** Returns a copy of this format with the specified [.cryptoType].  */
    fun copyWithCryptoType(cryptoType: @CryptoType Int): Format {
        return buildUpon().setCryptoType(cryptoType).build()
    }

    /**
     * Returns the number of pixels if this is a video format whose [.width] and [.height]
     * are known, or [.NO_VALUE] otherwise
     */
    val pixelCount: Int
        get() {
            return if (width == NO_VALUE || height == NO_VALUE) NO_VALUE else (width * height)
        }

    public override fun toString(): String {
        return ("Format("
                + id
                + ", "
                + label
                + ", "
                + containerMimeType
                + ", "
                + sampleMimeType
                + ", "
                + codecs
                + ", "
                + bitrate
                + ", "
                + language
                + ", ["
                + width
                + ", "
                + height
                + ", "
                + frameRate
                + "]"
                + ", ["
                + channelCount
                + ", "
                + sampleRate
                + "])")
    }

    public override fun hashCode(): Int {
        if (hashCode == 0) {
            // Some fields for which hashing is expensive are deliberately omitted.
            var result: Int = 17
            result = 31 * result + (if (id == null) 0 else id.hashCode())
            result = 31 * result + (if (label != null) label.hashCode() else 0)
            result = 31 * result + (if (language == null) 0 else language.hashCode())
            result = 31 * result + selectionFlags
            result = 31 * result + roleFlags
            result = 31 * result + averageBitrate
            result = 31 * result + peakBitrate
            result = 31 * result + (if (codecs == null) 0 else codecs.hashCode())
            result = 31 * result + (if (metadata == null) 0 else metadata.hashCode())
            // Container specific.
            result = 31 * result + (if (containerMimeType == null) 0 else containerMimeType.hashCode())
            // Sample specific.
            result = 31 * result + (if (sampleMimeType == null) 0 else sampleMimeType.hashCode())
            result = 31 * result + maxInputSize
            // [Omitted] initializationData.
            // [Omitted] drmInitData.
            result = 31 * result + subsampleOffsetUs.toInt()
            // Video specific.
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + java.lang.Float.floatToIntBits(frameRate)
            result = 31 * result + rotationDegrees
            result = 31 * result + java.lang.Float.floatToIntBits(pixelWidthHeightRatio)
            // [Omitted] projectionData.
            result = 31 * result + stereoMode
            // [Omitted] colorInfo.
            // Audio specific.
            result = 31 * result + channelCount
            result = 31 * result + sampleRate
            result = 31 * result + pcmEncoding
            result = 31 * result + encoderDelay
            result = 31 * result + encoderPadding
            // Text specific.
            result = 31 * result + accessibilityChannel
            // Provided by the source.
            result = 31 * result + cryptoType
            hashCode = result
        }
        return hashCode
    }

    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other: Format = obj as Format
        if ((hashCode != 0) && (other.hashCode != 0) && (hashCode != other.hashCode)) {
            return false
        }
        // Field equality checks ordered by type, with the cheapest checks first.
        return ((selectionFlags == other.selectionFlags
                ) && (roleFlags == other.roleFlags
                ) && (averageBitrate == other.averageBitrate
                ) && (peakBitrate == other.peakBitrate
                ) && (maxInputSize == other.maxInputSize
                ) && (subsampleOffsetUs == other.subsampleOffsetUs
                ) && (width == other.width
                ) && (height == other.height
                ) && (rotationDegrees == other.rotationDegrees
                ) && (stereoMode == other.stereoMode
                ) && (channelCount == other.channelCount
                ) && (sampleRate == other.sampleRate
                ) && (pcmEncoding == other.pcmEncoding
                ) && (encoderDelay == other.encoderDelay
                ) && (encoderPadding == other.encoderPadding
                ) && (accessibilityChannel == other.accessibilityChannel
                ) && (cryptoType == other.cryptoType
                ) && (java.lang.Float.compare(frameRate, other.frameRate) == 0
                ) && (java.lang.Float.compare(pixelWidthHeightRatio, other.pixelWidthHeightRatio) == 0
                ) && Util.areEqual(id, other.id)
                && Util.areEqual(label, other.label)
                && Util.areEqual(codecs, other.codecs)
                && Util.areEqual(containerMimeType, other.containerMimeType)
                && Util.areEqual(sampleMimeType, other.sampleMimeType)
                && Util.areEqual(language, other.language)
                && Arrays.equals(projectionData, other.projectionData)
                && Util.areEqual(metadata, other.metadata)
                && Util.areEqual(colorInfo, other.colorInfo)
                && Util.areEqual(drmInitData, other.drmInitData)
                && initializationDataEquals(other))
    }

    /**
     * Returns whether the [.initializationData]s belonging to this format and `other` are
     * equal.
     *
     * @param other The other format whose [.initializationData] is being compared.
     * @return Whether the [.initializationData]s belonging to this format and `other` are
     * equal.
     */
    fun initializationDataEquals(other: Format): Boolean {
        if (initializationData.size != other.initializationData.size) {
            return false
        }
        for (i in initializationData.indices) {
            if (!Arrays.equals(initializationData.get(i), other.initializationData.get(i))) {
                return false
            }
        }
        return true
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([FIELD_ID, FIELD_LABEL, FIELD_LANGUAGE, FIELD_SELECTION_FLAGS, FIELD_ROLE_FLAGS, FIELD_AVERAGE_BITRATE, FIELD_PEAK_BITRATE, FIELD_CODECS, FIELD_METADATA, FIELD_CONTAINER_MIME_TYPE, FIELD_SAMPLE_MIME_TYPE, FIELD_MAX_INPUT_SIZE, FIELD_INITIALIZATION_DATA, FIELD_DRM_INIT_DATA, FIELD_SUBSAMPLE_OFFSET_US, FIELD_WIDTH, FIELD_HEIGHT, FIELD_FRAME_RATE, FIELD_ROTATION_DEGREES, FIELD_PIXEL_WIDTH_HEIGHT_RATIO, FIELD_PROJECTION_DATA, FIELD_STEREO_MODE, FIELD_COLOR_INFO, FIELD_CHANNEL_COUNT, FIELD_SAMPLE_RATE, FIELD_PCM_ENCODING, FIELD_ENCODER_DELAY, FIELD_ENCODER_PADDING, FIELD_ACCESSIBILITY_CHANNEL, FIELD_CRYPTO_TYPE])
    private annotation class FieldNumber constructor()

    public override fun toBundle(): Bundle {
        return toBundle( /* excludeMetadata= */false)
    }

    /**
     * Returns a [Bundle] representing the information stored in this object. If `excludeMetadata` is true, [metadata][Format.metadata] is excluded.
     */
    fun toBundle(excludeMetadata: Boolean): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putString(keyForField(FIELD_ID), id)
        bundle.putString(keyForField(FIELD_LABEL), label)
        bundle.putString(keyForField(FIELD_LANGUAGE), language)
        bundle.putInt(keyForField(FIELD_SELECTION_FLAGS), selectionFlags)
        bundle.putInt(keyForField(FIELD_ROLE_FLAGS), roleFlags)
        bundle.putInt(keyForField(FIELD_AVERAGE_BITRATE), averageBitrate)
        bundle.putInt(keyForField(FIELD_PEAK_BITRATE), peakBitrate)
        bundle.putString(keyForField(FIELD_CODECS), codecs)
        if (!excludeMetadata) {
            // TODO (internal ref: b/239701618)
            bundle.putParcelable(keyForField(FIELD_METADATA), metadata)
        }
        // Container specific.
        bundle.putString(keyForField(FIELD_CONTAINER_MIME_TYPE), containerMimeType)
        // Sample specific.
        bundle.putString(keyForField(FIELD_SAMPLE_MIME_TYPE), sampleMimeType)
        bundle.putInt(keyForField(FIELD_MAX_INPUT_SIZE), maxInputSize)
        for (i in initializationData.indices) {
            bundle.putByteArray(keyForInitializationData(i), initializationData.get(i))
        }
        // DrmInitData doesn't need to be Bundleable as it's only used in the playing process to
        // initialize the decoder.
        bundle.putParcelable(keyForField(FIELD_DRM_INIT_DATA), drmInitData)
        bundle.putLong(keyForField(FIELD_SUBSAMPLE_OFFSET_US), subsampleOffsetUs)
        // Video specific.
        bundle.putInt(keyForField(FIELD_WIDTH), width)
        bundle.putInt(keyForField(FIELD_HEIGHT), height)
        bundle.putFloat(keyForField(FIELD_FRAME_RATE), frameRate)
        bundle.putInt(keyForField(FIELD_ROTATION_DEGREES), rotationDegrees)
        bundle.putFloat(keyForField(FIELD_PIXEL_WIDTH_HEIGHT_RATIO), pixelWidthHeightRatio)
        bundle.putByteArray(keyForField(FIELD_PROJECTION_DATA), projectionData)
        bundle.putInt(keyForField(FIELD_STEREO_MODE), stereoMode)
        if (colorInfo != null) {
            bundle.putBundle(keyForField(FIELD_COLOR_INFO), colorInfo.toBundle())
        }
        // Audio specific.
        bundle.putInt(keyForField(FIELD_CHANNEL_COUNT), channelCount)
        bundle.putInt(keyForField(FIELD_SAMPLE_RATE), sampleRate)
        bundle.putInt(keyForField(FIELD_PCM_ENCODING), pcmEncoding)
        bundle.putInt(keyForField(FIELD_ENCODER_DELAY), encoderDelay)
        bundle.putInt(keyForField(FIELD_ENCODER_PADDING), encoderPadding)
        // Text specific.
        bundle.putInt(keyForField(FIELD_ACCESSIBILITY_CHANNEL), accessibilityChannel)
        // Source specific.
        bundle.putInt(keyForField(FIELD_CRYPTO_TYPE), cryptoType)
        return bundle
    }

    init {
        id = builder.id
        label = builder.label
        language = Util.normalizeLanguageCode(builder.language)
        selectionFlags = builder.selectionFlags
        roleFlags = builder.roleFlags
        averageBitrate = builder.averageBitrate
        peakBitrate = builder.peakBitrate
        bitrate = if (peakBitrate != NO_VALUE) peakBitrate else averageBitrate
        codecs = builder.codecs
        metadata = builder.metadata
        // Container specific.
        containerMimeType = builder.containerMimeType
        // Sample specific.
        sampleMimeType = builder.sampleMimeType
        maxInputSize = builder.maxInputSize
        initializationData = if (builder.initializationData == null) emptyList() else builder.initializationData!!
        drmInitData = builder.drmInitData
        subsampleOffsetUs = builder.subsampleOffsetUs
        // Video specific.
        width = builder.width
        height = builder.height
        frameRate = builder.frameRate
        rotationDegrees = if (builder.rotationDegrees == NO_VALUE) 0 else builder.rotationDegrees
        pixelWidthHeightRatio = if (builder.pixelWidthHeightRatio == NO_VALUE.toFloat()) 1 else builder.pixelWidthHeightRatio
        projectionData = builder.projectionData
        stereoMode = builder.stereoMode
        colorInfo = builder.colorInfo
        // Audio specific.
        channelCount = builder.channelCount
        sampleRate = builder.sampleRate
        pcmEncoding = builder.pcmEncoding
        encoderDelay = if (builder.encoderDelay == NO_VALUE) 0 else builder.encoderDelay
        encoderPadding = if (builder.encoderPadding == NO_VALUE) 0 else builder.encoderPadding
        // Text specific.
        accessibilityChannel = builder.accessibilityChannel
        // Provided by source.
        if (builder.cryptoType == C.CRYPTO_TYPE_NONE && drmInitData != null) {
            // Encrypted content cannot use CRYPTO_TYPE_NONE.
            cryptoType = C.CRYPTO_TYPE_UNSUPPORTED
        } else {
            cryptoType = builder.cryptoType
        }
    }

    companion object {
        /** A value for various fields to indicate that the field's value is unknown or not applicable.  */
        const  val NO_VALUE: Int = -1

        /**
         * A value for [.subsampleOffsetUs] to indicate that subsample timestamps are relative to
         * the timestamps of their parent samples.
         */
        val OFFSET_SAMPLE_RELATIVE: Long = Long.MAX_VALUE
        private val DEFAULT: Format = Builder().build()
        // Video.

        @Deprecated("Use {@link Format.Builder}.")
        fun createVideoSampleFormat(
                id: String?,
                sampleMimeType: String?,
                codecs: String?,
                bitrate: Int,
                maxInputSize: Int,
                width: Int,
                height: Int,
                frameRate: Float,
                initializationData: List<ByteArray>?,
                drmInitData: DrmInitData?): Format {
            return Builder()
                    .setId(id)
                    .setAverageBitrate(bitrate)
                    .setPeakBitrate(bitrate)
                    .setCodecs(codecs)
                    .setSampleMimeType(sampleMimeType)
                    .setMaxInputSize(maxInputSize)
                    .setInitializationData(initializationData)
                    .setDrmInitData(drmInitData)
                    .setWidth(width)
                    .setHeight(height)
                    .setFrameRate(frameRate)
                    .build()
        }

        @Deprecated("Use {@link Format.Builder}.")
        fun createVideoSampleFormat(
                id: String?,
                sampleMimeType: String?,
                codecs: String?,
                bitrate: Int,
                maxInputSize: Int,
                width: Int,
                height: Int,
                frameRate: Float,
                initializationData: List<ByteArray>?,
                rotationDegrees: Int,
                pixelWidthHeightRatio: Float,
                drmInitData: DrmInitData?): Format {
            return Builder()
                    .setId(id)
                    .setAverageBitrate(bitrate)
                    .setPeakBitrate(bitrate)
                    .setCodecs(codecs)
                    .setSampleMimeType(sampleMimeType)
                    .setMaxInputSize(maxInputSize)
                    .setInitializationData(initializationData)
                    .setDrmInitData(drmInitData)
                    .setWidth(width)
                    .setHeight(height)
                    .setFrameRate(frameRate)
                    .setRotationDegrees(rotationDegrees)
                    .setPixelWidthHeightRatio(pixelWidthHeightRatio)
                    .build()
        }
        // Audio.

        @Deprecated("Use {@link Format.Builder}.")
        fun createAudioSampleFormat(
                id: String?,
                sampleMimeType: String?,
                codecs: String?,
                bitrate: Int,
                maxInputSize: Int,
                channelCount: Int,
                sampleRate: Int,
                initializationData: List<ByteArray>?,
                drmInitData: DrmInitData?,
                selectionFlags: @SelectionFlags Int,
                language: String?): Format {
            return Builder()
                    .setId(id)
                    .setLanguage(language)
                    .setSelectionFlags(selectionFlags)
                    .setAverageBitrate(bitrate)
                    .setPeakBitrate(bitrate)
                    .setCodecs(codecs)
                    .setSampleMimeType(sampleMimeType)
                    .setMaxInputSize(maxInputSize)
                    .setInitializationData(initializationData)
                    .setDrmInitData(drmInitData)
                    .setChannelCount(channelCount)
                    .setSampleRate(sampleRate)
                    .build()
        }

        @Deprecated("Use {@link Format.Builder}.")
        fun createAudioSampleFormat(
                id: String?,
                sampleMimeType: String?,
                codecs: String?,
                bitrate: Int,
                maxInputSize: Int,
                channelCount: Int,
                sampleRate: Int,
                pcmEncoding: @PcmEncoding Int,
                initializationData: List<ByteArray>?,
                drmInitData: DrmInitData?,
                selectionFlags: @SelectionFlags Int,
                language: String?): Format {
            return Builder()
                    .setId(id)
                    .setLanguage(language)
                    .setSelectionFlags(selectionFlags)
                    .setAverageBitrate(bitrate)
                    .setPeakBitrate(bitrate)
                    .setCodecs(codecs)
                    .setSampleMimeType(sampleMimeType)
                    .setMaxInputSize(maxInputSize)
                    .setInitializationData(initializationData)
                    .setDrmInitData(drmInitData)
                    .setChannelCount(channelCount)
                    .setSampleRate(sampleRate)
                    .setPcmEncoding(pcmEncoding)
                    .build()
        }
        // Generic.

        @Deprecated("Use {@link Format.Builder}.")
        fun createContainerFormat(
                id: String?,
                label: String?,
                containerMimeType: String?,
                sampleMimeType: String?,
                codecs: String?,
                bitrate: Int,
                selectionFlags: @SelectionFlags Int,
                roleFlags: @RoleFlags Int,
                language: String?): Format {
            return Builder()
                    .setId(id)
                    .setLabel(label)
                    .setLanguage(language)
                    .setSelectionFlags(selectionFlags)
                    .setRoleFlags(roleFlags)
                    .setAverageBitrate(bitrate)
                    .setPeakBitrate(bitrate)
                    .setCodecs(codecs)
                    .setContainerMimeType(containerMimeType)
                    .setSampleMimeType(sampleMimeType)
                    .build()
        }

        @Deprecated("Use {@link Format.Builder}.")
        fun createSampleFormat(id: String?, sampleMimeType: String?): Format {
            return Builder().setId(id).setSampleMimeType(sampleMimeType).build()
        }
        // Utility methods
        /** Returns a prettier [String] than [.toString], intended for logging.  */
        fun toLogString(format: Format?): String {
            if (format == null) {
                return "null"
            }
            val builder: StringBuilder = StringBuilder()
            builder.append("id=").append(format.id).append(", mimeType=").append(format.sampleMimeType)
            if (format.bitrate != NO_VALUE) {
                builder.append(", bitrate=").append(format.bitrate)
            }
            if (format.codecs != null) {
                builder.append(", codecs=").append(format.codecs)
            }
            if (format.drmInitData != null) {
                val schemes: MutableSet<String> = LinkedHashSet()
                for (i in 0 until format.drmInitData.schemeDataCount) {
                    val schemeUuid: UUID? = format.drmInitData.get(i).uuid
                    if ((schemeUuid == C.COMMON_PSSH_UUID)) {
                        schemes.add("cenc")
                    } else if ((schemeUuid == C.CLEARKEY_UUID)) {
                        schemes.add("clearkey")
                    } else if ((schemeUuid == C.PLAYREADY_UUID)) {
                        schemes.add("playready")
                    } else if ((schemeUuid == C.WIDEVINE_UUID)) {
                        schemes.add("widevine")
                    } else if ((schemeUuid == C.UUID_NIL)) {
                        schemes.add("universal")
                    } else {
                        schemes.add("unknown (" + schemeUuid + ")")
                    }
                }
                builder.append(", drm=[")
                Joiner.on(',').appendTo(builder, schemes)
                builder.append(']')
            }
            if (format.width != NO_VALUE && format.height != NO_VALUE) {
                builder.append(", res=").append(format.width).append("x").append(format.height)
            }
            if (format.frameRate != NO_VALUE.toFloat()) {
                builder.append(", fps=").append(format.frameRate)
            }
            if (format.channelCount != NO_VALUE) {
                builder.append(", channels=").append(format.channelCount)
            }
            if (format.sampleRate != NO_VALUE) {
                builder.append(", sample_rate=").append(format.sampleRate)
            }
            if (format.language != null) {
                builder.append(", language=").append(format.language)
            }
            if (format.label != null) {
                builder.append(", label=").append(format.label)
            }
            if (format.selectionFlags != 0) {
                val selectionFlags: MutableList<String> = ArrayList()
                // LINT.IfChange(selection_flags)
                if ((format.selectionFlags and C.SELECTION_FLAG_AUTOSELECT) != 0) {
                    selectionFlags.add("auto")
                }
                if ((format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0) {
                    selectionFlags.add("default")
                }
                if ((format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) {
                    selectionFlags.add("forced")
                }
                builder.append(", selectionFlags=[")
                Joiner.on(',').appendTo(builder, selectionFlags)
                builder.append("]")
            }
            if (format.roleFlags != 0) {
                // LINT.IfChange(role_flags)
                val roleFlags: MutableList<String> = ArrayList()
                if ((format.roleFlags and C.ROLE_FLAG_MAIN) != 0) {
                    roleFlags.add("main")
                }
                if ((format.roleFlags and C.ROLE_FLAG_ALTERNATE) != 0) {
                    roleFlags.add("alt")
                }
                if ((format.roleFlags and C.ROLE_FLAG_SUPPLEMENTARY) != 0) {
                    roleFlags.add("supplementary")
                }
                if ((format.roleFlags and C.ROLE_FLAG_COMMENTARY) != 0) {
                    roleFlags.add("commentary")
                }
                if ((format.roleFlags and C.ROLE_FLAG_DUB) != 0) {
                    roleFlags.add("dub")
                }
                if ((format.roleFlags and C.ROLE_FLAG_EMERGENCY) != 0) {
                    roleFlags.add("emergency")
                }
                if ((format.roleFlags and C.ROLE_FLAG_CAPTION) != 0) {
                    roleFlags.add("caption")
                }
                if ((format.roleFlags and C.ROLE_FLAG_SUBTITLE) != 0) {
                    roleFlags.add("subtitle")
                }
                if ((format.roleFlags and C.ROLE_FLAG_SIGN) != 0) {
                    roleFlags.add("sign")
                }
                if ((format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO) != 0) {
                    roleFlags.add("describes-video")
                }
                if ((format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0) {
                    roleFlags.add("describes-music")
                }
                if ((format.roleFlags and C.ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY) != 0) {
                    roleFlags.add("enhanced-intelligibility")
                }
                if ((format.roleFlags and C.ROLE_FLAG_TRANSCRIBES_DIALOG) != 0) {
                    roleFlags.add("transcribes-dialog")
                }
                if ((format.roleFlags and C.ROLE_FLAG_EASY_TO_READ) != 0) {
                    roleFlags.add("easy-read")
                }
                if ((format.roleFlags and C.ROLE_FLAG_TRICK_PLAY) != 0) {
                    roleFlags.add("trick-play")
                }
                builder.append(", roleFlags=[")
                Joiner.on(',').appendTo(builder, roleFlags)
                builder.append("]")
            }
            return builder.toString()
        }

        private val FIELD_ID: Int = 0
        private val FIELD_LABEL: Int = 1
        private val FIELD_LANGUAGE: Int = 2
        private val FIELD_SELECTION_FLAGS: Int = 3
        private val FIELD_ROLE_FLAGS: Int = 4
        private val FIELD_AVERAGE_BITRATE: Int = 5
        private val FIELD_PEAK_BITRATE: Int = 6
        private val FIELD_CODECS: Int = 7
        private val FIELD_METADATA: Int = 8
        private val FIELD_CONTAINER_MIME_TYPE: Int = 9
        private val FIELD_SAMPLE_MIME_TYPE: Int = 10
        private val FIELD_MAX_INPUT_SIZE: Int = 11
        private val FIELD_INITIALIZATION_DATA: Int = 12
        private val FIELD_DRM_INIT_DATA: Int = 13
        private val FIELD_SUBSAMPLE_OFFSET_US: Int = 14
        private val FIELD_WIDTH: Int = 15
        private val FIELD_HEIGHT: Int = 16
        private val FIELD_FRAME_RATE: Int = 17
        private val FIELD_ROTATION_DEGREES: Int = 18
        private val FIELD_PIXEL_WIDTH_HEIGHT_RATIO: Int = 19
        private val FIELD_PROJECTION_DATA: Int = 20
        private val FIELD_STEREO_MODE: Int = 21
        private val FIELD_COLOR_INFO: Int = 22
        private val FIELD_CHANNEL_COUNT: Int = 23
        private val FIELD_SAMPLE_RATE: Int = 24
        private val FIELD_PCM_ENCODING: Int = 25
        private val FIELD_ENCODER_DELAY: Int = 26
        private val FIELD_ENCODER_PADDING: Int = 27
        private val FIELD_ACCESSIBILITY_CHANNEL: Int = 28
        private val FIELD_CRYPTO_TYPE: Int = 29

        /** Object that can restore `Format` from a [Bundle].  */
        val CREATOR: Bundleable.Creator<Format> = Bundleable.Creator({ bundle: Bundle -> fromBundle(bundle) })
        private fun fromBundle(bundle: Bundle): Format {
            val builder: Builder = Builder()
            BundleableUtil.ensureClassLoader(bundle)
            builder
                    .setId(defaultIfNull<String?>(bundle.getString(keyForField(FIELD_ID)), DEFAULT.id))
                    .setLabel(defaultIfNull<String?>(bundle.getString(keyForField(FIELD_LABEL)), DEFAULT.label))
                    .setLanguage(defaultIfNull<String?>(bundle.getString(keyForField(FIELD_LANGUAGE)), DEFAULT.language))
                    .setSelectionFlags(
                            bundle.getInt(keyForField(FIELD_SELECTION_FLAGS), DEFAULT.selectionFlags))
                    .setRoleFlags(bundle.getInt(keyForField(FIELD_ROLE_FLAGS), DEFAULT.roleFlags))
                    .setAverageBitrate(
                            bundle.getInt(keyForField(FIELD_AVERAGE_BITRATE), DEFAULT.averageBitrate))
                    .setPeakBitrate(bundle.getInt(keyForField(FIELD_PEAK_BITRATE), DEFAULT.peakBitrate))
                    .setCodecs(defaultIfNull<String?>(bundle.getString(keyForField(FIELD_CODECS)), DEFAULT.codecs))
                    .setMetadata(
                            defaultIfNull<Metadata?>(bundle.getParcelable(keyForField(FIELD_METADATA)), DEFAULT.metadata)) // Container specific.
                    .setContainerMimeType(
                            defaultIfNull<String?>(
                                    bundle.getString(keyForField(FIELD_CONTAINER_MIME_TYPE)),
                                    DEFAULT.containerMimeType)) // Sample specific.
                    .setSampleMimeType(
                            defaultIfNull<String?>(
                                    bundle.getString(keyForField(FIELD_SAMPLE_MIME_TYPE)), DEFAULT.sampleMimeType))
                    .setMaxInputSize(bundle.getInt(keyForField(FIELD_MAX_INPUT_SIZE), DEFAULT.maxInputSize))
            val initializationData: MutableList<ByteArray> = ArrayList()
            var i: Int = 0
            while (true) {
                val data: ByteArray? = bundle.getByteArray(keyForInitializationData(i))
                if (data == null) {
                    break
                }
                initializationData.add(data)
                i++
            }
            builder
                    .setInitializationData(initializationData)
                    .setDrmInitData(bundle.getParcelable(keyForField(FIELD_DRM_INIT_DATA)))
                    .setSubsampleOffsetUs(
                            bundle.getLong(keyForField(FIELD_SUBSAMPLE_OFFSET_US), DEFAULT.subsampleOffsetUs)) // Video specific.
                    .setWidth(bundle.getInt(keyForField(FIELD_WIDTH), DEFAULT.width))
                    .setHeight(bundle.getInt(keyForField(FIELD_HEIGHT), DEFAULT.height))
                    .setFrameRate(bundle.getFloat(keyForField(FIELD_FRAME_RATE), DEFAULT.frameRate))
                    .setRotationDegrees(
                            bundle.getInt(keyForField(FIELD_ROTATION_DEGREES), DEFAULT.rotationDegrees))
                    .setPixelWidthHeightRatio(
                            bundle.getFloat(
                                    keyForField(FIELD_PIXEL_WIDTH_HEIGHT_RATIO), DEFAULT.pixelWidthHeightRatio))
                    .setProjectionData(bundle.getByteArray(keyForField(FIELD_PROJECTION_DATA)))
                    .setStereoMode(bundle.getInt(keyForField(FIELD_STEREO_MODE), DEFAULT.stereoMode))
            val colorInfoBundle: Bundle? = bundle.getBundle(keyForField(FIELD_COLOR_INFO))
            if (colorInfoBundle != null) {
                builder.setColorInfo(ColorInfo.Companion.CREATOR.fromBundle(colorInfoBundle))
            }
            // Audio specific.
            builder
                    .setChannelCount(bundle.getInt(keyForField(FIELD_CHANNEL_COUNT), DEFAULT.channelCount))
                    .setSampleRate(bundle.getInt(keyForField(FIELD_SAMPLE_RATE), DEFAULT.sampleRate))
                    .setPcmEncoding(bundle.getInt(keyForField(FIELD_PCM_ENCODING), DEFAULT.pcmEncoding))
                    .setEncoderDelay(bundle.getInt(keyForField(FIELD_ENCODER_DELAY), DEFAULT.encoderDelay))
                    .setEncoderPadding(
                            bundle.getInt(keyForField(FIELD_ENCODER_PADDING), DEFAULT.encoderPadding)) // Text specific.
                    .setAccessibilityChannel(
                            bundle.getInt(keyForField(FIELD_ACCESSIBILITY_CHANNEL), DEFAULT.accessibilityChannel)) // Source specific.
                    .setCryptoType(bundle.getInt(keyForField(FIELD_CRYPTO_TYPE), DEFAULT.cryptoType))
            return builder.build()
        }

        private fun keyForField(field: @FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }

        private fun keyForInitializationData(initialisationDataIndex: Int): String {
            return (keyForField(FIELD_INITIALIZATION_DATA)
                    + "_"
                    + Integer.toString(initialisationDataIndex, Character.MAX_RADIX))
        }

        private fun <T> defaultIfNull(value: T?, defaultValue: T?): T? {
            return if (value != null) value else defaultValue
        }
    }
}