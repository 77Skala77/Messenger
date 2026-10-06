package app.securemessenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.securemessenger.transport.RelayHealthStatus

/**
 * The settings in Telegram's layout (DEC-033): a gray ground with white sections, each with its
 * heading in the accent color, 15 sp medium. What the settings do has not changed.
 */
@Composable
internal fun SettingsScreen(
    relayServerUrl: String,
    relayHealthStatus: RelayHealthStatus?,
    allowInsecureRelayHttp: Boolean,
    debugQaToolsEnabled: Boolean,
    isPhysicalDevice: Boolean,
    notificationsAvailable: Boolean,
    screenSecurity: Boolean,
    onScreenSecurityChanged: (Boolean) -> Unit,
    themeMode: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    onOpenThemePreview: () -> Unit,
    onBack: () -> Unit,
    onSaveRelayUrl: (String) -> Unit,
    onCheckRelay: (String) -> Unit,
    onSynchronizeNow: () -> Unit,
    onDeleteLocalData: () -> Unit,
    onResetTestData: () -> Unit,
) {
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(LanguageController.selected(context)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTestResetDialog by remember { mutableStateOf(false) }
    var relayUrlInput by remember(relayServerUrl) { mutableStateOf(relayServerUrl) }
    val addressWarning = relayClientAddressWarning(relayUrlInput, isPhysicalDevice)
    Scaffold(
        topBar = { NavigationTopBar(stringResource(R.string.settings), onBack) },
        containerColor = MaterialTheme.messenger.sectionBackground,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            item {
                SettingsGroup(stringResource(R.string.language)) {
                    Column(Modifier.selectableGroup()) {
                        SelectableOptionRow(
                            label = stringResource(R.string.russian),
                            selected = selectedLanguage == AppLanguage.RUSSIAN,
                            modifier = Modifier.testTag("language_option_ru"),
                        ) {
                            selectedLanguage = AppLanguage.RUSSIAN
                            LanguageController.select(context, selectedLanguage)
                        }
                        SelectableOptionRow(
                            label = stringResource(R.string.english),
                            selected = selectedLanguage == AppLanguage.ENGLISH,
                            modifier = Modifier.testTag("language_option_en"),
                        ) {
                            selectedLanguage = AppLanguage.ENGLISH
                            LanguageController.select(context, selectedLanguage)
                        }
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.theme_title)) {
                    Column(Modifier.selectableGroup()) {
                        SelectableOptionRow(
                            label = stringResource(R.string.theme_system),
                            selected = themeMode == AppThemeMode.SYSTEM,
                            modifier = Modifier.testTag(THEME_OPTION_SYSTEM),
                        ) { onThemeSelected(AppThemeMode.SYSTEM) }
                        SelectableOptionRow(
                            label = stringResource(R.string.theme_light),
                            selected = themeMode == AppThemeMode.LIGHT,
                            modifier = Modifier.testTag(THEME_OPTION_LIGHT),
                        ) { onThemeSelected(AppThemeMode.LIGHT) }
                        SelectableOptionRow(
                            label = stringResource(R.string.theme_dark),
                            selected = themeMode == AppThemeMode.DARK,
                            modifier = Modifier.testTag(THEME_OPTION_DARK),
                        ) { onThemeSelected(AppThemeMode.DARK) }
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_privacy_title)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = screenSecurity, role = Role.Switch, onValueChange = onScreenSecurityChanged)
                            .testTag(SCREEN_SECURITY_TAG)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.screen_security_title))
                            SettingsHint(stringResource(R.string.screen_security_hint))
                        }
                        Switch(checked = screenSecurity, onCheckedChange = null)
                    }
                }
            }
            // Only where chats exist (debug builds, v2): a release build posts nothing.
            if (notificationsAvailable) {
                item {
                    SettingsGroup(stringResource(R.string.settings_notifications_title)) { NotificationSettingsSection() }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.relay_server)) {
                    Text(stringResource(R.string.relay_server_explanation))
                    OutlinedTextField(
                        value = relayUrlInput,
                        onValueChange = { relayUrlInput = it },
                        label = { Text(stringResource(R.string.relay_server_url)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsHint(stringResource(R.string.relay_server_example))
                    if (relayUrlInput.trim().startsWith("http://", ignoreCase = true)) {
                        SecurityWarning(
                            stringResource(
                                if (allowInsecureRelayHttp) R.string.http_relay_debug_warning
                                else R.string.https_relay_required,
                            ),
                        )
                        if (allowInsecureRelayHttp) {
                            SettingsHint(stringResource(R.string.http_debug_lan_only))
                            SettingsHint(stringResource(R.string.use_https_for_different_networks))
                        }
                    } else if (relayTestMode(relayUrlInput) == RelayTestMode.PUBLIC_RELAY) {
                        SettingsHint(stringResource(R.string.public_relay_tunnel_notice))
                    }
                    when (addressWarning) {
                        RelayClientAddressWarning.PHONE_LOOPBACK ->
                            SecurityWarning(stringResource(R.string.relay_phone_loopback_warning))
                        RelayClientAddressWarning.UNSPECIFIED_BIND_ADDRESS ->
                            SecurityWarning(stringResource(R.string.relay_unspecified_address_warning))
                        null -> Unit
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSaveRelayUrl(relayUrlInput) }) {
                            Text(stringResource(R.string.save_relay_server))
                        }
                        OutlinedButton(onClick = { onCheckRelay(relayUrlInput) }) {
                            Text(stringResource(R.string.check_relay_server))
                        }
                    }
                    if (relayHealthStatus != null) {
                        Text(relayHealthLabel(relayHealthStatus), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onSynchronizeNow) {
                        Text(stringResource(R.string.synchronize_now))
                    }
                    SettingsHint(stringResource(R.string.background_sync_notice))
                }
            }
            item {
                SettingsGroup(stringResource(R.string.storage_title)) {
                    OutlinedButton(onClick = { showDeleteDialog = true }) {
                        Text(stringResource(R.string.delete_local_data))
                    }
                }
            }
            if (debugQaToolsEnabled) {
                item {
                    SettingsGroup(stringResource(R.string.debug_qa_tools_title)) {
                        Text(stringResource(R.string.debug_reset_test_data_explanation))
                        OutlinedButton(
                            onClick = { showTestResetDialog = true },
                            modifier = Modifier.testTag("debug_reset_test_data"),
                        ) {
                            Text(stringResource(R.string.reset_test_data))
                        }
                        OutlinedButton(onClick = onOpenThemePreview, modifier = Modifier.testTag(THEME_PREVIEW_BUTTON_TAG)) {
                            Text(stringResource(R.string.theme_preview_open))
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.no_secrets_logs),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
    if (showDeleteDialog) {
        TelegramAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_local_data)) },
            text = { Text(stringResource(R.string.delete_warning)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteLocalData() }) {
                    Text(stringResource(R.string.delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showTestResetDialog) {
        TelegramAlertDialog(
            onDismissRequest = { showTestResetDialog = false },
            title = { Text(stringResource(R.string.reset_test_data)) },
            text = { Text(stringResource(R.string.reset_test_data_warning)) },
            confirmButton = {
                TextButton(
                    onClick = { showTestResetDialog = false; onResetTestData() },
                    modifier = Modifier.testTag("confirm_debug_reset_test_data"),
                ) {
                    Text(stringResource(R.string.reset_test_data_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** One white section of the settings with its accent-colored heading, on the gray ground. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MessengerType.SectionTitle, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** The small gray text under a setting. */
@Composable
private fun SettingsHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.messenger.hint)
}
