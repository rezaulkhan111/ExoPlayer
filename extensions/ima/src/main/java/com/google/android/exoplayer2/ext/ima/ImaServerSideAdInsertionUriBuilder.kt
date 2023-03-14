/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.ima

import android.net.Uri
import android.text.TextUtils
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import com.google.ads.interactivemedia.v3.api.StreamRequest
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.util.Assertions
import com.google.common.collect.ImmutableMap
import com.google.errorprone.annotations.CanIgnoreReturnValue

/**
 * Builder for URI for IMA DAI streams. The resulting URI can be used to build a [ ][com.google.android.exoplayer2.MediaItem.fromUri] that can be played by the [ ].
 */
class ImaServerSideAdInsertionUriBuilder {
    private var adsId: String? = null
    private var assetKey: String? = null
    private var apiKey: String? = null
    private var contentSourceId: String? = null
    private var videoId: String? = null
    private var manifestSuffix: String? = null
    private var contentUrl: String? = null
    private var authToken: String? = null
    private var streamActivityMonitorId: String? = null
    private var adTagParameters: ImmutableMap<String, String>
    var format: @C.ContentType Int
    private var loadVideoTimeoutMs: Int

    /** Creates a new instance.  */
    init {
        adTagParameters = ImmutableMap.of()
        loadVideoTimeoutMs = DEFAULT_LOAD_VIDEO_TIMEOUT_MS
        format = C.CONTENT_TYPE_OTHER
    }

    /**
     * An opaque identifier for associated ad playback state, or `null` if the [ ][.setAssetKey] (for live) or [video id][.setVideoId] (for VOD)
     * should be used as the ads identifier.
     *
     * @param adsId The ads identifier.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setAdsId(adsId: String?): ImaServerSideAdInsertionUriBuilder {
        this.adsId = adsId
        return this
    }

    /**
     * The stream request asset key used for live streams.
     *
     * @param assetKey Live stream asset key.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setAssetKey(assetKey: String?): ImaServerSideAdInsertionUriBuilder {
        this.assetKey = assetKey
        return this
    }

    /**
     * Sets the stream request authorization token. Used in place of [the API][.setApiKey] for stricter content authorization. The publisher can control individual content streams
     * authorizations based on this token.
     *
     * @param authToken Live stream authorization token.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setAuthToken(authToken: String?): ImaServerSideAdInsertionUriBuilder {
        this.authToken = authToken
        return this
    }

    /**
     * The stream request content source ID used for on-demand streams.
     *
     * @param contentSourceId VOD stream content source id.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setContentSourceId(contentSourceId: String?): ImaServerSideAdInsertionUriBuilder {
        this.contentSourceId = contentSourceId
        return this
    }

    /**
     * The stream request video ID used for on-demand streams.
     *
     * @param videoId VOD stream video id.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setVideoId(videoId: String?): ImaServerSideAdInsertionUriBuilder {
        this.videoId = videoId
        return this
    }

    /**
     * Sets the format of the stream request.
     *
     * @param format [C.TYPE_DASH] or [C.TYPE_HLS].
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setFormat(format: @C.ContentType Int): ImaServerSideAdInsertionUriBuilder {
        Assertions.checkArgument(format == C.CONTENT_TYPE_DASH || format == C.CONTENT_TYPE_HLS)
        this.format = format
        return this
    }

    /**
     * The stream request API key. This is used for content authentication. The API key is provided to
     * the publisher to unlock their content. It's a security measure used to verify the applications
     * that are attempting to access the content.
     *
     * @param apiKey Stream api key.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setApiKey(apiKey: String?): ImaServerSideAdInsertionUriBuilder {
        this.apiKey = apiKey
        return this
    }

    /**
     * Sets the ID to be used to debug the stream with the stream activity monitor. This is used to
     * provide a convenient way to allow publishers to find a stream log in the stream activity
     * monitor tool.
     *
     * @param streamActivityMonitorId ID for debugging the stream with the stream activity monitor.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setStreamActivityMonitorId(
            streamActivityMonitorId: String?): ImaServerSideAdInsertionUriBuilder {
        this.streamActivityMonitorId = streamActivityMonitorId
        return this
    }

    /**
     * Sets the overridable ad tag parameters on the stream request. [Supply targeting parameters to your
 * stream](//support.google.com/dfp_premium/answer/7320899) provides more information.
     *
     *
     * You can use the dai-ot and dai-ov parameters for stream variant preference. See [Override Stream Variant Parameters](//support.google.com/dfp_premium/answer/7320898)
     * for more information.
     *
     * @param adTagParameters A map of extra parameters to pass to the ad server.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setAdTagParameters(
            adTagParameters: Map<String, String>?): ImaServerSideAdInsertionUriBuilder {
        this.adTagParameters = ImmutableMap.copyOf(adTagParameters)
        return this
    }

    /**
     * Sets the optional stream manifest's suffix, which will be appended to the stream manifest's
     * URL. The provided string must be URL-encoded and must not include a leading question mark.
     *
     * @param manifestSuffix Stream manifest's suffix.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setManifestSuffix(manifestSuffix: String?): ImaServerSideAdInsertionUriBuilder {
        this.manifestSuffix = manifestSuffix
        return this
    }

    /**
     * Specifies the deep link to the content's screen. If provided, this parameter is passed to the
     * OM SDK. See [Android
 * documentation](//developer.android.com/training/app-links/deep-linking) for more information.
     *
     * @param contentUrl Deep link to the content's screen.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setContentUrl(contentUrl: String?): ImaServerSideAdInsertionUriBuilder {
        this.contentUrl = contentUrl
        return this
    }

    /**
     * Sets the duration after which resolving the video URI should time out, in milliseconds.
     *
     *
     * The default is [.DEFAULT_LOAD_VIDEO_TIMEOUT_MS] milliseconds.
     *
     * @param loadVideoTimeoutMs The timeout after which to give up resolving the video URI.
     * @return This instance, for convenience.
     */
    @CanIgnoreReturnValue
    fun setLoadVideoTimeoutMs(loadVideoTimeoutMs: Int): ImaServerSideAdInsertionUriBuilder {
        this.loadVideoTimeoutMs = loadVideoTimeoutMs
        return this
    }

    /**
     * Builds a URI with the builder's current values.
     *
     * @return The build [Uri].
     * @throws IllegalStateException If the builder has missing or invalid inputs.
     */
    fun build(): Uri {
        Assertions.checkState((TextUtils.isEmpty(assetKey)
                && !TextUtils.isEmpty(contentSourceId)
                && !TextUtils.isEmpty(videoId))
                || (!TextUtils.isEmpty(assetKey)
                && TextUtils.isEmpty(contentSourceId)
                && TextUtils.isEmpty(videoId)))
        Assertions.checkState(format != C.CONTENT_TYPE_OTHER)
        var adsId = adsId
        if (adsId == null) {
            adsId = if (assetKey != null) assetKey else Assertions.checkNotNull(videoId)
        }
        val dataUriBuilder = Uri.Builder()
        dataUriBuilder.scheme(C.SSAI_SCHEME)
        dataUriBuilder.authority(IMA_AUTHORITY)
        dataUriBuilder.appendQueryParameter(ADS_ID, adsId)
        if (loadVideoTimeoutMs != DEFAULT_LOAD_VIDEO_TIMEOUT_MS) {
            dataUriBuilder.appendQueryParameter(
                    LOAD_VIDEO_TIMEOUT_MS, loadVideoTimeoutMs.toString())
        }
        if (assetKey != null) {
            dataUriBuilder.appendQueryParameter(ASSET_KEY, assetKey)
        }
        if (apiKey != null) {
            dataUriBuilder.appendQueryParameter(API_KEY, apiKey)
        }
        if (contentSourceId != null) {
            dataUriBuilder.appendQueryParameter(CONTENT_SOURCE_ID, contentSourceId)
        }
        if (videoId != null) {
            dataUriBuilder.appendQueryParameter(VIDEO_ID, videoId)
        }
        if (manifestSuffix != null) {
            dataUriBuilder.appendQueryParameter(MANIFEST_SUFFIX, manifestSuffix)
        }
        if (contentUrl != null) {
            dataUriBuilder.appendQueryParameter(CONTENT_URL, contentUrl)
        }
        if (authToken != null) {
            dataUriBuilder.appendQueryParameter(AUTH_TOKEN, authToken)
        }
        if (streamActivityMonitorId != null) {
            dataUriBuilder.appendQueryParameter(STREAM_ACTIVITY_MONITOR_ID, streamActivityMonitorId)
        }
        if (!adTagParameters.isEmpty()) {
            val adTagParametersUriBuilder = Uri.Builder()
            for ((key, value) in adTagParameters) {
                adTagParametersUriBuilder.appendQueryParameter(key, value)
            }
            dataUriBuilder.appendQueryParameter(
                    AD_TAG_PARAMETERS, adTagParametersUriBuilder.build().toString())
        }
        dataUriBuilder.appendQueryParameter(FORMAT, format.toString())
        return dataUriBuilder.build()
    }

    companion object {
        /** The default timeout for loading the video URI, in milliseconds.  */
        const val DEFAULT_LOAD_VIDEO_TIMEOUT_MS = 10000

        /* package */
        const val IMA_AUTHORITY = "dai.google.com"
        private const val ADS_ID = "adsId"
        private const val ASSET_KEY = "assetKey"
        private const val API_KEY = "apiKey"
        private const val CONTENT_SOURCE_ID = "contentSourceId"
        private const val VIDEO_ID = "videoId"
        private const val AD_TAG_PARAMETERS = "adTagParameters"
        private const val MANIFEST_SUFFIX = "manifestSuffix"
        private const val CONTENT_URL = "contentUrl"
        private const val AUTH_TOKEN = "authToken"
        private const val STREAM_ACTIVITY_MONITOR_ID = "streamActivityMonitorId"
        private const val FORMAT = "format"
        private const val LOAD_VIDEO_TIMEOUT_MS = "loadVideoTimeoutMs"

        /** Returns whether the provided request is for a live stream or false if it is a VOD stream.  */ /* package */
        @JvmStatic
        fun isLiveStream(uri: Uri): Boolean {
            return !TextUtils.isEmpty(uri.getQueryParameter(ASSET_KEY))
        }

        /** Returns the opaque adsId for this stream.  */ /* package */
        @JvmStatic
        fun getAdsId(uri: Uri): String {
            return Assertions.checkNotNull(uri.getQueryParameter(ADS_ID))
        }

        /** Returns the video load timeout in milliseconds.  */ /* package */
        @JvmStatic
        fun getLoadVideoTimeoutMs(uri: Uri): Int {
            val adsLoaderTimeoutUs = uri.getQueryParameter(LOAD_VIDEO_TIMEOUT_MS)
            return if (TextUtils.isEmpty(adsLoaderTimeoutUs)) DEFAULT_LOAD_VIDEO_TIMEOUT_MS else adsLoaderTimeoutUs!!.toInt()
        }

        /** Returns the corresponding [StreamRequest].  */
        @JvmStatic
        fun createStreamRequest(uri: Uri): StreamRequest {
            require(!(C.SSAI_SCHEME != uri.scheme || IMA_AUTHORITY != uri.authority)) { "Invalid URI scheme or authority." }
            val streamRequest: StreamRequest
            // Required params.
            val assetKey = uri.getQueryParameter(ASSET_KEY)
            val apiKey = uri.getQueryParameter(API_KEY)
            val contentSourceId = uri.getQueryParameter(CONTENT_SOURCE_ID)
            val videoId = uri.getQueryParameter(VIDEO_ID)
            streamRequest = if (!TextUtils.isEmpty(assetKey)) {
                ImaSdkFactory.getInstance().createLiveStreamRequest(assetKey, apiKey)
            } else {
                ImaSdkFactory.getInstance()
                        .createVodStreamRequest(Assertions.checkNotNull(contentSourceId), Assertions.checkNotNull(videoId), apiKey)
            }
            val format = uri.getQueryParameter(FORMAT)!!.toInt()
            if (format == C.CONTENT_TYPE_DASH) {
                streamRequest.format = StreamFormat.DASH
            } else if (format == C.CONTENT_TYPE_HLS) {
                streamRequest.format = StreamFormat.HLS
            } else {
                throw IllegalArgumentException("Unsupported stream format:$format")
            }
            // Optional params.
            val adTagParametersValue = uri.getQueryParameter(AD_TAG_PARAMETERS)
            if (!TextUtils.isEmpty(adTagParametersValue)) {
                val adTagParameters: MutableMap<String, String?> = HashMap()
                val adTagParametersUri = Uri.parse(adTagParametersValue)
                for (paramName in adTagParametersUri.queryParameterNames) {
                    val singleAdTagParameterValue = adTagParametersUri.getQueryParameter(paramName)
                    if (!TextUtils.isEmpty(singleAdTagParameterValue)) {
                        adTagParameters[paramName] = singleAdTagParameterValue
                    }
                }
                streamRequest.setAdTagParameters(adTagParameters)
            }
            val manifestSuffix = uri.getQueryParameter(MANIFEST_SUFFIX)
            if (manifestSuffix != null) {
                streamRequest.manifestSuffix = manifestSuffix
            }
            val contentUrl = uri.getQueryParameter(CONTENT_URL)
            if (contentUrl != null) {
                streamRequest.contentUrl = contentUrl
            }
            val authToken = uri.getQueryParameter(AUTH_TOKEN)
            if (authToken != null) {
                streamRequest.authToken = authToken
            }
            val streamActivityMonitorId = uri.getQueryParameter(STREAM_ACTIVITY_MONITOR_ID)
            if (streamActivityMonitorId != null) {
                streamRequest.streamActivityMonitorId = streamActivityMonitorId
            }
            Assertions.checkState(streamRequest.format != StreamFormat.DASH
                    || TextUtils.isEmpty(streamRequest.assetKey),
                    "DASH live streams are not supported yet.")
            return streamRequest
        }
    }
}