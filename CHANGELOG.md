# Changelog

Every version before 1.0.0 is a development snapshot.

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
