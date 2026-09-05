# Java 17 벤치마크 시작 코드

Codex 작업 방식을 비교할 때 사용하는 작은 Java 프로젝트입니다. 환불, 계정 잠금 해제, 구독 해지라는 세 작업의 핵심 구현은 의도적으로 비어 있으므로 처음에는 공개 테스트가 실패합니다. 각 작업 계약을 읽고 허용된 범위만 구현한 뒤 같은 JUnit 테스트로 결과를 확인합니다.

## 프로젝트 구성

```text
build.gradle
settings.gradle
gradlew, gradlew.bat
gradle/wrapper/
src/main/java/lab/benchmark/
src/test/java/lab/benchmark/
```

- `build.gradle`은 Java 17 컴파일 조건과 JUnit 공개 테스트를 정의합니다.
- `src/main/java`에는 과제별 시작 코드가 있습니다.
- `src/test/java`에는 IDE에서 바로 실행할 수 있는 JUnit 공개 테스트가 있습니다.
- Gradle Wrapper는 IDE와 자동화가 같은 Gradle 버전을 쓰게 합니다. 전역 Gradle 설치는 필요하지 않습니다.

## IDE에서 실행하기

1. VS Code나 IntelliJ에서 `shared/benchmark/app` 폴더를 Gradle 프로젝트로 엽니다.
2. Gradle 동기화가 끝나면 `src/test/java`의 테스트 클래스를 엽니다.
3. 클래스나 메서드 옆 실행 버튼을 눌러 필요한 공개 테스트만 실행합니다.
4. 세 작업을 한꺼번에 확인하려면 Gradle 도구 창에서 `verification > test`를 실행합니다.
5. 테스트 창에서 실패한 메서드, 예외와 stack trace를 확인합니다.

처음 동기화할 때는 Gradle과 JUnit을 내려받기 위해 인터넷 연결이 필요합니다. Wrapper의 운영체제별 실행 파일을 직접 다루지 않아도 IDE에서 컴파일과 테스트를 모두 수행할 수 있습니다. 자동화나 독립 검증에서는 같은 프로젝트의 Gradle `test` 작업을 실행합니다.

`build.gradle`의 `mavenCentral()`은 JUnit 의존성을 받을 저장소일 뿐입니다. 빌드 도구는 Maven이 아니라 Gradle입니다.

공개 테스트는 구현 중 빠른 피드백을 제공합니다. 10주차 본 실험에서는 구현 작업과 분리한 비공개 평가기가 추가 엣지 케이스를 검사합니다.

## 과제별 결정적 검증

한 과제만 구현할 때 전체 `test`를 실행하면 아직 TODO인 다른 과제 때문에 실패합니다. 계약의 과제별 명령으로 해당 공개 테스트 클래스만 실행합니다.

| 과제 | 테스트 클래스 |
|---|---|
| TASK-A | `lab.benchmark.refund.RefundServicePublicTest` |
| TASK-B | `lab.benchmark.account.AccountUnlockServicePublicTest` |
| TASK-C | `lab.benchmark.cancellation.CancellationJobServicePublicTest` |

Windows 예: `gradlew.bat clean test --tests "lab.benchmark.refund.RefundServicePublicTest"`

macOS·Linux·WSL 예: `./gradlew clean test --tests "lab.benchmark.refund.RefundServicePublicTest"`

세 과제를 모두 구현한 경우에만 필터 없는 `clean test`를 전체 회귀 검증으로 사용합니다. 완료 기록에는 테스트 메서드 통과·실패 수와 실행 명령을 남기고, 실행하지 못한 기준은 `NOT_VERIFIED`로 둡니다.

## 공개 테스트 원본과 추가 테스트

과정이 제공한 `*PublicTest.java`는 그대로 보존합니다. 새 경계·회귀 검증은 별도 `*Test.java` 파일에 추가하고 IDE에서 해당 파일을 함께 실행합니다. 원본 보존 검사는 UTF-8 BOM과 줄바꿈 차이를 제외한 내용을 과정 원본과 대조합니다. 주석에 테스트 이름만 남기는 방식으로는 통과하지 않습니다. 원본 보존과 실제 기능 테스트 결과는 각각 확인합니다.

기본 과제 명령은 해당 공개 테스트만 실행합니다. 추가 테스트를 썼다면 그 테스트도 IDE에서 실행하거나 `--tests`에 해당 클래스 이름을 더 지정합니다. 아직 구현하지 않은 다른 과제를 포함하는 전체 테스트를 현재 과제의 완료 기준으로 삼지 않습니다.
