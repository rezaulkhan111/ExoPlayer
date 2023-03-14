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
import com.google.android.exoplayer2.C.ColorRange
import com.google.android.exoplayer2.C.ColorTransfer
import org.checkerframework.dataflow.qual.Pure

com.google.android.exoplayer2.*import java.lang.annotation.Documentedimport

java.lang.annotation .Retentionimport java.lang.annotation .RetentionPolicyimport java.util.*
/**
 * Stores color info.
 *
 *
 * When a `null` `ColorInfo` instance is used, this often represents a generic [ ][.SDR_BT709_LIMITED] instance.
 */
class ColorInfo
/**
 * Constructs the ColorInfo.
 *
 * @param colorSpace The color space of the video.
 * @param colorRange The color range of the video.
 * @param colorTransfer The color transfer characteristics of the video.
 * @param hdrStaticInfo HdrStaticInfo as defined in CTA-861.3, or null if none specified.
 */ constructor(
        /**
         * The color space of the video. Valid values are [C.COLOR_SPACE_BT601], [ ][C.COLOR_SPACE_BT709], [C.COLOR_SPACE_BT2020] or [Format.NO_VALUE] if unknown.
         */
        val colorSpace: @C.ColorSpace Int,
        /**
         * The color range of the video. Valid values are [C.COLOR_RANGE_LIMITED], [ ][C.COLOR_RANGE_FULL] or [Format.NO_VALUE] if unknown.
         */
        val colorRange: @ColorRange Int,
        /**
         * The color transfer characteristics of the video. Valid values are [C.COLOR_TRANSFER_HLG],
         * [C.COLOR_TRANSFER_ST2084], [C.COLOR_TRANSFER_SDR] or [Format.NO_VALUE] if
         * unknown.
         */
        val colorTransfer: @ColorTransfer Int,
        /** HdrStaticInfo as defined in CTA-861.3, or null if none specified.  */
        val hdrStaticInfo: ByteArray?) : Bundleable {
    // Lazily initialized hashcode.
    private var hashCode: Int = 0
    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other: ColorInfo = obj as ColorInfo
        return (colorSpace == other.colorSpace
                ) && (colorRange == other.colorRange
                ) && (colorTransfer == other.colorTransfer
                ) && Arrays.equals(hdrStaticInfo, other.hdrStaticInfo)
    }

    public override fun toString(): String {
        return ("ColorInfo("
                + colorSpace
                + ", "
                + colorRange
                + ", "
                + colorTransfer
                + ", "
                + (hdrStaticInfo != null)
                + ")")
    }

    public override fun hashCode(): Int {
        if (hashCode == 0) {
            var result: Int = 17
            result = 31 * result + colorSpace
            result = 31 * result + colorRange
            result = 31 * result + colorTransfer
            result = 31 * result + Arrays.hashCode(hdrStaticInfo)
            hashCode = result
        }
        return hashCode
    }

    // Bundleable implementation
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([FIELD_COLOR_SPACE, FIELD_COLOR_RANGE, FIELD_COLOR_TRANSFER, FIELD_HDR_STATIC_INFO])
    private annotation class FieldNumber constructor()

    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putInt(keyForField(FIELD_COLOR_SPACE), colorSpace)
        bundle.putInt(keyForField(FIELD_COLOR_RANGE), colorRange)
        bundle.putInt(keyForField(FIELD_COLOR_TRANSFER), colorTransfer)
        bundle.putByteArray(keyForField(FIELD_HDR_STATIC_INFO), hdrStaticInfo)
        return bundle
    }

    companion object {
        /** Color info representing SDR BT.709 limited range, which is a common SDR video color format.  */
        val SDR_BT709_LIMITED: ColorInfo = ColorInfo(
                C.COLOR_SPACE_BT709,
                C.COLOR_RANGE_LIMITED,
                C.COLOR_TRANSFER_SDR,  /* hdrStaticInfo= */
                null)

        /**
         * Returns the [C.ColorSpace] corresponding to the given ISO color primary code, as per
         * table A.7.21.1 in Rec. ITU-T T.832 (03/2009), or [Format.NO_VALUE] if no mapping can be
         * made.
         */
        @Pure
        fun isoColorPrimariesToColorSpace(isoColorPrimaries: Int): @C.ColorSpace Int {
            when (isoColorPrimaries) {
                1 -> return C.COLOR_SPACE_BT709
                4, 5, 6, 7 -> return C.COLOR_SPACE_BT601
                9 -> return C.COLOR_SPACE_BT2020
                else -> return Format.Companion.NO_VALUE
            }
        }

        /**
         * Returns the [C.ColorTransfer] corresponding to the given ISO transfer characteristics
         * code, as per table A.7.21.2 in Rec. ITU-T T.832 (03/2009), or [Format.NO_VALUE] if no
         * mapping can be made.
         */
        @Pure
        fun isoTransferCharacteristicsToColorTransfer(
                isoTransferCharacteristics: Int): @ColorTransfer Int {
            when (isoTransferCharacteristics) {
                1, 6, 7 -> return C.COLOR_TRANSFER_SDR
                16 -> return C.COLOR_TRANSFER_ST2084
                18 -> return C.COLOR_TRANSFER_HLG
                else -> return Format.Companion.NO_VALUE
            }
        }

        /** Returns whether the `ColorInfo` uses an HDR [C.ColorTransfer].  */
        fun isTransferHdr(colorInfo: ColorInfo?): Boolean {
            return (colorInfo != null
                    ) && (colorInfo.colorTransfer != Format.Companion.NO_VALUE
                    ) && (colorInfo.colorTransfer != C.COLOR_TRANSFER_SDR)
        }

        private val FIELD_COLOR_SPACE: Int = 0
        private val FIELD_COLOR_RANGE: Int = 1
        private val FIELD_COLOR_TRANSFER: Int = 2
        private val FIELD_HDR_STATIC_INFO: Int = 3
        val CREATOR: Bundleable.Creator<ColorInfo> = Bundleable.Creator({ bundle: Bundle ->
            ColorInfo(
                    bundle.getInt(keyForField(FIELD_COLOR_SPACE), Format.Companion.NO_VALUE),
                    bundle.getInt(keyForField(FIELD_COLOR_RANGE), Format.Companion.NO_VALUE),
                    bundle.getInt(keyForField(FIELD_COLOR_TRANSFER), Format.Companion.NO_VALUE),
                    bundle.getByteArray(keyForField(FIELD_HDR_STATIC_INFO)))
        })

        private fun keyForField(field: @FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}