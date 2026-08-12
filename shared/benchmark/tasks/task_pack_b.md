# Task Pack B — 역할 기반 계정 잠금 해제

실행 계약은 `shared/benchmark/contracts/TASK-B.md`다. 구현 세션은 계약을 읽고 `shared/benchmark/app`의 Task B 영역만 수정한다.

핵심 평가 축:

- 역할 검사
- 실패 시 무변경
- 멱등성
- 감사 로그
- 범위 통제

공개 완료 확인은 `AccountUnlockServicePublicTest`만 실행하며, 원시 통과·실패 수를 기록한다. 세부 기준 ID와 정확한 Wrapper 명령은 `contracts/TASK-B.md`를 따른다.
