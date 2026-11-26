# Roadmap

Draft – currently being reworked.

## Pace

Until mid-to-late January 2027, development will progress slowly due to personal commitments.
After that, development will resume on a part-time to full-time basis.

## Released changes

Server management, creating/editing/deleting short URLs, tags, QR codes, statistics,
redirect rules, and the share sheet. See the [changelog](CHANGELOG.md) for details.

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

- [x] Open the selected server's Shlink list on app launch instead of showing the server list.
- [x] Remove the back arrow from the Shlink list and add a native menu with **Servers** and **Settings**.
  Both a side menu and a top bar menu are available in **Settings** until the final design is chosen.
- [x] Add a server-wide overview above the search field, positioned between it and the sort hint (e.g. "Newest first").
  It displays **Shlinks** (total links), **Clicks** (excluding bots and orphan visits), and **Tags** (total tags).
- [x] Add a toggle in **Settings** to show or hide the overview.
- [x] Fetch at most 50 Shlinks per server request and prefetch the next page when scrolling near the end of the loaded results.

## 0.3.x refinements and tests

The 0.3 series refines and tests the current feature set.
- [ ] In-app update checks, toggleable in Settings, with per-build-tree checks and info about disabling them if installed via an app store (F-Droid, Obtainium, Accrescent)

### Redirect rules

- [ ] When a destination URL is entered without a scheme (e.g. `orf.at`), try HTTPS first and fall back to HTTP only if HTTPS is unavailable.
- [ ] Verify that the destination URL is reachable when it is entered.
- [ ] Thoroughly test escaping of free-text condition values, especially regex-like patterns (`.*`, `^`, `$`,
  `\`, `[ ]`, `( )`, `|`), URL-sensitive characters (`?`, `&`, `=`, `#`, `%`, `+`, `/`), quotes, spaces, and
  non-ASCII text. Values must reach Shlink unchanged and appear unchanged after reloading.

### Statistics

- [ ] Use seven timeline segments by default for fast loading and allow adjusting this in **Settings** via manual input,
  with explanatory text noting that finer granularity increases load time.
- [ ] Verify whether the current query method is optimal. The Shlink web app loads statistics considerably faster,
  but its server is located nearby; compare performance under similar network conditions.

### Build and dependencies

- [ ] Update the Compose BOM to 2026.11 (Compose 1.13), which requires compileSdk 37.1 (SDK platform in
  `ci/setup-android.sh`) and replacing the deprecated `ListItem` overload and `VisualTransformation`.
- [ ] Update Robolectric to 4.18, which requires new Android framework JARs. Verify that Google's Maven Central
  mirror already hosts them to prevent CI from hitting Maven Central rate limits.
- [ ] Remove the `ObsoleteLintCustomCheck` suppression from `lint.xml` once Lifecycle 2.12 becomes stable.

## 0.4.0 Redirect rule sets

A milestone of its own: reusable redirect rule sets, which the Shlink web app does not offer.

- [ ] Manage an unlimited number of named redirect rule sets in **Settings** with customizable ordering.
- [ ] Automatically apply a rule set to new Shlinks upon creation, either per server or globally. Existing Shlinks remain unchanged.
- [ ] Save the current rules of a Shlink as a new rule set from its redirect rules screen.
- [ ] Offer the option to apply a saved rule set when adding redirect rules to a Shlink.

## Release channels

| Channel | App ID | Published as |
|---|---|---|
| Dev | `app.shlinker.devs` | Git tag |
| Nightly | `app.shlinker.nightly` | Pre-release (later) |
| Stable | `app.shlinker` | Release from 1.0.0 on |

## Towards 1.0.0

### Major Priority: UX Redesign

- [ ] **Complete redesign with Material Design** – Modernize all screens, components, and interactions with two tiers:
  - **Android 16+ (API 35+):** Full Material 3 Expressive implementation with dynamic springy animations, responsive motion physics, depth effects, background blur, modern typography, updated color themes, and emotionally engaging interactions
  - **Android 10–15 (API 29–34):** Material 3 / Material You baseline ensuring consistent, modern UX across all supported versions while omitting Expressive-specific features unavailable on older platforms

### Features & Refinements

- [ ] App settings: language, light/dark/system, color scheme
- [ ] Per-server defaults for new short URLs
- [x] Shlink Options: Valid since/until, max visits (per shlink, servet, app)
- [ ] Sorting either shared or per server
- [ ] Adaptive layout for tablets and foldables
- [ ] Visual overhaul of the details screen
- [ ] Nightly design polish: choose the final menu style (side menu or top bar menu)
- [ ] Translations, German first (via Weblate, self-hosted)
- [ ] Bug Report Feature -> SemiAuto creating Forgejo Issue incl. Logs
- [ ] Custom certificates / homelab CA
- [ ] Reproducible release builds
- [ ] CI: emulator tests, faster builds
- [x] Project website at shlinker.app
- [x] Make the repository public

## Distribution

F-Droid is essential, which is why the app remains free of proprietary dependencies, tracking, and advertisements.
CI verifies this on every build.

- [ ] Own F-Droid repo for test builds
- [ ] Inclusion in the official F-Droid repo
- [ ] After 4 months from release → add to official Obtainium; before 4 months → provide a custom Obtainium-to-Forgejo button (4 months is the Obtainium cooldown period)
- [ ] Accrescent (Rules?)
- [ ] Google Play Store

## Ideas for later

Once a stable Android release is available, Shlinker could expand to other platforms using Compose Multiplatform:

- Linux as AppImage and on the AUR
- Windows at some point

No code or timeline for this expansion currently exists.
