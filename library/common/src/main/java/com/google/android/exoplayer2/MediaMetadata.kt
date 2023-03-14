/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2import

import android.net.Uri
import android.os.Bundle
import androidx.annotation.IntRange
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.MediaMetadata.FolderType
import com.google.android.exoplayer2.MediaMetadata.PictureType
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.util.*
import com.google.common.base.Objects
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

androidx.annotation .IntDef
import com.google.android.exoplayer2.ui.AdOverlayInfo
import android.view.ViewGroup
import com.google.common.collect.ImmutableList
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData
import android.os.Parcelable
import android.os.Parcel
import android.text.TextUtils
import com.google.android.exoplayer2.text.span.TextAnnotation
import com.google.android.exoplayer2.text.span.LanguageFeatureSpan
import android.text.Spannable
import com.google.android.exoplayer2.text.span.TextEmphasisSpan.MarkFill
import com.google.android.exoplayer2.text.span.TextEmphasisSpan
import android.graphics.Bitmap
import com.google.android.exoplayer2.text.Cue.LineType
import com.google.android.exoplayer2.text.Cue.TextSizeType
import com.google.android.exoplayer2.text.Cue.VerticalType
import com.google.android.exoplayer2.text.Cue
import androidx.annotation.ColorInt
import org.checkerframework.dataflow.qual.Pure
import android.os.Bundle
import android.text.Spanned
import android.text.SpannedString
import com.google.android.exoplayer2.text.CueGroup
import android.content.IntentFilter
import android.content.Intent
import android.content.ComponentName
import android.app.Activity
import com.google.android.exoplayer2.MediaItem.SubtitleConfiguration
import android.provider.MediaStore
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import org.checkerframework.checker.initialization.qual.UnknownInitialization
import android.os.Looper
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Futures
import org.checkerframework.checker.nullness.qual.PolyNull
import androidx.annotation.RequiresApi
import android.util.SparseLongArray
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.C.PcmEncoding
import android.annotation.SuppressLint
import com.google.android.exoplayer2.C.AudioUsage
import com.google.android.exoplayer2.C.AudioContentType
import android.media.MediaDrm
import android.telephony.TelephonyManager
import android.app.UiModeManager
import android.hardware.display.DisplayManager
import android.view.WindowManager
import android.database.sqlite.SQLiteDatabase
import android.database.DatabaseUtils
import android.Manifest.permission
import android.security.NetworkSecurityPolicy
import android.opengl.EGL14
import javax.microedition.khronos.egl.EGL10
import android.opengl.GLES20
import com.google.android.exoplayer2.util.GlUtil.GlException
import com.google.android.exoplayer2.util.GlUtil.Api17
import android.opengl.GLU
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.SparseBooleanArray
import com.google.android.exoplayer2.util.GlProgram.Uniform
import com.google.android.exoplayer2.util.MimeTypes.CustomMimeType
import com.google.android.exoplayer2.util.MimeTypes.Mp4aObjectType
import com.google.android.exoplayer2.util.AtomicFile.AtomicFileOutputStream
import android.os.IBinder
import kotlin.annotations.jvm.UnderMigration
import kotlin.annotations.jvm.MigrationStatus
import com.google.android.exoplayer2.util.ListenerSet.IterationFinishedEvent
import android.util.SparseArray
import com.google.android.exoplayer2.video.ColorInfo
import android.media.MediaFormat
import android.app.NotificationManager
import androidx.annotation.StringRes
import com.google.android.exoplayer2.util.NotificationUtil.Importance
import android.app.NotificationChannel
import android.view.SurfaceView
import com.google.android.exoplayer2.util.EGLSurfaceTexture.TextureImageListener
import android.graphics.SurfaceTexture
import com.google.android.exoplayer2.util.EGLSurfaceTexture.SecureMode
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParser
import android.net.ConnectivityManager
import com.google.android.exoplayer2.util.NetworkTypeObserver.Api31.DisplayInfoCallback
import android.telephony.TelephonyCallback
import android.telephony.TelephonyCallback.DisplayInfoListener
import android.telephony.TelephonyDisplayInfo
import android.net.NetworkInfo
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException
import com.google.android.exoplayer2.util.SystemHandlerWrapper.SystemMessage
import com.google.android.exoplayer2.audio.AuxEffectInfo
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.C.AudioFlags
import com.google.android.exoplayer2.C.AudioAllowedCapturePolicy
import com.google.android.exoplayer2.C.SpatializationBehavior
import com.google.android.exoplayer2.audio.AudioAttributes.Api32
import com.google.android.exoplayer2.audio.AudioAttributes.AudioAttributesV21
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.C.ColorRange
import com.google.android.exoplayer2.C.ColorTransfer
import androidx.annotation.FloatRange
import android.media.MediaCodec
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdGroup
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdState
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.C.RoleFlags
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.C.SelectionFlags
import com.google.android.exoplayer2.C.StereoMode
import com.google.android.exoplayer2.C.CryptoType
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.Player.TimelineChangeReason
import com.google.android.exoplayer2.Player.MediaItemTransitionReason
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason
import com.google.android.exoplayer2.Player.DiscontinuityReason
import android.view.SurfaceHolder
import android.view.TextureView
import com.google.android.exoplayer2.Rating.RatingType
import com.google.common.primitives.Booleans
import com.google.common.base.MoreObjects
import com.google.android.exoplayer2.MediaItem.LiveConfiguration
import com.google.android.exoplayer2.Timeline.RemotableTimeline
import com.google.android.exoplayer2.MediaItem.ClippingProperties
import com.google.android.exoplayer2.MediaItem.PlaybackProperties
import com.google.android.exoplayer2.MediaItem.RequestMetadata
import com.google.android.exoplayer2.MediaItem.AdsConfiguration
import com.google.android.exoplayer2.MediaItem.LocalConfiguration
import com.google.android.exoplayer2.MediaItem.ClippingConfiguration
import com.google.android.exoplayer2.MediaItem.DrmConfiguration
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.common.primitives.Ints
import android.view.accessibility.CaptioningManager
import com.google.android.exoplayer2.DeviceInfo.PlaybackType
import com.google.android.exoplayer2.MediaMetadata.PictureType
import com.google.android.exoplayer2.MediaMetadata.FolderType
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import androidx.annotation.CallSuper
import android.media.MediaPlayer

/**
 * Metadata of a [MediaItem], playlist, or a combination of multiple sources of [ ].
 */
class MediaMetadata private constructor(builder: MediaMetadata.Builder) : Bundleable {
    /** A builder for [MediaMetadata] instances.  */
    class Builder {
        private var title: CharSequence? = null
        private var artist: CharSequence? = null
        private var albumTitle: CharSequence? = null
        private var albumArtist: CharSequence? = null
        private var displayTitle: CharSequence? = null
        private var subtitle: CharSequence? = null
        private var description: CharSequence? = null
        private var userRating: Rating? = null
        private var overallRating: Rating? = null
        private var artworkData: ByteArray?
        private var artworkDataType: @PictureType Int? = null
        private var artworkUri: Uri? = null
        private var trackNumber: Int? = null
        private var totalTrackCount: Int? = null
        private var folderType: @FolderType Int? = null
        private var isPlayable: Boolean? = null
        private var recordingYear: Int? = null
        private var recordingMonth: Int? = null
        private var recordingDay: Int? = null
        private var releaseYear: Int? = null
        private var releaseMonth: Int? = null
        private var releaseDay: Int? = null
        private var writer: CharSequence? = null
        private var composer: CharSequence? = null
        private var conductor: CharSequence? = null
        private var discNumber: Int? = null
        private var totalDiscCount: Int? = null
        private var genre: CharSequence? = null
        private var compilation: CharSequence? = null
        private var station: CharSequence? = null
        private var extras: Bundle? = null

        constructor() {}
        private constructor(mediaMetadata: MediaMetadata) {
            title = mediaMetadata.title
            artist = mediaMetadata.artist
            albumTitle = mediaMetadata.albumTitle
            albumArtist = mediaMetadata.albumArtist
            displayTitle = mediaMetadata.displayTitle
            subtitle = mediaMetadata.subtitle
            description = mediaMetadata.description
            userRating = mediaMetadata.userRating
            overallRating = mediaMetadata.overallRating
            artworkData = mediaMetadata.artworkData
            artworkDataType = mediaMetadata.artworkDataType
            artworkUri = mediaMetadata.artworkUri
            trackNumber = mediaMetadata.trackNumber
            totalTrackCount = mediaMetadata.totalTrackCount
            folderType = mediaMetadata.folderType
            isPlayable = mediaMetadata.isPlayable
            recordingYear = mediaMetadata.recordingYear
            recordingMonth = mediaMetadata.recordingMonth
            recordingDay = mediaMetadata.recordingDay
            releaseYear = mediaMetadata.releaseYear
            releaseMonth = mediaMetadata.releaseMonth
            releaseDay = mediaMetadata.releaseDay
            writer = mediaMetadata.writer
            composer = mediaMetadata.composer
            conductor = mediaMetadata.conductor
            discNumber = mediaMetadata.discNumber
            totalDiscCount = mediaMetadata.totalDiscCount
            genre = mediaMetadata.genre
            compilation = mediaMetadata.compilation
            station = mediaMetadata.station
            extras = mediaMetadata.extras
        }

        /** Sets the title.  */
        @CanIgnoreReturnValue
        fun setTitle(title: CharSequence?): MediaMetadata.Builder {
            this.title = title
            return this
        }

        /** Sets the artist.  */
        @CanIgnoreReturnValue
        fun setArtist(artist: CharSequence?): MediaMetadata.Builder {
            this.artist = artist
            return this
        }

        /** Sets the album title.  */
        @CanIgnoreReturnValue
        fun setAlbumTitle(albumTitle: CharSequence?): MediaMetadata.Builder {
            this.albumTitle = albumTitle
            return this
        }

        /** Sets the album artist.  */
        @CanIgnoreReturnValue
        fun setAlbumArtist(albumArtist: CharSequence?): MediaMetadata.Builder {
            this.albumArtist = albumArtist
            return this
        }

        /** Sets the display title.  */
        @CanIgnoreReturnValue
        fun setDisplayTitle(displayTitle: CharSequence?): MediaMetadata.Builder {
            this.displayTitle = displayTitle
            return this
        }

        /**
         * Sets the subtitle.
         *
         *
         * This is the secondary title of the media, unrelated to closed captions.
         */
        @CanIgnoreReturnValue
        fun setSubtitle(subtitle: CharSequence?): MediaMetadata.Builder {
            this.subtitle = subtitle
            return this
        }

        /** Sets the description.  */
        @CanIgnoreReturnValue
        fun setDescription(description: CharSequence?): MediaMetadata.Builder {
            this.description = description
            return this
        }

        /** Sets the user [Rating].  */
        @CanIgnoreReturnValue
        fun setUserRating(userRating: Rating?): MediaMetadata.Builder {
            this.userRating = userRating
            return this
        }

        /** Sets the overall [Rating].  */
        @CanIgnoreReturnValue
        fun setOverallRating(overallRating: Rating?): MediaMetadata.Builder {
            this.overallRating = overallRating
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setArtworkData(byte[] data, Integer pictureType)} or {@link\n" + "     *     #maybeSetArtworkData(byte[] data, int pictureType)}, providing a {@link PictureType}.")
        fun setArtworkData(artworkData: ByteArray?): MediaMetadata.Builder {
            return setArtworkData(artworkData,  /* artworkDataType= */null)
        }

        /**
         * Sets the artwork data as a compressed byte array with an associated [ artworkDataType][PictureType].
         */
        @CanIgnoreReturnValue
        fun setArtworkData(
                artworkData: ByteArray?, artworkDataType: @PictureType Int?): MediaMetadata.Builder {
            this.artworkData = if (artworkData == null) null else artworkData.clone()
            this.artworkDataType = artworkDataType
            return this
        }

        /**
         * Sets the artwork data as a compressed byte array in the event that the associated [ ] is [.PICTURE_TYPE_FRONT_COVER], the existing [PictureType] is not
         * [.PICTURE_TYPE_FRONT_COVER], or the current artworkData is not set.
         *
         *
         * Use [.setArtworkData] to set the artwork data without checking the
         * [PictureType].
         */
        @CanIgnoreReturnValue
        fun maybeSetArtworkData(artworkData: ByteArray, artworkDataType: @PictureType Int): MediaMetadata.Builder {
            if (((this.artworkData == null
                            ) || Util.areEqual(artworkDataType, MediaMetadata.Companion.PICTURE_TYPE_FRONT_COVER)
                            || !Util.areEqual(this.artworkDataType, MediaMetadata.Companion.PICTURE_TYPE_FRONT_COVER))) {
                this.artworkData = artworkData.clone()
                this.artworkDataType = artworkDataType
            }
            return this
        }

        /** Sets the artwork [Uri].  */
        @CanIgnoreReturnValue
        fun setArtworkUri(artworkUri: Uri?): MediaMetadata.Builder {
            this.artworkUri = artworkUri
            return this
        }

        /** Sets the track number.  */
        @CanIgnoreReturnValue
        fun setTrackNumber(trackNumber: Int?): MediaMetadata.Builder {
            this.trackNumber = trackNumber
            return this
        }

        /** Sets the total number of tracks.  */
        @CanIgnoreReturnValue
        fun setTotalTrackCount(totalTrackCount: Int?): MediaMetadata.Builder {
            this.totalTrackCount = totalTrackCount
            return this
        }

        /** Sets the [FolderType].  */
        @CanIgnoreReturnValue
        fun setFolderType(folderType: @FolderType Int?): MediaMetadata.Builder {
            this.folderType = folderType
            return this
        }

        /** Sets whether the media is playable.  */
        @CanIgnoreReturnValue
        fun setIsPlayable(isPlayable: Boolean?): MediaMetadata.Builder {
            this.isPlayable = isPlayable
            return this
        }

        @CanIgnoreReturnValue
        @Deprecated("Use {@link #setRecordingYear(Integer)} instead.")
        fun setYear(year: Int?): MediaMetadata.Builder {
            return setRecordingYear(year)
        }

        /** Sets the year of the recording date.  */
        @CanIgnoreReturnValue
        fun setRecordingYear(recordingYear: Int?): MediaMetadata.Builder {
            this.recordingYear = recordingYear
            return this
        }

        /**
         * Sets the month of the recording date.
         *
         *
         * Value should be between 1 and 12.
         */
        @CanIgnoreReturnValue
        fun setRecordingMonth(
                @IntRange(from = 1, to = 12) recordingMonth: Int?): MediaMetadata.Builder {
            this.recordingMonth = recordingMonth
            return this
        }

        /**
         * Sets the day of the recording date.
         *
         *
         * Value should be between 1 and 31.
         */
        @CanIgnoreReturnValue
        fun setRecordingDay(@IntRange(from = 1, to = 31) recordingDay: Int?): MediaMetadata.Builder {
            this.recordingDay = recordingDay
            return this
        }

        /** Sets the year of the release date.  */
        @CanIgnoreReturnValue
        fun setReleaseYear(releaseYear: Int?): MediaMetadata.Builder {
            this.releaseYear = releaseYear
            return this
        }

        /**
         * Sets the month of the release date.
         *
         *
         * Value should be between 1 and 12.
         */
        @CanIgnoreReturnValue
        fun setReleaseMonth(@IntRange(from = 1, to = 12) releaseMonth: Int?): MediaMetadata.Builder {
            this.releaseMonth = releaseMonth
            return this
        }

        /**
         * Sets the day of the release date.
         *
         *
         * Value should be between 1 and 31.
         */
        @CanIgnoreReturnValue
        fun setReleaseDay(@IntRange(from = 1, to = 31) releaseDay: Int?): MediaMetadata.Builder {
            this.releaseDay = releaseDay
            return this
        }

        /** Sets the writer.  */
        @CanIgnoreReturnValue
        fun setWriter(writer: CharSequence?): MediaMetadata.Builder {
            this.writer = writer
            return this
        }

        /** Sets the composer.  */
        @CanIgnoreReturnValue
        fun setComposer(composer: CharSequence?): MediaMetadata.Builder {
            this.composer = composer
            return this
        }

        /** Sets the conductor.  */
        @CanIgnoreReturnValue
        fun setConductor(conductor: CharSequence?): MediaMetadata.Builder {
            this.conductor = conductor
            return this
        }

        /** Sets the disc number.  */
        @CanIgnoreReturnValue
        fun setDiscNumber(discNumber: Int?): MediaMetadata.Builder {
            this.discNumber = discNumber
            return this
        }

        /** Sets the total number of discs.  */
        @CanIgnoreReturnValue
        fun setTotalDiscCount(totalDiscCount: Int?): MediaMetadata.Builder {
            this.totalDiscCount = totalDiscCount
            return this
        }

        /** Sets the genre.  */
        @CanIgnoreReturnValue
        fun setGenre(genre: CharSequence?): MediaMetadata.Builder {
            this.genre = genre
            return this
        }

        /** Sets the compilation.  */
        @CanIgnoreReturnValue
        fun setCompilation(compilation: CharSequence?): MediaMetadata.Builder {
            this.compilation = compilation
            return this
        }

        /** Sets the name of the station streaming the media.  */
        @CanIgnoreReturnValue
        fun setStation(station: CharSequence?): MediaMetadata.Builder {
            this.station = station
            return this
        }

        /** Sets the extras [Bundle].  */
        @CanIgnoreReturnValue
        fun setExtras(extras: Bundle?): MediaMetadata.Builder {
            this.extras = extras
            return this
        }

        /**
         * Sets all fields supported by the [entries][Metadata.Entry] within the [Metadata].
         *
         *
         * Fields are only set if the [Metadata.Entry] has an implementation for [ ][Metadata.Entry.populateMediaMetadata].
         *
         *
         * In the event that multiple [Metadata.Entry] objects within the [Metadata]
         * relate to the same [MediaMetadata] field, then the last one will be used.
         */
        @CanIgnoreReturnValue
        fun populateFromMetadata(metadata: Metadata): MediaMetadata.Builder {
            for (i in 0 until metadata.length()) {
                val entry: Metadata.Entry? = metadata.get(i)
                entry!!.populateMediaMetadata(this)
            }
            return this
        }

        /**
         * Sets all fields supported by the [entries][Metadata.Entry] within the list of [ ].
         *
         *
         * Fields are only set if the [Metadata.Entry] has an implementation for [ ][Metadata.Entry.populateMediaMetadata].
         *
         *
         * In the event that multiple [Metadata.Entry] objects within any of the [ ] relate to the same [MediaMetadata] field, then the last one will be used.
         */
        @CanIgnoreReturnValue
        fun populateFromMetadata(metadataList: List<Metadata>): MediaMetadata.Builder {
            for (i in metadataList.indices) {
                val metadata: Metadata = metadataList.get(i)
                for (j in 0 until metadata.length()) {
                    val entry: Metadata.Entry? = metadata.get(j)
                    entry!!.populateMediaMetadata(this)
                }
            }
            return this
        }

        /** Populates all the fields from `mediaMetadata`, provided they are non-null.  */
        @CanIgnoreReturnValue
        fun populate(mediaMetadata: MediaMetadata?): MediaMetadata.Builder {
            if (mediaMetadata == null) {
                return this
            }
            if (mediaMetadata.title != null) {
                setTitle(mediaMetadata.title)
            }
            if (mediaMetadata.artist != null) {
                setArtist(mediaMetadata.artist)
            }
            if (mediaMetadata.albumTitle != null) {
                setAlbumTitle(mediaMetadata.albumTitle)
            }
            if (mediaMetadata.albumArtist != null) {
                setAlbumArtist(mediaMetadata.albumArtist)
            }
            if (mediaMetadata.displayTitle != null) {
                setDisplayTitle(mediaMetadata.displayTitle)
            }
            if (mediaMetadata.subtitle != null) {
                setSubtitle(mediaMetadata.subtitle)
            }
            if (mediaMetadata.description != null) {
                setDescription(mediaMetadata.description)
            }
            if (mediaMetadata.userRating != null) {
                setUserRating(mediaMetadata.userRating)
            }
            if (mediaMetadata.overallRating != null) {
                setOverallRating(mediaMetadata.overallRating)
            }
            if (mediaMetadata.artworkData != null) {
                setArtworkData(mediaMetadata.artworkData, mediaMetadata.artworkDataType)
            }
            if (mediaMetadata.artworkUri != null) {
                setArtworkUri(mediaMetadata.artworkUri)
            }
            if (mediaMetadata.trackNumber != null) {
                setTrackNumber(mediaMetadata.trackNumber)
            }
            if (mediaMetadata.totalTrackCount != null) {
                setTotalTrackCount(mediaMetadata.totalTrackCount)
            }
            if (mediaMetadata.folderType != null) {
                setFolderType(mediaMetadata.folderType)
            }
            if (mediaMetadata.isPlayable != null) {
                setIsPlayable(mediaMetadata.isPlayable)
            }
            if (mediaMetadata.year != null) {
                setRecordingYear(mediaMetadata.year)
            }
            if (mediaMetadata.recordingYear != null) {
                setRecordingYear(mediaMetadata.recordingYear)
            }
            if (mediaMetadata.recordingMonth != null) {
                setRecordingMonth(mediaMetadata.recordingMonth)
            }
            if (mediaMetadata.recordingDay != null) {
                setRecordingDay(mediaMetadata.recordingDay)
            }
            if (mediaMetadata.releaseYear != null) {
                setReleaseYear(mediaMetadata.releaseYear)
            }
            if (mediaMetadata.releaseMonth != null) {
                setReleaseMonth(mediaMetadata.releaseMonth)
            }
            if (mediaMetadata.releaseDay != null) {
                setReleaseDay(mediaMetadata.releaseDay)
            }
            if (mediaMetadata.writer != null) {
                setWriter(mediaMetadata.writer)
            }
            if (mediaMetadata.composer != null) {
                setComposer(mediaMetadata.composer)
            }
            if (mediaMetadata.conductor != null) {
                setConductor(mediaMetadata.conductor)
            }
            if (mediaMetadata.discNumber != null) {
                setDiscNumber(mediaMetadata.discNumber)
            }
            if (mediaMetadata.totalDiscCount != null) {
                setTotalDiscCount(mediaMetadata.totalDiscCount)
            }
            if (mediaMetadata.genre != null) {
                setGenre(mediaMetadata.genre)
            }
            if (mediaMetadata.compilation != null) {
                setCompilation(mediaMetadata.compilation)
            }
            if (mediaMetadata.station != null) {
                setStation(mediaMetadata.station)
            }
            if (mediaMetadata.extras != null) {
                setExtras(mediaMetadata.extras)
            }
            return this
        }

        /** Returns a new [MediaMetadata] instance with the current builder values.  */
        fun build(): MediaMetadata {
            return MediaMetadata( /* builder= */this)
        }
    }

    /**
     * The folder type of the media item.
     *
     *
     * This can be used as the type of a browsable bluetooth folder (see section 6.10.2.2 of the [Bluetooth
 * AVRCP 1.6.2](https://www.bluetooth.com/specifications/specs/a-v-remote-control-profile-1-6-2/)).
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([MediaMetadata.Companion.FOLDER_TYPE_NONE, MediaMetadata.Companion.FOLDER_TYPE_MIXED, MediaMetadata.Companion.FOLDER_TYPE_TITLES, MediaMetadata.Companion.FOLDER_TYPE_ALBUMS, MediaMetadata.Companion.FOLDER_TYPE_ARTISTS, MediaMetadata.Companion.FOLDER_TYPE_GENRES, MediaMetadata.Companion.FOLDER_TYPE_PLAYLISTS, MediaMetadata.Companion.FOLDER_TYPE_YEARS])
    annotation class FolderType constructor()

    /**
     * The picture type of the artwork.
     *
     *
     * Values sourced from the ID3 v2.4 specification (See section 4.14 of
     * https://id3.org/id3v2.4.0-frames).
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE, TYPE_USE)
    @IntDef([MediaMetadata.Companion.PICTURE_TYPE_OTHER, MediaMetadata.Companion.PICTURE_TYPE_FILE_ICON, MediaMetadata.Companion.PICTURE_TYPE_FILE_ICON_OTHER, MediaMetadata.Companion.PICTURE_TYPE_FRONT_COVER, MediaMetadata.Companion.PICTURE_TYPE_BACK_COVER, MediaMetadata.Companion.PICTURE_TYPE_LEAFLET_PAGE, MediaMetadata.Companion.PICTURE_TYPE_MEDIA, MediaMetadata.Companion.PICTURE_TYPE_LEAD_ARTIST_PERFORMER, MediaMetadata.Companion.PICTURE_TYPE_ARTIST_PERFORMER, MediaMetadata.Companion.PICTURE_TYPE_CONDUCTOR, MediaMetadata.Companion.PICTURE_TYPE_BAND_ORCHESTRA, MediaMetadata.Companion.PICTURE_TYPE_COMPOSER, MediaMetadata.Companion.PICTURE_TYPE_LYRICIST, MediaMetadata.Companion.PICTURE_TYPE_RECORDING_LOCATION, MediaMetadata.Companion.PICTURE_TYPE_DURING_RECORDING, MediaMetadata.Companion.PICTURE_TYPE_DURING_PERFORMANCE, MediaMetadata.Companion.PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE, MediaMetadata.Companion.PICTURE_TYPE_A_BRIGHT_COLORED_FISH, MediaMetadata.Companion.PICTURE_TYPE_ILLUSTRATION, MediaMetadata.Companion.PICTURE_TYPE_BAND_ARTIST_LOGO, MediaMetadata.Companion.PICTURE_TYPE_PUBLISHER_STUDIO_LOGO])
    annotation class PictureType constructor()

    /** Optional title.  */
    val title: CharSequence?

    /** Optional artist.  */
    val artist: CharSequence?

    /** Optional album title.  */
    val albumTitle: CharSequence?

    /** Optional album artist.  */
    val albumArtist: CharSequence?

    /** Optional display title.  */
    val displayTitle: CharSequence?

    /**
     * Optional subtitle.
     *
     *
     * This is the secondary title of the media, unrelated to closed captions.
     */
    val subtitle: CharSequence?

    /** Optional description.  */
    val description: CharSequence?

    /** Optional user [Rating].  */
    val userRating: Rating?

    /** Optional overall [Rating].  */
    val overallRating: Rating?

    /** Optional artwork data as a compressed byte array.  */
    val artworkData: ByteArray?

    /** Optional [PictureType] of the artwork data.  */
    val artworkDataType: @PictureType Int?

    /** Optional artwork [Uri].  */
    val artworkUri: Uri?

    /** Optional track number.  */
    val trackNumber: Int?

    /** Optional total number of tracks.  */
    val totalTrackCount: Int?

    /** Optional [FolderType].  */
    val folderType: @FolderType Int?

    /** Optional boolean for media playability.  */
    val isPlayable: Boolean?

    @Deprecated("Use {@link #recordingYear} instead.")
    val year: Int?

    /** Optional year of the recording date.  */
    val recordingYear: Int?

    /**
     * Optional month of the recording date.
     *
     *
     * Note that there is no guarantee that the month and day are a valid combination.
     */
    val recordingMonth: Int?

    /**
     * Optional day of the recording date.
     *
     *
     * Note that there is no guarantee that the month and day are a valid combination.
     */
    val recordingDay: Int?

    /** Optional year of the release date.  */
    val releaseYear: Int?

    /**
     * Optional month of the release date.
     *
     *
     * Note that there is no guarantee that the month and day are a valid combination.
     */
    val releaseMonth: Int?

    /**
     * Optional day of the release date.
     *
     *
     * Note that there is no guarantee that the month and day are a valid combination.
     */
    val releaseDay: Int?

    /** Optional writer.  */
    val writer: CharSequence?

    /** Optional composer.  */
    val composer: CharSequence?

    /** Optional conductor.  */
    val conductor: CharSequence?

    /** Optional disc number.  */
    val discNumber: Int?

    /** Optional total number of discs.  */
    val totalDiscCount: Int?

    /** Optional genre.  */
    val genre: CharSequence?

    /** Optional compilation.  */
    val compilation: CharSequence?

    /** Optional name of the station streaming the media.  */
    val station: CharSequence?

    /**
     * Optional extras [Bundle].
     *
     *
     * Given the complexities of checking the equality of two [Bundle]s, this is not
     * considered in the [.equals] or [.hashCode].
     */
    val extras: Bundle?

    /** Returns a new [Builder] instance with the current [MediaMetadata] fields.  */
    fun buildUpon(): MediaMetadata.Builder {
        return MediaMetadata.Builder( /* mediaMetadata= */this)
    }

    public override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (obj == null || javaClass != obj.javaClass) {
            return false
        }
        val that: MediaMetadata = obj as MediaMetadata
        return (Util.areEqual(title, that.title)
                && Util.areEqual(artist, that.artist)
                && Util.areEqual(albumTitle, that.albumTitle)
                && Util.areEqual(albumArtist, that.albumArtist)
                && Util.areEqual(displayTitle, that.displayTitle)
                && Util.areEqual(subtitle, that.subtitle)
                && Util.areEqual(description, that.description)
                && Util.areEqual(userRating, that.userRating)
                && Util.areEqual(overallRating, that.overallRating)
                && Arrays.equals(artworkData, that.artworkData)
                && Util.areEqual(artworkDataType, that.artworkDataType)
                && Util.areEqual(artworkUri, that.artworkUri)
                && Util.areEqual(trackNumber, that.trackNumber)
                && Util.areEqual(totalTrackCount, that.totalTrackCount)
                && Util.areEqual(folderType, that.folderType)
                && Util.areEqual(isPlayable, that.isPlayable)
                && Util.areEqual(recordingYear, that.recordingYear)
                && Util.areEqual(recordingMonth, that.recordingMonth)
                && Util.areEqual(recordingDay, that.recordingDay)
                && Util.areEqual(releaseYear, that.releaseYear)
                && Util.areEqual(releaseMonth, that.releaseMonth)
                && Util.areEqual(releaseDay, that.releaseDay)
                && Util.areEqual(writer, that.writer)
                && Util.areEqual(composer, that.composer)
                && Util.areEqual(conductor, that.conductor)
                && Util.areEqual(discNumber, that.discNumber)
                && Util.areEqual(totalDiscCount, that.totalDiscCount)
                && Util.areEqual(genre, that.genre)
                && Util.areEqual(compilation, that.compilation)
                && Util.areEqual(station, that.station))
    }

    public override fun hashCode(): Int {
        return Objects.hashCode(
                title,
                artist,
                albumTitle,
                albumArtist,
                displayTitle,
                subtitle,
                description,
                userRating,
                overallRating,
                Arrays.hashCode(artworkData),
                artworkDataType,
                artworkUri,
                trackNumber,
                totalTrackCount,
                folderType,
                isPlayable,
                recordingYear,
                recordingMonth,
                recordingDay,
                releaseYear,
                releaseMonth,
                releaseDay,
                writer,
                composer,
                conductor,
                discNumber,
                totalDiscCount,
                genre,
                compilation,
                station)
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef([MediaMetadata.Companion.FIELD_TITLE, MediaMetadata.Companion.FIELD_ARTIST, MediaMetadata.Companion.FIELD_ALBUM_TITLE, MediaMetadata.Companion.FIELD_ALBUM_ARTIST, MediaMetadata.Companion.FIELD_DISPLAY_TITLE, MediaMetadata.Companion.FIELD_SUBTITLE, MediaMetadata.Companion.FIELD_DESCRIPTION, MediaMetadata.Companion.FIELD_MEDIA_URI, MediaMetadata.Companion.FIELD_USER_RATING, MediaMetadata.Companion.FIELD_OVERALL_RATING, MediaMetadata.Companion.FIELD_ARTWORK_DATA, MediaMetadata.Companion.FIELD_ARTWORK_DATA_TYPE, MediaMetadata.Companion.FIELD_ARTWORK_URI, MediaMetadata.Companion.FIELD_TRACK_NUMBER, MediaMetadata.Companion.FIELD_TOTAL_TRACK_COUNT, MediaMetadata.Companion.FIELD_FOLDER_TYPE, MediaMetadata.Companion.FIELD_IS_PLAYABLE, MediaMetadata.Companion.FIELD_RECORDING_YEAR, MediaMetadata.Companion.FIELD_RECORDING_MONTH, MediaMetadata.Companion.FIELD_RECORDING_DAY, MediaMetadata.Companion.FIELD_RELEASE_YEAR, MediaMetadata.Companion.FIELD_RELEASE_MONTH, MediaMetadata.Companion.FIELD_RELEASE_DAY, MediaMetadata.Companion.FIELD_WRITER, MediaMetadata.Companion.FIELD_COMPOSER, MediaMetadata.Companion.FIELD_CONDUCTOR, MediaMetadata.Companion.FIELD_DISC_NUMBER, MediaMetadata.Companion.FIELD_TOTAL_DISC_COUNT, MediaMetadata.Companion.FIELD_GENRE, MediaMetadata.Companion.FIELD_COMPILATION, MediaMetadata.Companion.FIELD_STATION, MediaMetadata.Companion.FIELD_EXTRAS])
    private annotation class FieldNumber constructor()

    public override fun toBundle(): Bundle {
        val bundle: Bundle = Bundle()
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TITLE), title)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ARTIST), artist)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ALBUM_TITLE), albumTitle)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ALBUM_ARTIST), albumArtist)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_DISPLAY_TITLE), displayTitle)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_SUBTITLE), subtitle)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_DESCRIPTION), description)
        bundle.putByteArray(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ARTWORK_DATA), artworkData)
        bundle.putParcelable(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ARTWORK_URI), artworkUri)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_WRITER), writer)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_COMPOSER), composer)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_CONDUCTOR), conductor)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_GENRE), genre)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_COMPILATION), compilation)
        bundle.putCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_STATION), station)
        if (userRating != null) {
            bundle.putBundle(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_USER_RATING), userRating.toBundle())
        }
        if (overallRating != null) {
            bundle.putBundle(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_OVERALL_RATING), overallRating.toBundle())
        }
        if (trackNumber != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TRACK_NUMBER), trackNumber)
        }
        if (totalTrackCount != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TOTAL_TRACK_COUNT), totalTrackCount)
        }
        if (folderType != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_FOLDER_TYPE), folderType)
        }
        if (isPlayable != null) {
            bundle.putBoolean(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_IS_PLAYABLE), isPlayable)
        }
        if (recordingYear != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RECORDING_YEAR), recordingYear)
        }
        if (recordingMonth != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RECORDING_MONTH), recordingMonth)
        }
        if (recordingDay != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RECORDING_DAY), recordingDay)
        }
        if (releaseYear != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RELEASE_YEAR), releaseYear)
        }
        if (releaseMonth != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RELEASE_MONTH), releaseMonth)
        }
        if (releaseDay != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RELEASE_DAY), releaseDay)
        }
        if (discNumber != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_DISC_NUMBER), discNumber)
        }
        if (totalDiscCount != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TOTAL_DISC_COUNT), totalDiscCount)
        }
        if (artworkDataType != null) {
            bundle.putInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ARTWORK_DATA_TYPE), artworkDataType)
        }
        if (extras != null) {
            bundle.putBundle(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_EXTRAS), extras)
        }
        return bundle
    }

    init {
        title = builder.title
        artist = builder.artist
        albumTitle = builder.albumTitle
        albumArtist = builder.albumArtist
        displayTitle = builder.displayTitle
        subtitle = builder.subtitle
        description = builder.description
        userRating = builder.userRating
        overallRating = builder.overallRating
        artworkData = builder.artworkData
        artworkDataType = builder.artworkDataType
        artworkUri = builder.artworkUri
        trackNumber = builder.trackNumber
        totalTrackCount = builder.totalTrackCount
        folderType = builder.folderType
        isPlayable = builder.isPlayable
        year = builder.recordingYear
        recordingYear = builder.recordingYear
        recordingMonth = builder.recordingMonth
        recordingDay = builder.recordingDay
        releaseYear = builder.releaseYear
        releaseMonth = builder.releaseMonth
        releaseDay = builder.releaseDay
        writer = builder.writer
        composer = builder.composer
        conductor = builder.conductor
        discNumber = builder.discNumber
        totalDiscCount = builder.totalDiscCount
        genre = builder.genre
        compilation = builder.compilation
        station = builder.station
        extras = builder.extras
    }

    companion object {
        /** Type for an item that is not a folder.  */
        val FOLDER_TYPE_NONE: Int = -1

        /** Type for a folder containing media of mixed types.  */
        val FOLDER_TYPE_MIXED: Int = 0

        /** Type for a folder containing only playable media.  */
        val FOLDER_TYPE_TITLES: Int = 1

        /** Type for a folder containing media categorized by album.  */
        val FOLDER_TYPE_ALBUMS: Int = 2

        /** Type for a folder containing media categorized by artist.  */
        val FOLDER_TYPE_ARTISTS: Int = 3

        /** Type for a folder containing media categorized by genre.  */
        val FOLDER_TYPE_GENRES: Int = 4

        /** Type for a folder containing a playlist.  */
        val FOLDER_TYPE_PLAYLISTS: Int = 5

        /** Type for a folder containing media categorized by year.  */
        val FOLDER_TYPE_YEARS: Int = 6
        val PICTURE_TYPE_OTHER: Int = 0x00
        val PICTURE_TYPE_FILE_ICON: Int = 0x01
        val PICTURE_TYPE_FILE_ICON_OTHER: Int = 0x02
        val PICTURE_TYPE_FRONT_COVER: Int = 0x03
        val PICTURE_TYPE_BACK_COVER: Int = 0x04
        val PICTURE_TYPE_LEAFLET_PAGE: Int = 0x05
        val PICTURE_TYPE_MEDIA: Int = 0x06
        val PICTURE_TYPE_LEAD_ARTIST_PERFORMER: Int = 0x07
        val PICTURE_TYPE_ARTIST_PERFORMER: Int = 0x08
        val PICTURE_TYPE_CONDUCTOR: Int = 0x09
        val PICTURE_TYPE_BAND_ORCHESTRA: Int = 0x0A
        val PICTURE_TYPE_COMPOSER: Int = 0x0B
        val PICTURE_TYPE_LYRICIST: Int = 0x0C
        val PICTURE_TYPE_RECORDING_LOCATION: Int = 0x0D
        val PICTURE_TYPE_DURING_RECORDING: Int = 0x0E
        val PICTURE_TYPE_DURING_PERFORMANCE: Int = 0x0F
        val PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE: Int = 0x10
        val PICTURE_TYPE_A_BRIGHT_COLORED_FISH: Int = 0x11
        val PICTURE_TYPE_ILLUSTRATION: Int = 0x12
        val PICTURE_TYPE_BAND_ARTIST_LOGO: Int = 0x13
        val PICTURE_TYPE_PUBLISHER_STUDIO_LOGO: Int = 0x14

        /** Empty [MediaMetadata].  */
        val EMPTY: MediaMetadata = MediaMetadata.Builder().build()
        private val FIELD_TITLE: Int = 0
        private val FIELD_ARTIST: Int = 1
        private val FIELD_ALBUM_TITLE: Int = 2
        private val FIELD_ALBUM_ARTIST: Int = 3
        private val FIELD_DISPLAY_TITLE: Int = 4
        private val FIELD_SUBTITLE: Int = 5
        private val FIELD_DESCRIPTION: Int = 6
        private val FIELD_MEDIA_URI: Int = 7
        private val FIELD_USER_RATING: Int = 8
        private val FIELD_OVERALL_RATING: Int = 9
        private val FIELD_ARTWORK_DATA: Int = 10
        private val FIELD_ARTWORK_URI: Int = 11
        private val FIELD_TRACK_NUMBER: Int = 12
        private val FIELD_TOTAL_TRACK_COUNT: Int = 13
        private val FIELD_FOLDER_TYPE: Int = 14
        private val FIELD_IS_PLAYABLE: Int = 15
        private val FIELD_RECORDING_YEAR: Int = 16
        private val FIELD_RECORDING_MONTH: Int = 17
        private val FIELD_RECORDING_DAY: Int = 18
        private val FIELD_RELEASE_YEAR: Int = 19
        private val FIELD_RELEASE_MONTH: Int = 20
        private val FIELD_RELEASE_DAY: Int = 21
        private val FIELD_WRITER: Int = 22
        private val FIELD_COMPOSER: Int = 23
        private val FIELD_CONDUCTOR: Int = 24
        private val FIELD_DISC_NUMBER: Int = 25
        private val FIELD_TOTAL_DISC_COUNT: Int = 26
        private val FIELD_GENRE: Int = 27
        private val FIELD_COMPILATION: Int = 28
        private val FIELD_ARTWORK_DATA_TYPE: Int = 29
        private val FIELD_STATION: Int = 30
        private val FIELD_EXTRAS: Int = 1000

        /** Object that can restore [MediaMetadata] from a [Bundle].  */
        val CREATOR: Bundleable.Creator<MediaMetadata> = Bundleable.Creator({ bundle: Bundle? -> MediaMetadata.Companion.fromBundle(bundle) })
        private fun fromBundle(bundle: Bundle): MediaMetadata {
            val builder: MediaMetadata.Builder = MediaMetadata.Builder()
            builder
                    .setTitle(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TITLE)))
                    .setArtist(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ARTIST)))
                    .setAlbumTitle(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ALBUM_TITLE)))
                    .setAlbumArtist(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ALBUM_ARTIST)))
                    .setDisplayTitle(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_DISPLAY_TITLE)))
                    .setSubtitle(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_SUBTITLE)))
                    .setDescription(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_DESCRIPTION)))
                    .setArtworkData(
                            bundle.getByteArray(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ARTWORK_DATA)),
                            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ARTWORK_DATA_TYPE))) bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ARTWORK_DATA_TYPE)) else null)
                    .setArtworkUri(bundle.getParcelable(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_ARTWORK_URI)))
                    .setWriter(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_WRITER)))
                    .setComposer(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_COMPOSER)))
                    .setConductor(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_CONDUCTOR)))
                    .setGenre(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_GENRE)))
                    .setCompilation(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_COMPILATION)))
                    .setStation(bundle.getCharSequence(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_STATION)))
                    .setExtras(bundle.getBundle(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_EXTRAS)))
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_USER_RATING))) {
                val fieldBundle: Bundle? = bundle.getBundle(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_USER_RATING))
                if (fieldBundle != null) {
                    builder.setUserRating(Rating.Companion.CREATOR.fromBundle(fieldBundle))
                }
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_OVERALL_RATING))) {
                val fieldBundle: Bundle? = bundle.getBundle(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_OVERALL_RATING))
                if (fieldBundle != null) {
                    builder.setOverallRating(Rating.Companion.CREATOR.fromBundle(fieldBundle))
                }
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TRACK_NUMBER))) {
                builder.setTrackNumber(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TRACK_NUMBER)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TOTAL_TRACK_COUNT))) {
                builder.setTotalTrackCount(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TOTAL_TRACK_COUNT)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_FOLDER_TYPE))) {
                builder.setFolderType(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_FOLDER_TYPE)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_IS_PLAYABLE))) {
                builder.setIsPlayable(bundle.getBoolean(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_IS_PLAYABLE)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RECORDING_YEAR))) {
                builder.setRecordingYear(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RECORDING_YEAR)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RECORDING_MONTH))) {
                builder.setRecordingMonth(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RECORDING_MONTH)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RECORDING_DAY))) {
                builder.setRecordingDay(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RECORDING_DAY)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RELEASE_YEAR))) {
                builder.setReleaseYear(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RELEASE_YEAR)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RELEASE_MONTH))) {
                builder.setReleaseMonth(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RELEASE_MONTH)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RELEASE_DAY))) {
                builder.setReleaseDay(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_RELEASE_DAY)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_DISC_NUMBER))) {
                builder.setDiscNumber(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_DISC_NUMBER)))
            }
            if (bundle.containsKey(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TOTAL_DISC_COUNT))) {
                builder.setTotalDiscCount(bundle.getInt(MediaMetadata.Companion.keyForField(MediaMetadata.Companion.FIELD_TOTAL_DISC_COUNT)))
            }
            return builder.build()
        }

        private fun keyForField(field: @MediaMetadata.FieldNumber Int): String {
            return Integer.toString(field, Character.MAX_RADIX)
        }
    }
}