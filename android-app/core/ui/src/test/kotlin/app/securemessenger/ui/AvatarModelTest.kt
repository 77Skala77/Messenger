package app.securemessenger.ui

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarModelTest {
    @Test
    fun `two words give the first letters of the first and the last word`() {
        assertEquals("AB", avatarInitials("Alice Brown"))
        assertEquals("АП", avatarInitials("Анна Петрова"))
        assertEquals("MS", avatarInitials("  mary   ann   smith "))
    }

    @Test
    fun `one word gives one letter and a hyphenated name is one word`() {
        assertEquals("D", avatarInitials("Dmitry"))
        assertEquals("S", avatarInitials("Smith-Jones"))
        assertEquals("Ё", avatarInitials("ёжик"))
    }

    @Test
    fun `a whole emoji counts as one character`() {
        assertEquals("😀S", avatarInitials("😀 Smile"))
        // A flag is two regional indicators.
        assertEquals("🇩🇪B", avatarInitials("🇩🇪 Berlin"))
        // A family is three people joined by zero-width joiners.
        val family = "👨‍👩‍👧"
        assertEquals(family + "H", avatarInitials("$family Home"))
        // A skin tone and a variation selector stay with their emoji.
        assertEquals("👍🏽O", avatarInitials("👍🏽 Ok"))
        assertEquals("❤️L", avatarInitials("❤️ Love"))
    }

    @Test
    fun `a letter keeps its combining accent`() {
        assertEquals("É", avatarInitials("école"))
    }

    @Test
    fun `nothing to show gives a question mark`() {
        assertEquals("?", avatarInitials(""))
        assertEquals("?", avatarInitials("   "))
    }

    @Test
    fun `the color index is stable and follows the string hash`() {
        // "abc".hashCode() is 96354, and 96354 % 7 is 6.
        assertEquals(6, avatarColorIndex("abc"))
        assertEquals(avatarColorIndex("contact-1"), avatarColorIndex("contact-1"))
        // This string's hash is Int.MIN_VALUE, whose remainder is negative before abs.
        assertEquals(2, avatarColorIndex("polygenelubricants"))
    }

    @Test
    fun `every index is in range and all seven colors are used`() {
        val random = Random(5)
        val indexes = (1..1000).map { avatarColorIndex("id-" + random.nextLong()) }
        assertTrue(indexes.all { it in 0..6 })
        assertEquals((0..6).toSet(), indexes.toSet())
    }

    @Test
    fun `the palette is Telegram's seven gradients in order`() {
        assertEquals(7, AvatarGradients.size)
        assertEquals(0xFFFF845E, AvatarGradients.first().top)
        assertEquals(0xFFD95574, AvatarGradients.last().bottom)
    }
}
