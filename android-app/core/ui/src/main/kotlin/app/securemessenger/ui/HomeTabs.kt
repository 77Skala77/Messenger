package app.securemessenger.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState

/** The bar of tabs at the bottom of the main screen, and each tab by its name in lower case. */
internal const val HOME_TAB_BAR_TAG = "home_tab_bar"
internal const val HOME_TAB_TAG_PREFIX = "home_tab_"
internal const val CONTACTS_TAB_TAG = "contacts_tab"
internal const val CONTACT_ROW_TAG_PREFIX = "contact_row_"
internal const val PROFILE_TAB_TAG = "profile_tab"

/** The four tabs of Telegram 12's main screen (`MainTabsActivity`); calls are not part of this messenger. */
internal enum class HomeTab(@StringRes val label: Int, @DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int) {
    CHATS(R.string.home_tab_chats, R.drawable.tg_tab_chats, R.drawable.tg_tab_chats_selected),
    CONTACTS(R.string.home_tab_contacts, R.drawable.tg_tab_contacts, R.drawable.tg_tab_contacts_selected),
    SETTINGS(R.string.settings, R.drawable.tg_tab_settings, R.drawable.tg_tab_settings_selected),

    /** Drawn with the person's own avatar instead of an icon. */
    PROFILE(R.string.home_tab_profile, 0, 0),
}

/** Telegram's numbers (`DialogsActivity.MAIN_TABS_*`, `GlassTabView`, `MainTabsActivity`). */
internal object HomeTabsLook {
    /** The glass pill: 56 dp high, 8 dp from the edges, at most 328 dp of tabs inside 4 dp of padding. */
    val BarHeight = 56.dp
    val BarMargin = 8.dp
    val BarMaxWidth = 344.dp
    val BarPadding = 4.dp

    /** What a page keeps free at its end so its last row is not hidden behind the bar. */
    val PageBottomSpace = BarHeight + BarMargin * 2

    /**
     * The icon is drawn 24 dp in the middle of a 44 dp box that starts 6 dp above the tab
     * (`setAnimation(..., 24, 24)`); the 12 sp label 28.33 dp below the tab's top.
     */
    val IconBox = 44.dp
    val IconSize = 24.dp
    val IconLift = 6.dp
    val LabelTop = 28.33.dp
    val AvatarSize = 24.dp

    /** The counter: 16 dp high, 11 dp right of the middle, 10 dp from the top, set apart by 1.33 dp. */
    val CounterHeight = 16.dp
    val CounterShift = 11.dp
    val CounterTop = 10.dp
    val CounterGap = 1.33.dp

    /** A tab is selected in 380 ms; its icon fills in the 100 ms of the Lottie's six frames. */
    const val SELECT_MILLIS = 380
    const val ICON_MILLIS = 100

    /** The pages slide like Telegram's pager. */
    const val PAGE_MILLIS = 200

    /** Telegram's `glass_tabSelected` at 9 percent behind the selected tab. */
    const val SELECTOR_ALPHA = 0.09f
}

/**
 * Telegram 12's main screen (owner, 2026-10-07): the page of the selected tab, sliding sideways when
 * another is chosen, under a pill of glass with the tabs, Chats, Contacts, Settings and Profile, that
 * floats over the bottom of it. [chromeHidden] (searching or selecting chats) lets the bar go, as
 * Telegram's goes in its action mode. Back on another tab returns to the chats.
 */
@Composable
internal fun HomeTabsScaffold(
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    unreadChats: Int,
    profileName: String?,
    chromeHidden: Boolean,
    page: @Composable (HomeTab) -> Unit,
) {
    val glass = rememberGlassSource()
    // Only while the main screen is the one on show: under an open chat, Back belongs to the chat.
    androidx.activity.compose.BackHandler(enabled = tab != HomeTab.CHATS && LocalScreenVisible.current) { onTab(HomeTab.CHATS) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassSource(glass)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val spec = tween<androidx.compose.ui.unit.IntOffset>(HomeTabsLook.PAGE_MILLIS, easing = TelegramMotion.Decelerate)
                    slideInHorizontally(spec) { if (forward) it else -it } togetherWith slideOutHorizontally(spec) { if (forward) -it else it }
                },
                label = "home tab",
            ) { shown -> page(shown) }
        }
        val hidden by animateFloatAsState(if (chromeHidden) 1f else 0f, tween(HomeTabsLook.SELECT_MILLIS, easing = EaseOutQuintHome), label = "tab bar")
        if (hidden < 1f) {
            HomeTabBar(
                selected = tab,
                onSelect = onTab,
                unreadChats = unreadChats,
                profileName = profileName,
                glass = glass,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(HomeTabsLook.BarMargin)
                    .graphicsLayer {
                        alpha = 1f - hidden
                        translationY = 40.dp.toPx() * hidden
                    },
            )
        }
    }
}

private val EaseOutQuintHome = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** The pill of glass with the four tabs. */
@Composable
private fun HomeTabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit, unreadChats: Int, profileName: String?, glass: GlassSource, modifier: Modifier) {
    Row(
        modifier
            .widthIn(max = HomeTabsLook.BarMaxWidth)
            .fillMaxWidth()
            .height(HomeTabsLook.BarHeight)
            .glass(glass, HomeTabsLook.BarHeight / 2)
            .padding(HomeTabsLook.BarPadding)
            .testTag(HOME_TAB_BAR_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTab.entries.forEach { tab ->
            HomeTabItem(
                tab = tab,
                selected = tab == selected,
                counter = if (tab == HomeTab.CHATS) unreadChats else 0,
                profileName = profileName,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One tab (`GlassTabView`): the icon (outline, filled when selected) or the avatar, the bold label
 * under it, a pill of the selected color behind the selected tab that grows from 60 percent, and the
 * counter of unread chats at the icon's upper right.
 */
@Composable
private fun HomeTabItem(tab: HomeTab, selected: Boolean, counter: Int, profileName: String?, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.messenger
    val dark = MaterialTheme.colorScheme.background.luminanceBelowHalf()
    // Telegram's `glass_tabUnselected`, `glass_tabSelectedText` and `glass_tabSelected`; Dark Blue keeps its
    // labels white and its accent blue.
    val idle = if (dark) Color.White else Color(0xFF1A1D21)
    val accent = if (dark) colors.link else Color(0xFF0D7FCF)
    val selector = if (dark) Color.White else Color(0xFF1A91E6)
    val factor by animateFloatAsState(if (selected) 1f else 0f, tween(HomeTabsLook.SELECT_MILLIS, easing = EaseOutQuintHome), label = "tab selected")
    val filled by animateFloatAsState(if (selected) 1f else 0f, tween(HomeTabsLook.ICON_MILLIS), label = "tab icon")
    val tint = androidx.compose.ui.graphics.lerp(idle, accent, factor)
    val label = stringResource(tab.label)
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                if (factor <= 0f) return@drawBehind
                val alpha = TelegramMotion.Decelerate.transform(factor)
                val scale = lerp(0.6f, 1f, factor)
                val w = size.width * scale
                val h = size.height * scale
                drawRoundRect(
                    selector.copy(alpha = HomeTabsLook.SELECTOR_ALPHA * alpha),
                    Offset((size.width - w) / 2f, (size.height - h) / 2f),
                    Size(w, h),
                    CornerRadius(minOf(w, h) / 2f),
                )
            }
            // No ripple: Telegram's tabs show only their pill (the owner saw a blue square, 2026-10-07).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .testTag(HOME_TAB_TAG_PREFIX + tab.name.lowercase()),
    ) {
        if (tab == HomeTab.PROFILE) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp)) {
                Avatar(profileName.orEmpty(), avatarColorIndex(profileName.orEmpty()), size = HomeTabsLook.AvatarSize)
            }
        } else {
            Box(Modifier.align(Alignment.TopCenter).offset(y = -HomeTabsLook.IconLift).size(HomeTabsLook.IconBox), contentAlignment = Alignment.Center) {
                if (filled < 1f) TelegramIcon(tab.icon, tint = tint, size = HomeTabsLook.IconSize, modifier = Modifier.graphicsLayer { alpha = 1f - filled })
                if (filled > 0f) TelegramIcon(tab.selectedIcon, tint = tint, size = HomeTabsLook.IconSize, modifier = Modifier.graphicsLayer { alpha = filled })
            }
        }
        Text(
            label,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp, letterSpacing = 0.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = HomeTabsLook.LabelTop, start = 2.dp, end = 2.dp),
        )
        if (counter > 0) TabCounter(counter, Modifier.align(Alignment.TopCenter))
    }
}

/** The blue counter with white digits, set apart from the icon by a thin ring of the glass. */
@Composable
private fun BoxScope.TabCounter(count: Int, modifier: Modifier) {
    val colors = MaterialTheme.messenger
    val text = if (count > 99) "99+" else count.toString()
    Box(
        modifier
            .offset(x = HomeTabsLook.CounterShift, y = HomeTabsLook.CounterTop - HomeTabsLook.CounterHeight / 2)
            .height(HomeTabsLook.CounterHeight + HomeTabsLook.CounterGap * 2)
            .widthIn(min = HomeTabsLook.CounterHeight + HomeTabsLook.CounterGap * 2)
            .drawBehind {
                val gap = HomeTabsLook.CounterGap.toPx()
                drawRoundRect(colors.glassTint.copy(alpha = 1f), cornerRadius = CornerRadius(size.height / 2f))
                drawRoundRect(
                    colors.fab,
                    Offset(gap, gap),
                    Size(size.width - 2 * gap, size.height - 2 * gap),
                    CornerRadius((size.height - 2 * gap) / 2f),
                )
            }
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp), color = Color.White, maxLines = 1)
    }
}

/**
 * The Contacts tab (Telegram 12's `ContactsActivity`, written anew): "My invitation" and "Add contact"
 * at the top, then every contact by name; a tap opens the chat. A blocked contact (changed key) says so.
 * The round button adds a contact.
 */
@Composable
internal fun ContactsTab(contacts: List<V2Contact>, onOpen: (String) -> Unit, onInvite: () -> Unit, onAdd: () -> Unit) {
    val sorted = remember(contacts) { contacts.sortedBy { it.localDisplayName.lowercase() } }
    Box(Modifier.fillMaxSize().testTag(CONTACTS_TAB_TAG)) {
        SettingsPage(title = stringResource(R.string.home_tab_contacts), onBack = null, bottomSpace = HomeTabsLook.PageBottomSpace + 72.dp) {
            SettingsSection {
                ContactsAction(R.drawable.tg_msg_qrcode, stringResource(R.string.v2_my_invitation), onInvite)
                ContactsAction(R.drawable.tg_msg_contacts, stringResource(R.string.v2_add_contact), onAdd)
            }
            SettingsGap()
            SettingsSection {
                SettingsHeader(stringResource(R.string.contacts_sorted_by_name))
                if (sorted.isEmpty()) {
                    Text(
                        stringResource(R.string.contacts_empty),
                        style = MessengerType.ListMessage,
                        color = MaterialTheme.messenger.hint,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
                sorted.forEach { contact -> ContactRow(contact, onClick = { onOpen(contact.contactId) }) }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            shape = CircleShape,
            containerColor = MaterialTheme.messenger.fab,
            contentColor = MaterialTheme.messenger.fabIcon,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = HomeTabsLook.PageBottomSpace + 8.dp),
        ) {
            TelegramIcon(R.drawable.tg_msg_contacts, tint = MaterialTheme.messenger.fabIcon, description = stringResource(R.string.v2_add_contact))
        }
    }
}

@Composable
private fun ContactsAction(@DrawableRes icon: Int, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TelegramIcon(icon, tint = MaterialTheme.messenger.link)
        Spacer(Modifier.width(28.dp))
        Text(text, style = MessengerType.ListName.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.messenger.link, maxLines = 1)
    }
}

@Composable
private fun ContactRow(contact: V2Contact, onClick: () -> Unit) {
    val colors = MaterialTheme.messenger
    val status = when {
        contact.state == V2ContactState.KEY_CHANGED -> stringResource(R.string.contacts_status_blocked)
        contact.safetyNumberChecked -> stringResource(R.string.conversation_subtitle_verified)
        else -> stringResource(R.string.conversation_subtitle_unverified)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(CONTACT_ROW_TAG_PREFIX + contact.contactId),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.localDisplayName, contact.contactId, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.localDisplayName, style = MessengerType.ListName, color = colors.listName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                status,
                style = MessengerType.ListMessage.copy(fontSize = 14.sp),
                color = if (contact.safetyNumberChecked) colors.link else colors.hint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The Profile tab (Telegram 12's own profile, written anew): the avatar and name of this phone's
 * profile, which exists only here, and the way to the person's invitation.
 */
@Composable
internal fun ProfileTab(profileName: String?, onInvite: () -> Unit) {
    SettingsPage(
        title = profileName.orEmpty(),
        onBack = null,
        titleAfter = 150.dp,
        bottomSpace = HomeTabsLook.PageBottomSpace,
        modifier = Modifier.testTag(PROFILE_TAB_TAG),
    ) {
        ProfileHeader(profileName)
        SettingsSection {
            ContactsAction(R.drawable.tg_msg_qrcode, stringResource(R.string.v2_my_invitation), onInvite)
        }
        SettingsInfo(stringResource(R.string.profile_info))
    }
}
