# Direct·LangChain·LangGraph 비교 실행

같은 주문 입력·조회 함수·생성 함수를 세 연결 방식으로 실행합니다. Day별 해설과 제작 요청은 [주차 README](../README.md)에 있습니다. 실제 LangChain의 Runnable과 LangGraph의 StateGraph를 사용하며 모델 호출 경계만 고정 답변으로 바꿀 수 있습니다.

IDE에서 Python 3.12 가상환경을 지정하고 `requirements.txt`를 설치한 뒤 `run.py`를 실행합니다. Python은 이 비교 구현에 사용합니다. 기존 Java 실습은 유지하며 `../framework_lab/`의 LangChain4j는 별도의 Java 참고 예제입니다.

Windows PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -B run.py
.\.venv\Scripts\python.exe -B run.py --method langgraph --order-id O-999
```

macOS·Linux·WSL:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -B run.py
.venv/bin/python -B run.py --method langgraph --order-id O-999
```

`--order-id ''`로 번호 누락을 실행합니다. 기본 실행은 `FIXED_OFFLINE`이며 실제 프레임워크 연결·업무 함수를 확인합니다. 모델은 호출하지 않습니다. 실제 모델 실행은 환경에 `AI_AX_LIVE=1`, `OPENAI_API_KEY`, `OPENAI_MODEL`을 설정한 뒤 `run.py --method langgraph --live`로 진행합니다. 같은 명령에서 다른 method를 고르면 같은 모델 경계를 사용합니다. 모델 호출은 과금되며, `all --live`는 세 번 호출합니다. 고정 답변의 같은 결과는 모델 답변 품질의 우열을 뜻하지 않습니다.

| 파일 | 역할 |
|---|---|
| `business.py` | 주문 자료, 입력 초기화, 조회, 질문·실패 안내와 생성 함수 경계 |
| `comparison.py` | Direct 분기, LangChain Runnable 연결, LangGraph 상태·간선 정의 |
| `model_boundary.py` | 세 방식에 공통인 실제 모델 요청 |
| `components.py` | 학습자의 상태 형태를 정하지 않는 조회·생성 부품 |
| `clarification.py` | 질문 후 같은 요청에서 대기·재개할 때의 실제 SDK 비교 예제 |
| `exercise.py` | 선택 가능한 초기 스케치. 함수 이름·상태·진입점을 바꿀 수 있음 |
| `test_comparison.py` | 제작자가 유지하는 동일 계약·정정·모델 오류 검사 |

설계 실습에서는 제공 요구에서 입력·결과·상태의 수명·실패의 다음 행동을 먼저 도출합니다. `business.State`와 `build_flow(draft)`는 비교 예제의 선택이며 학습자의 고정 계약이 아닙니다. `components.py`의 조회·생성 부품을 사용해 선택한 구조와 실행 진입점을 AI와 만듭니다. 완성된 비교 함수를 그대로 호출한 것만으로 제작을 마치지 않습니다. 후보 구조의 판단 근거는 주차 가이드의 완성된 설계 해설에서 읽습니다.

`python -B clarification.py`는 번호가 없는 같은 요청을 중단했다가 제공 번호로 재개하는 SDK 예제입니다. 기본 세 방식 비교와 요구가 달라졌으므로 실행 시간의 우열 비교에 섞지 않습니다. `InMemorySaver`는 같은 프로세스 안의 대기 상태만 보존합니다. 체크포인트·질문 재개를 모든 설계에 추가하는 과제는 아닙니다.

필요한 코드 변경 후 기존 검사를 실행하려면 선택한 가상환경에서 `python -B -m unittest -v`를 사용합니다. 매 입력마다 전체 검사를 반복할 필요는 없습니다. 실행 결과와 선택 이유는 `../framework-note.md` 한 곳에 남깁니다.
