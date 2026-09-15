# 회의 후속 작업 검사 Plugin

담당자·기한·근거를 항목별로 검사하고, 하나라도 보완이 필요하면 메모 전체를 검토하도록 반환한다. 확정 항목과 검토 이유는 상세 결과에 함께 보존한다. 학습 해설과 실제 실행 결과는 [주차 노트](../dify-review.md), 단계별 안내는 [주차 README](../README.md)에 있다.

## 코드의 역할

- `business.py`: 제공 항목별 검사 `check_tasks`. 업무 규칙의 재사용 부품.
- `meeting_review.py`: 입력 형식 확인, 항목별 검사 호출, 전체 상태와 요약 구성.
- `tools/review_tasks.py`: 선택한 입출력을 SDK 메시지로 연결.
- `manifest.yaml` → `provider/meeting.yaml` → `tools/review_tasks.yaml`: 제공자·Tool·실행 코드 등록.
- `main.py`: 개발 연결 실행 진입점.
- `workflow_client.py`: Workflow 호출, 원문 재검사, 사람 확인과 로컬 초안 저장.
- `test_workflow_client.py`: 대역 응답으로 저장·보류·재전달 정책 확인.
- `test_plugin.py`: 가짜 SDK 설정으로 등록·메시지·제공 사례 확인. 실제 Cloud 연결 검사는 별도다.

Dify에 표시되는 제공자는 **Meeting Review Lab**, Tool은 **Review meeting tasks**다. 참고 예제의 제공자와 구분할 수 있도록 이름을 정했다.

## VS Code에서 준비·실행

1. 이 `meeting_tool` 폴더 자체를 VS Code에서 연다.
2. **Python: Select Interpreter**에서 이 폴더의 Python 3.12 `.venv`를 선택한다. 환경이 없다면 **Python: Create Environment → Venv → Python 3.12**로 만들고 `requirements.txt`를 설치한다.
3. `.env.example`을 `.env`로 복사하고 Dify Plugin 개발 화면의 주소·키를 직접 입력한다. `INSTALL_METHOD=remote`로 둔다. 모델 제공자 키와 개발 연결 키는 용도가 다르다.
4. 실행 및 디버그에서 **Dify Plugin 개발 연결**을 선택해 F5로 시작한다. 실행 구성은 `main` 모듈, 작업 위치는 이 폴더, 설정 파일은 `.env`다.
5. 연결 프로세스를 유지한 채 Dify Workflow에서 Tool을 호출한다.

가상환경·인증 설정과 `.vscode`는 Git·패키지에서 제외한다. 다른 환경에서는 VS Code의 Python 모듈 실행 구성에서 `main`과 이 폴더를 지정하면 된다.

## Workflow 연결

| Tool 입력 | 선행 노드 변수 |
|---|---|
| `memo` | 회의 입력 → memo |
| `extraction_json` | 추출 직렬화 → extraction_json |

Tool은 상세 JSON과 `status`, `summary`, `payload`를 반환한다. `status`는 분기, `summary`는 안내문 표시, 상세 결과를 담은 JSON 문자열 `payload`는 호출 앱의 후속 처리에 사용한다. 최종 Output은 `status`, `result`, `extraction`, `payload`를 반환하며 현재 안내문 위치는 `result.output`이다. 사람에게 보이는 문구와 프로그램이 읽는 결과를 나누므로 호출 앱이 안내문에서 담당자·기한을 다시 추론할 필요가 없다.

정상 입력 예: `민수가 금요일에 초안을 공유한다.`

예상 결과는 `status=ready`와 작업·담당자·기한이 포함된 요약이다. 로컬 검사 통과와 실제 Dify 호출 성공은 구분한다.

## 오프라인 검사

VS Code의 테스트 탐색기에서 unittest를 실행할 수 있다. 명령으로 실행할 때는 이 폴더에서 가상환경을 명시한다. 인증 입력을 읽지 않고 SDK 등록과 메시지 계약을 확인하기 위한 명령이다.

Windows PowerShell:

```powershell
.\.venv\Scripts\python.exe -B -m unittest -v test_plugin
```

macOS·Linux·WSL:

```bash
.venv/bin/python -B -m unittest -v test_plugin
```

## 사람 확인 후 초안 저장

`workflow_client.py`는 실제 API 결과의 `data.outputs.payload`를 읽고, 같은 `meeting_review.review`로 다시 검사한 뒤 원문과 작업을 표시한다. 전체가 `ready`이며 실제 확인 입력이 `save`일 때만 저장한다. `test_workflow_client.py`는 대역 응답과 메모리 DB로 제공 저장 정책을 검사한다.

분기별 Template 결과를 Variable Aggregator로 모아 최종 Output 하나에 연결한다. 최종 Output에 `payload=업무 검사.payload`를 문자열로 추가하고 Workflow를 게시한다. VS Code의 비공유 `.env`에 `DIFY_API_URL`과 `DIFY_API_KEY`를 직접 추가한다. API 주소는 앱에서 확인한 기본 주소이며 호출자가 `/workflows/run`을 붙인다. 기존 Plugin 개발 연결 설정은 함께 유지한다.

HTTP 요청은 `User-Agent: MeetingReviewLab/1.0`으로 호출 프로그램의 이름·버전을 명시한다. `Content-Type: application/json`은 요청 본문의 형식, `Accept: application/json`은 받을 응답의 형식을 나타낸다. 이 값들은 `workflow_client.py`의 요청 헤더에 있으며 Dify 화면의 앱 이름이나 API 키와는 별개다.

Plugin 개발 연결을 실행한 채 VS Code 실행 목록에서 **회의 초안 저장 - 실제 Dify**를 선택하고 추가로 실행한다. 원문과 초안이 나타나면 내용을 확인한 후 저장할 경우에만 터미널에 `save`를 입력한다. 이 입력은 Plugin 터미널이 아니라 호출 프로그램의 터미널에 한다.

실제 실행은 정상·누락·부분 보완 메모를 두 차례 호출한다. 두 번째 호출도 모델을 다시 호출하므로 같은 원문이라도 작업 제목이나 근거 표현이 달라지면 `revision_required`가 나올 수 있다. `already_saved`는 같은 ID와 같은 저장 내용일 때의 결과다. 기존 초안을 자동으로 덮어쓰지 않는다.

저장 위치는 이 폴더의 `.local/meeting-drafts.sqlite`다. 다른 서비스에 작업을 등록한 결과가 아니라 로컬 회의 초안이다. API 실패·잘못된 응답·검토 보류·사람 확인 대기·저장 오류는 구분된다. 독립된 메모는 앞 메모의 호출 실패 후에도 계속 처리한다.
