# LangChain·LangGraph 배송 문의 실습

같은 주문 자료로 값 전달·도구 실행·요청 보관을 비교하고, 두 배송 구성에 실제 모델과 번호 정정을 연결합니다. 개념과 Day별 진행은 [주차 README](../README.md)에 있습니다.

## 폴더와 주요 파일

| 폴더 | 역할 | 주요 파일 |
|---|---|---|
| `examples/` | IDE에서 실행할 작은 사례 | `run.py`, `state_updates.py`, `tool_loop.py`, `waiting_compare.py`, `workflow_demo.py` |
| `shipping/` | 배송 업무·상태·모델 연결 | `agent.py`, `graph.py`, `components.py`, `support.py`, `scenarios.py`, `model_boundary.py` |
| `comparisons/` | 기초 비교의 상태와 연결 부품 | `business.py`, `flows.py`, `waiting_graph.py` |
| `tests/` | 코드 변경 시 사용할 기존 검사 | 고정 조회·상태 갱신·도구 연결·대기·복구·정정 검사 |

`shipping/agent.py`는 도구·업무 상태·middleware와 `ShippingAgent`, `shipping/graph.py`는 상태·노드·간선과 `ShippingGraph`를 함께 둡니다. 실행 파일은 이 구현을 호출합니다. `shipping/components.py`의 주문 자료와 조회 함수는 두 구성과 기초 비교에서 공유합니다.

## IDE에서 실행하기

이 `framework_compare` 폴더의 `.venv`를 Python 인터프리터로 선택하고 `examples/`에서 원하는 파일을 열어 **Run Python File in Terminal**로 실행합니다. `examples/_bootstrap.py`는 직접 파일 실행 시 같은 프로젝트의 패키지를 찾도록 연결합니다. 가상환경은 `examples/` 안으로 옮기지 않습니다.

처음 의존성을 준비할 때만 터미널에서 다음 명령을 사용합니다.

| Windows PowerShell | macOS·Linux·WSL |
|---|---|
| `python -m venv .venv` | `python3 -m venv .venv` |
| `.\.venv\Scripts\python.exe -m pip install -r requirements.txt` | `.venv/bin/python -m pip install -r requirements.txt` |

## 실행할 사례

| 파일 | 입력과 확인할 동작 |
|---|---|
| `examples/run.py` | 같은 고정 조회를 Direct·Runnable·LangGraph로 실행. `--order-id=`는 누락, `--order-id O-999`는 부재 |
| `examples/state_updates.py` | 부분 반환과 명시적 병합에서 문의가 생성 단계까지 남는지 비교 |
| `examples/tool_loop.py` | 직접 루프와 LangChain의 도구 호출·결과 연결. `--method langchain --issue "O-100 배송 문의"`로 한 입력 지정 |
| `examples/waiting_compare.py` | Direct 세션과 LangGraph 체크포인트에서 두 문의를 역순 보충 |
| `examples/workflow_demo.py` | `shipping/`의 두 구성에 선택한 배송 사례 적용 |

명령으로 실행할 때도 작업 폴더는 `framework_compare`입니다.

| Windows PowerShell | macOS·Linux·WSL |
|---|---|
| `.\.venv\Scripts\python.exe -B examples/run.py` | `.venv/bin/python -B examples/run.py` |
| `.\.venv\Scripts\python.exe -B examples/workflow_demo.py --method langchain --case lookup` | `.venv/bin/python -B examples/workflow_demo.py --method langchain --case lookup` |
| `.\.venv\Scripts\python.exe -B examples/workflow_demo.py --method langgraph --case waiting` | `.venv/bin/python -B examples/workflow_demo.py --method langgraph --case waiting` |

`workflow_demo.py`의 `--method`는 `langchain`, `langgraph`, `both`입니다. `--case`는 다음 중 필요한 사례 하나를 선택합니다.

| 사례 | 입력·관찰 |
|---|---|
| `lookup` | O-100·O-200의 조회 사실과 답변 |
| `missing` | O-999의 주문 부재 |
| `partial` | O-100·O-999의 부분 안내 |
| `waiting` | 번호 없는 A·B 문의에 B부터 번호 보충 |
| `empty` | 빈 보충 두 번 뒤 종료 |
| `generation-failure` | 조회 뒤 모의 생성 실패와 저장된 사실로 재시도 |
| `correction` | O-100 → O-200 → O-999 정정과 이전 근거 제거 |

`run.py`와 `waiting_compare.py`는 고정 안내, `state_updates.py`는 고정 생성 단계의 입력, `tool_loop.py`와 배송 시연의 기본값은 모의 모델을 사용합니다. 실제 프레임워크와 조회 함수가 실행되며 모델의 자연어 판단은 모의 결과에 포함되지 않습니다.

## 실제 모델과 검사

실제 모델은 `shipping/model_boundary.py`의 `live_model()`에서 만들고 기존 배송 구성에 전달합니다. API 구성은 주차 README의 Day 4와 [6주차 모델 연결 안내](../../week06-llm-api-tool-calling/README.md)를 따릅니다. `examples/workflow_demo.py --method langchain --case lookup --live`처럼 한 구성·사례를 선택할 수 있습니다. 생성 실패 사례는 모의 모델 전용입니다.

환경변수 파일은 학습자만 열어 값을 입력합니다. AI는 해당 파일을 직접 또는 프로그램 실행으로 읽지 않으며, 검증에서는 파일 로더와 실제 모델을 가짜 설정·모의 응답으로 대체합니다.

코드를 바꿨을 때 기존 검사는 IDE의 테스트 기능으로 실행합니다. 터미널에서 검사할 경우 Windows는 `.\.venv\Scripts\python.exe -B -m unittest discover -s tests`, macOS·Linux·WSL은 `.venv/bin/python -B -m unittest discover -s tests`를 사용합니다.

## 이 학습 구현의 실행 파일

| 활동 | 실행 파일 | 연결되는 구현 |
|---|---|---|
| Day 2 복수 주문·번호 보충 | `examples/day2_agent.py`, `examples/day2_supplement.py` | `shipping/agent.py` |
| Day 3 조회·대기·생성 재시도 | `examples/day3_graph.py`, `examples/day3_supplement.py`, `examples/day3_retry.py` | `shipping/graph.py` |
| Day 4 실제 모델의 부분 부재 | `examples/day4_langchain.py`, `examples/day4_langgraph.py` | `shipping/live.py`에서 각 배송 구성 선택 |
| Day 5 실제 모델의 번호 정정 | `examples/day5_langchain.py`, `examples/day5_langgraph.py` | 같은 배송 구성의 `correct` 호출 |

Gemini 모델은 `shipping/model_boundary.py`에서 연결합니다. 모델 설정 파일은 저장소 루트의 `.local/week07-gemini.env`이며 학습자가 직접 입력합니다. 필요한 연결 의존성은 `requirements-gemini.txt`에 있습니다. `shipping/scenarios.py`는 공통 입력 순서를 제공하며 실행 결과를 저장하는 파일은 아닙니다.
