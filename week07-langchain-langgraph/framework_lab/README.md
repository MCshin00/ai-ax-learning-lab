# Java 참고 — LangChain4j로 고객 조회 연결하기

LangChain4j의 실제 `AiServices`가 등록된 조회 함수를 실행하고 그 결과를 다음 모델 요청으로 전달하는 Java 참고 예제입니다. 이번 주의 기본 비교·제작은 [framework_compare](../framework_compare/README.md)의 실제 Direct·LangChain·LangGraph로 진행합니다. LangChain4j와 Python LangChain·LangGraph는 서로 다른 라이브러리입니다. 이 Java 예제를 실행한 것만으로 세 방식의 비교를 완료하지 않습니다.

프레임워크가 맡는 호출·메시지 연결과 앱이 관리할 업무 상태를 구분해 읽습니다. 함수 결과가 다음 응답에 이어지는 경로를 먼저 보고, 번호 정정 때는 현재 대상에 맞지 않는 옛 조회 결과·초안을 무효화하는 위치를 확인합니다.

이 예제는 Java에서 모델·도구 연결을 참고하거나 기존 작업을 이어 읽을 때 사용합니다. 이 폴더에 별도의 두 번째 앱을 제작하는 과제는 없습니다. Day별 해설은 [주차 README](../README.md)에 있으며 결과와 판단은 `../framework-note.md`에 이어 남깁니다.

JDK 17 이상을 지정하고 이 폴더를 IDE의 Gradle 프로젝트로 가져옵니다. `Quickstart.main` 또는 Gradle의 `run`·`test` 작업을 실행할 수 있습니다. LangChain4j 1.20.0과 JUnit 5.14.1을 고정했으며 첫 실행에는 의존성 다운로드가 필요합니다. [공식 시작 안내](https://docs.langchain4j.dev/get-started/)

Windows PowerShell:

```powershell
.\gradlew.bat run --args='--offline'
.\gradlew.bat run --args='--offline --case missing'
.\gradlew.bat run --args='--offline --case unknown'
.\gradlew.bat test
```

macOS·Linux·WSL:

```bash
sh ./gradlew run --args='--offline'
sh ./gradlew run --args='--offline --case missing'
sh ./gradlew run --args='--offline --case unknown'
sh ./gradlew test
```

오프라인에서도 실제 `AiServices`와 업무 함수가 실행됩니다. `OfflineModel`만 호출 요청과 최종 문장을 고정합니다. 정상 결과에는 `SCRIPTED_OFFLINE`, `MODEL_RESPONSE`, `basic`이 보입니다. `missing`은 조회 없이 질문합니다. `unknown`에서는 프레임워크가 오류 결과를 모델에 돌려준 다음 애플리케이션이 최종 출력을 `TOOL_ERROR`로 정리합니다. 이 종료 시점이 6주차와의 비교 지점입니다.

| 파일·구성 | 역할 |
|---|---|
| `src/main/java/lab/week07/Quickstart.java` | 도구 등록, 모델 선택, 프레임워크 실행과 결과 상태 처리 |
| `CustomerTools.getCustomerContext` | 등록한 이름 `get_customer_context`로 호출되는 합성 고객 조회 |
| `OfflineModel` | 모델 경계만 고정하는 테스트 공급자 |
| `beforeToolExecution`, `validateArguments` | 실제 실행 전에 JSON 완전성과 식별자의 입력 형식 검사 |
| `src/test/java/lab/week07/QuickstartTest.java` | 실제 프레임워크의 함수 실행·자료 변경 전달·실패·반복 제한 검사 |
| `build.gradle`, `gradle/wrapper/` | 의존성과 빌드 도구 버전 |

실제 모델은 실행 환경에 `OPENAI_API_KEY`, `OPENAI_MODEL`, `AI_AX_LIVE=1`을 설정한 뒤 호출합니다. 이 모델 연결은 LangChain4j의 `OpenAiChatModel`을 사용하므로 Chat Completions와 함수 호출을 지원하는 모델을 선택합니다. 6주차의 Responses API와 전송 방식이 다릅니다. 비교할 때는 같은 업무 입력·반환값·실패 계약이 유지되는지 확인합니다.

Windows PowerShell:

```powershell
.\gradlew.bat run --args='--text "C-100 고객의 요금제를 알려주세요."'
```

macOS·Linux·WSL:

```bash
sh ./gradlew run --args='--text "C-100 고객의 요금제를 알려주세요."'
```

API는 별도 과금됩니다. 자동 재시도를 끄고 요청 시간·출력 길이를 제한하며 도구 왕복을 최대 두 번 허용합니다. 보통 첫 모델 요청과 두 번의 후속 요청까지 생길 수 있습니다. 이 제한은 여러 사용자 답변에 걸친 재질문 횟수나 계정 금액 상한이 아닙니다. 예제는 요청·응답 로그와 외부 trace 내보내기를 활성화하지 않습니다. 실제 연결 미확인은 `NOT_VERIFIED`로 남깁니다. [공식 도구 실행 안내](https://docs.langchain4j.dev/tutorials/tools/)

IDE 실행 버튼의 환경과 터미널 환경은 별개이며 다른 폴더의 `.env`를 자동으로 읽지 않습니다. 키 값은 출력하지 않습니다. 테스트의 고정 모델 실행과 실제 모델의 선택·답변 확인은 구분합니다.
