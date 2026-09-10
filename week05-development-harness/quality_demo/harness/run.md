# 요청 파일로 개발 하네스 실행하기

`refund-work.json`으로 작업 목적·맥락·완료 검사·복구 범위를 정하고, 순서 함수로 Codex 작업과 검사를 연결한다. 정해진 작은 작업을 처리하며 최종 상태·실패 단계·이유·시도 수를 반환하는 구성이다.

## 파일과 실행 경로

| 파일 | 책임 |
|---|---|
| `refund-work.json` | 작업 폴더·요구 파일·맥락 파일·운영체제별 검사 명령·복구 상한·시간 제한 |
| `task.md`, `contract.md`, 프로젝트 `AGENTS.md` | 작업 목적과 수용 기준, 반복해서 적용할 규칙 |
| `src/main/java/lab/week05/harness/WorkRequest.java` | 요청을 읽고 실행 위치와 실제 맥락 본문을 준비 |
| `DevelopmentHarness.java` | 준비 → 작업 → 검사 → 필요한 복구를 순서대로 실행하고 종료 결과 반환 |
| `HarnessCli.java` | 자기 엔진을 시작하고 Codex·기존 `ProcessRunner`·검사를 연결 |
| `GradleVerifier.java` | 이번 Gradle 검사 결과로 업무 검사 실패와 실행 불가를 구분 |

`workspace`는 요청 JSON 파일의 폴더를 기준으로 해석한다. 제공 요청의 `".."`는 `quality_demo/`를 가리킨다. `task`와 `context`의 경로는 그 작업 폴더가 기준이다. 요구와 규칙의 본문을 첫 요청에 붙이고, 상세 코드와 입력은 Codex가 작업 폴더에서 읽는다.

실행 결과는 시작한 폴더의 `.local/development-harness/<실행 식별자>/result.json`에 남는다. 매번 새 식별자를 사용하며 과거 결과를 현재 성공으로 재사용하지 않는다. `mode`는 실제 Codex 프로세스와 교육용 응답을 구분한다. 실제 실행에서는 각 시도의 마지막 모델 응답이 같은 폴더의 `agent-1.txt` 등에 남고, 전체 대화를 따로 수집하지 않는다.

## 준비와 시작

`quality_demo/`를 JDK 17 이상 Gradle 프로젝트로 연다. IDE에서 `developmentHarnessJar`를 실행하면 JAR가 만들어진다. 엔진은 JAR로 시작하므로 실행 중인 Gradle 작업 안에서 같은 프로젝트의 Gradle 검사를 중첩 실행하지 않는다.

Windows PowerShell:

```powershell
.\gradlew.bat developmentHarnessJar
java -jar build/libs/development-harness.jar harness/refund-work.json
```

macOS·Linux·WSL:

```bash
./gradlew developmentHarnessJar
java -jar build/libs/development-harness.jar harness/refund-work.json
```

이 명령은 **실제 Codex 작업**을 실행한다. 설치·로그인과 계정 사용량이 필요하다. 현재 설정된 모델을 사용하며, 파일·명령 도구는 Codex에 맡긴다. 연결한 CLI의 `workspace-write` 범위에서 실행하고 승인 없이 수행할 수 없는 동작은 실패할 수 있다. Windows의 프로젝트 로컬 소켓 설정은 [기존 안내](../windows-gradle.md)를 따른다.

다음 명령은 두 운영체제에서 같은 형태로 실행한다. 실행 경계에 정해진 응답을 넣어 검사 실패 → 복구 → 재검사 통과를 확인한다.

```text
java -jar build/libs/development-harness.jar harness/refund-work.json --demo-repair
```

`--demo-repair`에서는 실제 모델이나 Gradle 검사를 호출하지 않는다. 자기 엔진의 기본 흐름을 사용하며, 기대 결과는 `SIMULATED_RESPONSE`, `SUCCEEDED`, 작업 시도 2회다. 실제 업무 검사에는 별도로 IDE의 `test` 또는 Windows `.\gradlew.bat test`, macOS·Linux·WSL `./gradlew test`를 사용한다.

## 결과와 중단의 의미

| 결과 | 의미와 다음 행동 |
|---|---|
| `SUCCEEDED` / 종료 코드 0 | 작업 뒤 검사가 통과함. `mode`로 실제 실행인지 교육용 응답인지 확인 |
| `NEEDS_INPUT` / 종료 코드 2 | 작업 파일·맥락·검사 명령 등 준비에 필요한 정보를 보충 |
| `STOPPED` / 종료 코드 1 | 실패 단계와 근거를 확인해 다음 조치를 판단 |

`stage`는 준비(`PREPARE`), 작업 실행(`EXECUTE`), 검사(`VERIFY`) 중 결과가 결정된 위치다. `attempts`는 시작을 시도한 Codex 작업 수다. 실행 시작 불가·시간 초과는 자동 코드 복구로 이어지지 않는다.

이번 요청은 `maxRepairs: 1`로 최초 작업 이후 한 번의 복구를 허용한다. 0은 복구 없이 중단하며 현재 입력 계약은 실습 범위인 0~1을 받는다. `timeoutSeconds`는 개별 프로세스의 대기 한도다. 품질은 수용 기준과 검사 범위로 판단하고, 복구 상한은 자동 시도 범위를 정한다. 복구한 뒤에도 검사가 실패하면 완료로 반환하지 않는다.

`StepRunner`는 도구 호출을 담당하는 작은 연결부다. `StepResult`는 통과·업무 검사 실패·실행 불가·시간 초과와 원래 종료 코드·출력을 엔진에 전달한다. 순서 엔진은 제공 예제의 `Job`이나 상태 전이 표를 사용하지 않으며, 업무 검사 실패일 때만 복구 여부를 판단한다.

요청의 `verify`는 이 프로젝트의 Gradle `test` 명령을 담는다. `GradleVerifier`는 `--rerun-tasks`와 `--no-build-cache`를 붙여 새 검사를 실행하고, 같은 실행 폴더의 `check-1/` 등에 JUnit 결과를 받는다. 새 JUnit 실패 결과와 비정상 종료가 함께 확인될 때 업무 검사 실패로 분류한다. 프로그램을 시작하지 못했거나 새 검사 근거가 없으면 실행 불가로 중단한다. 셸의 종료 코드 1만으로 테스트 실패라고 판단하지 않는다.
