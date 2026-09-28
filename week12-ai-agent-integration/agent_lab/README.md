# 주문 상담 앱 실행 자료

상세 해설·설계 선택·복사할 요청문은 [주차 README](../README.md)의 Day 1~5에서 읽습니다. Java 앱 하나에서 업무 처리와 LangChain4j의 모델·도구·검색을 연결합니다.

## 코드를 읽을 순서

Java 파일은 `src/main/java/lab/week11/`에 있습니다.

| 파일 | 맡는 일 |
|---|---|
| `FlowPatterns.java` | Day 1의 분기·독립 조회·한 번의 보완 비교 |
| `Consultation.java` | 전체 처리 순서, 대화 상태, 모델 호출 상한, 근거 검사·보완·다음 행동 |
| `OrderLookup.java` | 제공 주문 JSON에서 사실 조회 |
| `LangChainAi.java` | AI Services의 출력 변환·검색 도구 실행·모델 재호출, 내부 호출 제한 |
| `PolicySearch.java` | 임베딩 생성, 메모리 벡터 저장소 검색, 정책 발췌와 출처 전달 |
| `ModelSetup.java`, `ScriptedModels.java` | 실제 모델·임베딩 연결과 고정 응답 대역 |
| `Application.java`, `AiPort.java`, `Json.java` | 화면·API, 모델 처리 입출력, JSON 변환 |
| `CompareAnswers.java` | 발췌만·한 번의 보완·처음부터 전체 본문을 같은 입력으로 비교 |
| `data/consultation_cases.json`, `src/test/java/` | 실제 모델 확인 사례와 오프라인 검사 |

## 실행 준비

IDE에서 이 폴더를 Gradle 프로젝트로 엽니다. Java 17 이상을 선택하고 의존성을 동기화합니다. `build.gradle`은 LangChain4j와 OpenAI 어댑터를 1.20.0으로 고정합니다. 실행 설정의 작업 폴더는 `agent_lab`이며 여기서 `data/orders.json`과 `data/policies.json`을 읽습니다.

Day 1은 `FlowPatterns.main`을 실행합니다. 고정된 문의 종류로 분기를 선택하고 실제 주문 파일을 읽으며, 독립 조회 결과와 해결되지 않은 보완 결과를 보여 줍니다.

## 화면에서 실행하기

1. IDE에서 `Application.main`을 인수 `--port 0`으로 실행합니다. 0은 비어 있는 로컬 포트를 선택하게 합니다.
2. 출력된 **상담 화면** 주소를 브라우저에서 엽니다.
3. 대화 ID와 문의를 입력하고 주문 사실·정책 출처·다음 행동·`trace`를 확인합니다.

기본 `SCRIPTED_DEMO` 모드는 아래 입력에 준비된 응답을 연결합니다. 실제 AI Services와 벡터 저장소를 실행하고 모델과 임베딩은 대역을 사용합니다. 자연어 이해와 답변 품질은 실제 모델 실행에서 확인합니다.

- `A-102 상태가 궁금해요.`
- `A-103 상태가 궁금해요.`
- `A-102를 취소할 수 있나요? 신청 경로도 알려 주세요.`
- 같은 대화 ID로 `반품하고 싶어요` → `A-104예요` → `받은 지 이틀이고 사용하지 않았어요`
- `A-103으로 정정할게요`
- `A-999, A-102, A-103의 상태를 각각 알려 주세요.`

실제 모델은 IDE의 비공유 실행 설정에 `AI_AX_LIVE=1`, `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_EMBEDDING_MODEL`을 준비하고 인수를 `--live --port 0`으로 바꿉니다. 도구 호출과 JSON 출력이 가능한 대화 모델을 선택합니다. `LIVE` 표시를 확인한 뒤 제공 입력과 `data/consultation_cases.json`의 질문을 실행합니다. 실제 호출과 결과 확인은 학습자가 IDE에서 수행합니다. 대화 상태는 실행 중 메모리에 유지되며 앱을 종료하면 초기화됩니다.

반복 실행을 자동화하거나 터미널을 사용할 때는 다음 명령으로 고정 응답 앱을 실행합니다.

Windows PowerShell:

```powershell
.\gradlew.bat run --args="--port 0"
```

macOS·Linux·WSL:

```sh
sh ./gradlew run --args="--port 0"
```

## 앱과 모델 사이에 전달하는 값

`POST /consultations`는 `conversation_id`와 `request` 문자열을 받습니다. `Consultation.reply`가 `LangChainAi.invoke`를 호출해 다음 처리를 연결합니다.

| 작업 | 모델에 주는 값 | 다음 코드가 받는 값 |
|---|---|---|
| `intake` | 새 문의, 이전 접수 | `Intake`: 문의 종류·주문 번호·주문별 보충 정보 |
| `draft` | 문의·접수·조회 사실·근거·검사 피드백 | `Draft`: 주문별 초안과 사용한 출처 ID |

AI Services는 반환형에 맞는 출력 안내를 모델에 전달하고 응답을 객체로 변환합니다. 이 예제의 출력 제어는 프롬프트와 파싱이며 제공자의 JSON Schema 강제 모드는 사용하지 않습니다. `@Tool`로 공개한 `search_policy`는 모델의 호출 요청을 받아 실제 검색을 실행합니다. 검색 본문·출처는 모델에 반환되는 동시에 앱의 근거 목록과 실행 기록에도 남습니다.

앱은 접수·도구 결과 이후 재호출·보완을 합쳐 요청당 대화 모델 호출을 최대 6회로 제한합니다. `LimitedModel`이 호출 직전에 횟수를 올리므로 실패한 시도도 포함합니다. 임베딩 호출은 별개입니다. 자료와 질문의 벡터를 검색하는 비용과 답변을 생성하는 비용을 구분해 읽습니다.

최종 `items`에는 주문별 사실·근거·안내·질문·상태가, `actions`에는 표시·질문·검토 동작이 있습니다. `ready`는 항목·근거 검사를 통과했다는 뜻이며 안내 문장의 정확성은 원문으로 확인합니다. `partial`, `limit_reached`, `unavailable`에서도 확인된 사실과 남은 처리를 읽습니다.

## 근거 보완과 비교

`PolicySearch`는 정책 벡터를 메모리에 준비하고 질문마다 상위 2개, 최소 점수 0.7의 후보를 찾습니다. 점수는 저장소의 유사도 변환값이므로 8주차의 별도 검색 설정과 숫자만 비교하지 않습니다. 이 값은 예제의 시작 설정이며 실제 입력의 검색 결과로 적절성을 판단합니다.

검색된 정책의 첫 문장을 발췌로 전달해 문맥 누락과 보완의 차이를 관찰합니다. 앱이 필요한 정책의 전체 본문이 없다는 피드백을 만들면 같은 출처의 원문을 가져와 한 번 다시 생성·검사합니다. 처음부터 전체 본문을 보내는 대안은 `initialContext="full"`입니다.

IDE에서 `CompareAnswers.main`을 실행하면 발췌만·한 번의 보완·처음부터 전체 본문을 비교합니다. 기본 실행은 흐름과 호출 수를 확인합니다. 실제 연결 설정과 `--live` 인수를 지정하면 실제 답변을 비교합니다. 각 구성은 별도 대화 상태에서 시작합니다.

## 검사

IDE의 Java 테스트 실행기를 사용합니다. 대화 분리·정정·부분 실패·호출 상한과 실제 AI Services의 출력 변환·도구 결과 전달을 가짜 모델로 검사합니다. 화면 API 검사는 포트 0의 로컬 서버를 사용합니다. 실제 모델의 선택과 답변 의미는 제공 사례의 실제 실행과 원문 대조로 확인합니다.

자동화 명령은 Windows PowerShell의 `.\gradlew.bat test`, macOS·Linux·WSL의 `sh ./gradlew test`입니다.
