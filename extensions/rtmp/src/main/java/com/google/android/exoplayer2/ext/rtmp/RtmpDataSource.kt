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
package com.google.android.exoplayer2.ext.rtmp

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.util.Util
import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.antmedia.rtmp_client.RtmpClient
import io.antmedia.rtmp_client.RtmpClient.RtmpIOException
import java.io.IOException

/** A Real-Time Messaging Protocol (RTMP) [DataSource].  */
class RtmpDataSource : BaseDataSource( /* isNetwork= */true) {
    /** [DataSource.Factory] for [RtmpDataSource] instances.  */
    class Factory : DataSource.Factory {
        private var transferListener: TransferListener? = null

        /**
         * Sets the [TransferListener] that will be used.
         *
         *
         * The default is `null`.
         *
         *
         * See [DataSource.addTransferListener].
         *
         * @param transferListener The listener that will be used.
         * @return This factory.
         */
        @CanIgnoreReturnValue
        fun setTransferListener(transferListener: TransferListener?): Factory {
            this.transferListener = transferListener
            return this
        }

        override fun createDataSource(): RtmpDataSource {
            val dataSource = RtmpDataSource()
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener!!)
            }
            return dataSource
        }
    }

    private var rtmpClient: RtmpClient? = null
    private var uri: Uri? = null
    @Throws(RtmpIOException::class)
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        rtmpClient = RtmpClient()
        rtmpClient!!.open(dataSpec.uri.toString(), false)
        uri = dataSpec.uri
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = Util.castNonNull(rtmpClient).read(buffer, offset, length)
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun close() {
        if (uri != null) {
            uri = null
            transferEnded()
        }
        if (rtmpClient != null) {
            rtmpClient!!.close()
            rtmpClient = null
        }
    }

    override fun getUri(): Uri? {
        return uri
    }

    companion object {
        init {
            ExoPlayerLibraryInfo.registerModule("goog.exo.rtmp")
        }
    }
}