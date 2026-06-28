package org.breezyweather.wallpaper.photo

import breezyweather.data.wallpaper.WallpaperPhotoRecord
import io.kotest.matchers.shouldBe
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
    fun `thumbs up outranks neutral`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("neutral", 0), photo("up", 1)),
            emptySet(),
        )

        selected?.id shouldBe "up"
    }

    @Test
    fun `thumbs down only wins when nothing else is eligible`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("down", -1), photo("neutral", 0), photo("up", 1)),
            emptySet(),
        )
        selected?.id shouldBe "up"

        val lastResort = selectWallpaperPhoto(
            listOf(photo("down1", -1, views = 10), photo("down2", -1, views = 0)),
            emptySet(),
        )
        lastResort?.id shouldBe "down2"
    }

    @Test
    fun `fewer views win within the same score`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("seen", 0, views = 4), photo("fresh", 0, views = 1)),
            emptySet(),
        )

        selected?.id shouldBe "fresh"
    }

    @Test
    fun `view penalty is capped so it cannot outweigh other terms`() {
        val heavilyShown = photo("heavy", 1, views = 1_000)
        val score = wallpaperPhotoScore(heavilyShown, isNight = false, currentSeason = "summer")
        // thumbs up (+5) + unclassified-as-day match (+20) - capped view penalty (-25): 0,
        // regardless of how high views climbs above the cap.
        score shouldBe 0
    }

    @Test
    fun `disabled and excluded photos are never selected`() {
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

    @Test
    fun `day-night match is scored, with unclassified photos treated as day`() {
        val dayPhoto = photo("day", 0, dayPeriod = "day")
        val nightPhoto = photo("night", 0, dayPeriod = "night")
        val unclassified = photo("unclassified", 0, dayPeriod = null)

        wallpaperPhotoScore(dayPhoto, isNight = false, currentSeason = "summer") shouldBe 20
        wallpaperPhotoScore(unclassified, isNight = false, currentSeason = "summer") shouldBe 20
        wallpaperPhotoScore(nightPhoto, isNight = false, currentSeason = "summer") shouldBe 0

        wallpaperPhotoScore(nightPhoto, isNight = true, currentSeason = "summer") shouldBe 20
        wallpaperPhotoScore(unclassified, isNight = true, currentSeason = "summer") shouldBe 0
    }

    @Test
    fun `matching season is a bonus, unknown season is neutral, wrong season is a penalty`() {
        val matching = photo("matching", 0, season = "summer")
        val wrong = photo("wrong", 0, season = "winter")
        val unknown = photo("unknown", 0, season = null)

        wallpaperPhotoScore(matching, isNight = false, currentSeason = "summer") shouldBe 35
        wallpaperPhotoScore(unknown, isNight = false, currentSeason = "summer") shouldBe 20
        wallpaperPhotoScore(wrong, isNight = false, currentSeason = "summer") shouldBe 5
    }

    @Test
    fun `selection picks the daytime photo when it is currently day`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("night", 0, dayPeriod = "night"), photo("day", 0, dayPeriod = "day")),
            emptySet(),
            now = noon,
        )

        selected?.id shouldBe "day"
    }

    @Test
    fun `selection picks the nighttime photo when it is currently night`() {
        val selected = selectWallpaperPhoto(
            listOf(photo("night", 0, dayPeriod = "night"), photo("day", 0, dayPeriod = "day")),
            emptySet(),
            now = midnight,
        )

        selected?.id shouldBe "night"
    }

    @Test
    fun `gps proximity is a bonus within 5km, a smaller bonus within 10km, and neutral beyond or unknown`() {
        // ~0.03 degrees latitude is ~3.3km, ~0.07 degrees is ~7.8km, ~1 degree is ~111km.
        val close = photo("close", 0, exifLat = 52.03, exifLon = 5.0)
        val near = photo("near", 0, exifLat = 52.07, exifLon = 5.0)
        val far = photo("far", 0, exifLat = 53.0, exifLon = 5.0)
        val unknown = photo("unknown", 0)

        fun score(p: WallpaperPhotoRecord) =
            wallpaperPhotoScore(p, isNight = false, currentSeason = "summer", latitude = 52.0, longitude = 5.0)

        score(close) shouldBe 30 // day match (20) + close gps (10)
        score(near) shouldBe 25 // day match (20) + near gps (5)
        score(far) shouldBe 20 // day match (20) + no gps bonus
        score(unknown) shouldBe 20 // day match (20), no exif gps at all
    }

    @Test
    fun `selection prefers the gps-close photo over an equally-scored farther one`() {
        val selected = selectWallpaperPhoto(
            listOf(
                photo("far", 0, exifLat = 53.0, exifLon = 5.0),
                photo("close", 0, exifLat = 52.03, exifLon = 5.0),
            ),
            emptySet(),
            latitude = 52.0,
            longitude = 5.0,
        )

        selected?.id shouldBe "close"
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
        exifLon = exifLon,
    )
}
