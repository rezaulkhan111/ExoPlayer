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
package com.google.android.exoplayer2.util

import android.annotation.SuppressLintimport

android.media.*import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.PcmEncodingimport

com.google.android.exoplayer2.video.ColorInfoimport java.nio.ByteBuffer
/** Helper class containing utility methods for managing [MediaFormat] instances.  */
object MediaFormatUtil {
    /**
     * Custom [MediaFormat] key associated with a float representing the ratio between a pixel's
     * width and height.
     */
    // The constant value must not be changed, because it's also set by the framework MediaParser API.
    val KEY_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT: String = "exo-pixel-width-height-ratio-float"

    /**
     * Custom [MediaFormat] key associated with an integer representing the PCM encoding.
     *
     *
     * Equivalent to [MediaFormat.KEY_PCM_ENCODING], except it allows additional values
     * defined by [C.PcmEncoding], including [C.ENCODING_PCM_16BIT_BIG_ENDIAN], [ ][C.ENCODING_PCM_24BIT], and [C.ENCODING_PCM_32BIT].
     */
    // The constant value must not be changed, because it's also set by the framework MediaParser API.
    val KEY_PCM_ENCODING_EXTENDED: String = "exo-pcm-encoding-int"

    /**
     * The [MediaFormat] key for the maximum bitrate in bits per second.
     *
     *
     * The associated value is an integer.
     *
     *
     * The key string constant is the same as `MediaFormat#KEY_MAX_BITRATE`. Values for it
     * are already returned by the framework MediaExtractor; the key is a hidden field in `MediaFormat` though, which is why it's being replicated here.
     */
    // The constant value must not be changed, because it's also set by the framework MediaParser and
    // MediaExtractor APIs.
    val KEY_MAX_BIT_RATE: String = "max-bitrate"
    private val MAX_POWER_OF_TWO_INT: Int = 1 shl 30

    /**
     * Returns a [MediaFormat] representing the given ExoPlayer [Format].
     *
     *
     * May include the following custom keys:
     *
     *
     *  * [.KEY_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT].
     *  * [.KEY_PCM_ENCODING_EXTENDED].
     *
     */
    @SuppressLint("InlinedApi") // Inlined MediaFormat keys.
    fun createMediaFormatFromFormat(format: Format): MediaFormat {
        val result: MediaFormat = MediaFormat()
        maybeSetInteger(result, MediaFormat.KEY_BIT_RATE, format.bitrate)
        maybeSetInteger(result, KEY_MAX_BIT_RATE, format.peakBitrate)
        maybeSetInteger(result, MediaFormat.KEY_CHANNEL_COUNT, format.channelCount)
        maybeSetColorInfo(result, format.colorInfo)
        maybeSetString(result, MediaFormat.KEY_MIME, format.sampleMimeType)
        maybeSetString(result, MediaFormat.KEY_CODECS_STRING, format.codecs)
        maybeSetFloat(result, MediaFormat.KEY_FRAME_RATE, format.frameRate)
        maybeSetInteger(result, MediaFormat.KEY_WIDTH, format.width)
        maybeSetInteger(result, MediaFormat.KEY_HEIGHT, format.height)
        setCsdBuffers(result, format.initializationData)
        maybeSetPcmEncoding(result, format.pcmEncoding)
        maybeSetString(result, MediaFormat.KEY_LANGUAGE, format.language)
        maybeSetInteger(result, MediaFormat.KEY_MAX_INPUT_SIZE, format.maxInputSize)
        maybeSetInteger(result, MediaFormat.KEY_SAMPLE_RATE, format.sampleRate)
        maybeSetInteger(result, MediaFormat.KEY_CAPTION_SERVICE_NUMBER, format.accessibilityChannel)
        result.setInteger(MediaFormat.KEY_ROTATION, format.rotationDegrees)
        val selectionFlags: Int = format.selectionFlags
        setBooleanAsInt(
                result, MediaFormat.KEY_IS_AUTOSELECT, selectionFlags and C.SELECTION_FLAG_AUTOSELECT)
        setBooleanAsInt(result, MediaFormat.KEY_IS_DEFAULT, selectionFlags and C.SELECTION_FLAG_DEFAULT)
        setBooleanAsInt(
                result, MediaFormat.KEY_IS_FORCED_SUBTITLE, selectionFlags and C.SELECTION_FLAG_FORCED)
        result.setInteger(MediaFormat.KEY_ENCODER_DELAY, format.encoderDelay)
        result.setInteger(MediaFormat.KEY_ENCODER_PADDING, format.encoderPadding)
        maybeSetPixelAspectRatio(result, format.pixelWidthHeightRatio)
        return result
    }

    /**
     * Sets a [MediaFormat] [String] value. Does nothing if `value` is null.
     *
     * @param format The [MediaFormat] being configured.
     * @param key The key to set.
     * @param value The value to set.
     */
    fun maybeSetString(format: MediaFormat, key: String?, value: String?) {
        if (value != null) {
            format.setString((key)!!, value)
        }
    }

    /**
     * Sets a [MediaFormat]'s codec specific data buffers.
     *
     * @param format The [MediaFormat] being configured.
     * @param csdBuffers The csd buffers to set.
     */
    fun setCsdBuffers(format: MediaFormat, csdBuffers: List<ByteArray?>?) {
        for (i in csdBuffers!!.indices) {
            format.setByteBuffer("csd-" + i, ByteBuffer.wrap(csdBuffers.get(i)))
        }
    }

    /**
     * Sets a [MediaFormat] integer value. Does nothing if `value` is [ ][Format.NO_VALUE].
     *
     * @param format The [MediaFormat] being configured.
     * @param key The key to set.
     * @param value The value to set.
     */
    fun maybeSetInteger(format: MediaFormat, key: String?, value: Int) {
        if (value != Format.Companion.NO_VALUE) {
            format.setInteger((key)!!, value)
        }
    }

    /**
     * Sets a [MediaFormat] float value. Does nothing if `value` is [ ][Format.NO_VALUE].
     *
     * @param format The [MediaFormat] being configured.
     * @param key The key to set.
     * @param value The value to set.
     */
    fun maybeSetFloat(format: MediaFormat, key: String?, value: Float) {
        if (value != Format.Companion.NO_VALUE.toFloat()) {
            format.setFloat((key)!!, value)
        }
    }

    /**
     * Sets a [MediaFormat] [ByteBuffer] value. Does nothing if `value` is null.
     *
     * @param format The [MediaFormat] being configured.
     * @param key The key to set.
     * @param value The byte array that will be wrapped to obtain the value.
     */
    fun maybeSetByteBuffer(format: MediaFormat, key: String?, value: ByteArray?) {
        if (value != null) {
            format.setByteBuffer((key)!!, ByteBuffer.wrap(value))
        }
    }

    /**
     * Sets a [MediaFormat]'s color information. Does nothing if `colorInfo` is null.
     *
     * @param format The [MediaFormat] being configured.
     * @param colorInfo The color info to set.
     */
    fun maybeSetColorInfo(format: MediaFormat, colorInfo: ColorInfo?) {
        if (colorInfo != null) {
            maybeSetInteger(format, MediaFormat.KEY_COLOR_TRANSFER, colorInfo.colorTransfer)
            maybeSetInteger(format, MediaFormat.KEY_COLOR_STANDARD, colorInfo.colorSpace)
            maybeSetInteger(format, MediaFormat.KEY_COLOR_RANGE, colorInfo.colorRange)
            maybeSetByteBuffer(format, MediaFormat.KEY_HDR_STATIC_INFO, colorInfo.hdrStaticInfo)
        }
    }

    /**
     * Creates and returns a `ColorInfo`, if a valid instance is described in the [ ].
     */
    fun getColorInfo(mediaFormat: MediaFormat): ColorInfo? {
        if (Util.SDK_INT < 29) {
            return null
        }
        var colorSpace: Int = mediaFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD,  /* defaultValue= */Format.Companion.NO_VALUE)
        var colorRange: Int = mediaFormat.getInteger(MediaFormat.KEY_COLOR_RANGE,  /* defaultValue= */Format.Companion.NO_VALUE)
        var colorTransfer: Int = mediaFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER,  /* defaultValue= */Format.Companion.NO_VALUE)
        val hdrStaticInfoByteBuffer: ByteBuffer? = mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO)
        val hdrStaticInfo: ByteArray? = if (hdrStaticInfoByteBuffer != null) getArray(hdrStaticInfoByteBuffer) else null
        // Some devices may produce invalid values from MediaFormat#getInteger.
        // See b/239435670 for more information.
        if (!isValidColorSpace(colorSpace)) {
            colorSpace = Format.Companion.NO_VALUE
        }
        if (!isValidColorRange(colorRange)) {
            colorRange = Format.Companion.NO_VALUE
        }
        if (!isValidColorTransfer(colorTransfer)) {
            colorTransfer = Format.Companion.NO_VALUE
        }
        if ((colorSpace != Format.Companion.NO_VALUE
                        ) || (colorRange != Format.Companion.NO_VALUE
                        ) || (colorTransfer != Format.Companion.NO_VALUE
                        ) || (hdrStaticInfo != null)) {
            return ColorInfo(colorSpace, colorRange, colorTransfer, hdrStaticInfo)
        }
        return null
    }

    fun getArray(byteBuffer: ByteBuffer): ByteArray {
        val array: ByteArray = ByteArray(byteBuffer.remaining())
        byteBuffer.get(array)
        return array
    }

    // Internal methods.
    private fun setBooleanAsInt(format: MediaFormat, key: String, value: Int) {
        format.setInteger(key, if (value != 0) 1 else 0)
    }

    // Inlined MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH and MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT.
    @SuppressLint("InlinedApi")
    private fun maybeSetPixelAspectRatio(
            mediaFormat: MediaFormat, pixelWidthHeightRatio: Float) {
        mediaFormat.setFloat(KEY_PIXEL_WIDTH_HEIGHT_RATIO_FLOAT, pixelWidthHeightRatio)
        var pixelAspectRatioWidth: Int = 1
        var pixelAspectRatioHeight: Int = 1
        // ExoPlayer extractors output the pixel aspect ratio as a float. Do our best to recreate the
        // pixel aspect ratio width and height by using a large power of two factor.
        if (pixelWidthHeightRatio < 1.0f) {
            pixelAspectRatioHeight = MAX_POWER_OF_TWO_INT
            pixelAspectRatioWidth = (pixelWidthHeightRatio * pixelAspectRatioHeight).toInt()
        } else if (pixelWidthHeightRatio > 1.0f) {
            pixelAspectRatioWidth = MAX_POWER_OF_TWO_INT
            pixelAspectRatioHeight = (pixelAspectRatioWidth / pixelWidthHeightRatio).toInt()
        }
        mediaFormat.setInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH, pixelAspectRatioWidth)
        mediaFormat.setInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT, pixelAspectRatioHeight)
    }

    @SuppressLint("InlinedApi") // Inlined KEY_PCM_ENCODING.
    private fun maybeSetPcmEncoding(
            mediaFormat: MediaFormat, exoPcmEncoding: @PcmEncoding Int) {
        if (exoPcmEncoding == Format.Companion.NO_VALUE) {
            return
        }
        val mediaFormatPcmEncoding: Int
        maybeSetInteger(mediaFormat, KEY_PCM_ENCODING_EXTENDED, exoPcmEncoding)
        when (exoPcmEncoding) {
            C.ENCODING_PCM_8BIT -> mediaFormatPcmEncoding = AudioFormat.ENCODING_PCM_8BIT
            C.ENCODING_PCM_16BIT -> mediaFormatPcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            C.ENCODING_PCM_FLOAT -> mediaFormatPcmEncoding = AudioFormat.ENCODING_PCM_FLOAT
            C.ENCODING_PCM_24BIT -> mediaFormatPcmEncoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
            C.ENCODING_PCM_32BIT -> mediaFormatPcmEncoding = AudioFormat.ENCODING_PCM_32BIT
            C.ENCODING_INVALID -> mediaFormatPcmEncoding = AudioFormat.ENCODING_INVALID
            Format.Companion.NO_VALUE, C.ENCODING_PCM_16BIT_BIG_ENDIAN ->         // No matching value. Do nothing.
                return
            else -> return
        }
        mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, mediaFormatPcmEncoding)
    }

    /** Whether this is a valid [C.ColorSpace] instance.  */
    private fun isValidColorSpace(colorSpace: Int): Boolean {
        // LINT.IfChange(color_space)
        return (colorSpace == C.COLOR_SPACE_BT601
                ) || (colorSpace == C.COLOR_SPACE_BT709
                ) || (colorSpace == C.COLOR_SPACE_BT2020
                ) || (colorSpace == Format.Companion.NO_VALUE)
    }

    /** Whether this is a valid [C.ColorRange] instance.  */
    private fun isValidColorRange(colorRange: Int): Boolean {
        // LINT.IfChange(color_range)
        return (colorRange == C.COLOR_RANGE_LIMITED
                ) || (colorRange == C.COLOR_RANGE_FULL
                ) || (colorRange == Format.Companion.NO_VALUE)
    }

    /** Whether this is a valid [C.ColorTransfer] instance.  */
    private fun isValidColorTransfer(colorTransfer: Int): Boolean {
        // LINT.IfChange(color_transfer)
        return (colorTransfer == C.COLOR_TRANSFER_SDR
                ) || (colorTransfer == C.COLOR_TRANSFER_ST2084
                ) || (colorTransfer == C.COLOR_TRANSFER_HLG
                ) || (colorTransfer == Format.Companion.NO_VALUE)
    }
}