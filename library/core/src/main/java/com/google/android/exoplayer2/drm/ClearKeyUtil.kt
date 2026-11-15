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
package com.google.android.exoplayer2.drm

import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import org.json.JSONException
import org.json.JSONObject

/** Utility methods for ClearKey. */
internal object ClearKeyUtil {

  private const val TAG = "ClearKeyUtil"

  /**
   * Adjusts ClearKey request data obtained from the Android ClearKey CDM to be spec compliant.
   *
   * @param request The request data.
   * @return The adjusted request data.
   */
  @JvmStatic
  fun adjustRequestData(request: ByteArray): ByteArray {
    if (Util.SDK_INT >= 27) {
      return request
    }
    // Prior to O-MR1 the ClearKey CDM encoded the values in the "kids" array using Base64 encoding
    // rather than Base64Url encoding. See [Internal: b/64388098]. We know the exact request format
    // from the platform's InitDataParser.cpp. Since there aren't any "+" or "/" symbols elsewhere
    // in the request, it's safe to fix the encoding by replacement through the whole request.
    val requestString = Util.fromUtf8Bytes(request)
    return Util.getUtf8Bytes(base64ToBase64Url(requestString))
  }

  /**
   * Adjusts ClearKey response data to be suitable for providing to the Android ClearKey CDM.
   *
   * @param response The response data.
   * @return The adjusted response data.
   */
  @JvmStatic
  fun adjustResponseData(response: ByteArray): ByteArray {
    if (Util.SDK_INT >= 27) {
      return response
    }
    // Prior to O-MR1 the ClearKey CDM expected Base64 encoding rather than Base64Url encoding for
    // the "k" and "kid" strings. See [Internal: b/64388098]. We know that the ClearKey CDM only
    // looks at the k, kid and kty parameters in each key, so can ignore the rest of the response.
    return try {
      val responseJson = JSONObject(Util.fromUtf8Bytes(response))
      val adjustedResponseBuilder = StringBuilder("{\"keys\":[")
      val keysArray = responseJson.getJSONArray("keys")
      for (i in 0 until keysArray.length()) {
        if (i != 0) {
          adjustedResponseBuilder.append(",")
        }
        val key = keysArray.getJSONObject(i)
        adjustedResponseBuilder.append("{\"k\":\"")
        adjustedResponseBuilder.append(base64UrlToBase64(key.getString("k")))
        adjustedResponseBuilder.append("\",\"kid\":\"")
        adjustedResponseBuilder.append(base64UrlToBase64(key.getString("kid")))
        adjustedResponseBuilder.append("\",\"kty\":\"")
        adjustedResponseBuilder.append(key.getString("kty"))
        adjustedResponseBuilder.append("\"}")
      }
      adjustedResponseBuilder.append("]}")
      Util.getUtf8Bytes(adjustedResponseBuilder.toString())
    } catch (e: JSONException) {
      Log.e(TAG, "Failed to adjust response data: " + Util.fromUtf8Bytes(response), e)
      response
    }
  }

  private fun base64ToBase64Url(base64: String): String {
    return base64.replace('+', '-').replace('/', '_')
  }

  private fun base64UrlToBase64(base64Url: String): String {
    return base64Url.replace('-', '+').replace('_', '/')
  }
}
