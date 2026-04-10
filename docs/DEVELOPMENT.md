# Development

Notes for people who **clone**, **build**, **download CI artifacts**, or **maintain** signing for NCarousel.

## Pre-built APKs (GitHub Actions)

On each push to `main`, the workflow **Android CI** builds a **debug** APK. If repository signing secrets are configured (see **GitHub Actions: stable APK signature** below), it also builds a **release** APK (`assembleRelease`), signed with the same keystore as CI debug.

**GitHub Releases** (on `main` after a green build) attach:

- `NCarousel-<version>.apk` — **release** build (only when `NCAROUSEL_*` signing secrets are set).
- `NCarousel-<version>-debug.apk` — **debug** build (always).

### Tags, versions, and when you see a “new” release

Repository **Cursor rules** (`.cursor/rules/50-commit-push-release-automation.mdc`) require bumping **`ncarouselBaseVersionName`** (at least patch), **`ncarouselLocalVersionCode`** (+1), and adding a Fastlane changelog **before** commit/push of **app-impacting** changes, so each such push yields a **new** GitHub Release entry.

CI reads **`ncarouselBaseVersionName`** from `app/build.gradle.kts` and uses the git tag **`v<that string>`** (e.g. `v0.2.40`).

- **First time** that tag appears on GitHub: CI creates the annotated tag (if missing) and creates the GitHub Release, then uploads the APKs.
- **Later pushes** that **do not** change `ncarouselBaseVersionName`: the **same** tag and Release are reused; CI **replaces** the APK assets (`--clobber`). The Releases page does **not** gain an extra row—only the files on that version’s release change. The release **title** includes the workflow run number so you can see when assets were refreshed.
- **A new row** on the Releases page requires **bumping** `ncarouselBaseVersionName` (and, for installable builds, following the project’s `versionCode` / Fastlane changelog rules). **Changing app code alone does not create a new tag or a new release entry.**

Optional: set repository secret **`FORGEJO_PUSH_TOKEN`** on GitHub so CI also pushes the same tag to Forgejo (see `.github/workflows/android-ci.yml`).

To download from a workflow run without using Releases:

1. Open **[Actions](https://github.com/myNemy/NCarousel/actions)** for this repository.
2. Select the latest successful **Android CI** run.
3. Under **Artifacts**, download **`app-debug-apk`** and, if present, **`app-release-apk`**.

These CI builds are intended for testing and sideloading. For a stable signature between runs (so Android can upgrade without uninstall), configure repository secrets as described below.

## Build requirements

- **JDK 17** (project targets Java 17; Android Gradle Plugin 8.x does not run on Java 8).
- Android SDK (Android Studio or `cmdline-tools`), with `local.properties` or `ANDROID_HOME` set.

From the repository root:

```bash
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/`.

## Release build without a local SDK (Podman)

If you do not have `ANDROID_HOME` / Android Studio on the host, you can build with **Podman** and a small SDK image (same idea as “clean machine” / F-Droid-like).

Pull once:

```bash
podman pull ghcr.io/cirruslabs/android-sdk:35
```

From the repository root, **release** without CI signing env (F-Droid signs its own APKs; this produces `app-release-unsigned.apk`):

```bash
podman run --rm \
  -v "$PWD:/project:Z" \
  -w /project \
  -e ANDROID_HOME=/opt/android-sdk-linux \
  -e ANDROID_SDK_ROOT=/opt/android-sdk-linux \
  ghcr.io/cirruslabs/android-sdk:35 \
  bash -lc 'unset NCAROUSEL_SIGNING_STORE_FILE NCAROUSEL_SIGNING_STORE_PASSWORD NCAROUSEL_SIGNING_KEY_ALIAS NCAROUSEL_SIGNING_KEY_PASSWORD GITHUB_RUN_NUMBER; chmod +x ./gradlew; ./gradlew :app:assembleRelease --no-daemon'
```

APK output: `app/build/outputs/apk/release/app-release-unsigned.apk`.

On Fedora/RHEL with SELinux, `:Z` on the volume mount relabels the tree for the container; omit it on typical Arch installs if you prefer.

The image ships **JDK 21**; AGP 8.x accepts it. For a **debug** build, use `./gradlew :app:assembleDebug` in the same command.

## Gradle wrapper license

The Gradle wrapper scripts (`gradlew`, `gradlew.bat`) are third-party files under **Apache-2.0** (see their file headers).

## GitHub Actions: stable APK signature (upgrade without uninstall)

Each clean GitHub Actions runner may create a **new** default Android debug keystore, so successive **debug** APKs can be signed with **different keys**. Android then blocks in-place updates and you must uninstall before installing another build from Actions.

**Maintainers** can configure a **fixed keystore** via repository secrets so every CI build uses the same signature:

1. Create a keystore (once, keep a secure backup):

   ```bash
   keytool -genkeypair -v -keystore ncarousel-ci.jks -alias ncarousel -keyalg RSA -keysize 2048 -validity 36500
   ```

2. Base64-encode it (single line):

   ```bash
   base64 -w0 ncarousel-ci.jks   # Linux
   # macOS: base64 -i ncarousel-ci.jks | tr -d '\n'
   ```

3. In the GitHub repo: **Settings → Secrets and variables → Actions**, add:

   | Secret | Value |
   |--------|--------|
   | `NCAROUSEL_KEYSTORE_B64` | output of base64 |
   | `NCAROUSEL_KEYSTORE_PASSWORD` | keystore password |
   | `NCAROUSEL_KEY_ALIAS` | e.g. `ncarousel` |
   | `NCAROUSEL_KEY_PASSWORD` | key password |

After the next workflow run, new debug APKs from Actions should **upgrade** previous installs from Actions (still keep `versionCode` monotonic; CI uses `GITHUB_RUN_NUMBER` when set).

**Local** `./gradlew assembleDebug` uses your machine’s `~/.android/debug.keystore` unless you set the same `NCAROUSEL_SIGNING_*` environment variables and keystore path as in `app/build.gradle.kts`. Mixing local and CI installs can still require uninstall if signing keys differ.

The workflow step that decodes the keystore is defined in `.github/workflows/android-ci.yml`.
