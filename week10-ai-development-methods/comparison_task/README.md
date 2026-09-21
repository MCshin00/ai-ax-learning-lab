# 다섯 실행 방식의 공통 과제

고객 지원 목록에는 서로 다른 시스템에서 받은 우선순위와 제목이 섞여 있습니다. 표시 규칙을 일정하게 만들되 빈 제목에는 우선순위 표시보다 정보 보충 안내가 먼저 나와야 합니다. 동일한 시작 코드를 M1~M5에 각각 사용합니다.

## 요구와 제공 코드

| 입력 또는 조건 | 기대 동작 |
|---|---|
| `P0`, `urgent` | 대소문자·앞뒤 공백을 정리해 `P0` |
| `P1`, `normal` | `P1` |
| `P2`, `low` | `P2` |
| 우선순위가 없거나 알 수 없음 | `P1` |
| 제목의 앞뒤·연속 공백·탭·줄바꿈 | 앞뒤는 제거하고 내부 연속 공백은 한 칸 |
| 제목이 없거나 공백뿐 | 전체 표시가 `확인 필요`; 우선순위 접두어 없음 |
| 나머지 | `[정리된 우선순위] 정리된 제목` |

이 실습에서 정리할 공백은 공백 문자·탭·줄바꿈을 포함한 Java 정규식 `\s` 범위입니다. 요청 문자열은 표시 자료로 다룹니다.

제공 시작 코드에서 `Priority.normalize()`와 `Title.normalize()`는 입력을 그대로 반환하고, `Summary.render()`는 두 반환값을 붙입니다. 이 골격은 컴파일과 기존 표시가 가능하지만 새 정리 규칙은 아직 구현하지 않았습니다. `SummaryTest`는 기존 표시를, `RequirementTest`는 위 요구의 공개 수용 사례 5개를 확인합니다. 예를 들어 두 정리 함수가 각자 맞아도 빈 제목에 `[P0]`가 남으면 **통합 규칙**이 빠진 것입니다.

## 프로젝트 구조

| 위치 | 역할 |
|---|---|
| `src/main/java/lab/week10/Priority.java` | 우선순위 입력을 정리하는 함수 |
| `src/main/java/lab/week10/Title.java` | 제목 입력을 정리하는 함수 |
| `src/main/java/lab/week10/Summary.java` | 두 결과를 조합하는 `render()`와 명령행 진입점 `main()` |
| `src/test/java/lab/week10/SummaryTest.java` | 기존 표시 검사 1개 |
| `src/test/java/lab/week10/RequirementTest.java` | 새 요구의 공개 수용 검사 5개 |
| `build.gradle` | JUnit 의존성, 컴파일·검사·앱 실행 작업 설정 |
| `settings.gradle` | Gradle 프로젝트 이름 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/` | 지정된 Gradle 버전을 실행하는 Wrapper와 설정 |
| `build/`, `.gradle/` | 실행 후 생기는 컴파일 결과·검사 보고서와 작업용 캐시 |

앱 실행 경로는 명령행 인자 두 개 → `Summary.main()` → `Summary.render()` → 우선순위·제목 정리 → 표시 문자열 반환·출력입니다. 테스트는 `render()`를 직접 호출해 반환값을 비교합니다. 명령행 입력과 출력의 연결은 아래 앱 실행 예로 확인합니다.

## test와 acceptance가 하는 일

`acceptance`는 수용 테스트(인수 테스트)를 뜻하며, 결과물이 업무 요구를 만족하는지 확인합니다. 두 작업 모두 JUnit 테스트를 실행합니다. 이 프로젝트의 `build.gradle`은 `test`에서 `RequirementTest`를 제외하고, 별도로 정의한 `acceptance` 작업에서는 그 클래스만 실행하도록 설정합니다.

| 실행 작업 | 입력과 기대값의 예 | 확인 목적 |
|---|---|---|
| `test` | `P1`, `결제 오류` → `[P1] 결제 오류` | 기존 표시 유지 |
| `acceptance` | `LOW`, `화면 개선` → `[P2] 화면 개선` | 새 요구 충족 |

IDE의 Gradle 화면에서 작업을 실행하면 필요한 앱 코드 컴파일(`compileJava`) → 테스트 코드 컴파일(`compileTestJava`) → 선택한 검사 실행으로 이어집니다. 입력과 기대값은 테스트 코드에 있으므로 별도로 입력하지 않습니다. `acceptance`만 실행하면 `test`까지 자동 실행되지는 않습니다. 두 작업을 함께 요청하면 `test` 다음 `acceptance` 순서로 실행합니다.

시작 코드에서 새 검사가 `Expected: [P2] 화면 개선`, `Actual: [LOW] 화면 개선`으로 실패한다면, 기대값과 실제 반환값의 차이가 아직 없는 변환 기능을 보여 줍니다. 반면 컴파일·의존성 다운로드 오류라면 검사를 실행하기 전의 문제입니다. 구현 후에는 두 작업 모두 통과해야 합니다.

`PASSED`·`FAILED`는 개별 검사 결과이고 `BUILD SUCCESSFUL`은 요청한 작업의 성공입니다. `test`만 성공했다고 새 요구까지 충족한 것은 아닙니다. `UP-TO-DATE`는 변경이 없어 해당 작업을 다시 수행하지 않았다는 뜻이고, `NO-SOURCE`는 그 단계에서 처리할 소스가 없다는 뜻입니다. 보고서는 `build/reports/tests/test/index.html`과 `build/reports/tests/acceptance/index.html`에 생성됩니다.

## 실행

IDE에서 JDK 17 이상의 Gradle 프로젝트로 열고 Gradle 화면에서 `test`와 `acceptance`를 실행합니다. 시작 상태에서는 기존 표시를 확인하는 `test`가 통과하고, 새 요구를 확인하는 `acceptance`는 실패합니다.

앱 출력은 Gradle의 `run` 작업에 인자 `" Urgent " "  결제   오류  "`를 전달해 확인합니다. 구현 후 기대 출력은 `[P0] 결제 오류`입니다. 터미널에서 검사와 앱 실행을 연결하거나 하네스에 실행 명령을 지정할 때는 아래 명령을 사용합니다.

Windows PowerShell (`--%`로 PowerShell의 인자 해석을 멈추고, 제목 안의 공백을 포함해 Gradle에 전달합니다):

```powershell
.\gradlew.bat test
.\gradlew.bat acceptance
.\gradlew.bat --% -q run --args="\" Urgent \" \"  결제   오류  \""
```

macOS·Linux·WSL:

```bash
./gradlew test
./gradlew acceptance
./gradlew -q run --args='" Urgent " "  결제   오류  "'
```

모든 방식에서 같은 `test acceptance`를 사용합니다. 검사 코드·요구·시작 상태를 유지하고 역할·진행 순서·실행 제어를 바꿉니다. 방법별 실행 지침은 [주차 README](../README.md)의 Day 2~4에 있습니다.

## Windows Codex의 소켓 오류

Gradle이 검사 전에 `Unable to establish loopback connection`으로 실패하고 상세 오류에 `UnixDomainSockets.connect0`와 `Invalid argument: connect`가 함께 나오면, 이 프로젝트의 로컬 `.codex/config.toml`에 소켓용 폴더를 지정합니다. M1~M5에서 같은 설정을 사용합니다.

```toml
[shell_environment_policy.set]
JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir="<소켓용 폴더의 실제 경로>"'
```

파일 탐색기로 쓰기 가능한 소켓용 폴더를 준비하고 IDE에서 실제 경로를 넣습니다. 기존 설정이 있으면 이 옵션을 합칩니다. 개인 경로가 들어가는 설정 파일은 이 프로젝트의 `.gitignore`에 `/.codex/config.toml`을 추가해 공유에서 제외하고, 이미 추적 중인지도 확인합니다. Codex를 다시 열고 프로젝트를 신뢰한 상태에서 기존 검사 작업을 실행합니다.

M4의 새 Worktree에도 같은 로컬 설정을 준비합니다. Git 제외 파일은 Worktree에 복사되지 않기 때문입니다. 캐시 파일의 접근 거부가 발생하면 실행 계정과 파일 권한을 확인합니다.

## 비교 자료 준비

주차 폴더에서 IDE나 파일 탐색기로 `comparison_task/`의 소스·Wrapper·README를 `runs/M1`~`runs/M5`에 각각 복사합니다. `build/`, `.gradle/`, `.local/`은 제외합니다. 복사한 README의 주차 본문 링크는 `../../README.md`로 맞춥니다. M4 안에서 Git 초기 커밋을 만들고 Worktree로 작업을 나눕니다. 각 방식은 새 Codex 작업에서 같은 모델·설정과 공통 요구로 시작합니다.

각 방식은 한 번씩 수행합니다. M3의 여러 역할 호출, M4의 병렬 작업, M5의 허용된 복구는 해당 한 번의 수행 안에 포함합니다. 결과가 불리하다고 초기 상태로 되돌려 유리한 실행을 골라 쓰지 않습니다. 환경 문제로 진행하지 못하면 그 사실과 조건을 남깁니다.
