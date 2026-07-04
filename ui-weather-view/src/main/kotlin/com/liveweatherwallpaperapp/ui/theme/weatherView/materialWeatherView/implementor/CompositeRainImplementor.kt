/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package com.liveweatherwallpaperapp.ui.theme.weatherView.materialWeatherView.implementor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.Size
import com.liveweatherwallpaperapp.ui.theme.weatherView.R
import com.liveweatherwallpaperapp.ui.theme.weatherView.materialWeatherView.MaterialWeatherView.WeatherAnimationImplementor

/**
 * Composite Rain implementor combining:
 * 1. Falling rain streaks (from RainImplementor)
 * 2. Rain-on-glass shader effect (overlay)
 */
class CompositeRainImplementor(
    private val context: Context,
    @Size(2) canvasSizes: IntArray,
    animate: Boolean,
    @RainImplementor.TypeRule type: Int,
    daylight: Boolean,
) : WeatherAnimationImplementor() {
    
    // Base rain implementation (falling streaks)
    private val rainImplementor: RainImplementor = RainImplementor(
        canvasSizes,
        animate,
        type,
        daylight
    )
    
    // Glass effect shader (Android 13+)
    private var shader: RuntimeShader? = null
    private val shaderPaint = Paint()
    private val startTime = System.currentTimeMillis()
    private val supportsShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    
    init {
        if (supportsShader && animate) {
            initShader()
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initShader() {
        try {
            val shaderCode = context.resources
                .openRawResource(R.raw.rain_glass_shader)
                .bufferedReader()
                .use { it.readText() }
            
            shader = RuntimeShader(shaderCode)
            shaderPaint.shader = shader
        } catch (e: Exception) {
            // Shader loading failed, continue without glass effect
            shader = null
        }
    }
    
    override fun updateData(
        @Size(2) canvasSizes: IntArray,
        interval: Long,
        rotation2D: Float,
        rotation3D: Float
    ) {
        // Update base rain animation
        rainImplementor.updateData(canvasSizes, interval, rotation2D, rotation3D)
    }
    
    override fun draw(
        @Size(2) canvasSizes: IntArray,
        canvas: Canvas,
        scrollRate: Float,
        rotation2D: Float,
        rotation3D: Float
    ) {
        // Keep the established rain/sleet/thunderstorm animation and add the glass
        // droplets as a translucent top layer when RuntimeShader is available.
        rainImplementor.draw(canvasSizes, canvas, scrollRate, rotation2D, rotation3D)
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawGlassEffect(canvasSizes, canvas, scrollRate)
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawGlassEffect(
        @Size(2) canvasSizes: IntArray,
        canvas: Canvas,
        scrollRate: Float
    ) {
        shader?.let { s ->
            if (scrollRate < 1) {
                // Update shader uniforms
                val currentTime = (System.currentTimeMillis() - startTime) / 1000f
                s.setFloatUniform("resolution", canvasSizes[0].toFloat(), canvasSizes[1].toFloat())
                s.setFloatUniform("time", currentTime)
                s.setFloatUniform("rainAmount", 0.7f) // Medium intensity
                
                // Apply scroll fade
                shaderPaint.alpha = ((1 - scrollRate) * 255).toInt()
                
                // Draw glass effect overlay
                canvas.drawRect(
                    0f,
                    0f,
                    canvasSizes[0].toFloat(),
                    canvasSizes[1].toFloat(),
                    shaderPaint
                )
            }
        }
    }
}
