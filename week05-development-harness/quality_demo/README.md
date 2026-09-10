# 개발 하네스 설계 실습

환불·배치 업무는 완성해서 제공합니다. 이번에는 업무 요구에서 **필요한 맥락·작업 규칙·도구·환경·검사·피드백과 실행 흐름**을 도출해 AI와 개발 하네스 v1을 만듭니다. 그 안에서 작업의 준비·실행·검사·복구·종료를 조율할 엔진의 중심 구조와 진입점을 정합니다. 비교 예제의 인터페이스를 구현해야 실행되는 틀은 없습니다.

하네스는 AI가 실제로 일하도록 자료와 실행 기능을 연결하는 전체 체계이며, 엔진은 그중 다음 행동을 결정하는 코드입니다. 이 프로젝트에서는 `AGENTS.md`와 업무 계약이 판단 기준을 제공하고, Codex가 파일·명령 도구를 사용하며, Gradle 검사가 업무 결과의 근거를 반환합니다. 자기 엔진은 이 요소들을 연결합니다. 개념 해설과 Day별 실습은 [주차 README](../README.md)에서 이어 읽습니다.

## 제공한 것과 만들 것

| 제공 자료 | 사용하는 이유 |
|---|---|
| `Refund`, `RefundInput`, `BatchRefund`, `data/` | 하네스 학습과 관계없는 업무 준비를 줄임 |
| `AGENTS.md`, `harness/contract.md`, `harness/task.md` | 반복할 작업 규칙, 완료 기준, 이번 요청을 구분해 전달 |
| `BatchRefundTest` | 같은 입력의 id·순서·값·오류를 확인하는 기존 완료 근거 |
| `ProcessRunner.java` | 명령 실행·표준 입출력·시간 제한의 운영체제 보조 코드 |
| `src/main/java/lab/week05/example/` | 요구를 두 구조로 완성한 비교 예제 |
| `examples/job.json` | 비교 예제가 스스로 선택한 작업 표현의 한 예 |

학습 결과는 자신의 요구 표현과 선택한 맥락·지침·도구·검사를 실제 작업·실패 대응·종료까지 연결한 하네스입니다. 그 중심의 실행 구조를 AI와 만들며, 프로세스 입출력이나 환불 코드를 반복 구현하지 않습니다. 분석과 실행 근거는 주차의 `failure-recovery.md` 한 곳에 연결합니다.

## 업무와 기본 실행

IDE에서 JDK 17 이상의 Gradle 프로젝트로 엽니다. `test`, `batch` 작업을 IDE에서 실행할 수 있습니다. 최초 실행은 Wrapper와 라이브러리 다운로드가 필요합니다.

Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat -q batch --args="data/batch-requests.json"
```

macOS·Linux·WSL:

```bash
./gradlew test
./gradlew -q batch --args="data/batch-requests.json"
```

`RefundInput`은 정수 금액과 면제 여부를 확인하고 `Refund`가 계산합니다. `BatchRefund`는 요청 8개에 성공 3행·입력 오류 5행을 같은 순서로 반환합니다. 오류 행도 요청을 처리한 결과입니다. `BatchRefundTest`는 전체 JSON 배열의 id·순서·내용을 기대값과 비교하며 공백·객체 키 순서에는 의미를 두지 않습니다. 종료 코드 0만 확인하면 결과 누락을 놓칠 수 있습니다.

## 완결된 비교 예제 읽기

두 구현은 같은 맥락·작업 규칙·검사와 실행 보조 도구를 사용해 같은 업무 조건을 처리합니다. 하네스에서 실행 제어의 구조가 달라지는 부분을 비교합니다. 학습자가 두 엔진을 반복 제작하는 과제가 아니라, 책임을 어디에 표현할지 판단하기 위한 실행 가능한 자료입니다.

| 파일 | 실제 선택과 역할 |
|---|---|
| `example/Job.java` | 작업 목적·작업 위치·맥락·실행과 검사 명령·복구 상한을 하나의 작업 입력으로 표현 |
| `example/LinearEngine.java` | 준비→실행→검사→필요한 복구를 순서 함수로 표현 |
| `example/StateEngine.java` | 현재 상태와 사건에 따른 다음 상태를 명시하고 실제 행동을 연결 |
| `example/Execution.java` | 프로세스 결과를 예제의 실행 결과로 바꾸고 실패 근거로 다음 요청을 구성 |
| `example/EngineCli.java` | 이 예제 자체의 명령행 입력·구조 선택·현재 실행 결과 기록 |

`Job`, 상태 이름, `Executor`, CLI 인자는 **이 예제의 설계 결과**입니다. 자신의 엔진에서 그대로 사용해야 할 고정 계약이 아닙니다. 같은 요구를 요청 파일+순서 함수로 표현할지 작업 객체+상태 전이로 표현할지, 실패와 정보 보충을 어떤 결과로 반환할지 AI와 선택합니다.

비교 예제의 내부 도구 호출·파일 편집은 기존 Codex가 담당합니다. 예제 엔진은 Codex 작업 한 번을 실행 단위로 다루고, 그 뒤 프로젝트 검사를 실행합니다. 6주차에서 앱이 직접 모델 응답과 Tool Calling을 연결하는 루프와 실행 경계가 다릅니다.

## 비교 예제 실행

IDE의 `referenceEngineJar` 또는 다음 명령으로 예제 JAR를 만듭니다.

Windows PowerShell:

```powershell
.\gradlew.bat referenceEngineJar
```

macOS·Linux·WSL:

```bash
./gradlew referenceEngineJar
```

다음 두 명령은 **교육용으로 정한 실행 응답**을 넣습니다. Codex 연결이나 코드 복구의 증거가 아닙니다. 같은 검사 실패를 두 구조가 어떻게 표현하는지 봅니다.

```text
java -jar build/libs/reference-engine.jar examples/job.json linear demo-repair
java -jar build/libs/reference-engine.jar examples/job.json state demo-repair
```

순서 함수는 최종 결과와 시도 수를, 상태 방식은 그 결과에 도달한 상태·사건의 연결도 보여 줍니다. 정상 종료 응답 뒤 검사가 실패하면 완료를 반환하지 않으며, 실패 근거로 한 번 더 실행한 뒤 새 검사를 사용합니다. 정보나 완료 기준이 없으면 추측하지 않고 필요한 내용을 반환합니다.

마지막 `demo-repair`를 생략하면 **실제 Codex CLI를 사용**하며 설치·로그인과 계정 사용량이 필요합니다. 기본 명령은 `exec --sandbox workspace-write --json`이고 `-a never`는 승인 범위를 넓히지 않습니다. 승인이나 실행 환경이 필요한 상태는 실패할 수 있습니다. [Codex 비대화형 실행](https://learn.chatgpt.com/docs/non-interactive-mode)

이 예제의 현재 결과는 `.local/reference-engine/current.json`에 새 실행 식별자와 함께 기록됩니다. 준비 중·정보 보충 필요·성공·중단을 구분합니다. 실제 Codex 마지막 응답은 `agent-message.txt`이며 이전 응답을 새 결과로 재사용하지 않습니다. 전체 대화를 모으는 측정 도구는 만들지 않습니다.

`examples/job.json`의 검사 명령은 Gradle `test`로 업무 코드를 다시 실행해 확인합니다. 저장된 출력 파일을 완료 근거로 사용할 때는 그 파일을 직접 검사하는 명령을 자기 엔진에 연결합니다. 코드의 회귀 검사가 통과해도 이전에 저장한 파일의 누락까지 확인한 것은 아닙니다.

## 내 엔진의 실행 위치

자신의 Java 클래스·작업 파일·진입점은 AI와 선택합니다. 기존 Gradle 프로젝트에 별도 실행 작업을 연결하거나 작은 실행 프로젝트를 둘 수 있습니다. 별도 클래스 이름, 반환 타입, JSON 필드에 맞추기 위해 중심 설계를 바꾸지 않습니다. 선택한 실제 시작 명령에서 업무 요구 → 실행 → 검사 → 다음 행동이 연결되어야 합니다.

실행 중인 Gradle 작업이 같은 프로젝트의 Gradle 검사를 중첩 호출하면 실행 자원을 놓고 충돌할 수 있습니다. 예제는 JAR로 바깥 실행을 시작한 뒤 검사 명령을 부릅니다. 자신의 엔진도 실행기와 검사 프로세스의 관계를 고려해 진입점을 정합니다.

Windows에서 Gradle의 loopback 소켓 오류가 발생할 때만 [프로젝트 로컬 소켓 안내](windows-gradle.md)를 사용합니다. 기존 프로젝트 로컬 `.codex/config.toml`의 `JAVA_TOOL_OPTIONS` 설정을 유지하고 일반 `TEMP`·`TMP`나 전역 설정을 바꾸지 않습니다.

## 선택 참고: Hook

제공 Hook은 명령 종료 후 실패 지침을 추가하는 예제입니다. 코드 0에 `{}`를 반환하므로 결과 누락을 판정하지 않습니다. 업무 검사와 엔진의 수용·복구·종료 결정을 대신하지 않습니다. 필요할 때 `prepareHook hookTest`와 Host의 연결 안내를 사용합니다. 처리 코드 검사와 실제 Host에서의 후속 행동은 구분합니다. [공식 Hook 안내](https://learn.chatgpt.com/docs/hooks)
