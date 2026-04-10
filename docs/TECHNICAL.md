<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->

# Technical overview

This document is a short, developer-oriented overview of NCarousel’s architecture and key technical choices.

## Core goal

- Download images from a user-provided **Nextcloud** instance via **WebDAV**.
- Set the device **wallpaper** via official Android APIs.

## Tech stack

- **Language**: Kotlin
- **Build**: Gradle (Kotlin DSL)
- **UI**: Jetpack Compose
- **Background scheduling**: WorkManager
- **Networking**: OkHttp
- **Image loading**: Coil
- **Persistence**:
  - Credentials: **EncryptedSharedPreferences** (never plaintext).
  - Metadata/cache: Room (offline-first list / state).

## Scheduling constraints

- The app supports a minimum wallpaper interval of **1 minute**.
- WorkManager’s **periodic work** minimum is 15 minutes, so short intervals use **chained one-shot work** with delays.

## Localization

- Default language is **English** (`app/src/main/res/values/strings.xml`).
- Additional translations are under `app/src/main/res/values-*`.

## CI releases (GitHub)

On pushes to `main` (after a green build), CI manages the tag **`v<ncarouselBaseVersionName>`**, the matching GitHub Release, and attached APKs. **New release entries vs updating the same release** depend on whether the version string in Gradle was bumped—see [docs/DEVELOPMENT.md](DEVELOPMENT.md) (“Tags, versions, and when you see a new release”) and `.github/workflows/android-ci.yml`.

