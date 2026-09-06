# 1주차 Microtask — 티켓 제목 정규화

## 이 과제를 준비한 이유

같은 과제를 두 가지 방식으로 요청했을 때 Codex의 결과가 어떻게 달라지는지 비교하기 위한 작은 Java 과제입니다. 복잡한 도메인 지식은 빼고 메서드 하나와 공개 테스트만 두었습니다. 이번 실습의 목표는 Java 문법을 외우는 것이 아니라 작업 폴더의 문서·코드·테스트를 읽고 AI의 결과를 검증하는 과정을 익히는 데 있습니다.

## 프로젝트 구성

```text
README.md
build.gradle
settings.gradle
gradlew, gradlew.bat
gradle/wrapper/
src/main/java/lab/week01/TicketTitleNormalizer.java
src/test/java/lab/week01/TicketTitleNormalizerTest.java
```

각 파일의 역할은 다음과 같습니다.

| 경로 | 역할 |
|---|---|
| `build.gradle` | Java 17 컴파일 조건, JUnit 의존성, `test` 작업을 정의합니다. |
| `settings.gradle` | IDE와 Gradle에 프로젝트 이름을 알려 줍니다. |
| `src/main/java/.../TicketTitleNormalizer.java` | 구현할 `normalize` 메서드가 들어 있는 시작 코드입니다. |
| `src/test/java/.../TicketTitleNormalizerTest.java` | 정상 입력과 경계 조건을 확인하는 JUnit 공개 테스트입니다. |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/` | 설치된 Gradle 버전과 무관하게 이 프로젝트가 정한 버전을 쓰도록 하는 공식 Gradle Wrapper입니다. |

`TicketTitleNormalizer.normalize`는 현재 `UnsupportedOperationException`을 던지도록 비어 있습니다. 처음 테스트하면 빨간색 실패 결과가 나오는 것이 정상입니다.

## 요구사항

- `null`과 공백 문자로만 이뤄진 입력은 `IllegalArgumentException`을 던집니다.
- 앞뒤 공백을 제거합니다.
- 연속된 공백 문자를 일반 공백 한 칸으로 바꿉니다.
- 정규화한 제목이 Unicode code point 기준 80자를 넘으면 앞의 80개 code point만 남깁니다.
- 공개 테스트를 삭제하거나 약화하지 않습니다.

## IDE에서 확인하고 테스트하기

1. VS Code나 IntelliJ에서 `week01-codex-prompt-comparison/lab/ticket-title-normalizer` 폴더를 프로젝트로 엽니다.
2. Gradle 프로젝트를 가져올지 묻는 안내가 나오면 가져오기를 선택하고 동기화가 끝날 때까지 기다립니다.
3. 구현 코드와 테스트 코드를 직접 읽습니다.
4. `TicketTitleNormalizerTest` 옆의 테스트 실행 버튼을 누르거나 Gradle 도구 창에서 `verification > test`를 실행합니다.
5. 구현 전 실패와 구현 후 결과를 IDE의 테스트 창에서 비교합니다.

VS Code에서는 Java와 Gradle 프로젝트를 지원하는 확장이 필요합니다. IntelliJ는 `build.gradle`을 열거나 과제 폴더를 열면 Gradle 프로젝트로 가져올 수 있습니다. 처음 동기화할 때는 Gradle 배포 파일과 JUnit을 내려받으므로 인터넷 연결이 필요합니다. 이후에는 내려받은 파일을 재사용합니다.

`build.gradle`의 `mavenCentral()`은 JUnit을 내려받을 저장소를 뜻합니다. 빌드 도구로 Maven을 쓴다는 의미가 아닙니다. 이 과제의 빌드와 테스트는 Gradle이 맡습니다.

전역 Gradle 설치는 필요하지 않습니다. IDE는 프로젝트에 포함된 Gradle Wrapper를 사용합니다. Wrapper 파일은 운영체제별 진입점을 제공하지만 학습자가 테스트 로직을 이해하려고 이 파일을 읽거나 직접 실행할 필요는 없습니다. 실제 테스트 규칙은 `build.gradle`과 JUnit 테스트 코드에 있습니다.

## 처음 예상되는 결과

- Gradle 동기화 실패: JDK 설정, 네트워크 또는 의존성 다운로드 문제를 먼저 확인합니다.
- 컴파일 실패: Java 코드가 아직 컴파일 가능한 상태가 아닙니다.
- `UnsupportedOperationException`: 시작 코드가 미구현인 의도된 기준선 실패입니다.
- JUnit assertion 실패: 코드는 실행됐지만 공개 요구사항 가운데 하나를 만족하지 못했습니다.
- 모든 테스트가 초록색: 현재 공개 테스트를 통과했다는 뜻입니다. 공개되지 않은 모든 동작까지 맞다는 보장은 아닙니다.

Gradle이 만드는 `.gradle/`과 `build/`는 생성물입니다. 실행 과정에서 구현 코드, 테스트 코드, README 또는 다른 실험 폴더를 자동으로 수정하지 않습니다.

AI에 요청하기 전에 README, 시작 코드와 공개 테스트를 먼저 읽습니다. Codex에는 이 파일들이 들어 있는 과제 폴더를 작업 맥락으로 제공하므로 README 전체를 대화창에 다시 붙여넣을 필요는 없습니다. 1주차에는 준비된 A/B 프롬프트를 각각 직접 전송한 뒤 Codex가 폴더의 문서와 테스트를 어떻게 활용하는지 관찰합니다.
