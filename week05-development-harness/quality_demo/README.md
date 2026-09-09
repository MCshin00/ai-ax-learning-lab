# 환불 계산 작업 환경

실제 결제 서비스와 연결하지 않는 작은 Java 프로젝트입니다. 개인 프로젝트나 API 키 없이 업무 요구를 AI와 구현하고 지침·검사·실패 피드백을 조정합니다. Day별 개념과 진행 순서는 [주차 README](../README.md)에 있습니다.

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

`Refund.refundAmount(paid, fee)`는 유효한 두 금액의 차감을 계산하고 결과의 하한을 0으로 둡니다. 음수 입력은 계산 전에 거부합니다. 정수 원 단위이며 기본 출력은 `8000`, 시작 테스트는 세 개이며 현재 A까지의 전체 업무 검사는 11개입니다.

**현재 Day 2의 요구 A까지 완료했습니다.** 수수료 면제와 JSON 입력 경계, 제공 자료를 연결한 검사가 구현되어 있습니다. 요구 B는 다음 활동으로 남아 있습니다. 아래 시작 설명은 제공본의 기준이며 현재 구현의 자동 검증 결과는 `../failure-recovery.md`에 있습니다.

## 요구 A: 수수료 면제 조건 추가

고객 지원 담당자가 승인한 환불에는 수수료를 면제할 수 있어야 합니다. 중요한 원리는 **면제가 계산 방법을 바꾸어도 입력 계약은 유지한다**는 것입니다.

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

A를 완료한 뒤 JSON 파일 하나의 여러 요청을 처리하도록 확장합니다. 이 활동을 시작하기 전에는 구현하지 않습니다.

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

## Hook: 명령 결과를 다음 판단에 연결하기

Day 4~5에는 **결과 누락을 완료로 보고하지 않도록 작업 환경을 보완하는 요구**를 진행합니다. 판정할 위치와 피드백을 전달할 위치를 AI와 선택합니다. 제공 Hook을 연결하는 것만으로 이 설계를 대신하지 않습니다.

`.codex/hooks.json`은 `PostToolUse` 이벤트를 `.codex/hooks/PostToolUseReview.java`에 전달합니다. 실패 종료 코드는 짧은 경고와 원인 구분·수정·재검사 지침으로 바꿉니다. 종료 코드가 없으면 성공으로 간주하지 않고 실행 완료 여부를 확인하도록 알립니다. 종료 코드 0은 `{}`를 반환하므로 결과 누락 자체를 찾지 못합니다.

Hook은 검사를 직접 시작하거나 코드를 복구하거나 작업 종료를 강제로 막지 않습니다. 실제 결과를 읽고 다음 행동을 정하는 모델까지 연결되었는지 확인합니다. 이벤트 입력을 읽는 데에는 Gson을 사용합니다.

Hook을 처음 연결하기 전에 IDE의 `prepareHook`, `hookTest`를 실행하거나 다음 명령을 사용합니다.

Windows PowerShell:

```powershell
.\gradlew.bat prepareHook hookTest
```

macOS·Linux·WSL:

```bash
./gradlew prepareHook hookTest
```

`prepareHook`는 `.local/hook-runtime/`에 의존성을 준비합니다. 실제 Hook 이벤트에서는 다음 명령으로 Java 소스 파일만 실행합니다. 업무 코드를 빌드하지 않아 업무 소스가 깨진 경우에도 실패 피드백을 만들 수 있습니다. 이벤트 안에서 Gradle을 호출하지 않아 검사→Hook→검사의 반복도 만들지 않습니다. 준비한 의존성을 지웠다면 연결 전 다시 준비합니다.

```text
java --class-path ".local/hook-runtime/*" ".codex/hooks/PostToolUseReview.java"
```

사용하는 Codex 표면의 Hook 목록에서 정의를 검토·신뢰한 뒤 사용합니다. 변경한 정의는 다시 검토가 필요할 수 있습니다. 제공 matcher는 `Bash`이며 실제 관찰한 도구 이름이나 payload가 다르면 맞춥니다. 작업 위치는 이 프로젝트 폴더입니다. 사람이 터미널에서 직접 실행한 명령은 Codex 이벤트가 아니므로 Host 연결의 증거가 아닙니다. 지원하지 않는 표면에서는 직접 검사와 지침을 활용하고 Hook 연결은 미확인으로 남깁니다.

`hookTest`는 실패·성공·종료 코드 없음·잘못된 payload·최소 로그 필드를 검사합니다. 이것은 코드 검사이며 실제 Host 연결이나 모델의 후속 행동을 증명하지 않습니다. 로그 `../.local/raw/hook-events.jsonl`에는 도구 이름·종료 코드·시각·턴 식별자만 남기고 원시 명령·출력은 기록하지 않습니다. [공식 Hook 안내](https://learn.chatgpt.com/docs/hooks)

## 주요 파일

- `src/main/java/lab/week05/Refund.java`: 시작 환불 계산과 A에서 확장할 업무 규칙.
- `src/test/java/lab/week05/RefundTest.java`: 기존 세 검사와 A의 업무 규칙 검사를 둘 위치.
- `RefundInput.java`, `RefundInputTest.java`: A에서 추가할 JSON 입력 경계와 검사. 각 main/test 패키지에 둡니다.
- `BatchRefund.java`: B에서 추가할 파일 처리 진입점. 시작 자료에는 없습니다.
- `data/`: A·B의 업무 입력과 기대 결과.
- `AGENTS.md`, `build.gradle`: 작업 지침과 실행·검사 구성.
- `.codex/config.toml`, `.codex/hooks.json`: Hook 기능과 이벤트 연결 설정.
- `.codex/hooks/PostToolUseReview.java`, `.codex/hooks/tests/`: 결과 피드백과 독립 검사.
- `../failure-recovery.md`: 선택 이유·실제 결과·실패 대응·재사용 근거를 누적하는 주차 노트.
