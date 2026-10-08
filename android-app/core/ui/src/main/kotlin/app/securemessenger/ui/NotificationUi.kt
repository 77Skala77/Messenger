package app.securemessenger.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

internal const val NOTIFICATION_PROMPT_TAG = "notification_prompt"
internal const val NOTIFICATION_PROMPT_ENABLE_TAG = "notification_prompt_enable"
internal const val NOTIFICATION_PROMPT_LATER_TAG = "notification_prompt_later"
internal const val NOTIFICATION_SETTINGS_TAG = "settings_notifications"

/**
 * Asks for the Android 13+ notification permission in context (DEC-027): only once someone can
 * write (a chat or a request exists), only while it is not granted, and not again after "Not now"
 * or a refusal; the settings keep the way back.
 */
@Composable
internal fun NotificationPromptCard(hasConversations: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(V2Notifications.permissionGranted(context)) }
    var dismissed by remember { mutableStateOf(NotificationPrompt.dismissed(context)) }
    // The person may have changed it in Android's settings meanwhile.
    LifecycleResumeEffect(Unit) {
        granted = V2Notifications.permissionGranted(context)
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        if (!allowed) {
            NotificationPrompt.dismiss(context)
            dismissed = true
        }
    }
    if (granted || dismissed || !hasConversations) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(NOTIFICATION_PROMPT_TAG)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.notification_prompt_text))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.testTag(NOTIFICATION_PROMPT_ENABLE_TAG),
                ) { Text(stringResource(R.string.notification_prompt_enable)) }
                TextButton(
                    onClick = {
                        NotificationPrompt.dismiss(context)
                        dismissed = true
                    },
                    modifier = Modifier.testTag(NOTIFICATION_PROMPT_LATER_TAG),
                ) { Text(stringResource(R.string.notification_prompt_later)) }
            }
        }
    }
}

/**
 * Whether notifications are on, what they show, and the way to Android's settings for them: the rows
 * of a settings section (DEC-038).
 */
@Composable
internal fun NotificationSettingsSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(V2Notifications.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = V2Notifications.canPost(context)
        onPauseOrDispose {}
    }
    SettingsHeader(stringResource(R.string.settings_notifications_title))
    SettingsText(stringResource(if (enabled) R.string.settings_notifications_on else R.string.settings_notifications_off))
    SettingsDivider()
    SettingsActionRow(
        stringResource(R.string.settings_notifications_open),
        onClick = {
            val settings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(settings)
        },
        modifier = Modifier.testTag(NOTIFICATION_SETTINGS_TAG),
    )
}
