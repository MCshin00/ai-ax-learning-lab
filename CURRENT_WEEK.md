## 명령을 실행하기 전에 읽는 공통 안내

이 과정에서는 학습자가 명령을 직접 실행하고 결과를 판단합니다. Codex는 코드와 설명을 대신 만들어 주는 정답지가 아니라, 비교할 결과를 내거나 막힌 원인을 함께 찾는 도구로 사용합니다. “이 명령을 실행해 줘”라고 맡기기보다, 먼저 아래 설명을 읽고 본인이 실행한 뒤 관찰값을 남기는 것이 기본 흐름입니다.

명령은 별도 안내가 없으면 학습 저장소의 루트, 즉 `CURRENT_WEEK.md`, 주차별 폴더와 `shared/`가 보이는 폴더에서 실행합니다. 설명용 `text`, `json`, `dotenv` 블록은 복사해 실행하는 명령이 아닙니다. 실제로 실행하는 블록에는 `powershell`, `bash`, `python`을 표시합니다.

### AI를 활용하는 기본 순서

이 과정은 보통의 AI 코딩 도구 사용 흐름을 따릅니다. 작업 폴더에는 코드뿐 아니라 `README`, 작업 계약, `AGENTS.md`, 테스트와 설정이 준비돼 있고, 학습자는 그 맥락 위에서 Codex에 요청을 직접 보냅니다.

1. 작업 폴더에 준비된 코드·문서·테스트·설정을 확인합니다.
2. 이번 단계에서 사용할 프롬프트가 고정 실험 입력인지, 경로를 채울 템플릿인지, 검토 요청인지 확인합니다.
3. 프롬프트의 목적과 각 항목이 필요한 이유를 읽습니다.
4. Codex 앱이나 대화형 CLI의 입력창에 안내된 내용을 직접 입력하거나 붙여넣어 전송합니다.
5. Codex가 읽은 파일, 세운 계획, 만든 diff와 실행한 테스트를 확인합니다.
6. 필요한 후속 질문과 교정을 대화로 직접 이어 갑니다.
7. 결과를 검토하고 실제로 보낸 요청, 수정 횟수와 판정을 해당 주차 폴더의 `experiments/`에 남깁니다. 이 기록은 기본적으로 Git에 올리지 않고 로컬에 보관합니다.
8. 반복 실행과 정량 측정이 학습 목표가 되면 동결한 프롬프트 파일과 Runner·JSONL 자동화를 사용합니다.

프롬프트를 직접 전송한다는 말은 매번 문구를 처음부터 새로 만들라는 뜻이 아닙니다. A/B 비교처럼 문구 차이 자체가 실험 조건이면 제공된 내용을 그대로 복사해 보내야 합니다. 실제 프로젝트에 맞게 경로와 조건을 채우는 것이 목표라면 템플릿을 수정하고, 프롬프트 설계가 학습 목표인 단계에서만 본인이 새 문구를 작성합니다.

각 주차 폴더의 `prompts/`에는 다음 유형의 자료가 들어 있습니다.

| 유형 | 사용하는 방법 |
|---|---|
| 실험 입력 | 표시된 본문을 앱이나 대화형 CLI에 그대로 붙여넣어 보냅니다. |
| 요청 템플릿 | 실제 파일 경로와 조건을 채운 뒤 입력창에 붙여넣어 보냅니다. |
| 검토 요청 | 본인의 1차 판단을 남긴 뒤 표시된 본문을 직접 보내 반대 관점을 확인합니다. |
| 자동 측정용 | 대표 입력으로 실행 흐름과 판정 규칙을 확인한 뒤, 반복 실행 단계에서 동결한 파일 입력을 사용합니다. |

모든 주차가 네 유형을 전부 사용하지는 않습니다. 코드의 상태 전이, 평가 데이터 설계, Workflow 조립이나 배포 검증이 학습의 중심이라면 그 활동을 우선하고, 필요하지 않은 프롬프트 실습을 억지로 추가하지 않습니다. 각 주차 안내에 적힌 유형과 사용 시점을 따릅니다.

과정의 첫 사용 경험에서는 wrapper가 프롬프트 파일을 보이지 않게 읽어 자동 전송하지 않습니다. 학습자가 어떤 맥락과 문구를 보내는지 확인하고 직접 전송합니다.

### 누가 무엇을 결정하는가

| 주체 | 맡는 일 |
|---|---|
| 학습자 | 학습 목표, 성공 기준, 작업 경계, 실행 전 예상, 결과 수용 여부와 회고를 결정합니다. |
| AI | 모르는 부분을 설명하고, 구현을 돕고, 반례·테스트·대안을 제안합니다. 최종 판단의 근거는 학습자가 실제 파일과 결과에서 확인합니다. |
| 자동화 도구 | 이미 이해하고 직접 해 본 절차를 반복하고 로그와 수치를 모읍니다. 목표나 정답을 대신 정하지 않습니다. |

AI가 만든 계획이나 구현을 다음 단계의 승인으로 간주하지 않습니다. 계획 뒤에는 범위, 구현 뒤에는 diff와 테스트, 평가 뒤에는 오탐과 근거를 학습자가 확인한 다음 진행합니다.

### 실행 전에 확인할 여섯 가지

| 확인할 것 | 스스로 답할 질문 |
|---|---|
| 목적 | 이 명령으로 어떤 가설이나 경계를 확인하는가? |
| 준비 조건 | 어느 폴더에서 실행하며, 필요한 버전·로그인·환경변수는 무엇인가? |
| 변화 | 어떤 파일·Git 상태·외부 서비스가 바뀌고 비용이 생길 수 있는가? |
| 예상 결과 | 처음부터 통과해야 하는가, 과제를 남긴 채 실패해야 하는가? |
| 기록 | 종료 코드, 출력 파일, 시간, 토큰 중 무엇을 남겨야 하는가? |
| 재실행 | 기존 결과를 보존한 채 다시 실행할 수 있는가? |

### 실패를 세 층으로 나누기

- **환경 실패**: 실행을 시작할 조건이 갖춰지지 않은 상태입니다. 예를 들어 `python`이나 `codex`를 찾지 못하거나, IDE가 JDK를 찾지 못해 Gradle 동기화가 실패하거나, 가상환경 설치가 끝나지 않은 경우입니다. 과제 구현의 품질로 세지 않습니다.
- **의도된 기준선 실패**: 제공된 `TODO`나 `NotImplementedError` 때문에 처음에는 실패하도록 만든 경우입니다. 오류의 종류와 개수가 문서의 예상과 같은지 확인한 뒤 구현을 시작합니다.
- **구현·실험 실패**: 명령은 정상적으로 실행됐지만 인수 조건, 테스트, 평가 기준을 통과하지 못한 경우입니다. 이 결과가 학습 기록의 대상입니다.

“명령이 종료됐다”, “Codex 실행이 완료됐다”, “기능 테스트가 통과했다”는 서로 다른 상태입니다. JSONL 파서가 성공해도 Codex turn은 실패했을 수 있고, Codex가 정상 종료해도 구현 테스트는 실패할 수 있습니다. 세 상태를 한 칸에 `성공`이라고 합치지 않습니다.

### 파일과 외부 상태를 다루는 원칙

- 같은 실험을 다시 할 때는 기존 폴더를 덮어쓰지 말고 새 `run_id`를 사용합니다.
- `.env`, 가상환경, 빌드 산출물과 원시 로그는 Git에 넣기 전에 `.gitignore`를 확인합니다.
- Cloud, API, Dify Knowledge, MCP 등록처럼 저장소 밖을 바꾸는 명령은 생성되는 자원, 비용, 제거 방법을 먼저 적습니다.
- 일부러 깨뜨리는 실험은 복사본이나 폐기 가능한 Worktree에서만 합니다.
- `CURRENT_WEEK.md`, `LEARNING_GUIDE.md`, 루트 `README.md`, 시작한 주차의 `README.md`, 각 주차 폴더의 `prompts/`와 공개한 `references/`는 과정 도구가 다시 만드는 설명·프롬프트·참고 자료입니다.
- 실제로 보낸 본문, 후속 대화, 회고와 원시 실행 결과는 해당 주차의 `notes/`와 `experiments/`에 남깁니다. `notes/` 전체와 `experiments/`의 Markdown·JSONL·로그·`private/` 자료는 `.gitignore`에 포함되므로 개인 기록은 로컬에만 보관됩니다.
- A/B Run의 코드·테스트, Skills, MCP, 하네스와 평가 자산처럼 다른 학습자가 재현할 산출물은 계속 추적합니다. 5주차의 `notes/workflow-research.csv`와 10주차의 시작 벤치마크 문서처럼 과정이 먼저 제공한 파일도 예외로 추적됩니다. 새 개인 기록을 올리기 위해 `git add -f`를 사용하지 않습니다.

### 개인 기록과 Day 마감 커밋

개인 기록을 Git에서 제외해도 학습 진행 시점은 커밋으로 남깁니다. 각 주차의 **모든 Day를 마칠 때 최소 한 번**, 그날 직접 검증한 마지막 상태를 커밋합니다. 한 Day 안에서 실험 설계상 중간 커밋이 더 필요할 수 있으며, 이미 그날의 최종 상태를 커밋했다면 빈 커밋을 다시 만들 필요는 없습니다.

1. 테스트·평가·수동 확인 중 해당 Day가 요구한 검증을 마칩니다.
2. `git status --short`로 개인 기록이 stage되지 않았는지 확인합니다.
3. 재사용할 코드·테스트·설정만 stage하고 `git diff --cached`를 읽습니다.
4. `type(scope): 한국어 제목` 형식으로 Day 마감 커밋을 만듭니다. `scope`는 `week01`처럼 해당 주차를 사용합니다.

```powershell
git commit -m "feat(week05): Day 3 Hooks 구현"
```

읽기·회고만 진행해 공유할 변경이 없는 Day에는 ignored 개인 기록을 강제로 추가하지 않습니다. 대신 다음처럼 내용 없는 마감 커밋으로 검증 시점만 남깁니다.

```powershell
git commit --allow-empty -m "chore(week01): Day 1 학습 완료"
```

커밋 제목과 필요한 본문은 루트 `AGENTS.md`의 AngularJS 커밋 컨벤션을 따르고 한국어로 작성합니다. 개인 기록은 GitHub에 포함되지 않으므로, 커밋에는 재사용 가능한 산출물과 Day 완료 시점만 남습니다.

Windows에서는 `python`이 연결되지 않았다면 `py -3`를 사용할 수 있습니다. 다만 가상환경을 만든 뒤에는 활성화 성공 여부에 기대지 말고 `.\<주차 폴더>\.venv\Scripts\python.exe`처럼 그 주차 환경의 Python을 직접 부르는 편이 안전합니다.

---

# 1주차 — 직접 요청하고 Codex 작업을 관찰하기

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
```

두 Run의 코드와 테스트는 다른 학습자가 비교 결과를 재현할 수 있는 산출물이므로 Git에 포함합니다. 반면 `request-a.md`, `request-b.md`, 첫 응답과 비교 회고 같은 Markdown 기록은 `.gitignore` 대상입니다. Windows에서 복사한 두 `gradlew`의 Git 실행 권한은 시작 상태를 커밋하기 전에 다음처럼 기록합니다.

```powershell
git add --chmod=+x week01-codex-prompt-comparison/experiments/run-a/gradlew `
  week01-codex-prompt-comparison/experiments/run-b/gradlew
```

#### A와 B를 동시에 실행하려면

동시 실행은 필수가 아니지만 시간을 줄이고 싶다면 Codex 앱의 서로 다른 새 작업 두 개를 사용합니다. 같은 대화에서 A와 B를 이어서 보내면 앞선 응답이 다음 실행의 맥락이 되므로 비교 실험이 아닙니다.

1. Codex 앱에서 `week01-codex-prompt-comparison/experiments/run-a`와 `run-b`를 각각 별도 Local 프로젝트로 추가합니다. 한 프로젝트 안에서 폴더 이름만 언급하는 것으로 대신하지 말고, 각 프로젝트의 primary folder가 정확히 해당 Run 폴더인지 확인합니다.
2. 각 프로젝트에서 새 작업을 열고 두 작업의 모델, reasoning, 권한과 검증 조건을 같게 맞춥니다.
3. Run A에는 A 프롬프트만, Run B에는 B 프롬프트만 보냅니다. 두 작업이 상대 Run이나 저장소 공용 파일을 수정하지 않도록 작업 경계를 함께 적습니다.
4. 첫 응답과 테스트 결과는 각 Run과 `request-a.md`, `request-b.md`에 로컬 기록으로 남깁니다.
5. 두 실행이 끝난 뒤 `prompt-comparison.md`를 작성합니다. 이 파일은 개인 기록으로 로컬에 남기고, 두 Run의 코드·테스트와 재사용 가능한 `AGENTS.md`는 Day 마감 커밋에 포함합니다.

서로 다른 폴더를 primary folder로 연 두 작업이면 A/B의 수정 경로가 분리됩니다. 두 결과는 같은 Day 마감 커밋에 함께 넣거나 별도 브랜치에서 커밋한 뒤 합칠 수 있습니다. Git Worktree의 브랜치 분리·병합 자체는 4주차에서 더 자세히 다룹니다.

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
- [ ] 각 Day의 마지막 검증 시점을 AngularJS 형식의 한국어 커밋으로 한 번씩 남겼습니다.

---

## 이번 주에 사용할 프롬프트 자료

아래 파일에는 실험 입력, 요청 템플릿, 검토 요청 또는 자동 측정용 입력이 들어 있습니다. 각 파일의 사용 시점과 작업 폴더를 먼저 확인하세요. 대화형 실습에서는 표시된 `직접 보낼 내용`만 복사해 Codex 앱이나 대화형 CLI의 입력창에 직접 붙여넣습니다. 사용 설명까지 통째로 보내거나 wrapper가 파일을 보이지 않게 자동 전송하지 않습니다. 반복 측정 단계에서는 안내에 따라 실제 전송한 본문을 동결한 파일을 사용할 수 있습니다. 프롬프트 사용이 핵심이 아닌 주차에서는 해당 코드·데이터·도구 실습을 우선합니다.

### `agents-audit.md`

````markdown
# [검토 요청] `AGENTS.md` 적용 확인

## 사용 방법

루트와 하위 지침의 적용 결과를 먼저 예상한 뒤 사용합니다. 아래 본문만 복사해 읽기 전용 Codex 작업이나 대화형 CLI 입력창에 직접 붙여넣어 보냅니다.

## 직접 보낼 내용

```text
저장소를 수정하지 말고 루트와 현재 디렉터리에 적용되는 AGENTS.md 지침을 우선순위와 함께 설명하라. 서로 충돌하거나 모호한 규칙이 있으면 정확한 파일과 문구를 지적하라.
```

응답은 원본 `AGENTS.md`와 직접 대조합니다.
````

### `minimal.md`

````markdown
# [실험 입력 A] 짧은 요청

`week01-codex-prompt-comparison/experiments/run-a`에 과제 파일이 준비된 상태에서 사용합니다. 아래 본문만 복사해 Codex 앱이나 대화형 CLI의 입력창에 직접 붙여넣어 보냅니다.

## 직접 보낼 내용

```text
TicketTitleNormalizer를 구현하고 테스트를 통과시켜줘.
```

이 입력은 의도적으로 짧습니다. 작업 폴더의 README·코드·테스트를 Codex가 얼마나 찾아 읽는지, 어떤 조건을 빠뜨리는지 관찰합니다.
````

### `plan-only.md`

````markdown
# [요청 템플릿] 계획만 받기

구현 전 계획만 검토하고 싶을 때 사용합니다. 대상 작업 폴더에 작업 계약과 코드가 준비됐는지 확인한 뒤 아래 본문을 Codex 입력창에 직접 붙여넣어 보냅니다.

## 직접 보낼 내용

```text
코드를 수정하지 마라. 작업 계약과 현재 코드를 읽고 구현 계획, 수정 파일, 테스트 계획, 위험, 중단해야 하는 조건만 handoff 형식으로 작성하라.
```
````

### `structured.md`

````markdown
# [실험 입력 B] 구조화된 요청

`week01-codex-prompt-comparison/experiments/run-b`에 같은 과제 파일이 준비된 상태에서 사용합니다. 각 섹션이 작업 폴더의 어떤 파일과 조건을 가리키는지 확인한 뒤, 아래 본문만 복사해 Codex 앱이나 대화형 CLI의 입력창에 직접 붙여넣어 보냅니다.

## 직접 보낼 내용

```markdown
# Goal
현재 작업 폴더의 `TicketTitleNormalizer.normalize`를 구현한다.

# Context
- 먼저 `README.md`, `build.gradle`, `src/main/java/lab/week01/TicketTitleNormalizer.java`, `src/test/java/lab/week01/TicketTitleNormalizerTest.java`를 읽는다.
- 시작 코드는 의도적으로 미구현 상태다.

# Allowed paths
- `src/main/java/**`
- 필요하면 `src/test/java/**`에 새 회귀 테스트 추가

# Forbidden
- Public Test 수정·삭제
- Gradle 설정이나 기존 의존성 변경
- 다른 폴더 변경

# Acceptance criteria
- null·blank는 IllegalArgumentException
- 앞뒤 공백 제거
- 연속 whitespace를 한 칸으로 변환
- 결과가 Unicode code point 기준 80자를 넘으면 앞의 80개 code point만 남김
- Java 17

# Verification
- 이 Gradle 프로젝트의 `test` 작업을 실행한다.
- IDE가 아닌 자동 검증에서는 현재 운영체제에 맞는 Gradle Wrapper를 사용한다.

# Handoff
변경 파일, 실행한 테스트, 남은 위험을 보고한다.
```

Run A와 비교할 때는 이 본문을 임의로 줄이거나 늘리지 않습니다.
````

---

## 다음 단계

아래 과정 명령은 `course.py`가 있는 과정 패키지 폴더에서 실행합니다.

- 진행 상태 확인: `python course.py status "ai-ax-learning-lab"`
- 실습 완료 후 참고 구현 확인: `python course.py reference "ai-ax-learning-lab"`
- 완료 기준을 통과한 뒤 다음 주차 시작: `python course.py next "ai-ax-learning-lab"`
