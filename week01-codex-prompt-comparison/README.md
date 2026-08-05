# 1주차 — 직접 요청하고 Codex 작업을 관찰하기

> 이 주차는 학습 저장소에 적용되었습니다. 현재 진행 주차는 루트의 `CURRENT_WEEK.md`에서 확인합니다.

[전체 과정 보기](../README.md)


준비된 작은 Java 과제와 두 개의 실험용 프롬프트를 사용합니다. 과제 폴더에는 요구사항·시작 코드·공개 테스트가 들어 있고, 학습자는 짧은 요청 A와 구조화된 요청 B를 읽은 뒤 Codex 앱이나 대화형 CLI에 직접 붙여넣어 보냅니다. 자동 실행과 JSONL 측정은 이 대화형 경험을 얻은 다음 단계에서 다룹니다.

## 학습 목표

- 준비된 과제의 코드·테스트·의도된 실패를 실행 전에 설명합니다.
- 준비된 짧은 요청과 구조화된 요청을 직접 전송하고 결과를 비교합니다.
- 프롬프트 문구와 작업 폴더에 이미 있는 맥락이 각각 무엇을 전달하는지 구분합니다.
- 저장소 규칙과 일회성 작업 요구를 구분합니다.
- 일반적인 대화형 사용과 재현 가능한 `codex exec --json` 측정을 구분합니다.
- JSONL 로그에서 결과·오류·도구 사용·토큰을 읽습니다.
- 실패를 재현 가능한 기록으로 남깁니다.

## 개념 이해

### Codex 작업은 무엇으로 결정되는가

Codex의 결과는 모델 하나만으로 결정되지 않습니다. 사용자가 보낸 요청, 현재 대화에 들어 있는 맥락, 저장소의 코드와 문서, `AGENTS.md`, 사용할 수 있는 도구, 실행 권한과 검증 명령이 함께 작용합니다.

```text
사용자 요청
+ 저장소 맥락과 `AGENTS.md`
+ 도구와 권한
+ 실행 환경
→ Codex의 판단과 작업
→ 코드·문서·명령 실행 결과
```

같은 모델을 사용해도 요청이 모호하거나 저장소 규칙이 다르면 결과가 달라질 수 있습니다. 반대로 요청과 검증 조건을 일정하게 유지하면 어떤 설정이 결과에 영향을 주었는지 비교하기 쉬워집니다.

### 짧은 요청, 작업 계약, `AGENTS.md`

세 가지는 적용 범위가 다릅니다.

| 구분 | 맡는 역할 | 적합한 내용 |
|---|---|---|
| 짧은 요청 | 지금 수행할 작업을 알림 | 버그 수정, 코드 설명, 테스트 추가 |
| 작업 계약 | 이번 작업의 범위와 완료 조건을 정함 | 수정 경로, 인수 조건, 검증 명령, 중단 조건 |
| `AGENTS.md` | 저장소에서 반복해서 지켜야 할 규칙을 전달 | 코딩 규칙, 필수 테스트, 비밀값 처리, 완료 보고 형식 |

작업 계약은 “무엇을 끝내야 하는가”를 구체화합니다. `AGENTS.md`는 여러 작업에 계속 적용할 저장소 규칙을 담습니다. 한 번만 필요한 세부 요구를 `AGENTS.md`에 계속 쌓으면 지침이 길어지고 서로 충돌하기 쉽습니다. 반대로 매번 지켜야 하는 검증 규칙을 프롬프트에만 적으면 작업마다 빠뜨릴 수 있습니다.

하위 디렉터리의 `AGENTS.md`는 해당 영역에 더 가까운 규칙을 덧붙이는 데 사용합니다. 예를 들어 루트에는 저장소 공통 규칙을 두고 테스트 디렉터리에는 테스트를 삭제하거나 약화하지 말라는 규칙을 둘 수 있습니다.

### 실행 로그와 결과 검증

`codex exec --json`은 실행 중 생긴 이벤트를 JSONL 형식으로 내보냅니다. JSONL은 한 줄에 JSON 객체 하나를 기록하는 형식입니다. 전체 파일을 한 번에 읽지 않아도 이벤트를 순서대로 처리할 수 있어 실행 로그에 잘 맞습니다.

로그에서는 다음 정보를 찾습니다.

- 작업의 시작과 종료
- 실행한 명령과 도구 호출
- 파일 변경
- 정상 완료와 오류
- 입력·캐시 입력·출력 토큰
- 최종 응답

로그에 완료 이벤트가 있다고 해서 구현이 정확하다는 뜻은 아닙니다. 완료 여부는 Codex 실행 상태이고 기능의 정확성은 테스트와 인수 조건으로 판단합니다. 실행 로그와 품질 검증 결과를 따로 기록해야 하는 이유입니다.

### 전체 시간과 사람 작업 시간

병렬 작업이나 자동화를 비교할 때는 시간을 둘로 나눕니다.

- `T_wall`: 실행을 시작한 순간부터 최종 결과가 나온 순간까지의 전체 경과 시간
- `T_human`: 사람이 요청을 작성하고 결과를 읽고 판단하고 직접 수정한 시간

Codex가 빠르게 끝나도 사람이 결과를 고치는 데 오래 걸리면 실제 효율은 낮을 수 있습니다. 반대로 전체 실행은 길어도 사람이 다른 일을 할 수 있었다면 사람 시간은 적게 들 수 있습니다. 두 수치를 함께 봐야 속도와 개입 비용을 구분할 수 있습니다.

### 비교 실험의 기본 조건

요청 방식 A와 B를 비교하려면 시작 코드를 같게 맞추고 모델·reasoning·시간 제한·검증 명령을 기록합니다. 결과를 본 뒤 평가 기준을 바꾸지 않도록 인수 조건도 실행 전에 정합니다.

좋은 비교는 “어느 쪽이 마음에 들었는가”에서 끝나지 않습니다. 첫 테스트 통과 여부, 누락된 요구사항, 추가 교정 횟수, 사람 작업 시간처럼 다시 확인할 수 있는 증거를 남깁니다.

### 준비된 작업 맥락과 직접 보내는 요청을 구분하기

준비된 과제는 두 실행의 시작 조건을 같게 만드는 실험 도구입니다. 과제 코드와 공개 테스트뿐 아니라 비교할 두 프롬프트도 미리 제공합니다. 다만 교재의 wrapper가 파일을 곧바로 모델에 넣지는 않습니다. 학습자가 작업 폴더와 프롬프트 내용을 직접 확인하고 대화창에서 전송합니다.

```text
교재가 준비하는 것
과제 코드·README·공개 테스트·실행 스크립트·실험용 프롬프트

학습자가 직접 하는 것
파일과 프롬프트 읽기·대화창에 전송·추가 질문·diff와 테스트 검토
```

프롬프트를 직접 작성하는 연습이 필요한 단계에서는 새 문구를 만들지만, 이번 A/B 실험에서는 제공된 문구가 통제 변수입니다. 처음에는 Codex 앱이나 대화형 CLI에 두 문구를 직접 붙여넣습니다. 같은 입력을 반복하거나 로그를 측정할 때만 실제 전송한 본문을 저장한 파일을 Runner에 전달합니다.

### 구성 요소의 관계

```text
`AGENTS.md`        저장소 공통 규칙
       ↓
작업 계약          이번 작업의 범위와 완료 조건
       ↓
Codex 실행         코드 수정과 도구 사용
       ↓
JSONL 로그         실행 과정과 사용량
       ↓
테스트·인수 조건   결과의 정확성
       ↓
비교표·회고        다음 요청 방식에 반영할 근거
```

### 자주 생기는 문제

- 서로 다른 시작 코드에서 A/B 실험을 실행해 요청 방식 외의 변수가 섞입니다.
- 실행이 정상 종료됐다는 이유로 테스트 없이 성공으로 기록합니다.
- 토큰 수만 비교하고 사람의 검토·수정 시간을 빠뜨립니다.
- `AGENTS.md`에 일회성 요구까지 넣어 저장소 규칙이 계속 불어납니다.
- 루트와 하위 `AGENTS.md`의 지침이 충돌하지만 실제 적용 결과를 확인하지 않습니다.
- JSONL 파싱 오류와 Codex가 기록한 실행 오류를 같은 문제로 처리합니다.
- 결과를 기록하기 전에 원인을 추측해 관찰 사실과 해석이 섞입니다.

### 학습을 마친 뒤 설명할 수 있어야 하는 것

- 짧은 요청, 작업 계약, `AGENTS.md`는 각각 어떤 범위를 맡는가?
- Codex 실행 성공과 기능 검증 성공은 왜 다른가?
- JSONL 로그에서 무엇을 측정할 수 있는가?
- `T_wall`과 `T_human`을 따로 재는 이유는 무엇인가?
- 요청 방식 두 개를 공정하게 비교하려면 무엇을 고정해야 하는가?

## 준비된 자료와 이번 주 산출물

과정이 먼저 제공하는 것은 아래와 같습니다.

| 준비된 자료 | 현재 상태와 용도 |
|---|---|
| `week01-codex-prompt-comparison/starter/ticket-title-normalizer/README.md` | Java 과제의 요구사항과 실습 의도를 설명합니다. |
| `build.gradle`, `settings.gradle` | Java 17 대상 컴파일, JUnit과 Gradle 프로젝트 이름을 정의합니다. |
| `src/main/java/lab/week01/TicketTitleNormalizer.java` | `normalize`가 의도적으로 미구현된 시작 코드입니다. |
| `src/test/java/lab/week01/TicketTitleNormalizerTest.java` | 공백·빈 값·Unicode 길이 경계를 확인하는 JUnit 공개 테스트입니다. |
| Gradle Wrapper | 전역 Gradle 설치 없이 IDE와 자동화가 같은 Gradle 버전을 사용하게 합니다. |
| 루트 `AGENTS.md` | 저장소 공통 규칙의 시작 예시이며, 학습자가 읽고 조정합니다. |
| `week01-codex-prompt-comparison/prompts/minimal.md` | Run A에서 그대로 붙여넣을 짧은 실험 입력입니다. |
| `week01-codex-prompt-comparison/prompts/structured.md` | Run B에서 그대로 붙여넣을 구조화된 실험 입력입니다. |
| `week01-codex-prompt-comparison/prompts/agents-audit.md` | Day 3에 직접 보낼 읽기 전용 확인 요청입니다. |
| `week01-codex-prompt-comparison/prompts/plan-only.md` | 계획만 받고 싶을 때 사용할 선택 요청입니다. |
| `shared/runner/` | 선택 심화에서 대화창에 보낸 입력을 반복 측정할 때 사용합니다. |

아래 항목은 학습자가 직접 요청하고 판단한 뒤 남기는 산출물입니다.

```text
week01-codex-prompt-comparison/notes/00_ai_ax_direction.md              선택: 학습 방향 메모
week01-codex-prompt-comparison/experiments/request-a.md
week01-codex-prompt-comparison/experiments/request-b.md
week01-codex-prompt-comparison/experiments/run-a/
week01-codex-prompt-comparison/experiments/run-b/
week01-codex-prompt-comparison/experiments/measured-a/       선택 측정
week01-codex-prompt-comparison/experiments/measured-b/       선택 측정
week01-codex-prompt-comparison/experiments/prompt-comparison.md
week01-codex-prompt-comparison/notes/week01-retrospective.md
```

## 실습 순서

| 일차 | 학습 내용 | 실습 결과 |
|---:|---|---|
| 1 | 준비된 Java 과제 이해 | 파일 구조·요구사항·의도된 실패 설명 |
| 2 | 준비된 두 프롬프트 직접 전송 | 대화형 A/B 실행과 요청 원문 |
| 3 | `AGENTS.md` 계층 | 직접 쓴 지침과 적용 확인 |
| 4 | 선택 측정과 실행 로그 | 같은 요청의 JSONL 요약과 시간 기록 |
| 5 | 비교·회고·구술 점검 | 비교표, 실패 카드, 글 초안 |

### 이번 주의 실행 지도

| 단계 | 무엇을 확인하려는가 | 처음 예상되는 결과 | 반드시 남길 것 |
|---|---|---|---|
| 과제 읽기 | 무엇을 구현하고 테스트가 무엇을 잡는지 이해했는가 | 시작 코드는 `UnsupportedOperationException`으로 비어 있음 | 본인이 설명한 요구사항과 파일 역할 |
| 대화형 Run A·B | 준비된 두 요청의 정보 차이가 결과에 영향을 주는가 | 둘 중 하나 또는 둘 다 실패할 수 있음 | 보낸 요청 원문·첫 응답·추가 교정·테스트 |
| `AGENTS.md` | 일회성 요청과 저장소 규칙의 범위가 어떻게 다른가 | 가까운 하위 지침이 해당 경로에서 우선 | 적용된 지침과 충돌 해소 기록 |
| 선택 측정 | 대화형 경험을 같은 고정 요청으로 재현할 수 있는가 | 새 복사본에 JSONL과 metadata가 생김 | 실제 전송한 요청, `summary.json`, 원본 로그 |

---

### Day 1 — 준비된 Java 과제를 이해하기

이 과제는 Java 실력을 평가하려고 고른 것이 아닙니다. 작은 메서드 하나와 공개 테스트만 두어, 요청에 어떤 정보를 담았는지가 결과에 미치는 영향을 보기 위한 통제된 시작점입니다.

#### 먼저 열어 볼 파일

```text
week01-codex-prompt-comparison/starter/ticket-title-normalizer/README.md
week01-codex-prompt-comparison/starter/ticket-title-normalizer/build.gradle
week01-codex-prompt-comparison/starter/ticket-title-normalizer/settings.gradle
week01-codex-prompt-comparison/starter/ticket-title-normalizer/src/main/java/lab/week01/TicketTitleNormalizer.java
week01-codex-prompt-comparison/starter/ticket-title-normalizer/src/test/java/lab/week01/TicketTitleNormalizerTest.java
```

각 파일을 에디터에서 직접 엽니다.

- `README.md`: 구현 요구사항과 이 과제를 준비한 이유
- `build.gradle`: Java 17 컴파일 조건, JUnit 의존성과 `test` 작업
- `settings.gradle`: IDE와 Gradle이 표시할 프로젝트 이름
- `TicketTitleNormalizer.java`: 현재 한 메서드가 의도적으로 비어 있는 시작 코드
- `TicketTitleNormalizerTest.java`: 공백, `null`, 빈 입력, 80 code point와 이모지를 확인하는 JUnit 공개 테스트

#### Gradle 프로젝트에서는 무엇이 일어나는가

IDE에서 과제 폴더를 Gradle 프로젝트로 가져오면 다음 순서로 처리됩니다.

```text
build.gradle과 settings.gradle 읽기
→ 프로젝트의 Gradle Wrapper 버전 확인
→ main 코드와 test 코드를 Java 17 대상으로 컴파일
→ JUnit이 @Test 메서드 여섯 개를 찾아 실행
→ IDE 테스트 창과 build/reports/tests/에 결과 표시
```

`build.gradle`에서 먼저 볼 부분은 다음과 같습니다.

| 설정 | 역할 |
|---|---|
| `plugins { id 'java' }` | 표준 Java 소스 구조와 컴파일·테스트 작업을 추가합니다. |
| `repositories { mavenCentral() }` | Gradle이 처음 동기화할 때 JUnit을 받을 저장소를 지정합니다. Maven으로 빌드한다는 뜻은 아닙니다. |
| `testImplementation ... junit-jupiter` | 테스트 코드를 JUnit으로 작성하고 IDE에서 인식하게 합니다. |
| `options.release.set(17)` | 더 최신 JDK를 사용해도 Java 17 API와 bytecode를 대상으로 컴파일합니다. |
| `useJUnitPlatform()` | Gradle의 `test` 작업이 JUnit Jupiter 테스트를 실행하게 합니다. |

`gradlew`, `gradlew.bat`과 `gradle/wrapper/`는 Gradle이 생성한 공식 Wrapper입니다. 운영체제마다 진입 파일은 다르지만 테스트 로직이 들어 있는 파일은 아닙니다. 학습자는 IDE에서 같은 `test` 작업을 실행하면 되며, Wrapper 파일을 직접 읽거나 전역 Gradle을 설치할 필요가 없습니다. 처음 동기화할 때만 Gradle과 JUnit을 내려받기 위한 인터넷 연결이 필요합니다.

Gradle 동기화 실패는 JDK·네트워크·의존성 문제, 컴파일 실패는 Java 소스 문제입니다. `UnsupportedOperationException`은 미구현 기준선이고, JUnit assertion 실패는 요구사항 불일치입니다. 초록색 결과는 공개 테스트를 통과했다는 뜻이며 공개되지 않은 모든 경우까지 맞다는 보장은 아닙니다. `.gradle/`과 `build/`는 생성물이므로 구현 코드와 구분합니다.

AI에 요청하기 전에 다음을 본인의 말로 설명해 봅니다.

```text
입력 "  결제   오류\n문의  "는 어떤 결과가 되어야 하는가?
null과 공백뿐인 입력은 어떻게 처리해야 하는가?
문자 수가 아니라 Unicode code point를 세는 이유는 무엇인가?
현재 테스트가 확인하는 것과 확인하지 않는 것은 무엇인가?
```

처음부터 답을 완벽히 알 필요는 없습니다. 모르는 부분은 그대로 적어 두고, 나중에 Codex에 질문할 내용과 구현을 맡길 내용을 구분합니다.

Codex 환경은 다음 두 명령으로 확인합니다. 이 명령은 파일을 바꾸지 않습니다.

```powershell
codex --version
codex login status
```

이후 VS Code나 IntelliJ에서 `week01-codex-prompt-comparison/starter/ticket-title-normalizer` 폴더를 프로젝트로 엽니다. Gradle 가져오기를 승인하고 동기화가 끝나면 `TicketTitleNormalizerTest`의 실행 버튼을 누릅니다. Gradle 도구 창의 `verification > test`를 실행해도 같은 공개 테스트가 동작합니다. 구현 전에는 여섯 테스트가 `UnsupportedOperationException`으로 실패하는 것이 정상입니다. 이 화면에서 테스트 이름, 실패한 줄과 stack trace를 직접 확인해 둡니다.

IDE가 JDK를 찾지 못하거나 Gradle 동기화가 실패할 때만 터미널의 `java -version`으로 설정을 보조 확인합니다. Gradle 9.6.1은 JDK 17 이상에서 실행되며, 이 프로젝트는 실제 컴파일 대상을 Java 17로 고정합니다.

`week01-codex-prompt-comparison/notes/00_ai_ax_direction.md`는 학습 이유를 남기고 싶을 때 작성합니다. 특정 공고, 프로젝트 기능이나 가설 5~8개를 지금 정하는 것은 1주차의 선행 조건이 아닙니다.

---

### Day 2 — 준비된 두 프롬프트를 직접 보내기

Day 1에서 읽은 과제를 같은 시작 상태 두 개로 복사합니다. 기존 `run-a`나 `run-b`가 있으면 결과가 섞이지 않도록 명령이 중단됩니다. 기존 기록은 지우지 말고 다른 Run 이름으로 보존합니다.

```powershell
New-Item -ItemType Directory -Force week01-codex-prompt-comparison\experiments | Out-Null
if (Test-Path week01-codex-prompt-comparison\experiments\run-a) { throw "run-a가 이미 있습니다." }
if (Test-Path week01-codex-prompt-comparison\experiments\run-b) { throw "run-b가 이미 있습니다." }
Copy-Item -Recurse -LiteralPath week01-codex-prompt-comparison\starter\ticket-title-normalizer -Destination week01-codex-prompt-comparison\experiments\run-a
Copy-Item -Recurse -LiteralPath week01-codex-prompt-comparison\starter\ticket-title-normalizer -Destination week01-codex-prompt-comparison\experiments\run-b

git add --chmod=+x week01-codex-prompt-comparison/experiments/run-a/gradlew `
  week01-codex-prompt-comparison/experiments/run-b/gradlew
```

마지막 명령은 Windows에서 복사한 `gradlew`에도 Git의 실행 권한 비트를 다시 기록합니다. Windows에서 직접 실행할 때는 `gradlew.bat`를 쓰더라도, Linux나 GitHub Actions에서 두 복사본의 `gradlew`를 실행하려면 이 비트가 필요합니다. 두 Run의 시작 상태를 커밋하기 전에 한 번만 실행합니다.

#### A와 B를 동시에 실행하려면

동시 실행은 필수가 아니지만 시간을 줄이고 싶다면 Codex 앱의 서로 다른 새 작업 두 개를 사용합니다. 같은 대화에서 A와 B를 이어서 보내면 앞선 응답이 다음 실행의 맥락이 되므로 비교 실험이 아닙니다.

Git Worktree까지 사용하려면 두 Run 폴더를 만든 현재 상태를 먼저 커밋합니다. 이때 위의 `git add --chmod=+x` 결과도 포함합니다. 커밋 메시지는 루트 `AGENTS.md` 규칙에 따라 AngularJS 형식과 한국어 제목을 함께 사용합니다. 예를 들면 `chore(week01): A/B 실험 시작 상태 준비`처럼 두 폴더가 아직 같은 상태라는 사실이 드러나게 적습니다.

1. Codex 앱에서 `week01-codex-prompt-comparison/experiments/run-a`와 `run-b`를 각각 별도 Local 프로젝트로 추가합니다. 한 프로젝트 안에서 폴더 이름만 언급하는 것으로 대신하지 말고, 각 프로젝트의 primary folder가 정확히 해당 Run 폴더인지 확인합니다.
2. Run A 프로젝트에서 새 작업을 열어 `Worktree`를 선택하고 방금 커밋한 `main`을 시작점으로 고릅니다. Run B도 별도 프로젝트에서 같은 시작 commit으로 새 Worktree 작업을 만듭니다. 각 Worktree에서도 primary folder가 `run-a` 또는 `run-b`를 가리켜야 합니다.
3. 두 작업의 모델, reasoning, 권한과 검증 조건을 같게 유지하고 각각 고정된 프롬프트만 보냅니다.
4. 첫 응답과 테스트 결과를 기록한 뒤 각 Worktree에서 `Create branch here`를 사용해 `week01/run-a`, `week01/run-b`처럼 서로 다른 브랜치를 만듭니다.
5. A 브랜치는 `run-a/`와 `request-a.md`, B 브랜치는 `run-b/`와 `request-b.md`만 포함하는지 확인한 뒤 GitHub에 올립니다. 변경 경로가 겹치지 않으므로 두 브랜치를 차례로 `main`에 병합하면 최종 저장소에서 두 결과를 함께 볼 수 있습니다.
6. 두 결과가 합쳐진 뒤에만 `prompt-comparison.md`를 작성합니다. 두 실험 브랜치가 이 공용 비교 문서를 동시에 수정하지 않게 합니다.

Worktree를 사용하지 않아도 `run-a`와 `run-b`를 각각 별도 Local 프로젝트의 primary folder로 열고 새 작업을 만들면 동시에 실행할 수 있습니다. 다만 같은 Local checkout에서 두 작업이 Git 명령이나 공용 파일을 함께 건드리지 않도록 주의해야 합니다. 독립성이 중요한 첫 비교에는 Worktree 두 개가 더 명확합니다.

이제 아래 두 파일을 직접 엽니다.

```text
week01-codex-prompt-comparison/prompts/minimal.md
week01-codex-prompt-comparison/prompts/structured.md
```

두 파일에는 사용 설명과 `직접 보낼 내용`이 구분돼 있습니다. 같은 내용은 `CURRENT_WEEK.md` 아래쪽의 `이번 주에 사용할 프롬프트 자료`에도 표시됩니다. 설명까지 통째로 보내지 말고 표시된 프롬프트 본문만 사용합니다. A는 짧은 요청, B는 목표·허용 경로·금지 변경·인수 조건·검증·보고 형식을 포함한 요청입니다.

보내기 전에 다음을 먼저 예상합니다.

```text
Run A가 작업 폴더의 README와 테스트를 스스로 읽을 가능성
Run A에서 빠질 수 있는 요구사항이나 검증
Run B의 각 섹션이 줄이려는 모호성
두 Run에서 같게 유지해야 할 모델·권한·시작 코드
```

다음 중 한 표면만 골라 A와 B에서 계속 사용합니다.

- Codex 앱: `run-a`와 `run-b`를 각각 별도 Local 프로젝트의 primary folder로 연 뒤, 각 프로젝트에서 새 작업을 만들고 프롬프트 본문을 붙여넣습니다.
- 대화형 CLI: 아래의 `codex -C <Run 경로>` 명령으로 각 세션을 따로 연 뒤, 나타나는 입력창에 프롬프트 본문을 붙여넣습니다.

프롬프트를 보내기 전에 앱에 표시된 primary folder 또는 CLI의 `-C` 경로를 각 Run 기록에 남깁니다. 첫 구현 응답을 평가하거나 후속 수정을 요청하기 전에는 실행 로그에서 실제로 읽은 `README.md`, `build.gradle`, `src/`와 `test/` 경로도 확인해 적습니다. 폴더 이름을 프롬프트에 썼다는 사실만으로 올바른 작업 폴더에서 실행됐다고 판단하지 않습니다.

#### Run A — 짧은 고정 입력

`minimal.md`의 `직접 보낼 내용`을 복사합니다. Codex 앱에서는 `week01-codex-prompt-comparison/experiments/run-a`를 작업 대상으로 새 작업을 열어 붙여넣고 직접 전송합니다. CLI를 쓴다면 다음 명령으로 대화형 세션을 연 뒤 붙여넣습니다.

```powershell
codex -C .\week01-codex-prompt-comparison\experiments\run-a
```

이 명령은 프롬프트 파일을 자동으로 읽지 않습니다. 작업 폴더만 정하며, 학습자가 대화창에 본문을 붙여넣고 전송합니다.

첫 응답이 끝나면 바로 후속 수정을 시키지 말고 다음을 확인합니다.

1. 어떤 파일을 읽고 바꿨는지 확인합니다.
2. 변경된 코드를 읽습니다.
3. IDE에서 공개 JUnit 테스트를 직접 실행합니다.
4. 누락되었거나 이해되지 않는 부분을 적습니다.
5. 대화창에서 실제로 보낸 첫 요청을 그대로 `week01-codex-prompt-comparison/experiments/request-a.md`에 옮깁니다.

`week01-codex-prompt-comparison/experiments/run-a`를 IDE에서 별도 Gradle 프로젝트로 열거나 현재 IDE 작업 공간에 추가합니다. Gradle 동기화 뒤 `TicketTitleNormalizerTest`를 실행하고, 통과·실패 개수와 첫 실패 원인을 기록합니다. Codex가 테스트를 실행했다고 보고했더라도 학습자가 결과 창을 다시 확인합니다.

첫 결과를 기록한 뒤에는 평소처럼 후속 질문이나 수정 요청을 보내도 됩니다. 다만 몇 번의 교정이 필요했는지 셉니다.

#### Run B — 구조화된 고정 입력

`structured.md`에서 다음 구성 요소가 실제 과제의 어느 파일과 연결되는지 확인합니다.

```text
Goal                 구현할 메서드
Context              작업 폴더의 README·시작 코드·테스트
Allowed paths        수정해도 되는 위치
Forbidden changes    건드리면 안 되는 공개 테스트와 외부 의존성
Acceptance criteria  정상·경계 동작
Verification         직접 실행할 테스트
Report               변경·검증·남은 위험
```

`structured.md`의 `직접 보낼 내용`을 복사해 Codex 앱의 `run-b` 새 작업에 붙여넣거나, 아래 대화형 세션에 붙여넣고 직접 전송합니다. Run A의 후속 대화는 Run B로 가져오지 않습니다.

```powershell
codex -C .\week01-codex-prompt-comparison\experiments\run-b
```

첫 응답 뒤에는 Run A와 같은 순서로 코드와 테스트를 직접 확인하고, 실제 보낸 문장을 `week01-codex-prompt-comparison/experiments/request-b.md`에 옮깁니다. `run-b`도 별도 Gradle 프로젝트로 열어 같은 JUnit 테스트를 실행하고 같은 항목을 기록합니다.

원본과 달라진 줄은 IDE의 파일 비교 기능으로 먼저 확인합니다. VS Code에서는 두 파일을 차례로 선택해 비교하고, IntelliJ에서는 `Compare Files`를 사용합니다. 줄 수를 함께 기록하고 싶을 때만 아래 명령을 보조로 사용합니다. `git diff --no-index`의 종료 코드 `1`은 차이가 발견됐다는 뜻이며 오류가 아닙니다.

```powershell
git diff --no-index --numstat `
  week01-codex-prompt-comparison\starter\ticket-title-normalizer\src\main\java week01-codex-prompt-comparison\experiments\run-a\src\main\java

git diff --no-index --numstat `
  week01-codex-prompt-comparison\starter\ticket-title-normalizer\src\main\java week01-codex-prompt-comparison\experiments\run-b\src\main\java
```

`week01-codex-prompt-comparison/experiments/prompt-comparison.md`에는 두 요청의 원문, 각 프롬프트가 작업 폴더의 어떤 맥락을 명시했는지, 첫 결과의 테스트 통과 여부, 누락된 요구사항, 추가 교정 횟수와 사람이 검토한 시간을 적습니다. 어느 요청이 “더 그럴듯해 보였는지”보다 코드·diff·테스트를 근거로 판정합니다.

이 실험을 마친 뒤에만 선택적으로 본인 방식의 세 번째 요청을 만들어 볼 수 있습니다. A/B의 고정 입력을 먼저 바꾸면 제공된 두 요청의 차이를 비교하는 실험이 아니게 됩니다.

---

### Day 3 — `AGENTS.md`를 읽고 직접 적용해 보기

과정이 준비한 루트 `AGENTS.md`를 먼저 엽니다. 이 파일은 완성 답안이 아니라, 저장소 전체에서 반복할 규칙의 예입니다. 각 항목이 왜 일회성 요청이 아니라 장기 규칙인지 본인의 말로 설명하고, 불필요하거나 빠진 항목은 직접 고칩니다.

```text
수정 범위와 기존 변경 보존
필수 검증 명령
비밀값과 테스트에 관한 규칙
미검증 결과 표기
완료 보고 형식
```

그다음 `week01-codex-prompt-comparison/starter/ticket-title-normalizer/AGENTS.md`를 만들어 이 Java 과제에만 필요한 지침을 추가합니다. AI에 확인을 맡기기 전에 다음 두 가지를 먼저 예상해 적습니다.

- 루트 지침 중 이 경로에도 적용될 항목
- 하위 지침을 추가했을 때 더 구체적으로 바뀔 항목

`agents-audit.md`는 이때 사용하는 준비된 검토 요청입니다. 먼저 본인의 예상부터 적은 뒤 파일의 `직접 보낼 내용`을 복사해 Codex 입력창에 붙여넣습니다. 이 요청은 현재 작업 경로에 적용되는 지침, 지침을 찾은 파일과 충돌 시 우선한 근거를 설명하게 합니다.

- Codex 앱에서는 Java 과제 폴더를 대상으로 새 작업을 열고, 파일은 바꾸지 말고 설명만 해 달라고 직접 질문합니다.
- CLI에서는 `codex -s read-only -C .\week01-codex-prompt-comparison\starter\ticket-title-normalizer`로 대화형 세션을 열고 직접 질문합니다.

AI의 설명을 정답으로 받아들이지 말고 루트와 하위 `AGENTS.md` 원문을 직접 대조합니다. 안전한 범위에서만 충돌도 시험합니다. 예를 들어 완료 보고 언어를 서로 다르게 지정할 수 있지만, 파일 삭제나 테스트 생략처럼 결과를 위험하게 만드는 충돌은 만들지 않습니다. 실험 뒤에는 충돌을 제거하고 예상·실제 결과·근거를 실패 카드에 남깁니다.

핵심은 저장소의 장기 규칙과 한 번만 쓰는 작업 요구를 구분하고, 최종 적용 여부를 학습자가 확인하는 데 있습니다.

---

### Day 4 — 선택 심화: 대화창에 보낸 입력을 JSONL로 재현하기

Day 2까지가 일반적인 Codex 사용 실습입니다. 이 단계는 대화창에 직접 붙여넣어 사용한 두 고정 입력을 같은 조건에서 반복 측정하고 싶을 때만 진행합니다. `request-a.md`와 `request-b.md`에는 실제로 보낸 프롬프트 본문만 저장하고, 사용 설명이나 Markdown 코드 울타리는 넣지 않습니다.

원본과 대화형 결과는 보존하고, 측정용 복사본을 새로 만듭니다.

```powershell
if (Test-Path week01-codex-prompt-comparison\experiments\measured-a) { throw "measured-a가 이미 있습니다." }
if (Test-Path week01-codex-prompt-comparison\experiments\measured-b) { throw "measured-b가 이미 있습니다." }
Copy-Item -Recurse -LiteralPath week01-codex-prompt-comparison\starter\ticket-title-normalizer -Destination week01-codex-prompt-comparison\experiments\measured-a
Copy-Item -Recurse -LiteralPath week01-codex-prompt-comparison\starter\ticket-title-normalizer -Destination week01-codex-prompt-comparison\experiments\measured-b
$Model = "gpt-5.6"
$Reasoning = "medium"
```

설치된 CLI에서 예시 모델 ID를 사용할 수 없다면 본인 환경의 모델로 바꾸되 두 실행에 같은 값을 사용합니다. Runner는 Windows에서도 요청 원문과 JSONL을 UTF-8로 보존하고 작업 폴더·종료 코드·경과 시간을 함께 기록하기 위한 반복 측정 도구입니다.

```powershell
python shared\runner\run_codex_exec.py `
  --prompt week01-codex-prompt-comparison\experiments\request-a.md `
  --working-directory week01-codex-prompt-comparison\experiments\measured-a `
  --events week01-codex-prompt-comparison\experiments\measured-a\events.jsonl `
  --stderr week01-codex-prompt-comparison\experiments\measured-a\stderr.log `
  --metadata week01-codex-prompt-comparison\experiments\measured-a\run.json `
  --model $Model --reasoning $Reasoning `
  --sandbox workspace-write --approval-policy never `
  --timeout-seconds 1800 --ignore-user-config

python shared\runner\run_codex_exec.py `
  --prompt week01-codex-prompt-comparison\experiments\request-b.md `
  --working-directory week01-codex-prompt-comparison\experiments\measured-b `
  --events week01-codex-prompt-comparison\experiments\measured-b\events.jsonl `
  --stderr week01-codex-prompt-comparison\experiments\measured-b\stderr.log `
  --metadata week01-codex-prompt-comparison\experiments\measured-b\run.json `
  --model $Model --reasoning $Reasoning `
  --sandbox workspace-write --approval-policy never `
  --timeout-seconds 1800 --ignore-user-config
```

`codex_exit_code=0`은 Codex 실행이 끝났다는 뜻이지 기능이 맞다는 뜻은 아닙니다. `measured-a`와 `measured-b`를 각각 IDE의 Gradle 프로젝트로 열고 JUnit 테스트를 다시 실행합니다. 통과·실패 개수와 첫 실패를 기록한 뒤 JSONL 로그를 요약합니다.

```powershell
python shared\runner\parse_codex_jsonl.py `
  week01-codex-prompt-comparison\experiments\measured-a\events.jsonl `
  --metadata week01-codex-prompt-comparison\experiments\measured-a\run.json `
  --output week01-codex-prompt-comparison\experiments\measured-a\summary.json

python shared\runner\parse_codex_jsonl.py `
  week01-codex-prompt-comparison\experiments\measured-b\events.jsonl `
  --metadata week01-codex-prompt-comparison\experiments\measured-b\run.json `
  --output week01-codex-prompt-comparison\experiments\measured-b\summary.json
```

`parse_status`, `execution_status`, 기능 테스트 결과는 서로 다른 판정입니다. 사람이 diff와 요구사항을 검토한 시간도 `shared/runner/human_timer.py`로 따로 잴 수 있습니다. Timer 결과가 다른 주차와 섞이지 않도록 주차별 출력 경로를 명시합니다.

```powershell
python shared\runner\human_timer.py `
  --output-root week01-codex-prompt-comparison\experiments\timers `
  start --run-id run-a-review --activity review

# 직접 검토를 마친 뒤
python shared\runner\human_timer.py `
  --output-root week01-codex-prompt-comparison\experiments\timers `
  stop --run-id run-a-review --activity review
```

원한다면 원본 로그의 복사본 한 줄을 의도적으로 손상시켜 파싱 오류와 정상 JSON으로 기록된 실행 오류가 어떻게 다른지도 확인합니다. 이 측정은 대화형 Run A·B의 결과를 대체하지 않고, 같은 요청을 자동 실행했을 때의 별도 표본으로 기록합니다.

---

### Day 5 — 결과를 비교하고 말로 설명하기

`shared/templates/weekly-retrospective.md`를 복사해 회고를 작성합니다.

```powershell
if (Test-Path week01-codex-prompt-comparison\notes\week01-retrospective.md) {
  throw "기존 회고가 있습니다. 덮어쓰지 말고 내용을 확인하세요."
}
Copy-Item -LiteralPath shared\templates\weekly-retrospective.md `
  -Destination week01-codex-prompt-comparison\notes\week01-retrospective.md
```

다음 질문에 자료를 보지 않고 답한 뒤, 모호한 부분만 다시 확인합니다.

```text
준비된 Java 과제에서 구현할 부분과 이미 제공된 부분은 무엇이었는가?
직접 전송한 Run A와 Run B 프롬프트는 무엇이 달랐는가?
AGENTS.md에는 어떤 내용을 넣는 편이 좋은가?
선택 측정을 했다면 JSONL에서 파싱 오류와 실행 오류를 어떻게 구분했는가?
전체 경과 시간과 사람 작업 시간을 왜 따로 재는가?
이번 결과로 말할 수 있는 것과 아직 말하기 어려운 것은 무엇인가?
```

#### 블로그 자료

- 진행한 날마다 남긴 짧은 실험 노트
- 발행 후보 1편: `준비된 Java 과제를 Codex에 직접 요청하며 알게 된 것`
- 실패 카드 2개 이상

글의 첫 초안과 결론은 본인이 씁니다. AI는 빠진 질문을 찾거나 문장을 다듬는 데 활용할 수 있지만, 어떤 차이가 중요했는지와 결과를 수용할지는 대신 결정하게 두지 않습니다.

## 완료 기준

- [ ] 준비된 Java 과제의 파일 역할, 요구사항과 의도된 첫 실패를 설명할 수 있습니다.
- [ ] 동일한 시작 코드에서 준비된 두 프롬프트를 앱이나 대화형 CLI에 직접 붙여넣어 보냈습니다.
- [ ] 실제 보낸 요청 원문과 첫 결과, 후속 교정 횟수를 저장했습니다.
- [ ] 두 실행의 코드·diff·테스트·요구사항을 직접 비교했습니다.
- [ ] 루트 `AGENTS.md`를 검토하고 하위 `AGENTS.md`를 직접 작성해 적용 결과를 확인했습니다.
- [ ] 선택 측정을 했다면 대화형 결과와 자동 실행 표본을 구분하고 JSONL 요약을 남겼습니다.
- [ ] 실패 카드가 2개 이상 있습니다.
- [ ] 자료 없이 핵심 질문에 답할 수 있습니다.
- [ ] 발행 가능한 글 초안 한 편이 있습니다.

---

## 이 폴더의 자료

- `prompts/`: 이번 주에 직접 보내거나 측정에 사용할 요청 자료
- 그 밖의 폴더: 실습 시작 코드, 데이터, 평가 자료와 기록
- `references/`: 실습 뒤 `reference` 명령으로 공개한 비교용 참고 구현
