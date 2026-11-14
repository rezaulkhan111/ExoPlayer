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
package com.google.android.exoplayer2.analytics

import android.util.Base64
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import com.google.common.base.Supplier
import java.util.HashMap
import java.util.Random
import kotlin.math.max

/**
 * Default [PlaybackSessionManager] which instantiates a new session for each window in the
 * timeline and also for each ad within the windows.
 *
 * <p>By default, sessions are identified by Base64-encoded, URL-safe, random strings.
 */
class DefaultPlaybackSessionManager @JvmOverloads constructor(
    private val sessionIdGenerator: Supplier<String> = DEFAULT_SESSION_ID_GENERATOR
) : PlaybackSessionManager {

  private val window: Timeline.Window = Timeline.Window()
  private val period: Timeline.Period = Timeline.Period()
  private val sessions: HashMap<String, SessionDescriptor> = HashMap()

  private var listener: PlaybackSessionManager.Listener? = null
  private var currentTimeline: Timeline = Timeline.EMPTY
  private var currentSessionId: String? = null

  override fun setListener(listener: PlaybackSessionManager.Listener?) {
    this.listener = listener
  }

  @Synchronized
  override fun getSessionForMediaPeriodId(
      timeline: Timeline?, mediaPeriodId: MediaPeriodId?
  ): String {
    timeline!!
    mediaPeriodId!!
    val windowIndex = timeline.getPeriodByUid(mediaPeriodId.periodUid, period)!!.windowIndex
    return getOrAddSession(windowIndex, mediaPeriodId).sessionId
  }

  @Synchronized
  override fun belongsToSession(eventTime: EventTime?, sessionId: String?): Boolean {
    val sessionDescriptor = sessions[sessionId] ?: return false
    eventTime!!
    sessionDescriptor.maybeSetWindowSequenceNumber(eventTime.windowIndex, eventTime.mediaPeriodId)
    return sessionDescriptor.belongsToSession(eventTime.windowIndex, eventTime.mediaPeriodId)
  }

  @Synchronized
  override fun updateSessions(eventTime: EventTime?) {
    val currentListener = Assertions.checkNotNull(listener)!!
    eventTime!!
    val timeline = eventTime.timeline
    if (timeline == null || timeline.isEmpty) {
      // Don't try to create new sessions for empty timelines.
      return
    }
    val currentSession = sessions[currentSessionId]
    if (eventTime.mediaPeriodId != null && currentSession != null) {
      // If we receive an event associated with a media period, then it needs to be either part of
      // the current window if it's the first created media period, or a window that will be played
      // in the future. Otherwise, we know that it belongs to a session that was already finished
      // and we can ignore the event.
      val isAlreadyFinished =
          if (currentSession.windowSequenceNumber == C.INDEX_UNSET.toLong()) {
            currentSession.windowIndex != eventTime.windowIndex
          } else {
            eventTime.mediaPeriodId!!.windowSequenceNumber < currentSession.windowSequenceNumber
          }
      if (isAlreadyFinished) {
        return
      }
    }
    val eventSession = getOrAddSession(eventTime.windowIndex, eventTime.mediaPeriodId)
    if (currentSessionId == null) {
      currentSessionId = eventSession.sessionId
    }
    if (eventTime.mediaPeriodId != null && eventTime.mediaPeriodId!!.isAd) {
      // Ensure that the content session for an ad session is created first.
      val contentMediaPeriodId =
          MediaPeriodId(
              eventTime.mediaPeriodId!!.periodUid,
              eventTime.mediaPeriodId!!.windowSequenceNumber,
              eventTime.mediaPeriodId!!.adGroupIndex
          )
      val contentSession = getOrAddSession(eventTime.windowIndex, contentMediaPeriodId)
      if (!contentSession.isCreated) {
        contentSession.isCreated = true
        timeline.getPeriodByUid(eventTime.mediaPeriodId!!.periodUid, period)
        var adGroupPositionMs =
            Util.usToMs(period.getAdGroupTimeUs(eventTime.mediaPeriodId!!.adGroupIndex)) +
                period.positionInWindowMs
        // getAdGroupTimeUs may return 0 for prerolls despite period offset.
        adGroupPositionMs = max(0L, adGroupPositionMs)
        val eventTimeForContent =
            EventTime(
                eventTime.realtimeMs,
                eventTime.timeline,
                eventTime.windowIndex,
                contentMediaPeriodId,
                /* eventPlaybackPositionMs= */ adGroupPositionMs,
                eventTime.currentTimeline,
                eventTime.currentWindowIndex,
                eventTime.currentMediaPeriodId,
                eventTime.currentPlaybackPositionMs,
                eventTime.totalBufferedDurationMs
            )
        currentListener.onSessionCreated(eventTimeForContent, contentSession.sessionId)
      }
    }
    if (!eventSession.isCreated) {
      eventSession.isCreated = true
      currentListener.onSessionCreated(eventTime, eventSession.sessionId)
    }
    if (eventSession.sessionId == currentSessionId && !eventSession.isActive) {
      eventSession.isActive = true
      currentListener.onSessionActive(eventTime, eventSession.sessionId)
    }
  }

  @Synchronized
  override fun updateSessionsWithTimelineChange(eventTime: EventTime?) {
    val currentListener = Assertions.checkNotNull(listener)!!
    eventTime!!
    val previousTimeline = currentTimeline
    currentTimeline = eventTime.timeline ?: Timeline.EMPTY
    val iterator = sessions.values.iterator()
    while (iterator.hasNext()) {
      val session = iterator.next()
      if (!session.tryResolvingToNewTimeline(previousTimeline, currentTimeline) ||
          session.isFinishedAtEventTime(eventTime)
      ) {
        iterator.remove()
        if (session.isCreated) {
          if (session.sessionId == currentSessionId) {
            currentSessionId = null
          }
          currentListener.onSessionFinished(
              eventTime, session.sessionId, /* automaticTransitionToNextPlayback= */ false
          )
        }
      }
    }
    updateCurrentSession(eventTime)
  }

  @Synchronized
  override fun updateSessionsWithDiscontinuity(
      eventTime: EventTime?, @DiscontinuityReason reason: Int
  ) {
    val currentListener = Assertions.checkNotNull(listener)!!
    eventTime!!
    val hasAutomaticTransition = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
    val iterator = sessions.values.iterator()
    while (iterator.hasNext()) {
      val session = iterator.next()
      if (session.isFinishedAtEventTime(eventTime)) {
        iterator.remove()
        if (session.isCreated) {
          val isRemovingCurrentSession = session.sessionId == currentSessionId
          val isAutomaticTransition =
              hasAutomaticTransition && isRemovingCurrentSession && session.isActive
          if (isRemovingCurrentSession) {
            currentSessionId = null
          }
          currentListener.onSessionFinished(eventTime, session.sessionId, isAutomaticTransition)
        }
      }
    }
    updateCurrentSession(eventTime)
  }

  @Synchronized
  override fun getActiveSessionId(): String? {
    return currentSessionId
  }

  @Synchronized
  override fun finishAllSessions(eventTime: EventTime?) {
    currentSessionId = null
    val iterator = sessions.values.iterator()
    while (iterator.hasNext()) {
      val session = iterator.next()
      iterator.remove()
      if (session.isCreated && listener != null) {
        listener!!.onSessionFinished(
            eventTime, session.sessionId, /* automaticTransitionToNextPlayback= */ false
        )
      }
    }
  }

  private fun updateCurrentSession(eventTime: EventTime) {
    val timeline = eventTime.timeline
    if (timeline == null || timeline.isEmpty) {
      // Clear current session if the Timeline is empty.
      currentSessionId = null
      return
    }
    val previousSessionDescriptor = sessions[currentSessionId]
    val currentSessionDescriptor =
        getOrAddSession(eventTime.windowIndex, eventTime.mediaPeriodId)
    currentSessionId = currentSessionDescriptor.sessionId
    updateSessions(eventTime)
    if (eventTime.mediaPeriodId != null &&
        eventTime.mediaPeriodId!!.isAd &&
        (previousSessionDescriptor == null ||
            previousSessionDescriptor.windowSequenceNumber !=
                eventTime.mediaPeriodId!!.windowSequenceNumber ||
            previousSessionDescriptor.adMediaPeriodId == null ||
            previousSessionDescriptor.adMediaPeriodId!!.adGroupIndex !=
                eventTime.mediaPeriodId!!.adGroupIndex ||
            previousSessionDescriptor.adMediaPeriodId!!.adIndexInAdGroup !=
                eventTime.mediaPeriodId!!.adIndexInAdGroup)
    ) {
      // New ad playback started. Find corresponding content session and notify ad playback started.
      val contentMediaPeriodId =
          MediaPeriodId(
              eventTime.mediaPeriodId!!.periodUid, eventTime.mediaPeriodId!!.windowSequenceNumber
          )
      val contentSession = getOrAddSession(eventTime.windowIndex, contentMediaPeriodId)
      listener!!.onAdPlaybackStarted(
          eventTime, contentSession.sessionId, currentSessionDescriptor.sessionId
      )
    }
  }

  private fun getOrAddSession(
      windowIndex: Int, mediaPeriodId: MediaPeriodId?
  ): SessionDescriptor {
    // There should only be one matching session if mediaPeriodId is non-null. If mediaPeriodId is
    // null, there may be multiple matching sessions with different window sequence numbers or
    // adMediaPeriodIds. The best match is the one with the smaller window sequence number, and for
    // windows with ads, the content session is preferred over ad sessions.
    var bestMatch: SessionDescriptor? = null
    var bestMatchWindowSequenceNumber = Long.MAX_VALUE
    for (sessionDescriptor in sessions.values) {
      sessionDescriptor.maybeSetWindowSequenceNumber(windowIndex, mediaPeriodId)
      if (sessionDescriptor.belongsToSession(windowIndex, mediaPeriodId)) {
        val windowSequenceNumber = sessionDescriptor.windowSequenceNumber
        if (windowSequenceNumber == C.INDEX_UNSET.toLong() ||
            windowSequenceNumber < bestMatchWindowSequenceNumber
        ) {
          bestMatch = sessionDescriptor
          bestMatchWindowSequenceNumber = windowSequenceNumber
        } else if (windowSequenceNumber == bestMatchWindowSequenceNumber &&
            bestMatch!!.adMediaPeriodId != null &&
            sessionDescriptor.adMediaPeriodId != null
        ) {
          bestMatch = sessionDescriptor
        }
      }
    }
    if (bestMatch == null) {
      val sessionId = sessionIdGenerator.get()
      bestMatch = SessionDescriptor(sessionId, windowIndex, mediaPeriodId)
      sessions[sessionId] = bestMatch
    }
    return bestMatch
  }

  /**
   * Descriptor for a session.
   *
   * The session may be described in one of three ways:
   *
   * <ul>
   *   <li>A window index with unset window sequence number and a null ad media period id
   *   <li>A content window with index and sequence number, but a null ad media period id.
   *   <li>An ad with all values set.
   * </ul>
   */
  private inner class SessionDescriptor(
      val sessionId: String,
      var windowIndex: Int,
      mediaPeriodId: MediaPeriodId?
  ) {
    var windowSequenceNumber: Long = mediaPeriodId?.windowSequenceNumber ?: C.INDEX_UNSET.toLong()
    var adMediaPeriodId: MediaPeriodId? =
        if (mediaPeriodId != null && mediaPeriodId.isAd) mediaPeriodId else null
    var isCreated: Boolean = false
    var isActive: Boolean = false

    fun tryResolvingToNewTimeline(oldTimeline: Timeline, newTimeline: Timeline): Boolean {
      windowIndex = resolveWindowIndexToNewTimeline(oldTimeline, newTimeline, windowIndex)
      if (windowIndex == C.INDEX_UNSET) {
        return false
      }
      val adMediaPeriodId = adMediaPeriodId ?: return true
      val newPeriodIndex = newTimeline.getIndexOfPeriod(adMediaPeriodId.periodUid)
      return newPeriodIndex != C.INDEX_UNSET
    }

    fun belongsToSession(
        eventWindowIndex: Int, eventMediaPeriodId: MediaPeriodId?
    ): Boolean {
      if (eventMediaPeriodId == null) {
        // Events without concrete media period id are for all sessions of the same window.
        return eventWindowIndex == windowIndex
      }
      val adMediaPeriodId =
          adMediaPeriodId
              ?: // If this is a content session, only events for content with the same window
              // sequence number belong to this session.
              return !eventMediaPeriodId.isAd &&
                  eventMediaPeriodId.windowSequenceNumber == windowSequenceNumber
      // If this is an ad session, only events for this ad belong to the session.
      return eventMediaPeriodId.windowSequenceNumber == adMediaPeriodId.windowSequenceNumber &&
          eventMediaPeriodId.adGroupIndex == adMediaPeriodId.adGroupIndex &&
          eventMediaPeriodId.adIndexInAdGroup == adMediaPeriodId.adIndexInAdGroup
    }

    fun maybeSetWindowSequenceNumber(
        eventWindowIndex: Int, eventMediaPeriodId: MediaPeriodId?
    ) {
      if (windowSequenceNumber == C.INDEX_UNSET.toLong() &&
          eventWindowIndex == windowIndex &&
          eventMediaPeriodId != null
      ) {
        // Set window sequence number for this session as soon as we have one.
        windowSequenceNumber = eventMediaPeriodId.windowSequenceNumber
      }
    }

    fun isFinishedAtEventTime(eventTime: EventTime): Boolean {
      val eventMediaPeriodId =
          eventTime.mediaPeriodId ?: return windowIndex != eventTime.windowIndex
      if (windowSequenceNumber == C.INDEX_UNSET.toLong()) {
        // Sessions with unspecified window sequence number are kept until we know more.
        return false
      }
      if (eventMediaPeriodId.windowSequenceNumber > windowSequenceNumber) {
        // All past window sequence numbers are finished.
        return true
      }
      val adMediaPeriodId = adMediaPeriodId ?: return false
      val timeline = eventTime.timeline ?: return false
      val eventPeriodIndex = timeline.getIndexOfPeriod(eventMediaPeriodId.periodUid)
      val adPeriodIndex = timeline.getIndexOfPeriod(adMediaPeriodId.periodUid)
      if (eventMediaPeriodId.windowSequenceNumber < adMediaPeriodId.windowSequenceNumber ||
          eventPeriodIndex < adPeriodIndex
      ) {
        // Ads in future windows or periods are not finished.
        return false
      }
      if (eventPeriodIndex > adPeriodIndex) {
        // Ads in past periods are finished.
        return true
      }
      return if (eventMediaPeriodId.isAd) {
        val eventAdGroup = eventMediaPeriodId.adGroupIndex
        val eventAdIndex = eventMediaPeriodId.adIndexInAdGroup
        // Finished if event is for an ad after this one in the same period.
        eventAdGroup > adMediaPeriodId.adGroupIndex ||
            (eventAdGroup == adMediaPeriodId.adGroupIndex &&
                eventAdIndex > adMediaPeriodId.adIndexInAdGroup)
      } else {
        // Finished if the event is for content after this ad.
        eventMediaPeriodId.nextAdGroupIndex == C.INDEX_UNSET ||
            eventMediaPeriodId.nextAdGroupIndex > adMediaPeriodId.adGroupIndex
      }
    }

    private fun resolveWindowIndexToNewTimeline(
        oldTimeline: Timeline, newTimeline: Timeline, windowIndex: Int
    ): Int {
      if (windowIndex >= oldTimeline.windowCount) {
        return if (windowIndex < newTimeline.windowCount) windowIndex else C.INDEX_UNSET
      }
      oldTimeline.getWindow(windowIndex, window)
      for (periodIndex in window.firstPeriodIndex..window.lastPeriodIndex) {
        val periodUid = oldTimeline.getUidOfPeriod(periodIndex)
        val newPeriodIndex = newTimeline.getIndexOfPeriod(periodUid)
        if (newPeriodIndex != C.INDEX_UNSET) {
          return newTimeline.getPeriod(newPeriodIndex, period).windowIndex
        }
      }
      return C.INDEX_UNSET
    }
  }

  companion object {
    /** Default generator for unique session ids that are random, Based64-encoded and URL-safe. */
    @JvmField
    val DEFAULT_SESSION_ID_GENERATOR: Supplier<String> = Supplier { generateDefaultSessionId() }

    private val RANDOM = Random()
    private const val SESSION_ID_LENGTH = 12

    private fun generateDefaultSessionId(): String {
      val randomBytes = ByteArray(SESSION_ID_LENGTH)
      RANDOM.nextBytes(randomBytes)
      return Base64.encodeToString(randomBytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }
  }
}
