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
            val input = toUint8Input(scaled)
            if (scaled !== source) scaled.recycle()

            val output = Array(1) { Array(INPUT_SIZE) { FloatArray(INPUT_SIZE) } }
            interp.run(input, output)
            val labels = output[0]

            // How much of the frame is sky? Skip when there's basically none.
            var skyCount = 0
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    if (labels[y][x].toInt() == SKY_CLASS) skyCount++
                }
            }
            val fraction = skyCount.toDouble() / (INPUT_SIZE * INPUT_SIZE)
            android.util.Log.i("LWWPhoto", "sky fraction=${"%.3f".format(fraction)} (min $MIN_SKY_FRACTION)")
            // Require a meaningful amount of sky; otherwise the photo can't show the weather, so
            // the caller skips it and tries another image.
            if (fraction < MIN_SKY_FRACTION) return null

            applyMask(source, labels)
        } catch (e: Throwable) {
            android.util.Log.w("LWWPhoto", "sky segmentation failed", e)
            null
        }
    }

    /** Packs [scaled] (513x513) into the model's uint8 RGB input buffer. */
    private fun toUint8Input(scaled: Bitmap): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.put((pixel shr 16 and 0xFF).toByte()) // R
            buffer.put((pixel shr 8 and 0xFF).toByte()) // G
            buffer.put((pixel and 0xFF).toByte()) // B
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
        private const val MODEL_ASSET = "sky_segmentation_cityscapes.tflite"
        private const val INPUT_SIZE = 513

        /** Cityscapes train-id for "sky". */
        private const val SKY_CLASS = 10

        /** A photo needs at least this fraction of sky to be usable as a wallpaper background. */
        private const val MIN_SKY_FRACTION = 0.25
    }
}
