/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.livewallpaperweather.wallpaper

/**
 * Debug-only frame time and quality aggregator for the live wallpaper render loop (ACT-009).
 *
 * Uses a fixed-size ring buffer so [recordFrame] and [recordDroppedFrame] perform no
 * allocations; sorting for [FrameTelemetrySnapshot.p95FrameTimeMillis] only happens in
 * [snapshot].
 *
 * @property windowSize number of frames kept for average/p95, at least
 * [MIN_WINDOW_SIZE] (~4 seconds at 30 FPS).
 */
class FrameTelemetry(private val windowSize: Int = MIN_WINDOW_SIZE) {
    init {
        require(windowSize >= MIN_WINDOW_SIZE) { "windowSize must be at least $MIN_WINDOW_SIZE" }
    }

    private val frameTimesMillis = FloatArray(windowSize)
    private val sortBuffer = FloatArray(windowSize)
    private var writeIndex = 0
    private var filled = 0
    private var droppedFrames = 0
    private var totalFrames = 0
    private var degradationEvents = 0
    private var recoveryEvents = 0

    /** Records one frame's render time. A frame slower than [DROPPED_FRAME_THRESHOLD_MILLIS] counts as dropped. */
    fun recordFrame(frameTimeMillis: Float) {
        frameTimesMillis[writeIndex] = frameTimeMillis
        writeIndex = (writeIndex + 1) % windowSize
        if (filled < windowSize) filled++
        totalFrames++
        if (frameTimeMillis > DROPPED_FRAME_THRESHOLD_MILLIS) {
            droppedFrames++
        }
    }

    /** Records a frame that was skipped entirely (e.g. no surface available). */
    fun recordDroppedFrame() {
        droppedFrames++
        totalFrames++
    }

    /** Records that [WallpaperWeatherEffectRenderer]'s degradation level increased by one step. */
    fun recordDegradation() {
        degradationEvents++
    }

    /** Records that [WallpaperWeatherEffectRenderer]'s degradation level decreased by one step. */
    fun recordRecovery() {
        recoveryEvents++
    }

    /** Summarizes the current window. Sorts a scratch buffer; only call periodically, not per frame. */
    fun snapshot(activeProfile: WallpaperQualityProfile): FrameTelemetrySnapshot {
        if (filled == 0) {
            return FrameTelemetrySnapshot(
                averageFrameTimeMillis = 0f,
                p95FrameTimeMillis = 0f,
                droppedFrames = droppedFrames,
                totalFrames = totalFrames,
                activeProfile = activeProfile,
                degradationEvents = degradationEvents,
                recoveryEvents = recoveryEvents,
            )
        }
        var sum = 0f
        for (i in 0 until filled) {
            sortBuffer[i] = frameTimesMillis[i]
            sum += frameTimesMillis[i]
        }
        sortBuffer.sort(0, filled)
        val p95Index = (filled - 1) * P95_FRACTION
        val p95Lower = p95Index.toInt()
        val p95 = sortBuffer[p95Lower.coerceIn(0, filled - 1)]
        return FrameTelemetrySnapshot(
            averageFrameTimeMillis = sum / filled,
            p95FrameTimeMillis = p95,
            droppedFrames = droppedFrames,
            totalFrames = totalFrames,
            activeProfile = activeProfile,
            degradationEvents = degradationEvents,
            recoveryEvents = recoveryEvents,
        )
    }

    /** Clears all counters and the ring buffer, e.g. when the wallpaper becomes visible again. */
    fun reset() {
        writeIndex = 0
        filled = 0
        droppedFrames = 0
        totalFrames = 0
        degradationEvents = 0
        recoveryEvents = 0
    }

    companion object {
        const val MIN_WINDOW_SIZE = 120
        const val TARGET_FRAME_MILLIS = 1000f / 30f

        /** A frame is considered dropped once it takes 1.5x the 30 FPS target (~50ms). */
        const val DROPPED_FRAME_THRESHOLD_MILLIS = TARGET_FRAME_MILLIS * 1.5f
        private const val P95_FRACTION = 0.95f
    }
}

/** Snapshot of [FrameTelemetry] for periodic debug logging. */
data class FrameTelemetrySnapshot(
    val averageFrameTimeMillis: Float,
    val p95FrameTimeMillis: Float,
    val droppedFrames: Int,
    val totalFrames: Int,
    val activeProfile: WallpaperQualityProfile,
    val degradationEvents: Int,
    val recoveryEvents: Int,
)

/**
 * Records wallpaper visibility lifecycle events with timestamps (ACT-009 section 9).
 *
 * Keeps only the last [maxEvents] events; [clockMillis] is injectable so tests don't depend on
 * the real wall clock.
 */
class WallpaperLifecycleTelemetry(
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    data class Event(val visible: Boolean, val timestampMillis: Long)

    private val events = ArrayDeque<Event>(maxEvents)

    /** Records a visibility change and returns the recorded event. */
    fun recordVisibilityChanged(visible: Boolean): Event {
        val event = Event(visible, clockMillis())
        if (events.size >= maxEvents) {
            events.removeFirst()
        }
        events.addLast(event)
        return event
    }

    /** Events in the order they were recorded, oldest first. */
    fun events(): List<Event> = events.toList()

    fun reset() {
        events.clear()
    }

    companion object {
        const val DEFAULT_MAX_EVENTS = 20
    }
}
