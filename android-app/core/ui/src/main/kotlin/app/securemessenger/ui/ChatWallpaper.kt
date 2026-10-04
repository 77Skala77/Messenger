package app.securemessenger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** One cell of the pattern is this many dp wide and high. */
private const val CELL_DP = 72f

/** How many different doodles the pattern has. */
internal const val DOODLE_KINDS = 6

/**
 * Which doodle, how turned and how far off center a cell gets: a fixed function of its position, so
 * the pattern is the same on every draw and never flickers. The numbers are a plain integer hash.
 */
internal fun doodleFor(column: Int, row: Int): Doodle {
    var hash = column * 73856093 xor row * 19349663
    hash = hash xor (hash ushr 13)
    hash *= 0x5bd1e995
    hash = hash xor (hash ushr 15)
    val positive = hash and 0x7fffffff
    return Doodle(
        kind = positive % DOODLE_KINDS,
        degrees = (positive / DOODLE_KINDS) % 360,
        shiftX = ((positive / 7) % 21 - 10) / 10f,
        shiftY = ((positive / 11) % 21 - 10) / 10f,
    )
}

internal class Doodle(val kind: Int, val degrees: Int, val shiftX: Float, val shiftY: Float)

/**
 * The chat background (owner decision, DEC-027): a calm base color with a light pattern of small
 * doodles drawn here, our own and not Telegram's images. Content is drawn on top of it.
 */
@Composable
fun ChatWallpaper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.messenger
    Box(modifier) {
        // The pattern takes the size of the content, so it also works inside a scrolling column.
        Canvas(Modifier.matchParentSize()) {
            drawRect(colors.wallpaperBase)
            val cell = CELL_DP.dp.toPx()
            val columns = (size.width / cell).toInt() + 1
            val rows = (size.height / cell).toInt() + 1
            for (row in 0..rows) {
                for (column in 0..columns) {
                    val doodle = doodleFor(column, row)
                    val center = Offset(
                        (column + 0.5f + doodle.shiftX * 0.18f) * cell,
                        (row + 0.5f + doodle.shiftY * 0.18f) * cell,
                    )
                    rotate(doodle.degrees.toFloat(), pivot = center) { drawDoodle(doodle.kind, center, cell * 0.2f, colors.wallpaperPattern) }
                }
            }
        }
        content()
    }
}

/** Six simple outline shapes: circle, plus, wave, triangle, diamond and arc. [half] is half their size. */
private fun DrawScope.drawDoodle(kind: Int, center: Offset, half: Float, color: Color) {
    val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
    when (kind) {
        0 -> drawCircle(color, radius = half * 0.8f, center = center, style = stroke)
        1 -> {
            drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y - half), Offset(center.x, center.y + half), stroke.width, StrokeCap.Round)
        }
        2 -> {
            val wave = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - half, center.y)
                quadraticTo(center.x - half / 2, center.y - half, center.x, center.y)
                quadraticTo(center.x + half / 2, center.y + half, center.x + half, center.y)
            }
            drawPath(wave, color, style = stroke)
        }
        3 -> {
            val triangle = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y - half)
                lineTo(center.x + half, center.y + half * 0.8f)
                lineTo(center.x - half, center.y + half * 0.8f)
                close()
            }
            drawPath(triangle, color, style = stroke)
        }
        4 -> {
            val diamond = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y - half)
                lineTo(center.x + half * 0.8f, center.y)
                lineTo(center.x, center.y + half)
                lineTo(center.x - half * 0.8f, center.y)
                close()
            }
            drawPath(diamond, color, style = stroke)
        }
        else -> drawArc(
            color,
            startAngle = 200f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = Offset(center.x - half, center.y - half),
            size = Size(half * 2, half * 2),
            style = stroke,
        )
    }
}
