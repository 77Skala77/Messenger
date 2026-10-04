# Phase 6 — Color notes from Telegram's bundled themes

Status: **NOTES** (2026-10-01, Phase 6 study). Source: five theme files shipped in Telegram for
Android (`TMessagesProj/src/main/assets/{day,night,arctic,darkblue,bluebubbles}.attheme`, commit
`f2908b1`), copied read-only to `D:\Tools\reference\themes`. The files carry no license header
(rule 2 in `PHASE6_UI_PLAN.md`; the owner's accepted legal risk, DEC-027). We use **only the colors,
as plain numbers**, in our own theme files; nothing else is copied.

Format: `name=value` lines, the value a signed 32-bit ARGB integer (`-1` = white, `-14509328` =
`#FF2187F0`). Below the values are converted to `#RRGGBB`; `/aNN` is an alpha below 255 (0–255).
The files hold 186 (`bluebubbles`) to 498 (`night`) keys each (every control of the app).

## 1. What the themes tell us

- **Day** is white and light gray surfaces, dark gray text `#333333`, one blue accent family
  (`#229AF0` for send and counters). Bubbles: incoming light gray, outgoing saturated blue with white
  text, a slight vertical gradient on outgoing bubbles in some themes.
- **Night** (neutral dark gray, near black wallpaper) and **darkblue** (blue-gray) are the two dark
  variants; `arctic` is a light blue-sky variant and `bluebubbles` a reduced set.
- The look is one accent color, soft shadows (alpha near 0 to 50), and **high contrast text**
  (white on dark, `#333333` on light).

## 2. Colors worth taking (light = `day`, dark = `night`)

| Role | Light (day) | Dark (night) | Telegram key |
| --- | --- | --- | --- |
| Top bar | `#FFFFFF` | `#232326` | `actionBarDefault` |
| Top bar title | `#333333` | `#FFFFFF` | `actionBarDefaultTitle` |
| Screen background (list) | `#FFFFFF` | `#181819` | `windowBackgroundWhite` |
| Section / gap background | light gray | `#000000` | `windowBackgroundGray` |
| Primary text | `#333333` | `#FFFFFF` | `windowBackgroundWhiteBlackText` |
| Secondary text | `#808384` | `#FFFFFF` at alpha 110 | `windowBackgroundWhiteGrayText` |
| Link / accent text | `#238AE3` | `#64B5EF` | `windowBackgroundWhiteBlueText` |
| Chat-list name | (primary) | `#F1F1F1` | `chats_name` |
| Chat-list last message | (secondary) | `#828282` | `chats_message` |
| Chat-list time | (secondary) | `#787878` | `chats_date` |
| Unread badge | `#24AEF7` | `#399EF3` | `chats_unreadCounter` |
| Unread badge, muted chat | gray | `#454545` | `chats_unreadCounterMuted` |
| Tick of a sent message in the list | `#1A9FEF` | `#46ADF3` | `chats_sentCheck` |
| Round action button | `#51A2E8` | `#5EA0DB` | `chats_actionBackground` |
| Divider | `#E6E6E6` | `#000000` | `divider` |
| Chat wallpaper | `#FFFFFF` | `#0F0F10` | `chat_wallpaper` |
| Incoming bubble | `#F0F0F0` | `#1F2123` | `chat_inBubble` |
| Outgoing bubble | `#2D7ED5` | `#366CAF` | `chat_outBubble` |
| Outgoing bubble, gradient end | `#5CB6E0` | `#3B8CB9` | `chat_outBubbleGradient` |
| Text in incoming bubble | dark | `#FAFAFA` | `chat_messageTextIn` |
| Text in outgoing bubble | `#FFFFFF` | `#FAFAFA` | `chat_messageTextOut` |
| Time in incoming bubble | `#939599` | `#7D7F81` | `chat_inTimeText` |
| Time in outgoing bubble | `#C7E6FF` | `#94D3F6` | `chat_outTimeText` |
| Tick in outgoing bubble | white, alpha 245 | `#C9F0FF` alpha 225 | `chat_outSentCheck` |
| Clock (pending) in outgoing bubble | white, alpha 112 | `#99C9EF` | `chat_outSentClock` |
| Date chip / service message | `#FFFFFF` alpha 244 | `#373737` alpha 150 | `chat_serviceBackground` |
| Input bar background | (white) | `#1E1E1F` | `chat_messagePanelBackground` |
| Input text / hint | dark / gray | `#FFFFFF` / white alpha 100 | `chat_messagePanelText/Hint` |
| Send button | `#229AF0` | `#229AF0` | `chat_messagePanelSend` |
| Reply line, incoming | `#3E91E0` | `#79C4FC` | `chat_inReplyLine` |
| Name of the sender in a bubble | blue `#2586D6`, pink `#CB4F86`, violet `#836FE6` | blue `#5CADEA`, cyan `#53CCC6`, green `#7DD071`, orange `#E8AB5A`, pink `#E8749A`, red `#E88878`, violet `#8C8DEA` | `avatar_nameInMessage*` |
| Avatar backgrounds | blue `#50A5E6` | red `#DC805B`, orange `#F2BC64`, blue `#4FA1E8` | `avatar_background*` |

Gaps (`-` in the extraction): the day theme leaves some keys unset because they inherit another
key's value in Telegram's theme engine. Our theme sets every color itself.

## 2a. The owner's choice: the classic palette (2026-10-01)

The owner chose Telegram's **classic** look (not "Day"): green outgoing and white incoming bubbles
on a light pattern of our own, with a dark theme that follows the system and an in-app switch.
Classic values come from Telegram's built-in defaults (`ui/ActionBar/ThemeColors.java`, no header,
values only; DEC-027):

| Role | Classic light | Key |
| --- | --- | --- |
| Accent (unread badge, send button, switches, links) | `#229AF0` | `TELEGRAM_COLOR` |
| Primary text, top bar title and icons | `#1A1D21` | `DEFAULT_BLACK_TEXT` |
| Top bar, list background | `#FFFFFF` | `actionBarDefault`, `windowBackgroundWhite` |
| Section background | `#F1F1F3` | `windowBackgroundGray` |
| Chat-list name / last message / time | `#1A1D21` / `#75787A` / `#848688` | `chats_name`, `chats_message`, `chats_date` |
| Chat-list tick | `#46AA36` | `chats_sentCheck` |
| Unread badge / muted / text | `#229AF0` / `#BEC3C7` / `#FFFFFF` | `chats_unreadCounter*` |
| New-chat button | `#65A9E0` | `chats_actionBackground` |
| Divider | `#D9D9D9` | `divider` |
| Incoming bubble (selected) | `#FFFFFF` (`#ECF7FD`) | `chat_inBubble` |
| Outgoing bubble (selected) | `#EFFFDE` (`#D9F7C5`) | `chat_outBubble` |
| Message text, both sides | `#000000` | `chat_messageText*` |
| Time: incoming / outgoing | `#A1AAB3` / `#70B15C` | `chat_inTimeText`, `chat_outTimeText` |
| Ticks / clock in outgoing bubble | `#5DB050` / `#75BD5E` | `chat_outSentCheck`, `chat_outSentClock` |
| Bubble shadows (low alpha) | incoming `#1D3753`, outgoing `#1E750C` | `chat_*BubbleShadow` |
| Reply line: incoming / outgoing | `#599FD8` / `#6EB969` | `chat_*ReplyLine` |
| Input bar / text / hint / icons | `#FFFFFF` / `#000000` / `#858A84` / `#8E959B` | `chat_messagePanel*` |
| Date chip | semi-transparent darker tone of the wallpaper, white text | `chat_serviceBackground` (computed from the wallpaper in Telegram), `chat_serviceText` `#FFFFFF` |

- **Wallpaper:** our own light pattern (six simple outline doodles, drawn in Compose at low contrast)
  over a soft green-gray base (`#DDE9CC`, pattern `#C3D3B0`; dark `#0F0F10`, pattern `#1B1D1F`). These
  are first values: the owner judges them on the phones with the debug preview (step 6c) and they
  change in one place (`MessengerColors.kt`).
- **Adjusted for contrast (step 6c).** Telegram's classic text colors for time, secondary text and
  ticks are paler than WCAG AA allows. Each failing value was moved just far enough and is pinned by
  `MessengerColorsTest`: time in an incoming bubble `#A1AAB3` → `#697480` (2.4 → 4.8:1), time in an
  outgoing bubble `#70B15C` → `#3C7F2E` (2.5 → 4.7), tick `#5DB050` → `#3A9230`, clock `#75BD5E` →
  `#4A9A3B`, list tick `#46AA36` → `#3A9A2B`, list message `#75787A` → `#707477`, list date `#848688` →
  `#6F7479`, hint `#858A84` → `#70756F`, muted badge `#BEC3C7` → `#858B91`. The new-chat button is
  `#229AF0` instead of `#65A9E0` (white icon 3.0:1). Material's `primary` in the light theme is the
  link blue `#2678B6` (white text 4.7:1, and as text on white 4.7:1); the brighter `#229AF0` stays on
  the badge and send button. Dark: time in an incoming bubble `#7D7F81` → `#8A8C8E`, in an outgoing one
  `#94D3F6` → `#EAF4FC`, list date `#787878` → `#8A8A8A`, badge `#399EF3` → `#2A8AD9` (white digits
  3.7:1); Material's `primary` is `#64B5EF` with dark text.
- **Thresholds:** text 4.5:1; ticks, clocks, badge digits and the new-chat icon 3:1 (non-text and bold
  level). Avatars carry white letters on Telegram's gradients (measured 1.7 to 4.8:1, the top of the
  orange gradient lowest): the contact's name is always next to the avatar, so the letters are
  decoration and are hidden from screen readers. If the owner finds them hard to read, the gradients
  are the thing to darken.
- **Dark theme:** Telegram has no dark variant of the classic green; the dark theme uses the Night
  values of section 2 (dark gray surfaces, blue outgoing bubbles), unless the owner's screenshots ask
  for something else.
- **Theme switch** in Settings: Light, Dark, System (default System).

## 3. How this maps to our app

- Our two themes follow Material 3 color roles; the table gives starting values, not a copy of
  Telegram's engine (which uses ~500 keys). A first token set: `surface`, `surfaceVariant`,
  `onSurface`, `onSurfaceVariant`, `primary` (the blue), `bubbleIncoming`, `bubbleOutgoing`,
  `onBubbleOutgoing`, `tick`, `badge`, `chip`, `divider`, `wallpaper`.
- Ticks follow our semantics, not Telegram's: one tick = "sent to the delivery server", two = "delivered"
  (an authenticated receipt); **never a "read" state** (the protocol has none). Telegram's
  `chats_sentReadCheck` keys are therefore unused.
- Avatar colors: our rule is a stable pick from a small palette by a hash of the local contact ID
  (never sent anywhere), using the seven hues above.
- Contrast: every pair (text on bubble, time on bubble, badge text on badge) is checked in the
  theme test against WCAG AA before it ships (a planned unit test).
- Dynamic color (Android 12+ wallpaper colors) stays off for chat bubbles so the look is stable;
  an option for the rest of the app is a later decision.

## 4. Next

- Step 6c writes `Theme.kt` with these tokens for both modes and a preview screen.
- The owner's screenshots of Telegram decide details the files cannot show (spacing, tail shape).
