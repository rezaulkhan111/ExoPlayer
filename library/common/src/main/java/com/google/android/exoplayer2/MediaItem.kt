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
package com.google.android.exoplayer2

import android.net.Uri
import android.os.Bundle
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import com.google.android.exoplayer2.C.RoleFlags
import com.google.android.exoplayer2.C.SelectionFlags
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.util.*
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.errorprone.annotations.InlineMe
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/** Representation of a media item.  */
class MediaItem private constructor(
        /** Identifies the media item.  */
        val mediaId: String?,
        clippingConfiguration: ClippingProperties,
        localConfiguration: PlaybackProperties?,
        liveConfiguration: LiveConfiguration,
        mediaMetadata: MediaMetadata,
        requestMetadata: RequestMetadata) : Bundleable {
    /** A builder for [MediaItem] instances.  */
    class Builder constructor() {
        private var mediaId: String? = null
        private var uri: Uri? = null
        private var mimeType: String? = null

        // TODO: Change this to ClippingProperties once all the deprecated individual setters are
        // removed.
        private var clippingConfiguration: ClippingConfiguration.Builder

        // TODO: Change this to @Nullable DrmConfiguration once all the deprecated individual setters
        // are removed.
        private var drmConfiguration: DrmConfiguration.Builder
        private var streamKeys: List<StreamKey>
        private var customCacheKey: String? = null
        private var subtitleConfigurations: ImmutableList<SubtitleConfiguration?>
        private var adsConfiguration: AdsConfiguration? = null
        private var tag: Any? = null
        private var mediaMetadata: MediaMetadata? = null

        // TODO: Change this to LiveConfiguration once all the deprecated individual setters
        // are removed.
        private var liveConfiguration: LiveConfiguration.Builder
        private var requestMetadata: RequestMetadata

        /** Creates a builder.  */
        init {
            clippingConfiguration = ClippingConfiguration.Builder()
            drmConfiguration = DrmConfiguration.Builder()
            streamKeys = emptyList()
            subtitleConfigurations = ImmutableList.of()
            liveConfiguration = LiveConfiguration.Builder()
            requestMetadata = RequestMetadata.EMPTY
        }

        constructor(mediaItem: MediaItem) : this() {
            clippingConfiguration = mediaItem.clippingConfiguration.buildUpon()
            mediaId = mediaItem.mediaId
            mediaMetadata = mediaItem.mediaMetadata
            liveConfiguration = mediaItem.liveConfiguration.buildUpon()
            requestMetadata = mediaItem.requestMetadata
            val localConfiguration: LocalConfiguration? = mediaItem.localConfiguration
            if (localConfiguration != null) {
                customCacheKey = localConfiguration.customCacheKey
                mimeType = localConfiguration.mimeType
                uri = localConfiguration.uri
                streamKeys = localConfiguration.streamKeys
                subtitleConfigurations = localConfiguration.subtitleConfigurations
                tag = localConfiguration.tag
                drmConfiguration = if (localConfiguration.drmConfiguration != null) localConfiguration.drmConfiguration.buildUpon() else DrmConfiguration.Builder()
                adsConfiguration = localConfiguration.adsConfiguration
            }
        }

        /**
         * Sets the optional media ID which identifies the media item.
         *
         *
         * By default [.DEFAULT_MEDIA_ID] is used.
         */
        @CanIgnoreReturnValue
        fun setMediaId(mediaId: String): Builder {
            this.mediaId = Assertions.checkNotNull(mediaId)
            return this
        }

        /**
         * Sets the optional URI.
         *
         *
         * If `uri` is null or unset then no [LocalConfiguration] object is created
         * during [.build] and no other `Builder` methods that would populate [ ][MediaItem.localConfiguration] should be called.
         */
        @CanIgnoreReturnValue
        fun setUri(uri: String?): Builder {
            return setUri(if (uri == null) null else Uri.parse(uri))
        }

        /**
         * Sets the optional URI.
         *
         *
         * If `uri` is null or unset then no [LocalConfiguration] object is created
         * during [.build] and no other `Builder` methods that would populate [ ][MediaItem.localConfiguration] should be called.
         */
        @CanIgnoreReturnValue
        fun setUri(uri: Uri?): Builder {
            this.uri = uri
            return this
        }

        /**
         * Sets the optional MIME type.
         *
         *
         * The MIME type may be used as a hint for inferring the type of the media item.
         *
         *
         * This method should only be called if [.setUri] is passed a non-null value.
         *
         * @param mimeType The MIME type.
         */
        @CanIgnoreReturnValue
        fun setMimeType(mimeType: String?): Builder {
            this.mimeType = mimeType
            return this
        }

        /** Sets the [ClippingConfiguration], defaults to [ClippingConfiguration.UNSET].  */
        @CanIgnoreReturnValue
        fun setClippingConfiguration(clippingConfiguration: ClippingConfiguration): Builder {
            this.clippingConfiguration = clippingConfiguration.buildUpon()
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setClippingConfiguration(ClippingConfiguration)} and {@link\n" + "     *     ClippingConfiguration.Builder#setStartPositionMs(long)} instead.")
        fun setClipStartPositionMs(@IntRange(from = 0) startPositionMs: Long): Builder {
            clippingConfiguration.setStartPositionMs(startPositionMs)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setClippingConfiguration(ClippingConfiguration)} and {@link\n" + "     *     ClippingConfiguration.Builder#setEndPositionMs(long)} instead.")
        fun setClipEndPositionMs(endPositionMs: Long): Builder {
            clippingConfiguration.setEndPositionMs(endPositionMs)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setClippingConfiguration(ClippingConfiguration)} and {@link\n" + "     *     ClippingConfiguration.Builder#setRelativeToLiveWindow(boolean)} instead.")
        fun setClipRelativeToLiveWindow(relativeToLiveWindow: Boolean): Builder {
            clippingConfiguration.setRelativeToLiveWindow(relativeToLiveWindow)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setClippingConfiguration(ClippingConfiguration)} and {@link\n" + "     *     ClippingConfiguration.Builder#setRelativeToDefaultPosition(boolean)} instead.")
        fun setClipRelativeToDefaultPosition(relativeToDefaultPosition: Boolean): Builder {
            clippingConfiguration.setRelativeToDefaultPosition(relativeToDefaultPosition)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setClippingConfiguration(ClippingConfiguration)} and {@link\n" + "     *     ClippingConfiguration.Builder#setStartsAtKeyFrame(boolean)} instead.")
        fun setClipStartsAtKeyFrame(startsAtKeyFrame: Boolean): Builder {
            clippingConfiguration.setStartsAtKeyFrame(startsAtKeyFrame)
            return this
        }

        /** Sets the optional DRM configuration.  */
        @CanIgnoreReturnValue
        fun setDrmConfiguration(drmConfiguration: DrmConfiguration?): Builder {
            this.drmConfiguration = if (drmConfiguration != null) drmConfiguration.buildUpon() else DrmConfiguration.Builder()
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link\n" + "     *     DrmConfiguration.Builder#setLicenseUri(Uri)} instead.")
        fun setDrmLicenseUri(licenseUri: Uri?): Builder {
            drmConfiguration.setLicenseUri(licenseUri)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link\n" + "     *     DrmConfiguration.Builder#setLicenseUri(String)} instead.")
        fun setDrmLicenseUri(licenseUri: String?): Builder {
            drmConfiguration.setLicenseUri(licenseUri)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link\n" + "     *     DrmConfiguration.Builder#setLicenseRequestHeaders(Map)} instead. Note that {@link\n" + "     *     DrmConfiguration.Builder#setLicenseRequestHeaders(Map)} doesn't accept null, use an empty\n" + "          map to clear the headers.")
        fun setDrmLicenseRequestHeaders(
                licenseRequestHeaders: Map<String?, String?>?): Builder {
            drmConfiguration.setLicenseRequestHeaders(
                    if (licenseRequestHeaders != null) licenseRequestHeaders else ImmutableMap.of())
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setDrmConfiguration(DrmConfiguration)} and pass the {@code uuid} to\n" + "          {@link DrmConfiguration.Builder#Builder(UUID)} instead.")
        fun setDrmUuid(uuid: UUID?): Builder {
            drmConfiguration.setNullableScheme(uuid)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link\n" + "     *     DrmConfiguration.Builder#setMultiSession(boolean)} instead.")
        fun setDrmMultiSession(multiSession: Boolean): Builder {
            drmConfiguration.setMultiSession(multiSession)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link\n" + "     *     DrmConfiguration.Builder#setForceDefaultLicenseUri(boolean)} instead.")
        fun setDrmForceDefaultLicenseUri(forceDefaultLicenseUri: Boolean): Builder {
            drmConfiguration.setForceDefaultLicenseUri(forceDefaultLicenseUri)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link\n" + "     *     DrmConfiguration.Builder#setPlayClearContentWithoutKey(boolean)} instead.")
        fun setDrmPlayClearContentWithoutKey(playClearContentWithoutKey: Boolean): Builder {
            drmConfiguration.setPlayClearContentWithoutKey(playClearContentWithoutKey)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link\n" + "     *     DrmConfiguration.Builder#setForceSessionsForAudioAndVideoTracks(boolean)} instead.")
        fun setDrmSessionForClearPeriods(sessionForClearPeriods: Boolean): Builder {
            drmConfiguration.setForceSessionsForAudioAndVideoTracks(sessionForClearPeriods)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link\n" + "     *     DrmConfiguration.Builder#setForcedSessionTrackTypes(List)} instead. Note that {@link\n" + "     *     DrmConfiguration.Builder#setForcedSessionTrackTypes(List)} doesn't accept null, use an\n" + "          empty list to clear the contents.")
        fun setDrmSessionForClearTypes(
                sessionForClearTypes: List<Int?>?): Builder {
            drmConfiguration.setForcedSessionTrackTypes(
                    if (sessionForClearTypes != null) sessionForClearTypes else ImmutableList.of())
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link\n" + "     *     DrmConfiguration.Builder#setKeySetId(byte[])} instead.")
        fun setDrmKeySetId(keySetId: ByteArray?): Builder {
            drmConfiguration.setKeySetId(keySetId)
            return this
        }

        /**
         * Sets the optional stream keys by which the manifest is filtered (only used for adaptive
         * streams).
         *
         *
         * `null` or an empty [List] can be used for a reset.
         *
         *
         * If [.setUri] is passed a non-null `uri`, the stream keys are used to create a
         * [LocalConfiguration] object. Otherwise they will be ignored.
         */
        @CanIgnoreReturnValue
        fun setStreamKeys(streamKeys: List<StreamKey>?): Builder {
            this.streamKeys = if (streamKeys != null && !streamKeys.isEmpty()) Collections.unmodifiableList(ArrayList(streamKeys)) else emptyList()
            return this
        }

        /**
         * Sets the optional custom cache key (only used for progressive streams).
         *
         *
         * This method should only be called if [.setUri] is passed a non-null value.
         */
        @CanIgnoreReturnValue
        fun setCustomCacheKey(customCacheKey: String?): Builder {
            this.customCacheKey = customCacheKey
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setSubtitleConfigurations(List)} instead. Note that {@link\n" + "     *     #setSubtitleConfigurations(List)} doesn't accept null, use an empty list to clear the\n" + "          contents.")
        fun setSubtitles(subtitles: List<Subtitle?>?): Builder {
            subtitleConfigurations = if (subtitles != null) ImmutableList.copyOf(subtitles) else ImmutableList.of()
            return this
        }

        /**
         * Sets the optional subtitles.
         *
         *
         * This method should only be called if [.setUri] is passed a non-null value.
         */
        @CanIgnoreReturnValue
        fun setSubtitleConfigurations(subtitleConfigurations: List<SubtitleConfiguration?>?): Builder {
            this.subtitleConfigurations = ImmutableList.copyOf(subtitleConfigurations)
            return this
        }

        /**
         * Sets the optional [AdsConfiguration].
         *
         *
         * This method should only be called if [.setUri] is passed a non-null value.
         */
        @CanIgnoreReturnValue
        fun setAdsConfiguration(adsConfiguration: AdsConfiguration?): Builder {
            this.adsConfiguration = adsConfiguration
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setAdsConfiguration(AdsConfiguration)}, parse the {@code adTagUri}\n" + "          with {@link Uri#parse(String)} and pass the result to {@link\n" + "     *     AdsConfiguration.Builder#Builder(Uri)} instead.")
        fun setAdTagUri(adTagUri: String?): Builder {
            return setAdTagUri(if (adTagUri != null) Uri.parse(adTagUri) else null)
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setAdsConfiguration(AdsConfiguration)} and pass the {@code adTagUri}\n" + "          to {@link AdsConfiguration.Builder#Builder(Uri)} instead.")
        fun setAdTagUri(adTagUri: Uri?): Builder {
            return setAdTagUri(adTagUri,  /* adsId= */null)
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setAdsConfiguration(AdsConfiguration)}, pass the {@code adTagUri} to\n" + "          {@link AdsConfiguration.Builder#Builder(Uri)} and the {@code adsId} to {@link\n" + "     *     AdsConfiguration.Builder#setAdsId(Object)} instead.")
        fun setAdTagUri(adTagUri: Uri?, adsId: Any?): Builder {
            adsConfiguration = if (adTagUri != null) AdsConfiguration.Builder(adTagUri).setAdsId(adsId).build() else null
            return this
        }

        /** Sets the [LiveConfiguration]. Defaults to [LiveConfiguration.UNSET].  */
        @CanIgnoreReturnValue
        fun setLiveConfiguration(liveConfiguration: LiveConfiguration): Builder {
            this.liveConfiguration = liveConfiguration.buildUpon()
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setLiveConfiguration(LiveConfiguration)} and {@link\n" + "     *     LiveConfiguration.Builder#setTargetOffsetMs(long)}.")
        fun setLiveTargetOffsetMs(liveTargetOffsetMs: Long): Builder {
            liveConfiguration.setTargetOffsetMs(liveTargetOffsetMs)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setLiveConfiguration(LiveConfiguration)} and {@link\n" + "     *     LiveConfiguration.Builder#setMinOffsetMs(long)}.")
        fun setLiveMinOffsetMs(liveMinOffsetMs: Long): Builder {
            liveConfiguration.setMinOffsetMs(liveMinOffsetMs)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setLiveConfiguration(LiveConfiguration)} and {@link\n" + "     *     LiveConfiguration.Builder#setMaxOffsetMs(long)}.")
        fun setLiveMaxOffsetMs(liveMaxOffsetMs: Long): Builder {
            liveConfiguration.setMaxOffsetMs(liveMaxOffsetMs)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setLiveConfiguration(LiveConfiguration)} and {@link\n" + "     *     LiveConfiguration.Builder#setMinPlaybackSpeed(float)}.")
        fun setLiveMinPlaybackSpeed(minPlaybackSpeed: Float): Builder {
            liveConfiguration.setMinPlaybackSpeed(minPlaybackSpeed)
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setLiveConfiguration(LiveConfiguration)} and {@link\n" + "     *     LiveConfiguration.Builder#setMaxPlaybackSpeed(float)}.")
        fun setLiveMaxPlaybackSpeed(maxPlaybackSpeed: Float): Builder {
            liveConfiguration.setMaxPlaybackSpeed(maxPlaybackSpeed)
            return this
        }

        /**
         * Sets the optional tag for custom attributes. The tag for the media source which will be
         * published in the `com.google.android.exoplayer2.Timeline` of the source as `com.google.android.exoplayer2.Timeline.Window#tag`.
         *
         *
         * This method should only be called if [.setUri] is passed a non-null value.
         */
        @CanIgnoreReturnValue
        fun setTag(tag: Any?): Builder {
            this.tag = tag
            return this
        }

        /** Sets the media metadata.  */
        @CanIgnoreReturnValue
        fun setMediaMetadata(mediaMetadata: MediaMetadata?): Builder {
            this.mediaMetadata = mediaMetadata
            return this
        }

        /** Sets the request metadata.  */
        @CanIgnoreReturnValue
        fun setRequestMetadata(requestMetadata: RequestMetadata): Builder {
            this.requestMetadata = requestMetadata
            return this
        }

        /** Returns a new [MediaItem] instance with the current builder values.  */
        // Using PlaybackProperties while it exists.
        fun build(): MediaItem {
            // TODO: remove this check once all the deprecated individual DRM setters are removed.
            Assertions.checkState(drmConfiguration.licenseUri == null || drmConfiguration.scheme != null)
            var localConfiguration: PlaybackProperties? = null
            val uri: Uri? = uri
            if (uri != null) {
                localConfiguration = PlaybackProperties(
                        uri,
                        mimeType,
                        if (drmConfiguration.scheme != null) drmConfiguration.build() else null,
                        adsConfiguration,
                        streamKeys,
                        customCacheKey,
                        subtitleConfigurations,
                        tag)
            }
            return MediaItem(
                    if (mediaId != null) mediaId else DEFAULT_MEDIA_ID,
                    clippingConfiguration.buildClippingProperties(),
                    localConfiguration,
                    liveConfiguration.build(),
                    (if (mediaMetadata != null) mediaMetadata else MediaMetadata.Companion.EMPTY)!!,
                    requestMetadata)
        }
    }

    /** DRM configuration for a media item.  */
    class DrmConfiguration private constructor(builder: Builder) {
        /** Builder for [DrmConfiguration].  */
        class Builder {
            // TODO remove @Nullable annotation when the deprecated zero-arg constructor is removed.
            var scheme: UUID? = null
            var licenseUri: Uri? = null
            var licenseRequestHeaders: ImmutableMap<String?, String?>
            var multiSession: Boolean = false
            var playClearContentWithoutKey: Boolean = false
            var forceDefaultLicenseUri: Boolean = false
            var forcedSessionTrackTypes: ImmutableList<Int?>
            var keySetId: ByteArray?

            /**
             * Constructs an instance.
             *
             * @param scheme The [UUID] of the protection scheme.
             */
            constructor(scheme: UUID?) {
                this.scheme = scheme
                licenseRequestHeaders = ImmutableMap.of()
                forcedSessionTrackTypes = ImmutableList.of()
            }

            @Deprecated("This only exists to support the deprecated setters for individual DRM\n" + "            properties on {@link MediaItem.Builder}.")
            constructor() {
                licenseRequestHeaders = ImmutableMap.of()
                forcedSessionTrackTypes = ImmutableList.of()
            }

            constructor(drmConfiguration: DrmConfiguration) {
                scheme = drmConfiguration.scheme
                licenseUri = drmConfiguration.licenseUri
                licenseRequestHeaders = drmConfiguration.licenseRequestHeaders
                multiSession = drmConfiguration.multiSession
                playClearContentWithoutKey = drmConfiguration.playClearContentWithoutKey
                forceDefaultLicenseUri = drmConfiguration.forceDefaultLicenseUri
                forcedSessionTrackTypes = drmConfiguration.forcedSessionTrackTypes
                keySetId = drmConfiguration.keySetId
            }

            /** Sets the [UUID] of the protection scheme.  */
            @CanIgnoreReturnValue
            fun setScheme(scheme: UUID?): Builder {
                this.scheme = scheme
                return this
            }

            @CanIgnoreReturnValue
            @Deprecated("This only exists to support the deprecated {@link\n" + "       *     MediaItem.Builder#setDrmUuid(UUID)}.")
            fun setNullableScheme(scheme: UUID?): Builder {
                this.scheme = scheme
                return this
            }

            /** Sets the optional default DRM license server URI.  */
            @CanIgnoreReturnValue
            fun setLicenseUri(licenseUri: Uri?): Builder {
                this.licenseUri = licenseUri
                return this
            }

            /** Sets the optional default DRM license server URI.  */
            @CanIgnoreReturnValue
            fun setLicenseUri(licenseUri: String?): Builder {
                this.licenseUri = if (licenseUri == null) null else Uri.parse(licenseUri)
                return this
            }

            /** Sets the optional request headers attached to DRM license requests.  */
            @CanIgnoreReturnValue
            fun setLicenseRequestHeaders(licenseRequestHeaders: Map<String?, String?>?): Builder {
                this.licenseRequestHeaders = ImmutableMap.copyOf(licenseRequestHeaders)
                return this
            }

            /** Sets whether multi session is enabled.  */
            @CanIgnoreReturnValue
            fun setMultiSession(multiSession: Boolean): Builder {
                this.multiSession = multiSession
                return this
            }

            /**
             * Sets whether to always use the default DRM license server URI even if the media specifies
             * its own DRM license server URI.
             */
            @CanIgnoreReturnValue
            fun setForceDefaultLicenseUri(forceDefaultLicenseUri: Boolean): Builder {
                this.forceDefaultLicenseUri = forceDefaultLicenseUri
                return this
            }

            /**
             * Sets whether clear samples within protected content should be played when keys for the
             * encrypted part of the content have yet to be loaded.
             */
            @CanIgnoreReturnValue
            fun setPlayClearContentWithoutKey(playClearContentWithoutKey: Boolean): Builder {
                this.playClearContentWithoutKey = playClearContentWithoutKey
                return this
            }

            @CanIgnoreReturnValue
            @InlineMe(replacement = "this.setForceSessionsForAudioAndVideoTracks(forceSessionsForAudioAndVideoTracks)")
            @Deprecated("Use {@link #setForceSessionsForAudioAndVideoTracks(boolean)} instead.")
            fun forceSessionsForAudioAndVideoTracks(
                    forceSessionsForAudioAndVideoTracks: Boolean): Builder {
                return setForceSessionsForAudioAndVideoTracks(forceSessionsForAudioAndVideoTracks)
            }

            /**
             * Sets whether a DRM session should be used for clear tracks of type [ ][C.TRACK_TYPE_VIDEO] and [C.TRACK_TYPE_AUDIO].
             *
             *
             * This method overrides what has been set by previously calling [ ][.setForcedSessionTrackTypes].
             */
            @CanIgnoreReturnValue
            fun setForceSessionsForAudioAndVideoTracks(
                    forceSessionsForAudioAndVideoTracks: Boolean): Builder {
                setForcedSessionTrackTypes(
                        if (forceSessionsForAudioAndVideoTracks) ImmutableList.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO) else ImmutableList.of())
                return this
            }

            /**
             * Sets a list of [track type][C.TrackType] constants for which to use a DRM session even
             * when the tracks are in the clear.
             *
             *
             * For the common case of using a DRM session for [C.TRACK_TYPE_VIDEO] and [ ][C.TRACK_TYPE_AUDIO], [.setForceSessionsForAudioAndVideoTracks] can be used.
             *
             *
             * This method overrides what has been set by previously calling [ ][.setForceSessionsForAudioAndVideoTracks].
             */
            @CanIgnoreReturnValue
            fun setForcedSessionTrackTypes(
                    forcedSessionTrackTypes: List<Int?>?): Builder {
                this.forcedSessionTrackTypes = ImmutableList.copyOf(forcedSessionTrackTypes)
                return this
            }

            /**
             * Sets the key set ID of the offline license.
             *
             *
             * The key set ID identifies an offline license. The ID is required to query, renew or
             * release an existing offline license (see `DefaultDrmSessionManager#setMode(int
             * mode,byte[] offlineLicenseKeySetId)`).
             */
            @CanIgnoreReturnValue
            fun setKeySetId(keySetId: ByteArray?): Builder {
                this.keySetId = if (keySetId != null) Arrays.copyOf(keySetId, keySetId.size) else null
                return this
            }

            fun build(): DrmConfiguration {
                return DrmConfiguration(this)
            }
        }

        /** The UUID of the protection scheme.  */
        val scheme: UUID?

        @Deprecated("Use {@link #scheme} instead.")
        val uuid: UUID?

        /**
         * Optional default DRM license server [Uri]. If `null` then the DRM license server
         * must be specified by the media.
         */
        val licenseUri: Uri?

        @Deprecated("Use {@link #licenseRequestHeaders} instead.")
        val requestHeaders: ImmutableMap<String?, String?>

        /** The headers to attach to requests sent to the DRM license server.  */
        val licenseRequestHeaders: ImmutableMap<String?, String?>

        /** Whether the DRM configuration is multi session enabled.  */
        val multiSession: Boolean

        /**
         * Whether clear samples within protected content should be played when keys for the encrypted
         * part of the content have yet to be loaded.
         */
        val playClearContentWithoutKey: Boolean

        /**
         * Whether to force use of [.licenseUri] even if the media specifies its own DRM license
         * server URI.
         */
        val forceDefaultLicenseUri: Boolean

        @Deprecated("Use {@link #forcedSessionTrackTypes}.")
        val sessionForClearTypes: ImmutableList<Int?>

        /**
         * The types of tracks for which to always use a DRM session even if the content is unencrypted.
         */
        val forcedSessionTrackTypes: ImmutableList<Int?>
        private val keySetId: ByteArray?

        init {
            Assertions.checkState(!(builder.forceDefaultLicenseUri && builder.licenseUri == null))
            scheme = Assertions.checkNotNull(builder.scheme)
            uuid = scheme
            licenseUri = builder.licenseUri
            requestHeaders = builder.licenseRequestHeaders
            licenseRequestHeaders = builder.licenseRequestHeaders
            multiSession = builder.multiSession
            forceDefaultLicenseUri = builder.forceDefaultLicenseUri
            playClearContentWithoutKey = builder.playClearContentWithoutKey
            sessionForClearTypes = builder.forcedSessionTrackTypes
            forcedSessionTrackTypes = builder.forcedSessionTrackTypes
            keySetId = if (builder.keySetId != null) Arrays.copyOf(builder.keySetId, builder.keySetId!!.size) else null
        }

        /** Returns the key set ID of the offline license.  */
        fun getKeySetId(): ByteArray? {
            return if (keySetId != null) Arrays.copyOf(keySetId, keySetId.size) else null
        }

        /** Returns a [Builder] initialized with the values of this instance.  */
        fun buildUpon(): Builder {
            return Builder(this)
        }

        public override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (!(obj is DrmConfiguration)) {
                return false
            }
            val other: DrmConfiguration = obj
            return ((scheme == other.scheme) && Util.areEqual(licenseUri, other.licenseUri)
                    && Util.areEqual(licenseRequestHeaders, other.licenseRequestHeaders)
                    && (multiSession == other.multiSession
                    ) && (forceDefaultLicenseUri == other.forceDefaultLicenseUri
                    ) && (playClearContentWithoutKey == other.playClearContentWithoutKey
                    ) && (forcedSessionTrackTypes == other.forcedSessionTrackTypes) && Arrays.equals(keySetId, other.keySetId))
        }

        public override fun hashCode(): Int {
            var result: Int = scheme.hashCode()
            result = 31 * result + (if (licenseUri != null) licenseUri.hashCode() else 0)
            result = 31 * result + licenseRequestHeaders.hashCode()
            result = 31 * result + (if (multiSession) 1 else 0)
            result = 31 * result + (if (forceDefaultLicenseUri) 1 else 0)
            result = 31 * result + (if (playClearContentWithoutKey) 1 else 0)
            result = 31 * result + forcedSessionTrackTypes.hashCode()
            result = 31 * result + Arrays.hashCode(keySetId)
            return result
        }
    }

    /** Configuration for playing back linear ads with a media item.  */
    class AdsConfiguration private constructor(builder: Builder) {
        /** Builder for [AdsConfiguration] instances.  */
        class Builder
        /**
         * Constructs a new instance.
         *
         * @param adTagUri The ad tag URI to load.
         */ constructor(var adTagUri: Uri) {
            var adsId: Any? = null

            /** Sets the ad tag URI to load.  */
            @CanIgnoreReturnValue
            fun setAdTagUri(adTagUri: Uri): Builder {
                this.adTagUri = adTagUri
                return this
            }

            /**
             * Sets the ads identifier.
             *
             *
             * See details on [AdsConfiguration.adsId] for how the ads identifier is used and how
             * it's calculated if not explicitly set.
             */
            @CanIgnoreReturnValue
            fun setAdsId(adsId: Any?): Builder {
                this.adsId = adsId
                return this
            }

            fun build(): AdsConfiguration {
                return AdsConfiguration(this)
            }
        }

        /** The ad tag URI to load.  */
        val adTagUri: Uri

        /**
         * An opaque identifier for ad playback state associated with this item, or `null` if the
         * combination of the [media ID][MediaItem.Builder.setMediaId] and [ ad tag URI][.adTagUri] should be used as the ads identifier.
         *
         *
         * Media items in the playlist that have the same ads identifier and ads loader share the
         * same ad playback state. To resume ad playback when recreating the playlist on returning from
         * the background, pass the same ads identifiers to the player.
         */
        val adsId: Any?

        init {
            adTagUri = builder.adTagUri
            adsId = builder.adsId
        }

        /** Returns a [Builder] initialized with the values of this instance.  */
        fun buildUpon(): Builder {
            return Builder(adTagUri).setAdsId(adsId)
        }

        public override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (!(obj is AdsConfiguration)) {
                return false
            }
            val other: AdsConfiguration = obj
            return (adTagUri == other.adTagUri) && Util.areEqual(adsId, other.adsId)
        }

        public override fun hashCode(): Int {
            var result: Int = adTagUri.hashCode()
            result = 31 * result + (if (adsId != null) adsId.hashCode() else 0)
            return result
        }
    }

    /** Properties for local playback.  */ // TODO: Mark this final when PlaybackProperties is deleted.
    open class LocalConfiguration private constructor(
            /** The [Uri].  */
            val uri: Uri,
            /**
             * The optional MIME type of the item, or `null` if unspecified.
             *
             *
             * The MIME type can be used to disambiguate media items that have a URI which does not allow
             * to infer the actual media type.
             */
            val mimeType: String?,
            /** Optional [DrmConfiguration] for the media.  */
            val drmConfiguration: DrmConfiguration?,
            /** Optional ads configuration.  */
            val adsConfiguration: AdsConfiguration?,
            /** Optional stream keys by which the manifest is filtered.  */
            val streamKeys: List<StreamKey>,
            /** Optional custom cache key (only used for progressive streams).  */
            val customCacheKey: String?,
            /** Optional subtitles to be sideloaded.  */
            val subtitleConfigurations: ImmutableList<SubtitleConfiguration?>,
            tag: Any?) {

        @Deprecated("Use {@link #subtitleConfigurations} instead.")
        val subtitles: List<Subtitle>

        /**
         * Optional tag for custom attributes. The tag for the media source which will be published in
         * the `com.google.android.exoplayer2.Timeline` of the source as `com.google.android.exoplayer2.Timeline.Window#tag`.
         */
        val tag: Any?

        init {
            val subtitles: ImmutableList.Builder<Subtitle> = ImmutableList.builder()
            for (i in subtitleConfigurations.indices) {
                subtitles.add(subtitleConfigurations.get(i)!!.buildUpon().buildSubtitle())
            }
            this.subtitles = subtitles.build()
            this.tag = tag
        }

        public override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (!(obj is LocalConfiguration)) {
                return false
            }
            val other: LocalConfiguration = obj
            return ((uri == other.uri) && Util.areEqual(mimeType, other.mimeType)
                    && Util.areEqual(drmConfiguration, other.drmConfiguration)
                    && Util.areEqual(adsConfiguration, other.adsConfiguration)
                    && (streamKeys == other.streamKeys) && Util.areEqual(customCacheKey, other.customCacheKey)
                    && (subtitleConfigurations == other.subtitleConfigurations) && Util.areEqual(tag, other.tag))
        }

        public override fun hashCode(): Int {
            var result: Int = uri.hashCode()
            result = 31 * result + (if (mimeType == null) 0 else mimeType.hashCode())
            result = 31 * result + (if (drmConfiguration == null) 0 else drmConfiguration.hashCode())
            result = 31 * result + (if (adsConfiguration == null) 0 else adsConfiguration.hashCode())
            result = 31 * result + streamKeys.hashCode()
            result = 31 * result + (if (customCacheKey == null) 0 else customCacheKey.hashCode())
            result = 31 * result + subtitleConfigurations.hashCode()
            result = 31 * result + (if (tag == null) 0 else tag.hashCode())
            return result
        }
    }

    @Deprecated("Use {@link LocalConfiguration}.")
    class PlaybackProperties(
            uri: Uri,
            mimeType: String?,
            drmConfiguration: DrmConfiguration?,
            adsConfiguration: AdsConfiguration?,
            streamKeys: List<StreamKey>,
            customCacheKey: String?,
            subtitleConfigurations: ImmutableList<SubtitleConfiguration?>,
            tag: Any?) : LocalConfiguration(
            uri,
            mimeType,
            drmConfiguration,
            adsConfiguration,
            streamKeys,
            customCacheKey,
            subtitleConfigurations,
            tag)

    /** Live playback configuration.  */
    class LiveConfiguration @Deprecated("Use {@link Builder} instead.") constructor(
            /**
             * Target offset from the live edge, in milliseconds, or [C.TIME_UNSET] to use the
             * media-defined default.
             */
            val targetOffsetMs: Long,
            /**
             * The minimum allowed offset from the live edge, in milliseconds, or [C.TIME_UNSET] to
             * use the media-defined default.
             */
            val minOffsetMs: Long,
            /**
             * The maximum allowed offset from the live edge, in milliseconds, or [C.TIME_UNSET] to
             * use the media-defined default.
             */
            val maxOffsetMs: Long,
            /**
             * Minimum factor by which playback can be sped up, or [C.RATE_UNSET] to use the
             * media-defined default.
             */
            val minPlaybackSpeed: Float,
            /**
             * Maximum factor by which playback can be sped up, or [C.RATE_UNSET] to use the
             * media-defined default.
             */
            val maxPlaybackSpeed: Float) : Bundleable {
        /** Builder for [LiveConfiguration] instances.  */
        class Builder {
            var targetOffsetMs: Long
            var minOffsetMs: Long
            var maxOffsetMs: Long
            var minPlaybackSpeed: Float
            var maxPlaybackSpeed: Float

            /** Constructs an instance.  */
            constructor() {
                targetOffsetMs = C.TIME_UNSET
                minOffsetMs = C.TIME_UNSET
                maxOffsetMs = C.TIME_UNSET
                minPlaybackSpeed = C.RATE_UNSET
                maxPlaybackSpeed = C.RATE_UNSET
            }

            constructor(liveConfiguration: LiveConfiguration) {
                targetOffsetMs = liveConfiguration.targetOffsetMs
                minOffsetMs = liveConfiguration.minOffsetMs
                maxOffsetMs = liveConfiguration.maxOffsetMs
                minPlaybackSpeed = liveConfiguration.minPlaybackSpeed
                maxPlaybackSpeed = liveConfiguration.maxPlaybackSpeed
            }

            /**
             * Sets the target live offset, in milliseconds.
             *
             *
             * See `Player#getCurrentLiveOffset()`.
             *
             *
             * Defaults to [C.TIME_UNSET], indicating the media-defined default will be used.
             */
            @CanIgnoreReturnValue
            fun setTargetOffsetMs(targetOffsetMs: Long): Builder {
                this.targetOffsetMs = targetOffsetMs
                return this
            }

            /**
             * Sets the minimum allowed live offset, in milliseconds.
             *
             *
             * See `Player#getCurrentLiveOffset()`.
             *
             *
             * Defaults to [C.TIME_UNSET], indicating the media-defined default will be used.
             */
            @CanIgnoreReturnValue
            fun setMinOffsetMs(minOffsetMs: Long): Builder {
                this.minOffsetMs = minOffsetMs
                return this
            }

            /**
             * Sets the maximum allowed live offset, in milliseconds.
             *
             *
             * See `Player#getCurrentLiveOffset()`.
             *
             *
             * Defaults to [C.TIME_UNSET], indicating the media-defined default will be used.
             */
            @CanIgnoreReturnValue
            fun setMaxOffsetMs(maxOffsetMs: Long): Builder {
                this.maxOffsetMs = maxOffsetMs
                return this
            }

            /**
             * Sets the minimum playback speed.
             *
             *
             * Defaults to [C.RATE_UNSET], indicating the media-defined default will be used.
             */
            @CanIgnoreReturnValue
            fun setMinPlaybackSpeed(minPlaybackSpeed: Float): Builder {
                this.minPlaybackSpeed = minPlaybackSpeed
                return this
            }

            /**
             * Sets the maximum playback speed.
             *
             *
             * Defaults to [C.RATE_UNSET], indicating the media-defined default will be used.
             */
            @CanIgnoreReturnValue
            fun setMaxPlaybackSpeed(maxPlaybackSpeed: Float): Builder {
                this.maxPlaybackSpeed = maxPlaybackSpeed
                return this
            }

            /** Creates a [LiveConfiguration] with the values from this builder.  */
            fun build(): LiveConfiguration {
                return LiveConfiguration(this)
            }
        }

        // Using the deprecated constructor while it exists.
        private constructor(builder: Builder) : this(
                builder.targetOffsetMs,
                builder.minOffsetMs,
                builder.maxOffsetMs,
                builder.minPlaybackSpeed,
                builder.maxPlaybackSpeed) {
        }

        /** Returns a [Builder] initialized with the values of this instance.  */
        fun buildUpon(): Builder {
            return Builder(this)
        }

        public override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (!(obj is LiveConfiguration)) {
                return false
            }
            val other: LiveConfiguration = obj
            return (targetOffsetMs == other.targetOffsetMs
                    ) && (minOffsetMs == other.minOffsetMs
                    ) && (maxOffsetMs == other.maxOffsetMs
                    ) && (minPlaybackSpeed == other.minPlaybackSpeed
                    ) && (maxPlaybackSpeed == other.maxPlaybackSpeed)
        }

        public override fun hashCode(): Int {
            var result: Int = (targetOffsetMs xor (targetOffsetMs ushr 32)).toInt()
            result = 31 * result + (minOffsetMs xor (minOffsetMs ushr 32)).toInt()
            result = 31 * result + (maxOffsetMs xor (maxOffsetMs ushr 32)).toInt()
            result = 31 * result + (if (minPlaybackSpeed != 0f) java.lang.Float.floatToIntBits(minPlaybackSpeed) else 0)
            result = 31 * result + (if (maxPlaybackSpeed != 0f) java.lang.Float.floatToIntBits(maxPlaybackSpeed) else 0)
            return result
        }

        // Bundleable implementation.
        @Documented
        @Retention(RetentionPolicy.SOURCE)
        @Target(TYPE_USE)
        @IntDef([FIELD_TARGET_OFFSET_MS, FIELD_MIN_OFFSET_MS, FIELD_MAX_OFFSET_MS, FIELD_MIN_PLAYBACK_SPEED, FIELD_MAX_PLAYBACK_SPEED])
        private annotation class FieldNumber constructor()

        public override fun toBundle(): Bundle {
            val bundle: Bundle = Bundle()
            bundle.putLong(keyForField(FIELD_TARGET_OFFSET_MS), targetOffsetMs)
            bundle.putLong(keyForField(FIELD_MIN_OFFSET_MS), minOffsetMs)
            bundle.putLong(keyForField(FIELD_MAX_OFFSET_MS), maxOffsetMs)
            bundle.putFloat(keyForField(FIELD_MIN_PLAYBACK_SPEED), minPlaybackSpeed)
            bundle.putFloat(keyForField(FIELD_MAX_PLAYBACK_SPEED), maxPlaybackSpeed)
            return bundle
        }

        companion object {
            /**
             * A live playback configuration with unset values, meaning media-defined default values will be
             * used.
             */
            val UNSET: LiveConfiguration = Builder().build()
            private val FIELD_TARGET_OFFSET_MS: Int = 0
            private val FIELD_MIN_OFFSET_MS: Int = 1
            private val FIELD_MAX_OFFSET_MS: Int = 2
            private val FIELD_MIN_PLAYBACK_SPEED: Int = 3
            private val FIELD_MAX_PLAYBACK_SPEED: Int = 4

            /** Object that can restore [LiveConfiguration] from a [Bundle].  */
            val CREATOR: Bundleable.Creator<LiveConfiguration> = Bundleable.Creator({ bundle: Bundle ->
                LiveConfiguration(
                        bundle.getLong(
                                keyForField(FIELD_TARGET_OFFSET_MS),  /* defaultValue= */C.TIME_UNSET),
                        bundle.getLong(keyForField(FIELD_MIN_OFFSET_MS),  /* defaultValue= */C.TIME_UNSET),
                        bundle.getLong(keyForField(FIELD_MAX_OFFSET_MS),  /* defaultValue= */C.TIME_UNSET),
                        bundle.getFloat(
                                keyForField(FIELD_MIN_PLAYBACK_SPEED),  /* defaultValue= */C.RATE_UNSET),
                        bundle.getFloat(
                                keyForField(FIELD_MAX_PLAYBACK_SPEED),  /* defaultValue= */C.RATE_UNSET))
            })

            private fun keyForField(field: @FieldNumber Int): String {
                return Integer.toString(field, Character.MAX_RADIX)
            }
        }
    }

    /** Properties for a text track.  */ // TODO: Mark this final when Subtitle is deleted.
    open class SubtitleConfiguration {
        /** Builder for [SubtitleConfiguration] instances.  */
        class Builder {
            var uri: Uri
            var mimeType: String? = null
            var language: String? = null
            var selectionFlags: @SelectionFlags Int = 0
            var roleFlags: @RoleFlags Int = 0
            var label: String? = null
            var id: String? = null

            /**
             * Constructs an instance.
             *
             * @param uri The [Uri] to the subtitle file.
             */
            constructor(uri: Uri) {
                this.uri = uri
            }

            constructor(subtitleConfiguration: SubtitleConfiguration) {
                uri = subtitleConfiguration.uri
                mimeType = subtitleConfiguration.mimeType
                language = subtitleConfiguration.language
                selectionFlags = subtitleConfiguration.selectionFlags
                roleFlags = subtitleConfiguration.roleFlags
                label = subtitleConfiguration.label
                id = subtitleConfiguration.id
            }

            /** Sets the [Uri] to the subtitle file.  */
            @CanIgnoreReturnValue
            fun setUri(uri: Uri): Builder {
                this.uri = uri
                return this
            }

            /** Sets the MIME type.  */
            @CanIgnoreReturnValue
            fun setMimeType(mimeType: String?): Builder {
                this.mimeType = mimeType
                return this
            }

            /** Sets the optional language of the subtitle file.  */
            @CanIgnoreReturnValue
            fun setLanguage(language: String?): Builder {
                this.language = language
                return this
            }

            /** Sets the flags used for track selection.  */
            @CanIgnoreReturnValue
            fun setSelectionFlags(selectionFlags: @SelectionFlags Int): Builder {
                this.selectionFlags = selectionFlags
                return this
            }

            /** Sets the role flags. These are used for track selection.  */
            @CanIgnoreReturnValue
            fun setRoleFlags(roleFlags: @RoleFlags Int): Builder {
                this.roleFlags = roleFlags
                return this
            }

            /** Sets the optional label for this subtitle track.  */
            @CanIgnoreReturnValue
            fun setLabel(label: String?): Builder {
                this.label = label
                return this
            }

            /** Sets the optional ID for this subtitle track.  */
            @CanIgnoreReturnValue
            fun setId(id: String?): Builder {
                this.id = id
                return this
            }

            /** Creates a [SubtitleConfiguration] from the values of this builder.  */
            fun build(): SubtitleConfiguration {
                return SubtitleConfiguration(this)
            }

            fun buildSubtitle(): Subtitle {
                return Subtitle(this)
            }
        }

        /** The [Uri] to the subtitle file.  */
        val uri: Uri

        /** The optional MIME type of the subtitle file, or `null` if unspecified.  */
        val mimeType: String?

        /** The language.  */
        val language: String?

        /** The selection flags.  */
        val selectionFlags: @SelectionFlags Int

        /** The role flags.  */
        val roleFlags: @RoleFlags Int

        /** The label.  */
        val label: String?

        /**
         * The ID of the subtitles. This will be propagated to the [Format.id] of the subtitle
         * track created from this configuration.
         */
        val id: String?

        private constructor(
                uri: Uri,
                mimeType: String?,
                language: String?,
                selectionFlags: Int,
                roleFlags: Int,
                label: String?,
                id: String?) {
            this.uri = uri
            this.mimeType = mimeType
            this.language = language
            this.selectionFlags = selectionFlags
            this.roleFlags = roleFlags
            this.label = label
            this.id = id
        }

        private constructor(builder: Builder) {
            uri = builder.uri
            mimeType = builder.mimeType
            language = builder.language
            selectionFlags = builder.selectionFlags
            roleFlags = builder.roleFlags
            label = builder.label
            id = builder.id
        }

        /** Returns a [Builder] initialized with the values of this instance.  */
        fun buildUpon(): Builder {
            return Builder(this)
        }

        public override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (!(obj is SubtitleConfiguration)) {
                return false
            }
            val other: SubtitleConfiguration = obj
            return ((uri == other.uri) && Util.areEqual(mimeType, other.mimeType)
                    && Util.areEqual(language, other.language)
                    && (selectionFlags == other.selectionFlags
                    ) && (roleFlags == other.roleFlags
                    ) && Util.areEqual(label, other.label)
                    && Util.areEqual(id, other.id))
        }

        public override fun hashCode(): Int {
            var result: Int = uri.hashCode()
            result = 31 * result + (if (mimeType == null) 0 else mimeType.hashCode())
            result = 31 * result + (if (language == null) 0 else language.hashCode())
            result = 31 * result + selectionFlags
            result = 31 * result + roleFlags
            result = 31 * result + (if (label == null) 0 else label.hashCode())
            result = 31 * result + (if (id == null) 0 else id.hashCode())
            return result
        }
    }

    @Deprecated("Use {@link MediaItem.SubtitleConfiguration} instead")
    class Subtitle : SubtitleConfiguration {

        @Deprecated("Use {@link Builder} instead.")
        constructor(uri: Uri, mimeType: String?, language: String?) : this(uri, mimeType, language,  /* selectionFlags= */0) {
        }

        @Deprecated("Use {@link Builder} instead.")
        constructor(
                uri: Uri, mimeType: String?, language: String?, selectionFlags: @SelectionFlags Int) : this(uri, mimeType, language, selectionFlags,  /* roleFlags= */0,  /* label= */null) {
        }

        @Deprecated("Use {@link Builder} instead.")
        constructor(
                uri: Uri,
                mimeType: String?,
                language: String?,
                selectionFlags: @SelectionFlags Int,
                roleFlags: @RoleFlags Int,
                label: String?) : super(uri, mimeType, language, selectionFlags, roleFlags, label,  /* id= */null) {
        }

        constructor(builder: Builder) : super(builder) {}
    }

    /** Optionally clips the media item to a custom start and end position.  */ // TODO: Mark this final when ClippingProperties is deleted.
    open class ClippingConfiguration private constructor(builder: Builder) : Bundleable {
        /** Builder for [ClippingConfiguration] instances.  */
        class Builder {
            var startPositionMs: Long = 0
            var endPositionMs: Long
            var relativeToLiveWindow: Boolean = false
            var relativeToDefaultPosition: Boolean = false
            var startsAtKeyFrame: Boolean = false

            /** Constructs an instance.  */
            constructor() {
                endPositionMs = C.TIME_END_OF_SOURCE
            }

            constructor(clippingConfiguration: ClippingConfiguration) {
                startPositionMs = clippingConfiguration.startPositionMs
                endPositionMs = clippingConfiguration.endPositionMs
                relativeToLiveWindow = clippingConfiguration.relativeToLiveWindow
                relativeToDefaultPosition = clippingConfiguration.relativeToDefaultPosition
                startsAtKeyFrame = clippingConfiguration.startsAtKeyFrame
            }

            /**
             * Sets the optional start position in milliseconds which must be a value larger than or equal
             * to zero (Default: 0).
             */
            @CanIgnoreReturnValue
            fun setStartPositionMs(@IntRange(from = 0) startPositionMs: Long): Builder {
                Assertions.checkArgument(startPositionMs >= 0)
                this.startPositionMs = startPositionMs
                return this
            }

            /**
             * Sets the optional end position in milliseconds which must be a value larger than or equal
             * to zero, or [C.TIME_END_OF_SOURCE] to end when playback reaches the end of media
             * (Default: [C.TIME_END_OF_SOURCE]).
             */
            @CanIgnoreReturnValue
            fun setEndPositionMs(endPositionMs: Long): Builder {
                Assertions.checkArgument(endPositionMs == C.TIME_END_OF_SOURCE || endPositionMs >= 0)
                this.endPositionMs = endPositionMs
                return this
            }

            /**
             * Sets whether the start/end positions should move with the live window for live streams. If
             * `false`, live streams end when playback reaches the end position in live window seen
             * when the media is first loaded (Default: `false`).
             */
            @CanIgnoreReturnValue
            fun setRelativeToLiveWindow(relativeToLiveWindow: Boolean): Builder {
                this.relativeToLiveWindow = relativeToLiveWindow
                return this
            }

            /**
             * Sets whether the start position and the end position are relative to the default position
             * in the window (Default: `false`).
             */
            @CanIgnoreReturnValue
            fun setRelativeToDefaultPosition(relativeToDefaultPosition: Boolean): Builder {
                this.relativeToDefaultPosition = relativeToDefaultPosition
                return this
            }

            /**
             * Sets whether the start point is guaranteed to be a key frame. If `false`, the
             * playback transition into the clip may not be seamless (Default: `false`).
             */
            @CanIgnoreReturnValue
            fun setStartsAtKeyFrame(startsAtKeyFrame: Boolean): Builder {
                this.startsAtKeyFrame = startsAtKeyFrame
                return this
            }

            /**
             * Returns a [ClippingConfiguration] instance initialized with the values of this
             * builder.
             */
            fun build(): ClippingConfiguration {
                return buildClippingProperties()
            }

            @Deprecated("Use {@link #build()} instead.")
            fun buildClippingProperties(): ClippingProperties {
                return ClippingProperties(this)
            }
        }

        /** The start position in milliseconds. This is a value larger than or equal to zero.  */
        @IntRange(from = 0)
        val startPositionMs: Long

        /**
         * The end position in milliseconds. This is a value larger than or equal to zero or [ ][C.TIME_END_OF_SOURCE] to play to the end of the stream.
         */
        val endPositionMs: Long

        /**
         * Whether the clipping of active media periods moves with a live window. If `false`,
         * playback ends when it reaches [.endPositionMs].
         */
        val relativeToLiveWindow: Boolean

        /**
         * Whether [.startPositionMs] and [.endPositionMs] are relative to the default
         * position.
         */
        val relativeToDefaultPosition: Boolean

        /** Sets whether the start point is guaranteed to be a key frame.  */
        val startsAtKeyFrame: Boolean

        /** Returns a [Builder] initialized with the values of this instance.  */
        fun buildUpon(): Builder {
            return Builder(this)
        }

        public override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (!(obj is ClippingConfiguration)) {
                return false
            }
            val other: ClippingConfiguration = obj
            return (startPositionMs == other.startPositionMs
                    ) && (endPositionMs == other.endPositionMs
                    ) && (relativeToLiveWindow == other.relativeToLiveWindow
                    ) && (relativeToDefaultPosition == other.relativeToDefaultPosition
                    ) && (startsAtKeyFrame == other.startsAtKeyFrame)
        }

        public override fun hashCode(): Int {
            var result: Int = (startPositionMs xor (startPositionMs ushr 32)).toInt()
            result = 31 * result + (endPositionMs xor (endPositionMs ushr 32)).toInt()
            result = 31 * result + (if (relativeToLiveWindow) 1 else 0)
            result = 31 * result + (if (relativeToDefaultPosition) 1 else 0)
            result = 31 * result + (if (startsAtKeyFrame) 1 else 0)
            return result
        }

        // Bundleable implementation.
        @Documented
        @Retention(RetentionPolicy.SOURCE)
        @Target(TYPE_USE)
        @IntDef([FIELD_START_POSITION_MS, FIELD_END_POSITION_MS, FIELD_RELATIVE_TO_LIVE_WINDOW, FIELD_RELATIVE_TO_DEFAULT_POSITION, FIELD_STARTS_AT_KEY_FRAME])
        private annotation class FieldNumber constructor()

        public override fun toBundle(): Bundle {
            val bundle: Bundle = Bundle()
            bundle.putLong(keyForField(FIELD_START_POSITION_MS), startPositionMs)
            bundle.putLong(keyForField(FIELD_END_POSITION_MS), endPositionMs)
            bundle.putBoolean(keyForField(FIELD_RELATIVE_TO_LIVE_WINDOW), relativeToLiveWindow)
            bundle.putBoolean(keyForField(FIELD_RELATIVE_TO_DEFAULT_POSITION), relativeToDefaultPosition)
            bundle.putBoolean(keyForField(FIELD_STARTS_AT_KEY_FRAME), startsAtKeyFrame)
            return bundle
        }

        init {
            startPositionMs = builder.startPositionMs
            endPositionMs = builder.endPositionMs
            relativeToLiveWindow = builder.relativeToLiveWindow
            relativeToDefaultPosition = builder.relativeToDefaultPosition
            startsAtKeyFrame = builder.startsAtKeyFrame
        }

        companion object {
            /** A clipping configuration with default values.  */
            val UNSET: ClippingConfiguration = Builder().build()
            private val FIELD_START_POSITION_MS: Int = 0
            private val FIELD_END_POSITION_MS: Int = 1
            private val FIELD_RELATIVE_TO_LIVE_WINDOW: Int = 2
            private val FIELD_RELATIVE_TO_DEFAULT_POSITION: Int = 3
            private val FIELD_STARTS_AT_KEY_FRAME: Int = 4

            /** Object that can restore [ClippingConfiguration] from a [Bundle].  */
            val CREATOR: Bundleable.Creator<ClippingProperties> = Bundleable.Creator({ bundle: Bundle ->
                Builder()
                        .setStartPositionMs(
                                bundle.getLong(keyForField(FIELD_START_POSITION_MS),  /* defaultValue= */0))
                        .setEndPositionMs(
                                bundle.getLong(
                                        keyForField(FIELD_END_POSITION_MS),  /* defaultValue= */
                                        C.TIME_END_OF_SOURCE))
                        .setRelativeToLiveWindow(
                                bundle.getBoolean(keyForField(FIELD_RELATIVE_TO_LIVE_WINDOW), false))
                        .setRelativeToDefaultPosition(
                                bundle.getBoolean(keyForField(FIELD_RELATIVE_TO_DEFAULT_POSITION), false))
                        .setStartsAtKeyFrame(
                                bundle.getBoolean(keyForField(FIELD_STARTS_AT_KEY_FRAME), false))
                        .buildClippingProperties()
            })

            private fun keyForField(field: @FieldNumber Int): String {
                return Integer.toString(field, Character.MAX_RADIX)
            }
        }
    }

    @Deprecated("Use {@link ClippingConfiguration} instead.")
    class ClippingProperties(builder: Builder) : ClippingConfiguration(builder) {
        companion object {
            val UNSET: ClippingProperties = Builder().buildClippingProperties()
        }
    }

    /**
     * Metadata that helps the player to understand a playback request represented by a [ ].
     *
     *
     * This metadata is most useful for cases where playback requests are forwarded to other player
     * instances (e.g. from a `androidx.media3.session.MediaController`) and the player creating
     * the request doesn't know the required [LocalConfiguration] for playback.
     */
    class RequestMetadata private constructor(builder: Builder) : Bundleable {
        /** Builder for [RequestMetadata] instances.  */
        class Builder {
            var mediaUri: Uri? = null
            var searchQuery: String? = null
            var extras: Bundle? = null

            /** Constructs an instance.  */
            constructor() {}
            constructor(requestMetadata: RequestMetadata) {
                mediaUri = requestMetadata.mediaUri
                searchQuery = requestMetadata.searchQuery
                extras = requestMetadata.extras
            }

            /** Sets the URI of the requested media, or null if not known or applicable.  */
            @CanIgnoreReturnValue
            fun setMediaUri(mediaUri: Uri?): Builder {
                this.mediaUri = mediaUri
                return this
            }

            /** Sets the search query for the requested media, or null if not applicable.  */
            @CanIgnoreReturnValue
            fun setSearchQuery(searchQuery: String?): Builder {
                this.searchQuery = searchQuery
                return this
            }

            /** Sets optional extras [Bundle].  */
            @CanIgnoreReturnValue
            fun setExtras(extras: Bundle?): Builder {
                this.extras = extras
                return this
            }

            /** Builds the request metadata.  */
            fun build(): RequestMetadata {
                return RequestMetadata(this)
            }
        }

        /** The URI of the requested media, or null if not known or applicable.  */
        val mediaUri: Uri?

        /** The search query for the requested media, or null if not applicable.  */
        val searchQuery: String?

        /**
         * Optional extras [Bundle].
         *
         *
         * Given the complexities of checking the equality of two [Bundle]s, this is not
         * considered in the [.equals] or [.hashCode].
         */
        val extras: Bundle?

        /** Returns a [Builder] initialized with the values of this instance.  */
        fun buildUpon(): Builder {
            return Builder(this)
        }

        public override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (!(o is RequestMetadata)) {
                return false
            }
            val that: RequestMetadata = o
            return Util.areEqual(mediaUri, that.mediaUri) && Util.areEqual(searchQuery, that.searchQuery)
        }

        public override fun hashCode(): Int {
            var result: Int = if (mediaUri == null) 0 else mediaUri.hashCode()
            result = 31 * result + (if (searchQuery == null) 0 else searchQuery.hashCode())
            return result
        }

        // Bundleable implementation.
        @Documented
        @Retention(RetentionPolicy.SOURCE)
        @Target(TYPE_USE)
        @IntDef([FIELD_MEDIA_URI, FIELD_SEARCH_QUERY, FIELD_EXTRAS])
        private annotation class FieldNumber constructor()

        public override fun toBundle(): Bundle {
            val bundle: Bundle = Bundle()
            if (mediaUri != null) {
                bundle.putParcelable(keyForField(FIELD_MEDIA_URI), mediaUri)
            }
            if (searchQuery != null) {
                bundle.putString(keyForField(FIELD_SEARCH_QUERY), searchQuery)
            }
            if (extras != null) {
                bundle.putBundle(keyForField(FIELD_EXTRAS), extras)
            }
            return bundle
        }

        init {
            mediaUri = builder.mediaUri
            searchQuery = builder.searchQuery
            extras = builder.extras
        }

        companion object {
            /** Empty request metadata.  */
            val EMPTY: RequestMetadata = Builder().build()
            private val FIELD_MEDIA_URI: Int = 0
            private val FIELD_SEARCH_QUERY: Int = 1
            private val FIELD_EXTRAS: Int = 2

            /** Object that can restore [RequestMetadata] from a [Bundle].  */
            val CREATOR: Bundleable.Creator<RequestMetadata> = Bundleable.Creator({ bundle: Bundle ->
                Builder()
                        .setMediaUri(bundle.getParcelable(keyForField(FIELD_MEDIA_URI)))
                        .setSearchQuery(bundle.getString(keyForField(FIELD_SEARCH_QUERY)))
                        .setExtras(bundle.getBundle(keyForField(FIELD_EXTRAS)))
                        .build()
            })

            private fun keyForField(field: @FieldNumber Int): String {
                return Integer.toString(field, Character.MAX_RADIX)
            }
        }
    }

    /**
     * Optional configuration for local playback. May be `null` if shared over process
     * boundaries.
     */
    val localConfiguration: LocalConfiguration?

    @Deprecated("Use {@link #localConfiguration} instead.")
    val playbackProperties: PlaybackProperties?

    /** The live playback configuration.  */
    val liveConfiguration: LiveConfiguration

    /** The media metadata.  */
    val mediaMetadata: MediaMetadata

    /** The clipping properties.  */
    val clippingConfiguration: ClippingConfiguration

    @Deprecated("Use {@link #clippingConfiguration} instead.")
    val clippingProperties: ClippingProperties

    /** The media [RequestMetadata].  */
    val requestMetadata: RequestMetadata

    /** Returns a [Builder] initialized with the values of this instance.  */
    fun buildUpon(): Builder {
        return Builder(this)
    }

    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (!(obj is MediaItem)) {
            return false
        }
        val other: MediaItem = obj
        return (Util.areEqual(mediaId, other.mediaId)
                && (clippingConfiguration == other.clippingConfiguration) && Util.areEqual(localConfiguration, other.localConfiguration)
                && Util.areEqual(liveConfiguration, other.liveConfiguration)
                && Util.areEqual(mediaMetadata, other.mediaMetadata)
                && Util.areEqual(requestMetadata, other.requestMetadata))
    }

    public override fun hashCode(): Int {
        var result: Int = mediaId.hashCode()
        result = 31 * result + (if (localConfiguration != null) localConfiguration.hashCode() else 0)
        result = 31 * result + liveConfiguration.hashCode()
        result = 31 * result + clippingConfiguration.hashCode()
        result = 31 * result + mediaMetadata.hashCode()
        result = 31 * result + requestMetadata.hashCode()
        return result
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([FIELD_MEDIA_ID, FIELD_LIVE_CONFIGURATION, FIELD_MEDIA_METADATA, FIELD_CLIPPING_PROPERTIES, FIELD_REQUEST_METADATA])
    private annotation class FieldNumber constructor()

    /**
     * {@inheritDoc}
     *
     *
     * It omits the [.localConfiguration] field. The [.localConfiguration] of an
     * instance restored by [.CREATOR] will always be `null`.
     */
    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putString(keyForField(FIELD_MEDIA_ID), mediaId)
        bundle.putBundle(keyForField(FIELD_LIVE_CONFIGURATION), liveConfiguration.toBundle())
        bundle.putBundle(keyForField(FIELD_MEDIA_METADATA), mediaMetadata.toBundle())
        bundle.putBundle(keyForField(FIELD_CLIPPING_PROPERTIES), clippingConfiguration.toBundle())
        bundle.putBundle(keyForField(FIELD_REQUEST_METADATA), requestMetadata.toBundle())
        return bundle
    }

    // Using PlaybackProperties and ClippingProperties until they're deleted.
    init {
        this.localConfiguration = localConfiguration
        playbackProperties = localConfiguration
        this.liveConfiguration = liveConfiguration
        this.mediaMetadata = mediaMetadata
        this.clippingConfiguration = clippingConfiguration
        clippingProperties = clippingConfiguration
        this.requestMetadata = requestMetadata
    }

    companion object {
        /**
         * Creates a [MediaItem] for the given URI.
         *
         * @param uri The URI.
         * @return An [MediaItem] for the given URI.
         */
        fun fromUri(uri: String?): MediaItem {
            return Builder().setUri(uri).build()
        }

        /**
         * Creates a [MediaItem] for the given [URI][Uri].
         *
         * @param uri The [uri][Uri].
         * @return An [MediaItem] for the given URI.
         */
        fun fromUri(uri: Uri?): MediaItem {
            return Builder().setUri(uri).build()
        }

        /**
         * The default media ID that is used if the media ID is not explicitly set by [ ][Builder.setMediaId].
         */
        val DEFAULT_MEDIA_ID: String = ""

        /** Empty [MediaItem].  */
        val EMPTY: MediaItem = Builder().build()
        private val FIELD_MEDIA_ID: Int = 0
        private val FIELD_LIVE_CONFIGURATION: Int = 1
        private val FIELD_MEDIA_METADATA: Int = 2
        private val FIELD_CLIPPING_PROPERTIES: Int = 3
        private val FIELD_REQUEST_METADATA: Int = 4

        /**
         * Object that can restore [MediaItem] from a [Bundle].
         *
         *
         * The [.localConfiguration] of a restored instance will always be `null`.
         */
        val CREATOR: Bundleable.Creator<MediaItem> = Bundleable.Creator({ bundle: Bundle -> fromBundle(bundle) })

        // Unbundling to ClippingProperties while it still exists.
        private fun fromBundle(bundle: Bundle): MediaItem {
            val mediaId: String? = Assertions.checkNotNull(bundle.getString(keyForField(FIELD_MEDIA_ID), DEFAULT_MEDIA_ID))
            val liveConfigurationBundle: Bundle? = bundle.getBundle(keyForField(FIELD_LIVE_CONFIGURATION))
            val liveConfiguration: LiveConfiguration
            if (liveConfigurationBundle == null) {
                liveConfiguration = LiveConfiguration.UNSET
            } else {
                liveConfiguration = LiveConfiguration.CREATOR.fromBundle(liveConfigurationBundle)
            }
            val mediaMetadataBundle: Bundle? = bundle.getBundle(keyForField(FIELD_MEDIA_METADATA))
            val mediaMetadata: MediaMetadata
            if (mediaMetadataBundle == null) {
                mediaMetadata = MediaMetadata.Companion.EMPTY
            } else {
                mediaMetadata = MediaMetadata.Companion.CREATOR.fromBundle(mediaMetadataBundle)
            }
            val clippingConfigurationBundle: Bundle? = bundle.getBundle(keyForField(FIELD_CLIPPING_PROPERTIES))
            val clippingConfiguration: ClippingProperties
            if (clippingConfigurationBundle == null) {
                clippingConfiguration = ClippingProperties.UNSET
            } else {
                clippingConfiguration = ClippingConfiguration.CREATOR.fromBundle(clippingConfigurationBundle)
            }
            val requestMetadataBundle: Bundle? = bundle.getBundle(keyForField(FIELD_REQUEST_METADATA))
            val requestMetadata: RequestMetadata
            if (requestMetadataBundle == null) {
                requestMetadata = RequestMetadata.EMPTY
            } else {
                requestMetadata = RequestMetadata.CREATOR.fromBundle(requestMetadataBundle)
            }
            return MediaItem(
                    mediaId,
                    clippingConfiguration,  /* localConfiguration= */
                    null,
                    liveConfiguration,
                    mediaMetadata,
                    requestMetadata)
        }

        private fun keyForField(field: @FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}