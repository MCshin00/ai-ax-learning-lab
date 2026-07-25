# 1주차 — Codex 작업을 관찰하고 측정하기

같은 작은 과제를 두 가지 요청 방식으로 실행해 결과의 차이를 확인합니다. 이어서 `AGENTS.md`로 저장소 규칙을 전달하고, Codex의 실행 이벤트와 사람이 쓴 시간을 함께 기록합니다.

## 학습 목표

- 짧은 요청과 구조화된 작업 계약을 같은 조건에서 비교합니다.
- 저장소 규칙과 일회성 작업 요구를 구분합니다.
- `codex exec --json` 로그에서 결과·오류·도구 사용·토큰을 읽습니다.
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

## 이번에 만들 것

```text
notes/00_ai_ax_direction.md
notes/career-map.csv
experiments/week01/run-a/
experiments/week01/run-b/
experiments/week01/prompt-comparison.md
notes/week01-retrospective.md
```

## 실습 순서

| 일차 | 학습 내용 | 실습 결과 |
|---:|---|---|
| 1 | AI/AX 직무와 현재 기준점 | 방향 문서와 공고 분류표 |
| 2 | 짧은 요청과 작업 계약 | 동일 과제 A/B 실행 |
| 3 | `AGENTS.md` 계층 | 루트·하위 지침과 적용 확인 |
| 4 | 실행 로그와 사람 작업 시간 | JSONL 요약과 시간 기록 |
| 5 | 비교·회고·구술 점검 | 비교표, 실패 카드, 글 초안 |

---

### Day 1 — 학습 목표를 직무와 연결하기

#### 할 일

1. `notes/00_ai_ax_direction.md`를 만들고 아래 질문에 각각 3~5문장으로 답합니다.

```text
내가 AI/AX로 옮기려는 이유는 무엇인가?
지금까지 Codex·Skills·MCP를 어디까지 사용해 봤는가?
3개월 뒤 공개 자료로 무엇을 증명하고 싶은가?
개발·기획·자동화 중 어떤 업무에 더 끌리는가?
```

2. 회사 공식 채용 페이지에서 관심 직무 5~8개를 골라 `templates/career-map.csv`에 기록합니다.

```powershell
New-Item -ItemType Directory -Force notes | Out-Null
Copy-Item templates\career-map.csv notes\career-map.csv
```

공고마다 다음만 정리합니다.

- 해결하는 업무 문제
- AI·자동화 관련 핵심 업무
- 자주 등장하는 기술과 협업 역량
- 지금 가진 강점과 보완할 항목

#### 기록 주제

> 백엔드 경험에서 출발해 AI/AX에서 검증해 보고 싶은 것

산업 전망을 길게 설명하기보다 현재 경험, 부족한 부분, 이번 과정에서 확인할 가설을 중심으로 씁니다.

---

### Day 2 — 같은 과제를 두 가지 요청 방식으로 실행하기

실습 대상은 `microtasks/week01-ticket-title`입니다. 먼저 공개 테스트가 실패하는지 확인합니다.

```powershell
powershell -ExecutionPolicy Bypass -File microtasks\week01-ticket-title\test.ps1
```

#### 독립된 시작 상태 만들기

두 실행이 같은 코드에서 시작하도록 과제 폴더를 각각 복사합니다.

```powershell
New-Item -ItemType Directory -Force experiments\week01 | Out-Null
Copy-Item -Recurse microtasks\week01-ticket-title experiments\week01\run-a
Copy-Item -Recurse microtasks\week01-ticket-title experiments\week01\run-b
```

#### Run A — 짧은 요청

```powershell
Push-Location experiments\week01\run-a
Get-Content -Raw ..\..\..\.learning\week01\prompts\minimal.md |
  codex exec --json --ephemeral --sandbox workspace-write - `
  1> events.jsonl 2> stderr.log
Pop-Location
```

#### Run B — 구조화된 작업 계약

```powershell
Push-Location experiments\week01\run-b
Get-Content -Raw ..\..\..\.learning\week01\prompts\structured.md |
  codex exec --json --ephemeral --sandbox workspace-write - `
  1> events.jsonl 2> stderr.log
Pop-Location
```

각 폴더에서 테스트를 다시 실행하고 다음 값을 기록합니다.

- 첫 결과가 테스트를 통과했는지
- 요구사항 누락 수
- 추가 교정 요청 수
- 변경 파일과 코드 줄 수
- 전체 경과 시간과 사람 작업 시간
- 입력·캐시 입력·출력 토큰

`experiments/week01/prompt-comparison.md`에는 결과를 먼저 적고, 원인 해석은 그다음에 적습니다.

---

### Day 3 — `AGENTS.md`로 저장소 규칙 전달하기

루트 `AGENTS.md`에는 저장소 전체에서 반복되는 규칙을 적습니다.

```text
수정 범위와 기존 변경 보존
필수 검증 명령
비밀값과 테스트에 관한 규칙
미검증 결과 표기
완료 보고 형식
```

그다음 `microtasks/week01-ticket-title/AGENTS.md`를 만들어 이 과제에만 적용되는 지침을 추가합니다.

#### 확인 실습

1. 문서 끝의 `agents-audit.md` 프롬프트로 Codex에 현재 적용 지침을 요약하게 합니다.
2. 루트와 하위 지침이 모두 반영됐는지 직접 대조합니다.
3. 하위 지침 하나를 의도적으로 충돌시킨 뒤 어떤 지침이 적용되는지 확인합니다.
4. 충돌을 제거하고 실패 카드에 원인과 해결 과정을 남깁니다.

저장소의 장기 규칙과 한 번만 쓰는 작업 요구를 섞지 않는 것이 핵심입니다.

---

### Day 4 — Codex 실행 로그를 읽기 쉬운 데이터로 바꾸기

Day 2에서 만든 JSONL을 요약합니다.

```powershell
python runner\parse_codex_jsonl.py `
  experiments\week01\run-a\events.jsonl `
  --output experiments\week01\run-a\summary.json

python runner\parse_codex_jsonl.py `
  experiments\week01\run-b\events.jsonl `
  --output experiments\week01\run-b\summary.json
```

사람이 결과를 검토하거나 직접 고치는 시간은 별도로 측정합니다.

```powershell
python runner\human_timer.py start --run-id WEEK01-REVIEW --activity review

# 결과 검토와 기록

python runner\human_timer.py stop --run-id WEEK01-REVIEW
```

#### 오류 실험

원본 로그는 보존하고 복사본을 만듭니다.

```powershell
Copy-Item experiments\week01\run-a\events.jsonl `
  experiments\week01\run-a\events-malformed.jsonl
Add-Content experiments\week01\run-a\events-malformed.jsonl "{broken json"
```

파서가 다음을 구분하도록 보완합니다.

- JSON으로 읽을 수 없는 행
- 정상 JSON으로 기록된 Codex 실행 오류
- 완료된 실행의 토큰 사용량
- 도구 호출과 파일 변경

---

### Day 5 — 결과를 비교하고 말로 설명하기

`templates/weekly-retrospective.md`를 복사해 회고를 작성합니다.

```powershell
Copy-Item templates\weekly-retrospective.md notes\week01-retrospective.md
```

다음 질문에 자료를 보지 않고 답한 뒤, 모호한 부분만 다시 확인합니다.

```text
짧은 요청과 작업 계약은 무엇이 달랐는가?
AGENTS.md에는 어떤 내용을 넣는 편이 좋은가?
JSONL에서 파싱 오류와 실행 오류를 어떻게 구분했는가?
전체 경과 시간과 사람 작업 시간을 왜 따로 재는가?
이번 결과로 말할 수 있는 것과 아직 말하기 어려운 것은 무엇인가?
```

#### 블로그 자료

- 매일의 짧은 실험 노트 5개
- 발행 후보 1편: `같은 Codex 과제에 요청 방식만 바꿔 실행해 본 결과`
- 실패 카드 2개 이상

## 완료 기준

- [ ] 관심 공고 5~8개의 반복 역량을 분류했습니다.
- [ ] 동일한 시작 코드에서 두 요청 방식을 실행했습니다.
- [ ] 두 실행의 테스트·요구사항·시간·토큰을 비교했습니다.
- [ ] 루트와 하위 `AGENTS.md`를 직접 작성하고 적용 결과를 확인했습니다.
- [ ] JSONL 파서가 파싱 오류와 실행 오류를 구분합니다.
- [ ] 실패 카드가 2개 이상 있습니다.
- [ ] 자료 없이 핵심 질문에 답할 수 있습니다.
- [ ] 발행 가능한 글 초안 한 편이 있습니다.

---

## 실습 프롬프트

아래 프롬프트는 실행 조건을 일정하게 유지하기 위한 기준본입니다. 수정했다면 변경 이유와 결과 차이를 실험 기록에 남깁니다.

### `agents-audit.md`

````markdown
저장소를 수정하지 말고 루트와 현재 디렉터리에 적용되는 AGENTS.md 지침을 우선순위와 함께 설명하라. 서로 충돌하거나 모호한 규칙이 있으면 정확한 파일과 문구를 지적하라.
````

### `minimal.md`

````markdown
TicketTitleNormalizer를 구현하고 테스트를 통과시켜줘.
````

### `plan-only.md`

````markdown
코드를 수정하지 마라. 작업 계약과 현재 코드를 읽고 구현 계획, 수정 파일, 테스트 계획, 위험, 중단해야 하는 조건만 handoff 형식으로 작성하라.
````

### `structured.md`

````markdown
# Goal
`microtasks/week01-ticket-title`의 `TicketTitleNormalizer.normalize`를 구현한다.

# Allowed paths
- `microtasks/week01-ticket-title/src/**`
- 필요하면 같은 폴더 아래 새 테스트 추가

# Forbidden
- Public Test 수정·삭제
- 외부 라이브러리 추가
- 다른 폴더 변경

# Acceptance criteria
- null·blank는 IllegalArgumentException
- 앞뒤 공백 제거
- 연속 whitespace를 한 칸으로 변환
- 결과가 Unicode code point 기준 80자를 넘으면 앞의 80개 code point만 남김
- Java 17

# Verification
Windows: `powershell -ExecutionPolicy Bypass -File microtasks\\week01-ticket-title\\test.ps1`
Linux/WSL: `bash microtasks/week01-ticket-title/test.sh`

# Handoff
변경 파일, 실행한 테스트, 남은 위험을 보고한다.
````

---

## 다음 단계

- 진행 상태 확인: `python "C:\Users\Administrator\Desktop\ai\course.py" status "C:\Users\Administrator\Desktop\ai\ai-ax-learning-lab"`
- 실습 완료 후 참고 구현 확인: `python "C:\Users\Administrator\Desktop\ai\course.py" reference "C:\Users\Administrator\Desktop\ai\ai-ax-learning-lab"`
- 완료 기준을 통과한 뒤 다음 주차 시작: `python "C:\Users\Administrator\Desktop\ai\course.py" next "C:\Users\Administrator\Desktop\ai\ai-ax-learning-lab"`
