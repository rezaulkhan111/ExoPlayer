/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cronet

import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import java.io.IOException
import java.nio.ByteBuffer

/** A [UploadDataProvider] implementation that provides data from a `byte[]`.  */ /* package */
internal class ByteArrayUploadDataProvider(private val data: ByteArray?) : UploadDataProvider() {
    private var position = 0
    override fun getLength(): Long {
        return data!!.size.toLong()
    }

    @Throws(IOException::class)
    override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
        val readLength = Math.min(byteBuffer.remaining(), data!!.size - position)
        byteBuffer.put(data, position, readLength)
        position += readLength
        uploadDataSink.onReadSucceeded(false)
    }

    @Throws(IOException::class)
    override fun rewind(uploadDataSink: UploadDataSink) {
        position = 0
        uploadDataSink.onRewindSucceeded()
    }
}