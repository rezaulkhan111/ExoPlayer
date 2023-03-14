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
package com.google.android.exoplayer2.ext.ima

import com.google.android.exoplayer2.ui.AdOverlayInfo
import com.google.ads.interactivemedia.v3.api.FriendlyObstructionPurpose
import com.google.android.exoplayer2.ext.ima.ImaUtil.ImaFactory
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.ads.interactivemedia.v3.api.AdsRequest
import com.google.android.exoplayer2.upstream.DataSchemeDataSource
import com.google.android.exoplayer2.upstream.DataSourceUtil
import com.google.ads.interactivemedia.v3.api.AdError
import android.os.Looper
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.ext.ima.ImaUtil
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdGroup
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil
import com.google.common.math.DoubleMath
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings
import android.view.ViewGroup
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer
import com.google.ads.interactivemedia.v3.api.FriendlyObstruction
import com.google.ads.interactivemedia.v3.api.UiElement
import com.google.ads.interactivemedia.v3.api.CompanionAdSlot
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback
import com.google.android.exoplayer2.ui.AdViewProvider
import com.google.common.collect.ImmutableList
import com.google.android.exoplayer2.Player
import androidx.annotation.IntDef
import com.google.android.exoplayer2.ext.ima.AdTagLoader
import com.google.common.collect.BiMap
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo
import com.google.android.exoplayer2.ext.ima.AdTagLoader.AdInfo
import com.google.ads.interactivemedia.v3.api.AdsManager
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException
import com.google.android.exoplayer2.ext.ima.AdTagLoader.ImaAdState
import com.google.common.collect.HashBiMap
import com.google.android.exoplayer2.Player.TimelineChangeReason
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.PlaybackException
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType
import com.google.ads.interactivemedia.v3.api.AdPodInfo
import com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader.DefaultImaFactory
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader.PlayerListenerImpl
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import com.google.android.exoplayer2.util.MimeTypes
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import com.google.android.exoplayer2.ext.ima.ImaServerSideAdInsertionUriBuilder
import android.text.TextUtils
import com.google.ads.interactivemedia.v3.api.StreamRequest
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat
import com.google.android.exoplayer2.ext.ima.ImaServerSideAdInsertionMediaSource.StreamPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.CompositeMediaSource
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider
import com.google.android.exoplayer2.MediaItem.LocalConfiguration
import com.google.ads.interactivemedia.v3.api.StreamDisplayContainer
import com.google.android.exoplayer2.ext.ima.ImaServerSideAdInsertionMediaSource
import com.google.android.exoplayer2.ext.ima.ImaUtil.ServerSideAdInsertionConfiguration
import com.google.android.exoplayer2.Bundleable
import android.os.Bundle
import com.google.android.exoplayer2.ext.ima.ImaServerSideAdInsertionMediaSource.AdsLoader.MediaSourceResourceHolder
import com.google.android.exoplayer2.source.ads.ServerSideAdInsertionMediaSource
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.ext.ima.ImaServerSideAdInsertionMediaSource.StreamManagerLoadable
import com.google.android.exoplayer2.ext.ima.ImaServerSideAdInsertionMediaSource.StreamManagerLoadableCallback
import com.google.android.exoplayer2.source.ForwardingTimeline
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.source.MediaPeriod
import androidx.annotation.MainThread
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import com.google.android.exoplayer2.source.ads.ServerSideAdInsertionMediaSource.AdPlaybackStateUpdater
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame
import com.google.android.exoplayer2.metadata.emsg.EventMessage
import com.google.ads.interactivemedia.v3.api.Ad
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction
import com.google.android.exoplayer2.ext.ima.ImaServerSideAdInsertionMediaSource.StreamPlayer.StreamLoadListener
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer.VideoStreamPlayerCallback
import com.google.ads.interactivemedia.v3.api.CuePoint
