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

Before opening an MR, skim the official **[F-Droid Inclusion Policy](https://f-droid.org/wiki/page/Inclusion_Policy)**. In short, the main repo only ships **FLOSS** apps built **from published source** with a **transparent** dependency story; **proprietary** tracking, ads, and typical Play-services analytics stacks are **not** allowed. NCarousel is intended to align with that (AGPL-3.0-or-later, `dev.nemeyes.ncarousel`, Gradle deps from standard Maven repos, no opt-in bypass for silent binary downloads). **Donation** / funding URLs in metadata must be **verifiable** upstream (same policy). When you add optional metadata keys later, fill them with real values—**do not** uncomment optional lines until you have content; empty active keys are worse than omitting the field.

F-Droid app additions happen in the **[fdroiddata](https://gitlab.com/fdroid/fdroiddata)** repository on GitLab.

1. [Sign up / sign in](https://gitlab.com/users/sign_up) on GitLab and **fork** `fdroiddata`.
2. **Create a dedicated branch on your fork** before adding metadata (recommended by [fdroiddata CONTRIBUTING](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md): easier MR tracking, avoids piling unrelated commits on your fork’s `master`).  
   Example after cloning your fork: `git checkout -b dev.nemeyes.ncarousel`  
   **If you already committed on `master`:** you can still open the MR with **source branch = `master`** of your fork — that is acceptable. For future updates, use a new branch per change. Optionally, from current `master`: `git checkout -b dev.nemeyes.ncarousel && git push -u origin dev.nemeyes.ncarousel` and open the MR from that branch instead (same commits, cleaner history).
3. Add a new **file** under `metadata/`: **`metadata/dev.nemeyes.ncarousel.yml`** (see template below).  
   **Cartelle in `metadata/`:** molte app hanno **due** voci con lo stesso “nome” (es. `An.stop.yml` **e** cartella `An.stop/`). Il **file `.yml`** è la ricetta di build (obbligatoria per una nuova app). La **cartella** omonima serve a testi/screenshot extra per il sito F-Droid ([descrizioni e grafica](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)); non la crei tu all’inizio salvo che tu voglia fornirli lì. Per NCarousel basta il `.yml`; le descrizioni possono restare anche nel tuo repo (Fastlane) e i revisori le useranno se appropriate.
4. **Push** your branch to your fork, then open a **Merge Request** against upstream `fdroiddata` with a short description: what the app does, license, source repo, and that it builds with standard Gradle (no proprietary blobs).
5. Watch the MR for **reviewer feedback** and CI (`fdroid build`); adjust the recipe if the build server reports missing SDK, wrong commit, etc.

### Choosing the `Repo:` URL

Either public git URL is valid:

- **GitHub**: `https://github.com/myNemy/NCarousel.git`
- **Forgejo**: `https://forgejo.it/Nemeyes/NCarousel.git`

Pick one as canonical for `Repo:` and keep tags pushed there. This repo pushes tags to both remotes when CI/secrets allow; align `commit:` in the recipe with a tag that exists on the URL you set (e.g. `v0.2.40`).

### Metadata template (`metadata/dev.nemeyes.ncarousel.yml`)

Copy into your **fdroiddata** fork as **`metadata/dev.nemeyes.ncarousel.yml`**. Adjust **`commit`**, **`versionName`**, and **`versionCode`** to match the tag and `app/build.gradle.kts` for each release.

**Comments in the skeleton below:** optional fields stay **`# ...` until you populate them with the maintainer**—**do not uncomment** optional keys with empty or placeholder values. Before submitting to fdroiddata, remove **instructional** comment blocks (template header, “PLEASE REMOVE…”) per upstream convention; for keys you still do not use, **delete** the commented line rather than leaving a bare `# Key:` stub, unless a reviewer asks otherwise. **`MaintainerNotes`** may stay if filled and useful.

Below is the **full Build Metadata Reference skeleton** (no omitted fields): required / known values active; optional lines remain commented for later.

```yaml
# F-Droid metadata template
#
# See https://f-droid.org/docs/ for more details
# and the Metadata reference
# https://f-droid.org/docs/Build_Metadata_Reference/
#
# Fields that are commented out are optional
#
# Single-line fields start right after the colon (with a whitespace).

# These items are the metadata for the app. Please fill as many as possible.
# Categories: pick those that apply (do not list every category below in the real file).
Categories:
  - Connectivity
  - Multimedia
  - Theming
#   - Development
#   - Games
#   - Graphics
#   - Internet
#   - Money
#   - Navigation
#   - Phone & SMS
#   - Reading
#   - Science & Education
#   - Security
#   - Sports & Health
#   - System
#   - Time
#   - Writing
License: AGPL-3.0-or-later
AuthorName: Nemeyes
# AuthorEmail: (text)
# AuthorWebSite: (web link)
WebSite: https://github.com/myNemy/NCarousel
SourceCode: https://github.com/myNemy/NCarousel
IssueTracker: https://github.com/myNemy/NCarousel/issues
Changelog: https://github.com/myNemy/NCarousel/releases
# Donate: (web link)
# Liberapay: (user name)
# Bitcoin: (bitcoin address)

AutoName: NCarousel

RepoType: git
Repo: https://github.com/myNemy/NCarousel.git
# Binaries: (Upstream binary link for reproducible build — usually omitted when F-Droid builds entirely from source)

# At least one for new apps
Builds:
  - versionName: '0.2.40'
    versionCode: 54
    commit: v0.2.40
    # The `subdir` is the parent dir of `src/main` which is generally `app`.
    subdir: app
    # submodules: true
    # output: some.apk
    # prebuild: sed -i -e
    # build: make
    gradle:
      # If flavor is used, the flavor name needs to be specified.
      # If no flavor is used, set `yes` here and `assembleRelease` is used.
      - yes

# For a complete list of possible flags, see the docs

# MaintainerNotes: |-
#     Here go the notes to take into account for future updates, builds, etc.
#     Will be published in the wiki if present.

# The following options are described at this location:
# https://f-droid.org/docs/Build_Metadata_Reference/#UpdateCheckMode
AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+$
CurrentVersion: '0.2.40'
CurrentVersionCode: 54

# PLEASE REMOVE ALL COMMENTS BEFORE SUBMITTING TO F-DROID DATA!
```

**Notes on the recipe**

- **`subdir: app`** points F-Droid at the Android module (parent of `src/main`). If the build server expects the root `settings.gradle.kts` instead, follow reviewer guidance (some recipes drop `subdir` and build from repo root).
- **`gradle: yes`** uses `assembleRelease` for the default variant (no product flavor in this project).
- **`versionName` / `versionCode`** must match the **source** at `commit` (F-Droid reads `versionCode` from the built APK and checks consistency). Keep in sync with `ncarouselBaseVersionName` / `ncarouselLocalVersionCode` in `app/build.gradle.kts` for that tag (F-Droid does not use `GITHUB_RUN_NUMBER`).
- After the first inclusion, the F-Droid team often tunes **`CurrentVersion*`** and update metadata; follow their MR comments.

Fastlane metadata in this repo (`fastlane/metadata/android/en-US/`) can be referenced in MR discussion for **description and screenshots**; final store text may still be edited during review.

## Notes / common pitfalls

- F-Droid does not use `GITHUB_RUN_NUMBER`, so make sure your **local fallback**
  `ncarouselLocalVersionCode` is bumped for each release.
- Avoid proprietary SDKs and binary downloads in Gradle.
- If the build needs secrets or CI-only files, F-Droid builds will fail.
- Do not rely on **`NCAROUSEL_SIGNING_*`** for F-Droid: release builds without those env vars must succeed (unsigned APK).

