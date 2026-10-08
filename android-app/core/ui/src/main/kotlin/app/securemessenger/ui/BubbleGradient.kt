package app.securemessenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize

/**
 * The area that the outgoing bubbles' gradient spans: the whole chat (DEC-038). Telegram's dark
 * default fills its outgoing bubbles from one gradient fixed to the screen (`MessageDrawable` with a
 * four-color `MotionBackgroundDrawable`): violet above, blue below, so a bubble changes color as the
 * list moves it.
 */
@Stable
internal class BubbleGradientArea {
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(Size.Zero)
}

internal val LocalBubbleGradientArea = staticCompositionLocalOf<BubbleGradientArea?> { null }

/** A box whose area the outgoing bubbles in [content] take their gradient from. */
@Composable
internal fun BubbleGradientBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val area = remember { BubbleGradientArea() }
    Box(
        modifier.onGloballyPositioned {
            area.origin = it.positionInRoot()
            area.size = it.size.toSize()
        },
    ) {
        CompositionLocalProvider(LocalBubbleGradientArea provides area) { content() }
    }
}

/** The last gradient made, shared by all bubbles: the colors change only with the theme. */
private object BubbleGradientImage {
    private var colors: List<Color>? = null
    private var image: ImageBitmap? = null

    fun of(stops: List<Color>): ImageBitmap {
        image?.takeIf { colors == stops }?.let { return it }
        return WallpaperGradient.image(stops, 0, 1f).also {
            colors = stops
            image = it
        }
    }
}

/**
 * A bubble's background: one color, or for an outgoing bubble of a theme with a gradient
 * ([MessengerColors.bubbleOutgoingGradient]), the part of the gradient behind the bubble's place in
 * the surrounding [BubbleGradientBox] (or in the bubble itself, outside of one).
 */
@Composable
internal fun Modifier.bubbleBackground(outgoing: Boolean, shape: Shape): Modifier {
    val colors = MaterialTheme.messenger
    val stops = colors.bubbleOutgoingGradient
    if (!outgoing) return background(colors.bubbleIncoming, shape)
    if (stops.all { it == stops[0] }) return background(stops[0], shape)
    val area = LocalBubbleGradientArea.current
    val image = BubbleGradientImage.of(stops)
    var place by remember { mutableStateOf<Offset?>(null) }
    return this
        .onGloballyPositioned { place = it.positionInRoot() }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val shader = ImageShader(image, TileMode.Clamp, TileMode.Clamp)
            val brush = ShaderBrush(shader)
            val matrix = android.graphics.Matrix()
            onDrawBehind {
                val here = place ?: Offset.Zero
                val spanned = area?.takeIf { it.size.width > 0f && it.size.height > 0f }
                val spanSize = spanned?.size ?: size
                val spanOrigin = spanned?.origin ?: here
                val scale = coverScale(spanSize, image.width, image.height)
                val dx = (spanSize.width - image.width * scale) / 2f
                val dy = (spanSize.height - image.height * scale) / 2f
                matrix.setScale(scale, scale)
                matrix.postTranslate(spanOrigin.x - here.x + dx, spanOrigin.y - here.y + dy)
                shader.setLocalMatrix(matrix)
                drawOutline(outline, brush)
            }
        }
}
