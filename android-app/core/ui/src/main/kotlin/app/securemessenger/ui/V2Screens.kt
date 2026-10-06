package app.securemessenger.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import com.journeyapps.barcodescanner.ScanContract
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay

private enum class V2Screen { HOME, INVITE, ADD, CONVERSATION, REQUEST_REVIEW, CONTACT_SAFETY }

/** The chat UI: the only conversation path (DEC-021), available in debug builds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationV2Root(
    onOpenSettings: () -> Unit,
    onLocalDataUnreadable: () -> Unit,
    viewModel: ConversationV2ViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Fetching every few seconds only while the app is on screen. In the background the periodic
    // sync takes over and announces new messages (DEC-031); a fetch here would take them unannounced.
    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }
    // DEC-023: the vault key disappeared while the chat was open; the app re-checks and explains.
    LaunchedEffect(state.localDataUnreadable) {
        if (state.localDataUnreadable) onLocalDataUnreadable()
    }
    // The screens of the chat flow as a stack (DEC-033): the chat list at the bottom; the new chat,
    // add contact, a conversation, a request's comparison and a safety number open over it.
    val nav = rememberNavStack(V2Screen.HOME)
    var reviewedRequestId by remember { mutableStateOf<String?>(null) }
    // One step back, for the app's own Back button, for Android's system Back (button or gesture)
    // and for the swipe back alike, so they can never disagree (TQ-109): it closes the top screen.
    // On the list itself the handler is off and the system behaves as usual.
    val goBack: () -> Unit = { nav.pop() }
    BackHandler(enabled = nav.canPop, onBack = goBack)
    // The conversation is deselected once the list is back and its closing animation is over: the
    // closing screen keeps showing its contact and messages while it slides out.
    LaunchedEffect(nav.top) {
        if (nav.top != V2Screen.HOME) return@LaunchedEffect
        delay(TelegramMotion.SCREEN_MILLIS + DESELECT_AFTER_MILLIS)
        if (nav.top == V2Screen.HOME && viewModel.state.value.selectedContactId != null) viewModel.selectContact(null)
    }
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
    // A tapped notification opens its chat, once the contact is known; an unknown ID is dropped.
    val chatToOpen by V2OpenChat.requests.collectAsState()
    LaunchedEffect(chatToOpen, state.contacts, state.ready) {
        val chatId = chatToOpen ?: return@LaunchedEffect
        if (state.contacts.any { it.contactId == chatId }) {
            viewModel.selectContact(chatId)
            nav.set(listOf(V2Screen.HOME, V2Screen.CONVERSATION))
            V2OpenChat.opened()
        } else if (state.ready) {
            V2OpenChat.opened()
        }
    }
    // The scanner compares the safety number right after adding, while both people are together
    // (owner decision, DEC-030). Back leads to the new chat; comparing later stays possible.
    LaunchedEffect(state.newContactId) {
        val contactId = state.newContactId ?: return@LaunchedEffect
        if (nav.top == V2Screen.HOME) {
            viewModel.selectContact(contactId)
            nav.set(listOf(V2Screen.HOME, V2Screen.CONVERSATION, V2Screen.CONTACT_SAFETY))
        }
        viewModel.newContactShown()
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        TelegramScreenStack(stack = nav.items, onSwipeBack = goBack, modifier = Modifier.padding(padding)) { screen ->
            when (screen) {
                V2Screen.HOME -> V2Home(
                    state = state,
                    onInvite = { nav.push(V2Screen.INVITE) },
                    onAdd = { nav.push(V2Screen.ADD) },
                    onOpen = {
                        viewModel.selectContact(it)
                        nav.push(V2Screen.CONVERSATION)
                    },
                    onReview = {
                        reviewedRequestId = it
                        nav.push(V2Screen.REQUEST_REVIEW)
                    },
                    onDecline = viewModel::declineRequest,
                    onRefresh = viewModel::synchronizeNow,
                    onSettings = onOpenSettings,
                )
                V2Screen.INVITE -> V2Invite(
                    code = state.invitationCode,
                    onCreate = viewModel::createInvitation,
                    onBack = goBack,
                    upload = state.invitationUpload,
                    busy = state.invitationBusy,
                    onRetry = viewModel::retryInvitationUpload,
                )
                V2Screen.ADD -> V2Add(
                    onAdd = { code, name ->
                        viewModel.addContactFromCode(code, name)
                        nav.popToRoot()
                    },
                    onBack = goBack,
                )
                V2Screen.CONVERSATION -> {
                    val visible = LocalScreenVisible.current
                    val live = state.contacts.firstOrNull { it.contactId == state.selectedContactId }
                    // The contact stays on show while the screen slides out, even after it was deselected.
                    val contact = rememberLatest(live)
                    if (live == null && contact != null && visible && nav.top == V2Screen.CONVERSATION) {
                        LaunchedEffect(Unit) { nav.popToRoot() }
                    }
                    if (contact != null) {
                        // Its texts count as seen only while it is on screen with the app in front (DEC-029).
                        if (visible) {
                            LifecycleResumeEffect(viewModel) {
                                viewModel.setConversationVisible(true)
                                onPauseOrDispose { viewModel.setConversationVisible(false) }
                            }
                        }
                        V2Conversation(
                            contact = contact,
                            messages = state.messages,
                            onSend = viewModel::sendText,
                            onBack = goBack,
                            onOpenSafety = { nav.push(V2Screen.CONTACT_SAFETY) },
                        )
                    }
                }
                V2Screen.REQUEST_REVIEW -> {
                    val visible = LocalScreenVisible.current
                    val live = state.requests.firstOrNull { it.request.requestId == reviewedRequestId }
                    val request = rememberLatest(live)
                    if (live == null && request != null && visible && nav.top == V2Screen.REQUEST_REVIEW) {
                        // Accepted, declined, or gone: back to the list.
                        LaunchedEffect(Unit) { nav.popToRoot() }
                    }
                    if (request != null) {
                        V2RequestReview(
                            view = request,
                            onAccept = { requestId, name, compared ->
                                viewModel.acceptRequest(requestId, name, compared)
                                nav.popToRoot()
                            },
                            onDecline = { requestId ->
                                viewModel.declineRequest(requestId)
                                nav.popToRoot()
                            },
                            onBack = goBack,
                        )
                    }
                }
                V2Screen.CONTACT_SAFETY -> V2SafetyNumber(
                    state.safetyNumber,
                    onBack = goBack,
                    contact = rememberLatest(state.contacts.firstOrNull { it.contactId == state.selectedContactId }),
                    onMarkVerified = viewModel::markVerified,
                    onClearVerified = viewModel::clearVerified,
                )
            }
        }
    }
}

/** How long after the closing animation the list waits before it forgets the conversation. */
private const val DESELECT_AFTER_MILLIS = 100L

/**
 * [value] while there is one; once it is gone, the last one there was. A screen that slides out
 * after its data disappeared keeps showing what it showed.
 */
@Composable
private fun <T : Any> rememberLatest(value: T?): T? {
    val last = remember { mutableStateOf<T?>(null) }
    SideEffect { if (value != null) last.value = value }
    return value ?: last.value
}

/**
 * The chat list (step 6d): the test banner, contact requests on top, then one row per chat with
 * the newest activity first, a round button for new chats, and pull down to fetch now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V2Home(
    state: ConversationV2UiState,
    onInvite: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onReview: (String) -> Unit,
    onDecline: (String) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    val chats = remember(state.chats) { chatListOrder(state.chats) }
    val nowMillis = System.currentTimeMillis()
    val listState = rememberLazyListState()
    // The round button hides while the list is scrolled down and returns when it is scrolled up (Telegram's `hideFloatingButton`).
    var fabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            val down = index > previousIndex || (index == previousIndex && offset > previousOffset + 1)
            val up = index < previousIndex || (index == previousIndex && offset < previousOffset - 1)
            if (down) fabVisible = false else if (up || (index == 0 && offset == 0)) fabVisible = true
            previousIndex = index
            previousOffset = offset
        }
    }
    Scaffold(
        topBar = {
            TelegramTopBar(
                title = stringResource(R.string.v2_title),
                actions = { HomeMenu(onSettings) },
            )
        },
        floatingActionButton = { NewChatFab(onInvite = onInvite, onAdd = onAdd, visible = fabVisible) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // The bottom padding keeps the last row clear of the round button.
            LazyColumn(
                Modifier.fillMaxSize().testTag(CHAT_LIST_TAG),
                state = listState,
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                item { SecurityWarning(stringResource(R.string.v2_qa_banner)) }
                item { NotificationPromptCard(hasConversations = chats.isNotEmpty() || state.requests.isNotEmpty()) }
                if (state.requests.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.v2_requests_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(state.requests, key = { it.request.requestId }) { view ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                            V2RequestCard(view, onReview, onDecline)
                        }
                    }
                } else if (chats.isEmpty()) {
                    item { EmptyChatList() }
                }
                items(chats, key = { it.contact.contactId }) { chat ->
                    // A chat that gets a message slides to the top, and rows appear and go with a fade,
                    // by Telegram's `DialogsItemAnimator` (DEC-033).
                    ChatListRow(
                        chat,
                        nowMillis,
                        onClick = { onOpen(chat.contact.contactId) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(TelegramMotion.DIALOG_FADE_MILLIS, easing = TelegramMotion.Decelerate),
                            placementSpec = tween(TelegramMotion.LIST_MOVE_MILLIS, easing = TelegramMotion.Decelerate),
                            fadeOutSpec = tween(TelegramMotion.DIALOG_FADE_MILLIS, easing = TelegramMotion.Decelerate),
                        ),
                    )
                }
                item {
                    Text(
                        stringResource(R.string.v2_background_sync_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.messenger.hint,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

internal const val HOME_MENU_TAG = "home_menu"
internal const val MENU_SETTINGS_TAG = "menu_settings"

/** The three dots at the end of the main top bar; their menu leads to the settings. */
@Composable
internal fun HomeMenu(onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TopBarAction(
            onClick = { open = true },
            description = stringResource(R.string.more_options),
            modifier = Modifier.testTag(HOME_MENU_TAG),
        ) { MoreIcon(MaterialTheme.colorScheme.onSurfaceVariant) }
        TelegramPopupMenu(expanded = open, onDismissRequest = { open = false }, itemCount = 1) {
            Item(
                index = 0,
                text = stringResource(R.string.settings),
                onClick = {
                    open = false
                    onSettings()
                },
                modifier = Modifier.testTag(MENU_SETTINGS_TAG),
            )
        }
    }
}

internal const val REVIEW_REQUEST_TAG = "v2_review_request"
internal const val DECLINE_REQUEST_TAG = "v2_decline_request"
internal const val REQUEST_NAME_TAG = "v2_request_name"
internal const val ACCEPT_MATCHING_TAG = "v2_accept_matching"
internal const val ACCEPT_UNCHECKED_TAG = "v2_accept_unchecked"
internal const val NUMBERS_DIFFER_TAG = "v2_numbers_differ"
internal const val MARK_VERIFIED_TAG = "v2_mark_verified"
internal const val CLEAR_VERIFIED_TAG = "v2_clear_verified"
internal const val VERIFIED_STATUS_TAG = "v2_verified_status"
internal const val INVITATION_SPENT_TAG = "v2_invitation_spent"
internal const val AWAITING_HINT_TAG = "v2_awaiting_hint"

/** A pending request in the chat list: who it claims to be, and the way to the comparison step. */
@Composable
private fun V2RequestCard(view: V2RequestView, onReview: (String) -> Unit, onDecline: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.v2_request_claimed_name, view.request.claimedDisplayName.ifBlank { "-" }),
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.request_card_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onReview(view.request.requestId) },
                    modifier = Modifier.testTag(REVIEW_REQUEST_TAG),
                ) { Text(stringResource(R.string.request_review)) }
                OutlinedButton(
                    onClick = { onDecline(view.request.requestId) },
                    modifier = Modifier.testTag(DECLINE_REQUEST_TAG),
                ) { Text(stringResource(R.string.v2_decline)) }
            }
        }
    }
}

/**
 * The comparison step before accepting a request (audit H1, owner decision DEC-030): the request's
 * safety number, how to compare it, and three explicit answers. "The numbers match" accepts and marks
 * the contact verified for exactly the number shown here; "Accept without comparing" accepts and
 * leaves it unverified; "The numbers differ" declines. The name the sender gave is never trusted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V2RequestReview(
    view: V2RequestView,
    onAccept: (requestId: String, localName: String, comparedSafetyNumber: String?) -> Unit,
    onDecline: (String) -> Unit,
    onBack: () -> Unit,
) {
    val requestId = view.request.requestId
    val claimed = view.request.claimedDisplayName.ifBlank { "-" }
    var name by remember(requestId) { mutableStateOf("") }
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.request_review_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.v2_request_claimed_name, claimed), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.compare_title), style = MaterialTheme.typography.titleMedium)
            view.safetyNumber?.let { SafetyNumberDigits(it) }
            Text(stringResource(R.string.compare_how_request))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.v2_contact_name)) },
                modifier = Modifier.fillMaxWidth().testTag(REQUEST_NAME_TAG),
            )
            Button(
                onClick = { onAccept(requestId, name, view.safetyNumber) },
                enabled = name.isNotBlank() && view.safetyNumber != null,
                modifier = Modifier.fillMaxWidth().testTag(ACCEPT_MATCHING_TAG),
            ) { Text(stringResource(R.string.request_accept_matching)) }
            OutlinedButton(
                onClick = { onAccept(requestId, name, null) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag(ACCEPT_UNCHECKED_TAG),
            ) { Text(stringResource(R.string.request_accept_unchecked)) }
            TextButton(
                onClick = { onDecline(requestId) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().testTag(NUMBERS_DIFFER_TAG),
            ) { Text(stringResource(R.string.request_numbers_differ)) }
        }
    }
}

@Composable
private fun SafetyNumberDigits(number: String) {
    SelectionContainer {
        Text(
            groupedDigits(number),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag("v2_safety_number_digits"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V2Invite(
    code: String?,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    upload: InvitationUpload = InvitationUpload.UNKNOWN,
    busy: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val context = LocalContext.current
    // A code that did not reach the relay cannot work, so it is not offered for sending (audit L2).
    val canSend = upload != InvitationUpload.FAILED && !busy
    // The code shown here was used or expired while the screen was open: it is single use, so say so
    // (owner, 2026-10-06; DEC-032). A new code clears the note.
    var shownCode by remember { mutableStateOf(code) }
    var spent by remember { mutableStateOf(false) }
    if (code != shownCode) {
        spent = code == null && shownCode != null
        shownCode = code
    }
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.v2_my_invitation), onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.v2_invitation_hint)) }
            if (spent) {
                item {
                    Text(
                        stringResource(R.string.invitation_spent),
                        color = MaterialTheme.messenger.link,
                        modifier = Modifier.testTag(INVITATION_SPENT_TAG),
                    )
                }
            }
            if (code != null) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // The code is black on white; a white frame keeps it scannable in the dark theme too.
                        Box(
                            Modifier
                                .testTag("v2_invitation_qr")
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) { QrCode(code, stringResource(R.string.v2_invitation_qr_description)) }
                    }
                }
                item { InvitationStatus(upload, busy, onRetry) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectionContainer { Text(code, style = MaterialTheme.typography.bodySmall) }
                        Button(
                            onClick = { shareInvitationCode(context, code) },
                            enabled = canSend,
                            modifier = Modifier.testTag("v2_share_invitation"),
                        ) { Text(stringResource(R.string.share_invitation_code)) }
                        OutlinedButton(
                            onClick = { copyInvitationCode(context, code) },
                            enabled = canSend,
                            modifier = Modifier.testTag("v2_copy_invitation"),
                        ) { Text(stringResource(R.string.copy_invitation_code)) }
                        Text(stringResource(R.string.copy_invitation_warning), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                if (code == null) {
                    Button(onClick = onCreate, enabled = !busy, modifier = Modifier.testTag("v2_create_invitation")) {
                        Text(stringResource(R.string.v2_create_invitation))
                    }
                } else {
                    OutlinedButton(onClick = onCreate, enabled = !busy, modifier = Modifier.testTag("v2_create_invitation")) {
                        Text(stringResource(R.string.v2_create_invitation))
                    }
                }
            }
        }
    }
}

/** Says whether the invitation reached the relay, and offers to send it again when it did not or when that is unknown (audit L2). */
@Composable
private fun InvitationStatus(upload: InvitationUpload, busy: Boolean, onRetry: () -> Unit) {
    val colors = MaterialTheme.messenger
    val (text, color) = when {
        busy -> stringResource(R.string.invitation_sending) to colors.hint
        upload == InvitationUpload.UPLOADED -> stringResource(R.string.invitation_status_uploaded) to colors.listTick
        upload == InvitationUpload.FAILED -> stringResource(R.string.invitation_status_failed) to MaterialTheme.colorScheme.error
        else -> stringResource(R.string.invitation_status_unknown) to colors.hint
    }
    Column(Modifier.testTag("v2_invitation_status"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, color = color)
        if (!busy && upload != InvitationUpload.UPLOADED) {
            val label = stringResource(if (upload == InvitationUpload.FAILED) R.string.invitation_retry else R.string.invitation_resend)
            if (upload == InvitationUpload.FAILED) {
                Button(onClick = onRetry, modifier = Modifier.testTag("v2_retry_invitation")) { Text(label) }
            } else {
                OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("v2_retry_invitation")) { Text(label) }
            }
        }
    }
}

/**
 * Hands the code straight to the app the user picks, without the clipboard: the safer way to send
 * an invitation from a distance (owner decision, DEC-026).
 */
private fun shareInvitationCode(context: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_invitation_code)))
}

/**
 * The code carries the invitation secret. Copying stays possible by the owner's choice (DEC-026),
 * with a warning on screen: the clip is marked sensitive, which Android 13+ and keyboards such as
 * Gboard honour (hidden preview, no history), but the Samsung keyboard keeps it in its history
 * anyway (seen in the Phase 5 hand check).
 */
private fun copyInvitationCode(context: Context, code: String) {
    val clip = ClipData.newPlainText(context.getString(R.string.v2_my_invitation), code)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
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
            item { Text(stringResource(R.string.v2_add_intro)) }
            item {
                Button(
                    onClick = { scanner.launch(portraitQrScanOptions(scanPrompt)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.v2_scan_invitation)) }
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
                    modifier = Modifier.fillMaxWidth().testTag("v2_submit_add"),
                ) { Text(stringResource(R.string.v2_add_contact)) }
            }
        }
    }
}

/**
 * A contact's page: who it is, its safety number, and whether the person has compared that number
 * (audit H1, DEC-030). "The numbers match" passes the number shown here, and the service marks the
 * contact only if that is still its number; the mark can be removed. A blocked contact offers neither.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V2SafetyNumber(
    number: String?,
    onBack: () -> Unit,
    contact: V2Contact? = null,
    onMarkVerified: (contactId: String, shownSafetyNumber: String) -> Unit = { _, _ -> },
    onClearVerified: (contactId: String) -> Unit = {},
) {
    val colors = MaterialTheme.messenger
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.v2_safety_number), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (contact != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(contact.localDisplayName, contact.contactId, size = MessengerDimens.AvatarList)
                    Column {
                        Text(contact.localDisplayName, style = MessengerType.HeaderTitle)
                        Text(
                            stateLabel(contact.state),
                            style = MessengerType.HeaderSubtitle,
                            color = if (contact.state == V2ContactState.KEY_CHANGED) MaterialTheme.colorScheme.error else colors.hint,
                        )
                    }
                }
            }
            if (contact?.state == V2ContactState.AWAITING_ACCEPTANCE) {
                // The invitation may have been used by someone else first: that answer never comes (I2).
                Text(
                    stringResource(R.string.awaiting_acceptance_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.hint,
                    modifier = Modifier.testTag(AWAITING_HINT_TAG),
                )
            }
            number?.let { SafetyNumberDigits(it) }
            when {
                contact == null || contact.state == V2ContactState.KEY_CHANGED ->
                    Text(stringResource(R.string.v2_safety_hint))
                contact.safetyNumberChecked -> {
                    val checkedOn = rememberCheckedDate(contact.verifiedAtEpochMillis ?: 0L)
                    Text(
                        stringResource(R.string.verified_status_checked, checkedOn),
                        color = colors.link,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag(VERIFIED_STATUS_TAG),
                    )
                    Text(stringResource(R.string.verified_checked_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        onClick = { onClearVerified(contact.contactId) },
                        modifier = Modifier.testTag(CLEAR_VERIFIED_TAG),
                    ) { Text(stringResource(R.string.verified_clear)) }
                }
                else -> {
                    Text(
                        stringResource(R.string.conversation_subtitle_unverified),
                        color = colors.hint,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag(VERIFIED_STATUS_TAG),
                    )
                    Text(stringResource(R.string.compare_how_contact))
                    Button(
                        onClick = { number?.let { onMarkVerified(contact.contactId, it) } },
                        enabled = number != null,
                        modifier = Modifier.fillMaxWidth().testTag(MARK_VERIFIED_TAG),
                    ) { Text(stringResource(R.string.verified_mark)) }
                }
            }
        }
    }
}

/** The day the mark was set, as on the date pills of a conversation. */
@Composable
private fun rememberCheckedDate(timeMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val patterns = rememberDatePatterns()
    return formatDateChip(timeMillis, System.currentTimeMillis(), ZoneId.systemDefault(), locale, patterns)
}

@Composable
internal fun stateLabel(state: V2ContactState): String = stringResource(
    when (state) {
        V2ContactState.AWAITING_ACCEPTANCE -> R.string.v2_state_awaiting
        V2ContactState.ACTIVE -> R.string.v2_state_active
        V2ContactState.KEY_CHANGED -> R.string.v2_state_key_changed
    },
)

/** 60 digits in groups of five, three groups to a line: readable aloud and identical on both devices. */
private fun groupedDigits(number: String): String = safetyNumberLines(number).joinToString("\n")
