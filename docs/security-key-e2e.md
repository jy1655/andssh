# Security Key E2E (OpenSSH 실기기 검증)

이 문서는 AndSSH의 하드웨어 보안키 경로(`sk-ecdsa-sha2-nistp256@openssh.com`)를
실서버(OpenSSH) 기준으로 검증하는 절차다.

## 1. 목적

- 앱에서 보안키 등록(U2F 기반) 후 SSH 접속 시 `publickey` 인증이 성공하는지 확인
- OpenSSH 서버 로그에서 `sk-ecdsa-sha2-nistp256@openssh.com` 타입 수락 확인

## 2. 사전 조건

- Android 실기기 1대 (`adb devices`에서 `device` 상태)
- 보안키(FIDO/U2F) 사용 가능 상태
- OpenSSH 8.2+ (권장: 9.x)
- 이 저장소 루트에서 실행

## 3. 테스트 서버 준비

로컬 OpenSSH 테스트 서버 시작:

```bash
./scripts/security-key-e2e.sh start
```

상태/접속 정보 확인:

```bash
./scripts/security-key-e2e.sh status
```

출력에서 확인할 값:

- `candidate host`: 폰에서 접속할 호스트 IP
- `listen port`: 기본 `10022`
- `username`: 접속 사용자명
- `authorized_keys`: 앱에서 복사한 `authorized_key`를 붙여넣을 파일

## 4. 앱(실기기)에서 등록/접속

1. 앱 실행 후 `새 연결` 화면으로 이동한다.
2. `Host`, `Port`, `Username`을 서버 출력값으로 입력한다.
3. `Security key application`은 기본값 `ssh:` 유지(또는 서버 정책에 맞게 입력).
4. `Enroll Security Key`를 누르고 보안키 터치를 완료한다.
5. `Copy authorized_key`를 눌러 공개키를 복사한다.
6. 데스크톱에서 `authorized_keys` 파일에 한 줄로 붙여넣는다.
7. 앱에서 해당 연결로 접속한다.

## 5. 성공 판정

서버 로그 확인:

```bash
./scripts/security-key-e2e.sh show-log
```

성공 기준:

- `Accepted publickey` 또는 이에 준하는 성공 로그 존재
- 키 타입이 `sk-ecdsa-sha2-nistp256@openssh.com`으로 표시

앱 측 기준:

- 연결 상태가 `CONNECTED`로 전환
- 비밀번호/개인키 없이 보안키 인증으로 세션 진입

## 6. 실패 시 점검

- `Permission denied (publickey)`:
  - `authorized_keys`에 복사한 키가 정확히 1줄인지 확인
  - 앱의 `Security key application` 값과 등록 때 값이 동일한지 확인
- 서버 시작 실패:
  - `./scripts/security-key-e2e.sh show-log`로 오류 확인
  - macOS에서 `sshd` 경로가 다르면 `SSHD_BIN` 환경변수로 지정
- 폰에서 연결 불가:
  - 동일 Wi-Fi/네트워크인지 확인
  - 방화벽에서 테스트 포트(`10022`) 허용 여부 확인

## 7. 정리

테스트 서버 종료:

```bash
./scripts/security-key-e2e.sh stop
```
