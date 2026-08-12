# Task Pack A — 승인 기반 환불 실행

실행 계약은 `shared/benchmark/contracts/TASK-A.md`다. 구현 세션은 계약을 읽고 `shared/benchmark/app`의 Task A 영역만 수정한다.

핵심 평가 축:

- 상태 전이 정확성
- 승인 우회 차단
- 멱등성
- 감사 로그 중복 방지
- 입력 검증
- 범위 통제

공개 완료 확인은 `RefundServicePublicTest`만 실행하며, 원시 통과·실패 수를 기록한다. 세부 기준 ID와 정확한 Wrapper 명령은 `contracts/TASK-A.md`를 따른다.
