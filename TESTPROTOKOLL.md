# Testprotokoll

Manuelle Tests auf echten Android-Geräten gegen eine eigene Shlink-Instanz.
Automatisierte Tests (Unit-Tests, Lint, ktlint, Screenshots) laufen zusätzlich in der CI.

## Ergebnisse

| Version | versionCode | Getestet | Ergebnis |
|---|---|---|---|
| 0.1.0 | 1 | Liste, Suche, Kopieren | ok; doppelte Server-Profile möglich → behoben in 0.1.2 |
| 0.1.2 | 3 | Benennungen, Dublettenprüfung | ok |
| 0.1.3 | 4 | Namensprüfung, Update mit Datenerhalt | ok |
| 0.1.4 | 5 | Umbenennen, Löschen, englische Oberfläche | ok |
| 0.1.5 | 6 | Server-Sortierung, Persistenz nach App-Neustart | ok |
| 0.1.6 | 7 | „Paste“, Tastatur-Fokus zwischen Feldern | ok |
| 0.2.0 | 8 | Shlink-Sortierung | ok |
| 0.2.1 | 9 | Server bearbeiten, Shlinks mit Tags anlegen | ok |
| 0.2.2 | 10 | Tag-Chips, App-Tag, Design | ok |
| 0.2.3 | 11 | Browser öffnen, Share/Copy | ok |
| 0.2.4 | 12 | Besuchszahlen, Detailseite | ok; Detailseite optisch später überarbeiten |
| 0.2.5 | 13 | Bearbeiten | ok; Datumseingabe und Abbrechen verbessert in 0.2.6 |
| 0.2.6 | 14 | Kalender/Uhrzeit, Verwerfen-Dialog | ok |
| 0.2.7 | 15 | Direkte Datums-/Uhrzeiteingabe | ok |
| 0.2.8 | 16 | Löschen, Tag-Vorschläge | ok |
| 0.2.9 | 17 | QR-Codes, Detail-Aktionen | ok |
| 0.2.10 | 18 | Statistik mit echten Werten | ok |
| 0.2.11 | 19 | Stabile Statistik-Kopfzeile | ok |
| 0.2.12 | 20 | Zeitverlauf | ok; Laden zu langsam → 0.2.13 |
| 0.2.13 | 21 | Sieben Abschnitte | ok, deutlich schneller |
| 0.2.14 | 22 | Redirect-Rules | ok |
| 0.2.15 | 23 | Domain-Auswahl, Regelstatus | ok |
| 0.3.0 | 24 | Teilen-Menü, Übernahme des Seitentitels | ok |

## Offen für 0.3.1

Die Grundfunktionen sind getestet. Diese Fälle stehen noch aus:

- [ ] Neustart und Persistenz: Profile, aktiver Server, Einstellungen, Sortierung
- [ ] Mehrere Profile und Domains, gleicher Short Code auf verschiedenen Domains
- [ ] Offline und Retry: sichtbare Fehler, keine falschen Erfolge
- [ ] Vorgaben für neue Shlinks gegen Server/Admin abgleichen, auch beim Teilen
- [ ] Teilen: ein und mehrere Server, Serverwechsel behält Eingaben, richtige Domain
- [ ] Teilen: Rotation, erneutes Teilen bei offener App, kein doppeltes Erstellen nach abgebrochenem Request
- [ ] Teilen: Erfolgsseite mit Symbolen, Screenreader-Beschriftungen, QR-Code

Optional: Sonderfälle bei Domain-Auswahl, Tags, Löschen, QR-Codes und Statistik
(Custom-Domains, Rotation, Dunkelmodus, Fehler/Retry).
