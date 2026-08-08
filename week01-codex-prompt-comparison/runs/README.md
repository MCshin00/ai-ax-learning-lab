# 1주차 실행 기록

이 폴더에는 같은 Java 과제를 서로 다른 프롬프트 조건에서 수행한 결과와, 결과를 다시 검토하며 만든 공개 증거가 함께 들어 있습니다. 전체 비교와 해석은 [RESULTS.md](RESULTS.md), 현재 스냅샷의 독립 재검증은 [VERIFICATION.md](VERIFICATION.md)에서 볼 수 있습니다.

| 실행 | 조건 | 공개 증거 |
|---|---|---|
| [`run-a`](run-a/) | `gpt-5.6-terra` / `medium` / 짧은 요청 | [요청](run-a/request.md) · [첫 응답](run-a/response.md) · [실패 카드](run-a/evidence/failure-card.md) |
| [`run-b`](run-b/) | `gpt-5.6-terra` / `medium` / 구조화된 요청 | [요청](run-b/request.md) · [첫 응답](run-b/response.md) · [실패 카드](run-b/evidence/failure-card.md) |
| [`run-a-gpt53-spark-low`](run-a-gpt53-spark-low/) | `gpt-5.3-codex-spark` / `low` / 짧은 요청 | [요청](run-a-gpt53-spark-low/request.md) · [첫 응답](run-a-gpt53-spark-low/response.md) |
| [`run-b-gpt53-spark-low`](run-b-gpt53-spark-low/) | `gpt-5.3-codex-spark` / `low` / 구조화된 요청 | [요청](run-b-gpt53-spark-low/request.md) · [첫 응답](run-b-gpt53-spark-low/response.md) |
| [`agents-audit`](agents-audit/) | 계층형 `AGENTS.md` 읽기와 충돌 우선순위 확인 | [감사 응답](agents-audit/response.md) · [충돌 실험](agents-audit/conflict-experiment.md) |

각 `run-*` 폴더는 당시 결과 코드를 직접 열고 Gradle 테스트를 다시 실행할 수 있는 프로젝트입니다. 루트의 `request.md`와 `response.md`에는 실제 요청과 첫 최종 응답, `evidence/`에는 사후 검토에서 확인한 실패 카드가 있습니다.

이 실험에서는 전체 CLI 원시 로그를 별도 파일로 남기지 않았으므로 존재하지 않는 로그를 사후에 만들지 않았습니다. 이후 실행에서는 비밀값과 로컬 절대 경로를 제거한 로그만 `runs/`에 공개하고, 원본 로그는 `.local/raw/`에 보관합니다.
