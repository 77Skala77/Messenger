package app.securemessenger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** DEC-033: the numbers and decisions taken from Telegram's `ActionBarLayout`, pinned. */
class TelegramMotionTest {
    @Test
    fun `every curve starts at zero and ends at one`() {
        val curves = listOf(
            TelegramMotion.Default, TelegramMotion.EaseOut, TelegramMotion.EaseOutQuint, TelegramMotion.EaseIn,
            TelegramMotion.EaseBoth, TelegramMotion.EaseOutBack, TelegramMotion.StandardDecelerate,
            TelegramMotion.ScreenEasing, TelegramMotion.AccelerateDecelerate,
        )
        curves.forEach { curve ->
            assertEquals(0f, curve.transform(0f), 1e-3f)
            assertEquals(1f, curve.transform(1f), 1e-3f)
        }
    }

    @Test
    fun `the screen curve is Android's decelerate with factor one and a half`() {
        // 1 - (1 - x)^3
        assertEquals(0.875f, TelegramMotion.ScreenEasing.transform(0.5f), 1e-6f)
        assertEquals(1f - 0.8f * 0.8f * 0.8f, TelegramMotion.ScreenEasing.transform(0.2f), 1e-6f)
        // Factor one is the plain quadratic.
        assertEquals(0.75f, TelegramMotion.decelerate(1f).transform(0.5f), 1e-6f)
    }

    @Test
    fun `decelerating curves move fast first and the symmetric one is half at half`() {
        assertTrue(TelegramMotion.EaseOutQuint.transform(0.5f) > 0.8f)
        assertTrue(TelegramMotion.ScreenEasing.transform(0.25f) > 0.5f)
        assertEquals(0.5f, TelegramMotion.AccelerateDecelerate.transform(0.5f), 1e-6f)
        assertEquals(0.5f, TelegramMotion.EaseBoth.transform(0.5f), 1e-2f)
        // The back curve overshoots on the way, as designed.
        assertTrue((1..9).any { TelegramMotion.EaseOutBack.transform(it / 10f) > 1f })
    }

    @Test
    fun `the counter appears with an overshoot and the list moves with Telegram's curve`() {
        val overshoot = TelegramMotion.overshoot(2f)
        assertEquals(0f, overshoot.transform(0f), 1e-6f)
        assertEquals(1f, overshoot.transform(1f), 1e-6f)
        // At 70 percent of the time it is already past one, then it settles back.
        assertEquals(1.099f, overshoot.transform(0.7f), 1e-3f)
        assertTrue(overshoot.transform(0.9f) < overshoot.transform(0.7f))

        assertEquals(0f, TelegramMotion.ListMove.transform(0f), 1e-3f)
        assertEquals(1f, TelegramMotion.ListMove.transform(1f), 1e-3f)
        assertEquals(250, TelegramMotion.LIST_MOVE_MILLIS)
        assertEquals(0.9f, TelegramMotion.LIST_ENTER_SCALE, 0f)
        assertEquals(220, TelegramMotion.COUNTER_IN_MILLIS)
        assertEquals(150, TelegramMotion.COUNTER_OUT_MILLIS)
        assertEquals(430, TelegramMotion.COUNTER_REPLACE_MILLIS)
        assertEquals(280, TelegramMotion.SIDE_BUTTON_MILLIS)
    }

    @Test
    fun `the scroll-down button shows once the newest message is out of view`() {
        assertFalse(scrollDownButtonVisible(firstVisibleIndex = 0, firstVisibleOffsetPx = 0, thresholdPx = 100))
        assertFalse(scrollDownButtonVisible(firstVisibleIndex = 0, firstVisibleOffsetPx = 100, thresholdPx = 100))
        assertTrue(scrollDownButtonVisible(firstVisibleIndex = 0, firstVisibleOffsetPx = 101, thresholdPx = 100))
        assertTrue(scrollDownButtonVisible(firstVisibleIndex = 1, firstVisibleOffsetPx = 0, thresholdPx = 100))
    }

    @Test
    fun `a screen opens in 150 ms from 48 dp away`() {
        assertEquals(150, TelegramMotion.SCREEN_MILLIS)
        assertEquals(48f, TelegramMotion.SlideOffset.value, 0f)
    }

    @Test
    fun `half a centimetre is counted from the real dots per inch`() {
        assertEquals(40f, pixelsInCm(0.4f, 254f), 1e-3f)
        // A broken screen report never gives a zero distance.
        assertEquals(0.4f, pixelsInCm(0.4f, 0f), 1e-3f)
    }

    @Test
    fun `the swipe starts after 0_4 cm and only if mostly sideways`() {
        assertTrue(shouldStartBackSwipe(dx = 40f, dy = 5f, minDistancePx = 40f))
        assertFalse(shouldStartBackSwipe(dx = 39f, dy = 0f, minDistancePx = 40f))
        // Three times as far sideways as up or down is the line.
        assertTrue(shouldStartBackSwipe(dx = 60f, dy = 19f, minDistancePx = 40f))
        assertFalse(shouldStartBackSwipe(dx = 60f, dy = 20f, minDistancePx = 40f))
        assertFalse(shouldStartBackSwipe(dx = 60f, dy = 80f, minDistancePx = 40f))
    }

    @Test
    fun `letting go before a third of the width puts the screen back, unless it was a fast fling`() {
        val width = 1000f
        assertTrue(shouldCancelBackSwipe(x = 200f, width = width, velocityX = 0f, velocityY = 0f))
        assertFalse(shouldCancelBackSwipe(x = 340f, width = width, velocityX = 0f, velocityY = 0f))
        assertFalse(shouldCancelBackSwipe(x = 100f, width = width, velocityX = 4000f, velocityY = 100f))
        // A fast move that is mostly vertical is no fling to the right.
        assertTrue(shouldCancelBackSwipe(x = 100f, width = width, velocityX = 4000f, velocityY = 5000f))
        // Just under the fling speed does not count.
        assertTrue(shouldCancelBackSwipe(x = 100f, width = width, velocityX = 3499f, velocityY = 0f))
    }

    @Test
    fun `a fling to the right closes the screen even if the distance never started the swipe`() {
        assertTrue(isClosingFling(3500f, 100f))
        assertFalse(isClosingFling(3499f, 0f))
        assertFalse(isClosingFling(4000f, 5000f))
        assertFalse(isClosingFling(-4000f, 0f))
    }

    @Test
    fun `closing and putting back take time by the distance left, with floors`() {
        // 200 ms for the whole width, never under 50.
        assertEquals(200, backFinishMillis(x = 0f, width = 1000f))
        assertEquals(100, backFinishMillis(x = 500f, width = 1000f))
        assertEquals(50, backFinishMillis(x = 950f, width = 1000f))
        // 320 ms for the whole width, never under 120.
        assertEquals(320, backCancelMillis(x = 1000f, width = 1000f))
        assertEquals(160, backCancelMillis(x = 500f, width = 1000f))
        assertEquals(120, backCancelMillis(x = 100f, width = 1000f))
    }

    @Test
    fun `the screen below is dimmed less and less as the top one leaves`() {
        val full = 120f / 255f * 0.8f
        assertEquals(full, backScrimAlpha(0f, 1000f), 1e-6f)
        assertEquals(120f / 255f * 0.5f, backScrimAlpha(500f, 1000f), 1e-6f)
        assertEquals(0f, backScrimAlpha(1000f, 1000f), 1e-6f)
        // Dragging cannot go past the edges.
        assertEquals(0f, backScrimAlpha(1200f, 1000f), 1e-6f)
        assertEquals(full, backScrimAlpha(-50f, 1000f), 1e-6f)
    }

    @Test
    fun `the edge shadow fades only in the last twenty dp`() {
        assertEquals(1f, backShadowShare(0f, 1000f, 60f), 1e-6f)
        assertEquals(1f, backShadowShare(900f, 1000f, 60f), 1e-6f)
        assertEquals(1f / 3f, backShadowShare(980f, 1000f, 60f), 1e-6f)
        assertEquals(0f, backShadowShare(1000f, 1000f, 60f), 1e-6f)
    }

    @Test
    fun `a stack always keeps its root and pushes and pops screens`() {
        val stack = NavStack("home")
        assertFalse(stack.canPop)
        assertFalse(stack.pop())

        stack.push("chat")
        stack.push("chat")
        assertEquals(listOf("home", "chat"), stack.items)
        stack.push("info")
        assertEquals("info", stack.top)

        assertTrue(stack.pop())
        assertEquals("chat", stack.top)
        stack.push("info")
        stack.popToRoot()
        assertEquals(listOf("home"), stack.items)

        stack.set(listOf("home", "chat", "info"))
        assertEquals(3, stack.items.size)
        assertThrows(IllegalArgumentException::class.java) { stack.set(emptyList()) }
    }
}
