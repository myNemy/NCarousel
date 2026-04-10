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

On pushes to `main` (after a green build), CI:

- Ensures a tag `v<ncarouselBaseVersionName>` exists.
- Creates or updates a GitHub Release for that tag and attaches:
  - `NCarousel-<version>.apk` (release build, when signing secrets are set)
  - `NCarousel-<version>-debug.apk` (debug build)

See `.github/workflows/android-ci.yml` and [docs/DEVELOPMENT.md](DEVELOPMENT.md).

