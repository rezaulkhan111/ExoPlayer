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

import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.source.MediaSource

/**
 * Manager for active playback sessions.
 *
 *
 * The manager keeps track of the association between window index and/or media period id to
 * session identifier.
 */
interface PlaybackSessionManager {
    /** A listener for session updates.  */
    interface Listener {
        /**
         * Called when a new session is created as a result of [.updateSessions].
         *
         * @param eventTime The [EventTime] at which the session is created.
         * @param sessionId The identifier of the new session.
         */
        fun onSessionCreated(eventTime: EventTime?, sessionId: String?)

        /**
         * Called when a session becomes active, i.e. playing in the foreground.
         *
         * @param eventTime The [EventTime] at which the session becomes active.
         * @param sessionId The identifier of the session.
         */
        fun onSessionActive(eventTime: EventTime?, sessionId: String?)

        /**
         * Called when a session is interrupted by ad playback.
         *
         * @param eventTime The [EventTime] at which the ad playback starts.
         * @param contentSessionId The session identifier of the content session.
         * @param adSessionId The identifier of the ad session.
         */
        fun onAdPlaybackStarted(eventTime: EventTime?, contentSessionId: String?, adSessionId: String?)

        /**
         * Called when a session is permanently finished.
         *
         * @param eventTime The [EventTime] at which the session finished.
         * @param sessionId The identifier of the finished session.
         * @param automaticTransitionToNextPlayback Whether the session finished because of an automatic
         * transition to the next playback item.
         */
        fun onSessionFinished(
                eventTime: EventTime?, sessionId: String?, automaticTransitionToNextPlayback: Boolean)
    }

    /**
     * Sets the listener to be notified of session updates. Must be called before the session manager
     * is used.
     *
     * @param listener The [Listener] to be notified of session updates.
     */
    fun setListener(listener: Listener?)

    /**
     * Returns the session identifier for the given media period id.
     *
     *
     * Note that this will reserve a new session identifier if it doesn't exist yet, but will not
     * call any [Listener] callbacks.
     *
     * @param timeline The timeline, `mediaPeriodId` is part of.
     * @param mediaPeriodId A [MediaPeriodId].
     */
    fun getSessionForMediaPeriodId(timeline: Timeline?, mediaPeriodId: MediaSource.MediaPeriodId?): String?

    /**
     * Returns whether an event time belong to a session.
     *
     * @param eventTime The [EventTime].
     * @param sessionId A session identifier.
     * @return Whether the event belongs to the specified session.
     */
    fun belongsToSession(eventTime: EventTime?, sessionId: String?): Boolean

    /**
     * Updates or creates sessions based on a player [EventTime].
     *
     *
     * Call [.updateSessionsWithTimelineChange] or [ ][.updateSessionsWithDiscontinuity] if the event is a [Timeline] change or
     * a position discontinuity respectively.
     *
     * @param eventTime The [EventTime].
     */
    fun updateSessions(eventTime: EventTime?)

    /**
     * Updates or creates sessions based on a [Timeline] change at [EventTime].
     *
     *
     * Should be called instead of [.updateSessions] if a [Timeline] change
     * occurred.
     *
     * @param eventTime The [EventTime] with the timeline change.
     */
    fun updateSessionsWithTimelineChange(eventTime: EventTime?)

    /**
     * Updates or creates sessions based on a position discontinuity at [EventTime].
     *
     *
     * Should be called instead of [.updateSessions] if a position discontinuity
     * occurred.
     *
     * @param eventTime The [EventTime] of the position discontinuity.
     * @param reason The [DiscontinuityReason].
     */
    fun updateSessionsWithDiscontinuity(eventTime: EventTime?, @DiscontinuityReason reason: Int)

    /**
     * Returns the session identifier of the session that is currently actively playing, or `null` if there no such session.
     */
    fun getActiveSessionId(): String?

    /**
     * Finishes all existing sessions and calls their respective [ ][Listener.onSessionFinished] callback.
     *
     * @param eventTime The event time at which sessions are finished.
     */
    fun finishAllSessions(eventTime: EventTime?)
}