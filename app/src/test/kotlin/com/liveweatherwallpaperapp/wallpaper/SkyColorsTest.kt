/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import android.graphics.Color
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SkyColorsTest {
    @Test
    fun `normal fog replaces blue sky with neutral grey`() {
        val colors = SkyColors.applyFogSkyTint(SkyColors.DAY, fogIntensity = 0.69f, daytime = true)

        colors.forEach { color ->
            Color.red(color) shouldBe Color.green(color)
            Color.green(color) shouldBe Color.blue(color)
        }
    }
}
