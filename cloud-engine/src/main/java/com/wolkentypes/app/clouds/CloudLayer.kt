package com.wolkentypes.app.clouds

/** Van achter naar voren; deze volgorde is tevens de tekenvolgorde. */
enum class CloudLayer(
    val label: String,
    val baseSpeedDpPerSec: Float,
    val scaleRange: ClosedFloatingPointRange<Float>,
    val heightBand: ClosedFloatingPointRange<Float>,
    val cameraParallax: Float,
    val alpha: Float
) {
    HORIZON("Horizon", 0.35f, 0.12f..0.22f, 0.60f..0.68f, 0.12f, 0.72f),
    DISTANT("Ver weg", 0.75f, 0.20f..0.34f, 0.48f..0.60f, 0.25f, 0.80f),
    MIDFIELD("Middenveld", 1.5f, 0.38f..0.60f, 0.30f..0.48f, 0.48f, 0.90f),
    NEAR("Dichtbij", 2.8f, 0.65f..0.95f, 0.12f..0.32f, 0.72f, 0.97f),
    OVERHEAD("Overhead", 4.5f, 1.10f..1.55f, -0.05f..0.14f, 1f, 1f)
}
