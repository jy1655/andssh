# Open Source Release Checklist

Date baseline: 2026-02-25

Use this checklist before making the repository public.

## 1. Legal and policy

- [x] `LICENSE` exists (MIT)
- [x] `README.md` references license
- [x] `CONTRIBUTING.md` exists
- [x] `CODE_OF_CONDUCT.md` exists
- [x] `SECURITY.md` exists
- [x] Third-party dependency license review completed (all dependencies are Apache 2.0 / MIT / BSD compatible with MIT)
- [x] `NOTICE` not required (no copyleft or attribution-required dependencies)

## 2. Secret and artifact hygiene

- [x] Run secret scan on full git history (`gitleaks git`, 2026-02-25, `.gitleaksignore` applied for test fixture false-positive fingerprint)
- [x] Confirm no signing assets are tracked (`git ls-files` check, 2026-02-25)
- [x] Confirm no personal/local config is tracked (`git ls-files` check, 2026-02-25)
- [x] Confirm no debug logs with sensitive data are committed (no tracked `*.log`; pattern scan only hit expected SSH key PEM header constants/tests, checked 2026-02-25)

## 3. Repository hygiene

- [x] Remove or relocate internal-only docs/prompts that should not be public (`AGENTS.md` removed; internal handover and device-specific U2F docs excluded from tracked files)
- [x] Ensure `.gitignore` covers local artifacts and build outputs (`local.properties`, `keystore.properties`, `*.jks`, build outputs ignored)
- [x] Verify README quickstart works on clean environment (temp clean copy build: `:app:assembleDebug :app:testDebugUnitTest` success on 2026-02-25)
- [x] Verify CI pipeline passes on default branch (GitHub Actions `Android CI` on `main`: recent runs success, checked 2026-02-25)

## 4. Security and support readiness

- [x] Confirm vulnerability reporting path is usable (GitHub `private-vulnerability-reporting` enabled on 2026-02-25)
- [x] Document supported branch/version policy in `SECURITY.md`
- [x] Add issue templates and baseline triage workflow (`bug` / `enhancement` / `needs-triage` + `security` + `release-blocker`, PR template)
- [x] Add release process note (tagging, changelog, announcement) in `docs/release-process.md`

## 5. Android-specific checks

- [x] Confirm release signing material is not in repository (`git ls-files` check, 2026-02-25)
- [x] Confirm debug/release build commands work from README (`assembleDebug`, `bundleRelease` verified on 2026-02-25)
- [x] Confirm current known limitations are documented in README/docs
- [x] Confirm disabled features for current release are clearly documented

## 6. Final gate

- [x] `./scripts/ci-check.sh` passes locally (2026-02-25)
- [x] One maintainer self-review completed (2026-02-25)
- [x] Public visibility toggle approved (repository set to public)
