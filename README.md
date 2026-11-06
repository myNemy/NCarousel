<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->

# NCarousel

Nextcloud wallpaper carousel (WebDAV).

Android app that downloads images from a folder on your **Nextcloud** server and sets them as the device **wallpaper**.

## Download

- **[F-Droid](https://f-droid.org/packages/dev.nemeyes.ncarousel/)** (recommended)
- **[GitHub Releases](https://github.com/myNemy/NCarousel/releases)**

## Features

- Sign in to your own Nextcloud (browser login or username / app password); multiple accounts
- Choose a remote folder and sync the image list
- Automatic wallpaper rotation (minimum interval 1 minute; WorkManager may delay in Doze)
- Order modes: sequential, random, shuffle once, smart random, no-repeat
- Manual “next” from the app and an optional Quick Settings tile
- Apply to home, lock screen, or both
- Image fit: fill (crop), fit with soft blurred edges, or center
- Optional Wi‑Fi-only mode for scheduled downloads (manual apply still works on any network)
- Library: search, sort (name, date, folders, carousel index; tap again to invert), folder filter, grid or list, pin current wallpaper
- Fullscreen preview (full image by default, pinch-to-zoom; optional Adjust frame for Cover/Center, offset remembered per image); apply only or apply and advance; share image via Android share sheet; exclude images from the automatic carousel
- Manage exclusions (Library filters: in carousel / excluded; export/import; optional save/load under `.ncarousel` on Nextcloud)
- Optional on-device image cache; EXIF / place info for the last wallpaper applied by the app
- Follows the system language when a translation is available (default English, plus several European languages)

## Privacy

- No trackers
- Credentials are stored encrypted on-device (AndroidX Security)
- Network connections only go to the Nextcloud server you configure

## License

Licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later). See `LICENSE`.

## Source

- **GitHub**: [github.com/myNemy/NCarousel](https://github.com/myNemy/NCarousel)
- **Forgejo** (mirror): [forgejo.it/Nemeyes/NCarousel](https://forgejo.it/Nemeyes/NCarousel)
- Issues: [github.com/myNemy/NCarousel/issues](https://github.com/myNemy/NCarousel/issues)
