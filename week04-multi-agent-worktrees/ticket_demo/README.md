# 작업을 둘로 나누어 보는 티켓 표시 예제

제목과 우선순위를 받아 티켓 한 줄을 표시하는 Python 프로그램입니다. `week04-multi-agent-worktrees/ticket_demo/`를 IDE에서 엽니다. 외부 패키지는 필요하지 않습니다. Day별 학습 본문은 [주차 README](../README.md), 실행 결과와 판단은 [two-changes.md](../two-changes.md)에 있습니다.

## 실행

IDE에서 `app.py`를 실행하거나 아래 명령을 사용합니다.

Windows PowerShell:

```powershell
python -X utf8 -B app.py
python -X utf8 -B -m unittest discover -s tests -v
```

macOS·Linux·WSL:

```bash
python3 -B app.py
python3 -B -m unittest discover -s tests -v
```

앱의 기본 입력은 `"  로그인   오류  ", "P0"`이며 출력은 `[긴급] 로그인 오류`입니다.

## 현재 요구와 담당

- `titles.py`의 `normalize_title`은 앞뒤·연속 공백·탭·줄바꿈을 정리하고, 공백뿐인 제목에는 `(제목 없음)`을 반환합니다. 제목 함수의 입력별 동작은 `tests/test_titles.py`에서 확인합니다.
- `priorities.py`의 `priority_label`은 P0·P1·P2·P3를 각각 `긴급`·`높음`·`보통`·`낮음`으로 표시하고 미등록 값은 그대로 반환합니다. 우선순위 함수의 동작은 `tests/test_priorities.py`에서 확인합니다.
- `app.py`의 `render_ticket`은 두 함수를 호출하고 대괄호를 붙여 최종 표시를 만듭니다. 원래 제목이 공백뿐이고 우선순위가 P0이면 표시할 제목을 `담당자 확인 필요`로 바꿉니다. 최종 출력은 `tests/test_ticket.py`에서 확인합니다.

| 제목 입력 | 우선순위 | 기대 출력 |
|---|---|---|
| `"  로그인   오류  "` | `P0` | `[긴급] 로그인 오류` |
| `"   "` 또는 `" \t\n "` | `P0` | `[긴급] 담당자 확인 필요` |
| `"   "` | `P1` | `[높음] (제목 없음)` |
| `"요청"` | `CUSTOM` | `[CUSTOM] 요청` |
| `"(제목 없음)"` | `P0` | `[긴급] (제목 없음)` |

두 보조 함수는 표시할 문자열을 반환하고, 대괄호는 `render_ticket`에서 붙입니다. 제목의 누락 여부는 `not title.strip()`으로 원래 입력에서 확인합니다. `normalize_title`의 반환값만 보면 빈 제목과 실제 제목 `"(제목 없음)"`을 구분할 수 없기 때문입니다.

## 구현 순서와 후속 요구

기본 제목 정리와 우선순위 표시는 같은 시작 커밋의 두 Worktree에서 각각 구현한 뒤 통합했습니다. 당시 선택한 공백 제목의 기대 출력은 `[긴급] (제목 없음)`이었으며, 제목 함수 안에서 처리할 수 있어 두 변경을 독립적으로 진행했습니다.

후속 요구는 제목 누락과 P0를 함께 판단해야 합니다. 처리 위치는 두 입력을 모두 받는 `render_ticket`으로 정했습니다. 공통 변경은 기존 통합 코드를 다루는 한 작업에서 `app.py`와 `tests/test_ticket.py`를 함께 담당합니다. 처리 위치와 기존 P1의 기대값을 정한 뒤 구현하고, 최종 출력과 전체 테스트를 확인하는 순서입니다.
