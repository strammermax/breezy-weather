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

package org.breezyweather.wallpaper.photo

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Automatic "sky eraser": runs an on-device DeepLab (MobileNetV3, Cityscapes) segmentation model
 * and makes the sky region of a photo transparent, so the live wallpaper can render its own
 * weather (sky/clouds/sun/rain/snow) through the hole while the photo's foreground (buildings,
 * ground) stays on top — like YoWindow's sky eraser, but fully automatic.
 *
 * Model: input `ImageTensor` [1,513,513,3] uint8 (RGB), output `SemanticPredictions`
 * [1,513,513] float32 = the per-pixel Cityscapes train-id. Sky is class [SKY_CLASS].
 */
class SkySegmenter(context: Context) {

    private val appContext = context.applicationContext
    private val interpreter: Interpreter? by lazy { loadInterpreter() }

    fun isAvailable(): Boolean = interpreter != null

    /**
     * Returns a copy of [source] (ARGB_8888) with the detected sky pixels made fully transparent,
     * or null when the model is unavailable, errored, or detected essentially no sky (so the
     * caller keeps the original opaque photo).
     */
    fun eraseSky(source: Bitmap): Bitmap? {
        val interp = interpreter ?: return null
        return try {
            val scaled = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true)
            val scaledPixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            scaled.getPixels(scaledPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            val input = toFloatInput(scaled)
            if (scaled !== source) scaled.recycle()

            val output = Array(1) { Array(INPUT_SIZE) { FloatArray(INPUT_SIZE) } }
            interp.run(input, output)
            val labels = output[0]

            // How much of the frame is sky, and how much of the TOP band is sky? Real sky sits at
            // the top of the frame; this lets us reject e.g. aerial photos where lake water is
            // misclassified as sky (it would otherwise be scattered/low, not a top band).
            var skyCount = 0
            var topBandSky = 0
            var bottomBandSky = 0
            var skyColoured = 0
            var sumY = 0L
            val topRows = (INPUT_SIZE * TOP_BAND_FRACTION).toInt().coerceAtLeast(1)
            val bottomStart = INPUT_SIZE - topRows
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    if (labels[y][x].toInt() == SKY_CLASS) {
                        skyCount++
                        sumY += y
                        if (y < topRows) topBandSky++
                        if (y >= bottomStart) bottomBandSky++
                        // Real sky is blue or bright/neutral (overcast). Reject regions that are
                        // clearly not sky-coloured: red banners, green fields (aerials), etc.
                        val c = scaledPixels[y * INPUT_SIZE + x]
                        val r = c shr 16 and 0xFF
                        val g = c shr 8 and 0xFF
                        val b = c and 0xFF
                        val bright = (r + g + b) / 3 >= 150
                        if (b >= r - 12 && (bright || b >= 80)) skyColoured++
                    }
                }
            }
            val fraction = skyCount.toDouble() / (INPUT_SIZE * INPUT_SIZE)
            val topBandFraction = topBandSky.toDouble() / (topRows * INPUT_SIZE)
            val bottomBandFraction = bottomBandSky.toDouble() / (topRows * INPUT_SIZE)
            // Vertical centre of mass of the sky, 0 = top .. 1 = bottom.
            val centroidY = if (skyCount > 0) (sumY.toDouble() / skyCount) / INPUT_SIZE else 1.0
            android.util.Log.i(
                "LWWPhoto",
                "sky fraction=${"%.3f".format(fraction)} top=${"%.2f".format(topBandFraction)} " +
                    "bottom=${"%.2f".format(bottomBandFraction)} centroidY=${"%.2f".format(centroidY)}"
            )
            // Require enough sky, concentrated near the top (low centroid + some top band, little
            // bottom). This keeps ground-level photos with real sky and rejects e.g. aerial photos
            // where lake water/haze is misread as a sky blob in the middle of the frame.
            val skyColouredFraction = if (skyCount > 0) skyColoured.toDouble() / skyCount else 0.0
            if (fraction < MIN_SKY_FRACTION) return null
            if (topBandFraction < MIN_TOP_BAND_SKY) return null
            if (centroidY > MAX_SKY_CENTROID_Y) return null
            if (bottomBandFraction > MAX_BOTTOM_BAND_SKY) return null
            if (skyColouredFraction < MIN_SKY_COLOURED) return null

            applyMask(source, labels)
        } catch (e: Throwable) {
            android.util.Log.w("LWWPhoto", "sky segmentation failed", e)
            null
        }
    }

    /** Packs [scaled] into the model's float32 RGB input, normalized to [-1, 1]. */
    private fun toFloatInput(scaled: Bitmap): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 127.5f) // R
            buffer.putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 127.5f) // G
            buffer.putFloat(((pixel and 0xFF) - 127.5f) / 127.5f) // B
        }
        buffer.rewind()
        return buffer
    }

    /** Copies [source] and clears the alpha of every pixel whose [labels] cell is sky. */
    private fun applyMask(source: Bitmap, labels: Array<FloatArray>): Bitmap {
        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        // A copy of an opaque (JPEG) bitmap keeps hasAlpha=false, which makes Android ignore the
        // alpha channel and save an opaque PNG. Enable alpha so the erased sky stays transparent.
        result.setHasAlpha(true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)
        for (y in 0 until height) {
            val my = (y * INPUT_SIZE / height).coerceIn(0, INPUT_SIZE - 1)
            val row = labels[my]
            val base = y * width
            for (x in 0 until width) {
                val mx = (x * INPUT_SIZE / width).coerceIn(0, INPUT_SIZE - 1)
                if (row[mx].toInt() == SKY_CLASS) {
                    pixels[base + x] = pixels[base + x] and 0x00FFFFFF // alpha = 0
                }
            }
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun loadInterpreter(): Interpreter? = try {
        val bytes = appContext.assets.open(MODEL_ASSET).use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        model.put(bytes)
        model.rewind()
        Interpreter(model, Interpreter.Options().apply { numThreads = 2 })
    } catch (e: Throwable) {
        android.util.Log.w("LWWPhoto", "sky model load failed", e)
        null
    }

    @Suppress("unused")
    fun close() {
        interpreter?.close()
    }

    companion object {
        private const val MODEL_ASSET = "sky_segmentation_ade20k.tflite"
        private const val INPUT_SIZE = 512

        /** ADE20K (1-indexed) class for "sky": 0=bg, 1=wall, 2=building, 3=sky. */
        private const val SKY_CLASS = 3

        // The model measures sky conservatively (~half of what a person perceives), so these
        // measured thresholds correspond to roughly "25%+ visible sky, located at the top".

        /** A photo needs at least this measured fraction of sky to be usable as a background.
         *  (The shape gates below — top band, centroid, bottom — do the real quality filtering.) */
        private const val MIN_SKY_FRACTION = 0.08

        /** Fraction of the image height treated as the "top band" for the sky-position check. */
        private const val TOP_BAND_FRACTION = 0.15

        /** The top band must be at least this much sky, so the sky is really at the top. */
        private const val MIN_TOP_BAND_SKY = 0.30

        /** The sky's vertical centre of mass must be in the upper part of the frame (0=top). */
        private const val MAX_SKY_CENTROID_Y = 0.40

        /** The bottom band must have almost no sky (ground photos have ground at the bottom). */
        private const val MAX_BOTTOM_BAND_SKY = 0.15

        /** Most of the "sky" must actually be sky-coloured (blue or bright/neutral), which rejects
         *  e.g. red banners, green fields and coloured interiors misread as sky. */
        private const val MIN_SKY_COLOURED = 0.6
    }
}
