# Test log

Manual tests on real Android devices against a self-hosted Shlink instance, using the published dev build
while it is the current release. Problems found there are fixed in the next version.
Automated tests (unit tests, lint, ktlint, screenshots) run in CI and before every release.

## Results

| Version | versionCode | Tested | Result |
|---|---|---|---|
| 0.1.0 | 1 | List, search, copy | ok; duplicate server profiles possible → fixed in 0.1.2 |
| 0.1.2 | 3 | Naming, duplicate check | ok |
| 0.1.3 | 4 | Name validation, update keeps data | ok |
| 0.1.4 | 5 | Rename, delete, English UI | ok |
| 0.1.5 | 6 | Server order, persists after app restart | ok |
| 0.1.6 | 7 | "Paste", keyboard focus between fields | ok |
| 0.2.0 | 8 | Short URL sorting | ok |
| 0.2.1 | 9 | Edit server, create short URLs with tags | ok |
| 0.2.2 | 10 | Tag chips, app tag, design | ok |
| 0.2.3 | 11 | Open in browser, share/copy | ok |
| 0.2.4 | 12 | Visit counts, details screen | ok; details screen needs a visual pass later |
| 0.2.5 | 13 | Editing | ok; date input and cancel improved in 0.2.6 |
| 0.2.6 | 14 | Date/time pickers, discard dialog | ok |
| 0.2.7 | 15 | Direct date/time input | ok |
| 0.2.8 | 16 | Delete, tag suggestions | ok |
| 0.2.9 | 17 | QR codes, detail actions | ok |
| 0.2.10 | 18 | Statistics with real data | ok |
| 0.2.11 | 19 | Stable statistics header | ok |
| 0.2.12 | 20 | Timeline | ok; loading too slow → 0.2.13 |
| 0.2.13 | 21 | Seven segments | ok, much faster |
| 0.2.14 | 22 | Redirect rules | ok |
| 0.2.15 | 23 | Domain selection, rule status | ok |
| 0.3.0 | 24 | Share sheet, page title taken over | ok |

## 0.3.2 dev release

- [x] Restart and persistence: profiles, active server, settings, sorting
- [x] Multiple profiles and domains, same short code on different domains
- [x] Offline and retry: a visible error appears instead of a false success, and retry works once the connection returns
- [x] Check defaults for new short URLs against server/admin settings, also when sharing
- [x] Share with one or multiple servers: switching servers preserves input and the link uses the correct domain
- [x] Share: rotation, sharing again while the app is open, no duplicate after a cancelled request

New in 0.3.2, still to be checked on a device:

- [x] Share: the hostname is visible below the server name and in the server dropdown
- [x] Share success screen: copy and share icons work, and the QR code resolves to the correct short URL
- [x] Share screen in dark mode: all text, icons and hints are readable
- [x] App start opens the active server's Shlink list; the first server setup also leads there
- [x] Side menu and top bar menu both open **Servers** and **Settings**; the style can be switched in Settings
- [x] Servers: tapping a server selects it and returns to its Shlink list; the back arrow changes nothing
- [x] Overview matches the server (shlinks, clicks without bots, tags), updates on refresh and can be hidden
- [x] A server with more than 50 shlinks loads further pages while scrolling, without visible waiting
- [x] Deleting a server, a shlink or a redirect rule shows the red frame and the centered **DANGER ZONE** title;
  Cancel changes nothing
- [x] Fresh DEVS install: the app tag in Settings defaults to `shlinker-devs`
- [x] About: opens from both menu styles; the Forgejo, issue and license links open in the browser;
  the DEVS warning is visible
- [x] DEVS build: the red `DEVS build v<version>` banner is centered at the bottom of every screen,
  including the share screen, and covers no content or buttons, also with the keyboard open

Optional: edge cases for domain selection, tags, delete, QR codes and statistics
(custom domains, rotation, dark mode, errors/retry).

## Upcoming (0.3.x)

- [ ] Redirect rules: escaping of free-text condition values (query parameter keys and values, language,
  IP address and similar). Save rules with regex-like input such as `^a.*b$`, `\d+`, `[a-z]`, `(x|y)`,
  URL-sensitive characters such as `?`, `&`, `=`, `#`, `%20`, `+`, `/`, plus quotes, spaces and umlauts.
  Check that each value is stored exactly as entered, appears unchanged after reloading in the app and in
  the Shlink web app, and that the redirect behaves as expected.

## Pre-release

- [ ] Verify the TalkBack labels for the copy and share icons once the final text is ready.