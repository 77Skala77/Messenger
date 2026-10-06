package app.securemessenger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Counts above this show as "999+": a longer number would not fit the badge. */
internal const val BADGE_MAX_COUNT = 999

/** The text in the badge; nothing for zero or less. */
fun formatBadgeCount(count: Int): String = when {
    count <= 0 -> ""
    count > BADGE_MAX_COUNT -> "$BADGE_MAX_COUNT+"
    else -> count.toString()
}

/**
 * The count of unread messages in a chat, a local number only (DEC-027; never a read receipt).
 * Round for one digit, longer for more; gray when the chat is muted. Draws nothing for zero. With
 * [animated], a changing number rolls like Telegram's counter (`CounterView`): the old digits move
 * 13 dp out while the new ones move in, over 430 ms, cross-fading in the first half.
 */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier, muted: Boolean = false, animated: Boolean = false) {
    if (count <= 0) return
    val colors = MaterialTheme.messenger
    val description = LocalContext.current.resources.getQuantityString(R.plurals.unread_messages_description, count, count)
    val rollPx = with(LocalDensity.current) { TelegramMotion.CounterRoll.roundToPx() }
    Box(
        modifier = modifier
            .height(MessengerDimens.BadgeHeight)
            .defaultMinSize(minWidth = MessengerDimens.BadgeHeight)
            .background(if (muted) colors.badgeMuted else colors.badge, RoundedCornerShape(MessengerDimens.BadgeRadius))
            .padding(horizontal = MessengerDimens.BadgeTextPadding)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (animated) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val increment = targetState > initialState
                    val half = TelegramMotion.COUNTER_REPLACE_MILLIS / 2
                    (
                        slideInVertically(tween(TelegramMotion.COUNTER_REPLACE_MILLIS, easing = TelegramMotion.Default)) {
                            if (increment) rollPx else -rollPx
                        } + fadeIn(tween(half, easing = TelegramMotion.Default))
                        ) togetherWith (
                        slideOutVertically(tween(TelegramMotion.COUNTER_REPLACE_MILLIS, easing = TelegramMotion.Default)) {
                            if (increment) -rollPx else rollPx
                        } + fadeOut(tween(half, easing = TelegramMotion.Default))
                        ) using SizeTransform(clip = true)
                },
                contentAlignment = Alignment.Center,
                label = "counter digits",
            ) { shown ->
                Text(text = formatBadgeCount(shown), style = MessengerType.BadgeText, color = colors.badgeText, maxLines = 1)
            }
        } else {
            Text(
                text = formatBadgeCount(count),
                style = MessengerType.BadgeText,
                color = colors.badgeText,
                maxLines = 1,
            )
        }
    }
}

/**
 * The counter of a chat row: it pops in with an overshoot in 220 ms when the first unseen text
 * arrives, shrinks away in 150 ms when the last one is seen, and rolls its digits in between
 * (Telegram's `CounterView`, DEC-033).
 */
@Composable
fun AnimatedUnreadBadge(count: Int, modifier: Modifier = Modifier, muted: Boolean = false) {
    // While it shrinks away its number is already zero: keep showing the last one.
    var last by remember { mutableIntStateOf(count) }
    SideEffect { if (count > 0) last = count }
    AnimatedVisibility(
        visible = count > 0,
        modifier = modifier,
        enter = scaleIn(tween(TelegramMotion.COUNTER_IN_MILLIS, easing = TelegramMotion.overshoot(2f)), initialScale = 0f) +
            fadeIn(tween(TelegramMotion.COUNTER_IN_MILLIS / 2)),
        exit = scaleOut(tween(TelegramMotion.COUNTER_OUT_MILLIS, easing = TelegramMotion.Default), targetScale = 0f) +
            fadeOut(tween(TelegramMotion.COUNTER_OUT_MILLIS, easing = TelegramMotion.Default)),
    ) {
        UnreadBadge(if (count > 0) count else last, muted = muted, animated = true)
    }
}
