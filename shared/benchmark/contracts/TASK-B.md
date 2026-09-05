# TASK-B

## Goal

역할 기반 계정 잠금 해제 서비스와 감사 로그를 구현한다. 권한이 없는 사용자는 상태를 바꾸지 못해야 하며 같은 멱등성 키의 재호출은 중복 이벤트를 만들지 않아야 한다.

## Context

비교 실험용 Java 17 seed의 `AccountUnlockService`는 구현되지 않았다. 허용 역할은 `ADMIN`과 `SUPPORT`이며 `USER`는 해제할 수 없다.

## Allowed paths

- `shared/benchmark/app/src/main/java/lab/benchmark/account/**`
- `shared/benchmark/app/src/test/java/lab/benchmark/account/**`
- Task B에 직접 필요한 공용 테스트 유틸리티
- Task B 설명 문서

## Forbidden changes

- **B-F-01** Task A·C production code 수정
- **B-F-02** 호출자가 넘긴 문자열을 검사하지 않고 권한 있다고 가정
- **B-F-03** 권한 실패 시 계정 상태 또는 감사 로그 변경
- **B-F-04** 테스트 삭제·비활성화·assertion 약화

## Acceptance criteria

- **B-AC-01** 등록된 잠긴 계정은 `LOCKED` 상태다.
- **B-AC-02** `ADMIN`과 `SUPPORT`만 잠금을 해제할 수 있다.
- **B-AC-03** `USER`, null 역할, 빈 actor는 거부되고 상태와 감사 로그가 유지된다.
- **B-AC-04** 성공한 해제는 actor, role, account ID, idempotency key를 감사 로그에 남긴다.
- **B-AC-05** 동일한 idempotency key의 재호출은 결과를 재사용하며 중복 감사 이벤트를 만들지 않는다.
- **B-AC-06** 이미 해제된 계정에 다른 idempotency key를 사용하면 실패한다.
- **B-AC-07** 알 수 없는 account ID를 안전하게 거부한다.

### 결정적 검증 매핑

| 기준 | 공개 검증 | 독립 평가 |
|---|---|---|
| B-AC-01 | `registerStartsLocked` | 상태 조회 재확인 |
| B-AC-02, B-AC-04 | `supportUnlockRecordsEvidence` | ADMIN 허용과 감사 필드 |
| B-AC-03, B-F-02, B-F-03 | `unauthorizedUserDoesNotChangeStateOrAudit` | null 역할·빈 actor 무부작용 |
| B-AC-05 | `successfulReplayIsIdempotent` | 감사 중복 방지 |
| B-AC-06, B-AC-07 | 공개되지 않은 고정 엣지 케이스 | 다른 키·미등록 계정 |
| B-F-01, B-F-04 | diff와 공개 테스트 보존 확인 | 범위·테스트 계약 source policy |

기존 과정 제공 `*PublicTest.java`는 원본 그대로 보존합니다. 테스트 강화나 새 경계 사례는 같은 패키지의 별도 `*Test.java` 파일에 추가합니다. 원본 보존 검사는 기대 assertion이 남아 있는지 확인하는 근거이며, 추가 테스트도 실제로 실행해 결과를 남깁니다.

## Required verification

- `shared/benchmark/app`을 Gradle 프로젝트로 열고 IDE에서 `AccountUnlockServicePublicTest`만 실행하거나 아래 Wrapper 명령으로 같은 검증을 수행한다.
- 자동화(Windows): `shared\benchmark\app\gradlew.bat -p shared\benchmark\app clean test --tests "lab.benchmark.account.AccountUnlockServicePublicTest"`
- 자동화(macOS/WSL/Linux): `./shared/benchmark/app/gradlew -p ./shared/benchmark/app clean test --tests "lab.benchmark.account.AccountUnlockServicePublicTest"`
- 변경 diff에서 Task A·C production code가 수정되지 않았는지 확인한다.

## Stop conditions

- 역할 정의 또는 상태 전이가 계약과 테스트 사이에서 충돌한다.
- Forbidden changes 없이는 구현할 수 없다.

## Handoff

- 변경 파일
- 검증 결과
- 권한과 멱등성 규칙
- 실패 경로 테스트
- 남은 위험과 `NOT_VERIFIED`
