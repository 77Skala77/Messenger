package app.securemessenger.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.securemessenger.transport.RelayHealthStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

private enum class Screen {
    ONBOARDING,
    MAIN,
    SETTINGS,

    /** Debug tools only: the pieces of the messenger look in the current theme (step 6c). */
    THEME_PREVIEW,
}

/**
 * App shell: onboarding, the chat (v2, debug builds only; DEC-021), and settings. Release builds
 * show a "not for messaging" screen instead of a chat until an independent external audit (DEC-026).
 */
@Composable
fun SecureMessengerApp(viewModel: SecureMessengerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    // Onboarding or the main screen at the bottom; settings and the theme preview open over the main
    // screen with Telegram's transitions and swipe back (DEC-033).
    val nav = rememberNavStack(Screen.ONBOARDING)
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(ThemeController.selected(context)) }
    var screenSecurity by remember { mutableStateOf(ScreenSecurity.enabled(context)) }
    SecureWindowEffect(enabled = screenSecurity)
    // While any screen of the app is visible, the background sync posts no notifications (DEC-031).
    LifecycleStartEffect(Unit) {
        V2Foreground.visible = true
        onStopOrDispose { V2Foreground.visible = false }
    }

    LaunchedEffect(state.profileKnown, state.profileCreated) {
        if (state.profileKnown) {
            when {
                !state.profileCreated -> nav.set(listOf(Screen.ONBOARDING))
                nav.top == Screen.ONBOARDING -> nav.set(listOf(Screen.MAIN))
            }
        }
    }

    // System Back (button or gesture) from settings goes one screen back, like the app's own Back
    // button (TQ-109) and the swipe back; from the theme preview it goes back to settings. Off
    // everywhere else: the main screen, onboarding and the "data cannot be read" screen have nothing
    // to go back to, so the system behaves as usual there. Open dialogs handle Back themselves and
    // close first.
    BackHandler(enabled = nav.canPop && state.localDataProblem == null) { nav.pop() }

    MessengerTheme(themeMode) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val problem = state.localDataProblem
            if (problem != null) {
                // DEC-023: nothing else is shown or run while the stored data cannot be used.
                LocalDataProblemScreen(
                    problem = problem,
                    onCheckAgain = viewModel::checkLocalData,
                    onDeleteLocalData = viewModel::deleteLocalData,
                )
            } else if (state.profileKnown) {
                TelegramScreenStack(stack = nav.items, onSwipeBack = { nav.pop() }) { screen ->
                    when (screen) {
                        Screen.ONBOARDING -> OnboardingScreen(
                            creatingProfile = state.creatingProfile,
                            onCreateProfile = viewModel::createProfile,
                        )
                        Screen.MAIN ->
                            if (state.v2Available) {
                                ConversationV2Root(
                                    onOpenSettings = { nav.push(Screen.SETTINGS) },
                                    onLocalDataUnreadable = viewModel::checkLocalData,
                                )
                            } else {
                                NotForMessagingScreen(onOpenSettings = { nav.push(Screen.SETTINGS) })
                            }
                        Screen.SETTINGS -> SettingsScreen(
                            relayServerUrl = state.relayServerUrl,
                            relayHealthStatus = state.relayHealthStatus,
                            allowInsecureRelayHttp = state.allowInsecureRelayHttp,
                            debugQaToolsEnabled = state.debugQaToolsEnabled,
                            isPhysicalDevice = state.isPhysicalDevice,
                            notificationsAvailable = state.v2Available,
                            screenSecurity = screenSecurity,
                            onScreenSecurityChanged = { enabled ->
                                screenSecurity = enabled
                                ScreenSecurity.set(context, enabled)
                            },
                            themeMode = themeMode,
                            onThemeSelected = { mode ->
                                themeMode = mode
                                ThemeController.select(context, mode)
                            },
                            onOpenThemePreview = { nav.push(Screen.THEME_PREVIEW) },
                            onBack = { nav.pop() },
                            onSaveRelayUrl = viewModel::saveRelayServerUrl,
                            onCheckRelay = viewModel::checkRelayServer,
                            onSynchronizeNow = viewModel::synchronizeInBackground,
                            onDeleteLocalData = viewModel::deleteLocalData,
                            onResetTestData = viewModel::resetTestDataForQa,
                        )
                        // Only the debug tools lead here; a release build never shows the button.
                        Screen.THEME_PREVIEW -> if (state.debugQaToolsEnabled) ThemePreviewScreen(onBack = { nav.pop() })
                    }
                }
            }
            if (state.legacyResetNotice) {
                TelegramAlertDialog(
                    onDismissRequest = viewModel::dismissLegacyResetNotice,
                    title = { Text(stringResource(R.string.legacy_reset_title)) },
                    text = { Text(stringResource(R.string.legacy_reset_body)) },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissLegacyResetNotice) {
                            Text(stringResource(R.string.legacy_reset_ok))
                        }
                    },
                )
            } else if (state.relayResetNotice) {
                // DEC-024: the v2 test conversations were deleted once for the relay-queue change.
                TelegramAlertDialog(
                    onDismissRequest = viewModel::dismissRelayResetNotice,
                    title = { Text(stringResource(R.string.relay_reset_title)) },
                    text = { Text(stringResource(R.string.relay_reset_body)) },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissRelayResetNotice) {
                            Text(stringResource(R.string.legacy_reset_ok))
                        }
                    },
                )
            }
        }
    }
}

/**
 * A full-screen column that stays centred while its content fits and scrolls once it does not
 * (a phone in landscape, a large font, a small screen), so no button is ever out of reach.
 */
@Composable
private fun CenteredScrollableColumn(
    verticalArrangement: Arrangement.Vertical,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(24.dp),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
private fun OnboardingScreen(creatingProfile: Boolean, onCreateProfile: (String) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    CenteredScrollableColumn(verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.mvp_badge), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        SecurityWarning(text = stringResource(R.string.mvp_warning))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_body))
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.display_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        // Off while a profile is being created, so a double tap cannot start a second one (STORE-04).
        Button(
            onClick = { onCreateProfile(displayName) },
            enabled = displayName.isNotBlank() && !creatingProfile,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.create_profile))
        }
    }
}

/**
 * DEC-023: the stored data cannot be used. For [LocalDataProblem.UNREADABLE] and
 * [LocalDataProblem.RECEIVE_QUEUE_LOST] (DEC-032) the user may delete
 * the local data (confirmed) and start over; nothing is deleted automatically, because a failure
 * that only looked final must not cost the user their data.
 */
@Composable
internal fun LocalDataProblemScreen(
    problem: LocalDataProblem,
    onCheckAgain: () -> Unit,
    onDeleteLocalData: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    // Only a temporary problem may pass by itself; the others need the confirmed fresh start.
    val needsFreshStart = problem != LocalDataProblem.TEMPORARY
    val (title, body) = when (problem) {
        LocalDataProblem.UNREADABLE -> R.string.local_data_unreadable_title to R.string.local_data_unreadable_body
        LocalDataProblem.TEMPORARY -> R.string.local_data_temporary_title to R.string.local_data_temporary_body
        LocalDataProblem.RECEIVE_QUEUE_LOST -> R.string.receive_queue_lost_title to R.string.receive_queue_lost_body
    }
    // Scrollable: these buttons are the only way out of this state, also in landscape (Phase 5 F12).
    CenteredScrollableColumn(verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        SecurityWarning(stringResource(body))
        Button(onClick = onCheckAgain, modifier = Modifier.testTag(CHECK_AGAIN_TAG)) {
            Text(stringResource(R.string.local_data_check_again))
        }
        if (needsFreshStart) {
            OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.testTag(DELETE_AND_RESTART_TAG)) {
                Text(stringResource(R.string.local_data_delete_and_restart))
            }
        }
    }
    if (showDeleteDialog) {
        TelegramAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_local_data)) },
            text = { Text(stringResource(R.string.delete_warning)) },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onDeleteLocalData() },
                    modifier = Modifier.testTag(CONFIRM_DELETE_TAG),
                ) {
                    Text(stringResource(R.string.delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

internal const val THEME_OPTION_SYSTEM = "theme_option_system"
internal const val THEME_OPTION_LIGHT = "theme_option_light"
internal const val THEME_OPTION_DARK = "theme_option_dark"
internal const val THEME_PREVIEW_BUTTON_TAG = "theme_preview_button"
internal const val SCREEN_SECURITY_TAG = "settings_screen_security"
internal const val CHECK_AGAIN_TAG = "local_data_check_again"
internal const val DELETE_AND_RESTART_TAG = "local_data_delete_and_restart"
internal const val CONFIRM_DELETE_TAG = "local_data_confirm_delete"

@Composable
private fun NotForMessagingScreen(onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TelegramTopBar(
                title = stringResource(R.string.home_title),
                actions = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.not_for_messaging_title), style = MaterialTheme.typography.headlineSmall)
            SecurityWarning(stringResource(R.string.not_for_messaging_body))
        }
    }
}


@Composable
internal fun SelectableOptionRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onSelected: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelected,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            label,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun QrCode(value: String, contentDescription: String) {
    if (value.isBlank()) return
    val bitmap = remember(value) {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }.asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = Modifier.size(208.dp),
    )
}

@Composable
internal fun SecurityWarning(text: String) {
    Box(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
internal fun NavigationTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TelegramTopBar(title = title, onBack = onBack, actions = { actions() })
}

@Composable
internal fun relayHealthLabel(status: RelayHealthStatus): String = when (status) {
    RelayHealthStatus.AVAILABLE -> stringResource(R.string.relay_available)
    RelayHealthStatus.UNAVAILABLE -> stringResource(R.string.relay_unavailable)
    RelayHealthStatus.CONNECTION_ERROR -> stringResource(R.string.relay_connection_error)
}
