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

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.text.TextUtils
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.database.DatabaseIOException
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.database.VersionTable
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import java.util.ArrayList

/** A [DownloadIndex] that uses SQLite to persist [Download Downloads][Download]. */
class DefaultDownloadIndex @JvmOverloads constructor(
  private val databaseProvider: DatabaseProvider,
  private val name: String = "",
) : WritableDownloadIndex {

  private val tableName: String = TABLE_PREFIX + name
  private val initializationLock: Any = Any()

  @GuardedBy("initializationLock")
  private var initialized = false

  @Throws(DatabaseIOException::class)
  override fun getDownload(id: String?): Download? {
    ensureInitialized()
    if (id == null) {
      return null
    }
    try {
      getCursor(WHERE_ID_EQUALS, arrayOf(id)).use { cursor ->
        if (cursor.count == 0) {
          return null
        }
        cursor.moveToNext()
        return getDownloadForCurrentRow(cursor)
      }
    } catch (e: SQLiteException) {
      throw DatabaseIOException(e)
    }
  }

  @Throws(DatabaseIOException::class)
  override fun getDownloads(vararg states: Int): DownloadCursor {
    ensureInitialized()
    val cursor = getCursor(getStateQuery(*states), /* selectionArgs= */ null)
    return DownloadCursorImpl(cursor)
  }

  @Throws(DatabaseIOException::class)
  override fun putDownload(download: Download?) {
    ensureInitialized()
    val dl = Assertions.checkNotNull(download)!!
    try {
      val writableDatabase = databaseProvider.writableDatabase
      putDownloadInternal(dl, writableDatabase)
    } catch (e: SQLiteException) {
      throw DatabaseIOException(e)
    }
  }

  @Throws(DatabaseIOException::class)
  override fun removeDownload(id: String?) {
    ensureInitialized()
    if (id == null) {
      return
    }
    try {
      databaseProvider.writableDatabase.delete(tableName, WHERE_ID_EQUALS, arrayOf(id))
    } catch (e: SQLiteException) {
      throw DatabaseIOException(e)
    }
  }

  @Throws(DatabaseIOException::class)
  override fun setDownloadingStatesToQueued() {
    ensureInitialized()
    try {
      val values = ContentValues()
      values.put(COLUMN_STATE, Download.STATE_QUEUED)
      val writableDatabase = databaseProvider.writableDatabase
      writableDatabase.update(tableName, values, WHERE_STATE_IS_DOWNLOADING, /* whereArgs= */ null)
    } catch (e: SQLException) {
      throw DatabaseIOException(e)
    }
  }

  @Throws(DatabaseIOException::class)
  override fun setStatesToRemoving() {
    ensureInitialized()
    try {
      val values = ContentValues()
      values.put(COLUMN_STATE, Download.STATE_REMOVING)
      // Only downloads in STATE_FAILED are allowed a failure reason, so we need to clear it here in
      // case we're moving downloads from STATE_FAILED to STATE_REMOVING.
      values.put(COLUMN_FAILURE_REASON, Download.FAILURE_REASON_NONE)
      val writableDatabase = databaseProvider.writableDatabase
      writableDatabase.update(tableName, values, /* whereClause= */ null, /* whereArgs= */ null)
    } catch (e: SQLException) {
      throw DatabaseIOException(e)
    }
  }

  @Throws(DatabaseIOException::class)
  override fun setStopReason(stopReason: Int) {
    ensureInitialized()
    try {
      val values = ContentValues()
      values.put(COLUMN_STOP_REASON, stopReason)
      val writableDatabase = databaseProvider.writableDatabase
      writableDatabase.update(tableName, values, WHERE_STATE_IS_TERMINAL, /* whereArgs= */ null)
    } catch (e: SQLException) {
      throw DatabaseIOException(e)
    }
  }

  @Throws(DatabaseIOException::class)
  override fun setStopReason(id: String?, stopReason: Int) {
    ensureInitialized()
    if (id == null) {
      return
    }
    try {
      val values = ContentValues()
      values.put(COLUMN_STOP_REASON, stopReason)
      val writableDatabase = databaseProvider.writableDatabase
      writableDatabase.update(
        tableName,
        values,
        "$WHERE_STATE_IS_TERMINAL AND $WHERE_ID_EQUALS",
        arrayOf(id),
      )
    } catch (e: SQLException) {
      throw DatabaseIOException(e)
    }
  }

  @Throws(DatabaseIOException::class)
  private fun ensureInitialized() {
    synchronized(initializationLock) {
      if (initialized) {
        return
      }
      try {
        val readableDatabase = databaseProvider.readableDatabase
        val version = VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE, name)
        if (version != TABLE_VERSION) {
          val writableDatabase = databaseProvider.writableDatabase
          writableDatabase.beginTransactionNonExclusive()
          try {
            VersionTable.setVersion(
              writableDatabase, VersionTable.FEATURE_OFFLINE, name, TABLE_VERSION,
            )
            val upgradedDownloads =
              if (version == 2) loadDownloadsFromVersion2(writableDatabase) else ArrayList()
            writableDatabase.execSQL("DROP TABLE IF EXISTS $tableName")
            writableDatabase.execSQL("CREATE TABLE $tableName $TABLE_SCHEMA")
            for (download in upgradedDownloads) {
              putDownloadInternal(download, writableDatabase)
            }
            writableDatabase.setTransactionSuccessful()
          } finally {
            writableDatabase.endTransaction()
          }
        }
        initialized = true
      } catch (e: SQLException) {
        throw DatabaseIOException(e)
      }
    }
  }

  private fun putDownloadInternal(download: Download, database: SQLiteDatabase) {
    val keySetId = download.request.keySetId ?: Util.EMPTY_BYTE_ARRAY
    val values = ContentValues()
    values.put(COLUMN_ID, download.request.id)
    values.put(COLUMN_MIME_TYPE, download.request.mimeType)
    values.put(COLUMN_URI, download.request.uri.toString())
    values.put(COLUMN_STREAM_KEYS, encodeStreamKeys(download.request.streamKeys))
    values.put(COLUMN_CUSTOM_CACHE_KEY, download.request.customCacheKey)
    values.put(COLUMN_DATA, download.request.data)
    values.put(COLUMN_STATE, download.state)
    values.put(COLUMN_START_TIME_MS, download.startTimeMs)
    values.put(COLUMN_UPDATE_TIME_MS, download.updateTimeMs)
    values.put(COLUMN_CONTENT_LENGTH, download.contentLength)
    values.put(COLUMN_STOP_REASON, download.stopReason)
    values.put(COLUMN_FAILURE_REASON, download.failureReason)
    values.put(COLUMN_PERCENT_DOWNLOADED, download.percentDownloaded)
    values.put(COLUMN_BYTES_DOWNLOADED, download.bytesDownloaded)
    values.put(COLUMN_KEY_SET_ID, keySetId)
    database.replaceOrThrow(tableName, /* nullColumnHack= */ null, values)
  }

  private fun loadDownloadsFromVersion2(database: SQLiteDatabase): List<Download> {
    val downloads = ArrayList<Download>()
    if (!Util.tableExists(database, tableName)) {
      return downloads
    }
    val columnsV2 =
      arrayOf(
        "id",
        "title",
        "uri",
        "stream_keys",
        "custom_cache_key",
        "data",
        "state",
        "start_time_ms",
        "update_time_ms",
        "content_length",
        "stop_reason",
        "failure_reason",
        "percent_downloaded",
        "bytes_downloaded",
      )
    try {
      database.query(
        tableName,
        columnsV2,
        /* selection= */ null,
        /* selectionArgs= */ null,
        /* groupBy= */ null,
        /* having= */ null,
        /* orderBy= */ null,
      ).use { cursor ->
        while (cursor.moveToNext()) {
          downloads.add(getDownloadForCurrentRowV2(cursor))
        }
        return downloads
      }
    } catch (_: SQLiteException) {
      return downloads
    }
  }

  @Throws(DatabaseIOException::class)
  private fun getCursor(selection: String, selectionArgs: Array<String>?): Cursor {
    try {
      val sortOrder = "$COLUMN_START_TIME_MS ASC"
      return databaseProvider
        .readableDatabase
        .query(
          tableName,
          COLUMNS,
          selection,
          selectionArgs,
          /* groupBy= */ null,
          /* having= */ null,
          sortOrder,
        )
    } catch (e: SQLiteException) {
      throw DatabaseIOException(e)
    }
  }

  private class DownloadCursorImpl(private val cursor: Cursor) : DownloadCursor {
    override fun getDownload(): Download = getDownloadForCurrentRow(cursor)
    override fun getCount(): Int = cursor.count
    override fun getPosition(): Int = cursor.position
    override fun moveToPosition(position: Int): Boolean = cursor.moveToPosition(position)
    override fun close() = cursor.close()
    override fun isClosed(): Boolean = cursor.isClosed
  }

  companion object {
    private const val TABLE_PREFIX = DatabaseProvider.TABLE_PREFIX + "Downloads"

    @VisibleForTesting
    const val TABLE_VERSION = 3

    private const val COLUMN_ID = "id"
    private const val COLUMN_MIME_TYPE = "mime_type"
    private const val COLUMN_URI = "uri"
    private const val COLUMN_STREAM_KEYS = "stream_keys"
    private const val COLUMN_CUSTOM_CACHE_KEY = "custom_cache_key"
    private const val COLUMN_DATA = "data"
    private const val COLUMN_STATE = "state"
    private const val COLUMN_START_TIME_MS = "start_time_ms"
    private const val COLUMN_UPDATE_TIME_MS = "update_time_ms"
    private const val COLUMN_CONTENT_LENGTH = "content_length"
    private const val COLUMN_STOP_REASON = "stop_reason"
    private const val COLUMN_FAILURE_REASON = "failure_reason"
    private const val COLUMN_PERCENT_DOWNLOADED = "percent_downloaded"
    private const val COLUMN_BYTES_DOWNLOADED = "bytes_downloaded"
    private const val COLUMN_KEY_SET_ID = "key_set_id"

    private const val COLUMN_INDEX_ID = 0
    private const val COLUMN_INDEX_MIME_TYPE = 1
    private const val COLUMN_INDEX_URI = 2
    private const val COLUMN_INDEX_STREAM_KEYS = 3
    private const val COLUMN_INDEX_CUSTOM_CACHE_KEY = 4
    private const val COLUMN_INDEX_DATA = 5
    private const val COLUMN_INDEX_STATE = 6
    private const val COLUMN_INDEX_START_TIME_MS = 7
    private const val COLUMN_INDEX_UPDATE_TIME_MS = 8
    private const val COLUMN_INDEX_CONTENT_LENGTH = 9
    private const val COLUMN_INDEX_STOP_REASON = 10
    private const val COLUMN_INDEX_FAILURE_REASON = 11
    private const val COLUMN_INDEX_PERCENT_DOWNLOADED = 12
    private const val COLUMN_INDEX_BYTES_DOWNLOADED = 13
    private const val COLUMN_INDEX_KEY_SET_ID = 14

    private const val WHERE_ID_EQUALS = "$COLUMN_ID = ?"
    private const val WHERE_STATE_IS_DOWNLOADING = "$COLUMN_STATE = ${Download.STATE_DOWNLOADING}"
    private val WHERE_STATE_IS_TERMINAL =
      getStateQuery(Download.STATE_COMPLETED, Download.STATE_FAILED)

    private val COLUMNS =
      arrayOf(
        COLUMN_ID,
        COLUMN_MIME_TYPE,
        COLUMN_URI,
        COLUMN_STREAM_KEYS,
        COLUMN_CUSTOM_CACHE_KEY,
        COLUMN_DATA,
        COLUMN_STATE,
        COLUMN_START_TIME_MS,
        COLUMN_UPDATE_TIME_MS,
        COLUMN_CONTENT_LENGTH,
        COLUMN_STOP_REASON,
        COLUMN_FAILURE_REASON,
        COLUMN_PERCENT_DOWNLOADED,
        COLUMN_BYTES_DOWNLOADED,
        COLUMN_KEY_SET_ID,
      )

    private const val TABLE_SCHEMA =
      "($COLUMN_ID TEXT PRIMARY KEY NOT NULL,$COLUMN_MIME_TYPE TEXT,$COLUMN_URI TEXT NOT NULL,$COLUMN_STREAM_KEYS TEXT NOT NULL,$COLUMN_CUSTOM_CACHE_KEY TEXT,$COLUMN_DATA BLOB NOT NULL,$COLUMN_STATE INTEGER NOT NULL,$COLUMN_START_TIME_MS INTEGER NOT NULL,$COLUMN_UPDATE_TIME_MS INTEGER NOT NULL,$COLUMN_CONTENT_LENGTH INTEGER NOT NULL,$COLUMN_STOP_REASON INTEGER NOT NULL,$COLUMN_FAILURE_REASON INTEGER NOT NULL,$COLUMN_PERCENT_DOWNLOADED REAL NOT NULL,$COLUMN_BYTES_DOWNLOADED INTEGER NOT NULL,$COLUMN_KEY_SET_ID BLOB NOT NULL)"

    private const val TRUE = "1"

    @VisibleForTesting
    @JvmStatic
    fun encodeStreamKeys(streamKeys: List<StreamKey>): String {
      val stringBuilder = StringBuilder()
      for (i in streamKeys.indices) {
        val streamKey = streamKeys[i]
        stringBuilder
          .append(streamKey.periodIndex)
          .append('.')
          .append(streamKey.groupIndex)
          .append('.')
          .append(streamKey.streamIndex)
          .append(',')
      }
      if (stringBuilder.isNotEmpty()) {
        stringBuilder.setLength(stringBuilder.length - 1)
      }
      return stringBuilder.toString()
    }

    private fun getStateQuery(vararg states: Int): String {
      if (states.isEmpty()) {
        return TRUE
      }
      val selectionBuilder = StringBuilder()
      selectionBuilder.append(COLUMN_STATE).append(" IN (")
      for (i in states.indices) {
        if (i > 0) {
          selectionBuilder.append(',')
        }
        selectionBuilder.append(states[i])
      }
      selectionBuilder.append(')')
      return selectionBuilder.toString()
    }

    private fun getDownloadForCurrentRow(cursor: Cursor): Download {
      val keySetId = cursor.getBlob(COLUMN_INDEX_KEY_SET_ID)
      val request =
        DownloadRequest.Builder(
          /* id= */ Assertions.checkNotNull(cursor.getString(COLUMN_INDEX_ID)),
          /* uri= */ Uri.parse(Assertions.checkNotNull(cursor.getString(COLUMN_INDEX_URI))),
        )
          .setMimeType(cursor.getString(COLUMN_INDEX_MIME_TYPE))
          .setStreamKeys(decodeStreamKeys(cursor.getString(COLUMN_INDEX_STREAM_KEYS)))
          .setKeySetId(if (keySetId.isNotEmpty()) keySetId else null)
          .setCustomCacheKey(cursor.getString(COLUMN_INDEX_CUSTOM_CACHE_KEY))
          .setData(cursor.getBlob(COLUMN_INDEX_DATA))
          .build()
      val downloadProgress = DownloadProgress()
      downloadProgress.bytesDownloaded = cursor.getLong(COLUMN_INDEX_BYTES_DOWNLOADED)
      downloadProgress.percentDownloaded = cursor.getFloat(COLUMN_INDEX_PERCENT_DOWNLOADED)
      @Download.State val state = cursor.getInt(COLUMN_INDEX_STATE)
      @Download.FailureReason
      val failureReason =
        if (state == Download.STATE_FAILED) cursor.getInt(COLUMN_INDEX_FAILURE_REASON)
        else Download.FAILURE_REASON_NONE
      return Download(
        request,
        state,
        /* startTimeMs= */ cursor.getLong(COLUMN_INDEX_START_TIME_MS),
        /* updateTimeMs= */ cursor.getLong(COLUMN_INDEX_UPDATE_TIME_MS),
        /* contentLength= */ cursor.getLong(COLUMN_INDEX_CONTENT_LENGTH),
        /* stopReason= */ cursor.getInt(COLUMN_INDEX_STOP_REASON),
        failureReason,
        downloadProgress,
      )
    }

    private fun getDownloadForCurrentRowV2(cursor: Cursor): Download {
      val request =
        DownloadRequest.Builder(
          /* id= */ Assertions.checkNotNull(cursor.getString(0)),
          /* uri= */ Uri.parse(Assertions.checkNotNull(cursor.getString(2))),
        )
          .setMimeType(inferMimeType(cursor.getString(1)))
          .setStreamKeys(decodeStreamKeys(cursor.getString(3)))
          .setCustomCacheKey(cursor.getString(4))
          .setData(cursor.getBlob(5))
          .build()
      val downloadProgress = DownloadProgress()
      downloadProgress.bytesDownloaded = cursor.getLong(13)
      downloadProgress.percentDownloaded = cursor.getFloat(12)
      @Download.State val state = cursor.getInt(6)
      @Download.FailureReason
      val failureReason =
        if (state == Download.STATE_FAILED) cursor.getInt(11) else Download.FAILURE_REASON_NONE
      return Download(
        request,
        state,
        /* startTimeMs= */ cursor.getLong(7),
        /* updateTimeMs= */ cursor.getLong(8),
        /* contentLength= */ cursor.getLong(9),
        /* stopReason= */ cursor.getInt(10),
        failureReason,
        downloadProgress,
      )
    }

    private fun inferMimeType(downloadType: String?): String {
      return when (downloadType) {
        "dash" -> MimeTypes.APPLICATION_MPD
        "hls" -> MimeTypes.APPLICATION_M3U8
        "ss" -> MimeTypes.APPLICATION_SS
        else -> MimeTypes.VIDEO_UNKNOWN
      }
    }

    private fun decodeStreamKeys(encodedStreamKeys: String?): List<StreamKey> {
      val streamKeys = ArrayList<StreamKey>()
      if (TextUtils.isEmpty(encodedStreamKeys)) {
        return streamKeys
      }
      val streamKeysStrings = Util.split(encodedStreamKeys!!, ",")
      for (streamKeysString in streamKeysStrings) {
        val indices = Util.split(streamKeysString!!, "\\.")
        Assertions.checkState(indices.size == 3)
        streamKeys.add(
          StreamKey(
            indices[0]!!.toInt(),
            indices[1]!!.toInt(),
            indices[2]!!.toInt(),
          ),
        )
      }
      return streamKeys
    }
  }
}
