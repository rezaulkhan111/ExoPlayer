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
package com.google.android.exoplayer2.util

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Activity
import android.app.UiModeManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.media.*
import android.net.Uri
import android.os.*
import android.os.SystemClock
import android.provider.MediaStore
import android.security.NetworkSecurityPolicy
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Base64
import android.util.SparseLongArray
import android.view.*
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.*
import com.google.android.exoplayer2.C.AudioContentType
import com.google.android.exoplayer2.C.AudioUsage
import com.google.android.exoplayer2.C.PcmEncoding
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.MediaItem.SubtitleConfiguration
import com.google.android.exoplayer2.util.Assertions.checkArgument
import com.google.android.exoplayer2.util.Assertions.checkNotNull
import com.google.android.exoplayer2.util.Assertions.checkState
import com.google.android.exoplayer2.util.Assertions.checkStateNotNull
import com.google.android.exoplayer2.util.Log.e
import com.google.android.exoplayer2.util.MimeTypes.getTrackTypeOfCodec
import com.google.common.base.Ascii
import com.google.common.base.Charsets
import com.google.common.util.concurrent.*
import org.checkerframework.checker.initialization.qual.UnknownInitialization
import org.checkerframework.checker.nullness.compatqual.NullableType
import org.checkerframework.checker.nullness.qual.EnsuresNonNull
import org.checkerframework.checker.nullness.qual.PolyNull
import java.io.*
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern
import java.util.zip.DataFormatException
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Miscellaneous utility methods.  */
object Util {

    /**
     * Like [Build.VERSION.SDK_INT], but in a place where it can be conveniently overridden for
     * local testing.
     */
    val SDK_INT = Build.VERSION.SDK_INT

    /**
     * Like [Build.DEVICE], but in a place where it can be conveniently overridden for local
     * testing.
     */
    val DEVICE = Build.DEVICE

    /**
     * Like [Build.MANUFACTURER], but in a place where it can be conveniently overridden for
     * local testing.
     */
    val MANUFACTURER = Build.MANUFACTURER

    /**
     * Like [Build.MODEL], but in a place where it can be conveniently overridden for local
     * testing.
     */
    val MODEL = Build.MODEL

    /** A concise description of the device that it can be useful to log for debugging purposes.  */
    val DEVICE_DEBUG_INFO = "$DEVICE, $MODEL, $MANUFACTURER, $SDK_INT"

    /** An empty byte array.  */
    val EMPTY_BYTE_ARRAY = ByteArray(0)

    private val TAG = "Util"
    private val XS_DATE_TIME_PATTERN = Pattern.compile("(\\d\\d\\d\\d)\\-(\\d\\d)\\-(\\d\\d)[Tt]" + "(\\d\\d):(\\d\\d):(\\d\\d)([\\.,](\\d+))?" + "([Zz]|((\\+|\\-)(\\d?\\d):?(\\d\\d)))?")
    private val XS_DURATION_PATTERN = Pattern.compile(("^(-)?P(([0-9]*)Y)?(([0-9]*)M)?(([0-9]*)D)?" + "(T(([0-9]*)H)?(([0-9]*)M)?(([0-9.]*)S)?)?$"))
    private val ESCAPED_CHARACTER_PATTERN = Pattern.compile("%([A-Fa-f0-9]{2})")

    // https://docs.microsoft.com/en-us/azure/media-services/previous/media-services-deliver-content-overview#URLs
    private val ISM_PATH_PATTERN = Pattern.compile("(?:.*\\.)?isml?(?:/(manifest(.*))?)?", Pattern.CASE_INSENSITIVE)
    private const val ISM_HLS_FORMAT_EXTENSION = "format=m3u8-aapl"
    private const val ISM_DASH_FORMAT_EXTENSION = "format=mpd-time-csf"

    // Replacement map of ISO language codes used for normalization.
    private var languageTagReplacementMap: HashMap<String, String>? = null

    /**
     * Converts the entirety of an [InputStream] to a byte array.
     *
     * @param inputStream the [InputStream] to be read. The input stream is not closed by this
     * method.
     * @return a byte array containing all of the inputStream's bytes.
     * @throws IOException if an error occurs reading from the stream.
     */
    @Throws(IOException::class)
    fun toByteArray(inputStream: InputStream): ByteArray? {
        val buffer = ByteArray(1024 * 4)
        val outputStream = ByteArrayOutputStream()
        var bytesRead: Int
        while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        return outputStream.toByteArray()
    }

    /**
     * Registers a [BroadcastReceiver] that's not intended to receive broadcasts from other
     * apps. This will be enforced by specifying [Context.RECEIVER_NOT_EXPORTED] if [ ][.SDK_INT] is 33 or above.
     *
     * @param context The context on which [Context.registerReceiver] will be called.
     * @param receiver The [BroadcastReceiver] to register. This value may be null.
     * @param filter Selects the Intent broadcasts to be received.
     * @return The first sticky intent found that matches `filter`, or null if there are none.
     */
    @SuppressLint("NewApi")
    fun registerReceiverNotExported(context: Context, receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        return if (SDK_INT < 33) {
            context.registerReceiver(receiver, filter)
        } else {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }
    }

    /**
     * Registers a [BroadcastReceiver] that's not intended to receive broadcasts from other
     * apps. This will be enforced by specifying [Context.RECEIVER_NOT_EXPORTED] if [ ][.SDK_INT] is 33 or above.
     *
     * @param context The context on which [Context.registerReceiver] will be called.
     * @param receiver The [BroadcastReceiver] to register. This value may be null.
     * @param filter Selects the Intent broadcasts to be received.
     * @param handler Handler identifying the thread that will receive the Intent.
     * @return The first sticky intent found that matches `filter`, or null if there are none.
     */
    @SuppressLint("NewApi")
    fun registerReceiverNotExported(context: Context, receiver: BroadcastReceiver?, filter: IntentFilter?, handler: Handler?): Intent? {
        return if (SDK_INT < 33) {
            context.registerReceiver(receiver, filter,  /* broadcastPermission= */null, handler)
        } else {
            context.registerReceiver(receiver, filter,  /* broadcastPermission= */
                    null, handler, Context.RECEIVER_NOT_EXPORTED)
        }
    }

    /**
     * Calls [Context.startForegroundService] if [.SDK_INT] is 26 or higher, or
     * [Context.startService] otherwise.
     *
     * @param context The context to call.
     * @param intent The intent to pass to the called method.
     * @return The result of the called method.
     */
    @SuppressLint("NewApi")
    fun startForegroundService(context: Context, intent: Intent?): ComponentName? {
        return if (SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Checks whether it's necessary to request the [permission.READ_EXTERNAL_STORAGE]
     * permission read the specified [Uri]s, requesting the permission if necessary.
     *
     * @param activity The host activity for checking and requesting the permission.
     * @param uris [Uri]s that may require [permission.READ_EXTERNAL_STORAGE] to read.
     * @return Whether a permission request was made.
     */
    fun maybeRequestReadExternalStoragePermission(activity: Activity, vararg uris: Uri): Boolean {
        if (SDK_INT < 23) {
            return false
        }
        for (uri: Uri in uris) {
            if (maybeRequestReadExternalStoragePermission(activity, uri)) {
                return true
            }
        }
        return false
    }

    /**
     * Checks whether it's necessary to request the [permission.READ_EXTERNAL_STORAGE]
     * permission for the specified [media items][MediaItem], requesting the permission if
     * necessary.
     *
     * @param activity The host activity for checking and requesting the permission.
     * @param mediaItems [Media items][MediaItem]s that may require [     ][permission.READ_EXTERNAL_STORAGE] to read.
     * @return Whether a permission request was made.
     */
    fun maybeRequestReadExternalStoragePermission(activity: Activity, vararg mediaItems: MediaItem): Boolean {
        if (SDK_INT < 23) {
            return false
        }
        for (mediaItem: MediaItem in mediaItems) {
            if (mediaItem.localConfiguration == null) {
                continue
            }
            if (maybeRequestReadExternalStoragePermission(activity, mediaItem.localConfiguration.uri)) {
                return true
            }
            val subtitleConfigs: List<SubtitleConfiguration?> = mediaItem.localConfiguration.subtitleConfigurations
            for (i in subtitleConfigs.indices) {
                if (maybeRequestReadExternalStoragePermission(activity, subtitleConfigs[i]!!.uri)) {
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("NewApi")
    private fun maybeRequestReadExternalStoragePermission(activity: Activity, uri: Uri): Boolean {
        return ((SDK_INT >= 23) && (isLocalFileUri(uri) || isMediaStoreExternalContentUri(uri)) && requestExternalStoragePermission(activity))
    }

    private fun isMediaStoreExternalContentUri(uri: Uri): Boolean {
        if ("content" != uri.scheme || MediaStore.AUTHORITY != uri.authority) {
            return false
        }
        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) {
            return false
        }
        val firstPathSegment = pathSegments[0]
        return ((MediaStore.VOLUME_EXTERNAL == firstPathSegment) || (MediaStore.VOLUME_EXTERNAL_PRIMARY == firstPathSegment))
    }

    /**
     * Returns whether it may be possible to load the URIs of the given media items based on the
     * network security policy's cleartext traffic permissions.
     *
     * @param mediaItems A list of [media items][MediaItem].
     * @return Whether it may be possible to load the URIs of the given media items.
     */

    fun checkCleartextTrafficPermitted(vararg mediaItems: MediaItem): Boolean {
        if (SDK_INT < 24) {
            // We assume cleartext traffic is permitted.
            return true
        }
        for (mediaItem: MediaItem in mediaItems) {
            if (mediaItem.localConfiguration == null) {
                continue
            }
            if (isTrafficRestricted(mediaItem.localConfiguration.uri)) {
                return false
            }
            for (i in mediaItem.localConfiguration.subtitleConfigurations.indices) {
                if (isTrafficRestricted(mediaItem.localConfiguration.subtitleConfigurations[i]!!.uri)) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Returns true if the URI is a path to a local file or a reference to a local file.
     *
     * @param uri The uri to test.
     */
    fun isLocalFileUri(uri: Uri): Boolean {
        val scheme = uri.scheme
        return TextUtils.isEmpty(scheme) || ("file" == scheme)
    }

    /**
     * Tests two objects for [Object.equals] equality, handling the case where one or
     * both may be null.
     *
     * @param o1 The first object.
     * @param o2 The second object.
     * @return `o1 == null ? o2 == null : o1.equals(o2)`.
     */
    fun areEqual(o1: Any?, o2: Any?): Boolean {
        return if (o1 == null) o2 == null else (o1 == o2)
    }

    /**
     * Tests whether an `items` array contains an object equal to `item`, according to
     * [Object.equals].
     *
     *
     * If `item` is null then true is returned if and only if `items` contains null.
     *
     * @param items The array of items to search.
     * @param item The item to search for.
     * @return True if the array contains an object equal to the item being searched for.
     */
    fun contains(items: Array<Any?>, item: Any?): Boolean {
        for (arrayItem: Any? in items) {
            if (areEqual(arrayItem, item)) {
                return true
            }
        }
        return false
    }

    /**
     * Removes an indexed range from a List.
     *
     *
     * Does nothing if the provided range is valid and `fromIndex == toIndex`.
     *
     * @param list The List to remove the range from.
     * @param fromIndex The first index to be removed (inclusive).
     * @param toIndex The last index to be removed (exclusive).
     * @throws IllegalArgumentException If `fromIndex` &lt; 0, `toIndex` &gt; `list.size()`, or `fromIndex` &gt; `toIndex`.
     */
    fun <T> removeRange(list: List<T>, fromIndex: Int, toIndex: Int) {
        if ((fromIndex < 0) || (toIndex > list.size) || (fromIndex > toIndex)) {
            throw IllegalArgumentException()
        } else if (fromIndex != toIndex) {
            // Checking index inequality prevents an unnecessary allocation.
            list.subList(fromIndex, toIndex).clear()
        }
    }

    /**
     * Casts a nullable variable to a non-null variable without runtime null check.
     *
     *
     * Use [Assertions.checkNotNull] to throw if the value is null.
     */
    @EnsuresNonNull("#1")
    fun <T> castNonNull(value: T?): T? {
        return value
    }

    /** Casts a nullable type array to a non-null type array without runtime null check.  */
    @EnsuresNonNull("#1")
    fun <T> castNonNullTypeArray(value: Array<T>?): Array<T>? {
        return value
    }

    /**
     * Copies and optionally truncates an array. Prevents null array elements created by [ ][Arrays.copyOf] by ensuring the new length does not exceed the current length.
     *
     * @param input The input array.
     * @param length The output array length. Must be less or equal to the length of the input array.
     * @return The copied array.
     */
    fun <T> nullSafeArrayCopy(input: Array<T>, length: Int): Array<T>? {
        checkArgument(length <= input.size)
        return Arrays.copyOf(input, length)
    }

    /**
     * Copies a subset of an array.
     *
     * @param input The input array.
     * @param from The start the range to be copied, inclusive
     * @param to The end of the range to be copied, exclusive.
     * @return The copied array.
     */
    fun <T> nullSafeArrayCopyOfRange(input: Array<T>, from: Int, to: Int): Array<T>? {
        checkArgument(0 <= from)
        checkArgument(to <= input.size)
        return input.copyOfRange(from, to)
    }

    /**
     * Creates a new array containing `original` with `newElement` appended.
     *
     * @param original The input array.
     * @param newElement The element to append.
     * @return The new array.
     */
    fun <T> nullSafeArrayAppend(original: Array<T?>, newElement: T): Array<T?>? {
        val result = original.copyOf(original.size + 1)
        result[original.size] = newElement
        return castNonNullTypeArray<@NullableType T?>(result)
    }

    /**
     * Creates a new array containing the concatenation of two non-null type arrays.
     *
     * @param first The first array.
     * @param second The second array.
     * @return The concatenated result.
     */
    fun <T> nullSafeArrayConcatenation(first: Array<T>, second: Array<T>): Array<T>? {
        val concatenation = first.copyOf(first.size + second.size)
        System.arraycopy( /* src= */
                second,  /* srcPos= */
                0,  /* dest= */
                concatenation,  /* destPos= */
                first.size,  /* length= */
                second.size)
        return concatenation
    }

    /**
     * Copies the contents of `list` into `array`.
     *
     *
     * `list.size()` must be the same as `array.length` to ensure the contents can be
     * copied into `array` without leaving any nulls at the end.
     *
     * @param list The list to copy items from.
     * @param array The array to copy items to.
     */
    @SuppressLint("NewApi")
    fun <T> nullSafeListToArray(list: List<T>, array: Array<T>) {
        checkState(list.size == array.size)
        list.toArray<T>(array)
    }

    /**
     * Creates a [Handler] on the current [Looper] thread.
     *
     * @throws IllegalStateException If the current thread doesn't have a [Looper].
     */
    fun createHandlerForCurrentLooper(): Handler? {
        return createHandlerForCurrentLooper( /* callback= */null)
    }

    /**
     * Creates a [Handler] with the specified [Handler.Callback] on the current [ ] thread.
     *
     *
     * The method accepts partially initialized objects as callback under the assumption that the
     * Handler won't be used to send messages until the callback is fully initialized.
     *
     * @param callback A [Handler.Callback]. May be a partially initialized class, or null if no
     * callback is required.
     * @return A [Handler] with the specified callback on the current [Looper] thread.
     * @throws IllegalStateException If the current thread doesn't have a [Looper].
     */
    fun createHandlerForCurrentLooper(callback: @UnknownInitialization Handler.Callback?): Handler? {
        return createHandler(checkStateNotNull(Looper.myLooper()), callback)
    }

    /**
     * Creates a [Handler] on the current [Looper] thread.
     *
     *
     * If the current thread doesn't have a [Looper], the application's main thread [ ] is used.
     */
    fun createHandlerForCurrentOrMainLooper(): Handler? {
        return createHandlerForCurrentOrMainLooper( /* callback= */null)
    }

    /**
     * Creates a [Handler] with the specified [Handler.Callback] on the current [ ] thread.
     *
     *
     * The method accepts partially initialized objects as callback under the assumption that the
     * Handler won't be used to send messages until the callback is fully initialized.
     *
     *
     * If the current thread doesn't have a [Looper], the application's main thread [ ] is used.
     *
     * @param callback A [Handler.Callback]. May be a partially initialized class, or null if no
     * callback is required.
     * @return A [Handler] with the specified callback on the current [Looper] thread.
     */
    fun createHandlerForCurrentOrMainLooper(callback: @UnknownInitialization Handler.Callback?): Handler? {
        return createHandler(getCurrentOrMainLooper(), callback)
    }

    /**
     * Creates a [Handler] with the specified [Handler.Callback] on the specified [ ] thread.
     *
     *
     * The method accepts partially initialized objects as callback under the assumption that the
     * Handler won't be used to send messages until the callback is fully initialized.
     *
     * @param looper A [Looper] to run the callback on.
     * @param callback A [Handler.Callback]. May be a partially initialized class, or null if no
     * callback is required.
     * @return A [Handler] with the specified callback on the current [Looper] thread.
     */
    fun createHandler(looper: Looper?, callback: @UnknownInitialization Handler.Callback?): Handler? {
        return Handler((looper)!!, callback)
    }

    /**
     * Posts the [Runnable] if the calling thread differs with the [Looper] of the [ ]. Otherwise, runs the [Runnable] directly.
     *
     * @param handler The handler to which the [Runnable] will be posted.
     * @param runnable The runnable to either post or run.
     * @return `true` if the [Runnable] was successfully posted to the [Handler] or
     * run. `false` otherwise.
     */
    fun postOrRun(handler: Handler, runnable: Runnable): Boolean {
        val looper = handler.looper
        if (!looper.thread.isAlive) {
            return false
        }
        return if (handler.looper == Looper.myLooper()) {
            runnable.run()
            true
        } else {
            handler.post(runnable)
        }
    }

    /**
     * Posts the [Runnable] if the calling thread differs with the [Looper] of the [ ]. Otherwise, runs the [Runnable] directly. Also returns a [ ] for when the [Runnable] has run.
     *
     * @param handler The handler to which the [Runnable] will be posted.
     * @param runnable The runnable to either post or run.
     * @param successValue The value to set in the [ListenableFuture] once the runnable
     * completes.
     * @param <T> The type of `successValue`.
     * @return A [ListenableFuture] for when the [Runnable] has run.
    </T> */
    fun <T> postOrRunWithCompletion(handler: Handler, runnable: Runnable, successValue: T): ListenableFuture<T>? {
        val outputFuture = SettableFuture.create<T>()
        postOrRun(handler) {
            try {
                if (outputFuture.isCancelled()) {
                    return@postOrRun
                }
                runnable.run()
                outputFuture.set(successValue)
            } catch (e: Throwable) {
                outputFuture.setException(e)
            }
        }
        return outputFuture
    }

    /**
     * Asynchronously transforms the result of a [ListenableFuture].
     *
     *
     * The transformation function is called using a [ direct executor][MoreExecutors.directExecutor].
     *
     *
     * The returned Future attempts to keep its cancellation state in sync with that of the input
     * future and that of the future returned by the transform function. That is, if the returned
     * Future is cancelled, it will attempt to cancel the other two, and if either of the other two is
     * cancelled, the returned Future will also be cancelled. All forwarded cancellations will not
     * attempt to interrupt.
     *
     * @param future The input [ListenableFuture].
     * @param transformFunction The function transforming the result of the input future.
     * @param <T> The result type of the input future.
     * @param <U> The result type of the transformation function.
     * @return A [ListenableFuture] for the transformed result.
    </U></T> */
    fun <T, U> transformFutureAsync(future: ListenableFuture<U>, transformFunction: AsyncFunction<U, T>): ListenableFuture<T>? {
        // This is a simplified copy of Guava's Futures.transformAsync.
        val outputFuture = SettableFuture.create<T>()
        outputFuture.addListener({
            if (outputFuture.isCancelled()) {
                future.cancel( /* mayInterruptIfRunning= */false)
            }
        }, MoreExecutors.directExecutor())
        future.addListener({
            val inputFutureResult: U
            try {
                inputFutureResult = Futures.getDone(future)
            } catch (cancellationException: CancellationException) {
                outputFuture.cancel( /* mayInterruptIfRunning= */false)
                return@addListener
            } catch (exception: ExecutionException) {
                val cause: Throwable? = exception.cause
                outputFuture.setException(if (cause == null) exception else cause)
                return@addListener
            } catch (error: RuntimeException) {
                outputFuture.setException(error)
                return@addListener
            } catch (error: Error) {
                outputFuture.setException(error)
                return@addListener
            }
            try {
                outputFuture.setFuture(transformFunction.apply(inputFutureResult))
            } catch (exception: Throwable) {
                outputFuture.setException(exception)
            }
        }, MoreExecutors.directExecutor())
        return outputFuture
    }

    /**
     * Returns the [Looper] associated with the current thread, or the [Looper] of the
     * application's main thread if the current thread doesn't have a [Looper].
     */
    fun getCurrentOrMainLooper(): Looper? {
        val myLooper = Looper.myLooper()
        return myLooper ?: Looper.getMainLooper()
    }

    /**
     * Instantiates a new single threaded executor whose thread has the specified name.
     *
     * @param threadName The name of the thread.
     * @return The executor.
     */
    fun newSingleThreadExecutor(threadName: String?): ExecutorService? {
        return Executors.newSingleThreadExecutor { runnable: Runnable? -> Thread(runnable, threadName) }
    }

    /**
     * Closes a [Closeable], suppressing any [IOException] that may occur. Both [ ] and [InputStream] are `Closeable`.
     *
     * @param closeable The [Closeable] to close.
     */
    fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (e: IOException) {
            // Ignore.
        }
    }

    /**
     * Reads an integer from a [Parcel] and interprets it as a boolean, with 0 mapping to false
     * and all other values mapping to true.
     *
     * @param parcel The [Parcel] to read from.
     * @return The read value.
     */
    fun readBoolean(parcel: Parcel): Boolean {
        return parcel.readInt() != 0
    }

    /**
     * Writes a boolean to a [Parcel]. The boolean is written as an integer with value 1 (true)
     * or 0 (false).
     *
     * @param parcel The [Parcel] to write to.
     * @param value The value to write.
     */
    fun writeBoolean(parcel: Parcel, value: Boolean) {
        parcel.writeInt(if (value) 1 else 0)
    }

    /**
     * Returns the language tag for a [Locale].
     *
     *
     * For API levels  21, this tag is IETF BCP 47 compliant. Use [ ][.normalizeLanguageCode] to retrieve a normalized IETF BCP 47 language tag for all API
     * levels if needed.
     *
     * @param locale A [Locale].
     * @return The language tag.
     */
    @SuppressLint("NewApi")
    fun getLocaleLanguageTag(locale: Locale): String? {
        return if (SDK_INT >= 21) getLocaleLanguageTagV21(locale) else locale.toString()
    }

    /**
     * Returns a normalized IETF BCP 47 language tag for `language`.
     *
     * @param language A case-insensitive language code supported by [     ][Locale.forLanguageTag].
     * @return The all-lowercase normalized code, or null if the input was null, or `language.toLowerCase()` if the language could not be normalized.
     */
    fun normalizeLanguageCode(language: @PolyNull String?): @PolyNull String? {
        if (language == null) {
            return null
        }
        // Locale data (especially for API < 21) may produce tags with '_' instead of the
        // standard-conformant '-'.
        var normalizedTag = language.replace('_', '-')
        if (normalizedTag.isEmpty() || (normalizedTag == C.LANGUAGE_UNDETERMINED)) {
            // Tag isn't valid, keep using the original.
            normalizedTag = language
        }
        normalizedTag = Ascii.toLowerCase(normalizedTag)
        var mainLanguage = splitAtFirst(normalizedTag, "-")[0]
        if (languageTagReplacementMap == null) {
            languageTagReplacementMap = createIsoLanguageReplacementMap()
        }
        val replacedLanguage = languageTagReplacementMap!![mainLanguage]
        if (replacedLanguage != null) {
            normalizedTag = replacedLanguage + normalizedTag.substring( /* beginIndex= */mainLanguage.length)
            mainLanguage = replacedLanguage
        }
        if (("no" == mainLanguage) || ("i" == mainLanguage) || ("zh" == mainLanguage)) {
            normalizedTag = maybeReplaceLegacyLanguageTags(normalizedTag)
        }
        return normalizedTag
    }

    /**
     * Returns a new [String] constructed by decoding UTF-8 encoded bytes.
     *
     * @param bytes The UTF-8 encoded bytes to decode.
     * @return The string.
     */
    fun fromUtf8Bytes(bytes: ByteArray?): String? {
        return String((bytes)!!, Charsets.UTF_8)
    }

    /**
     * Returns a new [String] constructed by decoding UTF-8 encoded bytes in a subarray.
     *
     * @param bytes The UTF-8 encoded bytes to decode.
     * @param offset The index of the first byte to decode.
     * @param length The number of bytes to decode.
     * @return The string.
     */
    fun fromUtf8Bytes(bytes: ByteArray?, offset: Int, length: Int): String? {
        return String((bytes)!!, offset, length, Charsets.UTF_8)
    }

    /**
     * Returns a new byte array containing the code points of a [String] encoded using UTF-8.
     *
     * @param value The [String] whose bytes should be obtained.
     * @return The code points encoding using UTF-8.
     */
    fun getUtf8Bytes(value: String): ByteArray? {
        return value.toByteArray(Charsets.UTF_8)
    }

    /**
     * Splits a string using `value.split(regex, -1`). Note: this is is similar to [ ][String.split] but empty matches at the end of the string will not be omitted from the
     * returned array.
     *
     * @param value The string to split.
     * @param regex A delimiting regular expression.
     * @return The array of strings resulting from splitting the string.
     */
    fun split(value: String, regex: String): Array<String?> {
        return value.split(regex.toRegex()).toTypedArray()
    }

    /**
     * Splits the string at the first occurrence of the delimiter `regex`. If the delimiter does
     * not match, returns an array with one element which is the input string. If the delimiter does
     * match, returns an array with the portion of the string before the delimiter and the rest of the
     * string.
     *
     * @param value The string.
     * @param regex A delimiting regular expression.
     * @return The string split by the first occurrence of the delimiter.
     */
    fun splitAtFirst(value: String, regex: String): Array<String> {
        return value.split(regex.toRegex(), limit =  /* limit= */2).toTypedArray()
    }

    /**
     * Returns whether the given character is a carriage return ('\r') or a line feed ('\n').
     *
     * @param c The character.
     * @return Whether the given character is a linebreak.
     */
    fun isLinebreak(c: Int): Boolean {
        return c == '\n'.code || c == '\r'.code
    }

    /**
     * Formats a string using [Locale.US].
     *
     * @see String.format
     */
    fun formatInvariant(format: String?, vararg args: Any?): String? {
        return String.format(Locale.US, (format)!!, *args)
    }

    /**
     * Divides a `numerator` by a `denominator`, returning the ceiled result.
     *
     * @param numerator The numerator to divide.
     * @param denominator The denominator to divide by.
     * @return The ceiled result of the division.
     */
    fun ceilDivide(numerator: Int, denominator: Int): Int {
        return (numerator + denominator - 1) / denominator
    }

    /**
     * Divides a `numerator` by a `denominator`, returning the ceiled result.
     *
     * @param numerator The numerator to divide.
     * @param denominator The denominator to divide by.
     * @return The ceiled result of the division.
     */
    fun ceilDivide(numerator: Long, denominator: Long): Long {
        return (numerator + denominator - 1) / denominator
    }

    /**
     * Constrains a value to the specified bounds.
     *
     * @param value The value to constrain.
     * @param min The lower bound.
     * @param max The upper bound.
     * @return The constrained value `Math.max(min, Math.min(value, max))`.
     */
    fun constrainValue(value: Int, min: Int, max: Int): Int {
        return max(min, min(value, max))
    }

    /**
     * Constrains a value to the specified bounds.
     *
     * @param value The value to constrain.
     * @param min The lower bound.
     * @param max The upper bound.
     * @return The constrained value `Math.max(min, Math.min(value, max))`.
     */
    fun constrainValue(value: Long, min: Long, max: Long): Long {
        return max(min, min(value, max))
    }

    /**
     * Constrains a value to the specified bounds.
     *
     * @param value The value to constrain.
     * @param min The lower bound.
     * @param max The upper bound.
     * @return The constrained value `Math.max(min, Math.min(value, max))`.
     */
    fun constrainValue(value: Float, min: Float, max: Float): Float {
        return max(min, min(value, max))
    }

    /**
     * Returns the sum of two arguments, or a third argument if the result overflows.
     *
     * @param x The first value.
     * @param y The second value.
     * @param overflowResult The return value if `x + y` overflows.
     * @return `x + y`, or `overflowResult` if the result overflows.
     */
    fun addWithOverflowDefault(x: Long, y: Long, overflowResult: Long): Long {
        val result = x + y
        // See Hacker's Delight 2-13 (H. Warren Jr).
        return if (((x xor result) and (y xor result)) < 0) {
            overflowResult
        } else result
    }

    /**
     * Returns the difference between two arguments, or a third argument if the result overflows.
     *
     * @param x The first value.
     * @param y The second value.
     * @param overflowResult The return value if `x - y` overflows.
     * @return `x - y`, or `overflowResult` if the result overflows.
     */
    fun subtractWithOverflowDefault(x: Long, y: Long, overflowResult: Long): Long {
        val result = x - y
        // See Hacker's Delight 2-13 (H. Warren Jr).
        return if (((x xor y) and (x xor result)) < 0) {
            overflowResult
        } else result
    }

    /**
     * Returns the index of the first occurrence of `value` in `array`, or [ ][C.INDEX_UNSET] if `value` is not contained in `array`.
     *
     * @param array The array to search.
     * @param value The value to search for.
     * @return The index of the first occurrence of value in `array`, or [C.INDEX_UNSET]
     * if `value` is not contained in `array`.
     */
    fun linearSearch(array: IntArray, value: Int): Int {
        for (i in array.indices) {
            if (array[i] == value) {
                return i
            }
        }
        return C.INDEX_UNSET
    }

    /**
     * Returns the index of the first occurrence of `value` in `array`, or [ ][C.INDEX_UNSET] if `value` is not contained in `array`.
     *
     * @param array The array to search.
     * @param value The value to search for.
     * @return The index of the first occurrence of value in `array`, or [C.INDEX_UNSET]
     * if `value` is not contained in `array`.
     */
    fun linearSearch(array: kotlin.LongArray, value: Long): Int {
        for (i in array.indices) {
            if (array[i] == value) {
                return i
            }
        }
        return C.INDEX_UNSET
    }

    /**
     * Returns the index of the largest element in `array` that is less than (or optionally
     * equal to) a specified `value`.
     *
     *
     * The search is performed using a binary search algorithm, so the array must be sorted. If the
     * array contains multiple elements equal to `value` and `inclusive` is true, the
     * index of the first one will be returned.
     *
     * @param array The array to search.
     * @param value The value being searched for.
     * @param inclusive If the value is present in the array, whether to return the corresponding
     * index. If false then the returned index corresponds to the largest element strictly less
     * than the value.
     * @param stayInBounds If true, then 0 will be returned in the case that the value is smaller than
     * the smallest element in the array. If false then -1 will be returned.
     * @return The index of the largest element in `array` that is less than (or optionally
     * equal to) `value`.
     */
    fun binarySearchFloor(array: IntArray, value: Int, inclusive: Boolean, stayInBounds: Boolean): Int {
        var index = Arrays.binarySearch(array, value)
        if (index < 0) {
            index = -(index + 2)
        } else {
            while (--index >= 0 && array[index] == value) {
            }
            if (inclusive) {
                index++
            }
        }
        return if (stayInBounds) max(0, index) else index
    }

    /**
     * Returns the index of the largest element in `array` that is less than (or optionally
     * equal to) a specified `value`.
     *
     *
     * The search is performed using a binary search algorithm, so the array must be sorted. If the
     * array contains multiple elements equal to `value` and `inclusive` is true, the
     * index of the first one will be returned.
     *
     * @param array The array to search.
     * @param value The value being searched for.
     * @param inclusive If the value is present in the array, whether to return the corresponding
     * index. If false then the returned index corresponds to the largest element strictly less
     * than the value.
     * @param stayInBounds If true, then 0 will be returned in the case that the value is smaller than
     * the smallest element in the array. If false then -1 will be returned.
     * @return The index of the largest element in `array` that is less than (or optionally
     * equal to) `value`.
     */
    fun binarySearchFloor(array: kotlin.LongArray, value: Long, inclusive: Boolean, stayInBounds: Boolean): Int {
        var index = Arrays.binarySearch(array, value)
        if (index < 0) {
            index = -(index + 2)
        } else {
            while (--index >= 0 && array[index] == value) {
            }
            if (inclusive) {
                index++
            }
        }
        return if (stayInBounds) max(0, index) else index
    }

    /**
     * Returns the index of the largest element in `list` that is less than (or optionally equal
     * to) a specified `value`.
     *
     *
     * The search is performed using a binary search algorithm, so the list must be sorted. If the
     * list contains multiple elements equal to `value` and `inclusive` is true, the index
     * of the first one will be returned.
     *
     * @param <T> The type of values being searched.
     * @param list The list to search.
     * @param value The value being searched for.
     * @param inclusive If the value is present in the list, whether to return the corresponding
     * index. If false then the returned index corresponds to the largest element strictly less
     * than the value.
     * @param stayInBounds If true, then 0 will be returned in the case that the value is smaller than
     * the smallest element in the list. If false then -1 will be returned.
     * @return The index of the largest element in `list` that is less than (or optionally equal
     * to) `value`.
    </T> */
    fun <T : Comparable<T>?> binarySearchFloor(list: List<Comparable<T>>, value: T, inclusive: Boolean, stayInBounds: Boolean): Int {
        var index = Collections.binarySearch(list, value)
        if (index < 0) {
            index = -(index + 2)
        } else {
            while (--index >= 0 && list[index].compareTo(value) == 0) {
            }
            if (inclusive) {
                index++
            }
        }
        return if (stayInBounds) max(0, index) else index
    }

    /**
     * Returns the index of the largest element in `longArray` that is less than (or optionally
     * equal to) a specified `value`.
     *
     *
     * The search is performed using a binary search algorithm, so the array must be sorted. If the
     * array contains multiple elements equal to `value` and `inclusive` is true, the
     * index of the first one will be returned.
     *
     * @param longArray The array to search.
     * @param value The value being searched for.
     * @param inclusive If the value is present in the array, whether to return the corresponding
     * index. If false then the returned index corresponds to the largest element strictly less
     * than the value.
     * @param stayInBounds If true, then 0 will be returned in the case that the value is smaller than
     * the smallest element in the array. If false then -1 will be returned.
     * @return The index of the largest element in `array` that is less than (or optionally
     * equal to) `value`.
     */
    fun binarySearchFloor(longArray: LongArray, value: Long, inclusive: Boolean, stayInBounds: Boolean): Int {
        var lowIndex = 0
        var highIndex = longArray.size() - 1
        while (lowIndex <= highIndex) {
            val midIndex = (lowIndex + highIndex) ushr 1
            if (longArray[midIndex] < value) {
                lowIndex = midIndex + 1
            } else {
                highIndex = midIndex - 1
            }
        }
        if (inclusive && (highIndex + 1 < longArray.size()) && (longArray[highIndex + 1] == value)) {
            highIndex++
        } else if (stayInBounds && highIndex == -1) {
            highIndex = 0
        }
        return highIndex
    }

    /**
     * Returns the index of the smallest element in `array` that is greater than (or optionally
     * equal to) a specified `value`.
     *
     *
     * The search is performed using a binary search algorithm, so the array must be sorted. If the
     * array contains multiple elements equal to `value` and `inclusive` is true, the
     * index of the last one will be returned.
     *
     * @param array The array to search.
     * @param value The value being searched for.
     * @param inclusive If the value is present in the array, whether to return the corresponding
     * index. If false then the returned index corresponds to the smallest element strictly
     * greater than the value.
     * @param stayInBounds If true, then `(a.length - 1)` will be returned in the case that the
     * value is greater than the largest element in the array. If false then `a.length` will
     * be returned.
     * @return The index of the smallest element in `array` that is greater than (or optionally
     * equal to) `value`.
     */
    fun binarySearchCeil(array: IntArray, value: Int, inclusive: Boolean, stayInBounds: Boolean): Int {
        var index = Arrays.binarySearch(array, value)
        if (index < 0) {
            index = index.inv()
        } else {
            while (++index < array.size && array[index] == value) {
            }
            if (inclusive) {
                index--
            }
        }
        return if (stayInBounds) min(array.size - 1, index) else index
    }

    /**
     * Returns the index of the smallest element in `array` that is greater than (or optionally
     * equal to) a specified `value`.
     *
     *
     * The search is performed using a binary search algorithm, so the array must be sorted. If the
     * array contains multiple elements equal to `value` and `inclusive` is true, the
     * index of the last one will be returned.
     *
     * @param array The array to search.
     * @param value The value being searched for.
     * @param inclusive If the value is present in the array, whether to return the corresponding
     * index. If false then the returned index corresponds to the smallest element strictly
     * greater than the value.
     * @param stayInBounds If true, then `(a.length - 1)` will be returned in the case that the
     * value is greater than the largest element in the array. If false then `a.length` will
     * be returned.
     * @return The index of the smallest element in `array` that is greater than (or optionally
     * equal to) `value`.
     */
    fun binarySearchCeil(array: kotlin.LongArray, value: Long, inclusive: Boolean, stayInBounds: Boolean): Int {
        var index = Arrays.binarySearch(array, value)
        if (index < 0) {
            index = index.inv()
        } else {
            while (++index < array.size && array[index] == value) {
            }
            if (inclusive) {
                index--
            }
        }
        return if (stayInBounds) min(array.size - 1, index) else index
    }

    /**
     * Returns the index of the smallest element in `list` that is greater than (or optionally
     * equal to) a specified value.
     *
     *
     * The search is performed using a binary search algorithm, so the list must be sorted. If the
     * list contains multiple elements equal to `value` and `inclusive` is true, the index
     * of the last one will be returned.
     *
     * @param <T> The type of values being searched.
     * @param list The list to search.
     * @param value The value being searched for.
     * @param inclusive If the value is present in the list, whether to return the corresponding
     * index. If false then the returned index corresponds to the smallest element strictly
     * greater than the value.
     * @param stayInBounds If true, then `(list.size() - 1)` will be returned in the case that
     * the value is greater than the largest element in the list. If false then `list.size()` will be returned.
     * @return The index of the smallest element in `list` that is greater than (or optionally
     * equal to) `value`.
    </T> */
    fun <T : Comparable<T>?> binarySearchCeil(list: List<Comparable<T>>, value: T, inclusive: Boolean, stayInBounds: Boolean): Int {
        var index = Collections.binarySearch(list, value)
        if (index < 0) {
            index = index.inv()
        } else {
            val listSize = list.size
            while (++index < listSize && list[index].compareTo(value) == 0) {
            }
            if (inclusive) {
                index--
            }
        }
        return if (stayInBounds) min(list.size - 1, index) else index
    }

    /**
     * Compares two long values and returns the same value as `Long.compare(long, long)`.
     *
     * @param left The left operand.
     * @param right The right operand.
     * @return 0, if left == right, a negative value if left &lt; right, or a positive value if left
     * &gt; right.
     */
    fun compareLong(left: Long, right: Long): Int {
        return if (left < right) -1 else if (left == right) 0 else 1
    }

    /**
     * Returns the minimum value in the given [SparseLongArray].
     *
     * @param sparseLongArray The [SparseLongArray].
     * @return The minimum value.
     * @throws NoSuchElementException If the array is empty.
     */
    @RequiresApi(18)
    fun minValue(sparseLongArray: SparseLongArray): Long {
        if (sparseLongArray.size() == 0) {
            throw NoSuchElementException()
        }
        var min = Long.MAX_VALUE
        for (i in 0 until sparseLongArray.size()) {
            min = min(min, sparseLongArray.valueAt(i))
        }
        return min
    }

    /**
     * Returns the maximum value in the given [SparseLongArray].
     *
     * @param sparseLongArray The [SparseLongArray].
     * @return The maximum value.
     * @throws NoSuchElementException If the array is empty.
     */
    @RequiresApi(18)
    fun maxValue(sparseLongArray: SparseLongArray): Long {
        if (sparseLongArray.size() == 0) {
            throw NoSuchElementException()
        }
        var max = Long.MIN_VALUE
        for (i in 0 until sparseLongArray.size()) {
            max = Math.max(max, sparseLongArray.valueAt(i))
        }
        return max
    }

    /**
     * Converts a time in microseconds to the corresponding time in milliseconds, preserving [ ][C.TIME_UNSET] and [C.TIME_END_OF_SOURCE] values.
     *
     * @param timeUs The time in microseconds.
     * @return The corresponding time in milliseconds.
     */
    fun usToMs(timeUs: Long): Long {
        return if ((timeUs == C.TIME_UNSET || timeUs == C.TIME_END_OF_SOURCE)) timeUs else (timeUs / 1000)
    }

    /**
     * Converts a time in milliseconds to the corresponding time in microseconds, preserving [ ][C.TIME_UNSET] values and [C.TIME_END_OF_SOURCE] values.
     *
     * @param timeMs The time in milliseconds.
     * @return The corresponding time in microseconds.
     */
    fun msToUs(timeMs: Long): Long {
        return if ((timeMs == C.TIME_UNSET || timeMs == C.TIME_END_OF_SOURCE)) timeMs else (timeMs * 1000)
    }

    /**
     * Parses an xs:duration attribute value, returning the parsed duration in milliseconds.
     *
     * @param value The attribute value to decode.
     * @return The parsed duration in milliseconds.
     */
    fun parseXsDuration(value: String): Long {
        val matcher = XS_DURATION_PATTERN.matcher(value)
        if (matcher.matches()) {
            val negated = !TextUtils.isEmpty(matcher.group(1))
            // Durations containing years and months aren't completely defined. We assume there are
            // 30.4368 days in a month, and 365.242 days in a year.
            val years = matcher.group(3)
            var durationSeconds: Double = if ((years != null)) years.toDouble() * 31556908 else 0.0
            val months = matcher.group(5)
            durationSeconds += if ((months != null)) months.toDouble() * 2629739 else 0.0
            val days = matcher.group(7)
            durationSeconds += if ((days != null)) days.toDouble() * 86400 else 0.0
            val hours = matcher.group(10)
            durationSeconds += if ((hours != null)) hours.toDouble() * 3600 else 0.0
            val minutes = matcher.group(12)
            durationSeconds += if ((minutes != null)) minutes.toDouble() * 60 else 0.0
            val seconds = matcher.group(14)
            durationSeconds += seconds?.toDouble() ?: 0.0
            val durationMillis = (durationSeconds * 1000).toLong()
            return if (negated) -durationMillis else durationMillis
        } else {
            return (value.toDouble() * 3600 * 1000).toLong()
        }
    }

    /**
     * Parses an xs:dateTime attribute value, returning the parsed timestamp in milliseconds since the
     * epoch.
     *
     * @param value The attribute value to decode.
     * @return The parsed timestamp in milliseconds since the epoch.
     * @throws ParserException if an error occurs parsing the dateTime attribute value.
     */
    // incompatible types in argument.
    // dereference of possibly-null reference matcher.group(9)
    @Throws(ParserException::class)
    fun parseXsDateTime(value: String): Long {
        val matcher = XS_DATE_TIME_PATTERN.matcher(value)
        if (!matcher.matches()) {
            throw ParserException.createForMalformedContainer("Invalid date/time format: $value",  /* cause= */null)
        }
        var timezoneShift: Int
        if (matcher.group(9) == null) {
            // No time zone specified.
            timezoneShift = 0
        } else if (matcher.group(9).equals("Z", ignoreCase = true)) {
            timezoneShift = 0
        } else {
            timezoneShift = ((matcher.group(12).toInt() * 60 + matcher.group(13).toInt()))
            if (("-" == matcher.group(11))) {
                timezoneShift *= -1
            }
        }
        val dateTime: Calendar = GregorianCalendar(TimeZone.getTimeZone("GMT"))
        dateTime.clear()
        // Note: The month value is 0-based, hence the -1 on group(2)
        dateTime[matcher.group(1).toInt(), matcher.group(2).toInt() - 1, matcher.group(3).toInt(), matcher.group(4).toInt(), matcher.group(5).toInt()] = matcher.group(6).toInt()
        if (!TextUtils.isEmpty(matcher.group(8))) {
            val bd = BigDecimal("0." + matcher.group(8))
            // we care only for milliseconds, so movePointRight(3)
            dateTime[Calendar.MILLISECOND] = bd.movePointRight(3).toInt()
        }
        var time = dateTime.timeInMillis
        if (timezoneShift != 0) {
            time -= timezoneShift * 60000L
        }
        return time
    }

    /**
     * Scales a large timestamp.
     *
     *
     * Logically, scaling consists of a multiplication followed by a division. The actual
     * operations performed are designed to minimize the probability of overflow.
     *
     * @param timestamp The timestamp to scale.
     * @param multiplier The multiplier.
     * @param divisor The divisor.
     * @return The scaled timestamp.
     */
    fun scaleLargeTimestamp(timestamp: Long, multiplier: Long, divisor: Long): Long {
        return if (divisor >= multiplier && (divisor % multiplier) == 0L) {
            val divisionFactor = divisor / multiplier
            timestamp / divisionFactor
        } else if (divisor < multiplier && (multiplier % divisor) == 0L) {
            val multiplicationFactor = multiplier / divisor
            timestamp * multiplicationFactor
        } else {
            val multiplicationFactor = multiplier.toDouble() / divisor
            (timestamp * multiplicationFactor).toLong()
        }
    }

    /**
     * Applies [.scaleLargeTimestamp] to a list of unscaled timestamps.
     *
     * @param timestamps The timestamps to scale.
     * @param multiplier The multiplier.
     * @param divisor The divisor.
     * @return The scaled timestamps.
     */
    fun scaleLargeTimestamps(timestamps: List<Long>, multiplier: Long, divisor: Long): kotlin.LongArray? {
        val scaledTimestamps = kotlin.LongArray(timestamps.size)
        if (divisor >= multiplier && (divisor % multiplier) == 0L) {
            val divisionFactor = divisor / multiplier
            for (i in scaledTimestamps.indices) {
                scaledTimestamps[i] = timestamps[i] / divisionFactor
            }
        } else if (divisor < multiplier && (multiplier % divisor) == 0L) {
            val multiplicationFactor = multiplier / divisor
            for (i in scaledTimestamps.indices) {
                scaledTimestamps[i] = timestamps[i] * multiplicationFactor
            }
        } else {
            val multiplicationFactor = multiplier.toDouble() / divisor
            for (i in scaledTimestamps.indices) {
                scaledTimestamps[i] = (timestamps[i] * multiplicationFactor).toLong()
            }
        }
        return scaledTimestamps
    }

    /**
     * Applies [.scaleLargeTimestamp] to an array of unscaled timestamps.
     *
     * @param timestamps The timestamps to scale.
     * @param multiplier The multiplier.
     * @param divisor The divisor.
     */
    fun scaleLargeTimestampsInPlace(timestamps: kotlin.LongArray, multiplier: Long, divisor: Long) {
        if (divisor >= multiplier && (divisor % multiplier) == 0L) {
            val divisionFactor = divisor / multiplier
            for (i in timestamps.indices) {
                timestamps[i] /= divisionFactor
            }
        } else if (divisor < multiplier && (multiplier % divisor) == 0L) {
            val multiplicationFactor = multiplier / divisor
            for (i in timestamps.indices) {
                timestamps[i] *= multiplicationFactor
            }
        } else {
            val multiplicationFactor = multiplier.toDouble() / divisor
            for (i in timestamps.indices) {
                timestamps[i] = (timestamps[i] * multiplicationFactor).toLong()
            }
        }
    }

    /**
     * Returns the duration of media that will elapse in `playoutDuration`.
     *
     * @param playoutDuration The duration to scale.
     * @param speed The factor by which playback is sped up.
     * @return The scaled duration, in the same units as `playoutDuration`.
     */
    fun getMediaDurationForPlayoutDuration(playoutDuration: Long, speed: Float): Long {
        return if (speed == 1f) {
            playoutDuration
        } else (playoutDuration.toDouble() * speed).roundToLong()
    }

    /**
     * Returns the playout duration of `mediaDuration` of media.
     *
     * @param mediaDuration The duration to scale.
     * @return The scaled duration, in the same units as `mediaDuration`.
     */
    fun getPlayoutDurationForMediaDuration(mediaDuration: Long, speed: Float): Long {
        return if (speed == 1f) {
            mediaDuration
        } else Math.round(mediaDuration.toDouble() / speed)
    }

    /**
     * Returns the integer equal to the big-endian concatenation of the characters in `string`
     * as bytes. The string must be no more than four characters long.
     *
     * @param string A string no more than four characters long.
     */
    fun getIntegerCodeForString(string: String): Int {
        val length = string.length
        checkArgument(length <= 4)
        var result = 0
        for (i in 0 until length) {
            result = result shl 8
            result = result or string[i].code
        }
        return result
    }

    /**
     * Converts an integer to a long by unsigned conversion.
     *
     *
     * This method is equivalent to [Integer.toUnsignedLong] for API 26+.
     */
    fun toUnsignedLong(x: Int): Long {
        // x is implicitly casted to a long before the bit operation is executed but this does not
        // impact the method correctness.
        return x.toLong() and 0xFFFFFFFFL
    }

    /**
     * Returns the long that is composed of the bits of the 2 specified integers.
     *
     * @param mostSignificantBits The 32 most significant bits of the long to return.
     * @param leastSignificantBits The 32 least significant bits of the long to return.
     * @return a long where its 32 most significant bits are `mostSignificantBits` bits and its
     * 32 least significant bits are `leastSignificantBits`.
     */
    fun toLong(mostSignificantBits: Int, leastSignificantBits: Int): Long {
        return (toUnsignedLong(mostSignificantBits) shl 32) or toUnsignedLong(leastSignificantBits)
    }

    /**
     * Truncates a sequence of ASCII characters to a maximum length.
     *
     *
     * This preserves span styling in the [CharSequence]. If that's not important, use [ ][Ascii.truncate].
     *
     *
     * **Note:** This is not safe to use in general on Unicode text because it may separate
     * characters from combining characters or split up surrogate pairs.
     *
     * @param sequence The character sequence to truncate.
     * @param maxLength The max length to truncate to.
     * @return `sequence` directly if `sequence.length() <= maxLength`, otherwise `sequence.subsequence(0, maxLength`.
     */
    fun truncateAscii(sequence: CharSequence, maxLength: Int): CharSequence? {
        return if (sequence.length <= maxLength) sequence else sequence.subSequence(0, maxLength)
    }

    /**
     * Returns a byte array containing values parsed from the hex string provided.
     *
     * @param hexString The hex string to convert to bytes.
     * @return A byte array containing values parsed from the hex string provided.
     */
    fun getBytesFromHexString(hexString: String): ByteArray? {
        val data = ByteArray(hexString.length / 2)
        for (i in data.indices) {
            val stringOffset = i * 2
            data[i] = (((((hexString[stringOffset].digitToIntOrNull(16)
                    ?: (-1 shl 4)) + (hexString[stringOffset + 1].digitToIntOrNull(16))!!)
                    ?: -1))).toByte()
        }
        return data
    }

    /**
     * Returns a string containing a lower-case hex representation of the bytes provided.
     *
     * @param bytes The byte data to convert to hex.
     * @return A String containing the hex representation of `bytes`.
     */
    fun toHexString(bytes: ByteArray): String? {
        val result = StringBuilder(bytes.size * 2)
        for (i in bytes.indices) {
            result.append(Character.forDigit((bytes[i].toInt() shr 4) and 0xF, 16)).append(Character.forDigit(bytes[i].toInt() and 0xF, 16))
        }
        return result.toString()
    }

    /**
     * Returns a string with comma delimited simple names of each object's class.
     *
     * @param objects The objects whose simple class names should be comma delimited and returned.
     * @return A string with comma delimited simple names of each object's class.
     */
    fun getCommaDelimitedSimpleClassNames(objects: Array<Any>): String? {
        val stringBuilder = StringBuilder()
        for (i in objects.indices) {
            stringBuilder.append(objects[i].javaClass.simpleName)
            if (i < objects.size - 1) {
                stringBuilder.append(", ")
            }
        }
        return stringBuilder.toString()
    }

    /**
     * Returns a user agent string based on the given application name and the library version.
     *
     * @param context A valid context of the calling application.
     * @param applicationName String that will be prefix'ed to the generated user agent.
     * @return A user agent string generated using the applicationName and the library version.
     */
    fun getUserAgent(context: Context, applicationName: String): String? {
        var versionName: String
        try {
            val packageName = context.packageName
            val info = context.packageManager.getPackageInfo(packageName, 0)
            versionName = info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            versionName = "?"
        }
        return (applicationName + "/" + versionName + " (Linux;Android " + Build.VERSION.RELEASE + ") " + ExoPlayerLibraryInfo.VERSION_SLASHY)
    }

    /** Returns the number of codec strings in `codecs` whose type matches `trackType`.  */
    fun getCodecCountOfType(codecs: String?, trackType: @TrackType Int): Int {
        val codecArray = splitCodecs(codecs)
        var count = 0
        for (codec: String? in codecArray) {
            if (trackType == getTrackTypeOfCodec(codec)) {
                count++
            }
        }
        return count
    }

    /**
     * Returns a copy of `codecs` without the codecs whose track type doesn't match `trackType`.
     *
     * @param codecs A codec sequence string, as defined in RFC 6381.
     * @param trackType The [track type][C.TrackType].
     * @return A copy of `codecs` without the codecs whose track type doesn't match `trackType`. If this ends up empty, or `codecs` is null, returns null.
     */
    fun getCodecsOfType(codecs: String?, trackType: @TrackType Int): String? {
        val codecArray = splitCodecs(codecs)
        if (codecArray.size == 0) {
            return null
        }
        val builder = StringBuilder()
        for (codec: String? in codecArray) {
            if (trackType == getTrackTypeOfCodec(codec)) {
                if (builder.length > 0) {
                    builder.append(",")
                }
                builder.append(codec)
            }
        }
        return if (builder.length > 0) builder.toString() else null
    }

    /**
     * Splits a codecs sequence string, as defined in RFC 6381, into individual codec strings.
     *
     * @param codecs A codec sequence string, as defined in RFC 6381.
     * @return The split codecs, or an array of length zero if the input was empty or null.
     */
    fun splitCodecs(codecs: String?): Array<String?> {
        return if (TextUtils.isEmpty(codecs)) {
            arrayOfNulls(0)
        } else split(codecs!!.trim { it <= ' ' }, "(\\s*,\\s*)")
    }

    /**
     * Gets a PCM [Format] with the specified parameters.
     *
     * @param pcmEncoding The [C.PcmEncoding].
     * @param channels The number of channels, or [Format.NO_VALUE] if unknown.
     * @param sampleRate The sample rate in Hz, or [Format.NO_VALUE] if unknown.
     * @return The PCM format.
     */
    fun getPcmFormat(@PcmEncoding pcmEncoding: Int, channels: Int, sampleRate: Int): Format? {
        return Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setChannelCount(channels).setSampleRate(sampleRate).setPcmEncoding(pcmEncoding).build()
    }

    /**
     * Converts a sample bit depth to a corresponding PCM encoding constant.
     *
     * @param bitDepth The bit depth. Supported values are 8, 16, 24 and 32.
     * @return The corresponding encoding. One of [C.ENCODING_PCM_8BIT], [     ][C.ENCODING_PCM_16BIT], [C.ENCODING_PCM_24BIT] and [C.ENCODING_PCM_32BIT]. If
     * the bit depth is unsupported then [C.ENCODING_INVALID] is returned.
     */
    @PcmEncoding
    fun getPcmEncoding(bitDepth: Int): Int {
        return when (bitDepth) {
            8 -> C.ENCODING_PCM_8BIT
            16 -> C.ENCODING_PCM_16BIT
            24 -> C.ENCODING_PCM_24BIT
            32 -> C.ENCODING_PCM_32BIT
            else -> C.ENCODING_INVALID
        }
    }

    /**
     * Returns whether `encoding` is one of the linear PCM encodings.
     *
     * @param encoding The encoding of the audio data.
     * @return Whether the encoding is one of the PCM encodings.
     */
    fun isEncodingLinearPcm(@C.Encoding encoding: Int): Boolean {
        return (encoding == C.ENCODING_PCM_8BIT) || (encoding == C.ENCODING_PCM_16BIT) || (encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN) || (encoding == C.ENCODING_PCM_24BIT) || (encoding == C.ENCODING_PCM_32BIT) || (encoding == C.ENCODING_PCM_FLOAT)
    }

    /**
     * Returns whether `encoding` is high resolution (&gt; 16-bit) PCM.
     *
     * @param encoding The encoding of the audio data.
     * @return Whether the encoding is high resolution PCM.
     */
    fun isEncodingHighResolutionPcm(@PcmEncoding encoding: Int): Boolean {
        return (encoding == C.ENCODING_PCM_24BIT) || (encoding == C.ENCODING_PCM_32BIT) || (encoding == C.ENCODING_PCM_FLOAT)
    }

    /**
     * Returns the audio track channel configuration for the given channel count, or [ ][AudioFormat.CHANNEL_INVALID] if output is not possible.
     *
     * @param channelCount The number of channels in the input audio.
     * @return The channel configuration or [AudioFormat.CHANNEL_INVALID] if output is not
     * possible.
     */
    @SuppressLint("InlinedApi") // Inlined AudioFormat constants.
    fun getAudioTrackChannelConfig(channelCount: Int): Int {
        when (channelCount) {
            1 -> return AudioFormat.CHANNEL_OUT_MONO
            2 -> return AudioFormat.CHANNEL_OUT_STEREO
            3 -> return AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_FRONT_CENTER
            4 -> return AudioFormat.CHANNEL_OUT_QUAD
            5 -> return AudioFormat.CHANNEL_OUT_QUAD or AudioFormat.CHANNEL_OUT_FRONT_CENTER
            6 -> return AudioFormat.CHANNEL_OUT_5POINT1
            7 -> return AudioFormat.CHANNEL_OUT_5POINT1 or AudioFormat.CHANNEL_OUT_BACK_CENTER
            8 -> return AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            12 -> return AudioFormat.CHANNEL_OUT_7POINT1POINT4
            else -> return AudioFormat.CHANNEL_INVALID
        }
    }

    /**
     * Returns the frame size for audio with `channelCount` channels in the specified encoding.
     *
     * @param pcmEncoding The encoding of the audio data.
     * @param channelCount The channel count.
     * @return The size of one audio frame in bytes.
     */
    fun getPcmFrameSize(@PcmEncoding pcmEncoding: Int, channelCount: Int): Int {
        when (pcmEncoding) {
            C.ENCODING_PCM_8BIT -> return channelCount
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> return channelCount * 2
            C.ENCODING_PCM_24BIT -> return channelCount * 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> return channelCount * 4
            C.ENCODING_INVALID, Format.NO_VALUE -> throw IllegalArgumentException()
            else -> throw IllegalArgumentException()
        }
    }

    /** Returns the [C.AudioUsage] corresponding to the specified [C.StreamType].  */
    @AudioUsage
    fun getAudioUsageForStreamType(@C.StreamType streamType: Int): Int {
        when (streamType) {
            C.STREAM_TYPE_ALARM -> return C.USAGE_ALARM
            C.STREAM_TYPE_DTMF -> return C.USAGE_VOICE_COMMUNICATION_SIGNALLING
            C.STREAM_TYPE_NOTIFICATION -> return C.USAGE_NOTIFICATION
            C.STREAM_TYPE_RING -> return C.USAGE_NOTIFICATION_RINGTONE
            C.STREAM_TYPE_SYSTEM -> return C.USAGE_ASSISTANCE_SONIFICATION
            C.STREAM_TYPE_VOICE_CALL -> return C.USAGE_VOICE_COMMUNICATION
            C.STREAM_TYPE_MUSIC -> return C.USAGE_MEDIA
            else -> return C.USAGE_MEDIA
        }
    }

    /** Returns the [C.AudioContentType] corresponding to the specified [C.StreamType].  */
    @AudioContentType
    fun getAudioContentTypeForStreamType(@C.StreamType streamType: Int): Int {
        when (streamType) {
            C.STREAM_TYPE_ALARM, C.STREAM_TYPE_DTMF, C.STREAM_TYPE_NOTIFICATION, C.STREAM_TYPE_RING, C.STREAM_TYPE_SYSTEM -> return C.AUDIO_CONTENT_TYPE_SONIFICATION
            C.STREAM_TYPE_VOICE_CALL -> return C.AUDIO_CONTENT_TYPE_SPEECH
            C.STREAM_TYPE_MUSIC -> return C.AUDIO_CONTENT_TYPE_MUSIC
            else -> return C.AUDIO_CONTENT_TYPE_MUSIC
        }
    }

    /** Returns the [C.StreamType] corresponding to the specified [C.AudioUsage].  */
    @C.StreamType
    fun getStreamTypeForAudioUsage(@AudioUsage usage: Int): Int {
        when (usage) {
            C.USAGE_MEDIA, C.USAGE_GAME, C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> return C.STREAM_TYPE_MUSIC
            C.USAGE_ASSISTANCE_SONIFICATION -> return C.STREAM_TYPE_SYSTEM
            C.USAGE_VOICE_COMMUNICATION -> return C.STREAM_TYPE_VOICE_CALL
            C.USAGE_VOICE_COMMUNICATION_SIGNALLING -> return C.STREAM_TYPE_DTMF
            C.USAGE_ALARM -> return C.STREAM_TYPE_ALARM
            C.USAGE_NOTIFICATION_RINGTONE -> return C.STREAM_TYPE_RING
            C.USAGE_NOTIFICATION, C.USAGE_NOTIFICATION_COMMUNICATION_REQUEST, C.USAGE_NOTIFICATION_COMMUNICATION_INSTANT, C.USAGE_NOTIFICATION_COMMUNICATION_DELAYED, C.USAGE_NOTIFICATION_EVENT -> return C.STREAM_TYPE_NOTIFICATION
            C.USAGE_ASSISTANCE_ACCESSIBILITY, C.USAGE_ASSISTANT, C.USAGE_UNKNOWN -> return C.STREAM_TYPE_DEFAULT
            else -> return C.STREAM_TYPE_DEFAULT
        }
    }

    /**
     * Returns a newly generated audio session identifier, or [AudioManager.ERROR] if an error
     * occurred in which case audio playback may fail.
     *
     * @see AudioManager.generateAudioSessionId
     */
    @RequiresApi(21)
    fun generateAudioSessionIdV21(context: Context): Int {
        val audioManager: AudioManager? = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        return audioManager?.generateAudioSessionId() ?: AudioManager.ERROR
    }

    /**
     * Derives a DRM [UUID] from `drmScheme`.
     *
     * @param drmScheme A UUID string, or `"widevine"`, `"playready"` or `"clearkey"`.
     * @return The derived [UUID], or `null` if one could not be derived.
     */
    fun getDrmUuid(drmScheme: String?): UUID? {
        when (Ascii.toLowerCase(drmScheme)) {
            "widevine" -> return C.WIDEVINE_UUID
            "playready" -> return C.PLAYREADY_UUID
            "clearkey" -> return C.CLEARKEY_UUID
            else -> try {
                return UUID.fromString(drmScheme)
            } catch (e: RuntimeException) {
                return null
            }
        }
    }

    /**
     * Returns a [PlaybackException.ErrorCode] value that corresponds to the provided [ ] value. Returns [PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR] if
     * the provided error code isn't recognised.
     */
    @PlaybackException.ErrorCode
    fun getErrorCodeForMediaDrmErrorCode(mediaDrmErrorCode: Int): Int {
        when (mediaDrmErrorCode) {
            MediaDrm.ErrorCodes.ERROR_PROVISIONING_CONFIG, MediaDrm.ErrorCodes.ERROR_PROVISIONING_PARSE, MediaDrm.ErrorCodes.ERROR_PROVISIONING_REQUEST_REJECTED, MediaDrm.ErrorCodes.ERROR_PROVISIONING_CERTIFICATE, MediaDrm.ErrorCodes.ERROR_PROVISIONING_RETRY -> return PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED
            MediaDrm.ErrorCodes.ERROR_LICENSE_PARSE, MediaDrm.ErrorCodes.ERROR_LICENSE_RELEASE, MediaDrm.ErrorCodes.ERROR_LICENSE_REQUEST_REJECTED, MediaDrm.ErrorCodes.ERROR_LICENSE_RESTORE, MediaDrm.ErrorCodes.ERROR_LICENSE_STATE, MediaDrm.ErrorCodes.ERROR_CERTIFICATE_MALFORMED -> return PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED
            MediaDrm.ErrorCodes.ERROR_LICENSE_POLICY, MediaDrm.ErrorCodes.ERROR_INSUFFICIENT_OUTPUT_PROTECTION, MediaDrm.ErrorCodes.ERROR_INSUFFICIENT_SECURITY, MediaDrm.ErrorCodes.ERROR_KEY_EXPIRED, MediaDrm.ErrorCodes.ERROR_KEY_NOT_LOADED -> return PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION
            MediaDrm.ErrorCodes.ERROR_INIT_DATA, MediaDrm.ErrorCodes.ERROR_FRAME_TOO_LARGE -> return PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR
            else -> return PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR
        }
    }


    @C.ContentType
    @Deprecated("Use {@link #inferContentTypeForExtension(String)} when {@code overrideExtension} is\n" + "        non-empty, and {@link #inferContentType(Uri)} otherwise.")
    fun inferContentType(uri: Uri, overrideExtension: String?): Int {
        return if (TextUtils.isEmpty(overrideExtension)) inferContentType(uri) else inferContentTypeForExtension(overrideExtension)
    }

    /**
     * Makes a best guess to infer the [ContentType] from a [Uri].
     *
     * @param uri The [Uri].
     * @return The content type.
     */
    @C.ContentType
    fun inferContentType(uri: Uri): Int {
        val scheme = uri.scheme
        if (scheme != null && Ascii.equalsIgnoreCase("rtsp", scheme)) {
            return C.CONTENT_TYPE_RTSP
        }
        val lastPathSegment = uri.lastPathSegment ?: return C.CONTENT_TYPE_OTHER
        val lastDotIndex = lastPathSegment.lastIndexOf('.')
        if (lastDotIndex >= 0) {
            @C.ContentType val contentType = inferContentTypeForExtension(lastPathSegment.substring(lastDotIndex + 1))
            if (contentType != C.CONTENT_TYPE_OTHER) {
                // If contentType is TYPE_SS that indicates the extension is .ism or .isml and shows the ISM
                // URI is missing the "/manifest" suffix, which contains the information used to
                // disambiguate between Smooth Streaming, HLS and DASH below - so we can just return TYPE_SS
                // here without further checks.
                return contentType
            }
        }
        val ismMatcher = ISM_PATH_PATTERN.matcher(checkNotNull(uri.path))
        if (ismMatcher.matches()) {
            val extensions = ismMatcher.group(2)
            if (extensions != null) {
                if (extensions.contains(ISM_DASH_FORMAT_EXTENSION)) {
                    return C.CONTENT_TYPE_DASH
                } else if (extensions.contains(ISM_HLS_FORMAT_EXTENSION)) {
                    return C.CONTENT_TYPE_HLS
                }
            }
            return C.CONTENT_TYPE_SS
        }
        return C.CONTENT_TYPE_OTHER
    }


    @C.ContentType
    @Deprecated("Use {@link Uri#parse(String)} and {@link #inferContentType(Uri)} for full file\n" + "        paths or {@link #inferContentTypeForExtension(String)} for extensions.")
    fun inferContentType(fileName: String): Int {
        return inferContentType(Uri.parse("file:///$fileName"))
    }

    /**
     * Makes a best guess to infer the [ContentType] from a file extension.
     *
     * @param fileExtension The extension of the file (excluding the '.').
     * @return The content type.
     */
    @C.ContentType
    fun inferContentTypeForExtension(fileExtension: String?): Int {
        var fileExtension = fileExtension
        fileExtension = Ascii.toLowerCase(fileExtension)
        when (fileExtension) {
            "mpd" -> return C.CONTENT_TYPE_DASH
            "m3u8" -> return C.CONTENT_TYPE_HLS
            "ism", "isml" -> return C.TYPE_SS
            else -> return C.CONTENT_TYPE_OTHER
        }
    }

    /**
     * Makes a best guess to infer the [ContentType] from a [Uri] and optional MIME type.
     *
     * @param uri The [Uri].
     * @param mimeType If MIME type, or `null`.
     * @return The content type.
     */
    @C.ContentType
    fun inferContentTypeForUriAndMimeType(uri: Uri, mimeType: String?): Int {
        if (mimeType == null) {
            return inferContentType(uri)
        }
        when (mimeType) {
            MimeTypes.APPLICATION_MPD -> return C.CONTENT_TYPE_DASH
            MimeTypes.APPLICATION_M3U8 -> return C.CONTENT_TYPE_HLS
            MimeTypes.APPLICATION_SS -> return C.CONTENT_TYPE_SS
            MimeTypes.APPLICATION_RTSP -> return C.CONTENT_TYPE_RTSP
            else -> return C.CONTENT_TYPE_OTHER
        }
    }

    /**
     * Returns the MIME type corresponding to the given adaptive [ContentType], or `null`
     * if the content type is not adaptive.
     */
    fun getAdaptiveMimeTypeForContentType(@C.ContentType contentType: Int): String? {
        when (contentType) {
            C.CONTENT_TYPE_DASH -> return MimeTypes.APPLICATION_MPD
            C.CONTENT_TYPE_HLS -> return MimeTypes.APPLICATION_M3U8
            C.CONTENT_TYPE_SS -> return MimeTypes.APPLICATION_SS
            C.CONTENT_TYPE_RTSP, C.CONTENT_TYPE_OTHER -> return null
            else -> return null
        }
    }

    /**
     * If the provided URI is an ISM Presentation URI, returns the URI with "Manifest" appended to its
     * path (i.e., the corresponding default manifest URI). Else returns the provided URI without
     * modification. See [MS-SSTR] v20180912, section 2.2.1.
     *
     * @param uri The original URI.
     * @return The fixed URI.
     */
    fun fixSmoothStreamingIsmManifestUri(uri: Uri): Uri? {
        val path = uri.path ?: return uri
        val ismMatcher = ISM_PATH_PATTERN.matcher(path)
        return if (ismMatcher.matches() && ismMatcher.group(1) == null) {
            // Add missing "Manifest" suffix.
            Uri.withAppendedPath(uri, "Manifest")
        } else uri
    }

    /**
     * Returns the specified millisecond time formatted as a string.
     *
     * @param builder The builder that `formatter` will write to.
     * @param formatter The formatter.
     * @param timeMs The time to format as a string, in milliseconds.
     * @return The time formatted as a string.
     */
    fun getStringForTime(builder: StringBuilder, formatter: Formatter, timeMs: Long): String? {
        var timeMs = timeMs
        if (timeMs == C.TIME_UNSET) {
            timeMs = 0
        }
        val prefix = if (timeMs < 0) "-" else ""
        timeMs = Math.abs(timeMs)
        val totalSeconds = (timeMs + 500) / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        builder.setLength(0)
        return if (hours > 0) formatter.format("%s%d:%02d:%02d", prefix, hours, minutes, seconds).toString() else formatter.format("%s%02d:%02d", prefix, minutes, seconds).toString()
    }

    /**
     * Escapes a string so that it's safe for use as a file or directory name on at least FAT32
     * filesystems. FAT32 is the most restrictive of all filesystems still commonly used today.
     *
     *
     * For simplicity, this only handles common characters known to be illegal on FAT32: &lt;,
     * &gt;, :, ", /, \, |, ?, and *. % is also escaped since it is used as the escape character.
     * Escaping is performed in a consistent way so that no collisions occur and [ ][.unescapeFileName] can be used to retrieve the original file name.
     *
     * @param fileName File name to be escaped.
     * @return An escaped file name which will be safe for use on at least FAT32 filesystems.
     */
    fun escapeFileName(fileName: String): String? {
        val length = fileName.length
        var charactersToEscapeCount = 0
        for (i in 0 until length) {
            if (shouldEscapeCharacter(fileName[i])) {
                charactersToEscapeCount++
            }
        }
        if (charactersToEscapeCount == 0) {
            return fileName
        }
        var i = 0
        val builder = StringBuilder(length + charactersToEscapeCount * 2)
        while (charactersToEscapeCount > 0) {
            val c = fileName[i++]
            if (shouldEscapeCharacter(c)) {
                builder.append('%').append(Integer.toHexString(c.code))
                charactersToEscapeCount--
            } else {
                builder.append(c)
            }
        }
        if (i < length) {
            builder.append(fileName, i, length)
        }
        return builder.toString()
    }

    private fun shouldEscapeCharacter(c: Char): Boolean {
        when (c) {
            '<', '>', ':', '"', '/', '\\', '|', '?', '*', '%' -> return true
            else -> return false
        }
    }

    /**
     * Unescapes an escaped file or directory name back to its original value.
     *
     *
     * See [.escapeFileName] for more information.
     *
     * @param fileName File name to be unescaped.
     * @return The original value of the file name before it was escaped, or null if the escaped
     * fileName seems invalid.
     */
    fun unescapeFileName(fileName: String): String? {
        val length = fileName.length
        var percentCharacterCount = 0
        for (i in 0 until length) {
            if (fileName[i] == '%') {
                percentCharacterCount++
            }
        }
        if (percentCharacterCount == 0) {
            return fileName
        }
        val expectedLength = length - percentCharacterCount * 2
        val builder = StringBuilder(expectedLength)
        val matcher = ESCAPED_CHARACTER_PATTERN.matcher(fileName)
        var startOfNotEscaped = 0
        while (percentCharacterCount > 0 && matcher.find()) {
            val unescapedCharacter = checkNotNull(matcher.group(1))!!.toInt(16).toChar()
            builder.append(fileName, startOfNotEscaped, matcher.start()).append(unescapedCharacter)
            startOfNotEscaped = matcher.end()
            percentCharacterCount--
        }
        if (startOfNotEscaped < length) {
            builder.append(fileName, startOfNotEscaped, length)
        }
        return if (builder.length != expectedLength) {
            null
        } else builder.toString()
    }

    /** Returns a data URI with the specified MIME type and data.  */
    fun getDataUriForString(mimeType: String, data: String): Uri? {
        return Uri.parse("data:" + mimeType + ";base64," + Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP))
    }

    /**
     * A hacky method that always throws `t` even if `t` is a checked exception, and is
     * not declared to be thrown.
     */
    fun sneakyThrow(t: Throwable) {
        sneakyThrowInternal<RuntimeException>(t)
    }

    @Throws(T::class)
    private fun <T : Throwable?> sneakyThrowInternal(t: Throwable) {
        throw t as T
    }

    /** Recursively deletes a directory and its content.  */
    fun recursiveDelete(fileOrDirectory: File) {
        val directoryFiles = fileOrDirectory.listFiles()
        if (directoryFiles != null) {
            for (child: File in directoryFiles) {
                recursiveDelete(child)
            }
        }
        fileOrDirectory.delete()
    }

    /** Creates an empty directory in the directory returned by [Context.getCacheDir].  */
    @Throws(IOException::class)
    fun createTempDirectory(context: Context, prefix: String?): File? {
        val tempFile = createTempFile(context, prefix)
        tempFile.delete() // Delete the temp file.
        tempFile.mkdir() // Create a directory with the same name.
        return tempFile
    }

    /** Creates a new empty file in the directory returned by [Context.getCacheDir].  */
    @Throws(IOException::class)
    fun createTempFile(context: Context, prefix: String?): File {
        return File.createTempFile(prefix, null, checkNotNull(context.cacheDir))
    }

    /**
     * Returns the result of updating a CRC-32 with the specified bytes in a "most significant bit
     * first" order.
     *
     * @param bytes Array containing the bytes to update the crc value with.
     * @param start The index to the first byte in the byte range to update the crc with.
     * @param end The index after the last byte in the byte range to update the crc with.
     * @param initialValue The initial value for the crc calculation.
     * @return The result of updating the initial value with the specified bytes.
     */
    fun crc32(bytes: ByteArray, start: Int, end: Int, initialValue: Int): Int {
        var initialValue = initialValue
        for (i in start until end) {
            initialValue = ((initialValue shl 8) xor CRC32_BYTES_MSBF[((initialValue ushr 24) xor (bytes[i].toInt() and 0xFF)) and 0xFF])
        }
        return initialValue
    }

    /**
     * Returns the result of updating a CRC-8 with the specified bytes in a "most significant bit
     * first" order.
     *
     * @param bytes Array containing the bytes to update the crc value with.
     * @param start The index to the first byte in the byte range to update the crc with.
     * @param end The index after the last byte in the byte range to update the crc with.
     * @param initialValue The initial value for the crc calculation.
     * @return The result of updating the initial value with the specified bytes.
     */
    fun crc8(bytes: ByteArray, start: Int, end: Int, initialValue: Int): Int {
        var initialValue = initialValue
        for (i in start until end) {
            initialValue = CRC8_BYTES_MSBF[initialValue xor (bytes[i].toInt() and 0xFF)]
        }
        return initialValue
    }

    /** Compresses `input` using gzip and returns the result in a newly allocated byte array.  */
    fun gzip(input: ByteArray?): ByteArray? {
        val output = ByteArrayOutputStream()
        try {
            GZIPOutputStream(output).use { os -> os.write(input) }
        } catch (e: IOException) {
            // A ByteArrayOutputStream wrapped in a GZipOutputStream should never throw IOException since
            // no I/O is happening.
            throw IllegalStateException(e)
        }
        return output.toByteArray()
    }

    /**
     * Absolute *get* method for reading an int value in [ByteOrder.BIG_ENDIAN] in a [ ]. Same as [ByteBuffer.getInt] except the buffer's order as returned by
     * [ByteBuffer.order] is ignored and [ByteOrder.BIG_ENDIAN] is used instead.
     *
     * @param buffer The buffer from which to read an int in big endian.
     * @param index The index from which the bytes will be read.
     * @return The int value at the given index with the buffer bytes ordered most significant to
     * least significant.
     */
    fun getBigEndianInt(buffer: ByteBuffer, index: Int): Int {
        val value = buffer.getInt(index)
        return if (buffer.order() == ByteOrder.BIG_ENDIAN) value else Integer.reverseBytes(value)
    }

    /**
     * Returns the upper-case ISO 3166-1 alpha-2 country code of the current registered operator's MCC
     * (Mobile Country Code), or the country code of the default Locale if not available.
     *
     * @param context A context to access the telephony service. If null, only the Locale can be used.
     * @return The upper-case ISO 3166-1 alpha-2 country code, or an empty String if unavailable.
     */
    fun getCountryCode(context: Context?): String? {
        if (context != null) {
            val telephonyManager: TelephonyManager? = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (telephonyManager != null) {
                val countryCode = telephonyManager.networkCountryIso
                if (!TextUtils.isEmpty(countryCode)) {
                    return Ascii.toUpperCase(countryCode)
                }
            }
        }
        return Ascii.toUpperCase(Locale.getDefault().country)
    }

    /**
     * Returns a non-empty array of normalized IETF BCP 47 language tags for the system languages
     * ordered by preference.
     */
    fun getSystemLanguageCodes(): Array<String?>? {
        val systemLocales = getSystemLocales()
        for (i in systemLocales.indices) {
            systemLocales[i] = normalizeLanguageCode(systemLocales[i])
        }
        return systemLocales
    }

    /** Returns the default [DISPLAY][Locale.Category.DISPLAY] [Locale].  */
    fun getDefaultDisplayLocale(): Locale? {
        return if (SDK_INT >= 24) Locale.getDefault(Locale.Category.DISPLAY) else Locale.getDefault()
    }

    /**
     * Uncompresses the data in `input`.
     *
     * @param input Wraps the compressed input data.
     * @param output Wraps an output buffer to be used to store the uncompressed data. If `output.data` isn't big enough to hold the uncompressed data, a new array is created. If
     * `true` is returned then the output's position will be set to 0 and its limit will be
     * set to the length of the uncompressed data.
     * @param inflater If not null, used to uncompressed the input. Otherwise a new [Inflater]
     * is created.
     * @return Whether the input is uncompressed successfully.
     */
    fun inflate(input: ParsableByteArray, output: ParsableByteArray, inflater: Inflater?): Boolean {
        var inflater = inflater
        if (input.bytesLeft() <= 0) {
            return false
        }
        if (output.capacity() < input.bytesLeft()) {
            output.ensureCapacity(2 * input.bytesLeft())
        }
        if (inflater == null) {
            inflater = Inflater()
        }
        inflater.setInput(input.getData(), input.getPosition(), input.bytesLeft())
        try {
            var outputSize = 0
            while (true) {
                outputSize += inflater.inflate(output.getData(), outputSize, output.capacity() - outputSize)
                if (inflater.finished()) {
                    output.setLimit(outputSize)
                    return true
                }
                if (inflater.needsDictionary() || inflater.needsInput()) {
                    return false
                }
                if (outputSize == output.capacity()) {
                    output.ensureCapacity(output.capacity() * 2)
                }
            }
        } catch (e: DataFormatException) {
            return false
        } finally {
            inflater.reset()
        }
    }

    /**
     * Returns whether the app is running on a TV device.
     *
     * @param context Any context.
     * @return Whether the app is running on a TV device.
     */
    fun isTv(context: Context): Boolean {
        // See https://developer.android.com/training/tv/start/hardware.html#runtime-check.
        val uiModeManager: UiModeManager? = context.applicationContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return (uiModeManager != null && uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION)
    }

    /**
     * Returns whether the app is running on an automotive device.
     *
     * @param context Any context.
     * @return Whether the app is running on an automotive device.
     */
    fun isAutomotive(context: Context): Boolean {
        return (SDK_INT >= 23 && context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
    }

    /**
     * Gets the size of the current mode of the default display, in pixels.
     *
     *
     * Note that due to application UI scaling, the number of pixels made available to applications
     * (as reported by [Display.getSize] may differ from the mode's actual resolution (as
     * reported by this function). For example, applications running on a display configured with a 4K
     * mode may have their UI laid out and rendered in 1080p and then scaled up. Applications can take
     * advantage of the full mode resolution through a [SurfaceView] using full size buffers.
     *
     * @param context Any context.
     * @return The size of the current mode, in pixels.
     */
    fun getCurrentDisplayModeSize(context: Context): Point? {
        var defaultDisplay: Display? = null
        if (SDK_INT >= 17) {
            val displayManager: DisplayManager? = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            // We don't expect displayManager to ever be null, so this check is just precautionary.
            // Consider removing it when the library minSdkVersion is increased to 17 or higher.
            if (displayManager != null) {
                defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            }
        }
        if (defaultDisplay == null) {
            val windowManager = checkNotNull(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            defaultDisplay = windowManager!!.defaultDisplay
        }
        return getCurrentDisplayModeSize(context, defaultDisplay)
    }

    /**
     * Gets the size of the current mode of the specified display, in pixels.
     *
     *
     * Note that due to application UI scaling, the number of pixels made available to applications
     * (as reported by [Display.getSize] may differ from the mode's actual resolution (as
     * reported by this function). For example, applications running on a display configured with a 4K
     * mode may have their UI laid out and rendered in 1080p and then scaled up. Applications can take
     * advantage of the full mode resolution through a [SurfaceView] using full size buffers.
     *
     * @param context Any context.
     * @param display The display whose size is to be returned.
     * @return The size of the current mode, in pixels.
     */
    fun getCurrentDisplayModeSize(context: Context, display: Display?): Point? {
        if (display!!.displayId == Display.DEFAULT_DISPLAY && isTv(context)) {
            // On Android TVs it's common for the UI to be driven at a lower resolution than the physical
            // resolution of the display (e.g., driving the UI at 1080p when the display is 4K).
            // SurfaceView outputs are still able to use the full physical resolution on such devices.
            //
            // Prior to API level 26, the Display object did not provide a way to obtain the true physical
            // resolution of the display. From API level 26, Display.getMode().getPhysical[Width|Height]
            // is expected to return the display's true physical resolution, but we still see devices
            // setting their hardware compositor output size incorrectly, which makes this unreliable.
            // Hence for TV devices, we try and read the display's true physical resolution from system
            // properties.
            //
            // From API level 28, Treble may prevent the system from writing sys.display-size, so we check
            // vendor.display-size instead.
            val displaySize = if (SDK_INT < 28) getSystemProperty("sys.display-size") else getSystemProperty("vendor.display-size")
            // If we managed to read the display size, attempt to parse it.
            if (!TextUtils.isEmpty(displaySize)) {
                try {
                    val displaySizeParts = split(displaySize!!.trim { it <= ' ' }, "x")
                    if (displaySizeParts.size == 2) {
                        val width = displaySizeParts[0]!!.toInt()
                        val height = displaySizeParts[1]!!.toInt()
                        if (width > 0 && height > 0) {
                            return Point(width, height)
                        }
                    }
                } catch (e: NumberFormatException) {
                    // Do nothing.
                }
                e(TAG, "Invalid display size: $displaySize")
            }

            // Sony Android TVs advertise support for 4k output via a system feature.
            if ((("Sony" == MANUFACTURER) && MODEL.startsWith("BRAVIA") && context.packageManager.hasSystemFeature("com.sony.dtv.hardware.panel.qfhd"))) {
                return Point(3840, 2160)
            }
        }
        val displaySize = Point()
        if (SDK_INT >= 23) {
            getDisplaySizeV23(display, displaySize)
        } else if (SDK_INT >= 17) {
            getDisplaySizeV17(display, displaySize)
        } else {
            getDisplaySizeV16(display, displaySize)
        }
        return displaySize
    }

    /**
     * Returns a string representation of a [C.TrackType].
     *
     * @param trackType A [C.TrackType] constant,
     * @return A string representation of this constant.
     */
    fun getTrackTypeString(trackType: @TrackType Int): String? {
        when (trackType) {
            C.TRACK_TYPE_DEFAULT -> return "default"
            C.TRACK_TYPE_AUDIO -> return "audio"
            C.TRACK_TYPE_VIDEO -> return "video"
            C.TRACK_TYPE_TEXT -> return "text"
            C.TRACK_TYPE_IMAGE -> return "image"
            C.TRACK_TYPE_METADATA -> return "metadata"
            C.TRACK_TYPE_CAMERA_MOTION -> return "camera motion"
            C.TRACK_TYPE_NONE -> return "none"
            C.TRACK_TYPE_UNKNOWN -> return "unknown"
            else -> return if (trackType >= C.TRACK_TYPE_CUSTOM_BASE) "custom ($trackType)" else "?"
        }
    }

    /**
     * Returns the current time in milliseconds since the epoch.
     *
     * @param elapsedRealtimeEpochOffsetMs The offset between [SystemClock.elapsedRealtime]
     * and the time since the Unix epoch, or [C.TIME_UNSET] if unknown.
     * @return The Unix time in milliseconds since the epoch.
     */
    fun getNowUnixTimeMs(elapsedRealtimeEpochOffsetMs: Long): Long {
        return if (elapsedRealtimeEpochOffsetMs == C.TIME_UNSET) System.currentTimeMillis() else SystemClock.elapsedRealtime() + elapsedRealtimeEpochOffsetMs
    }

    /**
     * Moves the elements starting at `fromIndex` to `newFromIndex`.
     *
     * @param items The list of which to move elements.
     * @param fromIndex The index at which the items to move start.
     * @param toIndex The index up to which elements should be moved (exclusive).
     * @param newFromIndex The new from index.
     */
    // See go/lsc-extends-object
    fun <T : Any?> moveItems(items: MutableList<T>, fromIndex: Int, toIndex: Int, newFromIndex: Int) {
        val removedItems = java.util.ArrayDeque<T>()
        val removedItemsLength = toIndex - fromIndex
        for (i in removedItemsLength - 1 downTo 0) {
            removedItems.addFirst(items.removeAt(fromIndex + i))
        }
        items.addAll(Math.min(newFromIndex, items.size), removedItems)
    }

    /** Returns whether the table exists in the database.  */
    fun tableExists(database: SQLiteDatabase?, tableName: String): Boolean {
        val count = DatabaseUtils.queryNumEntries(database, "sqlite_master", "tbl_name = ?", arrayOf(tableName))
        return count > 0
    }

    /**
     * Attempts to parse an error code from a diagnostic string found in framework media exceptions.
     *
     *
     * For example: android.media.MediaCodec.error_1 or android.media.MediaDrm.error_neg_2.
     *
     * @param diagnosticsInfo A string from which to parse the error code.
     * @return The parser error code, or 0 if an error code could not be parsed.
     */
    fun getErrorCodeFromPlatformDiagnosticsInfo(diagnosticsInfo: String?): Int {
        // TODO (internal b/192337376): Change 0 for ERROR_UNKNOWN once available.
        if (diagnosticsInfo == null) {
            return 0
        }
        val strings = split(diagnosticsInfo, "_")
        val length = strings.size
        if (length < 2) {
            return 0
        }
        val digitsSection = strings[length - 1]
        val isNegative = length >= 3 && ("neg" == strings[length - 2])
        try {
            val errorCode = checkNotNull(digitsSection)!!.toInt()
            return if (isNegative) -errorCode else errorCode
        } catch (e: NumberFormatException) {
            return 0
        }
    }

    /**
     * Returns string representation of a [C.FormatSupport] flag.
     *
     * @param formatSupport A [C.FormatSupport] flag.
     * @return A string representation of the flag.
     */
    fun getFormatSupportString(@C.FormatSupport formatSupport: Int): String? {
        when (formatSupport) {
            C.FORMAT_HANDLED -> return "YES"
            C.FORMAT_EXCEEDS_CAPABILITIES -> return "NO_EXCEEDS_CAPABILITIES"
            C.FORMAT_UNSUPPORTED_DRM -> return "NO_UNSUPPORTED_DRM"
            C.FORMAT_UNSUPPORTED_SUBTYPE -> return "NO_UNSUPPORTED_TYPE"
            C.FORMAT_UNSUPPORTED_TYPE -> return "NO"
            else -> throw IllegalStateException()
        }
    }

    /**
     * Returns the [Commands] available in the [Player].
     *
     * @param player The [Player].
     * @param permanentAvailableCommands The commands permanently available in the player.
     * @return The available [Commands].
     */
    fun getAvailableCommands(player: Player, permanentAvailableCommands: Player.Commands?): Player.Commands? {
        val isPlayingAd = player.isPlayingAd()
        val isCurrentMediaItemSeekable = player.isCurrentMediaItemSeekable()
        val hasPreviousMediaItem = player.hasPreviousMediaItem()
        val hasNextMediaItem = player.hasNextMediaItem()
        val isCurrentMediaItemLive = player.isCurrentMediaItemLive()
        val isCurrentMediaItemDynamic = player.isCurrentMediaItemDynamic()
        val isTimelineEmpty = player.getCurrentTimeline()!!.isEmpty
        return Player.Commands.Builder().addAll((permanentAvailableCommands)!!).addIf(Player.COMMAND_SEEK_TO_DEFAULT_POSITION, !isPlayingAd).addIf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, isCurrentMediaItemSeekable && !isPlayingAd).addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPreviousMediaItem && !isPlayingAd).addIf(Player.COMMAND_SEEK_TO_PREVIOUS, (!isTimelineEmpty && (hasPreviousMediaItem || !isCurrentMediaItemLive || isCurrentMediaItemSeekable) && !isPlayingAd)).addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNextMediaItem && !isPlayingAd).addIf(Player.COMMAND_SEEK_TO_NEXT, (!isTimelineEmpty && (hasNextMediaItem || (isCurrentMediaItemLive && isCurrentMediaItemDynamic)) && !isPlayingAd)).addIf(Player.COMMAND_SEEK_TO_MEDIA_ITEM, !isPlayingAd).addIf(Player.COMMAND_SEEK_BACK, isCurrentMediaItemSeekable && !isPlayingAd).addIf(Player.COMMAND_SEEK_FORWARD, isCurrentMediaItemSeekable && !isPlayingAd).build()
    }

    /**
     * Returns the sum of all summands of the given array.
     *
     * @param summands The summands to calculate the sum from.
     * @return The sum of all summands.
     */
    fun sum(vararg summands: Long): Long {
        var sum: Long = 0
        for (summand: Long in summands) {
            sum += summand
        }
        return sum
    }

    private fun getSystemProperty(name: String): String? {
        try {
            @SuppressLint("PrivateApi") val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java)
            return getMethod.invoke(systemProperties, name) as String
        } catch (e: Exception) {
            e(TAG, "Failed to read system property $name", e)
            return null
        }
    }

    @RequiresApi(23)
    private fun getDisplaySizeV23(display: Display?, outSize: Point) {
        val mode = display!!.mode
        outSize.x = mode.physicalWidth
        outSize.y = mode.physicalHeight
    }

    @RequiresApi(17)
    private fun getDisplaySizeV17(display: Display?, outSize: Point) {
        display!!.getRealSize(outSize)
    }

    private fun getDisplaySizeV16(display: Display?, outSize: Point) {
        display!!.getSize(outSize)
    }

    @SuppressLint("NewApi")
    private fun getSystemLocales(): Array<String?> {
        val config = Resources.getSystem().configuration
        return if (SDK_INT >= 24) getSystemLocalesV24(config) else arrayOf(getLocaleLanguageTag(config.locale))
    }

    @RequiresApi(24)
    private fun getSystemLocalesV24(config: Configuration): Array<String?> {
        return split(config.locales.toLanguageTags(), ",")
    }

    @RequiresApi(21)
    private fun getLocaleLanguageTagV21(locale: Locale): String? {
        return locale.toLanguageTag()
    }

    private fun createIsoLanguageReplacementMap(): HashMap<String, String>? {
        val iso2Languages = Locale.getISOLanguages()
        val replacedLanguages = HashMap<String, String>( /* initialCapacity= */
                iso2Languages.size + additionalIsoLanguageReplacements.size)
        for (iso2: String in iso2Languages) {
            try {
                // This returns the ISO 639-2/T code for the language.
                val iso3 = Locale(iso2).isO3Language
                if (!TextUtils.isEmpty(iso3)) {
                    replacedLanguages[iso3] = iso2
                }
            } catch (e: MissingResourceException) {
                // Shouldn't happen for list of known languages, but we don't want to throw either.
            }
        }
        // Add additional replacement mappings.
        var i = 0
        while (i < additionalIsoLanguageReplacements.size) {
            replacedLanguages[additionalIsoLanguageReplacements[i]] = additionalIsoLanguageReplacements[i + 1]
            i += 2
        }
        return replacedLanguages
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun requestExternalStoragePermission(activity: Activity): Boolean {
        if ((activity.checkSelfPermission(permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            activity.requestPermissions(arrayOf(permission.READ_EXTERNAL_STORAGE),  /* requestCode= */0)
            return true
        }
        return false
    }

    //    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun isTrafficRestricted(uri: Uri): Boolean {
        return (("http" == uri.scheme) && !NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(checkNotNull(uri.host)))
    }

    private fun maybeReplaceLegacyLanguageTags(languageTag: String): String {
        var i = 0
        while (i < isoLegacyTagReplacements.size) {
            if (languageTag.startsWith(isoLegacyTagReplacements[i])) {
                return (isoLegacyTagReplacements[i + 1] + languageTag.substring( /* beginIndex= */isoLegacyTagReplacements[i].length))
            }
            i += 2
        }
        return languageTag
    }

    // Additional mapping from ISO3 to ISO2 language codes.
    private val additionalIsoLanguageReplacements = arrayOf( // Bibliographical codes defined in ISO 639-2/B, replaced by terminological code defined in
            // ISO 639-2/T. See https://en.wikipedia.org/wiki/List_of_ISO_639-2_codes.
            "alb", "sq", "arm", "hy", "baq", "eu", "bur", "my", "tib", "bo", "chi", "zh", "cze", "cs", "dut", "nl", "ger", "de", "gre", "el", "fre", "fr", "geo", "ka", "ice", "is", "mac", "mk", "mao", "mi", "may", "ms", "per", "fa", "rum", "ro", "scc", "hbs-srp", "slo", "sk", "wel", "cy",  // Deprecated 2-letter codes, replaced by modern equivalent (including macrolanguage)
            // See https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes, "ISO 639:1988"
            "id", "ms-ind", "iw", "he", "heb", "he", "ji", "yi",  // Individual macrolanguage codes mapped back to full macrolanguage code.
            // See https://en.wikipedia.org/wiki/ISO_639_macrolanguage
            "arb", "ar-arb", "in", "ms-ind", "ind", "ms-ind", "nb", "no-nob", "nob", "no-nob", "nn", "no-nno", "nno", "no-nno", "tw", "ak-twi", "twi", "ak-twi", "bs", "hbs-bos", "bos", "hbs-bos", "hr", "hbs-hrv", "hrv", "hbs-hrv", "sr", "hbs-srp", "srp", "hbs-srp", "cmn", "zh-cmn", "hak", "zh-hak", "nan", "zh-nan", "hsn", "zh-hsn")

    // Legacy tags that have been replaced by modern equivalents (including macrolanguage)
    // See https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry.
    private val isoLegacyTagReplacements = arrayOf("i-lux", "lb", "i-hak", "zh-hak", "i-navajo", "nv", "no-bok", "no-nob", "no-nyn", "no-nno", "zh-guoyu", "zh-cmn", "zh-hakka", "zh-hak", "zh-min-nan", "zh-nan", "zh-xiang", "zh-hsn")

    /**
     * Allows the CRC-32 calculation to be done byte by byte instead of bit per bit in the order "most
     * significant bit first".
     */
    private val CRC32_BYTES_MSBF = intArrayOf(0X00000000, 0X04C11DB7, 0X09823B6E, 0X0D4326D9, 0X130476DC, 0X17C56B6B, 0X1A864DB2, 0X1E475005, 0X2608EDB8, 0X22C9F00F, 0X2F8AD6D6, 0X2B4BCB61, 0X350C9B64, 0X31CD86D3, 0X3C8EA00A, 0X384FBDBD, 0X4C11DB70, 0X48D0C6C7, 0X4593E01E, 0X4152FDA9, 0X5F15ADAC, 0X5BD4B01B, 0X569796C2, 0X52568B75, 0X6A1936C8, 0X6ED82B7F, 0X639B0DA6, 0X675A1011, 0X791D4014, 0X7DDC5DA3, 0X709F7B7A, 0X745E66CD, -0x67dc4920, -0x631d54a9, -0x6e5e7272, -0x6a9f6fc7, -0x74d83fc4, -0x70192275, -0x7d5a04ae, -0x799b191b, -0x41d4a4a8, -0x4515b911, -0x48569fca, -0x4c97827f, -0x52d0d27c, -0x5611cfcd, -0x5b52e916, -0x5f93f4a3, -0x2bcd9270, -0x2f0c8fd9, -0x224fa902, -0x268eb4b7, -0x38c9e4b4, -0x3c08f905, -0x314bdfde, -0x358ac26b, -0xdc57fd8, -0x9046261, -0x44744ba, -0x86590f, -0x1ec1090c, -0x1a0014bd, -0x17433266, -0x13822fd3, 0X34867077, 0X30476DC0, 0X3D044B19, 0X39C556AE, 0X278206AB, 0X23431B1C, 0X2E003DC5, 0X2AC12072, 0X128E9DCF, 0X164F8078, 0X1B0CA6A1, 0X1FCDBB16, 0X018AEB13, 0X054BF6A4, 0X0808D07D, 0X0CC9CDCA, 0X7897AB07, 0X7C56B6B0, 0X71159069, 0X75D48DDE, 0X6B93DDDB, 0X6F52C06C, 0X6211E6B5, 0X66D0FB02, 0X5E9F46BF, 0X5A5E5B08, 0X571D7DD1, 0X53DC6066, 0X4D9B3063, 0X495A2DD4, 0X44190B0D, 0X40D816BA, -0x535a3969, -0x579b24e0, -0x5ad80207, -0x5e191fb2, -0x405e4fb5, -0x449f5204, -0x49dc74db, -0x4d1d696e, -0x7552d4d1, -0x7193c968, -0x7cd0efbf, -0x7811f20a, -0x6656a20d, -0x6297bfbc, -0x6fd49963, -0x6b1584d6, -0x1f4be219, -0x1b8affb0, -0x16c9d977, -0x1208c4c2, -0xc4f94c5, -0x88e8974, -0x5cdafab, -0x10cb21e, -0x39430fa1, -0x3d821218, -0x30c134cf, -0x3400297a, -0x2a47797d, -0x2e8664cc, -0x23c54213, -0x27045fa6, 0X690CE0EE, 0X6DCDFD59, 0X608EDB80, 0X644FC637, 0X7A089632, 0X7EC98B85, 0X738AAD5C, 0X774BB0EB, 0X4F040D56, 0X4BC510E1, 0X46863638, 0X42472B8F, 0X5C007B8A, 0X58C1663D, 0X558240E4, 0X51435D53, 0X251D3B9E, 0X21DC2629, 0X2C9F00F0, 0X285E1D47, 0X36194D42, 0X32D850F5, 0X3F9B762C, 0X3B5A6B9B, 0X0315D626, 0X07D4CB91, 0X0A97ED48, 0X0E56F0FF, 0X1011A0FA, 0X14D0BD4D, 0X19939B94, 0X1D528623, -0xed0a9f2, -0xa11b447, -0x75292a0, -0x3938f29, -0x1dd4df2e, -0x1915c29b, -0x1456e444, -0x1097f9f5, -0x28d8444a, -0x2c1959ff, -0x215a7f28, -0x259b6291, -0x3bdc3296, -0x3f1d2f23, -0x325e09fc, -0x369f144d, -0x42c17282, -0x46006f37, -0x4b4349f0, -0x4f825459, -0x51c5045e, -0x550419eb, -0x58473f34, -0x5c862285, -0x64c99f3a, -0x6008828f, -0x6d4ba458, -0x698ab9e1, -0x77cde9e6, -0x730cf453, -0x7e4fd28c, -0x7a8ecf3d, 0X5D8A9099, 0X594B8D2E, 0X5408ABF7, 0X50C9B640, 0X4E8EE645, 0X4A4FFBF2, 0X470CDD2B, 0X43CDC09C, 0X7B827D21, 0X7F436096, 0X7200464F, 0X76C15BF8, 0X68860BFD, 0X6C47164A, 0X61043093, 0X65C52D24, 0X119B4BE9, 0X155A565E, 0X18197087, 0X1CD86D30, 0X029F3D35, 0X065E2082, 0X0B1D065B, 0X0FDC1BEC, 0X3793A651, 0X3352BBE6, 0X3E119D3F, 0X3AD08088, 0X2497D08D, 0X2056CD3A, 0X2D15EBE3, 0X29D4F654, -0x3a56d987, -0x3e97c432, -0x33d4e2e9, -0x3715ff60, -0x2952af5b, -0x2d93b2ee, -0x20d09435, -0x24118984, -0x1c5e343f, -0x189f298a, -0x15dc0f51, -0x111d12e8, -0xf5a42e3, -0xb9b5f56, -0x6d8798d, -0x219643c, -0x764702f7, -0x72861f42, -0x7fc53999, -0x7b042430, -0x6543742b, -0x6182699e, -0x6cc14f45, -0x680052f4, -0x504fef4f, -0x548ef2fa, -0x59cdd421, -0x5d0cc998, -0x434b9993, -0x478a8426, -0x4ac9a2fd, -0x4e08bf4c)

    /**
     * Allows the CRC-8 calculation to be done byte by byte instead of bit per bit in the order "most
     * significant bit first".
     */
    private val CRC8_BYTES_MSBF = intArrayOf(0x00, 0x07, 0x0E, 0x09, 0x1C, 0x1B, 0x12, 0x15, 0x38, 0x3F, 0x36, 0x31, 0x24, 0x23, 0x2A, 0x2D, 0x70, 0x77, 0x7E, 0x79, 0x6C, 0x6B, 0x62, 0x65, 0x48, 0x4F, 0x46, 0x41, 0x54, 0x53, 0x5A, 0x5D, 0xE0, 0xE7, 0xEE, 0xE9, 0xFC, 0xFB, 0xF2, 0xF5, 0xD8, 0xDF, 0xD6, 0xD1, 0xC4, 0xC3, 0xCA, 0xCD, 0x90, 0x97, 0x9E, 0x99, 0x8C, 0x8B, 0x82, 0x85, 0xA8, 0xAF, 0xA6, 0xA1, 0xB4, 0xB3, 0xBA, 0xBD, 0xC7, 0xC0, 0xC9, 0xCE, 0xDB, 0xDC, 0xD5, 0xD2, 0xFF, 0xF8, 0xF1, 0xF6, 0xE3, 0xE4, 0xED, 0xEA, 0xB7, 0xB0, 0xB9, 0xBE, 0xAB, 0xAC, 0xA5, 0xA2, 0x8F, 0x88, 0x81, 0x86, 0x93, 0x94, 0x9D, 0x9A, 0x27, 0x20, 0x29, 0x2E, 0x3B, 0x3C, 0x35, 0x32, 0x1F, 0x18, 0x11, 0x16, 0x03, 0x04, 0x0D, 0x0A, 0x57, 0x50, 0x59, 0x5E, 0x4B, 0x4C, 0x45, 0x42, 0x6F, 0x68, 0x61, 0x66, 0x73, 0x74, 0x7D, 0x7A, 0x89, 0x8E, 0x87, 0x80, 0x95, 0x92, 0x9B, 0x9C, 0xB1, 0xB6, 0xBF, 0xB8, 0xAD, 0xAA, 0xA3, 0xA4, 0xF9, 0xFE, 0xF7, 0xF0, 0xE5, 0xE2, 0xEB, 0xEC, 0xC1, 0xC6, 0xCF, 0xC8, 0xDD, 0xDA, 0xD3, 0xD4, 0x69, 0x6E, 0x67, 0x60, 0x75, 0x72, 0x7B, 0x7C, 0x51, 0x56, 0x5F, 0x58, 0x4D, 0x4A, 0x43, 0x44, 0x19, 0x1E, 0x17, 0x10, 0x05, 0x02, 0x0B, 0x0C, 0x21, 0x26, 0x2F, 0x28, 0x3D, 0x3A, 0x33, 0x34, 0x4E, 0x49, 0x40, 0x47, 0x52, 0x55, 0x5C, 0x5B, 0x76, 0x71, 0x78, 0x7F, 0x6A, 0x6D, 0x64, 0x63, 0x3E, 0x39, 0x30, 0x37, 0x22, 0x25, 0x2C, 0x2B, 0x06, 0x01, 0x08, 0x0F, 0x1A, 0x1D, 0x14, 0x13, 0xAE, 0xA9, 0xA0, 0xA7, 0xB2, 0xB5, 0xBC, 0xBB, 0x96, 0x91, 0x98, 0x9F, 0x8A, 0x8D, 0x84, 0x83, 0xDE, 0xD9, 0xD0, 0xD7, 0xC2, 0xC5, 0xCC, 0xCB, 0xE6, 0xE1, 0xE8, 0xEF, 0xFA, 0xFD, 0xF4, 0xF3)
}