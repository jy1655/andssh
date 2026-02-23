# Android SSH Terminal Code Convention

이 문서는 `com.opencode.sshterminal` 코드의 일관성 유지와 유지보수 비용 절감을 위한 팀 규칙이다.

## 1. 기본 원칙

- 동작이 바뀌지 않는 범위에서 최소 변경으로 해결한다.
- 신규 추상화는 "중복이 실제로 2회 이상 발생하고, 앞으로도 반복될 가능성이 높은 경우"에만 도입한다.
- 리팩토링 시 기존 동작 경로(SSH 연결, 탭 전환, SFTP 파일 작업)는 유지되어야 한다.

## 2. 패키지/레이어 규칙

- `ui/*`: Compose 화면과 ViewModel 상태 표현 담당.
- `session/*`: SSH 세션/탭 상태와 연결 오케스트레이션 담당.
- `ssh/*`, `sftp/*`: 프로토콜 어댑터 구현 담당.
- `data/*`: 영속 모델과 저장소 접근 담당.
- `security/*`: 암복호화/키 관리 담당.

레이어 역참조 금지:

- `data`는 `ui`를 참조하지 않는다.
- `ssh`, `sftp`는 Compose/UI 타입을 참조하지 않는다.

## 3. Kotlin 스타일 규칙

- 들여쓰기 4칸, LF, 파일 끝 newline 유지 (`.editorconfig` 준수).
- 매직 넘버는 의미 있는 상수로 치환한다.
- nullable 처리 시 early return (`?: return`)을 우선 사용한다.
- `when`은 분기 누락이 없도록 exhaustive 유지.

## 4. ViewModel 상태 갱신 규칙

- UI 상태는 `data class` + `MutableStateFlow` + `copy()`로만 갱신한다.
- `stateIn(..., SharingStarted.WhileSubscribed(timeout), ...)` 사용 시 timeout 상수화.
- 연결 요청(`ConnectRequest`) 생성은 공용 팩토리(`ConnectionProfile.toConnectRequest`)를 사용한다.
- 사용자 노출 상태 문자열은 하드코딩하지 않고 `strings.xml` + `context.getString(...)`으로 관리한다.

## 5. SSH/SFTP 어댑터 규칙

- SSH 인증 로직은 `SSHClient.authenticate(request)` 공용 함수를 사용한다.
- `known_hosts` 파일 생성/갱신은 공용 유틸로 처리한다.
- I/O 동작은 `Dispatchers.IO`에서 실행한다.
- 연결 실패 정리는 `runCatching` 기반 best-effort close 패턴을 사용한다.

## 6. 문서 업데이트 규칙

- 동작 변경 또는 규칙 추가 시 다음 중 최소 1개를 함께 업데이트한다.
1. `README.md` (사용자/개발자 관점 변경점)
2. `docs/code-convention.md` (팀 규칙 변경)
3. 관련 테스트 파일 (행동 보장)

## 7. 정적 분석 규칙

- 코드 스타일 검사는 `ktlint`를 기준으로 한다.
- 정적 품질 검사는 `detekt`를 기준으로 한다.
- 현 시점 기존 이슈는 `app/detekt-baseline.xml`로 관리하며, 신규 이슈 유입은 막는다.

기본 실행 커맨드:

- `./gradlew ktlintCheck`
- `./gradlew detekt`
- `./gradlew testDebugUnitTest`

## 8. 리팩토링 완료 체크

- 변경 파일이 요청 범위를 벗어나지 않았는지 확인.
- `./gradlew ktlintCheck detekt testDebugUnitTest` 통과 확인.
