# U2F 장애 리포트 템플릿

## 1) 기본 정보

- 발생 일시:
- 앱 버전 (`versionName/versionCode`):
- 단말 모델 / Android 버전:
- Google Play Services 버전:
- 보안키 종류(벤더/모델):

## 2) 재현 절차

1.
2.
3.

## 3) 기대 결과

-

## 4) 실제 결과

-

## 5) 앱 로그 수집 (`AndSSH-U2F`)

```bash
adb logcat -d | rg "AndSSH-U2F|Fido"
```

필수 포함 항목:
- `stage` (`enroll`, `enroll_unexpected`, `sign`)
- `appId`
- `code`
- `message`

## 6) 서버 로그 (접속 단계 문제일 때)

```bash
./scripts/security-key-e2e.sh show-log
```

필수 확인 항목:
- `Accepted publickey` 존재 여부
- `sk-ecdsa-sha2-nistp256@openssh.com` 타입 표시 여부

## 7) 임시 우회/복구

- 재시도 가능 여부:
- appId 변경(비움/명시) 시 결과:
- 기존 개인키 인증 fallback 가능 여부:

## 8) 분류

- [ ] 앱 로직 결함
- [ ] GMS/FIDO API 동작 편차
- [ ] 서버 설정/키 불일치
- [ ] 환경(네트워크/권한/디바이스 상태)
