/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.drm

import com.google.android.exoplayer2.MediaItem

/**
 * A provider to obtain a [DrmSessionManager] suitable for playing the content described by a
 * [MediaItem].
 */
interface DrmSessionManagerProvider {
    /**
     * Returns a [DrmSessionManager] for the given media item.
     *
     *
     * The caller is responsible for [preparing][DrmSessionManager.prepare] the [ ] before use, and subsequently [releasing][DrmSessionManager.release]
     * it.
     */
    operator fun get(mediaItem: MediaItem?): DrmSessionManager?
}