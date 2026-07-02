package com.wolkentypes.app.clouds

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View

/**
 * Compose-vrije vervanger van de vorige `CloudField`-composable: een dunne `View`-wrapper rond
 * [CloudEngineRenderer] (dezelfde model-/positiewiskunde en Canvas/AGSL-tekenlogica), met een
 * eigen `Choreographer`-gamelus voor standalone gebruik (zoals in de wolkentypes-app). Wanneer
 * deze renderer in een bestaande draw-loop hangt (zoals een live wallpaper's `WallpaperService`,
 * die al zijn eigen Canvas en timing heeft), gebruik [CloudEngineRenderer] rechtstreeks in plaats
 * van deze View.
 */
class CloudSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val renderer = CloudEngineRenderer(context)

    var profile: CloudProfile
        get() = renderer.profile
        set(value) {
            renderer.profile = value
        }
    var weatherId: String?
        get() = renderer.weatherId
        set(value) {
            renderer.weatherId = value
        }
    var windSpeedMultiplier: Float
        get() = renderer.windSpeedMultiplier
        set(value) {
            renderer.windSpeedMultiplier = value
        }
    var densityMultiplier: Float
        get() = renderer.densityMultiplier
        set(value) {
            renderer.densityMultiplier = value
        }
    var layerDepthMultiplier: Float
        get() = renderer.layerDepthMultiplier
        set(value) {
            renderer.layerDepthMultiplier = value
        }
    var randomSeed: Long
        get() = renderer.randomSeed
        set(value) {
            renderer.randomSeed = value
        }

    private var startNanos = -1L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (startNanos < 0L) startNanos = frameTimeNanos
            renderer.update((frameTimeNanos - startNanos) / 1_000_000_000f)
            invalidate()
            if (isAttachedToWindow) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(canvas)
    }
}
