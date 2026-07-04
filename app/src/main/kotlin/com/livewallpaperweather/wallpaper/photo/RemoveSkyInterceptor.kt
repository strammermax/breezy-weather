/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.livewallpaperweather.wallpaper.photo

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/** Adds authentication and focused error diagnostics for the configured RemoveSky host. */
internal class RemoveSkyInterceptor(
    private val store: WallpaperImageStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!isRemoveSkyHost(original.url.host)) return chain.proceed(original)

        val request = original.newBuilder().apply {
            store.removeSkyApiKey.takeIf { it.isNotBlank() }?.let { header("x-api-key", it) }

            val clientId = store.cfAccessClientId
            val clientSecret = store.cfAccessClientSecret
            if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
                header("CF-Access-Client-Id", clientId)
                header("CF-Access-Client-Secret", clientSecret)
            }
        }.build()

        val response = chain.proceed(request)
        logProblemResponse(response)
        return response
    }

    private fun isRemoveSkyHost(host: String): Boolean {
        val configuredUrl = store.removeSkyBaseUrl.ifBlank { RemoveSkyProvider.DEFAULT_BASE_URL }
        return configuredUrl.toHttpUrlOrNull()?.host.equals(host, ignoreCase = true)
    }

    private fun logProblemResponse(response: Response) {
        val contentType = response.header("Content-Type").orEmpty()
        val isHtmlContentType = contentType.contains("text/html", ignoreCase = true)
        if (response.isSuccessful && !isHtmlContentType) return

        val preview = try {
            response.peekBody(LOG_BODY_BYTE_LIMIT).string().take(LOG_BODY_CHAR_LIMIT)
        } catch (e: Throwable) {
            "<unavailable: ${e.javaClass.simpleName}>"
        }
        val trimmedPreview = preview.trimStart()
        val isHtml = isHtmlContentType ||
            trimmedPreview.startsWith("<!doctype html", ignoreCase = true) ||
            trimmedPreview.startsWith("<html", ignoreCase = true)

        Log.w(
            TAG,
            "RemoveSky response url=${response.request.url} status=${response.code} " +
                "contentType=${contentType.ifBlank { "<missing>" }} body=${preview.replace('\n', ' ')}"
        )
        if (isHtml) {
            Log.w(TAG, "Cloudflare Access response received; headers may be missing or invalid.")
        }
    }

    private companion object {
        const val TAG = "LWWPhoto"
        const val LOG_BODY_BYTE_LIMIT = 4096L
        const val LOG_BODY_CHAR_LIMIT = 200
    }
}
