# 주문 상담 앱 실행 자료

학습 흐름과 요청문은 상위 주차 README의 Day 1~5에 있습니다. 업무 함수·SDK를 재사용해 선택한 구조로 한 앱을 완성합니다.

| 파일 | 역할 |
|---|---|
| `consultation.py` | 접수·사실 조회·검색 에이전트·근거 검사·한 번의 보완·업무 결과 |
| `consultation_demo.py`, `app.py` | 고정 응답 예와 IDE 실제 대화 진입점 |
| `service.py` | 같은 앱의 로컬 HTTP API와 작은 입력 화면 |
| `business.py`, `data/` | 가상 주문·정책 원문과 실제 임베딩 검색 |
| `agent.py` | 모델의 도구 선택을 중심에 둔 단일 에이전트 비교 구현 |
| `flow_patterns.py`, `pipeline_example.py` | 고정 순서·분기·독립 조회·보완 구조의 비교 예 |
| `compare_answers.py` | 같은 입력에서 전달할 정책 본문 범위·추가 호출 또는 프롬프트 비교 |
| `model_boundary.py`, `offline.py`, `tests/` | 모델 호출 코드·테스트용 응답·단계별 전달과 실패 검사 |

Python 3.11 이상 가상환경을 IDE에서 선택합니다. 의존성을 한 번에 준비하는 터미널 명령은 PowerShell과 macOS·Linux·WSL 공통으로 `python -m pip install -r requirements.txt`입니다.

IDE에서 `app.py`를 기본 실행하면 고정 응답으로 제공 취소 문의를 처리합니다. 실제 연결은 인수 `--live`와 비공유 설정 `AI_AX_LIVE=1`, `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_EMBEDDING_MODEL`을 사용합니다. 실제 API 호출은 학습자가 IDE에서 실행합니다. AI는 실제 키와 설정 파일을 읽지 않고 가짜 설정·모델로 확인합니다. 다른 제공자를 쓰려면 `model_boundary.py`에서 모델·임베딩 객체를 만드는 부분을 해당 라이브러리로 바꿉니다.

`service.py`의 기본 실행은 화면에 있는 제공 문의만 처리하는 고정 응답 모드입니다. 인수 `--live --port 0`은 실제 대화 모드에서 사용 가능한 로컬 포트를 선택합니다. 출력된 주소를 브라우저에서 엽니다. 고정 포트가 필요하면 `--port 8765`처럼 지정합니다. 대화와 색인은 메모리에서 유지되며 프로세스를 종료하면 초기화됩니다.

API는 `POST /consultations`로 `conversation_id`와 `request` 두 문자열을 받습니다. 응답의 `items`에는 주문별 사실·근거·안내·질문·상태가 있고 `actions`에는 표시·질문·검토 동작이 있습니다. `ready`는 지정한 출력·근거 조건을 통과했다는 뜻이며 답변의 의미는 원문으로 확인합니다. `partial`과 `limit_reached`에서도 확인된 사실과 남은 처리를 읽습니다. HTTP 상태와 업무 상태를 구분합니다.

`PolicyEvidence`는 검색된 정책의 첫 문장을 발췌로 전달합니다. 신청 경로가 빠진 초안을 검사하면 같은 출처의 전체 원문을 확장해 한 번 다시 생성·검사합니다. 짧은 정책 전체를 처음부터 전달하는 대안은 `initial_context="full"`입니다. 긴 자료에서는 전체 문서 대신 필요한 절과 예외 조건을 함께 가져올 수 있습니다.

`compare_answers.py`에 `--structure`를 지정하면 발췌만·한 번의 보완·처음부터 전체 본문 전달을 준비된 모델 응답으로 비교합니다. `--structure --live`는 같은 세 구성에 실제 모델을 연결합니다. `--live`만 지정하면 단일 에이전트의 두 프롬프트 비교입니다. 직접 선택한 변경은 자기 진입점에서 같은 입력과 원문으로 비교해도 됩니다.

실제 모델 입력과 대조 기준은 `data/consultation_cases.json`에 있습니다. 최초 근거·피드백·확장 원문·최종 답변을 이어 읽습니다. 검사는 IDE 테스트 실행기 또는 자동화 명령 `python -B -m unittest discover -s tests`를 사용합니다. 모델·임베딩·설정은 실제 API 대신 준비된 값을 반환하는 테스트용 구현을 사용하며 HTTP 검사는 명시적인 로컬 임시 포트를 사용합니다. 실제 모델의 도구 선택과 답변 품질은 학습자가 실행한 결과로 확인합니다.
