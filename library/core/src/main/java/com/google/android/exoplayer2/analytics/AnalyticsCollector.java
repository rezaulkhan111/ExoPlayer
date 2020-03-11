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
package com.google.android.exoplayer2.analytics;

import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Data collector which is able to forward analytics events to {@link AnalyticsListener}s by
 * listening to all available ExoPlayer listeners.
 */
public class AnalyticsCollector
    implements Player.EventListener,
        MetadataOutput,
        AudioRendererEventListener,
        VideoRendererEventListener,
        MediaSourceEventListener,
        BandwidthMeter.EventListener,
        DrmSessionEventListener,
        VideoListener,
        AudioListener {

  private final CopyOnWriteArraySet<AnalyticsListener> listeners;
  private final Clock clock;
  private final Window window;
  private final MediaPeriodQueueTracker mediaPeriodQueueTracker;

  private @MonotonicNonNull Player player;
  private boolean isSeeking;

  /**
   * Creates an analytics collector.
   *
   * @param clock A {@link Clock} used to generate timestamps.
   */
  public AnalyticsCollector(Clock clock) {
    this.clock = Assertions.checkNotNull(clock);
    listeners = new CopyOnWriteArraySet<>();
    mediaPeriodQueueTracker = new MediaPeriodQueueTracker();
    window = new Window();
  }

  /**
   * Adds a listener for analytics events.
   *
   * @param listener The listener to add.
   */
  public void addListener(AnalyticsListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a previously added analytics event listener.
   *
   * @param listener The listener to remove.
   */
  public void removeListener(AnalyticsListener listener) {
    listeners.remove(listener);
  }

  /**
   * Sets the player for which data will be collected. Must only be called if no player has been set
   * yet or the current player is idle.
   *
   * @param player The {@link Player} for which data will be collected.
   */
  public void setPlayer(Player player) {
    Assertions.checkState(
        this.player == null || mediaPeriodQueueTracker.mediaPeriodInfoQueue.isEmpty());
    this.player = Assertions.checkNotNull(player);
  }

  // External events.

  /**
   * Notify analytics collector that a seek operation will start. Should be called before the player
   * adjusts its state and position to the seek.
   */
  public final void notifySeekStarted() {
    if (!isSeeking) {
      EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
      isSeeking = true;
      for (AnalyticsListener listener : listeners) {
        listener.onSeekStarted(eventTime);
      }
    }
  }

  /** Resets the analytics collector for a new playlist. */
  public final void resetForNewPlaylist() {
    // Copying the list is needed because onMediaPeriodReleased will modify the list.
    List<MediaPeriodInfo> mediaPeriodInfos =
        new ArrayList<>(mediaPeriodQueueTracker.mediaPeriodInfoQueue);
    for (MediaPeriodInfo mediaPeriodInfo : mediaPeriodInfos) {
      onMediaPeriodReleased(mediaPeriodInfo.windowIndex, mediaPeriodInfo.mediaPeriodId);
    }
  }

  // MetadataOutput implementation.

  @Override
  public final void onMetadata(Metadata metadata) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onMetadata(eventTime, metadata);
    }
  }

  // AudioRendererEventListener implementation.

  @Override
  public final void onAudioEnabled(DecoderCounters counters) {
    // The renderers are only enabled after we changed the playing media period.
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderEnabled(eventTime, C.TRACK_TYPE_AUDIO, counters);
    }
  }

  @Override
  public final void onAudioDecoderInitialized(
      String decoderName, long initializedTimestampMs, long initializationDurationMs) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderInitialized(
          eventTime, C.TRACK_TYPE_AUDIO, decoderName, initializationDurationMs);
    }
  }

  @Override
  public final void onAudioInputFormatChanged(Format format) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderInputFormatChanged(eventTime, C.TRACK_TYPE_AUDIO, format);
    }
  }

  @Override
  public final void onAudioSinkUnderrun(
      int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onAudioUnderrun(eventTime, bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }
  }

  @Override
  public final void onAudioDisabled(DecoderCounters counters) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderDisabled(eventTime, C.TRACK_TYPE_AUDIO, counters);
    }
  }

  // AudioListener implementation.

  @Override
  public final void onAudioSessionId(int audioSessionId) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onAudioSessionId(eventTime, audioSessionId);
    }
  }

  @Override
  public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onAudioAttributesChanged(eventTime, audioAttributes);
    }
  }

  @Override
  public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onSkipSilenceEnabledChanged(eventTime, skipSilenceEnabled);
    }
  }

  @Override
  public void onVolumeChanged(float audioVolume) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onVolumeChanged(eventTime, audioVolume);
    }
  }

  // VideoRendererEventListener implementation.

  @Override
  public final void onVideoEnabled(DecoderCounters counters) {
    // The renderers are only enabled after we changed the playing media period.
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderEnabled(eventTime, C.TRACK_TYPE_VIDEO, counters);
    }
  }

  @Override
  public final void onVideoDecoderInitialized(
      String decoderName, long initializedTimestampMs, long initializationDurationMs) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderInitialized(
          eventTime, C.TRACK_TYPE_VIDEO, decoderName, initializationDurationMs);
    }
  }

  @Override
  public final void onVideoInputFormatChanged(Format format) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderInputFormatChanged(eventTime, C.TRACK_TYPE_VIDEO, format);
    }
  }

  @Override
  public final void onDroppedFrames(int count, long elapsedMs) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDroppedVideoFrames(eventTime, count, elapsedMs);
    }
  }

  @Override
  public final void onVideoDisabled(DecoderCounters counters) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDecoderDisabled(eventTime, C.TRACK_TYPE_VIDEO, counters);
    }
  }

  @Override
  public final void onRenderedFirstFrame(@Nullable Surface surface) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onRenderedFirstFrame(eventTime, surface);
    }
  }

  @Override
  public final void onVideoFrameProcessingOffset(
      long totalProcessingOffsetUs, int frameCount, Format format) {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onVideoFrameProcessingOffset(eventTime, totalProcessingOffsetUs, frameCount, format);
    }
  }

  // VideoListener implementation.

  @Override
  public final void onRenderedFirstFrame() {
    // Do nothing. Already reported in VideoRendererEventListener.onRenderedFirstFrame.
  }

  @Override
  public final void onVideoSizeChanged(
      int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onVideoSizeChanged(
          eventTime, width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
    }
  }

  @Override
  public void onSurfaceSizeChanged(int width, int height) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onSurfaceSizeChanged(eventTime, width, height);
    }
  }

  // MediaSourceEventListener implementation.

  @Override
  public final void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId) {
    mediaPeriodQueueTracker.onMediaPeriodCreated(
        windowIndex, mediaPeriodId, Assertions.checkNotNull(player));
    EventTime eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onMediaPeriodCreated(eventTime);
    }
  }

  @Override
  public final void onMediaPeriodReleased(int windowIndex, MediaPeriodId mediaPeriodId) {
    EventTime eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
    if (mediaPeriodQueueTracker.onMediaPeriodReleased(
        mediaPeriodId, Assertions.checkNotNull(player))) {
      for (AnalyticsListener listener : listeners) {
        listener.onMediaPeriodReleased(eventTime);
      }
    }
  }

  @Override
  public final void onLoadStarted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    EventTime eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onLoadStarted(eventTime, loadEventInfo, mediaLoadData);
    }
  }

  @Override
  public final void onLoadCompleted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    EventTime eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onLoadCompleted(eventTime, loadEventInfo, mediaLoadData);
    }
  }

  @Override
  public final void onLoadCanceled(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    EventTime eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onLoadCanceled(eventTime, loadEventInfo, mediaLoadData);
    }
  }

  @Override
  public final void onLoadError(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {
    EventTime eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onLoadError(eventTime, loadEventInfo, mediaLoadData, error, wasCanceled);
    }
  }

  @Override
  public final void onReadingStarted(int windowIndex, MediaPeriodId mediaPeriodId) {
    mediaPeriodQueueTracker.onReadingStarted(mediaPeriodId);
    EventTime eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onReadingStarted(eventTime);
    }
  }

  @Override
  public final void onUpstreamDiscarded(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
    EventTime eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onUpstreamDiscarded(eventTime, mediaLoadData);
    }
  }

  @Override
  public final void onDownstreamFormatChanged(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
    EventTime eventTime = generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
    for (AnalyticsListener listener : listeners) {
      listener.onDownstreamFormatChanged(eventTime, mediaLoadData);
    }
  }

  // Player.EventListener implementation.

  // TODO: Add onFinishedReportingChanges to Player.EventListener to know when a set of simultaneous
  // callbacks finished. This helps to assign exactly the same EventTime to all of them instead of
  // having slightly different real times.

  @Override
  public final void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
    mediaPeriodQueueTracker.onTimelineChanged(timeline, Assertions.checkNotNull(player));
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onTimelineChanged(eventTime, reason);
    }
  }

  @Override
  public final void onTracksChanged(
      TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onTracksChanged(eventTime, trackGroups, trackSelections);
    }
  }

  @Override
  public final void onIsLoadingChanged(boolean isLoading) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onIsLoadingChanged(eventTime, isLoading);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public final void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPlayerStateChanged(eventTime, playWhenReady, playbackState);
    }
  }

  @Override
  public final void onPlaybackStateChanged(@Player.State int state) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPlaybackStateChanged(eventTime, state);
    }
  }

  @Override
  public final void onPlayWhenReadyChanged(
      boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPlayWhenReadyChanged(eventTime, playWhenReady, reason);
    }
  }

  @Override
  public void onPlaybackSuppressionReasonChanged(
      @PlaybackSuppressionReason int playbackSuppressionReason) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPlaybackSuppressionReasonChanged(eventTime, playbackSuppressionReason);
    }
  }

  @Override
  public void onIsPlayingChanged(boolean isPlaying) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onIsPlayingChanged(eventTime, isPlaying);
    }
  }

  @Override
  public final void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onRepeatModeChanged(eventTime, repeatMode);
    }
  }

  @Override
  public final void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onShuffleModeChanged(eventTime, shuffleModeEnabled);
    }
  }

  @Override
  public final void onPlayerError(ExoPlaybackException error) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPlayerError(eventTime, error);
    }
  }

  @Override
  public final void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    mediaPeriodQueueTracker.onPositionDiscontinuity(Assertions.checkNotNull(player));
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPositionDiscontinuity(eventTime, reason);
    }
  }

  /**
   * @deprecated Use {@link #onPlaybackSpeedChanged(float)} and {@link
   *     #onSkipSilenceEnabledChanged(boolean)} instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public final void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPlaybackParametersChanged(eventTime, playbackParameters);
    }
  }

  @Override
  public void onPlaybackSpeedChanged(float playbackSpeed) {
    EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onPlaybackSpeedChanged(eventTime, playbackSpeed);
    }
  }

  @Override
  public final void onSeekProcessed() {
    if (isSeeking) {
      isSeeking = false;
      EventTime eventTime = generateCurrentPlayerMediaPeriodEventTime();
      for (AnalyticsListener listener : listeners) {
        listener.onSeekProcessed(eventTime);
      }
    }
  }

  // BandwidthMeter.Listener implementation.

  @Override
  public final void onBandwidthSample(int elapsedMs, long bytes, long bitrate) {
    EventTime eventTime = generateLoadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onBandwidthEstimate(eventTime, elapsedMs, bytes, bitrate);
    }
  }

  // DefaultDrmSessionManager.EventListener implementation.

  @Override
  public final void onDrmSessionAcquired() {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDrmSessionAcquired(eventTime);
    }
  }

  @Override
  public final void onDrmKeysLoaded() {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDrmKeysLoaded(eventTime);
    }
  }

  @Override
  public final void onDrmSessionManagerError(Exception error) {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDrmSessionManagerError(eventTime, error);
    }
  }

  @Override
  public final void onDrmKeysRestored() {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDrmKeysRestored(eventTime);
    }
  }

  @Override
  public final void onDrmKeysRemoved() {
    EventTime eventTime = generateReadingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDrmKeysRemoved(eventTime);
    }
  }

  @Override
  public final void onDrmSessionReleased() {
    EventTime eventTime = generatePlayingMediaPeriodEventTime();
    for (AnalyticsListener listener : listeners) {
      listener.onDrmSessionReleased(eventTime);
    }
  }

  // Internal methods.

  /** Returns read-only set of registered listeners. */
  protected Set<AnalyticsListener> getListeners() {
    return Collections.unmodifiableSet(listeners);
  }

  /** Returns a new {@link EventTime} for the specified timeline, window and media period id. */
  @RequiresNonNull("player")
  protected EventTime generateEventTime(
      Timeline timeline, int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
    if (timeline.isEmpty()) {
      // Ensure media period id is only reported together with a valid timeline.
      mediaPeriodId = null;
    }
    long realtimeMs = clock.elapsedRealtime();
    long eventPositionMs;
    boolean isInCurrentWindow =
        timeline.equals(player.getCurrentTimeline())
            && windowIndex == player.getCurrentWindowIndex();
    if (mediaPeriodId != null && mediaPeriodId.isAd()) {
      boolean isCurrentAd =
          isInCurrentWindow
              && player.getCurrentAdGroupIndex() == mediaPeriodId.adGroupIndex
              && player.getCurrentAdIndexInAdGroup() == mediaPeriodId.adIndexInAdGroup;
      // Assume start position of 0 for future ads.
      eventPositionMs = isCurrentAd ? player.getCurrentPosition() : 0;
    } else if (isInCurrentWindow) {
      eventPositionMs = player.getContentPosition();
    } else {
      // Assume default start position for future content windows. If timeline is not available yet,
      // assume start position of 0.
      eventPositionMs =
          timeline.isEmpty() ? 0 : timeline.getWindow(windowIndex, window).getDefaultPositionMs();
    }
    return new EventTime(
        realtimeMs,
        timeline,
        windowIndex,
        mediaPeriodId,
        eventPositionMs,
        player.getCurrentPosition(),
        player.getTotalBufferedDuration());
  }

  private EventTime generateEventTime(@Nullable MediaPeriodInfo mediaPeriodInfo) {
    Assertions.checkNotNull(player);
    if (mediaPeriodInfo == null) {
      int windowIndex = player.getCurrentWindowIndex();
      Timeline timeline = player.getCurrentTimeline();
      boolean windowIsInTimeline = windowIndex < timeline.getWindowCount();
      return generateEventTime(
          windowIsInTimeline ? timeline : Timeline.EMPTY, windowIndex, /* mediaPeriodId= */ null);
    }
    return generateEventTime(
        mediaPeriodInfo.timeline, mediaPeriodInfo.windowIndex, mediaPeriodInfo.mediaPeriodId);
  }

  private EventTime generateCurrentPlayerMediaPeriodEventTime() {
    return generateEventTime(mediaPeriodQueueTracker.getCurrentPlayerMediaPeriod());
  }

  private EventTime generatePlayingMediaPeriodEventTime() {
    return generateEventTime(mediaPeriodQueueTracker.getPlayingMediaPeriod());
  }

  private EventTime generateReadingMediaPeriodEventTime() {
    return generateEventTime(mediaPeriodQueueTracker.getReadingMediaPeriod());
  }

  private EventTime generateLoadingMediaPeriodEventTime() {
    return generateEventTime(mediaPeriodQueueTracker.getLoadingMediaPeriod());
  }

  private EventTime generateMediaPeriodEventTime(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
    Assertions.checkNotNull(player);
    if (mediaPeriodId != null) {
      MediaPeriodInfo mediaPeriodInfo = mediaPeriodQueueTracker.getMediaPeriodInfo(mediaPeriodId);
      return mediaPeriodInfo != null
          ? generateEventTime(mediaPeriodInfo)
          : generateEventTime(Timeline.EMPTY, windowIndex, mediaPeriodId);
    }
    Timeline timeline = player.getCurrentTimeline();
    boolean windowIsInTimeline = windowIndex < timeline.getWindowCount();
    return generateEventTime(
        windowIsInTimeline ? timeline : Timeline.EMPTY, windowIndex, /* mediaPeriodId= */ null);
  }

  /** Keeps track of the active media periods and currently playing and reading media period. */
  private static final class MediaPeriodQueueTracker {

    // TODO: Investigate reporting MediaPeriodId in renderer events and adding a listener of queue
    // changes, which would hopefully remove the need to track the queue here.

    private final ArrayList<MediaPeriodInfo> mediaPeriodInfoQueue;
    private final HashMap<MediaPeriodId, MediaPeriodInfo> mediaPeriodIdToInfo;
    private final Period period;

    @Nullable private MediaPeriodInfo currentPlayerMediaPeriod;
    private @MonotonicNonNull MediaPeriodInfo playingMediaPeriod;
    @Nullable private MediaPeriodInfo readingMediaPeriod;
    private Timeline timeline;

    public MediaPeriodQueueTracker() {
      mediaPeriodInfoQueue = new ArrayList<>();
      mediaPeriodIdToInfo = new HashMap<>();
      period = new Period();
      timeline = Timeline.EMPTY;
    }

    /**
     * Returns the {@link MediaPeriodInfo} of the media period corresponding the current position of
     * the player.
     *
     * <p>May be null if no matching media period has been created yet.
     */
    @Nullable
    public MediaPeriodInfo getCurrentPlayerMediaPeriod() {
      return currentPlayerMediaPeriod;
    }

    /**
     * Returns the {@link MediaPeriodInfo} of the media period at the front of the queue. If the
     * queue is empty, this is the last media period which was at the front of the queue.
     *
     * <p>May be null, if no media period has been created yet.
     */
    @Nullable
    public MediaPeriodInfo getPlayingMediaPeriod() {
      return playingMediaPeriod;
    }

    /**
     * Returns the {@link MediaPeriodInfo} of the media period currently being read by the player.
     *
     * <p>May be null, if the player is not reading a media period.
     */
    @Nullable
    public MediaPeriodInfo getReadingMediaPeriod() {
      return readingMediaPeriod;
    }

    /**
     * Returns the {@link MediaPeriodInfo} of the media period at the end of the queue which is
     * currently loading or will be the next one loading.
     *
     * <p>May be null, if no media period is active yet.
     */
    @Nullable
    public MediaPeriodInfo getLoadingMediaPeriod() {
      return mediaPeriodInfoQueue.isEmpty()
          ? null
          : mediaPeriodInfoQueue.get(mediaPeriodInfoQueue.size() - 1);
    }

    /** Returns the {@link MediaPeriodInfo} for the given {@link MediaPeriodId}. */
    @Nullable
    public MediaPeriodInfo getMediaPeriodInfo(MediaPeriodId mediaPeriodId) {
      return mediaPeriodIdToInfo.get(mediaPeriodId);
    }

    /** Updates the queue with a reported position discontinuity. */
    public void onPositionDiscontinuity(Player player) {
      currentPlayerMediaPeriod = findMatchingMediaPeriodInQueue(player);
    }

    /** Updates the queue with a reported timeline change. */
    public void onTimelineChanged(Timeline timeline, Player player) {
      for (int i = 0; i < mediaPeriodInfoQueue.size(); i++) {
        MediaPeriodInfo newMediaPeriodInfo =
            updateMediaPeriodInfoToNewTimeline(mediaPeriodInfoQueue.get(i), timeline);
        mediaPeriodInfoQueue.set(i, newMediaPeriodInfo);
        mediaPeriodIdToInfo.put(newMediaPeriodInfo.mediaPeriodId, newMediaPeriodInfo);
      }
      if (readingMediaPeriod != null) {
        readingMediaPeriod = updateMediaPeriodInfoToNewTimeline(readingMediaPeriod, timeline);
      }
      if (!mediaPeriodInfoQueue.isEmpty()) {
        playingMediaPeriod = mediaPeriodInfoQueue.get(0);
      } else if (playingMediaPeriod != null) {
        playingMediaPeriod = updateMediaPeriodInfoToNewTimeline(playingMediaPeriod, timeline);
      }
      this.timeline = timeline;
      currentPlayerMediaPeriod = findMatchingMediaPeriodInQueue(player);
    }

    /** Updates the queue with a newly created media period. */
    public void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId, Player player) {
      int periodIndex = timeline.getIndexOfPeriod(mediaPeriodId.periodUid);
      boolean isInTimeline = periodIndex != C.INDEX_UNSET;
      MediaPeriodInfo mediaPeriodInfo =
          new MediaPeriodInfo(
              mediaPeriodId,
              isInTimeline ? timeline : Timeline.EMPTY,
              isInTimeline ? timeline.getPeriod(periodIndex, period).windowIndex : windowIndex);
      mediaPeriodInfoQueue.add(mediaPeriodInfo);
      mediaPeriodIdToInfo.put(mediaPeriodId, mediaPeriodInfo);
      playingMediaPeriod = mediaPeriodInfoQueue.get(0);
      if (currentPlayerMediaPeriod == null && isMatchingPlayingMediaPeriod(player)) {
        currentPlayerMediaPeriod = playingMediaPeriod;
      }
    }

    /**
     * Updates the queue with a released media period. Returns whether the media period was still in
     * the queue.
     */
    public boolean onMediaPeriodReleased(MediaPeriodId mediaPeriodId, Player player) {
      @Nullable MediaPeriodInfo mediaPeriodInfo = mediaPeriodIdToInfo.remove(mediaPeriodId);
      if (mediaPeriodInfo == null) {
        // The media period has already been removed from the queue in resetForNewPlaylist().
        return false;
      }
      mediaPeriodInfoQueue.remove(mediaPeriodInfo);
      if (readingMediaPeriod != null && mediaPeriodId.equals(readingMediaPeriod.mediaPeriodId)) {
        readingMediaPeriod = mediaPeriodInfoQueue.isEmpty() ? null : mediaPeriodInfoQueue.get(0);
      }
      if (!mediaPeriodInfoQueue.isEmpty()) {
        playingMediaPeriod = mediaPeriodInfoQueue.get(0);
      }
      if (currentPlayerMediaPeriod == null && isMatchingPlayingMediaPeriod(player)) {
        currentPlayerMediaPeriod = playingMediaPeriod;
      }
      return true;
    }

    /** Update the queue with a change in the reading media period. */
    public void onReadingStarted(MediaPeriodId mediaPeriodId) {
      readingMediaPeriod = mediaPeriodIdToInfo.get(mediaPeriodId);
    }

    @Nullable
    private MediaPeriodInfo findMatchingMediaPeriodInQueue(Player player) {
      Timeline playerTimeline = player.getCurrentTimeline();
      int playerPeriodIndex = player.getCurrentPeriodIndex();
      @Nullable
      Object playerPeriodUid =
          playerTimeline.isEmpty() ? null : playerTimeline.getUidOfPeriod(playerPeriodIndex);
      int playerNextAdGroupIndex =
          player.isPlayingAd() || playerTimeline.isEmpty()
              ? C.INDEX_UNSET
              : playerTimeline
                  .getPeriod(playerPeriodIndex, period)
                  .getAdGroupIndexAfterPositionUs(
                      C.msToUs(player.getCurrentPosition()) - period.getPositionInWindowUs());
      for (int i = 0; i < mediaPeriodInfoQueue.size(); i++) {
        MediaPeriodInfo mediaPeriodInfo = mediaPeriodInfoQueue.get(i);
        if (isMatchingMediaPeriod(
            mediaPeriodInfo,
            playerTimeline,
            player.getCurrentWindowIndex(),
            playerPeriodUid,
            player.isPlayingAd(),
            player.getCurrentAdGroupIndex(),
            player.getCurrentAdIndexInAdGroup(),
            playerNextAdGroupIndex)) {
          return mediaPeriodInfo;
        }
      }
      if (mediaPeriodInfoQueue.isEmpty() && playingMediaPeriod != null) {
        if (isMatchingMediaPeriod(
            playingMediaPeriod,
            playerTimeline,
            player.getCurrentWindowIndex(),
            playerPeriodUid,
            player.isPlayingAd(),
            player.getCurrentAdGroupIndex(),
            player.getCurrentAdIndexInAdGroup(),
            playerNextAdGroupIndex)) {
          return playingMediaPeriod;
        }
      }
      return null;
    }

    private boolean isMatchingPlayingMediaPeriod(Player player) {
      if (playingMediaPeriod == null) {
        return false;
      }
      Timeline playerTimeline = player.getCurrentTimeline();
      int playerPeriodIndex = player.getCurrentPeriodIndex();
      @Nullable
      Object playerPeriodUid =
          playerTimeline.isEmpty() ? null : playerTimeline.getUidOfPeriod(playerPeriodIndex);
      int playerNextAdGroupIndex =
          player.isPlayingAd() || playerTimeline.isEmpty()
              ? C.INDEX_UNSET
              : playerTimeline
                  .getPeriod(playerPeriodIndex, period)
                  .getAdGroupIndexAfterPositionUs(
                      C.msToUs(player.getCurrentPosition()) - period.getPositionInWindowUs());
      return isMatchingMediaPeriod(
          playingMediaPeriod,
          playerTimeline,
          player.getCurrentWindowIndex(),
          playerPeriodUid,
          player.isPlayingAd(),
          player.getCurrentAdGroupIndex(),
          player.getCurrentAdIndexInAdGroup(),
          playerNextAdGroupIndex);
    }

    private static boolean isMatchingMediaPeriod(
        MediaPeriodInfo mediaPeriodInfo,
        Timeline playerTimeline,
        int playerWindowIndex,
        @Nullable Object playerPeriodUid,
        boolean isPlayingAd,
        int playerAdGroupIndex,
        int playerAdIndexInAdGroup,
        int playerNextAdGroupIndex) {
      if (mediaPeriodInfo.timeline.isEmpty()
          || !mediaPeriodInfo.timeline.equals(playerTimeline)
          || mediaPeriodInfo.windowIndex != playerWindowIndex
          || !mediaPeriodInfo.mediaPeriodId.periodUid.equals(playerPeriodUid)) {
        return false;
      }
      // Timeline period matches. Still need to check ad information.
      return (isPlayingAd
              && mediaPeriodInfo.mediaPeriodId.adGroupIndex == playerAdGroupIndex
              && mediaPeriodInfo.mediaPeriodId.adIndexInAdGroup == playerAdIndexInAdGroup)
          || (!isPlayingAd
              && mediaPeriodInfo.mediaPeriodId.adGroupIndex == C.INDEX_UNSET
              && mediaPeriodInfo.mediaPeriodId.nextAdGroupIndex == playerNextAdGroupIndex);
    }

    private MediaPeriodInfo updateMediaPeriodInfoToNewTimeline(
        MediaPeriodInfo info, Timeline newTimeline) {
      int newPeriodIndex = newTimeline.getIndexOfPeriod(info.mediaPeriodId.periodUid);
      if (newPeriodIndex == C.INDEX_UNSET) {
        // Media period is not yet or no longer available in the new timeline. Keep it as it is.
        return info;
      }
      int newWindowIndex = newTimeline.getPeriod(newPeriodIndex, period).windowIndex;
      return new MediaPeriodInfo(info.mediaPeriodId, newTimeline, newWindowIndex);
    }
  }

  /** Information about a media period and its associated timeline. */
  private static final class MediaPeriodInfo {

    /** The {@link MediaPeriodId} of the media period. */
    public final MediaPeriodId mediaPeriodId;
    /**
     * The {@link Timeline} in which the media period can be found. Or {@link Timeline#EMPTY} if the
     * media period is not part of a known timeline yet.
     */
    public final Timeline timeline;
    /**
     * The window index of the media period in the timeline. If the timeline is empty, this is the
     * prospective window index.
     */
    public final int windowIndex;

    public MediaPeriodInfo(MediaPeriodId mediaPeriodId, Timeline timeline, int windowIndex) {
      this.mediaPeriodId = mediaPeriodId;
      this.timeline = timeline;
      this.windowIndex = windowIndex;
    }
  }
}
