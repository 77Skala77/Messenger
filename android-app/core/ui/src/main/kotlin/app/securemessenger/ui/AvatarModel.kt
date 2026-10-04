package app.securemessenger.ui

import java.util.Locale

/** Two ARGB colors, top and bottom of an avatar's gradient. */
class AvatarGradient(val top: Long, val bottom: Long)

/**
 * The seven avatar gradients, in Telegram's order (`ThemeColors.java` values, DEC-027): red,
 * orange, violet, green, cyan, blue, pink.
 */
internal val AvatarGradients = listOf(
    AvatarGradient(0xFFFF845E, 0xFFD45246),
    AvatarGradient(0xFFFEBB5B, 0xFFF68136),
    AvatarGradient(0xFFB694F9, 0xFF6C61DF),
    AvatarGradient(0xFF9AD164, 0xFF46BA43),
    AvatarGradient(0xFF5BCBE3, 0xFF359AD4),
    AvatarGradient(0xFF5CAFFA, 0xFF408ACF),
    AvatarGradient(0xFFFF8AAC, 0xFFD95574),
)

/**
 * Which gradient a contact gets: a stable pick from the local contact ID, which never leaves the
 * phone. `String.hashCode` is specified exactly, so the pick is the same on every run and device.
 */
fun avatarColorIndex(localId: String): Int = Math.abs(localId.hashCode() % AvatarGradients.size)

/**
 * The letters in the avatar: the first character of the first word and of the last word, upper
 * case; one word gives one letter. A character is a whole emoji, flag or letter with its marks, not
 * half of one. Nothing to show gives "?".
 */
fun avatarInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val first = firstCharacter(words.first())
    val last = if (words.size > 1) firstCharacter(words.last()) else ""
    return (first + last).uppercase(Locale.ROOT)
}

private const val ZERO_WIDTH_JOINER = 0x200D

/** The first user-visible character of [word]: a code point plus whatever belongs to it. */
private fun firstCharacter(word: String): String {
    var end = Character.charCount(word.codePointAt(0))
    // A flag is two regional indicators.
    if (isRegionalIndicator(word.codePointAt(0)) && end < word.length && isRegionalIndicator(word.codePointAt(end))) {
        end += Character.charCount(word.codePointAt(end))
    }
    while (end < word.length) {
        val next = word.codePointAt(end)
        end += when {
            // A joiner pulls the next character in: a family, a profession.
            next == ZERO_WIDTH_JOINER && end + 1 < word.length ->
                1 + Character.charCount(word.codePointAt(end + 1))
            isCombining(next) -> Character.charCount(next)
            else -> break
        }
    }
    return word.substring(0, end)
}

private fun isRegionalIndicator(codePoint: Int) = codePoint in 0x1F1E6..0x1F1FF

/** Marks, variation selectors and skin tones that stay with the character before them. */
private fun isCombining(codePoint: Int): Boolean {
    if (codePoint in 0x1F3FB..0x1F3FF || codePoint == 0xFE0F || codePoint == 0xFE0E) return true
    return when (Character.getType(codePoint).toByte()) {
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
        else -> false
    }
}
