# 작업을 둘로 나누어 보는 티켓 표시 예제

이미 실행되는 Python 코드입니다. 생성된 `week04-multi-agent-worktrees/ticket_demo/`을 IDE에서 엽니다. 외부 패키지는 필요하지 않습니다. Day별 학습과 위임 요청은 [주차 README](../README.md)에 있습니다.

Windows PowerShell:

```powershell
python -B app.py
python -B -m unittest discover -s tests -v
```

macOS·Linux·WSL:

```bash
python3 -B app.py
python3 -B -m unittest discover -s tests -v
```

현재 출력은 `[P0] 로그인   오류`입니다. 이번 변경의 최종 출력은 `[긴급] 로그인 오류`입니다.

기본 두 변경은 다음과 같습니다. 주차 Day 1에서 선택한 추가 조건은 이 README에 함께 적고, 각 작업이 맡을 범위와 처리 순서를 Day 2에서 정합니다.

- 제목 작업: `titles.py`에서 앞뒤 공백과 연속 공백·탭·줄바꿈을 한 칸으로 정리합니다. `tests/test_titles.py`에 대표 사례를 추가합니다. 반환값은 문자열입니다.
- 우선순위 작업: `priorities.py`에 `P0 → 긴급`을 추가합니다. `tests/test_priorities.py`에 새 값과 기존 P1·알 수 없는 값 사례를 추가합니다. 반환값은 문자열입니다.
- 통합 작업: 두 변경을 합친 뒤 기존 테스트와 새 테스트를 실행하고 `app.py`가 정확히 `[긴급] 로그인 오류`를 출력하는지 확인합니다.

개별 Worktree 작업은 정한 담당 파일만 바꾸고, `app.py`와 다른 작업의 테스트를 수정하지 않습니다. 추가 조건에 공통 조립부 변경이 필요하다면 Day 4의 원본 작업에서 한 번 반영하고 통합 결과를 확인합니다.

`app.py`는 두 함수의 결과를 조립하고 `titles.py`·`priorities.py`는 표시할 문자열을 반환합니다. `tests/`는 기존 동작과 변경 요구를 확인합니다. 위임·통합 근거와 판단은 `../two-changes.md`에 이어 씁니다.
