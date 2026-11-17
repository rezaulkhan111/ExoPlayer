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
package com.google.android.exoplayer2.offline

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.SparseIntArray
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.RendererCapabilities
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.audio.AudioRendererEventListener
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.metadata.MetadataOutput
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaPeriod
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId
import com.google.android.exoplayer2.source.MediaSource.MediaSourceCaller
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.chunk.MediaChunk
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.text.TextOutput
import com.google.android.exoplayer2.trackselection.BaseTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride
import com.google.android.exoplayer2.trackselection.ExoTrackSelection
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.trackselection.TrackSelectionUtil
import com.google.android.exoplayer2.trackselection.TrackSelectorResult
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.upstream.BandwidthMeter
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoRendererEventListener
import java.io.IOException
import java.util.Collections

/**
 * A helper for initializing and removing downloads.
 *
 * The helper extracts track information from the media, selects tracks for downloading, and
 * creates [download requests][DownloadRequest] based on the selected tracks.
 *
 * A typical usage of DownloadHelper follows these steps:
 * 1. Build the helper using one of the `forMediaItem` methods.
 * 2. Prepare the helper using [prepare] and wait for the callback.
 * 3. Optional: Inspect the selected tracks using [getMappedTrackInfo] and [getTrackSelections],
 *    and make adjustments using [clearTrackSelections], [replaceTrackSelections] and
 *    [addTrackSelection].
 * 4. Create a download request for the selected track using [getDownloadRequest].
 * 5. Release the helper using [release].
 */
class DownloadHelper(
    mediaItem: MediaItem,
    private val mediaSource: MediaSource?,
    trackSelectionParameters: TrackSelectionParameters,
    private val rendererCapabilities: Array<RendererCapabilities>
) {

  /** A callback to be notified when the [DownloadHelper] is prepared. */
  interface Callback {
    /**
     * Called when preparation completes.
     *
     * @param helper The reporting [DownloadHelper].
     */
    fun onPrepared(helper: DownloadHelper)

    /**
     * Called when preparation fails.
     *
     * @param helper The reporting [DownloadHelper].
     * @param e The error.
     */
    fun onPrepareError(helper: DownloadHelper, e: IOException)
  }

  /** Thrown at an attempt to download live content. */
  class LiveContentUnsupportedException : IOException()

  private val localConfiguration: MediaItem.LocalConfiguration =
      checkNotNull(mediaItem.localConfiguration)!!
  private val trackSelector: DefaultTrackSelector =
      DefaultTrackSelector(trackSelectionParameters, DownloadTrackSelection.Factory())
  private val scratchSet: SparseIntArray = SparseIntArray()
  private val callbackHandler: Handler = Util.createHandlerForCurrentOrMainLooper()
  private val window: Timeline.Window = Timeline.Window()

  private var isPreparedWithMedia: Boolean = false
  private var callback: Callback? = null
  private var mediaPreparer: MediaPreparer? = null
  private var trackGroupArrays: Array<TrackGroupArray>? = null
  private var mappedTrackInfos: Array<MappedTrackInfo>? = null
  private var trackSelectionsByPeriodAndRenderer: Array<Array<MutableList<ExoTrackSelection>>>? = null
  private var immutableTrackSelectionsByPeriodAndRenderer: Array<Array<List<ExoTrackSelection>>>? = null

  init {
    trackSelector.init({ }, FakeBandwidthMeter())
  }

  /**
   * Initializes the helper for starting a download.
   *
   * @param callback A callback to be notified when preparation completes or fails.
   * @throws IllegalStateException If the download helper has already been prepared.
   */
  fun prepare(callback: Callback) {
    Assertions.checkState(this.callback == null)
    this.callback = callback
    if (mediaSource != null) {
      mediaPreparer = MediaPreparer(mediaSource, this)
    } else {
      callbackHandler.post { callback.onPrepared(this) }
    }
  }

  /** Releases the helper and all resources it is holding. */
  fun release() {
    mediaPreparer?.release()
    trackSelector.release()
  }

  /**
   * Returns the manifest, or null if no manifest is loaded. Must not be called until after
   * preparation completes.
   */
  fun getManifest(): Any? {
    if (mediaSource == null) {
      return null
    }
    assertPreparedWithMedia()
    val mediaPreparer = checkNotNull(this.mediaPreparer)!!
    val timeline = checkNotNull(mediaPreparer.timeline)!!
    return if (timeline.windowCount > 0) {
      timeline.getWindow(/* windowIndex= */ 0, window).manifest
    } else {
      null
    }
  }

  /**
   * Returns the number of periods for which media is available. Must not be called until after
   * preparation completes.
   */
  fun getPeriodCount(): Int {
    if (mediaSource == null) {
      return 0
    }
    assertPreparedWithMedia()
    return checkNotNull(trackGroupArrays)!!.size
  }

  /**
   * Returns [Tracks] for the given period. Must not be called until after preparation completes.
   *
   * @param periodIndex The period index.
   * @return The [Tracks] for the period. May be [Tracks.EMPTY] for single stream content.
   */
  fun getTracks(periodIndex: Int): Tracks {
    assertPreparedWithMedia()
    return TrackSelectionUtil.buildTracks(
        checkNotNull(mappedTrackInfos)!![periodIndex],
        checkNotNull(immutableTrackSelectionsByPeriodAndRenderer)!![periodIndex]
    )
  }

  /**
   * Returns the track groups for the given period. Must not be called until after preparation
   * completes.
   *
   * Use [getMappedTrackInfo] to get the track groups mapped to renderers.
   *
   * @param periodIndex The period index.
   * @return The track groups for the period. May be [TrackGroupArray.EMPTY] for single stream
   *     content.
   */
  fun getTrackGroups(periodIndex: Int): TrackGroupArray {
    assertPreparedWithMedia()
    return checkNotNull(trackGroupArrays)!![periodIndex]
  }

  /**
   * Returns the mapped track info for the given period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index.
   * @return The [MappedTrackInfo] for the period.
   */
  fun getMappedTrackInfo(periodIndex: Int): MappedTrackInfo {
    assertPreparedWithMedia()
    return checkNotNull(mappedTrackInfos)!![periodIndex]
  }

  /**
   * Returns all [ExoTrackSelection track selections] for a period and renderer. Must not be called
   * until after preparation completes.
   *
   * @param periodIndex The period index.
   * @param rendererIndex The renderer index.
   * @return A list of selected [ExoTrackSelection track selections].
   */
  fun getTrackSelections(periodIndex: Int, rendererIndex: Int): List<ExoTrackSelection> {
    assertPreparedWithMedia()
    return checkNotNull(immutableTrackSelectionsByPeriodAndRenderer)!![periodIndex][rendererIndex]
  }

  /**
   * Clears the selection of tracks for a period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index for which track selections are cleared.
   */
  fun clearTrackSelections(periodIndex: Int) {
    assertPreparedWithMedia()
    val selections = checkNotNull(trackSelectionsByPeriodAndRenderer)!!
    for (i in rendererCapabilities.indices) {
      selections[periodIndex][i].clear()
    }
  }

  /**
   * Replaces a selection of tracks to be downloaded. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index for which the track selection is replaced.
   * @param trackSelectionParameters The [TrackSelectionParameters] to obtain the new selection of
   *     tracks.
   */
  fun replaceTrackSelections(
      periodIndex: Int,
      trackSelectionParameters: TrackSelectionParameters
  ) {
    try {
      assertPreparedWithMedia()
      clearTrackSelections(periodIndex)
      addTrackSelectionInternal(periodIndex, trackSelectionParameters)
    } catch (e: ExoPlaybackException) {
      throw IllegalStateException(e)
    }
  }

  /**
   * Adds a selection of tracks to be downloaded. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index this track selection is added for.
   * @param trackSelectionParameters The [TrackSelectionParameters] to obtain the new selection of
   *     tracks.
   */
  fun addTrackSelection(
      periodIndex: Int,
      trackSelectionParameters: TrackSelectionParameters
  ) {
    try {
      assertPreparedWithMedia()
      addTrackSelectionInternal(periodIndex, trackSelectionParameters)
    } catch (e: ExoPlaybackException) {
      throw IllegalStateException(e)
    }
  }

  /**
   * Convenience method to add selections of tracks for all specified audio languages. If an audio
   * track in one of the specified languages is not available, the default fallback audio track is
   * used instead. Must not be called until after preparation completes.
   *
   * @param languages A list of audio languages for which tracks should be added to the download
   *     selection, as IETF BCP 47 conformant tags.
   */
  fun addAudioLanguagesToSelection(vararg languages: String) {
    try {
      assertPreparedWithMedia()

      val parametersBuilder = DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT.buildUpon()!!
      // Prefer highest supported bitrate for downloads.
      parametersBuilder.setForceHighestSupportedBitrate(true)
      // Disable all non-audio track types supported by the renderers.
      for (capabilities in rendererCapabilities) {
        val trackType = capabilities.getTrackType()
        parametersBuilder.setTrackTypeDisabled(
            trackType, /* disabled= */ trackType != C.TRACK_TYPE_AUDIO
        )
      }

      // Add a track selection to each period for each of the languages.
      val periodCount = getPeriodCount()
      for (language in languages) {
        val parameters = parametersBuilder.setPreferredAudioLanguage(language)!!.build()!!
        for (periodIndex in 0 until periodCount) {
          addTrackSelectionInternal(periodIndex, parameters)
        }
      }
    } catch (e: ExoPlaybackException) {
      throw IllegalStateException(e)
    }
  }

  /**
   * Convenience method to add selections of tracks for all specified text languages. Must not be
   * called until after preparation completes.
   *
   * @param selectUndeterminedTextLanguage Whether a text track with undetermined language should be
   *     selected for downloading if no track with one of the specified [languages] is available.
   * @param languages A list of text languages for which tracks should be added to the download
   *     selection, as IETF BCP 47 conformant tags.
   */
  fun addTextLanguagesToSelection(
      selectUndeterminedTextLanguage: Boolean,
      vararg languages: String
  ) {
    try {
      assertPreparedWithMedia()

      val parametersBuilder = DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT.buildUpon()!!
      parametersBuilder.setSelectUndeterminedTextLanguage(selectUndeterminedTextLanguage)
      // Prefer highest supported bitrate for downloads.
      parametersBuilder.setForceHighestSupportedBitrate(true)
      // Disable all non-text track types supported by the renderers.
      for (capabilities in rendererCapabilities) {
        val trackType = capabilities.getTrackType()
        parametersBuilder.setTrackTypeDisabled(
            trackType, /* disabled= */ trackType != C.TRACK_TYPE_TEXT
        )
      }

      // Add a track selection to each period for each of the languages.
      val periodCount = getPeriodCount()
      for (language in languages) {
        val parameters = parametersBuilder.setPreferredTextLanguage(language)!!.build()!!
        for (periodIndex in 0 until periodCount) {
          addTrackSelectionInternal(periodIndex, parameters)
        }
      }
    } catch (e: ExoPlaybackException) {
      throw IllegalStateException(e)
    }
  }

  /**
   * Convenience method to add a selection of tracks to be downloaded for a single renderer. Must
   * not be called until after preparation completes.
   *
   * @param periodIndex The period index the track selection is added for.
   * @param rendererIndex The renderer index the track selection is added for.
   * @param trackSelectorParameters The [DefaultTrackSelector.Parameters] to obtain the new
   *     selection of tracks.
   * @param overrides A list of [SelectionOverride SelectionOverrides] to apply to the
   *     [trackSelectorParameters]. If empty, [trackSelectorParameters] are used as they are.
   */
  fun addTrackSelectionForSingleRenderer(
      periodIndex: Int,
      rendererIndex: Int,
      trackSelectorParameters: DefaultTrackSelector.Parameters,
      overrides: List<SelectionOverride>
  ) {
    try {
      assertPreparedWithMedia()
      val builder = trackSelectorParameters.buildUpon()!!
      val mappedTrackInfo = checkNotNull(mappedTrackInfos)!![periodIndex]
      for (i in 0 until mappedTrackInfo.rendererCount) {
        builder.setRendererDisabled(/* rendererIndex= */ i, /* disabled= */ i != rendererIndex)
      }
      if (overrides.isEmpty()) {
        addTrackSelectionInternal(periodIndex, builder.build()!!)
      } else {
        val trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)
        for (i in overrides.indices) {
          builder.setSelectionOverride(rendererIndex, trackGroupArray, overrides[i])
          addTrackSelectionInternal(periodIndex, builder.build()!!)
        }
      }
    } catch (e: ExoPlaybackException) {
      throw IllegalStateException(e)
    }
  }

  /**
   * Builds a [DownloadRequest] for downloading the selected tracks. Must not be called until after
   * preparation completes. The uri of the [DownloadRequest] will be used as content id.
   *
   * @param data Application provided data to store in [DownloadRequest.data].
   * @return The built [DownloadRequest].
   */
  fun getDownloadRequest(data: ByteArray?): DownloadRequest {
    return getDownloadRequest(localConfiguration.uri.toString(), data)
  }

  /**
   * Builds a [DownloadRequest] for downloading the selected tracks. Must not be called until after
   * preparation completes.
   *
   * @param id The unique content id.
   * @param data Application provided data to store in [DownloadRequest.data].
   * @return The built [DownloadRequest].
   */
  fun getDownloadRequest(id: String, data: ByteArray?): DownloadRequest {
    val requestBuilder =
        DownloadRequest.Builder(id, localConfiguration.uri)
            .setMimeType(localConfiguration.mimeType)
            .setKeySetId(localConfiguration.drmConfiguration?.getKeySetId())
            .setCustomCacheKey(localConfiguration.customCacheKey)
            .setData(data)
    if (mediaSource == null) {
      return requestBuilder.build()
    }
    assertPreparedWithMedia()
    val streamKeys = ArrayList<StreamKey>()
    val allSelections = ArrayList<ExoTrackSelection>()
    val selectionsByPeriod = checkNotNull(trackSelectionsByPeriodAndRenderer)!!
    val mediaPreparer = checkNotNull(this.mediaPreparer)!!
    val mediaPeriods = checkNotNull(mediaPreparer.mediaPeriods)!!
    val periodCount = selectionsByPeriod.size
    for (periodIndex in 0 until periodCount) {
      allSelections.clear()
      val rendererCount = selectionsByPeriod[periodIndex].size
      for (rendererIndex in 0 until rendererCount) {
        allSelections.addAll(selectionsByPeriod[periodIndex][rendererIndex])
      }
      val keys = mediaPeriods[periodIndex].getStreamKeys(allSelections)
      if (keys != null) {
        streamKeys.addAll(keys.filterNotNull())
      }
    }
    return requestBuilder.setStreamKeys(streamKeys).build()
  }

  @Throws(ExoPlaybackException::class)
  private fun addTrackSelectionInternal(
      periodIndex: Int,
      trackSelectionParameters: TrackSelectionParameters
  ) {
    trackSelector.setParameters(trackSelectionParameters)
    runTrackSelection(periodIndex)
    for (override in trackSelectionParameters.overrides.values) {
      trackSelector.setParameters(
          trackSelectionParameters.buildUpon()!!.setOverrideForType(override)!!.build()!!
      )
      runTrackSelection(periodIndex)
    }
  }

  @Throws(ExoPlaybackException::class)
  private fun onMediaPrepared() {
    val preparer = checkNotNull(mediaPreparer)!!
    val mediaPeriods = checkNotNull(preparer.mediaPeriods)!!
    val periodCount = mediaPeriods.size
    val rendererCount = rendererCapabilities.size

    val selectionsByPeriodAndRenderer =
        Array(periodCount) { Array(rendererCount) { mutableListOf<ExoTrackSelection>() } }
    val immutableSelectionsByPeriodAndRenderer =
        Array(periodCount) { i ->
          Array(rendererCount) { j ->
            Collections.unmodifiableList(selectionsByPeriodAndRenderer[i][j])
          }
        }
    trackSelectionsByPeriodAndRenderer = selectionsByPeriodAndRenderer
    immutableTrackSelectionsByPeriodAndRenderer = immutableSelectionsByPeriodAndRenderer

    val trackGroupArrays = Array<TrackGroupArray?>(periodCount) { null }
    val mappedTrackInfos = Array<MappedTrackInfo?>(periodCount) { null }
    for (i in 0 until periodCount) {
      val trackGroups = mediaPeriods[i].getTrackGroups()
      trackGroupArrays[i] = trackGroups
      this.trackGroupArrays = trackGroupArrays.requireNoNulls()
      val trackSelectorResult = runTrackSelection(i)
      trackSelector.onSelectionActivated(trackSelectorResult.info)
      mappedTrackInfos[i] = checkNotNull(trackSelector.currentMappedTrackInfo)!!
    }
    this.trackGroupArrays = trackGroupArrays.requireNoNulls()
    this.mappedTrackInfos = mappedTrackInfos.requireNoNulls()
    setPreparedWithMedia()
    callbackHandler.post { checkNotNull(callback)!!.onPrepared(this) }
  }

  private fun onMediaPreparationFailed(error: IOException) {
    callbackHandler.post { checkNotNull(callback)!!.onPrepareError(this, error) }
  }

  private fun setPreparedWithMedia() {
    isPreparedWithMedia = true
  }

  private fun assertPreparedWithMedia() {
    Assertions.checkState(isPreparedWithMedia)
  }

  @Throws(ExoPlaybackException::class)
  private fun runTrackSelection(periodIndex: Int): TrackSelectorResult {
    val preparer = checkNotNull(mediaPreparer)!!
    val timeline = checkNotNull(preparer.timeline)!!
    val trackGroups = checkNotNull(trackGroupArrays)!![periodIndex]
    val trackSelectorResult =
        trackSelector.selectTracks(
            rendererCapabilities,
            trackGroups,
            MediaPeriodId(timeline.getUidOfPeriod(periodIndex)),
            timeline
        )
    val selectionsByPeriod = checkNotNull(trackSelectionsByPeriodAndRenderer)!!
    for (i in 0 until trackSelectorResult.length) {
      val newSelection = trackSelectorResult.selections[i] ?: continue
      val existingSelectionList = selectionsByPeriod[periodIndex][i]
      var mergedWithExistingSelection = false
      for (j in existingSelectionList.indices) {
        val existingSelection = existingSelectionList[j]
        if (existingSelection.getTrackGroup() == newSelection.getTrackGroup()) {
          scratchSet.clear()
          for (k in 0 until existingSelection.length()) {
            scratchSet.put(existingSelection.getIndexInTrackGroup(k), 0)
          }
          for (k in 0 until newSelection.length()) {
            scratchSet.put(newSelection.getIndexInTrackGroup(k), 0)
          }
          val mergedTracks = IntArray(scratchSet.size())
          for (k in 0 until scratchSet.size()) {
            mergedTracks[k] = scratchSet.keyAt(k)
          }
          existingSelectionList[j] =
              DownloadTrackSelection(existingSelection.getTrackGroup()!!, mergedTracks)
          mergedWithExistingSelection = true
          break
        }
      }
      if (!mergedWithExistingSelection) {
        existingSelectionList.add(newSelection)
      }
    }
    return trackSelectorResult
  }

  private class MediaPreparer(
      private val mediaSource: MediaSource,
      private val downloadHelper: DownloadHelper
  ) : MediaSourceCaller, MediaPeriod.Callback, Handler.Callback {

    private companion object {
      private const val MESSAGE_PREPARE_SOURCE = 0
      private const val MESSAGE_CHECK_FOR_FAILURE = 1
      private const val MESSAGE_CONTINUE_LOADING = 2
      private const val MESSAGE_RELEASE = 3

      private const val DOWNLOAD_HELPER_CALLBACK_MESSAGE_PREPARED = 0
      private const val DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED = 1
    }

    private val allocator: Allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
    private val pendingMediaPeriods: ArrayList<MediaPeriod> = ArrayList()
    private val downloadHelperHandler: Handler =
        Util.createHandlerForCurrentOrMainLooper(::handleDownloadHelperCallbackMessage)
    private val mediaSourceThread: HandlerThread = HandlerThread("ExoPlayer:DownloadHelper")
    private val mediaSourceHandler: Handler

    var timeline: Timeline? = null
    var mediaPeriods: Array<MediaPeriod>? = null

    private var released: Boolean = false

    init {
      mediaSourceThread.start()
      mediaSourceHandler = Util.createHandler(mediaSourceThread.looper, this)
      mediaSourceHandler.sendEmptyMessage(MESSAGE_PREPARE_SOURCE)
    }

    fun release() {
      if (released) {
        return
      }
      released = true
      mediaSourceHandler.sendEmptyMessage(MESSAGE_RELEASE)
    }

    override fun handleMessage(msg: Message): Boolean {
      return when (msg.what) {
        MESSAGE_PREPARE_SOURCE -> {
          mediaSource.prepareSource(
              this, /* mediaTransferListener= */ null, PlayerId.UNSET
          )
          mediaSourceHandler.sendEmptyMessage(MESSAGE_CHECK_FOR_FAILURE)
          true
        }
        MESSAGE_CHECK_FOR_FAILURE -> {
          try {
            val mediaPeriods = this.mediaPeriods
            if (mediaPeriods == null) {
              mediaSource.maybeThrowSourceInfoRefreshError()
            } else {
              for (i in pendingMediaPeriods.indices) {
                pendingMediaPeriods[i].maybeThrowPrepareError()
              }
            }
            mediaSourceHandler.sendEmptyMessageDelayed(
                MESSAGE_CHECK_FOR_FAILURE, /* delayMillis= */ 100
            )
          } catch (e: IOException) {
            downloadHelperHandler
                .obtainMessage(DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED, e)
                .sendToTarget()
          }
          true
        }
        MESSAGE_CONTINUE_LOADING -> {
          val mediaPeriod = msg.obj as MediaPeriod
          if (pendingMediaPeriods.contains(mediaPeriod)) {
            mediaPeriod.continueLoading(/* positionUs= */ 0)
          }
          true
        }
        MESSAGE_RELEASE -> {
          val mediaPeriods = this.mediaPeriods
          if (mediaPeriods != null) {
            for (period in mediaPeriods) {
              mediaSource.releasePeriod(period)
            }
          }
          mediaSource.releaseSource(this)
          mediaSourceHandler.removeCallbacksAndMessages(null)
          mediaSourceThread.quit()
          true
        }
        else -> false
      }
    }

    override fun onSourceInfoRefreshed(source: MediaSource?, timeline: Timeline?) {
      if (this.timeline != null || timeline == null) {
        return
      }
      if (timeline.getWindow(/* windowIndex= */ 0, Timeline.Window()).isLive) {
        downloadHelperHandler
            .obtainMessage(
                DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED,
                LiveContentUnsupportedException()
            )
            .sendToTarget()
        return
      }
      this.timeline = timeline
      val mediaPeriods = Array<MediaPeriod?>(timeline.periodCount) { null }
      for (i in mediaPeriods.indices) {
        val mediaPeriod =
            mediaSource.createPeriod(
                MediaPeriodId(timeline.getUidOfPeriod(/* periodIndex= */ i)),
                allocator,
                /* startPositionUs= */ 0
            )!!
        mediaPeriods[i] = mediaPeriod
        pendingMediaPeriods.add(mediaPeriod)
      }
      val nonNullMediaPeriods = mediaPeriods.requireNoNulls()
      this.mediaPeriods = nonNullMediaPeriods
      for (mediaPeriod in nonNullMediaPeriods) {
        mediaPeriod.prepare(this, /* positionUs= */ 0)
      }
    }

    override fun onPrepared(mediaPeriod: MediaPeriod?) {
      pendingMediaPeriods.remove(mediaPeriod)
      if (pendingMediaPeriods.isEmpty()) {
        mediaSourceHandler.removeMessages(MESSAGE_CHECK_FOR_FAILURE)
        downloadHelperHandler.sendEmptyMessage(DOWNLOAD_HELPER_CALLBACK_MESSAGE_PREPARED)
      }
    }

    override fun onContinueLoadingRequested(source: MediaPeriod?) {
      if (pendingMediaPeriods.contains(source)) {
        mediaSourceHandler.obtainMessage(MESSAGE_CONTINUE_LOADING, source).sendToTarget()
      }
    }

    private fun handleDownloadHelperCallbackMessage(msg: Message): Boolean {
      if (released) {
        return false
      }
      return when (msg.what) {
        DOWNLOAD_HELPER_CALLBACK_MESSAGE_PREPARED -> {
          try {
            downloadHelper.onMediaPrepared()
          } catch (e: ExoPlaybackException) {
            downloadHelperHandler
                .obtainMessage(
                    DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED, IOException(e)
                )
                .sendToTarget()
          }
          true
        }
        DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED -> {
          release()
          downloadHelper.onMediaPreparationFailed(msg.obj as IOException)
          true
        }
        else -> false
      }
    }
  }

  private class DownloadTrackSelection(
      trackGroup: TrackGroup,
      tracks: IntArray
  ) : BaseTrackSelection(trackGroup, *tracks) {

    class Factory : ExoTrackSelection.Factory {
      override fun createTrackSelections(
          definitions: Array<ExoTrackSelection.Definition?>?,
          bandwidthMeter: BandwidthMeter?,
          mediaPeriodId: MediaPeriodId?,
          timeline: Timeline?
      ): Array<ExoTrackSelection?>? {
        if (definitions == null) return null
        val selections = arrayOfNulls<ExoTrackSelection>(definitions.size)
        for (i in definitions.indices) {
          val definition = definitions[i]
          selections[i] =
              if (definition == null) null
              else DownloadTrackSelection(definition.group!!, definition.tracks!!)
        }
        return selections
      }
    }

    override fun getSelectedIndex(): Int {
      return 0
    }

    override fun getSelectionReason(): Int {
      return C.SELECTION_REASON_UNKNOWN
    }

    override fun getSelectionData(): Any? {
      return null
    }

    override fun updateSelectedTrack(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        availableDurationUs: Long,
        queue: List<MediaChunk?>?,
        mediaChunkIterators: Array<MediaChunkIterator?>?
    ) {
      // Do nothing.
    }
  }

  private class FakeBandwidthMeter : BandwidthMeter {
    override fun getBitrateEstimate(): Long {
      return 0
    }

    override fun getTransferListener(): TransferListener? {
      return null
    }

    override fun addEventListener(eventHandler: Handler?, eventListener: BandwidthMeter.EventListener?) {
      // Do nothing.
    }

    override fun removeEventListener(eventListener: BandwidthMeter.EventListener?) {
      // Do nothing.
    }
  }

  companion object {
    /**
     * Default track selection parameters for downloading, but without any [Context] constraints.
     *
     * If possible, use [getDefaultTrackSelectorParameters] instead.
     *
     * @see DefaultTrackSelector.Parameters.DEFAULT_WITHOUT_CONTEXT
     */
    @JvmField
    val DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT: DefaultTrackSelector.Parameters =
        DefaultTrackSelector.Parameters.DEFAULT_WITHOUT_CONTEXT
            .buildUpon()!!
            .setForceHighestSupportedBitrate(true)!!
            .setConstrainAudioChannelCountToDeviceCapabilities(false)!!
            .build()!!

    /** Returns the default parameters used for track selection for downloading. */
    @JvmStatic
    fun getDefaultTrackSelectorParameters(context: Context): DefaultTrackSelector.Parameters {
      return DefaultTrackSelector.Parameters.getDefaults(context)
          .buildUpon()!!
          .setForceHighestSupportedBitrate(true)!!
          .setConstrainAudioChannelCountToDeviceCapabilities(false)!!
          .build()!!
    }

    /**
     * Extracts renderer capabilities for the renderers created by the provided renderers factory.
     *
     * @param renderersFactory A [RenderersFactory].
     * @return The [RendererCapabilities] for each renderer created by the `renderersFactory`.
     */
    @JvmStatic
    fun getRendererCapabilities(renderersFactory: RenderersFactory): Array<RendererCapabilities> {
      val renderers =
          renderersFactory.createRenderers(
              Util.createHandlerForCurrentOrMainLooper(),
              object : VideoRendererEventListener {},
              object : AudioRendererEventListener {},
              object : TextOutput {
                override fun onCues(cueGroup: CueGroup?) {}
              },
              object : MetadataOutput {
                override fun onMetadata(metadata: Metadata?) {}
              }
          )!!
      val capabilities = Array(renderers.size) { i -> renderers[i]!!.getCapabilities()!! }
      return capabilities
    }

    /**
     * @deprecated Use [forMediaItem]
     */
    @Deprecated("Use forMediaItem(Context, MediaItem)")
    @JvmStatic
    fun forProgressive(context: Context, uri: Uri): DownloadHelper {
      return forMediaItem(context, MediaItem.Builder().setUri(uri).build())
    }

    /**
     * @deprecated Use [forMediaItem]
     */
    @Deprecated("Use forMediaItem(Context, MediaItem)")
    @JvmStatic
    fun forProgressive(context: Context, uri: Uri, cacheKey: String?): DownloadHelper {
      return forMediaItem(
          context, MediaItem.Builder().setUri(uri).setCustomCacheKey(cacheKey).build()
      )
    }

    /**
     * @deprecated Use [forMediaItem] instead.
     */
    @Deprecated("Use forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory?, DataSource.Factory?) instead.")
    @JvmStatic
    fun forDash(
        context: Context,
        uri: Uri,
        dataSourceFactory: DataSource.Factory,
        renderersFactory: RenderersFactory
    ): DownloadHelper {
      return forDash(
          uri,
          dataSourceFactory,
          renderersFactory,
          /* drmSessionManager= */ null,
          getDefaultTrackSelectorParameters(context)
      )
    }

    /**
     * @deprecated Use [forMediaItem] instead.
     */
    @Deprecated("Use forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory?, DataSource.Factory?, DrmSessionManager?) instead.")
    @JvmStatic
    fun forDash(
        uri: Uri,
        dataSourceFactory: DataSource.Factory,
        renderersFactory: RenderersFactory,
        drmSessionManager: DrmSessionManager?,
        trackSelectionParameters: TrackSelectionParameters
    ): DownloadHelper {
      return forMediaItem(
          MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_MPD).build(),
          trackSelectionParameters,
          renderersFactory,
          dataSourceFactory,
          drmSessionManager
      )
    }

    /**
     * @deprecated Use [forMediaItem] instead.
     */
    @Deprecated("Use forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory?, DataSource.Factory?) instead.")
    @JvmStatic
    fun forHls(
        context: Context,
        uri: Uri,
        dataSourceFactory: DataSource.Factory,
        renderersFactory: RenderersFactory
    ): DownloadHelper {
      return forHls(
          uri,
          dataSourceFactory,
          renderersFactory,
          /* drmSessionManager= */ null,
          getDefaultTrackSelectorParameters(context)
      )
    }

    /**
     * @deprecated Use [forMediaItem] instead.
     */
    @Deprecated("Use forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory?, DataSource.Factory?, DrmSessionManager?) instead.")
    @JvmStatic
    fun forHls(
        uri: Uri,
        dataSourceFactory: DataSource.Factory,
        renderersFactory: RenderersFactory,
        drmSessionManager: DrmSessionManager?,
        trackSelectionParameters: TrackSelectionParameters
    ): DownloadHelper {
      return forMediaItem(
          MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
          trackSelectionParameters,
          renderersFactory,
          dataSourceFactory,
          drmSessionManager
      )
    }

    /**
     * @deprecated Use [forMediaItem] instead.
     */
    @Deprecated("Use forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory?, DataSource.Factory?) instead.")
    @JvmStatic
    fun forSmoothStreaming(
        uri: Uri,
        dataSourceFactory: DataSource.Factory,
        renderersFactory: RenderersFactory
    ): DownloadHelper {
      return forSmoothStreaming(
          uri,
          dataSourceFactory,
          renderersFactory,
          /* drmSessionManager= */ null,
          DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT
      )
    }

    /**
     * @deprecated Use [forMediaItem] instead.
     */
    @Deprecated("Use forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory?, DataSource.Factory?) instead.")
    @JvmStatic
    fun forSmoothStreaming(
        context: Context,
        uri: Uri,
        dataSourceFactory: DataSource.Factory,
        renderersFactory: RenderersFactory
    ): DownloadHelper {
      return forSmoothStreaming(
          uri,
          dataSourceFactory,
          renderersFactory,
          /* drmSessionManager= */ null,
          getDefaultTrackSelectorParameters(context)
      )
    }

    /**
     * @deprecated Use [forMediaItem] instead.
     */
    @Deprecated("Use forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory?, DataSource.Factory?, DrmSessionManager?) instead.")
    @JvmStatic
    fun forSmoothStreaming(
        uri: Uri,
        dataSourceFactory: DataSource.Factory,
        renderersFactory: RenderersFactory,
        drmSessionManager: DrmSessionManager?,
        trackSelectionParameters: TrackSelectionParameters
    ): DownloadHelper {
      return forMediaItem(
          MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_SS).build(),
          trackSelectionParameters,
          renderersFactory,
          dataSourceFactory,
          drmSessionManager
      )
    }

    /**
     * Creates a [DownloadHelper] for the given progressive media item.
     *
     * @param context The context.
     * @param mediaItem A [MediaItem].
     * @return A [DownloadHelper] for progressive streams.
     * @throws IllegalStateException If the media item is of type DASH, HLS or SmoothStreaming.
     */
    @JvmStatic
    fun forMediaItem(context: Context, mediaItem: MediaItem): DownloadHelper {
      Assertions.checkArgument(isProgressive(checkNotNull(mediaItem.localConfiguration)!!))
      return forMediaItem(
          mediaItem,
          getDefaultTrackSelectorParameters(context),
          /* renderersFactory= */ null,
          /* dataSourceFactory= */ null,
          /* drmSessionManager= */ null
      )
    }

    /**
     * Creates a [DownloadHelper] for the given media item.
     *
     * @param context The context.
     * @param mediaItem A [MediaItem].
     * @param renderersFactory A [RenderersFactory] creating the renderers for which tracks are
     *     selected.
     * @param dataSourceFactory A [DataSource.Factory] used to load the manifest for adaptive
     *     streams. This argument is required for adaptive streams and ignored for progressive
     *     streams.
     * @return A [DownloadHelper].
     * @throws IllegalStateException If the corresponding module is missing for DASH, HLS or
     *     SmoothStreaming media items.
     * @throws IllegalArgumentException If the `dataSourceFactory` is null for adaptive streams.
     */
    @JvmStatic
    fun forMediaItem(
        context: Context,
        mediaItem: MediaItem,
        renderersFactory: RenderersFactory?,
        dataSourceFactory: DataSource.Factory?
    ): DownloadHelper {
      return forMediaItem(
          mediaItem,
          getDefaultTrackSelectorParameters(context),
          renderersFactory,
          dataSourceFactory,
          /* drmSessionManager= */ null
      )
    }

    /**
     * Creates a [DownloadHelper] for the given media item.
     *
     * @param mediaItem A [MediaItem].
     * @param trackSelectionParameters [TrackSelectionParameters] for selecting tracks for
     *     downloading.
     * @param renderersFactory A [RenderersFactory] creating the renderers for which tracks are
     *     selected.
     * @param dataSourceFactory A [DataSource.Factory] used to load the manifest for adaptive
     *     streams. This argument is required for adaptive streams and ignored for progressive
     *     streams.
     * @return A [DownloadHelper].
     * @throws IllegalStateException If the corresponding module is missing for DASH, HLS or
     *     SmoothStreaming media items.
     * @throws IllegalArgumentException If the `dataSourceFactory` is null for adaptive streams.
     */
    @JvmStatic
    fun forMediaItem(
        mediaItem: MediaItem,
        trackSelectionParameters: TrackSelectionParameters,
        renderersFactory: RenderersFactory?,
        dataSourceFactory: DataSource.Factory?
    ): DownloadHelper {
      return forMediaItem(
          mediaItem,
          trackSelectionParameters,
          renderersFactory,
          dataSourceFactory,
          /* drmSessionManager= */ null
      )
    }

    /**
     * Creates a [DownloadHelper] for the given media item.
     *
     * @param mediaItem A [MediaItem].
     * @param trackSelectionParameters [TrackSelectionParameters] for selecting tracks for
     *     downloading.
     * @param renderersFactory A [RenderersFactory] creating the renderers for which tracks are
     *     selected.
     * @param dataSourceFactory A [DataSource.Factory] used to load the manifest for adaptive
     *     streams. This argument is required for adaptive streams and ignored for progressive
     *     streams.
     * @param drmSessionManager An optional [DrmSessionManager]. Used to help determine which
     *     tracks can be selected.
     * @return A [DownloadHelper].
     * @throws IllegalStateException If the corresponding module is missing for DASH, HLS or
     *     SmoothStreaming media items.
     * @throws IllegalArgumentException If the `dataSourceFactory` is null for adaptive streams.
     */
    @JvmStatic
    fun forMediaItem(
        mediaItem: MediaItem,
        trackSelectionParameters: TrackSelectionParameters,
        renderersFactory: RenderersFactory?,
        dataSourceFactory: DataSource.Factory?,
        drmSessionManager: DrmSessionManager?
    ): DownloadHelper {
      val isProgressive = isProgressive(checkNotNull(mediaItem.localConfiguration)!!)
      Assertions.checkArgument(isProgressive || dataSourceFactory != null)
      return DownloadHelper(
          mediaItem,
          if (isProgressive) null
          else createMediaSourceInternal(mediaItem, checkNotNull(dataSourceFactory)!!, drmSessionManager),
          trackSelectionParameters,
          if (renderersFactory != null) getRendererCapabilities(renderersFactory)
          else emptyArray()
      )
    }

    /**
     * Equivalent to [createMediaSource(downloadRequest, dataSourceFactory, null)][createMediaSource].
     */
    @JvmStatic
    fun createMediaSource(
        downloadRequest: DownloadRequest,
        dataSourceFactory: DataSource.Factory
    ): MediaSource {
      return createMediaSource(downloadRequest, dataSourceFactory, /* drmSessionManager= */ null)
    }

    /**
     * Utility method to create a [MediaSource] that only exposes the tracks defined in `downloadRequest`.
     *
     * @param downloadRequest A [DownloadRequest].
     * @param dataSourceFactory A factory for [DataSource]s to read the media.
     * @param drmSessionManager An optional [DrmSessionManager] to be passed to the [MediaSource].
     * @return A [MediaSource] that only exposes the tracks defined in `downloadRequest`.
     */
    @JvmStatic
    fun createMediaSource(
        downloadRequest: DownloadRequest,
        dataSourceFactory: DataSource.Factory,
        drmSessionManager: DrmSessionManager?
    ): MediaSource {
      return createMediaSourceInternal(
          downloadRequest.toMediaItem(), dataSourceFactory, drmSessionManager
      )
    }

    private fun createMediaSourceInternal(
        mediaItem: MediaItem,
        dataSourceFactory: DataSource.Factory,
        drmSessionManager: DrmSessionManager?
    ): MediaSource {
      val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, ExtractorsFactory.EMPTY)
      if (drmSessionManager != null) {
        mediaSourceFactory.setDrmSessionManagerProvider(object : DrmSessionManagerProvider {
          override fun get(mediaItem: MediaItem?): DrmSessionManager = drmSessionManager
        })
      }
      return mediaSourceFactory.createMediaSource(mediaItem)!!
    }

    private fun isProgressive(localConfiguration: MediaItem.LocalConfiguration): Boolean {
      return Util.inferContentTypeForUriAndMimeType(
          localConfiguration.uri, localConfiguration.mimeType
      ) == C.CONTENT_TYPE_OTHER
    }
  }
}
