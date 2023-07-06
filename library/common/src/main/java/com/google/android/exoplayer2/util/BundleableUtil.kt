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

import android.os.Bundle
import android.util.SparseArray
import com.google.android.exoplayer2.Bundleable
import com.google.android.exoplayer2.util.Util.castNonNull
import com.google.common.collect.ImmutableList

/** Utilities for [Bundleable].  */
object BundleableUtil {

    /** Converts a list of [Bundleable] to a list [Bundle].  */
    fun <T : Bundleable?> toBundleList(bundleableList: List<T>): ImmutableList<Bundle>? {
        val builder = ImmutableList.builder<Bundle>()
        for (i in bundleableList.indices) {
            val bundleable: Bundleable = bundleableList[i] as Bundleable
            builder.add(bundleable.toBundle())
        }
        return builder.build()
    }

    /** Converts a list of [Bundle] to a list of [Bundleable].  */
    fun <T : Bundleable?> fromBundleList(
            creator: Bundleable.Creator<T>, bundleList: List<Bundle?>): ImmutableList<T>? {
        val builder = ImmutableList.builder<T>()
        for (i in bundleList.indices) {
            val bundle = checkNotNull(bundleList[i]) // Fail fast during parsing.
            val bundleable = creator.fromBundle(bundle)
            builder.add(bundleable)
        }
        return builder.build()
    }

    /**
     * Converts a collection of [Bundleable] to an [ArrayList] of [Bundle] so that
     * the returned list can be put to [Bundle] using [Bundle.putParcelableArrayList]
     * conveniently.
     */
    fun <T : Bundleable?> toBundleArrayList(
            bundleables: Collection<T>): ArrayList<Bundle>? {
        val arrayList = ArrayList<Bundle>(bundleables.size)
        for (element in bundleables) {
            arrayList.add(element!!.toBundle())
        }
        return arrayList
    }

    /**
     * Converts a [SparseArray] of [Bundle] to a [SparseArray] of [ ].
     */
    fun <T : Bundleable?> fromBundleSparseArray(
            creator: Bundleable.Creator<T>, bundleSparseArray: SparseArray<Bundle?>): SparseArray<T>? {
        val result = SparseArray<T>(bundleSparseArray.size())
        for (i in 0 until bundleSparseArray.size()) {
            result.put(bundleSparseArray.keyAt(i), creator.fromBundle(bundleSparseArray.valueAt(i)))
        }
        return result
    }

    /**
     * Converts a [SparseArray] of [Bundleable] to an [SparseArray] of [ ] so that the returned [SparseArray] can be put to [Bundle] using [ ][Bundle.putSparseParcelableArray] conveniently.
     */
    fun <T : Bundleable?> toBundleSparseArray(
            bundleableSparseArray: SparseArray<T>): SparseArray<Bundle>? {
        val sparseArray = SparseArray<Bundle>(bundleableSparseArray.size())
        for (i in 0 until bundleableSparseArray.size()) {
            sparseArray.put(bundleableSparseArray.keyAt(i), bundleableSparseArray.valueAt(i)!!.toBundle())
        }
        return sparseArray
    }

    /**
     * Sets the application class loader to the given [Bundle] if no class loader is present.
     *
     *
     * This assumes that all classes unparceled from `bundle` are sharing the class loader of
     * `BundleableUtils`.
     */
    fun ensureClassLoader(bundle: Bundle?) {
        if (bundle != null) {
            bundle.classLoader = castNonNull(BundleableUtil::class.java.classLoader)
        }
    }
}