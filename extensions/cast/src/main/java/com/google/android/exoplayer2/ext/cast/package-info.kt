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
package com.google.android.exoplayer2.ext.cast

import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.exoplayer2.BasePlayer
import com.google.android.exoplayer2.ext.cast.CastTimelineTracker
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.ext.cast.CastPlayer.SeekResultCallback
import com.google.android.exoplayer2.util.ListenerSet
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener
import com.google.android.exoplayer2.ext.cast.CastPlayer.StateHolder
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.exoplayer2.ext.cast.CastTimeline
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.util.Assertions
import android.os.Looper
import com.google.android.exoplayer2.util.ListenerSet.IterationFinishedEvent
import com.google.android.exoplayer2.util.FlagSet
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.DeviceInfo
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.source.TrackGroup
import com.google.common.collect.ImmutableList
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import android.util.SparseArray
import com.google.android.exoplayer2.ext.cast.CastTimeline.ItemData
import android.util.SparseIntArray
import org.json.JSONObject
import com.google.android.exoplayer2.MediaItem.LocalConfiguration
import com.google.android.exoplayer2.util.MimeTypes
import org.json.JSONException
import com.google.android.exoplayer2.MediaItem.DrmConfiguration
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.exoplayer2.ext.cast.DefaultCastOptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
