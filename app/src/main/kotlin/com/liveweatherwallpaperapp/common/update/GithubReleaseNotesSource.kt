/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.common.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Reads the release history generated at push time by scripts/generate_release_notes.py.
 * The historical class name is retained to keep callers stable; no GitHub/network call is made.
 */
class GithubReleaseNotesSource(private val context: Context) {
    data class ReleaseNotes(
        val tagName: String,
        val publishedAt: String,
        val changes: List<String>,
    )

    suspend fun getReleases(): List<ReleaseNotes> = withContext(Dispatchers.IO) {
        runCatching {
            val releases = context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                JSONArray(reader.readText())
            }
            buildList {
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: continue
                    val changesJson = release.optJSONArray("changes") ?: JSONArray()
                    val changes = buildList {
                        for (changeIndex in 0 until changesJson.length()) {
                            changesJson.optString(changeIndex).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                    add(
                        ReleaseNotes(
                            tagName = release.optString("version"),
                            publishedAt = release.optString("date"),
                            changes = changes
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun getLatest(): ReleaseNotes? = getReleases().firstOrNull()

    private companion object {
        const val ASSET_NAME = "release-notes.json"
    }
}
