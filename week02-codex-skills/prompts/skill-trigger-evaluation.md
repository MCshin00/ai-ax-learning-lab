# Skill 발동 결과 집계

> 분류: **[자동 측정용]**

## 사용 방법

`week02-codex-skills/lab/`을 Codex 앱의 primary folder 또는 CLI의 CWD로 두어 이 폴더의 Skill이 실제로 발견되게 합니다. `../runs/`도 쓰기 가능한 범위로 추가합니다. positive·negative·boundary 대표 사례를 먼저 앱이나 대화형 CLI에서 수동으로 실행합니다. 그다음 학습자가 `expected_trigger`와 근거를 확정하고 실제 관찰 파일을 채웁니다. 아래 본문과 입출력 경로를 확인해 동결한 뒤, 대화창에 직접 붙여넣거나 반복 측정에서 `codex exec` 입력으로 사용할 수 있습니다.

이 요청은 사례를 대신 실행하거나 기대값을 정하지 않습니다. 집계 결과도 원본 행과 대조한 뒤 학습자가 승인합니다.

## 전송할 본문

```text
Skill 발동 평가 결과를 읽기 전용으로 집계해 주세요.

입력:
- 기대값: evals/skill-trigger-cases.jsonl
- 실제 관찰값: ../runs/skill-trigger/trigger-observations.jsonl

허용된 출력:
- 행별 판정: ../runs/skill-trigger/trigger-evaluation.jsonl
- 요약: ../runs/skill-trigger/RESULTS.md

Skill 구현과 입력 파일은 수정하지 마세요. 두 입력을 id로 대응시키고, 실제 관찰값이나 발동 근거가 비어 있으면 추정하지 말고 NOT_VERIFIED로 남겨 주세요.

행별 출력에는 다음을 포함해 주세요.
- expected_trigger
- did_trigger
- true_positive / true_negative / false_positive / false_negative / not_verified
- 관찰 근거
- description 개선 후보

전체 precision과 recall을 계산하고, positive에는 TPR·FNR, negative에는 TNR·FPR, boundary에는 accuracy를 따로 계산해 주세요. 같은 30개를 수정 전후로 다시 사용했다면 독립 성능 추정이 아니라 회귀 비교라고 명시해 주세요.

마지막에는 오분류 유형과 description 개선 후보만 제안하세요. expected_trigger를 바꾸거나 최종 판정을 대신하지 마세요.
```

## 실행 후 확인

- 입력 두 파일의 ID가 빠짐없이 대응하는가
- 분모가 0인 지표를 임의로 계산하지 않았는가
- `NOT_VERIFIED`를 통과나 실패로 간주하지 않았는가
- 개선 후보가 실제 오분류 근거와 연결되는가
