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

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.google.android.exoplayer2.BaseRenderer
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.RendererCapabilities
import com.google.android.exoplayer2.util.Assertions.checkState
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.util.Util.castNonNull
import java.util.ArrayList
import org.checkerframework.dataflow.qual.SideEffectFree

/**
 * A renderer for metadata.
 *
 * <p>The renderer can be configured to render metadata as soon as they are available using [ ][MetadataRenderer].
 */
class MetadataRenderer @JvmOverloads constructor(
  private val output: MetadataOutput,
  outputLooper: Looper?,
  private val decoderFactory: MetadataDecoderFactory = MetadataDecoderFactory.DEFAULT,
  private val outputMetadataEarly: Boolean = false
) : BaseRenderer(C.TRACK_TYPE_METADATA), Handler.Callback {

  private val outputHandler: Handler? =
    if (outputLooper == null) null else Util.createHandler(outputLooper, /* callback= */ this)
  private val buffer: MetadataInputBuffer = MetadataInputBuffer()

  private var decoder: MetadataDecoder? = null
  private var inputStreamEnded = false
  private var outputStreamEnded = false
  private var subsampleOffsetUs: Long = 0
  private var pendingMetadata: Metadata? = null
  private var outputStreamOffsetUs: Long = C.TIME_UNSET

  override fun getName(): String {
    return TAG
  }

  override fun supportsFormat(format: Format?): Int {
    return if (format != null && decoderFactory.supportsFormat(format)) {
      RendererCapabilities.create(
        if (format.cryptoType == C.CRYPTO_TYPE_NONE) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_DRM
      )
    } else {
      RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
    }
  }

  override fun onStreamChanged(formats: Array<Format>, startPositionUs: Long, offsetUs: Long) {
    decoder = decoderFactory.createDecoder(formats[0])
    val currentPendingMetadata = pendingMetadata
    if (currentPendingMetadata != null) {
      pendingMetadata =
        currentPendingMetadata.copyWithPresentationTimeUs(
          currentPendingMetadata.presentationTimeUs + outputStreamOffsetUs - offsetUs
        )
    }
    outputStreamOffsetUs = offsetUs
  }

  override fun onPositionReset(positionUs: Long, joining: Boolean) {
    pendingMetadata = null
    inputStreamEnded = false
    outputStreamEnded = false
  }

  override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
    var working = true
    while (working) {
      readMetadata()
      working = outputMetadata(positionUs)
    }
  }

  /**
   * Iterates through `metadata.entries` and checks each one to see if contains wrapped
   * metadata. If it does, then we recursively decode the wrapped metadata. If it doesn't (recursion
   * base-case), we add the [Metadata.Entry] to `decodedEntries` (output parameter).
   */
  private fun decodeWrappedMetadata(metadata: Metadata, decodedEntries: MutableList<Metadata.Entry>) {
    for (i in 0 until metadata.length()) {
      val entry = metadata[i] ?: continue
      val wrappedMetadataFormat = entry.wrappedMetadataFormat
      if (wrappedMetadataFormat != null && decoderFactory.supportsFormat(wrappedMetadataFormat)) {
        val wrappedMetadataDecoder = decoderFactory.createDecoder(wrappedMetadataFormat)
        // wrappedMetadataFormat != null so wrappedMetadataBytes must be non-null too.
        val wrappedMetadataBytes = checkNotNull(entry.wrappedMetadataBytes)
        buffer.clear()
        buffer.ensureSpaceForWrite(wrappedMetadataBytes.size)
        castNonNull(buffer.data)!!.put(wrappedMetadataBytes)
        buffer.flip()
        val innerMetadata = wrappedMetadataDecoder.decode(buffer)
        if (innerMetadata != null) {
          // The decoding succeeded, so we'll try another level of unwrapping.
          decodeWrappedMetadata(innerMetadata, decodedEntries)
        }
      } else {
        // Entry doesn't contain any wrapped metadata, so output it directly.
        decodedEntries.add(entry)
      }
    }
  }

  override fun onDisabled() {
    pendingMetadata = null
    decoder = null
    outputStreamOffsetUs = C.TIME_UNSET
  }

  override fun isEnded(): Boolean {
    return outputStreamEnded
  }

  override fun isReady(): Boolean {
    return true
  }

  override fun handleMessage(msg: Message): Boolean {
    return when (msg.what) {
      MSG_INVOKE_RENDERER -> {
        invokeRendererInternal(msg.obj as Metadata)
        true
      }
      else -> {
        // Should never happen.
        throw IllegalStateException()
      }
    }
  }

  private fun readMetadata() {
    if (!inputStreamEnded && pendingMetadata == null) {
      buffer.clear()
      val formatHolder = formatHolder
      val result = readSource(formatHolder, buffer, /* readFlags= */ 0)
      if (result == C.RESULT_BUFFER_READ) {
        if (buffer.isEndOfStream) {
          inputStreamEnded = true
        } else {
          buffer.subsampleOffsetUs = subsampleOffsetUs
          buffer.flip()
          val metadata = castNonNull(decoder)!!.decode(buffer)
          if (metadata != null) {
            val entries = ArrayList<Metadata.Entry>(metadata.length())
            decodeWrappedMetadata(metadata, entries)
            if (entries.isNotEmpty()) {
              val expandedMetadata =
                Metadata(getPresentationTimeUs(buffer.timeUs), entries)
              pendingMetadata = expandedMetadata
            }
          }
        }
      } else if (result == C.RESULT_FORMAT_READ) {
        subsampleOffsetUs = checkNotNull(formatHolder.format).subsampleOffsetUs
      }
    }
  }

  private fun outputMetadata(positionUs: Long): Boolean {
    var didOutput = false
    val currentPendingMetadata = pendingMetadata
    if (currentPendingMetadata != null &&
      (outputMetadataEarly ||
        currentPendingMetadata.presentationTimeUs <= getPresentationTimeUs(positionUs))
    ) {
      invokeRenderer(currentPendingMetadata)
      pendingMetadata = null
      didOutput = true
    }
    if (inputStreamEnded && pendingMetadata == null) {
      outputStreamEnded = true
    }
    return didOutput
  }

  private fun invokeRenderer(metadata: Metadata) {
    if (outputHandler != null) {
      outputHandler.obtainMessage(MSG_INVOKE_RENDERER, metadata).sendToTarget()
    } else {
      invokeRendererInternal(metadata)
    }
  }

  private fun invokeRendererInternal(metadata: Metadata) {
    output.onMetadata(metadata)
  }

  @SideEffectFree
  private fun getPresentationTimeUs(positionUs: Long): Long {
    checkState(positionUs != C.TIME_UNSET)
    checkState(outputStreamOffsetUs != C.TIME_UNSET)
    return positionUs - outputStreamOffsetUs
  }

  companion object {
    private const val TAG = "MetadataRenderer"
    private const val MSG_INVOKE_RENDERER = 0
  }
}
