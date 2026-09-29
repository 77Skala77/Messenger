package app.securemessenger.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.securemessenger.domain.Contact
import app.securemessenger.domain.ContactDevice
import app.securemessenger.domain.DeliveryBlockReason
import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.DisplayMessage
import app.securemessenger.domain.MessageStatus
import app.securemessenger.domain.PairingStatus
import app.securemessenger.domain.VerificationStatus
import app.securemessenger.domain.deliveryBlockReason
import app.securemessenger.transport.RelayHealthStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract

private enum class Screen {
    ONBOARDING,
    HOME,
    ADD_CONTACT,
    CONVERSATION,
    SECURITY,
    SETTINGS,
    DIAGNOSTICS,
}

@Composable
fun SecureMessengerApp(viewModel: SecureMessengerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val appContext = LocalContext.current
    // Hidden debug QA switch (DEC-019): always false in release builds.
    val v2Enabled by V2QaSwitch.flow(appContext, state.debugQaToolsEnabled).collectAsState()
    var screen by remember { mutableStateOf(Screen.ONBOARDING) }
    val snackbarHostState = remember { SnackbarHostState() }
    val emptyContactName = stringResource(R.string.empty_contact_name)
    val emptyInvitationCode = stringResource(R.string.empty_invitation_code)
    val invalidInvitation = stringResource(R.string.invalid_invitation)
    val unsupportedInvitationVersion = stringResource(R.string.unsupported_invitation_version)
    val contactAdded = stringResource(R.string.contact_added)
    val deviceStaged = stringResource(R.string.device_staged_notice)
    val sessionUnavailable = stringResource(R.string.session_unavailable)
    val deliveryBlocked = stringResource(R.string.delivery_blocked_notice)
    val syncRequested = stringResource(R.string.sync_requested_notice)

    LaunchedEffect(state.profileCreated) {
        if (state.profileCreated && screen == Screen.ONBOARDING) screen = Screen.HOME
    }
    LaunchedEffect(state.notice) {
        val notice = when (state.notice) {
            UiNotice.EMPTY_CONTACT_NAME -> emptyContactName
            UiNotice.EMPTY_INVITATION_CODE -> emptyInvitationCode
            UiNotice.INVALID_OR_CORRUPTED_INVITATION -> invalidInvitation
            UiNotice.UNSUPPORTED_INVITATION_VERSION -> unsupportedInvitationVersion
            UiNotice.CONTACT_ADDED -> contactAdded
            UiNotice.DEVICE_STAGED -> deviceStaged
            UiNotice.SESSION_UNAVAILABLE -> sessionUnavailable
            UiNotice.DELIVERY_BLOCKED -> deliveryBlocked
            UiNotice.SYNC_REQUESTED -> syncRequested
            null -> null
        }
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    MaterialTheme {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Box(Modifier.padding(padding)) {
                val showV2 = v2Enabled && state.profileCreated &&
                    screen != Screen.SETTINGS && screen != Screen.DIAGNOSTICS && screen != Screen.ONBOARDING
                if (showV2) {
                    ConversationV2Root(
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onTurnOff = { V2QaSwitch.set(appContext, state.debugQaToolsEnabled, false) },
                    )
                } else when (screen) {
                    Screen.ONBOARDING -> OnboardingScreen(onCreateProfile = viewModel::createProfile)
                    Screen.HOME -> HomeScreen(
                        contacts = state.contacts,
                        onAddContact = { screen = Screen.ADD_CONTACT },
                        onSettings = { screen = Screen.SETTINGS },
                        onConversation = {
                            viewModel.selectContact(it)
                            screen = Screen.CONVERSATION
                        },
                    )
                    Screen.ADD_CONTACT -> AddContactScreen(
                        invitationCode = state.invitationCode,
                        onImport = viewModel::importInvitation,
                        onBack = { screen = Screen.HOME },
                    )
                    Screen.CONVERSATION -> {
                        val contact = state.contacts.firstOrNull { it.contactId == state.selectedContactId }
                        if (contact == null) {
                            screen = Screen.HOME
                        } else {
                            ConversationScreen(
                                contact = contact,
                                devices = state.devices[contact.contactId].orEmpty(),
                                messages = state.messages[contact.contactId].orEmpty(),
                                pairing = state.diagnostics.pairingStatus ?: PairingStatus.WAITING_FOR_RETURN_INVITATION,
                                onBack = { screen = Screen.HOME },
                                onSecurity = { screen = Screen.SECURITY },
                                onSend = viewModel::sendMessage,
                                onConfirmTwoWayPairing = viewModel::confirmTwoWayPairing,
                            )
                        }
                    }
                    Screen.SECURITY -> {
                        val contact = state.contacts.firstOrNull { it.contactId == state.selectedContactId }
                        if (contact == null) {
                            screen = Screen.HOME
                        } else {
                            ContactSecurityScreen(
                                contact = contact,
                                devices = state.devices[contact.contactId].orEmpty(),
                                onBack = { screen = Screen.CONVERSATION },
                                onVerify = viewModel::verifySelectedContact,
                                onStageDevice = viewModel::stageDeviceInvitation,
                                onApproveDevice = viewModel::approveDevice,
                                onRevokeDevice = viewModel::revokeDevice,
                            )
                        }
                    }
                    Screen.SETTINGS -> SettingsScreen(
                        relayServerUrl = state.relayServerUrl,
                        relayHealthStatus = state.relayHealthStatus,
                        allowInsecureRelayHttp = state.allowInsecureRelayHttp,
                        diagnostics = state.diagnostics,
                        debugQaToolsEnabled = state.debugQaToolsEnabled,
                        isPhysicalDeviceForQa = state.isPhysicalDeviceForQa,
                        preflightResult = state.preflightResult,
                        onBack = { screen = Screen.HOME },
                        onSaveRelayUrl = viewModel::saveRelayServerUrl,
                        onCheckRelay = viewModel::checkRelayServer,
                        onRunPreflight = viewModel::runTwoDevicePreflight,
                        onSynchronizeNow = viewModel::synchronizeNow,
                        onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                        onDeleteLocalData = {
                            viewModel.deleteLocalData()
                            screen = Screen.ONBOARDING
                        },
                        onResetTestData = {
                            viewModel.resetTestDataForQa()
                            screen = Screen.ONBOARDING
                        },
                    )
                    Screen.DIAGNOSTICS -> DiagnosticsScreen(
                        diagnostics = state.diagnostics,
                        onBack = { screen = Screen.SETTINGS },
                        onClear = viewModel::clearDiagnostics,
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    contacts: List<Contact>,
    onAddContact: () -> Unit,
    onSettings: () -> Unit,
    onConversation: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = { TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Button(onClick = onAddContact) { Text(stringResource(R.string.add_contact)) }
            Spacer(Modifier.height(20.dp))
            if (contacts.isEmpty()) {
                Text(stringResource(R.string.no_conversations))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(contacts, key = { it.contactId }) { contact ->
                        Card(onClick = { onConversation(contact.contactId) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(contact.localDisplayName, fontWeight = FontWeight.Bold)
                                Text(verificationLabel(contact.verificationStatus))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContactScreen(
    invitationCode: String,
    onImport: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var importedCode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val scanPrompt = stringResource(R.string.scan_prompt)
    val clipboardManager = LocalClipboardManager.current
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) importedCode = result.contents
    }
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.add_contact_title), onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.my_invitation), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.invitation_explanation))
            }
            item {
                Text(stringResource(R.string.two_way_pairing_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.two_way_pairing_explanation))
                Text(
                    stringResource(R.string.pairing_incomplete_instruction),
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Text(stringResource(R.string.show_invitation_qr), style = MaterialTheme.typography.titleMedium)
                QrCode(invitationCode, stringResource(R.string.invitation_qr_content_description))
            }
            item {
                SelectionContainer { Text(invitationCode, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(
                    onClick = { clipboardManager.setText(AnnotatedString(invitationCode)) },
                    modifier = Modifier.testTag("copy_invitation_code"),
                ) {
                    Text(stringResource(R.string.copy_invitation_code))
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        scanner.launch(
                            portraitQrScanOptions(scanPrompt),
                        )
                    },
                ) { Text(stringResource(R.string.scan_invitation)) }
            }
            item {
                Text(stringResource(R.string.paste_invitation), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = importedCode,
                    onValueChange = { importedCode = it },
                    label = { Text(stringResource(R.string.invitation_code)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.contact_local_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(onClick = { onImport(importedCode, name) }) {
                    Text(stringResource(R.string.import_invitation))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreen(
    contact: Contact,
    devices: List<ContactDevice>,
    messages: List<DisplayMessage>,
    pairing: PairingStatus,
    onBack: () -> Unit,
    onSecurity: () -> Unit,
    onSend: (String) -> Unit,
    onConfirmTwoWayPairing: () -> Unit,
) {
    var message by remember { mutableStateOf("") }
    val blockReason = deliveryBlockReason(contact, devices)
    val deviceBlocksDelivery = blockReason != null
    Scaffold(
        topBar = {
            NavigationTopBar(contact.localDisplayName, onBack) {
                TextButton(onClick = onSecurity) { Text(stringResource(R.string.security_screen_action)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Text(
                stringResource(
                    R.string.suite_value_format,
                    stringResource(R.string.encryption_suite),
                    stringResource(R.string.standard_aead),
                ),
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    R.string.pairing_status_format,
                    pairingStatusLabel(pairing),
                ),
                fontWeight = FontWeight.Bold,
            )
            when (pairing) {
                PairingStatus.WAITING_FOR_RETURN_INVITATION -> {
                    Text(stringResource(R.string.pairing_return_invitation_required), style = MaterialTheme.typography.bodySmall)
                    if (blockReason == null) {
                        OutlinedButton(onClick = onConfirmTwoWayPairing) {
                            Text(stringResource(R.string.confirm_two_way_pairing))
                        }
                    }
                }
                PairingStatus.READY_FOR_CONVERSATION ->
                    Text(stringResource(R.string.pairing_ready_confirmation), fontWeight = FontWeight.Bold)
                PairingStatus.DEVICE_BLOCKED -> Unit
            }
            if (blockReason != null) {
                SecurityWarning(deliveryBlockReasonLabel(blockReason))
            }
            if (contact.verificationStatus == VerificationStatus.KEY_CHANGED) {
                SecurityWarning(stringResource(R.string.key_changed_warning))
            } else if (contact.verificationStatus != VerificationStatus.VERIFIED) {
                SecurityWarning(stringResource(R.string.unverified_warning))
            }
            if (devices.any { it.approvalStatus == DeviceApprovalStatus.REVOKED }) {
                SecurityWarning(stringResource(R.string.revoked_device_warning))
            }
            Text(stringResource(R.string.relay_development_notice), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (messages.isEmpty()) item { Text(stringResource(R.string.empty_conversation)) }
                items(messages, key = { it.id }) { visibleMessage ->
                    MessageCard(visibleMessage)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.message_input)) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        onSend(message)
                        message = ""
                    },
                    enabled = message.isNotBlank() &&
                        contact.verificationStatus != VerificationStatus.KEY_CHANGED &&
                        !deviceBlocksDelivery,
                ) { Text(stringResource(R.string.send)) }
            }
        }
    }
}

@Composable
private fun MessageCard(message: DisplayMessage) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(message.plaintextInMemoryOnly)
            Text(messageStatusLabel(message.status), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactSecurityScreen(
    contact: Contact,
    devices: List<ContactDevice>,
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onStageDevice: (String) -> Unit,
    onApproveDevice: (ContactDevice) -> Unit,
    onRevokeDevice: (ContactDevice) -> Unit,
) {
    var verificationMismatch by remember(contact.contactId) { mutableStateOf(false) }
    var linkedInvitation by remember(contact.contactId) { mutableStateOf("") }
    val scanPrompt = stringResource(R.string.verification_scan_prompt)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            if (result.contents == contact.identity.fingerprint) {
                verificationMismatch = false
                onVerify()
            } else {
                verificationMismatch = true
            }
        }
    }
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.contact_security), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.security_status), fontWeight = FontWeight.Bold)
                Text(verificationLabel(contact.verificationStatus))
            }
            if (contact.verificationStatus == VerificationStatus.KEY_CHANGED) {
                item { SecurityWarning(stringResource(R.string.key_changed_warning)) }
            }
            item {
                Text(stringResource(R.string.safety_number), style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(contact.identity.fingerprint) }
                Text(stringResource(R.string.compare_safety_number))
            }
            item {
                Text(stringResource(R.string.qr_verification), style = MaterialTheme.typography.titleMedium)
                QrCode(contact.identity.fingerprint, stringResource(R.string.verification_qr_content_description))
                OutlinedButton(
                    onClick = {
                        scanner.launch(
                            portraitQrScanOptions(scanPrompt),
                        )
                    },
                ) { Text(stringResource(R.string.scan_verification)) }
            }
            if (verificationMismatch) {
                item { SecurityWarning(stringResource(R.string.verification_mismatch)) }
            }
            item {
                Button(onClick = onVerify) { Text(stringResource(R.string.mark_verified)) }
                Text(stringResource(R.string.devices), style = MaterialTheme.typography.titleMedium)
            }
            items(devices, key = { it.identity.deviceId }) { device ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.device_id_format, device.identity.deviceId))
                        Text(deviceApprovalLabel(device.approvalStatus), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.safety_number), style = MaterialTheme.typography.labelMedium)
                        SelectionContainer { Text(device.identity.fingerprint) }
                        if (device.approvalStatus == DeviceApprovalStatus.PENDING_APPROVAL) {
                            SecurityWarning(stringResource(R.string.new_device_warning))
                            Button(onClick = { onApproveDevice(device) }) {
                                Text(stringResource(R.string.approve_device))
                            }
                        }
                        if (device.approvalStatus == DeviceApprovalStatus.KEY_CHANGED) {
                            SecurityWarning(stringResource(R.string.key_changed_warning))
                        }
                        if (device.approvalStatus == DeviceApprovalStatus.REVOKED) {
                            SecurityWarning(stringResource(R.string.revoked_device_warning))
                        } else {
                            OutlinedButton(onClick = { onRevokeDevice(device) }) {
                                Text(stringResource(R.string.revoke_device))
                            }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.link_device_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.link_device_explanation))
                OutlinedTextField(
                    value = linkedInvitation,
                    onValueChange = { linkedInvitation = it },
                    label = { Text(stringResource(R.string.invitation_code)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onStageDevice(linkedInvitation); linkedInvitation = "" },
                    enabled = linkedInvitation.isNotBlank(),
                ) { Text(stringResource(R.string.stage_device)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    relayServerUrl: String,
    relayHealthStatus: RelayHealthStatus?,
    allowInsecureRelayHttp: Boolean,
    diagnostics: SafeDiagnosticsSnapshot,
    debugQaToolsEnabled: Boolean,
    isPhysicalDeviceForQa: Boolean,
    preflightResult: TwoDevicePreflightResult?,
    onBack: () -> Unit,
    onSaveRelayUrl: (String) -> Unit,
    onCheckRelay: (String) -> Unit,
    onRunPreflight: () -> Unit,
    onSynchronizeNow: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onDeleteLocalData: () -> Unit,
    onResetTestData: () -> Unit,
) {
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(LanguageController.selected(context)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTestResetDialog by remember { mutableStateOf(false) }
    var relayUrlInput by remember(relayServerUrl) { mutableStateOf(relayServerUrl) }
    val addressWarning = relayClientAddressWarning(relayUrlInput, isPhysicalDeviceForQa)
    val typedRelayMode = relayTestMode(relayUrlInput)
    val savedRelayMode = relayTestMode(diagnostics.relayUrl)
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
            item { Text(stringResource(R.string.encryption_suite), style = MaterialTheme.typography.titleLarge) }
            item {
                Column(Modifier.selectableGroup()) {
                    SelectableOptionRow(
                        label = stringResource(R.string.standard_aead),
                        selected = true,
                        modifier = Modifier.testTag("encryption_option_standard"),
                    ) {}
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
                } else if (relayUrlInput.trim().startsWith("https://", ignoreCase = true)) {
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
            item { Text(stringResource(R.string.qa_preflight_title), style = MaterialTheme.typography.titleLarge) }
            item {
                Text(stringResource(R.string.qa_preflight_explanation))
                DiagnosticRow(
                    stringResource(R.string.diagnostics_relay_url),
                    diagnostics.relayUrl.ifBlank { stringResource(R.string.diagnostics_none) },
                )
                DiagnosticRow(
                    stringResource(R.string.qa_preflight_relay_mode),
                    relayModeLabel(savedRelayMode ?: typedRelayMode),
                )
                DiagnosticRow(
                    stringResource(R.string.diagnostics_health),
                    diagnostics.relayHealthStatus?.let { relayHealthLabel(it) }
                        ?: stringResource(R.string.diagnostics_none),
                )
                if (savedRelayMode == RelayTestMode.PUBLIC_RELAY &&
                    diagnostics.relayHealthStatus == RelayHealthStatus.AVAILABLE
                ) {
                    Text(stringResource(R.string.public_relay_available), fontWeight = FontWeight.Bold)
                }
                DiagnosticRow(stringResource(R.string.diagnostics_contacts), diagnostics.contactCount.toString())
                DiagnosticRow(
                    stringResource(R.string.diagnostics_pairing),
                    diagnostics.pairingStatus?.let { pairingStatusLabel(it) }
                        ?: stringResource(R.string.diagnostics_none),
                )
                DiagnosticRow(
                    stringResource(R.string.diagnostics_primary_device),
                    diagnostics.primaryDeviceState?.let { deviceApprovalLabel(it) }
                        ?: stringResource(R.string.diagnostics_none),
                )
                DiagnosticRow(stringResource(R.string.diagnostics_pending), diagnostics.pendingOutgoingCount.toString())
                DiagnosticRow(stringResource(R.string.diagnostics_failed), diagnostics.failedOutgoingCount.toString())
                DiagnosticRow(
                    stringResource(R.string.diagnostics_next_retry),
                    diagnostics.nextRetryAtEpochMillis?.toString() ?: stringResource(R.string.diagnostics_none),
                )
                DiagnosticRow(
                    stringResource(R.string.diagnostics_work_status),
                    workSyncStatusLabel(diagnostics.workSyncStatus),
                )
                Button(onClick = onRunPreflight, modifier = Modifier.testTag("run_two_device_preflight")) {
                    Text(stringResource(R.string.run_qa_preflight))
                }
                if (preflightResult != null) {
                    SecurityWarning(twoDevicePreflightLabel(preflightResult, savedRelayMode))
                }
            }
            item { Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.titleLarge) }
            item {
                Text(stringResource(R.string.app_lock), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.app_lock_placeholder))
            }
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
                item { V2QaSwitchRow(debugQaToolsEnabled) }
            }
            item {
                Text(stringResource(R.string.export_diagnostics), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.diagnostics_safe_notice))
                Button(onClick = onOpenDiagnostics) {
                    Text(stringResource(R.string.open_delivery_diagnostics))
                }
                Text(stringResource(R.string.no_secrets_logs), fontWeight = FontWeight.Bold)
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsScreen(
    diagnostics: SafeDiagnosticsSnapshot,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val report = diagnosticReport(diagnostics)
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.diagnostics_title), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SecurityWarning(stringResource(R.string.diagnostics_safe_notice)) }
            item { DiagnosticRow(stringResource(R.string.diagnostics_relay_url), diagnostics.relayUrl) }
            item {
                DiagnosticRow(
                    stringResource(R.string.diagnostics_health),
                    diagnostics.relayHealthStatus?.let { relayHealthLabel(it) } ?: stringResource(R.string.diagnostics_none),
                )
            }
            item {
                DiagnosticRow(
                    stringResource(R.string.diagnostics_last_sync),
                    diagnostics.lastSuccessfulSyncAtEpochMillis?.let(DiagnosticsTimeFormatter::format)
                        ?: stringResource(R.string.diagnostics_none),
                )
            }
            item { DiagnosticRow(stringResource(R.string.diagnostics_pending), diagnostics.pendingOutgoingCount.toString()) }
            item { DiagnosticRow(stringResource(R.string.diagnostics_failed), diagnostics.failedOutgoingCount.toString()) }
            item {
                DiagnosticRow(
                    stringResource(R.string.diagnostics_next_retry),
                    diagnostics.nextRetryAtEpochMillis?.let(DiagnosticsTimeFormatter::format)
                        ?: stringResource(R.string.diagnostics_none),
                )
            }
            item { DiagnosticRow(stringResource(R.string.diagnostics_contacts), diagnostics.contactCount.toString()) }
            item {
                DiagnosticRow(
                    stringResource(R.string.diagnostics_local_queue),
                    diagnostics.localQueueFingerprint ?: stringResource(R.string.diagnostics_none),
                )
            }
            item {
                DiagnosticRow(
                    stringResource(R.string.diagnostics_contact_queue),
                    diagnostics.selectedContactQueueFingerprint ?: stringResource(R.string.diagnostics_none),
                )
            }
            item {
                DiagnosticRow(
                    stringResource(R.string.diagnostics_pairing),
                    diagnostics.pairingStatus?.let { pairingStatusLabel(it) } ?: stringResource(R.string.diagnostics_none),
                )
            }
            item {
                DiagnosticRow(
                    stringResource(R.string.diagnostics_primary_device),
                    diagnostics.primaryDeviceState?.let { deviceApprovalLabel(it) } ?: stringResource(R.string.diagnostics_none),
                )
            }
            item { DiagnosticRow(stringResource(R.string.diagnostics_work_status), workSyncStatusLabel(diagnostics.workSyncStatus)) }
            item { DiagnosticRow(stringResource(R.string.diagnostics_last_error), lastErrorLabel(diagnostics.lastError)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clipboardManager.setText(AnnotatedString(report)) }) {
                        Text(stringResource(R.string.copy_diagnostic_report))
                    }
                    OutlinedButton(onClick = onClear) {
                        Text(stringResource(R.string.clear_diagnostics))
                    }
                }
            }
            item { Text(stringResource(R.string.diagnostics_events), style = MaterialTheme.typography.titleMedium) }
            if (diagnostics.events.isEmpty()) {
                item { Text(stringResource(R.string.diagnostics_none)) }
            } else {
                DeliveryEventGroup.entries.forEach { group ->
                    val groupEvents = diagnostics.events.filter { DeliveryDiagnosticsClassifier.group(it.type) == group }
                    if (groupEvents.isNotEmpty()) {
                        item { Text(deliveryEventGroupLabel(group), style = MaterialTheme.typography.titleSmall) }
                        items(groupEvents) { event ->
                            DiagnosticRow(DiagnosticsTimeFormatter.format(event.timestampEpochMillis), deliveryEventLabel(event.type))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value)
    }
}

@Composable
private fun diagnosticReport(diagnostics: SafeDiagnosticsSnapshot): String = SafeDiagnosticReport.render(
    snapshot = diagnostics,
    labels = DiagnosticReportLabels(
        title = stringResource(R.string.diagnostics_title),
        safetyNotice = stringResource(R.string.diagnostics_safe_notice),
        relayUrl = stringResource(R.string.diagnostics_relay_url),
        relayHealth = stringResource(R.string.diagnostics_health),
        lastSuccessfulSync = stringResource(R.string.diagnostics_last_sync),
        pendingOutgoing = stringResource(R.string.diagnostics_pending),
        failedOutgoing = stringResource(R.string.diagnostics_failed),
        nextRetryAt = stringResource(R.string.diagnostics_next_retry),
        contacts = stringResource(R.string.diagnostics_contacts),
        localQueue = stringResource(R.string.diagnostics_local_queue),
        contactQueue = stringResource(R.string.diagnostics_contact_queue),
        pairing = stringResource(R.string.diagnostics_pairing),
        primaryDevice = stringResource(R.string.diagnostics_primary_device),
        workSync = stringResource(R.string.diagnostics_work_status),
        lastError = stringResource(R.string.diagnostics_last_error),
        events = stringResource(R.string.diagnostics_events),
        unavailable = stringResource(R.string.diagnostics_none),
    ),
    relayHealthValue = diagnostics.relayHealthStatus?.let { relayHealthLabel(it) }
        ?: stringResource(R.string.diagnostics_none),
    pairingValue = diagnostics.pairingStatus?.let { pairingStatusLabel(it) }
        ?: stringResource(R.string.diagnostics_none),
    primaryDeviceValue = diagnostics.primaryDeviceState?.let { deviceApprovalLabel(it) }
        ?: stringResource(R.string.diagnostics_none),
    workSyncValue = workSyncStatusLabel(diagnostics.workSyncStatus),
    lastErrorValue = lastErrorLabel(diagnostics.lastError),
    eventValues = DeliveryTechnicalEvent.entries.associateWith { deliveryEventLabel(it) },
)

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
private fun deviceApprovalLabel(status: DeviceApprovalStatus): String = when (status) {
    DeviceApprovalStatus.PRIMARY_UNVERIFIED -> stringResource(R.string.unverified)
    DeviceApprovalStatus.VERIFIED -> stringResource(R.string.verified)
    DeviceApprovalStatus.PENDING_APPROVAL -> stringResource(R.string.pending_approval)
    DeviceApprovalStatus.KEY_CHANGED -> stringResource(R.string.key_changed)
    DeviceApprovalStatus.REVOKED -> stringResource(R.string.device_revoked)
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
private fun verificationLabel(status: VerificationStatus): String = when (status) {
    VerificationStatus.VERIFIED -> stringResource(R.string.verified)
    VerificationStatus.UNVERIFIED -> stringResource(R.string.unverified)
    VerificationStatus.KEY_CHANGED -> stringResource(R.string.key_changed)
    VerificationStatus.UNKNOWN -> stringResource(R.string.unknown)
}

@Composable
private fun messageStatusLabel(status: MessageStatus): String = when (status) {
    MessageStatus.PENDING -> stringResource(R.string.status_pending)
    MessageStatus.SENT -> stringResource(R.string.status_sent)
    MessageStatus.DELIVERED -> stringResource(R.string.status_delivered)
    MessageStatus.RECEIVED -> stringResource(R.string.status_received)
    MessageStatus.FAILED -> stringResource(R.string.status_failed)
    MessageStatus.WAITING_FOR_PAIRING -> stringResource(R.string.status_waiting_pairing)
}

@Composable
private fun pairingStatusLabel(status: PairingStatus): String = when (status) {
    PairingStatus.WAITING_FOR_RETURN_INVITATION -> stringResource(R.string.pairing_waiting)
    PairingStatus.READY_FOR_CONVERSATION -> stringResource(R.string.pairing_ready)
    PairingStatus.DEVICE_BLOCKED -> stringResource(R.string.pairing_blocked)
}

@Composable
private fun relayHealthLabel(status: RelayHealthStatus): String = when (status) {
    RelayHealthStatus.AVAILABLE -> stringResource(R.string.relay_available)
    RelayHealthStatus.UNAVAILABLE -> stringResource(R.string.relay_unavailable)
    RelayHealthStatus.CONNECTION_ERROR -> stringResource(R.string.relay_connection_error)
}

@Composable
private fun relayModeLabel(mode: RelayTestMode?): String = when (mode) {
    RelayTestMode.LAN_RELAY -> stringResource(R.string.relay_mode_lan)
    RelayTestMode.PUBLIC_RELAY -> stringResource(R.string.relay_mode_public)
    null -> stringResource(R.string.diagnostics_none)
}

@Composable
private fun twoDevicePreflightLabel(result: TwoDevicePreflightResult, relayMode: RelayTestMode?): String = when (result) {
    TwoDevicePreflightResult.READY -> stringResource(
        if (relayMode == RelayTestMode.PUBLIC_RELAY) R.string.qa_preflight_https_ready else R.string.qa_preflight_ready,
    )
    TwoDevicePreflightResult.RELAY_URL_MISSING -> stringResource(R.string.qa_preflight_missing_relay)
    TwoDevicePreflightResult.PHONE_LOOPBACK_URL -> stringResource(R.string.qa_preflight_phone_loopback)
    TwoDevicePreflightResult.UNSPECIFIED_RELAY_URL -> stringResource(R.string.qa_preflight_unspecified_relay)
    TwoDevicePreflightResult.RELAY_UNAVAILABLE -> stringResource(R.string.qa_preflight_relay_unavailable)
    TwoDevicePreflightResult.PAIRING_INCOMPLETE -> stringResource(R.string.qa_preflight_pairing_incomplete)
    TwoDevicePreflightResult.DEVICE_BLOCKED -> stringResource(R.string.qa_preflight_device_blocked)
    TwoDevicePreflightResult.RETRY_PENDING -> stringResource(R.string.qa_preflight_retry_pending)
}

@Composable
private fun deliveryBlockReasonLabel(reason: DeliveryBlockReason): String = when (reason) {
    DeliveryBlockReason.MISSING_PRIMARY_DEVICE -> stringResource(R.string.delivery_block_missing_primary)
    DeliveryBlockReason.PENDING_APPROVAL -> stringResource(R.string.delivery_block_pending_approval)
    DeliveryBlockReason.REVOKED -> stringResource(R.string.delivery_block_revoked)
    DeliveryBlockReason.KEY_CHANGED -> stringResource(R.string.delivery_block_key_changed)
}

@Composable
private fun workSyncStatusLabel(status: WorkSyncStatus): String = when (status) {
    WorkSyncStatus.UNKNOWN -> stringResource(R.string.work_sync_unknown)
    WorkSyncStatus.SCHEDULED -> stringResource(R.string.work_sync_scheduled)
    WorkSyncStatus.RUNNING -> stringResource(R.string.work_sync_running)
    WorkSyncStatus.SUCCEEDED -> stringResource(R.string.work_sync_succeeded)
    WorkSyncStatus.RETRY_PENDING -> stringResource(R.string.work_sync_retry_pending)
}

@Composable
private fun lastErrorLabel(error: DeliveryLastError): String = when (error) {
    DeliveryLastError.NONE -> stringResource(R.string.last_error_none)
    DeliveryLastError.RELAY_UNAVAILABLE -> stringResource(R.string.last_error_relay_unavailable)
    DeliveryLastError.PAIRING_INCOMPLETE -> stringResource(R.string.last_error_pairing_incomplete)
    DeliveryLastError.DEVICE_BLOCKED -> stringResource(R.string.last_error_device_blocked)
}

@Composable
private fun deliveryEventGroupLabel(group: DeliveryEventGroup): String = when (group) {
    DeliveryEventGroup.RELAY -> stringResource(R.string.event_group_relay)
    DeliveryEventGroup.PAIRING -> stringResource(R.string.event_group_pairing)
    DeliveryEventGroup.OUTGOING -> stringResource(R.string.event_group_outgoing)
    DeliveryEventGroup.INCOMING -> stringResource(R.string.event_group_incoming)
    DeliveryEventGroup.RETRY -> stringResource(R.string.event_group_retry)
    DeliveryEventGroup.RECEIPTS -> stringResource(R.string.event_group_receipts)
    DeliveryEventGroup.ERRORS -> stringResource(R.string.event_group_errors)
}

@Composable
private fun deliveryEventLabel(type: DeliveryTechnicalEvent): String = when (type) {
    DeliveryTechnicalEvent.RELAY_HEALTH_OK -> stringResource(R.string.event_relay_health_ok)
    DeliveryTechnicalEvent.RELAY_HEALTH_FAILED -> stringResource(R.string.event_relay_health_failed)
    DeliveryTechnicalEvent.OUTGOING_QUEUED -> stringResource(R.string.event_outgoing_queued)
    DeliveryTechnicalEvent.OUTGOING_SENT -> stringResource(R.string.event_outgoing_sent)
    DeliveryTechnicalEvent.OUTGOING_RETRY_SCHEDULED -> stringResource(R.string.event_outgoing_retry_scheduled)
    DeliveryTechnicalEvent.OUTGOING_RETRY_STARTED -> stringResource(R.string.event_outgoing_retry_started)
    DeliveryTechnicalEvent.OUTGOING_RETRY_SUCCEEDED -> stringResource(R.string.event_outgoing_retry_succeeded)
    DeliveryTechnicalEvent.OUTGOING_RETRY_FAILED -> stringResource(R.string.event_outgoing_retry_failed)
    DeliveryTechnicalEvent.DELIVERY_RECEIPT_QUEUED -> stringResource(R.string.event_delivery_receipt_queued)
    DeliveryTechnicalEvent.DELIVERY_RECEIPT_SENT -> stringResource(R.string.event_delivery_receipt_sent)
    DeliveryTechnicalEvent.DELIVERY_RECEIPT_RECEIVED -> stringResource(R.string.event_delivery_receipt_received)
    DeliveryTechnicalEvent.DELIVERY_RECEIPT_DUPLICATE_SUPPRESSED ->
        stringResource(R.string.event_delivery_receipt_duplicate_suppressed)
    DeliveryTechnicalEvent.DELIVERY_STATUS_UPDATED -> stringResource(R.string.event_delivery_status_updated)
    DeliveryTechnicalEvent.INCOMING_FETCH_STARTED -> stringResource(R.string.event_incoming_fetch_started)
    DeliveryTechnicalEvent.INCOMING_ACCEPTED -> stringResource(R.string.event_incoming_accepted)
    DeliveryTechnicalEvent.INCOMING_DUPLICATE_SUPPRESSED ->
        stringResource(R.string.event_incoming_duplicate_suppressed)
    DeliveryTechnicalEvent.DEVICE_BLOCKED_SEND -> stringResource(R.string.event_device_blocked_send)
    DeliveryTechnicalEvent.PAIRING_INCOMPLETE -> stringResource(R.string.event_pairing_incomplete)
    DeliveryTechnicalEvent.PAIRING_READY -> stringResource(R.string.event_pairing_ready)
    DeliveryTechnicalEvent.MANUAL_SYNC_STARTED -> stringResource(R.string.event_manual_sync_started)
    DeliveryTechnicalEvent.MANUAL_SYNC_FINISHED -> stringResource(R.string.event_manual_sync_finished)
}
