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
package com.google.android.exoplayer2.video

import android.os.Bundle
import androidx.annotation.IntDef
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.ColorRange
import com.google.android.exoplayer2.C.ColorTransfer
import org.checkerframework.dataflow.qual.Pure
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/**
 * Stores color info.
 *
 *
 * When a `null` `ColorInfo` instance is used, this often represents a generic [ ][.SDR_BT709_LIMITED] instance.
 */
class ColorInfo : Bundleable {
    /**
     * The color space of the video. Valid values are [C.COLOR_SPACE_BT601], [ ][C.COLOR_SPACE_BT709], [C.COLOR_SPACE_BT2020] or [Format.NO_VALUE] if unknown.
     */
    @C.ColorSpace
    var colorSpace = 0

    /**
     * The color range of the video. Valid values are [C.COLOR_RANGE_LIMITED], [ ][C.COLOR_RANGE_FULL] or [Format.NO_VALUE] if unknown.
     */
    @ColorRange
    var colorRange = 0

    /**
     * The color transfer characteristics of the video. Valid values are [C.COLOR_TRANSFER_HLG],
     * [C.COLOR_TRANSFER_ST2084], [C.COLOR_TRANSFER_SDR] or [Format.NO_VALUE] if
     * unknown.
     */
    @ColorTransfer
    var colorTransfer = 0

    /** HdrStaticInfo as defined in CTA-861.3, or null if none specified.  */
    val hdrStaticInfo: ByteArray?

    // Lazily initialized hashcode.
    private var hashCode = 0

    /**
     * Constructs the ColorInfo.
     *
     * @param colorSpace The color space of the video.
     * @param colorRange The color range of the video.
     * @param colorTransfer The color transfer characteristics of the video.
     * @param hdrStaticInfo HdrStaticInfo as defined in CTA-861.3, or null if none specified.
     */
    constructor(@C.ColorSpace colorSpace: Int, @ColorRange colorRange: Int, @ColorTransfer colorTransfer: Int, hdrStaticInfo: ByteArray?) {
        this.colorSpace = colorSpace
        this.colorRange = colorRange
        this.colorTransfer = colorTransfer
        this.hdrStaticInfo = hdrStaticInfo
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as ColorInfo
        return colorSpace == other.colorSpace && colorRange == other.colorRange && colorTransfer == other.colorTransfer && Arrays.equals(hdrStaticInfo, other.hdrStaticInfo)
    }

    override fun toString(): String {
        return ("ColorInfo(" + colorSpace + ", " + colorRange + ", " + colorTransfer + ", " + (hdrStaticInfo != null) + ")")
    }

    override fun hashCode(): Int {
        if (hashCode == 0) {
            var result = 17
            result = 31 * result + colorSpace
            result = 31 * result + colorRange
            result = 31 * result + colorTransfer
            result = 31 * result + Arrays.hashCode(hdrStaticInfo)
            hashCode = result
        }
        return hashCode
    }

    // Bundleable implementation

    // Bundleable implementation
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [FIELD_COLOR_SPACE, FIELD_COLOR_RANGE, FIELD_COLOR_TRANSFER, FIELD_HDR_STATIC_INFO])
    private annotation class FieldNumber

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(keyForField(FIELD_COLOR_SPACE), colorSpace)
        bundle.putInt(keyForField(FIELD_COLOR_RANGE), colorRange)
        bundle.putInt(keyForField(FIELD_COLOR_TRANSFER), colorTransfer)
        bundle.putByteArray(keyForField(FIELD_HDR_STATIC_INFO), hdrStaticInfo)
        return bundle
    }

    companion object {
        /** Color info representing SDR BT.709 limited range, which is a common SDR video color format.  */
        val SDR_BT709_LIMITED = ColorInfo(C.COLOR_SPACE_BT709, C.COLOR_RANGE_LIMITED, C.COLOR_TRANSFER_SDR,  /* hdrStaticInfo= */ null)

        /**
         * Returns the [C.ColorSpace] corresponding to the given ISO color primary code, as per
         * table A.7.21.1 in Rec. ITU-T T.832 (03/2009), or [Format.NO_VALUE] if no mapping can be
         * made.
         */
        @Pure
        @C.ColorSpace
        fun isoColorPrimariesToColorSpace(isoColorPrimaries: Int): Int {
            return when (isoColorPrimaries) {
                1 -> C.COLOR_SPACE_BT709
                4, 5, 6, 7 -> C.COLOR_SPACE_BT601
                9 -> C.COLOR_SPACE_BT2020
                else -> Format.NO_VALUE
            }
        }

        /**
         * Returns the [C.ColorTransfer] corresponding to the given ISO transfer characteristics
         * code, as per table A.7.21.2 in Rec. ITU-T T.832 (03/2009), or [Format.NO_VALUE] if no
         * mapping can be made.
         */
        @Pure
        @ColorTransfer
        fun isoTransferCharacteristicsToColorTransfer(isoTransferCharacteristics: Int): Int {
            return when (isoTransferCharacteristics) {
                1, 6, 7 -> C.COLOR_TRANSFER_SDR
                16 -> C.COLOR_TRANSFER_ST2084
                18 -> C.COLOR_TRANSFER_HLG
                else -> Format.NO_VALUE
            }
        }

        /** Returns whether the `ColorInfo` uses an HDR [C.ColorTransfer].  */
        fun isTransferHdr(colorInfo: ColorInfo?): Boolean {
            return colorInfo != null && colorInfo.colorTransfer != Format.NO_VALUE && colorInfo.colorTransfer != C.COLOR_TRANSFER_SDR
        }

        private const val FIELD_COLOR_SPACE = 0
        private const val FIELD_COLOR_RANGE = 1
        private const val FIELD_COLOR_TRANSFER = 2
        private const val FIELD_HDR_STATIC_INFO = 3

        val CREATOR: Bundleable.Creator<ColorInfo> = Bundleable.Creator<ColorInfo> { bundle: Bundle ->
            ColorInfo(bundle.getInt(keyForField(FIELD_COLOR_SPACE), Format.NO_VALUE), bundle.getInt(keyForField(FIELD_COLOR_RANGE), Format.NO_VALUE), bundle.getInt(keyForField(FIELD_COLOR_TRANSFER), Format.NO_VALUE), bundle.getByteArray(keyForField(FIELD_HDR_STATIC_INFO)))
        }

        private fun keyForField(@FieldNumber field: Int): String? {
            return field.toString(Character.MAX_RADIX)
        }
    }
}