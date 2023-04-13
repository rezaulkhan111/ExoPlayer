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
package com.google.android.exoplayer2.source.rtsp.reader

import com.google.android.exoplayer2.ParserException
import com.google.android.exoplayer2.extractor.ExtractorOutput
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat
import com.google.android.exoplayer2.util.ParsableByteArray

/** Extracts media samples from the payload of received RTP packets.  */ /* package */
interface RtpPayloadReader {

    /** Factory of [RtpPayloadReader] instances.  */
    interface Factory {
        /**
         * Returns a [RtpPayloadReader] for a given [RtpPayloadFormat].
         *
         * @param payloadFormat The [RtpPayloadFormat] of the RTP stream.
         * @return A [RtpPayloadReader] for the packet stream, or `null` if the stream
         * format is not supported.
         */
        fun createPayloadReader(payloadFormat: RtpPayloadFormat?): RtpPayloadReader?
    }

    /**
     * Initializes the reader by providing its output and track id.
     *
     * @param extractorOutput The [ExtractorOutput] instance that receives the extracted data.
     * @param trackId The track identifier to set on the format.
     */
    fun createTracks(extractorOutput: ExtractorOutput?, trackId: Int)

    /**
     * This method should be called on reading the first packet in a stream of incoming packets.
     *
     * @param timestamp The timestamp associated with the first received RTP packet. This number has
     * no unit, the duration conveyed by it depends on the frequency of the media that the RTP
     * packet is carrying.
     * @param sequenceNumber The sequence associated with the first received RTP packet.
     */
    fun onReceivingFirstPacket(timestamp: Long, sequenceNumber: Int)

    /**
     * Consumes the payload from the an RTP packet.
     *
     * @param data The RTP payload to consume.
     * @param timestamp The timestamp of the RTP packet that transmitted the data. This number has no
     * unit, the duration conveyed by it depends on the frequency of the media that the RTP packet
     * is carrying.
     * @param sequenceNumber The sequence number of the RTP packet.
     * @param rtpMarker The marker bit of the RTP packet. The interpretation of this bit is specific
     * to each payload format.
     * @throws ParserException If the data could not be parsed.
     */
    @Throws(ParserException::class)
    fun consume(data: ParsableByteArray?, timestamp: Long, sequenceNumber: Int, rtpMarker: Boolean)

    /**
     * Seeks the reader.
     *
     *
     * This method must only be invoked after the PLAY request for seeking is acknowledged by the
     * RTSP server.
     *
     * @param nextRtpTimestamp The timestamp of the first packet to arrive after seek.
     * @param timeUs The server acknowledged seek time in microseconds.
     */
    fun seek(nextRtpTimestamp: Long, timeUs: Long)
}