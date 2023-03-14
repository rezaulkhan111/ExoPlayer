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

import android.net.Uri
import android.os.Bundle
import androidx.annotation.*
import androidx.annotation.IntRange
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.util.*
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/**
 * Represents ad group times and information on the state and URIs of ads within each ad group.
 *
 *
 * Instances are immutable. Call the `with*` methods to get new instances that have the
 * required changes.
 */
class AdPlaybackState private constructor(
        /**
         * The opaque identifier for ads with which this instance is associated, or `null` if unset.
         */
        val adsId: Any?,
        adGroups: Array<AdGroup?>?,
        /** The position offset in the first unplayed ad at which to begin playback, in microseconds.  */
        val adResumePositionUs: Long,
        /**
         * The duration of the content period in microseconds, if known. [C.TIME_UNSET] otherwise.
         */
        val contentDurationUs: Long,
        removedAdGroupCount: Int) : Bundleable {
    /**
     * Represents a group of ads, with information about their states.
     *
     *
     * Instances are immutable. Call the `with*` methods to get new instances that have the
     * required changes.
     */
    class AdGroup(
            timeUs: Long,
            count: Int,
            originalCount: Int,
            states: IntArray,
            uris: Array<Uri?>,
            durationsUs: LongArray,
            contentResumeOffsetUs: Long,
            isServerSideInserted: Boolean) : Bundleable {
        /**
         * The time of the ad group in the [Timeline.Period], in microseconds, or [ ][C.TIME_END_OF_SOURCE] to indicate a postroll ad.
         */
        val timeUs: Long

        /** The number of ads in the ad group, or [C.LENGTH_UNSET] if unknown.  */
        val count: Int

        /**
         * The original number of ads in the ad group in case the ad group is only partially available,
         * or [C.LENGTH_UNSET] if unknown. An ad can be partially available when a server side
         * inserted ad live stream is joined while an ad is already playing and some ad information is
         * missing.
         */
        val originalCount: Int

        /** The URI of each ad in the ad group.  */
        val uris: Array<Uri?>

        /** The state of each ad in the ad group.  */
        val states: IntArray

        /** The durations of each ad in the ad group, in microseconds.  */
        val durationsUs: LongArray

        /**
         * The offset in microseconds which should be added to the content stream when resuming playback
         * after the ad group.
         */
        val contentResumeOffsetUs: Long

        /** Whether this ad group is server-side inserted and part of the content stream.  */
        val isServerSideInserted: Boolean

        /**
         * Creates a new ad group with an unspecified number of ads.
         *
         * @param timeUs The time of the ad group in the [Timeline.Period], in microseconds, or
         * [C.TIME_END_OF_SOURCE] to indicate a postroll ad.
         */
        constructor(timeUs: Long) : this(
                timeUs,  /* count= */
                C.LENGTH_UNSET,  /* originalCount= */
                C.LENGTH_UNSET, IntArray(0), arrayOfNulls<Uri>(0), LongArray(0),  /* contentResumeOffsetUs= */
                0,  /* isServerSideInserted= */
                false) {
        }

        /**
         * Returns the index of the first ad in the ad group that should be played, or [.count] if
         * no ads should be played.
         */
        val firstAdIndexToPlay: Int
            get() {
                return getNextAdIndexToPlay(-1)
            }

        /**
         * Returns the index of the next ad in the ad group that should be played after playing `lastPlayedAdIndex`, or [.count] if no later ads should be played. If no ads have been
         * played, pass -1 to get the index of the first ad to play.
         *
         *
         * Note: [Server side inserted ads][.isServerSideInserted] are always considered
         * playable.
         */
        fun getNextAdIndexToPlay(@IntRange(from = -1) lastPlayedAdIndex: Int): Int {
            var nextAdIndexToPlay: Int = lastPlayedAdIndex + 1
            while (nextAdIndexToPlay < states.size) {
                if ((isServerSideInserted
                                || (states.get(nextAdIndexToPlay) == AD_STATE_UNAVAILABLE
                                ) || (states.get(nextAdIndexToPlay) == AD_STATE_AVAILABLE))) {
                    break
                }
                nextAdIndexToPlay++
            }
            return nextAdIndexToPlay
        }

        /** Returns whether the ad group has at least one ad that should be played.  */
        fun shouldPlayAdGroup(): Boolean {
            return count == C.LENGTH_UNSET || firstAdIndexToPlay < count
        }

        /**
         * Returns whether the ad group has at least one ad that is neither played, skipped, nor failed.
         */
        fun hasUnplayedAds(): Boolean {
            if (count == C.LENGTH_UNSET) {
                return true
            }
            for (i in 0 until count) {
                if (states.get(i) == AD_STATE_UNAVAILABLE || states.get(i) == AD_STATE_AVAILABLE) {
                    return true
                }
            }
            return false
        }

        public override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val adGroup: AdGroup = o as AdGroup
            return ((timeUs == adGroup.timeUs
                    ) && (count == adGroup.count
                    ) && (originalCount == adGroup.originalCount
                    ) && Arrays.equals(uris, adGroup.uris)
                    && Arrays.equals(states, adGroup.states)
                    && Arrays.equals(durationsUs, adGroup.durationsUs)
                    && (contentResumeOffsetUs == adGroup.contentResumeOffsetUs
                    ) && (isServerSideInserted == adGroup.isServerSideInserted))
        }

        public override fun hashCode(): Int {
            var result: Int = count
            result = 31 * result + originalCount
            result = 31 * result + (timeUs xor (timeUs ushr 32)).toInt()
            result = 31 * result + Arrays.hashCode(uris)
            result = 31 * result + Arrays.hashCode(states)
            result = 31 * result + Arrays.hashCode(durationsUs)
            result = 31 * result + (contentResumeOffsetUs xor (contentResumeOffsetUs ushr 32)).toInt()
            result = 31 * result + (if (isServerSideInserted) 1 else 0)
            return result
        }

        /** Returns a new instance with the [.timeUs] set to the specified value.  */
        @CheckResult
        fun withTimeUs(timeUs: Long): AdGroup {
            return AdGroup(
                    timeUs,
                    count,
                    originalCount,
                    states,
                    uris,
                    durationsUs,
                    contentResumeOffsetUs,
                    isServerSideInserted)
        }

        /** Returns a new instance with the ad count set to `count`.  */
        @CheckResult
        fun withAdCount(count: Int): AdGroup {
            val states: IntArray = copyStatesWithSpaceForAdCount(states, count)
            val durationsUs: LongArray = copyDurationsUsWithSpaceForAdCount(durationsUs, count)
            val uris: Array<Uri?> = Arrays.copyOf(uris, count)
            return AdGroup(
                    timeUs,
                    count,
                    originalCount,
                    states,
                    uris,
                    durationsUs,
                    contentResumeOffsetUs,
                    isServerSideInserted)
        }

        /**
         * Returns a new instance with the specified `uri` set for the specified ad, and the ad
         * marked as [.AD_STATE_AVAILABLE].
         */
        @CheckResult
        fun withAdUri(uri: Uri?, @IntRange(from = 0) index: Int): AdGroup {
            val states: IntArray = copyStatesWithSpaceForAdCount(states, index + 1)
            val durationsUs: LongArray = if (durationsUs.size == states.size) durationsUs else copyDurationsUsWithSpaceForAdCount(durationsUs, states.size)
            val uris: Array<Uri?> = Arrays.copyOf(uris, states.size)
            uris.get(index) = uri
            states.get(index) = AD_STATE_AVAILABLE
            return AdGroup(
                    timeUs,
                    count,
                    originalCount,
                    states,
                    uris,
                    durationsUs,
                    contentResumeOffsetUs,
                    isServerSideInserted)
        }

        /**
         * Returns a new instance with the specified ad set to the specified `state`. The ad
         * specified must currently either be in [.AD_STATE_UNAVAILABLE] or [ ][.AD_STATE_AVAILABLE].
         *
         *
         * This instance's ad count may be unknown, in which case `index` must be less than the
         * ad count specified later. Otherwise, `index` must be less than the current ad count.
         */
        @CheckResult
        fun withAdState(state: @AdState Int, @IntRange(from = 0) index: Int): AdGroup {
            Assertions.checkArgument(count == C.LENGTH_UNSET || index < count)
            val states: IntArray = copyStatesWithSpaceForAdCount(states,  /* count= */index + 1)
            Assertions.checkArgument(
                    (states.get(index) == AD_STATE_UNAVAILABLE
                            ) || (states.get(index) == AD_STATE_AVAILABLE
                            ) || (states.get(index) == state))
            val durationsUs: LongArray = if (durationsUs.size == states.size) durationsUs else copyDurationsUsWithSpaceForAdCount(durationsUs, states.size)
            val uris: Array<Uri?> = if (uris.size == states.size) uris else Arrays.copyOf(uris, states.size)
            states.get(index) = state
            return AdGroup(
                    timeUs,
                    count,
                    originalCount,
                    states,
                    uris,
                    durationsUs,
                    contentResumeOffsetUs,
                    isServerSideInserted)
        }

        /** Returns a new instance with the specified ad durations, in microseconds.  */
        @CheckResult
        fun withAdDurationsUs(durationsUs: LongArray): AdGroup {
            var durationsUs: LongArray = durationsUs
            if (durationsUs.size < uris.size) {
                durationsUs = copyDurationsUsWithSpaceForAdCount(durationsUs, uris.size)
            } else if (count != C.LENGTH_UNSET && durationsUs.size > uris.size) {
                durationsUs = Arrays.copyOf(durationsUs, uris.size)
            }
            return AdGroup(
                    timeUs,
                    count,
                    originalCount,
                    states,
                    uris,
                    durationsUs,
                    contentResumeOffsetUs,
                    isServerSideInserted)
        }

        /** Returns an instance with the specified [.contentResumeOffsetUs].  */
        @CheckResult
        fun withContentResumeOffsetUs(contentResumeOffsetUs: Long): AdGroup {
            return AdGroup(
                    timeUs,
                    count,
                    originalCount,
                    states,
                    uris,
                    durationsUs,
                    contentResumeOffsetUs,
                    isServerSideInserted)
        }

        /** Returns an instance with the specified value for [.isServerSideInserted].  */
        @CheckResult
        fun withIsServerSideInserted(isServerSideInserted: Boolean): AdGroup {
            return AdGroup(
                    timeUs,
                    count,
                    originalCount,
                    states,
                    uris,
                    durationsUs,
                    contentResumeOffsetUs,
                    isServerSideInserted)
        }

        /** Returns an instance with the specified value for [.originalCount].  */
        fun withOriginalAdCount(originalCount: Int): AdGroup {
            return AdGroup(
                    timeUs,
                    count,
                    originalCount,
                    states,
                    uris,
                    durationsUs,
                    contentResumeOffsetUs,
                    isServerSideInserted)
        }

        /** Removes the last ad from the ad group.  */
        fun withLastAdRemoved(): AdGroup {
            val newCount: Int = states.size - 1
            val newStates: IntArray = Arrays.copyOf(states, newCount)
            val newUris: Array<Uri?> = Arrays.copyOf(uris, newCount)
            var newDurationsUs: LongArray = durationsUs
            if (durationsUs.size > newCount) {
                newDurationsUs = Arrays.copyOf(durationsUs, newCount)
            }
            return AdGroup(
                    timeUs,
                    newCount,
                    originalCount,
                    newStates,
                    newUris,
                    newDurationsUs,  /* contentResumeOffsetUs= */
                    Util.sum(*newDurationsUs),
                    isServerSideInserted)
        }

        /**
         * Returns an instance with all unavailable and available ads marked as skipped. If the ad count
         * hasn't been set, it will be set to zero.
         */
        @CheckResult
        fun withAllAdsSkipped(): AdGroup {
            if (count == C.LENGTH_UNSET) {
                return AdGroup(
                        timeUs,  /* count= */
                        0,
                        originalCount, IntArray(0), arrayOfNulls(0), LongArray(0),
                        contentResumeOffsetUs,
                        isServerSideInserted)
            }
            val count: Int = states.size
            val states: IntArray = Arrays.copyOf(states, count)
            for (i in 0 until count) {
                if (states.get(i) == AD_STATE_AVAILABLE || states.get(i) == AD_STATE_UNAVAILABLE) {
                    states.get(i) = AD_STATE_SKIPPED
                }
            }
            return AdGroup(
                    timeUs,
                    count,
                    originalCount,
                    states,
                    uris,
                    durationsUs,
                    contentResumeOffsetUs,
                    isServerSideInserted)
        }

        /**
         * Returns an instance with all ads in final states (played, skipped, error) reset to either
         * available or unavailable, which allows to play them again.
         */
        @CheckResult
        fun withAllAdsReset(): AdGroup {
            if (count == C.LENGTH_UNSET) {
                return this
            }
            val count: Int = states.size
            val states: IntArray = Arrays.copyOf(states, count)
            for (i in 0 until count) {
                if ((states.get(i) == AD_STATE_PLAYED
                                ) || (states.get(i) == AD_STATE_SKIPPED
                                ) || (states.get(i) == AD_STATE_ERROR)) {
                    states.get(i) = if (uris.get(i) == null) AD_STATE_UNAVAILABLE else AD_STATE_AVAILABLE
                }
            }
            return AdGroup(
                    timeUs,
                    count,
                    originalCount,
                    states,
                    uris,
                    durationsUs,
                    contentResumeOffsetUs,
                    isServerSideInserted)
        }

        // Bundleable implementation.
        @Documented
        @Retention(RetentionPolicy.SOURCE)
        @Target(TYPE_USE)
        @IntDef([FIELD_TIME_US, FIELD_COUNT, FIELD_URIS, FIELD_STATES, FIELD_DURATIONS_US, FIELD_CONTENT_RESUME_OFFSET_US, FIELD_IS_SERVER_SIDE_INSERTED, FIELD_ORIGINAL_COUNT])
        private annotation class FieldNumber constructor()

        // putParcelableArrayList actually supports null elements.
        public override fun toBundle(): Bundle {
            val bundle: Bundle = Bundle()
            bundle.putLong(keyForField(FIELD_TIME_US), timeUs)
            bundle.putInt(keyForField(FIELD_COUNT), count)
            bundle.putInt(keyForField(FIELD_ORIGINAL_COUNT), originalCount)
            bundle.putParcelableArrayList(
                    keyForField(FIELD_URIS), ArrayList(Arrays.asList(*uris)))
            bundle.putIntArray(keyForField(FIELD_STATES), states)
            bundle.putLongArray(keyForField(FIELD_DURATIONS_US), durationsUs)
            bundle.putLong(keyForField(FIELD_CONTENT_RESUME_OFFSET_US), contentResumeOffsetUs)
            bundle.putBoolean(keyForField(FIELD_IS_SERVER_SIDE_INSERTED), isServerSideInserted)
            return bundle
        }

        init {
            Assertions.checkArgument(states.size == uris.size)
            this.timeUs = timeUs
            this.count = count
            this.originalCount = originalCount
            this.states = states
            this.uris = uris
            this.durationsUs = durationsUs
            this.contentResumeOffsetUs = contentResumeOffsetUs
            this.isServerSideInserted = isServerSideInserted
        }

        companion object {
            @CheckResult
            private fun copyStatesWithSpaceForAdCount(states: IntArray, count: Int): IntArray {
                var states: IntArray = states
                val oldStateCount: Int = states.size
                val newStateCount: Int = Math.max(count, oldStateCount)
                states = Arrays.copyOf(states, newStateCount)
                Arrays.fill(states, oldStateCount, newStateCount, AD_STATE_UNAVAILABLE)
                return states
            }

            @CheckResult
            private fun copyDurationsUsWithSpaceForAdCount(durationsUs: LongArray, count: Int): LongArray {
                var durationsUs: LongArray = durationsUs
                val oldDurationsUsCount: Int = durationsUs.size
                val newDurationsUsCount: Int = Math.max(count, oldDurationsUsCount)
                durationsUs = Arrays.copyOf(durationsUs, newDurationsUsCount)
                Arrays.fill(durationsUs, oldDurationsUsCount, newDurationsUsCount, C.TIME_UNSET)
                return durationsUs
            }

            private val FIELD_TIME_US: Int = 0
            private val FIELD_COUNT: Int = 1
            private val FIELD_URIS: Int = 2
            private val FIELD_STATES: Int = 3
            private val FIELD_DURATIONS_US: Int = 4
            private val FIELD_CONTENT_RESUME_OFFSET_US: Int = 5
            private val FIELD_IS_SERVER_SIDE_INSERTED: Int = 6
            private val FIELD_ORIGINAL_COUNT: Int = 7

            /** Object that can restore [AdGroup] from a [Bundle].  */
            val CREATOR: Bundleable.Creator<AdGroup> = Bundleable.Creator({ bundle: Bundle -> fromBundle(bundle) })

            // getParcelableArrayList may have null elements.
            private fun fromBundle(bundle: Bundle): AdGroup {
                val timeUs: Long = bundle.getLong(keyForField(FIELD_TIME_US))
                val count: Int = bundle.getInt(keyForField(FIELD_COUNT),  /* defaultValue= */C.LENGTH_UNSET)
                val originalCount: Int = bundle.getInt(keyForField(FIELD_ORIGINAL_COUNT),  /* defaultValue= */C.LENGTH_UNSET)
                val uriList: ArrayList<Uri>? = bundle.getParcelableArrayList(keyForField(FIELD_URIS))
                val states: IntArray? = bundle.getIntArray(keyForField(FIELD_STATES))
                val durationsUs: LongArray? = bundle.getLongArray(keyForField(FIELD_DURATIONS_US))
                val contentResumeOffsetUs: Long = bundle.getLong(keyForField(FIELD_CONTENT_RESUME_OFFSET_US))
                val isServerSideInserted: Boolean = bundle.getBoolean(keyForField(FIELD_IS_SERVER_SIDE_INSERTED))
                return AdGroup(
                        timeUs,
                        count,
                        originalCount,
                        if (states == null) IntArray(0) else states,
                        if (uriList == null) arrayOfNulls(0) else uriList.toTypedArray<Uri>(),
                        if (durationsUs == null) LongArray(0) else durationsUs,
                        contentResumeOffsetUs,
                        isServerSideInserted)
            }

            private fun keyForField(field: @FieldNumber Int): String {
                return Integer.toString(field, Character.MAX_RADIX)
            }
        }
    }

    /**
     * Represents the state of an ad in an ad group. One of [.AD_STATE_UNAVAILABLE], [ ][.AD_STATE_AVAILABLE], [.AD_STATE_SKIPPED], [.AD_STATE_PLAYED] or [ ][.AD_STATE_ERROR].
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([AD_STATE_UNAVAILABLE, AD_STATE_AVAILABLE, AD_STATE_SKIPPED, AD_STATE_PLAYED, AD_STATE_ERROR])
    annotation class AdState constructor()

    /** The number of ad groups.  */
    val adGroupCount: Int

    /**
     * The number of ad groups that have been removed. Ad groups with indices between `0`
     * (inclusive) and `removedAdGroupCount` (exclusive) will be empty and must not be modified
     * by any of the `with*` methods.
     */
    val removedAdGroupCount: Int
    private val adGroups: Array<AdGroup?>?

    /**
     * Creates a new ad playback state with the specified ad group times.
     *
     * @param adsId The opaque identifier for ads with which this instance is associated.
     * @param adGroupTimesUs The times of ad groups in microseconds, relative to the start of the
     * [Timeline.Period] they belong to. A final element with the value [     ][C.TIME_END_OF_SOURCE] indicates that there is a postroll ad.
     */
    constructor(adsId: Any?, vararg adGroupTimesUs: Long) : this(
            adsId,
            createEmptyAdGroups(adGroupTimesUs),  /* adResumePositionUs= */
            0,  /* contentDurationUs= */
            C.TIME_UNSET,  /* removedAdGroupCount= */
            0) {
    }

    /** Returns the specified [AdGroup].  */
    fun getAdGroup(@IntRange(from = 0) adGroupIndex: Int): AdGroup {
        return if (adGroupIndex < removedAdGroupCount) REMOVED_AD_GROUP else (adGroups!!.get(adGroupIndex - removedAdGroupCount))!!
    }

    /**
     * Returns the index of the ad group at or before `positionUs` that should be played before
     * the content at `positionUs`. Returns [C.INDEX_UNSET] if the ad group at or before
     * `positionUs` has no ads remaining to be played, or if there is no such ad group.
     *
     * @param positionUs The period position at or before which to find an ad group, in microseconds,
     * or [C.TIME_END_OF_SOURCE] for the end of the stream (in which case the index of any
     * unplayed postroll ad group will be returned).
     * @param periodDurationUs The duration of the containing timeline period, in microseconds, or
     * [C.TIME_UNSET] if not known.
     * @return The index of the ad group, or [C.INDEX_UNSET].
     */
    fun getAdGroupIndexForPositionUs(positionUs: Long, periodDurationUs: Long): Int {
        // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
        // In practice we expect there to be few ad groups so the search shouldn't be expensive.
        var index: Int = adGroupCount - 1
        while (index >= 0 && isPositionBeforeAdGroup(positionUs, periodDurationUs, index)) {
            index--
        }
        return if (index >= 0 && getAdGroup(index).hasUnplayedAds()) index else C.INDEX_UNSET
    }

    /**
     * Returns the index of the next ad group after `positionUs` that should be played. Returns
     * [C.INDEX_UNSET] if there is no such ad group.
     *
     * @param positionUs The period position after which to find an ad group, in microseconds, or
     * [C.TIME_END_OF_SOURCE] for the end of the stream (in which case there can be no ad
     * group after the position).
     * @param periodDurationUs The duration of the containing timeline period, in microseconds, or
     * [C.TIME_UNSET] if not known.
     * @return The index of the ad group, or [C.INDEX_UNSET].
     */
    fun getAdGroupIndexAfterPositionUs(positionUs: Long, periodDurationUs: Long): Int {
        if ((positionUs == C.TIME_END_OF_SOURCE
                        || (periodDurationUs != C.TIME_UNSET && positionUs >= periodDurationUs))) {
            return C.INDEX_UNSET
        }
        // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
        // In practice we expect there to be few ad groups so the search shouldn't be expensive.
        var index: Int = removedAdGroupCount
        while ((index < adGroupCount
                        && ((getAdGroup(index).timeUs != C.TIME_END_OF_SOURCE
                        && getAdGroup(index).timeUs <= positionUs)
                        || !getAdGroup(index).shouldPlayAdGroup()))) {
            index++
        }
        return if (index < adGroupCount) index else C.INDEX_UNSET
    }

    /** Returns whether the specified ad has been marked as in [.AD_STATE_ERROR].  */
    fun isAdInErrorState(
            @IntRange(from = 0) adGroupIndex: Int, @IntRange(from = 0) adIndexInAdGroup: Int): Boolean {
        if (adGroupIndex >= adGroupCount) {
            return false
        }
        val adGroup: AdGroup = getAdGroup(adGroupIndex)
        if (adGroup.count == C.LENGTH_UNSET || adIndexInAdGroup >= adGroup.count) {
            return false
        }
        return adGroup.states.get(adIndexInAdGroup) == AD_STATE_ERROR
    }

    /**
     * Returns an instance with the specified ad group time.
     *
     * @param adGroupIndex The index of the ad group.
     * @param adGroupTimeUs The new ad group time, in microseconds, or [C.TIME_END_OF_SOURCE] to
     * indicate a postroll ad.
     * @return The updated ad playback state.
     */
    @CheckResult
    fun withAdGroupTimeUs(
            @IntRange(from = 0) adGroupIndex: Int, adGroupTimeUs: Long): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups!!.size)
        adGroups!!.get(adjustedIndex) = this.adGroups.get(adjustedIndex)!!.withTimeUs(adGroupTimeUs)
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with a new ad group.
     *
     * @param adGroupIndex The insertion index of the new group.
     * @param adGroupTimeUs The ad group time, in microseconds, or [C.TIME_END_OF_SOURCE] to
     * indicate a postroll ad.
     * @return The updated ad playback state.
     */
    @CheckResult
    fun withNewAdGroup(@IntRange(from = 0) adGroupIndex: Int, adGroupTimeUs: Long): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        val newAdGroup: AdGroup = AdGroup(adGroupTimeUs)
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayAppend(adGroups, newAdGroup)
        System.arraycopy( /* src= */
                adGroups,  /* srcPos= */
                adjustedIndex,  /* dest= */
                adGroups,  /* destPos= */
                adjustedIndex + 1,  /* length= */
                this.adGroups!!.size - adjustedIndex)
        adGroups!!.get(adjustedIndex) = newAdGroup
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with the number of ads in `adGroupIndex` resolved to `adCount`.
     * The ad count must be greater than zero.
     */
    @CheckResult
    fun withAdCount(
            @IntRange(from = 0) adGroupIndex: Int, @IntRange(from = 1) adCount: Int): AdPlaybackState {
        Assertions.checkArgument(adCount > 0)
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        if (adGroups!!.get(adjustedIndex)!!.count == adCount) {
            return this
        }
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups.size)
        adGroups!!.get(adjustedIndex) = this.adGroups.get(adjustedIndex)!!.withAdCount(adCount)
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with the specified ad URI and the ad marked as [ ][.AD_STATE_AVAILABLE].
     *
     * @throws IllegalStateException If [Uri.EMPTY] is passed as argument for a client-side
     * inserted ad group.
     */
    @CheckResult
    fun withAvailableAdUri(
            @IntRange(from = 0) adGroupIndex: Int, @IntRange(from = 0) adIndexInAdGroup: Int, uri: Uri): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups!!.size)
        Assertions.checkState(!(Uri.EMPTY == uri) || adGroups!!.get(adjustedIndex)!!.isServerSideInserted)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withAdUri(uri, adIndexInAdGroup)
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with the specified ad marked as [available][.AD_STATE_AVAILABLE].
     *
     *
     * Must not be called with client side inserted ad groups. Client side inserted ads should use
     * [.withAvailableAdUri].
     *
     * @throws IllegalStateException in case this methods is called on an ad group that [     ][AdGroup.isServerSideInserted].
     */
    @CheckResult
    fun withAvailableAd(
            @IntRange(from = 0) adGroupIndex: Int, @IntRange(from = 0) adIndexInAdGroup: Int): AdPlaybackState {
        return withAvailableAdUri(adGroupIndex, adIndexInAdGroup, Uri.EMPTY)
    }

    /** Returns an instance with the specified ad marked as [played][.AD_STATE_PLAYED].  */
    @CheckResult
    fun withPlayedAd(
            @IntRange(from = 0) adGroupIndex: Int, @IntRange(from = 0) adIndexInAdGroup: Int): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups!!.size)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withAdState(AD_STATE_PLAYED, adIndexInAdGroup)
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /** Returns an instance with the specified ad marked as [skipped][.AD_STATE_SKIPPED].  */
    @CheckResult
    fun withSkippedAd(
            @IntRange(from = 0) adGroupIndex: Int, @IntRange(from = 0) adIndexInAdGroup: Int): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups!!.size)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withAdState(AD_STATE_SKIPPED, adIndexInAdGroup)
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /** Returns an instance with the last ad of the given ad group removed.  */
    @CheckResult
    fun withLastAdRemoved(@IntRange(from = 0) adGroupIndex: Int): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups!!.size)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withLastAdRemoved()
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with the specified ad marked [as having a load][.AD_STATE_ERROR].
     */
    @CheckResult
    fun withAdLoadError(
            @IntRange(from = 0) adGroupIndex: Int, @IntRange(from = 0) adIndexInAdGroup: Int): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups!!.size)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withAdState(AD_STATE_ERROR, adIndexInAdGroup)
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with all ads in the specified ad group skipped (except for those already
     * marked as played or in the error state).
     */
    @CheckResult
    fun withSkippedAdGroup(@IntRange(from = 0) adGroupIndex: Int): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups!!.size)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withAllAdsSkipped()
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with the specified ad durations, in microseconds.
     *
     *
     * Must only be used if [.removedAdGroupCount] is 0.
     */
    @CheckResult
    fun withAdDurationsUs(adDurationUs: Array<LongArray>): AdPlaybackState {
        Assertions.checkState(removedAdGroupCount == 0)
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups!!.size)
        for (adGroupIndex in 0 until adGroupCount) {
            adGroups!!.get(adGroupIndex) = adGroups.get(adGroupIndex)!!.withAdDurationsUs(adDurationUs.get(adGroupIndex))
        }
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with the specified ad durations, in microseconds, in the specified ad
     * group.
     */
    @CheckResult
    fun withAdDurationsUs(
            @IntRange(from = 0) adGroupIndex: Int, vararg adDurationsUs: Long): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups!!.size)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withAdDurationsUs(adDurationsUs)
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with the specified ad resume position, in microseconds, relative to the
     * start of the current ad.
     */
    @CheckResult
    fun withAdResumePositionUs(adResumePositionUs: Long): AdPlaybackState {
        if (this.adResumePositionUs == adResumePositionUs) {
            return this
        } else {
            return AdPlaybackState(
                    adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
        }
    }

    /** Returns an instance with the specified content duration, in microseconds.  */
    @CheckResult
    fun withContentDurationUs(contentDurationUs: Long): AdPlaybackState {
        if (this.contentDurationUs == contentDurationUs) {
            return this
        } else {
            return AdPlaybackState(
                    adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
        }
    }

    /**
     * Returns an instance with the specified number of [removed ad][.removedAdGroupCount].
     *
     *
     * Ad groups with indices between `0` (inclusive) and `removedAdGroupCount`
     * (exclusive) will be empty and must not be modified by any of the `with*` methods.
     */
    @CheckResult
    fun withRemovedAdGroupCount(@IntRange(from = 0) removedAdGroupCount: Int): AdPlaybackState {
        if (this.removedAdGroupCount == removedAdGroupCount) {
            return this
        } else {
            Assertions.checkArgument(removedAdGroupCount > this.removedAdGroupCount)
            val adGroups: Array<AdGroup?> = arrayOfNulls(adGroupCount - removedAdGroupCount)
            System.arraycopy( /* src= */
                    this.adGroups,  /* srcPos= */
                    removedAdGroupCount - this.removedAdGroupCount,  /* dest= */
                    adGroups,  /* destPos= */
                    0,  /* length= */
                    adGroups.size)
            return AdPlaybackState(
                    adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
        }
    }

    /**
     * Returns an instance with the specified [AdGroup.contentResumeOffsetUs], in microseconds,
     * for the specified ad group.
     */
    @CheckResult
    fun withContentResumeOffsetUs(
            @IntRange(from = 0) adGroupIndex: Int, contentResumeOffsetUs: Long): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        if (adGroups!!.get(adjustedIndex)!!.contentResumeOffsetUs == contentResumeOffsetUs) {
            return this
        }
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups.size)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withContentResumeOffsetUs(contentResumeOffsetUs)
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with the specified value for [AdGroup.originalCount] in the specified
     * ad group.
     */
    @CheckResult
    fun withOriginalAdCount(
            @IntRange(from = 0) adGroupIndex: Int, originalAdCount: Int): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        if (adGroups!!.get(adjustedIndex)!!.originalCount == originalAdCount) {
            return this
        }
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups.size)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withOriginalAdCount(originalAdCount)
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with the specified value for [AdGroup.isServerSideInserted] in the
     * specified ad group.
     */
    @CheckResult
    fun withIsServerSideInserted(
            @IntRange(from = 0) adGroupIndex: Int, isServerSideInserted: Boolean): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        if (adGroups!!.get(adjustedIndex)!!.isServerSideInserted == isServerSideInserted) {
            return this
        }
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups.size)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withIsServerSideInserted(isServerSideInserted)
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    /**
     * Returns an instance with all ads in the specified ad group reset from final states (played,
     * skipped, error) to either available or unavailable, which allows to play them again.
     */
    @CheckResult
    fun withResetAdGroup(@IntRange(from = 0) adGroupIndex: Int): AdPlaybackState {
        val adjustedIndex: Int = adGroupIndex - removedAdGroupCount
        val adGroups: Array<AdGroup?>? = Util.nullSafeArrayCopy(adGroups, adGroups!!.size)
        adGroups!!.get(adjustedIndex) = adGroups.get(adjustedIndex)!!.withAllAdsReset()
        return AdPlaybackState(
                adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
    }

    public override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that: AdPlaybackState = o as AdPlaybackState
        return (Util.areEqual(adsId, that.adsId)
                && (adGroupCount == that.adGroupCount
                ) && (adResumePositionUs == that.adResumePositionUs
                ) && (contentDurationUs == that.contentDurationUs
                ) && (removedAdGroupCount == that.removedAdGroupCount
                ) && Arrays.equals(adGroups, that.adGroups))
    }

    public override fun hashCode(): Int {
        var result: Int = adGroupCount
        result = 31 * result + (if (adsId == null) 0 else adsId.hashCode())
        result = 31 * result + adResumePositionUs.toInt()
        result = 31 * result + contentDurationUs.toInt()
        result = 31 * result + removedAdGroupCount
        result = 31 * result + Arrays.hashCode(adGroups)
        return result
    }

    public override fun toString(): String {
        val sb: StringBuilder = StringBuilder()
        sb.append("AdPlaybackState(adsId=")
        sb.append(adsId)
        sb.append(", adResumePositionUs=")
        sb.append(adResumePositionUs)
        sb.append(", adGroups=[")
        for (i in adGroups!!.indices) {
            sb.append("adGroup(timeUs=")
            sb.append(adGroups.get(i)!!.timeUs)
            sb.append(", ads=[")
            for (j in adGroups.get(i)!!.states.indices) {
                sb.append("ad(state=")
                when (adGroups.get(i)!!.states.get(j)) {
                    AD_STATE_UNAVAILABLE -> sb.append('_')
                    AD_STATE_ERROR -> sb.append('!')
                    AD_STATE_AVAILABLE -> sb.append('R')
                    AD_STATE_PLAYED -> sb.append('P')
                    AD_STATE_SKIPPED -> sb.append('S')
                    else -> sb.append('?')
                }
                sb.append(", durationUs=")
                sb.append(adGroups.get(i)!!.durationsUs.get(j))
                sb.append(')')
                if (j < adGroups.get(i)!!.states.size - 1) {
                    sb.append(", ")
                }
            }
            sb.append("])")
            if (i < adGroups.size - 1) {
                sb.append(", ")
            }
        }
        sb.append("])")
        return sb.toString()
    }

    private fun isPositionBeforeAdGroup(
            positionUs: Long, periodDurationUs: Long, adGroupIndex: Int): Boolean {
        if (positionUs == C.TIME_END_OF_SOURCE) {
            // The end of the content is at (but not before) any postroll ad, and after any other ads.
            return false
        }
        val adGroupPositionUs: Long = getAdGroup(adGroupIndex).timeUs
        if (adGroupPositionUs == C.TIME_END_OF_SOURCE) {
            return periodDurationUs == C.TIME_UNSET || positionUs < periodDurationUs
        } else {
            return positionUs < adGroupPositionUs
        }
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([FIELD_AD_GROUPS, FIELD_AD_RESUME_POSITION_US, FIELD_CONTENT_DURATION_US, FIELD_REMOVED_AD_GROUP_COUNT])
    private annotation class FieldNumber constructor()

    /**
     * {@inheritDoc}
     *
     *
     * It omits the [.adsId] field so the [.adsId] of instances restored by [ ][.CREATOR] will always be `null`.
     */
    // TODO(b/166765820): See if missing adsId would be okay and add adsId to the Bundle otherwise.
    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        val adGroupBundleList: ArrayList<Bundle> = ArrayList()
        for (adGroup: AdGroup? in adGroups!!) {
            adGroupBundleList.add(adGroup!!.toBundle())
        }
        bundle.putParcelableArrayList(keyForField(FIELD_AD_GROUPS), adGroupBundleList)
        bundle.putLong(keyForField(FIELD_AD_RESUME_POSITION_US), adResumePositionUs)
        bundle.putLong(keyForField(FIELD_CONTENT_DURATION_US), contentDurationUs)
        bundle.putInt(keyForField(FIELD_REMOVED_AD_GROUP_COUNT), removedAdGroupCount)
        return bundle
    }

    init {
        adGroupCount = adGroups!!.size + removedAdGroupCount
        this.adGroups = adGroups
        this.removedAdGroupCount = removedAdGroupCount
    }

    companion object {
        /** State for an ad that does not yet have a URL.  */
        val AD_STATE_UNAVAILABLE: Int = 0

        /** State for an ad that has a URL but has not yet been played.  */
        val AD_STATE_AVAILABLE: Int = 1

        /** State for an ad that was skipped.  */
        val AD_STATE_SKIPPED: Int = 2

        /** State for an ad that was played in full.  */
        val AD_STATE_PLAYED: Int = 3

        /** State for an ad that could not be loaded.  */
        val AD_STATE_ERROR: Int = 4

        /** Ad playback state with no ads.  */
        val NONE: AdPlaybackState = AdPlaybackState( /* adsId= */
                null, arrayOfNulls(0),  /* adResumePositionUs= */
                0L,  /* contentDurationUs= */
                C.TIME_UNSET,  /* removedAdGroupCount= */
                0)
        private val REMOVED_AD_GROUP: AdGroup = AdGroup( /* timeUs= */0).withAdCount(0)

        /**
         * Returns a copy of the ad playback state with the given ads ID.
         *
         * @param adsId The new ads ID.
         * @param adPlaybackState The ad playback state to copy.
         * @return The new ad playback state.
         */
        fun fromAdPlaybackState(adsId: Any?, adPlaybackState: AdPlaybackState): AdPlaybackState {
            val adGroups: Array<AdGroup?> = arrayOfNulls(adPlaybackState.adGroupCount - adPlaybackState.removedAdGroupCount)
            for (i in adGroups.indices) {
                val adGroup: AdGroup? = adPlaybackState.adGroups!!.get(i)
                adGroups.get(i) = AdGroup(
                        adGroup!!.timeUs,
                        adGroup.count,
                        adGroup.originalCount,
                        Arrays.copyOf(adGroup.states, adGroup.states.size),
                        Arrays.copyOf(adGroup.uris, adGroup.uris.size),
                        Arrays.copyOf(adGroup.durationsUs, adGroup.durationsUs.size),
                        adGroup.contentResumeOffsetUs,
                        adGroup.isServerSideInserted)
            }
            return AdPlaybackState(
                    adsId,
                    adGroups,
                    adPlaybackState.adResumePositionUs,
                    adPlaybackState.contentDurationUs,
                    adPlaybackState.removedAdGroupCount)
        }

        private val FIELD_AD_GROUPS: Int = 1
        private val FIELD_AD_RESUME_POSITION_US: Int = 2
        private val FIELD_CONTENT_DURATION_US: Int = 3
        private val FIELD_REMOVED_AD_GROUP_COUNT: Int = 4

        /**
         * Object that can restore [AdPlaybackState] from a [Bundle].
         *
         *
         * The [.adsId] of restored instances will always be `null`.
         */
        val CREATOR: Bundleable.Creator<AdPlaybackState> = Bundleable.Creator({ bundle: Bundle -> fromBundle(bundle) })
        private fun fromBundle(bundle: Bundle): AdPlaybackState {
            val adGroupBundleList: ArrayList<Bundle>? = bundle.getParcelableArrayList(keyForField(FIELD_AD_GROUPS))
            val adGroups: Array<AdGroup?>?
            if (adGroupBundleList == null) {
                adGroups = arrayOfNulls(0)
            } else {
                adGroups = arrayOfNulls(adGroupBundleList.size)
                for (i in adGroupBundleList.indices) {
                    adGroups.get(i) = AdGroup.CREATOR.fromBundle(adGroupBundleList.get(i))
                }
            }
            val adResumePositionUs: Long = bundle.getLong(keyForField(FIELD_AD_RESUME_POSITION_US),  /* defaultValue= */0)
            val contentDurationUs: Long = bundle.getLong(keyForField(FIELD_CONTENT_DURATION_US),  /* defaultValue= */C.TIME_UNSET)
            val removedAdGroupCount: Int = bundle.getInt(keyForField(FIELD_REMOVED_AD_GROUP_COUNT))
            return AdPlaybackState( /* adsId= */
                    null, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount)
        }

        private fun keyForField(field: @FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }

        private fun createEmptyAdGroups(adGroupTimesUs: LongArray): Array<AdGroup?> {
            val adGroups: Array<AdGroup?> = arrayOfNulls(adGroupTimesUs.size)
            for (i in adGroups.indices) {
                adGroups.get(i) = AdGroup(adGroupTimesUs.get(i))
            }
            return adGroups
        }
    }
}