/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntRange
import androidx.annotation.VisibleForTesting
import com.google.ads.interactivemedia.v3.api.*
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader
import com.google.android.exoplayer2.ext.ima.ImaUtil.ImaFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ads.*
import com.google.android.exoplayer2.ui.AdViewProvider
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.io.IOException
import java.util.*

/**
 * [AdsLoader] using the IMA SDK. All methods must be called on the main thread.
 *
 *
 * The player instance that will play the loaded ads must be set before playback using [ ][.setPlayer]. If the ads loader is no longer required, it must be released by calling
 * [.release].
 *
 *
 * See [IMA's
 * Support and compatibility page](https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility) for information on compatible ad tag formats. Pass the ad tag
 * URI when setting media item playback properties (if using the media item API) or as a [ ] when constructing the [AdsMediaSource] (if using media sources directly). For the
 * latter case, please note that this implementation delegates loading of the data spec to the IMA
 * SDK, so range and headers specifications will be ignored in ad tag URIs. Literal ads responses
 * can be encoded as data scheme data specs, for example, by constructing the data spec using a URI
 * generated via [Util.getDataUriForString].
 *
 *
 * The IMA SDK can report obstructions to the ad view for accurate viewability measurement. This
 * means that any overlay views that obstruct the ad overlay but are essential for playback need to
 * be registered via the [AdViewProvider] passed to the [AdsMediaSource]. See the [IMA
 * SDK Open Measurement documentation](https://developers.google.com/interactive-media-ads/docs/sdks/android/client-side/omsdk) for more information.
 */
class ImaAdsLoader private constructor(
        context: Context, configuration: ImaUtil.Configuration, imaFactory: ImaFactory) : com.google.android.exoplayer2.source.ads.AdsLoader {
    /** Builder for [ImaAdsLoader].  */
    class Builder(context: Context?) {
        private val context: Context
        private var imaSdkSettings: ImaSdkSettings? = null
        private var adErrorListener: AdErrorListener? = null
        private var adEventListener: AdEventListener? = null
        private var videoAdPlayerCallback: VideoAdPlayerCallback? = null
        private var adMediaMimeTypes: List<String>? = null
        private var adUiElements: Set<UiElement>? = null
        private var companionAdSlots: Collection<CompanionAdSlot>? = null
        private var enableContinuousPlayback: Boolean? = null
        private var adPreloadTimeoutMs: Long
        private var vastLoadTimeoutMs: Int
        private var mediaLoadTimeoutMs: Int
        private var mediaBitrate: Int
        private var focusSkipButtonWhenAvailable: Boolean
        private var playAdBeforeStartPosition: Boolean
        private var debugModeEnabled = false
        private var imaFactory: ImaFactory

        /**
         * Creates a new builder for [ImaAdsLoader].
         *
         * @param context The context;
         */
        init {
            this.context = Assertions.checkNotNull(context).applicationContext
            adPreloadTimeoutMs = DEFAULT_AD_PRELOAD_TIMEOUT_MS
            vastLoadTimeoutMs = ImaUtil.TIMEOUT_UNSET
            mediaLoadTimeoutMs = ImaUtil.TIMEOUT_UNSET
            mediaBitrate = ImaUtil.BITRATE_UNSET
            focusSkipButtonWhenAvailable = true
            playAdBeforeStartPosition = true
            imaFactory = DefaultImaFactory()
        }

        /**
         * Sets the IMA SDK settings. The provided settings instance's player type and version fields
         * may be overwritten.
         *
         *
         * If this method is not called the default settings will be used.
         *
         * @param imaSdkSettings The [ImaSdkSettings].
         * @return This builder, for convenience.
         */
        @CanIgnoreReturnValue
        fun setImaSdkSettings(imaSdkSettings: ImaSdkSettings?): Builder {
            this.imaSdkSettings = Assertions.checkNotNull(imaSdkSettings)
            return this
        }

        /**
         * Sets a listener for ad errors that will be passed to [ ][com.google.ads.interactivemedia.v3.api.AdsLoader.addAdErrorListener] and
         * [AdsManager.addAdErrorListener].
         *
         * @param adErrorListener The ad error listener.
         * @return This builder, for convenience.
         */
        @CanIgnoreReturnValue
        fun setAdErrorListener(adErrorListener: AdErrorListener?): Builder {
            this.adErrorListener = Assertions.checkNotNull(adErrorListener)
            return this
        }

        /**
         * Sets a listener for ad events that will be passed to [ ][AdsManager.addAdEventListener].
         *
         * @param adEventListener The ad event listener.
         * @return This builder, for convenience.
         */
        @CanIgnoreReturnValue
        fun setAdEventListener(adEventListener: AdEventListener?): Builder {
            this.adEventListener = Assertions.checkNotNull(adEventListener)
            return this
        }

        /**
         * Sets a callback to receive video ad player events. Note that these events are handled
         * internally by the IMA SDK and this ads loader. For analytics and diagnostics, new
         * implementations should generally use events from the top-level [Player] listeners
         * instead of setting a callback via this method.
         *
         * @param videoAdPlayerCallback The callback to receive video ad player events.
         * @return This builder, for convenience.
         * @see com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback
         */
        @CanIgnoreReturnValue
        fun setVideoAdPlayerCallback(
                videoAdPlayerCallback: VideoAdPlayerCallback?): Builder {
            this.videoAdPlayerCallback = Assertions.checkNotNull(videoAdPlayerCallback)
            return this
        }

        /**
         * Sets the ad UI elements to be rendered by the IMA SDK.
         *
         * @param adUiElements The ad UI elements to be rendered by the IMA SDK.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setUiElements
         */
        @CanIgnoreReturnValue
        fun setAdUiElements(adUiElements: Set<UiElement>): Builder {
            this.adUiElements = ImmutableSet.copyOf(Assertions.checkNotNull(adUiElements))
            return this
        }

        /**
         * Sets the slots to use for companion ads, if they are present in the loaded ad.
         *
         * @param companionAdSlots The slots to use for companion ads.
         * @return This builder, for convenience.
         * @see AdDisplayContainer.setCompanionSlots
         */
        @CanIgnoreReturnValue
        fun setCompanionAdSlots(companionAdSlots: Collection<CompanionAdSlot>): Builder {
            this.companionAdSlots = ImmutableList.copyOf(Assertions.checkNotNull(companionAdSlots))
            return this
        }

        /**
         * Sets the MIME types to prioritize for linear ad media. If not specified, MIME types supported
         * by the [adMediaSourceFactory][MediaSource.Factory] used to construct the [ ] will be used.
         *
         * @param adMediaMimeTypes The MIME types to prioritize for linear ad media. May contain [     ][MimeTypes.APPLICATION_MPD], [MimeTypes.APPLICATION_M3U8], [     ][MimeTypes.VIDEO_MP4], [MimeTypes.VIDEO_WEBM], [MimeTypes.VIDEO_H263], [     ][MimeTypes.AUDIO_MP4] and [MimeTypes.AUDIO_MPEG].
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setMimeTypes
         */
        @CanIgnoreReturnValue
        fun setAdMediaMimeTypes(adMediaMimeTypes: List<String>): Builder {
            this.adMediaMimeTypes = ImmutableList.copyOf(Assertions.checkNotNull(adMediaMimeTypes))
            return this
        }

        /**
         * Sets whether to enable continuous playback. Pass `true` if content videos will be
         * played continuously, similar to a TV broadcast. This setting may modify the ads request but
         * does not affect ad playback behavior. The requested value is unknown by default.
         *
         * @param enableContinuousPlayback Whether to enable continuous playback.
         * @return This builder, for convenience.
         * @see AdsRequest.setContinuousPlayback
         */
        @CanIgnoreReturnValue
        fun setEnableContinuousPlayback(enableContinuousPlayback: Boolean): Builder {
            this.enableContinuousPlayback = enableContinuousPlayback
            return this
        }

        /**
         * Sets the duration in milliseconds for which the player must buffer while preloading an ad
         * group before that ad group is skipped and marked as having failed to load. Pass [ ][C.TIME_UNSET] if there should be no such timeout. The default value is {@value
         * * #DEFAULT_AD_PRELOAD_TIMEOUT_MS} ms.
         *
         *
         * The purpose of this timeout is to avoid playback getting stuck in the unexpected case that
         * the IMA SDK does not load an ad break based on the player's reported content position.
         *
         * @param adPreloadTimeoutMs The timeout buffering duration in milliseconds, or [     ][C.TIME_UNSET] for no timeout.
         * @return This builder, for convenience.
         */
        @CanIgnoreReturnValue
        fun setAdPreloadTimeoutMs(adPreloadTimeoutMs: Long): Builder {
            Assertions.checkArgument(adPreloadTimeoutMs == C.TIME_UNSET || adPreloadTimeoutMs > 0)
            this.adPreloadTimeoutMs = adPreloadTimeoutMs
            return this
        }

        /**
         * Sets the VAST load timeout, in milliseconds.
         *
         * @param vastLoadTimeoutMs The VAST load timeout, in milliseconds.
         * @return This builder, for convenience.
         * @see AdsRequest.setVastLoadTimeout
         */
        @CanIgnoreReturnValue
        fun setVastLoadTimeoutMs(@IntRange(from = 1) vastLoadTimeoutMs: Int): Builder {
            Assertions.checkArgument(vastLoadTimeoutMs > 0)
            this.vastLoadTimeoutMs = vastLoadTimeoutMs
            return this
        }

        /**
         * Sets the ad media load timeout, in milliseconds.
         *
         * @param mediaLoadTimeoutMs The ad media load timeout, in milliseconds.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setLoadVideoTimeout
         */
        @CanIgnoreReturnValue
        fun setMediaLoadTimeoutMs(@IntRange(from = 1) mediaLoadTimeoutMs: Int): Builder {
            Assertions.checkArgument(mediaLoadTimeoutMs > 0)
            this.mediaLoadTimeoutMs = mediaLoadTimeoutMs
            return this
        }

        /**
         * Sets the media maximum recommended bitrate for ads, in bps.
         *
         * @param bitrate The media maximum recommended bitrate for ads, in bps.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setBitrateKbps
         */
        @CanIgnoreReturnValue
        fun setMaxMediaBitrate(@IntRange(from = 1) bitrate: Int): Builder {
            Assertions.checkArgument(bitrate > 0)
            mediaBitrate = bitrate
            return this
        }

        /**
         * Sets whether to focus the skip button (when available) on Android TV devices. The default
         * setting is `true`.
         *
         * @param focusSkipButtonWhenAvailable Whether to focus the skip button (when available) on
         * Android TV devices.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setFocusSkipButtonWhenAvailable
         */
        @CanIgnoreReturnValue
        fun setFocusSkipButtonWhenAvailable(focusSkipButtonWhenAvailable: Boolean): Builder {
            this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable
            return this
        }

        /**
         * Sets whether to play an ad before the start position when beginning playback. If `true`, an ad will be played if there is one at or before the start position. If `false`, an ad will be played only if there is one exactly at the start position. The default
         * setting is `true`.
         *
         * @param playAdBeforeStartPosition Whether to play an ad before the start position when
         * beginning playback.
         * @return This builder, for convenience.
         */
        @CanIgnoreReturnValue
        fun setPlayAdBeforeStartPosition(playAdBeforeStartPosition: Boolean): Builder {
            this.playAdBeforeStartPosition = playAdBeforeStartPosition
            return this
        }

        /**
         * Sets whether to enable outputting verbose logs for the IMA extension and IMA SDK. The default
         * value is `false`. This setting is intended for debugging only, and should not be
         * enabled in production applications.
         *
         * @param debugModeEnabled Whether to enable outputting verbose logs for the IMA extension and
         * IMA SDK.
         * @return This builder, for convenience.
         * @see ImaSdkSettings.setDebugMode
         */
        @CanIgnoreReturnValue
        fun setDebugModeEnabled(debugModeEnabled: Boolean): Builder {
            this.debugModeEnabled = debugModeEnabled
            return this
        }

        @CanIgnoreReturnValue
        @VisibleForTesting
        fun  /* package */setImaFactory(imaFactory: ImaFactory?): Builder {
            this.imaFactory = Assertions.checkNotNull(imaFactory)
            return this
        }

        /** Returns a new [ImaAdsLoader].  */
        fun build(): ImaAdsLoader {
            return ImaAdsLoader(
                    context,
                    ImaUtil.Configuration(
                            adPreloadTimeoutMs,
                            vastLoadTimeoutMs,
                            mediaLoadTimeoutMs,
                            focusSkipButtonWhenAvailable,
                            playAdBeforeStartPosition,
                            mediaBitrate,
                            enableContinuousPlayback,
                            adMediaMimeTypes,
                            adUiElements,
                            companionAdSlots,
                            adErrorListener,
                            adEventListener,
                            videoAdPlayerCallback,
                            imaSdkSettings,
                            debugModeEnabled),
                    imaFactory)
        }

        companion object {
            /**
             * The default duration in milliseconds for which the player must buffer while preloading an ad
             * group before that ad group is skipped and marked as having failed to load.
             *
             *
             * This value should be large enough not to trigger discarding the ad when it actually might
             * load soon, but small enough so that user is not waiting for too long.
             *
             * @see .setAdPreloadTimeoutMs
             */
            const val DEFAULT_AD_PRELOAD_TIMEOUT_MS = 10 * C.MILLIS_PER_SECOND
        }
    }

    private val configuration: ImaUtil.Configuration
    private val context: Context
    private val imaFactory: ImaFactory
    private val playerListener: PlayerListenerImpl
    private val adTagLoaderByAdsId: HashMap<Any, AdTagLoader>
    private val adTagLoaderByAdsMediaSource: HashMap<AdsMediaSource, AdTagLoader>
    private val period: Timeline.Period
    private val window: Timeline.Window
    private var wasSetPlayerCalled = false
    private var nextPlayer: Player? = null
    private var supportedMimeTypes: List<String>
    private var player: Player? = null
    private var currentAdTagLoader: AdTagLoader? = null

    init {
        this.context = context.applicationContext
        this.configuration = configuration
        this.imaFactory = imaFactory
        playerListener = PlayerListenerImpl()
        supportedMimeTypes = ImmutableList.of()
        adTagLoaderByAdsId = HashMap()
        adTagLoaderByAdsMediaSource = HashMap()
        period = Timeline.Period()
        window = Timeline.Window()
    }

    /**
     * Returns the underlying [com.google.ads.interactivemedia.v3.api.AdsLoader] wrapped by this
     * instance, or `null` if ads have not been requested yet.
     */
    val adsLoader: AdsLoader?
        get() = if (currentAdTagLoader != null) currentAdTagLoader.getAdsLoader() else null

    /**
     * Returns the [AdDisplayContainer] used by this loader, or `null` if ads have not
     * been requested yet.
     *
     *
     * Note: any video controls overlays registered via [ ][AdDisplayContainer.registerFriendlyObstruction] will be unregistered
     * automatically when the media source detaches from this instance. It is therefore necessary to
     * re-register views each time the ads loader is reused. Alternatively, provide overlay views via
     * the [AdViewProvider] when creating the media source to benefit from automatic
     * registration.
     */
    val adDisplayContainer: AdDisplayContainer?
        get() = if (currentAdTagLoader != null) currentAdTagLoader.getAdDisplayContainer() else null

    /**
     * Requests ads, if they have not already been requested. Must be called on the main thread.
     *
     *
     * Ads will be requested automatically when the player is prepared if this method has not been
     * called, so it is only necessary to call this method if you want to request ads before preparing
     * the player.
     *
     * @param adTagDataSpec The data specification of the ad tag to load. See class javadoc for
     * information about compatible ad tag formats.
     * @param adsId A opaque identifier for the ad playback state across start/stop calls.
     * @param adViewGroup A [ViewGroup] on top of the player that will show any ad UI, or `null` if playing audio-only ads.
     */
    fun requestAds(adTagDataSpec: DataSpec, adsId: Any, adViewGroup: ViewGroup?) {
        if (!adTagLoaderByAdsId.containsKey(adsId)) {
            val adTagLoader = AdTagLoader(
                    context,
                    configuration,
                    imaFactory,
                    supportedMimeTypes,
                    adTagDataSpec,
                    adsId,
                    adViewGroup)
            adTagLoaderByAdsId[adsId] = adTagLoader
        }
    }

    /**
     * Skips the current ad.
     *
     *
     * This method is intended for apps that play audio-only ads and so need to provide their own
     * UI for users to skip skippable ads. Apps showing video ads should not call this method, as the
     * IMA SDK provides the UI to skip ads in the ad view group passed via [AdViewProvider].
     */
    fun skipAd() {
        if (currentAdTagLoader != null) {
            currentAdTagLoader!!.skipAd()
        }
    }

    /**
     * Moves UI focus to the skip button (or other interactive elements), if currently shown. See
     * [AdsManager.focus].
     */
    fun focusSkipButton() {
        if (currentAdTagLoader != null) {
            currentAdTagLoader!!.focusSkipButton()
        }
    }

    // AdsLoader implementation.
    override fun setPlayer(player: Player?) {
        Assertions.checkState(Looper.myLooper() == ImaUtil.getImaLooper())
        Assertions.checkState(player == null || player.applicationLooper == ImaUtil.getImaLooper())
        nextPlayer = player
        wasSetPlayerCalled = true
    }

    override fun setSupportedContentTypes(vararg contentTypes: Int) {
        val supportedMimeTypes: MutableList<String> = ArrayList()
        for (contentType in contentTypes) {
            // IMA does not support Smooth Streaming ad media.
            if (contentType == C.CONTENT_TYPE_DASH) {
                supportedMimeTypes.add(MimeTypes.APPLICATION_MPD)
            } else if (contentType == C.CONTENT_TYPE_HLS) {
                supportedMimeTypes.add(MimeTypes.APPLICATION_M3U8)
            } else if (contentType == C.CONTENT_TYPE_OTHER) {
                supportedMimeTypes.addAll(
                        Arrays.asList(
                                MimeTypes.VIDEO_MP4,
                                MimeTypes.VIDEO_WEBM,
                                MimeTypes.VIDEO_H263,
                                MimeTypes.AUDIO_MP4,
                                MimeTypes.AUDIO_MPEG))
            }
        }
        this.supportedMimeTypes = Collections.unmodifiableList(supportedMimeTypes)
    }

    override fun start(
            adsMediaSource: AdsMediaSource,
            adTagDataSpec: DataSpec,
            adsId: Any,
            adViewProvider: AdViewProvider,
            eventListener: com.google.android.exoplayer2.source.ads.AdsLoader.EventListener) {
        Assertions.checkState(
                wasSetPlayerCalled, "Set player using adsLoader.setPlayer before preparing the player.")
        if (adTagLoaderByAdsMediaSource.isEmpty()) {
            player = nextPlayer
            val player = player ?: return
            player.addListener(playerListener)
        }
        var adTagLoader = adTagLoaderByAdsId[adsId]
        if (adTagLoader == null) {
            requestAds(adTagDataSpec, adsId, adViewProvider.adViewGroup)
            adTagLoader = adTagLoaderByAdsId[adsId]
        }
        adTagLoaderByAdsMediaSource[adsMediaSource] = Assertions.checkNotNull(adTagLoader)
        adTagLoader!!.addListenerWithAdView(eventListener, adViewProvider)
        maybeUpdateCurrentAdTagLoader()
    }

    override fun stop(adsMediaSource: AdsMediaSource, eventListener: com.google.android.exoplayer2.source.ads.AdsLoader.EventListener) {
        val removedAdTagLoader = adTagLoaderByAdsMediaSource.remove(adsMediaSource)
        maybeUpdateCurrentAdTagLoader()
        removedAdTagLoader?.removeListener(eventListener)
        if (player != null && adTagLoaderByAdsMediaSource.isEmpty()) {
            player!!.removeListener(playerListener)
            player = null
        }
    }

    override fun release() {
        if (player != null) {
            player!!.removeListener(playerListener)
            player = null
            maybeUpdateCurrentAdTagLoader()
        }
        nextPlayer = null
        for (adTagLoader in adTagLoaderByAdsMediaSource.values) {
            adTagLoader.release()
        }
        adTagLoaderByAdsMediaSource.clear()
        for (adTagLoader in adTagLoaderByAdsId.values) {
            adTagLoader.release()
        }
        adTagLoaderByAdsId.clear()
    }

    override fun handlePrepareComplete(
            adsMediaSource: AdsMediaSource, adGroupIndex: Int, adIndexInAdGroup: Int) {
        if (player == null) {
            return
        }
        Assertions.checkNotNull(adTagLoaderByAdsMediaSource[adsMediaSource])
                .handlePrepareComplete(adGroupIndex, adIndexInAdGroup)
    }

    override fun handlePrepareError(
            adsMediaSource: AdsMediaSource,
            adGroupIndex: Int,
            adIndexInAdGroup: Int,
            exception: IOException) {
        if (player == null) {
            return
        }
        Assertions.checkNotNull(adTagLoaderByAdsMediaSource[adsMediaSource])
                .handlePrepareError(adGroupIndex, adIndexInAdGroup, exception)
    }

    // Internal methods.
    private fun maybeUpdateCurrentAdTagLoader() {
        val oldAdTagLoader = currentAdTagLoader
        val newAdTagLoader = getCurrentAdTagLoader()
        if (!Util.areEqual(oldAdTagLoader, newAdTagLoader)) {
            oldAdTagLoader?.deactivate()
            currentAdTagLoader = newAdTagLoader
            newAdTagLoader?.activate(Assertions.checkNotNull(player))
        }
    }

    private fun getCurrentAdTagLoader(): AdTagLoader? {
        val player = player ?: return null
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            return null
        }
        val periodIndex = player.currentPeriodIndex
        val adsId = timeline.getPeriod(periodIndex, period).adsId ?: return null
        val adTagLoader = adTagLoaderByAdsId[adsId]
        return if (adTagLoader == null || !adTagLoaderByAdsMediaSource.containsValue(adTagLoader)) {
            null
        } else adTagLoader
    }

    private fun maybePreloadNextPeriodAds() {
        val player = player ?: return
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            return
        }
        val nextPeriodIndex = timeline.getNextPeriodIndex(
                player.currentPeriodIndex,
                period,
                window,
                player.repeatMode,
                player.shuffleModeEnabled)
        if (nextPeriodIndex == C.INDEX_UNSET) {
            return
        }
        timeline.getPeriod(nextPeriodIndex, period)
        val nextAdsId = period.adsId ?: return
        val nextAdTagLoader = adTagLoaderByAdsId[nextAdsId]
        if (nextAdTagLoader == null || nextAdTagLoader == currentAdTagLoader) {
            return
        }
        val periodPositionUs = timeline.getPeriodPositionUs(
                window, period, period.windowIndex,  /* windowPositionUs= */C.TIME_UNSET).second
        nextAdTagLoader.maybePreloadAds(Util.usToMs(periodPositionUs), Util.usToMs(period.durationUs))
    }

    private inner class PlayerListenerImpl : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: @TimelineChangeReason Int) {
            if (timeline.isEmpty) {
                // The player is being reset or contains no media.
                return
            }
            maybeUpdateCurrentAdTagLoader()
            maybePreloadNextPeriodAds()
        }

        override fun onPositionDiscontinuity(
                oldPosition: PositionInfo,
                newPosition: PositionInfo,
                reason: @DiscontinuityReason Int) {
            maybeUpdateCurrentAdTagLoader()
            maybePreloadNextPeriodAds()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            maybePreloadNextPeriodAds()
        }

        override fun onRepeatModeChanged(repeatMode: @Player.RepeatMode Int) {
            maybePreloadNextPeriodAds()
        }
    }

    /**
     * Default [ImaUtil.ImaFactory] for non-test usage, which delegates to [ ].
     */
    private class DefaultImaFactory : ImaFactory {
        override fun createImaSdkSettings(): ImaSdkSettings {
            val settings = ImaSdkFactory.getInstance().createImaSdkSettings()
            settings.language = Util.getSystemLanguageCodes()[0]
            return settings
        }

        override fun createAdsRenderingSettings(): AdsRenderingSettings? {
            return ImaSdkFactory.getInstance().createAdsRenderingSettings()
        }

        override fun createAdDisplayContainer(container: ViewGroup?, player: VideoAdPlayer?): AdDisplayContainer? {
            return ImaSdkFactory.createAdDisplayContainer(container, player)
        }

        override fun createAudioAdDisplayContainer(context: Context?, player: VideoAdPlayer?): AdDisplayContainer? {
            return ImaSdkFactory.createAudioAdDisplayContainer(context, player)
        }

        // The reasonDetail parameter to createFriendlyObstruction is annotated @Nullable but the
        // annotation is not kept in the obfuscated dependency.
        override fun createFriendlyObstruction(
                view: View?,
                friendlyObstructionPurpose: FriendlyObstructionPurpose?,
                reasonDetail: String?): FriendlyObstruction? {
            return ImaSdkFactory.getInstance()
                    .createFriendlyObstruction(view, friendlyObstructionPurpose, reasonDetail)
        }

        override fun createAdsRequest(): AdsRequest {
            return ImaSdkFactory.getInstance().createAdsRequest()
        }

        override fun createAdsLoader(
                context: Context?, imaSdkSettings: ImaSdkSettings?, adDisplayContainer: AdDisplayContainer?): AdsLoader? {
            return ImaSdkFactory.getInstance()
                    .createAdsLoader(context, imaSdkSettings, adDisplayContainer)
        }
    }

    companion object {
        init {
            ExoPlayerLibraryInfo.registerModule("goog.exo.ima")
        }
    }
}