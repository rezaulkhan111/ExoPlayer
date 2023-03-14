/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CheckResult
import com.google.ads.interactivemedia.v3.api.*
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdGroup
import com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil
import com.google.android.exoplayer2.ui.AdOverlayInfo
import com.google.android.exoplayer2.ui.AdViewProvider
import com.google.android.exoplayer2.upstream.DataSchemeDataSource
import com.google.android.exoplayer2.upstream.DataSourceUtil
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.math.DoubleMath
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

/** Utilities for working with IMA SDK and IMA extension data types.  */ /* package */
object ImaUtil {
    const val TIMEOUT_UNSET = -1
    const val BITRATE_UNSET = -1

    /**
     * Returns the IMA [FriendlyObstructionPurpose] corresponding to the given [ ][AdOverlayInfo.purpose].
     */
    fun getFriendlyObstructionPurpose(
            purpose: @AdOverlayInfo.Purpose Int): FriendlyObstructionPurpose {
        return when (purpose) {
            AdOverlayInfo.PURPOSE_CONTROLS -> FriendlyObstructionPurpose.VIDEO_CONTROLS
            AdOverlayInfo.PURPOSE_CLOSE_AD -> FriendlyObstructionPurpose.CLOSE_AD
            AdOverlayInfo.PURPOSE_NOT_VISIBLE -> FriendlyObstructionPurpose.NOT_VISIBLE
            AdOverlayInfo.PURPOSE_OTHER -> FriendlyObstructionPurpose.OTHER
            else -> FriendlyObstructionPurpose.OTHER
        }
    }

    /**
     * Returns the microsecond ad group timestamps corresponding to the specified cue points.
     *
     * @param cuePoints The cue points of the ads in seconds, provided by the IMA SDK.
     * @return The corresponding microsecond ad group timestamps.
     */
    @JvmStatic
    fun getAdGroupTimesUsForCuePoints(cuePoints: List<Float>): LongArray {
        if (cuePoints.isEmpty()) {
            return longArrayOf(0L)
        }
        val count = cuePoints.size
        val adGroupTimesUs = LongArray(count)
        var adGroupIndex = 0
        for (i in 0 until count) {
            val cuePoint = cuePoints[i].toDouble()
            if (cuePoint == -1.0) {
                adGroupTimesUs[count - 1] = C.TIME_END_OF_SOURCE
            } else {
                adGroupTimesUs[adGroupIndex++] = Math.round(C.MICROS_PER_SECOND * cuePoint)
            }
        }
        // Cue points may be out of order, so sort them.
        Arrays.sort(adGroupTimesUs, 0, adGroupIndex)
        return adGroupTimesUs
    }

    /** Returns an [AdsRequest] based on the specified ad tag [DataSpec].  */
    @Throws(IOException::class)
    fun getAdsRequestForAdTagDataSpec(
            imaFactory: ImaFactory, adTagDataSpec: DataSpec): AdsRequest {
        val request = imaFactory.createAdsRequest()
        if (DataSchemeDataSource.SCHEME_DATA == adTagDataSpec.uri.scheme) {
            val dataSchemeDataSource = DataSchemeDataSource()
            try {
                dataSchemeDataSource.open(adTagDataSpec)
                request.adsResponse = Util.fromUtf8Bytes(DataSourceUtil.readToEnd(dataSchemeDataSource))
            } finally {
                dataSchemeDataSource.close()
            }
        } else {
            request.adTagUrl = adTagDataSpec.uri.toString()
        }
        return request
    }

    /** Returns whether the ad error indicates that an entire ad group failed to load.  */
    fun isAdGroupLoadError(adError: AdError): Boolean {
        // TODO: Find out what other errors need to be handled (if any), and whether each one relates to
        // a single ad, ad group or the whole timeline.
        return (adError.errorCode == AdError.AdErrorCode.VAST_LINEAR_ASSET_MISMATCH
                || adError.errorCode == AdError.AdErrorCode.UNKNOWN_ERROR)
    }// IMA SDK callbacks occur on the main thread. This method can be used to check that the player
    // is using the same looper, to ensure all interaction with this class is on the main thread.
    /** Returns the looper on which all IMA SDK interaction must occur.  */
    val imaLooper: Looper
        get() =// IMA SDK callbacks occur on the main thread. This method can be used to check that the player
                // is using the same looper, to ensure all interaction with this class is on the main thread.
            Looper.getMainLooper()

    /** Returns a human-readable representation of a video progress update.  */
    fun getStringForVideoProgressUpdate(videoProgressUpdate: VideoProgressUpdate): String {
        return if (VideoProgressUpdate.VIDEO_TIME_NOT_READY == videoProgressUpdate) {
            "not ready"
        } else {
            Util.formatInvariant(
                    "%d ms of %d ms",
                    videoProgressUpdate.currentTimeMs, videoProgressUpdate.durationMs)
        }
    }

    /**
     * Expands a placeholder ad group with a single ad to the requested number of ads and sets the
     * duration of the inserted ad.
     *
     *
     * The remaining ad group duration is propagated to the ad following the inserted ad. If the
     * inserted ad is the last ad, the remaining ad group duration is wrapped around to the first ad
     * in the group.
     *
     * @param adGroupIndex The ad group index of the ad group to expand.
     * @param adIndexInAdGroup The ad index to set the duration.
     * @param adDurationUs The duration of the ad.
     * @param adGroupDurationUs The duration of the whole ad group.
     * @param adsInAdGroupCount The number of ads of the ad group.
     * @param adPlaybackState The ad playback state to modify.
     * @return The updated ad playback state.
     */
    @JvmStatic
    @CheckResult
    fun expandAdGroupPlaceholder(
            adGroupIndex: Int,
            adGroupDurationUs: Long,
            adIndexInAdGroup: Int,
            adDurationUs: Long,
            adsInAdGroupCount: Int,
            adPlaybackState: AdPlaybackState?): AdPlaybackState {
        Assertions.checkArgument(adIndexInAdGroup < adsInAdGroupCount)
        val adDurationsUs = updateAdDurationAndPropagate(LongArray(adsInAdGroupCount), adIndexInAdGroup, adDurationUs, adGroupDurationUs)
        return adPlaybackState
                .withAdCount(adGroupIndex, adDurationsUs.size)
                .withAdDurationsUs(adGroupIndex, *adDurationsUs)
    }

    /**
     * Updates the duration of an ad in and ad group.
     *
     *
     * The difference of the previous duration and the updated duration is propagated to the ad
     * following the updated ad. If the updated ad is the last ad, the remaining duration is wrapped
     * around to the first ad in the group.
     *
     *
     * The remaining ad duration is only propagated if the destination ad has a duration of 0.
     *
     * @param adGroupIndex The ad group index of the ad group to expand.
     * @param adIndexInAdGroup The ad index to set the duration.
     * @param adDurationUs The duration of the ad.
     * @param adPlaybackState The ad playback state to modify.
     * @return The updated ad playback state.
     */
    @JvmStatic
    @CheckResult
    fun updateAdDurationInAdGroup(
            adGroupIndex: Int, adIndexInAdGroup: Int, adDurationUs: Long, adPlaybackState: AdPlaybackState?): AdPlaybackState {
        val adGroup = adPlaybackState!!.getAdGroup(adGroupIndex)
        Assertions.checkArgument(adIndexInAdGroup < adGroup.durationsUs.size)
        val adDurationsUs = updateAdDurationAndPropagate(
                Arrays.copyOf(adGroup.durationsUs, adGroup.durationsUs.size),
                adIndexInAdGroup,
                adDurationUs,
                adGroup.durationsUs[adIndexInAdGroup])
        return adPlaybackState.withAdDurationsUs(adGroupIndex, *adDurationsUs)
    }

    /**
     * Updates the duration of the given ad in the array.
     *
     *
     * The remaining difference when subtracting `adDurationUs` from `remainingDurationUs` is used as the duration of the next ad after `adIndex`. If the
     * updated ad is the last ad, the remaining duration is wrapped around to the first ad of the
     * group.
     *
     *
     * The remaining ad duration is only propagated if the destination ad has a duration of 0.
     *
     * @param adDurationsUs The array to edit.
     * @param adIndex The index of the ad in the durations array.
     * @param adDurationUs The new ad duration.
     * @param remainingDurationUs The remaining ad duration before updating the new ad duration.
     * @return The updated input array, for convenience.
     */
    private fun updateAdDurationAndPropagate(
            adDurationsUs: LongArray, adIndex: Int, adDurationUs: Long, remainingDurationUs: Long): LongArray {
        adDurationsUs[adIndex] = adDurationUs
        val nextAdIndex = (adIndex + 1) % adDurationsUs.size
        if (adDurationsUs[nextAdIndex] == 0L) {
            // Propagate the remaining duration to the next ad.
            adDurationsUs[nextAdIndex] = Math.max(0, remainingDurationUs - adDurationUs)
        }
        return adDurationsUs
    }

    /**
     * Splits an [AdPlaybackState] into a separate [AdPlaybackState] for each period of a
     * content timeline.
     *
     *
     * If a period is enclosed by an ad group, the period is considered an ad period. Splitting
     * results in a separate [ad playback state][AdPlaybackState] for each period that has either
     * no ads or a single ad. In the latter case, the duration of the single ad is set to the duration
     * of the period consuming the entire duration of the period. Accordingly an ad period does not
     * contribute to the duration of the containing window.
     *
     * @param adPlaybackState The ad playback state to be split.
     * @param contentTimeline The content timeline for each period of which to create an [     ].
     * @return A map of ad playback states for each period UID in the content timeline.
     */
    @JvmStatic
    fun splitAdPlaybackStateForPeriods(
            adPlaybackState: AdPlaybackState?, contentTimeline: Timeline): ImmutableMap<Any, AdPlaybackState?> {
        val period = Timeline.Period()
        if (contentTimeline.periodCount == 1) {
            // A single period gets the entire ad playback state that may contain multiple ad groups.
            return ImmutableMap.of(
                    Assertions.checkNotNull(
                            contentTimeline.getPeriod( /* periodIndex= */0, period,  /* setIds= */true).uid),
                    adPlaybackState)
        }
        var periodIndex = 0
        var totalElapsedContentDurationUs: Long = 0
        val adsId = Assertions.checkNotNull(adPlaybackState!!.adsId)
        val contentOnlyAdPlaybackState = AdPlaybackState(adsId)
        val adPlaybackStates: MutableMap<Any, AdPlaybackState?> = HashMap()
        for (i in adPlaybackState.removedAdGroupCount until adPlaybackState.adGroupCount) {
            val adGroup = adPlaybackState.getAdGroup( /* adGroupIndex= */i)
            if (adGroup.timeUs == C.TIME_END_OF_SOURCE) {
                Assertions.checkState(i == adPlaybackState.adGroupCount - 1)
                // The last ad group is a placeholder for a potential post roll. We can just stop here.
                break
            }
            // The ad group start timeUs is in content position. We need to add the ad
            // duration before the ad group to translate the start time to the position in the period.
            val adGroupDurationUs = Util.sum(*adGroup.durationsUs)
            var elapsedAdGroupAdDurationUs: Long = 0
            for (j in periodIndex until contentTimeline.periodCount) {
                contentTimeline.getPeriod(j, period,  /* setIds= */true)
                if (totalElapsedContentDurationUs < adGroup.timeUs) {
                    // Period starts before the ad group, so it is a content period.
                    adPlaybackStates[Assertions.checkNotNull(period.uid)] = contentOnlyAdPlaybackState
                    totalElapsedContentDurationUs += period.durationUs
                } else {
                    val periodStartUs = totalElapsedContentDurationUs + elapsedAdGroupAdDurationUs
                    if (periodStartUs + period.durationUs <= adGroup.timeUs + adGroupDurationUs) {
                        // The period ends before the end of the ad group, so it is an ad period (Note: A VOD ad
                        // reported by the IMA SDK spans multiple periods before the LOADED event arrives).
                        adPlaybackStates[Assertions.checkNotNull(period.uid)] = splitAdGroupForPeriod(adsId, adGroup, periodStartUs, period.durationUs)
                        elapsedAdGroupAdDurationUs += period.durationUs
                    } else {
                        // Period is after the current ad group. Continue with next ad group.
                        break
                    }
                }
                // Increment the period index to the next unclassified period.
                periodIndex++
            }
        }
        // The remaining periods end after the last ad group, so these are content periods.
        for (i in periodIndex until contentTimeline.periodCount) {
            contentTimeline.getPeriod(i, period,  /* setIds= */true)
            adPlaybackStates[Assertions.checkNotNull(period.uid)] = contentOnlyAdPlaybackState
        }
        return ImmutableMap.copyOf(adPlaybackStates)
    }

    private fun splitAdGroupForPeriod(
            adsId: Any, adGroup: AdGroup, periodStartUs: Long, periodDurationUs: Long): AdPlaybackState {
        var adPlaybackState = AdPlaybackState(Assertions.checkNotNull(adsId),  /* adGroupTimesUs...= */0)
                .withAdCount( /* adGroupIndex= */0,  /* adCount= */1)
                .withAdDurationsUs( /* adGroupIndex= */0, periodDurationUs)
                .withIsServerSideInserted( /* adGroupIndex= */0, true)
                .withContentResumeOffsetUs( /* adGroupIndex= */0, adGroup.contentResumeOffsetUs)
        val periodEndUs = periodStartUs + periodDurationUs
        var adDurationsUs: Long = 0
        for (i in 0 until adGroup.count) {
            adDurationsUs += adGroup.durationsUs[i]
            if (periodEndUs <= adGroup.timeUs + adDurationsUs + 10000) {
                // Map the state of the global ad state to the period specific ad state.
                when (adGroup.states[i]) {
                    AdPlaybackState.AD_STATE_PLAYED -> adPlaybackState = adPlaybackState.withPlayedAd( /* adGroupIndex= */0,  /* adIndexInAdGroup= */0)
                    AdPlaybackState.AD_STATE_SKIPPED -> adPlaybackState = adPlaybackState.withSkippedAd( /* adGroupIndex= */0,  /* adIndexInAdGroup= */0)
                    AdPlaybackState.AD_STATE_ERROR -> adPlaybackState = adPlaybackState.withAdLoadError( /* adGroupIndex= */0,  /* adIndexInAdGroup= */0)
                    else -> {}
                }
                break
            }
        }
        return adPlaybackState
    }

    /**
     * Returns the `adGroupIndex` and the `adIndexInAdGroup` for the given period index of
     * an ad period.
     *
     * @param adPeriodIndex The period index of the ad period.
     * @param adPlaybackState The ad playback state that holds the ad group and ad information.
     * @param contentTimeline The timeline that contains the ad period.
     * @return A pair with the ad group index (first) and the ad index in that ad group (second).
     */
    @JvmStatic
    fun getAdGroupAndIndexInMultiPeriodWindow(
            adPeriodIndex: Int, adPlaybackState: AdPlaybackState?, contentTimeline: Timeline): Pair<Int, Int> {
        val period = Timeline.Period()
        var periodIndex = 0
        var totalElapsedContentDurationUs: Long = 0
        for (i in adPlaybackState!!.removedAdGroupCount until adPlaybackState.adGroupCount) {
            var adIndexInAdGroup = 0
            val adGroup = adPlaybackState.getAdGroup( /* adGroupIndex= */i)
            val adGroupDurationUs = Util.sum(*adGroup.durationsUs)
            var elapsedAdGroupAdDurationUs: Long = 0
            for (j in periodIndex until contentTimeline.periodCount) {
                contentTimeline.getPeriod(j, period,  /* setIds= */true)
                if (totalElapsedContentDurationUs < adGroup.timeUs) {
                    // Period starts before the ad group, so it is a content period.
                    totalElapsedContentDurationUs += period.durationUs
                } else {
                    val periodStartUs = totalElapsedContentDurationUs + elapsedAdGroupAdDurationUs
                    if (periodStartUs + period.durationUs <= adGroup.timeUs + adGroupDurationUs) {
                        // The period ends before the end of the ad group, so it is an ad period.
                        if (j == adPeriodIndex) {
                            return Pair( /* adGroupIndex= */i, adIndexInAdGroup)
                        }
                        elapsedAdGroupAdDurationUs += period.durationUs
                        adIndexInAdGroup++
                    } else {
                        // Period is after the current ad group. Continue with next ad group.
                        break
                    }
                }
                // Increment the period index to the next unclassified period.
                periodIndex++
            }
        }
        throw IllegalStateException()
    }

    /**
     * Called when the SDK emits a `LOADED` event of an IMA SSAI live stream.
     *
     *
     * For each ad, the SDK emits a `LOADED` event at the start of the ad. The `LOADED`
     * event provides the information of a certain ad (index and duration) and its ad pod (number of
     * ads and total ad duration) that is mapped to an ad in an [ad group][AdGroup] of an
     * [ad playback state][AdPlaybackState] to reflect ads in the ExoPlayer media structure.
     *
     *
     * In the normal case (when all ad information is available completely and in time), the
     * life-cycle of a live ad group and its ads has these phases:
     *
     *
     *  1. When playing content and a `LOADED` event arrives, an ad group is inserted at the
     * current position with the number of ads reported by the ad pod. The duration of the first
     * ad is set and its state is set to [AdPlaybackState.AD_STATE_AVAILABLE]. The
     * duration of the 2nd ad is set to the remaining duration of the total ad group duration.
     * This pads out the duration of the ad group, so it doesn't end before the next ad event
     * arrives. When inserting the ad group at the current position, the player immediately
     * advances to play the inserted ad period.
     *  1. When playing an ad group and a further `LOADED` event arrives, the ad state is
     * inspected to find the [ad group currently being played][AdPlaybackState.getAdGroupIndexForPositionUs]. We query for the first [       ][AdPlaybackState.AD_STATE_UNAVAILABLE] of that ad group, override its
     * placeholder duration, mark it [available][AdPlaybackState.AD_STATE_AVAILABLE]
     * and propagate the remainder of the placeholder duration to the next ad. Repeating this
     * step until all ads are configured and marked as available.
     *  1. When playing an ad and a `LOADED` event arrives but no more ads are in [       ][AdPlaybackState.AD_STATE_UNAVAILABLE], the group is expanded by inserting a new ad at the
     * end of the ad group.
     *  1. After playing an ad: When playback exits from an ad period to the next ad or back to
     * content, [ImaServerSideAdInsertionMediaSource] detects [       ][Player.Listener.onPositionDiscontinuity], identifies [the][Player.PositionInfo.adIndexInAdGroup] and [marks the ad as played][AdPlaybackState.AD_STATE_PLAYED].
     *
     *
     *
     * Some edge-cases need consideration. When a user joins a live stream during an ad being
     * played, ad information previous to the first received `LOADED` event is missing. Only ads
     * starting from the first ad with full information are inserted into the group (back to happy
     * path step 2).
     *
     *
     * There is further a chance, that a (pre-fetch) event arrives after the ad group has already
     * ended. In such a case, the pre-fetch ad starts a new ad group with the remaining ads in the
     * same way as the during-ad-joiner case that can afterwards be expanded again (back to end of
     * happy path step 2).
     *
     * @param currentContentPeriodPositionUs The current public content position, in microseconds.
     * @param adDurationUs The duration of the ad to be inserted, in microseconds.
     * @param adPositionInAdPod The ad position in the ad pod (Note: starts with index 1).
     * @param totalAdDurationUs The total duration of all ads as declared by the ad pod.
     * @param totalAdsInAdPod The total number of ads declared by the ad pod.
     * @param adPlaybackState The ad playback state with the current ad information.
     * @return The updated [AdPlaybackState].
     */
    @JvmStatic
    @CheckResult
    fun addLiveAdBreak(
            currentContentPeriodPositionUs: Long,
            adDurationUs: Long,
            adPositionInAdPod: Int,
            totalAdDurationUs: Long,
            totalAdsInAdPod: Int,
            adPlaybackState: AdPlaybackState?): AdPlaybackState {
        var adPlaybackState = adPlaybackState
        Assertions.checkArgument(adPositionInAdPod > 0)
        val mediaPeriodPositionUs = ServerSideAdInsertionUtil.getMediaPeriodPositionUsForContent(
                currentContentPeriodPositionUs,  /* nextAdGroupIndex= */C.INDEX_UNSET, adPlaybackState!!)
        // TODO(b/217187518) Support seeking backwards.
        var adGroupIndex = adPlaybackState.getAdGroupIndexForPositionUs(
                mediaPeriodPositionUs,  /* periodDurationUs= */C.TIME_UNSET)
        if (adGroupIndex == C.INDEX_UNSET) {
            val adIndexInAdGroup = adPositionInAdPod - 1
            val adDurationsUs = updateAdDurationAndPropagate(LongArray(totalAdsInAdPod - adIndexInAdGroup),  /* adIndex= */
                    0,
                    adDurationUs,
                    totalAdDurationUs)
            adPlaybackState = ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
                    adPlaybackState,  /* fromPositionUs= */
                    currentContentPeriodPositionUs,  /* contentResumeOffsetUs= */
                    Util.sum(*adDurationsUs),  /* adDurationsUs...= */
                    *adDurationsUs)
            adGroupIndex = adPlaybackState.getAdGroupIndexForPositionUs(
                    mediaPeriodPositionUs,  /* periodDurationUs= */C.TIME_UNSET)
            if (adGroupIndex != C.INDEX_UNSET) {
                adPlaybackState = adPlaybackState
                        .withAvailableAd(adGroupIndex,  /* adIndexInAdGroup= */0)
                        .withOriginalAdCount(adGroupIndex,  /* originalAdCount= */totalAdsInAdPod)
            }
        } else {
            val adGroup = adPlaybackState.getAdGroup(adGroupIndex)
            var newDurationsUs = Arrays.copyOf(adGroup.durationsUs, adGroup.count)
            val nextUnavailableAdIndex = getNextUnavailableAdIndex(adGroup)
            if (adGroup.originalCount < totalAdsInAdPod || nextUnavailableAdIndex == adGroup.count) {
                val adInAdGroupCount = Math.max(totalAdsInAdPod, nextUnavailableAdIndex + 1)
                adPlaybackState = adPlaybackState
                        .withAdCount(adGroupIndex, adInAdGroupCount)
                        .withOriginalAdCount(adGroupIndex,  /* originalAdCount= */adInAdGroupCount)
                newDurationsUs = Arrays.copyOf(newDurationsUs, adInAdGroupCount)
                newDurationsUs[nextUnavailableAdIndex] = totalAdDurationUs
                Arrays.fill(
                        newDurationsUs,  /* fromIndex= */
                        nextUnavailableAdIndex + 1,  /* toIndex= */
                        adInAdGroupCount,  /* val= */
                        0L)
            }
            val remainingDurationUs = Math.max(adDurationUs, newDurationsUs[nextUnavailableAdIndex])
            updateAdDurationAndPropagate(
                    newDurationsUs, nextUnavailableAdIndex, adDurationUs, remainingDurationUs)
            adPlaybackState = adPlaybackState
                    .withAdDurationsUs(adGroupIndex, *newDurationsUs)
                    .withAvailableAd(adGroupIndex, nextUnavailableAdIndex)
                    .withContentResumeOffsetUs(adGroupIndex, Util.sum(*newDurationsUs))
        }
        return adPlaybackState
    }

    /**
     * Splits the ad group at an available ad at a given split index.
     *
     *
     * When splitting, the ads from and after the split index are removed from the existing ad
     * group. Then the ad events of all removed available ads are replicated to get the exact same
     * result as if the new ad group was created by SDK ad events.
     *
     * @param adGroup The ad group to split.
     * @param adGroupIndex The index of the ad group in the ad playback state.
     * @param splitIndexExclusive The first index that should be part of the newly created ad group.
     * @param adPlaybackState The ad playback state to modify.
     * @return The ad playback state with the split ad group.
     */
    @JvmStatic
    @CheckResult
    fun splitAdGroup(
            adGroup: AdGroup, adGroupIndex: Int, splitIndexExclusive: Int, adPlaybackState: AdPlaybackState?): AdPlaybackState? {
        var adPlaybackState = adPlaybackState
        Assertions.checkArgument(splitIndexExclusive > 0 && splitIndexExclusive < adGroup.count)
        // Remove the ads from the ad group.
        for (i in 0 until adGroup.count - splitIndexExclusive) {
            adPlaybackState = adPlaybackState!!.withLastAdRemoved(adGroupIndex)
        }
        val previousAdGroup = adPlaybackState!!.getAdGroup(adGroupIndex)
        val newAdGroupTimeUs = previousAdGroup.timeUs + previousAdGroup.contentResumeOffsetUs
        // Replicate ad events for each available ad that has been removed.
        val removedStates = Arrays.copyOfRange(adGroup.states, splitIndexExclusive, adGroup.count)
        val removedDurationsUs = Arrays.copyOfRange(adGroup.durationsUs, splitIndexExclusive, adGroup.count)
        var remainingAdDurationUs = Util.sum(*removedDurationsUs)
        var i = 0
        while (i < removedStates.size && removedStates[i] == AdPlaybackState.AD_STATE_AVAILABLE) {
            adPlaybackState = addLiveAdBreak(
                    newAdGroupTimeUs,  /* adDurationUs= */
                    removedDurationsUs[i],  /* adPositionInAdPod= */
                    i + 1,  /* totalAdDurationUs= */
                    remainingAdDurationUs,  /* totalAdsInAdPod= */
                    removedDurationsUs.size,
                    adPlaybackState)
            remainingAdDurationUs -= removedDurationsUs[i]
            i++
        }
        return adPlaybackState
    }

    private fun getNextUnavailableAdIndex(adGroup: AdGroup): Int {
        for (i in adGroup.states.indices) {
            if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
                return i
            }
        }
        return adGroup.states.size
    }

    /**
     * Converts a time in seconds to the corresponding time in microseconds.
     *
     *
     * Fractional values are rounded to the nearest microsecond using [RoundingMode.HALF_UP].
     *
     * @param timeSec The time in seconds.
     * @return The corresponding time in microseconds.
     */
    fun secToUsRounded(timeSec: Double): Long {
        return DoubleMath.roundToLong(
                BigDecimal.valueOf(timeSec).scaleByPowerOfTen(6).toDouble(), RoundingMode.HALF_UP)
    }

    /**
     * Converts a time in seconds to the corresponding time in milliseconds.
     *
     *
     * Fractional values are rounded to the nearest millisecond using [RoundingMode.HALF_UP].
     *
     * @param timeSec The time in seconds.
     * @return The corresponding time in milliseconds.
     */
    fun secToMsRounded(timeSec: Double): Long {
        return DoubleMath.roundToLong(
                BigDecimal.valueOf(timeSec).scaleByPowerOfTen(3).toDouble(), RoundingMode.HALF_UP)
    }

    /** Factory for objects provided by the IMA SDK.  */
    interface ImaFactory {
        /** Creates [ImaSdkSettings] for configuring the IMA SDK.  */
        fun createImaSdkSettings(): ImaSdkSettings

        /**
         * Creates [AdsRenderingSettings] for giving the [AdsManager] parameters that
         * control rendering of ads.
         */
        fun createAdsRenderingSettings(): AdsRenderingSettings?

        /**
         * Creates an [AdDisplayContainer] to hold the player for video ads, a container for
         * non-linear ads, and slots for companion ads.
         */
        fun createAdDisplayContainer(container: ViewGroup?, player: VideoAdPlayer?): AdDisplayContainer?

        /** Creates an [AdDisplayContainer] to hold the player for audio ads.  */
        fun createAudioAdDisplayContainer(context: Context?, player: VideoAdPlayer?): AdDisplayContainer?

        /**
         * Creates a [FriendlyObstruction] to describe an obstruction considered "friendly" for
         * viewability measurement purposes.
         */
        fun createFriendlyObstruction(
                view: View?,
                friendlyObstructionPurpose: FriendlyObstructionPurpose?,
                reasonDetail: String?): FriendlyObstruction?

        /** Creates an [AdsRequest] to contain the data used to request ads.  */
        fun createAdsRequest(): AdsRequest

        /** Creates an [AdsLoader] for requesting ads using the specified settings.  */
        fun createAdsLoader(
                context: Context?, imaSdkSettings: ImaSdkSettings?, adDisplayContainer: AdDisplayContainer?): AdsLoader?
    }

    /** Stores configuration for ad loading and playback.  */
    class Configuration(
            val adPreloadTimeoutMs: Long,
            val vastLoadTimeoutMs: Int,
            val mediaLoadTimeoutMs: Int,
            val focusSkipButtonWhenAvailable: Boolean,
            val playAdBeforeStartPosition: Boolean,
            val mediaBitrate: Int,
            val enableContinuousPlayback: Boolean?,
            val adMediaMimeTypes: List<String>?,
            val adUiElements: Set<UiElement>?,
            val companionAdSlots: Collection<CompanionAdSlot>?,
            val applicationAdErrorListener: AdErrorListener?,
            val applicationAdEventListener: AdEventListener?,
            val applicationVideoAdPlayerCallback: VideoAdPlayerCallback?,
            val imaSdkSettings: ImaSdkSettings?,
            val debugModeEnabled: Boolean)

    /** Stores configuration for playing server side ad insertion content.  */
    class ServerSideAdInsertionConfiguration(
            val adViewProvider: AdViewProvider,
            val imaSdkSettings: ImaSdkSettings?,
            val applicationAdEventListener: AdEventListener?,
            val applicationAdErrorListener: AdErrorListener?,
            companionAdSlots: List<CompanionAdSlot>?,
            debugModeEnabled: Boolean) {
        val companionAdSlots: ImmutableList<CompanionAdSlot>
        val debugModeEnabled: Boolean

        init {
            this.companionAdSlots = ImmutableList.copyOf(companionAdSlots)
            this.debugModeEnabled = debugModeEnabled
        }
    }
}