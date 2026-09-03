# 2주차 — 반복 작업을 Codex Skill로 만들기

> 이 README가 이번 주차의 상세 학습 본문입니다. 루트 `CURRENT_WEEK.md`는 여기로 돌아오는 짧은 대시보드입니다.

- 기본 작업 폴더: [`lab`](lab/)
- 전체 과정: [루트 README](../README.md)
- 현재 위치와 다음 명령: [CURRENT_WEEK](../CURRENT_WEEK.md)


이미 사용해 본 Skill의 구조를 살펴본 뒤, 모호한 요청을 작업 계약으로 바꾸는 `task-contract-writer`를 직접 만듭니다. 명시적으로 호출했을 때와 Codex가 필요성을 판단했을 때를 나눠 시험하고, 발동 조건을 평가 케이스로 다듬습니다.

## 학습 목표

- Skill과 일회성 프롬프트의 쓰임을 구분합니다.
- `SKILL.md`, 스크립트, 참고 자료, 템플릿의 역할을 설명합니다.
- Skill 설명이 자동 선택에 미치는 영향을 관찰합니다.
- 반복 가능한 검증은 스크립트로 옮깁니다.

## 개념 이해

### Skill이 해결하는 문제

Skill은 반복해서 사용하는 작업 절차와 필요한 자료를 하나의 패키지로 묶은 것입니다. 매번 긴 프롬프트를 다시 쓰는 대신, 특정 상황에서 불러올 수 있는 이름 있는 작업 단위를 만듭니다.

예를 들어 `task-contract-writer`는 모호한 요구를 읽고 수정 범위, 인수 조건, 검증 명령과 중단 조건이 있는 작업 계약으로 바꾸는 절차를 담을 수 있습니다.

```text
모호한 기능 요청
→ `task-contract-writer` 선택
→ 요구사항 확인 절차 수행
→ 계약 템플릿 작성
→ 검증 스크립트 실행
→ 작업 계약 또는 오류 보고
```

Skill의 가치는 설명문 자체보다 반복 가능성에 있습니다. 같은 종류의 요청에 비슷한 절차를 적용하고 빠뜨리기 쉬운 검증을 자동화할 수 있어야 합니다.

### `SKILL.md`와 부속 파일

Skill의 중심은 `SKILL.md`입니다. 여기에는 어떤 요청에 이 Skill을 쓰는지, 어떤 순서로 작업하는지, 무엇을 결과로 남기는지 적습니다. 필요에 따라 부속 파일을 나눕니다.

| 구성 요소 | 맡는 역할 |
|---|---|
| `SKILL.md` | 적용 대상, 판단 순서, 작업 절차, 완료 형식 |
| `references/` | 필요할 때 읽을 배경 지식과 규칙 |
| `scripts/` | 형식 검사처럼 결과가 일정해야 하는 작업 |
| `assets/`·`templates/` | 반복해서 만드는 산출물의 시작 형식 |
| `tests/`·`evals/` | 스크립트와 선택 조건을 검증하는 사례 |

모든 내용을 `SKILL.md` 한 파일에 넣으면 필요한 정보를 찾기 어려워집니다. 판단과 절차는 `SKILL.md`에 두고 긴 참고 자료와 결정적으로 검사할 수 있는 부분은 별도 파일로 나누는 편이 관리하기 쉽습니다.

### 명시 호출과 자동 선택

Skill은 이름을 지정해 명시적으로 호출할 수도 있고 요청 내용에 따라 Codex가 선택할 수도 있습니다.

```text
명시 호출:
$task-contract-writer를 사용해 이 요구를 작업 계약으로 정리해 주세요.

자동 선택:
이 요구를 구현자에게 넘길 수 있도록 범위와 인수 조건이 있는 문서로 정리해 주세요.
```

명시 호출은 Skill 자체가 정상 작동하는지 확인하기 좋습니다. 자동 선택 실험에서는 설명과 요청이 얼마나 잘 맞는지 봅니다. 두 방식은 확인하려는 대상이 다르므로 새 실행으로 나눠 기록합니다.

자동 선택에서 중요한 것은 `description`의 범위입니다. 범위가 너무 넓으면 관련 없는 요청에도 Skill이 선택됩니다. 반대로 너무 좁으면 필요한 요청을 놓칩니다. 긍정 사례만으로는 이 문제를 찾기 어렵습니다.

### 모델의 판단과 스크립트의 검사

모호한 요구를 어떤 항목으로 정리할지는 문맥 판단이 필요합니다. 반면 필수 섹션이 있는지, 허용 경로와 금지 경로가 겹치는지는 코드로 같은 결과를 낼 수 있습니다.

```text
모델이 맡을 부분
- 요구의 의도 파악
- 누락된 질문 찾기
- 인수 조건 초안 작성

스크립트가 맡을 부분
- 필수 섹션 존재 검사
- 경로 충돌 검사
- 검증 명령 존재 검사
- 출력 형식 검사
```

이렇게 나누면 표현이 달라져도 반드시 지켜야 할 조건은 안정적으로 확인할 수 있습니다. 스크립트의 통과가 계약 내용의 타당성까지 보장하는 것은 아니므로, 의미 검토와 형식 검사를 함께 사용합니다.

### Skill, 프롬프트, `AGENTS.md`, MCP의 관계

| 구성 요소 | 질문 |
|---|---|
| 프롬프트 | 이번에 무엇을 해 달라는가? |
| `AGENTS.md` | 이 저장소에서 계속 지켜야 할 규칙은 무엇인가? |
| Skill | 이 종류의 작업을 어떤 절차로 반복할 것인가? |
| MCP | 작업에 필요한 외부 기능과 자료를 어떤 표준 인터페이스로 제공할 것인가? |

Skill 안에서 MCP Tool을 사용하거나 저장소 규칙을 따를 수 있습니다. 서로 대체하는 개념이 아니라 적용 범위가 다른 구성 요소입니다.

### 구성 요소의 관계

```text
요청
→ Skill 설명과의 일치 여부 판단
→ `SKILL.md`의 절차
→ 필요할 때 `references/` 읽기
→ `assets/`·`templates/`로 산출물 작성
→ `scripts/`로 결정적 검증
→ 결과와 실패 사유 기록
```

### 자주 생기는 문제

- `description`이 너무 넓어 단순 요약이나 일반 질문에도 Skill이 선택됩니다.
- `description`이 구현 세부에 묶여 비슷한 작업을 놓칩니다.
- `SKILL.md`가 긴 참고 문서처럼 변해 실제 실행 절차가 묻힙니다.
- 모델이 안정적으로 검사할 수 있는 항목까지 매번 다시 판단합니다.
- 스크립트가 특정 운영체제, 경로 또는 환경변수를 암묵적으로 가정합니다.
- 긍정 사례만 평가해 오발동과 경계 사례를 발견하지 못합니다.
- 하나의 Skill이 요구 분석, 구현, 배포까지 모두 맡아 적용 범위가 불분명해집니다.
- Skill을 수정했지만 평가 케이스와 버전을 함께 남기지 않습니다.

### 학습을 마친 뒤 설명할 수 있어야 하는 것

- Skill은 긴 프롬프트나 `AGENTS.md`와 무엇이 다른가?
- `SKILL.md`, `references/`, `scripts/`, `assets/`는 어떻게 역할을 나누는가?
- 명시 호출과 자동 선택은 각각 무엇을 검증하는가?
- 긍정·부정·경계 사례를 모두 두는 이유는 무엇인가?
- 모델의 판단과 결정적 검증을 어떤 기준으로 나누는가?

## 이번 주에 완성하고 기록할 것

```text
lab/.agents/skills/task-contract-writer/
lab/evals/skill-trigger-cases.jsonl
runs/trigger-evaluation/
.local/notes/week02-skill-retrospective.md
```

선택 실습으로 `experiment-recorder`를 추가할 수 있습니다.

## 실습 순서

| 일차 | 학습 내용 | 실습 결과 |
|---:|---|---|
| 1 | 기존 Skill 분석 | 구조와 발동 조건 비교표 |
| 2 | 첫 Skill 설계 | `SKILL.md`와 출력 템플릿 |
| 3 | 결정적 검증 분리 | 계약 검사 스크립트와 테스트 |
| 4 | 명시 호출·자동 선택 | 두 호출 방식의 실행 기록 |
| 5 | 발동 평가 | 긍정·부정·경계 대표 3건, 선택 연구 시 전체 30개 |
| 6 | 실제 과제 적용과 회고 | 적용 전후 비교와 회고 요약 |

### 이번 주의 실행 지도

| Day | 먼저 읽을 파일 | IDE·Codex에서 열 폴더 | 사용할 표면 | 공개 산출물 | 개인 기록 |
|---:|---|---|---|---|---|
| 1 | 주차 `README.md`, 비교할 두 Skill의 `SKILL.md` | `lab/` | IDE 읽기 + Codex 읽기 전용 검토 | `runs/skill-audit/response.md`와 비교표 | `.local/notes/day01.md` |
| 2 | `lab/.agents/skills/task-contract-writer/SKILL.md`, 템플릿·평가 사례 | `lab/` | Codex 앱·IDE 확장·대화형 CLI | Skill 초안과 설계 근거 | `.local/notes/week02-skill-design.md` |
| 3 | validator·tests, `shared/benchmark/contracts/TASK-A.md` | `lab/` | IDE·터미널 테스트·디버거 | validator, tests, 실패 카드 | `.local/notes/day03.md` |
| 4 | 명시·자동 요청과 완성한 Skill | `lab/` | 같은 Codex 표면의 새 작업 두 개 | `runs/explicit/`, `runs/automatic/`의 요청·응답·근거 | `.local/notes/day04.md` |
| 5 | `lab/evals/skill-trigger-cases.jsonl` | `lab/` | 대표 3건 수동 후 선택적으로 Runner | `runs/trigger-evaluation/`과 혼동 행렬 | `.local/notes/day05.md` |
| 6 | Week 1 과제 또는 `TASK-A`, 모든 평가 결과 | `lab/` | Codex 직접 협업 + IDE 검증 | 적용 전후 결과와 공개 회고 | `.local/notes/week02-skill-retrospective.md` |

### 이번 주 작업 폴더

2주차 Codex 작업의 현재 작업 폴더(CWD)는 `week02-codex-skills/lab/`로 고정합니다. 저장소용 Skill은 현재 작업 폴더에서 저장소 루트까지 올라가며 만나는 `.agents/skills/`에서 발견되므로, 학습 저장소 루트에서 작업을 시작하면 이 주차의 `task-contract-writer`가 보이지 않습니다.

- Codex 앱·IDE 확장: `week02-codex-skills/lab`을 primary folder로 열고 그 프로젝트에서 새 작업을 만듭니다.
- 대화형 CLI: 학습 저장소 루트에서 아래 명령으로 시작합니다.

```text
codex -C ./week02-codex-skills/lab
```

이제부터 `.agents/`와 `evals/`는 `lab/` 기준입니다. 프롬프트·공개 실행 증거·개인 메모는 각각 `../prompts/`, `../runs/`, `../.local/notes/`, 저장소 공용 파일은 `../../shared/`로 접근합니다. 첫 요청 전에 primary folder 또는 `-C` 값은 저장소 기준 상대경로로, Codex가 실제로 읽은 Skill은 이름과 Skill 내부 상대경로로 실행 기록에 남깁니다. 사용자 홈이나 설치 캐시의 절대경로는 공개 산출물에 기록하지 않습니다.

### 먼저 살펴볼 제공 파일

이번 주에는 빈 폴더에서 Skill을 만드는 대신, 일부가 비어 있는 시작 자료를 완성합니다. AI에게 구현을 요청하기 전에 아래 파일을 직접 열어 각 파일의 책임과 시작 상태를 확인합니다.

| 경로 | 현재 상태 | 먼저 확인할 것 |
|---|---|---|
| `../../AGENTS.md` | 저장소 공통 작업 규칙이 준비됨 | Skill 지침과 함께 적용될 때 어느 범위를 맡는지 |
| `../../shared/benchmark/contracts/TASK-A.md` | 완성된 작업 계약 예시가 준비됨 | 새 Skill이 만들어야 할 결과와 실제 계약의 차이 |
| `.agents/skills/task-contract-writer/SKILL.md` | 발동 조건과 절차가 `TODO` | 어떤 요청에서 발동하고 어떤 요청에서는 발동하면 안 되는가 |
| `.agents/skills/task-contract-writer/assets/task-contract-template.md` | 출력 heading이 준비됨 | validator가 찾는 정확한 heading과 비어 있는 본문 |
| `.agents/skills/task-contract-writer/scripts/validate_contract.py` | 의도적으로 미구현 | 모델 판단 없이 코드로 검사할 수 있는 항목 |
| `.agents/skills/task-contract-writer/tests/` | 시작용 테스트가 준비됨 | 처음 실패할 테스트와 아직 빠진 오류 사례 |
| `evals/skill-trigger-cases.jsonl` | positive·negative·boundary 30개가 준비됨 | 핵심 대표 3건의 기대값이 타당한지, 선택 연구 시 나머지 사례와 별도 holdout이 필요한지 |
| `.agents/skills/experiment-recorder/` | 선택 실습용 시작 자료 | 이번 주 필수 범위와 섞지 않아야 할 이유 |

파일을 읽은 뒤 `../.local/notes/week02-skill-design.md`에 다음을 먼저 적습니다.

```text
이 Skill이 해결할 반복 작업
발동해야 하는 요청과 발동하면 안 되는 요청
모델이 판단할 부분과 스크립트가 검사할 부분
validator에서 처음 실패할 테스트
AI에게 도움을 요청할 부분과 내가 직접 결정할 부분
```

이 메모가 이번 주의 기준입니다. AI가 더 넓은 범위나 다른 발동 조건을 제안하더라도 바꿀지는 학습자가 판단합니다.

---

### Day 1 — 기존 Skill 두 개 분석하기

현재 설치된 Skill 또는 공개된 Skill 중 성격이 다른 두 개를 고릅니다. 각 Skill을 다음 표로 정리합니다.

```text
이름과 해결하는 작업
설명의 범위
실행 절차
참고 문서·스크립트·템플릿
모델이 판단하는 부분
코드가 결정적으로 검사하는 부분
필요한 입력과 남기는 결과
발동하면 안 되는 요청
```

먼저 두 Skill을 직접 읽고 위 표를 채운 뒤, 문서 끝의 `skill-audit.md`를 엽니다. 이 파일은 **[검토 요청]**입니다. 검토 목적과 출력 구조를 이해하고 Skill 이름 또는 저장소 상대경로를 채운 다음, `전송할 본문`을 Codex 앱의 새 작업이나 대화형 CLI 대화창에 직접 붙여넣어 보냅니다. 제공 문구를 써도 되지만 AI가 만든 표는 원문과 다시 대조하고, 발동 범위와 책임 분리는 학습자가 최종 판정합니다.

#### 기록 주제

> Skill을 써 본 경험과 구조를 뜯어본 경험은 무엇이 달랐나

---

### Day 2 — `task-contract-writer` 설계하기

실습 폴더:

```text
.agents/skills/task-contract-writer/
```

`SKILL.md`에는 다음을 담습니다.

- 이 Skill이 처리하는 요청
- 자동 선택에 필요한 구체적인 설명
- 입력을 확인하는 순서
- 작업 계약을 작성하는 절차
- 스크립트를 실행할 시점
- 완료 결과와 실패 보고 형식

먼저 `../.local/notes/week02-skill-design.md`의 기준으로 `description`과 절차를 직접 초안으로 씁니다. AI에는 “무엇을 발동 조건으로 정할지”를 대신 결정하게 하지 않고, 초안에서 넓거나 모호한 부분과 놓친 반례를 질문합니다. 제안을 반영할지는 positive·negative 예시를 대조한 뒤 학습자가 결정합니다.

출력 계약은 아래 항목을 사용합니다.

```text
Goal                  목표
Context               배경
Allowed paths         수정 허용 경로
Forbidden changes     금지된 변경
Acceptance criteria   인수 조건
Required verification 필수 검증
Stop conditions       중단 조건
Handoff               인계 내용
```

왼쪽 영문은 템플릿과 validator가 정확히 찾는 `##` heading입니다. 한국어는 의미를 이해하기 위한 설명이며 heading을 번역해 바꾸지 않습니다. `assets/task-contract-template.md`는 반복 출력 형식을, `SKILL.md`는 판단과 절차를 맡습니다.

---

### Day 3 — 계약 검증을 스크립트로 옮기기

`scripts/validate_contract.py`와 테스트를 완성합니다. 제공된 validator는 아직 `TODO` 상태입니다. 구현 전 첫 명령은 `valid: false`와 종료 코드 `1`, 단위 테스트는 실패로 끝나는 것이 정상입니다. 먼저 이 기준선을 확인한 뒤 코드를 작성합니다.

```text
python .agents/skills/task-contract-writer/scripts/validate_contract.py ../../shared/benchmark/contracts/TASK-A.md
python -m unittest discover -s .agents/skills/task-contract-writer/tests -v
```

IDE의 테스트 실행·디버그 기능을 써도 같은 CWD와 인수를 유지합니다.

자동 검사로 옮길 항목:

- 필수 섹션 존재
- 섹션 중복과 빈 본문
- 허용 경로와 금지 경로의 충돌
- 검증 명령 존재

다음 항목은 문장의 의미를 판단해야 하므로 별도의 사람 검토 rubric으로 남깁니다.

- 인수 조건이 실제로 관찰 가능한가
- 구현 방법을 불필요하게 고정하지 않았는가
- 중단 조건과 검증이 작업 위험에 맞는가

#### 오류 실험

- 인수 조건이 없는 계약
- 허용 경로와 금지 경로가 겹치는 계약
- “잘 동작해야 한다”처럼 판정할 수 없는 조건
- 존재하지 않는 검증 명령

검사 결과는 성공 여부와 오류 목록을 기계가 읽을 수 있는 형식으로 반환합니다.

---

### Day 4 — 명시 호출과 자동 선택 비교하기

첫 비교는 자동 실행이 아니라 평소 Skill을 쓰는 방식으로 진행합니다. Codex 앱에서 `lab/` 프로젝트의 새 작업을 열거나 위의 `codex -C ./week02-codex-skills/lab`로 대화형 세션을 시작한 뒤, 본인이 실제로 넘기고 싶은 모호한 개발 요청 하나를 고릅니다.

아래 두 예시는 이번 실험에서 사용할 입력의 골격입니다. `...` 부분을 실제 과제로 바꾼 뒤 정확한 원문을 `../runs/explicit/request.md`와 `../runs/automatic/request.md`에 저장하고, Codex 앱이나 대화형 CLI에 직접 붙여넣어 보냅니다. Skill 이름 유무를 제외한 목표와 조건은 최대한 같게 유지하고, 발동 판정 기준도 실행 전에 정합니다.

먼저 Skill 이름을 넣은 요청으로 정상 동작을 확인합니다.

```text
$task-contract-writer를 사용해 이 요구사항을 작업 계약으로 정리해 주세요: ...
```

그다음 새 앱 작업이나 새 대화형 세션에서 Skill 이름을 쓰지 않고 같은 목적을 요청해 자동 선택을 관찰합니다.

```text
이 모호한 요구를 구현 세션에 넘길 수 있도록 범위, 인수 조건,
검증 명령과 중단 조건이 있는 문서로 정리해 주세요: ...
```

두 실행이 끝나면 실제로 보낸 요청, 결과, Skill을 읽었다는 근거와 필수 heading 통과 여부를 직접 기록합니다. AI가 “Skill을 사용했다”고 말한 자기 보고만으로 발동했다고 판정하지 않습니다.

앱·대화형 실행으로 차이를 이해한 뒤 같은 요청을 반복 측정하는 것은 심화입니다. 먼저 `shared/tools/runner/run_codex_exec.py`를 `--working-directory week02-codex-skills/lab --output-directory week02-codex-skills/.local/raw/<run-id> --dry-run`으로 점검하고, 동결한 요청과 동일 설정으로 실행합니다. 파일 생성을 평가하는 사례만 허용 경로를 확인한 뒤 `--sandbox workspace-write`를 추가하고, 발동 여부만 보는 사례는 기본 `read-only`를 유지합니다. 공개 가능한 요청·응답·`run.json`·정제 로그는 검토 후 `runs/explicit/`와 `runs/automatic/`에 승격하고 비정제 원본은 `.local/raw/`에 둡니다. 학습자가 수동 pilot을 읽고 확정하기 전에 Runner를 돌리지 않습니다.

기록할 내용:

- Skill이 실제로 읽혔다는 로그 또는 결과 증거
- 출력 계약의 필수 항목 통과 여부
- 추가 교정 요청 수
- 전체 시간과 토큰
- Skill 없이 같은 요청을 처리했을 때의 차이

마지막 항목은 같은 저장소에서 Skill 이름만 빼는 것으로 성립하지 않습니다. 자동 선택 가능한 Skill이 그대로 남아 있기 때문입니다. Skill이 없는 별도 Worktree나 고정된 이전 commit에서 실행하고, 활성 Skill 목록을 함께 기록합니다.

---

### Day 5 — 발동 조건을 평가 사례로 다듬기

`evals/skill-trigger-cases.jsonl`에는 다음 세 범주의 사례가 10개씩 이미 들어 있습니다. 먼저 내용을 검토하고 기대값이 애매한 행만 근거를 남겨 고칩니다.

```text
발동해야 하는 요청 10개
발동하면 안 되는 요청 10개
판단이 어려운 경계 요청 10개
```

새 사례를 추가할 때도 같은 구조를 사용합니다.

```json
{
  "id": "trigger-001",
  "text": "모호한 기능 요청을 구현 가능한 작업 계약으로 정리해 줘",
  "expected_trigger": true,
  "category": "positive"
}
```

핵심 평가로 positive·negative·boundary에서 한 사례씩 골라 앱이나 대화형 CLI에서 직접 요청합니다. 학습자가 예상한 발동 여부와 실제 근거가 어떻게 다른지 이 대표 3건에서 확인합니다. 발동 성능을 정량적으로 비교하려는 경우에만 선택 연구로 30개 전체를 동결하고 실행합니다.

자동 선택을 시험할 때는 프롬프트에 Skill 이름이나 `$task-contract-writer`를 넣지 않습니다. `expected_trigger`와 그 근거는 실행 전에 학습자가 확정합니다. 핵심 대표 3건은 `lab/`을 작업 위치로 둔 서로 격리된 새 실행에서 평가하고 `../runs/trigger-evaluation/observations.jsonl`에 실제 발동 여부와 근거를 남깁니다. 대표 사례를 수동으로 확인한 뒤 `../prompts/skill-trigger-evaluation.md`를 엽니다. 이 파일은 **[자동 측정용]**입니다. 입력·출력과 판정 규칙을 이해하고 동결한 다음 대화창에 직접 보내거나, 수동 pilot 뒤에만 Runner로 집계할 수 있습니다. 선택 연구에서 전체 30개를 실행할 때도 이 요청은 학습자의 기대값 판정과 대표 사례 수동 확인을 대신하지 않습니다.
전체 30개 선택 연구를 수행했을 때에만 다음 정량 지표를 집계합니다.

- 전체 precision과 recall
- positive의 TPR·FNR
- negative의 TNR·FPR
- boundary의 accuracy
- 경계 사례에서 틀린 문장 유형

설명을 수정하기 전 결과와 수정한 뒤 결과를 서로 다른 커밋으로 보존합니다. 선택 연구에서 같은 30개를 보고 description을 고친 뒤 다시 측정한 결과는 독립 평가가 아니라 회귀 확인입니다. 일반화 성능을 주장하려면 수정할 때 보지 않은 별도 사례를 마지막에 추가합니다.

AI는 오분류 유형과 description 수정 후보를 제안할 수 있습니다. 경계 사례의 기대값을 바꾸거나 수정안을 채택하고 최종 평가표를 승인하는 일은 학습자가 맡습니다.

---

### Day 6 — 실제 과제에 적용하고 정리하기

Week 1의 Microtask 또는 `TASK-A.md` 하나를 정해 두 방식으로 실행합니다.

```text
A: 작업 계약을 사람이 직접 작성
B: task-contract-writer로 초안을 만들고 사람이 검토
```

B의 결과는 Skill이 만들었다는 이유만으로 승인하지 않습니다. 학습자가 두 계약의 누락, 검증 명령과 중단 조건을 같은 기준으로 검토한 뒤 구현 세션에 넘길 계약을 선택합니다.

비교 항목:

- 계약 작성에 든 사람 작업 시간
- 누락된 인수 조건
- 검증 명령의 정확성
- 구현 과정의 추가 질문 수
- 첫 결과 테스트 통과율
- 전체 토큰

## 완료 기준

- [ ] 기존 Skill 두 개의 구조와 발동 조건을 비교했습니다.
- [ ] `task-contract-writer`를 직접 만들었습니다.
- [ ] 계약 검증 스크립트와 테스트가 통과합니다.
- [ ] 명시 호출과 자동 선택을 따로 시험했습니다.
- [ ] 긍정·부정·경계 대표 사례를 한 건씩, 총 3건 평가했습니다.
- [ ] 선택 정량 연구를 수행했다면 동결한 30개 전체와 별도 holdout을 다른 목적으로 구분해 기록했습니다.
- [ ] Skill 설명 수정 전후의 결과를 보존했습니다.
- [ ] 적용 전후의 누락·테스트 결과를 비교했고, 효율 비교를 선택했다면 같은 기준의 시간 원시값도 별도로 기록했습니다.
- [ ] 각 Day의 학습 결과와 마지막 검증 시점을 커밋으로 남겼습니다.

---

## 이 폴더의 자료

- `lab/`: 시작 코드, 설정, 평가 자료와 이번 주 구현
- `prompts/`: 과정이 제공하고 `sync`가 갱신할 수 있는 요청 자료
- `runs/`: 실제 요청·응답, 실패 카드, 검증 결과와 정제된 로그처럼 공개할 재현 증거
- `references/`: 실습 뒤 `reference` 명령으로 공개하는 비교용 참고 구현
- `.local/notes/`: 개인 메모, `.local/raw/`: 정제 전 로그, `.local/scratch/`: 임시 작업. `.local/` 전체는 Git에서 제외

`runs/`와 `lab/`은 학습자가 관리합니다. `sync`는 이 두 폴더를 덮어쓰지 않습니다.
