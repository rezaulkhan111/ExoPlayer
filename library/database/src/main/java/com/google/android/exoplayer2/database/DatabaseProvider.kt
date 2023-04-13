/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.database

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException

/**
 * Provides [SQLiteDatabase] instances to media library components, which may read and write
 * tables prefixed with [.TABLE_PREFIX].
 */
interface DatabaseProvider {
    companion object {
        /** Prefix for tables that can be read and written by media library components.  */
        const val TABLE_PREFIX = "ExoPlayer"
    }
    /**
     * Creates and/or opens a database that will be used for reading and writing.
     *
     *
     * Once opened successfully, the database is cached, so you can call this method every time you
     * need to write to the database. Errors such as bad permissions or a full disk may cause this
     * method to fail, but future attempts may succeed if the problem is fixed.
     *
     * @throws SQLiteException If the database cannot be opened for writing.
     * @return A read/write database object.
     */
    fun getWritableDatabase(): SQLiteDatabase?

    /**
     * Creates and/or opens a database. This will be the same object returned by [ ][.getWritableDatabase] unless some problem, such as a full disk, requires the database to be
     * opened read-only. In that case, a read-only database object will be returned. If the problem is
     * fixed, a future call to [.getWritableDatabase] may succeed, in which case the read-only
     * database object will be closed and the read/write object will be returned in the future.
     *
     *
     * Once opened successfully, the database is cached, so you can call this method every time you
     * need to read from the database.
     *
     * @throws SQLiteException If the database cannot be opened.
     * @return A database object valid until [.getWritableDatabase] is called.
     */
    fun getReadableDatabase(): SQLiteDatabase?
}