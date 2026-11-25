# Contributing to Shlinker

Thank you for your interest in Shlinker. Bug reports, testing, feedback and code contributions are all welcome.

## Where development happens

The canonical repository is [Shlinker/Shlinker_App on Forgejo](https://code.awit.at/Shlinker/Shlinker_App). The [GitHub repository](https://github.com/Shlinker-App/Shlinker_App) is an automatically updated mirror.

Please submit code contributions and development discussions on Forgejo. Please do not open pull requests on GitHub.

## Reporting bugs and requesting features

Open an issue on [Forgejo](https://code.awit.at/Shlinker/Shlinker_App/issues) (preferred) or on [GitHub](https://github.com/Shlinker-App/Shlinker_App/issues). Both trackers are synchronized, see [Issue synchronization](#issue-synchronization).

Please use one of the issue templates: Bug report, Feature request or Feedback/Question.

Labels are copied along with the issue. On GitHub, the issue type (for example `Bug`, `Feature` or `Feedback`) is set automatically from the labels. If you pick a type yourself, it is kept.

A useful bug report includes:

- What you did, what you expected and what happened instead.
- The Shlinker version and release channel, your Android version and your device.
- Your Shlink server version, if the problem involves a server.

Never post API keys, passwords, tokens or confidential server URLs. See [Do not post secrets](#do-not-post-secrets).

## Building and testing

You need JDK 21 and the Android SDK, including platform 37. Build instructions are in the [README](README.md#build-from-source). Before you submit a change, please run:

```bash
./gradlew ktlintCheck lintDebug lintDevsDebug
./gradlew testDebugUnitTest testDevsDebugUnitTest
```

Notes on testing are in [TESTING.md](TESTING.md).

## Writing style

Use English for code comments, test names, diagnostics, build and CI text, and default app copy.
Keep localized app and store text in the target language. Use neutral phrasing for names,
labels, and comments. If a personal perspective is needed, write in the first person.

## Issue synchronization

Issues can be opened on either platform. A bot copies issues and comments between Forgejo and GitHub roughly every five minutes. Copied issues and comments name the original author and link back to the source, in both directions, so you can see where something was written.

Forgejo is the source of truth for the state of an issue. If you close or reopen an issue on GitHub, the bot restores the state from Forgejo on its next run. Comments you write on GitHub are kept and copied to Forgejo.

### Things to know

- Issue numbers can differ between the platforms. When it is not clear which one you mean, name the platform, for example `Forgejo #12` or `GitHub #8`.
- Commit messages use full Forgejo links instead of short references like `#12`.
- Deleting an issue is not synchronized. Deletions need to be done on both platforms by a maintainer.
- Delays of a few minutes are normal.

### Do not post secrets

Issues are public and copied to a second platform. Never post API keys, passwords, tokens or confidential server URLs in an issue or comment. Describe your setup in general terms and redact addresses where needed.

### About the tooling

The synchronization uses [forgesync](https://github.com/eleboucher/forgesync) by Erwan Leboucher, extended with an optional setting that makes the Forgejo issue state authoritative on GitHub. This extension is not published yet. It has been offered upstream as an opt-in feature in [forgesync issue #40](https://git.erwanleboucher.dev/eleboucher/forgesync/issues/40), together with an offer to contribute a pull request. If upstream does not adopt it, the changes will be published as a public fork with full credit to the original project. 

It also contains a small fix for the GitHub issue search, which GitHub now requires to be restricted with `is:issue`.
