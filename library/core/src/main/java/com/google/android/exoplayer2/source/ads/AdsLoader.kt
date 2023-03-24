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
package com.google.android.exoplayer2.source.ads

import com.google.android.exoplayer2.C.ContentType
import com.google.android.exoplayer2.MediaItem.AdsConfiguration
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ads.AdsLoader.EventListener
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException
import com.google.android.exoplayer2.ui.AdViewProvider
import com.google.android.exoplayer2.upstream.DataSpec
import java.io.IOException

/**
 * Interface for loaders of ads, which can be used with [AdsMediaSource].
 *
 *
 * Ads loaders notify the [AdsMediaSource] about events via [EventListener]. In
 * particular, implementations must call [EventListener.onAdPlaybackState]
 * with a new copy of the current [AdPlaybackState] whenever further information about ads
 * becomes known (for example, when an ad media URI is available, or an ad has played to the end).
 *
 *
 * [.start] will be called
 * when an ads media source first initializes, at which point the loader can request ads. If the
 * player enters the background, [.stop] will be called.
 * Loaders should maintain any ad playback state in preparation for a later call to [ ][.start]. If an ad is playing
 * when the player is detached, update the ad playback state with the current playback position
 * using [AdPlaybackState.withAdResumePositionUs].
 *
 *
 * If [EventListener.onAdPlaybackState] has been called, the
 * implementation of [.start]
 * should invoke the same listener to provide the existing playback state to the new player.
 */
interface AdsLoader {
    /**
     * Provides [AdsLoader] instances for media items that have [ ][MediaItem.LocalConfiguration.adsConfiguration].
     */
    interface Provider {
        /**
         * Returns an [AdsLoader] for the given [ ][MediaItem.LocalConfiguration.adsConfiguration], or `null` if no ads
         * loader is available for the given ads configuration.
         *
         *
         * This method is called each time a [MediaSource] is created from a [MediaItem]
         * that defines an [ads configuration][MediaItem.LocalConfiguration.adsConfiguration].
         */
        fun getAdsLoader(adsConfiguration: AdsConfiguration?): AdsLoader?
    }

    /** Listener for ads loader events. All methods are called on the main thread.  */
    interface EventListener {
        /**
         * Called when the ad playback state has been updated. The number of [ ][AdPlaybackState.adGroupCount] may not change after the first call.
         *
         * @param adPlaybackState The new ad playback state.
         */
        fun onAdPlaybackState(adPlaybackState: AdPlaybackState?) {}

        /**
         * Called when there was an error loading ads.
         *
         * @param error The error.
         * @param dataSpec The data spec associated with the load error.
         */
        fun onAdLoadError(error: AdLoadException?, dataSpec: DataSpec?) {}

        /** Called when the user clicks through an ad (for example, following a 'learn more' link).  */
        fun onAdClicked() {}

        /** Called when the user taps a non-clickthrough part of an ad.  */
        fun onAdTapped() {}
    }
    // Methods called by the application.
    /**
     * Sets the player that will play the loaded ads.
     *
     *
     * This method must be called before the player is prepared with media using this ads loader.
     *
     *
     * This method must also be called on the main thread and only players which are accessed on
     * the main thread are supported (`player.getApplicationLooper() ==
     * Looper.getMainLooper()`).
     *
     * @param player The player instance that will play the loaded ads. May be null to delete the
     * reference to a previously set player.
     */
    fun setPlayer(player: Player?)

    /**
     * Releases the loader. Must be called by the application on the main thread when the instance is
     * no longer needed.
     */
    fun release()
    // Methods called by AdsMediaSource.
    /**
     * Sets the supported content types for ad media. Must be called before the first call to [ ][.start]. Subsequent calls may
     * be ignored. Called on the main thread by [AdsMediaSource].
     *
     * @param contentTypes The supported content types for ad media. Each element must be one of
     * [C.CONTENT_TYPE_DASH], [C.CONTENT_TYPE_HLS], [C.CONTENT_TYPE_SS] and
     * [C.CONTENT_TYPE_OTHER].
     */
    fun setSupportedContentTypes(@ContentType vararg contentTypes: Int)

    /**
     * Starts using the ads loader for playback. Called on the main thread by [AdsMediaSource].
     *
     * @param adsMediaSource The ads media source requesting to start loading ads.
     * @param adTagDataSpec A data spec for the ad tag to load.
     * @param adsId An opaque identifier for the ad playback state across start/stop calls.
     * @param adViewProvider Provider of views for the ad UI.
     * @param eventListener Listener for ads loader events.
     */
    fun start(
        adsMediaSource: AdsMediaSource?,
        adTagDataSpec: DataSpec?,
        adsId: Any?,
        adViewProvider: AdViewProvider?,
        eventListener: EventListener?
    )

    /**
     * Stops using the ads loader for playback and deregisters the event listener. Called on the main
     * thread by [AdsMediaSource].
     *
     * @param adsMediaSource The ads media source requesting to stop loading/playing ads.
     * @param eventListener The ads media source's listener for ads loader events.
     */
    fun stop(adsMediaSource: AdsMediaSource?, eventListener: EventListener?)

    /**
     * Notifies the ads loader that preparation of an ad media period is complete. Called on the main
     * thread by [AdsMediaSource].
     *
     * @param adsMediaSource The ads media source for which preparation of ad media completed.
     * @param adGroupIndex The index of the ad group.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     */
    fun handlePrepareComplete(
        adsMediaSource: AdsMediaSource?,
        adGroupIndex: Int,
        adIndexInAdGroup: Int
    )

    /**
     * Notifies the ads loader that the player was not able to prepare media for a given ad.
     * Implementations should update the ad playback state as the specified ad has failed to load.
     * Called on the main thread by [AdsMediaSource].
     *
     * @param adsMediaSource The ads media source for which preparation of ad media failed.
     * @param adGroupIndex The index of the ad group.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     * @param exception The preparation error.
     */
    fun handlePrepareError(
        adsMediaSource: AdsMediaSource?,
        adGroupIndex: Int,
        adIndexInAdGroup: Int,
        exception: IOException?
    )
}