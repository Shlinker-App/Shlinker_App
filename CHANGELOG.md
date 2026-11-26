# Changelog

Every version before 1.0.0 is a development snapshot.

## Unreleased

- CI no longer posts the end of a failed run's log as an issue comment; issues are public and mirrored to
  GitHub. The full log stays in Forgejo Actions and the reports remain available as an artifact.

## 0.3.2 – 2026-11-25

### App

- The app opens the Shlink list of the active server instead of the server list.
- The back arrow in the Shlink list is replaced by a menu with **Servers**, **Settings** and **About**.
  A side menu and a top bar menu can be compared in Settings.
- The server list is now called **Servers** and has a back arrow. Tapping a server makes it active and returns
  to its Shlinks. The “Show shlinks” button and the settings shortcut were removed; both are in the menu now.
- Without a server, the Shlink list offers an “Add server” button.
- Optional server overview between the sort hint and the search field: total Shlinks, clicks without bots and
  orphan visits, and tags. It reloads with the list and can be turned off in Settings.
- The Shlink list loads 50 Shlinks per request instead of 30 and fetches the next page well before the end
  of the list, but only once scrolling starts.
- Deleting a server, a Shlink or a redirect rule asks for confirmation in a red-framed dialog with a centered
  DANGER ZONE title. Deleting a server previously used a plain dialog.
- New About page with the version, links to the source code and issues on Forgejo, and the license.

### Sharing

- The server hostname appears below the server name and in the server selection.
- Copy and share are icon buttons with accessibility labels.
- The success screen shows a QR code, generated on the device.
- Fixed: text and icons on the share screen were dark on a dark background and hard to read in dark mode.
  After creating a Shlink, the server name and hostname were shown faded; they are now regular text.

### Settings

- New **Shlink list** section with the server overview switch and the menu style.
- The back button is labeled “Back”, since Settings is no longer opened from the server overview.

### DEVS builds

- Red `DEVS build v<version>` banner at the bottom of every screen, including the share screen.
- New installs use the app tag `shlinker-devs` by default; existing settings are kept.
- The About page shows a warning that this is a DEVS build.

### Project and CI

- Version 0.3.2 (versionCode 26).
- Code comments, test names, diagnostics, and build and CI messages are now in English.
  CONTRIBUTING.md describes the writing style and the UI conventions (Danger Zone, DEVS banner,
  channel-specific values).
- Issue forms for bug reports, feature requests and feedback on Forgejo and GitHub, with default labels.
  A GitHub workflow sets the issue type from the label.
- Issues are synchronized between Forgejo and GitHub; see CONTRIBUTING.md and
  `docs/maintainers/repo-workflow.md`.
- CI no longer runs on every push; it is started manually and still checks pull requests. It caches
  Gradle dependencies, the JDK and the Android SDK, also after failed runs. CI and releases load Maven
  Central artifacts from Google's mirror to avoid rate limits. Releases run
  ktlint, Android lint and the unit tests themselves before publishing. The release workflow no longer
  overwrites a version that is already published in the package registry.
- Each dev build is also published as a Forgejo pre-release with its changelog section, the APK and its
  SHA-256 file. A tag without a matching changelog section fails before building.
- README rewritten with the new logo and badges; the local badge files were removed.
- Build toolchain updated: Gradle 9.9.2, Android Gradle Plugin 9.5.3 and KSP 2.3.14, which removes all
  deprecation warnings for Gradle 11. Also updated: Ktor 3.6.2, Lifecycle 2.11.1, Navigation3 1.2.2,
  DataStore 1.3.0, kotlinx-serialization 1.13.0, Roborazzi 1.79.0 and ZXing 3.5.4.
- Lint cleanups: unused strings removed, window height read from `LocalWindowInfo`, `toUri()` and
  `mutableIntStateOf` used where lint suggested them, and the QR code composable follows the modifier
  convention.

## 0.3.1 – 2026-11-17

- Separate app IDs for dev (`app.shlinker.devs`), nightly (`app.shlinker.nightly`),
  and stable (`app.shlinker`), with `app.shlinker` as the shared Android namespace.
  Existing installs (`at.awit.shlinker.debug`) won't be updated.
  The new app has to be installed fresh and servers have to be added again.
- Debug builds no longer add a suffix to the app ID or version name. Debug and release
  builds within the same channel share an app ID but use different signing keys,
  so switching between them requires reinstalling.
- Separate launcher icons for each channel: stable uses the new official logo and
  supports themed icons; dev and nightly keep their channel colors.
- Separate release signing configuration per channel and a new signing key for dev builds.
- On version tags, CI verifies the dev signing certificate and publishes the signed APK
  and its SHA-256 checksum to the Forgejo package registry.
- Repository cleanup and rewritten English documentation, including the README, roadmap,
  changelog, and test log. The README shows the new logo and project badges.

## 0.3.0 – 2026-10-31

- Create short URLs straight from the Android share sheet, with server selection and the page title filled in

## 0.2.15

- Domain selection when creating (only domains visible to the API key, manual entry possible)
- Clearer status display for redirect rules

## 0.2.14

- Full redirect rule management: create, edit, delete, reorder

## 0.2.13

- Statistics timeline with seven segments, much faster; defaults to "Last 4 weeks"

## 0.2.12

- Chronological visit timeline with stacked bot/human bars

## 0.2.11

- Statistics header no longer jumps while loading

## 0.2.10

- Visit statistics with time ranges and bot/human counts

## 0.2.9

- QR codes from the list and details, generated locally
- Action group on the details screen

## 0.2.8

- Delete short URLs, with confirmation
- Suggestions for existing tags from the server

## 0.2.7

- Date and time in "Enabled since/until" can be tapped directly

## 0.2.6

- Date and time pickers, discard dialog only when something actually changed

## 0.2.5

- Edit short URLs
- Shared defaults for new short URLs

## 0.2.4

- Visit counts in the list, details screen per short URL

## 0.2.3

- Open the short URL in the browser, separate share and copy actions

## 0.2.2

- Tags as chips, configurable app tag, design tweaks

## 0.2.1

- Create short URLs
- Edit server address and API key after the fact

## 0.2.0

- Sort short URLs by date and visits, persisted

## 0.1.6

- "Paste" button in the API key field

## 0.1.5

- Reorder servers, manually via drag and drop

## 0.1.4

- English UI throughout
- Rename and delete servers

## 0.1.3

- Unique profile names per server address

## 0.1.2

- Duplicate server profiles are prevented

## 0.1.0

- First test build: add a server, short URL list with search and copy
