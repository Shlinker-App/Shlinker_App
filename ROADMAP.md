# Roadmap

Draft, currently being reworked.

## Pace

Until mid to end of January 2027 there will only be small steps, since a lot is going on
privately. After that, development picks up again, part-time to full-time.

## Done (up to 0.3.0)

Server management, creating/editing/deleting short URLs, tags, QR codes, statistics,
redirect rules and the share sheet. See the [changelog](CHANGELOG.md) for details.

## 0.3.1 – dev release

- [x] Clean up the repository, rewrite the docs
- [x] Separate app IDs for dev, nightly and stable, namespace `app.shlinker`
- [x] Own icon per channel
- [x] New signing key for dev builds
- [x] Signed dev APK published to the Forgejo package registry on every tag

## 0.3.2

- [ ] Share: show the hostname below the server name, in the server dropdown too
- [ ] Share: copy/share as icons with accessibility labels
- [ ] Share: QR code on the success screen
- [ ] Easter egg in the delete-server dialog
- [ ] Open items from the [test log](TESTING.md)

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
- [ ] Shlink Options: Valid since/until, max visits (per shlink, servet, app)
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
