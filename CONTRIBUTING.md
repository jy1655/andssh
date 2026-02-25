# Contributing to AndSSH

Thanks for contributing.

## Before you start

- Read [README](README.md) for build and test commands.
- Read team conventions in `docs/code-convention.md`.
- Search existing issues and pull requests before creating a new one.

## Development workflow

1. Fork the repository and create a branch from `main`.
2. Keep changes focused and small.
3. Run local checks before opening a pull request:
   - `./gradlew :app:assembleDebug`
   - `./gradlew :app:testDebugUnitTest`
   - `./gradlew ktlintCheck detekt`
4. Open a pull request using the PR template.

## Pull request expectations

- Explain what changed and why.
- Include validation details (tests run, manual checks).
- Add screenshots or logs when UI or behavior changes.
- Update docs when behavior or policy changes.

## Issue reports

- Use issue templates for bug reports and feature requests.
- Include enough context to reproduce:
  - app version / commit
  - device model and Android version
  - logs and exact error messages

## Security issues

Please do not open public issues for sensitive security vulnerabilities.
Use the process in [SECURITY.md](SECURITY.md).
