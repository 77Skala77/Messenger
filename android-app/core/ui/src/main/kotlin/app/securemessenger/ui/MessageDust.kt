package app.securemessenger.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A deleted message falls apart into dust (Telegram's `ThanosEffect`, its vertex shader written anew
 * on the CPU, DEC-037): the bubble's picture is cut into a grid of grains as fine as Telegram's; from
 * left to right they start to drift in random directions, float up and fade, in about a second and a
 * half. Only the picture already on screen is used; nothing of the message is kept once the dust is gone.
 */
@Stable
internal class MessageDust {
    internal val clouds = mutableStateListOf<DustCloud>()

    /** Lets the picture of a deleted bubble at [topLeft] (relative to the dust layer) fall apart. */
    fun scatter(image: ImageBitmap, topLeft: Offset, density: Float) {
        clouds += DustCloud(image, topLeft, density)
    }
}

/**
 * The pictures of the bubbles on screen (each bubble records itself into its own layer as it draws)
 * and where they are, so a deleted one can fall apart where it stood (DEC-037).
 */
@Stable
internal class BubblePictures {
    private val layers = HashMap<String, GraphicsLayer>()
    private val places = HashMap<String, Offset>()
    private val sizes = HashMap<String, androidx.compose.ui.unit.IntSize>()

    fun register(messageId: String, layer: GraphicsLayer) {
        layers[messageId] = layer
    }

    fun forget(messageId: String, layer: GraphicsLayer) {
        if (layers[messageId] === layer) {
            layers.remove(messageId)
            places.remove(messageId)
            sizes.remove(messageId)
        }
    }

    fun place(messageId: String, topLeftInRoot: Offset, size: androidx.compose.ui.unit.IntSize = androidx.compose.ui.unit.IntSize.Zero) {
        places[messageId] = topLeftInRoot
        sizes[messageId] = size
    }

    /** Where the bubble is on the screen and how big, or null if it is not on screen. */
    fun bounds(messageId: String): androidx.compose.ui.geometry.Rect? {
        val place = places[messageId] ?: return null
        val size = sizes[messageId] ?: return null
        return androidx.compose.ui.geometry.Rect(place, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
    }

    /** The bubble's picture and its place on the screen, or null if it is not on screen. */
    suspend fun capture(messageId: String): Pair<ImageBitmap, Offset>? {
        val layer = layers[messageId] ?: return null
        val place = places[messageId] ?: return null
        if (layer.size.width <= 0 || layer.size.height <= 0) return null
        return runCatching { layer.toImageBitmap() }.getOrNull()?.let { it to place }
    }
}

/**
 * One bubble's dust: its picture cut into squares of [cell] pixels, as small as Telegram's grains
 * (`ThanosEffect`: 0.4 dp, but no more than a set number per message), one grain per square that is
 * not clear, in the color of the square's middle.
 */
internal class DustCloud(image: ImageBitmap, topLeft: Offset, density: Float) {
    val cell: Int
    val count: Int
    private val dp = density
    private val random = Random(System.nanoTime())

    /** Per grain: x, y (on screen), velocity x, y, time left, its place left to right, its color. */
    val x: FloatArray
    val y: FloatArray
    private val vx: FloatArray
    private val vy: FloatArray
    val life: FloatArray
    private val u: FloatArray
    val color: IntArray
    var time = 0f
        private set

    init {
        val width = image.width
        val height = image.height
        cell = dustCellPx(width, height, density)
        val pixels = IntArray(width * height)
        readPixels(image, pixels)
        val columns = max(1, width / cell)
        val rows = max(1, height / cell)
        val kept = ArrayList<Int>(columns * rows)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                // The clear corners around a bubble's round edge make no grains.
                if ((pixels[middleOf(column, row, width, height)] ushr 24) > 8) kept += row * columns + column
            }
        }
        count = kept.size
        x = FloatArray(count)
        y = FloatArray(count)
        vx = FloatArray(count)
        vy = FloatArray(count)
        life = FloatArray(count)
        u = FloatArray(count)
        color = IntArray(count)
        kept.forEachIndexed { index, square ->
            val column = square % columns
            val row = square / columns
            color[index] = pixels[middleOf(column, row, width, height)]
            u[index] = column.toFloat() / columns
            x[index] = topLeft.x + column * cell
            y[index] = topLeft.y + row * cell
            val direction = random.nextFloat() * (2f * PI.toFloat())
            val speed = (0.1f + random.nextFloat() * 0.1f) * DustRules.SPEED * dp
            vx[index] = cos(direction) * speed
            vy[index] = sin(direction) * speed
            life[index] = (0.7f + random.nextFloat() * 0.8f) / 1.15f
        }
        pixels.fill(0)
    }

    private fun middleOf(column: Int, row: Int, width: Int, height: Int): Int =
        min(height - 1, row * cell + cell / 2) * width + min(width - 1, column * cell + cell / 2)

    /** Moves the dust on by [delta] seconds; false once every grain is gone. */
    fun step(delta: Float): Boolean {
        time += delta
        val effect = min(0.35f, time) / 0.35f
        var alive = false
        for (index in 0 until count) {
            // Grains leave from left to right (Telegram's uvOffset sweep).
            val started = (min(0.2f, max(0f, 0.1f + time - u[index] * DustRules.SWEEP)) / 0.2f)
            if (started > 0f) {
                x[index] += vx[index] * delta * started
                y[index] += vy[index] * delta * started
                vx[index] += DustRules.PUSH * (if (vx[index] > 0f) 1f else -1f) * (1f - effect) * delta * dp * started
                vy[index] += -DustRules.LIFT * delta * dp * started
                life[index] = max(0f, life[index] - 1.2f * delta * started)
            }
            if (life[index] > 0f) alive = true
        }
        return alive
    }

    fun alpha(index: Int): Float = min(0.55f, life[index]) / 0.55f

    /** Forgets the grains' colors: nothing of the message stays once the dust is gone. */
    fun wipe() = color.fill(0)

    private companion object {
        /** The picture of a layer may live on the graphics card; a copy in memory is read once. */
        fun readPixels(image: ImageBitmap, into: IntArray) {
            val bitmap = image.asAndroidBitmap()
            val readable = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
            try {
                readable.getPixels(into, 0, image.width, 0, 0, image.width, image.height)
            } finally {
                if (readable !== bitmap) readable.recycle()
            }
        }
    }
}

/**
 * The side of a grain in pixels: Telegram's 0.4 dp (at least one pixel), larger only where a big
 * bubble would need more than [DustRules.MAX_GRAINS] of them (Telegram caps them by phone class too).
 */
internal fun dustCellPx(widthPx: Int, heightPx: Int, density: Float): Int {
    val ideal = max(1f, DustRules.GRAIN_DP * density)
    val area = widthPx.coerceAtLeast(1).toFloat() * heightPx.coerceAtLeast(1).toFloat()
    val capped = sqrt(area / DustRules.MAX_GRAINS)
    return ceil(max(ideal, capped)).toInt().coerceAtLeast(1)
}

/** Telegram's numbers (`thanos_vertex.glsl`, `ThanosEffect`). */
internal object DustRules {
    const val GRAIN_DP = 0.4f

    /** Telegram allows 30,000 to 120,000 by phone class; drawn here without a GPU program of our own, fewer. */
    const val MAX_GRAINS = 24_000
    const val SPEED = 260f
    const val PUSH = 19f
    const val LIFT = 65f
    const val SWEEP = 0.6f

    /** Grains drawn per call: four corners each, and Android numbers the corners with 16 bits. */
    const val BATCH = 16_000
}

/** Draws [dust] over whatever is below it and moves it on every frame until it has settled. */
@Composable
internal fun MessageDustLayer(dust: MessageDust, modifier: Modifier = Modifier) {
    if (dust.clouds.isEmpty()) return
    var frame by remember { mutableLongStateOf(0L) }
    val batch = remember { DustBatch() }
    LaunchedEffect(dust.clouds.size) {
        var last = 0L
        while (dust.clouds.isNotEmpty()) {
            withFrameNanos { now ->
                val delta = if (last == 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                dust.clouds.toList().forEach { cloud ->
                    if (!cloud.step(delta)) {
                        cloud.wipe()
                        dust.clouds.remove(cloud)
                    }
                }
                frame = now
            }
        }
        batch.wipe()
    }
    Canvas(modifier) {
        // Read so the canvas draws again on every step.
        if (frame < 0L) return@Canvas
        drawIntoCanvas { canvas -> dust.clouds.forEach { cloud -> batch.draw(canvas.nativeCanvas, cloud) } }
    }
}

/**
 * Draws grains as small squares, many per call (`Canvas.drawVertices`), each corner in the grain's
 * color: a grain is a pixel or two, so one call per grain would be far too slow.
 */
private class DustBatch {
    private val vertices = FloatArray(DustRules.BATCH * 8)
    private val colors = IntArray(DustRules.BATCH * 4)
    private val indices = ShortArray(DustRules.BATCH * 6).also { index ->
        for (grain in 0 until DustRules.BATCH) {
            val corner = grain * 4
            val at = grain * 6
            index[at] = corner.toShort()
            index[at + 1] = (corner + 1).toShort()
            index[at + 2] = (corner + 2).toShort()
            index[at + 3] = (corner + 2).toShort()
            index[at + 4] = (corner + 1).toShort()
            index[at + 5] = (corner + 3).toShort()
        }
    }
    private val paint = android.graphics.Paint()

    fun draw(canvas: android.graphics.Canvas, cloud: DustCloud) {
        val size = cloud.cell.toFloat()
        var filled = 0
        for (index in 0 until cloud.count) {
            val alpha = cloud.alpha(index)
            if (alpha <= 0f) continue
            val base = cloud.color[index]
            val argb = ((((base ushr 24) * alpha).toInt() and 0xFF) shl 24) or (base and 0xFFFFFF)
            val left = cloud.x[index]
            val top = cloud.y[index]
            val v = filled * 8
            vertices[v] = left
            vertices[v + 1] = top
            vertices[v + 2] = left + size
            vertices[v + 3] = top
            vertices[v + 4] = left
            vertices[v + 5] = top + size
            vertices[v + 6] = left + size
            vertices[v + 7] = top + size
            val c = filled * 4
            colors[c] = argb
            colors[c + 1] = argb
            colors[c + 2] = argb
            colors[c + 3] = argb
            filled++
            if (filled == DustRules.BATCH) {
                flush(canvas, filled)
                filled = 0
            }
        }
        if (filled > 0) flush(canvas, filled)
    }

    private fun flush(canvas: android.graphics.Canvas, grains: Int) {
        canvas.drawVertices(
            android.graphics.Canvas.VertexMode.TRIANGLES, grains * 8, vertices, 0, null, 0, colors, 0, indices, 0, grains * 6, paint,
        )
    }

    fun wipe() = colors.fill(0)
}
