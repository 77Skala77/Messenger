package app.securemessenger.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Telegram's soft four-color gradient (`MotionBackgroundDrawable`, the native `generateGradient` in
 * `jni/image.cpp`; DEC-038): the four colors sit at four of eight points on a ring, the picture is
 * twisted a little around its middle, and each pixel mixes the colors by how near they are, weighed by
 * (0.9 - distance) to the fourth power. It is computed on a 60 by 80 grid and drawn stretched, which
 * keeps it smooth. A sent message moves the colors one point on (`switchToNextPosition`).
 */
internal object WallpaperGradient {
    const val WIDTH = 60
    const val HEIGHT = 80
    const val PHASES = 8

    /** The eight points of the ring, x and y in turn. */
    private val RING = floatArrayOf(
        0.80f, 0.10f,
        0.60f, 0.20f,
        0.35f, 0.25f,
        0.25f, 0.60f,
        0.20f, 0.90f,
        0.40f, 0.80f,
        0.65f, 0.75f,
        0.75f, 0.40f,
    )

    /** Where the four colors stand in [phase]: every second point from it on, upside down. */
    fun positions(phase: Int): FloatArray {
        val result = FloatArray(8)
        for (i in 0 until 4) {
            val point = Math.floorMod(phase + i * 2, PHASES)
            result[i * 2] = RING[point * 2]
            result[i * 2 + 1] = 1f - RING[point * 2 + 1]
        }
        return result
    }

    /**
     * The gradient's pixels (ARGB, row by row). At [progress] 1 the colors stand in [phase]; below 1
     * they are still on their way from the phase after it, where they stood before the last move. A
     * fourth color of 0 means three colors, as in Telegram.
     */
    fun pixels(colors: IntArray, phase: Int, progress: Float, width: Int = WIDTH, height: Int = HEIGHT): IntArray {
        require(colors.size == 4) { "Four colors" }
        val from = positions(phase + 1)
        val to = positions(phase)
        val count = if (colors[3] == 0) 3 else 4
        val pointX = FloatArray(count) { from[it * 2] + (to[it * 2] - from[it * 2]) * progress }
        val pointY = FloatArray(count) { from[it * 2 + 1] + (to[it * 2 + 1] - from[it * 2 + 1]) * progress }
        val red = FloatArray(count) { ((colors[it] shr 16) and 0xFF) / 255f }
        val green = FloatArray(count) { ((colors[it] shr 8) and 0xFF) / 255f }
        val blue = FloatArray(count) { (colors[it] and 0xFF) / 255f }
        val result = IntArray(width * height)
        for (y in 0 until height) {
            val centerY = y.toFloat() / height - 0.5f
            for (x in 0 until width) {
                val centerX = x.toFloat() / width - 0.5f
                val centerDistance = sqrt(centerX * centerX + centerY * centerY)
                val swirl = 0.35f * centerDistance
                val theta = swirl * swirl * 0.8f * 8f
                val sinTheta = sin(theta)
                val cosTheta = cos(theta)
                val pixelX = (0.5f + centerX * cosTheta - centerY * sinTheta).coerceIn(0f, 1f)
                val pixelY = (0.5f + centerX * sinTheta + centerY * cosTheta).coerceIn(0f, 1f)
                var sum = 0f
                var r = 0f
                var g = 0f
                var b = 0f
                for (i in 0 until count) {
                    val dx = pixelX - pointX[i]
                    val dy = pixelY - pointY[i]
                    var weight = max(0f, 0.9f - sqrt(dx * dx + dy * dy))
                    weight *= weight
                    weight *= weight
                    sum += weight
                    r += weight * red[i]
                    g += weight * green[i]
                    b += weight * blue[i]
                }
                result[y * width + x] = if (sum <= 0f) {
                    colors[0] or (0xFF shl 24)
                } else {
                    (0xFF shl 24) or (channel(r / sum) shl 16) or (channel(g / sum) shl 8) or channel(b / sum)
                }
            }
        }
        return result
    }

    private fun channel(value: Float): Int = (value * 255f).toInt().coerceIn(0, 255)

    fun image(colors: List<Color>, phase: Int, progress: Float): ImageBitmap {
        val bitmap = ImageBitmap(WIDTH, HEIGHT, ImageBitmapConfig.Argb8888)
        val argb = IntArray(4) { colors[it].toArgb() }
        bitmap.asAndroidBitmap().setPixels(pixels(argb, phase, progress), 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        return bitmap
    }
}

/**
 * Where the wallpaper's colors stand (one for the whole app, like Telegram's wallpaper): each message
 * of ours moves them one point on along the ring in 500 ms (`MotionBackgroundDrawable`, curve 0.33, 0,
 * 0, 1). A move that comes while one runs is dropped, as in Telegram.
 */
internal object WallpaperMotion {
    var phase by mutableIntStateOf(0)
        private set
    private val move = Animatable(1f)
    val progress: Float get() = move.value

    suspend fun next() {
        if (move.isRunning) return
        phase = Math.floorMod(phase - 1, WallpaperGradient.PHASES)
        move.snapTo(0f)
        move.animateTo(1f, tween(MOVE_MILLIS, easing = MoveEasing))
    }

    private const val MOVE_MILLIS = 500
    private val MoveEasing = CubicBezierEasing(0.33f, 0f, 0f, 1f)
}

/**
 * Telegram's doodle pattern (`res/raw/default_pattern.svg`, a 1440 by 2960 drawing of 745 filled
 * shapes, copied unchanged as `tg_default_pattern.svg`): read once, drawn once into an alpha mask as
 * wide as the phone's shorter side (Telegram's `SvgHelper`, scaled by width), and kept for the life
 * of the app. Reading and drawing take a moment, so they run in the background; until then the
 * gradient shows alone.
 */
internal object WallpaperPattern {
    private val lock = Mutex()

    @Volatile
    var mask: ImageBitmap? = null
        private set

    /** The drawing's size and the path data of its shapes, in order. */
    class Drawing(val width: Float, val height: Float, val paths: List<String>)

    private val VIEW_BOX = Regex("""viewBox="\s*[-\d.]+\s+[-\d.]+\s+([\d.]+)\s+([\d.]+)\s*"""")
    private val PATH_DATA = Regex("""\bd="([^"]*)"""")

    fun parse(svg: String): Drawing {
        val box = VIEW_BOX.find(svg) ?: throw IllegalArgumentException("No viewBox")
        return Drawing(
            width = box.groupValues[1].toFloat(),
            height = box.groupValues[2].toFloat(),
            paths = PATH_DATA.findAll(svg).map { it.groupValues[1] }.toList(),
        )
    }

    /** The mask, made on first use; null if the drawing cannot be read (then the gradient shows alone). */
    suspend fun load(context: Context): ImageBitmap? {
        mask?.let { return it }
        return lock.withLock {
            mask ?: withContext(Dispatchers.Default) {
                try {
                    val svg = context.resources.openRawResource(R.raw.tg_default_pattern).bufferedReader().use { it.readText() }
                    val metrics = context.resources.displayMetrics
                    render(parse(svg), min(metrics.widthPixels, metrics.heightPixels))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }?.also { mask = it }
        }
    }

    private fun render(drawing: Drawing, width: Int): ImageBitmap {
        val scale = width / drawing.width
        val height = ceil(drawing.height * scale).toInt()
        val bitmap = ImageBitmap(width, height, ImageBitmapConfig.Alpha8)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val paint = Paint().apply { isAntiAlias = true }
        val parser = PathParser()
        val path = Path()
        for (data in drawing.paths) {
            path.reset()
            parser.clear()
            parser.parsePathString(data).toPath(path)
            canvas.drawPath(path, paint)
        }
        return bitmap
    }
}

/**
 * The chat background (DEC-038, the owner wanted Telegram's own): Telegram's default wallpaper, the
 * soft gradient of [MessengerColors.wallpaperColors] with the doodle pattern laid over it in
 * [MessengerColors.wallpaperPatternColor] with "soft light" at [MessengerColors.wallpaperPatternAlpha]
 * (34 percent black on the light green, 38 percent white on the dark one). Both are stretched to cover
 * the box and centered, like `MotionBackgroundDrawable`. Content is drawn on top of it.
 */
@Composable
fun ChatWallpaper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.messenger
    val context = LocalContext.current
    var mask by remember { mutableStateOf(WallpaperPattern.mask) }
    LaunchedEffect(Unit) { if (mask == null) mask = WallpaperPattern.load(context) }
    val phase = WallpaperMotion.phase
    val progress = WallpaperMotion.progress
    val gradient = remember(colors.wallpaperColors, phase, progress) {
        WallpaperGradient.image(colors.wallpaperColors, phase, progress)
    }
    Box(modifier.drawBehind { drawWallpaper(gradient, mask, colors.wallpaperPatternColor, colors.wallpaperPatternAlpha) }) {
        content()
    }
}

/** The gradient, then the pattern blended over it; both cover the area and are centered. */
internal fun DrawScope.drawWallpaper(gradient: ImageBitmap, mask: ImageBitmap?, patternColor: Color, patternAlpha: Float) {
    drawCovering(gradient)
    if (mask != null && patternAlpha > 0f) {
        drawCovering(
            mask,
            alpha = patternAlpha,
            colorFilter = ColorFilter.tint(patternColor),
            blendMode = BlendMode.Softlight,
        )
    }
}

private fun DrawScope.drawCovering(
    image: ImageBitmap,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    val scale = max(size.width / image.width, size.height / image.height)
    val width = (image.width * scale).roundToInt()
    val height = (image.height * scale).roundToInt()
    drawImage(
        image = image,
        dstOffset = IntOffset(((size.width - width) / 2f).roundToInt(), ((size.height - height) / 2f).roundToInt()),
        dstSize = IntSize(width, height),
        alpha = alpha,
        colorFilter = colorFilter,
        blendMode = blendMode,
        filterQuality = FilterQuality.Low,
    )
}

/** The size a covering image is drawn at; for the bubbles' gradient too. */
internal fun coverScale(area: Size, imageWidth: Int, imageHeight: Int): Float =
    max(area.width / imageWidth, area.height / imageHeight)
