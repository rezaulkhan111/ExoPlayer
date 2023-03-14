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
package com.google.android.exoplayer2.util

import android.util.Pair
import com.google.android.exoplayer2.C

/** Provides utilities for handling various types of codec-specific data.  */
object CodecSpecificDataUtil {
    private val NAL_START_CODE: ByteArray = byteArrayOf(0, 0, 0, 1)
    private val HEVC_GENERAL_PROFILE_SPACE_STRINGS: Array<String> = arrayOf("", "A", "B", "C")

    // MP4V-ES
    private val VISUAL_OBJECT_LAYER: Int = 1
    private val VISUAL_OBJECT_LAYER_START: Int = 0x20
    private val EXTENDED_PAR: Int = 0x0F
    private val RECTANGULAR: Int = 0x00

    /**
     * Parses an ALAC AudioSpecificConfig (i.e. an [ALACSpecificConfig](https://github.com/macosforge/alac/blob/master/ALACMagicCookieDescription.txt)).
     *
     * @param audioSpecificConfig A byte array containing the AudioSpecificConfig to parse.
     * @return A pair consisting of the sample rate in Hz and the channel count.
     */
    fun parseAlacAudioSpecificConfig(audioSpecificConfig: ByteArray): Pair<Int, Int> {
        val byteArray: ParsableByteArray = ParsableByteArray(audioSpecificConfig)
        byteArray.setPosition(9)
        val channelCount: Int = byteArray.readUnsignedByte()
        byteArray.setPosition(20)
        val sampleRate: Int = byteArray.readUnsignedIntToInt()
        return Pair.create(sampleRate, channelCount)
    }

    /**
     * Returns initialization data for formats with MIME type [MimeTypes.APPLICATION_CEA708].
     *
     * @param isWideAspectRatio Whether the CEA-708 closed caption service is formatted for displays
     * with 16:9 aspect ratio.
     * @return Initialization data for formats with MIME type [MimeTypes.APPLICATION_CEA708].
     */
    fun buildCea708InitializationData(isWideAspectRatio: Boolean): List<ByteArray> {
        return listOf(if (isWideAspectRatio) byteArrayOf(1) else byteArrayOf(0))
    }

    /**
     * Returns whether the CEA-708 closed caption service with the given initialization data is
     * formatted for displays with 16:9 aspect ratio.
     *
     * @param initializationData The initialization data to parse.
     * @return Whether the CEA-708 closed caption service is formatted for displays with 16:9 aspect
     * ratio.
     */
    fun parseCea708InitializationData(initializationData: List<ByteArray>): Boolean {
        return (initializationData.size == 1
                ) && (initializationData.get(0).size == 1
                ) && (initializationData.get(0).get(0).toInt() == 1)
    }

    /**
     * Parses an MPEG-4 Visual configuration information, as defined in ISO/IEC14496-2.
     *
     * @param videoSpecificConfig A byte array containing the MPEG-4 Visual configuration information
     * to parse.
     * @return A pair of the video's width and height.
     */
    fun getVideoResolutionFromMpeg4VideoConfig(
            videoSpecificConfig: ByteArray): Pair<Int, Int> {
        var offset: Int = 0
        var foundVOL: Boolean = false
        val scratchBytes: ParsableByteArray = ParsableByteArray(videoSpecificConfig)
        while (offset + 3 < videoSpecificConfig.size) {
            if ((scratchBytes.readUnsignedInt24() != VISUAL_OBJECT_LAYER
                            || (videoSpecificConfig.get(offset + 3).toInt() and 0xF0) != VISUAL_OBJECT_LAYER_START)) {
                scratchBytes.setPosition(scratchBytes.getPosition() - 2)
                offset++
                continue
            }
            foundVOL = true
            break
        }
        Assertions.checkArgument(foundVOL, "Invalid input: VOL not found.")
        val scratchBits: ParsableBitArray = ParsableBitArray(videoSpecificConfig)
        // Skip the start codecs from the bitstream
        scratchBits.skipBits((offset + 4) * 8)
        scratchBits.skipBits(1) // random_accessible_vol
        scratchBits.skipBits(8) // video_object_type_indication
        if (scratchBits.readBit()) { // object_layer_identifier
            scratchBits.skipBits(4) // video_object_layer_verid
            scratchBits.skipBits(3) // video_object_layer_priority
        }
        val aspectRatioInfo: Int = scratchBits.readBits(4)
        if (aspectRatioInfo == EXTENDED_PAR) {
            scratchBits.skipBits(8) // par_width
            scratchBits.skipBits(8) // par_height
        }
        if (scratchBits.readBit()) { // vol_control_parameters
            scratchBits.skipBits(2) // chroma_format
            scratchBits.skipBits(1) // low_delay
            if (scratchBits.readBit()) { // vbv_parameters
                scratchBits.skipBits(79)
            }
        }
        val videoObjectLayerShape: Int = scratchBits.readBits(2)
        Assertions.checkArgument(
                videoObjectLayerShape == RECTANGULAR,
                "Only supports rectangular video object layer shape.")
        Assertions.checkArgument(scratchBits.readBit()) // marker_bit
        var vopTimeIncrementResolution: Int = scratchBits.readBits(16)
        Assertions.checkArgument(scratchBits.readBit()) // marker_bit
        if (scratchBits.readBit()) { // fixed_vop_rate
            Assertions.checkArgument(vopTimeIncrementResolution > 0)
            vopTimeIncrementResolution--
            var numBitsToSkip: Int = 0
            while (vopTimeIncrementResolution > 0) {
                numBitsToSkip++
                vopTimeIncrementResolution = vopTimeIncrementResolution shr 1
            }
            scratchBits.skipBits(numBitsToSkip) // fixed_vop_time_increment
        }
        Assertions.checkArgument(scratchBits.readBit()) // marker_bit
        val videoObjectLayerWidth: Int = scratchBits.readBits(13)
        Assertions.checkArgument(scratchBits.readBit()) // marker_bit
        val videoObjectLayerHeight: Int = scratchBits.readBits(13)
        Assertions.checkArgument(scratchBits.readBit()) // marker_bit
        scratchBits.skipBits(1) // interlaced
        return Pair.create(videoObjectLayerWidth, videoObjectLayerHeight)
    }

    /**
     * Builds an RFC 6381 AVC codec string using the provided parameters.
     *
     * @param profileIdc The encoding profile.
     * @param constraintsFlagsAndReservedZero2Bits The constraint flags followed by the reserved zero
     * 2 bits, all contained in the least significant byte of the integer.
     * @param levelIdc The encoding level.
     * @return An RFC 6381 AVC codec string built using the provided parameters.
     */
    fun buildAvcCodecString(
            profileIdc: Int, constraintsFlagsAndReservedZero2Bits: Int, levelIdc: Int): String {
        return String.format(
                "avc1.%02X%02X%02X", profileIdc, constraintsFlagsAndReservedZero2Bits, levelIdc)
    }

    /** Builds an RFC 6381 HEVC codec string using the provided parameters.  */
    fun buildHevcCodecString(
            generalProfileSpace: Int,
            generalTierFlag: Boolean,
            generalProfileIdc: Int,
            generalProfileCompatibilityFlags: Int,
            constraintBytes: IntArray,
            generalLevelIdc: Int): String {
        val builder: StringBuilder = StringBuilder(
                Util.formatInvariant(
                        "hvc1.%s%d.%X.%c%d",
                        HEVC_GENERAL_PROFILE_SPACE_STRINGS.get(generalProfileSpace),
                        generalProfileIdc,
                        generalProfileCompatibilityFlags,
                        if (generalTierFlag) 'H' else 'L',
                        generalLevelIdc))
        // Omit trailing zero bytes.
        var trailingZeroIndex: Int = constraintBytes.size
        while (trailingZeroIndex > 0 && constraintBytes.get(trailingZeroIndex - 1) == 0) {
            trailingZeroIndex--
        }
        for (i in 0 until trailingZeroIndex) {
            builder.append(String.format(".%02X", constraintBytes.get(i)))
        }
        return builder.toString()
    }

    /**
     * Constructs a NAL unit consisting of the NAL start code followed by the specified data.
     *
     * @param data An array containing the data that should follow the NAL start code.
     * @param offset The start offset into `data`.
     * @param length The number of bytes to copy from `data`
     * @return The constructed NAL unit.
     */
    fun buildNalUnit(data: ByteArray?, offset: Int, length: Int): ByteArray {
        val nalUnit: ByteArray = ByteArray(length + NAL_START_CODE.size)
        System.arraycopy(NAL_START_CODE, 0, nalUnit, 0, NAL_START_CODE.size)
        System.arraycopy(data, offset, nalUnit, NAL_START_CODE.size, length)
        return nalUnit
    }

    /**
     * Splits an array of NAL units.
     *
     *
     * If the input consists of NAL start code delimited units, then the returned array consists of
     * the split NAL units, each of which is still prefixed with the NAL start code. For any other
     * input, null is returned.
     *
     * @param data An array of data.
     * @return The individual NAL units, or null if the input did not consist of NAL start code
     * delimited units.
     */
    fun splitNalUnits(data: ByteArray): Array<ByteArray?>? {
        if (!isNalStartCode(data, 0)) {
            // data does not consist of NAL start code delimited units.
            return null
        }
        val starts: MutableList<Int> = ArrayList()
        var nalUnitIndex: Int = 0
        do {
            starts.add(nalUnitIndex)
            nalUnitIndex = findNalStartCode(data, nalUnitIndex + NAL_START_CODE.size)
        } while (nalUnitIndex != C.INDEX_UNSET)
        val split: Array<ByteArray?> = arrayOfNulls(starts.size)
        for (i in starts.indices) {
            val startIndex: Int = starts.get(i)
            val endIndex: Int = if (i < starts.size - 1) starts.get(i + 1) else data.size
            val nal: ByteArray = ByteArray(endIndex - startIndex)
            System.arraycopy(data, startIndex, nal, 0, nal.size)
            split.get(i) = nal
        }
        return split
    }

    /**
     * Finds the next occurrence of the NAL start code from a given index.
     *
     * @param data The data in which to search.
     * @param index The first index to test.
     * @return The index of the first byte of the found start code, or [C.INDEX_UNSET].
     */
    private fun findNalStartCode(data: ByteArray, index: Int): Int {
        val endIndex: Int = data.size - NAL_START_CODE.size
        for (i in index..endIndex) {
            if (isNalStartCode(data, i)) {
                return i
            }
        }
        return C.INDEX_UNSET
    }

    /**
     * Tests whether there exists a NAL start code at a given index.
     *
     * @param data The data.
     * @param index The index to test.
     * @return Whether there exists a start code that begins at `index`.
     */
    private fun isNalStartCode(data: ByteArray, index: Int): Boolean {
        if (data.size - index <= NAL_START_CODE.size) {
            return false
        }
        for (j in NAL_START_CODE.indices) {
            if (data.get(index + j) != NAL_START_CODE.get(j)) {
                return false
            }
        }
        return true
    }
}