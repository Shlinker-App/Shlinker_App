# Roadmap

Entwurf, wird gerade überarbeitet.

## Erledigt (bis 0.3.0)

Serververwaltung, Shlinks anlegen/bearbeiten/löschen, Tags, QR-Codes, Statistik,
Redirect-Rules und das Teilen-Menü. Details im [Changelog](CHANGELOG.md).

## 0.3.1 – nächste Dev-Version

- [x] Repository aufräumen, Dokumentation neu schreiben
- [x] Getrennte App-IDs für Dev, Nightly und Stable, Namespace `app.shlinker`
- [x] Eigene Icons je Kanal
- [ ] Neuer Signaturschlüssel für Dev-Builds
- [ ] Teilen: Hostname unter dem Servernamen, auch im Server-Dropdown
- [ ] Teilen: Copy/Share als Symbole mit Accessibility-Beschriftung
- [ ] Teilen: QR-Code auf der Erfolgsseite
- [ ] Easter Egg im Dialog zum Löschen eines Servers
- [ ] Offene Tests aus dem [Testprotokoll](TESTPROTOKOLL.md)

## Release-Kanäle

| Kanal | App-ID | Veröffentlichung |
|---|---|---|
| Dev | `app.shlinker.devs` | Git-Tag |
| Nightly | `app.shlinker.nightly` | Pre-Release (später) |
| Stable | `app.shlinker` | Release ab 1.0.0 |

## Bis 1.0.0

- [ ] App-Einstellungen: Sprache, Hell/Dunkel/System, Farbschema
- [ ] Server-spezifische Vorgaben für neue Shlinks
- [ ] Sortierung wahlweise gemeinsam oder pro Server
- [ ] Adaptives Layout für Tablets und Foldables
- [ ] Detailseite optisch überarbeiten
- [ ] Übersetzungen, zuerst Deutsch
- [ ] Eigene Zertifikate / Homelab-CA
- [ ] Reproduzierbare Release-Builds
- [ ] CI: Emulator-Tests, schnellere Builds, Signaturschlüssel als Secret
- [ ] Projektseite unter shlinker.app
- [ ] Repository öffentlich

## Verteilung

F-Droid ist Pflicht. Deshalb bleiben proprietäre Abhängigkeiten, Tracking und Werbung draußen;
die CI prüft das bei jedem Build.

- [ ] Eigenes F-Droid-Repo für Testbuilds
- [ ] Aufnahme ins offizielle F-Droid-Repo
- [ ] Google Play Store

## Ferne Ideen

Nach einem stabilen Android-Release könnte Shlinker mit Compose Multiplatform auf weitere
Plattformen kommen:

- Linux als AppImage und im AUR
- später Windows

Dazu gibt es noch keinen Code und keinen Zeitplan.
