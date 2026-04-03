<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->

## NCarousel

Android app that downloads images from a Nextcloud instance (WebDAV) and sets them as the device wallpaper using official Android APIs.

## License

Licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later). See `LICENSE`.

Note: the Gradle wrapper scripts (`gradlew`, `gradlew.bat`) are third-party files distributed under **Apache-2.0** (as stated in their headers).

## Build requirements

- **JDK 17** (this project targets Java 17; the CI workflow uses Temurin 17).
- Android SDK (e.g. via Android Studio); `local.properties` with `sdk.dir` for command-line builds.

## GitHub Actions APK (upgrade without uninstall)

Each clean GitHub runner creates a **new** default Android debug keystore, so successive **debug** APKs from workflow artifacts are signed with **different keys**. Android then blocks updates and you must uninstall before installing another build from Actions.

To get a **stable signature** for CI builds:

1. Create a keystore (once, keep a backup):

   ```bash
   keytool -genkeypair -v -keystore ncarousel-ci.jks -alias ncarousel -keyalg RSA -keysize 2048 -validity 36500
   ```

2. Base64-encode it (single line of output):

   - Linux: `base64 -w0 ncarousel-ci.jks`
   - macOS: `base64 -i ncarousel-ci.jks | tr -d '\n'`

3. In the GitHub repo: **Settings → Secrets and variables → Actions**, add these **optional** secrets (if any are missing, the workflow still builds, but the APK signature changes every run):

   | Secret | Value |
   |--------|--------|
   | `NCAROUSEL_KEYSTORE_B64` | output of base64 |
   | `NCAROUSEL_KEYSTORE_PASSWORD` | keystore password |
   | `NCAROUSEL_KEY_ALIAS` | e.g. `ncarousel` |
   | `NCAROUSEL_KEY_PASSWORD` | key password |

After the next workflow run, new debug APKs from Actions will **upgrade** previous installs from Actions (still bump `versionCode` as usual).

**Local `./gradlew assembleDebug`** keeps using your machine’s `~/.android/debug.keystore`. The first time you install an APK **from CI** over a **local** debug install (or the reverse), Android may still require uninstall because the signing keys differ. Use either only CI builds or point Gradle at the same keystore via the same `NCAROUSEL_SIGNING_*` environment variables when building locally.

