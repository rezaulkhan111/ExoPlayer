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

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** [XmlPullParser] utility methods.  */
object XmlPullParserUtil {
    /**
     * Returns whether the current event is an end tag with the specified name.
     *
     * @param xpp The [XmlPullParser] to query.
     * @param name The specified name.
     * @return Whether the current event is an end tag with the specified name.
     * @throws XmlPullParserException If an error occurs querying the parser.
     */
    @Throws(XmlPullParserException::class)
    fun isEndTag(xpp: XmlPullParser, name: String): Boolean {
        return isEndTag(xpp) && (xpp.getName() == name)
    }

    /**
     * Returns whether the current event is an end tag.
     *
     * @param xpp The [XmlPullParser] to query.
     * @return Whether the current event is an end tag.
     * @throws XmlPullParserException If an error occurs querying the parser.
     */
    @Throws(XmlPullParserException::class)
    fun isEndTag(xpp: XmlPullParser): Boolean {
        return xpp.getEventType() == XmlPullParser.END_TAG
    }

    /**
     * Returns whether the current event is a start tag with the specified name.
     *
     * @param xpp The [XmlPullParser] to query.
     * @param name The specified name.
     * @return Whether the current event is a start tag with the specified name.
     * @throws XmlPullParserException If an error occurs querying the parser.
     */
    @Throws(XmlPullParserException::class)
    fun isStartTag(xpp: XmlPullParser, name: String): Boolean {
        return isStartTag(xpp) && (xpp.getName() == name)
    }

    /**
     * Returns whether the current event is a start tag.
     *
     * @param xpp The [XmlPullParser] to query.
     * @return Whether the current event is a start tag.
     * @throws XmlPullParserException If an error occurs querying the parser.
     */
    @Throws(XmlPullParserException::class)
    fun isStartTag(xpp: XmlPullParser): Boolean {
        return xpp.getEventType() == XmlPullParser.START_TAG
    }

    /**
     * Returns whether the current event is a start tag with the specified name. If the current event
     * has a raw name then its prefix is stripped before matching.
     *
     * @param xpp The [XmlPullParser] to query.
     * @param name The specified name.
     * @return Whether the current event is a start tag with the specified name.
     * @throws XmlPullParserException If an error occurs querying the parser.
     */
    @Throws(XmlPullParserException::class)
    fun isStartTagIgnorePrefix(xpp: XmlPullParser, name: String): Boolean {
        return isStartTag(xpp) && (stripPrefix(xpp.getName()) == name)
    }

    /**
     * Returns the value of an attribute of the current start tag.
     *
     * @param xpp The [XmlPullParser] to query.
     * @param attributeName The name of the attribute.
     * @return The value of the attribute, or null if the current event is not a start tag or if no
     * such attribute was found.
     */
    fun getAttributeValue(xpp: XmlPullParser, attributeName: String): String? {
        val attributeCount: Int = xpp.attributeCount
        for (i in 0 until attributeCount) {
            if ((xpp.getAttributeName(i) == attributeName)) {
                return xpp.getAttributeValue(i)
            }
        }
        return null
    }

    /**
     * Returns the value of an attribute of the current start tag. Any raw attribute names in the
     * current start tag have their prefixes stripped before matching.
     *
     * @param xpp The [XmlPullParser] to query.
     * @param attributeName The name of the attribute.
     * @return The value of the attribute, or null if the current event is not a start tag or if no
     * such attribute was found.
     */
    fun getAttributeValueIgnorePrefix(xpp: XmlPullParser, attributeName: String): String? {
        val attributeCount: Int = xpp.attributeCount
        for (i in 0 until attributeCount) {
            if ((stripPrefix(xpp.getAttributeName(i)) == attributeName)) {
                return xpp.getAttributeValue(i)
            }
        }
        return null
    }

    private fun stripPrefix(name: String): String {
        val prefixSeparatorIndex: Int = name.indexOf(':')
        return if (prefixSeparatorIndex == -1) name else name.substring(prefixSeparatorIndex + 1)
    }
}