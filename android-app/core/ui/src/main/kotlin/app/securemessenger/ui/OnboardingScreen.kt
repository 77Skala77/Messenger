package app.securemessenger.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val ONBOARDING_NAME_TAG = "onboarding_name"
internal const val ONBOARDING_START_TAG = "onboarding_start"

/**
 * The first screen in Telegram's look (its intro and "Your name" pages, DEC-033): a round emblem of
 * our own (never Telegram's paper plane), the title, what the app does and does not do, the test
 * warning, an underlined name field, and the round button with an arrow at the bottom end, which
 * turns into a spinner while the profile is created. The button is off while one is being created,
 * so a double tap cannot start a second one (STORE-04).
 */
@Composable
internal fun OnboardingScreen(creatingProfile: Boolean, onCreateProfile: (String) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    val canCreate = displayName.isNotBlank() && !creatingProfile
    val create = { if (canCreate) onCreateProfile(displayName) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Centred while it fits, scrolling once it does not (landscape, a large font): nothing out of reach.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppEmblem()
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.onboarding_title),
                    style = MessengerType.OnboardingTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.onboarding_body),
                    style = MessengerType.OnboardingText,
                    color = MaterialTheme.messenger.hint,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.mvp_badge),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Spacer(Modifier.height(12.dp))
                SecurityWarning(stringResource(R.string.mvp_warning))
                Spacer(Modifier.height(28.dp))
                UnderlinedNameField(displayName, { displayName = it }, onDone = create)
                // Room for the round button, so it never covers the field.
                Spacer(Modifier.height(88.dp))
            }
        }
        RoundNextButton(
            enabled = canCreate,
            busy = creatingProfile,
            description = stringResource(R.string.create_profile),
            onClick = create,
            modifier = Modifier.align(Alignment.BottomEnd).imePadding().padding(24.dp).testTag(ONBOARDING_START_TAG),
        )
    }
}

/** The name field of Telegram's registration: no box, a line under it that turns accent-colored and thicker while typing. */
@Composable
private fun UnderlinedNameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = MaterialTheme.colorScheme.primary
    val hint = MaterialTheme.messenger.hint
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MessengerType.InputText.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(accent),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_NAME_TAG),
        decorationBox = { field ->
            Column {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    if (value.isEmpty()) Text(stringResource(R.string.display_name), style = MessengerType.InputText, color = hint)
                    field()
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (focused) 2.dp else 1.dp)
                        .background(if (focused) accent else hint.copy(alpha = 0.5f)),
                )
            }
        },
    )
}

/**
 * The round button of Telegram's registration pages: accent, 56 dp, a white arrow; dimmed while it
 * cannot be pressed, and a spinner while the profile is being created.
 */
@Composable
private fun RoundNextButton(enabled: Boolean, busy: Boolean, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val shown by animateFloatAsState(if (enabled || busy) 1f else 0.4f, tween(TelegramMotion.DIALOG_FADE_MILLIS), label = "next button")
    Box(
        modifier
            .size(56.dp)
            .graphicsLayer { alpha = shown }
            .shadow(4.dp, CircleShape)
            .background(accent, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Canvas(Modifier.size(24.dp)) {
                val stroke = 2.dp.toPx()
                val tip = Offset(center.x + 7.dp.toPx(), center.y)
                drawLine(Color.White, Offset(center.x - 7.dp.toPx(), center.y), tip, stroke, StrokeCap.Round)
                drawLine(Color.White, Offset(tip.x - 6.dp.toPx(), center.y - 6.dp.toPx()), tip, stroke, StrokeCap.Round)
                drawLine(Color.White, Offset(tip.x - 6.dp.toPx(), center.y + 6.dp.toPx()), tip, stroke, StrokeCap.Round)
            }
        }
    }
}

/** Our own emblem: a speech bubble with a padlock in an accent circle. Drawn; nothing of Telegram's logo. */
@Composable
private fun AppEmblem() {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(112.dp)) {
        drawCircle(accent)
        val unit = size.minDimension / 112f
        // The bubble, with its tail at the bottom left.
        val bubbleLeft = 28f * unit
        val bubbleTop = 32f * unit
        val bubbleSize = Size(56f * unit, 40f * unit)
        drawRoundRect(Color.White, Offset(bubbleLeft, bubbleTop), bubbleSize, CornerRadius(12f * unit))
        val tail = Path().apply {
            moveTo(bubbleLeft + 8f * unit, bubbleTop + bubbleSize.height - 2f * unit)
            lineTo(bubbleLeft + 4f * unit, bubbleTop + bubbleSize.height + 10f * unit)
            lineTo(bubbleLeft + 20f * unit, bubbleTop + bubbleSize.height - 2f * unit)
            close()
        }
        drawPath(tail, Color.White)
        // The padlock in the middle of the bubble.
        val lockCenter = Offset(bubbleLeft + bubbleSize.width / 2f, bubbleTop + bubbleSize.height / 2f + 3f * unit)
        drawRoundRect(
            accent,
            Offset(lockCenter.x - 9f * unit, lockCenter.y - 6f * unit),
            Size(18f * unit, 14f * unit),
            CornerRadius(3f * unit),
        )
        drawArc(
            accent,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(lockCenter.x - 6f * unit, lockCenter.y - 15f * unit),
            size = Size(12f * unit, 16f * unit),
            style = Stroke(width = 3f * unit),
        )
    }
}
