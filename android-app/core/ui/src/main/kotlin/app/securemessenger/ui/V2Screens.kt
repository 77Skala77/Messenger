package app.securemessenger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2StoredMessage
import com.journeyapps.barcodescanner.ScanContract

private enum class V2Screen { HOME, INVITE, ADD, CONVERSATION, REQUEST_SAFETY }

/** The chat UI: the only conversation path (DEC-021), available in debug builds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationV2Root(
    onOpenSettings: () -> Unit,
    onLocalDataUnreadable: () -> Unit,
    viewModel: ConversationV2ViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(viewModel) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }
    // DEC-023: the vault key disappeared while the chat was open; the app re-checks and explains.
    LaunchedEffect(state.localDataUnreadable) {
        if (state.localDataUnreadable) onLocalDataUnreadable()
    }
    var screen by remember { mutableStateOf(V2Screen.HOME) }
    var requestForSafety by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val notices = mapOf(
        V2Notice.INVALID_CODE to stringResource(R.string.v2_notice_invalid_code),
        V2Notice.UNSUPPORTED_VERSION to stringResource(R.string.v2_notice_unsupported),
        V2Notice.NOT_FOUND to stringResource(R.string.v2_notice_not_found),
        V2Notice.EXPIRED to stringResource(R.string.v2_notice_expired),
        V2Notice.OWN_INVITATION to stringResource(R.string.v2_notice_own_invitation),
        V2Notice.ALREADY_PAIRED to stringResource(R.string.v2_notice_already_paired),
        V2Notice.REQUEST_SENT to stringResource(R.string.v2_notice_request_sent),
        V2Notice.RELAY_ERROR to stringResource(R.string.v2_notice_relay_error),
        V2Notice.FAILED to stringResource(R.string.v2_notice_failed),
        V2Notice.INVITATION_READY to stringResource(R.string.v2_notice_invitation_ready),
        V2Notice.BLOCKED to stringResource(R.string.v2_notice_blocked),
    )
    LaunchedEffect(state.notice) {
        val text = state.notice?.let(notices::get)
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.clearNotice()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding)) {
            when (screen) {
                V2Screen.HOME -> V2Home(
                    state = state,
                    onInvite = { screen = V2Screen.INVITE },
                    onAdd = { screen = V2Screen.ADD },
                    onOpen = {
                        viewModel.selectContact(it)
                        screen = V2Screen.CONVERSATION
                    },
                    onAccept = viewModel::acceptRequest,
                    onDecline = viewModel::declineRequest,
                    onSafety = {
                        requestForSafety = it
                        screen = V2Screen.REQUEST_SAFETY
                    },
                    onSync = viewModel::synchronizeNow,
                    onSettings = onOpenSettings,
                )
                V2Screen.INVITE -> V2Invite(
                    code = state.invitationCode,
                    onCreate = viewModel::createInvitation,
                    onBack = { screen = V2Screen.HOME },
                )
                V2Screen.ADD -> V2Add(
                    onAdd = { code, name ->
                        viewModel.addContactFromCode(code, name)
                        screen = V2Screen.HOME
                    },
                    onBack = { screen = V2Screen.HOME },
                )
                V2Screen.CONVERSATION -> {
                    val contact = state.contacts.firstOrNull { it.contactId == state.selectedContactId }
                    if (contact == null) {
                        screen = V2Screen.HOME
                    } else {
                        V2Conversation(
                            contact = contact,
                            messages = state.messages,
                            safetyNumber = state.safetyNumber,
                            onSend = viewModel::sendText,
                            onBack = {
                                viewModel.selectContact(null)
                                screen = V2Screen.HOME
                            },
                        )
                    }
                }
                V2Screen.REQUEST_SAFETY -> {
                    val request = state.requests.firstOrNull { it.request.requestId == requestForSafety }
                    V2SafetyNumber(request?.safetyNumber, onBack = { screen = V2Screen.HOME })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V2Home(
    state: ConversationV2UiState,
    onInvite: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onAccept: (String, String) -> Unit,
    onDecline: (String) -> Unit,
    onSafety: (String) -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.v2_title)) },
                actions = { TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SecurityWarning(stringResource(R.string.v2_qa_banner)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onInvite, modifier = Modifier.testTag("v2_my_invitation")) {
                        Text(stringResource(R.string.v2_my_invitation))
                    }
                    OutlinedButton(onClick = onAdd, modifier = Modifier.testTag("v2_add_contact")) {
                        Text(stringResource(R.string.v2_add_contact))
                    }
                }
            }
            item {
                OutlinedButton(onClick = onSync) { Text(stringResource(R.string.v2_sync_now)) }
                Text(stringResource(R.string.v2_background_sync_notice), style = MaterialTheme.typography.bodySmall)
            }
            if (state.requests.isNotEmpty()) {
                item { Text(stringResource(R.string.v2_requests_title), style = MaterialTheme.typography.titleLarge) }
                items(state.requests, key = { it.request.requestId }) { view ->
                    V2RequestCard(view, onAccept, onDecline, onSafety)
                }
            }
            if (state.contacts.isEmpty()) {
                item { Text(stringResource(R.string.v2_no_contacts)) }
            } else {
                items(state.contacts, key = { it.contactId }) { contact ->
                    Card(onClick = { onOpen(contact.contactId) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(contact.localDisplayName, fontWeight = FontWeight.Bold)
                            Text(stateLabel(contact.state))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V2RequestCard(
    view: V2RequestView,
    onAccept: (String, String) -> Unit,
    onDecline: (String) -> Unit,
    onSafety: (String) -> Unit,
) {
    var name by remember(view.request.requestId) { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.v2_request_claimed_name, view.request.claimedDisplayName.ifBlank { "-" }),
                fontWeight = FontWeight.Bold,
            )
            view.safetyNumber?.let { number ->
                Text(stringResource(R.string.v2_safety_number), fontWeight = FontWeight.Bold)
                SelectionContainer { Text(groupedDigits(number), style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.v2_safety_hint), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.v2_contact_name)) },
                modifier = Modifier.fillMaxWidth().testTag("v2_request_name"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAccept(view.request.requestId, name) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("v2_accept_request"),
                ) { Text(stringResource(R.string.v2_accept)) }
                OutlinedButton(onClick = { onDecline(view.request.requestId) }) {
                    Text(stringResource(R.string.v2_decline))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V2Invite(code: String?, onCreate: () -> Unit, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.v2_my_invitation), onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.v2_invitation_hint)) }
            item {
                Button(onClick = onCreate, modifier = Modifier.testTag("v2_create_invitation")) {
                    Text(stringResource(R.string.v2_create_invitation))
                }
            }
            if (code != null) {
                item { QrCode(code, stringResource(R.string.v2_invitation_qr_description)) }
                item {
                    SelectionContainer { Text(code, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(code)) },
                        modifier = Modifier.testTag("v2_copy_invitation"),
                    ) { Text(stringResource(R.string.copy_invitation_code)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V2Add(onAdd: (String, String) -> Unit, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val scanPrompt = stringResource(R.string.scan_prompt)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) code = result.contents
    }
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.v2_add_contact), onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedButton(onClick = { scanner.launch(portraitQrScanOptions(scanPrompt)) }) {
                    Text(stringResource(R.string.v2_scan_invitation))
                }
            }
            item {
                Text(stringResource(R.string.v2_paste_invitation), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.v2_invitation_code)) },
                    modifier = Modifier.fillMaxWidth().testTag("v2_code_input"),
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.v2_contact_name)) },
                    modifier = Modifier.fillMaxWidth().testTag("v2_name_input"),
                )
            }
            item {
                Button(
                    onClick = { onAdd(code, name) },
                    enabled = code.isNotBlank() && name.isNotBlank(),
                    modifier = Modifier.testTag("v2_submit_add"),
                ) { Text(stringResource(R.string.v2_add_contact)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V2Conversation(
    contact: V2Contact,
    messages: List<V2StoredMessage>,
    safetyNumber: String?,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Scaffold(topBar = { NavigationTopBar(contact.localDisplayName, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stateLabel(contact.state), fontWeight = FontWeight.Bold)
            safetyNumber?.let { number ->
                Text(stringResource(R.string.v2_safety_number), fontWeight = FontWeight.Bold)
                SelectionContainer { Text(groupedDigits(number), style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.v2_safety_hint), style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(messages, key = { it.messageId }) { message ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(message.text)
                            Text(statusLabel(message), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(R.string.v2_message_hint)) },
                    modifier = Modifier.weight(1f).testTag("v2_message_input"),
                )
                Button(
                    onClick = {
                        onSend(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank() && contact.state != V2ContactState.KEY_CHANGED,
                    modifier = Modifier.testTag("v2_send"),
                ) { Text(stringResource(R.string.v2_send)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V2SafetyNumber(number: String?, onBack: () -> Unit) {
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.v2_safety_number), onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            number?.let { SelectionContainer { Text(groupedDigits(it), style = MaterialTheme.typography.headlineSmall) } }
            Text(stringResource(R.string.v2_safety_hint))
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun stateLabel(state: V2ContactState): String = stringResource(
    when (state) {
        V2ContactState.AWAITING_ACCEPTANCE -> R.string.v2_state_awaiting
        V2ContactState.ACTIVE -> R.string.v2_state_active
        V2ContactState.KEY_CHANGED -> R.string.v2_state_key_changed
    },
)

/** "Delivered" only for an authenticated receipt; incoming messages are just received. */
@Composable
private fun statusLabel(message: V2StoredMessage): String = stringResource(
    when (message.status) {
        V2MessageStatus.PENDING, V2MessageStatus.FAILED -> R.string.status_pending
        V2MessageStatus.SENT -> R.string.status_sent
        V2MessageStatus.DELIVERED -> R.string.status_delivered
        V2MessageStatus.RECEIVED -> R.string.status_received
    },
)

/** 60 digits in groups of five: readable aloud and identical on both devices. */
private fun groupedDigits(number: String): String = number.filter(Char::isDigit).chunked(5).joinToString(" ")
