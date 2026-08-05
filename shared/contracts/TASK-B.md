# TASK-B

## Goal

역할 기반 계정 잠금 해제 서비스와 감사 로그를 구현한다. 권한이 없는 사용자는 상태를 바꾸지 못해야 하며 같은 멱등성 키의 재호출은 중복 이벤트를 만들지 않아야 한다.

## Context

비교 실험용 Java 17 seed의 `AccountUnlockService`는 구현되지 않았다. 허용 역할은 `ADMIN`과 `SUPPORT`이며 `USER`는 해제할 수 없다.

## Allowed paths

- `shared/benchmark-app/src/main/java/lab/benchmark/account/**`
- `shared/benchmark-app/src/test/java/lab/benchmark/account/**`
- Task B에 직접 필요한 공용 테스트 유틸리티
- Task B 설명 문서

## Forbidden changes

- Task A·C production code 수정
- 호출자가 넘긴 문자열을 검사하지 않고 권한 있다고 가정
- 권한 실패 시 계정 상태 또는 감사 로그 변경
- 테스트 삭제·비활성화·assertion 약화

## Acceptance criteria

- 등록된 잠긴 계정은 `LOCKED` 상태다.
- `ADMIN`과 `SUPPORT`만 잠금을 해제할 수 있다.
- `USER`, null 역할, 빈 actor는 거부되고 상태와 감사 로그가 유지된다.
- 성공한 해제는 actor, role, account ID, idempotency key를 감사 로그에 남긴다.
- 동일한 idempotency key의 재호출은 결과를 재사용하며 중복 감사 이벤트를 만들지 않는다.
- 이미 해제된 계정에 다른 idempotency key를 사용하면 실패한다.
- 알 수 없는 account ID를 안전하게 거부한다.

## Required verification

- `shared/benchmark-app`를 Gradle 프로젝트로 열고 IDE에서 `clean`, `test` 작업을 실행하거나 아래 Wrapper 명령으로 같은 검증을 수행한다.
- 자동화(Windows): `shared\benchmark-app\gradlew.bat -p shared\benchmark-app clean test`
- 자동화(macOS/WSL/Linux): `./shared/benchmark-app/gradlew -p ./shared/benchmark-app clean test`
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
