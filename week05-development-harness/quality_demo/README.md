# 환불 계산 작업 환경

Codex의 내장 하네스를 프로젝트에 맞게 사용하는 방법을 배우는 작은 Java 프로젝트입니다. 수수료 면제(A)를 구현하며 지침·권한·검사가 실제 작업을 어떻게 이끄는지 확인하고, 여러 환불 요청(B)의 출력으로 업무 완료에 필요한 근거를 정합니다. 이어 검사 판정을 수정·재검사·완료 보고에 연결하고 새 세션에서 재사용합니다. 실제 결제 서비스나 API 키 없이 진행하며 Day별 해설과 복사할 요청은 [주차 README](../README.md)에 있습니다.

## 시작 상태와 실행

`week05-development-harness/quality_demo/`를 IDE의 Gradle 프로젝트로 엽니다. JDK 17 이상을 사용합니다. Wrapper가 빌드 도구·JUnit과 JSON 라이브러리 Gson을 준비하며 최초 실행에는 다운로드가 필요합니다. IDE의 Gradle 테스트 실행 기능을 먼저 사용해도 됩니다.

Windows PowerShell:

```powershell
.\gradlew.bat -q run
.\gradlew.bat test
```

macOS·Linux·WSL:

```bash
./gradlew -q run
./gradlew test
```

`Refund.refundAmount(paid, fee)`는 유효한 두 금액의 차감을 계산하고 결과의 하한을 0으로 둡니다. 음수 입력은 계산 전에 거부합니다. 정수 원 단위이며 기본 출력은 `8000`입니다. 시작 테스트 세 개를 포함한 A 검사 11개, B 검사 7개, 저장된 출력 검사 10개로 `test` 작업은 총 28개입니다.

**현재 요구 A와 B의 구현·업무 검사를 확인했습니다.** 수수료 면제와 JSON 입력 경계를 여러 요청의 배치 처리에 재사용합니다. 실제 8행의 출력과 오류 행을 제외한 교육용 복사본의 대조 근거는 `../failure-recovery.md`에 있습니다.

Windows의 Codex에서 Gradle 소켓 초기화가 실패하면 [소켓 오류 해결 및 정리](#windows에서-gradle-소켓-오류가-날-때)를 참고하세요.

## 요구 A: 수수료 면제 조건 추가

고객 지원 담당자가 승인한 환불에는 수수료를 면제할 수 있어야 합니다. Day 2에서는 이 변경을 통해 **참고할 요구·검사 방법·실패 대응 지침이 실제 파일 확인·편집·검사·보고에 적용되는지** 확인합니다. 아래 입력 계약과 계산 규칙은 기능이 맞는지 판정할 기준입니다.

- 기존 두 인자 호출은 같은 계산을 유지합니다. 면제를 지정하지 않으면 기존 계산입니다.
- 결제액·수수료는 0 이상의 정수 원입니다. 외부 JSON 입력에서 문자열·소수·불리언 금액을 거부하며 오류 메시지는 `금액은 0 이상의 정수여야 합니다.`입니다.
- `fee_waived`는 JSON 불리언만 허용합니다. 생략하면 `false`, 명시한 다른 형식은 `fee_waived는 불리언이어야 합니다.` 오류입니다.
- 금액을 검사하고 면제 여부를 검사한 뒤 계산합니다. 둘 다 잘못되었다면 금액 오류가 먼저입니다. 면제인 요청의 음수 수수료도 거부합니다.
- 면제이면 결제액, 비면제이면 결제액에서 수수료를 빼고 0보다 작을 때 0을 반환합니다.

이번 구현에서는 `Refund.refundAmount(long paid, long fee, boolean feeWaived)`와 기존 두 인자 메서드를 함께 둡니다. 외부 형식은 새 `RefundInput.java`에서 Gson으로 JSON을 읽어 검사하고 유효한 값만 계산 메서드에 전달합니다. 계산 메서드도 음수라는 업무 오류를 거부하므로 다른 호출 경로에서도 같은 규칙을 지킵니다. JSON 파서 자체를 재구현하지 않습니다.

금액 표현은 64비트 정수 범위로 선택합니다. 최대값은 9,223,372,036,854,775,807이며 초과한 입력도 금액 오류로 거부합니다. JSON 숫자는 소수점·지수 표기 없는 정수 형식을 사용합니다. 이는 현재 구현의 입력 계약이며, 다른 통화나 소수 단위 금액 요구가 생기면 단위·표현·검증을 함께 다시 선택합니다.

`data/refund-cases.json` 각 행에는 `id`, `paid`, `fee`, `fee_waived`, `expected`가 있습니다. 기대값이 숫자이면 환불액, `{ "error": "..." }`이면 입력 거부의 메시지입니다. 외부 입력은 JSON 경계에, 계산 규칙은 계산 메서드에 연결한 JUnit 검사로 확인합니다.

| 입력 | 기대 결과 | 이유 |
|---|---|---|
| 결제 10000, 수수료 2000, 비면제 | 8000 | 기존 계산 |
| 결제 10000, 수수료 2000, 면제 | 10000 | 수수료 면제 |
| 결제 1000, 수수료 2000, 비면제 | 0 | 환불 하한 |
| 결제 10000, 수수료 -1, 면제 | 금액 오류 | 잘못된 입력을 면제로 숨기지 않음 |

자료의 음수 결제액·문자열·불리언 금액·잘못된 면제 여부도 확인합니다. 테스트 기대값은 구현 결과에서 만들지 않고 요구에서 정합니다.

## 요구 B: 여러 환불 요청 처리

A를 완료한 뒤 JSON 파일 하나의 여러 요청을 처리하도록 확장합니다. Day 3에서는 **종료 코드와 업무 완료의 근거를 구분**합니다. 제공 입력 8개에 성공 3행과 올바른 오류 5행이 있으면 모두 처리한 결과이지만, 성공 3행만 있으면 다른 요청의 결과가 빠진 것입니다. 실제 출력의 식별자·순서·내용까지 대조한 뒤 완료 판단에 필요한 근거를 정합니다. 이 활동을 시작하기 전에는 구현하지 않습니다.

`src/main/java/lab/week05/BatchRefund.java`를 추가하고 파일 경로를 인자 하나로 받게 합니다. `build.gradle`에는 `BatchRefund`를 실행할 `batch` 작업을 추가합니다. `data/batch-requests.json`은 입력 배열이며 모든 행은 객체이고 필요한 필드와 고유 식별자가 제공된다고 가정합니다.

- A의 입력 검사와 계산을 재사용하며 입력 순서와 `id`를 유지합니다.
- 성공 행은 `{ "id": "regular", "status": "ok", "refund": 8000 }`입니다.
- 잘못된 행은 `{ "id": "negative-paid", "status": "error", "error": "금액은 0 이상의 정수여야 합니다." }`입니다. 면제 형식의 오류도 A와 같습니다.
- 한 행이 잘못되어도 다음 행을 처리합니다. 모든 행의 처리 결과가 있으면 정상·오류 행이 섞여도 종료 코드는 0입니다.
- 결과 배열만 한 줄의 JSON으로 표준 출력에 씁니다. 안내 문구를 JSON에 섞지 않습니다.
- 파일 읽기 실패·잘못된 JSON·배열이 아닌 최상위 값은 원인을 표준 오류로 알리고 0이 아닌 코드로 종료합니다. 이때 성공 배열을 출력하지 않습니다. 빈 배열은 정상이며 출력은 `[]`입니다.

새 작업은 다음처럼 연결할 수 있습니다.

```groovy
tasks.register('batch', JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'lab.week05.BatchRefund'
}
```

Windows PowerShell:

```powershell
.\gradlew.bat -q batch --args="data/batch-requests.json"
.\gradlew.bat test
```

macOS·Linux·WSL:

```bash
./gradlew -q batch --args="data/batch-requests.json"
./gradlew test
```

`data/batch-expected.json`과 파싱한 결과의 값·배열 순서를 비교합니다. 공백과 객체 키 순서는 판정 기준이 아닙니다. 오류 행도 요청을 처리한 결과이며, 반환 행 수만 같아도 식별자나 순서가 다르면 완전하지 않습니다.

## 이번 출력 검사와 완료 판단

`BatchResultCheck.compare`는 기존 배치 테스트의 요청 수·식별자·순서·행 내용 비교를 공통 코드로 옮긴 것입니다. `BatchRefundTest`와 `checkBatch` 명령이 같은 기준을 사용합니다. `checkBatch`는 입력 파일, 업무 요구에서 정한 기대 결과 파일, 실제 출력 파일을 순서대로 받습니다. 파일을 읽거나 해석하지 못해도 실패합니다. 환불액은 현재 금액 계약에 맞는 0 이상의 64비트 정수 형식과 정확한 값까지 확인합니다. 소수점·지수 표기나 숫자를 담은 문자열은 통과하지 않습니다.

다음은 배치를 새로 실행하고 **바로 그 출력**을 검사하는 경로입니다. 아래 순서와 검사 판정을 완료 보고 전에 사용하는 규칙은 `AGENTS.md`에 있습니다.

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path .local/current-batch | Out-Null
.\gradlew.bat -q batch --args="data/batch-requests.json" 2> .local/current-batch/batch.stderr.log |
    Set-Content -Encoding utf8 .local/current-batch/actual.json
if ($LASTEXITCODE -ne 0) { throw '배치 실행 실패: 표준 오류의 원인을 확인하세요.' }
.\gradlew.bat -q checkBatch --args="data/batch-requests.json data/batch-expected.json .local/current-batch/actual.json"
if ($LASTEXITCODE -ne 0) { throw '완료 보류: 검사에서 지적한 결과를 확인하세요.' }
.\gradlew.bat test
```

macOS·Linux·WSL:

```bash
mkdir -p .local/current-batch
./gradlew -q batch --args="data/batch-requests.json" > .local/current-batch/actual.json 2> .local/current-batch/batch.stderr.log &&
./gradlew -q checkBatch --args="data/batch-requests.json data/batch-expected.json .local/current-batch/actual.json" &&
./gradlew test
```

정상 출력이나 비교용 복사본을 따로 검사하려면 `checkBatch`의 마지막 경로를 해당 파일로 바꿉니다. 성공 3행과 올바른 오류 5행이 모두 있으면 검사 통과와 종료 코드 0을 반환합니다. 누락·중복·잘못된 식별자·내용 불일치가 있으면 「완료 보류」와 이유를 표준 오류로 알리고 0이 아닌 코드로 끝납니다. 배치가 정상 종료했더라도 이 검사에서 실패한 파일은 완료 근거로 사용하지 않습니다.

실패 원인을 확인하고 필요한 부분을 수정하거나 배치를 다시 실행한 뒤, 사용할 출력에 같은 검사를 적용합니다. 기대 자료를 실제 오답에 맞춰 바꾸지 않습니다. 검사 명령은 전달받은 파일을 대조하므로 어떤 실행에서 나온 파일인지는 실행·저장 경로와 함께 확인해야 합니다. 이 구성은 명령 결과와 프로젝트 지침으로 Codex의 다음 행동을 연결하며, Hook 없이 사용합니다.

## Hook: 명령 결과를 다음 판단에 연결하기

Day 4~5에는 **Day 3의 완료 기준을 실제 검사와 후속 행동에 연결하는 방법**을 배웁니다. 결과 누락을 판정할 위치와 그 판정을 AI에 전달할 위치를 선택하고, 완료 보류·수정·재검사로 이어지는지 확인합니다. 새 세션에서는 저장된 지침과 검사를 찾아 같은 판단을 하는지 봅니다.

Hook은 도구 사용 같은 특정 사건이 발생했을 때 정해 둔 프로그램을 실행하는 연결입니다. 이 프로젝트의 Hook은 **명령 실행이 끝난 시점에 실패 대응 지침을 추가로 전달하는 구성**입니다. 아래는 프로젝트에 들어 있는 실제 파일과 설정을 기준으로 한 설명입니다.

### 실제 구성 파일과 설정

| 파일 | 현재 들어 있는 구성과 역할 |
|---|---|
| [.codex/config.toml](.codex/config.toml) | `[features]`의 `hooks = true`로 Hook 기능 사용을 설정합니다. |
| [.codex/hooks.json](.codex/hooks.json) | `PostToolUse` 이벤트에 `matcher: "Bash"`와 `type: "command"`를 연결합니다. 어떤 명령 결과 뒤에 어느 프로그램을 실행할지 정합니다. |
| [.codex/hooks/PostToolUseReview.java](.codex/hooks/PostToolUseReview.java) | 이벤트 JSON을 읽고 종료 코드에 따라 경고와 추가 지침을 만들어 반환합니다. |
| [build.gradle](build.gradle) | `prepareHook`는 실행에 필요한 Gson을 준비하고, `hookTest`는 Hook 처리 코드의 동작을 검사합니다. |
| [.codex/hooks/tests/PostToolUseReviewTest.java](.codex/hooks/tests/PostToolUseReviewTest.java) | 성공·실패·종료 코드 누락·잘못된 입력·로그 필드를 검사합니다. |

`PostToolUse`는 도구가 결과를 반환한 뒤의 이벤트입니다. `Bash`는 여기서 명령 실행 도구를 고르는 이름이며 Windows에 Bash를 설치하라는 뜻이 아닙니다. Codex의 `exec_command`도 이 이름으로 매칭됩니다. [공식 도구 매칭 안내](https://learn.chatgpt.com/docs/hooks#tool-coverage)

`hooks.json`의 `commandWindows`와 `command`에는 모두 다음 명령이 들어 있습니다. 작업 위치는 `quality_demo/`입니다.

```text
java --class-path ".local/hook-runtime/*" ".codex/hooks/PostToolUseReview.java"
```

`commandWindows`는 Windows에서, `command`는 macOS·Linux에서 사용할 명령입니다. 실행 제한 시간은 `timeout: 30`으로 30초이며, `statusMessage`는 `Reviewing command result`입니다. 이 시간 제한은 Hook 프로그램 한 번의 실행에 적용됩니다.

### 명령 결과가 모델에 돌아가는 경로

정의가 신뢰되고 Hook 연결이 동작하면 다음 순서로 처리됩니다. 이벤트 정보는 표준 입력으로 들어오는 JSON이며, Java Hook이 표준 출력에 쓴 JSON을 Codex가 피드백으로 읽습니다. [공식 입력·출력 규약](https://learn.chatgpt.com/docs/hooks#common-input-fields)

```text
Codex 도구로 명령 실행
  → 도구 결과 반환, PostToolUse 이벤트
  → hooks.json의 Bash 조건에 매칭
  → PostToolUseReview.main() → run()
      → 이벤트 JSON 읽기
      → safeRecord()로 최소 이벤트 정보를 추려 로그에 기록
      → hookOutput()이 commandExitCode()로 코드를 읽고 피드백 생성
      → 피드백 JSON 출력
  → Codex가 경고를 표시하고 모델에 추가 지침 전달
  → 모델이 실제 명령 출력과 지침을 읽고 다음 행동 선택
```

예를 들어 명령이 실패했을 때 사용하는 필드만 추리면 다음과 같습니다. 전체 이벤트에는 작업 위치 등의 정보도 포함됩니다.

```json
{"tool_name":"Bash","tool_response":{"exit_code":1}}
```

`commandExitCode()`는 `tool_response.exit_code`를 읽습니다. 코드에는 `toolResponse.exitCode` 표기도 처리하도록 되어 있습니다. `hookOutput()`이 만드는 결과는 다음과 같습니다.

| 읽은 명령 종료 코드 | 이 프로젝트의 Hook이 반환하는 내용 |
|---|---|
| `0` | 빈 JSON 객체 `{}`. 추가 경고나 지침을 만들지 않습니다. |
| `0`이 아닌 정수 | 실패 코드 경고와 원인 구분·수정·같은 검사 재실행 안내를 만듭니다. |
| 없거나 읽을 수 없는 값 | 종료 코드를 확인하지 못했다는 경고와 실행 완료 여부 확인 안내를 만듭니다. |

경고는 `systemMessage`, 모델에게 추가할 지침은 `hookSpecificOutput.additionalContext`에 들어갑니다. 예시 입력의 `1`에는 “명령이 종료 코드 1로 실패했습니다.”라는 경고와 실행 환경 문제인지 기대값 불일치인지 구분하고 재검사하라는 지침이 생성됩니다. Codex는 경고를 표시하고 추가 지침을 모델의 맥락에 넣습니다. [공식 PostToolUse 출력 규약](https://learn.chatgpt.com/docs/hooks#posttooluse)

**실패한 명령의 종료 코드와 Hook 프로그램의 종료 코드는 별개입니다.** 입력의 명령 코드가 `1`이어도 피드백을 정상적으로 만든 `run()`은 `0`으로 끝납니다. 이는 Hook 처리가 끝났다는 뜻이며, 원래 명령이 성공으로 바뀐 것은 아닙니다.

### 검사·작업 지침·Hook의 역할

프로젝트 검사는 요청과 실제 결과가 맞는지 판정합니다. `AGENTS.md`는 어떤 검사를 언제 실행하고 실패하면 어떻게 대응할지 안내합니다. 이 Hook은 명령 결과가 돌아오는 시점에 실패 대응 안내를 더합니다. 명령 출력은 Hook이 없어도 모델에 전달되므로, Hook의 추가 지침이 실제 후속 행동에 도움이 되는지 보고 사용할 이유를 판단합니다.

**이 예제 Hook의 코드는 배치 요청이나 환불 결과를 대조하지 않습니다.** 정상 종료한 출력이 8행인지 3행인지 확인하지 않고 종료 코드 `0`에 `{}`를 반환합니다. 누락을 잡으려면 입력과 실제 출력을 대조하는 검사가 필요하며, 그 판정은 도구 출력으로 직접 전달할 수도 있습니다.

이 예제 Hook은 피드백을 반환하고, 다음 명령 요청은 그 피드백을 읽은 모델이 결정합니다. 따라서 실제 수정·재검사는 모델이 도구를 다시 사용했는지 확인해야 합니다. 경고 표시나 추가 지침 전달만으로 완료 보고를 강제로 차단했다고 판단하지 않습니다.

### 준비와 실제 연결 확인

Hook을 처음 연결하기 전에 IDE의 `prepareHook`, `hookTest`를 실행하거나 다음 명령을 사용합니다.

Windows PowerShell:

```powershell
.\gradlew.bat prepareHook hookTest
```

macOS·Linux·WSL:

```bash
./gradlew prepareHook hookTest
```

`prepareHook`는 `.local/hook-runtime/`에 Gson을 준비합니다. Hook 이벤트에서는 준비된 라이브러리와 Java 소스 파일을 직접 실행합니다. 업무 코드를 빌드하지 않아 업무 소스가 깨져도 실패 피드백을 만들 수 있고, Hook 안에서 Gradle을 다시 호출하는 반복도 피합니다. 준비한 의존성을 지웠다면 연결 전에 다시 준비합니다.

프로젝트를 신뢰하고, 사용하는 Codex의 Hook 목록에서 정의와 실행 명령을 검토·신뢰한 뒤 활성화합니다. CLI에서는 `/hooks`를 사용할 수 있습니다. Hook 정의가 바뀌면 다시 검토합니다. `hooks = true`라는 설정과 해당 정의의 신뢰·자동 실행은 구분해서 확인합니다. [공식 Hook 검토·신뢰 안내](https://learn.chatgpt.com/docs/hooks#review-and-trust-hooks)

실제 연결은 Codex 도구로 명령을 실행한 뒤 Hook의 피드백과 뒤따른 모델의 행동을 보고 확인합니다. `hookTest`나 예시 JSON을 넣은 직접 실행은 처리 코드의 검사입니다. 사람이 터미널에서 직접 실행한 명령은 Codex 이벤트 연결의 근거가 되지 않습니다. `exec_command`라는 도구명이 표시된다는 이유만으로 `Bash` matcher를 바꾸지 않고, 이벤트 입력이 코드가 읽는 형태인지 확인합니다.

`run()`은 이벤트의 작업 위치를 기준으로 `../.local/raw/hook-events.jsonl`에 도구 이름·종료 코드·시각·턴 식별자를 기록합니다. 명령·출력 원문은 저장하지 않습니다. 로그는 호출을 대조하는 보조 근거이고, 모델이 지침을 받아 적절히 행동했는지는 실제 다음 도구 실행과 보고로 확인합니다.

### 이 실습의 연결 확인 상태

Day 4 초기 점검에서는 Hook 코드 검사 5개와 예시 실패 입력의 피드백 생성을 확인했습니다. Codex 명령 뒤의 Hook 피드백과 이벤트 기록은 관찰되지 않아 자동 연결은 미확인입니다. 후속 연결 상태와 이 구성을 사용할지에 대한 판단은 [학습 기록](../failure-recovery.md#day-4--완료-검사를-실행하고-판정을-다음-행동에-연결하기)에 이어 기록합니다.

## Windows에서 Gradle 소켓 오류가 날 때

Codex에서 Gradle이 컴파일·테스트를 시작하기 전에 `Unable to establish loopback connection`으로 실패하고, 상세 오류에 `UnixDomainSockets.connect0`와 `Invalid argument: connect`가 함께 나오면 아래 방법을 사용할 수 있습니다. 같은 명령을 Windows 시작 메뉴에서 직접 연 PowerShell에서도 비교합니다. [Codex의 유사 오류 보고](https://github.com/openai/codex/issues/40902)

Java는 내부 통신용 소켓의 주소로 임시 폴더의 경로를 사용합니다. 이 증상에서는 기본 임시 폴더의 일반 파일 읽기·쓰기가 가능해도 소켓 연결은 실패할 수 있습니다. `jdk.net.unixdomain.tmpdir`로 소켓용 폴더만 지정하면 일반 `TEMP`·`TMP`와 다른 도구의 임시 파일 위치를 유지할 수 있습니다. Windows 내부 원인을 고치는 설정은 아니며, `java.io.tmpdir`만 바꾸는 것과도 다릅니다. [Java의 소켓 주소용 폴더 선택 순서](https://docs.oracle.com/en/java/javase/24/core/java-networking.html)

### 5주차에만 적용하기

1. 소켓용 폴더를 만들고 설정에 넣을 경로를 확인합니다. 기본 Codex 데이터 폴더 안에 이 실습용 `shell-tmp` 폴더를 추가하는 Windows PowerShell 명령입니다.

   ```powershell
   $socketTemp = Join-Path $env:USERPROFILE '.codex\shell-tmp'
   New-Item -ItemType Directory -Force -Path $socketTemp | Out-Null
   $socketTemp.Replace('\', '/')
   ```

2. 5주차 폴더의 `.codex/config.toml`에 아래 항목을 추가합니다. 이 프로젝트에서 보면 `../.codex/config.toml`이며, 예제 Hook 설정이 있는 `quality_demo/.codex/config.toml`과는 다른 파일입니다. 자리표시자를 위 명령에서 확인한 실제 경로로 바꿉니다.

   ```toml
   [shell_environment_policy.set]
   JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir="<소켓용 폴더의 실제 경로>"'
   ```

   같은 표나 `JAVA_TOOL_OPTIONS`가 이미 있으면 중복으로 만들지 말고 기존 옵션에 합칩니다. 이 오류를 피하려고 앞서 추가한 `TEMP`·`TMP` 재지정은 제거합니다. 개인 전역 `config.toml`에도 같은 우회 설정이 남아 있다면 제거해야 다른 프로젝트에 적용되지 않습니다.

3. 개인 경로가 들어간 이 파일은 Git에 공유하지 않습니다. 학습 저장소의 `.git/info/exclude`에 다음 한 줄을 추가합니다. 이미 Git이 추적하는 파일은 제외 규칙만으로 숨겨지지 않으므로 추적 여부를 확인합니다.

   ```gitignore
   /week05-development-harness/.codex/config.toml
   ```

4. Codex를 다시 열고 `quality_demo/`를 작업 폴더로 사용합니다. 프로젝트를 신뢰한 상태에서 설정을 읽으면 기존 `.\gradlew.bat -q run`과 `.\gradlew.bat test`를 그대로 실행할 수 있습니다. 별도 실행 스크립트는 필요 없습니다.

적용 범위는 **5주차 폴더 또는 그 아래에서 시작한 Codex 작업과 그 하위 프로그램**입니다. 다른 프로젝트에서 시작한 작업에는 적용되지 않습니다. 같은 작업에서 `cd`로 이동하는 것만으로 환경변수가 해제되지는 않습니다. JVM이 시작할 때 옵션 적용 안내가 표준 오류에 출력될 수 있으므로, 로그를 공유할 때는 안내에 포함된 개인 경로를 자리표시자로 바꿉니다. [Codex의 프로젝트별 설정](https://learn.chatgpt.com/docs/config-file/config-advanced#project-config-files-codexconfigtoml)

### 학습 후 설정과 임시 폴더 정리하기

`%USERPROFILE%/.codex/shell-tmp`는 이 설정을 위해 만든 작업용 폴더입니다. 남은 파일이 자동으로 정리된다고 보장할 수 없으므로, 학습을 마쳤거나 앱 업데이트 후 기본 환경을 다시 확인할 때 다음 순서로 정리합니다.

1. 진행 중인 빌드·Java 실행을 끝내고, `quality_demo/`에서 `.\gradlew.bat --stop`으로 Gradle 데몬을 종료합니다.
2. `../.codex/config.toml`에서 추가한 소켓용 `JAVA_TOOL_OPTIONS` 옵션을 제거합니다. 이 설정만 있던 파일이면 파일을 삭제해도 됩니다. 다른 옵션·Hook 설정은 보존합니다.
3. Codex를 다시 열어 기존 작업에 남아 있던 환경도 해제합니다. 다른 작업에서 같은 임시 폴더를 사용 중이면 그 작업이 끝난 뒤 정리합니다.
4. 파일 탐색기 주소 표시줄에 `%USERPROFILE%\.codex\shell-tmp`를 입력하고, 이 `shell-tmp` 폴더만 삭제합니다. 상위 `.codex` 폴더에는 Codex의 설정·인증·작업 기록이 있으므로 통째로 삭제하지 않습니다.

학습 중 이 설정을 제거한 뒤 같은 소켓 오류가 다시 나면, 위 적용 절차로 폴더와 설정을 다시 준비합니다.

## 주요 파일

- `src/main/java/lab/week05/Refund.java`: 기본 차감·하한·면제 계산과 음수 입력 거부.
- `src/test/java/lab/week05/RefundTest.java`: 기존 세 검사와 A의 업무 규칙 검사.
- `RefundInput.java`, `RefundInputTest.java`: A의 JSON 입력 경계와 검사. 각 main/test 패키지에 있습니다.
- `BatchRefund.java`, `BatchRefundTest.java`: B의 파일 처리 진입점과 실제 프로세스의 출력·종료 코드 검사. 각 main/test 패키지에 있습니다.
- `BatchResultCheck.java`, `BatchResultCheckTest.java`: 입력·기대 결과·저장된 출력의 공통 비교와 판정 검사. `JavaProcessFixture.java`는 두 테스트에서 Java 실행과 출력 수집을 재사용합니다.
- `data/`: A·B의 업무 입력과 기대 결과.
- `AGENTS.md`, `build.gradle`: 작업 지침과 실행·검사 구성.
- `.codex/config.toml`, `.codex/hooks.json`: Hook 기능과 이벤트 연결 설정.
- `.codex/hooks/PostToolUseReview.java`, `.codex/hooks/tests/`: 결과 피드백과 독립 검사.
- `../failure-recovery.md`: 선택 이유·실제 결과·실패 대응·재사용 근거를 누적하는 주차 노트.
