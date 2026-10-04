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
- The Telegram name, logo, icons, stickers, sounds, fonts and images are not used.
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
