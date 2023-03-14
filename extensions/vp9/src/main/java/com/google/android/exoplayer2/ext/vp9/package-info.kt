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
package com.google.android.exoplayer2.ext.vp9

import com.google.android.exoplayer2.decoder.CryptoConfig
import com.google.android.exoplayer2.decoder.SimpleDecoder
import com.google.android.exoplayer2.decoder.DecoderInputBuffer
import com.google.android.exoplayer2.decoder.VideoDecoderOutputBuffer
import com.google.android.exoplayer2.ext.vp9.VpxDecoderException
import com.google.android.exoplayer2.C.VideoOutputMode
import com.google.android.exoplayer2.ext.vp9.VpxLibrary
import com.google.android.exoplayer2.decoder.DecoderOutputBuffer
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.ext.vp9.VpxDecoder
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.C.CryptoType
import com.google.android.exoplayer2.video.VideoRendererEventListener
import com.google.android.exoplayer2.video.DecoderVideoRenderer
import com.google.android.exoplayer2.ext.vp9.LibvpxVideoRenderer
import com.google.android.exoplayer2.RendererCapabilities
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.TraceUtil
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
