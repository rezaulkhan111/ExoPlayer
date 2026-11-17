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
package com.google.android.exoplayer2.drm

import android.net.Uri
import com.google.android.exoplayer2.upstream.DataSpec
import java.io.IOException

/**
 * Thrown when an error occurs while executing a DRM [key][MediaDrmCallback.executeKeyRequest]
 * or [provisioning][MediaDrmCallback.executeProvisionRequest] request.
 */
class MediaDrmCallbackException
/**
 * Creates a new instance with the given values.
 * 
 * @param dataSpec See [.dataSpec].
 * @param uriAfterRedirects See [.uriAfterRedirects].
 * @param responseHeaders See [.responseHeaders].
 * @param bytesLoaded See [.bytesLoaded].
 * @param cause The cause of the exception.
 */(
    /** The [DataSpec] associated with the request.  */
    val dataSpec: DataSpec?,
    /**
     * The [Uri] after redirections, or [dataSpec.uri][.dataSpec] if no redirection
     * occurred.
     */
    val uriAfterRedirects: Uri?,
    /** The HTTP request headers included in the response.  */
    val responseHeaders: MutableMap<String?, MutableList<String?>?>?,
    /** The number of bytes obtained from the server.  */
    val bytesLoaded: Long,
    cause: Throwable?
) : IOException(cause)
