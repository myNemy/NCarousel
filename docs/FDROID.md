# F-Droid publishing (step-by-step)

> **Maintainer-only.** This document is internal release and store-submission notes. It is **not** end-user documentation and is not meant for the general public reading the repo.

This doc guides maintainers through publishing **NCarousel** on **F-Droid**.

## What F-Droid will do vs what you do

- F-Droid will **build** the app from source and **sign** it with their key.
- You must provide:
  - A public git repository (you already have it on Forgejo/GitHub)
  - A release tag (recommended)
  - Versioning that increases over time (`versionCode`)
  - Metadata (title/description/changelog/screenshots)

## Prerequisites (one time)

### Android Studio / SDK

Make sure you can build the app locally at least once.

### Check the applicationId

For this project it is:

- `dev.nemeyes.ncarousel` (see `app/build.gradle.kts`)

## Step 1 — Prepare store metadata (in repo)

This repo includes Fastlane-style metadata files:

- `fastlane/metadata/android/en-US/title.txt`
- `fastlane/metadata/android/en-US/short_description.txt`
- `fastlane/metadata/android/en-US/full_description.txt`
- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`

What you should do for each release:

- Update descriptions if needed
- Add a new changelog file named after the **new** `versionCode`
- (Recommended) add screenshots under:
  - `fastlane/metadata/android/en-US/images/phoneScreenshots/`

## Step 2 — Bump versionCode and create a release tag

Open `app/build.gradle.kts` and update:

- `ncarouselLocalVersionCode`: increment by 1
- `ncarouselBaseVersionName`: bump if you want (e.g. patch version)

Then commit and create a tag, e.g. `v0.2.38`.

## Step 3 — Verify the release build locally

From the repo root (host with Android SDK):

```bash
unset NCAROUSEL_SIGNING_STORE_FILE NCAROUSEL_SIGNING_STORE_PASSWORD NCAROUSEL_SIGNING_KEY_ALIAS NCAROUSEL_SIGNING_KEY_PASSWORD GITHUB_RUN_NUMBER
./gradlew :app:assembleRelease
```

Without a local SDK, use **Podman** as in [DEVELOPMENT.md — Release build without a local SDK](DEVELOPMENT.md#release-build-without-a-local-sdk-podman). A successful run produces **`app/build/outputs/apk/release/app-release-unsigned.apk`**, which matches the expectation that **F-Droid signs** the published APK.

## Step 4 — Request inclusion in F-Droid

F-Droid app additions happen in the **[fdroiddata](https://gitlab.com/fdroid/fdroiddata)** repository on GitLab.

1. [Sign up / sign in](https://gitlab.com/users/sign_up) on GitLab and **fork** `fdroiddata`.
2. Add a new **file** under `metadata/`: **`metadata/dev.nemeyes.ncarousel.yml`** (see template below).  
   **Cartelle in `metadata/`:** molte app hanno **due** voci con lo stesso “nome” (es. `An.stop.yml` **e** cartella `An.stop/`). Il **file `.yml`** è la ricetta di build (obbligatoria per una nuova app). La **cartella** omonima serve a testi/screenshot extra per il sito F-Droid ([descrizioni e grafica](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)); non la crei tu all’inizio salvo che tu voglia fornirli lì. Per NCarousel basta il `.yml`; le descrizioni possono restare anche nel tuo repo (Fastlane) e i revisori le useranno se appropriate.
3. Open a **Merge Request** against `fdroiddata` with a short description: what the app does, license, source repo, and that it builds with standard Gradle (no proprietary blobs).
4. Watch the MR for **reviewer feedback** and CI (`fdroid build`); adjust the recipe if the build server reports missing SDK, wrong commit, etc.

### Choosing the `Repo:` URL

Either public git URL is valid:

- **GitHub**: `https://github.com/myNemy/NCarousel.git`
- **Forgejo**: `https://forgejo.it/Nemeyes/NCarousel.git`

Pick one as canonical for `Repo:` and keep tags pushed there. This repo pushes tags to both remotes when CI/secrets allow; align `commit:` in the recipe with a tag that exists on the URL you set (e.g. `v0.2.40`).

### Metadata template (`metadata/dev.nemeyes.ncarousel.yml`)

Copy into your **fdroiddata** fork and adjust **`commit`**, **`versionName`**, and **`versionCode`** to match the tag and `app/build.gradle.kts` for the release you are submitting. Remove this comment block before committing in fdroiddata.

```yaml
Categories:
  - Theming
  - Connectivity
License: AGPL-3.0-or-later
AuthorName: Nemeyes
WebSite: https://github.com/myNemy/NCarousel
SourceCode: https://github.com/myNemy/NCarousel
IssueTracker: https://github.com/myNemy/NCarousel/issues
Changelog: https://github.com/myNemy/NCarousel/releases
Repo: https://github.com/myNemy/NCarousel.git
AutoName: NCarousel

Builds:
  - versionName: 0.2.40
    versionCode: 54
    commit: v0.2.40
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+$
CurrentVersion: 0.2.40
CurrentVersionCode: 54
```

**Notes on the recipe**

- **`gradle: yes`** runs the Gradle build from the **repository root** (wrapper at top level, module `:app`). No `subdir:` is required for this layout.
- **`versionName` / `versionCode`** must match the **source** at `commit` (F-Droid reads `versionCode` from the built APK and checks consistency).
- After the first inclusion, the F-Droid team often tunes **`CurrentVersion*`** and update metadata; follow their MR comments.

Fastlane metadata in this repo (`fastlane/metadata/android/en-US/`) can be referenced in MR discussion for **description and screenshots**; final store text may still be edited during review.

## Notes / common pitfalls

- F-Droid does not use `GITHUB_RUN_NUMBER`, so make sure your **local fallback**
  `ncarouselLocalVersionCode` is bumped for each release.
- Avoid proprietary SDKs and binary downloads in Gradle.
- If the build needs secrets or CI-only files, F-Droid builds will fail.
- Do not rely on **`NCAROUSEL_SIGNING_*`** for F-Droid: release builds without those env vars must succeed (unsigned APK).

