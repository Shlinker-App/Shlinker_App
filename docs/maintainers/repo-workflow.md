# Repository workflow (maintainers)

This document describes how the Shlinker repositories, the issue synchronization and the GitHub automation fit together. Contributor-facing rules are in [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Overview

- **Canonical repository:** [Shlinker/Shlinker_App on Forgejo](https://code.awit.at/Shlinker/Shlinker_App). All development, reviews and pull requests happen here.
- **Mirror:** [Shlinker-App/Shlinker_App on GitHub](https://github.com/Shlinker-App/Shlinker_App), updated by a Forgejo push mirror.
- **Issues:** synchronized in both directions by forgesync. Forgejo is the source of truth for issue state.
- **GitHub issue types:** set by a GitHub Actions workflow that ships with the repository.

## Push mirror

Configured in Forgejo under **Settings** → **Repository** → **Mirror settings**. It pushes to the GitHub repository on every commit and on a regular interval.

- The Forgejo instance must have mirroring enabled (`FORGEJO__mirror__ENABLED=true`).
- The mirror token must be allowed to update workflows, because the repository contains `.github/workflows/`. Without that permission, the push fails with `refusing to allow a Personal Access Token to create or update workflow ... without workflow scope`.
- Mirror status and the last error are shown in the same settings page. The sync icon triggers an immediate push.

## Issue synchronization (forgesync)

[forgesync](https://github.com/eleboucher/forgesync) runs as a container next to the Forgejo host and polls both platforms at its configured interval (`FORGESYNC_POLL_INTERVAL`, default `5m`). It copies new issues, comments, labels and state changes, names the original author and links back to the source.

### Accounts and permissions

| Account | Platform | Purpose |
|---|---|---|
| `shlinker-bot` | Forgejo | Reads the push-mirror configuration and writes synchronized issues |
| `aw-sync-bot` | GitHub | Pushes the mirror and writes synchronized issues and comments |

- Each bot is restricted to the Shlinker repository. forgesync only processes repositories on which its Forgejo user has admin access, because listing push mirrors requires it. Keep `shlinker-bot` limited to this one repository.
- `aw-sync-bot` uses two fine-grained tokens, **Shlinker Push Mirror** (mirror push, including workflows) and **Shlinker Issues Sync** (issue synchronization). The exact permissions can be checked by an organization owner in the GitHub organization settings under **Personal access tokens**.
- The Forgejo token for `shlinker-bot` is named `Issues-Sync`. Its permissions can be checked by an instance administrator in the Forgejo user settings.
- Tokens live in the server configuration. Never commit them and never paste them into issues.

### Behavior

- Issues and comments created on either platform appear on the other one.
- Issue numbers can differ between platforms.
- State is authoritative on Forgejo. Closing or reopening on GitHub is reverted at the next run.
- Deleting an issue is not synchronized and must be done on both platforms.
- An issue with the label `local-only` (any case) is not copied to the mirror. Add the label before the next run, because an issue that has already been copied keeps its mirror copy and only stops receiving updates.

### Local modifications

The deployed forgesync build is based on upstream release v0.0.6 and differs from it in two ways. Both exist only as local branches in the server's source checkout and are **not pushed anywhere** while we wait for the upstream maintainer's reply.

| Change | Branch | Notes |
|---|---|---|
| Opt-in setting that makes the Forgejo issue state authoritative on GitHub | `feat/canonical-forgejo-issue-state` | Offered upstream in [forgesync issue #40](https://git.erwanleboucher.dev/eleboucher/forgesync/issues/40) |
| GitHub marker search restricted with `is:issue`, plus a regression test | `fix/github-marker-search-is-issue` | Branched from upstream `main`, independent of the feature |

- The feature branch contains the fix as its own commit, followed by the feature commit. The deployed image is built from this branch.
- The fix branch contains only the fix and its test. All upstream tests pass on it without the feature.
- The fix is needed because GitHub rejects the marker search query without `is:issue`. Without it, the sink fails before it creates the issue.
- Upstream must not be pulled into the working copy casually. An update can conflict with or silently drop these changes. Before updating, check whether upstream has adopted either change, rebuild, run the full test suite and keep the previous image for rollback.
- If upstream declines the feature, the plan is a public fork with full credit to the original project (see [CONTRIBUTING.md](../../CONTRIBUTING.md#about-the-tooling)).
- Do not push any of the branches until the upstream maintainer has replied.

#### Rebuilding after a change

1. Work on `feat/canonical-forgejo-issue-state`.
2. Run the full test suite (`go test ./...`).
3. Build the image and recreate the container.
4. Check the log for a clean tick (`tick done`, no `failed` lines).

## Labels and issue types

| Label | Set by | GitHub type |
|---|---|---|
| `bug` | Issue template | `Bug` |
| `enhancement` | Issue template | `Feature` |
| `question` | Issue template | `Feedback` |
| `triage` | Issue template | none |
| `duplicate` | Maintainer, manually | none |
| `invalid` | Maintainer, manually | none |

The workflow `.github/workflows/issue-type.yml` runs on GitHub when an issue is opened or labeled. It maps the label to the type above and does nothing if a type is already set, so a manual choice is never overwritten. The `Feedback` type was created in the GitHub organization settings under **Issue types**. Forgejo ignores the workflow because `.forgejo/workflows` takes precedence.

## Continuous integration

The CI workflow is `.forgejo/workflows/ci.yml`. It runs on demand (**Actions** → **CI** → **Run workflow**) and for pull requests, not on every push, to keep dependency downloads from Maven Central low. Pull requests that only touch Markdown files, `docs/**` or `assets/badges/**` are skipped. README badges are served by a self-hosted shields service at `badge.awit.at`.

### Dependency cache

Maven Central answers heavy download traffic with `429 Too Many Requests`, and repeated blocks get longer. The 152 MB Robolectric native runtime (`nativeruntime-dist-compat`) was the file that kept failing. Two measures reduce the traffic:

- **Cache:** CI restores `~/.gradle`, Robolectric's Android framework jars in `~/.m2/repository/org/robolectric`, and the JDK and Android SDK in `/opt/ci`, and saves them at the end of every run, including failed runs, so downloads are never lost. Each run saves the Gradle cache under its own key. This needs the runner's cache server (`cache:` section in `config.yml`, with `container.network` set to the runner's network). Without it, the cache steps only log a warning.
- **Mirror:** Both workflows set `MAVEN_CENTRAL_MIRROR` to Google's mirror of Maven Central (`https://maven-central.storage-download.googleapis.com/maven2/`). `settings.gradle.kts` and `build-logic/settings.gradle.kts` use it instead of Maven Central when the variable is set, and the convention plugin passes it to Robolectric (`robolectric.dependency.repo.url`), which downloads its Android framework jars outside Gradle. Local builds are unchanged.

Runner setup on the Forgejo host (`/opt/docker/forgejo-runner/data/config.yml`): `cache.enabled: true`, `cache.dir: /data/cache`, `cache.port: 4000`, `cache.proxy_port: 4001` and `container.network: "forgejo-runner_default"`.

The release workflow does not use the cache, so a pull request run can never influence a signed build. Releases are rare, so the full download is acceptable.

### DEVS releases

Pushing a tag `vX.Y.Z` runs `.forgejo/workflows/release-devs.yml`:

1. The tag must match `versionName` in `app/build.gradle.kts`.
2. `CHANGELOG.md` must contain a section that starts with `## X.Y.Z ` (for example `## 0.3.2 – 2026-11-25`). Otherwise the run fails before anything is built.
3. ktlint, Android lint and the unit tests run; a failure stops the release.
4. The signed dev APK is built, its certificate is checked against the fingerprint in the README, and the APK and its SHA-256 file are uploaded to `generic/shlinker-devs/X.Y.Z`. An already published version is never overwritten.
5. A Forgejo pre-release named “Shlinker DEVS X.Y.Z” is created for the tag. Its text is the changelog section plus the package link and checksum, and the files are attached exactly as published in the package registry. An existing release is left unchanged.

Before tagging: bump `versionName` and `versionCode`, date the changelog section and finish the device checks in `TESTING.md`. Uploads use the `PACKAGES_TOKEN` secret; the pre-release uses the workflow's own token.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Mirror shows `PushRejected` mentioning `workflow` scope | Mirror token is not allowed to update workflows | Allow workflow updates for the token (**Workflows: Read and write**), update the token in the mirror settings, trigger a sync |
| Issue appears on one platform only | forgesync error or the next run has not happened yet | Check the container log for `upsert issue failed`, wait one interval |
| Log shows `search by marker` errors | GitHub search query without `is:issue` | Use a build that contains the `is:issue` fix |
| Issue has no type on GitHub | Label missing or workflow did not run | Check the issue labels and the **Actions** tab on GitHub |
| Duplicate issue after a restart | Marker lookup failed | Check the log for errors before the duplicate was created, close the duplicate on both platforms |
| Issue is not copied at all | Label `local-only` is set | Remove the label before the next run |