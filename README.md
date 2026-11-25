<div align="center">

<img src="assets/logo/shlinker.svg" alt="Shlinker logo" width="128" height="128">

# Shlinker

Your Shlink servers. Your links. Your Android companion.

A native Android client for [Shlink](https://shlink.io/), the self-hosted URL shortener.
Manage links, fine-tune redirects and check visit statistics — without handing your workflow to another cloud service.

[![CI](https://code.awit.at/Shlinker/Shlinker_App/badges/workflows/ci.yml/badge.svg?branch=main)](https://code.awit.at/Shlinker/Shlinker_App/actions)
[![Version](https://badge.awit.at/badge/dev-0.3.2-b3261e)](https://code.awit.at/Shlinker/-/packages/generic/shlinker-devs)
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

[Download dev builds](https://code.awit.at/Shlinker/-/packages/generic/shlinker-devs) · [Source & development](https://code.awit.at/Shlinker/Shlinker_App) · [Roadmap](ROADMAP.md)

</div>

> Early development: Shlinker is currently in the 0.3.x series. Expect changes and rough edges.
> Shlinker is an independent project, not an official Shlink app.

## 🧭 Development lives on Forgejo

The canonical repository is [Shlinker/Shlinker_App on code.awit.at](https://code.awit.at/Shlinker/Shlinker_App).
[GitHub](https://github.com/Shlinker-App/Shlinker_App) is an automatically updated mirror of the code for discovery and convenient access.

Please submit code contributions and development discussions on Forgejo rather than opening GitHub pull requests.

### Issues on Forgejo and GitHub

Bug reports and feature requests are welcome on the [Forgejo issue tracker](https://code.awit.at/Shlinker/Shlinker_App/issues), which is the preferred place. If you do not want to create an account there, you can also open an issue on [GitHub](https://github.com/Shlinker-App/Shlinker_App/issues).

Issues and comments are synchronized between both platforms using a lightly extended version of [forgesync](https://github.com/eleboucher/forgesync). Forgejo is the source of truth: the open or closed state of an issue always follows Forgejo, and issue numbers can differ between the platforms. Details are in [CONTRIBUTING.md](CONTRIBUTING.md#issue-synchronization).

Like the project? A star on GitHub or Forgejo, a shared link, or a useful bug report helps other self-hosters discover it.


## ✨ Features

### Multiple servers, one app

- Manage multiple Shlink servers: add, edit, rename, delete and reorder them, including drag and drop.
- Test connections before saving, with readable errors for DNS, TLS, timeouts, Shlink versions and API keys.
- Set per-server defaults for new short URLs instead of repeating the same choices every time.
- Configure the app tag (`shlinker`) to suit your workflow.

### Your links, within reach

- Search, sort, open, copy and share short URLs.
- Create, edit and delete short URLs, with domain selection and tag suggestions from your server.
- Create short URLs directly from the Android share sheet — from your browser or another app.
- Open link details, generate QR codes locally and explore visit statistics split into bots and humans.

### Redirects and reusable defaults

- View, create, edit and reorder redirect rules.
- Save and edit reusable redirect rule sets.
- Configure default redirect rules per server or across all your Shlink servers.
- Configure Shlink options such as valid-since and valid-until dates and maximum visits, with defaults at app and server level and individual settings per short URL.

### Make it yours

- Choose your app language, light, dark or system appearance, and color scheme.
- Export and import app settings in encrypted form.

## 🔒 Privacy and security

- No Google Play Services, no tracking, no ads and no cloud backups.
- API keys are encrypted with AES-256-GCM; the encryption key is non-exportable and held in the Android Keystore.
- Server profiles and keys are stored separately, with tampering detection.
- HTTPS only, with publicly trusted certificates. Support for custom CAs in homelabs is planned.
- QR codes are generated on the device, without a network request.
- Encrypted settings import and export provide a way to move your configuration without a cloud backup service.

## 📦 Download

Dev builds are available in the [Forgejo package registry](https://code.awit.at/Shlinker/-/packages/generic/shlinker-devs), each with a SHA-256 checksum. From 0.3.2 on, each dev build also appears as a [pre-release](https://code.awit.at/Shlinker/Shlinker_App/releases) with its changelog.

These are development builds, not stable releases. Feedback and reproducible bug reports are welcome.

SHA-256 fingerprint of the dev signing key:

```text
45:3B:B5:52:A5:C6:ED:1E:37:24:E4:1A:90:0F:90:C2:91:F8:52:DB:66:AC:EA:5B:61:6B:D1:4B:26:F3:B9:99
```

### Requirements

- Android 10 (API 29) or newer.
- A Shlink instance running version 3.0 or later, and an API key.

### Release channels

| Channel | App ID                 | Icon                        | Distribution                                  |
| ------- | ---------------------- | --------------------------- | --------------------------------------------- |
| Dev     | `app.shlinker.devs`    | Red, “DEVS”, non-themed     | Git-tagged dev builds in the package registry |
| Nightly | `app.shlinker.nightly` | Blue, “Nightly”, non-themed | Pre-releases, planned                         |
| Stable  | `app.shlinker`         | Green, themed               | Releases starting with 1.0.0                  |

The channels use separate app IDs and can be installed side by side. Dev builds show a red “DEVS build” banner at the bottom of every screen. Debug and release builds within a channel share an app ID but use different signing keys, so switching between them requires a reinstall.

## 🗓️ Roadmap and development pace

See the [roadmap](ROADMAP.md) for the direction of the project and the [changelog](CHANGELOG.md) for what has already changed.

Until mid-to-late January 2027, development will move in small steps because of private commitments. After that, the pace is expected to pick up again, with time ranging from part-time to full-time as circumstances allow.

This is an independent project: the roadmap describes intentions, not guaranteed delivery dates. Testing, thoughtful feedback and contributions are welcome along the way.

## 🛠️ Build from source

You need JDK 21 and the Android SDK, including platform 37.

```bash
./gradlew assembleDevsDebug                   # Dev APK, debug key
./gradlew assembleDevsRelease                 # Dev release APK
./gradlew ktlintCheck lintDebug lintDevsDebug  # Code style and lint
./gradlew testDebugUnitTest testDevsDebugUnitTest
./gradlew recordRoborazziDebug                # UI screenshots without an emulator
```

APKs are written to `app/build/outputs/apk/<channel>/<buildtype>/`.

### Signing

Each channel has its own signing key. Local signing is configured in `signing/<channel>.properties`; that directory is not committed:

```properties
storeFile=/path/to/shlinker-devs.jks
storePassword=...
keyAlias=shlinker-devs
keyPassword=...
```

In CI, signing values come from `SHLINKER_<CHANNEL>_STORE_FILE`, `_STORE_PASSWORD`, `_KEY_ALIAS` and `_KEY_PASSWORD`. Without a configured key, the release APK is unsigned.

### Project layout

```text
app/               Entry point, navigation, settings
core/model         Domain models, URL normalization
core/ui            Theme and shared Compose components
core/network       Shlink REST API v3 (Ktor)
core/data          Repositories
core/datastore     Server profiles, settings, encrypted keys
feature/servers    Server management and connection tests
feature/shorturls  Short URLs, details, editing, statistics, redirect rules
build-logic/       Gradle convention plugins
```

Built with Kotlin, Jetpack Compose with Material 3, Navigation 3, Hilt, Ktor, kotlinx.serialization, DataStore, Paging 3 and ZXing Core.

The logo is in [`assets/logo/`](assets/logo/). For testing notes, see [TESTING.md](TESTING.md).

## 💬 Why “Shlinker”?

A little German-language wordplay, with an Austrian wink.

In German — including Austrian German — the ending *-er* often appears in words for people who do a particular job. Many occupational terms also became family names: *Tischler* means joiner or carpenter, and *Müller* means miller.

Shlinker borrows that idea: Shlink + *-er* — your little helper that works with Shlink.

Not every German surname ending in *-er* comes from an occupation, and the ending is not uniquely Austrian. The name is a playful nod, not a linguistic rule.

## 📜 License

Shlinker is free and open-source software, licensed under [GPL-3.0](LICENSE).
