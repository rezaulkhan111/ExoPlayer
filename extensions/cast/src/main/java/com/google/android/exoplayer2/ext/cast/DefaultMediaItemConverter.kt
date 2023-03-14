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
package com.google.android.exoplayer2.ext.cast

import android.net.Uri
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.MediaItem.DrmConfiguration
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.gms.cast.*
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.common.images.WebImage
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/** Default [MediaItemConverter] implementation.  */
class DefaultMediaItemConverter : MediaItemConverter {
    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val mediaInfo = mediaQueueItem.media
        Assertions.checkNotNull(mediaInfo)
        val metadataBuilder = com.google.android.exoplayer2.MediaMetadata.Builder()
        val metadata = mediaInfo!!.metadata
        if (metadata != null) {
            if (metadata.containsKey(MediaMetadata.KEY_TITLE)) {
                metadataBuilder.setTitle(metadata.getString(MediaMetadata.KEY_TITLE))
            }
            if (metadata.containsKey(MediaMetadata.KEY_SUBTITLE)) {
                metadataBuilder.setSubtitle(metadata.getString(MediaMetadata.KEY_SUBTITLE))
            }
            if (metadata.containsKey(MediaMetadata.KEY_ARTIST)) {
                metadataBuilder.setArtist(metadata.getString(MediaMetadata.KEY_ARTIST))
            }
            if (metadata.containsKey(MediaMetadata.KEY_ALBUM_ARTIST)) {
                metadataBuilder.setAlbumArtist(metadata.getString(MediaMetadata.KEY_ALBUM_ARTIST))
            }
            if (metadata.containsKey(MediaMetadata.KEY_ALBUM_TITLE)) {
                metadataBuilder.setArtist(metadata.getString(MediaMetadata.KEY_ALBUM_TITLE))
            }
            if (!metadata.images.isEmpty()) {
                metadataBuilder.setArtworkUri(metadata.images[0].url)
            }
            if (metadata.containsKey(MediaMetadata.KEY_COMPOSER)) {
                metadataBuilder.setComposer(metadata.getString(MediaMetadata.KEY_COMPOSER))
            }
            if (metadata.containsKey(MediaMetadata.KEY_DISC_NUMBER)) {
                metadataBuilder.setDiscNumber(metadata.getInt(MediaMetadata.KEY_DISC_NUMBER))
            }
            if (metadata.containsKey(MediaMetadata.KEY_TRACK_NUMBER)) {
                metadataBuilder.setTrackNumber(metadata.getInt(MediaMetadata.KEY_TRACK_NUMBER))
            }
        }
        // `mediaQueueItem` came from `toMediaQueueItem()` so the custom JSON data must be set.
        return getMediaItem(
                Assertions.checkNotNull(mediaInfo.customData), metadataBuilder.build())
    }

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        Assertions.checkNotNull(mediaItem.localConfiguration)
        requireNotNull(mediaItem.localConfiguration!!.mimeType) { "The item must specify its mimeType" }
        val metadata = MediaMetadata(
                if (MimeTypes.isAudio(mediaItem.localConfiguration!!.mimeType)) MediaMetadata.MEDIA_TYPE_MUSIC_TRACK else MediaMetadata.MEDIA_TYPE_MOVIE)
        if (mediaItem.mediaMetadata.title != null) {
            metadata.putString(MediaMetadata.KEY_TITLE, mediaItem.mediaMetadata.title.toString())
        }
        if (mediaItem.mediaMetadata.subtitle != null) {
            metadata.putString(MediaMetadata.KEY_SUBTITLE, mediaItem.mediaMetadata.subtitle.toString())
        }
        if (mediaItem.mediaMetadata.artist != null) {
            metadata.putString(MediaMetadata.KEY_ARTIST, mediaItem.mediaMetadata.artist.toString())
        }
        if (mediaItem.mediaMetadata.albumArtist != null) {
            metadata.putString(
                    MediaMetadata.KEY_ALBUM_ARTIST, mediaItem.mediaMetadata.albumArtist.toString())
        }
        if (mediaItem.mediaMetadata.albumTitle != null) {
            metadata.putString(
                    MediaMetadata.KEY_ALBUM_TITLE, mediaItem.mediaMetadata.albumTitle.toString())
        }
        if (mediaItem.mediaMetadata.artworkUri != null) {
            metadata.addImage(WebImage(mediaItem.mediaMetadata.artworkUri!!))
        }
        if (mediaItem.mediaMetadata.composer != null) {
            metadata.putString(MediaMetadata.KEY_COMPOSER, mediaItem.mediaMetadata.composer.toString())
        }
        if (mediaItem.mediaMetadata.discNumber != null) {
            metadata.putInt(MediaMetadata.KEY_DISC_NUMBER, mediaItem.mediaMetadata.discNumber!!)
        }
        if (mediaItem.mediaMetadata.trackNumber != null) {
            metadata.putInt(MediaMetadata.KEY_TRACK_NUMBER, mediaItem.mediaMetadata.trackNumber!!)
        }
        val contentUrl = mediaItem.localConfiguration!!.uri.toString()
        val contentId = if (mediaItem.mediaId == MediaItem.DEFAULT_MEDIA_ID) contentUrl else mediaItem.mediaId
        val mediaInfo = MediaInfo.Builder(contentId)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mediaItem.localConfiguration!!.mimeType!!)
                .setContentUrl(contentUrl)
                .setMetadata(metadata)
                .setCustomData(getCustomData(mediaItem))
                .build()
        return MediaQueueItem.Builder(mediaInfo).build()
    }

    companion object {
        private const val KEY_MEDIA_ITEM = "mediaItem"
        private const val KEY_PLAYER_CONFIG = "exoPlayerConfig"
        private const val KEY_MEDIA_ID = "mediaId"
        private const val KEY_URI = "uri"
        private const val KEY_TITLE = "title"
        private const val KEY_MIME_TYPE = "mimeType"
        private const val KEY_DRM_CONFIGURATION = "drmConfiguration"
        private const val KEY_UUID = "uuid"
        private const val KEY_LICENSE_URI = "licenseUri"
        private const val KEY_REQUEST_HEADERS = "requestHeaders"

        // Deserialization.
        private fun getMediaItem(
                customData: JSONObject, mediaMetadata: com.google.android.exoplayer2.MediaMetadata): MediaItem {
            return try {
                val mediaItemJson = customData.getJSONObject(KEY_MEDIA_ITEM)
                val builder = MediaItem.Builder()
                        .setUri(Uri.parse(mediaItemJson.getString(KEY_URI)))
                        .setMediaId(mediaItemJson.getString(KEY_MEDIA_ID))
                        .setMediaMetadata(mediaMetadata)
                if (mediaItemJson.has(KEY_MIME_TYPE)) {
                    builder.setMimeType(mediaItemJson.getString(KEY_MIME_TYPE))
                }
                if (mediaItemJson.has(KEY_DRM_CONFIGURATION)) {
                    populateDrmConfiguration(mediaItemJson.getJSONObject(KEY_DRM_CONFIGURATION), builder)
                }
                builder.build()
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }

        @Throws(JSONException::class)
        private fun populateDrmConfiguration(json: JSONObject, mediaItem: MediaItem.Builder) {
            val drmConfiguration = DrmConfiguration.Builder(UUID.fromString(json.getString(KEY_UUID)))
                    .setLicenseUri(json.getString(KEY_LICENSE_URI))
            val requestHeadersJson = json.getJSONObject(KEY_REQUEST_HEADERS)
            val requestHeaders = HashMap<String, String>()
            val iterator = requestHeadersJson.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                requestHeaders[key] = requestHeadersJson.getString(key)
            }
            drmConfiguration.setLicenseRequestHeaders(requestHeaders)
            mediaItem.setDrmConfiguration(drmConfiguration.build())
        }

        // Serialization.
        private fun getCustomData(mediaItem: MediaItem): JSONObject {
            val json = JSONObject()
            try {
                json.put(KEY_MEDIA_ITEM, getMediaItemJson(mediaItem))
                val playerConfigJson = getPlayerConfigJson(mediaItem)
                if (playerConfigJson != null) {
                    json.put(KEY_PLAYER_CONFIG, playerConfigJson)
                }
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
            return json
        }

        @Throws(JSONException::class)
        private fun getMediaItemJson(mediaItem: MediaItem): JSONObject {
            Assertions.checkNotNull(mediaItem.localConfiguration)
            val json = JSONObject()
            json.put(KEY_MEDIA_ID, mediaItem.mediaId)
            json.put(KEY_TITLE, mediaItem.mediaMetadata.title)
            json.put(KEY_URI, mediaItem.localConfiguration!!.uri.toString())
            json.put(KEY_MIME_TYPE, mediaItem.localConfiguration!!.mimeType)
            if (mediaItem.localConfiguration!!.drmConfiguration != null) {
                json.put(
                        KEY_DRM_CONFIGURATION,
                        getDrmConfigurationJson(mediaItem.localConfiguration!!.drmConfiguration))
            }
            return json
        }

        @Throws(JSONException::class)
        private fun getDrmConfigurationJson(drmConfiguration: DrmConfiguration?): JSONObject {
            val json = JSONObject()
            json.put(KEY_UUID, drmConfiguration!!.scheme)
            json.put(KEY_LICENSE_URI, drmConfiguration.licenseUri)
            json.put(KEY_REQUEST_HEADERS, JSONObject(drmConfiguration.licenseRequestHeaders))
            return json
        }

        @Throws(JSONException::class)
        private fun getPlayerConfigJson(mediaItem: MediaItem): JSONObject? {
            if (mediaItem.localConfiguration == null
                    || mediaItem.localConfiguration!!.drmConfiguration == null) {
                return null
            }
            val drmConfiguration = mediaItem.localConfiguration!!.drmConfiguration
            val drmScheme: String
            drmScheme = if (C.WIDEVINE_UUID == drmConfiguration!!.scheme) {
                "widevine"
            } else if (C.PLAYREADY_UUID == drmConfiguration.scheme) {
                "playready"
            } else {
                return null
            }
            val playerConfigJson = JSONObject()
            playerConfigJson.put("withCredentials", false)
            playerConfigJson.put("protectionSystem", drmScheme)
            if (drmConfiguration.licenseUri != null) {
                playerConfigJson.put("licenseUrl", drmConfiguration.licenseUri)
            }
            if (!drmConfiguration.licenseRequestHeaders.isEmpty()) {
                playerConfigJson.put("headers", JSONObject(drmConfiguration.licenseRequestHeaders))
            }
            return playerConfigJson
        }
    }
}