---
name: task-contract-writer
description: TODO — 필요한 요청에서는 선택되고, 설명·번역·단순 질문에서는 선택되지 않도록 구체적으로 작성한다.
---

# Task Contract Writer

## 목표

모호한 구현 요청을 검토 가능한 Markdown 작업 계약으로 변환한다.

## TODO

1. 저장소에서 읽을 최소 컨텍스트를 정의한다.
2. 정보가 부족하면 구현을 시작하지 않고 Open questions를 남긴다.
3. `assets/task-contract-template.md`를 사용한다.
4. Allowed paths·Forbidden changes·Acceptance criteria·Verification·Stop conditions를 작성한다.
5. `scripts/validate_contract.py`로 결과를 검증한다.
6. 위험한 요청을 거부하는 규칙을 작성한다.
