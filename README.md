# Shlinker

Ein nativer Android-Client für [Shlink](https://shlink.io/), den selbst gehosteten URL-Shortener.

> **Status:** frühe Entwicklung (0.3.x). Shlinker ist kein offizielles Shlink-Projekt.
> Der Name ist ein Wortspiel mit der österreichischen Endung „-er“.

## Funktionen

- Mehrere Shlink-Server verwalten: hinzufügen, bearbeiten, umbenennen, löschen, sortieren (auch per Drag-and-drop)
- Verbindungstest vor dem Speichern mit verständlichen Fehlermeldungen (DNS, TLS, Timeout, Shlink-Version, API-Key)
- Shlinks durchsuchen, sortieren, öffnen, kopieren und teilen
- Shlinks anlegen, bearbeiten und löschen, mit Domain-Auswahl und Tag-Vorschlägen vom Server
- Details, lokal erzeugte QR-Codes und Besuchsstatistik mit Bot-/Nicht-Bot-Verlauf
- Redirect-Rules anzeigen, anlegen, bearbeiten und sortieren
- Über das Android-Teilen-Menü direkt aus Browser oder anderen Apps einen Shlink erstellen
- Einstellbarer App-Tag (`shlinker`) und Vorgaben für neue Shlinks

Die Oberfläche ist auf Englisch. Weitere Sprachen sind geplant.

## Datenschutz und Sicherheit

- Keine Google Play Services, kein Tracking, keine Werbung, keine Cloud-Backups
- API-Keys sind mit AES-256-GCM verschlüsselt; der Schlüssel liegt nicht exportierbar im Android Keystore
- Server-Profile und Keys sind getrennt gespeichert, Manipulationen werden erkannt
- Verbindungen nur über HTTPS mit öffentlich vertrauenswürdigen Zertifikaten
  (eigene CAs für Homelabs sind geplant)
- QR-Codes entstehen lokal am Gerät, ohne Netzwerkabfrage

## Download

Dev-Builds liegen in der [Package Registry](https://code.awit.at/Shlinker/-/packages/generic/shlinker-devs),
jeweils mit SHA-256-Prüfsumme. Fingerprint des Dev-Signaturschlüssels (SHA-256):

```text
45:3B:B5:52:A5:C6:ED:1E:37:24:E4:1A:90:0F:90:C2:91:F8:52:DB:66:AC:EA:5B:61:6B:D1:4B:26:F3:B9:99
```

## Voraussetzungen

- Android 10 (API 29) oder neuer
- Eine Shlink-Instanz ab Version 3.0 und ein API-Key

## Kanäle

| Kanal | App-ID | Icon | Veröffentlichung |
|---|---|---|---|
| Dev | `app.shlinker.devs` | rot, „DEVS“ | Git-Tag |
| Nightly | `app.shlinker.nightly` | blau, „Nightly“ | Pre-Release (geplant) |
| Stable | `app.shlinker` | grün, Themed Icon | Release ab 1.0.0 |

Alle drei lassen sich parallel installieren. Debug- und Release-Builds eines Kanals teilen sich
die App-ID; wegen unterschiedlicher Schlüssel ist vor dem Wechsel eine Neuinstallation nötig.

## Bauen

Benötigt werden JDK 21 und das Android SDK (Platform 37).

```bash
./gradlew assembleDevsDebug                   # Dev-APK mit Debug-Schlüssel
./gradlew assembleDevsRelease                 # Dev-Release-APK
./gradlew ktlintCheck lintDebug lintDevsDebug # Code-Style und Lint
./gradlew testDebugUnitTest testDevsDebugUnitTest
./gradlew recordRoborazziDebug                # UI-Screenshots ohne Emulator
```

Die APKs landen unter `app/build/outputs/apk/<kanal>/<buildtyp>/`.

### Signieren

Jeder Kanal hat einen eigenen Schlüssel. Lokal liegt er in `signing/<kanal>.properties`
(der Ordner wird nie committet):

```properties
storeFile=/pfad/zu/shlinker-devs.jks
storePassword=...
keyAlias=shlinker-devs
keyPassword=...
```

In der CI kommen die Werte aus `SHLINKER_<KANAL>_STORE_FILE`, `_STORE_PASSWORD`, `_KEY_ALIAS`
und `_KEY_PASSWORD`. Ohne Schlüssel entsteht ein unsigniertes Release-APK.

## Projektstruktur

```
app/               Einstieg, Navigation, Einstellungen
core/model         Domänenmodelle, URL-Normalisierung
core/ui            Theme und gemeinsame Compose-Komponenten
core/network       Shlink REST API v3 (Ktor)
core/data          Repositories
core/datastore     Server-Profile, Einstellungen, verschlüsselte Keys
feature/servers    Serververwaltung und Verbindungstest
feature/shorturls  Shlink-Liste, Details, Bearbeiten, Statistik, Redirect-Rules
build-logic/       Gradle Convention Plugins
```

Tech-Stack: Kotlin, Jetpack Compose mit Material 3, Navigation 3, Hilt, Ktor, kotlinx.serialization,
DataStore, Paging 3, ZXing Core.

Das Logo liegt unter [`assets/logo/`](assets/logo/).

## Weitere Dokumente

- [Roadmap](ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Testprotokoll](TESTPROTOKOLL.md)

## Lizenz

[GPL-3.0](LICENSE)
