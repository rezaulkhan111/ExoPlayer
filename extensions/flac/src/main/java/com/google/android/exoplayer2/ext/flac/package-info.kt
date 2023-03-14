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
package com.google.android.exoplayer2.ext.flac

import com.google.android.exoplayer2.decoder.SimpleDecoder
import com.google.android.exoplayer2.decoder.DecoderInputBuffer
import com.google.android.exoplayer2.decoder.SimpleDecoderOutputBuffer
import com.google.android.exoplayer2.ext.flac.FlacDecoderException
import com.google.android.exoplayer2.extractor.FlacStreamMetadata
import com.google.android.exoplayer2.ext.flac.FlacDecoderJni
import com.google.android.exoplayer2.decoder.DecoderOutputBuffer
import com.google.android.exoplayer2.ext.flac.FlacDecoderJni.FlacFrameDecodeException
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.ext.flac.FlacLibrary
import com.google.android.exoplayer2.extractor.Extractor
import androidx.annotation.IntDef
import com.google.android.exoplayer2.util.ParsableByteArray
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import com.google.android.exoplayer2.extractor.ExtractorOutput
import com.google.android.exoplayer2.extractor.TrackOutput
import com.google.android.exoplayer2.ext.flac.FlacBinarySearchSeeker.OutputFrameHolder
import com.google.android.exoplayer2.extractor.ExtractorInput
import com.google.android.exoplayer2.extractor.FlacMetadataReader
import com.google.android.exoplayer2.extractor.PositionHolder
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import com.google.android.exoplayer2.util.Assertions
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import com.google.android.exoplayer2.extractor.SeekMap.SeekPoints
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.ext.flac.FlacExtractor.FlacSeekMap
import com.google.android.exoplayer2.extractor.SeekMap.Unseekable
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.audio.DecoderAudioRenderer
import com.google.android.exoplayer2.ext.flac.FlacDecoder
import com.google.android.exoplayer2.audio.AudioRendererEventListener
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.ext.flac.LibflacAudioRenderer
import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.util.TraceUtil
import com.google.android.exoplayer2.extractor.BinarySearchSeeker
import com.google.android.exoplayer2.extractor.BinarySearchSeeker.SeekTimestampConverter
import com.google.android.exoplayer2.extractor.BinarySearchSeeker.TimestampSeeker
import com.google.android.exoplayer2.extractor.BinarySearchSeeker.TimestampSearchResult
