<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->

## NCarousel

Android app that downloads images from your **Nextcloud** server (WebDAV) and sets them as the device **wallpaper**, using official Android APIs.

## License

Licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later). See `LICENSE`.

## Getting the app

Pre-built **debug APKs** are produced by [GitHub Actions](https://github.com/myNemy/NCarousel/actions) on each push to `main`. Open the latest successful workflow run → **Artifacts** → download `app-debug-apk`, then install the APK on your device (you may need to allow installation from unknown sources).

> Debug builds are meant for testing. For day-to-day use, prefer a build you trust; see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) if you build or publish releases yourself.

## For developers

Build setup, CI signing (so APKs from Actions can upgrade without reinstall), and local Gradle commands are documented in **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)**.
