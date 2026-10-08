package app.securemessenger.ui

import app.securemessenger.signalstore.V2MessageStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The small pure rules behind the badge, the ticks, the bubble corners, the theme choice, the
 * wallpaper (Telegram's gradient and pattern, DEC-038), the record circle and the saved screen stack.
 */
class MessengerPiecesTest {
    @Test
    fun `the badge text is empty for zero and caps at 999 plus`() {
        assertEquals("", formatBadgeCount(0))
        assertEquals("", formatBadgeCount(-3))
        assertEquals("1", formatBadgeCount(1))
        assertEquals("999", formatBadgeCount(999))
        assertEquals("999+", formatBadgeCount(1000))
        assertEquals("999+", formatBadgeCount(Int.MAX_VALUE))
    }

    @Test
    fun `ticks follow the message status and there is no read state`() {
        assertEquals(TickKind.CLOCK, tickKindFor(V2MessageStatus.PENDING))
        // A failed send is retried, so it still waits.
        assertEquals(TickKind.CLOCK, tickKindFor(V2MessageStatus.FAILED))
        assertEquals(TickKind.SENT, tickKindFor(V2MessageStatus.SENT))
        assertEquals(TickKind.DELIVERED, tickKindFor(V2MessageStatus.DELIVERED))
        assertNull(tickKindFor(V2MessageStatus.RECEIVED))
        assertEquals(3, TickKind.entries.size)
    }

    @Test
    fun `an outgoing bubble with a tail has the tail on the right and tight corners next to neighbours`() {
        val last = bubbleGeometry(outgoing = true, tail = true, nearTop = false, nearBottom = false)
        assertEquals(17f, last.topLeft); assertEquals(17f, last.topRight)
        assertEquals(0f, last.bottomRight); assertEquals(17f, last.bottomLeft)
        assertTrue(last.tail)

        val middle = bubbleGeometry(outgoing = true, tail = false, nearTop = true, nearBottom = true)
        assertEquals(6f, middle.topRight); assertEquals(6f, middle.bottomRight)
        assertEquals(17f, middle.topLeft); assertEquals(17f, middle.bottomLeft)
        assertTrue(!middle.tail)
    }

    @Test
    fun `an incoming bubble mirrors the outgoing one`() {
        val last = bubbleGeometry(outgoing = false, tail = true, nearTop = true, nearBottom = false)
        assertEquals(6f, last.topLeft); assertEquals(17f, last.topRight)
        assertEquals(17f, last.bottomRight); assertEquals(0f, last.bottomLeft)

        val first = bubbleGeometry(outgoing = false, tail = false, nearTop = false, nearBottom = true)
        assertEquals(17f, first.topLeft); assertEquals(6f, first.bottomLeft)
        assertEquals(17f, first.topRight); assertEquals(17f, first.bottomRight)
    }

    @Test
    fun `the tail replaces the corner even if the bubble is also marked as touching the next one`() {
        assertEquals(0f, bubbleGeometry(outgoing = true, tail = true, nearTop = false, nearBottom = true).bottomRight)
        assertEquals(0f, bubbleGeometry(outgoing = false, tail = true, nearTop = false, nearBottom = true).bottomLeft)
    }

    @Test
    fun `the theme choice defaults to the system and knows its three values`() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStored(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStored("purple"))
        AppThemeMode.entries.forEach { assertEquals(it, AppThemeMode.fromStored(it.storedValue)) }
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStored("dark"))
        assertEquals(setOf("system", "light", "dark"), AppThemeMode.entries.map { it.storedValue }.toSet())
    }

    /** DEC-038: Telegram's gradient (`generateGradient` in `jni/image.cpp`): where its colors stand. */
    @Test
    fun `the wallpaper colors stand where telegram puts them and go round in eight phases`() {
        assertArrayEquals(floatArrayOf(0.80f, 0.90f, 0.35f, 0.75f, 0.20f, 0.10f, 0.65f, 0.25f), WallpaperGradient.positions(0), 0.0001f)
        assertArrayEquals(floatArrayOf(0.60f, 0.80f, 0.25f, 0.40f, 0.40f, 0.20f, 0.75f, 0.60f), WallpaperGradient.positions(1), 0.0001f)
        assertArrayEquals(WallpaperGradient.positions(0), WallpaperGradient.positions(8), 0f)
        assertArrayEquals(WallpaperGradient.positions(7), WallpaperGradient.positions(-1), 0f)
    }

    @Test
    fun `each wallpaper color is strongest at its own corner`() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val pixels = WallpaperGradient.pixels(intArrayOf(red, 0xFF00FF00.toInt(), blue, 0xFFFFFFFF.toInt()), phase = 0, progress = 1f)
        assertEquals(WallpaperGradient.WIDTH * WallpaperGradient.HEIGHT, pixels.size)
        assertTrue(pixels.all { it ushr 24 == 0xFF })
        // Phase 0: the third color at the top left, the first at the bottom right.
        val topLeft = pixels.first()
        assertTrue((topLeft and 0xFF) > ((topLeft shr 16) and 0xFF) + 150)
        val bottomRight = pixels.last()
        assertTrue(((bottomRight shr 16) and 0xFF) > (bottomRight and 0xFF) + 150)
    }

    @Test
    fun `a move between phases starts exactly where the last one ended`() {
        val colors = intArrayOf(0xFFDBDDBB.toInt(), 0xFF6BA587.toInt(), 0xFFD5D88D.toInt(), 0xFF88B884.toInt())
        val settled = WallpaperGradient.pixels(colors, phase = 4, progress = 1f)
        val starting = WallpaperGradient.pixels(colors, phase = 3, progress = 0f)
        assertTrue(settled.indices.all { close(settled[it], starting[it]) })
    }

    @Test
    fun `one color everywhere is flat and a missing fourth color is left out`() {
        val flat = WallpaperGradient.pixels(IntArray(4) { 0xFF336699.toInt() }, phase = 2, progress = 0.5f)
        assertTrue(flat.all { close(it, 0xFF336699.toInt()) })
        // Three colors (red, green, blue) and a fourth of 0: every pixel mixes only the three.
        val three = WallpaperGradient.pixels(intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0), phase = 0, progress = 1f)
        assertTrue(three.all { ((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF) in 250..258 })
    }

    /** DEC-038: Telegram's pattern, copied unchanged: its size and its 745 shapes, each a valid path. */
    @Test
    fun `telegram's pattern reads as 745 shapes in a 1440 by 2960 drawing`() {
        val drawing = WallpaperPattern.parse(java.io.File("src/main/res/raw/tg_default_pattern.svg").readText())
        assertEquals(1440f, drawing.width)
        assertEquals(2960f, drawing.height)
        assertEquals(745, drawing.paths.size)
        val parser = androidx.compose.ui.graphics.vector.PathParser()
        drawing.paths.forEach { data -> assertTrue(parser.parsePathString(data).toNodes().isNotEmpty()) }
    }

    @Test
    fun `the record circle comes in growing past its size and settling`() {
        assertEquals(0f, recordCircleEnterScale(0f), 0.0001f)
        assertEquals(1f, recordCircleEnterScale(0.5f), 0.0001f)
        assertEquals(0.9f, recordCircleEnterScale(0.75f), 0.0001f)
        assertEquals(1f, recordCircleEnterScale(1f), 0.0001f)
    }

    private enum class Page { ROOT, ONE, TWO }

    /** The app's screen stack outlives a rebuilt activity (a language change): saved by name, unknown names left out. */
    @Test
    fun `a saved screen stack comes back as it was`() {
        val saver = navStackSaver(Page.entries.toTypedArray())
        val stack = NavStack(Page.ROOT).apply { push(Page.ONE); push(Page.TWO) }
        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any) = true
        }
        val saved = with(saver) { scope.save(stack) }
        assertEquals(arrayListOf("ROOT", "ONE", "TWO"), saved)
        assertEquals(listOf(Page.ROOT, Page.ONE, Page.TWO), saver.restore(checkNotNull(saved))?.items)
        assertEquals(listOf(Page.ROOT, Page.TWO), saver.restore(arrayListOf("ROOT", "GONE", "TWO"))?.items)
        assertNull(saver.restore(arrayListOf("GONE")))
    }

    private fun close(a: Int, b: Int): Boolean =
        (0..2).all { shift -> kotlin.math.abs(((a shr (shift * 8)) and 0xFF) - ((b shr (shift * 8)) and 0xFF)) <= 1 }
}
