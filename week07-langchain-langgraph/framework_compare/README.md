# 프레임워크의 목적과 책임을 비교하는 실행 예제

같은 제공 주문 자료로 고정 조회, 모델–도구 실행, 요청 보관·재개를 비교합니다. **답변이 같은지와 함께, 그 결과에 필요한 일을 직접 작성하는지 프레임워크에 맡기는지 확인합니다.** 개념·사례 해설·Day별 행동과 완료 기준은 [주차 README](../README.md)를 순서대로 읽습니다.

## 실행 준비

IDE에서 이 폴더를 열고 아래 명령으로 가상환경과 고정 버전의 의존성을 준비합니다. 이 설치에는 터미널을 사용해 실행 환경을 맞춥니다. 이미 환경이 준비돼 있으면 IDE의 Python 인터프리터로 `.venv`를 선택하고 파일의 실행 기능을 사용합니다.

Windows PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

macOS·Linux·WSL:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
```

## 실행 파일과 확인할 책임

| 실행 파일 | 확인할 것 | 기본 실행의 범위 |
|---|---|---|
| `run.py` | Direct의 분기, Runnable 연결, 그래프의 노드·간선 | 같은 고정 조회를 세 방식으로 실행 |
| `state_updates.py` | 반환값 전달과 상태 갱신, 명시적 병합 | 생성 단계와 최종 결과에 문의가 남는지 출력 |
| `tool_loop.py` | 직접 모델–도구 루프와 LangChain `create_agent` | 제공된 작은 문의 사례의 실제 도구 실행·결과 연결 |
| `waiting_compare.py` | 앱의 요청별 저장소와 LangGraph 체크포인트 | 두 요청을 대기시키고 역순으로 번호만 보충 |
| `clarification.py` | `interrupt`, 상태 갱신, 대기·종료 분기의 실제 그래프 | 번호 누락 요청 하나를 중단했다가 제공 번호로 재개 |

`business.py`와 `components.py`는 조회·안내 부품, `comparison.py`는 고정 흐름의 연결 코드입니다. `model_boundary.py`는 실제 모델 접점입니다. `exercise.py`는 선택한 구조를 구현할 때 사용할 수 있는 초기 스케치이며 상태·함수·진입점을 정답으로 고정하지 않습니다.

각 파일을 IDE에서 실행하거나, 반복 실행에 다음 명령을 사용합니다. 모든 명령의 실행 위치는 이 폴더입니다.

| Windows PowerShell | macOS·Linux·WSL |
|---|---|
| `.\.venv\Scripts\python.exe -B run.py` | `.venv/bin/python -B run.py` |
| `.\.venv\Scripts\python.exe -B state_updates.py` | `.venv/bin/python -B state_updates.py` |
| `.\.venv\Scripts\python.exe -B tool_loop.py` | `.venv/bin/python -B tool_loop.py` |
| `.\.venv\Scripts\python.exe -B waiting_compare.py` | `.venv/bin/python -B waiting_compare.py` |

`run.py --order-id=`는 번호 누락, `run.py --order-id O-999`는 주문 부재입니다. `tool_loop.py --method langchain --issue "O-100 배송 문의"`처럼 한 방식·입력을 지정할 수도 있습니다.

`FIXED_OFFLINE`은 고정 안내를 사용합니다. `SCRIPTED_MODEL`은 제공 번호로 정해진 도구 요청을 만드는 모의 모델이며, 실제 LangChain과 조회 도구는 실행합니다. 이 결과는 모델의 자연어 판단·도구 선택 능력을 확인한 것이 아닙니다. 보관 비교의 두 저장소는 모두 메모리 방식이며 프로세스 종료 후 복원을 제공하지 않습니다.

## 실제 모델 연결

IDE의 실행 환경에 `AI_AX_LIVE=1`, `OPENAI_API_KEY`, `OPENAI_MODEL`을 설정합니다. 값은 Git으로 공유하지 않는 로컬 환경에 둡니다. 실제 모델 호출은 과금됩니다. Day 4에서 선택한 흐름의 모델 역할에 맞는 실행을 사용합니다.

| 모델 역할 | Windows PowerShell | macOS·Linux·WSL |
|---|---|---|
| 자연어 문의의 도구 선택·응답 | `.\.venv\Scripts\python.exe -B tool_loop.py --method langchain --issue "O-100 배송 문의" --live` | `.venv/bin/python -B tool_loop.py --method langchain --issue "O-100 배송 문의" --live` |
| 대기·재개 뒤 안내 생성 | `.\.venv\Scripts\python.exe -B waiting_compare.py --method langgraph --live` | `.venv/bin/python -B waiting_compare.py --method langgraph --live` |

각 method를 `direct`로 바꾸면 직접 구현한 흐름을 사용합니다. 고정 조회의 `run.py --method direct --live`도 같은 모델 경계를 사용합니다. 도구 실행 예제는 실제 모델 실행 시 한 방식·입력을 지정하며, 대기 예제의 실제 모델 실행은 제공 문의 하나를 재개합니다. 자기 구현에서는 선택한 호출 계약에 이 모델 접점을 연결합니다.

`test_comparison.py`와 `test_framework_purposes.py`는 제작자가 예제의 업무 결과·정보 보존·도구 연결·대기 정책을 확인하는 검사입니다. 코드 변경에 따른 확인이 필요하면 IDE의 테스트 실행 기능을 사용합니다. 터미널에서는 Windows의 `.\.venv\Scripts\python.exe -B -m unittest -v`, macOS·Linux·WSL의 `.venv/bin/python -B -m unittest -v`를 사용합니다. 검사 성공이 실제 모델 연결이나 학습자의 이해를 대신하지는 않습니다.
