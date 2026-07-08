package com.liveweatherwallpaperapp.wallpaper.photo

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import livewallpaperweather.data.wallpaper.WallpaperPhotoRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.TimeZone

class WallpaperPhotoPriorityTest {

    private lateinit var originalDefaultTimeZone: TimeZone

    // selectWallpaperPhoto's day/night check uses TimeZone.getDefault(), so it's pinned for
    // these tests rather than left to whatever zone happens to run the test.
    private val zone = TimeZone.getTimeZone("Europe/Amsterdam")

    @BeforeEach
    fun pinTimeZone() {
        originalDefaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(zone)
    }

    @AfterEach
    fun restoreTimeZone() {
        TimeZone.setDefault(originalDefaultTimeZone)
    }

    /** June 15th local noon/midnight in [zone]: unambiguously day and unambiguously night. */
    private fun localMillis(hourOfDay: Int): Long = Calendar.getInstance(zone).apply {
        set(2025, Calendar.JUNE, 15, hourOfDay, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val noon = localMillis(12)
    private val midnight = localMillis(2)

    @Test
    fun `season tier ranks a match above unknown above a different known season`() {
        seasonTier(photo("matching", 0, season = "summer"), currentSeason = "summer") shouldBe 2
        seasonTier(photo("unknown", 0, season = null), currentSeason = "summer") shouldBe 1
        seasonTier(photo("wrong", 0, season = "winter"), currentSeason = "summer") shouldBe 0
    }

    @Test
    fun `day-night match treats unclassified photos as day`() {
        dayNightMatches(photo("day", 0, dayPeriod = "day"), isNight = false) shouldBe true
        dayNightMatches(photo("unclassified", 0, dayPeriod = null), isNight = false) shouldBe true
        dayNightMatches(photo("night", 0, dayPeriod = "night"), isNight = false) shouldBe false
        dayNightMatches(photo("night", 0, dayPeriod = "night"), isNight = true) shouldBe true
        dayNightMatches(photo("unclassified", 0, dayPeriod = null), isNight = true) shouldBe false
    }

    @Test
    fun `gps distance is real for known coordinates and worst-case for unknown ones`() {
        // ~0.03 degrees latitude is ~3.3km.
        val close = photo("close", 0, exifLat = 52.03, exifLon = 5.0)
        val unknown = photo("unknown", 0)

        gpsDistanceKmOrWorst(close, latitude = 52.0, longitude = 5.0) shouldBe (3.335847799336888 plusOrMinus 0.01)
        gpsDistanceKmOrWorst(unknown, latitude = 52.0, longitude = 5.0) shouldBe Double.MAX_VALUE
    }

    @Test
    fun `season beats day-night even when day-night would otherwise win`() {
        // "wrong" matches day/night but not season; "right" matches season but not day/night.
        // Season must win outright — no amount of day/night match compensates.
        val selected = selectWallpaperPhoto(
            listOf(
                photo("wrong-season-right-daynight", 0, season = "winter", dayPeriod = "day"),
                photo("right-season-wrong-daynight", 0, season = "summer", dayPeriod = "night")
            ),
            emptySet(),
            now = noon // currently day, currently summer
        )

        selected?.id shouldBe "right-season-wrong-daynight"
    }

    @Test
    fun `day-night beats gps proximity when season ties`() {
        val selected = selectWallpaperPhoto(
            listOf(
                photo("far-but-daynight-matches", 0, dayPeriod = "day", exifLat = 53.0, exifLon = 5.0),
                photo("close-but-daynight-wrong", 0, dayPeriod = "night", exifLat = 52.0, exifLon = 5.0)
            ),
            emptySet(),
            latitude = 52.0,
            longitude = 5.0,
            now = noon // currently day
        )

        selected?.id shouldBe "far-but-daynight-matches"
    }

    @Test
    fun `gps proximity beats thumbs up when season and day-night tie`() {
        val selected = selectWallpaperPhoto(
            listOf(
                photo("far-but-liked", 1, exifLat = 53.0, exifLon = 5.0),
                photo("close-but-neutral", 0, exifLat = 52.03, exifLon = 5.0)
            ),
            emptySet(),
            latitude = 52.0,
            longitude = 5.0
        )

        selected?.id shouldBe "close-but-neutral"
    }

    @Test
    fun `thumbs up beats fewer views when everything else ties`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("liked-but-shown-more", 1, views = 5), photo("fresh-but-neutral", 0, views = 0)),
            emptySet()
        )

        selected?.id shouldBe "liked-but-shown-more"
    }

    @Test
    fun `fewer views win when everything else ties`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("seen", 0, views = 4), photo("fresh", 0, views = 1)),
            emptySet()
        )

        selected?.id shouldBe "fresh"
    }

    @Test
    fun `thumbs down only wins when nothing else is eligible, regardless of other tiers`() {
        // The thumbs-down photo wins every other tier (season, day/night, gps, views) but must
        // still lose, since "not thumbs-down" is the very first tier.
        val selected = selectWallpaperPhoto(
            listOf(
                photo("down-but-perfect-otherwise", -1, season = "summer", dayPeriod = "day", views = 0),
                photo("neutral-but-worse-otherwise", 0, season = "winter", dayPeriod = "night", views = 10)
            ),
            emptySet(),
            now = noon
        )
        selected?.id shouldBe "neutral-but-worse-otherwise"

        val lastResort = selectWallpaperPhoto(
            listOf(photo("down1", -1, views = 10), photo("down2", -1, views = 0)),
            emptySet()
        )
        lastResort?.id shouldBe "down2"
    }

    @Test
    fun `disabled and excluded photos are never selected`() {
        val selected = selectWallpaperPhoto(
            listOf(
                photo("disabled", 1, disabled = true),
                photo("recent", 1, sourceUrl = "https://recent"),
                photo("available", 0)
            ),
            setOf("https://recent")
        )

        selected?.id shouldBe "available"
    }

    @Test
    fun `selection picks the daytime photo when it is currently day`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("night", 0, dayPeriod = "night"), photo("day", 0, dayPeriod = "day")),
            emptySet(),
            now = noon
        )

        selected?.id shouldBe "day"
    }

    @Test
    fun `selection picks the nighttime photo when it is currently night`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("night", 0, dayPeriod = "night"), photo("day", 0, dayPeriod = "day")),
            emptySet(),
            now = midnight
        )

        selected?.id shouldBe "night"
    }

    private fun photo(
        id: String,
        rating: Int,
        views: Int = 0,
        disabled: Boolean = false,
        sourceUrl: String = "https://example/$id",
        dayPeriod: String? = null,
        season: String? = null,
        exifLat: Double? = null,
        exifLon: Double? = null,
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
        dayPeriod = dayPeriod,
        season = season,
        exifLat = exifLat,
        exifLon = exifLon
    )
}
