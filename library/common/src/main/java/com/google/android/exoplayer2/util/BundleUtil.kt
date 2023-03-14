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
package com.google.android.exoplayer2.util

import android.os.Bundleimport

android.os.IBinderimport java.lang.reflect.InvocationTargetExceptionimport java.lang.reflect.Method
/** Utilities for [Bundle].  */
object BundleUtil {
    private val TAG: String = "BundleUtil"
    private var getIBinderMethod: Method? = null
    private var putIBinderMethod: Method? = null

    /**
     * Gets an [IBinder] inside a [Bundle] for all Android versions.
     *
     * @param bundle The bundle to get the [IBinder].
     * @param key The key to use while getting the [IBinder].
     * @return The [IBinder] that was obtained.
     */
    fun getBinder(bundle: Bundle, key: String?): IBinder? {
        if (Util.SDK_INT >= 18) {
            return bundle.getBinder(key)
        } else {
            return getBinderByReflection(bundle, key)
        }
    }

    /**
     * Puts an [IBinder] inside a [Bundle] for all Android versions.
     *
     * @param bundle The bundle to insert the [IBinder].
     * @param key The key to use while putting the [IBinder].
     * @param binder The [IBinder] to put.
     */
    fun putBinder(bundle: Bundle, key: String?, binder: IBinder?) {
        if (Util.SDK_INT >= 18) {
            bundle.putBinder(key, binder)
        } else {
            putBinderByReflection(bundle, key, binder)
        }
    }

    // Method.invoke may take null "key".
    private fun getBinderByReflection(bundle: Bundle, key: String?): IBinder? {
        var getIBinder: Method? = getIBinderMethod
        if (getIBinder == null) {
            try {
                getIBinderMethod = Bundle::class.java.getMethod("getIBinder", String::class.java)
                getIBinderMethod.setAccessible(true)
            } catch (e: NoSuchMethodException) {
                Log.i(TAG, "Failed to retrieve getIBinder method", e)
                return null
            }
            getIBinder = getIBinderMethod
        }
        try {
            return getIBinder!!.invoke(bundle, key) as IBinder?
        } catch (e: InvocationTargetException) {
            Log.i(TAG, "Failed to invoke getIBinder via reflection", e)
            return null
        } catch (e: IllegalAccessException) {
            Log.i(TAG, "Failed to invoke getIBinder via reflection", e)
            return null
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "Failed to invoke getIBinder via reflection", e)
            return null
        }
    }

    // Method.invoke may take null "key" and "binder".
    private fun putBinderByReflection(
            bundle: Bundle, key: String?, binder: IBinder?) {
        var putIBinder: Method? = putIBinderMethod
        if (putIBinder == null) {
            try {
                putIBinderMethod = Bundle::class.java.getMethod("putIBinder", String::class.java, IBinder::class.java)
                putIBinderMethod.setAccessible(true)
            } catch (e: NoSuchMethodException) {
                Log.i(TAG, "Failed to retrieve putIBinder method", e)
                return
            }
            putIBinder = putIBinderMethod
        }
        try {
            putIBinder!!.invoke(bundle, key, binder)
        } catch (e: InvocationTargetException) {
            Log.i(TAG, "Failed to invoke putIBinder via reflection", e)
        } catch (e: IllegalAccessException) {
            Log.i(TAG, "Failed to invoke putIBinder via reflection", e)
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "Failed to invoke putIBinder via reflection", e)
        }
    }
}