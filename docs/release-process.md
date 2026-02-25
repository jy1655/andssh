# Release Process

Date baseline: 2026-02-25

This document defines the minimum release process for AndSSH.

## 0. One-time CI setup (Google Play auto upload)

1. Configure repository secrets:
   - `PLAY_SERVICE_ACCOUNT_JSON`
   - `ANDROID_UPLOAD_KEYSTORE_BASE64`
   - `ANDROID_UPLOAD_STORE_PASSWORD`
   - `ANDROID_UPLOAD_KEY_ALIAS`
   - `ANDROID_UPLOAD_KEY_PASSWORD`
2. Optional repository variables:
   - `PLAY_TRACK` (default `internal`)
   - `PLAY_RELEASE_STATUS` (default `completed`)
   - `PLAY_VERSION_CODE_OFFSET` (default `100000`)
3. Confirm workflow exists:
   - `.github/workflows/play-release.yml`

## 1. Pre-release checks

1. Verify open-source gate checklist:
   - `docs/open-source-release-checklist.md`
2. Run local CI-equivalent checks:
   - `./scripts/ci-check.sh`
3. Build release bundle:
   - `./gradlew :app:bundleRelease`
4. Confirm latest Play upload workflow run is successful on `main`.

## 2. Version update

1. Update baseline `versionCode` and `versionName` in:
   - `app/build.gradle.kts`
2. CI can override `versionCode` using `-Pandssh.ciVersionCode` (used by `play-release.yml`) so repeated main-branch pushes do not fail with duplicate version codes.
3. Commit with release intent:
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
