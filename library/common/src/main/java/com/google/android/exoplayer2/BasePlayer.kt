/*
 * Copyright (C) 2018 The Android Open Source Project
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


import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.util.*
import com.google.common.collect.ImmutableList
import com.google.errorprone.annotations.ForOverride

/** Abstract base [Player] which implements common implementation independent methods.  */
abstract class BasePlayer: Player {

    protected val window: Timeline.Window

    init {
        window = Timeline.Window()
    }

    public override fun setMediaItem(mediaItem: MediaItem?) {
        setMediaItems(ImmutableList.of(mediaItem))
    }

    public override fun setMediaItem(mediaItem: MediaItem?, startPositionMs: Long) {
        setMediaItems(ImmutableList.of(mediaItem),  /* startIndex= */0, startPositionMs)
    }

    public override fun setMediaItem(mediaItem: MediaItem?, resetPosition: Boolean) {
        setMediaItems(ImmutableList.of(mediaItem), resetPosition)
    }

    public override fun setMediaItems(mediaItems: List<MediaItem?>?) {
        setMediaItems(mediaItems,  /* resetPosition= */true)
    }

    public override fun addMediaItem(index: Int, mediaItem: MediaItem?) {
        addMediaItems(index, ImmutableList.of(mediaItem))
    }

    public override fun addMediaItem(mediaItem: MediaItem?) {
        addMediaItems(ImmutableList.of(mediaItem))
    }

    public override fun addMediaItems(mediaItems: List<MediaItem?>?) {
        addMediaItems(Int.MAX_VALUE, mediaItems)
    }

    public override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        if (currentIndex != newIndex) {
            moveMediaItems( /* fromIndex= */currentIndex,  /* toIndex= */currentIndex + 1, newIndex)
        }
    }

    public override fun removeMediaItem(index: Int) {
        removeMediaItems( /* fromIndex= */index,  /* toIndex= */index + 1)
    }

    public override fun clearMediaItems() {
        removeMediaItems( /* fromIndex= */0, Int.MAX_VALUE)
    }

    public override fun isCommandAvailable(command: @Player.Command Int): Boolean {
        return getAvailableCommands().contains(command)
    }

    /**
     * {@inheritDoc}
     *
     *
     * BasePlayer and its descendants will return `true`.
     */
    public override fun canAdvertiseSession(): Boolean {
        return true
    }

    public override fun play() {
        setPlayWhenReady(true)
    }

    public override fun pause() {
        setPlayWhenReady(false)
    }

    override val isPlaying: Boolean
        get() {
            return ((getPlaybackState() == Player.Companion.STATE_READY) && getPlayWhenReady() && (getPlaybackSuppressionReason() == Player.Companion.PLAYBACK_SUPPRESSION_REASON_NONE))
        }

    public override fun seekToDefaultPosition() {
        seekToDefaultPosition(getCurrentMediaItemIndex())
    }

    public override fun seekToDefaultPosition(mediaItemIndex: Int) {
        seekTo(mediaItemIndex,  /* positionMs= */C.TIME_UNSET)
    }

    public override fun seekTo(positionMs: Long) {
        seekTo(getCurrentMediaItemIndex(), positionMs)
    }

    public override fun seekBack() {
        seekToOffset(-getSeekBackIncrement())
    }

    public override fun seekForward() {
        seekToOffset(getSeekForwardIncrement())
    }

    @Deprecated("Use {@link #hasPreviousMediaItem()} instead.")
    public override fun hasPrevious(): Boolean {
        return hasPreviousMediaItem()
    }

    @Deprecated("Use {@link #hasPreviousMediaItem()} instead.")
    public override fun hasPreviousWindow(): Boolean {
        return hasPreviousMediaItem()
    }

    public override fun hasPreviousMediaItem(): Boolean {
        return previousMediaItemIndex != C.INDEX_UNSET
    }

    @Deprecated("Use {@link #seekToPreviousMediaItem()} instead.")
    public override fun previous() {
        seekToPreviousMediaItem()
    }

    @Deprecated("Use {@link #seekToPreviousMediaItem()} instead.")
    public override fun seekToPreviousWindow() {
        seekToPreviousMediaItem()
    }

    public override fun seekToPreviousMediaItem() {
        val previousMediaItemIndex: Int = previousMediaItemIndex
        if (previousMediaItemIndex == C.INDEX_UNSET) {
            return
        }
        if (previousMediaItemIndex == getCurrentMediaItemIndex()) {
            repeatCurrentMediaItem()
        } else {
            seekToDefaultPosition(previousMediaItemIndex)
        }
    }

    public override fun seekToPrevious() {
        val timeline: Timeline? = getCurrentTimeline()
        if (timeline.isEmpty() || isPlayingAd()) {
            return
        }
        val hasPreviousMediaItem: Boolean = hasPreviousMediaItem()
        if (isCurrentMediaItemLive && !isCurrentMediaItemSeekable) {
            if (hasPreviousMediaItem) {
                seekToPreviousMediaItem()
            }
        } else if (hasPreviousMediaItem && getCurrentPosition() <= getMaxSeekToPreviousPosition()) {
            seekToPreviousMediaItem()
        } else {
            seekTo( /* positionMs= */0)
        }
    }

    @Deprecated("Use {@link #hasNextMediaItem()} instead.")
    public override fun hasNext(): Boolean {
        return hasNextMediaItem()
    }

    @Deprecated("Use {@link #hasNextMediaItem()} instead.")
    public override fun hasNextWindow(): Boolean {
        return hasNextMediaItem()
    }

    public override fun hasNextMediaItem(): Boolean {
        return nextMediaItemIndex != C.INDEX_UNSET
    }

    @Deprecated("Use {@link #seekToNextMediaItem()} instead.")
    public override fun next() {
        seekToNextMediaItem()
    }

    @Deprecated("Use {@link #seekToNextMediaItem()} instead.")
    public override fun seekToNextWindow() {
        seekToNextMediaItem()
    }

    public override fun seekToNextMediaItem() {
        val nextMediaItemIndex: Int = nextMediaItemIndex
        if (nextMediaItemIndex == C.INDEX_UNSET) {
            return
        }
        if (nextMediaItemIndex == getCurrentMediaItemIndex()) {
            repeatCurrentMediaItem()
        } else {
            seekToDefaultPosition(nextMediaItemIndex)
        }
    }

    public override fun seekToNext() {
        val timeline: Timeline? = getCurrentTimeline()
        if (timeline.isEmpty() || isPlayingAd()) {
            return
        }
        if (hasNextMediaItem()) {
            seekToNextMediaItem()
        } else if (isCurrentMediaItemLive && isCurrentMediaItemDynamic) {
            seekToDefaultPosition()
        }
    }

    public override fun setPlaybackSpeed(speed: Float) {
        setPlaybackParameters(getPlaybackParameters().withSpeed(speed))
    }

    @get:Deprecated("Use {@link #getCurrentMediaItemIndex()} instead.")
    override val currentWindowIndex: Int
        get() {
            return getCurrentMediaItemIndex()
        }

    @get:Deprecated("Use {@link #getNextMediaItemIndex()} instead.")
    override val nextWindowIndex: Int
        get() {
            return nextMediaItemIndex
        }
    override val nextMediaItemIndex: Int
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return if (timeline.isEmpty()) C.INDEX_UNSET else timeline!!.getNextWindowIndex(getCurrentMediaItemIndex(), repeatModeForNavigation, getShuffleModeEnabled())
        }

    @get:Deprecated("Use {@link #getPreviousMediaItemIndex()} instead.")
    override val previousWindowIndex: Int
        get() {
            return previousMediaItemIndex
        }
    override val previousMediaItemIndex: Int
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return if (timeline.isEmpty()) C.INDEX_UNSET else timeline!!.getPreviousWindowIndex(getCurrentMediaItemIndex(), repeatModeForNavigation, getShuffleModeEnabled())
        }
    override val currentMediaItem: MediaItem?
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return if (timeline.isEmpty()) null else timeline!!.getWindow(getCurrentMediaItemIndex(), window).mediaItem
        }
    override val mediaItemCount: Int
        get() {
            return getCurrentTimeline().getWindowCount()
        }

    public override fun getMediaItemAt(index: Int): MediaItem? {
        return getCurrentTimeline().getWindow(index, window).mediaItem
    }

    override val currentManifest: Any?
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return if (timeline.isEmpty()) null else timeline!!.getWindow(getCurrentMediaItemIndex(), window).manifest
        }
    override val bufferedPercentage: Int
        get() {
            val position: Long = getBufferedPosition()
            val duration: Long = getDuration()
            return if (position == C.TIME_UNSET || duration == C.TIME_UNSET) 0 else if (duration == 0L) 100 else Util.constrainValue(((position * 100) / duration).toInt(), 0, 100)
        }

    @get:Deprecated("Use {@link #isCurrentMediaItemDynamic()} instead.")
    override val isCurrentWindowDynamic: Boolean
        get() {
            return isCurrentMediaItemDynamic
        }
    override val isCurrentMediaItemDynamic: Boolean
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return !timeline.isEmpty() && timeline!!.getWindow(getCurrentMediaItemIndex(), window).isDynamic
        }

    @get:Deprecated("Use {@link #isCurrentMediaItemLive()} instead.")
    override val isCurrentWindowLive: Boolean
        get() {
            return isCurrentMediaItemLive
        }
    override val isCurrentMediaItemLive: Boolean
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return !timeline.isEmpty() && timeline!!.getWindow(getCurrentMediaItemIndex(), window).isLive()
        }
    override val currentLiveOffset: Long
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            if (timeline.isEmpty()) {
                return C.TIME_UNSET
            }
            val windowStartTimeMs: Long = timeline!!.getWindow(getCurrentMediaItemIndex(), window).windowStartTimeMs
            if (windowStartTimeMs == C.TIME_UNSET) {
                return C.TIME_UNSET
            }
            return window.getCurrentUnixTimeMs() - window.windowStartTimeMs - getContentPosition()
        }

    @get:Deprecated("Use {@link #isCurrentMediaItemSeekable()} instead.")
    override val isCurrentWindowSeekable: Boolean
        get() {
            return isCurrentMediaItemSeekable
        }
    override val isCurrentMediaItemSeekable: Boolean
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return !timeline.isEmpty() && timeline!!.getWindow(getCurrentMediaItemIndex(), window).isSeekable
        }
    override val contentDuration: Long
        get() {
            val timeline: Timeline? = getCurrentTimeline()
            return if (timeline.isEmpty()) C.TIME_UNSET else timeline!!.getWindow(getCurrentMediaItemIndex(), window).getDurationMs()
        }

    /**
     * Repeat the current media item.
     *
     *
     * The default implementation seeks to the default position in the current item, which can be
     * overridden for additional handling.
     */
    @ForOverride
    protected open fun repeatCurrentMediaItem() {
        seekToDefaultPosition()
    }

    private val repeatModeForNavigation: @Player.RepeatMode Int
        private get() {
            val repeatMode: @Player.RepeatMode Int = getRepeatMode()
            return if (repeatMode == Player.Companion.REPEAT_MODE_ONE) Player.Companion.REPEAT_MODE_OFF else repeatMode
        }

    private fun seekToOffset(offsetMs: Long) {
        var positionMs: Long = getCurrentPosition() + offsetMs
        val durationMs: Long = getDuration()
        if (durationMs != C.TIME_UNSET) {
            positionMs = Math.min(positionMs, durationMs)
        }
        positionMs = Math.max(positionMs, 0)
        seekTo(positionMs)
    }
}