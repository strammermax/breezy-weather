package org.breezyweather.wallpaper.photo

import breezyweather.data.wallpaper.WallpaperPhotoRecord
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WallpaperPhotoPriorityTest {

    @Test
    fun `thumbs up outranks neutral and thumbs down`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("down", -1), photo("neutral", 0), photo("up", 1)),
            emptySet(),
        )

        selected?.id shouldBe "up"
    }

    @Test
    fun `fewer views win within the same rating`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("seen", 0, views = 4), photo("fresh", 0, views = 1)),
            emptySet(),
        )

        selected?.id shouldBe "fresh"
    }

    @Test
    fun `disabled and recently shown photos are excluded`() {
        val selected = selectWallpaperPhoto(
            listOf(
                photo("disabled", 1, disabled = true),
                photo("recent", 1, sourceUrl = "https://recent"),
                photo("available", 0),
            ),
            setOf("https://recent"),
        )

        selected?.id shouldBe "available"
    }

    private fun photo(
        id: String,
        rating: Int,
        views: Int = 0,
        disabled: Boolean = false,
        sourceUrl: String = "https://example/$id",
    ) = WallpaperPhotoRecord(
        id = id,
        sourceUrl = sourceUrl,
        locationKey = "wallpaper_Test",
        locationName = "Test",
        filePath = "/tmp/$id.webp",
        attribution = null,
        processed = true,
        rating = rating,
        disabled = disabled,
        viewCount = views,
        createdAt = 0,
        updatedAt = 0,
        lastShownAt = null,
    )
}
