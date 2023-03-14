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

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Pair
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.google.ads.interactivemedia.v3.api.*
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType
import com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer.VideoStreamPlayerCallback
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider
import com.google.android.exoplayer2.ext.ima.ImaServerSideAdInsertionMediaSource.StreamPlayer.StreamLoadListener
import com.google.android.exoplayer2.ext.ima.ImaUtil.ServerSideAdInsertionConfiguration
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.metadata.emsg.EventMessage
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame
import com.google.android.exoplayer2.source.CompositeMediaSource
import com.google.android.exoplayer2.source.ForwardingTimeline
import com.google.android.exoplayer2.source.MediaPeriod
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.ServerSideAdInsertionMediaSource
import com.google.android.exoplayer2.source.ads.ServerSideAdInsertionMediaSource.AdPlaybackStateUpdater
import com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil
import com.google.android.exoplayer2.ui.AdViewProvider
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction
import com.google.android.exoplayer2.util.*
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.errorprone.annotations.CanIgnoreReturnValue
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** MediaSource for IMA server side inserted ad streams.  */
class ImaServerSideAdInsertionMediaSource private constructor(
        private val mediaItem: MediaItem,
        private val player: Player,
        private val adsLoader: AdsLoader,
        private val sdkAdsLoader: com.google.ads.interactivemedia.v3.api.AdsLoader,
        private val streamPlayer: StreamPlayer,
        private val contentMediaSourceFactory: MediaSource.Factory,
        private val applicationAdEventListener: AdEventListener?,
        private val applicationAdErrorListener: AdErrorListener?) : CompositeMediaSource<Void?>() {
    /**
     * Factory for creating [ ImaServerSideAdInsertionMediaSources][ImaServerSideAdInsertionMediaSource].
     *
     *
     * Apps can use the [ImaServerSideAdInsertionMediaSource.Factory] to customized the
     * [DefaultMediaSourceFactory] that is used to build a player:
     */
    class Factory
    /**
     * Creates a new factory for [ ImaServerSideAdInsertionMediaSources][ImaServerSideAdInsertionMediaSource].
     *
     * @param adsLoader The [AdsLoader].
     * @param contentMediaSourceFactory The content media source factory to create content sources.
     */(private val adsLoader: AdsLoader, private val contentMediaSourceFactory: MediaSource.Factory) : MediaSource.Factory {
        @CanIgnoreReturnValue
        override fun setLoadErrorHandlingPolicy(
                loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
            contentMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            return this
        }

        @CanIgnoreReturnValue
        override fun setDrmSessionManagerProvider(
                drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
            contentMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
            return this
        }

        override fun getSupportedTypes(): IntArray {
            return contentMediaSourceFactory.supportedTypes
        }

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            Assertions.checkNotNull(mediaItem.localConfiguration)
            val player = Assertions.checkNotNull(adsLoader.player)
            val streamPlayer = StreamPlayer(player, mediaItem)
            val imaSdkFactory = ImaSdkFactory.getInstance()
            val streamDisplayContainer = createStreamDisplayContainer(imaSdkFactory, adsLoader.configuration, streamPlayer)
            val imaAdsLoader = imaSdkFactory.createAdsLoader(
                    adsLoader.context, adsLoader.configuration.imaSdkSettings, streamDisplayContainer)
            val mediaSource = ImaServerSideAdInsertionMediaSource(
                    mediaItem,
                    player,
                    adsLoader,
                    imaAdsLoader,
                    streamPlayer,
                    contentMediaSourceFactory,
                    adsLoader.configuration.applicationAdEventListener,
                    adsLoader.configuration.applicationAdErrorListener)
            adsLoader.addMediaSourceResources(mediaSource, streamPlayer, imaAdsLoader)
            return mediaSource
        }
    }

    /** An ads loader for IMA server side ad insertion streams.  */
    class AdsLoader private constructor(
            context: Context, configuration: ServerSideAdInsertionConfiguration, state: State) {
        /** Builder for building an [AdsLoader].  */
        class Builder(private val context: Context, private val adViewProvider: AdViewProvider) {
            private var imaSdkSettings: ImaSdkSettings? = null
            private var adEventListener: AdEventListener? = null
            private var adErrorListener: AdErrorListener? = null
            private var state: State
            private var companionAdSlots: ImmutableList<CompanionAdSlot>

            /**
             * Creates an instance.
             *
             * @param context A context.
             * @param adViewProvider A provider for [ViewGroup] instances.
             */
            init {
                companionAdSlots = ImmutableList.of()
                state = State(ImmutableMap.of())
            }

            /**
             * Sets the IMA SDK settings.
             *
             *
             * If this method is not called the default settings will be used.
             *
             * @param imaSdkSettings The [ImaSdkSettings].
             * @return This builder, for convenience.
             */
            @CanIgnoreReturnValue
            fun setImaSdkSettings(imaSdkSettings: ImaSdkSettings?): Builder {
                this.imaSdkSettings = imaSdkSettings
                return this
            }

            /**
             * Sets the optional [AdEventListener] that will be passed to [ ][AdsManager.addAdEventListener].
             *
             * @param adEventListener The ad event listener.
             * @return This builder, for convenience.
             */
            @CanIgnoreReturnValue
            fun setAdEventListener(adEventListener: AdEventListener?): Builder {
                this.adEventListener = adEventListener
                return this
            }

            /**
             * Sets the optional [AdErrorEvent.AdErrorListener] that will be passed to [ ][AdsManager.addAdErrorListener].
             *
             * @param adErrorListener The [AdErrorEvent.AdErrorListener].
             * @return This builder, for convenience.
             */
            @CanIgnoreReturnValue
            fun setAdErrorListener(adErrorListener: AdErrorListener?): Builder {
                this.adErrorListener = adErrorListener
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
            fun setCompanionAdSlots(companionAdSlots: Collection<CompanionAdSlot>?): Builder {
                this.companionAdSlots = ImmutableList.copyOf(companionAdSlots)
                return this
            }

            /**
             * Sets the optional state to resume with.
             *
             *
             * The state can be received when [releasing][.release] the [AdsLoader].
             *
             * @param state The state to resume with.
             * @return This builder, for convenience.
             */
            @CanIgnoreReturnValue
            fun setAdsLoaderState(state: State): Builder {
                this.state = state
                return this
            }

            /** Returns a new [AdsLoader].  */
            fun build(): AdsLoader {
                var imaSdkSettings = imaSdkSettings
                if (imaSdkSettings == null) {
                    imaSdkSettings = ImaSdkFactory.getInstance().createImaSdkSettings()
                    imaSdkSettings.language = Util.getSystemLanguageCodes()[0]
                }
                val configuration = ServerSideAdInsertionConfiguration(
                        adViewProvider,
                        imaSdkSettings,
                        adEventListener,
                        adErrorListener,
                        companionAdSlots,
                        imaSdkSettings!!.isDebugMode)
                return AdsLoader(context, configuration, state)
            }
        }

        /** The state of the [AdsLoader] that can be used when resuming from the background.  */
        class State @VisibleForTesting internal constructor(val adPlaybackStates: ImmutableMap<String?, AdPlaybackState?>) : Bundleable {
            override fun equals(o: Any?): Boolean {
                if (this === o) {
                    return true
                }
                if (o !is State) {
                    return false
                }
                return adPlaybackStates == o.adPlaybackStates
            }

            override fun hashCode(): Int {
                return adPlaybackStates.hashCode()
            }

            // Bundleable implementation.
            @Documented
            @Retention(RetentionPolicy.SOURCE)
            @Target(TYPE_USE)
            @IntDef([FIELD_AD_PLAYBACK_STATES])
            private annotation class FieldNumber

            override fun toBundle(): Bundle {
                val bundle = Bundle()
                val adPlaybackStatesBundle = Bundle()
                for ((key, value) in adPlaybackStates) {
                    adPlaybackStatesBundle.putBundle(key, value!!.toBundle())
                }
                bundle.putBundle(keyForField(FIELD_AD_PLAYBACK_STATES), adPlaybackStatesBundle)
                return bundle
            }

            companion object {
                private const val FIELD_AD_PLAYBACK_STATES = 1

                /** Object that can restore [AdsLoader.State] from a [Bundle].  */
                @JvmField
                val CREATOR = Bundleable.Creator { bundle: Bundle -> fromBundle(bundle) }
                private fun fromBundle(bundle: Bundle): State {
                    val adPlaybackStateMap = ImmutableMap.Builder<String?, AdPlaybackState?>()
                    val adPlaybackStateBundle = Assertions.checkNotNull(bundle.getBundle(keyForField(FIELD_AD_PLAYBACK_STATES)))
                    for (key in adPlaybackStateBundle.keySet()) {
                        val adPlaybackState = AdPlaybackState.CREATOR.fromBundle(
                                Assertions.checkNotNull(adPlaybackStateBundle.getBundle(key)))
                        adPlaybackStateMap.put(
                                key, AdPlaybackState.fromAdPlaybackState( /* adsId= */key, adPlaybackState))
                    }
                    return State(adPlaybackStateMap.buildOrThrow())
                }

                private fun keyForField(field: @FieldNumber Int): String {
                    return Integer.toString(field, Character.MAX_RADIX)
                }
            }
        }

        val configuration: ServerSideAdInsertionConfiguration
        val context: Context
        private val mediaSourceResources: MutableMap<ImaServerSideAdInsertionMediaSource, MediaSourceResourceHolder>
        private val adPlaybackStateMap: MutableMap<String?, AdPlaybackState?>
        var player: Player? = null

        init {
            this.context = context.applicationContext
            this.configuration = configuration
            mediaSourceResources = HashMap()
            adPlaybackStateMap = HashMap()
            for ((key, value) in state.adPlaybackStates) {
                adPlaybackStateMap[key] = value
            }
        }

        /**
         * Sets the player.
         *
         *
         * This method needs to be called before adding server side ad insertion media items to the
         * player.
         */
        fun setPlayer(player: Player?) {
            this.player = player
        }

        /**
         * Releases resources.
         *
         * @return The [State] that can be used when resuming from the background.
         */
        fun release(): State {
            for (resourceHolder in mediaSourceResources.values) {
                resourceHolder.streamPlayer.release()
                resourceHolder.adsLoader.release()
                resourceHolder.imaServerSideAdInsertionMediaSource.setStreamManager( /* streamManager= */
                        null)
            }
            val state = State(ImmutableMap.copyOf(adPlaybackStateMap))
            adPlaybackStateMap.clear()
            mediaSourceResources.clear()
            player = null
            return state
        }

        // Internal methods.
        private fun addMediaSourceResources(
                mediaSource: ImaServerSideAdInsertionMediaSource,
                streamPlayer: StreamPlayer,
                adsLoader: com.google.ads.interactivemedia.v3.api.AdsLoader) {
            mediaSourceResources[mediaSource] = MediaSourceResourceHolder(mediaSource, streamPlayer, adsLoader)
        }

        fun getAdPlaybackState(adsId: String): AdPlaybackState {
            val adPlaybackState = adPlaybackStateMap[adsId]
            return adPlaybackState ?: AdPlaybackState.NONE
        }

        fun setAdPlaybackState(adsId: String, adPlaybackState: AdPlaybackState?) {
            adPlaybackStateMap[adsId] = adPlaybackState
        }

        private class MediaSourceResourceHolder(
                val imaServerSideAdInsertionMediaSource: ImaServerSideAdInsertionMediaSource,
                val streamPlayer: StreamPlayer,
                val adsLoader: com.google.ads.interactivemedia.v3.api.AdsLoader)
    }

    private val isLiveStream: Boolean
    private val adsId: String
    private val streamRequest: StreamRequest
    private val loadVideoTimeoutMs: Int
    private val mainHandler: Handler
    private val componentListener: ComponentListener
    private var loader: Loader? = null
    private var streamManager: StreamManager? = null
    private var serverSideAdInsertionMediaSource: ServerSideAdInsertionMediaSource? = null
    private var loadError: IOException? = null
    private var contentTimeline: @MonotonicNonNull Timeline? = null
    private var adPlaybackState: AdPlaybackState?

    init {
        componentListener = ComponentListener()
        mainHandler = Util.createHandlerForCurrentLooper()
        val streamRequestUri = Assertions.checkNotNull(mediaItem.localConfiguration).uri
        isLiveStream = ImaServerSideAdInsertionUriBuilder.Companion.isLiveStream(streamRequestUri)
        adsId = ImaServerSideAdInsertionUriBuilder.Companion.getAdsId(streamRequestUri)
        loadVideoTimeoutMs = ImaServerSideAdInsertionUriBuilder.Companion.getLoadVideoTimeoutMs(streamRequestUri)
        streamRequest = ImaServerSideAdInsertionUriBuilder.Companion.createStreamRequest(streamRequestUri)
        adPlaybackState = adsLoader.getAdPlaybackState(adsId)
    }

    override fun getMediaItem(): MediaItem {
        return mediaItem
    }

    public override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        mainHandler.post { assertSingleInstanceInPlaylist(Assertions.checkNotNull(player)) }
        super.prepareSourceInternal(mediaTransferListener)
        if (loader == null) {
            val loader = Loader("ImaServerSideAdInsertionMediaSource")
            player.addListener(componentListener)
            val streamManagerLoadable = StreamManagerLoadable(
                    sdkAdsLoader,
                    streamRequest,
                    streamPlayer,
                    applicationAdErrorListener,
                    loadVideoTimeoutMs)
            loader.startLoading(
                    streamManagerLoadable,
                    StreamManagerLoadableCallback(),  /* defaultMinRetryCount= */
                    0)
            this.loader = loader
        }
    }

    protected override fun onChildSourceInfoRefreshed(
            childSourceId: Void, mediaSource: MediaSource, newTimeline: Timeline) {
        refreshSourceInfo(
                object : ForwardingTimeline(newTimeline) {
                    override fun getWindow(
                            windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                        newTimeline.getWindow(windowIndex, window, defaultPositionProjectionUs)
                        window.mediaItem = mediaItem
                        return window
                    }
                })
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod {
        return Assertions.checkNotNull(serverSideAdInsertionMediaSource)
                .createPeriod(id, allocator, startPositionUs)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        Assertions.checkNotNull(serverSideAdInsertionMediaSource).releasePeriod(mediaPeriod)
    }

    @Throws(IOException::class)
    override fun maybeThrowSourceInfoRefreshError() {
        super.maybeThrowSourceInfoRefreshError()
        if (loadError != null) {
            val loadError = loadError
            this.loadError = null
            throw loadError!!
        }
    }

    override fun releaseSourceInternal() {
        super.releaseSourceInternal()
        if (loader != null) {
            loader!!.release()
            player.removeListener(componentListener)
            mainHandler.post { setStreamManager( /* streamManager= */null) }
            loader = null
        }
    }

    // Internal methods (called on the main thread).
    @MainThread
    private fun setStreamManager(streamManager: StreamManager?) {
        if (this.streamManager === streamManager) {
            return
        }
        if (this.streamManager != null) {
            if (applicationAdEventListener != null) {
                this.streamManager!!.removeAdEventListener(applicationAdEventListener)
            }
            if (applicationAdErrorListener != null) {
                this.streamManager!!.removeAdErrorListener(applicationAdErrorListener)
            }
            this.streamManager!!.removeAdEventListener(componentListener)
            this.streamManager!!.destroy()
            this.streamManager = null
        }
        this.streamManager = streamManager
        if (streamManager != null) {
            streamManager.addAdEventListener(componentListener)
            if (applicationAdEventListener != null) {
                streamManager.addAdEventListener(applicationAdEventListener)
            }
            if (applicationAdErrorListener != null) {
                streamManager.addAdErrorListener(applicationAdErrorListener)
            }
        }
    }

    @MainThread
    private fun setAdPlaybackState(adPlaybackState: AdPlaybackState?) {
        if (adPlaybackState == this.adPlaybackState) {
            return
        }
        this.adPlaybackState = adPlaybackState
        invalidateServerSideAdInsertionAdPlaybackState()
    }

    @MainThread
    @EnsuresNonNull("this.contentTimeline")
    private fun setContentTimeline(contentTimeline: Timeline) {
        if (contentTimeline == this.contentTimeline) {
            return
        }
        this.contentTimeline = contentTimeline
        invalidateServerSideAdInsertionAdPlaybackState()
    }

    @MainThread
    private fun invalidateServerSideAdInsertionAdPlaybackState() {
        if (adPlaybackState != AdPlaybackState.NONE && contentTimeline != null) {
            val splitAdPlaybackStates: ImmutableMap<Any?, AdPlaybackState?> = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, contentTimeline!!)
            streamPlayer.setAdPlaybackStates(adsId, splitAdPlaybackStates, contentTimeline)
            Assertions.checkNotNull(serverSideAdInsertionMediaSource).setAdPlaybackStates(splitAdPlaybackStates)
            if (!ImaServerSideAdInsertionUriBuilder.Companion.isLiveStream(
                            Assertions.checkNotNull(mediaItem.localConfiguration).uri)) {
                adsLoader.setAdPlaybackState(adsId, adPlaybackState)
            }
        }
    }

    // Internal methods (called on the playback thread).
    private fun setContentUri(contentUri: Uri) {
        if (serverSideAdInsertionMediaSource != null) {
            return
        }
        val contentMediaItem = MediaItem.Builder()
                .setUri(contentUri)
                .setDrmConfiguration(Assertions.checkNotNull(mediaItem.localConfiguration).drmConfiguration)
                .setLiveConfiguration(mediaItem.liveConfiguration)
                .setCustomCacheKey(mediaItem.localConfiguration!!.customCacheKey)
                .setStreamKeys(mediaItem.localConfiguration!!.streamKeys)
                .build()
        val serverSideAdInsertionMediaSource = ServerSideAdInsertionMediaSource(
                contentMediaSourceFactory.createMediaSource(contentMediaItem), componentListener)
        this.serverSideAdInsertionMediaSource = serverSideAdInsertionMediaSource
        if (isLiveStream) {
            val liveAdPlaybackState = AdPlaybackState(adsId)
                    .withNewAdGroup( /* adGroupIndex= */0,  /* adGroupTimeUs= */C.TIME_END_OF_SOURCE)
                    .withIsServerSideInserted( /* adGroupIndex= */0, true)
            mainHandler.post { setAdPlaybackState(liveAdPlaybackState) }
        }
        prepareChildSource( /* id= */null, serverSideAdInsertionMediaSource)
    }

    private inner class ComponentListener : AdEventListener, Player.Listener, AdPlaybackStateUpdater {
        // Implement Player.Listener.
        override fun onPositionDiscontinuity(
                oldPosition: PositionInfo,
                newPosition: PositionInfo,
                reason: @DiscontinuityReason Int) {
            if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                // Only auto transitions within the same or to the next media item are of interest.
                return
            }
            if (mediaItem == oldPosition.mediaItem && mediaItem != newPosition.mediaItem) {
                // Playback automatically transitioned to the next media item. Notify the SDK.
                streamPlayer.onContentCompleted()
            }
            if (mediaItem != oldPosition.mediaItem
                    || mediaItem != newPosition.mediaItem
                    || adsId != player
                            .currentTimeline
                            .getPeriodByUid(Assertions.checkNotNull(newPosition.periodUid), Timeline.Period())
                            .adsId) {
                // Discontinuity not within this ad media source.
                return
            }
            if (oldPosition.adGroupIndex != C.INDEX_UNSET) {
                var adGroupIndex = oldPosition.adGroupIndex
                var adIndexInAdGroup = oldPosition.adIndexInAdGroup
                val timeline = player.currentTimeline
                val window = timeline.getWindow(oldPosition.mediaItemIndex, Timeline.Window())
                if (window.lastPeriodIndex > window.firstPeriodIndex) {
                    // Map adGroupIndex and adIndexInAdGroup to multi-period window.
                    val adGroupIndexAndAdIndexInAdGroup: Pair<Int?, Int?> = ImaUtil.getAdGroupAndIndexInMultiPeriodWindow(
                            oldPosition.periodIndex - window.firstPeriodIndex,
                            adPlaybackState,
                            Assertions.checkNotNull(contentTimeline))
                    adGroupIndex = adGroupIndexAndAdIndexInAdGroup.first!!
                    adIndexInAdGroup = adGroupIndexAndAdIndexInAdGroup.second!!
                }
                var adGroup = adPlaybackState!!.getAdGroup(adGroupIndex)
                val adState = adGroup.states[adIndexInAdGroup]
                if (adState == AdPlaybackState.AD_STATE_AVAILABLE || adState == AdPlaybackState.AD_STATE_UNAVAILABLE) {
                    var newAdPlaybackState = adPlaybackState!!.withPlayedAd(adGroupIndex,  /* adIndexInAdGroup= */adIndexInAdGroup)
                    adGroup = newAdPlaybackState.getAdGroup(adGroupIndex)
                    if (isLiveStream && newPosition.adGroupIndex == C.INDEX_UNSET && adIndexInAdGroup < adGroup.states.size - 1 && adGroup.states[adIndexInAdGroup + 1] == AdPlaybackState.AD_STATE_AVAILABLE) {
                        // There is an available ad after the ad period that just ended being played!
                        Log.w(TAG, "Detected late ad event. Regrouping trailing ads into separate ad group.")
                        newAdPlaybackState = ImaUtil.splitAdGroup(
                                adGroup,
                                adGroupIndex,  /* splitIndexExclusive= */
                                adIndexInAdGroup + 1,
                                newAdPlaybackState)
                    }
                    setAdPlaybackState(newAdPlaybackState)
                }
            }
        }

        override fun onMetadata(metadata: Metadata) {
            if (!isCurrentAdPlaying(player, mediaItem, adsId)) {
                return
            }
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                if (entry is TextInformationFrame) {
                    val textFrame = entry
                    if ("TXXX" == textFrame.id) {
                        streamPlayer.triggerUserTextReceived(textFrame.value)
                    }
                } else if (entry is EventMessage) {
                    val eventMessageValue = String(entry.messageData)
                    streamPlayer.triggerUserTextReceived(eventMessageValue)
                }
            }
        }

        override fun onPlaybackStateChanged(state: @Player.State Int) {
            if (state == Player.STATE_ENDED && isCurrentAdPlaying(player, mediaItem, adsId)) {
                streamPlayer.onContentCompleted()
            }
        }

        override fun onVolumeChanged(volume: Float) {
            if (!isCurrentAdPlaying(player, mediaItem, adsId)) {
                return
            }
            val volumePct = Math.floor((volume * 100).toDouble()).toInt()
            streamPlayer.onContentVolumeChanged(volumePct)
        }

        // Implement AdEvent.AdEventListener.
        @MainThread
        override fun onAdEvent(event: AdEvent) {
            var newAdPlaybackState = adPlaybackState
            when (event.type) {
                AdEventType.CUEPOINTS_CHANGED ->           // CUEPOINTS_CHANGED event is firing multiple times with the same queue points.
                    if (!isLiveStream && newAdPlaybackState == AdPlaybackState.NONE) {
                        newAdPlaybackState = setVodAdGroupPlaceholders(
                                Assertions.checkNotNull(streamManager).cuePoints, AdPlaybackState(adsId))
                    }
                AdEventType.LOADED -> newAdPlaybackState = if (isLiveStream) {
                    val timeline = player.currentTimeline
                    val window = timeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
                    if (window.lastPeriodIndex > window.firstPeriodIndex) {
                        // multi-period live not integrated
                        return
                    }
                    val positionInWindowUs = timeline.getPeriod(player.currentPeriodIndex, Timeline.Period()).positionInWindowUs
                    val currentContentPeriodPositionUs = Util.msToUs(player.contentPosition) - positionInWindowUs
                    val ad = event.ad
                    val adPodInfo = ad.adPodInfo
                    ImaUtil.addLiveAdBreak(
                            currentContentPeriodPositionUs,  /* adDurationUs= */
                            ImaUtil.secToUsRounded(ad.duration),  /* adPositionInAdPod= */
                            adPodInfo.adPosition,  /* totalAdDurationUs= */
                            ImaUtil.secToUsRounded(adPodInfo.maxDuration),  /* totalAdsInAdPod= */
                            adPodInfo.totalAds,  /* adPlaybackState= */
                            if (newAdPlaybackState == AdPlaybackState.NONE) AdPlaybackState(adsId) else newAdPlaybackState)
                } else {
                    setVodAdInPlaceholder(event.ad, newAdPlaybackState)
                }
                AdEventType.SKIPPED -> if (!isLiveStream) {
                    newAdPlaybackState = skipAd(event.ad, newAdPlaybackState)
                }
                else -> {}
            }
            setAdPlaybackState(newAdPlaybackState)
        }

        // Implement AdPlaybackStateUpdater (called on the playback thread).
        override fun onAdPlaybackStateUpdateRequested(contentTimeline: Timeline): Boolean {
            mainHandler.post { setContentTimeline(contentTimeline) }
            // Defer source refresh to ad playback state update for VOD. Refresh immediately when live
            // with single period.
            return !isLiveStream || contentTimeline.periodCount > 1
        }
    }

    private inner class StreamManagerLoadableCallback : Loader.Callback<StreamManagerLoadable?> {
        override fun onLoadCompleted(
                loadable: StreamManagerLoadable, elapsedRealtimeMs: Long, loadDurationMs: Long) {
            mainHandler.post { setStreamManager(Assertions.checkNotNull(loadable.getStreamManager())) }
            setContentUri(Assertions.checkNotNull(loadable.getContentUri()))
        }

        override fun onLoadCanceled(
                loadable: StreamManagerLoadable,
                elapsedRealtimeMs: Long,
                loadDurationMs: Long,
                released: Boolean) {
            // We only cancel when the loader is released.
            Assertions.checkState(released)
        }

        override fun onLoadError(
                loadable: StreamManagerLoadable,
                elapsedRealtimeMs: Long,
                loadDurationMs: Long,
                error: IOException,
                errorCount: Int): LoadErrorAction {
            loadError = error
            return Loader.DONT_RETRY
        }
    }

    /** Loads the [StreamManager] and the content URI.  */
    private class StreamManagerLoadable(
            private val adsLoader: com.google.ads.interactivemedia.v3.api.AdsLoader,
            private val request: StreamRequest,
            private val streamPlayer: StreamPlayer,
            private val adErrorListener: AdErrorListener?,
            private val loadVideoTimeoutMs: Int) : Loader.Loadable, AdsLoadedListener, AdErrorListener {
        private val conditionVariable: ConditionVariable

        /** Returns the stream manager or null if not yet loaded.  */
        @Volatile
        var streamManager: StreamManager? = null
            private set

        /** Returns the DAI content URI or null if not yet available.  */
        @Volatile
        var contentUri: Uri? = null
            private set

        @Volatile
        private var cancelled = false

        @Volatile
        private var error = false

        @Volatile
        private var errorMessage: String? = null

        @Volatile
        private var errorCode: Int

        /** Creates an instance.  */
        init {
            conditionVariable = ConditionVariable()
            errorCode = -1
        }

        // Implement Loadable.
        @Throws(IOException::class)
        override fun load() {
            try {
                // SDK will call loadUrl on stream player for SDK once manifest uri is available.
                streamPlayer.setStreamLoadListener(
                        StreamLoadListener { streamUri: String?, subtitles: List<HashMap<String?, String?>?>? ->
                            contentUri = Uri.parse(streamUri)
                            conditionVariable.open()
                        })
                if (adErrorListener != null) {
                    adsLoader.addAdErrorListener(adErrorListener)
                }
                adsLoader.addAdsLoadedListener(this)
                adsLoader.addAdErrorListener(this)
                adsLoader.requestStream(request)
                while (contentUri == null && !cancelled && !error) {
                    try {
                        conditionVariable.block()
                    } catch (e: InterruptedException) {
                        /* Do nothing. */
                    }
                }
                if (error && contentUri == null) {
                    throw IOException("$errorMessage [errorCode: $errorCode]")
                }
            } finally {
                adsLoader.removeAdsLoadedListener(this)
                adsLoader.removeAdErrorListener(this)
                if (adErrorListener != null) {
                    adsLoader.removeAdErrorListener(adErrorListener)
                }
            }
        }

        override fun cancelLoad() {
            cancelled = true
        }

        // AdsLoader.AdsLoadedListener implementation.
        @MainThread
        override fun onAdsManagerLoaded(event: AdsManagerLoadedEvent) {
            val streamManager = event.streamManager
            if (streamManager == null) {
                error = true
                errorMessage = "streamManager is null after ads manager has been loaded"
                conditionVariable.open()
                return
            }
            val adsRenderingSettings = ImaSdkFactory.getInstance().createAdsRenderingSettings()
            adsRenderingSettings.setLoadVideoTimeout(loadVideoTimeoutMs)
            // After initialization completed the streamUri will be reported to the streamPlayer.
            streamManager.init(adsRenderingSettings)
            this.streamManager = streamManager
        }

        // AdErrorEvent.AdErrorListener implementation.
        @MainThread
        override fun onAdError(adErrorEvent: AdErrorEvent) {
            error = true
            if (adErrorEvent.error != null) {
                val errorMessage = adErrorEvent.error.message
                if (errorMessage != null) {
                    this.errorMessage = errorMessage.replace('\n', ' ')
                }
                errorCode = adErrorEvent.error.errorCodeNumber
            }
            conditionVariable.open()
        }
    }

    /**
     * Receives the content URI from the SDK and sends back in-band media metadata and playback
     * progression data to the SDK.
     */
    private class StreamPlayer(private val player: Player, private val mediaItem: MediaItem) : VideoStreamPlayer {
        /** A listener to listen for the stream URI loaded by the SDK.  */
        interface StreamLoadListener {
            /**
             * Loads a stream with dynamic ad insertion given the stream url and subtitles array. The
             * subtitles array is only used in VOD streams.
             *
             *
             * Each entry in the subtitles array is a HashMap that corresponds to a language. Each map
             * will have a "language" key with a two letter language string value, a "language name" to
             * specify the set of subtitles if multiple sets exist for the same language, and one or more
             * subtitle key/value pairs. Here's an example the map for English:
             *
             *
             * "language" -> "en" "language_name" -> "English" "webvtt" ->
             * "https://example.com/vtt/en.vtt" "ttml" -> "https://example.com/ttml/en.ttml"
             */
            fun onLoadStream(streamUri: String?, subtitles: List<HashMap<String, String>>?)
        }

        private val callbacks: MutableList<VideoStreamPlayerCallback>
        private val window: Timeline.Window
        private val period: Timeline.Period
        private var adPlaybackStates: ImmutableMap<Any?, AdPlaybackState?>?
        private var contentTimeline: Timeline? = null
        private var adsId: Any? = null
        private var streamLoadListener: StreamLoadListener? = null

        /** Creates an instance.  */
        init {
            callbacks = ArrayList( /* initialCapacity= */1)
            adPlaybackStates = ImmutableMap.of()
            window = Timeline.Window()
            period = Timeline.Period()
        }

        /** Registers the ad playback states matching to the given content timeline.  */
        fun setAdPlaybackStates(
                adsId: Any?,
                adPlaybackStates: ImmutableMap<Any?, AdPlaybackState?>?,
                contentTimeline: Timeline?) {
            this.adsId = adsId
            this.adPlaybackStates = adPlaybackStates
            this.contentTimeline = contentTimeline
        }

        /** Sets the [StreamLoadListener] to be called when the SSAI content URI was loaded.  */
        fun setStreamLoadListener(listener: StreamLoadListener?) {
            streamLoadListener = Assertions.checkNotNull(listener)
        }

        /** Called when the content has completed playback.  */
        fun onContentCompleted() {
            for (callback in callbacks) {
                callback.onContentComplete()
            }
        }

        /** Called when the content player changed the volume.  */
        fun onContentVolumeChanged(volumePct: Int) {
            for (callback in callbacks) {
                callback.onVolumeChanged(volumePct)
            }
        }

        /** Releases the player.  */
        fun release() {
            callbacks.clear()
            adsId = null
            adPlaybackStates = ImmutableMap.of()
            contentTimeline = null
            streamLoadListener = null
        }

        // Implements VolumeProvider.
        override fun getVolume(): Int {
            return Math.floor((player.volume * 100).toDouble()).toInt()
        }

        // Implement ContentProgressProvider.
        override fun getContentProgress(): VideoProgressUpdate {
            if (!isCurrentAdPlaying(player, mediaItem, adsId)) {
                return VideoProgressUpdate.VIDEO_TIME_NOT_READY
            } else if (adPlaybackStates!!.isEmpty()) {
                return VideoProgressUpdate( /* currentTimeMs= */0,  /* durationMs= */C.TIME_UNSET)
            }
            val timeline = player.currentTimeline
            val currentPeriodIndex = player.currentPeriodIndex
            timeline.getPeriod(currentPeriodIndex, period,  /* setIds= */true)
            timeline.getWindow(player.currentMediaItemIndex, window)

            // We need the period of the content timeline because its period UIDs are the key used in the
            // ad playback state map. The period UIDs of the public timeline are different (masking).
            val contentPeriod = Assertions.checkNotNull(contentTimeline)
                    .getPeriod(
                            currentPeriodIndex - window.firstPeriodIndex,
                            Timeline.Period(),  /* setIds= */
                            true)
            val adPlaybackState = Assertions.checkNotNull(adPlaybackStates!![contentPeriod.uid])
            var streamPositionMs = Util.usToMs(ServerSideAdInsertionUtil.getStreamPositionUs(player, adPlaybackState))
            if (window.windowStartTimeMs != C.TIME_UNSET) {
                // Add the time since epoch at start of the window for live streams.
                streamPositionMs += window.windowStartTimeMs + period.positionInWindowMs
            } else if (currentPeriodIndex > window.firstPeriodIndex) {
                // Add the end position of the previous period in the underlying stream.
                Assertions.checkNotNull(contentTimeline)
                        .getPeriod(
                                currentPeriodIndex - window.firstPeriodIndex - 1,
                                contentPeriod,  /* setIds= */
                                true)
                streamPositionMs += Util.usToMs(contentPeriod.positionInWindowUs + contentPeriod.durationUs)
            }
            return VideoProgressUpdate(
                    streamPositionMs,
                    Assertions.checkNotNull(contentTimeline).getWindow( /* windowIndex= */0, window).durationMs)
        }

        // Implement VideoStreamPlayer.
        override fun loadUrl(url: String, subtitles: List<HashMap<String, String>>) {
            if (streamLoadListener != null) {
                // SDK provided manifest url, notify the listener.
                streamLoadListener!!.onLoadStream(url, subtitles)
            }
        }

        override fun addCallback(callback: VideoStreamPlayerCallback) {
            callbacks.add(callback)
        }

        override fun removeCallback(callback: VideoStreamPlayerCallback) {
            callbacks.remove(callback)
        }

        override fun onAdBreakStarted() {
            // Do nothing.
        }

        override fun onAdBreakEnded() {
            // Do nothing.
        }

        override fun onAdPeriodStarted() {
            // Do nothing.
        }

        override fun onAdPeriodEnded() {
            // Do nothing.
        }

        override fun pause() {
            // Do nothing.
        }

        override fun resume() {
            // Do nothing.
        }

        override fun seek(timeMs: Long) {
            // Do nothing.
        }

        // Internal methods.
        fun triggerUserTextReceived(userText: String) {
            for (callback in callbacks) {
                callback.onUserTextReceived(userText)
            }
        }
    }

    companion object {
        private const val TAG = "ImaSSAIMediaSource"

        // Static methods.
        private fun setVodAdGroupPlaceholders(
                cuePoints: List<CuePoint>, adPlaybackState: AdPlaybackState): AdPlaybackState {
            // TODO(b/192231683) Use getEndTimeMs()/getStartTimeMs() after jar target was removed
            var adPlaybackState = adPlaybackState
            for (i in cuePoints.indices) {
                val cuePoint = cuePoints[i]
                val fromPositionUs = Util.msToUs(ImaUtil.secToMsRounded(cuePoint.startTime))
                adPlaybackState = ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
                        adPlaybackState,  /* fromPositionUs= */
                        fromPositionUs,  /* contentResumeOffsetUs= */
                        0,  /* adDurationsUs...= */
                        getAdDuration( /* startTimeSeconds= */
                                cuePoint.startTime,  /* endTimeSeconds= */
                                cuePoint.endTime))
            }
            return adPlaybackState
        }

        private fun getAdDuration(startTimeSeconds: Double, endTimeSeconds: Double): Long {
            // startTimeSeconds and endTimeSeconds that are coming from the SDK, only have a precision of
            // milliseconds so everything that is below a millisecond can be safely considered as coming
            // from rounding issues.
            return Util.msToUs(ImaUtil.secToMsRounded(endTimeSeconds - startTimeSeconds))
        }

        private fun setVodAdInPlaceholder(ad: Ad, adPlaybackState: AdPlaybackState?): AdPlaybackState? {
            var adPlaybackState = adPlaybackState
            val adPodInfo = ad.adPodInfo
            // Handle post rolls that have a podIndex of -1.
            val adGroupIndex = if (adPodInfo.podIndex == -1) adPlaybackState!!.adGroupCount - 1 else adPodInfo.podIndex
            val adGroup = adPlaybackState!!.getAdGroup(adGroupIndex)
            val adIndexInAdGroup = adPodInfo.adPosition - 1
            if (adGroup.count < adPodInfo.totalAds) {
                adPlaybackState = ImaUtil.expandAdGroupPlaceholder(
                        adGroupIndex,  /* adGroupDurationUs= */
                        Util.msToUs(ImaUtil.secToMsRounded(adPodInfo.maxDuration)),
                        adIndexInAdGroup,  /* adDurationUs= */
                        Util.msToUs(ImaUtil.secToMsRounded(ad.duration)),  /* adsInAdGroupCount= */
                        adPodInfo.totalAds,
                        adPlaybackState)
            } else if (adIndexInAdGroup < adGroup.count - 1) {
                adPlaybackState = ImaUtil.updateAdDurationInAdGroup(
                        adGroupIndex,
                        adIndexInAdGroup,  /* adDurationUs= */
                        Util.msToUs(ImaUtil.secToMsRounded(ad.duration)),
                        adPlaybackState)
            }
            return adPlaybackState
        }

        private fun skipAd(ad: Ad, adPlaybackState: AdPlaybackState?): AdPlaybackState {
            val adPodInfo = ad.adPodInfo
            val adGroupIndex = adPodInfo.podIndex
            // IMA SDK always returns index starting at 1.
            val adIndexInAdGroup = adPodInfo.adPosition - 1
            return adPlaybackState!!.withSkippedAd(adGroupIndex, adIndexInAdGroup)
        }

        private fun isCurrentAdPlaying(
                player: Player, mediaItem: MediaItem, adsId: Any?): Boolean {
            if (player.playbackState == Player.STATE_IDLE) {
                return false
            }
            val period = Timeline.Period()
            player.currentTimeline.getPeriod(player.currentPeriodIndex, period)
            return period.isPlaceholder && mediaItem == player.currentMediaItem || adsId != null && adsId == period.adsId
        }

        private fun createStreamDisplayContainer(
                imaSdkFactory: ImaSdkFactory,
                config: ServerSideAdInsertionConfiguration,
                streamPlayer: StreamPlayer): StreamDisplayContainer {
            val container = ImaSdkFactory.createStreamDisplayContainer(
                    Assertions.checkNotNull(config.adViewProvider.adViewGroup), streamPlayer)
            container.companionSlots = config.companionAdSlots
            registerFriendlyObstructions(imaSdkFactory, container, config.adViewProvider)
            return container
        }

        private fun registerFriendlyObstructions(
                imaSdkFactory: ImaSdkFactory,
                container: StreamDisplayContainer,
                adViewProvider: AdViewProvider?) {
            for (i in adViewProvider!!.adOverlayInfos.indices) {
                val overlayInfo = adViewProvider.adOverlayInfos[i]
                container.registerFriendlyObstruction(
                        imaSdkFactory.createFriendlyObstruction(
                                overlayInfo.view,
                                ImaUtil.getFriendlyObstructionPurpose(overlayInfo.purpose),
                                if (overlayInfo.reasonDetail != null) overlayInfo.reasonDetail else "Unknown reason"))
            }
        }

        private fun assertSingleInstanceInPlaylist(player: Player) {
            var counter = 0
            for (i in 0 until player.mediaItemCount) {
                val mediaItem = player.getMediaItemAt(i)
                if (mediaItem.localConfiguration != null && C.SSAI_SCHEME == mediaItem.localConfiguration!!.uri.scheme && ImaServerSideAdInsertionUriBuilder.Companion.IMA_AUTHORITY == mediaItem.localConfiguration!!.uri.authority) {
                    check(++counter <= 1) { "Multiple IMA server side ad insertion sources not supported." }
                }
            }
        }
    }
}