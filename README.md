<div align="center">

<img src="assets/logo/shlinker.svg" alt="Shlinker logo" width="128" height="128">

# Shlinker

A native Android client for [Shlink](https://shlink.io/), the self-hosted URL shortener.

[![CI](https://code.awit.at/Shlinker/Shlinker_App/badges/workflows/ci.yml/badge.svg?branch=main)](https://code.awit.at/Shlinker/Shlinker_App/actions)
[![Version](https://badge.awit.at/badge/dev-0.3.1-b3261e)](https://code.awit.at/Shlinker/-/packages/generic/shlinker-devs)
[![License: GPL-3.0](https://badge.awit.at/badge/license-GPL--3.0-007ec6)](LICENSE)
<br>
[![Android 10+](https://badge.awit.at/badge/Android-10%2B%20%28API%2029%29-3ddc84?logo=android&logoColor=white)](https://developer.android.com/about/versions/10)
[![Kotlin](https://badge.awit.at/badge/Kotlin-2.4-7f52ff?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://badge.awit.at/badge/Jetpack%20Compose-Material%203-4285f4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Shlink](https://badge.awit.at/badge/Shlink-3.0%2B-4696e5)](https://shlink.io/)
<br>
[![No Google Play Services](https://badge.awit.at/badge/Google%20Play%20Services-none-1c624b)](#privacy-and-security)
[![No tracking](https://badge.awit.at/badge/tracking-none-1c624b)](#privacy-and-security)
[![Forgejo](https://badge.awit.at/badge/hosted%20on-Forgejo-fb923c?logo=forgejo&logoColor=white)](https://code.awit.at/Shlinker/Shlinker_App)

</div>

> **Status:** early development (0.3.x). Shlinker is not an official Shlink project.
> The name is a pun on the Austrian "-er" suffix.

## Features

- Manage multiple Shlink servers: add, edit, rename, delete and reorder them (drag and drop works too)
- Connection test before saving, with readable errors (DNS, TLS, timeout, Shlink version, API key)
- Search, sort, open, copy and share short URLs
- Create, edit and delete short URLs, with domain selection and tag suggestions from the server
- Details, locally generated QR codes and visit statistics split into bots and humans
- View, create, edit and reorder redirect rules
- Create a short URL straight from the Android share sheet in your browser or any other app
- Configurable app tag (`shlinker`) and defaults for new short URLs

The UI is in English. More languages are planned.

## Privacy and security

- No Google Play Services, no tracking, no ads, no cloud backups
- API keys are encrypted with AES-256-GCM; the key lives non-exportable in the Android Keystore
- Server profiles and keys are stored separately, and tampering is detected
- HTTPS only, with publicly trusted certificates
  (custom CAs for homelabs are planned)
- QR codes are generated on the device, without any network request

## Download

Dev builds are published in the [package registry](https://code.awit.at/Shlinker/-/packages/generic/shlinker-devs),
each with a SHA-256 checksum. SHA-256 fingerprint of the dev signing key:

```text
45:3B:B5:52:A5:C6:ED:1E:37:24:E4:1A:90:0F:90:C2:91:F8:52:DB:66:AC:EA:5B:61:6B:D1:4B:26:F3:B9:99
```

## Requirements

- Android 10 (API 29) or newer
- A Shlink instance, version 3.0 or later, and an API key

## Channels

| Channel | App ID | Icon | Published as |
|---|---|---|---|
| Dev | `app.shlinker.devs` | red, "DEVS", Non-themed icon  | Git tag |
| Nightly | `app.shlinker.nightly` | blue, "Nightly", Non-themed icon | Pre-release (planned) |
| Stable | `app.shlinker` | green, themed icon | Release from 1.0.0 on |

All three can be installed side by side. Debug and release builds of a channel share the same
app ID, but they are signed with different keys, so switching between them needs a reinstall.

## Building

You need JDK 21 and the Android SDK (platform 37).

```bash
./gradlew assembleDevsDebug                   # dev APK, debug key
./gradlew assembleDevsRelease                 # dev release APK
./gradlew ktlintCheck lintDebug lintDevsDebug # code style and lint
./gradlew testDebugUnitTest testDevsDebugUnitTest
./gradlew recordRoborazziDebug                # UI screenshots without an emulator
```

APKs end up in `app/build/outputs/apk/<channel>/<buildtype>/`.

### Signing

Every channel has its own key. Locally it is configured in `signing/<channel>.properties`
(that folder is never committed):

```properties
storeFile=/path/to/shlinker-devs.jks
storePassword=...
keyAlias=shlinker-devs
keyPassword=...
```

In CI the values come from `SHLINKER_<CHANNEL>_STORE_FILE`, `_STORE_PASSWORD`, `_KEY_ALIAS`
and `_KEY_PASSWORD`. Without a key you get an unsigned release APK.

## Project layout

```
app/               entry point, navigation, settings
core/model         domain models, URL normalization
core/ui            theme and shared Compose components
core/network       Shlink REST API v3 (Ktor)
core/data          repositories
core/datastore     server profiles, settings, encrypted keys
feature/servers    server management and connection test
feature/shorturls  short URL list, details, editing, statistics, redirect rules
build-logic/       Gradle convention plugins
```

Tech stack: Kotlin, Jetpack Compose with Material 3, Navigation 3, Hilt, Ktor, kotlinx.serialization,
DataStore, Paging 3, ZXing Core.

The logo lives in [`assets/logo/`](assets/logo/).

## More

- [Roadmap](ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Testing](TESTING.md)

## License

[GPL-3.0](LICENSE)
