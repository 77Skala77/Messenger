package app.securemessenger.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.viewmodel.compose.viewModel
import app.securemessenger.transport.RelayHealthStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

private enum class Screen {
    ONBOARDING,
    MAIN,
    SETTINGS,
}

/**
 * App shell: onboarding, the chat (v2, debug builds only; DEC-021), and settings. Release builds
 * show a "not for messaging" screen instead of a chat until the Phase 5 independent review.
 */
@Composable
fun SecureMessengerApp(viewModel: SecureMessengerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.ONBOARDING) }

    LaunchedEffect(state.profileKnown, state.profileCreated) {
        if (state.profileKnown) {
            screen = when {
                !state.profileCreated -> Screen.ONBOARDING
                screen == Screen.ONBOARDING -> Screen.MAIN
                else -> screen
            }
        }
    }

    MaterialTheme {
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
                when (screen) {
                    Screen.ONBOARDING -> OnboardingScreen(onCreateProfile = viewModel::createProfile)
                    Screen.MAIN ->
                        if (state.v2Available) {
                            ConversationV2Root(
                                onOpenSettings = { screen = Screen.SETTINGS },
                                onLocalDataUnreadable = viewModel::checkLocalData,
                            )
                        } else {
                            NotForMessagingScreen(onOpenSettings = { screen = Screen.SETTINGS })
                        }
                    Screen.SETTINGS -> SettingsScreen(
                        relayServerUrl = state.relayServerUrl,
                        relayHealthStatus = state.relayHealthStatus,
                        allowInsecureRelayHttp = state.allowInsecureRelayHttp,
                        debugQaToolsEnabled = state.debugQaToolsEnabled,
                        isPhysicalDevice = state.isPhysicalDevice,
                        onBack = { screen = Screen.MAIN },
                        onSaveRelayUrl = viewModel::saveRelayServerUrl,
                        onCheckRelay = viewModel::checkRelayServer,
                        onSynchronizeNow = viewModel::synchronizeInBackground,
                        onDeleteLocalData = viewModel::deleteLocalData,
                        onResetTestData = viewModel::resetTestDataForQa,
                    )
                }
            }
            if (state.legacyResetNotice) {
                AlertDialog(
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
                AlertDialog(
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

@Composable
private fun OnboardingScreen(onCreateProfile: (String) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
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
        Button(onClick = { onCreateProfile(displayName) }, enabled = displayName.isNotBlank()) {
            Text(stringResource(R.string.create_profile))
        }
    }
}

/**
 * DEC-023: the stored data cannot be used. For [LocalDataProblem.UNREADABLE] the user may delete
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
    val unreadable = problem == LocalDataProblem.UNREADABLE
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(if (unreadable) R.string.local_data_unreadable_title else R.string.local_data_temporary_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        SecurityWarning(
            stringResource(if (unreadable) R.string.local_data_unreadable_body else R.string.local_data_temporary_body),
        )
        Button(onClick = onCheckAgain, modifier = Modifier.testTag(CHECK_AGAIN_TAG)) {
            Text(stringResource(R.string.local_data_check_again))
        }
        if (unreadable) {
            OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.testTag(DELETE_AND_RESTART_TAG)) {
                Text(stringResource(R.string.local_data_delete_and_restart))
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
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

internal const val CHECK_AGAIN_TAG = "local_data_check_again"
internal const val DELETE_AND_RESTART_TAG = "local_data_delete_and_restart"
internal const val CONFIRM_DELETE_TAG = "local_data_confirm_delete"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotForMessagingScreen(onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
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
private fun SettingsScreen(
    relayServerUrl: String,
    relayHealthStatus: RelayHealthStatus?,
    allowInsecureRelayHttp: Boolean,
    debugQaToolsEnabled: Boolean,
    isPhysicalDevice: Boolean,
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
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.settings), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge) }
            item {
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
            item { Text(stringResource(R.string.relay_server), style = MaterialTheme.typography.titleLarge) }
            item {
                Text(stringResource(R.string.relay_server_explanation))
                OutlinedTextField(
                    value = relayUrlInput,
                    onValueChange = { relayUrlInput = it },
                    label = { Text(stringResource(R.string.relay_server_url)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.relay_server_example), style = MaterialTheme.typography.bodySmall)
                if (relayUrlInput.trim().startsWith("http://", ignoreCase = true)) {
                    SecurityWarning(
                        stringResource(
                            if (allowInsecureRelayHttp) R.string.http_relay_debug_warning
                            else R.string.https_relay_required,
                        ),
                    )
                    if (allowInsecureRelayHttp) {
                        Text(stringResource(R.string.http_debug_lan_only), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.use_https_for_different_networks), style = MaterialTheme.typography.bodySmall)
                    }
                } else if (relayTestMode(relayUrlInput) == RelayTestMode.PUBLIC_RELAY) {
                    Text(stringResource(R.string.public_relay_tunnel_notice), style = MaterialTheme.typography.bodySmall)
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
                Text(stringResource(R.string.background_sync_notice), style = MaterialTheme.typography.bodySmall)
            }
            item { Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.titleLarge) }
            item {
                OutlinedButton(onClick = { showDeleteDialog = true }) {
                    Text(stringResource(R.string.delete_local_data))
                }
            }
            if (debugQaToolsEnabled) {
                item {
                    Text(stringResource(R.string.debug_qa_tools_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.debug_reset_test_data_explanation))
                    OutlinedButton(
                        onClick = { showTestResetDialog = true },
                        modifier = Modifier.testTag("debug_reset_test_data"),
                    ) {
                        Text(stringResource(R.string.reset_test_data))
                    }
                }
            }
            item { Text(stringResource(R.string.no_secrets_logs), fontWeight = FontWeight.Bold) }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
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
        AlertDialog(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavigationTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
        actions = { actions() },
    )
}

@Composable
private fun relayHealthLabel(status: RelayHealthStatus): String = when (status) {
    RelayHealthStatus.AVAILABLE -> stringResource(R.string.relay_available)
    RelayHealthStatus.UNAVAILABLE -> stringResource(R.string.relay_unavailable)
    RelayHealthStatus.CONNECTION_ERROR -> stringResource(R.string.relay_connection_error)
}
