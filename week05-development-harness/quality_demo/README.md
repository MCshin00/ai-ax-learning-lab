# 환불 계산 작업 환경

실제 결제 서비스와 연결하지 않는 작은 Python 프로젝트입니다. 개인 프로젝트나 API 키 없이, 제공된 변경 요구를 AI에게 맡기고 작업 지침·검사·실패 피드백을 조정할 수 있습니다. Day별 개념과 진행 순서는 [주차 README](../README.md)에 있습니다.

## 시작 상태와 실행

작업 폴더는 `week05-development-harness/quality_demo/`입니다. Codex에서 이 폴더를 작업 위치로 열고, 먼저 `AGENTS.md`와 현재 요구를 읽게 합니다. Python 표준 라이브러리만 사용하므로 패키지를 설치할 필요는 없습니다. IDE의 테스트 실행 기능을 사용해도 됩니다.

Windows PowerShell:

```powershell
python -X utf8 -B refund.py
python -X utf8 -B -m unittest discover -s tests -v
```

macOS·Linux·WSL:

```bash
python3 -X utf8 -B refund.py
python3 -X utf8 -B -m unittest discover -s tests -v
```

`refund_amount(paid, fee)`는 결제액에서 수수료를 빼고, 결과가 음수이면 0을 반환합니다. 결제액이나 수수료가 음수이면 `ValueError`가 발생합니다. 금액 단위는 정수 원이며 첫 실행의 출력은 `8000`, 기존 테스트는 세 개 모두 통과해야 합니다.

**아래 요구 A·B는 앞으로 구현할 내용입니다.** 시작 코드에는 수수료 면제·엄격한 타입 검사·일괄 처리 기능이 없습니다. `data/`의 JSON도 아직 함수에 연결되지 않은 업무 예시와 기대 결과입니다. 기존 테스트 통과는 시작 동작만 확인하며, 아래 요구의 완료를 뜻하지 않습니다.

## 요구 A: 수수료 면제 조건 추가

고객 지원 담당자가 승인한 환불에는 수수료를 면제할 수 있어야 합니다. 기존 함수를 다음 인터페이스로 확장합니다.

```python
refund_amount(paid, fee, *, fee_waived=False)
```

- 기존 두 인자 호출은 그대로 사용할 수 있어야 합니다. 면제를 지정하지 않으면 기존 계산을 유지합니다.
- `paid`와 `fee`는 0 이상의 정수만 허용합니다. 문자열·실수·불리언은 거부합니다. Python에서 `bool`은 `int`의 하위 타입이므로 따로 구별해야 합니다.
- 금액이 잘못되면 `ValueError("금액은 0 이상의 정수여야 합니다.")`를 발생시킵니다.
- `fee_waived`에는 불리언 `True` 또는 `False`만 허용합니다. 다른 타입이면 `ValueError("fee_waived는 불리언이어야 합니다.")`를 발생시킵니다.
- 금액과 면제 여부를 검사한 뒤 계산합니다. `fee_waived=True`여도 음수 수수료를 유효한 입력으로 바꾸지 않습니다. 금액과 면제 여부가 둘 다 잘못되었다면 금액 오류를 먼저 알립니다.
- 면제이면 `paid`를, 면제가 아니면 `max(0, paid - fee)`를 반환합니다.

`data/refund-cases.json`의 각 행은 `id`, `paid`, `fee`, `fee_waived`, `expected`로 구성됩니다. `expected`가 정수이면 반환값이고, `{ "error": "..." }`이면 `ValueError`의 기대 메시지입니다. 예를 들어 다음 네 가지는 새 요구의 핵심 경계입니다.

| 입력 | 기대 결과 | 확인하는 이유 |
|---|---|---|
| `10000, 2000, fee_waived=False` | `8000` | 기존 계산 유지 |
| `10000, 2000, fee_waived=True` | `10000` | 수수료 면제 |
| `1000, 2000, fee_waived=False` | `0` | 음수 환불 방지 |
| `10000, -1, fee_waived=True` | 금액 오류 | 면제로 잘못된 입력을 숨기지 않음 |

파일에는 음수 결제액·문자열·불리언 금액·잘못된 면제 타입 사례도 있습니다. AI와 이 예시를 테스트로 옮기고, 기존 테스트와 새 테스트를 함께 실행합니다. 예시는 구현 결과에서 만든 정답이 아니라 업무 요구에 따른 기대값입니다.

## 요구 B: 여러 환불 요청 처리

요구 A를 완료한 뒤, 고객 지원 담당자가 JSON 파일 하나로 여러 환불 요청을 처리하도록 확장합니다. 이 요구를 시작하기 전에는 미리 구현하지 않습니다.

`batch_refund.py`를 만들고 파일 경로를 인자 하나로 받게 합니다. `data/batch-requests.json`은 입력 배열이며 각 행에 `id`, `paid`, `fee`, `fee_waived`가 있습니다. 모든 행은 객체이고 필드와 식별자가 제공된다고 가정합니다. 필드 누락·중복 식별자·외부 결제 연동은 이번 요구의 범위가 아닙니다.

- 요구 A의 함수를 재사용하며, 입력 순서와 `id`를 유지합니다.
- 성공한 행은 `{ "id": "regular", "status": "ok", "refund": 8000 }` 형식으로 반환합니다.
- 잘못된 행은 `{ "id": "negative-paid", "status": "error", "error": "금액은 0 이상의 정수여야 합니다." }` 형식으로 반환합니다. 잘못된 면제 타입의 메시지도 요구 A와 같습니다.
- 한 행이 잘못되어도 다음 행을 처리합니다. 정상 행과 오류 행이 섞여 있어도 파일을 끝까지 처리했으면 종료 코드는 0입니다.
- 전체 결과 배열만 **한 줄의 JSON으로 표준 출력**에 씁니다. 안내 문구를 JSON에 섞지 않습니다.
- 파일을 읽을 수 없거나 JSON 문법이 잘못되었거나 최상위 값이 배열이 아니면, 원인을 표준 오류에 쓰고 0이 아닌 코드로 종료합니다. 이때 성공 결과 배열을 출력하지 않습니다. 빈 배열은 정상 입력이며 결과도 `[]`입니다.

`data/batch-expected.json`은 제공 입력의 기대 출력입니다. 정상·면제·환불 하한·음수·문자열·불리언 금액·잘못된 면제 타입이 섞여 있습니다. JSON의 공백이나 객체 키 순서 대신 **파싱한 결과의 값과 배열 순서**를 비교합니다.

구현 후 Windows PowerShell:

```powershell
python -X utf8 -B batch_refund.py data/batch-requests.json
python -X utf8 -B -m unittest discover -s tests -v
```

구현 후 macOS·Linux·WSL:

```bash
python3 -X utf8 -B batch_refund.py data/batch-requests.json
python3 -X utf8 -B -m unittest discover -s tests -v
```

## Hook: 명령 결과를 다음 판단에 연결하기

`.codex/hooks.json`은 Codex의 명령 실행 뒤에 동작하는 `PostToolUse` 예제입니다. 실패 종료 코드가 있으면 사용자에게 짧은 경고를 표시하고, 모델에 원인 구분·수정 후 재검사·기대값 유지 지침을 전달합니다. 종료 코드가 없으면 성공으로 간주하지 않고 실제 결과와 실행 완료 여부를 확인하도록 알립니다.

**이 Hook은 검사를 직접 시작하거나 코드를 복구하거나 작업 종료를 강제하지 않습니다.** 종료 코드만으로 실패 원인을 판정하지도 않습니다. 다음 행동은 모델이 실제 출력과 프로젝트 지침을 읽고 결정하므로, 전달된 피드백과 실제 행동을 구분해 확인해야 합니다.

설정과 `.codex/hooks/post_tool_use_review.py`를 읽고, 사용하는 Codex 표면의 Hook 목록에서 검토·신뢰한 뒤 사용합니다. 변경한 Hook은 다시 검토가 필요할 수 있습니다. 제공 matcher는 `Bash`이며, 실제 명령 도구 이름이 다르면 관찰한 이름으로 맞춥니다. payload의 종료 코드 위치가 다르면 스크립트도 실제 형식에 맞춥니다. 작업 위치는 이 프로젝트 폴더를 유지합니다.

학습자가 터미널에서 직접 실행한 명령은 Codex의 도구 이벤트가 아니므로 Hook이 호출되지 않을 수 있습니다. Hook을 지원하지 않는 표면에서는 지침과 직접 검사를 활용하고, Hook 연결은 확인하지 못했다고 남깁니다.

Hook의 보조 테스트는 Windows에서 `python -X utf8 -B -m unittest discover -s .codex/hooks/tests -v`, macOS·Linux·WSL에서 `python3 -X utf8 -B -m unittest discover -s .codex/hooks/tests -v`로 실행합니다. 이는 이벤트 입력을 읽고 피드백 JSON을 만드는 코드의 검사이며, 실제 Codex 연결을 증명하지 않습니다.

이 Hook은 `../.local/raw/hook-events.jsonl`에 도구 이름·종료 코드·시각·턴 식별자만 기록합니다. 원시 명령과 출력은 저장하지 않으며 로그를 기능 검사의 대체물로 사용하지 않습니다. [공식 Hook 안내](https://learn.chatgpt.com/docs/hooks)

## 주요 파일

- `AGENTS.md`: 현재 요구를 확인하고 구현·검사·실패 대응·완료 보고를 수행하는 프로젝트 지침 초안.
- `refund.py`, `tests/test_refund.py`: 시작 상태의 환불 계산과 기존 동작 검사. 요구 A를 적용하며 확장할 대상.
- `data/refund-cases.json`: 요구 A의 입력과 기대 반환값·오류.
- `data/batch-requests.json`, `data/batch-expected.json`: 요구 B의 입력 파일과 기대 출력.
- `batch_refund.py`: 요구 B에서 학습자가 AI와 추가할 파일. 시작 자료에는 포함되어 있지 않음.
- `.codex/config.toml`, `.codex/hooks.json`: Hook 활성화와 이벤트 연결 설정.
- `.codex/hooks/post_tool_use_review.py`, `.codex/hooks/tests/`: 결과를 읽어 피드백을 만드는 코드와 그 보조 검사.
- `../failure-recovery.md`: 작업 환경의 조정 이유·실행 결과·실패 대응·재사용 확인을 누적하는 주차 노트.
