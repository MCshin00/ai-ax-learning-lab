# AI/AX Learning Lab repository rules

## 목적

이 저장소는 Codex 하네스, Skills, MCP, LLM 애플리케이션, 평가 방식을 단계적으로 실험한다. 기능 수보다 **재현 가능한 실행·실패 기록·검증**을 우선한다.

## 공통 규칙

- 저장소 루트와 하위 경로 밖의 파일을 생성·수정·이동·삭제하지 않는다.
- 사용자의 기존 변경을 보존하고 요청과 무관한 파일은 수정하지 않는다.
- `.env`, API 키, OAuth 토큰, 개인 경로와 비밀값을 코드·로그·Git diff에 남기지 않는다.
- 테스트를 삭제하거나 assertion을 약화해 통과시키지 않는다.
- 외부 네트워크와 실제 LLM API 호출은 작업 계약이 명시적으로 허용한 경우만 수행한다.
- 현재 주차에 공개된 과제 README 또는 작업 계약을 따른다. `shared/benchmark/contracts/`가 공개된 뒤에는 해당 계약을 정본으로 사용하며, 일회성 요구를 이 파일에 추가하지 않는다.
- 실행하지 못한 검증은 `NOT_VERIFIED`와 이유를 남긴다.
- 완료 전 변경 범위, 테스트, 남은 위험, 미검증 항목을 handoff 형식으로 보고한다.
- 공개할 실험 결과는 해당 주차 폴더의 `runs/<run-id>/`에 저장하고 기존 run을 덮어쓰지 않는다.

## 기술 기준

- Python 3.11 이상
- Java 17 이상
- Python 외부 의존성은 해당 `pyproject.toml`과 lock file로 관리한다.
- 실제 모델 호출은 `AI_LIVE_CALLS_ENABLED=true`일 때만 허용한다.

## 세션 규칙

- 병렬 쓰기 세션은 서로 다른 worktree 또는 겹치지 않는 Allowed paths를 사용한다.
- 통합 세션만 main에 병합한다.
- Reviewer와 Evaluator는 구현 대화보다 계약, diff, 테스트 증거를 우선 본다.
