<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->

## NCarousel

Android app that downloads images from your **Nextcloud** server (WebDAV) and sets them as the device **wallpaper**, using official Android APIs. The UI is built with **Jetpack Compose**; background work uses **WorkManager** (including short intervals via chained one-shot work). Credentials are stored with **EncryptedSharedPreferences**.

The app follows the system locale when translations are available (default English, plus several European languages under `app/src/main/res/values-*`).

## Download

- **[GitHub Releases](https://github.com/myNemy/NCarousel/releases)** — for each release tag `v*`, CI attaches `NCarousel-<version>-debug.apk` and, when [signing secrets](docs/DEVELOPMENT.md) are set on the repo, a **release** APK `NCarousel-<version>.apk` (same keystore as CI). Builds are for testing and sideloading.
- **[GitHub Actions](https://github.com/myNemy/NCarousel/actions)** — artifacts **`app-debug-apk`** (always) and **`app-release-apk`** (when secrets are configured).

Releases and tags are created automatically on `main` after a green build (see `.github/workflows/android-ci.yml`). To mirror release tags to **Forgejo**, maintainers can set the `FORGEJO_PUSH_TOKEN` repository secret on GitHub.

## Development

Clone either remote below, then see **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)** for JDK/SDK requirements, local builds, and optional CI signing secrets so successive GitHub APKs can upgrade in place.

## License

Licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later). See `LICENSE`.

## Repositories

Source and issue tracking:

- **Forgejo**: [forgejo.it/Nemeyes/NCarousel](https://forgejo.it/Nemeyes/NCarousel)
- **GitHub** (mirror): [github.com/myNemy/NCarousel](https://github.com/myNemy/NCarousel)
