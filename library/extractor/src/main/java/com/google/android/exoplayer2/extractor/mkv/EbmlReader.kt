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
package com.google.android.exoplayer2.extractor.mkv

import com.google.android.exoplayer2.extractor.ExtractorInput
import java.io.IOException

/**
 * Event-driven EBML reader that delivers events to an [EbmlProcessor].
 *
 *
 * EBML can be summarized as a binary XML format somewhat similar to Protocol Buffers. It was
 * originally designed for the Matroska container format. More information about EBML and Matroska
 * is available [here](http://www.matroska.org/technical/specs/index.html).
 */
/* package */
internal interface EbmlReader {
    /**
     * Initializes the extractor with an [EbmlProcessor].
     *
     * @param processor An [EbmlProcessor] to process events.
     */
    fun init(processor: EbmlProcessor?)

    /**
     * Resets the state of the reader.
     *
     *
     * Subsequent calls to [.read] will start reading a new EBML structure
     * from scratch.
     */
    fun reset()

    /**
     * Reads from an [ExtractorInput], invoking an event callback if possible.
     *
     * @param input The [ExtractorInput] from which data should be read.
     * @return True if data can continue to be read. False if the end of the input was encountered.
     * @throws ParserException If parsing fails.
     * @throws IOException If an error occurs reading from the input.
     */
    @Throws(IOException::class)
    fun read(input: ExtractorInput?): Boolean
}