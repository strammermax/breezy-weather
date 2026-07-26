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
        seasonTier(photo("matching", season = "summer"), currentSeason = "summer") shouldBe 2
        seasonTier(photo("unknown", season = null), currentSeason = "summer") shouldBe 1
        seasonTier(photo("wrong", season = "winter"), currentSeason = "summer") shouldBe 0
    }

    @Test
    fun `day-night match treats unclassified photos as day`() {
        dayNightMatches(photo("day", dayPeriod = "day"), isNight = false) shouldBe true
        dayNightMatches(photo("unclassified", dayPeriod = null), isNight = false) shouldBe true
        dayNightMatches(photo("night", dayPeriod = "night"), isNight = false) shouldBe false
        dayNightMatches(photo("night", dayPeriod = "night"), isNight = true) shouldBe true
        dayNightMatches(photo("unclassified", dayPeriod = null), isNight = true) shouldBe false
    }

    @Test
    fun `gps distance is real for known coordinates and worst-case for unknown ones`() {
        // ~0.03 degrees latitude is ~3.3km.
        val close = photo("close", exifLat = 52.03, exifLon = 5.0)
        val unknown = photo("unknown")

        gpsDistanceKmOrWorst(close, latitude = 52.0, longitude = 5.0) shouldBe (3.335847799336888 plusOrMinus 0.01)
        gpsDistanceKmOrWorst(unknown, latitude = 52.0, longitude = 5.0) shouldBe Double.MAX_VALUE
    }

    @Test
    fun `season beats day-night even when day-night would otherwise win`() {
        // "wrong" matches day/night but not season; "right" matches season but not day/night.
        // Season must win outright — no amount of day/night match compensates.
        val selected = selectWallpaperPhoto(
            listOf(
                photo("wrong-season-right-daynight", season = "winter", dayPeriod = "day"),
                photo("right-season-wrong-daynight", season = "summer", dayPeriod = "night")
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
                photo("far-but-daynight-matches", dayPeriod = "day", exifLat = 53.0, exifLon = 5.0),
                photo("close-but-daynight-wrong", dayPeriod = "night", exifLat = 52.0, exifLon = 5.0)
            ),
            emptySet(),
            latitude = 52.0,
            longitude = 5.0,
            now = noon // currently day
        )

        selected?.id shouldBe "far-but-daynight-matches"
    }

    @Test
    fun `gps proximity wins when season and day-night tie`() {
        val selected = selectWallpaperPhoto(
            listOf(
                photo("far", exifLat = 53.0, exifLon = 5.0),
                photo("close", exifLat = 52.03, exifLon = 5.0)
            ),
            emptySet(),
            latitude = 52.0,
            longitude = 5.0
        )

        selected?.id shouldBe "close"
    }

    @Test
    fun `fewer views win when everything else ties`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("seen", views = 4), photo("fresh", views = 1)),
            emptySet()
        )

        selected?.id shouldBe "fresh"
    }

    @Test
    fun `disabled and excluded photos are never selected`() {
        val selected = selectWallpaperPhoto(
            listOf(
                photo("disabled", disabled = true),
                photo("recent", sourceUrl = "https://recent"),
                photo("available")
            ),
            setOf("https://recent")
        )

        selected?.id shouldBe "available"
    }

    @Test
    fun `selection picks the daytime photo when it is currently day`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("night", dayPeriod = "night"), photo("day", dayPeriod = "day")),
            emptySet(),
            now = noon
        )

        selected?.id shouldBe "day"
    }

    @Test
    fun `selection picks the nighttime photo when it is currently night`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("night", dayPeriod = "night"), photo("day", dayPeriod = "day")),
            emptySet(),
            now = midnight
        )

        selected?.id shouldBe "night"
    }

    private fun photo(
        id: String,
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
