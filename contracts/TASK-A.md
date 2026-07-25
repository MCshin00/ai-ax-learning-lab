# TASK-A

## Goal

환불 요청을 승인한 뒤 한 번만 실행할 수 있는 인메모리 도메인 서비스를 구현한다. 같은 멱등성 키가 반복되어도 환불 실행과 감사 이벤트가 중복되지 않아야 한다.

## Context

비교 실험용 Java 17 seed의 `RefundService`는 의도적으로 구현되지 않았다. 외부 데이터베이스나 네트워크 없이 공개 테스트와 추가 평가 테스트가 같은 결과를 내야 한다.

## Allowed paths

- `benchmark-app/src/main/java/lab/benchmark/refund/**`
- `benchmark-app/src/test/java/lab/benchmark/refund/**`
- Task A에 직접 필요한 공용 테스트 유틸리티
- Task A 설명 문서

## Forbidden changes

- Task B·C production code 수정
- 기존 테스트 삭제 또는 assertion 약화
- 승인 없이 `EXECUTED` 상태로 전이
- 같은 멱등성 키에 대해 감사 이벤트 중복 생성
- 실제 결제·네트워크·파일 시스템 호출

## Acceptance criteria

- 등록된 환불 요청의 초기 상태는 `REQUESTED`다.
- `approve`는 요청을 `APPROVED`로 전이하고 actor와 idempotency key를 감사 로그에 기록한다.
- 승인 전 `execute`는 실패하며 상태와 감사 로그를 바꾸지 않는다.
- 승인 후 `execute`는 `EXECUTED`로 전이한다.
- 동일한 execute idempotency key의 재호출은 성공적으로 같은 결과를 반환하되 실행 감사 이벤트를 추가하지 않는다.
- 다른 idempotency key로 이미 실행된 요청을 다시 실행하려 하면 실패한다.
- 알 수 없는 요청 ID와 빈 actor/idempotency key를 안전하게 거부한다.

## Required verification

- Windows: `powershell -ExecutionPolicy Bypass -File benchmark-app/scripts/test.ps1`
- macOS/WSL/Linux: `bash benchmark-app/scripts/test.sh`
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
