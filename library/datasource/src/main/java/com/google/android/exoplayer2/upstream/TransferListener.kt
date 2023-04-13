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
package com.google.android.exoplayer2.upstream

/**
 * A listener of data transfer events.
 *
 *
 * A transfer usually progresses through multiple steps:
 *
 *
 *  1. Initializing the underlying resource (e.g. opening a HTTP connection). [       ][.onTransferInitializing] is called before the initialization
 * starts.
 *  1. Starting the transfer after successfully initializing the resource. [       ][.onTransferStart] is called. Note that this only happens if
 * the initialization was successful.
 *  1. Transferring data. [.onBytesTransferred] is
 * called frequently during the transfer to indicate progress.
 *  1. Closing the transfer and the underlying resource. [.onTransferEnd] is called. Note that each [.onTransferStart] will have exactly one corresponding call to [.onTransferEnd].
 *
 */
interface TransferListener {
    /**
     * Called when a transfer is being initialized.
     *
     * @param source The source performing the transfer.
     * @param dataSpec Describes the data for which the transfer is initialized.
     * @param isNetwork Whether the data is transferred through a network.
     */
    fun onTransferInitializing(source: DataSource?, dataSpec: DataSpec?, isNetwork: Boolean)

    /**
     * Called when a transfer starts.
     *
     * @param source The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     * @param isNetwork Whether the data is transferred through a network.
     */
    fun onTransferStart(source: DataSource?, dataSpec: DataSpec?, isNetwork: Boolean)

    /**
     * Called incrementally during a transfer.
     *
     * @param source The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     * @param isNetwork Whether the data is transferred through a network.
     * @param bytesTransferred The number of bytes transferred since the previous call to this method.
     */
    fun onBytesTransferred(
        source: DataSource?, dataSpec: DataSpec?, isNetwork: Boolean, bytesTransferred: Int
    )

    /**
     * Called when a transfer ends.
     *
     * @param source The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     * @param isNetwork Whether the data is transferred through a network.
     */
    fun onTransferEnd(source: DataSource?, dataSpec: DataSpec?, isNetwork: Boolean)
}