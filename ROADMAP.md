# Roadmap

Draft, currently being reworked.

## Pace

Until mid to end of January 2027 there will only be small steps, since a lot is going on
privately. After that, development picks up again, part-time to full-time.

## Released changes

Server management, creating/editing/deleting short URLs, tags, QR codes, statistics,
redirect rules and the share sheet. See the [changelog](CHANGELOG.md) for details.

## 0.3.2

- [ ] Share: show the hostname below the server name, in the server dropdown too
- [ ] Share: copy/share as icons with accessibility labels
- [ ] Share: QR code on the success screen
- [ ] Easter egg in the delete-server dialog
- [x] Complete the required 0.3.2 device checks in the [test log](TESTING.md)

### App start and Shlink list

- [ ] Open the selected server's Shlink list on app launch instead of the server list.
- [ ] Remove the back arrow from the Shlink list and add a native menu with **Servers** and **Settings**.
- [ ] Add a server-wide overview above the search field, between it and the sort hint (e.g. "Newest first").
  Show **Shlinks** (total links), **Clicks** (total, excluding orphan visits), and **Tags** (total tags).
- [ ] Add a toggle in **Settings** to show or hide the overview.
- [ ] Fetch at most 50 Shlinks per server request. When scrolling, prefetch the next page well before
  reaching the end of the loaded results.

## Release channels

| Channel | App ID | Published as |
|---|---|---|
| Dev | `app.shlinker.devs` | Git tag |
| Nightly | `app.shlinker.nightly` | Pre-release (later) |
| Stable | `app.shlinker` | Release from 1.0.0 on |

## Towards 1.0.0

- [ ] App settings: language, light/dark/system, color scheme
- [ ] Per-server defaults for new short URLs
- [ ] Per-server or all shlinks default redirect rules
- [ ] Save/Editable Redirect rule sets
- [x] Shlink Options: Valid since/until, max visits (per shlink, servet, app)
- [ ] Sorting either shared or per server
- [ ] Adaptive layout for tablets and foldables
- [ ] Visual overhaul of the details screen
- [ ] Translations, German first
- [ ] Custom certificates / homelab CA
- [ ] Reproducible release builds
- [ ] CI: emulator tests, faster builds
- [x] Project website at shlinker.app
- [x] Make the repository public

## Distribution

F-Droid is a must. That's why proprietary dependencies, tracking and ads stay out;
CI checks this on every build.

- [ ] Own F-Droid repo for test builds
- [ ] Inclusion in the official F-Droid repo
- [ ] Google Play Store

## Ideas for later

Once there is a stable Android release, Shlinker could move to other platforms
with Compose Multiplatform:

- Linux as AppImage and on the AUR
- Windows at some point

There is no code and no schedule for this yet.
