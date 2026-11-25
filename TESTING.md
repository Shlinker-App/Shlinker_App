# Test log

Manual tests on real Android devices against a self-hosted Shlink instance.
Automated tests (unit tests, lint, ktlint, screenshots) run in CI on top of that.

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

## Open for 0.3.1

The basics are covered. These cases are still open:

- [ ] Restart and persistence: profiles, active server, settings, sorting
- [ ] Multiple profiles and domains, same short code on different domains
- [ ] Offline and retry: visible errors, no false successes
- [ ] Check defaults for new short URLs against server/admin settings, also when sharing
- [ ] Share: one and several servers, switching servers keeps input, correct domain
- [ ] Share: rotation, sharing again while the app is open, no duplicate after a cancelled request
- [ ] Share: success screen with icons, screen reader labels, QR code

Optional: edge cases for domain selection, tags, delete, QR codes and statistics
(custom domains, rotation, dark mode, errors/retry).

TEST - New CI Filter
