# Meeting Review Tool Plugin

회의 추출 결과를 제공 업무 규칙으로 검사하고 Dify Workflow에 `status`·`summary`를 반환하는 실제 Tool Plugin 참고 구현입니다. 이 출력 형태와 메모 전체를 검토로 보내는 정책은 하나의 설계 선택입니다. 상세 실습은 [주차 README](../README.md), 이 선택을 실행하는 노드와 변수는 [구성표](../workflow_materials/workflow-setup.md)에 있습니다.

IDE에서 Python 3.12 가상환경을 선택하고 `requirements.txt`를 설치합니다. 명령이 필요하면 이 폴더에서 실행합니다.

Windows PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -B -m unittest -v
```

macOS·Linux·WSL:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -B -m unittest -v
```

제공 검사는 SDK의 manifest·provider·tool 로딩과 실제 Tool 메시지, 핵심 업무 입력을 확인합니다. Dify 접속·실제 모델 추출을 확인하는 검사는 아닙니다. 제작·수정한 경계가 필요할 때 실행하고 매 입력마다 반복하지 않습니다.

| 파일 | 책임 |
|---|---|
| `manifest.yaml` | Plugin 이름·실행환경·공개할 provider |
| `provider/meeting.yaml`, `.py` | 제공자 등록·Tool 목록, 외부 서비스 자격 증명을 요구하지 않는 제공자 |
| `tools/review_tasks.yaml` | 입력 두 개·출력 변수 이름과 타입 |
| `tools/review_tasks.py` | Dify 입력을 업무 함수에 넘기고 SDK 메시지로 결과 반환 |
| `business.py` | 항목별 검사 `check_tasks`와 메모 전체 결과를 구성하는 참고 함수 `review`. 의미를 다시 추론하지 않음 |
| `main.py` | SDK Plugin 실행 진입점 |
| `../workflow_materials/` | 합성 메모·예상 추출·LLM 지침·노드 구성표 |

설계 실습에서는 제공 메모에서 필요한 판단과 다음 행동을 먼저 도출합니다. 원문·추출 결과를 Plugin에 얼마나 전달할지, 항목별 결과를 어떤 단위로 묶을지, 분기에서 어떤 값을 읽을지 선택하고 AI와 구현합니다. SDK가 요구하는 manifest·등록·메시지 규약은 따르되 `memo`·`extraction_json`·`status`라는 업무 필드명 자체가 SDK의 고정 규약인 것은 아닙니다. `check_tasks`를 사용하면 업무 검사를 다시 만들지 않고도 선택한 입출력·전체 처리 정책을 구성할 수 있습니다.

실제 연결은 Dify의 Plugin 개발 화면에서 원격 디버그 주소와 키를 받고, `.env.example`을 `.env`로 복사해 해당 값으로 바꾼 뒤 선택한 가상환경에서 `python -B -m main`을 실행합니다. Windows에서는 `.\.venv\Scripts\python.exe`, macOS·Linux·WSL에서는 `.venv/bin/python`을 사용합니다. 직접 만든 Plugin에서는 그 폴더를 작업 위치로 지정합니다. 디버그 연결은 현재 Dify 작업공간에서 Plugin을 사용할 수 있게 합니다. Plugin이 나타나면 Workflow Tool 노드에 연결하고 Test Run으로 실제 입력·출력을 봅니다. [공식 디버그 절차](https://docs.dify.ai/en/develop-plugin/dev-guides-and-walkthroughs/tool-plugin#debug-the-plugin)

`.env`와 가상환경은 Git·패키지에서 제외합니다. 이 예제의 업무 함수는 외부 API·모델·저장소를 호출하지 않으므로 manifest의 추가 역호출 권한은 비워 두었습니다. Dify의 LLM 노드는 별도로 모델을 사용합니다.

실제 실행을 마친 뒤 패키지 파일이 필요하면 [공식 CLI](https://docs.dify.ai/en/develop-plugin/getting-started/cli)를 설치하고 **주차 폴더**에서 `dify plugin package ./meeting_plugin`을 실행합니다. PowerShell에서 PATH에 등록하지 않은 CLI는 `& <CLI 실행파일> plugin package ./meeting_plugin`으로 실행할 수 있습니다. 패키지 생성과 Dify 설치·Workflow 실행은 서로 다른 확인입니다.
