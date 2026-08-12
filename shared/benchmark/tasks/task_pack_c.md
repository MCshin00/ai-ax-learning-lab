# Task Pack C — 재시도 가능한 구독 해지 작업

실행 계약은 `shared/benchmark/contracts/TASK-C.md`다. 구현 세션은 계약을 읽고 `shared/benchmark/app`의 Task C 영역만 수정한다.

핵심 평가 축:

- 일시·영구 오류 분류
- 최대 재시도
- 성공 후 재실행 방지
- 시도·최종 결과 감사 로그
- 범위 통제

공개 완료 확인은 `CancellationJobServicePublicTest`만 실행하며, 원시 통과·실패 수를 기록한다. 세부 기준 ID와 정확한 Wrapper 명령은 `contracts/TASK-C.md`를 따른다.
