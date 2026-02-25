# Issue: FIDO2 전환 설계 분리 (등록/서명 호환성)

## 배경

- 현재 Phase 3-1은 `play-services-fido`의 U2F 경로 기반 구현이다.
- U2F API는 deprecated 상태이고, 단말/Play Services 조합에 따라 appId/facet 제약이 까다롭다.
- `sk-ecdsa-sha2-nistp256@openssh.com` SSH 인증 경로는 유지해야 한다.

## 목표

- U2F 등록/서명 흐름을 FIDO2 기반으로 전환할 수 있는 설계를 별도 이슈로 정의한다.
- 기존 등록 데이터와 SSH 인증 경로의 호환 전략을 명확히 한다.

## 범위

- 포함:
  - Android FIDO2 등록(credential create) 및 서명(assertion) 후보 API 분석
  - 기존 저장 필드(`application`, `keyHandleBase64`, `publicKeyBase64`, `securityKeyFlags`)와의 매핑 전략
  - OpenSSH `sk-ecdsa` 인증에 필요한 데이터 추출/변환 경로 정의
  - 점진 전환(기존 U2F 등록키 계속 사용 + 신규 FIDO2 등록 병행) 전략
- 제외:
  - 즉시 코드 마이그레이션/릴리스
  - 지원하지 않는 구형 단말에 대한 별도 우회 구현

## 현재 구현 상태 (2026-02-25)

- PoC 코드 추가:
  - `app/src/main/java/com/opencode/sshterminal/security/Fido2SecurityKeyPocManager.kt`
  - 등록(create) + assertion(get) 최소 경로 구현
  - assertion `authenticatorData`에서 `flags/counter` 파싱
  - attestationObject(authData)에서 COSE EC2 공개키(x,y) 추출
- 실행 제어:
  - `BuildConfig.ENABLE_FIDO2_POC` 플래그로만 실행
  - 기본값 `false` (런타임 기본 동작은 기존 U2F 유지)
- 호출 지점:
  - `ConnectionListViewModel.enrollHardwareSecurityKey()`에서 플래그 on 시 PoC 실행 후 U2F 등록 진행
- 테스트:
  - `Fido2SecurityKeyPocManagerTest` 추가
  - 로컬 `testDebugUnitTest/ktlintCheck/detekt` 통과
- 미완료:
  - 실기기에서 FIDO2 PoC 성공 로그 1회 확보 필요
  - OpenSSH `sk-ecdsa` 직접 로그인 호환성 검증은 별도 추적

## 설계 결정이 필요한 항목

1. 등록 데이터 모델:
   - 기존 U2F 필드 확장 vs FIDO2 전용 필드 신설
2. appId/application 처리:
   - U2F의 `application`과 FIDO2의 RP ID/ClientDataHash 차이를 어떻게 SSH 포맷에 반영할지
3. 서명 데이터 파싱:
   - U2F `flags+counter+DER`와 FIDO2 assertion(authData/signature)의 매핑 규칙
4. 호환성 정책:
   - 기존 U2F 키 우선 사용 여부
   - 신규 등록 기본값을 FIDO2로 전환하는 시점
5. 장애 대응:
   - 단말별 FIDO2 API 미지원/오류 시 U2F fallback 유지 기간

## 작업 항목

1. GMS FIDO2 API 등록/서명 시퀀스 PoC 작성
2. OpenSSH `sk-ecdsa`와의 데이터 매핑 검증 테스트 작성
3. 기존 U2F 등록 데이터 마이그레이션 필요성 판단
4. 전환 플래그(실험 옵션) 설계
5. 실기기 매트릭스(안드로이드 버전/벤더/보안키 종류) 호환성 점검 계획 수립

## 완료 조건

- 설계 문서가 코드 변경 없이도 구현 가능한 수준으로 확정됨
- 데이터 모델/호환성/롤백 전략이 명시됨
- 최소 1개 실기기에서 FIDO2 등록/서명 PoC 성공 로그 확보

## 리스크

- OpenSSH `sk-ecdsa` 요구 포맷과 Android FIDO2 응답 포맷 간 차이
- 단말별 Play Services 버전 편차에 따른 동작 불일치
- U2F/FIDO2 이중 지원 기간 동안 QA 범위 증가
