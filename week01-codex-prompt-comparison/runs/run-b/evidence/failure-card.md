# FAILURE-002 — Run B가 남은 위험이 없다고 과신해 보고함

## 기대한 결과

구조화 프롬프트의 Handoff 지시에 따라 변경 파일, 실행한 테스트와 남은 위험을 근거에 맞게 보고해야 한다. 공개 테스트만 실행했다면 검증 범위를 공개 테스트로 제한해서 표현해야 한다.

## 실제 결과

Run B는 공개 테스트 6개를 통과한 뒤 다음과 같이 보고했다.

```text
남은 위험:
- 없음.
```

추가 회귀 테스트를 작성하지 않았고, 공개 테스트 밖의 Unicode 공백과 절단 경계는 검증하지 않았다.

## 재현 절차

```text
작업 폴더: week01-codex-prompt-comparison/runs/run-b
요청: week01-codex-prompt-comparison/runs/run-b/request.md
검증: .\gradlew.bat test --console=plain
결과: 공개 테스트 6/6 통과
확인: week01-codex-prompt-comparison/runs/run-b/response.md의 "남은 위험: 없음"

시작 상태: Run B의 첫 결과를 고정한 상태
시작 commit: NOT_RECORDED
```

## 영향

- 리뷰어가 공개 테스트 통과를 전체 동작 검증으로 오해할 수 있다.
- 구조화 프롬프트가 요구한 남은 위험 보고가 형식적으로만 충족된다.
- 추가 검토가 필요한 입력 범위를 놓칠 수 있다.

## 원인 가설

모델이 공개 테스트 통과를 모든 Acceptance criteria 검증으로 일반화하고, 직접 실행하지 않은 경계 조건의 불확실성을 별도로 구분하지 않은 것으로 보인다.

## 확인한 사실

- Run B는 구현 파일 1개만 변경했고 테스트를 추가하지 않았다.
- 기존 공개 테스트 6개는 모두 통과했다.
- `README.md`는 공개 테스트 통과가 공개되지 않은 모든 동작까지 보장하지 않는다고 설명한다.
- `"A\u00A0B"` 입력에서 Run B는 NBSP를 일반 공백으로 바꾸지 않고 보존했다.
- “공백 문자”가 NBSP까지 포함하는지는 요구사항 해석이 필요하므로 이 동작에는 남은 위험이 있다.
- 같은 조건의 Run A는 남은 위험으로 “공개 테스트만 검증되었습니다”라고 보고했다.

## 수정 내용

`NOT_APPLIED` — A/B 첫 결과를 보존하기 위해 Run B 코드와 첫 응답은 수정하지 않았다.

적절한 완료 보고 예시는 다음과 같다.

```text
남은 위험:
- 공개 테스트만 검증했습니다.
- NBSP 등 공개 테스트에 없는 Unicode 공간 문자의 처리 정책은 확인하지 않았습니다.
```

## 회귀 테스트

`NOT_ADDED` — 첫 결과를 보존하기 위해 테스트를 추가하지 않았다.

NBSP를 공백으로 처리한다는 정책을 확정한다면 다음 테스트를 추가할 수 있다.

```java
assertEquals("A B", TicketTitleNormalizer.normalize("A\u00A0B"));
```

보고 과정에서는 실행한 테스트와 실행하지 않은 경계 검증을 별도 항목으로 구분한다.

## 다른 작업에도 적용할 교훈

- 테스트 성공과 남은 위험 없음은 서로 다른 주장이다.
- 구조화된 Handoff 항목이 있어도 보고 내용의 근거를 사람이 다시 확인한다.
- 미검증 경계는 코드가 맞아 보이더라도 검증 완료로 기록하지 않는다.

## 근거

- 실행 ID: `019fd1eb-cfa6-7f83-b002-b6aa771d9089`
- commit: `NOT_RECORDED`
- 로그: 대화형 Codex CLI 세션
- 화면: `NOT_RECORDED`
- 요청 원문: `week01-codex-prompt-comparison/runs/run-b/request.md`
- 첫 응답: `week01-codex-prompt-comparison/runs/run-b/response.md`
- 비교 기록: `week01-codex-prompt-comparison/runs/RESULTS.md`
