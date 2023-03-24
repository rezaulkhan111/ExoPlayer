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
package com.google.android.exoplayer2.offline

import java.io.Closeable

/** Provides random read-write access to the result set returned by a database query.  */
interface DownloadCursor : Closeable {
    /** Returns the download at the current position.  */
    fun getDownload(): Download?

    /** Returns the numbers of downloads in the cursor.  */
    fun getCount(): Int

    /**
     * Returns the current position of the cursor in the download set. The value is zero-based. When
     * the download set is first returned the cursor will be at position -1, which is before the first
     * download. After the last download is returned another call to next() will leave the cursor past
     * the last entry, at a position of count().
     *
     * @return the current cursor position.
     */
    fun getPosition(): Int

    /**
     * Move the cursor to an absolute position. The valid range of values is -1 &lt;= position &lt;=
     * count.
     *
     *
     * This method will return true if the request destination was reachable, otherwise, it returns
     * false.
     *
     * @param position the zero-based position to move to.
     * @return whether the requested move fully succeeded.
     */
    fun moveToPosition(position: Int): Boolean

    /**
     * Move the cursor to the first download.
     *
     *
     * This method will return false if the cursor is empty.
     *
     * @return whether the move succeeded.
     */
    fun moveToFirst(): Boolean {
        return moveToPosition(0)
    }

    /**
     * Move the cursor to the last download.
     *
     *
     * This method will return false if the cursor is empty.
     *
     * @return whether the move succeeded.
     */
    open fun moveToLast(): Boolean {
        return moveToPosition(getCount() - 1)
    }

    /**
     * Move the cursor to the next download.
     *
     *
     * This method will return false if the cursor is already past the last entry in the result
     * set.
     *
     * @return whether the move succeeded.
     */
    fun moveToNext(): Boolean {
        return moveToPosition(getPosition() + 1)
    }

    /**
     * Move the cursor to the previous download.
     *
     *
     * This method will return false if the cursor is already before the first entry in the result
     * set.
     *
     * @return whether the move succeeded.
     */
    fun moveToPrevious(): Boolean {
        return moveToPosition(getPosition() - 1)
    }

    /** Returns whether the cursor is pointing to the first download.  */
    fun isFirst(): Boolean {
        return getPosition() == 0 && getCount() != 0
    }

    /** Returns whether the cursor is pointing to the last download.  */
    fun isLast(): Boolean {
        val count = getCount()
        return getPosition() == count - 1 && count != 0
    }

    /** Returns whether the cursor is pointing to the position before the first download.  */
    fun isBeforeFirst(): Boolean {
        return if (getCount() == 0) {
            true
        } else getPosition() == -1
    }

    /** Returns whether the cursor is pointing to the position after the last download.  */
    fun isAfterLast(): Boolean {
        return if (getCount() == 0) {
            true
        } else getPosition() == getCount()
    }

    /** Returns whether the cursor is closed  */
    fun isClosed(): Boolean

    override fun close()
}