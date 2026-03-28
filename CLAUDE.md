# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Share2Obsidian is a minimal Android share-target app. It intercepts `ACTION_SEND` intents with `text/plain` content from other Android apps and forwards the text to Obsidian via the `obsidian://new` deep link URI scheme. The app has no visible UI — it processes the intent and immediately finishes.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew build                  # Full build (all variants)
./gradlew clean                  # Clean build outputs
./gradlew lint                   # Run lint checks
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
```

## Architecture

**Single-activity, intent-driven.** `MainActivity` is the only activity and never displays any Compose UI to the user. Flow:

1. Another app shares text → Android routes the `ACTION_SEND` intent to this app
2. `MainActivity.onCreate` extracts `EXTRA_TEXT` and optionally `EXTRA_SUBJECT`
3. If a subject/title exists, content is formatted as `# Title\n\nText`; otherwise plain text is used
4. An `obsidian://new?content=...` URI is constructed and launched via `startActivity`
5. If Obsidian is not installed (`ActivityNotFoundException`), a Toast is shown (in Japanese)
6. `finish()` is called immediately — the app never stays in the foreground

**Key files:**
- `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt` — all app logic lives here
- `app/src/main/AndroidManifest.xml` — declares the `ACTION_SEND` intent filter and queries the `obsidian://` scheme

## SDK & Language Versions

- **minSdk:** 33 (Android 13)
- **targetSdk/compileSdk:** 36
- **Kotlin:** 2.2.10
- **AGP:** 9.1.0
- **Java compatibility:** 11

Dependencies are managed via the version catalog at `gradle/libs.versions.toml`. Compose BOM is pinned to `2024.09.00`.

## Localization Note

Error messages are currently in Japanese (e.g., "Obsidian がインストールされていません"). Keep this in mind when modifying user-facing strings.
