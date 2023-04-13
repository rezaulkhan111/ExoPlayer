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
package com.google.android.exoplayer2.metadata

/** Decodes metadata from binary data.  */
interface MetadataDecoder {
    /**
     * Decodes a [Metadata] element from the provided input buffer.
     *
     *
     * Respects [ByteBuffer.limit] of `inputBuffer.data`, but assumes [ ][ByteBuffer.position] and [ByteBuffer.arrayOffset] are both zero and [ ][ByteBuffer.hasArray] is true.
     *
     * @param inputBuffer The input buffer to decode.
     * @return The decoded metadata object, or `null` if the metadata could not be decoded or if
     * [MetadataInputBuffer.isDecodeOnly] was set on the input buffer.
     */
    fun decode(inputBuffer: MetadataInputBuffer?): Metadata?
}