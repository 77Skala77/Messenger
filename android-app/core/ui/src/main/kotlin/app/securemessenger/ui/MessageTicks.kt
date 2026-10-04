package app.securemessenger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.securemessenger.signalstore.V2MessageStatus

/**
 * What the ticks of an outgoing message say, and no more: a clock while it waits to be sent, one
 * tick once the delivery server took it, two ticks when the contact's app confirmed it with an
 * authenticated receipt. There is no "read" state, because the protocol has none.
 */
enum class TickKind { CLOCK, SENT, DELIVERED }

/** The tick for a message status; null where no tick belongs (received messages). */
fun tickKindFor(status: V2MessageStatus): TickKind? = when (status) {
    // A failed send is retried later, so it still waits; the status text says the same.
    V2MessageStatus.PENDING, V2MessageStatus.FAILED -> TickKind.CLOCK
    V2MessageStatus.SENT -> TickKind.SENT
    V2MessageStatus.DELIVERED -> TickKind.DELIVERED
    V2MessageStatus.RECEIVED -> null
}

// The ticks are drawn in a box 12 units high; a single tick is 12 wide, a double one 17.
private const val UNITS_HIGH = 12f
private const val SINGLE_WIDTH = 12f
private const val DOUBLE_WIDTH = 17f
private const val SECOND_TICK_SHIFT = 5f

/**
 * The tick or clock, drawn in [color]: Material's icon set would add thousands of icons for these
 * three shapes. The words for a screen reader are the status texts that were already there.
 */
@Composable
fun MessageTicks(kind: TickKind, color: Color, modifier: Modifier = Modifier) {
    val description = stringResource(
        when (kind) {
            TickKind.CLOCK -> R.string.status_pending
            TickKind.SENT -> R.string.status_sent
            TickKind.DELIVERED -> R.string.status_delivered
        },
    )
    val width = if (kind == TickKind.DELIVERED) DOUBLE_WIDTH else SINGLE_WIDTH
    Canvas(modifier.width(width.dp).height(UNITS_HIGH.dp).semantics { contentDescription = description }) {
        val unit = size.height / UNITS_HIGH
        val stroke = Stroke(width = 1.5f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun at(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun drawTick(shift: Float) {
            val start = at(1.5f + shift, 6.5f)
            val bend = at(4.8f + shift, 9.8f)
            val end = at(10.5f + shift, 2.7f)
            val tick = Path().apply {
                moveTo(start.x, start.y)
                lineTo(bend.x, bend.y)
                lineTo(end.x, end.y)
            }
            drawPath(tick, color, style = stroke)
        }
        when (kind) {
            TickKind.SENT -> drawTick(0f)
            TickKind.DELIVERED -> {
                drawTick(0f)
                drawTick(SECOND_TICK_SHIFT)
            }
            TickKind.CLOCK -> {
                drawCircle(color, radius = 5.2f * unit, center = at(6f, 6f), style = stroke)
                drawLine(color, at(6f, 6f), at(6f, 3.2f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, at(6f, 6f), at(8.1f, 7.2f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}
