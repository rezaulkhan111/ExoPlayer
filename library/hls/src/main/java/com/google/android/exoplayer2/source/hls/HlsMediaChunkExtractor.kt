/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls

import com.google.android.exoplayer2.extractor.ExtractorInput
import com.google.android.exoplayer2.extractor.ExtractorOutput
import java.io.IOException

/** Extracts samples and track [Formats][Format] from [HlsMediaChunks][HlsMediaChunk].  */
interface HlsMediaChunkExtractor {

    /**
     * Initializes the extractor with an [ExtractorOutput]. Called at most once.
     *
     * @param extractorOutput An [ExtractorOutput] to receive extracted data.
     */
    fun init(extractorOutput: ExtractorOutput?)

    /**
     * Extracts data read from a provided [ExtractorInput]. Must not be called before [ ][.init].
     *
     *
     * A single call to this method will block until some progress has been made, but will not
     * block for longer than this. Hence each call will consume only a small amount of input data.
     *
     *
     * When this method throws an [IOException], extraction may continue by providing an
     * [ExtractorInput] with an unchanged [read position][ExtractorInput.getPosition] to
     * a subsequent call to this method.
     *
     * @param extractorInput The input to read from.
     * @return Whether there is any data left to extract. Returns false if the end of input has been
     * reached.
     * @throws IOException If an error occurred reading from or parsing the input.
     */
    @Throws(IOException::class)
    fun read(extractorInput: ExtractorInput?): Boolean

    /** Returns whether this is a packed audio extractor, as defined in RFC 8216, Section 3.4.  */
    fun isPackedAudioExtractor(): Boolean

    /** Returns whether this instance can be used for extracting multiple continuous segments.  */
    fun isReusable(): Boolean

    /**
     * Returns a new instance for extracting the same type of media as this one. Can only be called on
     * instances that are not [reusable][.isReusable].
     */
    fun recreate(): HlsMediaChunkExtractor?

    /**
     * Resets the sample parsing state.
     *
     *
     * Resetting the parsing state allows support for Fragmented MP4 EXT-X-I-FRAME-STREAM-INF
     * segments. EXT-X-I-FRAME-STREAM-INF segments are truncated to include only a leading key frame.
     * After parsing said keyframe, an extractor may reach an unexpected end of file. By resetting its
     * state, we can continue feeding samples from the following segments to the extractor. See [#7512](https://github.com/google/ExoPlayer/issues/7512) for context.
     */
    fun onTruncatedSegmentParsed()
}