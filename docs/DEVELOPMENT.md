# Development

Notes for people who **clone**, **build**, **download CI artifacts**, or **maintain** signing for NCarousel.

## Branch workflow (`dev` / `main` / optional `feature/*`)

Same local clone; switch with `git switch`. Cursor rules: `.cursor/rules/06-git-branch-workflow.mdc`.

| Branch | Purpose |
|--------|---------|
| **`dev`** | Default daily work. Push → CI **debug** artifact only. No F-Droid version bump, no official GitHub Release / `Binaries` APK. |
| **`feature/<slug>`** | Optional for a long or risky task. Branch from `dev`, merge back into `dev`. |
| **`main`** | **Publish** only. Merge `dev` → bump version + Fastlane changelog → push → Release APK → sync `fdroiddata` → merge `main` back into `dev`. |

**Continuation**

1. Resume on `dev` (pull). Short fix → stay on `dev`; long task → `feature/<slug>` from `dev`.
2. Iterate: commit/push → download **`app-debug-apk`** from Actions → sideload → repeat.
3. When a feature branch is done: merge into `dev`, push `dev`.
4. When ready to ship: merge `dev` → `main`, bump, push `main`, wait for Release APK, sync F-Droid, then **merge `main` → `dev`** and push `dev`.

Ordinary “commit and push” on `dev` is a trial. Phrases like **publish** / **release** / **merge to main** / **F-Droid** mean the full `main` publish path.

## Pre-built APKs (GitHub Actions)

**Android CI** runs on pushes to `main`, `dev`, and `feature/**` (and PRs targeting `main` / `dev`).

| Ref | Debug artifact | Release APK + tag + GitHub Release |
|-----|----------------|--------------------------------------|
| `dev` / `feature/**` | Yes (`app-debug-apk`) | No |
| `main` (push) | Yes | Yes (when `NCAROUSEL_*` signing secrets are set) |

**GitHub Releases** (only after a green build on **`main`**) attach:

- `NCarousel-<version>.apk` — **release** build (only when `NCAROUSEL_*` signing secrets are set). Uses `ncarouselBaseVersionName` and `ncarouselLocalVersionCode` (CI sets `NCAROUSEL_PUBLISH_RELEASE_APK=true` for `assembleRelease`) so F-Droid `Binaries` verification matches the built APK.
- `NCarousel-<version>-debug.apk` — **debug** build on that release. With signing secrets, uses the same `versionName` / `versionCode` as the release APK so you can install release over debug. Without secrets, debug may use `0.2.47+<run>` and `versionCode` `1000+<run>`.

### “Invalid package” / “pacchetto non valido” when sideloading

1. **Downgrade (most common):** Older GitHub **debug** builds used `versionCode` **1000+** (e.g. `1101`). Installing **0.2.48** (`versionCode` **62**) fails on top of that install — **both** release and debug look “invalid”. **Fix:** uninstall NCarousel completely, then install the latest `NCarousel-<version>.apk`. From **0.2.49** onward, published APKs use **`versionCode` 1103+** so they upgrade older CI installs without uninstall when possible.
2. **Fresh install still fails:** Ensure the download finished (GitHub Releases, not a truncated chat attachment). CI signs with **v1+v2+v3** (see `app/build.gradle.kts` `enableV1Signing`).

### Tags, versions, and when you see a “new” release

On **`main` publish**, Cursor rules require bumping **`ncarouselBaseVersionName`** (at least patch), **`ncarouselLocalVersionCode`** (+1), and a Fastlane changelog (`.cursor/rules/50-commit-push-release-automation.mdc`). Trial commits on **`dev`** do not bump for F-Droid.

CI reads **`ncarouselBaseVersionName`** from `app/build.gradle.kts` and uses the git tag **`v<that string>`** (e.g. `v0.2.40`).

- **First time** that tag appears on GitHub: CI creates the annotated tag (if missing) and creates the GitHub Release, then uploads the APKs.
- **Later pushes to `main`** that **do not** change `ncarouselBaseVersionName`: the **same** tag and Release are reused. CI **refreshes** `NCarousel-<version>-debug.apk` but **does not replace** `NCarousel-<version>.apk` once it is already on the release (so F-Droid `Binaries` reproducible verification stays aligned with `Builds.commit`). The Releases page does **not** gain an extra row. The release **title** includes the workflow run number so you can see when assets were refreshed.
- **F-Droid:** after a `main` version bump, sync `../fdroiddata/metadata/dev.nemeyes.ncarousel.yml` (`commit` = `git rev-parse "v<version>^{commit}"`). See [FDROID.md](FDROID.md) and `.cursor/rules/61-fdroiddata-ncarousel-metadata-consistency.mdc`.
- **A new row** on the Releases page requires **bumping** `ncarouselBaseVersionName` on the publish commit. Pushing app code only to **`dev`** does **not** create a new store release.

Optional: set repository secret **`FORGEJO_PUSH_TOKEN`** on GitHub so CI also pushes the same tag to Forgejo (see `.github/workflows/android-ci.yml`).

To download a **trial** build from a `dev` / `feature/**` workflow run:

1. Open **[Actions](https://github.com/myNemy/NCarousel/actions)** for this repository.
2. Select the successful **Android CI** run for that branch.
3. Under **Artifacts**, download **`app-debug-apk`**.

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
