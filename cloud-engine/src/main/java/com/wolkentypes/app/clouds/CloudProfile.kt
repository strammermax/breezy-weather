package com.wolkentypes.app.clouds

enum class CloudTextureType {
    WHITE,
    DARK,
    SMOKE,
    EXPLOSION,
    EXPLOSION_2,
    HORIZON_BANK,
    OVERHEAD_BANK,
}

enum class CloudAmount(val weight: Int) { NONE(0), A_FEW(1), SOME(3), A_LOT(7) }

data class LayerCloudConfig(
    val types: Set<CloudTextureType> = emptySet(),
    val amount: CloudAmount = CloudAmount.NONE,
    val sizeMultiplier: Float = 1f,
    val heightOffset: Float = 0f,
    val verticalSpread: Float = 1f,
    val speedMultiplier: Float = 1f,
    val alphaMultiplier: Float = 1f,
    val horizontalSpread: Float = 1f,
)

data class CloudProfile(
    val amounts: Map<CloudTextureType, CloudAmount> = emptyMap(),
    val layers: Map<CloudLayer, LayerCloudConfig> = emptyMap(),
    val density: Float = 1f,
    val speed: Float = 1f,
)

fun cloudProfileFor(weatherId: String): CloudProfile {
    val (types, amount, density, speed) = when (weatherId) {
        "clear" -> ProfileSeed(emptySet(), CloudAmount.NONE, 0f, 1f)
        "mostly_clear" -> ProfileSeed(setOf(CloudTextureType.WHITE), CloudAmount.A_FEW, 0.8f, 1f)
        "partly_cloudy" -> ProfileSeed(
            setOf(CloudTextureType.WHITE, CloudTextureType.DARK),
            CloudAmount.A_LOT,
            1.2f,
            1f
        )
        // "Zwaar bewolkt": dreigende, donkere wolken — vrijwel volledige dekking, DARK-zwaar.
        "mostly_cloudy" -> ProfileSeed(
            setOf(CloudTextureType.DARK, CloudTextureType.WHITE),
            CloudAmount.A_LOT,
            1.6f,
            1f
        )
        "cloudy" -> ProfileSeed(setOf(CloudTextureType.WHITE, CloudTextureType.DARK), CloudAmount.A_LOT, 1.8f, 1f)
        "overcast", "rain" -> ProfileSeed(
            setOf(CloudTextureType.DARK, CloudTextureType.SMOKE),
            CloudAmount.A_LOT,
            1.15f,
            1f
        )
        "drizzle", "fog" -> ProfileSeed(
            setOf(CloudTextureType.SMOKE, CloudTextureType.DARK),
            CloudAmount.SOME,
            1.1f,
            0.8f
        )
        "showers", "thunderstorm" -> ProfileSeed(
            setOf(CloudTextureType.DARK, CloudTextureType.WHITE),
            CloudAmount.A_LOT,
            1.15f,
            1.5f
        )
        // Zelfde egaal-grijze dekcompositie als "overcast": vraag was om dat wolkendek ook
        // voor sneeuw en sneeuwbuien te gebruiken.
        "snow", "snow_showers" -> ProfileSeed(
            setOf(CloudTextureType.DARK, CloudTextureType.SMOKE),
            CloudAmount.A_LOT,
            1.15f,
            .85f
        )
        "windy" -> ProfileSeed(setOf(CloudTextureType.WHITE, CloudTextureType.DARK), CloudAmount.SOME, 1f, 2.4f)
        else -> ProfileSeed(setOf(CloudTextureType.WHITE), CloudAmount.SOME, 1f, 1f)
    }

    val layers = when (weatherId) {
        "mostly_clear" -> lightCloudLayers()
        "partly_cloudy" -> halfCloudLayers()
        "cloudy" -> cloudyLayers()
        "overcast", "snow", "snow_showers" -> grayOvercastLayers()
        "drizzle" -> drizzleLayers()
        "rain" -> rainLayers()
        "showers" -> showerLayers()
        "mostly_cloudy", "thunderstorm" -> fullCoverageLayers(types)
        else -> standardLayers(types, amount, weatherId)
    }
    return CloudProfile(layers = layers, density = density, speed = speed)
}

/**
 * Volle-dekking compositie voor "100% grijs" weertypes: elke laag staat op A_LOT
 * (i.p.v. de NEAR-laag terug te schalen naar SOME, zoals [standardLayers] doet voor
 * lichtere profielen), en beide banklagen staan altijd aan zodat er geen gaten in de
 * onderste helft van de lucht overblijven.
 */
private fun fullCoverageLayers(types: Set<CloudTextureType>) = mapOf(
    CloudLayer.HORIZON to LayerCloudConfig(types + CloudTextureType.HORIZON_BANK, CloudAmount.A_LOT),
    CloudLayer.DISTANT to LayerCloudConfig(types, CloudAmount.A_LOT),
    CloudLayer.MIDFIELD to LayerCloudConfig(types, CloudAmount.A_LOT),
    CloudLayer.NEAR to LayerCloudConfig(types, CloudAmount.A_LOT),
    CloudLayer.OVERHEAD to LayerCloudConfig(types + CloudTextureType.OVERHEAD_BANK, CloudAmount.A_LOT)
)

/** Startcompositie gebaseerd op de vijf gemarkeerde stroken uit de referentie. */
private fun lightCloudLayers() = mapOf(
    CloudLayer.HORIZON to LayerCloudConfig(
        setOf(CloudTextureType.WHITE, CloudTextureType.HORIZON_BANK),
        CloudAmount.SOME
    ),
    CloudLayer.DISTANT to LayerCloudConfig(setOf(CloudTextureType.WHITE), CloudAmount.A_LOT),
    CloudLayer.MIDFIELD to LayerCloudConfig(setOf(CloudTextureType.WHITE), CloudAmount.SOME),
    CloudLayer.NEAR to LayerCloudConfig(setOf(CloudTextureType.WHITE), CloudAmount.A_FEW),
    CloudLayer.OVERHEAD to LayerCloudConfig(
        setOf(CloudTextureType.WHITE, CloudTextureType.SMOKE),
        CloudAmount.A_FEW
    )
)

/**
 * Half-bewolkt compositie naar bewolkt4.jpg: een groot donker dek boven de kijker,
 * brede blauwe openingen in het midden en steeds kleinere wolken naar de horizon.
 */
private fun halfCloudLayers() = mapOf(
    CloudLayer.HORIZON to LayerCloudConfig(
        setOf(CloudTextureType.WHITE, CloudTextureType.DARK, CloudTextureType.HORIZON_BANK),
        CloudAmount.A_LOT
    ),
    CloudLayer.DISTANT to LayerCloudConfig(
        setOf(CloudTextureType.WHITE, CloudTextureType.DARK),
        CloudAmount.SOME,
        sizeMultiplier = 1.40f
    ),
    CloudLayer.MIDFIELD to LayerCloudConfig(
        setOf(CloudTextureType.WHITE, CloudTextureType.DARK),
        CloudAmount.SOME,
        sizeMultiplier = 1.55f
    ),
    CloudLayer.NEAR to LayerCloudConfig(
        setOf(CloudTextureType.DARK, CloudTextureType.WHITE),
        CloudAmount.A_FEW,
        sizeMultiplier = 1.35f
    ),
    CloudLayer.OVERHEAD to LayerCloudConfig(
        setOf(CloudTextureType.OVERHEAD_BANK),
        CloudAmount.A_LOT
    )
)

/** Zacht, vrijwel gesloten stratocumulusdek naar Voorbeelden/Bewolkt/bewolkt1.png. */
private fun cloudyLayers() = mapOf(
    CloudLayer.HORIZON to LayerCloudConfig(
        setOf(CloudTextureType.HORIZON_BANK),
        CloudAmount.A_LOT
    ),
    CloudLayer.DISTANT to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.NONE,
        sizeMultiplier = 1.30f
    ),
    CloudLayer.MIDFIELD to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        sizeMultiplier = 1.55f
    ),
    CloudLayer.NEAR to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.NONE,
        sizeMultiplier = 1.75f
    ),
    CloudLayer.OVERHEAD to LayerCloudConfig(
        setOf(CloudTextureType.OVERHEAD_BANK),
        CloudAmount.A_LOT
    )
)

/** Vijf rustige stratusplaten, één eigen asset en beginpositie per dieptelaag. */
private fun grayOvercastLayers() = mapOf(
    CloudLayer.HORIZON to LayerCloudConfig(
        setOf(CloudTextureType.HORIZON_BANK),
        CloudAmount.A_LOT,
        heightOffset = .08f,
        speedMultiplier = .35f
    ),
    CloudLayer.DISTANT to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        heightOffset = .10f,
        verticalSpread = .2f,
        speedMultiplier = .4f,
        horizontalSpread = .65f
    ),
    CloudLayer.MIDFIELD to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        heightOffset = .09f,
        verticalSpread = .2f,
        speedMultiplier = .48f,
        horizontalSpread = .65f
    ),
    CloudLayer.NEAR to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        heightOffset = .08f,
        verticalSpread = .2f,
        speedMultiplier = .58f,
        horizontalSpread = .65f
    ),
    CloudLayer.OVERHEAD to LayerCloudConfig(
        setOf(CloudTextureType.OVERHEAD_BANK),
        CloudAmount.A_LOT,
        speedMultiplier = .7f
    )
)

/** Laag stratusdek met weinig beweging; de fijne neerslag wordt apart getekend. */
private fun drizzleLayers() = mapOf(
    CloudLayer.HORIZON to LayerCloudConfig(
        setOf(CloudTextureType.HORIZON_BANK),
        CloudAmount.A_LOT,
        heightOffset = .11f,
        speedMultiplier = .24f,
        alphaMultiplier = .98f
    ),
    CloudLayer.DISTANT to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        heightOffset = .12f,
        verticalSpread = .18f,
        speedMultiplier = .28f,
        alphaMultiplier = .96f
    ),
    CloudLayer.MIDFIELD to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        heightOffset = .08f,
        verticalSpread = .22f,
        speedMultiplier = .34f,
        alphaMultiplier = .97f
    ),
    CloudLayer.NEAR to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        heightOffset = .04f,
        verticalSpread = .2f,
        speedMultiplier = .42f,
        alphaMultiplier = .94f
    ),
    CloudLayer.OVERHEAD to LayerCloudConfig(
        setOf(CloudTextureType.OVERHEAD_BANK),
        CloudAmount.A_LOT,
        heightOffset = -.02f,
        speedMultiplier = .48f,
        alphaMultiplier = .97f
    )
)

/** Zwaar blauw regenfront: brede voorrand met een gesloten dek erboven en nevel eronder. */
private fun rainLayers() = mapOf(
    CloudLayer.HORIZON to LayerCloudConfig(
        setOf(CloudTextureType.HORIZON_BANK, CloudTextureType.SMOKE),
        CloudAmount.A_LOT,
        heightOffset = .10f,
        speedMultiplier = .55f,
        alphaMultiplier = .88f
    ),
    CloudLayer.DISTANT to LayerCloudConfig(
        setOf(CloudTextureType.DARK, CloudTextureType.SMOKE),
        CloudAmount.SOME,
        heightOffset = .10f,
        verticalSpread = .45f,
        speedMultiplier = .65f,
        alphaMultiplier = .78f
    ),
    CloudLayer.MIDFIELD to LayerCloudConfig(
        emptySet(),
        CloudAmount.A_FEW,
        speedMultiplier = .72f
    ),
    CloudLayer.NEAR to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        heightOffset = -.08f,
        verticalSpread = .35f,
        sizeMultiplier = 1.45f,
        speedMultiplier = .82f,
        alphaMultiplier = .92f
    ),
    CloudLayer.OVERHEAD to LayerCloudConfig(
        setOf(CloudTextureType.OVERHEAD_BANK),
        CloudAmount.A_LOT,
        heightOffset = -.04f,
        speedMultiplier = .9f,
        alphaMultiplier = .94f
    )
)

/** Losse buienwolken met bewust brede open stukken ertussen. */
private fun showerLayers() = mapOf(
    CloudLayer.HORIZON to LayerCloudConfig(
        setOf(CloudTextureType.DARK, CloudTextureType.HORIZON_BANK),
        CloudAmount.SOME,
        speedMultiplier = 1.15f
    ),
    CloudLayer.DISTANT to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        sizeMultiplier = 1.15f,
        horizontalSpread = 1.35f
    ),
    CloudLayer.MIDFIELD to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        sizeMultiplier = 1.25f,
        horizontalSpread = 1.55f
    ),
    CloudLayer.NEAR to LayerCloudConfig(
        setOf(CloudTextureType.DARK),
        CloudAmount.A_FEW,
        sizeMultiplier = 1.45f,
        heightOffset = -.05f,
        horizontalSpread = 1.7f
    ),
    CloudLayer.OVERHEAD to LayerCloudConfig(
        setOf(CloudTextureType.OVERHEAD_BANK),
        CloudAmount.A_FEW,
        sizeMultiplier = .9f,
        speedMultiplier = 1.25f
    )
)

private fun standardLayers(
    types: Set<CloudTextureType>,
    amount: CloudAmount,
    weatherId: String,
): Map<CloudLayer, LayerCloudConfig> {
    if (types.isEmpty()) return CloudLayer.entries.associateWith { LayerCloudConfig() }
    val dense = amount == CloudAmount.A_LOT
    val overheadHasBank = dense || weatherId in setOf("overcast", "rain", "thunderstorm")
    return mapOf(
        CloudLayer.HORIZON to LayerCloudConfig(types + CloudTextureType.HORIZON_BANK, amount),
        CloudLayer.DISTANT to LayerCloudConfig(types, amount),
        CloudLayer.MIDFIELD to LayerCloudConfig(types, amount),
        CloudLayer.NEAR to LayerCloudConfig(types, if (dense) CloudAmount.SOME else amount),
        CloudLayer.OVERHEAD to LayerCloudConfig(
            types + if (overheadHasBank) setOf(CloudTextureType.OVERHEAD_BANK) else emptySet(),
            if (dense) CloudAmount.SOME else CloudAmount.A_FEW
        )
    )
}

private data class ProfileSeed(
    val types: Set<CloudTextureType>,
    val amount: CloudAmount,
    val density: Float,
    val speed: Float,
)
