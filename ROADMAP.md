# Roadmap

Draft, currently being reworked.

## Pace

Until mid to end of January 2027 there will only be small steps, since a lot is going on
privately. After that, development picks up again, part-time to full-time.

## Released changes

Server management, creating/editing/deleting short URLs, tags, QR codes, statistics,
redirect rules and the share sheet. See the [changelog](CHANGELOG.md) for details.

## 0.3.2

- [x] Share: show the hostname below the server name, in the server dropdown too
- [x] Share: copy/share as icons with accessibility labels
- [x] Share: QR code on the success screen
- [x] Easter egg: dangerous actions (deleting a server, a shlink or a redirect rule) use a red-framed
  dialog with a centered **DANGER ZONE** title
- [x] DEVS builds show a red `DEVS build v<version>` banner at the bottom of every screen
- [x] DEVS builds use the app tag `shlinker-devs` by default
- [x] **About** at the end of the menu: version, links to the source code and issues on Forgejo, license,
  and a warning in DEVS builds
- [ ] Complete the 0.3.2 device checks in the [test log](TESTING.md)

### App start and Shlink list

- [x] Open the selected server's Shlink list on app launch instead of the server list.
- [x] Remove the back arrow from the Shlink list and add a native menu with **Servers** and **Settings**.
  Both a side menu and a top bar menu are available in **Settings** until the final design is chosen.
- [x] Add a server-wide overview above the search field, between it and the sort hint (e.g. "Newest first").
  Show **Shlinks** (total links), **Clicks** (excluding bots and orphan visits), and **Tags** (total tags).
- [x] Add a toggle in **Settings** to show or hide the overview.
- [x] Fetch at most 50 Shlinks per server request. When scrolling, prefetch the next page well before
  reaching the end of the loaded results.

## 0.3.x – refinements and tests

The 0.3 series refines and tests the current feature set.

### Redirect rules

- [ ] When a destination URL is entered without a scheme (e.g. `orf.at`), try HTTPS first and fall back
  to HTTP only if HTTPS is not available.
- [ ] Check whether the destination URL is reachable when it is entered.
- [ ] Test escaping of free-text condition values thoroughly, especially regex-like input (`.*`, `^`, `$`,
  `\`, `[ ]`, `( )`, `|`), URL-sensitive characters (`?`, `&`, `=`, `#`, `%`, `+`, `/`), quotes, spaces and
  non-ASCII text. Values must reach Shlink unchanged and show up unchanged after reloading.

### Statistics

- [ ] Keep seven timeline segments as the default for fast loading. Make the number adjustable in
  **Settings** with manual input and an info text explaining that finer granularity takes longer to load.
- [ ] Check whether the current query method is the fastest option. The Shlink web app loads statistics
  much faster, but its server is nearby; compare under similar network conditions.

## 0.4.0 – Redirect rule sets

A milestone of its own: reusable redirect rule sets, which the Shlink web app does not offer.

- [ ] Manage an unlimited number of named redirect rule sets in **Settings**; their order can be changed.
- [ ] Apply a rule set automatically to new Shlinks when they are created, either per server or for all
  servers. Existing Shlinks are not changed.
- [ ] Save the current rules of a Shlink as a new rule set from its redirect rules screen.
- [ ] Offer to apply a saved rule set when adding redirect rules to a Shlink.

## Release channels

| Channel | App ID | Published as |
|---|---|---|
| Dev | `app.shlinker.devs` | Git tag |
| Nightly | `app.shlinker.nightly` | Pre-release (later) |
| Stable | `app.shlinker` | Release from 1.0.0 on |

## Towards 1.0.0

- [ ] App settings: language, light/dark/system, color scheme
- [ ] Per-server defaults for new short URLs
- [x] Shlink Options: Valid since/until, max visits (per shlink, servet, app)
- [ ] Sorting either shared or per server
- [ ] Adaptive layout for tablets and foldables
- [ ] Visual overhaul of the details screen
- [ ] Nightly design polish: choose the final menu style (side menu or top bar menu)
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
