# IT 문의 처리 앱

직원의 자유로운 문의를 서비스 상태·운영 문서와 연결해 담당자가 검토할 작업 요청으로 만드는 프로젝트다. 이 문서는 지금의 구조를 설명하며, 구조를 바꾸는 변경과 같은 변경 안에서 고친다. 결정과 이유는 [docs/adr](adr/)에, 작업 한 건의 기록은 그 작업의 풀 리퀘스트 본문에 있다.

## 업무 요구

1. 직원은 자연어로 문의하고 빠진 정보를 이어서 알려 줄 수 있다.
2. 현재 서비스 상태와 관련 운영 문서를 함께 보여 주고 대응 초안을 만든다.
3. 여러 서비스 중 일부만 확인되어도 확인된 내용은 전달한다.
4. 담당자가 초안을 검토·편집한 뒤 작업 요청으로 저장하고, 운영팀의 별도 도구에서 읽을 수 있게 한다.
5. 검토 중 대상이나 서비스 상태가 바뀌면 새 사실로 다시 검토한다. 저장 응답을 놓치고 재시도해도 같은 요청이 중복 생성되지 않아야 한다.
6. 담당자 화면이 같은 분석과 저장을 쓸 수 있어야 하고, 처리 중임을 담당자가 알 수 있어야 한다.

## 처리 순서

접수(모델) → 상태 조회(코드가 MCP 호출) → 운영 문서 검색 → 초안(모델) → 출처 검사(코드) → 검토·저장(담당자 확인 뒤 코드가 MCP 호출). 이유는 [0001](adr/0001-processing-roles.md)에 있다.

## 지금 구현된 것

| 구간 | 상태 |
|---|---|
| 상태 조회 | 별도 stdio MCP 서버가 `get_service_status`를 공개한다. 요청 ID와 같은 첫 행에서 읽은 값을 전달하며, 앱 클라이언트와 운영 도구가 같은 서버를 호출한다. 클라이언트는 서버의 원인 값과 문장을 그대로 전달한다 |
| 접수 | OpenAI Java SDK의 엄격한 JSON Schema로 서비스·증상·오류를 받는다. 대화별 이전 결과를 이어 보내고, 서비스가 없으면 질문하며 있으면 주입한 조회 함수로 모두 조회한다 |
| 검색, 초안, 출처 검사, 검토·저장, HTTP | 없음 |

## 제공 자료

| 파일 | 역할 |
|---|---|
| `data/services.json` | VPN·SSO·MAIL의 상태·설명·변경 번호 `revision`. 서비스 상태 화면을 대신한다 |
| `data/runbooks.json` | 서비스별 운영 문서의 ID·제목·본문 |
| `data/cases.json` | 확인할 문의와 판단 기준. 앱이 읽지 않고, 검사의 입력과 기대 결과를 정할 때 쓴다 |

## 서비스 상태 조회

`src/main/java/lab/inquiry/status/`는 상태 조회의 구현이다.

| 파일 | 맡은 일 |
|---|---|
| `ServiceCatalog.java` | 인수를 먼저 확인하고 매번 `services.json`에서 요청 ID와 같은 첫 행을 읽는다. 조회 결과나 읽지 못한 원인을 돌려준다 |
| `LookupResult.java` | 서비스별 결과와 문자열 원인 값. 앱이 실패를 만들 때 쓰는 원인 상수도 둔다 |
| `StatusWire.java` | 도구 선언, 공개 JSON의 필드·순서와 MCP 내용 변환, 구조화된 응답 해석. 텍스트 내용은 해석하지 않는다 |
| `StatusServerMain.java` | `get_service_status` 도구 하나를 별도 프로세스의 표준 입출력으로 공개한다 |
| `StatusClient.java` | 서버 시작·목록·순차 조회·종료. 응답 제한 시간은 10초다. 응답 읽기는 `StatusWire.decode`에 맡기며 통신 실패는 `MCP_UNAVAILABLE`로 돌려준다 |
| `OperationsMain.java` | 담당자가 도구 목록이나 서비스별 결과를 JSON 한 줄로 확인하는 진입점 |

응답 형식은 `src/main/resources/lab/inquiry/status/status-output-schema.json`에 있다. 자료와 응답을 읽는 규칙, 읽지 못했을 때의 결과는 [0006](adr/0006-status-lookup-inputs.md)과 [0011](adr/0011-each-app-owns-its-own-responsibility.md)에 정리한다.

IDE에서 `lab.inquiry.status.OperationsMain`을 실행하고 작업 폴더를 프로젝트 루트로 둔다. 인수가 없으면 도구 목록, `get VPN`이면 VPN 조회 결과를 출력한다. 다른 자료 폴더는 `--data-dir <자료 폴더> get VPN`으로 지정한다. `FOUND`·`NOT_FOUND`의 종료 코드는 0, 조회·입력 오류는 1이다. 실행 인수의 형태가 틀리면 표준 오류에 사용법을 쓰고 2로 끝난다. 빈 서비스 ID는 서버에 넘겨 `INVALID_INPUT`을 받는다.

서버를 직접 연결할 때의 진입점은 `lab.inquiry.status.StatusServerMain`, 인수는 `[자료 폴더]`이며 생략하면 `data`다. 운영 도구는 선택한 자료 폴더로 이 서버를 시작한다. 서버의 표준 출력은 MCP 메시지 전용이다.

검사는 `src/test/java/lab/inquiry/status/`에 있다. `ServerPlanTest`는 실제 MCP 연결의 조회와 응답 선언, `ClientPlanTest`는 준비한 응답의 해석과 실제 프로세스의 무응답·서비스별 결과 분리, `OperationsPlanTest`는 운영 진입점의 출력과 종료 코드를 확인한다. `PlanSupport`는 연결·응답 대조·프로세스 실행을 돕고, `ProtocolFixture`는 무응답을 재현한다.

## 자연어 문의 접수

`src/main/java/lab/inquiry/intake/`는 발언 한 줄을 접수하고 현재 상태와 함께 돌려주는 구현이다. 결정과 이유는 [0007](adr/0007-intake-model-connection.md), [0008](adr/0008-intake-result-and-service-existence.md), [0009](adr/0009-conversation-state-and-missing-target.md), [0010](adr/0010-intake-instructions-and-service-scope.md)에 있다.

| 파일 | 맡은 일 |
|---|---|
| `IntakeMain.java` | 실행 인수와 모델 환경변수를 읽고 SDK 클라이언트를 연결하며 상태 클라이언트의 `get`을 접수 세션에 넘긴다. 표준 입력 한 줄마다 결과 JSON 한 줄을 출력한다 |
| `IntakeModel.java` | 이름 대응표와 질문·접수 지침, 엄격한 스키마를 지정한 모델 요청, 완료·거절·내용 유무 확인, JSON 읽기와 중복 서비스 제거. `Call` 한 곳에서 모델 호출을 검사 대역으로 바꾼다 |
| `Intake.java` | 서비스 목록, 증상, 오류 메시지를 담는 내부 값 |
| `IntakeSession.java` | 첫 `\|`로 대화 ID와 발언을 나누고 대화별 현재 결과·발언 원문을 메모리에 보관한다. 접수 결과의 서비스를 주입한 `Function<String, LookupResult>`로 순서대로 조회하고 성공한 접수만 상태에 반영한다 |
| `IntakeWire.java` | 접수 결과의 공개 JSON 필드와 순서, 없는 값의 생략. 상태 항목은 기존 `StatusWire.fields`로 변환한다 |

이름 대응표는 `IntakeModel.NAMES`에, 접수 지침과 예는 `IntakeModel.INSTRUCTIONS`에 있다. 결과는 `READY`, `NEEDS_INPUT`, `FAILED`다. 한 서비스의 조회 실패는 그 서비스의 `statuses` 항목에 남고 접수 전체는 `READY`다. 모델 호출은 OpenAI Java SDK 4.60.0의 Chat Completions를 직접 사용하고, 제한 시간은 30초, SDK 자동 재시도는 0회다.

IDE에서 `lab.inquiry.intake.IntakeMain`을 실행한다. 작업 폴더는 프로젝트 루트 `inquiry_desk`, 인수는 생략하거나 `--data-dir <자료 폴더>`다. IDE의 비공유 실행 설정에 `OPENAI_API_KEY`와 `OPENAI_MODEL`을 넣는다. 모델은 엄격한 JSON Schema 출력을 지원하는 것을 지정한다. 환경변수 파일을 읽는 로더는 없으며, 설정이 없으면 표준 오류에 없는 변수 이름을 출력하고 종료 코드 2로 끝난다. 입력 종료는 코드 0이다. Gradle의 기본 실행 진입점은 상태 조회 운영 도구다.

IDE 콘솔에서 다음을 한 줄씩 입력한다.

```text
a|접속이 안 돼요
a|VPN이고 인증서 만료 메시지가 나와요
a|인증서 만료 메시지가 나와요
b|인증서 만료 메시지가 나와요
c|급여 시스템이 안 돼요
d|통합인증이 느려요
e|사내망 연결이 자꾸 끊겨요
f|인터넷이 안 돼요
g|VPN이 자꾸 끊겨요
g|VPN이 아니라 통합 로그인 문제예요
k|VPN이랑 급여 시스템이 안 돼요
```

실제 모델에서 볼 것은 다음과 같다.

| 입력 | 기대 |
|---|---|
| a의 셋째 발언 | VPN과 오류 메시지가 남는다 |
| b | 별도 대화로 보고 질문한다. 오류 메시지는 `errorMessage`에 적힌다 |
| c | `급여 시스템`을 그대로 적고 조회해 `NOT_FOUND`가 나온다 |
| d | `SSO`로 적힌다 |
| e, f | 서비스 없이 질문한다 |
| g의 둘째 발언 | 서비스가 `SSO`로 바뀌고 증상은 남는다 |
| k | `VPN`과 `급여 시스템`이 함께 적힌다 |

`src/test/java/lab/inquiry/intake/IntakePlanTest.java`에서 접수를 검사한다. 모델 응답과 상태 조회는 대역을 쓴다. 실제 모델의 해석은 자동 검사에서 확인하지 않는다.

## 개발 환경

| 위치 | 역할 |
|---|---|
| `AGENTS.md` | 구현 작업자의 원칙과 이 앱의 조건 |
| `CLAUDE.md` | 메인의 규칙: 계획과 승인, 작업 지시, 검토, 최종 검사와 합치기 |
| `.agents/skills/inquiry-change/SKILL.md` | 작업자가 변경 한 건을 처리하는 절차 |
| `docs/adr/` | 결정 하나당 한 파일. 상황, 결정, 이유, 다시 볼 조건 |
| `src/harness/java/lab/harness/run/` | 작업자 실행 관리. 아래 절 |
| `src/harness/java/lab/harness/StopHook.java`, `SessionStartHook.java` | Codex 세션의 종료 검사와 시작 문맥. 아래 "Hook" 절 |
| `.codex/hooks.json` | 두 Hook의 등록. 경로를 프로젝트 기준 상대경로로만 적어 Git으로 공유한다. 기준 커밋에 들어 있는 등록이 새 작업 폴더로 따라간다 |
| `src/harnessTest/` | 실행 관리와 두 Hook의 검사. Gradle `harnessTest` |
| `.local/harness/manager.json` | 실행 관리의 로컬 설정. Git에서 제외 |
| `.local/harness/tasks/` | 작업 접수 기록과 실행 시도별 결과. Git에서 제외 |

`src/harness`는 앱과 다른 소스 묶음이다. 앱의 `test`에는 이 묶음의 검사가 들어가지 않고, `check`가 `test`와 `harnessTest`를 함께 실행한다.

### 작업 한 건이 지나는 길

```text
승인된 계획 → 요청문
  → 접수(잠금) → 작업별 작업 폴더 → 작업자 실행 → 종료 확인 → 결과 읽기 → 최종 검사 → 상태 기록
  → 검토 → 풀 리퀘스트 → 합치기 → 합친 코드에서 다시 검사
```

가운데 줄이 실행 관리가 맡는 구간이다. 그 앞과 뒤는 메인이 `CLAUDE.md`에 따라 진행한다.

### 작업자 실행 관리

| 클래스 | 맡은 일 |
|---|---|
| `TaskLedger` | 접수 기록. 작업마다 폴더 하나, 실행 시도마다 그 아래 폴더 하나. 잠금 파일이 있으면 그 작업의 시도 하나가 실행 권한을 갖고 있고, 실행 관리가 종료를 확인하거나 사람이 `release`로 풀 때까지 남는다. 잠금을 살피고 풀고 만들고 시도의 끝을 기록하는 일은 작업마다 한 번에 한 곳에서만 한다 |
| `Workspaces.GitWorktrees` | 기준 커밋에서 작업별 브랜치 `task/<작업 이름>`과 worktree를 만든다. 같은 작업의 다음 시도는 같은 폴더를 쓴다. 이미 있는 폴더는 이 저장소의 worktree 목록에 그 브랜치로 올라 있을 때만 쓴다 |
| `ProcessRunner` | 요청을 표준 입력에 쓰고 닫는다. 제한 시간과 중단 요청을 지켜보고, 하위 프로세스를 추적하며, 종료를 요청한 뒤 실제로 사라졌는지 확인한다. 작업자와 최종 검사가 같은 것을 쓴다 |
| `GradleChecker` | 작업 폴더에서 검사를 실행하고, 이번 실행이 새로 만든 결과 파일로 통과·실패·실행 불가를 가른다. 실패한 검사가 하나라도 있으면 실패다. 실패가 없을 때는 설정에 적은 검사 작업마다 실행된 검사가 있어야 통과이고, 하나라도 없으면 실행 불가다 |
| `WorkerRunManager` | 위 넷을 순서대로 잇고 시도의 끝과 작업의 상태를 기록한다. 정상 종료한 작업자의 결과에서 상태와 질문 유무를 읽는다 |
| `WorkerRunCli` | 실행 진입점. `start <작업 이름> <요청문 파일>`, `stop <작업 이름>`, `status <작업 이름>`, `release <작업 이름> <시도 번호>` |

작업자가 돌려주는 결과의 형식은 `src/harness/resources/worker-result.schema.json`에 있다. 끝남 또는 멈춤, 요약, 질문, 변경 파일, 실행한 검사, 확인하지 못한 것, 풀 리퀘스트 본문 초안이다. 작업자가 맡아서 정한 구현 선택과 이유는 본문 초안에 적는다.

결과의 형식은 작업자 명령(Codex라면 `--output-schema`에 스키마 파일을 지정)과 요청문이 맡는다. 실행 관리는 정상 종료한 작업자의 결과 파일을 한 번 읽는다. 파일이 없거나 JSON 객체로 읽을 수 없거나 `status`가 `done`·`stopped`가 아니면 `NEEDS_CHECK`다. `stopped`에 질문이 있으면 `QUESTION`, 없으면 `NEEDS_CHECK`이고, `done`이면 최종 검사를 실행한다.

작업의 상태는 여섯 가지다.

| 상태 | 뜻 |
|---|---|
| `RUNNING` | 시도가 실행 중 |
| `REVIEW_READY` | 작업자가 끝났고 최종 검사가 통과함. 검토 대기 |
| `CHECK_FAILED` | 최종 검사에서 실패한 검사가 있음 |
| `CHECK_UNAVAILABLE` | 최종 검사를 실행하지 못했거나 새 결과가 없음 |
| `QUESTION` | 작업자가 질문을 남기고 멈춤. 검사하지 않음 |
| `NEEDS_CHECK` | 제한 시간 초과, 중단, 시작 실패, 결과를 읽을 수 없거나 상태를 모르거나 멈춘 결과에 질문이 없음, 종료 미확인 등 사람이 확인해야 하는 경우 |

실행이 정상으로 끝나지 않았으면 작업자가 남긴 결과와 관계없이 `NEEDS_CHECK`다. 작업자가 질문을 남기고 멈춘 결과를 썼더라도 같다. 프로세스가 남았을 수 있는 실행에 답을 붙여 바로 다시 넘기면 안 되기 때문이다. 결과 파일은 시도 폴더에 그대로 남는다.

실행 관리는 검사가 실패해도 작업자를 다시 실행하지 않는다. 수정 요청은 메인이 같은 작업의 새 시도로 넘긴다.

작업 폴더에서 실행되는 작업자에게도 Hook이 걸린다. 종료 Hook은 작업자가 스스로 하는 검사이고, 상태를 정하는 것은 작업자와 Hook의 보고와 별개로 실행하는 최종 검사다. 실행 관리는 종료 Hook이 그 실행에서 남긴 판정 기록을 시도 폴더의 `hook-event.json`으로 가져온다. 이 파일이 없으면 Hook이 호출되지 않았거나, 호출됐지만 판정을 기록하기 전에 실패한 것이다. Hook이 작업자의 세션을 멈춘 실행도 상태는 위 표대로 정해지고, 멈췄다는 사실과 Hook의 판정 요약이 상태의 이유에 덧붙는다.

`stop`은 시도 전체를 중단한다. 작업자가 실행 중이면 작업자를, 최종 검사가 실행 중이면 검사를 끝낸다. 작업 폴더를 준비하는 동안 들어온 요청이면 작업자를 시작하지 않고, 작업자가 끝나는 순간에 들어온 요청이면 결과를 읽거나 최종 검사로 넘어가지 않는다. 중단된 시도의 상태는 `NEEDS_CHECK`다.

잠금이 남아 있으면 다음 `start`는 거절된다. 시도의 끝이 기록되지 않았고 실행 관리가 살아 있으면 `RUNNING`, 그 밖에는 `NEEDS_CHECK`이며 이유에 복구 순서가 나온다. 실행 관리가 자기 시도의 종료를 확인하면 잠금을 풀지만, 남은 잠금을 다음 접수가 자동으로 풀지는 않는다.

잠금이 남은 작업은 다음 순서로 복구한다.

1. `stop <작업 이름>`으로 기록된 프로세스와 그 하위 프로세스를 끝낸다. 실행 관리가 살아 있으면 중단을 요청하고 끝을 기다린다. 실행 관리가 사라졌거나 끝은 기록됐지만 종료가 확인되지 않은 시도는 종료 결과를 기록하고 잠금을 남긴다.
2. 남은 프로세스가 없는지 사람이 확인한다. 프로세스 시작 직후 기록 전에 실행 관리가 사라진 경우는 `stop`이 찾지 못하므로 이때 확인한다.
3. `release <작업 이름> <확인한 시도 번호>`로 잠금을 푼다. 잠금이 없거나 시도 번호가 다르거나, 끝이 기록되지 않은 시도의 실행 관리가 살아 있으면 거절한다.
4. `start <작업 이름> <요청문 파일>`로 다음 시도를 시작한다.

작업의 상태는 `.local/harness/tasks/<작업 이름>/task.json`, 시도별 요청·결과·로그·검사와 프로세스 기록은 그 아래 `attempts/<시도 번호>/`에 남는다. `status <작업 이름>`으로 상태와 이유, 시도 폴더를 확인한다.

설정의 프로젝트 경로(`projectPath`)가 작업 폴더 밖을 가리키면 작업 폴더를 만들기 전에 거절한다. 서로 다른 작업이 같은 폴더를 고치게 되기 때문이다.

### Hook

`.codex/hooks.json`은 두 Hook을 프로젝트 루트 기준 상대경로의 명령으로 등록한다. Codex는 세션의 작업 폴더에서 Hook 명령을 실행하므로 원래 폴더와 작업별 폴더에서 같은 등록이 그 폴더의 코드를 가리킨다. 세션은 프로젝트 루트에서 시작해야 한다. 하위 폴더에서 시작하면 상대경로가 맞지 않는다.

두 Hook은 빌드 없이 소스 파일 하나로 실행된다. 그래서 라이브러리를 쓰지 않고, Gradle의 소스 인코딩 설정도 적용되지 않는다. 명령의 `'-Dfile.encoding=UTF-8'`은 Java가 Hook의 소스 파일을 UTF-8로 읽게 한다. Java 17은 이 옵션이 없으면 운영체제의 기본 문자 집합으로 읽어, 기본값이 UTF-8이 아닌 Windows에서 한글이 든 소스가 깨진다. 옵션은 작은따옴표로 감싼다. 따옴표가 없으면 PowerShell이 이 옵션을 두 인수로 쪼개 Java가 시작하지 못한다. 한글 출력은 Hook 프로그램이 UTF-8 바이트로 직접 쓴다.

Codex는 등록의 내용이 바뀌면 사람이 다시 검토해 신뢰하기 전까지 그 Hook을 건너뛴다. 등록을 고친 뒤에는 대화형 세션에서 `/hooks`로 신뢰한다.

| Hook | 시점 | 하는 일 |
|---|---|---|
| 시작 Hook | 세션 시작과 재개 | 이 문서의 앞부분(업무 요구, 처리 순서, 지금 구현된 것, 제공 자료)을 문맥으로 전달하고, 나머지와 결정 기록의 위치를 알린다 |
| 종료 Hook | 응답을 끝내려는 순간 | 이번 실행만의 빌드 폴더(`.local/harness/runs/<실행 번호>/build`)에서 Gradle `test`를 실행하고 새 결과 파일로 판정한다 |

종료 Hook의 판정에 따른 동작은 다음과 같다.

| 판정 | 동작 |
|---|---|
| 통과 | 그대로 끝낸다 |
| 실패, Hook의 요청으로 이어진 턴이 아님 | 실패한 검사와 결과 위치를 알리고 같은 세션이 한 번 고치게 한다. 계획에서 온 검사의 기대값을 바꾸지 말고, 기대 결과를 바꿔야만 통과한다면 멈추라고 함께 알린다 |
| 실패, Hook의 요청으로 이어진 턴 | 멈추고 실패한 검사와 결과 위치를 알린다 |
| 실행 불가 | 멈추고 이유와 로그 위치를 알린다. 고치게 하지 않는다 |
| 마지막 응답이 질문을 남기고 멈춘 결과 | 검사하지 않는다 |

실행 불가는 실행된 검사의 결과를 얻지 못했다는 뜻이다. Gradle을 시작하지 못한 경우와 제한 시간 초과뿐 아니라, 작업자의 코드가 컴파일되지 않아 결과 파일이 생기지 않은 경우도 여기에 든다. Hook은 원인이 환경인지 코드인지 가리지 못하므로 고치게 하지 않고 멈추며, 원인은 알려 준 로그에서 가린다.

한 번 고칠 기회는 사건의 `stop_hook_active` 값으로 가린다. Codex는 종료 Hook의 요청으로 이어진 턴이 끝날 때 이 값을 참으로 보낸다.

"질문을 남기고 멈춘 결과"는 마지막 응답 전체가 JSON 객체이고, `status`가 `stopped`이며, `questions`가 비어 있지 않은 배열인 경우다. Hook은 이 둘만 본다. 결과의 나머지 항목이 형식에 맞는지는 실행 관리가 확인한다. 글로 쓴 질문, 결과를 다른 글로 감싼 응답, 질문이 빈 멈춤 결과는 여기에 들지 않아 검사가 실행된다.

마지막 판정은 `.local/harness/last-hook-event.json`에 남는다. `check_status`는 검사의 판정이고 `hook_action`은 Hook이 세션에 한 일(`none`, `fix_once`, `stopped`, `skipped`)이다. 검사 제한 시간은 `StopHook.TEST_TIMEOUT`의 120초이고, 등록의 150초는 프로그램 시작과 기록까지 포함한 바깥 제한이다. 작업자의 제한 시간은 Hook의 검사와 한 번의 수정까지 포함해 잡는다.

Windows에서 Gradle이 프로세스 사이 연결용 소켓을 기본 위치에 만들지 못하는 환경이 있다. 그런 환경에서 시작한 세션은 종료 Hook 안의 Gradle이 `Unable to establish loopback connection`으로 끝나 실행 불가가 된다. 세션을 시작하는 쪽의 환경에 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<경로가 짧은 폴더>`를 넣으면 Hook과 Gradle이 그 값을 이어받는다. 실행 관리로 작업자를 시작할 때는 설정의 `workerEnvironment`에 넣는다. 종료 Hook 프로그램은 세 번째 인수로 소켓 폴더(프로젝트의 `.local/sock`)를 받을 수도 있는데, 공유하는 등록은 이 인수를 넘기지 않는다.

작업 폴더를 지울 때는 Hook의 빌드 폴더가 깊어 Windows의 경로 길이 제한에 걸릴 수 있다.

### 실행

IDE에서는 `lab.harness.run.WorkerRunCli`를 `harness` 소스 묶음의 클래스패스로 실행하고 작업 폴더를 이 프로젝트 루트로 둔다. 다른 프로그램이나 터미널에서 실행할 때는 Gradle `installWorkerRun`으로 `build/worker-run`에 실행 파일을 모은 뒤 다음처럼 실행한다. 작업자를 사람 대신 시작하고 종료까지 기다리는 프로그램이어서 이 경로가 필요하다.

- Windows PowerShell: `java -cp "build/worker-run/classes;build/worker-run/lib/*" lab.harness.run.WorkerRunCli start <작업 이름> <요청문 파일>`
- macOS·Linux·WSL: `java -cp "build/worker-run/classes:build/worker-run/lib/*" lab.harness.run.WorkerRunCli start <작업 이름> <요청문 파일>`

설정은 Git에서 제외된 `.local/harness/manager.json`에서 읽는다. 다른 파일을 쓰려면 명령 앞에 `--config <설정 파일>`을 붙인다. 경로와 명령은 장비마다 다르므로 아래는 자리표시자를 넣은 예다.

```json
{
  "repository": "<저장소 루트>",
  "projectPath": "week12-ai-agent-integration/inquiry_desk",
  "worktreeBase": "<작업 폴더를 둘 위치>",
  "baseRef": "main",
  "ledger": ".local/harness/tasks",
  "workerCommand": ["<작업자 실행 명령>", "<인수>", "{workspace}", "{result}"],
  "workerEnvironment": {},
  "workerTimeoutSeconds": 3600,
  "checkCommand": ["<Gradle 실행 명령>", "test", "--rerun-tasks", "--no-daemon", "-PcourseBuildDir={build}"],
  "checkEnvironment": {},
  "checkTimeoutSeconds": 600,
  "checkResults": ["test"]
}
```

| 항목 | 뜻 |
|---|---|
| `repository`, `baseRef` | worktree를 만들 저장소와 기준 브랜치 |
| `projectPath` | 저장소 루트에서 이 프로젝트까지의 상대경로. 작업 폴더 안의 같은 위치가 작업자와 검사의 실행 폴더가 된다 |
| `worktreeBase` | 작업별 폴더를 둘 위치. 그 아래에 작업 이름의 폴더가 생긴다 |
| `ledger` | 접수 기록과 시도별 결과를 둘 위치 |
| `workerCommand` | 작업자 명령. `{workspace}`는 작업 폴더로, `{result}`는 작업자가 결과를 쓸 파일로 바뀐다. 요청문은 표준 입력으로 전달된다 |
| `checkCommand` | 검사 명령. `{project}`는 검사할 프로젝트 폴더로, `{build}`는 이번 검사의 빌드 폴더로 바뀐다. `{build}`는 반드시 있어야 한다 |
| `checkResults` | 결과를 읽을 검사 작업의 이름. 검사 명령이 실행하는 검사 작업과 같아야 한다. 비우면 `test` |

검사를 캐시 없이 처음부터 다시 실행하게 하는 옵션(`--rerun-tasks` 등)은 검사 명령에 직접 적는다. 실행 관리가 붙여 주지 않는다. 실행 관리나 Hook을 고치는 작업이면 검사 명령과 `checkResults`에 `harnessTest`를 함께 넣는다.

작업 이름은 영문 소문자·숫자·하이픈으로 61자까지이고 영문 소문자나 숫자로 시작한다. 실행 결과는 표준 출력에 JSON으로 나오고, 종료 코드는 상태에 따라 다르다.

| 종료 코드 | 상태 |
|---|---|
| 0 | `REVIEW_READY` |
| 3 | `QUESTION` |
| 4 | `RUNNING`(이미 실행 중이어서 접수하지 않음, 또는 `status`로 본 실행 중인 작업) |
| 1 | 그 밖의 상태 |
| 2 | 인수가 사용법과 다름 |

### 검사

- 앱: Gradle `test`. 결과는 `build/test-results/test`.
- 실행 관리와 Hook: Gradle `harnessTest`. 실행 관리의 검사는 실제 코딩 작업자 대신 `FakeWorker`를 실행한다. 실제 Codex 작업자와 실제 Gradle 검사를 거친 확인은 이 검사에 들어 있지 않다.
