package app.securemessenger.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The corner radii of a message bubble in dp (`docs/PHASE6_PORTING_NOTES.md`, 2.1). A corner that
 * touches the neighbouring bubble of the same series is tight; the sender's bottom corner of the
 * last bubble of a series is not a corner but the tail.
 */
class BubbleGeometry(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
    /** The tail sits on the sender's side: right for outgoing messages, left for incoming ones. */
    val tail: Boolean,
)

fun bubbleGeometry(outgoing: Boolean, tail: Boolean, nearTop: Boolean, nearBottom: Boolean): BubbleGeometry {
    val round = MessengerDimens.BubbleRadius
    val tight = MessengerDimens.BubbleNearRadius
    val senderTop = if (nearTop) tight else round
    val senderBottom = when {
        tail -> 0f
        nearBottom -> tight
        else -> round
    }
    return if (outgoing) {
        BubbleGeometry(topLeft = round, topRight = senderTop, bottomRight = senderBottom, bottomLeft = round, tail = tail)
    } else {
        BubbleGeometry(topLeft = senderTop, topRight = round, bottomRight = round, bottomLeft = senderBottom, tail = tail)
    }
}

/**
 * A message bubble's outline. Its bounds include the tail strip on the sender's side
 * ([MessengerDimens.BubbleTailStrip]) for every bubble of a series, so all of them share one edge;
 * only the last one draws the tail into the strip.
 */
class BubbleShape(
    private val outgoing: Boolean,
    private val tail: Boolean,
    private val nearTop: Boolean,
    private val nearBottom: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val dp = density.density
        val geometry = bubbleGeometry(outgoing, tail, nearTop, nearBottom)
        val strip = MessengerDimens.BubbleTailStrip * dp
        val left = if (outgoing) 0f else strip
        val right = if (outgoing) size.width - strip else size.width
        val top = 0f
        val bottom = size.height
        // Radii never exceed what a small bubble can hold.
        val limit = minOf(size.height, right - left) / 2f
        fun radius(value: Float) = minOf(value * dp, limit)
        val topLeft = radius(geometry.topLeft)
        val topRight = radius(geometry.topRight)
        val bottomRight = radius(geometry.bottomRight)
        val bottomLeft = radius(geometry.bottomLeft)
        val tipReach = (MessengerDimens.BubbleTailStrip - MessengerDimens.BubbleTailTipInset) * dp
        val join = maxOf(0f, minOf(MessengerDimens.BubbleTailJoin * dp, size.height - topRight - topLeft))

        val path = Path().apply {
            moveTo(left + topLeft, top)
            lineTo(right - topRight, top)
            if (topRight > 0f) arcTo(Rect(right - 2 * topRight, top, right, top + 2 * topRight), 270f, 90f, false)
            if (outgoing && geometry.tail) {
                lineTo(right, bottom - join)
                // The side runs down and flares out to the tip, which ends in the bottom edge.
                cubicTo(right, bottom - 4f * dp, right + 1f * dp, bottom - 1.5f * dp, right + tipReach, bottom)
            } else {
                lineTo(right, bottom - bottomRight)
                if (bottomRight > 0f) arcTo(Rect(right - 2 * bottomRight, bottom - 2 * bottomRight, right, bottom), 0f, 90f, false)
            }
            if (!outgoing && geometry.tail) {
                lineTo(left - tipReach, bottom)
                cubicTo(left - 1f * dp, bottom - 1.5f * dp, left, bottom - 4f * dp, left, bottom - join)
            } else {
                lineTo(left + bottomLeft, bottom)
                if (bottomLeft > 0f) arcTo(Rect(left, bottom - 2 * bottomLeft, left + 2 * bottomLeft, bottom), 90f, 90f, false)
            }
            lineTo(left, top + topLeft)
            if (topLeft > 0f) arcTo(Rect(left, top, left + 2 * topLeft, top + 2 * topLeft), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}
