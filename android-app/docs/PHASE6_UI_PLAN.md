# Phase 6 — Telegram-style UI: plan

Status: **ACCEPTED** (2026-10-01, step 6a done: the owner's decisions are in section 7, DEC-027).
Next: step 6b. Plan section: `ai-workflow/REWRITE_PLAN.md`, Phase 6.
Visual only: protocol v2, wire, invitation and storage formats stay as they are; release builds keep
the chat closed until the external audit (TQ-106).

## 1. Goal and exit gate

A Telegram-familiar Android UI on this project's own Compose code: a chat list (avatar, name, last
message, time, unread count), a conversation with bubbles grouped by sender and time and honest
ticks ("sent to the server", "delivered"; never "read"), a clear contact and safety-number screen,
light and dark themes. Exit gate (`REWRITE_PLAN.md`): someone who has never seen the app sends a
first message within a minute without explanation; all suites green; the owner's hand check.

## 2. Using Telegram for Android's code (owner decision, 2026-10-01)

Telegram for Android (github.com/DrKLO/Telegram) is open source. Checked on 2026-10-01:

- The repository `LICENSE` is **GPL v2** (no "or later").
- Source files carry their own header. A survey of all 1,836 Java files under `org/telegram/ui` and
  `org/telegram/messenger` (local reference copy, commit `f2908b1`, 12.10.6, 2026-09-30):
  **416** say "licensed under GNU GPL v. 2 or later"; **1,366 have no header** and fall under the
  repository's GPL v2 only; **54** carry other third-party notices.
- Of the pieces this phase wants:

| Piece | File | Header | Use |
| --- | --- | --- | --- |
| Avatar with initials and color | `ui/Components/AvatarDrawable.java` | v2 or later | may be ported |
| Chat-list row | `ui/Cells/DialogCell.java` | v2 or later | may be ported (metrics, drawing order; heavily tied to their data) |
| Message cell (time and ticks placement) | `ui/Cells/ChatMessageCell.java` | v2 or later | may be ported in parts |
| Date separators | `ui/Cells/ChatActionCell.java` | v2 or later | may be ported |
| Input bar | `ui/Components/ChatActivityEnterView.java` | v2 or later | may be ported in parts |
| Chat header (avatar, name, status) | `ui/Components/ChatAvatarContainer.java` | v2 or later | may be ported |
| Date and time rules | `messenger/LocaleController.java` | v2 or later | may be ported |
| Theme structure | `ui/ActionBar/Theme.java` | v2 or later | may be ported (colors are facts anyway) |
| Message bubble shape | `ui/ActionBar/MessageDrawable.java` | none (repository GPL v2) | may be ported at the owner's accepted risk (rule 2) |
| Unread counter | `ui/Components/CounterView.java` | none (repository GPL v2) | same |
| Check boxes, list item animator, animated text | `CheckBoxBase`, `ChatListItemAnimator`, `AnimatedTextView` | none (repository GPL v2) | same |
- The README asks forks not to use the name "Telegram" or its logo (a white paper plane in a blue
  circle), to publish their own code, and to get their own api_id (not needed here: this app never
  talks to Telegram's servers).

Rules for this project, decided by the owner (we publish everything, under AGPL-3.0):

1. **Port isolated, strong pieces** (bubble shape, chat-list row layout, avatar initials and colors,
   date rules, ticks, small animations) by translating them to Kotlin/Compose, **only from files
   whose header says "GPL v. 2 or later"**: GPL v2-or-later code can be used under GPL v3, which may
   be combined with AGPL-3.0. Keep the original header and copyright in every derived file, mark it
   as derived from Telegram for Android, and list it in `THIRD_PARTY_NOTICES.md`.
2. **Files without a header** (1,366 files, e.g. `CounterView.java`, `MessageDrawable.java`): the
   repository states GPL v2 for them without "or later" and without "only". Using them under GPL v3
   (needed to combine them with this AGPL-3.0 work) rests on GPLv2 section 9 ("a program that does not
   specify a version: any version"), which is an **open legal question**: no lawyer's opinion has been
   obtained (two documents received on 2026-10-01 were not one; they say themselves that a headerless
   file must be checked against the project's general terms). **The owner knowingly accepts this
   risk (2026-10-01, DEC-027):** such files MAY be ported, one by one, each recorded in
   `THIRD_PARTY_NOTICES.md` as "no header; GPL v2 repository; used under GPLv3 at the owner's accepted
   risk". The risk (a rights holder could object, and the code would then have to be removed or
   rewritten) is stated in the notices, not hidden. Where a file is also easy to write anew, writing
   it anew is still preferred. Files with an explicit "GPL v2 only" marking would still be excluded
   (none exist among the 1,836 checked), and the 54 files with their own Apache-2.0 header keep it.
3. **Everything tied to their data model or networking** (most of `ChatMessageCell`, `DialogCell`,
   `ChatActivity`) is rebuilt from screenshots and screen recordings, not ported.
4. **Never** the Telegram name, logo, icons, stickers, sounds, fonts or images: Material Icons and
   Roboto instead. A similar feel is the goal, not a copy of their brand.
5. The project needs its own **`LICENSE` (AGPL-3.0)** before any ported code lands; libsignal
   (AGPL-3.0) already requires it for any distribution (owner decision 2, `REWRITE_PLAN.md`).

This is engineering practice, not legal advice.

## 3. Starting point

Today's v2 UI (`core/ui/.../V2Screens.kt`, `SecureMessengerApp.kt`): Material 3 cards and buttons; a
home list of contact names and states, requests as cards, a conversation of plain cards with a
status line, the safety number as text, QR invitation, scan or paste, settings. No avatars, last
message, times, unread counts, bubbles, grouping, ticks, or dark theme.

## 4. Target screens

| Screen | Telegram-like target | Source |
| --- | --- | --- |
| Chat list | avatar with initials and a color, name, last message, time, unread badge, tick of the last outgoing message; requests on top; a floating "new chat" button | row metrics and avatar from `DialogCell` / `AvatarDrawable` (v2-or-later); badge re-implemented |
| Conversation | bubbles with a tail, grouped by sender and time, date separators, time and ticks inside the bubble, input bar with a send button | bubble from `MessageDrawable.java` (headerless, accepted risk) or written anew; date rules from `LocaleController`, separators from `ChatActionCell`, input bar from `ChatActivityEnterView` (v2-or-later); the rest from screenshots |
| Contact info | avatar, name, safety number grouped in fives with a plain explanation, "verified in person" guidance | own design |
| New chat | "show my QR", "scan", "paste a code", share | own design, current flow |
| Contact request | claimed name marked unverified, safety number, accept with a local name, decline | own design |
| Settings, onboarding, local-data problem | same functions, new look | own design |

Never shown: "read" ticks (not in the protocol), online or typing status, anything a contact did not
authenticate.

## 5. Data the UI needs (compatibility)

- Last message per chat and per-chat ordering by last activity: from existing rows, no schema change.
- Message times: `createdAtEpochMillis` exists.
- **Unread counts** need a "read up to" marker per chat. Proposal: Room v10 adds a nullable column
  `lastReadAtEpochMillis` to `v2_contacts` (`ALTER TABLE ... ADD COLUMN`, no data rewritten, a
  migration test 9→10). A time, not content: not encrypted, like the other timestamps. Needs the
  owner's approval (`AGENTS.md` compatibility rules).
- Avatar colors from a hash of the local contact ID (never sent anywhere).

## 6. Steps and models

| Step | Work | Model |
| --- | --- | --- |
| 6a | This plan, the owner's decisions, `LICENSE`, `THIRD_PARTY_NOTICES.md` | Opus 5.5 · High |
| 6b | Study Telegram's sources: map each wanted piece to its file, check every header, write the porting notes | Sonnet 5.5 · High |
| 6c | Theme (light, dark; starting colors in `PHASE6_THEME_NOTES.md`), typography, avatar, badge, ticks | Sonnet 5.5 · High |
| 6d | Chat list (and unread marker, if approved) | Sonnet 5.5 · High; Opus for the Room migration |
| 6e | Conversation: bubbles, grouping, dates, input bar | Sonnet 5.5 · High |
| 6f | New chat, request, contact info, settings, onboarding | Sonnet 5.5 · High |
| 6f' | Notifications without text (channel, permission, posting from the sync) | Opus 5.5 · High for the privacy rules, then Sonnet |
| 6g | TQ-108: receive-queue loss screen (R6), `FLAG_SECURE` decision, telling a second scanner (I2), pruning digests (I5) | Opus 5.5 · High |
| 6h | Usability check with a new person, device tests on both phones, docs, commits | Sonnet 5.5 · High |

## 7. Owner decisions (2026-10-01, DEC-027)

1. **License:** the repository gets `LICENSE` (AGPL-3.0, the official text) and
   `THIRD_PARTY_NOTICES.md` now. Default stated: AGPL-3.0-only, like libsignal (the owner may choose
   "or later" instead).
2. **Reference copy of Telegram's sources:** downloaded read-only to `D:\Tools\reference\Telegram`
   (sparse, shallow: `org/telegram/ui` and `org/telegram/messenger`, 61 MB, commit `f2908b1`); never
   built, run, or added to this repository.
3. **Unread counters:** yes; Room v10 with `lastReadAtEpochMillis` on `v2_contacts` (section 5).
4. **Notifications for new messages:** yes, **without message text**: "New message from <local
   name>"; the text never reaches the lock screen or Android's notification history. Driven by the
   background sync (about every 15 minutes or later). Needs the Android 13+ notification
   permission, asked in context.
5. **Legal risk accepted (2026-10-01):** headerless Telegram files may be used under GPLv3 at the
   owner's own risk; no lawyer's opinion exists (rule 2 in section 2). Recorded in DEC-027 and
   `THIRD_PARTY_NOTICES.md`.
6. **Color themes:** Telegram's five bundled theme files (`day`, `night`, `arctic`, `darkblue`,
   `bluebubbles` `.attheme`, headerless) are studied for colors; the analysis is in
   `PHASE6_THEME_NOTES.md`. The colors are used as plain values for our own light and dark themes.
   Copies for study: `D:\Tools\reference\themes` (outside the repository).
