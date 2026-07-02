package com.wolkentypes.app.clouds

/**
 * Eén wolk-instantie. De baan is deterministisch afgeleid uit [laneOffsetFraction] en de
 * verstreken tijd (zie [CloudField]) in plaats van een per-frame gemuteerde xPosition: dat
 * voorkomt de reset-race die ontstaat als meerdere wolken tegelijk buiten beeld raken, en
 * betekent dat er geen enkel object per frame hoeft te worden aangemaakt of gemuteerd.
 */
internal data class CloudInstance(
    val layer: CloudLayer,
    val laneOffsetFraction: Float,
    val yFraction: Float,
    val scale: Float,
    val assetIndex: Int,
    val baseWidthDp: Float,
    val rotationDegrees: Float,
    val alpha: Float
)
