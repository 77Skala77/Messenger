package app.securemessenger.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.securemessenger.domain.Contact
import app.securemessenger.domain.ContactDevice
import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.DisplayMessage
import app.securemessenger.domain.MessageStatus
import app.securemessenger.domain.PairingStatus
import app.securemessenger.domain.VerificationStatus
import app.securemessenger.domain.pairingStatus
import app.securemessenger.transport.RelayHealthStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private enum class Screen {
    ONBOARDING,
    HOME,
    ADD_CONTACT,
    CONVERSATION,
    SECURITY,
    SETTINGS,
}

@Composable
fun SecureMessengerApp(viewModel: SecureMessengerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.ONBOARDING) }
    val snackbarHostState = remember { SnackbarHostState() }
    val invalidInvitation = stringResource(R.string.invalid_invitation)
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
            UiNotice.INVALID_INVITATION -> invalidInvitation
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
                when (screen) {
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
                                onBack = { screen = Screen.HOME },
                                onSecurity = { screen = Screen.SECURITY },
                                onSend = viewModel::sendMessage,
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
                        onBack = { screen = Screen.HOME },
                        onSaveRelayUrl = viewModel::saveRelayServerUrl,
                        onCheckRelay = viewModel::checkRelayServer,
                        onSynchronizeNow = viewModel::synchronizeNow,
                        onDeleteLocalData = {
                            viewModel.deleteLocalData()
                            screen = Screen.ONBOARDING
                        },
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
                    pairingStatusLabel(PairingStatus.WAITING_FOR_RETURN_INVITATION),
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                QrCode(invitationCode, stringResource(R.string.invitation_qr_content_description))
            }
            item {
                SelectionContainer { Text(invitationCode, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                OutlinedButton(
                    onClick = {
                        scanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt(scanPrompt)
                                .setBeepEnabled(false),
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
    onBack: () -> Unit,
    onSecurity: () -> Unit,
    onSend: (String) -> Unit,
) {
    var message by remember { mutableStateOf("") }
    val primaryDevice = devices.firstOrNull { it.identity.deviceId == contact.identity.deviceId }
    val deviceBlocksDelivery = primaryDevice == null || primaryDevice.approvalStatus in setOf(
        DeviceApprovalStatus.PENDING_APPROVAL,
        DeviceApprovalStatus.REVOKED,
        DeviceApprovalStatus.KEY_CHANGED,
    )
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
                    pairingStatusLabel(pairingStatus(contact, devices)),
                ),
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.pairing_ready_scope_notice), style = MaterialTheme.typography.bodySmall)
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
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt(scanPrompt)
                                .setBeepEnabled(false),
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
    onBack: () -> Unit,
    onSaveRelayUrl: (String) -> Unit,
    onCheckRelay: (String) -> Unit,
    onSynchronizeNow: () -> Unit,
    onDeleteLocalData: () -> Unit,
) {
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(LanguageController.selected(context)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var relayUrlInput by remember(relayServerUrl) { mutableStateOf(relayServerUrl) }
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.settings), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge) }
            item {
                LanguageChoice(stringResource(R.string.russian), selectedLanguage == AppLanguage.RUSSIAN) {
                    selectedLanguage = AppLanguage.RUSSIAN
                    LanguageController.select(context, selectedLanguage)
                }
                LanguageChoice(stringResource(R.string.english), selectedLanguage == AppLanguage.ENGLISH) {
                    selectedLanguage = AppLanguage.ENGLISH
                    LanguageController.select(context, selectedLanguage)
                }
            }
            item { Text(stringResource(R.string.encryption_suite), style = MaterialTheme.typography.titleLarge) }
            item {
                LanguageChoice(stringResource(R.string.standard_aead), selected = true) {}
                LanguageChoice(
                    stringResource(
                        R.string.experimental_suite_format,
                        stringResource(R.string.paranoia_c4_aead),
                        stringResource(R.string.experimental_mode),
                    ),
                    selected = false,
                    enabled = false,
                ) {}
                SecurityWarning(stringResource(R.string.c4_warning))
                Text(stringResource(R.string.c4_disabled), style = MaterialTheme.typography.bodySmall)
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
                Text(stringResource(R.string.app_lock), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.app_lock_placeholder))
            }
            item {
                OutlinedButton(onClick = { showDeleteDialog = true }) {
                    Text(stringResource(R.string.delete_local_data))
                }
            }
            item {
                Text(stringResource(R.string.export_diagnostics), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.diagnostics_notice))
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
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(label, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun QrCode(value: String, contentDescription: String) {
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
private fun SecurityWarning(text: String) {
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
private fun NavigationTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
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
    MessageStatus.RECEIVED -> stringResource(R.string.status_received)
    MessageStatus.FAILED -> stringResource(R.string.status_failed)
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
