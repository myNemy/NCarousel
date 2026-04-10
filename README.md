<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->

## NCarousel

Android app that downloads images from your **Nextcloud** server (WebDAV) and sets them as the device **wallpaper**, using official Android APIs.

The app follows the system locale when translations are available (default English, plus several European languages).

## Download

- **[GitHub Releases](https://github.com/myNemy/NCarousel/releases)** — CI publishes one GitHub Release per **`v<versionName>`** tag, where `versionName` is `ncarouselBaseVersionName` in `app/build.gradle.kts`. **Pushes that do not bump that value update the APK files on the existing release** (same tag); you will not see a brand‑new release row until the version string is increased. Release titles include the CI run number so you can tell when assets were refreshed.
- **[GitHub Actions](https://github.com/myNemy/NCarousel/actions)** — every successful run on `main` also uploads workflow artifacts (`app-debug-apk`, and `app-release-apk` when signing secrets are set).

Builds are for testing and sideloading. See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for signing and upgrade behaviour.

## License

Licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later). See `LICENSE`.

## Repositories

Source and issue tracking:

- **Forgejo**: [forgejo.it/Nemeyes/NCarousel](https://forgejo.it/Nemeyes/NCarousel)
- **GitHub** (mirror): [github.com/myNemy/NCarousel](https://github.com/myNemy/NCarousel)
