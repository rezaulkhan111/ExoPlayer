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
package com.google.android.exoplayer2.ext.media2

import android.net.Uri
import androidx.media2.common.CallbackMediaItem
import androidx.media2.common.FileMediaItem
import androidx.media2.common.MediaItem
import androidx.media2.common.UriMediaItem
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem.ClippingConfiguration
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.util.Assertions

/**
 * Default implementation of [MediaItemConverter].
 *
 *
 * Note that [.getMetadata] can be overridden to fill in additional metadata when
 * converting [ExoPlayer MediaItems][MediaItem] to their AndroidX equivalents.
 */
class DefaultMediaItemConverter : MediaItemConverter {
    override fun convertToExoPlayerMediaItem(media2MediaItem: MediaItem): com.google.android.exoplayer2.MediaItem {
        check(media2MediaItem !is FileMediaItem) { "FileMediaItem isn't supported" }
        check(media2MediaItem !is CallbackMediaItem) { "CallbackMediaItem isn't supported" }
        var uri: Uri? = null
        var mediaId: String? = null
        var title: String? = null
        if (media2MediaItem is UriMediaItem) {
            uri = media2MediaItem.uri
        }
        val metadata = media2MediaItem.metadata
        if (metadata != null) {
            val uriString = metadata.getString(androidx.media2.common.MediaMetadata.METADATA_KEY_MEDIA_URI)
            mediaId = metadata.getString(androidx.media2.common.MediaMetadata.METADATA_KEY_MEDIA_ID)
            if (uri == null) {
                if (uriString != null) {
                    uri = Uri.parse(uriString)
                } else if (mediaId != null) {
                    uri = Uri.parse("media2:///$mediaId")
                }
            }
            title = metadata.getString(androidx.media2.common.MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            if (title == null) {
                title = metadata.getString(androidx.media2.common.MediaMetadata.METADATA_KEY_TITLE)
            }
        }
        if (uri == null) {
            // Generate a URI to make it non-null. If not, then the tag passed to setTag will be ignored.
            uri = Uri.parse("media2:///")
        }
        var startPositionMs = media2MediaItem.startPosition
        if (startPositionMs == MediaItem.POSITION_UNKNOWN) {
            startPositionMs = 0
        }
        var endPositionMs = media2MediaItem.endPosition
        if (endPositionMs == MediaItem.POSITION_UNKNOWN) {
            endPositionMs = C.TIME_END_OF_SOURCE
        }
        return com.google.android.exoplayer2.MediaItem.Builder()
                .setUri(uri)
                .setMediaId(mediaId ?: com.google.android.exoplayer2.MediaItem.DEFAULT_MEDIA_ID)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .setTag(media2MediaItem)
                .setClippingConfiguration(
                        ClippingConfiguration.Builder()
                                .setStartPositionMs(startPositionMs)
                                .setEndPositionMs(endPositionMs)
                                .build())
                .build()
    }

    override fun convertToMedia2MediaItem(exoPlayerMediaItem: com.google.android.exoplayer2.MediaItem): MediaItem {
        Assertions.checkNotNull(exoPlayerMediaItem)
        val localConfiguration = Assertions.checkNotNull(exoPlayerMediaItem.localConfiguration)
        val tag = localConfiguration.tag
        if (tag is MediaItem) {
            return tag
        }
        val metadata = getMetadata(exoPlayerMediaItem)
        val startPositionMs = exoPlayerMediaItem.clippingConfiguration.startPositionMs
        var endPositionMs = exoPlayerMediaItem.clippingConfiguration.endPositionMs
        if (endPositionMs == C.TIME_END_OF_SOURCE) {
            endPositionMs = MediaItem.POSITION_UNKNOWN
        }
        return MediaItem.Builder()
                .setMetadata(metadata)
                .setStartPosition(startPositionMs)
                .setEndPosition(endPositionMs)
                .build()
    }

    /**
     * Returns a [androidx.media2.common.MediaMetadata] corresponding to the given [ ].
     */
    protected fun getMetadata(exoPlayerMediaItem: com.google.android.exoplayer2.MediaItem): androidx.media2.common.MediaMetadata {
        val title = exoPlayerMediaItem.mediaMetadata.title
        val metadataBuilder = androidx.media2.common.MediaMetadata.Builder()
                .putString(androidx.media2.common.MediaMetadata.METADATA_KEY_MEDIA_ID, exoPlayerMediaItem.mediaId)
        if (title != null) {
            metadataBuilder.putString(androidx.media2.common.MediaMetadata.METADATA_KEY_TITLE, title.toString())
            metadataBuilder.putString(androidx.media2.common.MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title.toString())
        }
        return metadataBuilder.build()
    }
}