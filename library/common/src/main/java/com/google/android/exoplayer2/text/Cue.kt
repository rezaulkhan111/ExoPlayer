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
package com.google.android.exoplayer2.text

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.*
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import com.google.android.exoplayer2.Bundleable
import com.google.android.exoplayer2.util.Assertions.checkArgument
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.common.base.Objects
import com.google.errorprone.annotations.CanIgnoreReturnValue
import org.checkerframework.dataflow.qual.Pure
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Contains information about a specific cue, including textual content and formatting data.  */ // This class shouldn't be sub-classed. If a subtitle format needs additional fields, either they
// should be generic enough to be added here, or the format-specific decoder should pass the
// information around in a sidecar object.
class Cue : Bundleable {

    companion object {
        /** The empty cue.  */
        val EMPTY = Builder().setText("")?.build()

        /** An unset position, width or size.  */ // Note: We deliberately don't use Float.MIN_VALUE because it's positive & very close to zero.
        const val DIMEN_UNSET = -Float.MAX_VALUE

        /** An unset anchor, line, text size or vertical type value.  */
        const val TYPE_UNSET = Int.MIN_VALUE

        /**
         * Anchors the left (for horizontal positions) or top (for vertical positions) edge of the cue
         * box.
         */
        const val ANCHOR_TYPE_START = 0

        /** Anchors the middle of the cue box.  */
        const val ANCHOR_TYPE_MIDDLE = 1

        /**
         * Anchors the right (for horizontal positions) or bottom (for vertical positions) edge of the cue
         * box.
         */
        const val ANCHOR_TYPE_END = 2

        /** Value for [.lineType] when [.line] is a fractional position.  */
        const val LINE_TYPE_FRACTION = 0

        /** Value for [.lineType] when [.line] is a line number.  */
        const val LINE_TYPE_NUMBER = 1

        /** Text size is measured as a fraction of the viewport size minus the view padding.  */
        const val TEXT_SIZE_TYPE_FRACTIONAL = 0

        /** Text size is measured as a fraction of the viewport size, ignoring the view padding  */
        const val TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING = 1

        /** Text size is measured in number of pixels.  */
        const val TEXT_SIZE_TYPE_ABSOLUTE = 2


        /** Vertical right-to-left (e.g. for Japanese).  */
        const val VERTICAL_TYPE_RL = 1

        /** Vertical left-to-right (e.g. for Mongolian).  */
        const val VERTICAL_TYPE_LR = 2

        private const val FIELD_TEXT = 0
        private const val FIELD_TEXT_ALIGNMENT = 1
        private const val FIELD_MULTI_ROW_ALIGNMENT = 2
        private const val FIELD_BITMAP = 3
        private const val FIELD_LINE = 4
        private const val FIELD_LINE_TYPE = 5
        private const val FIELD_LINE_ANCHOR = 6
        private const val FIELD_POSITION = 7
        private const val FIELD_POSITION_ANCHOR = 8
        private const val FIELD_TEXT_SIZE_TYPE = 9
        private const val FIELD_TEXT_SIZE = 10
        private const val FIELD_SIZE = 11
        private const val FIELD_BITMAP_HEIGHT = 12
        private const val FIELD_WINDOW_COLOR = 13
        private const val FIELD_WINDOW_COLOR_SET = 14
        private const val FIELD_VERTICAL_TYPE = 15
        private const val FIELD_SHEAR_DEGREES = 16

        val CREATOR: Bundleable.Creator<Cue> = Bundleable.Creator<Cue> { obj: Cue, bundle: Bundle -> obj.fromBundle(bundle) }
    }


    /**
     * The type of anchor, which may be unset. One of [.TYPE_UNSET], [.ANCHOR_TYPE_START],
     * [.ANCHOR_TYPE_MIDDLE] or [.ANCHOR_TYPE_END].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef(value = [TYPE_UNSET, ANCHOR_TYPE_START, ANCHOR_TYPE_MIDDLE, ANCHOR_TYPE_END])
    annotation class AnchorType

    /**
     * The type of line, which may be unset. One of [.TYPE_UNSET], [.LINE_TYPE_FRACTION]
     * or [.LINE_TYPE_NUMBER].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef(value = [TYPE_UNSET, LINE_TYPE_FRACTION, LINE_TYPE_NUMBER])
    annotation class LineType

    /**
     * The type of default text size for this cue, which may be unset. One of [.TYPE_UNSET],
     * [.TEXT_SIZE_TYPE_FRACTIONAL], [.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING] or [ ][.TEXT_SIZE_TYPE_ABSOLUTE].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef(value = [TYPE_UNSET, TEXT_SIZE_TYPE_FRACTIONAL, TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING, TEXT_SIZE_TYPE_ABSOLUTE])
    annotation class TextSizeType

    /**
     * The type of vertical layout for this cue, which may be unset (i.e. horizontal). One of [ ][.TYPE_UNSET], [.VERTICAL_TYPE_RL] or [.VERTICAL_TYPE_LR].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef(value = [TYPE_UNSET, VERTICAL_TYPE_RL, VERTICAL_TYPE_LR])
    annotation class VerticalType

    /**
     * The cue text, or null if this is an image cue. Note the [CharSequence] may be decorated
     * with styling spans.
     */
    var text: CharSequence? = null

    /** The alignment of the cue text within the cue box, or null if the alignment is undefined.  */
    var textAlignment: Layout.Alignment? = null

    /**
     * The alignment of multiple lines of text relative to the longest line, or null if the alignment
     * is undefined.
     */
    var multiRowAlignment: Layout.Alignment? = null

    /** The cue image, or null if this is a text cue.  */
    var bitmap: Bitmap? = null

    /**
     * The position of the cue box within the viewport in the direction orthogonal to the writing
     * direction (determined by [.verticalType]), or [.DIMEN_UNSET]. When set, the
     * interpretation of the value depends on the value of [.lineType].
     *
     *
     * The measurement direction depends on [.verticalType]:
     *
     *
     *  * For [.TYPE_UNSET] (i.e. horizontal), this is the vertical position relative to the
     * top of the viewport.
     *  * For [.VERTICAL_TYPE_LR] this is the horizontal position relative to the left of the
     * viewport.
     *  * For [.VERTICAL_TYPE_RL] this is the horizontal position relative to the right of
     * the viewport.
     *
     */
    var line = 0f

    /**
     * The type of the [.line] value.
     *
     *
     *  * [.LINE_TYPE_FRACTION] indicates that [.line] is a fractional position within
     * the viewport (measured to the part of the cue box determined by [.lineAnchor]).
     *  * [.LINE_TYPE_NUMBER] indicates that [.line] is a viewport line number. The
     * viewport is divided into lines (each equal in size to the first line of the cue box). The
     * cue box is positioned to align with the viewport lines as follows:
     *
     *  * [.lineAnchor]) is ignored.
     *  * When `line` is greater than or equal to 0 the first line in the cue box is
     * aligned with a viewport line, with 0 meaning the first line of the viewport.
     *  * When `line` is negative the last line in the cue box is aligned with a
     * viewport line, with -1 meaning the last line of the viewport.
     *  * For horizontal text the start and end of the viewport are the top and bottom
     * respectively.
     *
     *
     */
    @LineType
    var lineType: Int = 0

    /**
     * The cue box anchor positioned by [.line] when [.lineType] is [ ][.LINE_TYPE_FRACTION].
     *
     *
     * One of:
     *
     *
     *  * [.ANCHOR_TYPE_START]
     *  * [.ANCHOR_TYPE_MIDDLE]
     *  * [.ANCHOR_TYPE_END]
     *  * [.TYPE_UNSET]
     *
     *
     *
     * For the normal case of horizontal text, [.ANCHOR_TYPE_START], [ ][.ANCHOR_TYPE_MIDDLE] and [.ANCHOR_TYPE_END] correspond to the top, middle and bottom of
     * the cue box respectively.
     */
    @AnchorType
    var lineAnchor: Int = 0

    /**
     * The fractional position of the [.positionAnchor] of the cue box within the viewport in
     * the direction orthogonal to [.line], or [.DIMEN_UNSET].
     *
     *
     * The measurement direction depends on [.verticalType].
     *
     *
     *  * For [.TYPE_UNSET] (i.e. horizontal), this is the horizontal position relative to
     * the left of the viewport. Note that positioning is relative to the left of the viewport
     * even in the case of right-to-left text.
     *  * For [.VERTICAL_TYPE_LR] and [.VERTICAL_TYPE_RL] (i.e. vertical), this is the
     * vertical position relative to the top of the viewport.
     *
     */
    var position = 0f

    /**
     * The cue box anchor positioned by [.position]. One of [.ANCHOR_TYPE_START], [ ][.ANCHOR_TYPE_MIDDLE], [.ANCHOR_TYPE_END] and [.TYPE_UNSET].
     *
     *
     * For the normal case of horizontal text, [.ANCHOR_TYPE_START], [ ][.ANCHOR_TYPE_MIDDLE] and [.ANCHOR_TYPE_END] correspond to the left, middle and right of
     * the cue box respectively.
     */
    @AnchorType
    var positionAnchor: Int = 0

    /**
     * The size of the cue box in the writing direction specified as a fraction of the viewport size
     * in that direction, or [.DIMEN_UNSET].
     */
    var size = 0f

    /**
     * The bitmap height as a fraction of the of the viewport size, or [.DIMEN_UNSET] if the
     * bitmap should be displayed at its natural height given the bitmap dimensions and the specified
     * [.size].
     */
    var bitmapHeight = 0f

    /** Specifies whether or not the [.windowColor] property is set.  */
    var windowColorSet = false

    /** The fill color of the window.  */
    var windowColor = 0

    /**
     * The default text size type for this cue's text, or [.TYPE_UNSET] if this cue has no
     * default text size.
     */
    @TextSizeType
    var textSizeType: Int = 0

    /**
     * The default text size for this cue's text, or [.DIMEN_UNSET] if this cue has no default
     * text size.
     */
    var textSize = 0f

    /**
     * The vertical formatting of this Cue, or [.TYPE_UNSET] if the cue has no vertical setting
     * (and so should be horizontal).
     */
    @VerticalType
    var verticalType: Int = 0

    /**
     * The shear angle in degrees to be applied to this Cue, expressed in graphics coordinates. This
     * results in a skew transform for the block along the inline progression axis.
     */
    var shearDegrees = 0f

    /**
     * Creates a text cue whose [.textAlignment] is null, whose type parameters are set to
     * [.TYPE_UNSET] and whose dimension parameters are set to [.DIMEN_UNSET].
     *
     * @param text See [.text].
     */
    @Deprecated("Use {@link Builder}.")
    constructor(text: CharSequence?) {
        Cue(text,  /* textAlignment= */
                null,  /* line= */
                DIMEN_UNSET,  /* lineType= */
                TYPE_UNSET,  /* lineAnchor= */
                TYPE_UNSET,  /* position= */
                DIMEN_UNSET,  /* positionAnchor= */
                TYPE_UNSET,  /* size= */
                DIMEN_UNSET)
    }

    /**
     * Creates a text cue.
     *
     * @param text See [.text].
     * @param textAlignment See [.textAlignment].
     * @param line See [.line].
     * @param lineType See [.lineType].
     * @param lineAnchor See [.lineAnchor].
     * @param position See [.position].
     * @param positionAnchor See [.positionAnchor].
     * @param size See [.size].
     */
    @Deprecated("Use {@link Builder}.")
    constructor(text: CharSequence?, textAlignment: Layout.Alignment?, line: Float, @LineType lineType: Int, @AnchorType lineAnchor: Int, position: Float, @AnchorType positionAnchor: Int, size: Float) {
        Cue(text, textAlignment, line, lineType, lineAnchor, position, positionAnchor, size,  /* windowColorSet= */
                false,  /* windowColor= */
                Color.BLACK)
    }

    /**
     * Creates a text cue.
     *
     * @param text See [.text].
     * @param textAlignment See [.textAlignment].
     * @param line See [.line].
     * @param lineType See [.lineType].
     * @param lineAnchor See [.lineAnchor].
     * @param position See [.position].
     * @param positionAnchor See [.positionAnchor].
     * @param size See [.size].
     * @param textSizeType See [.textSizeType].
     * @param textSize See [.textSize].
     */
    @Deprecated("Use {@link Builder}.")
    constructor(text: CharSequence?, textAlignment: Layout.Alignment?, line: Float, @LineType lineType: Int, @AnchorType lineAnchor: Int, position: Float, @AnchorType positionAnchor: Int, size: Float, @TextSizeType textSizeType: Int, textSize: Float) {
        Cue(text, textAlignment,  /* multiRowAlignment= */
                null,  /* bitmap= */
                null, line, lineType, lineAnchor, position, positionAnchor, textSizeType, textSize, size,  /* bitmapHeight= */
                DIMEN_UNSET,  /* windowColorSet= */
                false,  /* windowColor= */
                Color.BLACK,  /* verticalType= */
                TYPE_UNSET,  /* shearDegrees= */
                0f)
    }

    /**
     * Creates a text cue.
     *
     * @param text See [.text].
     * @param textAlignment See [.textAlignment].
     * @param line See [.line].
     * @param lineType See [.lineType].
     * @param lineAnchor See [.lineAnchor].
     * @param position See [.position].
     * @param positionAnchor See [.positionAnchor].
     * @param size See [.size].
     * @param windowColorSet See [.windowColorSet].
     * @param windowColor See [.windowColor].
     */
    @Deprecated("Use {@link Builder}.")
    constructor(text: CharSequence?, textAlignment: Layout.Alignment?, line: Float, @LineType lineType: Int, @AnchorType lineAnchor: Int, position: Float, @AnchorType positionAnchor: Int, size: Float, windowColorSet: Boolean, windowColor: Int) {
        Cue(text, textAlignment,  /* multiRowAlignment= */
                null,  /* bitmap= */
                null, line, lineType, lineAnchor, position, positionAnchor,  /* textSizeType= */
                TYPE_UNSET,  /* textSize= */
                DIMEN_UNSET, size,  /* bitmapHeight= */
                DIMEN_UNSET, windowColorSet, windowColor,  /* verticalType= */
                TYPE_UNSET,  /* shearDegrees= */
                0f)
    }

    private constructor(text: CharSequence?, textAlignment: Layout.Alignment?, multiRowAlignment: Layout.Alignment?, bitmap: Bitmap?, line: Float, @LineType lineType: Int, @AnchorType lineAnchor: Int, position: Float, @AnchorType positionAnchor: Int, @TextSizeType textSizeType: Int, textSize: Float, size: Float, bitmapHeight: Float, windowColorSet: Boolean, windowColor: Int, @VerticalType verticalType: Int, shearDegrees: Float) {
        // Exactly one of text or bitmap should be set.
        if (text == null) {
            checkNotNull(bitmap)
        } else {
            checkArgument(bitmap == null)
        }
        if (text is Spanned) {
            this.text = SpannedString.valueOf(text)
        } else if (text != null) {
            this.text = text.toString()
        } else {
            this.text = null
        }
        this.textAlignment = textAlignment
        this.multiRowAlignment = multiRowAlignment
        this.bitmap = bitmap
        this.line = line
        this.lineType = lineType
        this.lineAnchor = lineAnchor
        this.position = position
        this.positionAnchor = positionAnchor
        this.size = size
        this.bitmapHeight = bitmapHeight
        this.windowColorSet = windowColorSet
        this.windowColor = windowColor
        this.textSizeType = textSizeType
        this.textSize = textSize
        this.verticalType = verticalType
        this.shearDegrees = shearDegrees
    }

    /** Returns a new [Cue.Builder] initialized with the same values as this Cue.  */
    fun buildUpon(): Builder? {
        return Builder(this)
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val that = obj as Cue
        return TextUtils.equals(text, that.text) && textAlignment == that.textAlignment && multiRowAlignment == that.multiRowAlignment && if (bitmap == null) that.bitmap == null else that.bitmap != null && bitmap!!.sameAs(that.bitmap) && line == that.line && lineType == that.lineType && lineAnchor == that.lineAnchor && position == that.position && positionAnchor == that.positionAnchor && size == that.size && bitmapHeight == that.bitmapHeight && windowColorSet == that.windowColorSet && windowColor == that.windowColor && textSizeType == that.textSizeType && textSize == that.textSize && verticalType == that.verticalType && shearDegrees == that.shearDegrees
    }

    override fun hashCode(): Int {
        return Objects.hashCode(text, textAlignment, multiRowAlignment, bitmap, line, lineType, lineAnchor, position, positionAnchor, size, bitmapHeight, windowColorSet, windowColor, textSizeType, textSize, verticalType, shearDegrees)
    }

    /** A builder for [Cue] objects.  */
    class Builder {
        private var text: CharSequence? = null
        private var bitmap: Bitmap? = null
        private var textAlignment: Layout.Alignment? = null
        private var multiRowAlignment: Layout.Alignment? = null
        private var line = 0f

        @LineType
        private var lineType: Int = 0

        @AnchorType
        private var lineAnchor: Int = 0
        private var position = 0f

        @AnchorType
        private var positionAnchor: Int = 0

        @TextSizeType
        private var textSizeType: Int = 0
        private var textSize = 0f
        private var size = 0f
        private var bitmapHeight = 0f
        private var windowColorSet = false

        @ColorInt
        private var windowColor = 0

        @VerticalType
        private var verticalType: Int = 0
        private var shearDegrees = 0f

        constructor() {
            text = null
            bitmap = null
            textAlignment = null
            multiRowAlignment = null
            line = DIMEN_UNSET
            lineType = TYPE_UNSET
            lineAnchor = TYPE_UNSET
            position = DIMEN_UNSET
            positionAnchor = TYPE_UNSET
            textSizeType = TYPE_UNSET
            textSize = DIMEN_UNSET
            size = DIMEN_UNSET
            bitmapHeight = DIMEN_UNSET
            windowColorSet = false
            windowColor = Color.BLACK
            verticalType = TYPE_UNSET
        }

        constructor(cue: Cue) {
            text = cue.text
            bitmap = cue.bitmap
            textAlignment = cue.textAlignment
            multiRowAlignment = cue.multiRowAlignment
            line = cue.line
            lineType = cue.lineType
            lineAnchor = cue.lineAnchor
            position = cue.position
            positionAnchor = cue.positionAnchor
            textSizeType = cue.textSizeType
            textSize = cue.textSize
            size = cue.size
            bitmapHeight = cue.bitmapHeight
            windowColorSet = cue.windowColorSet
            windowColor = cue.windowColor
            verticalType = cue.verticalType
            shearDegrees = cue.shearDegrees
        }

        /**
         * Sets the cue text.
         *
         *
         * Note that `text` may be decorated with styling spans.
         *
         * @see Cue.text
         */
        @CanIgnoreReturnValue
        fun setText(text: CharSequence?): Builder? {
            this.text = text
            return this
        }

        /**
         * Gets the cue text.
         *
         * @see Cue.text
         */
        @Pure
        fun getText(): CharSequence? {
            return text
        }

        /**
         * Sets the cue image.
         *
         * @see Cue.bitmap
         */
        @CanIgnoreReturnValue
        fun setBitmap(bitmap: Bitmap?): Builder? {
            this.bitmap = bitmap
            return this
        }

        /**
         * Gets the cue image.
         *
         * @see Cue.bitmap
         */
        @Pure
        fun getBitmap(): Bitmap? {
            return bitmap
        }

        /**
         * Sets the alignment of the cue text within the cue box.
         *
         *
         * Passing null means the alignment is undefined.
         *
         * @see Cue.textAlignment
         */
        @CanIgnoreReturnValue
        fun setTextAlignment(textAlignment: Layout.Alignment?): Builder? {
            this.textAlignment = textAlignment
            return this
        }

        /**
         * Gets the alignment of the cue text within the cue box, or null if the alignment is undefined.
         *
         * @see Cue.textAlignment
         */
        @Pure
        fun getTextAlignment(): Layout.Alignment? {
            return textAlignment
        }

        /**
         * Sets the multi-row alignment of the cue.
         *
         *
         * Passing null means the alignment is undefined.
         *
         * @see Cue.multiRowAlignment
         */
        @CanIgnoreReturnValue
        fun setMultiRowAlignment(multiRowAlignment: Layout.Alignment?): Builder? {
            this.multiRowAlignment = multiRowAlignment
            return this
        }

        /**
         * Sets the position of the cue box within the viewport in the direction orthogonal to the
         * writing direction.
         *
         * @see Cue.line
         *
         * @see Cue.lineType
         */
        @CanIgnoreReturnValue
        fun setLine(line: Float, @LineType lineType: Int): Builder? {
            this.line = line
            this.lineType = lineType
            return this
        }

        /**
         * Gets the position of the `lineAnchor` of the cue box within the viewport in the
         * direction orthogonal to the writing direction.
         *
         * @see Cue.line
         */
        @Pure
        fun getLine(): Float {
            return line
        }

        /**
         * Gets the type of the value of [.getLine].
         *
         * @see Cue.lineType
         */
        @Pure
        @LineType
        fun getLineType(): Int {
            return lineType
        }

        /**
         * Sets the cue box anchor positioned by [line][.setLine].
         *
         * @see Cue.lineAnchor
         */
        @CanIgnoreReturnValue
        fun setLineAnchor(@AnchorType lineAnchor: Int): Builder? {
            this.lineAnchor = lineAnchor
            return this
        }

        /**
         * Gets the cue box anchor positioned by [line][.setLine].
         *
         * @see Cue.lineAnchor
         */
        @Pure
        @AnchorType
        fun getLineAnchor(): Int {
            return lineAnchor
        }

        /**
         * Sets the fractional position of the [positionAnchor][.setPositionAnchor] of the cue
         * box within the viewport in the direction orthogonal to [line][.setLine].
         *
         * @see Cue.position
         */
        @CanIgnoreReturnValue
        fun setPosition(position: Float): Builder? {
            this.position = position
            return this
        }

        /**
         * Gets the fractional position of the [positionAnchor][.setPositionAnchor] of the cue
         * box within the viewport in the direction orthogonal to [line][.setLine].
         *
         * @see Cue.position
         */
        @Pure
        fun getPosition(): Float {
            return position
        }

        /**
         * Sets the cue box anchor positioned by [position][.setPosition].
         *
         * @see Cue.positionAnchor
         */
        @CanIgnoreReturnValue
        fun setPositionAnchor(@AnchorType positionAnchor: Int): Builder? {
            this.positionAnchor = positionAnchor
            return this
        }

        /**
         * Gets the cue box anchor positioned by [position][.setPosition].
         *
         * @see Cue.positionAnchor
         */
        @AnchorType
        @Pure
        fun getPositionAnchor(): Int {
            return positionAnchor
        }

        /**
         * Sets the default text size and type for this cue's text.
         *
         * @see Cue.textSize
         *
         * @see Cue.textSizeType
         */
        @CanIgnoreReturnValue
        fun setTextSize(textSize: Float, @TextSizeType textSizeType: Int): Builder? {
            this.textSize = textSize
            this.textSizeType = textSizeType
            return this
        }

        /**
         * Gets the default text size type for this cue's text.
         *
         * @see Cue.textSizeType
         */
        @TextSizeType
        @Pure
        fun getTextSizeType(): Int {
            return textSizeType
        }

        /**
         * Gets the default text size for this cue's text.
         *
         * @see Cue.textSize
         */
        @Pure
        fun getTextSize(): Float {
            return textSize
        }

        /**
         * Sets the size of the cue box in the writing direction specified as a fraction of the viewport
         * size in that direction.
         *
         * @see Cue.size
         */
        @CanIgnoreReturnValue
        fun setSize(size: Float): Builder? {
            this.size = size
            return this
        }

        /**
         * Gets the size of the cue box in the writing direction specified as a fraction of the viewport
         * size in that direction.
         *
         * @see Cue.size
         */
        @Pure
        fun getSize(): Float {
            return size
        }

        /**
         * Sets the bitmap height as a fraction of the viewport size.
         *
         * @see Cue.bitmapHeight
         */
        @CanIgnoreReturnValue
        fun setBitmapHeight(bitmapHeight: Float): Builder? {
            this.bitmapHeight = bitmapHeight
            return this
        }

        /**
         * Gets the bitmap height as a fraction of the viewport size.
         *
         * @see Cue.bitmapHeight
         */
        @Pure
        fun getBitmapHeight(): Float {
            return bitmapHeight
        }

        /**
         * Sets the fill color of the window.
         *
         *
         * Also sets [Cue.windowColorSet] to true.
         *
         * @see Cue.windowColor
         *
         * @see Cue.windowColorSet
         */
        @CanIgnoreReturnValue
        fun setWindowColor(@ColorInt windowColor: Int): Builder? {
            this.windowColor = windowColor
            windowColorSet = true
            return this
        }

        /**
         * Sets [Cue.windowColorSet] to false.
         */
        @CanIgnoreReturnValue
        fun clearWindowColor(): Builder? {
            windowColorSet = false
            return this
        }

        /**
         * Returns true if the fill color of the window is set.
         *
         * @see Cue.windowColorSet
         */
        fun isWindowColorSet(): Boolean {
            return windowColorSet
        }

        /**
         * Gets the fill color of the window.
         *
         * @see Cue.windowColor
         */
        @Pure
        @ColorInt
        fun getWindowColor(): Int {
            return windowColor
        }

        /**
         * Sets the vertical formatting for this Cue.
         *
         * @see Cue.verticalType
         */
        @CanIgnoreReturnValue
        fun setVerticalType(@VerticalType verticalType: Int): Builder? {
            this.verticalType = verticalType
            return this
        }

        /**
         * Sets the shear angle for this Cue.
         */
        @CanIgnoreReturnValue
        fun setShearDegrees(shearDegrees: Float): Builder? {
            this.shearDegrees = shearDegrees
            return this
        }

        /**
         * Gets the vertical formatting for this Cue.
         *
         * @see Cue.verticalType
         */
        @VerticalType
        @Pure
        fun getVerticalType(): Int {
            return verticalType
        }

        /**
         * Build the cue.
         */
        fun build(): Cue? {
            return Cue(text, textAlignment, multiRowAlignment, bitmap, line, lineType, lineAnchor, position, positionAnchor, textSizeType, textSize, size, bitmapHeight, windowColorSet, windowColor, verticalType, shearDegrees)
        }
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [FIELD_TEXT, FIELD_TEXT_ALIGNMENT, FIELD_MULTI_ROW_ALIGNMENT, FIELD_BITMAP, FIELD_LINE, FIELD_LINE_TYPE, FIELD_LINE_ANCHOR, FIELD_POSITION, FIELD_POSITION_ANCHOR, FIELD_TEXT_SIZE_TYPE, FIELD_TEXT_SIZE, FIELD_SIZE, FIELD_BITMAP_HEIGHT, FIELD_WINDOW_COLOR, FIELD_WINDOW_COLOR_SET, FIELD_VERTICAL_TYPE, FIELD_SHEAR_DEGREES])
    private annotation class FieldNumber {}

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putCharSequence(keyForField(FIELD_TEXT), text)
        bundle.putSerializable(keyForField(FIELD_TEXT_ALIGNMENT), textAlignment)
        bundle.putSerializable(keyForField(FIELD_MULTI_ROW_ALIGNMENT), multiRowAlignment)
        bundle.putParcelable(keyForField(FIELD_BITMAP), bitmap)
        bundle.putFloat(keyForField(FIELD_LINE), line)
        bundle.putInt(keyForField(FIELD_LINE_TYPE), lineType)
        bundle.putInt(keyForField(FIELD_LINE_ANCHOR), lineAnchor)
        bundle.putFloat(keyForField(FIELD_POSITION), position)
        bundle.putInt(keyForField(FIELD_POSITION_ANCHOR), positionAnchor)
        bundle.putInt(keyForField(FIELD_TEXT_SIZE_TYPE), textSizeType)
        bundle.putFloat(keyForField(FIELD_TEXT_SIZE), textSize)
        bundle.putFloat(keyForField(FIELD_SIZE), size)
        bundle.putFloat(keyForField(FIELD_BITMAP_HEIGHT), bitmapHeight)
        bundle.putBoolean(keyForField(FIELD_WINDOW_COLOR_SET), windowColorSet)
        bundle.putInt(keyForField(FIELD_WINDOW_COLOR), windowColor)
        bundle.putInt(keyForField(FIELD_VERTICAL_TYPE), verticalType)
        bundle.putFloat(keyForField(FIELD_SHEAR_DEGREES), shearDegrees)
        return bundle
    }

    private fun fromBundle(bundle: Bundle): Cue {
        val builder = Builder()
        val text = bundle.getCharSequence(keyForField(FIELD_TEXT))
        if (text != null) {
            builder.setText(text)
        }
        val textAlignment = bundle.getSerializable(keyForField(FIELD_TEXT_ALIGNMENT)) as Layout.Alignment?
        if (textAlignment != null) {
            builder.setTextAlignment(textAlignment)
        }
        val multiRowAlignment = bundle.getSerializable(keyForField(FIELD_MULTI_ROW_ALIGNMENT)) as Layout.Alignment?
        if (multiRowAlignment != null) {
            builder.setMultiRowAlignment(multiRowAlignment)
        }
        val bitmap = bundle.getParcelable<Bitmap>(keyForField(FIELD_BITMAP))
        if (bitmap != null) {
            builder.setBitmap(bitmap)
        }
        if (bundle.containsKey(keyForField(FIELD_LINE)) && bundle.containsKey(keyForField(FIELD_LINE_TYPE))) {
            builder.setLine(bundle.getFloat(keyForField(FIELD_LINE)), bundle.getInt(keyForField(FIELD_LINE_TYPE)))
        }
        if (bundle.containsKey(keyForField(FIELD_LINE_ANCHOR))) {
            builder.setLineAnchor(bundle.getInt(keyForField(FIELD_LINE_ANCHOR)))
        }
        if (bundle.containsKey(keyForField(FIELD_POSITION))) {
            builder.setPosition(bundle.getFloat(keyForField(FIELD_POSITION)))
        }
        if (bundle.containsKey(keyForField(FIELD_POSITION_ANCHOR))) {
            builder.setPositionAnchor(bundle.getInt(keyForField(FIELD_POSITION_ANCHOR)))
        }
        if (bundle.containsKey(keyForField(FIELD_TEXT_SIZE)) && bundle.containsKey(keyForField(FIELD_TEXT_SIZE_TYPE))) {
            builder.setTextSize(bundle.getFloat(keyForField(FIELD_TEXT_SIZE)), bundle.getInt(keyForField(FIELD_TEXT_SIZE_TYPE)))
        }
        if (bundle.containsKey(keyForField(FIELD_SIZE))) {
            builder.setSize(bundle.getFloat(keyForField(FIELD_SIZE)))
        }
        if (bundle.containsKey(keyForField(FIELD_BITMAP_HEIGHT))) {
            builder.setBitmapHeight(bundle.getFloat(keyForField(FIELD_BITMAP_HEIGHT)))
        }
        if (bundle.containsKey(keyForField(FIELD_WINDOW_COLOR))) {
            builder.setWindowColor(bundle.getInt(keyForField(FIELD_WINDOW_COLOR)))
        }
        if (!bundle.getBoolean(keyForField(FIELD_WINDOW_COLOR_SET),  /* defaultValue= */false)) {
            builder.clearWindowColor()
        }
        if (bundle.containsKey(keyForField(FIELD_VERTICAL_TYPE))) {
            builder.setVerticalType(bundle.getInt(keyForField(FIELD_VERTICAL_TYPE)))
        }
        if (bundle.containsKey(keyForField(FIELD_SHEAR_DEGREES))) {
            builder.setShearDegrees(bundle.getFloat(keyForField(FIELD_SHEAR_DEGREES)))
        }
        return builder.build()!!
    }

    private fun keyForField(@FieldNumber field: Int): String? {
        return field.toString(Character.MAX_RADIX)
    }
}