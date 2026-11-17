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

## Voraussetzungen

- Android 10 (API 29) oder neuer
- Eine Shlink-Instanz ab Version 3.0 und ein API-Key

## Bauen

Benötigt werden JDK 21 und das Android SDK (Platform 37).

```bash
./gradlew assembleDebug              # Debug-APK
./gradlew ktlintCheck lintDebug      # Code-Style und Lint
./gradlew testDebugUnitTest          # Unit-Tests
./gradlew recordRoborazziDebug       # UI-Screenshots ohne Emulator
```

Die APK landet unter `app/build/outputs/apk/debug/`.

### Release signieren

Für signierte Builds eine `key.properties` im Projektordner anlegen. Sie wird nie committet.

```properties
storeFile=/pfad/zu/shlinker.jks
storePassword=...
keyAlias=shlinker
keyPassword=...
```

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

## Weitere Dokumente

- [Roadmap](ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Testprotokoll](TESTPROTOKOLL.md)

## Lizenz

[GPL-3.0](LICENSE)
