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
package com.google.android.exoplayer2.ext.okhttp

import okhttp3.Response.request
import okhttp3.Request.url
import okhttp3.HttpUrl.toString
import okhttp3.Response.code
import okhttp3.Response.headers
import okhttp3.Headers.toMultimap
import okhttp3.Call.Factory.newCall
import okhttp3.Response.body
import okhttp3.ResponseBody.byteStream
import okhttp3.Response.isSuccessful
import okhttp3.Headers.get
import okhttp3.Response.message
import okhttp3.ResponseBody.contentType
import okhttp3.MediaType.toString
import okhttp3.ResponseBody.contentLength
import okhttp3.Request.Builder.url
import okhttp3.Request.Builder.cacheControl
import okhttp3.Request.Builder.header
import okhttp3.Request.Builder.addHeader
import okhttp3.Request.Builder.method
import okhttp3.Request.Builder.build
import okhttp3.Call.enqueue
import okhttp3.Call.cancel
import okhttp3.ResponseBody.close
import okhttp3.CacheControl
import com.google.android.exoplayer2.upstream.HttpDataSource.RequestProperties
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException
import okhttp3.ResponseBody
import com.google.android.exoplayer2.upstream.DataSourceException
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidContentTypeException
import okhttp3.HttpUrl
import okhttp3.RequestBody
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.upstream.HttpDataSource.BaseFactory
