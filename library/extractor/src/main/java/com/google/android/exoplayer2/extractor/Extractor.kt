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
package com.google.android.exoplayer2.extractor

import androidx.annotation.IntDef
import com.google.android.exoplayer2.C
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Extracts media data from a container format.  */
interface Extractor {
    /**
     * Result values that can be returned by [.read]. One of
     * [.RESULT_CONTINUE], [.RESULT_SEEK] or [.RESULT_END_OF_INPUT].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(value = [RESULT_CONTINUE, RESULT_SEEK, RESULT_END_OF_INPUT])
    annotation class ReadResult

    /**
     * Returns whether this extractor can extract samples from the [ExtractorInput], which must
     * provide data from the start of the stream.
     *
     *
     * If `true` is returned, the `input`'s reading position may have been modified.
     * Otherwise, only its peek position may have been modified.
     *
     * @param input The [ExtractorInput] from which data should be peeked/read.
     * @return Whether this extractor can read the provided input.
     * @throws IOException If an error occurred reading from the input.
     */
    @Throws(IOException::class)
    fun sniff(input: ExtractorInput?): Boolean

    /**
     * Initializes the extractor with an [ExtractorOutput]. Called at most once.
     *
     * @param output An [ExtractorOutput] to receive extracted data.
     */
    fun init(output: ExtractorOutput?)

    /**
     * Extracts data read from a provided [ExtractorInput]. Must not be called before [ ][.init].
     *
     *
     * A single call to this method will block until some progress has been made, but will not
     * block for longer than this. Hence each call will consume only a small amount of input data.
     *
     *
     * In the common case, [.RESULT_CONTINUE] is returned to indicate that the [ ] passed to the next read is required to provide data continuing from the
     * position in the stream reached by the returning call. If the extractor requires data to be
     * provided from a different position, then that position is set in `seekPosition` and
     * [.RESULT_SEEK] is returned. If the extractor reached the end of the data provided by the
     * [ExtractorInput], then [.RESULT_END_OF_INPUT] is returned.
     *
     *
     * When this method throws an [IOException], extraction may continue by providing an
     * [ExtractorInput] with an unchanged [read position][ExtractorInput.getPosition] to
     * a subsequent call to this method.
     *
     * @param input The [ExtractorInput] from which data should be read.
     * @param seekPosition If [.RESULT_SEEK] is returned, this holder is updated to hold the
     * position of the required data.
     * @return One of the `RESULT_` values defined in this interface.
     * @throws IOException If an error occurred reading from or parsing the input.
     */
    @Throws(IOException::class)
    fun read(input: ExtractorInput?, seekPosition: PositionHolder?): @ReadResult Int

    /**
     * Notifies the extractor that a seek has occurred.
     *
     *
     * Following a call to this method, the [ExtractorInput] passed to the next invocation of
     * [.read] is required to provide data starting from `position` in the stream. Valid random access positions are the start of the stream and
     * positions that can be obtained from any [SeekMap] passed to the [ExtractorOutput].
     *
     * @param position The byte offset in the stream from which data will be provided.
     * @param timeUs The seek time in microseconds.
     */
    fun seek(position: Long, timeUs: Long)

    /** Releases all kept resources.  */
    fun release()

    companion object {
        /**
         * Returned by [.read] if the [ExtractorInput] passed
         * to the next [.read] is required to provide data
         * continuing from the position in the stream reached by the returning call.
         */
        const val RESULT_CONTINUE = 0

        /**
         * Returned by [.read] if the [ExtractorInput] passed
         * to the next [.read] is required to provide data starting
         * from a specified position in the stream.
         */
        const val RESULT_SEEK = 1

        /**
         * Returned by [.read] if the end of the [ ] was reached. Equal to [C.RESULT_END_OF_INPUT].
         */
        const val RESULT_END_OF_INPUT = C.RESULT_END_OF_INPUT
    }
}