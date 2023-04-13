/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.mkv

import androidx.annotation.IntDef
import com.google.android.exoplayer2.ParserException
import com.google.android.exoplayer2.extractor.ExtractorInput
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Defines EBML element IDs/types and processes events.  */
interface EbmlProcessor {
    /**
     * EBML element types. One of [.ELEMENT_TYPE_UNKNOWN], [.ELEMENT_TYPE_MASTER], [ ][.ELEMENT_TYPE_UNSIGNED_INT], [.ELEMENT_TYPE_STRING], [.ELEMENT_TYPE_BINARY] or
     * [.ELEMENT_TYPE_FLOAT].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [ELEMENT_TYPE_UNKNOWN, ELEMENT_TYPE_MASTER, ELEMENT_TYPE_UNSIGNED_INT, ELEMENT_TYPE_STRING, ELEMENT_TYPE_BINARY, ELEMENT_TYPE_FLOAT])
    annotation class ElementType

    /**
     * Maps an element ID to a corresponding type.
     *
     *
     * If [.ELEMENT_TYPE_UNKNOWN] is returned then the element is skipped. Note that all
     * children of a skipped element are also skipped.
     *
     * @param id The element ID to map.
     * @return One of [.ELEMENT_TYPE_UNKNOWN], [.ELEMENT_TYPE_MASTER], [     ][.ELEMENT_TYPE_UNSIGNED_INT], [.ELEMENT_TYPE_STRING], [.ELEMENT_TYPE_BINARY] and
     * [.ELEMENT_TYPE_FLOAT].
     */
    fun getElementType(id: Int): @ElementType Int

    /**
     * Checks if the given id is that of a level 1 element.
     *
     * @param id The element ID.
     * @return Whether the given id is that of a level 1 element.
     */
    fun isLevel1Element(id: Int): Boolean

    /**
     * Called when the start of a master element is encountered.
     *
     *
     * Following events should be considered as taking place within this element until a matching
     * call to [.endMasterElement] is made.
     *
     *
     * Note that it is possible for another master element of the same element ID to be nested
     * within itself.
     *
     * @param id The element ID.
     * @param contentPosition The position of the start of the element's content in the stream.
     * @param contentSize The size of the element's content in bytes.
     * @throws ParserException If a parsing error occurs.
     */
    @Throws(ParserException::class)
    fun startMasterElement(id: Int, contentPosition: Long, contentSize: Long)

    /**
     * Called when the end of a master element is encountered.
     *
     * @param id The element ID.
     * @throws ParserException If a parsing error occurs.
     */
    @Throws(ParserException::class)
    fun endMasterElement(id: Int)

    /**
     * Called when an integer element is encountered.
     *
     * @param id The element ID.
     * @param value The integer value that the element contains.
     * @throws ParserException If a parsing error occurs.
     */
    @Throws(ParserException::class)
    fun integerElement(id: Int, value: Long)

    /**
     * Called when a float element is encountered.
     *
     * @param id The element ID.
     * @param value The float value that the element contains
     * @throws ParserException If a parsing error occurs.
     */
    @Throws(ParserException::class)
    fun floatElement(id: Int, value: Double)

    /**
     * Called when a string element is encountered.
     *
     * @param id The element ID.
     * @param value The string value that the element contains.
     * @throws ParserException If a parsing error occurs.
     */
    @Throws(ParserException::class)
    fun stringElement(id: Int, value: String?)

    /**
     * Called when a binary element is encountered.
     *
     *
     * The element header (containing the element ID and content size) will already have been read.
     * Implementations are required to consume the whole remainder of the element, which is `contentSize` bytes in length, before returning. Implementations are permitted to fail (by
     * throwing an exception) having partially consumed the data, however if they do this, they must
     * consume the remainder of the content when called again.
     *
     * @param id The element ID.
     * @param contentsSize The element's content size.
     * @param input The [ExtractorInput] from which data should be read.
     * @throws ParserException If a parsing error occurs.
     * @throws IOException If an error occurs reading from the input.
     */
    @Throws(IOException::class)
    fun binaryElement(id: Int, contentsSize: Int, input: ExtractorInput?)

    companion object {
        /** Type for unknown elements.  */
        const val ELEMENT_TYPE_UNKNOWN = 0

        /** Type for elements that contain child elements.  */
        const val ELEMENT_TYPE_MASTER = 1

        /** Type for integer value elements of up to 8 bytes.  */
        const val ELEMENT_TYPE_UNSIGNED_INT = 2

        /** Type for string elements.  */
        const val ELEMENT_TYPE_STRING = 3

        /** Type for binary elements.  */
        const val ELEMENT_TYPE_BINARY = 4

        /** Type for IEEE floating point value elements of either 4 or 8 bytes.  */
        const val ELEMENT_TYPE_FLOAT = 5
    }
}