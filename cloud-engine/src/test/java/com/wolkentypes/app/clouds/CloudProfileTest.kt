package com.wolkentypes.app.clouds

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Every weatherId [CloudEngineAdapter] (in the LiveWeatherApp app module) can produce for a
 * [org.breezyweather.wallpaper.WallpaperSceneState]. Kept in sync manually — if
 * `CloudEngineAdapter.weatherId()` starts returning a new id, add it here too, otherwise
 * [every non-clear weatherId has visible coverage configured] silently stops covering it.
 */
private val ADAPTER_WEATHER_IDS = listOf(
    "clear", "mostly_clear", "windy", "fog", "thunderstorm",
    "partly_cloudy", "mostly_cloudy", "cloudy", "overcast",
    "snow", "snow_showers", "rain", "drizzle",
)

private val SPRITE_TYPES = setOf(CloudTextureType.WHITE, CloudTextureType.DARK, CloudTextureType.SMOKE)

class CloudProfileTest {
    @Test
    fun `clear has no configured coverage`() {
        val profile = cloudProfileFor("clear")
        profile.density shouldBe 0f
        profile.layers.values.forEach { it.amount shouldBe CloudAmount.NONE }
    }

    @Test
    fun `every non-clear weatherId has at least one layer with visible coverage`() {
        ADAPTER_WEATHER_IDS.filterNot { it == "clear" }.forEach { weatherId ->
            val profile = cloudProfileFor(weatherId)
            val hasVisibleLayer = profile.layers.values.any { it.amount != CloudAmount.NONE }
            assert(hasVisibleLayer) {
                "cloudProfileFor(\"$weatherId\") has no layer with amount != NONE " +
                    "-- this weatherId would render an empty/invisible sky"
            }
        }
    }

    @Test
    fun `every non-clear weatherId's visible layers include a sprite type`() {
        // A layer can carry HORIZON_BANK/OVERHEAD_BANK alone (drawn as a single wide banner),
        // but families that rely on drawBillboardInstances (everything outside the "overcast"
        // family below) need at least one WHITE/DARK/SMOKE sprite somewhere, or the billboard
        // asset pool for that weatherId ends up empty and nothing gets drawn.
        val overcastFamily = setOf("overcast", "drizzle", "rain", "snow", "snow_showers")
        ADAPTER_WEATHER_IDS.filterNot { it == "clear" || it in overcastFamily }.forEach { weatherId ->
            val profile = cloudProfileFor(weatherId)
            val hasSpriteType = profile.layers.values.any { config ->
                config.amount != CloudAmount.NONE && config.types.any { it in SPRITE_TYPES }
            }
            assert(hasSpriteType) {
                "cloudProfileFor(\"$weatherId\") has visible layers but none carry a sprite " +
                    "type (WHITE/DARK/SMOKE) -- drawBillboardInstances would have nothing to draw"
            }
        }
    }

    @Test
    fun `thunderstorm uses full coverage on every layer`() {
        // Directly covers the "Thunderstorm looked like a flat grey screen" observation from
        // manual emulator testing: this proves the profile itself asks for maximum coverage on
        // every layer (not an empty/near-empty configuration), which is what fullCoverageLayers
        // is meant to do for storm-family weather.
        val profile = cloudProfileFor("thunderstorm")
        CloudLayer.entries.forEach { layer ->
            profile.layers[layer]?.amount shouldBe CloudAmount.A_LOT
        }
        profile.density shouldBeGreaterThan 0f
    }

    @Test
    fun `thunderstorm and showers share the same seed profile`() {
        // cloudProfileFor's when-block routes "mostly_cloudy" and "thunderstorm" to the same
        // fullCoverageLayers branch: confirms that's still true rather than one of them having
        // silently fallen through to the generic standardLayers() branch.
        val thunderstorm = cloudProfileFor("thunderstorm")
        val mostlyCloudy = cloudProfileFor("mostly_cloudy")
        thunderstorm.layers.keys shouldBe mostlyCloudy.layers.keys
        CloudLayer.entries.forEach { layer ->
            thunderstorm.layers[layer]?.amount shouldBe mostlyCloudy.layers[layer]?.amount
        }
    }

    @Test
    fun `overcast-family weatherIds route to the shared overcast asset folder mapping`() {
        // grayOvercastLayers is what CloudSurfaceView/CloudEngineRenderer key off of (via
        // isOvercastFamily) to draw stratus plates instead of billboards for these weatherIds.
        listOf("overcast", "snow", "snow_showers").forEach { weatherId ->
            val profile = cloudProfileFor(weatherId)
            profile.layers[CloudLayer.HORIZON]?.types.orEmpty() shouldContain CloudTextureType.HORIZON_BANK
            profile.layers[CloudLayer.OVERHEAD]?.types.orEmpty() shouldContain CloudTextureType.OVERHEAD_BANK
        }
    }

    @Test
    fun `density and speed are finite and positive for every non-clear weatherId`() {
        ADAPTER_WEATHER_IDS.filterNot { it == "clear" }.forEach { weatherId ->
            val profile = cloudProfileFor(weatherId)
            assert(profile.density.isFinite() && profile.density > 0f) {
                "cloudProfileFor(\"$weatherId\").density is not finite/positive: ${profile.density}"
            }
            assert(profile.speed.isFinite() && profile.speed > 0f) {
                "cloudProfileFor(\"$weatherId\").speed is not finite/positive: ${profile.speed}"
            }
        }
    }
}
