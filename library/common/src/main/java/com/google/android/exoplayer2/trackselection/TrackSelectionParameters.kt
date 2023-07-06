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
package com.google.android.exoplayer2.trackselection

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.view.accessibility.CaptioningManager
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.*
import com.google.android.exoplayer2.C.RoleFlags
import com.google.android.exoplayer2.C.SelectionFlags
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.util.*
import com.google.android.exoplayer2.util.BundleableUtil.fromBundleList
import com.google.android.exoplayer2.util.Util.getCurrentDisplayModeSize
import com.google.android.exoplayer2.util.Util.getLocaleLanguageTag
import com.google.android.exoplayer2.util.Util.normalizeLanguageCode
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.primitives.Ints
import com.google.errorprone.annotations.CanIgnoreReturnValue
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import java.util.*

/**
 * Parameters for controlling track selection.
 *
 *
 * Parameters can be queried and set on a [Player]. For example the following code modifies
 * the parameters to restrict video track selections to SD, and to select a German audio track if
 * there is one:
 *
 * <pre>`// Build on the current parameters.
 * TrackSelectionParameters currentParameters = player.getTrackSelectionParameters()
 * // Build the resulting parameters.
 * TrackSelectionParameters newParameters = currentParameters
 * .buildUpon()
 * .setMaxVideoSizeSd()
 * .setPreferredAudioLanguage("deu")
 * .build();
 * // Set the new parameters.
 * player.setTrackSelectionParameters(newParameters);
`</pre> *
 */
open class TrackSelectionParameters : Bundleable {
    /**
     * A builder for [TrackSelectionParameters]. See the [TrackSelectionParameters]
     * documentation for explanations of the parameters that can be configured using this builder.
     */
    class Builder {
        // Video
        internal var maxVideoWidth = 0
        internal var maxVideoHeight = 0
        internal var maxVideoFrameRate = 0
        internal var maxVideoBitrate = 0
        internal var minVideoWidth = 0
        internal var minVideoHeight = 0
        internal var minVideoFrameRate = 0
        internal var minVideoBitrate = 0
        internal var viewportWidth = 0
        internal var viewportHeight = 0
        internal var viewportOrientationMayChange = false
        internal var preferredVideoMimeTypes: ImmutableList<String?>? = null

        @RoleFlags
        internal var preferredVideoRoleFlags = 0

        // Audio
        internal var preferredAudioLanguages: ImmutableList<String?>? = null

        @RoleFlags
        internal var preferredAudioRoleFlags = 0
        internal var maxAudioChannelCount = 0
        internal var maxAudioBitrate = 0
        internal var preferredAudioMimeTypes: ImmutableList<String?>? = null

        // Text
        internal var preferredTextLanguages: ImmutableList<String?>? = null

        @RoleFlags
        internal var preferredTextRoleFlags = 0

        @SelectionFlags
        internal var ignoredTextSelectionFlags = 0
        internal var selectUndeterminedTextLanguage = false

        // General
        internal var forceLowestBitrate = false
        internal var forceHighestSupportedBitrate = false
        internal var overrides: HashMap<TrackGroup?, TrackSelectionOverride>? = null
        internal var disabledTrackTypes: HashSet<Int?>? = null


        @Deprecated("{@link Context} constraints will not be set using this constructor. Use {@link     * #Builder(Context)} instead.")
        constructor() {
            // Video
            maxVideoWidth = Int.MAX_VALUE
            maxVideoHeight = Int.MAX_VALUE
            maxVideoFrameRate = Int.MAX_VALUE
            maxVideoBitrate = Int.MAX_VALUE
            viewportWidth = Int.MAX_VALUE
            viewportHeight = Int.MAX_VALUE
            viewportOrientationMayChange = true
            preferredVideoMimeTypes = ImmutableList.of()
            preferredVideoRoleFlags = 0
            // Audio
            preferredAudioLanguages = ImmutableList.of()
            preferredAudioRoleFlags = 0
            maxAudioChannelCount = Int.MAX_VALUE
            maxAudioBitrate = Int.MAX_VALUE
            preferredAudioMimeTypes = ImmutableList.of()
            // Text
            preferredTextLanguages = ImmutableList.of()
            preferredTextRoleFlags = 0
            ignoredTextSelectionFlags = 0
            selectUndeterminedTextLanguage = false
            // General
            forceLowestBitrate = false
            forceHighestSupportedBitrate = false
            overrides = HashMap()
            disabledTrackTypes = HashSet()
        }

        /**
         * Creates a builder with default initial values.
         *
         * @param context Any context.
         */
        // Methods invoked are setter only.
        constructor(context: Context) {
            Builder()
            setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(context)
            setViewportSizeToPhysicalDisplaySize(context,  /* viewportOrientationMayChange= */true)
        }

        /**
         * Creates a builder with the initial values specified in `initialValues`.
         */
        internal constructor (initialValues: TrackSelectionParameters) {
            init(initialValues)
        }

        /**
         * Creates a builder with the initial values specified in `bundle`.
         */
        internal constructor (bundle: Bundle) {
            // Video
            maxVideoWidth = bundle.getInt(keyForField(FIELD_MAX_VIDEO_WIDTH), DEFAULT_WITHOUT_CONTEXT!!.maxVideoWidth)
            maxVideoHeight = bundle.getInt(keyForField(FIELD_MAX_VIDEO_HEIGHT), DEFAULT_WITHOUT_CONTEXT.maxVideoHeight)
            maxVideoFrameRate = bundle.getInt(keyForField(FIELD_MAX_VIDEO_FRAMERATE), DEFAULT_WITHOUT_CONTEXT.maxVideoFrameRate)
            maxVideoBitrate = bundle.getInt(keyForField(FIELD_MAX_VIDEO_BITRATE), DEFAULT_WITHOUT_CONTEXT.maxVideoBitrate)
            minVideoWidth = bundle.getInt(keyForField(FIELD_MIN_VIDEO_WIDTH), DEFAULT_WITHOUT_CONTEXT.minVideoWidth)
            minVideoHeight = bundle.getInt(keyForField(FIELD_MIN_VIDEO_HEIGHT), DEFAULT_WITHOUT_CONTEXT.minVideoHeight)
            minVideoFrameRate = bundle.getInt(keyForField(FIELD_MIN_VIDEO_FRAMERATE), DEFAULT_WITHOUT_CONTEXT.minVideoFrameRate)
            minVideoBitrate = bundle.getInt(keyForField(FIELD_MIN_VIDEO_BITRATE), DEFAULT_WITHOUT_CONTEXT.minVideoBitrate)
            viewportWidth = bundle.getInt(keyForField(FIELD_VIEWPORT_WIDTH), DEFAULT_WITHOUT_CONTEXT.viewportWidth)
            viewportHeight = bundle.getInt(keyForField(FIELD_VIEWPORT_HEIGHT), DEFAULT_WITHOUT_CONTEXT.viewportHeight)
            viewportOrientationMayChange = bundle.getBoolean(keyForField(FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE), DEFAULT_WITHOUT_CONTEXT.viewportOrientationMayChange)
            preferredVideoMimeTypes = ImmutableList.copyOf<String?>(MoreObjects.firstNonNull<Array<String?>?>(bundle.getStringArray(keyForField(FIELD_PREFERRED_VIDEO_MIMETYPES)), arrayOfNulls(0)))
            preferredVideoRoleFlags = bundle.getInt(keyForField(FIELD_PREFERRED_VIDEO_ROLE_FLAGS), DEFAULT_WITHOUT_CONTEXT.preferredVideoRoleFlags)
            // Audio
            val preferredAudioLanguages1 = MoreObjects.firstNonNull<Array<String?>?>(bundle.getStringArray(keyForField(FIELD_PREFERRED_AUDIO_LANGUAGES)), arrayOfNulls(0))
            preferredAudioLanguages = normalizeLanguageCodes(preferredAudioLanguages1)
            preferredAudioRoleFlags = bundle.getInt(keyForField(FIELD_PREFERRED_AUDIO_ROLE_FLAGS), DEFAULT_WITHOUT_CONTEXT.preferredAudioRoleFlags)
            maxAudioChannelCount = bundle.getInt(keyForField(FIELD_MAX_AUDIO_CHANNEL_COUNT), DEFAULT_WITHOUT_CONTEXT.maxAudioChannelCount)
            maxAudioBitrate = bundle.getInt(keyForField(FIELD_MAX_AUDIO_BITRATE), DEFAULT_WITHOUT_CONTEXT.maxAudioBitrate)
            preferredAudioMimeTypes = ImmutableList.copyOf<String?>(MoreObjects.firstNonNull<Array<String?>?>(bundle.getStringArray(keyForField(FIELD_PREFERRED_AUDIO_MIME_TYPES)), arrayOfNulls(0)))
            // Text
            preferredTextLanguages = normalizeLanguageCodes(MoreObjects.firstNonNull(bundle.getStringArray(keyForField(FIELD_PREFERRED_TEXT_LANGUAGES)), arrayOfNulls(0)))
            preferredTextRoleFlags = bundle.getInt(keyForField(FIELD_PREFERRED_TEXT_ROLE_FLAGS), DEFAULT_WITHOUT_CONTEXT.preferredTextRoleFlags)
            ignoredTextSelectionFlags = bundle.getInt(keyForField(FIELD_IGNORED_TEXT_SELECTION_FLAGS), DEFAULT_WITHOUT_CONTEXT.ignoredTextSelectionFlags)
            selectUndeterminedTextLanguage = bundle.getBoolean(keyForField(FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE), DEFAULT_WITHOUT_CONTEXT.selectUndeterminedTextLanguage)
            // General
            forceLowestBitrate = bundle.getBoolean(keyForField(FIELD_FORCE_LOWEST_BITRATE), DEFAULT_WITHOUT_CONTEXT.forceLowestBitrate)
            forceHighestSupportedBitrate = bundle.getBoolean(keyForField(FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE), DEFAULT_WITHOUT_CONTEXT.forceHighestSupportedBitrate)
            val overrideBundleList: List<Bundle?>? = bundle.getParcelableArrayList<Bundle?>(keyForField(FIELD_SELECTION_OVERRIDES))
            val overrideList: List<TrackSelectionOverride> = if (overrideBundleList == null) ImmutableList.of() else fromBundleList(TrackSelectionOverride.CREATOR, overrideBundleList)!!
            overrides = HashMap()
            for (i in overrideList.indices) {
                val override = overrideList[i]
                overrides!![override.mediaTrackGroup] = override
            }
            val disabledTrackTypeArray = MoreObjects.firstNonNull(bundle.getIntArray(keyForField(FIELD_DISABLED_TRACK_TYPE)), IntArray(0))
            disabledTrackTypes = HashSet()
            for (disabledTrackType: @TrackType Int in disabledTrackTypeArray) {
                disabledTrackTypes!!.add(disabledTrackType)
            }
        }

        /**
         * Overrides the value of the builder with the value of [TrackSelectionParameters].
         */
        @EnsuresNonNull("preferredVideoMimeTypes", "preferredAudioLanguages", "preferredAudioMimeTypes", "preferredTextLanguages", "overrides", "disabledTrackTypes")
        private fun init(parameters: TrackSelectionParameters) {
            // Video
            maxVideoWidth = parameters.maxVideoWidth
            maxVideoHeight = parameters.maxVideoHeight
            maxVideoFrameRate = parameters.maxVideoFrameRate
            maxVideoBitrate = parameters.maxVideoBitrate
            minVideoWidth = parameters.minVideoWidth
            minVideoHeight = parameters.minVideoHeight
            minVideoFrameRate = parameters.minVideoFrameRate
            minVideoBitrate = parameters.minVideoBitrate
            viewportWidth = parameters.viewportWidth
            viewportHeight = parameters.viewportHeight
            viewportOrientationMayChange = parameters.viewportOrientationMayChange
            preferredVideoMimeTypes = parameters.preferredVideoMimeTypes
            preferredVideoRoleFlags = parameters.preferredVideoRoleFlags
            // Audio
            preferredAudioLanguages = parameters.preferredAudioLanguages
            preferredAudioRoleFlags = parameters.preferredAudioRoleFlags
            maxAudioChannelCount = parameters.maxAudioChannelCount
            maxAudioBitrate = parameters.maxAudioBitrate
            preferredAudioMimeTypes = parameters.preferredAudioMimeTypes
            // Text
            preferredTextLanguages = parameters.preferredTextLanguages
            preferredTextRoleFlags = parameters.preferredTextRoleFlags
            ignoredTextSelectionFlags = parameters.ignoredTextSelectionFlags
            selectUndeterminedTextLanguage = parameters.selectUndeterminedTextLanguage
            // General
            forceLowestBitrate = parameters.forceLowestBitrate
            forceHighestSupportedBitrate = parameters.forceHighestSupportedBitrate
            disabledTrackTypes = HashSet(parameters.disabledTrackTypes)
            overrides = HashMap(parameters.overrides)
        }

        /**
         * Overrides the value of the builder with the value of [TrackSelectionParameters].
         */
        @CanIgnoreReturnValue
        protected open fun set(parameters: TrackSelectionParameters): Builder? {
            init(parameters)
            return this
        }

        // Video

        // Video
        /**
         * Equivalent to [setMaxVideoSize(1279, 719)][.setMaxVideoSize].
         *
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxVideoSizeSd(): Builder? {
            return setMaxVideoSize(1279, 719)
        }

        /**
         * Equivalent to [setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)][.setMaxVideoSize].
         *
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun clearVideoSizeConstraints(): Builder? {
            return setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        }

        /**
         * Sets the maximum allowed video width and height.
         *
         * @param maxVideoWidth  Maximum allowed video width in pixels.
         * @param maxVideoHeight Maximum allowed video height in pixels.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxVideoSize(maxVideoWidth: Int, maxVideoHeight: Int): Builder? {
            this.maxVideoWidth = maxVideoWidth
            this.maxVideoHeight = maxVideoHeight
            return this
        }

        /**
         * Sets the maximum allowed video frame rate.
         *
         * @param maxVideoFrameRate Maximum allowed video frame rate in hertz.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxVideoFrameRate(maxVideoFrameRate: Int): Builder? {
            this.maxVideoFrameRate = maxVideoFrameRate
            return this
        }

        /**
         * Sets the maximum allowed video bitrate.
         *
         * @param maxVideoBitrate Maximum allowed video bitrate in bits per second.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxVideoBitrate(maxVideoBitrate: Int): Builder? {
            this.maxVideoBitrate = maxVideoBitrate
            return this
        }

        /**
         * Sets the minimum allowed video width and height.
         *
         * @param minVideoWidth  Minimum allowed video width in pixels.
         * @param minVideoHeight Minimum allowed video height in pixels.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMinVideoSize(minVideoWidth: Int, minVideoHeight: Int): Builder? {
            this.minVideoWidth = minVideoWidth
            this.minVideoHeight = minVideoHeight
            return this
        }

        /**
         * Sets the minimum allowed video frame rate.
         *
         * @param minVideoFrameRate Minimum allowed video frame rate in hertz.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMinVideoFrameRate(minVideoFrameRate: Int): Builder? {
            this.minVideoFrameRate = minVideoFrameRate
            return this
        }

        /**
         * Sets the minimum allowed video bitrate.
         *
         * @param minVideoBitrate Minimum allowed video bitrate in bits per second.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMinVideoBitrate(minVideoBitrate: Int): Builder? {
            this.minVideoBitrate = minVideoBitrate
            return this
        }

        /**
         * Equivalent to calling [.setViewportSize] with the viewport size
         * obtained from [Util.getCurrentDisplayModeSize].
         *
         * @param context                      Any context.
         * @param viewportOrientationMayChange Whether the viewport orientation may change during
         * playback.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setViewportSizeToPhysicalDisplaySize(context: Context?, viewportOrientationMayChange: Boolean): Builder? {
            // Assume the viewport is fullscreen.
            val viewportSize = getCurrentDisplayModeSize(context!!)
            return setViewportSize(viewportSize.x, viewportSize.y, viewportOrientationMayChange)
        }

        /**
         * Equivalent to [setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE,][.setViewportSize].
         *
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun clearViewportSizeConstraints(): Builder? {
            return setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, true)
        }

        /**
         * Sets the viewport size to constrain adaptive video selections so that only tracks suitable
         * for the viewport are selected.
         *
         * @param viewportWidth                Viewport width in pixels.
         * @param viewportHeight               Viewport height in pixels.
         * @param viewportOrientationMayChange Whether the viewport orientation may change during
         * playback.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setViewportSize(viewportWidth: Int, viewportHeight: Int, viewportOrientationMayChange: Boolean): Builder? {
            this.viewportWidth = viewportWidth
            this.viewportHeight = viewportHeight
            this.viewportOrientationMayChange = viewportOrientationMayChange
            return this
        }

        /**
         * Sets the preferred sample MIME type for video tracks.
         *
         * @param mimeType The preferred MIME type for video tracks, or `null` to clear a
         * previously set preference.
         * @return This builder.
         */
        open fun setPreferredVideoMimeType(mimeType: String?): Builder? {
            return mimeType?.let { setPreferredVideoMimeTypes(it) } ?: setPreferredVideoMimeTypes()
        }

        /**
         * Sets the preferred sample MIME types for video tracks.
         *
         * @param mimeTypes The preferred MIME types for video tracks in order of preference, or an
         * empty list for no preference.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredVideoMimeTypes(vararg mimeTypes: String?): Builder? {
            preferredVideoMimeTypes = ImmutableList.copyOf(mimeTypes)
            return this
        }

        /**
         * Sets the preferred [C.RoleFlags] for video tracks.
         *
         * @param preferredVideoRoleFlags Preferred video role flags.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredVideoRoleFlags(@RoleFlags preferredVideoRoleFlags: Int): Builder? {
            this.preferredVideoRoleFlags = preferredVideoRoleFlags
            return this
        }

        // Audio

        // Audio
        /**
         * Sets the preferred language for audio and forced text tracks.
         *
         * @param preferredAudioLanguage Preferred audio language as an IETF BCP 47 conformant tag, or
         * `null` to select the default track, or the first track if there's no default.
         * @return This builder.
         */
        open fun setPreferredAudioLanguage(preferredAudioLanguage: String?): Builder? {
            return preferredAudioLanguage?.let { setPreferredAudioLanguages(it) }
                    ?: setPreferredAudioLanguages()
        }

        /**
         * Sets the preferred languages for audio and forced text tracks.
         *
         * @param preferredAudioLanguages Preferred audio languages as IETF BCP 47 conformant tags in
         * order of preference, or an empty array to select the default track, or the first track if
         * there's no default.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredAudioLanguages(vararg preferredAudioLanguages: String?): Builder? {
            this.preferredAudioLanguages = normalizeLanguageCodes(preferredAudioLanguages)
            return this
        }

        /**
         * Sets the preferred [C.RoleFlags] for audio tracks.
         *
         * @param preferredAudioRoleFlags Preferred audio role flags.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredAudioRoleFlags(@RoleFlags preferredAudioRoleFlags: Int): Builder? {
            this.preferredAudioRoleFlags = preferredAudioRoleFlags
            return this
        }

        /**
         * Sets the maximum allowed audio channel count.
         *
         * @param maxAudioChannelCount Maximum allowed audio channel count.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxAudioChannelCount(maxAudioChannelCount: Int): Builder? {
            this.maxAudioChannelCount = maxAudioChannelCount
            return this
        }

        /**
         * Sets the maximum allowed audio bitrate.
         *
         * @param maxAudioBitrate Maximum allowed audio bitrate in bits per second.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setMaxAudioBitrate(maxAudioBitrate: Int): Builder? {
            this.maxAudioBitrate = maxAudioBitrate
            return this
        }

        /**
         * Sets the preferred sample MIME type for audio tracks.
         *
         * @param mimeType The preferred MIME type for audio tracks, or `null` to clear a
         * previously set preference.
         * @return This builder.
         */
        open fun setPreferredAudioMimeType(mimeType: String?): Builder? {
            return mimeType?.let { setPreferredAudioMimeTypes(it) } ?: setPreferredAudioMimeTypes()
        }

        /**
         * Sets the preferred sample MIME types for audio tracks.
         *
         * @param mimeTypes The preferred MIME types for audio tracks in order of preference, or an
         * empty list for no preference.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredAudioMimeTypes(vararg mimeTypes: String?): Builder? {
            preferredAudioMimeTypes = ImmutableList.copyOf(mimeTypes)
            return this
        }

        // Text

        // Text
        /**
         * Sets the preferred language and role flags for text tracks based on the accessibility
         * settings of [CaptioningManager].
         *
         *
         * Does nothing for API levels &lt; 19 or when the [CaptioningManager] is disabled.
         *
         * @param context A [Context].
         * @return This builder.
         */
        @SuppressLint("NewApi")
        @CanIgnoreReturnValue
        open fun setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(context: Context): Builder? {
            if (Util.SDK_INT >= 19) {
                setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettingsV19(context)
            }
            return this
        }

        /**
         * Sets the preferred language for text tracks.
         *
         * @param preferredTextLanguage Preferred text language as an IETF BCP 47 conformant tag, or
         * `null` to select the default track if there is one, or no track otherwise.
         * @return This builder.
         */
        open fun setPreferredTextLanguage(preferredTextLanguage: String?): Builder? {
            return preferredTextLanguage?.let { setPreferredTextLanguages(it) }
                    ?: setPreferredTextLanguages()
        }

        /**
         * Sets the preferred languages for text tracks.
         *
         * @param preferredTextLanguages Preferred text languages as IETF BCP 47 conformant tags in
         * order of preference, or an empty array to select the default track if there is one, or no
         * track otherwise.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredTextLanguages(vararg preferredTextLanguages: String?): Builder? {
            this.preferredTextLanguages = normalizeLanguageCodes(preferredTextLanguages)
            return this
        }

        /**
         * Sets the preferred [C.RoleFlags] for text tracks.
         *
         * @param preferredTextRoleFlags Preferred text role flags.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setPreferredTextRoleFlags(@RoleFlags preferredTextRoleFlags: Int): Builder? {
            this.preferredTextRoleFlags = preferredTextRoleFlags
            return this
        }

        /**
         * Sets a bitmask of selection flags that are ignored for text track selections.
         *
         * @param ignoredTextSelectionFlags A bitmask of [C.SelectionFlags] that are ignored for
         * text track selections.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setIgnoredTextSelectionFlags(@SelectionFlags ignoredTextSelectionFlags: Int): Builder? {
            this.ignoredTextSelectionFlags = ignoredTextSelectionFlags
            return this
        }

        /**
         * Sets whether a text track with undetermined language should be selected if no track with
         * [a preferred language][.setPreferredTextLanguages] is available, or if the
         * preferred language is unset.
         *
         * @param selectUndeterminedTextLanguage Whether a text track with undetermined language should
         * be selected if no preferred language track is available.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setSelectUndeterminedTextLanguage(selectUndeterminedTextLanguage: Boolean): Builder? {
            this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage
            return this
        }

        // General

        // General
        /**
         * Sets whether to force selection of the single lowest bitrate audio and video tracks that
         * comply with all other constraints.
         *
         * @param forceLowestBitrate Whether to force selection of the single lowest bitrate audio and
         * video tracks.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setForceLowestBitrate(forceLowestBitrate: Boolean): Builder? {
            this.forceLowestBitrate = forceLowestBitrate
            return this
        }

        /**
         * Sets whether to force selection of the highest bitrate audio and video tracks that comply
         * with all other constraints.
         *
         * @param forceHighestSupportedBitrate Whether to force selection of the highest bitrate audio
         * and video tracks.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setForceHighestSupportedBitrate(forceHighestSupportedBitrate: Boolean): Builder? {
            this.forceHighestSupportedBitrate = forceHighestSupportedBitrate
            return this
        }

        /**
         * Adds an override, replacing any override for the same [TrackGroup].
         */
        @CanIgnoreReturnValue
        open fun addOverride(override: TrackSelectionOverride): Builder? {
            overrides!![override.mediaTrackGroup] = override
            return this
        }

        /**
         * Sets an override, replacing all existing overrides with the same track type.
         */
        @CanIgnoreReturnValue
        open fun setOverrideForType(override: TrackSelectionOverride): Builder? {
            clearOverridesOfType(override.getType())
            overrides!![override.mediaTrackGroup] = override
            return this
        }

        /**
         * Removes the override for the provided media [TrackGroup], if there is one.
         */
        @CanIgnoreReturnValue
        open fun clearOverride(mediaTrackGroup: TrackGroup?): Builder? {
            overrides!!.remove(mediaTrackGroup)
            return this
        }

        /**
         * Removes all overrides of the provided track type.
         */
        @CanIgnoreReturnValue
        open fun clearOverridesOfType(trackType: @TrackType Int): Builder? {
            val it = overrides!!.values.iterator()
            while (it.hasNext()) {
                val override = it.next()
                if (override.getType() == trackType) {
                    it.remove()
                }
            }
            return this
        }

        /**
         * Removes all overrides.
         */
        @CanIgnoreReturnValue
        open fun clearOverrides(): Builder? {
            overrides!!.clear()
            return this
        }

        /**
         * Sets the disabled track types, preventing all tracks of those types from being selected for
         * playback. Any previously disabled track types are cleared.
         *
         * @param disabledTrackTypes The track types to disable.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setTrackTypeDisabled(int, boolean)}.")
        open fun setDisabledTrackTypes(disabledTrackTypes: Set<Int?>?): Builder? {
            this.disabledTrackTypes!!.clear()
            this.disabledTrackTypes!!.addAll(disabledTrackTypes!!)
            return this
        }

        /**
         * Sets whether a track type is disabled. If disabled, no tracks of the specified type will be
         * selected for playback.
         *
         * @param trackType The track type.
         * @param disabled  Whether the track type should be disabled.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        open fun setTrackTypeDisabled(trackType: @TrackType Int, disabled: Boolean): Builder? {
            if (disabled) {
                disabledTrackTypes!!.add(trackType)
            } else {
                disabledTrackTypes!!.remove(trackType)
            }
            return this
        }

        /**
         * Builds a [TrackSelectionParameters] instance with the selected values.
         */
        open fun build(): TrackSelectionParameters? {
            return TrackSelectionParameters(this)
        }

        @RequiresApi(19)
        private fun setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettingsV19(context: Context) {
            if (Util.SDK_INT < 23 && Looper.myLooper() == null) {
                // Android platform bug (pre-Marshmallow) that causes RuntimeExceptions when
                // CaptioningService is instantiated from a non-Looper thread. See [internal: b/143779904].
                return
            }
            val captioningManager = context.getSystemService(Context.CAPTIONING_SERVICE) as CaptioningManager
            if (captioningManager == null || !captioningManager.isEnabled) {
                return
            }
            preferredTextRoleFlags = C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND
            val preferredLocale = captioningManager.locale
            if (preferredLocale != null) {
                preferredTextLanguages = ImmutableList.of(getLocaleLanguageTag(preferredLocale))
            }
        }

        private fun normalizeLanguageCodes(preferredTextLanguages: Array<String?>): ImmutableList<String?>? {
            val listBuilder = ImmutableList.builder<String?>()
            for (language in checkNotNull(preferredTextLanguages)) {
                listBuilder.add(normalizeLanguageCode(checkNotNull(language)))
            }
            return listBuilder.build()
        }
    }

    /** Returns an instance configured with default values.  */
    open fun getDefaults(context: Context?): TrackSelectionParameters? {
        return Builder(context!!).build()
    }

    // Video
    // Video
    /**
     * Maximum allowed video width in pixels. The default value is [Integer.MAX_VALUE] (i.e. no
     * constraint).
     *
     *
     * To constrain adaptive video track selections to be suitable for a given viewport (the region
     * of the display within which video will be played), use ([.viewportWidth], [ ][.viewportHeight] and [.viewportOrientationMayChange]) instead.
     */
    var maxVideoWidth = 0

    /**
     * Maximum allowed video height in pixels. The default value is [Integer.MAX_VALUE] (i.e. no
     * constraint).
     *
     *
     * To constrain adaptive video track selections to be suitable for a given viewport (the region
     * of the display within which video will be played), use ([.viewportWidth], [ ][.viewportHeight] and [.viewportOrientationMayChange]) instead.
     */
    var maxVideoHeight = 0

    /**
     * Maximum allowed video frame rate in hertz. The default value is [Integer.MAX_VALUE] (i.e.
     * no constraint).
     */
    var maxVideoFrameRate = 0

    /**
     * Maximum allowed video bitrate in bits per second. The default value is [ ][Integer.MAX_VALUE] (i.e. no constraint).
     */
    var maxVideoBitrate = 0

    /** Minimum allowed video width in pixels. The default value is 0 (i.e. no constraint).  */
    var minVideoWidth = 0

    /** Minimum allowed video height in pixels. The default value is 0 (i.e. no constraint).  */
    var minVideoHeight = 0

    /** Minimum allowed video frame rate in hertz. The default value is 0 (i.e. no constraint).  */
    var minVideoFrameRate = 0

    /**
     * Minimum allowed video bitrate in bits per second. The default value is 0 (i.e. no constraint).
     */
    var minVideoBitrate = 0

    /**
     * Viewport width in pixels. Constrains video track selections for adaptive content so that only
     * tracks suitable for the viewport are selected. The default value is the physical width of the
     * primary display, in pixels.
     */
    var viewportWidth = 0

    /**
     * Viewport height in pixels. Constrains video track selections for adaptive content so that only
     * tracks suitable for the viewport are selected. The default value is the physical height of the
     * primary display, in pixels.
     */
    var viewportHeight = 0

    /**
     * Whether the viewport orientation may change during playback. Constrains video track selections
     * for adaptive content so that only tracks suitable for the viewport are selected. The default
     * value is `true`.
     */
    var viewportOrientationMayChange = false

    /**
     * The preferred sample MIME types for video tracks in order of preference, or an empty list for
     * no preference. The default is an empty list.
     */
    var preferredVideoMimeTypes: ImmutableList<String?>? = null

    /**
     * The preferred [C.RoleFlags] for video tracks. `0` selects the default track if
     * there is one, or the first track if there's no default. The default value is `0`.
     */
    @RoleFlags
    var preferredVideoRoleFlags = 0
    // Audio
    // Audio
    /**
     * The preferred languages for audio and forced text tracks as IETF BCP 47 conformant tags in
     * order of preference. An empty list selects the default track, or the first track if there's no
     * default. The default value is an empty list.
     */
    var preferredAudioLanguages: ImmutableList<String?>? = null

    /**
     * The preferred [C.RoleFlags] for audio tracks. `0` selects the default track if
     * there is one, or the first track if there's no default. The default value is `0`.
     */
    @RoleFlags
    var preferredAudioRoleFlags = 0

    /**
     * Maximum allowed audio channel count. The default value is [Integer.MAX_VALUE] (i.e. no
     * constraint).
     */
    var maxAudioChannelCount = 0

    /**
     * Maximum allowed audio bitrate in bits per second. The default value is [ ][Integer.MAX_VALUE] (i.e. no constraint).
     */
    var maxAudioBitrate = 0

    /**
     * The preferred sample MIME types for audio tracks in order of preference, or an empty list for
     * no preference. The default is an empty list.
     */
    var preferredAudioMimeTypes: ImmutableList<String?>? = null
    // Text
    // Text
    /**
     * The preferred languages for text tracks as IETF BCP 47 conformant tags in order of preference.
     * An empty list selects the default track if there is one, or no track otherwise. The default
     * value is an empty list, or the language of the accessibility [CaptioningManager] if
     * enabled.
     */
    var preferredTextLanguages: ImmutableList<String?>? = null

    /**
     * The preferred [C.RoleFlags] for text tracks. `0` selects the default track if there
     * is one, or no track otherwise. The default value is `0`, or [C.ROLE_FLAG_SUBTITLE]
     * | [C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND] if the accessibility [CaptioningManager]
     * is enabled.
     */
    @RoleFlags
    var preferredTextRoleFlags = 0

    /**
     * Bitmask of selection flags that are ignored for text track selections. See [ ]. The default value is `0` (i.e., no flags are ignored).
     */
    @SelectionFlags
    var ignoredTextSelectionFlags = 0

    /**
     * Whether a text track with undetermined language should be selected if no track with [ ][.preferredTextLanguages] is available, or if [.preferredTextLanguages] is unset. The
     * default value is `false`.
     */
    var selectUndeterminedTextLanguage = false
    // General
    // General
    /**
     * Whether to force selection of the single lowest bitrate audio and video tracks that comply with
     * all other constraints. The default value is `false`.
     */
    var forceLowestBitrate = false

    /**
     * Whether to force selection of the highest bitrate audio and video tracks that comply with all
     * other constraints. The default value is `false`.
     */
    var forceHighestSupportedBitrate = false

    /** Overrides to force selection of specific tracks.  */
    var overrides: ImmutableMap<TrackGroup, TrackSelectionOverride>? = null

    /**
     * The track types that are disabled. No track of a disabled type will be selected, thus no track
     * type contained in the set will be played. The default value is that no track type is disabled
     * (empty set).
     */
    var disabledTrackTypes: ImmutableSet<Int?>? = null

    protected constructor(builder: Builder) {
        // Video
        maxVideoWidth = builder.maxVideoWidth
        maxVideoHeight = builder.maxVideoHeight
        maxVideoFrameRate = builder.maxVideoFrameRate
        maxVideoBitrate = builder.maxVideoBitrate
        minVideoWidth = builder.minVideoWidth
        minVideoHeight = builder.minVideoHeight
        minVideoFrameRate = builder.minVideoFrameRate
        minVideoBitrate = builder.minVideoBitrate
        viewportWidth = builder.viewportWidth
        viewportHeight = builder.viewportHeight
        viewportOrientationMayChange = builder.viewportOrientationMayChange
        preferredVideoMimeTypes = builder.preferredVideoMimeTypes
        preferredVideoRoleFlags = builder.preferredVideoRoleFlags
        // Audio
        preferredAudioLanguages = builder.preferredAudioLanguages
        preferredAudioRoleFlags = builder.preferredAudioRoleFlags
        maxAudioChannelCount = builder.maxAudioChannelCount
        maxAudioBitrate = builder.maxAudioBitrate
        preferredAudioMimeTypes = builder.preferredAudioMimeTypes
        // Text
        preferredTextLanguages = builder.preferredTextLanguages
        preferredTextRoleFlags = builder.preferredTextRoleFlags
        ignoredTextSelectionFlags = builder.ignoredTextSelectionFlags
        selectUndeterminedTextLanguage = builder.selectUndeterminedTextLanguage
        // General
        forceLowestBitrate = builder.forceLowestBitrate
        forceHighestSupportedBitrate = builder.forceHighestSupportedBitrate
        overrides = ImmutableMap.copyOf(builder.overrides)
        disabledTrackTypes = ImmutableSet.copyOf<@TrackType Int?>(builder.disabledTrackTypes)
    }

    /** Creates a new [Builder], copying the initial values from this instance.  */
    open fun buildUpon(): Builder? {
        return Builder(this)
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val other = obj as TrackSelectionParameters
        // Video
        return maxVideoWidth == other.maxVideoWidth && maxVideoHeight == other.maxVideoHeight && maxVideoFrameRate == other.maxVideoFrameRate && maxVideoBitrate == other.maxVideoBitrate && minVideoWidth == other.minVideoWidth && minVideoHeight == other.minVideoHeight && minVideoFrameRate == other.minVideoFrameRate && minVideoBitrate == other.minVideoBitrate && viewportOrientationMayChange == other.viewportOrientationMayChange && viewportWidth == other.viewportWidth && viewportHeight == other.viewportHeight && preferredVideoMimeTypes == other.preferredVideoMimeTypes && preferredVideoRoleFlags == other.preferredVideoRoleFlags && preferredAudioLanguages == other.preferredAudioLanguages && preferredAudioRoleFlags == other.preferredAudioRoleFlags && maxAudioChannelCount == other.maxAudioChannelCount && maxAudioBitrate == other.maxAudioBitrate && preferredAudioMimeTypes == other.preferredAudioMimeTypes && preferredTextLanguages == other.preferredTextLanguages && preferredTextRoleFlags == other.preferredTextRoleFlags && ignoredTextSelectionFlags == other.ignoredTextSelectionFlags && selectUndeterminedTextLanguage == other.selectUndeterminedTextLanguage && forceLowestBitrate == other.forceLowestBitrate && forceHighestSupportedBitrate == other.forceHighestSupportedBitrate && overrides == other.overrides && disabledTrackTypes == other.disabledTrackTypes
    }

    override fun hashCode(): Int {
        var result = 1
        // Video
        result = 31 * result + maxVideoWidth
        result = 31 * result + maxVideoHeight
        result = 31 * result + maxVideoFrameRate
        result = 31 * result + maxVideoBitrate
        result = 31 * result + minVideoWidth
        result = 31 * result + minVideoHeight
        result = 31 * result + minVideoFrameRate
        result = 31 * result + minVideoBitrate
        result = 31 * result + if (viewportOrientationMayChange) 1 else 0
        result = 31 * result + viewportWidth
        result = 31 * result + viewportHeight
        result = 31 * result + preferredVideoMimeTypes.hashCode()
        result = 31 * result + preferredVideoRoleFlags
        // Audio
        result = 31 * result + preferredAudioLanguages.hashCode()
        result = 31 * result + preferredAudioRoleFlags
        result = 31 * result + maxAudioChannelCount
        result = 31 * result + maxAudioBitrate
        result = 31 * result + preferredAudioMimeTypes.hashCode()
        // Text
        result = 31 * result + preferredTextLanguages.hashCode()
        result = 31 * result + preferredTextRoleFlags
        result = 31 * result + ignoredTextSelectionFlags
        result = 31 * result + if (selectUndeterminedTextLanguage) 1 else 0
        // General
        result = 31 * result + if (forceLowestBitrate) 1 else 0
        result = 31 * result + if (forceHighestSupportedBitrate) 1 else 0
        result = 31 * result + overrides.hashCode()
        result = 31 * result + disabledTrackTypes.hashCode()
        return result
    }

    /**
     * Defines a minimum field ID value for subclasses to use when implementing [.toBundle]
     * and [Bundleable.Creator].
     *
     *
     * Subclasses should obtain keys for their [Bundle] representation by applying a
     * non-negative offset on this constant and passing the result to [.keyForField].
     */
    override fun toBundle(): Bundle {
        val bundle = Bundle()

        // Video
        bundle.putInt(keyForField(FIELD_MAX_VIDEO_WIDTH), maxVideoWidth)
        bundle.putInt(keyForField(FIELD_MAX_VIDEO_HEIGHT), maxVideoHeight)
        bundle.putInt(keyForField(FIELD_MAX_VIDEO_FRAMERATE), maxVideoFrameRate)
        bundle.putInt(keyForField(FIELD_MAX_VIDEO_BITRATE), maxVideoBitrate)
        bundle.putInt(keyForField(FIELD_MIN_VIDEO_WIDTH), minVideoWidth)
        bundle.putInt(keyForField(FIELD_MIN_VIDEO_HEIGHT), minVideoHeight)
        bundle.putInt(keyForField(FIELD_MIN_VIDEO_FRAMERATE), minVideoFrameRate)
        bundle.putInt(keyForField(FIELD_MIN_VIDEO_BITRATE), minVideoBitrate)
        bundle.putInt(keyForField(FIELD_VIEWPORT_WIDTH), viewportWidth)
        bundle.putInt(keyForField(FIELD_VIEWPORT_HEIGHT), viewportHeight)
        bundle.putBoolean(keyForField(FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE), viewportOrientationMayChange)
        bundle.putStringArray(keyForField(FIELD_PREFERRED_VIDEO_MIMETYPES), preferredVideoMimeTypes!!.toTypedArray())
        bundle.putInt(keyForField(FIELD_PREFERRED_VIDEO_ROLE_FLAGS), preferredVideoRoleFlags)
        // Audio
        bundle.putStringArray(keyForField(FIELD_PREFERRED_AUDIO_LANGUAGES), preferredAudioLanguages!!.toTypedArray())
        bundle.putInt(keyForField(FIELD_PREFERRED_AUDIO_ROLE_FLAGS), preferredAudioRoleFlags)
        bundle.putInt(keyForField(FIELD_MAX_AUDIO_CHANNEL_COUNT), maxAudioChannelCount)
        bundle.putInt(keyForField(FIELD_MAX_AUDIO_BITRATE), maxAudioBitrate)
        bundle.putStringArray(keyForField(FIELD_PREFERRED_AUDIO_MIME_TYPES), preferredAudioMimeTypes!!.toTypedArray())
        // Text
        bundle.putStringArray(keyForField(FIELD_PREFERRED_TEXT_LANGUAGES), preferredTextLanguages!!.toTypedArray())
        bundle.putInt(keyForField(FIELD_PREFERRED_TEXT_ROLE_FLAGS), preferredTextRoleFlags)
        bundle.putInt(keyForField(FIELD_IGNORED_TEXT_SELECTION_FLAGS), ignoredTextSelectionFlags)
        bundle.putBoolean(keyForField(FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE), selectUndeterminedTextLanguage)
        // General
        bundle.putBoolean(keyForField(FIELD_FORCE_LOWEST_BITRATE), forceLowestBitrate)
        bundle.putBoolean(keyForField(FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE), forceHighestSupportedBitrate)
        bundle.putParcelableArrayList(keyForField(FIELD_SELECTION_OVERRIDES), toBundleArrayList(overrides!!.values))
        bundle.putIntArray(keyForField(FIELD_DISABLED_TRACK_TYPE), Ints.toArray(disabledTrackTypes))
        return bundle
    }

    companion object {
        /**
         * An instance with default values, except those obtained from the [Context].
         *
         *
         * If possible, use [.getDefaults] instead.
         *
         *
         * This instance will not have the following settings:
         *
         *
         *  * [Viewport][Builder.setViewportSizeToPhysicalDisplaySize] configured for the primary display.
         *  * [       Preferred text language and role flags][Builder.setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings] configured to the accessibility settings of
         * [CaptioningManager].
         *
         */
        val DEFAULT_WITHOUT_CONTEXT: TrackSelectionParameters? = Builder().build()

        @Deprecated("This instance is not configured using {@link Context} constraints. Use {@link   *     #getDefaults(Context)} instead.")
        val DEFAULT = DEFAULT_WITHOUT_CONTEXT

        // Bundleable implementation
        private const val FIELD_PREFERRED_AUDIO_LANGUAGES = 1
        private const val FIELD_PREFERRED_AUDIO_ROLE_FLAGS = 2
        private const val FIELD_PREFERRED_TEXT_LANGUAGES = 3
        private const val FIELD_PREFERRED_TEXT_ROLE_FLAGS = 4
        private const val FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE = 5
        private const val FIELD_MAX_VIDEO_WIDTH = 6
        private const val FIELD_MAX_VIDEO_HEIGHT = 7
        private const val FIELD_MAX_VIDEO_FRAMERATE = 8
        private const val FIELD_MAX_VIDEO_BITRATE = 9
        private const val FIELD_MIN_VIDEO_WIDTH = 10
        private const val FIELD_MIN_VIDEO_HEIGHT = 11
        private const val FIELD_MIN_VIDEO_FRAMERATE = 12
        private const val FIELD_MIN_VIDEO_BITRATE = 13
        private const val FIELD_VIEWPORT_WIDTH = 14
        private const val FIELD_VIEWPORT_HEIGHT = 15
        private const val FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE = 16
        private const val FIELD_PREFERRED_VIDEO_MIMETYPES = 17
        private const val FIELD_MAX_AUDIO_CHANNEL_COUNT = 18
        private const val FIELD_MAX_AUDIO_BITRATE = 19
        private const val FIELD_PREFERRED_AUDIO_MIME_TYPES = 20
        private const val FIELD_FORCE_LOWEST_BITRATE = 21
        private const val FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE = 22
        private const val FIELD_SELECTION_OVERRIDES = 23
        private const val FIELD_DISABLED_TRACK_TYPE = 24
        private const val FIELD_PREFERRED_VIDEO_ROLE_FLAGS = 25
        private const val FIELD_IGNORED_TEXT_SELECTION_FLAGS = 26

        protected const val FIELD_CUSTOM_ID_BASE = 1000

        /** Construct an instance from a [Bundle] produced by [.toBundle].  */
        open fun fromBundle(bundle: Bundle?): TrackSelectionParameters? {
            return Builder(bundle!!).build()
        }


        @Deprecated("Use {@link #fromBundle(Bundle)} instead.")
        val CREATOR: Bundleable.Creator<TrackSelectionParameters> = Bundleable.Creator<TrackSelectionParameters> { bundle: Bundle? -> fromBundle(bundle) }

        /**
         * Converts the given field number to a string which can be used as a field key when implementing
         * [.toBundle] and [Bundleable.Creator].
         *
         *
         * Subclasses should use `field` values greater than or equal to [ ][.FIELD_CUSTOM_ID_BASE].
         */
        protected open fun keyForField(field: Int): String? {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}