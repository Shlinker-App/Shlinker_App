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

[forgesync](https://github.com/eleboucher/forgesync) runs as a container next to the Forgejo host and polls both platforms at its configured interval. It copies new issues, comments, labels and state changes, names the original author and links back to the source.

### Accounts and permissions

| Account | Platform | Purpose |
|---|---|---|
| `shlinker-bot` | Forgejo | Reads the push-mirror configuration and writes synchronized issues |
| `aw-sync-bot` | GitHub | Pushes the mirror and writes synchronized issues and comments |

- Each bot is restricted to the Shlinker repository. forgesync processes every repository on which its Forgejo user has admin access, so keep `shlinker-bot` limited to this one.
- `aw-sync-bot` uses two fine-grained tokens, **Shlinker Push Mirror** (mirror push, including workflows) and **Shlinker Issues Sync** (issue synchronization). The exact permissions can be checked by an organization owner in the GitHub organization settings under **Personal access tokens**.
- The Forgejo token for `shlinker-bot` is named `Issues-Sync`. Its permissions can be checked by an instance administrator in the Forgejo user settings.
- Tokens live in the server configuration. Never commit them and never paste them into issues.

### Behavior

- Issues and comments created on either platform appear on the other one.
- Issue numbers can differ between platforms.
- State is authoritative on Forgejo. Closing or reopening on GitHub is reverted at the next run.
- Deleting an issue is not synchronized and must be done on both platforms.

### Local modifications

The deployed forgesync build differs from upstream in two ways:

1. An opt-in setting that makes the Forgejo issue state authoritative on GitHub. It was offered upstream in forgesync issue #40.
2. A one-line fix in the GitHub sink: the marker search query now ends with `is:issue`. GitHub rejects the search without it, and the sink then fails before creating the issue.

An upstream update can overwrite these changes. Before updating, check whether both are included upstream, and keep the previous image so you can roll back.

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

The CI workflow is `.forgejo/workflows/ci.yml`. It skips runs for changes that only touch the README, `docs/**` and `assets/badges/**`. README badges are served by a self-hosted shields service at `badge.awit.at`.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Mirror shows `PushRejected` mentioning `workflow` scope | Mirror token lacks the workflow permission | Add the `workflow` scope or **Workflows: Read and write**, update the token in the mirror settings, trigger a sync |
| Issue appears on one platform only | forgesync error or the next run has not happened yet | Check the container log for `upsert issue failed`, wait one interval |
| Log shows `search by marker` errors | GitHub search query without `is:issue` | Use a build that contains the `is:issue` fix |
| Issue has no type on GitHub | Label missing or workflow did not run | Check the issue labels and the **Actions** tab on GitHub |
| Duplicate issue after a restart | Marker lookup failed | Check the log for errors before the duplicate was created, close the duplicate on both platforms |