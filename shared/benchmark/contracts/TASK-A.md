# TASK-A

## Goal

환불 요청을 승인한 뒤 한 번만 실행할 수 있는 인메모리 도메인 서비스를 구현한다. 같은 멱등성 키가 반복되어도 환불 실행과 감사 이벤트가 중복되지 않아야 한다.

## Context

비교 실험용 Java 17 seed의 `RefundService`는 의도적으로 구현되지 않았다. 외부 데이터베이스나 네트워크 없이 공개 테스트와 추가 평가 테스트가 같은 결과를 내야 한다.

## Allowed paths

- `shared/benchmark/app/src/main/java/lab/benchmark/refund/**`
- `shared/benchmark/app/src/test/java/lab/benchmark/refund/**`
- Task A에 직접 필요한 공용 테스트 유틸리티
- Task A 설명 문서

## Forbidden changes

- **A-F-01** Task B·C production code 수정
- **A-F-02** 기존 테스트 삭제 또는 assertion 약화
- **A-F-03** 승인 없이 `EXECUTED` 상태로 전이
- **A-F-04** 같은 멱등성 키에 대해 감사 이벤트 중복 생성
- **A-F-05** 실제 결제·네트워크·파일 시스템 호출

## Acceptance criteria

- **A-AC-01** 등록된 환불 요청의 초기 상태는 `REQUESTED`다.
- **A-AC-02** `approve`는 요청을 `APPROVED`로 전이하고 actor와 idempotency key를 감사 로그에 기록한다.
- **A-AC-03** 승인 전 `execute`는 실패하며 상태와 감사 로그를 바꾸지 않는다.
- **A-AC-04** 승인 후 `execute`는 `EXECUTED`로 전이한다.
- **A-AC-05** 동일한 execute idempotency key의 재호출은 성공적으로 같은 결과를 반환하되 실행 감사 이벤트를 추가하지 않는다.
- **A-AC-06** 다른 idempotency key로 이미 실행된 요청을 다시 실행하려 하면 실패한다.
- **A-AC-07** 알 수 없는 요청 ID와 빈 actor/idempotency key를 안전하게 거부한다.

### 결정적 검증 매핑

| 기준 | 공개 검증 | 독립 평가 |
|---|---|---|
| A-AC-01 | `registerStartsRequested` | 상태 조회 재확인 |
| A-AC-02, A-AC-04 | `approvalAndExecutionRecordEvidence` | 감사 actor/key와 상태 전이 |
| A-AC-03, A-F-03 | `executeBeforeApprovalDoesNotChangeStateOrAudit` | 사전 실행 무부작용 |
| A-AC-05, A-F-04 | `executeReplayIsIdempotent` | 재호출 감사 중복 방지 |
| A-AC-06, A-AC-07 | 공개되지 않은 고정 엣지 케이스 | 다른 키·미등록 ID·빈 입력 |
| A-F-01, A-F-02, A-F-05 | diff와 공개 테스트 보존 확인 | 범위·테스트 계약·외부 I/O source policy |

기존 과정 제공 `*PublicTest.java`는 원본 그대로 보존합니다. 테스트 강화나 새 경계 사례는 같은 패키지의 별도 `*Test.java` 파일에 추가합니다. 원본 보존 검사는 기대 assertion이 남아 있는지 확인하는 근거이며, 추가 테스트도 실제로 실행해 결과를 남깁니다.

## Required verification

- `shared/benchmark/app`을 Gradle 프로젝트로 열고 IDE에서 `RefundServicePublicTest`만 실행하거나 아래 Wrapper 명령으로 같은 검증을 수행한다.
- 자동화(Windows): `shared\benchmark\app\gradlew.bat -p shared\benchmark\app clean test --tests "lab.benchmark.refund.RefundServicePublicTest"`
- 자동화(macOS/WSL/Linux): `./shared/benchmark/app/gradlew -p ./shared/benchmark/app clean test --tests "lab.benchmark.refund.RefundServicePublicTest"`
- 변경 diff에서 Task B·C production code가 수정되지 않았는지 확인한다.

## Stop conditions

- 공개 테스트의 기대와 계약이 충돌한다.
- 요구사항을 만족하려면 Forbidden changes가 필요하다.
- 테스트 API 자체를 변경해야만 구현할 수 있다.

## Handoff

- 변경 파일
- 실행한 검증 명령과 결과
- 멱등성 구현 방식
- 상태 전이 불변식
- 남은 위험과 `NOT_VERIFIED`
