# Phase 6 — Porting notes (step 6b)

Status: **DONE** (2026-10-01, step 6b). Source: Telegram for Android, commit `f2908b1` (12.10.6),
read from the reference copy `D:\Tools\reference\Telegram`. Rules: `PHASE6_UI_PLAN.md`, section 2
(DEC-027). Colors: `PHASE6_THEME_NOTES.md`.

## 1. Result in one paragraph

The pieces we want are tied to Telegram's huge monolithic classes (`ChatMessageCell` 27,780 lines,
`ChatActivity` 44,302, `DialogCell` 6,076, `ChatActivityEnterView` 14,319) and to its own View
system, so translating their code line by line would bring in their whole data model. What we need
from them is **geometry, metrics, rules, and colors**: a few dozen numbers and a handful of rules,
which are written below. Our Compose code is written anew from these notes. Every piece whose
numbers or rules come from a Telegram file is still listed in `THIRD_PARTY_NOTICES.md` when it lands,
with that file and its header status (for the headerless ones: "accepted risk", DEC-027), so the
provenance stays honest.

## 2. Pieces

Units: `dp` for sizes, `sp` for text (Telegram uses dp for text; we use sp so the system font size
setting still works).

### 2.1 Message bubble — `ui/ActionBar/MessageDrawable.java` (no header; accepted risk)

- Corner radius **17 dp** (`SharedConfig.bubbleRadius`, default 17); a corner that touches another
  bubble of the same series (top or bottom) uses **6 dp** (`min(6, radius)`).
- The path is inset **2 dp** inside its bounds. On the tail side the body is inset **8 dp** more;
  the tail lives in that strip.
- Tail (only on the **last** bubble of a series, see 2.2): at the bottom corner on the sender's
  side. The bottom edge runs to 2.6 dp from the outer edge; the tail curve is an arc of a circle
  of radius 6 dp (`smallRad`) inside the rectangle (x: outer edge − 8 dp … outer edge − 7 dp + 12 dp;
  y: bottom − 2 − 12 − 9 … bottom − 2 − 1), starting at 180° and sweeping −83° (outgoing; mirrored
  for incoming), joining the side edge 6 + 3 dp above the bottom.
- Bubbles that are not last in their series have **no tail** and use the rounded-rectangle variant
  (Telegram's "media" bubble type) with the 6 dp near corners.
- Outgoing bubble may carry a vertical gradient (`chat_outBubble` to `chat_outBubbleGradient`); a
  faint shadow (alpha around 0 to 50) under each bubble.
- Ours: a Compose `Shape` (`GenericShape`) with these numbers, parameters `outgoing`, `tail`,
  `nearTop`, `nearBottom`.

### 2.2 Grouping into series — `ui/ChatActivity.java` (v2 or later)

- Two neighbouring messages belong to one series (`pinnedBottom` / `pinnedTop`) when they go the
  **same direction** (both incoming or both outgoing) and their times are **at most 5 minutes**
  apart; a date separator between them breaks the series.
- Ours: a pure function over the message list (unit-tested), giving `nearTop`, `nearBottom`, `tail`.

### 2.3 Text, time and ticks inside a bubble — `ui/Cells/ChatMessageCell.java` (v2 or later)

- Message text **16 sp** (`SharedConfig.fontSize`, default 16), time **12 sp**.
- The time (plus ticks on outgoing messages) sits at the bottom end of the bubble. If the last text
  line plus the time block fits in the maximum width, the time shares the last line; otherwise it
  goes on its own line below the text. Bubble width = max(text width, last line + time block) plus
  **31 dp** of horizontal padding in total (text start about 11 dp in, tail strip on one side).
- Maximum bubble width: the screen width minus about 80 dp (no avatars in 1:1 chats), capped for
  tablets.
- Ours: a custom Compose `Layout` that measures the text's last line and places the time block.

### 2.4 Ticks — drawables `msg_check`, `msg_halfcheck`, `msg_clock` (resources, not code)

- Telegram's tick images are resources and are **not used**. Ours: Material Icons `Done` (one tick,
  "sent to the delivery server") and `DoneAll` (two ticks, "delivered", an authenticated receipt),
  `Schedule` (clock, waiting to send). Never a "read" state: the protocol has none.

### 2.5 Avatar — `ui/Components/AvatarDrawable.java` (v2 or later); colors `ThemeColors.java` (no header; values only)

- Initials: the first character of the first name, plus the first character of the last word of
  the last name; with no last name, the first character of the last word of the first name if it
  has more than one word. Emoji-aware (a whole emoji counts as one character). Upper case.
  Text **18 sp** bold on a ~42 dp avatar, scaled with the size.
- Color: `abs(id % 7)` into seven two-tone gradients (top color, bottom color):
  red `#FF845E` → `#D45246`, orange `#FEBB5B` → `#F68136`, violet `#B694F9` → `#6C61DF`,
  green `#9AD164` → `#46BA43`, cyan `#5BCBE3` → `#359AD4`, blue `#5CAFFA` → `#408ACF`,
  pink `#FF8AAC` → `#D95574`. Text white.
- Ours: our contacts have one local name, so: first character of the first word plus first
  character of the last word (if two or more words). The index comes from a stable hash of the local
  contact ID (never sent anywhere).

### 2.6 Chat-list row — `ui/Cells/DialogCell.java` (v2 or later)

- Row height **70 dp** (two-line layout; 76 dp for the three-line one).
- Avatar **52 dp**, 11 dp from the start edge, 9 dp from the top.
- Text column starts at **72 dp** (name about 76 to 78 dp, after optional icons).
- Name **17 sp** medium, top 14 dp. Time **12 to 13 sp**, right-aligned, 15 dp from the end edge, top
  16 dp; the tick of the last outgoing message sits just before the time.
- Last message **16 sp**, top 39 dp, one line, ellipsized, secondary color.
- Unread badge: height **20.67 dp**, corner radius **11.5 dp**, horizontal text padding **6.33 dp**,
  minimum text width **8 dp**, **15.67 dp** from the end edge, top 38 dp; text **13 sp** bold white;
  muted chats use a gray badge.
- Divider: 1 px from the text column start (72 dp) to the end edge.
- The unread counter's animation lives in `ui/Components/CounterView.java` (no header): we write
  our own simple animation (`AnimatedContent`) instead.

### 2.7 Date and time rules — `messenger/LocaleController.java` (v2 or later)

- Chat list time: older than 365 days: `dd.MM.yy`; same day (or the previous day, if less than
  8 hours ago): `HH:mm` (12-hour where the locale uses it); within the last 6 days: weekday
  (`EEE`); otherwise day and month (`d MMM`).
- Date separators in a conversation: within 365 days `d MMMM`, otherwise `d MMMM yyyy`; Russian and
  English patterns from our own string resources.
- Ours: pure functions with an injected clock, unit-tested in both languages.

### 2.8 Date separator chip — `ui/Cells/ChatActionCell.java` (v2 or later)

- Centered pill, text **14 sp** (`max(16, fontSize) − 2`), 7 dp from the top, row height text + 14 dp;
  background `chat_serviceBackground` (semi-transparent), text `chat_serviceText`.

### 2.9 Conversation header — `ui/Components/ChatAvatarContainer.java` (v2 or later)

- Avatar **42 dp** (radius 21), title **18 sp**, subtitle **14 sp**. Our subtitle shows the contact
  state ("waiting for acceptance", "safety number not checked"), never "online" or "typing".

### 2.10 Input bar — `ui/Components/ChatActivityEnterView.java` (v2 or later)

- Height **44 dp** (one line), text **18 sp**, grows with the text up to a few lines; the send button
  appears on the end side when the text is not empty (Telegram swaps it with the microphone, which
  we do not have). No emoji or attach buttons (not supported).

### 2.11 Floating "new chat" button — `ui/DialogsActivity.java` (v2 or later)

- The standard 56 dp round floating button at the bottom end of the chat list, color
  `chats_actionBackground`, white icon (Material `Edit` or `PersonAdd`).

## 3. Steps after 6b (reworked)

| Step | Work | Tests | Model |
| --- | --- | --- | --- |
| TQ-109 | System Back (done in the working tree; waits for the device run and the owner's hand check) | `SystemBackInstrumentedTest` | Sonnet 5.5 · High |
| 6c | `Theme.kt` (light, dark; tokens from the theme notes), type scale (sizes above), `Avatar`, `UnreadBadge`, `Ticks`, `BubbleShape`, `DateChip`; a debug preview screen | unit: initials, color index, date rules (ru, en), grouping; Compose: components render, contrast check | Sonnet 5.5 · High |
| 6d | Chat list: Room v10 (`lastReadAtEpochMillis`, migration 9→10), queries for last message and unread count per chat, rows per 2.6, requests on top, floating button, ordering by last activity, empty state | migration test 9→10; repository tests; Compose list tests | **Opus 5.5 · High** for the migration, then Sonnet |
| 6e | Conversation: bubbles (2.1–2.3), date chips, header, input bar, scroll to the end, "read up to" updated on open | layout rule tests; Compose tests incl. landscape (F12 lesson) | Sonnet 5.5 · High |
| 6f | New chat (QR, scan, paste, share), contact request, contact info with the safety number, settings, onboarding in the new style | Compose tests; system Back on every screen | Sonnet 5.5 · High |
| 6f' | Notifications without message text: channel, Android 13+ permission asked in context, posted by the background sync, tap opens the chat, lock screen shows only "New message" | a test that no notification ever contains message text | **Opus 5.5 · High** for the privacy rules, then Sonnet |
| 6g | TQ-108: receive-queue loss screen (R6), `FLAG_SECURE` decision, telling a second scanner (I2), pruning digests (I5) | per item | **Opus 5.5 · High** |
| 6h | Usability check with someone new (first message within a minute), device tests on both phones, lint, release build, docs, commits | all suites | Sonnet 5.5 · High |

## 4. The owner's style decisions (2026-10-01)

1. **Bubbles: the classic look** — light green outgoing (`#EFFFDE`, green ticks), white incoming,
   black text. Palette: `PHASE6_THEME_NOTES.md`, section 2a.
2. **Wallpaper: a light pattern of our own** (Telegram's patterns are images and are not used).
3. **Dark theme: follows the system, with an in-app switch from the start** (Settings: Light, Dark,
   System); the dark palette is Telegram's Night values.
