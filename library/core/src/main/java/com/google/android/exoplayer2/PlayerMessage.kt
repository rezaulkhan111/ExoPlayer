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
package com.google.android.exoplayer2

import android.os.Handler
import android.os.Looper
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.PlayerMessage.Sender
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.util.Assertions.checkArgument
import com.google.android.exoplayer2.util.Assertions.checkState
import com.google.android.exoplayer2.util.Clock
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.util.concurrent.TimeoutException

/**
 * Defines a player message which can be sent with a [Sender] and received by a [ ].
 */
class PlayerMessage(
        private val sender: Sender,
        /** Returns the target the message is sent to.  */
        val target: Target,
        /** Returns the timeline used for setting the position with [.setPosition].  */
        val timeline: Timeline,
        /** Returns media item index at which the message will be delivered.  */
        var mediaItemIndex: Int,
        private val clock: Clock,
        /** Returns the [Looper] the message is delivered on.  */
        var looper: Looper
) {
    /** A target for messages.  */
    interface Target {
        /**
         * Handles a message delivered to the target.
         *
         * @param messageType The message type.
         * @param message The message payload.
         * @throws ExoPlaybackException If an error occurred whilst handling the message. Should only be
         * thrown by targets that handle messages on the playback thread.
         */
        @Throws(ExoPlaybackException::class)
        fun handleMessage(@Renderer.MessageType messageType: Int, message: Any?)
    }

    /** A sender for messages.  */
    interface Sender {
        /**
         * Sends a message.
         *
         * @param message The message to be sent.
         */
        fun sendMessage(message: PlayerMessage?)
    }

    /** Returns the message type forwarded to [Target.handleMessage].  */
    var type = 0
        private set
    /** Returns the message payload forwarded to [Target.handleMessage].  */
    var payload: Any? = null
        private set

    /**
     * Returns position in the media item at [.getMediaItemIndex] at which the message will be
     * delivered, in milliseconds. If [C.TIME_UNSET], the message will be delivered immediately.
     * If [C.TIME_END_OF_SOURCE], the message will be delivered at the end of the media item at
     * [.getMediaItemIndex].
     */
    var positionMs: Long
        private set
    /** Returns whether the message will be deleted after delivery.  */
    var deleteAfterDelivery: Boolean
        private set
    private var isSent = false
    private var isDelivered = false
    private var isProcessed = false

    /** Returns whether the message delivery has been canceled.  */
    @get:Synchronized
    var isCanceled = false
        private set

    /**
     * Creates a new message.
     *
     * @param sender The [Sender] used to send the message.
     * @param target The [Target] the message is sent to.
     * @param timeline The timeline used when setting the position with [.setPosition]. If
     * set to [Timeline.EMPTY], any position can be specified.
     * @param defaultMediaItemIndex The default media item index in the `timeline` when no other
     * media item index is specified.
     * @param clock The [Clock].
     * @param defaultLooper The default [Looper] to send the message on when no other looper is
     * specified.
     */
    init {
        positionMs = C.TIME_UNSET
        deleteAfterDelivery = true
    }

    /**
     * Sets the message type forwarded to [Target.handleMessage].
     *
     * @param messageType The message type.
     * @return This message.
     * @throws IllegalStateException If [.send] has already been called.
     */
    @CanIgnoreReturnValue
    fun setType(messageType: Int): PlayerMessage {
        checkState(!isSent)
        type = messageType
        return this
    }

    /**
     * Sets the message payload forwarded to [Target.handleMessage].
     *
     * @param payload The message payload.
     * @return This message.
     * @throws IllegalStateException If [.send] has already been called.
     */
    @CanIgnoreReturnValue
    fun setPayload(payload: Any?): PlayerMessage {
        checkState(!isSent)
        this.payload = payload
        return this
    }

    @CanIgnoreReturnValue
    @Deprecated("Use {@link #setLooper(Looper)} instead.")
    fun setHandler(handler: Handler): PlayerMessage {
        return setLooper(handler.looper)
    }

    /**
     * Sets the [Looper] the message is delivered on.
     *
     * @param looper A [Looper].
     * @return This message.
     * @throws IllegalStateException If [.send] has already been called.
     */
    @CanIgnoreReturnValue
    fun setLooper(looper: Looper): PlayerMessage {
        checkState(!isSent)
        this.looper = looper
        return this
    }

    /**
     * Sets a position in the current media item at which the message will be delivered.
     *
     * @param positionMs The position in the current media item at which the message will be sent, in
     * milliseconds, or [C.TIME_END_OF_SOURCE] to deliver the message at the end of the
     * current media item.
     * @return This message.
     * @throws IllegalStateException If [.send] has already been called.
     */
    @CanIgnoreReturnValue
    fun setPosition(positionMs: Long): PlayerMessage {
        checkState(!isSent)
        this.positionMs = positionMs
        return this
    }

    /**
     * Sets a position in a media item at which the message will be delivered.
     *
     * @param mediaItemIndex The index of the media item at which the message will be sent.
     * @param positionMs The position in the media item with index `mediaItemIndex` at which the
     * message will be sent, in milliseconds, or [C.TIME_END_OF_SOURCE] to deliver the
     * message at the end of the media item with index `mediaItemIndex`.
     * @return This message.
     * @throws IllegalSeekPositionException If the timeline returned by [.getTimeline] is not
     * empty and the provided media item index is not within the bounds of the timeline.
     * @throws IllegalStateException If [.send] has already been called.
     */
    @CanIgnoreReturnValue
    fun setPosition(mediaItemIndex: Int, positionMs: Long): PlayerMessage {
        checkState(!isSent)
        checkArgument(positionMs != C.TIME_UNSET)
        if ((mediaItemIndex < 0 || !timeline.isEmpty) && mediaItemIndex >= timeline.windowCount) {
            throw IllegalSeekPositionException(timeline, mediaItemIndex, positionMs)
        }
        this.mediaItemIndex = mediaItemIndex
        this.positionMs = positionMs
        return this
    }

    /**
     * Sets whether the message will be deleted after delivery. If false, the message will be resent
     * if playback reaches the specified position again. Only allowed to be false if a position is set
     * with [.setPosition].
     *
     * @param deleteAfterDelivery Whether the message is deleted after delivery.
     * @return This message.
     * @throws IllegalStateException If [.send] has already been called.
     */
    @CanIgnoreReturnValue
    fun setDeleteAfterDelivery(deleteAfterDelivery: Boolean): PlayerMessage {
        checkState(!isSent)
        this.deleteAfterDelivery = deleteAfterDelivery
        return this
    }

    /**
     * Sends the message. If the target throws an [ExoPlaybackException] then it is propagated
     * out of the player as an error using [Player.Listener.onPlayerError].
     *
     * @return This message.
     * @throws IllegalStateException If this message has already been sent.
     */
    @CanIgnoreReturnValue
    fun send(): PlayerMessage {
        checkState(!isSent)
        if (positionMs == C.TIME_UNSET) {
            checkArgument(deleteAfterDelivery)
        }
        isSent = true
        sender.sendMessage(this)
        return this
    }

    /**
     * Cancels the message delivery.
     *
     * @return This message.
     * @throws IllegalStateException If this method is called before [.send].
     */
    @CanIgnoreReturnValue
    @Synchronized
    fun cancel(): PlayerMessage {
        checkState(isSent)
        isCanceled = true
        markAsProcessed( /* isDelivered= */false)
        return this
    }

    /**
     * Marks the message as processed. Should only be called by a [Sender] and may be called
     * multiple times.
     *
     * @param isDelivered Whether the message has been delivered to its target. The message is
     * considered as being delivered when this method has been called with `isDelivered` set
     * to true at least once.
     */
    @Synchronized
    fun markAsProcessed(isDelivered: Boolean) {
        this.isDelivered = this.isDelivered or isDelivered
        isProcessed = true
        notifyAll()
    }

    /**
     * Blocks until after the message has been delivered or the player is no longer able to deliver
     * the message.
     *
     *
     * Note that this method must not be called if the current thread is the same thread used by
     * the message [looper][.getLooper] as it would cause a deadlock.
     *
     * @return Whether the message was delivered successfully.
     * @throws IllegalStateException If this method is called before [.send].
     * @throws IllegalStateException If this method is called on the same thread used by the message
     * [looper][.getLooper].
     * @throws InterruptedException If the current thread is interrupted while waiting for the message
     * to be delivered.
     */
    @Synchronized
    @Throws(InterruptedException::class)
    fun blockUntilDelivered(): Boolean {
        checkState(isSent)
        checkState(looper.thread !== Thread.currentThread())
        while (!isProcessed) {
            wait()
        }
        return isDelivered
    }

    /**
     * Blocks until after the message has been delivered or the player is no longer able to deliver
     * the message or the specified timeout elapsed.
     *
     *
     * Note that this method must not be called if the current thread is the same thread used by
     * the message [looper][.getLooper] as it would cause a deadlock.
     *
     * @param timeoutMs The timeout in milliseconds.
     * @return Whether the message was delivered successfully.
     * @throws IllegalStateException If this method is called before [.send].
     * @throws IllegalStateException If this method is called on the same thread used by the message
     * [looper][.getLooper].
     * @throws TimeoutException If the `timeoutMs` elapsed and this message has not been
     * delivered and the player is still able to deliver the message.
     * @throws InterruptedException If the current thread is interrupted while waiting for the message
     * to be delivered.
     */
    @Synchronized
    @Throws(InterruptedException::class, TimeoutException::class)
    fun blockUntilDelivered(timeoutMs: Long): Boolean {
        checkState(isSent)
        checkState(looper.thread !== Thread.currentThread())
        val deadlineMs = clock.elapsedRealtime() + timeoutMs
        var remainingMs = timeoutMs
        while (!isProcessed && remainingMs > 0) {
            clock.onThreadBlocked()
            wait(remainingMs)
            remainingMs = deadlineMs - clock.elapsedRealtime()
        }
        if (!isProcessed) {
            throw TimeoutException("Message delivery timed out.")
        }
        return isDelivered
    }
}