package com.liveweatherwallpaperapp.wallpaper.photo

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SortLocationRecsTest {

    @Test
    fun keepsLastSufficientRingInsteadOfSelectingFirstInsufficientRing() {
        val unrestricted = (1..190).toList()
        val fiveKm = listOf(1)

        smallestRingWithEnoughPhotos(
            listOf(unrestricted, fiveKm),
            minimumRequired = 4
        ) shouldBe unrestricted
    }

    @Test
    fun selectsNarrowestRingThatStillHasEnoughPhotos() {
        val unrestricted = (1..190).toList()
        val fiveKm = (1..40).toList()
        val twoKm = (1..15).toList()
        val oneKm = listOf(1)

        smallestRingWithEnoughPhotos(
            listOf(unrestricted, fiveKm, twoKm, oneKm),
                minimumRequired = 4
        ) shouldBe twoKm
    }
}
