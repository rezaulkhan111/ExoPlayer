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

import android.graphics.Colorimport

android.text.TextUtilsimport androidx.annotation .ColorIntimport com.google.common.base.Asciiimport java.util.regex.Matcherimport java.util.regex.Pattern
/**
 * Parser for color expressions found in styling formats, e.g. TTML and CSS.
 *
 * @see [WebVTT CSS Styling](https://w3c.github.io/webvtt/.styling)
 *
 * @see [Timed Text Markup Language 2
](https://www.w3.org/TR/ttml2/) */
object ColorParser {
    private val RGB: String = "rgb"
    private val RGBA: String = "rgba"
    private val RGB_PATTERN: Pattern = Pattern.compile("^rgb\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)$")
    private val RGBA_PATTERN_INT_ALPHA: Pattern = Pattern.compile("^rgba\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)$")
    private val RGBA_PATTERN_FLOAT_ALPHA: Pattern = Pattern.compile("^rgba\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d*\\.?\\d*?)\\)$")
    private val COLOR_MAP: MutableMap<String, Int>? = null

    /**
     * Parses a TTML color expression.
     *
     * @param colorExpression The color expression.
     * @return The parsed ARGB color.
     */
    @ColorInt
    fun parseTtmlColor(colorExpression: String): Int {
        return parseColorInternal(colorExpression, false)
    }

    /**
     * Parses a CSS color expression.
     *
     * @param colorExpression The color expression.
     * @return The parsed ARGB color.
     */
    @ColorInt
    fun parseCssColor(colorExpression: String): Int {
        return parseColorInternal(colorExpression, true)
    }

    @ColorInt
    private fun parseColorInternal(colorExpression: String, alphaHasFloatFormat: Boolean): Int {
        var colorExpression: String = colorExpression
        Assertions.checkArgument(!TextUtils.isEmpty(colorExpression))
        colorExpression = colorExpression.replace(" ", "")
        if (colorExpression.get(0) == '#') {
            // Parse using Long to avoid failure when colorExpression is greater than #7FFFFFFF.
            var color: Int = colorExpression.substring(1).toLong(16).toInt()
            if (colorExpression.length == 7) {
                // Set the alpha value
                color = color or -0x1000000
            } else if (colorExpression.length == 9) {
                // We have #RRGGBBAA, but we need #AARRGGBB
                color = ((color and 0xFF) shl 24) or (color ushr 8)
            } else {
                throw IllegalArgumentException()
            }
            return color
        } else if (colorExpression.startsWith(RGBA)) {
            val matcher: Matcher = (if (alphaHasFloatFormat) RGBA_PATTERN_FLOAT_ALPHA else RGBA_PATTERN_INT_ALPHA)
                    .matcher(colorExpression)
            if (matcher.matches()) {
                return Color.argb(
                        if (alphaHasFloatFormat) (255 * Assertions.checkNotNull(matcher.group(4))!!.toFloat()).toInt() else Assertions.checkNotNull(matcher.group(4))!!.toInt(10), Assertions.checkNotNull(matcher.group(1))!!.toInt(10), Assertions.checkNotNull(matcher.group(2))!!.toInt(10), Assertions.checkNotNull(matcher.group(3))!!.toInt(10))
            }
        } else if (colorExpression.startsWith(RGB)) {
            val matcher: Matcher = RGB_PATTERN.matcher(colorExpression)
            if (matcher.matches()) {
                return Color.rgb(Assertions.checkNotNull(matcher.group(1))!!.toInt(10), Assertions.checkNotNull(matcher.group(2))!!.toInt(10), Assertions.checkNotNull(matcher.group(3))!!.toInt(10))
            }
        } else {
            // we use our own color map
            val color: Int? = COLOR_MAP!!.get(Ascii.toLowerCase(colorExpression))
            if (color != null) {
                return color
            }
        }
        throw IllegalArgumentException()
    }

    init {
        COLOR_MAP = HashMap()
        COLOR_MAP.put("aliceblue", -0xf0701)
        COLOR_MAP.put("antiquewhite", -0x51429)
        COLOR_MAP.put("aqua", -0xff0001)
        COLOR_MAP.put("aquamarine", -0x80002c)
        COLOR_MAP.put("azure", -0xf0001)
        COLOR_MAP.put("beige", -0xa0a24)
        COLOR_MAP.put("bisque", -0x1b3c)
        COLOR_MAP.put("black", -0x1000000)
        COLOR_MAP.put("blanchedalmond", -0x1433)
        COLOR_MAP.put("blue", -0xffff01)
        COLOR_MAP.put("blueviolet", -0x75d41e)
        COLOR_MAP.put("brown", -0x5ad5d6)
        COLOR_MAP.put("burlywood", -0x214779)
        COLOR_MAP.put("cadetblue", -0xa06160)
        COLOR_MAP.put("chartreuse", -0x800100)
        COLOR_MAP.put("chocolate", -0x2d96e2)
        COLOR_MAP.put("coral", -0x80b0)
        COLOR_MAP.put("cornflowerblue", -0x9b6a13)
        COLOR_MAP.put("cornsilk", -0x724)
        COLOR_MAP.put("crimson", -0x23ebc4)
        COLOR_MAP.put("cyan", -0xff0001)
        COLOR_MAP.put("darkblue", -0xffff75)
        COLOR_MAP.put("darkcyan", -0xff7475)
        COLOR_MAP.put("darkgoldenrod", -0x4779f5)
        COLOR_MAP.put("darkgray", -0x565657)
        COLOR_MAP.put("darkgreen", -0xff9c00)
        COLOR_MAP.put("darkgrey", -0x565657)
        COLOR_MAP.put("darkkhaki", -0x424895)
        COLOR_MAP.put("darkmagenta", -0x74ff75)
        COLOR_MAP.put("darkolivegreen", -0xaa94d1)
        COLOR_MAP.put("darkorange", -0x7400)
        COLOR_MAP.put("darkorchid", -0x66cd34)
        COLOR_MAP.put("darkred", -0x750000)
        COLOR_MAP.put("darksalmon", -0x166986)
        COLOR_MAP.put("darkseagreen", -0x704371)
        COLOR_MAP.put("darkslateblue", -0xb7c275)
        COLOR_MAP.put("darkslategray", -0xd0b0b1)
        COLOR_MAP.put("darkslategrey", -0xd0b0b1)
        COLOR_MAP.put("darkturquoise", -0xff312f)
        COLOR_MAP.put("darkviolet", -0x6bff2d)
        COLOR_MAP.put("deeppink", -0xeb6d)
        COLOR_MAP.put("deepskyblue", -0xff4001)
        COLOR_MAP.put("dimgray", -0x969697)
        COLOR_MAP.put("dimgrey", -0x969697)
        COLOR_MAP.put("dodgerblue", -0xe16f01)
        COLOR_MAP.put("firebrick", -0x4dddde)
        COLOR_MAP.put("floralwhite", -0x510)
        COLOR_MAP.put("forestgreen", -0xdd74de)
        COLOR_MAP.put("fuchsia", -0xff01)
        COLOR_MAP.put("gainsboro", -0x232324)
        COLOR_MAP.put("ghostwhite", -0x70701)
        COLOR_MAP.put("gold", -0x2900)
        COLOR_MAP.put("goldenrod", -0x255ae0)
        COLOR_MAP.put("gray", -0x7f7f80)
        COLOR_MAP.put("green", -0xff8000)
        COLOR_MAP.put("greenyellow", -0x5200d1)
        COLOR_MAP.put("grey", -0x7f7f80)
        COLOR_MAP.put("honeydew", -0xf0010)
        COLOR_MAP.put("hotpink", -0x964c)
        COLOR_MAP.put("indianred", -0x32a3a4)
        COLOR_MAP.put("indigo", -0xb4ff7e)
        COLOR_MAP.put("ivory", -0x10)
        COLOR_MAP.put("khaki", -0xf1974)
        COLOR_MAP.put("lavender", -0x191906)
        COLOR_MAP.put("lavenderblush", -0xf0b)
        COLOR_MAP.put("lawngreen", -0x830400)
        COLOR_MAP.put("lemonchiffon", -0x533)
        COLOR_MAP.put("lightblue", -0x52271a)
        COLOR_MAP.put("lightcoral", -0xf7f80)
        COLOR_MAP.put("lightcyan", -0x1f0001)
        COLOR_MAP.put("lightgoldenrodyellow", -0x5052e)
        COLOR_MAP.put("lightgray", -0x2c2c2d)
        COLOR_MAP.put("lightgreen", -0x6f1170)
        COLOR_MAP.put("lightgrey", -0x2c2c2d)
        COLOR_MAP.put("lightpink", -0x493f)
        COLOR_MAP.put("lightsalmon", -0x5f86)
        COLOR_MAP.put("lightseagreen", -0xdf4d56)
        COLOR_MAP.put("lightskyblue", -0x783106)
        COLOR_MAP.put("lightslategray", -0x887767)
        COLOR_MAP.put("lightslategrey", -0x887767)
        COLOR_MAP.put("lightsteelblue", -0x4f3b22)
        COLOR_MAP.put("lightyellow", -0x20)
        COLOR_MAP.put("lime", -0xff0100)
        COLOR_MAP.put("limegreen", -0xcd32ce)
        COLOR_MAP.put("linen", -0x50f1a)
        COLOR_MAP.put("magenta", -0xff01)
        COLOR_MAP.put("maroon", -0x800000)
        COLOR_MAP.put("mediumaquamarine", -0x993256)
        COLOR_MAP.put("mediumblue", -0xffff33)
        COLOR_MAP.put("mediumorchid", -0x45aa2d)
        COLOR_MAP.put("mediumpurple", -0x6c8f25)
        COLOR_MAP.put("mediumseagreen", -0xc34c8f)
        COLOR_MAP.put("mediumslateblue", -0x849712)
        COLOR_MAP.put("mediumspringgreen", -0xff0566)
        COLOR_MAP.put("mediumturquoise", -0xb72e34)
        COLOR_MAP.put("mediumvioletred", -0x38ea7b)
        COLOR_MAP.put("midnightblue", -0xe6e690)
        COLOR_MAP.put("mintcream", -0xa0006)
        COLOR_MAP.put("mistyrose", -0x1b1f)
        COLOR_MAP.put("moccasin", -0x1b4b)
        COLOR_MAP.put("navajowhite", -0x2153)
        COLOR_MAP.put("navy", -0xffff80)
        COLOR_MAP.put("oldlace", -0x20a1a)
        COLOR_MAP.put("olive", -0x7f8000)
        COLOR_MAP.put("olivedrab", -0x9471dd)
        COLOR_MAP.put("orange", -0x5b00)
        COLOR_MAP.put("orangered", -0xbb00)
        COLOR_MAP.put("orchid", -0x258f2a)
        COLOR_MAP.put("palegoldenrod", -0x111756)
        COLOR_MAP.put("palegreen", -0x670468)
        COLOR_MAP.put("paleturquoise", -0x501112)
        COLOR_MAP.put("palevioletred", -0x248f6d)
        COLOR_MAP.put("papayawhip", -0x102b)
        COLOR_MAP.put("peachpuff", -0x2547)
        COLOR_MAP.put("peru", -0x327ac1)
        COLOR_MAP.put("pink", -0x3f35)
        COLOR_MAP.put("plum", -0x225f23)
        COLOR_MAP.put("powderblue", -0x4f1f1a)
        COLOR_MAP.put("purple", -0x7fff80)
        COLOR_MAP.put("rebeccapurple", -0x99cc67)
        COLOR_MAP.put("red", -0x10000)
        COLOR_MAP.put("rosybrown", -0x437071)
        COLOR_MAP.put("royalblue", -0xbe961f)
        COLOR_MAP.put("saddlebrown", -0x74baed)
        COLOR_MAP.put("salmon", -0x57f8e)
        COLOR_MAP.put("sandybrown", -0xb5ba0)
        COLOR_MAP.put("seagreen", -0xd174a9)
        COLOR_MAP.put("seashell", -0xa12)
        COLOR_MAP.put("sienna", -0x5fadd3)
        COLOR_MAP.put("silver", -0x3f3f40)
        COLOR_MAP.put("skyblue", -0x783115)
        COLOR_MAP.put("slateblue", -0x95a533)
        COLOR_MAP.put("slategray", -0x8f7f70)
        COLOR_MAP.put("slategrey", -0x8f7f70)
        COLOR_MAP.put("snow", -0x506)
        COLOR_MAP.put("springgreen", -0xff0081)
        COLOR_MAP.put("steelblue", -0xb97d4c)
        COLOR_MAP.put("tan", -0x2d4b74)
        COLOR_MAP.put("teal", -0xff7f80)
        COLOR_MAP.put("thistle", -0x274028)
        COLOR_MAP.put("tomato", -0x9cb9)
        COLOR_MAP.put("transparent", 0x00000000)
        COLOR_MAP.put("turquoise", -0xbf1f30)
        COLOR_MAP.put("violet", -0x117d12)
        COLOR_MAP.put("wheat", -0xa214d)
        COLOR_MAP.put("white", -0x1)
        COLOR_MAP.put("whitesmoke", -0xa0a0b)
        COLOR_MAP.put("yellow", -0x100)
        COLOR_MAP.put("yellowgreen", -0x6532ce)
    }
}