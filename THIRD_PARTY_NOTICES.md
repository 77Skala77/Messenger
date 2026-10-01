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

| Our file | Derived from (Telegram for Android, commit `f2908b1`) | Original header |
| --- | --- | --- |
| none yet | | |
