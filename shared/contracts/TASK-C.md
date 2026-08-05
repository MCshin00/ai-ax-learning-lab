# TASK-C

## Goal

구독 해지 작업을 재시도 가능한 인메모리 작업으로 구현한다. 일시 오류는 제한된 횟수 안에서 재시도하고, 영구 오류와 최대 재시도 초과는 실패 상태와 감사 이력으로 남겨야 한다.

## Context

비교 실험용 Java 17 seed의 `CancellationJobService`는 구현되지 않았다. 외부 API 대신 제공된 `CancellationGateway`를 사용한다.

## Allowed paths

- `shared/benchmark-app/src/main/java/lab/benchmark/cancellation/**`
- `shared/benchmark-app/src/test/java/lab/benchmark/cancellation/**`
- Task C에 직접 필요한 공용 테스트 유틸리티
- Task C 설명 문서

## Forbidden changes

- Task A·B production code 수정
- 무제한 재시도
- 이미 성공한 작업을 외부 gateway에 다시 전달
- 실패를 삼키고 `SUCCEEDED`로 표시
- sleep 기반 장시간 대기
- 테스트 삭제·약화

## Acceptance criteria

- 새 작업은 `PENDING`, 시도 횟수 0으로 등록된다.
- 처리 시 gateway 성공이면 `SUCCEEDED`가 되고 시도 횟수가 1 증가한다.
- 일시 오류이면 최대 3회까지 재시도한다.
- 세 번째 시도까지 일시 오류이면 `FAILED`가 된다.
- 영구 오류이면 즉시 `FAILED`가 된다.
- 성공한 작업의 재처리는 gateway를 다시 호출하지 않는다.
- 각 시도와 최종 결과를 순서대로 감사 로그에 기록한다.
- 알 수 없는 작업 ID와 빈 idempotency key를 안전하게 거부한다.

## Required verification

- `shared/benchmark-app`를 Gradle 프로젝트로 열고 IDE에서 `clean`, `test` 작업을 실행하거나 아래 Wrapper 명령으로 같은 검증을 수행한다.
- 자동화(Windows): `shared\benchmark-app\gradlew.bat -p shared\benchmark-app clean test`
- 자동화(macOS/WSL/Linux): `./shared/benchmark-app/gradlew -p ./shared/benchmark-app clean test`
- 변경 diff에서 Task A·B production code가 수정되지 않았는지 확인한다.

## Stop conditions

- gateway 오류 분류가 계약과 코드에서 일치하지 않는다.
- 테스트 API 변경이 필요하다.
- Forbidden changes 없이는 구현할 수 없다.

## Handoff

- 변경 파일
- 검증 결과
- 재시도와 멱등성 규칙
- gateway 호출 횟수 증거
- 남은 위험과 `NOT_VERIFIED`
