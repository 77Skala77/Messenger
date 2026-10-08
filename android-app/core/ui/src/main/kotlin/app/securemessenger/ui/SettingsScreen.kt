package app.securemessenger.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size
import app.securemessenger.transport.RelayHealthStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

internal const val CHAT_SOUNDS_TAG = "settings_chat_sounds"

/** The tag of a swipe-gesture choice in the settings; the suffix is the action's name in lower case. */
internal const val SWIPE_OPTION_TAG_PREFIX = "settings_swipe_"

/** The main settings page; and the tag of a main entry, whose suffix is its page's name in lower case. */
internal const val SETTINGS_TAG = "settings_main"
internal const val SETTINGS_ENTRY_TAG_PREFIX = "settings_open_"

/** The pages behind the main entries (DEC-038, Telegram 12's settings). */
internal enum class SettingsPageId { CHATS, PRIVACY, NOTIFICATIONS, CONNECTION, RELAY, LANGUAGE }

/**
 * The settings in Telegram 12's layout (DEC-038): the person's avatar and name at the top, then the
 * main entries in a rounded section, each with its colored tile, each opening its own page; in debug
 * builds the test tools below them; the app's version at the end. What the settings do has not changed.
 */
@Composable
internal fun SettingsScreen(
    profileName: String?,
    chatsAvailable: Boolean,
    debugQaToolsEnabled: Boolean,
    onOpen: (SettingsPageId) -> Unit,
    onOpenThemePreview: () -> Unit,
    onResetTestData: () -> Unit,
    onBack: (() -> Unit)?,
    bottomSpace: Dp = 0.dp,
) {
    val context = LocalContext.current
    var showTestResetDialog by remember { mutableStateOf(false) }
    val language = stringResource(if (LanguageController.selected(context) == AppLanguage.ENGLISH) R.string.english else R.string.russian)
    SettingsPage(
        title = stringResource(R.string.settings),
        onBack = onBack,
        bottomSpace = bottomSpace,
        titleAfter = 150.dp,
        modifier = Modifier.testTag(SETTINGS_TAG),
    ) {
        ProfileHeader(profileName)
        SettingsSection {
            SettingsLinkRow(
                R.drawable.tg_settings_chat, SettingsTile.ORANGE,
                stringResource(R.string.settings_chat_title), stringResource(R.string.settings_chat_info),
                onClick = { onOpen(SettingsPageId.CHATS) }, modifier = Modifier.testTag(entryTag(SettingsPageId.CHATS)),
            )
            SettingsLinkRow(
                R.drawable.tg_settings_privacy, SettingsTile.GREEN,
                stringResource(R.string.settings_privacy_title), stringResource(R.string.settings_privacy_info),
                onClick = { onOpen(SettingsPageId.PRIVACY) }, modifier = Modifier.testTag(entryTag(SettingsPageId.PRIVACY)),
            )
            // Notifications, sounds and the background connection exist only where chats exist (debug, v2).
            if (chatsAvailable) {
                SettingsLinkRow(
                    R.drawable.tg_settings_sounds, SettingsTile.RED,
                    stringResource(R.string.settings_notifications_sounds_title), stringResource(R.string.settings_notifications_sounds_info),
                    onClick = { onOpen(SettingsPageId.NOTIFICATIONS) }, modifier = Modifier.testTag(entryTag(SettingsPageId.NOTIFICATIONS)),
                )
                SettingsLinkRow(
                    R.drawable.tg_settings_power, SettingsTile.ORANGE_DEEP,
                    stringResource(R.string.connection_setting_title), stringResource(R.string.settings_connection_info),
                    onClick = { onOpen(SettingsPageId.CONNECTION) }, modifier = Modifier.testTag(entryTag(SettingsPageId.CONNECTION)),
                )
            }
            SettingsLinkRow(
                R.drawable.tg_settings_data, SettingsTile.BLUE_DEEP,
                stringResource(R.string.relay_server), stringResource(R.string.settings_relay_info),
                onClick = { onOpen(SettingsPageId.RELAY) }, modifier = Modifier.testTag(entryTag(SettingsPageId.RELAY)),
            )
            SettingsLinkRow(
                R.drawable.tg_settings_language, SettingsTile.PURPLE,
                stringResource(R.string.language), language,
                onClick = { onOpen(SettingsPageId.LANGUAGE) }, modifier = Modifier.testTag(entryTag(SettingsPageId.LANGUAGE)),
            )
        }
        if (debugQaToolsEnabled) {
            SettingsGap()
            SettingsSection {
                SettingsHeader(stringResource(R.string.debug_qa_tools_title))
                SettingsActionRow(
                    stringResource(R.string.reset_test_data),
                    onClick = { showTestResetDialog = true },
                    destructive = true,
                    modifier = Modifier.testTag("debug_reset_test_data"),
                )
                SettingsActionRow(
                    stringResource(R.string.theme_preview_open),
                    onClick = onOpenThemePreview,
                    modifier = Modifier.testTag(THEME_PREVIEW_BUTTON_TAG),
                )
            }
            SettingsInfo(stringResource(R.string.debug_reset_test_data_explanation))
        } else {
            SettingsGap()
        }
        Text(
            stringResource(R.string.settings_version, appVersion(context)),
            style = SettingsLook.Info,
            color = MaterialTheme.messenger.hint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 10.dp),
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
                    Text(stringResource(R.string.reset_test_data_confirm), color = MaterialTheme.messenger.destructive)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

internal fun entryTag(page: SettingsPageId) = SETTINGS_ENTRY_TAG_PREFIX + page.name.lowercase()

private fun appVersion(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

/** The top of the main page (`SettingsActivity`): a 90 dp avatar, the name in 22 sp, a gray line under it. */
@Composable
internal fun ProfileHeader(name: String?) {
    Column(
        Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 22.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(name = name.orEmpty(), colorIndex = avatarColorIndex(name.orEmpty()), size = 90.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            name.orEmpty(),
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp, letterSpacing = 0.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.settings_profile_subtitle), style = SettingsLook.Subtitle, color = MaterialTheme.messenger.hint)
    }
}

/**
 * "Chat settings" (`ThemeActivity`): a chat as it looks now, the color theme as three cards (as the
 * system, light, dark) and, where chats exist, what a swipe to the left on a chat does.
 */
@Composable
internal fun ChatSettingsPage(
    profileName: String?,
    chatsAvailable: Boolean,
    themeMode: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    SettingsPage(stringResource(R.string.settings_chat_title), onBack) {
        SettingsSection {
            ChatPreview(profileName)
            SettingsHeader(stringResource(R.string.settings_theme_header))
            Row(
                Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 12.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ThemeCard(AppThemeMode.SYSTEM, R.string.theme_system, themeMode == AppThemeMode.SYSTEM, THEME_OPTION_SYSTEM) { onThemeSelected(AppThemeMode.SYSTEM) }
                ThemeCard(AppThemeMode.LIGHT, R.string.theme_light, themeMode == AppThemeMode.LIGHT, THEME_OPTION_LIGHT) { onThemeSelected(AppThemeMode.LIGHT) }
                ThemeCard(AppThemeMode.DARK, R.string.theme_dark, themeMode == AppThemeMode.DARK, THEME_OPTION_DARK) { onThemeSelected(AppThemeMode.DARK) }
            }
        }
        // Telegram's "Swipe gesture": what a swipe to the left on a chat row does (DEC-035).
        if (chatsAvailable) {
            SettingsGap()
            var swipe by remember { mutableStateOf(ChatSwipeSetting.get(context)) }
            SettingsSection(Modifier.selectableGroup()) {
                SettingsHeader(stringResource(R.string.settings_swipe_title))
                ChatSwipeAction.entries.forEach { action ->
                    SettingsRadioRow(
                        title = stringResource(action.label),
                        selected = swipe == action,
                        onSelect = {
                            swipe = action
                            ChatSwipeSetting.set(context, action)
                        },
                        modifier = Modifier.testTag(SWIPE_OPTION_TAG_PREFIX + action.name.lowercase()),
                    )
                }
            }
            SettingsInfo(stringResource(R.string.settings_swipe_info))
        }
    }
}

/**
 * A chat as it looks with the current theme (`ThemePreviewMessagesCell`): the wallpaper, a text of the
 * other person answering one of ours, and our reply with its ticks.
 */
@Composable
private fun ChatPreview(profileName: String?) {
    val colors = MaterialTheme.messenger
    val you = stringResource(R.string.reply_you)
    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    fun at(hour: Int, minute: Int) =
        formatMessageTime(ZonedDateTime.of(2026, 10, 7, hour, minute, 0, 0, zone).toInstant().toEpochMilli(), zone, locale)
    ChatWallpaper(Modifier.fillMaxWidth()) {
        BubbleGradientBox {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PreviewBubble(outgoing = false, time = at(9, 56)) {
                    BubbleQuote(
                        QuoteInfo(authorIsMe = profileName == null, text = stringResource(R.string.settings_preview_quote)),
                        contactName = profileName ?: you,
                        outgoing = false,
                        onClick = {},
                    )
                    Text(stringResource(R.string.settings_preview_incoming), style = MessengerType.MessageText, color = colors.messageText)
                }
                PreviewBubble(outgoing = true, time = at(10, 11)) {
                    Text(stringResource(R.string.settings_preview_outgoing), style = MessengerType.MessageText, color = colors.messageText)
                }
            }
        }
    }
}

@Composable
private fun PreviewBubble(outgoing: Boolean, time: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.messenger
    val shape = remember(outgoing) { BubbleShape(outgoing, tail = true, nearTop = false, nearBottom = false) }
    val strip = MessengerDimens.BubbleTailStrip.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .bubbleBackground(outgoing, shape)
                .padding(start = 11.dp + if (outgoing) 0.dp else strip, end = 11.dp + if (outgoing) strip else 0.dp, top = 6.dp, bottom = 5.dp),
        ) {
            content()
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(time, style = MessengerType.MessageTime, color = if (outgoing) colors.timeOutgoing else colors.timeIncoming)
                if (outgoing) MessageTicks(TickKind.DELIVERED, colors.tickOutgoing)
            }
        }
    }
}

/**
 * One theme as a small card (Telegram's theme strip): its wallpaper with an outgoing and an incoming
 * bubble, a ring around the chosen one, the name under it. "As the system" shows both halves.
 */
@Composable
private fun ThemeCard(mode: AppThemeMode, label: Int, selected: Boolean, tag: String, onSelect: () -> Unit) {
    val colors = MaterialTheme.messenger
    Column(
        Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(tag)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 84.dp, height = 112.dp)
                .border(2.dp, if (selected) colors.radioOn else Color.Transparent, RoundedCornerShape(14.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            when (mode) {
                AppThemeMode.LIGHT -> ThemeMiniature(LightMessengerColors)
                AppThemeMode.DARK -> ThemeMiniature(DarkMessengerColors)
                AppThemeMode.SYSTEM -> {
                    ThemeMiniature(LightMessengerColors)
                    Box(Modifier.fillMaxSize().clip(LowerRightHalf)) { ThemeMiniature(DarkMessengerColors) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(label),
            style = SettingsLook.Subtitle,
            color = if (selected) colors.settingsValue else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The lower right half of a box, cut along its diagonal. */
private val LowerRightHalf = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        })
}

@Composable
private fun ThemeMiniature(palette: MessengerColors) {
    CompositionLocalProvider(LocalMessengerColors provides palette) {
        ChatWallpaper(Modifier.fillMaxSize()) {
            BubbleGradientBox(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(Modifier.width(46.dp).height(18.dp).bubbleBackground(true, RoundedCornerShape(9.dp)))
                    }
                    Box(Modifier.width(40.dp).height(18.dp).bubbleBackground(false, RoundedCornerShape(9.dp)))
                }
            }
        }
    }
}

/** "Privacy and security": screen security, and deleting everything on this phone. */
@Composable
internal fun PrivacySettingsPage(
    screenSecurity: Boolean,
    onScreenSecurityChanged: (Boolean) -> Unit,
    onDeleteLocalData: () -> Unit,
    onBack: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    SettingsPage(stringResource(R.string.settings_privacy_title), onBack) {
        SettingsSection {
            SettingsSwitchRow(
                title = stringResource(R.string.screen_security_title),
                hint = stringResource(R.string.screen_security_hint),
                checked = screenSecurity,
                onCheckedChange = onScreenSecurityChanged,
                modifier = Modifier.testTag(SCREEN_SECURITY_TAG),
            )
        }
        SettingsInfo(stringResource(R.string.no_secrets_logs))
        SettingsSection {
            SettingsHeader(stringResource(R.string.storage_title))
            SettingsActionRow(stringResource(R.string.delete_local_data), onClick = { showDeleteDialog = true }, destructive = true)
        }
        SettingsInfo(stringResource(R.string.settings_local_data_info))
    }
    if (showDeleteDialog) {
        TelegramAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_local_data)) },
            text = { Text(stringResource(R.string.delete_warning)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteLocalData() }) {
                    Text(stringResource(R.string.delete_confirm), color = MaterialTheme.messenger.destructive)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** "Notifications and Sounds" (debug builds, where chats exist): Android's notifications and the chat sounds. */
@Composable
internal fun NotificationSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(stringResource(R.string.settings_notifications_sounds_title), onBack) {
        SettingsSection { NotificationSettingsSection() }
        SettingsGap()
        SettingsSection {
            SettingsHeader(stringResource(R.string.settings_sounds_header))
            var sounds by remember { mutableStateOf(ChatSounds.enabled(context)) }
            SettingsSwitchRow(
                title = stringResource(R.string.chat_sounds_title),
                hint = stringResource(R.string.chat_sounds_hint),
                checked = sounds,
                onCheckedChange = {
                    sounds = it
                    ChatSounds.set(context, it)
                },
                modifier = Modifier.testTag(CHAT_SOUNDS_TAG),
            )
        }
    }
}

/** "Background connection" (DEC-034; debug builds): new messages within half a minute, at a cost in battery. */
@Composable
internal fun ConnectionSettingsPage(onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.connection_setting_title), onBack) {
        BackgroundConnectionSettings()
    }
}

/** "Delivery server": the relay's address, a check of it, and a sync on request. */
@Composable
internal fun RelaySettingsPage(
    relayServerUrl: String,
    relayHealthStatus: RelayHealthStatus?,
    allowInsecureRelayHttp: Boolean,
    isPhysicalDevice: Boolean,
    onSaveRelayUrl: (String) -> Unit,
    onCheckRelay: (String) -> Unit,
    onSynchronizeNow: () -> Unit,
    onBack: () -> Unit,
) {
    var relayUrlInput by remember(relayServerUrl) { mutableStateOf(relayServerUrl) }
    val addressWarning = relayClientAddressWarning(relayUrlInput, isPhysicalDevice)
    SettingsPage(stringResource(R.string.relay_server), onBack) {
        SettingsSection {
            SettingsHeader(stringResource(R.string.relay_server_url))
            SettingsText(stringResource(R.string.relay_server_explanation))
            OutlinedTextField(
                value = relayUrlInput,
                onValueChange = { relayUrlInput = it },
                label = { Text(stringResource(R.string.relay_server_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            SettingsText(stringResource(R.string.relay_server_example), color = MaterialTheme.messenger.hint)
            if (relayUrlInput.trim().startsWith("http://", ignoreCase = true)) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SecurityWarning(
                        stringResource(
                            if (allowInsecureRelayHttp) R.string.http_relay_debug_warning
                            else R.string.https_relay_required,
                        ),
                    )
                }
                if (allowInsecureRelayHttp) {
                    SettingsText(stringResource(R.string.http_debug_lan_only), color = MaterialTheme.messenger.hint)
                    SettingsText(stringResource(R.string.use_https_for_different_networks), color = MaterialTheme.messenger.hint)
                }
            } else if (relayTestMode(relayUrlInput) == RelayTestMode.PUBLIC_RELAY) {
                SettingsText(stringResource(R.string.public_relay_tunnel_notice), color = MaterialTheme.messenger.hint)
            }
            when (addressWarning) {
                RelayClientAddressWarning.PHONE_LOOPBACK ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { SecurityWarning(stringResource(R.string.relay_phone_loopback_warning)) }
                RelayClientAddressWarning.UNSPECIFIED_BIND_ADDRESS ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { SecurityWarning(stringResource(R.string.relay_unspecified_address_warning)) }
                null -> Unit
            }
            SettingsDivider()
            SettingsActionRow(stringResource(R.string.save_relay_server), onClick = { onSaveRelayUrl(relayUrlInput) })
            SettingsDivider()
            SettingsActionRow(
                stringResource(R.string.check_relay_server),
                onClick = { onCheckRelay(relayUrlInput) },
                value = relayHealthStatus?.let { relayHealthLabel(it) },
            )
        }
        SettingsGap()
        SettingsSection {
            SettingsActionRow(stringResource(R.string.synchronize_now), onClick = onSynchronizeNow)
        }
        SettingsInfo(stringResource(R.string.background_sync_notice))
    }
}

/** "Language": Russian or English; the app switches at once (`LanguageController`). */
@Composable
internal fun LanguageSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(LanguageController.selected(context)) }
    SettingsPage(stringResource(R.string.language), onBack) {
        SettingsSection(Modifier.selectableGroup()) {
            SettingsRadioRow(
                title = stringResource(R.string.russian),
                selected = selectedLanguage == AppLanguage.RUSSIAN,
                onSelect = {
                    selectedLanguage = AppLanguage.RUSSIAN
                    LanguageController.select(context, selectedLanguage)
                },
                modifier = Modifier.testTag("language_option_ru"),
            )
            SettingsRadioRow(
                title = stringResource(R.string.english),
                selected = selectedLanguage == AppLanguage.ENGLISH,
                onSelect = {
                    selectedLanguage = AppLanguage.ENGLISH
                    LanguageController.select(context, selectedLanguage)
                },
                modifier = Modifier.testTag("language_option_en"),
            )
        }
    }
}
