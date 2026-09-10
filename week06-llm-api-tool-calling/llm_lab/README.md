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
