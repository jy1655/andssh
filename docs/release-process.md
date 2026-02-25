# Release Process

Date baseline: 2026-02-25

This document defines the minimum release process for AndSSH.

## 1. Pre-release checks

1. Verify open-source gate checklist:
   - `docs/open-source-release-checklist.md`
2. Run local CI-equivalent checks:
   - `./scripts/ci-check.sh`
3. Build release bundle:
   - `./gradlew :app:bundleRelease`

## 2. Version update

1. Update `versionCode` and `versionName` in:
   - `app/build.gradle.kts`
2. Commit with release intent:
   - example: `chore(release): bump version to x.y.z`

## 3. Tag and GitHub Release

1. Create annotated tag:
   - `git tag -a vX.Y.Z -m "vX.Y.Z"`
2. Push branch and tag:
   - `git push origin main`
   - `git push origin vX.Y.Z`
3. Create GitHub Release from tag `vX.Y.Z`.

## 4. Changelog and announcement

1. Summarize user-facing changes:
   - features
   - fixes
   - known limitations
2. Include security-impacting changes explicitly.
3. Publish release notes and announcement link.

## 5. Post-release

1. Verify GitHub Actions release-adjacent workflows are green.
2. Confirm README and docs point to current known limitations.
3. Open follow-up issues for deferred work.
