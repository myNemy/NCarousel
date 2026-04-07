<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->

## NCarousel

Android app that downloads images from your **Nextcloud** server (WebDAV) and sets them as the device **wallpaper**, using official Android APIs.

## License

Licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later). See `LICENSE`.

## Non-FOSS parts

The NCarousel app source code is FOSS (AGPL). However, depending on how you build/run it, you may interact with **non-FOSS** components:

- **Android SDK / Build Tools**: usually downloaded from Google and distributed under non-FOSS terms.
- **Hosting / CI**: GitHub / Forgejo instances and CI runners are external services; even if the app is FOSS, the service you use may not be.
- **Device / OS vendor components**: many Android devices ship with proprietary firmware and system apps; NCarousel uses Android platform APIs but cannot make the whole device stack FOSS.

## More information

Project page and source code:

- **Forgejo**: [forgejo.it/Nemeyes/NCarousel](https://forgejo.it/Nemeyes/NCarousel)
- **GitHub** (mirror): [github.com/myNemy/NCarousel](https://github.com/myNemy/NCarousel)

---

*If you build or install the app yourself (not via an app store), see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).*
