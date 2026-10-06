# Third-party notices

Secure Messenger is free software, licensed under the **GNU Affero General Public License v3.0
only** (AGPL-3.0-only). The full text is in [`LICENSE`](LICENSE).
Copyright (C) 2026 the Secure Messenger contributors (github.com/77Skala77/Messenger).

## Components in the Android app

| Component | Version | License |
| --- | --- | --- |
| libsignal-client and libsignal-android (Signal Messenger) | 0.94.1 | AGPL-3.0-only |
| AndroidX: core, appcompat, activity, lifecycle, navigation, Compose UI and Material 3, Room, WorkManager | see `android-app/gradle/libs.versions.toml` | Apache-2.0 |
| Kotlin standard library and kotlinx.coroutines (JetBrains) | 2.2.20, 1.10.2 | Apache-2.0 |
| zxing-android-embedded (JourneyApps), with ZXing core | 4.3.0 | Apache-2.0 |
| desugar_jdk_libs (Google, derived from OpenJDK) | 2.1.5 | GPL-2.0 with the Classpath Exception |
| Source Sans 3 variable font (Adobe), file `core/ui/src/main/res/font/source_sans_3.ttf` | as downloaded 2026-10-06 | SIL Open Font License 1.1 (text in `licenses/SourceSans3-LICENSE.md`) |

Test-only, not shipped in the app: JUnit 4.13.2 (EPL-1.0), AndroidX Test and Espresso (Apache-2.0).
The relay (`android-app/server/relay`) uses only Node.js built-in modules.

## Code derived from Telegram for Android

Source: github.com/DrKLO/Telegram (Telegram for Android, Copyright Nikolai Kudashov and
contributors). Rules (`android-app/docs/PHASE6_UI_PLAN.md`, section 2; DEC-027):

- Files whose own header says "licensed under GNU GPL v. 2 or later" are translated or adapted
  under GPL version 3, which may be combined with this AGPL-3.0 work.
- Files with an Apache-2.0 header keep it.
- **Files without any license header** (the large majority of the repository) are also used, at
  the project owner's accepted legal risk (DEC-027, 2026-10-01): the repository states GPL v2 for
  them, neither "or later" nor "only", and they are used under GPL version 3 on the basis of
  GPLv2 section 9 for a program that does not specify a version. **This has not been confirmed by a
  lawyer** and is not claimed to be legally cleared; if a rights holder objects, the affected code
  will be removed or rewritten. Each such file is marked below.
- Files explicitly marked "GPL v2 only" would be excluded (none were found among 1,836 checked).
- Every derived file keeps the original header (if any) and says what it was derived from.
- The Telegram name and logo (the paper plane) are not used. Since DEC-033 (2026-10-06, the owner's
  decision) some of Telegram's icons and two of its sounds are used unchanged; each file is listed
  below. Stickers, fonts and other images are not used.
- Colors taken from Telegram's bundled theme files (`day`, `night`, `arctic`, `darkblue`,
  `bluebubbles`) and from its built-in classic defaults (`ui/ActionBar/ThemeColors.java`, no
  header) are plain values (numbers) used in this project's own theme files.

No Telegram source code has been copied or translated line by line. The files below were written
anew in Kotlin and Compose from **numbers and rules** read in the listed Telegram files (sizes,
radii, colors, grouping and date rules, the initials rule). They are listed so that the origin of
those numbers stays visible. Where the origin has no header, the owner's accepted risk applies
(DEC-027).

| Our file (`android-app/core/ui/src/main/kotlin/app/securemessenger/ui/`) | Numbers and rules read in (Telegram for Android, commit `f2908b1`) | Original header |
| --- | --- | --- |
| `MessengerColors.kt` | `ui/ActionBar/ThemeColors.java` (classic defaults); `assets/{day,night}.attheme` (Night values); a few values darkened or lightened for contrast | none (repository GPL v2); accepted risk |
| `AvatarModel.kt`, `Avatar.kt` | `ui/Components/AvatarDrawable.java` (initials rule, seven gradients, 18 on 42 text share); `ThemeColors.java` (gradient colors) | GPL v. 2 or later; none (accepted risk) |
| `BubbleShape.kt` | `ui/ActionBar/MessageDrawable.java` (radii 17 and 6, tail strip 8, tail tip 2.6 from the edge, tail join 9) | none (repository GPL v2); accepted risk |
| `SeriesGrouping.kt` | `ui/ChatActivity.java` (same direction, at most five minutes, a date separator breaks a series) | GPL v. 2 or later |
| `ChatDates.kt` | `messenger/LocaleController.java` (chat-list time, weekday, day-and-month, year rules; date separator forms) | GPL v. 2 or later |
| `DateChip.kt` | `ui/Cells/ChatActionCell.java` (14 sp, 7 dp above) | GPL v. 2 or later |
| `UnreadBadge.kt` | `ui/Cells/DialogCell.java` (height 20.67, radius 11.5, text padding 6.33, 13 sp bold) | GPL v. 2 or later |
| `MessengerType.kt` | `ui/Cells/*`, `ui/Components/ChatAvatarContainer.java`, `ChatActivityEnterView.java` (text sizes) | GPL v. 2 or later |
| `ChatList.kt`, `ChatListModel.kt` | `ui/Cells/DialogCell.java` (row 70 dp, avatar 52 dp, text from 72 dp, divider), `ui/DialogsActivity.java` (round 56 dp button) | GPL v. 2 or later |
| Icons in `core/ui/src/main/res/drawable-xxhdpi/tg_*.webp` and `tg_msg_delete.png` (menus, settings, search; drawn by `TelegramIcon.kt`) | `TMessagesProj/src/main/res/drawable-xxhdpi/`: `ic_ab_other.webp`, `msg_search`, `msg_settings`, `msg_pin`, `msg_unpin`, `msg_mute`, `msg_unmute`, `msg_delete.png`, `msg_archive`, `msg_info`, `msg_qrcode`, `msg_language`, `msg_theme`, `msg_notifications`, `msg_secret`, `msg_help`, `msg_contacts`, `msg_folders`, `msg_clear`, `msg_close`, `msg_select`, `msg_block`, `msg_invited`, `msg_edit`, `msg_share`, `msg_stats`, `msg_pin_code`, `msg_cancel` (`.webp`), copied unchanged, 28 files, 33 KB with the sounds, downloaded 2026-10-06 with the owner's permission | binary images have no header; the repository states GPL-2.0: accepted risk (DEC-033) |
| More icons in `core/ui/src/main/res/drawable-xxhdpi/` (pin and mute marks of a chat row, swipe actions, read, reply, mute for a time) | `TMessagesProj/src/main/res/drawable-xxhdpi/`: `list_pin.webp`, `list_mute.webp`, `chats_pin.webp`, `chats_unpin.webp`, `msg_markread.webp`, `ic_ab_reply.webp`, `msg_mute_period.webp`, copied unchanged as `tg_*.webp`, 7 files, 3.4 KB, downloaded 2026-10-06 with the owner's permission | binary images have no header; the repository states GPL-2.0: accepted risk (DEC-033) |
| `ChatListActions.kt` (swipe on a chat row: 45 percent threshold, 3500 px/s escape velocity, 200 ms back, icon 43 dp from the edge, reveal circle; band colors `#E56555` and reveal `#9FAAB3`; selection check; mute periods), `MessageReplies.kt` (swipe to reply: start 0.4 cm with axis ratio 3, at most 80 dp, answer from 50 dp with one tick, sign from 20 dp, 32 dp sign, ring, 180 ms back), `OnboardingScreen.kt` (round 56 dp button of the registration pages) | `ui/DialogsActivity.java` (its `SwipeController`), `ui/Cells/DialogCell.java` (swipe drawing), `ui/ChatActivity.java` (sliding view, reply button), `ui/ActionBar/ThemeColors.java` (`dialogSwipeRemove`, `chats_archivePinBackground`), `ui/Components/CheckBox2.java` (the check's look) | `DialogsActivity.java`, `DialogCell.java`, `ChatActivity.java`: GPL v. 2 or later; `ThemeColors.java`, `CheckBox2.java`: none (accepted risk) |
| Sounds `core/ui/src/main/res/raw/tg_sound_in.wav`, `tg_sound_out.wav` (`ChatSounds.kt`) | `TMessagesProj/src/main/res/raw/sound_in.wav`, `sound_out.wav`, copied unchanged | no header; accepted risk (DEC-033) |
| `TelegramTopBar.kt` (bar 56/48 dp, back button 54 dp, title 20 sp at 72 dp, back arrow geometry), `TelegramPopupMenu.kt` (popup timing, item height 48 dp, corner 12 dp, cascade), `TelegramAlertDialog.kt` (look: rounded card, title and text sizes), send-button scale in `ConversationScreen.kt` | `ui/ActionBar/ActionBar.java`, `ui/ActionBar/BackDrawable.java`, `ui/ActionBar/ActionBarPopupWindow.java`, `ui/ActionBar/AlertDialog.java`, `ui/Components/ChatActivityEnterView.java` (numbers and geometry only; code written anew in Compose) | all five: GPL v. 2 or later |
| `TelegramMotion.kt`, `TelegramScreenStack.kt` | `ui/ActionBar/ActionBarLayout.java` (screen open and close 150 ms, 48 dp slide, decelerate 1.5; swipe back: 0.4 cm, axis ratio 3, fling 3500, one third of the width, 200 and 320 ms, scrim and shadow rules), `ui/Components/CubicBezierInterpolator.java` (the curve constants) | `ActionBarLayout.java`: GPL v. 2 or later; `CubicBezierInterpolator.java`: none (accepted risk) |
| `TelegramMotion.kt` (lists, counter, button, date), `UnreadBadge.kt`, `ConversationScreen.kt` | `ui/recyclerview/ChatListItemAnimator.java` (250 ms, the move curve, 0.9 scale), `ui/Components/DialogsItemAnimator.java` (180 ms), `ui/Components/CounterView.java` (220, 150 and 430 ms, 13 dp roll), `ui/Components/chat/layouts/ChatActivitySideControlsButtonsLayout.java` (280 ms, 0.7 scale, 80 dp), `ui/ChatActivity.java` (floating date, 150 ms, hide delays) | `ChatActivity.java`: GPL v. 2 or later; the other four: none (accepted risk) |
| `ConversationScreen.kt`, `ConversationModel.kt` | `ui/Cells/ChatMessageCell.java` (time on the last line when it fits, 11 dp text start, bubble width limit), `ui/Components/ChatActivityEnterView.java` (44 dp bar, 18 sp), `ChatAvatarContainer.java` (42 dp avatar) | GPL v. 2 or later |
