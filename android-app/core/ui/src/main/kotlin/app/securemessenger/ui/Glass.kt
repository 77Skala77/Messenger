package app.securemessenger.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Telegram's glass (12.x, `ui/Components/blur3`; DEC-036 block): panes that float over the chat and
 * show it through them, lightly blurred, its colors raised, and on Android 13 and later bent at the
 * rim like liquid glass. Written anew in Compose from Telegram's numbers and its shader.
 *
 * The chat is recorded once per frame into [GlassSource] (a `GraphicsLayer`, a render node); each
 * pane draws the part of it that lies behind it through its own effects. Nothing is copied to a
 * bitmap, nothing leaves the GPU.
 */
@Stable
internal class GlassSource(val layer: GraphicsLayer) {
    /** Where the recorded content sits on the screen; panes subtract it from their own place. */
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
internal fun rememberGlassSource(): GlassSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassSource(layer) }
}

/** Draws this content as usual and records it for the glass panes in front of it. */
internal fun Modifier.glassSource(source: GlassSource): Modifier = this
    .onGloballyPositioned { source.origin = it.positionInRoot() }
    .drawWithContent {
        source.layer.record { this@drawWithContent.drawContent() }
        drawLayer(source.layer)
    }

/** Telegram's glass numbers (`DownscaleScrollableNoiseSuppressor`, `BlurredBackgroundDrawable`). */
internal object GlassRules {
    /** The light blur of the glass ("weak blur"), with saturation three times up. */
    val BlurRadius = 6.dp
    const val SATURATION = 3f

    /** The liquid rim: at most 11 dp thick (a fifth of the smaller side), bending 0.75, index 1.5. */
    val RimThicknessMax = 11.dp
    const val REFRACT_INTENSITY = 0.75f
    const val REFRACT_INDEX = 1.5f

    /** The light lines on the glass: 1 dp along the top, 2/3 dp along the bottom. */
    val StrokeTop = 1.dp
    val StrokeBottom = (2f / 3f).dp

    /** The soft shadow of a light pane: 1 dp, a third of a dp down. */
    val ShadowRadius = 1.dp
    val ShadowDy = (1f / 3f).dp
}

/**
 * A pane of glass with rounded corners ([cornerRadius]; more than half the height makes a pill) in
 * front of [source]. Draw the pane's content on top of it as usual.
 */
@Composable
internal fun Modifier.glass(source: GlassSource, cornerRadius: Dp): Modifier {
    val colors = MaterialTheme.messenger
    val pane = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val liquid = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LiquidGlass() else null }
    return this
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawBehind {
            if (size.width <= 0f || size.height <= 0f) return@drawBehind
            val radius = minOf(cornerRadius.toPx(), size.minDimension / 2f)
            drawGlassShadow(colors.glassShadow, radius)
            val offset = position - source.origin
            // The picture behind is taken with a margin all round: the blur and the bent rim read
            // a little beyond the pane, and an empty margin showed as a band along its edges.
            val margin = (GlassRules.RimThicknessMax + GlassRules.BlurRadius).toPx()
            val recorded = IntSize((size.width + 2 * margin).toInt(), (size.height + 2 * margin).toInt())
            pane.record(size = recorded) { translate(margin - offset.x, margin - offset.y) { drawLayer(source.layer) } }
            pane.setRoundRectOutline(Offset(margin, margin), size, radius)
            pane.clip = true
            pane.renderEffect = glassEffect(this, radius, colors.glassTint, liquid, margin)
            translate(-margin, -margin) { drawLayer(pane) }
            // Without the shader (Android 12) the tint goes on top; the shader mixes it in itself.
            if (liquid == null) drawRoundRect(colors.glassTint, cornerRadius = CornerRadius(radius))
            drawGlassRims(colors.glassStrokeTop, colors.glassStrokeBottom, radius)
        }
}

/** The pane's effects: saturation up, the light blur, and the liquid rim where Android has shaders. */
private fun glassEffect(scope: DrawScope, radius: Float, tint: Color, liquid: LiquidGlass?, margin: Float): androidx.compose.ui.graphics.RenderEffect {
    val blurPx = with(scope) { GlassRules.BlurRadius.toPx() }
    val saturation = ColorMatrix().apply { setSaturation(GlassRules.SATURATION) }
    val frosted = RenderEffect.createChainEffect(
        RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP),
        RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(saturation)),
    )
    if (liquid == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return frosted.asComposeRenderEffect()
    val size = scope.size
    val thickness = with(scope) { GlassRules.RimThicknessMax.toPx() }.coerceAtMost(size.minDimension / 5f).coerceAtLeast(1f)
    return RenderEffect.createChainEffect(liquid.effect(size, radius, thickness, tint, margin), frosted).asComposeRenderEffect()
}

/** Telegram's light pane shadow; nothing on the dark theme (its shadow color is clear). */
private fun DrawScope.drawGlassShadow(color: Color, radius: Float) {
    if (color.alpha <= 0f) return
    val shadowRadius = GlassRules.ShadowRadius.toPx()
    val dy = GlassRules.ShadowDy.toPx()
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(shadowRadius, 0f, dy, color.toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
    }
}

/**
 * The light along the top and, fainter, along the bottom (`BlurredBackgroundDrawable.BoundProps.build`):
 * not a line of even width but a sliver, the pane's rounded top minus the same shape moved down by the
 * stroke width. It is full width only in the middle and runs out to nothing down the round sides.
 */
private fun DrawScope.drawGlassRims(top: Color, bottom: Color, radius: Float) {
    if (top.alpha > 0f) drawPath(glassRimPath(size, radius, GlassRules.StrokeTop.toPx(), atTop = true), top)
    if (bottom.alpha > 0f) drawPath(glassRimPath(size, radius, GlassRules.StrokeBottom.toPx(), atTop = false), bottom)
}

/** Telegram's sliver: the band of height [radius] at one edge, minus itself shifted inwards by [stroke]. */
internal fun glassRimPath(size: Size, radius: Float, stroke: Float, atTop: Boolean): Path {
    val band = minOf(radius, size.height)
    val corner = CornerRadius(radius)
    fun shape(top: Float, bottom: Float) = Path().apply {
        addRoundRect(
            if (atTop) RoundRect(0f, top, size.width, bottom, topLeftCornerRadius = corner, topRightCornerRadius = corner)
            else RoundRect(0f, top, size.width, bottom, bottomLeftCornerRadius = corner, bottomRightCornerRadius = corner),
        )
    }
    val outer = if (atTop) shape(0f, band) else shape(size.height - band, size.height)
    val inner = if (atTop) shape(stroke, band) else shape(size.height - band, size.height - stroke)
    return Path().apply { op(outer, inner, PathOperation.Difference) }
}

/**
 * Telegram's liquid-glass shader (`res/raw/liquid_glass_shader.agsl`, `LiquidGlassEffect.java`): inside
 * a rounded rectangle, near its rim, each point shows the picture behind as bent by a lens of
 * [REFRACT_INDEX] whose edge is [thickness] wide, and the pane's color is laid over it.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LiquidGlass {
    private val shader = RuntimeShader(LIQUID_GLASS_AGSL)

    /** [margin]: the recorded picture reaches this far beyond the pane on every side. */
    fun effect(size: Size, radius: Float, thickness: Float, tint: Color, margin: Float = 0f): RenderEffect {
        val alpha = tint.alpha
        shader.setFloatUniform("resolution", size.width + 2 * margin, size.height + 2 * margin)
        shader.setFloatUniform("center", margin + size.width / 2f, margin + size.height / 2f)
        shader.setFloatUniform("size", size.width / 2f, size.height / 2f)
        shader.setFloatUniform("radius", radius, radius, radius, radius)
        shader.setFloatUniform("thickness", thickness)
        shader.setFloatUniform("refract_intensity", GlassRules.REFRACT_INTENSITY)
        shader.setFloatUniform("refract_index", GlassRules.REFRACT_INDEX)
        shader.setFloatUniform("foreground_color_premultiplied", tint.red * alpha, tint.green * alpha, tint.blue * alpha, alpha)
        return RenderEffect.createRuntimeShaderEffect(shader, "img")
    }

    private companion object {
        /** Telegram's shader as it is, from `TMessagesProj/src/main/res/raw/liquid_glass_shader.agsl` (12.10.6). */
        const val LIQUID_GLASS_AGSL = """
uniform shader img;

uniform float2 resolution;
uniform float2 center;
uniform float2 size;
uniform float4 radius;
uniform float thickness;
uniform float refract_index;
uniform float refract_intensity;
uniform float4 foreground_color_premultiplied;

half sdfRect(half2 p, half4 r) {
  r.xy = (p.x > 0.0) ? r.xy : r.zw;
  r.x  = (p.y > 0.0) ? r.x  : r.y;
  half2 q = abs(p) - size + r.x;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r.x;
}

half4 srcOver(half4 src, half4 dst) {
    half3 outRGB = (src.rgb + dst.rgb * (1.0 - src.a));
    float outA = src.a + (1.0 - src.a) * dst.a;
    return half4(outRGB, outA);
}

half4 main(in float2 fragCoord) {
  half2 p = fragCoord - center;
  half sd = sdfRect(p, radius);
  half2 uv = fragCoord;
  if (sd < 0.0) {
    half sdX = sdfRect(p + half2(1.0, 0.0), radius);
    half sdY = sdfRect(p + half2(0.0, 1.0), radius);

    half n_cos = max(thickness + sd, 0.0) / thickness;
    half n_cos2 = n_cos * n_cos;
    half n_sin = sqrt(1.0 - n_cos2);
    half3 normal = normalize(half3((sdX - sd) * n_cos, (sdY - sd) * n_cos, n_sin));

    half3 refract_vec = refract(half3(0.0, 0.0, -1.0), normal, 1.0 / refract_index);
    half h = sd < -thickness ? thickness : sqrt(sd * (-2.0 * thickness - sd));
    half refract_length = (h + 8.0 * thickness) / -refract_vec.z;

    uv += refract_vec.xy * refract_length * refract_intensity;
  }

  return srcOver(half4(foreground_color_premultiplied), img.eval(uv));
}
"""
    }
}
