# 고객 조회와 Tool Calling 루프

제공 고객 조회 함수와 공식 SDK를 연결하는 Java 예제입니다. 모델 요청→호출 해석→인자 확인→함수 실행→결과 전달→다음 호출·종료를 배웁니다. Day별 해설과 제작 요청은 [주차 README](../README.md), 결과와 선택 이유는 `../api-note.md`에 이어 남깁니다.

`CustomerDirectory`는 모델과 분리된 제공 업무 함수입니다. `Quickstart`는 공개 Tool·호출 이력·종료 정책을 선택한 완성 참고 구현이고, Day 3에서는 요구에서 이 계약과 제어를 직접 설계합니다. `AssistantExercise`는 교체 가능한 빈 진입점으로 고정 `run` 인터페이스를 요구하지 않습니다. 자신의 메인 클래스를 만들었다면 `assistant`에 `-PassistantMain=lab.week06.CustomerAssistant`처럼 지정합니다. 기본 진입점의 `DESIGN_PENDING`은 아직 제작하지 않았다는 안내입니다.

JDK 17 이상으로 이 폴더를 IDE의 Gradle 프로젝트로 엽니다. `Quickstart.main` 또는 Gradle의 `run`·`assistant` 작업을 사용합니다. OpenAI 공식 Java SDK 4.60.0과 JUnit 5.14.1을 고정하며 Gradle Wrapper가 빌드 도구를 준비합니다. 첫 실행에는 의존성 다운로드가 필요합니다. [공식 Java SDK](https://developers.openai.com/api/docs/libraries)

Windows PowerShell:

```powershell
.\gradlew.bat run --args='--offline'
.\gradlew.bat run --args='--offline --case unknown'
```

macOS·Linux·WSL:

```bash
sh ./gradlew run --args='--offline'
sh ./gradlew run --args='--offline --case unknown'
```

정상 출력에는 `mode=SCRIPTED_OFFLINE`, `status=MODEL_RESPONSE`, 고객 `C-100`의 `basic` 요금제와 `tool_results`가 보입니다. 실제 조회 함수와 결과 전달을 실행하고 모델 경계만 고정합니다. `--text`를 바꾼 결과로 실제 모델의 이해·선택을 판단하지 않습니다.

`--case missing`은 조회 없이 고객 ID를 질문합니다. `unknown`은 `CUSTOMER_NOT_FOUND`를 같은 호출 ID의 결과로 모델에 전달한 뒤 확인 안내로 끝납니다. 모델 응답 완료인 `MODEL_RESPONSE`와 고객 조회 성공은 다른 의미입니다. `repeat`는 모델 요청 세 번·실제 조회 두 번 뒤 `STOPPED`로 끝납니다. 마지막 허용 응답에서 다시 함수를 실행하지 않는 이유는 그 결과를 모델에게 전달할 요청 여유가 없기 때문입니다.

실제 API는 IDE의 비공유 실행 설정이나 현재 셸 환경에 `OPENAI_API_KEY`, `OPENAI_MODEL`, `AI_AX_LIVE=1`을 설정합니다. IDE 실행 설정이 터미널에 자동 전달되거나 다른 폴더의 `.env`를 읽는다고 가정하지 않습니다. 키 값은 코드·대화·공개 기록에 넣거나 출력하지 않습니다. 계정에서 사용할 수 있는 Responses·함수 호출 지원 모델을 선택합니다.

Windows PowerShell:

```powershell
.\gradlew.bat run --args='--plain --text "메모를 한 문장으로 요약해줘: 로그인 수정 완료, 배포 확인 필요."'
.\gradlew.bat run --args='--text "C-100 고객의 요금제를 알려주세요."'
```

macOS·Linux·WSL:

```bash
sh ./gradlew run --args='--plain --text "메모를 한 문장으로 요약해줘: 로그인 수정 완료, 배포 확인 필요."'
sh ./gradlew run --args='--text "C-100 고객의 요금제를 알려주세요."'
```

`--plain`은 고객 도구와 조회 지침을 제외한 일반 모델 호출입니다. 정상 고객 조회는 보통 모델 호출 둘과 로컬 함수 실행 하나로 끝납니다. 자동 재시도를 끄고 요청 시간·출력 길이를 제한하며 요청 상한을 둡니다. 이 제한은 계정의 금액 상한이 아닙니다. 실제 연결 미확인은 `NOT_VERIFIED`로 구별합니다. [Function calling](https://developers.openai.com/api/docs/guides/function-calling)

자기 앱에서 같은 CLI 인자를 쓰기로 했다면 위 명령의 `run`을 `assistant`로 바꾸고, 다른 메인 클래스를 만들었으면 `'-PassistantMain=lab.week06.CustomerAssistant'`도 지정합니다. 다른 인자를 정했다면 구현과 실행 안내를 함께 맞춥니다. 참고 대역 `OfflineClient`는 참고 Tool 이름·반환 필드를 사용하므로 자기 계약을 바꾸면 대역도 그 계약에 맞춥니다. 정상과 선택한 핵심 실패에서 자기 앱이 종료하는지 확인하고, 실제 모델이 준비되면 같은 진입점으로 호출합니다.

| 파일·구성 | 역할 |
|---|---|
| `src/main/java/lab/week06/Quickstart.java` | 제공 업무 함수·Tool 정의·SDK 경계와 완성된 호출 루프 |
| `CustomerDirectory.java` | 제공 고객 데이터와 조회. 자기 Tool에서 재사용 |
| `AssistantExercise.java` | 교체 가능한 실행 시작점. 앱의 타입·클래스·메서드 구조는 설계에서 선택 |
| `Gateway`, `OfflineClient` | 모델 경계를 고정해 실제 앱 연결·업무 함수를 실행 |
| `Turn.usage`, 결과의 `turns` | SDK가 보고한 요청별 사용량과 미확인 여부 |
| `src/test/java/lab/week06/QuickstartTest.java` | 전달·호출 ID·추론 항목·오류 반환·종료·사용량의 기존 검사 |
| `build.gradle`, `gradle/wrapper/` | 실행 작업·의존성·빌드 도구 버전 |

`model_requests`는 프로그램이 시도한 요청 수입니다. `turns[].usage`의 캐시 입력은 입력 토큰의 일부, 추론 토큰은 출력 토큰의 일부이므로 합계에 다시 더하지 않습니다. 오프라인이나 응답 유실로 사용량을 모르면 `UNAVAILABLE`·`null`로 남깁니다. 이를 0원으로 바꾸지 않습니다. 실제 실행의 모델·단가·사용량으로 계산한 비용 근거는 기존 노트에 짧게 남기며 자세한 계산은 주차 가이드를 따릅니다.

필요한 코드 변경 후 기존 검사는 IDE의 `test`, PowerShell의 `.\gradlew.bat test`, macOS·Linux·WSL의 `sh ./gradlew test`로 실행합니다. 같은 결과를 확인하려고 매 입력마다 전체 검사를 반복하지 않습니다. 대역 검사는 루프와 계약을 확인하며 실제 모델의 도구 선택·답변 품질은 실제 호출 결과에서 확인합니다.

## Gemini로 Day 1 실행하기

Google GenAI Java SDK 1.70.0을 사용하는 `GeminiQuickstart`는 도구 없는 메모 요약의 실행 진입점입니다. Day 1에서는 **사용자 입력·지침 → SDK 호출 → 응답 해석**을 연결해 읽습니다. `requestConfig`에서 처리 지침을 구성하고, `run`에서 사용자 입력과 함께 SDK를 호출한 뒤 종료 이유와 답변을 해석합니다. 입력과 지침을 분리하는 이유, 도구가 필요 없는 이유, 실제 요약이 원문의 사실을 보존하는지가 학습의 중심입니다.

IntelliJ에서 Gradle 변경 사항을 다시 불러온 뒤 `GeminiQuickstart.main`의 실행 구성을 만듭니다. 실행 클래스는 `lab.week06.GeminiQuickstart`, 모듈은 이 프로젝트의 main 모듈입니다. 기존 `Quickstart`와 별도 구성이므로 환경변수도 새 구성에 입력합니다.

| 실행 설정 | 값 |
|---|---|
| Program arguments | 기본 요약은 비워 둠. 다른 메모는 `--text "요약할 메모"` |
| GEMINI_API_KEY | Google AI Studio에서 준비한 키를 비공유 실행 설정에 입력 |
| GEMINI_MODEL | `gemini-3.8-flash` |
| AI_AX_LIVE | `1` |

`Store as project file`은 선택하지 않습니다. 실제 실행에서는 `--offline`을 빼고, API 없이 연결 구조만 확인할 때는 `--offline`을 넣습니다. `--plain` 또는 인자 없는 실행은 도구 없는 요약이고, `--tools`를 넣으면 요청한 항목을 선택하는 고객 조회 흐름을 실행합니다.

기본 입력은 “메모를 한 문장으로 요약해줘: 로그인 수정 완료, 배포 확인 필요.”입니다. 실제 응답의 `answer`에서 두 사실이 보존되는지 대조합니다. `MODEL_RESPONSE`는 응답 종료 분류이며 내용의 정확성을 보증하지 않습니다. `mode=LIVE`는 실제 연결 시도, `SCRIPTED_OFFLINE`은 고정 응답을 뜻합니다.

<details>
<summary>참고: 출력 제한과 사용량 필드</summary>

출력 제한과 추론 수준은 요청 설정의 보충 정보입니다. `usage`는 Gemini가 보고한 필드 그대로 출력하며 빠진 값은 0으로 만들지 않습니다. `model_requests`는 앱의 호출 시도 수이며 SDK는 자동 재시도 없이 한 번만 시도합니다. 토큰별 사용량과 비용 해석은 주차 README의 선택 참고에서 다룹니다.

</details>

IDE 실행 대신 터미널을 선택할 때는 이 폴더에서 아래 명령을 사용합니다. 실제 API는 실행한 셸에도 위 환경변수가 있어야 합니다.

Windows PowerShell:

```powershell
.\gradlew.bat gemini
```

macOS·Linux·WSL:

```bash
sh ./gradlew gemini
```

오프라인 실행에는 각 명령에 `--args='--offline'`을 붙입니다. 테스트는 IDE에서 `GeminiQuickstartTest`를 실행합니다. 입력·도구 제외·종료·누락된 사용량·전송 실패를 대역으로 확인하며, 실제 Gemini의 답변 품질과 계정 할당량은 실제 호출에서 확인합니다. 결과와 해석은 주차의 `api-note.md`에 이어 남깁니다.

[Google Java SDK](https://github.com/googleapis/java-genai) · [모델 지원 기능](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash) · [무료 등급과 요금](https://ai.google.dev/gemini-api/docs/pricing)


## Gemini 고객 조회 실행하기

기존 IntelliJ의 `GeminiQuickstart.main` 실행 구성에서 Program arguments를 바꿉니다. 실제 API 실행은 학습자가 직접 수행합니다. 기존 비공유 실행 구성의 `GEMINI_API_KEY`, `GEMINI_MODEL`, `AI_AX_LIVE=1`을 사용하며 키 값을 출력하거나 공유하지 않습니다.

| 확인할 흐름 | Program arguments |
|---|---|
| 요금제 대역, API 사용 없음 | `--tools --offline` |
| 계정 상태 대역, API 사용 없음 | `--tools --offline --case status` |
| 두 항목 대역, API 사용 없음 | `--tools --offline --case both` |
| 없는 고객 대역, API 사용 없음 | `--tools --offline --case unknown` |
| 요금제 실제 Gemini | `--tools --text "C-100 고객의 요금제를 알려주세요."` |
| 계정 상태 실제 Gemini | `--tools --text "C-100 고객의 계정 상태를 알려주세요."` |
| 두 항목 실제 Gemini | `--tools --text "C-100 고객의 요금제와 계정 상태를 알려주세요."` |
| 없는 고객 실제 Gemini | `--tools --text "C-404 고객의 요금제를 알려주세요."` |

`GeminiQuickstart`가 실행 옵션과 실제 SDK 연결을 담당하고, `GeminiToolLoop`가 Tool 선언·인자 검사·고객 조회·결과 전달·종료를 담당합니다. `CustomerDirectory`는 실제 조회 함수를 그대로 제공합니다. 도구 없는 기존 요약은 인자를 비우거나 `--plain`으로 실행합니다.

Tool은 `customer_id`와 `fields`를 받습니다. `fields=["plan"]`이면 고객 ID와 `plan=basic`만, `["status"]`이면 고객 ID와 `status=active`만, 둘 다면 두 값을 반환합니다. 빈 목록이나 지원하지 않는 항목은 `INVALID_ARGUMENTS`, 없는 고객은 `CUSTOMER_NOT_FOUND`를 반환합니다. 같은 항목이 중복되면 한 번만 반환합니다. 업무 함수의 전체 레코드를 모델에 보낼 결과로 줄이는 책임은 앱의 `executeCall`에 있습니다. 최종 `status=MODEL_RESPONSE`는 모델 응답의 완료 분류이므로 조회 성공 여부는 `tool_results[].result`와 함께 확인합니다. 실제 모델 실행에서는 `tool_results`의 함수 이름·호출 ID·인자·조회값과 `answer`를 대조합니다. 토큰 사용량은 각 요청의 `turns[].usage`에 SDK가 보고한 그대로 남깁니다.

대역은 `--case`에 따라 고정된 함수 호출을 보냅니다. `--text`만 바꿔도 대역이 자연어를 해석하는 것은 아닙니다. 고정 응답의 `thoughtSignature`는 오프라인 검사용 가상값이며 실제 모델 요청에는 사용하지 않습니다. 실제 실행에서는 모델의 원래 `Content`와 받은 호출 ID를 그대로 이어 붙입니다. 한 응답에 호출이 여러 개면 실행 전에 종료하고, 모델 요청 세 번째에서도 호출을 요청하면 추가 조회 전에 멈춥니다.

IDE 대신 터미널을 선택하면 이 프로젝트 폴더에서 다음 명령을 사용합니다. Gradle 실행 작업에 인자를 전달하는 대안이며 IDE에서 같은 실행을 했다면 중복 수행할 필요는 없습니다.

Windows PowerShell:

```powershell
.\gradlew.bat gemini --args='--tools --offline'
.\gradlew.bat gemini --args='--tools --offline --case status'
.\gradlew.bat gemini --args='--tools --offline --case both'
.\gradlew.bat gemini --args='--tools --offline --case unknown'
```

macOS·Linux·WSL:

```bash
sh ./gradlew gemini --args='--tools --offline'
sh ./gradlew gemini --args='--tools --offline --case status'
sh ./gradlew gemini --args='--tools --offline --case both'
sh ./gradlew gemini --args='--tools --offline --case unknown'
```

`GeminiToolLoopTest`는 실제 조회값과 호출 ID 대응, 원래 모델 응답의 보존, 인자 오류와 반복 종료를 대역으로 확인합니다. 요금제·상태·두 항목의 메인 클래스 대역 결과는 `.local/day3-normal.json`, `.local/day3-status.json`, `.local/day3-both.json`에 있습니다. 결과의 해석은 주차의 `api-note.md`에 이어 남깁니다.

대역으로는 모델이 보낸 `fields`에 맞는 결과를 앱이 전달하는지 확인합니다. 자연어 질문에 맞는 `fields`를 실제 모델이 선택하는지는 실제 API 결과의 `arguments.fields`에서 확인합니다.


## Gemini 대화 이어가기

IntelliJ에서 기존 `GeminiQuickstart.main` 실행 구성을 사용합니다.

1. Gradle 변경을 불러온 뒤 Program arguments의 기존 `--tools --text "…"`를 지우고 `--chat`만 입력합니다. `--chat` 자체가 고객 조회 도구를 사용하는 대화 모드입니다.
2. 실제 실행에 사용할 `GEMINI_MODEL`을 비공유 실행 구성에서 지정합니다. 현재 설정은 `thinkingLevel=LOW`이므로 이를 지원하는 모델을 선택합니다. 기존 `GEMINI_API_KEY`와 `AI_AX_LIVE=1`도 사용합니다.
3. 실행하고 Run 콘솔의 `입력>` 뒤에 `요금제를 알려주세요`를 입력한 다음 Enter를 누릅니다.
4. 고객 번호를 묻는 응답이 나오면 **같은 실행 콘솔**에 `C-100`을 입력하고 Enter를 누릅니다.
5. 요금제 안내를 받은 뒤 `/exit`으로 종료합니다. 프로그램을 두 번 실행하면 이력이 이어지지 않습니다.

두 번째 결과의 `request.context_messages`에서 첫 질문·모델의 확인 질문·새 고객 번호가 순서대로 들어갔는지 확인합니다. `tool_results`에서는 `fields=["plan"]`과 고객 ID·요금제만 담긴 결과를, `answer`에서는 그 값으로 안내하는지 대조합니다. 질문 문장은 모델에 따라 달라질 수 있습니다. 두 입력의 결과 JSON을 함께 보면 실제 요청 맥락과 조회·답변을 연결할 수 있습니다.

`GeminiChat`이 콘솔 입력과 메모리 이력을 보관하고, `GeminiToolLoop`가 입력마다 최대 세 번의 모델 요청으로 처리합니다. 정상 모델 응답 뒤에는 다음 입력을 기다립니다. 빈 줄은 호출하지 않으며 `/exit`과 입력 스트림 종료는 대화를 끝냅니다. 한도 초과·실패·거절·불완전하거나 잘못된 응답이 오면 결과에 나온 종료 이유와 함께 대화를 중단합니다.

API 없이 실행하려면 인자를 `--chat --offline`으로 바꿉니다. 대역은 위 두 입력의 정확한 문장 또는 새 실행에서 `C-100 고객의 요금제를 알려주세요.`라는 단일 입력을 지원합니다. 임의의 파생 질문을 해석하는 모델이 아니며, 대역용 고정 서명은 실제 API로 보내지 않습니다. 기존 한 번의 조회에는 계속 `--tools --text "…"`를 사용합니다.

Gradle의 `JavaExec` 작업에는 `standardInput = System.in`을 연결해 콘솔 입력을 앱에 전달합니다. IDE 대신 터미널에서 진행한다면 이 프로젝트 폴더에서 아래 명령으로 같은 콘솔을 시작할 수 있습니다. 실제 실행과 대역 실행을 중복 수행할 필요는 없습니다.

Windows PowerShell:

```powershell
.\gradlew.bat gemini --args='--chat'
```

macOS·Linux·WSL:

```bash
sh ./gradlew gemini --args='--chat'
```

대역을 선택하면 위 명령의 인자를 `--args='--chat --offline'`으로 바꿉니다. 대화 흐름 검사는 `GeminiChatTest`에 있으며, 구조·선택 이유·실제 확인 내용은 `../api-note.md`의 Day 5에 이어 기록합니다.
