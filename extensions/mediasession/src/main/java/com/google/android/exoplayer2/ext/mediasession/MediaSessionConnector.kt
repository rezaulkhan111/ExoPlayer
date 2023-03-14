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
package com.google.android.exoplayer2.ext.mediasession

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.*
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Pair
import androidx.annotation.LongDef
import androidx.media.utils.MediaConstants
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.*
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.ErrorMessageProvider
import com.google.android.exoplayer2.util.Util
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

/**
 * Connects a [MediaSessionCompat] to a [Player].
 *
 *
 * This connector does *not* call [MediaSessionCompat.setActive], and so
 * application code is responsible for making the session active when desired. A session must be
 * active for transport controls to be displayed (e.g. on the lock screen) and for it to receive
 * media button events.
 *
 *
 * The connector listens for actions sent by the media session's controller and implements these
 * actions by calling appropriate player methods. The playback state of the media session is
 * automatically synced with the player. The connector can also be optionally extended by providing
 * various collaborators:
 *
 *
 *  * Actions to initiate media playback (`PlaybackStateCompat#ACTION_PREPARE_*` and `PlaybackStateCompat#ACTION_PLAY_*`) can be handled by a [PlaybackPreparer] passed to
 * [.setPlaybackPreparer].
 *  * Custom actions can be handled by passing one or more [CustomActionProvider]s to
 * [.setCustomActionProviders].
 *  * To enable a media queue and navigation within it, you can set a [QueueNavigator] by
 * calling [.setQueueNavigator]. Use of [TimelineQueueNavigator]
 * is recommended for most use cases.
 *  * To enable editing of the media queue, you can set a [QueueEditor] by calling [       ][.setQueueEditor].
 *  * A [MediaButtonEventHandler] can be set by calling [       ][.setMediaButtonEventHandler]. By default media button events are
 * handled by [MediaSessionCompat.Callback.onMediaButtonEvent].
 *  * An [ErrorMessageProvider] for providing human readable error messages and
 * corresponding error codes can be set by calling [       ][.setErrorMessageProvider].
 *  * A [MediaMetadataProvider] can be set by calling [       ][.setMediaMetadataProvider]. By default the [       ] is used.
 *
 */
class MediaSessionConnector(
        /** The wrapped [MediaSessionCompat].  */
        val mediaSession: MediaSessionCompat) {
    /** Playback actions supported by the connector.  */
    @LongDef(flag = true, value = [PlaybackStateCompat.ACTION_PLAY_PAUSE, PlaybackStateCompat.ACTION_PLAY, PlaybackStateCompat.ACTION_PAUSE, PlaybackStateCompat.ACTION_SEEK_TO, PlaybackStateCompat.ACTION_FAST_FORWARD, PlaybackStateCompat.ACTION_REWIND, PlaybackStateCompat.ACTION_STOP, PlaybackStateCompat.ACTION_SET_REPEAT_MODE, PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE, PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED])
    @Retention(RetentionPolicy.SOURCE)
    annotation class PlaybackActions

    /** Receiver of media commands sent by a media controller.  */
    interface CommandReceiver {
        /**
         * See [MediaSessionCompat.Callback.onCommand]. The
         * receiver may handle the command, but is not required to do so.
         *
         * @param player The player connected to the media session.
         * @param command The command name.
         * @param extras Optional parameters for the command, may be null.
         * @param cb A result receiver to which a result may be sent by the command, may be null.
         * @return Whether the receiver handled the command.
         */
        fun onCommand(
                player: Player, command: String, extras: Bundle?, cb: ResultReceiver?): Boolean
    }

    /** Interface to which playback preparation and play actions are delegated.  */
    interface PlaybackPreparer : CommandReceiver {
        /**
         * Returns the actions which are supported by the preparer. The supported actions must be a
         * bitmask combined out of [PlaybackStateCompat.ACTION_PREPARE], [ ][PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID], [ ][PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH], [ ][PlaybackStateCompat.ACTION_PREPARE_FROM_URI], [ ][PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID], [ ][PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH] and [ ][PlaybackStateCompat.ACTION_PLAY_FROM_URI].
         *
         * @return The bitmask of the supported media actions.
         */
        val supportedPrepareActions: Long

        /**
         * See [MediaSessionCompat.Callback.onPrepare].
         *
         * @param playWhenReady Whether playback should be started after preparation.
         */
        fun onPrepare(playWhenReady: Boolean)

        /**
         * See [MediaSessionCompat.Callback.onPrepareFromMediaId].
         *
         * @param mediaId The media id of the media item to be prepared.
         * @param playWhenReady Whether playback should be started after preparation.
         * @param extras A [Bundle] of extras passed by the media controller, may be null.
         */
        fun onPrepareFromMediaId(mediaId: String?, playWhenReady: Boolean, extras: Bundle?)

        /**
         * See [MediaSessionCompat.Callback.onPrepareFromSearch].
         *
         * @param query The search query.
         * @param playWhenReady Whether playback should be started after preparation.
         * @param extras A [Bundle] of extras passed by the media controller, may be null.
         */
        fun onPrepareFromSearch(query: String?, playWhenReady: Boolean, extras: Bundle?)

        /**
         * See [MediaSessionCompat.Callback.onPrepareFromUri].
         *
         * @param uri The [Uri] of the media item to be prepared.
         * @param playWhenReady Whether playback should be started after preparation.
         * @param extras A [Bundle] of extras passed by the media controller, may be null.
         */
        fun onPrepareFromUri(uri: Uri?, playWhenReady: Boolean, extras: Bundle?)

        companion object {
            const val ACTIONS = (PlaybackStateCompat.ACTION_PREPARE
                    or PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
                    or PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
                    or PlaybackStateCompat.ACTION_PREPARE_FROM_URI
                    or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                    or PlaybackStateCompat.ACTION_PLAY_FROM_URI)
        }
    }

    /**
     * Handles queue navigation actions, and updates the media session queue by calling `MediaSessionCompat.setQueue()`.
     */
    interface QueueNavigator : CommandReceiver {
        /**
         * Returns the actions which are supported by the navigator. The supported actions must be a
         * bitmask combined out of [PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM], [ ][PlaybackStateCompat.ACTION_SKIP_TO_NEXT], [ ][PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS].
         *
         * @param player The player connected to the media session.
         * @return The bitmask of the supported media actions.
         */
        fun getSupportedQueueNavigatorActions(player: Player): Long

        /**
         * Called when the timeline of the player has changed.
         *
         * @param player The player connected to the media session.
         */
        fun onTimelineChanged(player: Player)

        /**
         * Called when the current media item index changed.
         *
         * @param player The player connected to the media session.
         */
        fun onCurrentMediaItemIndexChanged(player: Player) {}

        /**
         * Gets the id of the currently active queue item, or [ ][MediaSessionCompat.QueueItem.UNKNOWN_ID] if the active item is unknown.
         *
         *
         * To let the connector publish metadata for the active queue item, the queue item with the
         * returned id must be available in the list of items returned by [ ][MediaControllerCompat.getQueue].
         *
         * @param player The player connected to the media session.
         * @return The id of the active queue item.
         */
        fun getActiveQueueItemId(player: Player?): Long

        /**
         * See [MediaSessionCompat.Callback.onSkipToPrevious].
         *
         * @param player The player connected to the media session.
         */
        fun onSkipToPrevious(player: Player?)

        /**
         * See [MediaSessionCompat.Callback.onSkipToQueueItem].
         *
         * @param player The player connected to the media session.
         */
        fun onSkipToQueueItem(player: Player?, id: Long)

        /**
         * See [MediaSessionCompat.Callback.onSkipToNext].
         *
         * @param player The player connected to the media session.
         */
        fun onSkipToNext(player: Player?)

        companion object {
            const val ACTIONS = (PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        }
    }

    /** Handles media session queue edits.  */
    interface QueueEditor : CommandReceiver {
        /**
         * See [MediaSessionCompat.Callback.onAddQueueItem].
         */
        fun onAddQueueItem(player: Player?, description: MediaDescriptionCompat?)

        /**
         * See [MediaSessionCompat.Callback.onAddQueueItem].
         */
        fun onAddQueueItem(player: Player?, description: MediaDescriptionCompat?, index: Int)

        /**
         * See [MediaSessionCompat.Callback.onRemoveQueueItem].
         */
        fun onRemoveQueueItem(player: Player?, description: MediaDescriptionCompat)
    }

    /** Callback receiving a user rating for the active media item.  */
    interface RatingCallback : CommandReceiver {
        /** See [MediaSessionCompat.Callback.onSetRating].  */
        fun onSetRating(player: Player?, rating: RatingCompat?)

        /** See [MediaSessionCompat.Callback.onSetRating].  */
        fun onSetRating(player: Player?, rating: RatingCompat?, extras: Bundle?)
    }

    /** Handles requests for enabling or disabling captions.  */
    interface CaptionCallback : CommandReceiver {
        /** See [MediaSessionCompat.Callback.onSetCaptioningEnabled].  */
        fun onSetCaptioningEnabled(player: Player?, enabled: Boolean)

        /**
         * Returns whether the media currently being played has captions.
         *
         *
         * This method is called each time the media session playback state needs to be updated and
         * published upon a player state change.
         */
        fun hasCaptions(player: Player?): Boolean
    }

    /** Handles a media button event.  */
    interface MediaButtonEventHandler {
        /**
         * See [MediaSessionCompat.Callback.onMediaButtonEvent].
         *
         * @param player The [Player].
         * @param mediaButtonEvent The [Intent].
         * @return True if the event was handled, false otherwise.
         */
        fun onMediaButtonEvent(player: Player?, mediaButtonEvent: Intent?): Boolean
    }

    /**
     * Provides a [PlaybackStateCompat.CustomAction] to be published and handles the action when
     * sent by a media controller.
     */
    interface CustomActionProvider {
        /**
         * Called when a custom action provided by this provider is sent to the media session.
         *
         * @param player The player connected to the media session.
         * @param action The name of the action which was sent by a media controller.
         * @param extras Optional extras sent by a media controller, may be null.
         */
        fun onCustomAction(player: Player, action: String?, extras: Bundle?)

        /**
         * Returns a [PlaybackStateCompat.CustomAction] which will be published to the media
         * session by the connector or `null` if this action should not be published at the given
         * player state.
         *
         * @param player The player connected to the media session.
         * @return The custom action to be included in the session playback state or `null`.
         */
        fun getCustomAction(player: Player): PlaybackStateCompat.CustomAction?
    }

    /** Provides a [MediaMetadataCompat] for a given player state.  */
    interface MediaMetadataProvider {
        /**
         * Gets the [MediaMetadataCompat] to be published to the session.
         *
         *
         * An app may need to load metadata resources like artwork bitmaps asynchronously. In such a
         * case the app should return a [MediaMetadataCompat] object that does not contain these
         * resources as a placeholder. The app should start an asynchronous operation to download the
         * bitmap and put it into a cache. Finally, the app should call [ ][.invalidateMediaSessionMetadata]. This causes this callback to be called again and the app
         * can now return a [MediaMetadataCompat] object with all the resources included.
         *
         * @param player The player connected to the media session.
         * @return The [MediaMetadataCompat] to be published to the session.
         */
        fun getMetadata(player: Player): MediaMetadataCompat

        /** Returns whether the old and the new metadata are considered the same.  */
        fun sameAs(oldMetadata: MediaMetadataCompat, newMetadata: MediaMetadataCompat): Boolean {
            if (oldMetadata == newMetadata) {
                return true
            }
            if (oldMetadata.size() != newMetadata.size()) {
                return false
            }
            val oldKeySet = oldMetadata.keySet()
            val oldMetadataBundle = oldMetadata.bundle
            val newMetadataBundle = newMetadata.bundle
            for (key in oldKeySet) {
                val oldProperty = oldMetadataBundle[key]
                val newProperty = newMetadataBundle[key]
                if (oldProperty === newProperty) {
                    continue
                }
                if (oldProperty is Bitmap && newProperty is Bitmap) {
                    if (!oldProperty.sameAs(newProperty as Bitmap?)) {
                        return false
                    }
                } else if (oldProperty is RatingCompat && newProperty is RatingCompat) {
                    val oldRating = oldProperty
                    val newRating = newProperty
                    if (oldRating.hasHeart() != newRating.hasHeart() || oldRating.isRated != newRating.isRated || oldRating.isThumbUp != newRating.isThumbUp || oldRating.percentRating != newRating.percentRating || oldRating.starRating != newRating.starRating || oldRating.ratingStyle != newRating.ratingStyle) {
                        return false
                    }
                } else if (!Util.areEqual(oldProperty, newProperty)) {
                    return false
                }
            }
            return true
        }
    }

    private val looper: Looper
    private val componentListener: ComponentListener
    private val commandReceivers: ArrayList<CommandReceiver>
    private val customCommandReceivers: ArrayList<CommandReceiver>
    private var customActionProviders: Array<CustomActionProvider?>
    private var customActionMap: Map<String, CustomActionProvider?>
    private var mediaMetadataProvider: MediaMetadataProvider?
    private var player: Player? = null
    private var errorMessageProvider: ErrorMessageProvider<in PlaybackException?>? = null
    private var customError: Pair<Int, CharSequence>? = null
    private var customErrorExtras: Bundle? = null
    private var playbackPreparer: PlaybackPreparer? = null
    private var queueNavigator: QueueNavigator? = null
    private var queueEditor: QueueEditor? = null
    private var ratingCallback: RatingCallback? = null
    private var captionCallback: CaptionCallback? = null
    private var mediaButtonEventHandler: MediaButtonEventHandler? = null
    private var enabledPlaybackActions: Long
    private var metadataDeduplicationEnabled = false
    private var dispatchUnsupportedActionsEnabled = false
    private var clearMediaItemsOnStop: Boolean
    private var mapIdleToStopped = false

    /**
     * Creates an instance.
     *
     * @param mediaSession The [MediaSessionCompat] to connect to.
     */
    init {
        looper = Util.getCurrentOrMainLooper()
        componentListener = ComponentListener()
        commandReceivers = ArrayList()
        customCommandReceivers = ArrayList()
        customActionProviders = arrayOfNulls(0)
        customActionMap = emptyMap<String, CustomActionProvider>()
        mediaMetadataProvider = DefaultMediaMetadataProvider(
                mediaSession.controller,  /* metadataExtrasPrefix= */null)
        enabledPlaybackActions = DEFAULT_PLAYBACK_ACTIONS
        mediaSession.setFlags(BASE_MEDIA_SESSION_FLAGS)
        mediaSession.setCallback(componentListener, Handler(looper))
        clearMediaItemsOnStop = true
    }

    /**
     * Sets the player to be connected to the media session. Must be called on the same thread that is
     * used to access the player.
     *
     * @param player The player to be connected to the `MediaSession`, or `null` to
     * disconnect the current player.
     */
    fun setPlayer(player: Player?) {
        Assertions.checkArgument(player == null || player.applicationLooper == looper)
        if (this.player != null) {
            this.player!!.removeListener(componentListener)
        }
        this.player = player
        player?.addListener(componentListener)
        invalidateMediaSessionPlaybackState()
        invalidateMediaSessionMetadata()
    }

    /**
     * Sets the [PlaybackPreparer].
     *
     * @param playbackPreparer The [PlaybackPreparer].
     */
    fun setPlaybackPreparer(playbackPreparer: PlaybackPreparer?) {
        if (this.playbackPreparer !== playbackPreparer) {
            unregisterCommandReceiver(this.playbackPreparer)
            this.playbackPreparer = playbackPreparer
            registerCommandReceiver(playbackPreparer)
            invalidateMediaSessionPlaybackState()
        }
    }

    /**
     * Sets the [MediaButtonEventHandler]. Pass `null` if the media button event should be
     * handled by [MediaSessionCompat.Callback.onMediaButtonEvent].
     *
     *
     * Please note that prior to API 21 MediaButton events are not delivered to the [ ]. Instead they are delivered as key events (see ['Responding to media
 * buttons'](https://developer.android.com/guide/topics/media-apps/mediabuttons)). In an [Activity][android.app.Activity], media button events arrive at the
     * [android.app.Activity.dispatchKeyEvent] method.
     *
     *
     * If you are running the player in a foreground service (prior to API 21), you can create an
     * intent filter and handle the `android.intent.action.MEDIA_BUTTON` action yourself. See [
 * Service handling ACTION_MEDIA_BUTTON](https://developer.android.com/reference/androidx/media/session/MediaButtonReceiver#service-handling-action_media_button) for more information.
     *
     * @param mediaButtonEventHandler The [MediaButtonEventHandler], or null to let the event be
     * handled by [MediaSessionCompat.Callback.onMediaButtonEvent].
     */
    fun setMediaButtonEventHandler(
            mediaButtonEventHandler: MediaButtonEventHandler?) {
        this.mediaButtonEventHandler = mediaButtonEventHandler
    }

    /**
     * Sets the enabled playback actions.
     *
     * @param enabledPlaybackActions The enabled playback actions.
     */
    fun setEnabledPlaybackActions(@PlaybackActions enabledPlaybackActions: Long) {
        var enabledPlaybackActions = enabledPlaybackActions
        enabledPlaybackActions = enabledPlaybackActions and ALL_PLAYBACK_ACTIONS
        if (this.enabledPlaybackActions != enabledPlaybackActions) {
            this.enabledPlaybackActions = enabledPlaybackActions
            invalidateMediaSessionPlaybackState()
        }
    }

    /**
     * Sets the optional [ErrorMessageProvider].
     *
     * @param errorMessageProvider The error message provider.
     */
    fun setErrorMessageProvider(
            errorMessageProvider: ErrorMessageProvider<in PlaybackException?>?) {
        if (this.errorMessageProvider !== errorMessageProvider) {
            this.errorMessageProvider = errorMessageProvider
            invalidateMediaSessionPlaybackState()
        }
    }

    /**
     * Sets the [QueueNavigator] to handle queue navigation actions `ACTION_SKIP_TO_NEXT`,
     * `ACTION_SKIP_TO_PREVIOUS` and `ACTION_SKIP_TO_QUEUE_ITEM`.
     *
     * @param queueNavigator The queue navigator.
     */
    fun setQueueNavigator(queueNavigator: QueueNavigator?) {
        if (this.queueNavigator !== queueNavigator) {
            unregisterCommandReceiver(this.queueNavigator)
            this.queueNavigator = queueNavigator
            registerCommandReceiver(queueNavigator)
        }
    }

    /**
     * Sets the [QueueEditor] to handle queue edits sent by the media controller.
     *
     * @param queueEditor The queue editor.
     */
    fun setQueueEditor(queueEditor: QueueEditor?) {
        if (this.queueEditor !== queueEditor) {
            unregisterCommandReceiver(this.queueEditor)
            this.queueEditor = queueEditor
            registerCommandReceiver(queueEditor)
            mediaSession.setFlags(
                    if (queueEditor == null) BASE_MEDIA_SESSION_FLAGS else EDITOR_MEDIA_SESSION_FLAGS)
        }
    }

    /**
     * Sets the [RatingCallback] to handle user ratings.
     *
     * @param ratingCallback The rating callback.
     */
    fun setRatingCallback(ratingCallback: RatingCallback?) {
        if (this.ratingCallback !== ratingCallback) {
            unregisterCommandReceiver(this.ratingCallback)
            this.ratingCallback = ratingCallback
            registerCommandReceiver(this.ratingCallback)
        }
    }

    /**
     * Sets the [CaptionCallback] to handle requests to enable or disable captions.
     *
     * @param captionCallback The caption callback.
     */
    fun setCaptionCallback(captionCallback: CaptionCallback?) {
        if (this.captionCallback !== captionCallback) {
            unregisterCommandReceiver(this.captionCallback)
            this.captionCallback = captionCallback
            registerCommandReceiver(this.captionCallback)
        }
    }

    /**
     * Sets a custom error on the session.
     *
     *
     * This sets the error code via [PlaybackStateCompat.Builder.setErrorMessage]. By default, the error code will be set to [ ][PlaybackStateCompat.ERROR_CODE_APP_ERROR].
     *
     * @param message The error string to report or `null` to clear the error.
     */
    fun setCustomErrorMessage(message: CharSequence?) {
        val code = if (message == null) 0 else PlaybackStateCompat.ERROR_CODE_APP_ERROR
        setCustomErrorMessage(message, code)
    }

    /**
     * Sets a custom error on the session.
     *
     * @param message The error string to report or `null` to clear the error.
     * @param code The error code to report. Ignored when `message` is `null`.
     */
    fun setCustomErrorMessage(message: CharSequence?, code: Int) {
        setCustomErrorMessage(message, code,  /* extras= */null)
    }

    /**
     * Sets a custom error on the session.
     *
     * @param message The error string to report or `null` to clear the error.
     * @param code The error code to report. Ignored when `message` is `null`.
     * @param extras Extras to include in reported [PlaybackStateCompat].
     */
    fun setCustomErrorMessage(
            message: CharSequence?, code: Int, extras: Bundle?) {
        customError = if (message == null) null else Pair(code, message)
        customErrorExtras = if (message == null) null else extras
        invalidateMediaSessionPlaybackState()
    }

    /**
     * Sets custom action providers. The order of the [CustomActionProvider]s determines the
     * order in which the actions are published.
     *
     * @param customActionProviders The custom action providers, or null to remove all existing custom
     * action providers.
     */
    fun setCustomActionProviders(vararg customActionProviders: CustomActionProvider?) {
        this.customActionProviders = customActionProviders ?: arrayOfNulls(0)
        invalidateMediaSessionPlaybackState()
    }

    /**
     * Sets a provider of metadata to be published to the media session. Pass `null` if no
     * metadata should be published.
     *
     * @param mediaMetadataProvider The provider of metadata to publish, or `null` if no
     * metadata should be published.
     */
    fun setMediaMetadataProvider(mediaMetadataProvider: MediaMetadataProvider?) {
        if (this.mediaMetadataProvider !== mediaMetadataProvider) {
            this.mediaMetadataProvider = mediaMetadataProvider
            invalidateMediaSessionMetadata()
        }
    }

    /**
     * Sets whether actions that are not advertised to the [MediaSessionCompat] will be
     * dispatched either way. Default value is false.
     */
    fun setDispatchUnsupportedActionsEnabled(dispatchUnsupportedActionsEnabled: Boolean) {
        this.dispatchUnsupportedActionsEnabled = dispatchUnsupportedActionsEnabled
    }

    /**
     * Sets whether media items are cleared from the playlist when a client sends a [ ][MediaControllerCompat.TransportControls.stop] command.
     */
    fun setClearMediaItemsOnStop(clearMediaItemsOnStop: Boolean) {
        this.clearMediaItemsOnStop = clearMediaItemsOnStop
    }

    /**
     * Sets whether [Player.STATE_IDLE] should be mapped to [ ][PlaybackStateCompat.STATE_STOPPED]. The default is false [Player.STATE_IDLE] which maps
     * to [PlaybackStateCompat.STATE_NONE].
     */
    fun setMapStateIdleToSessionStateStopped(mapIdleToStopped: Boolean) {
        this.mapIdleToStopped = mapIdleToStopped
    }

    /**
     * Sets whether [MediaMetadataProvider.sameAs]
     * should be consulted before calling [MediaSessionCompat.setMetadata].
     *
     *
     * Note that this comparison is normally only required when you are using media sources that
     * may introduce duplicate updates of the metadata for the same media item (e.g. live streams).
     *
     * @param metadataDeduplicationEnabled Whether to deduplicate metadata objects on invalidation.
     */
    fun setMetadataDeduplicationEnabled(metadataDeduplicationEnabled: Boolean) {
        this.metadataDeduplicationEnabled = metadataDeduplicationEnabled
    }

    /**
     * Updates the metadata of the media session.
     *
     *
     * Apps normally only need to call this method when the backing data for a given media item has
     * changed and the metadata should be updated immediately.
     *
     *
     * The [MediaMetadataCompat] which is published to the session is obtained by calling
     * [MediaMetadataProvider.getMetadata].
     */
    fun invalidateMediaSessionMetadata() {
        val metadata = if (mediaMetadataProvider != null && player != null) mediaMetadataProvider!!.getMetadata(player!!) else METADATA_EMPTY
        val mediaMetadataProvider = mediaMetadataProvider
        if (metadataDeduplicationEnabled && mediaMetadataProvider != null) {
            val oldMetadata = mediaSession.controller.metadata
            if (oldMetadata != null && mediaMetadataProvider.sameAs(oldMetadata, metadata)) {
                // Do not update if metadata did not change.
                return
            }
        }
        mediaSession.setMetadata(metadata)
    }

    /**
     * Updates the playback state of the media session.
     *
     *
     * Apps normally only need to call this method when the custom actions provided by a [ ] changed and the playback state needs to be updated immediately.
     */
    fun invalidateMediaSessionPlaybackState() {
        val builder = PlaybackStateCompat.Builder()
        val player = player
        if (player == null) {
            builder
                    .setActions(buildPrepareActions())
                    .setState(
                            PlaybackStateCompat.STATE_NONE,  /* position= */
                            0,  /* playbackSpeed= */
                            0f,  /* updateTime= */
                            SystemClock.elapsedRealtime())
            mediaSession.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
            mediaSession.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
            mediaSession.setPlaybackState(builder.build())
            return
        }
        val currentActions: MutableMap<String, CustomActionProvider?> = HashMap()
        for (customActionProvider in customActionProviders) {
            val customAction = customActionProvider!!.getCustomAction(player)
            if (customAction != null) {
                currentActions[customAction.action] = customActionProvider
                builder.addCustomAction(customAction)
            }
        }
        customActionMap = Collections.unmodifiableMap(currentActions)
        val extras = Bundle()
        val playbackError = player.playerError
        val reportError = playbackError != null || customError != null
        val sessionPlaybackState = if (reportError) PlaybackStateCompat.STATE_ERROR else getMediaSessionPlaybackState(player.playbackState, player.playWhenReady)
        if (customError != null) {
            builder.setErrorMessage(customError!!.first, customError!!.second)
            if (customErrorExtras != null) {
                extras.putAll(customErrorExtras)
            }
        } else if (playbackError != null && errorMessageProvider != null) {
            val message = errorMessageProvider!!.getErrorMessage(playbackError)
            builder.setErrorMessage(message.first, message.second)
        }
        val activeQueueItemId = if (queueNavigator != null) queueNavigator!!.getActiveQueueItemId(player) else MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
        val playbackSpeed = player.playbackParameters.speed
        extras.putFloat(EXTRAS_SPEED, playbackSpeed)
        val sessionPlaybackSpeed = if (player.isPlaying) playbackSpeed else 0f
        val currentMediaItem = player.currentMediaItem
        if (currentMediaItem != null && MediaItem.DEFAULT_MEDIA_ID != currentMediaItem.mediaId) {
            extras.putString(MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID, currentMediaItem.mediaId)
        }
        builder
                .setActions(buildPrepareActions() or buildPlaybackActions(player))
                .setActiveQueueItemId(activeQueueItemId)
                .setBufferedPosition(player.bufferedPosition)
                .setState(
                        sessionPlaybackState,
                        player.currentPosition,
                        sessionPlaybackSpeed,  /* updateTime= */
                        SystemClock.elapsedRealtime())
                .setExtras(extras)
        val repeatMode = player.repeatMode
        mediaSession.setRepeatMode(
                if (repeatMode == Player.REPEAT_MODE_ONE) PlaybackStateCompat.REPEAT_MODE_ONE else if (repeatMode == Player.REPEAT_MODE_ALL) PlaybackStateCompat.REPEAT_MODE_ALL else PlaybackStateCompat.REPEAT_MODE_NONE)
        mediaSession.setShuffleMode(
                if (player.shuffleModeEnabled) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
        mediaSession.setPlaybackState(builder.build())
    }

    /**
     * Updates the queue of the media session by calling [ ][QueueNavigator.onTimelineChanged].
     *
     *
     * Apps normally only need to call this method when the backing data for a given queue item has
     * changed and the queue should be updated immediately.
     */
    fun invalidateMediaSessionQueue() {
        if (queueNavigator != null && player != null) {
            queueNavigator!!.onTimelineChanged(player!!)
        }
    }

    /**
     * Registers a custom command receiver for responding to commands delivered via [ ][MediaSessionCompat.Callback.onCommand].
     *
     *
     * Commands are only dispatched to this receiver when a player is connected.
     *
     * @param commandReceiver The command receiver to register.
     */
    fun registerCustomCommandReceiver(commandReceiver: CommandReceiver?) {
        if (commandReceiver != null && !customCommandReceivers.contains(commandReceiver)) {
            customCommandReceivers.add(commandReceiver)
        }
    }

    /**
     * Unregisters a previously registered custom command receiver.
     *
     * @param commandReceiver The command receiver to unregister.
     */
    fun unregisterCustomCommandReceiver(commandReceiver: CommandReceiver?) {
        if (commandReceiver != null) {
            customCommandReceivers.remove(commandReceiver)
        }
    }

    private fun registerCommandReceiver(commandReceiver: CommandReceiver?) {
        if (commandReceiver != null && !commandReceivers.contains(commandReceiver)) {
            commandReceivers.add(commandReceiver)
        }
    }

    private fun unregisterCommandReceiver(commandReceiver: CommandReceiver?) {
        if (commandReceiver != null) {
            commandReceivers.remove(commandReceiver)
        }
    }

    private fun buildPrepareActions(): Long {
        return if (playbackPreparer == null) 0 else PlaybackPreparer.ACTIONS and playbackPreparer!!.supportedPrepareActions
    }

    private fun buildPlaybackActions(player: Player): Long {
        val enableSeeking = player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        val enableRewind = player.isCommandAvailable(Player.COMMAND_SEEK_BACK)
        val enableFastForward = player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD)
        var enableSetRating = false
        var enableSetCaptioningEnabled = false
        val timeline = player.currentTimeline
        if (!timeline.isEmpty && !player.isPlayingAd) {
            enableSetRating = ratingCallback != null
            enableSetCaptioningEnabled = captionCallback != null && captionCallback!!.hasCaptions(player)
        }
        var playbackActions = BASE_PLAYBACK_ACTIONS
        if (enableSeeking) {
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_SEEK_TO
        }
        if (enableFastForward) {
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_FAST_FORWARD
        }
        if (enableRewind) {
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_REWIND
        }
        playbackActions = playbackActions and enabledPlaybackActions
        var actions = playbackActions
        if (queueNavigator != null) {
            actions = actions or (QueueNavigator.ACTIONS and queueNavigator!!.getSupportedQueueNavigatorActions(player))
        }
        if (enableSetRating) {
            actions = actions or PlaybackStateCompat.ACTION_SET_RATING
        }
        if (enableSetCaptioningEnabled) {
            actions = actions or PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED
        }
        return actions
    }

    @EnsuresNonNullIf(result = true, expression = ["player"])
    private fun canDispatchPlaybackAction(action: Long): Boolean {
        return (player != null
                && (enabledPlaybackActions and action != 0L || dispatchUnsupportedActionsEnabled))
    }

    @EnsuresNonNullIf(result = true, expression = ["playbackPreparer"])
    private fun canDispatchToPlaybackPreparer(action: Long): Boolean {
        return (playbackPreparer != null
                && (playbackPreparer!!.supportedPrepareActions and action != 0L
                || dispatchUnsupportedActionsEnabled))
    }

    @EnsuresNonNullIf(result = true, expression = ["player", "queueNavigator"])
    private fun canDispatchToQueueNavigator(action: Long): Boolean {
        return player != null && queueNavigator != null && (queueNavigator!!.getSupportedQueueNavigatorActions(player!!) and action != 0L
                || dispatchUnsupportedActionsEnabled)
    }

    @EnsuresNonNullIf(result = true, expression = ["player", "ratingCallback"])
    private fun canDispatchSetRating(): Boolean {
        return player != null && ratingCallback != null
    }

    @EnsuresNonNullIf(result = true, expression = ["player", "captionCallback"])
    private fun canDispatchSetCaptioningEnabled(): Boolean {
        return player != null && captionCallback != null
    }

    @EnsuresNonNullIf(result = true, expression = ["player", "queueEditor"])
    private fun canDispatchQueueEdit(): Boolean {
        return player != null && queueEditor != null
    }

    @EnsuresNonNullIf(result = true, expression = ["player", "mediaButtonEventHandler"])
    private fun canDispatchMediaButtonEvent(): Boolean {
        return player != null && mediaButtonEventHandler != null
    }

    private fun seekTo(player: Player?, mediaItemIndex: Int, positionMs: Long) {
        player!!.seekTo(mediaItemIndex, positionMs)
    }

    private fun getMediaSessionPlaybackState(
            exoPlayerPlaybackState: @Player.State Int, playWhenReady: Boolean): Int {
        return when (exoPlayerPlaybackState) {
            Player.STATE_BUFFERING -> if (playWhenReady) PlaybackStateCompat.STATE_BUFFERING else PlaybackStateCompat.STATE_PAUSED
            Player.STATE_READY -> if (playWhenReady) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            Player.STATE_IDLE -> if (mapIdleToStopped) PlaybackStateCompat.STATE_STOPPED else PlaybackStateCompat.STATE_NONE
            else -> if (mapIdleToStopped) PlaybackStateCompat.STATE_STOPPED else PlaybackStateCompat.STATE_NONE
        }
    }

    /**
     * Provides a default [MediaMetadataCompat] with properties and extras taken from the [ ] of the [MediaSessionCompat.QueueItem] of the active queue item.
     */
    class DefaultMediaMetadataProvider(
            private val mediaController: MediaControllerCompat, metadataExtrasPrefix: String?) : MediaMetadataProvider {
        private val metadataExtrasPrefix: String

        /**
         * Creates a new instance.
         *
         * @param mediaController The [MediaControllerCompat].
         * @param metadataExtrasPrefix A string to prefix extra keys which are propagated from the
         * active queue item to the session metadata.
         */
        init {
            this.metadataExtrasPrefix = metadataExtrasPrefix ?: ""
        }

        override fun getMetadata(player: Player): MediaMetadataCompat {
            if (player.currentTimeline.isEmpty) {
                return METADATA_EMPTY
            }
            val builder = MediaMetadataCompat.Builder()
            if (player.isPlayingAd) {
                builder.putLong(MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT, 1)
            }
            builder.putLong(
                    MediaMetadataCompat.METADATA_KEY_DURATION,
                    if (player.isCurrentMediaItemDynamic || player.duration == C.TIME_UNSET) -1 else player.duration)
            val activeQueueItemId = mediaController.playbackState.activeQueueItemId
            if (activeQueueItemId != MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()) {
                val queue = mediaController.queue
                var i = 0
                while (queue != null && i < queue.size) {
                    val queueItem = queue[i]
                    if (queueItem.queueId == activeQueueItemId) {
                        val description = queueItem.description
                        val extras = description.extras
                        if (extras != null) {
                            for (key in extras.keySet()) {
                                val value = extras[key]
                                if (value is String) {
                                    builder.putString(metadataExtrasPrefix + key, value as String?)
                                } else if (value is CharSequence) {
                                    builder.putText(metadataExtrasPrefix + key, value as CharSequence?)
                                } else if (value is Long) {
                                    builder.putLong(metadataExtrasPrefix + key, (value as Long?)!!)
                                } else if (value is Int) {
                                    builder.putLong(metadataExtrasPrefix + key, value as kotlin.Int?. toLong ())
                                } else if (value is Bitmap) {
                                    builder.putBitmap(metadataExtrasPrefix + key, value as Bitmap?)
                                } else if (value is RatingCompat) {
                                    builder.putRating(metadataExtrasPrefix + key, value as RatingCompat?)
                                }
                            }
                        }
                        val title = description.title
                        if (title != null) {
                            val titleString = title.toString()
                            builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleString)
                            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, titleString)
                        }
                        val subtitle = description.subtitle
                        if (subtitle != null) {
                            builder.putString(
                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle.toString())
                        }
                        val displayDescription = description.description
                        if (displayDescription != null) {
                            builder.putString(
                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, displayDescription.toString())
                        }
                        val iconBitmap = description.iconBitmap
                        if (iconBitmap != null) {
                            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, iconBitmap)
                        }
                        val iconUri = description.iconUri
                        if (iconUri != null) {
                            builder.putString(
                                    MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, iconUri.toString())
                        }
                        val mediaId = description.mediaId
                        if (mediaId != null) {
                            builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
                        }
                        val mediaUri = description.mediaUri
                        if (mediaUri != null) {
                            builder.putString(
                                    MediaMetadataCompat.METADATA_KEY_MEDIA_URI, mediaUri.toString())
                        }
                        break
                    }
                    i++
                }
            }
            return builder.build()
        }
    }

    private inner class ComponentListener : MediaSessionCompat.Callback(), Player.Listener {
        private var currentMediaItemIndex = 0
        private var currentWindowCount = 0

        // Player.Listener implementation.
        override fun onEvents(player: Player, events: Player.Events) {
            var invalidatePlaybackState = false
            var invalidateMetadata = false
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                if (currentMediaItemIndex != player.currentMediaItemIndex) {
                    if (queueNavigator != null) {
                        queueNavigator!!.onCurrentMediaItemIndexChanged(player)
                    }
                    invalidateMetadata = true
                }
                invalidatePlaybackState = true
            }
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                val windowCount = player.currentTimeline.windowCount
                val mediaItemIndex = player.currentMediaItemIndex
                if (queueNavigator != null) {
                    queueNavigator!!.onTimelineChanged(player)
                    invalidatePlaybackState = true
                } else if (currentWindowCount != windowCount || currentMediaItemIndex != mediaItemIndex) {
                    // active queue item and queue navigation actions may need to be updated
                    invalidatePlaybackState = true
                }
                currentWindowCount = windowCount
                invalidateMetadata = true
            }

            // Update currentMediaItemIndex after comparisons above.
            currentMediaItemIndex = player.currentMediaItemIndex
            if (events.containsAny(
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_IS_PLAYING_CHANGED,
                            Player.EVENT_REPEAT_MODE_CHANGED,
                            Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                invalidatePlaybackState = true
            }

            // The queue needs to be updated by the queue navigator first. The queue navigator also
            // delivers the active queue item that is used to update the playback state.
            if (events.containsAny(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                invalidateMediaSessionQueue()
                invalidatePlaybackState = true
            }
            // Invalidate the playback state before invalidating metadata because the active queue item of
            // the session playback state needs to be updated before the MediaMetadataProvider uses it.
            if (invalidatePlaybackState) {
                invalidateMediaSessionPlaybackState()
            }
            if (invalidateMetadata) {
                invalidateMediaSessionMetadata()
            }
        }

        // MediaSessionCompat.Callback implementation.
        override fun onPlay() {
            if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_PLAY)) {
                if (player!!.playbackState == Player.STATE_IDLE) {
                    if (playbackPreparer != null) {
                        playbackPreparer!!.onPrepare( /* playWhenReady= */true)
                    } else {
                        player!!.prepare()
                    }
                } else if (player!!.playbackState == Player.STATE_ENDED) {
                    seekTo(player, player!!.currentMediaItemIndex, C.TIME_UNSET)
                }
                Assertions.checkNotNull(player).play()
            }
        }

        override fun onPause() {
            if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_PAUSE)) {
                player!!.pause()
            }
        }

        override fun onSeekTo(positionMs: Long) {
            if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SEEK_TO)) {
                seekTo(player, player!!.currentMediaItemIndex, positionMs)
            }
        }

        override fun onFastForward() {
            if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_FAST_FORWARD)) {
                player!!.seekForward()
            }
        }

        override fun onRewind() {
            if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_REWIND)) {
                player!!.seekBack()
            }
        }

        override fun onStop() {
            if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_STOP)) {
                player!!.stop()
                if (clearMediaItemsOnStop) {
                    player!!.clearMediaItems()
                }
            }
        }

        override fun onSetShuffleMode(@PlaybackStateCompat.ShuffleMode shuffleMode: Int) {
            if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)) {
                val shuffleModeEnabled: Boolean
                shuffleModeEnabled = when (shuffleMode) {
                    PlaybackStateCompat.SHUFFLE_MODE_ALL, PlaybackStateCompat.SHUFFLE_MODE_GROUP -> true
                    PlaybackStateCompat.SHUFFLE_MODE_NONE, PlaybackStateCompat.SHUFFLE_MODE_INVALID -> false
                    else -> false
                }
                player!!.shuffleModeEnabled = shuffleModeEnabled
            }
        }

        override fun onSetRepeatMode(@PlaybackStateCompat.RepeatMode mediaSessionRepeatMode: Int) {
            if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SET_REPEAT_MODE)) {
                val repeatMode: @Player.RepeatMode Int
                repeatMode = when (mediaSessionRepeatMode) {
                    PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> Player.REPEAT_MODE_ALL
                    PlaybackStateCompat.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                    PlaybackStateCompat.REPEAT_MODE_NONE, PlaybackStateCompat.REPEAT_MODE_INVALID -> Player.REPEAT_MODE_OFF
                    else -> Player.REPEAT_MODE_OFF
                }
                player!!.repeatMode = repeatMode
            }
        }

        override fun onSetPlaybackSpeed(speed: Float) {
            if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED) && speed > 0) {
                player!!.playbackParameters = player!!.playbackParameters.withSpeed(speed)
            }
        }

        override fun onSkipToNext() {
            if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)) {
                queueNavigator!!.onSkipToNext(player)
            }
        }

        override fun onSkipToPrevious() {
            if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)) {
                queueNavigator!!.onSkipToPrevious(player)
            }
        }

        override fun onSkipToQueueItem(id: Long) {
            if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)) {
                queueNavigator!!.onSkipToQueueItem(player, id)
            }
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
            if (player != null && customActionMap.containsKey(action)) {
                customActionMap[action]!!.onCustomAction(player!!, action, extras)
                invalidateMediaSessionPlaybackState()
            }
        }

        override fun onCommand(command: String, extras: Bundle?, cb: ResultReceiver?) {
            if (player != null) {
                for (i in commandReceivers.indices) {
                    if (commandReceivers[i].onCommand(player!!, command, extras, cb)) {
                        return
                    }
                }
                for (i in customCommandReceivers.indices) {
                    if (customCommandReceivers[i].onCommand(player!!, command, extras, cb)) {
                        return
                    }
                }
            }
        }

        override fun onPrepare() {
            if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE)) {
                playbackPreparer!!.onPrepare( /* playWhenReady= */false)
            }
        }

        override fun onPrepareFromMediaId(mediaId: String, extras: Bundle?) {
            if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID)) {
                playbackPreparer!!.onPrepareFromMediaId(mediaId,  /* playWhenReady= */false, extras)
            }
        }

        override fun onPrepareFromSearch(query: String, extras: Bundle?) {
            if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH)) {
                playbackPreparer!!.onPrepareFromSearch(query,  /* playWhenReady= */false, extras)
            }
        }

        override fun onPrepareFromUri(uri: Uri, extras: Bundle?) {
            if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_URI)) {
                playbackPreparer!!.onPrepareFromUri(uri,  /* playWhenReady= */false, extras)
            }
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)) {
                playbackPreparer!!.onPrepareFromMediaId(mediaId,  /* playWhenReady= */true, extras)
            }
        }

        override fun onPlayFromSearch(query: String, extras: Bundle?) {
            if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH)) {
                playbackPreparer!!.onPrepareFromSearch(query,  /* playWhenReady= */true, extras)
            }
        }

        override fun onPlayFromUri(uri: Uri, extras: Bundle?) {
            if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_URI)) {
                playbackPreparer!!.onPrepareFromUri(uri,  /* playWhenReady= */true, extras)
            }
        }

        override fun onSetRating(rating: RatingCompat) {
            if (canDispatchSetRating()) {
                ratingCallback!!.onSetRating(player, rating)
            }
        }

        override fun onSetRating(rating: RatingCompat, extras: Bundle?) {
            if (canDispatchSetRating()) {
                ratingCallback!!.onSetRating(player, rating, extras)
            }
        }

        override fun onAddQueueItem(description: MediaDescriptionCompat) {
            if (canDispatchQueueEdit()) {
                queueEditor!!.onAddQueueItem(player, description)
            }
        }

        override fun onAddQueueItem(description: MediaDescriptionCompat, index: Int) {
            if (canDispatchQueueEdit()) {
                queueEditor!!.onAddQueueItem(player, description, index)
            }
        }

        override fun onRemoveQueueItem(description: MediaDescriptionCompat) {
            if (canDispatchQueueEdit()) {
                queueEditor!!.onRemoveQueueItem(player, description)
            }
        }

        override fun onSetCaptioningEnabled(enabled: Boolean) {
            if (canDispatchSetCaptioningEnabled()) {
                captionCallback!!.onSetCaptioningEnabled(player, enabled)
            }
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            val isHandled = (canDispatchMediaButtonEvent()
                    && mediaButtonEventHandler!!.onMediaButtonEvent(player, mediaButtonEvent))
            return isHandled || super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

    companion object {
        init {
            ExoPlayerLibraryInfo.registerModule("goog.exo.mediasession")
        }

        @PlaybackActions
        val ALL_PLAYBACK_ACTIONS = (PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_SEEK_TO
                or PlaybackStateCompat.ACTION_FAST_FORWARD
                or PlaybackStateCompat.ACTION_REWIND
                or PlaybackStateCompat.ACTION_STOP
                or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                or PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED)

        /** The default playback actions.  */
        @PlaybackActions
        val DEFAULT_PLAYBACK_ACTIONS = ALL_PLAYBACK_ACTIONS - PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED

        /**
         * The name of the [PlaybackStateCompat] float extra with the value of `Player.getPlaybackParameters().speed`.
         */
        const val EXTRAS_SPEED = "EXO_SPEED"
        private const val BASE_PLAYBACK_ACTIONS = (PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_STOP
                or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                or PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED)
        private const val BASE_MEDIA_SESSION_FLAGS = (MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        private const val EDITOR_MEDIA_SESSION_FLAGS = BASE_MEDIA_SESSION_FLAGS or MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
        private val METADATA_EMPTY = MediaMetadataCompat.Builder().build()
    }
}