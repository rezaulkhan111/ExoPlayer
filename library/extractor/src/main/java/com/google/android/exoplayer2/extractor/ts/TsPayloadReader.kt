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
package com.google.android.exoplayer2.extractor.ts

import android.util.SparseArray
import androidx.annotation.IntDef
import com.google.android.exoplayer2.ParserException
import com.google.android.exoplayer2.extractor.ExtractorOutput
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator
import com.google.android.exoplayer2.util.ParsableByteArray
import com.google.android.exoplayer2.util.TimestampAdjuster
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/** Parses TS packet payload data.  */
interface TsPayloadReader {
    /** Factory of [TsPayloadReader] instances.  */
    interface Factory {
        /**
         * Returns the initial mapping from PIDs to payload readers.
         *
         *
         * This method allows the injection of payload readers for reserved PIDs, excluding PID 0.
         *
         * @return A [SparseArray] that maps PIDs to payload readers.
         */
        fun createInitialPayloadReaders(): SparseArray<TsPayloadReader?>?

        /**
         * Returns a [TsPayloadReader] for a given stream type and elementary stream information.
         * May return null if the stream type is not supported.
         *
         * @param streamType Stream type value as defined in the PMT entry or associated descriptors.
         * @param esInfo Information associated to the elementary stream provided in the PMT.
         * @return A [TsPayloadReader] for the packet stream carried by the provided pid, or
         * `null` if the stream is not supported.
         */
        fun createPayloadReader(streamType: Int, esInfo: EsInfo?): TsPayloadReader?
    }

    /** Holds information associated with a PMT entry.  */
    class EsInfo {
        var streamType = 0
        var language: String? = null
        var dvbSubtitleInfos: List<DvbSubtitleInfo>? = null
        val descriptorBytes: ByteArray

        /**
         * @param streamType The type of the stream as defined by the [TsExtractor]`.TS_STREAM_TYPE_*`.
         * @param language The language of the stream, as defined by ISO/IEC 13818-1, section 2.6.18.
         * @param dvbSubtitleInfos Information about DVB subtitles associated to the stream.
         * @param descriptorBytes The descriptor bytes associated to the stream.
         */
        constructor(
            streamType: Int,
            language: String?,
            dvbSubtitleInfos: List<DvbSubtitleInfo>?,
            descriptorBytes: ByteArray?
        ) {
            this.streamType = streamType
            this.language = language
            this.dvbSubtitleInfos =
                if (dvbSubtitleInfos == null)
                    emptyList()
                else Collections.unmodifiableList(
                    dvbSubtitleInfos
                )
            this.descriptorBytes = descriptorBytes!!
        }
    }

    /**
     * Holds information about a DVB subtitle, as defined in ETSI EN 300 468 V1.11.1 section 6.2.41.
     */
    class DvbSubtitleInfo {

        var language: String? = null
        var type = 0
        val initializationData: ByteArray

        /**
         * @param language The ISO 639-2 three-letter language code.
         * @param type The subtitling type.
         * @param initializationData The composition and ancillary page ids.
         */
        constructor(language: String?, type: Int, initializationData: ByteArray) {
            this.language = language
            this.type = type
            this.initializationData = initializationData
        }
    }

    /** Generates track ids for initializing [TsPayloadReader]s' [TrackOutput]s.  */
    class TrackIdGenerator {
        companion object {
            private const val ID_UNSET = Int.MIN_VALUE
        }

        private var formatIdPrefix: String? = null
        private var firstTrackId = 0
        private var trackIdIncrement = 0
        private var trackId = 0
        private var formatId: String? = null

        constructor(firstTrackId: Int, trackIdIncrement: Int) : this(
            ID_UNSET,
            firstTrackId,
            trackIdIncrement
        ) {

        }

        constructor(programNumber: Int, firstTrackId: Int, trackIdIncrement: Int) {
            formatIdPrefix = if (programNumber != ID_UNSET) "$programNumber/" else ""
            this.firstTrackId = firstTrackId
            this.trackIdIncrement = trackIdIncrement
            trackId = ID_UNSET
            formatId = ""
        }

        /**
         * Generates a new set of track and track format ids. Must be called before `get*`
         * methods.
         */
        fun generateNewId() {
            trackId = if (trackId == ID_UNSET) firstTrackId else trackId + trackIdIncrement
            formatId = formatIdPrefix + trackId
        }

        /**
         * Returns the last generated track id. Must be called after the first [.generateNewId]
         * call.
         *
         * @return The last generated track id.
         */
        fun getTrackId(): Int {
            maybeThrowUninitializedError()
            return trackId
        }

        /**
         * Returns the last generated format id, with the format `"programNumber/trackId"`. If no
         * `programNumber` was provided, the `trackId` alone is used as format id. Must be
         * called after the first [.generateNewId] call.
         *
         * @return The last generated format id, with the format `"programNumber/trackId"`. If no
         * `programNumber` was provided, the `trackId` alone is used as format id.
         */
        fun getFormatId(): String? {
            maybeThrowUninitializedError()
            return formatId
        }

        private fun maybeThrowUninitializedError() {
            check(trackId != ID_UNSET) { "generateNewId() must be called before retrieving ids." }
        }
    }

    /**
     * Contextual flags indicating the presence of indicators in the TS packet or PES packet headers.
     *
     *
     * The individual flag values are:
     *
     *
     *  * [.FLAG_PAYLOAD_UNIT_START_INDICATOR]
     *  * [.FLAG_RANDOM_ACCESS_INDICATOR]
     *  * [.FLAG_DATA_ALIGNMENT_INDICATOR]
     *
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(
        flag = true,
        value = [FLAG_PAYLOAD_UNIT_START_INDICATOR, FLAG_RANDOM_ACCESS_INDICATOR, FLAG_DATA_ALIGNMENT_INDICATOR]
    )
    annotation class Flags

    /**
     * Initializes the payload reader.
     *
     * @param timestampAdjuster A timestamp adjuster for offsetting and scaling sample timestamps.
     * @param extractorOutput The [ExtractorOutput] that receives the extracted data.
     * @param idGenerator A [PesReader.TrackIdGenerator] that generates unique track ids for the
     * [TrackOutput]s.
     */
    fun init(
        timestampAdjuster: TimestampAdjuster?,
        extractorOutput: ExtractorOutput?,
        idGenerator: TrackIdGenerator?
    )

    /**
     * Notifies the reader that a seek has occurred.
     *
     *
     * Following a call to this method, the data passed to the next invocation of [.consume]
     * will not be a continuation of the data that was previously passed. Hence the reader should
     * reset any internal state.
     */
    fun seek()

    /**
     * Consumes the payload of a TS packet.
     *
     * @param data The TS packet. The position will be set to the start of the payload.
     * @param flags See [Flags].
     * @throws ParserException If the payload could not be parsed.
     */
    @Throws(ParserException::class)
    fun consume(data: ParsableByteArray?, @Flags flags: Int)

    companion object {
        /** Indicates the presence of the payload_unit_start_indicator in the TS packet header.  */
        const val FLAG_PAYLOAD_UNIT_START_INDICATOR = 1

        /**
         * Indicates the presence of the random_access_indicator in the TS packet header adaptation field.
         */
        const val FLAG_RANDOM_ACCESS_INDICATOR = 1 shl 1

        /** Indicates the presence of the data_alignment_indicator in the PES header.  */
        const val FLAG_DATA_ALIGNMENT_INDICATOR = 1 shl 2
    }
}