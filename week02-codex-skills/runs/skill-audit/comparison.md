# imagegen과 documents Skill 비교

> Day 1의 Skill 구조 분석, 학습자 판단, 원문 대조 코멘트를 함께 기록한 문서다.

## 근거 표기를 읽는 방법

표의 `원문에서 볼 곳`은 `파일 → 섹션 제목 또는 검색어` 순서로 적었다. 예를 들어 `imagegen 원문 → When not to use`는 imagegen의 `SKILL.md`를 열고 `When not to use`를 검색하라는 뜻이다. 영어 섹션 제목은 원문에서 `Ctrl+F`로 찾을 수 있도록 그대로 남겼다.

- imagegen: 원문 식별자 `imagegen / SKILL.md` · 로컬 번역본 `imagegen.SKILL.ko.md`
- documents: 원문 식별자 `documents / SKILL.md` · 로컬 번역본 `documents.SKILL.ko.md`

> **표기 규칙:** 외부 설치 Skill은 `Skill 이름 / Skill 내부 상대경로`로 적고 로컬 설치 경로는 기록하지 않는다. Git에서 제외되는 번역본은 파일명만 기록한다.

한국어 번역본으로 내용을 먼저 파악한 뒤 판단에 필요한 규칙이나 표현만 원문에서 다시 확인하면 된다.

## 비교표

| 비교 기준 | imagegen | documents |
|---|---|---|
| 이름과 해결하는 작업 | 이름은 `imagegen`이다. 새 비트맵 이미지를 만들거나 기존 이미지를 편집하고 참고 이미지에서 변형본을 만드는 작업을 다룬다. 사진, 일러스트, 텍스처, 스프라이트, 목업, 투명 배경 이미지처럼 결과물이 래스터 이미지일 때 사용한다. **원문에서 볼 곳:** imagegen 원문 → frontmatter `name`, `description`; `# Image Generation Skill` | 이름은 `documents`다. 컨테이너 안에서 `.docx`, Word, Google Docs용 문서를 만들고 편집하며 변경 내용 표시와 댓글 작업까지 처리한다. 본문에는 문서 읽기와 검토 경로도 포함되어 있다. **원문에서 볼 곳:** documents 원문 → frontmatter `name`, `description`; `# Documents Skill (Read • Create • Edit • Redline • Comment)`; `## Where to go next` |
| `description`의 범위 | **넓은 부분:** 주제와 업종을 가리지 않고 여러 종류의 래스터 이미지를 생성·편집한다. **좁은 부분:** 결과물이 비트맵이어야 하며 저장소의 SVG·벡터·코드 기반 자산을 고치거나 HTML/CSS/canvas로 직접 만드는 편이 나은 작업은 제외한다. **원문에서 볼 곳:** imagegen 원문 → frontmatter `description`; `## When to use`; `## When not to use` | **넓은 부분:** 생성, 편집, 변경 내용 표시, 댓글까지 문서 작업의 여러 단계를 포괄한다. **좁은 부분:** `.docx`, Word, Google Docs용 문서와 컨테이너 작업 흐름에 초점이 맞춰져 있으며 최종 전달 전에 렌더링과 시각 검토를 요구한다. 순수 읽기·검토는 본문에는 있지만 `description`에는 직접 드러나지 않는다. **원문에서 볼 곳:** documents 원문 → frontmatter `description`; 본문 8–10행; `## Non-negotiable: render → inspect PNGs → iterate`; `## Where to go next` |
| 발동해야 하는 요청 | 새 이미지 생성, 참고 이미지의 분위기·구도·스타일을 활용한 생성, 기존 이미지의 일부 편집, 한 작업에서 여러 이미지나 변형본을 만드는 요청이다. 예: 제품 사진 생성, 사진의 배경 제거, 기존 이미지의 조명만 변경. **원문에서 볼 곳:** imagegen 원문 → `## When to use`; `## Decision tree` | `.docx` 또는 Word 문서를 새로 만들거나 고치는 요청, 변경 내용 표시나 Word 댓글을 넣는 요청, Google Docs로 가져갈 문서를 만드는 요청에서 발동한다. 본문의 작업 라우팅에 따르면 기존 문서 읽기·검토도 대상이다. **원문에서 볼 곳:** documents 원문 → frontmatter `description`; `## Google Docs-targeted output`; `## Where to go next` |
| 발동하면 안 되는 요청 | 저장소에 있는 SVG·벡터 아이콘 체계를 확장하는 작업, 간단한 도형·도식·와이어프레임을 SVG나 HTML/CSS/canvas로 직접 만드는 편이 적합한 작업, 편집 가능한 원본 형식이 이미 있는 작은 수정, 사용자가 결정론적인 코드 기반 결과를 요구한 작업에는 쓰지 않는다. **원문에서 볼 곳:** imagegen 원문 → `## When not to use` | 별도의 `When not to use` 절이 없다. `description`만 보면 독립적인 PDF, PPTX, XLSX 작업이나 일반 텍스트 윤문까지 이 Skill의 범위라고 볼 근거는 없다. 다만 명시적인 비발동 목록이 없으므로 세부 경계는 **NOT_VERIFIED**다. **원문에서 볼 곳:** documents 원문 → frontmatter `description`; 명시적 비발동 절 없음 |
| 실행 절차 | 기본 경로는 내장 `image_gen` 도구다. 먼저 생성인지 편집인지, 한 장인지 여러 장인지, 미리보기인지 프로젝트용 자산인지 판단한다. 프롬프트와 정확한 문구, 제약, 입력 이미지를 모으고 각 이미지의 역할을 표시한다. 프롬프트를 정리해 생성한 뒤 주제·스타일·구도·문자·보존 조건을 확인하고 한 번에 한 가지씩 수정하며 반복한다. 프로젝트 자산은 작업 공간에 저장하고 최종 경로·프롬프트·사용 모드를 알린다. CLI/API 경로는 사용자가 명시적으로 요청하거나 동의했을 때만 쓴다. **원문에서 볼 곳:** imagegen 원문 → `## Top-level modes and rules`; `## Decision tree`; `## Workflow` | 새 문서나 큰 개편에서는 필요한 질문으로 주제·독자·목적을 확인한다. `python-docx`로 작성·편집하고 DOCX를 PNG로 렌더링한 뒤 모든 페이지를 100% 크기로 살핀다. 문제가 있으면 수정하고 다시 렌더링한다. 댓글·변경 내용·필드처럼 필요할 때만 OOXML 패치를 쓰며 패치 뒤에도 재렌더링한다. 최신 PNG 검토가 통과한 뒤 최종 결과만 전달한다. **원문에서 볼 곳:** documents 원문 → `## Default workflow (80/20)`; `### Documents clarification questions`; `## Visual review (recommended)` |
| 참고 자료·스크립트·템플릿의 역할 | `references/prompting.md`는 프롬프트의 구조, 구체성, 보존 조건, 반복 수정 원칙을 설명한다. `references/sample-prompts.md`는 재사용할 수 있는 예시를 제공한다. `references/cli.md`, `references/image-api.md`, `references/codex-network.md`와 `scripts/image_gen.py`는 사용자가 CLI/API 경로를 선택했을 때만 사용한다. **원문에서 볼 곳:** imagegen 원문 → `## Top-level modes and rules`; `## Reference map`; imagegen 프롬프트 지침 → `## Structure`, `## Constraints and invariants`, `## Fallback-only execution controls` | 루트 `SKILL.md`는 작업 경로를 안내하고 `tasks/`는 작업별 절차, `references/`는 디자인 프리셋과 헤더 패턴, `ooxml/`은 고급 패치 방법을 제공한다. `render_docx.py`는 DOCX를 페이지 PNG로 바꾸는 표준 렌더러다. 여러 `scripts/`는 댓글, 변경 내용, 접근성, 개인정보, 표 구조 등을 처리하거나 검사한다. `template-distill.md`와 `template-create.md`는 기존 DOCX를 디자인 기준으로 삼을 때 사용한다. **원문에서 볼 곳:** documents 원문 → `## Template Following`; `## Package layout`; `## Coverage map (scripts ↔ task guides)`; `## Skill folder contents` |
| 모델이 판단하는 부분 | 요청이 생성인지 편집인지, 입력 이미지가 편집 대상인지 참고 자료인지, 프롬프트에 어느 정도 세부 사항을 보태야 하는지 판단한다. 생성 결과의 주제, 스타일, 구도, 문자 정확성, 보존 조건도 시각적으로 평가한다. 픽셀 생성 자체도 생성 모델의 작업이므로 같은 입력에서 항상 같은 결과가 나온다고 볼 수 없다. **원문에서 볼 곳:** imagegen 원문 → `## Decision tree`; `## Workflow`; `### Specificity policy`; imagegen 프롬프트 지침 → `## Input images and references`; `## Iterate deliberately` | 문서의 목적과 독자에 맞는 구조·디자인·정보 표현 방식을 고르고 필요한 수정 범위를 판단한다. 렌더링된 모든 페이지를 보고 잘림, 겹침, 글꼴 문제, 표 손상, 여백과 시각적 완성도를 판정한다. 렌더링은 검토용 이미지를 만들 뿐, 문서가 “완벽하다”는 판단까지 자동으로 내려주지는 않는다. **원문에서 볼 곳:** documents 원문 → `## Form factor selection`; `## Design standards for document generation`; `## Visual review (recommended)`; `### What rendering does and doesn’t validate` |
| 코드가 결정적으로 처리·검사하는 부분 | CLI 경로의 `scripts/image_gen.py`는 출력 형식, 투명 배경과 형식의 호환성, 압축값 범위, 출력 확장자, 배치 출력 폴더 같은 기계적 조건을 검사하고 결과 파일을 기록한다. 이 검사는 파일·인수의 유효성 검사이지, 생성 이미지의 미적·의미적 품질을 결정적으로 검증하는 절차는 아니다. 기본 내장 도구 경로에서는 이 CLI 스크립트를 사용하지 않는다. **원문에서 볼 곳:** imagegen CLI 스크립트 → 105–110행, 179–181행, 216–243행, 312행 이후, 972–975행; imagegen 원문 → `## Top-level modes and rules` | `python-docx`와 OOXML 도구는 문서 구조를 작성·패치하며 제목 sanitizer 같은 스크립트는 특정 OOXML 흔적을 규칙에 따라 제거·검사한다. `render_docx.py`는 변환 결과의 존재 여부를 확인하고 페이지별 PNG를 만든다. 다만 페이지가 보기 좋은지는 별도 시각 판단이 필요하며 댓글은 렌더링만으로 확인하기 어려워 XML 앵커·관계·콘텐츠 타입 검사도 필요하다. **원문에서 볼 곳:** documents 원문 → `## Tools + Contract Requirements`; `## Google Docs-targeted output`; `### What rendering does and doesn’t validate`; documents 렌더링 스크립트 → 196–200행, 314–369행 |
| 필요한 입력 | 기본 입력은 프롬프트, 이미지에 들어갈 정확한 문구, 반드시 지킬 조건과 피할 요소, 참고 이미지 또는 편집 대상 이미지다. 프로젝트용이면 저장 위치도 필요하다. CLI 경로에서는 모델·품질·출력 형식 같은 인수와 실제 호출을 위한 `OPENAI_API_KEY` 및 네트워크가 추가로 필요하다. **원문에서 볼 곳:** imagegen 원문 → `## Workflow` 5–7단계; `## Fallback CLI mode only`; `### Environment`; imagegen CLI 지침 → `## What this CLI does`; `## Output handling` | 새 문서나 큰 개편에는 주제, 독자, 목적이 필요하다. 편집 작업에는 기존 DOCX와 원하는 변경 내용이 필요하며 템플릿을 따를 때는 기준 DOCX가 필요하다. Google Docs용이면 목적지도 구분한다. 빠진 사실은 임의로 만들지 않고 자리표시자를 쓰며 해결되지 않은 참조는 사용자에게 묻는다. **원문에서 볼 곳:** documents 원문 → `## Template Following`; `### Editing tasks (DOCX edits)`; `### Documents clarification questions` |
| 남기는 결과 | 미리보기 작업은 대화 안에서 이미지를 보여줄 수 있다. 프로젝트용 작업은 최종 비트맵 파일을 작업 공간에 두고 저장 경로와 최종 프롬프트, 내장 도구 또는 CLI 중 어느 경로를 썼는지 보고한다. 여러 자산을 요청받았다면 선택된 최종 결과를 각각 남긴다. **원문에서 볼 곳:** imagegen 원문 → `Built-in save-path policy`; `## Workflow` 14–18단계 | 원칙적으로 사용자가 요청한 최종 문서만 전달한다. PNG와 선택적 PDF는 내부 QA 산출물이므로 요청받지 않았다면 전달하지 않는다. Google Docs 요청은 로컬 DOCX를 검증한 뒤 Google Drive 플러그인으로 네이티브 문서로 가져간다. **원문에서 볼 곳:** documents 원문 → `## Google Docs-targeted output`; `## Non-negotiable: render → inspect PNGs → iterate`; `## Final response citations` |
| 실패하거나 중단하는 조건 | 성공에 꼭 필요한 정보가 없으면 질문한다. 내장 도구가 실패하거나 없을 때는 `OPENAI_API_KEY`가 필요한 CLI 대안을 알리고 사용자가 명시적으로 선택하기 전에는 전환하지 않는다. CLI에서 키·네트워크·의존성이 없으면 실제 생성을 진행할 수 없다. 기존 파일은 명시적인 교체 요청 없이 덮어쓰지 않는다. **원문에서 볼 곳:** imagegen 원문 → `## Top-level modes and rules`; `## Shared prompt schema`의 augmentation rules; `### Environment` | 시각 문제가 남아 있으면 수정·재렌더링해야 하며 전달 단계로 넘어가면 안 된다. LibreOffice/`soffice`가 없어서 렌더링할 수 없는 경우에는 구조 검사를 거쳐 DOCX를 전달할 수 있지만 시각 QA를 하지 못했다고 밝히고 렌더 검사를 통과했다고 말해서는 안 된다. 그 밖의 렌더링 오류는 먼저 고쳐야 한다. 해결되지 않은 참조나 질문은 추측하지 않고 사용자에게 확인한다. **원문에서 볼 곳:** documents 원문 → `## Non-negotiable: render → inspect PNGs → iterate`; `### Documents clarification questions` |

## 두 `description`에서 눈에 띄는 점

### imagegen

- 결과 형식을 “래스터 이미지”로 좁히면서 SVG, 벡터, 코드 기반 자산을 명시적으로 제외한다.
- 이미지의 주제 범위는 넓지만 산출물 형식과 구현 방식의 경계는 비교적 선명하다.
- 새 로고를 이미지로 탐색하는 작업은 범위에 들어갈 수 있지만 저장소의 기존 로고·아이콘 체계를 이어서 고치는 작업은 제외한다. 요청에 `logo`나 `wireframe`이라는 말만 있다고 바로 발동할 수 없는 이유다.
- 내장 도구와 CLI의 선택 조건까지 `SKILL.md`에서 분명히 구분한다.

### documents

- 문서 생성부터 편집, 변경 내용 표시, 댓글까지 한 Skill에 묶여 있어 작업 단계의 범위가 넓다.
- 파일 생태계는 `.docx`, Word, Google Docs용 결과로 좁혀져 있다.
- 본문과 제목에는 읽기·검토가 포함되지만 frontmatter `description`에는 이를 직접 쓰지 않았다. 순수 문서 검토 요청에서 발동 정보가 충분한지는 다시 살펴볼 만하다.
- `When not to use` 절이 없어 다른 artifact Skill과의 경계는 `imagegen`보다 덜 명시적이다.

## 공통점

- 둘 다 특정 산출물을 만드는 artifact Skill이다.
- 작업 종류를 먼저 분류한 뒤 필요한 참고 문서나 스크립트만 추가로 읽는 점진적 구조를 쓴다.
- 결과를 한 번 만든 뒤 검사하고 고치는 반복 절차가 있다.
- 파일 생성이나 변환 같은 기계적 처리와, 의미·미감·사용자 의도를 보는 판단을 구분한다.
- 중간 결과보다 사용자가 요청한 최종 산출물을 중심으로 전달한다.

## 차이점

- `imagegen`은 생성 모델이 픽셀 결과를 만드는 과정이 중심이다. 스크립트 검사는 인수와 파일 처리에는 도움이 되지만 이미지의 의미나 미감을 확정하지 못한다.
- `documents`는 결정론적인 OOXML 조작과 렌더링을 적극 활용한다. 그래도 렌더된 페이지가 읽기 좋고 완성되었는지는 사람이 보듯 판단한다.
- `imagegen`은 명시적인 비발동 조건과 내장 도구·CLI 전환 조건을 둔다. `documents`는 작업별 세부 지침과 구조 검사 도구를 더 촘촘하게 제공한다.
- `imagegen`의 대표 반복은 “생성 또는 편집 → 시각 검토 → 한 가지씩 수정”이다. `documents`의 대표 반복은 “작성 또는 편집 → PNG 렌더링 → 전 페이지 검토 → 재작성”이다.

## 원문을 다시 보며 내 판단 적기

질문마다 모든 파일을 처음부터 읽을 필요는 없다. `볼 부분`만 찾아 사실을 확인한 뒤, `판단 기준`에 맞춰 내 결론을 한두 문장으로 적는다.

### 1. `documents`의 `description`에 읽기·검토가 빠진 영향

**질문:** 순수한 문서 읽기·검토 요청을 놓칠 위험이 있을까?

- **볼 부분:** documents 원문 맨 위의 `description:`, 제목 `# Documents Skill (Read • Create • Edit • Redline • Comment)`, `## Where to go next`의 `reading/reviewing` 항목
- **비교할 내용:** `description`에 적힌 작업과 본문에 적힌 작업이 같은지 확인한다. 특히 `Read` 또는 `reading/reviewing`이 어느 쪽에만 있는지 본다.
- **판단 기준:** 본문을 아직 읽지 않은 상태에서 “이 DOCX를 읽고 검토해 줘”라는 요청이 `description`만으로 이 Skill과 연결될 수 있을지 생각한다. 원문만으로 실제 발동률을 확정하지 않는다. 여기서는 놓칠 가능성이 있는지만 판단한다.

> **내 판단:** documents 스킬은 문서를 읽고 검증하는 용도보다는 문서 결과물을 만들고 이 파일이 잘 생성되었는지 품질을 관리하는 용도로 읽기 관련 지침을 넣은 것으로 보인다. 따라서 순수 문서 읽기/검토에서는 발동되지 않을 가능성이 커 보인다.

> **검토 코멘트 — 일부 수정 필요:** `documents` 본문에는 생성 결과의 QA뿐 아니라 기존 DOCX를 읽고 검토하는 독립적인 작업 경로가 있으며, 읽기 전용 작업과 생성·편집 작업도 구분한다. 따라서 읽기 지침이 생성물의 품질 관리만을 위한 것이라는 해석은 범위가 너무 좁다. 다만 `description`에 읽기·검토가 직접 드러나지 않으므로 자동 선택에서 놓칠 가능성이 있다는 판단은 타당하다. 실제 발동 여부는 원문만으로 확정할 수 없다.
>
> **수정안:** `documents` 본문에는 생성물의 QA와 별개로 기존 DOCX를 읽고 검토하는 작업 경로가 있다. 다만 `description`에는 읽기·검토가 직접 드러나지 않아 순수한 문서 읽기·검토 요청을 자동 선택에서 놓칠 가능성이 있어 보인다. 실제 발동 여부는 평가를 통해 확인해야 한다.
>
> **근거:** documents 원문 → 제목, `## Tools + Contract Requirements`, `## Where to go next`; documents 읽기·검토 지침 → `tasks/read_review.md`

### 2. `wireframe`이라는 단어만으로 `imagegen`을 발동할 수 없는 이유

**질문:** 같은 와이어프레임 요청이라도 언제 imagegen을 써야 할까? 언제 쓰지 않아야 할까?

- **볼 부분:** imagegen 원문의 첫 설명 문단에서 `wireframes` 검색, `## When not to use`, imagegen 프롬프트 지침의 `ui-mockup` 항목
- **비교할 내용:** 비트맵 시안이나 분위기 탐색을 만드는 경우와 SVG·HTML/CSS·canvas처럼 편집 가능한 코드 결과를 만드는 경우를 나눈다.
- **판단 기준:** 요청에 쓰인 단어보다 최종 산출물 형식을 본다. 탐색용 비트맵 시안이면 imagegen 후보다. 결정론적이고 편집 가능한 코드 결과가 필요하면 imagegen의 비발동 조건에 가깝다.

> **내 판단:** imagegen의 when not to use 부분을 보면, SVG, HTML/CSS, canvas로 직접 만드는 편이 더 적절하거나, 편집 가능한 원본 형식의 소스 파일이 이미 있는 내부 자산을 조금만 수정하거나, 코드 기반 결과물을 분명히 원하는 작업에서는 사용하지 말아야 할 것을 정의하고 있다. 따라서 이미지 파일 자체를 원하는 경우에는 사용하고, 산출물로 코드 기반을 원할 경우에는 사용하지 않는다.

> **검토 코멘트 — 일부 수정 필요:** 비발동 조건을 읽은 내용은 맞다. 다만 “이미지 파일 자체”는 범위가 너무 넓다. SVG도 이미지 파일이고, 편집 가능한 원본이 있는 작은 수정 역시 이미지 결과를 원하더라도 비발동 조건에 해당한다. 같은 와이어프레임도 생성형 비트맵 시안이 목적이면 사용할 수 있고, 편집 가능한 코드 결과가 목적이면 사용하지 않는다.
>
> **수정안:** `wireframe`이라는 단어 자체가 발동 여부를 정하지는 않는다. 생성형 비트맵 시안이나 저충실도 UI 목업이 목적이면 `imagegen`을 사용할 수 있다. 반면 SVG·HTML/CSS·canvas처럼 편집 가능하고 결정론적인 결과가 더 적합하거나, 기존 편집 가능한 원본을 조금만 수정하는 작업이라면 사용하지 않는다.
>
> **근거:** imagegen 원문 → frontmatter `description`, `## When not to use`; imagegen 프롬프트 지침 → `ui-mockup`

### 3. 출력 형식 검사와 이미지 품질 판단의 차이

**질문:** `imagegen`의 스크립트가 검사할 수 있는 것과 이미지 자체를 보고 판단해야 하는 것은 무엇일까?

- **볼 부분:** imagegen 원문의 `## Workflow`에서 `Inspect outputs and validate`, imagegen CLI 스크립트에서 `_normalize_output_format`, `_validate_transparency`, `_validate_generate_payload`, `_build_output_paths` 검색
- **비교할 내용:** 확장자·압축값 범위·투명 배경 호환성·출력 경로와 주제·스타일·구도·문자 정확성·보존 조건을 나눠 본다.
- **판단 기준:** 조건을 명확한 참·거짓 규칙으로 쓸 수 있으면 코드 검사에 가깝다. 사용자 의도, 의미, 미감이나 자연스러움을 해석해야 하면 모델의 시각 판단에 가깝다.

> **내 판단:** imagegen의 스크립트는 확장자, 투명 배경 호환성, 생성 시 payload의 값 범위, output의 경로를 검사할 수 있다. 스크립트 내에 해당 항목들을 검사하는 함수가 짜여저 있다. 피사체, 스타일, 구도, 텍스트 정확성, 유지 조건과 제외 조건은 결과물 이미지 자체를 보고 판단해야 한다.

> **검토 코멘트 — 일부 수정 필요:** 투명 배경 호환성과 `payload`의 허용값을 검사한다는 설명, 이미지 품질은 결과물을 보고 판단해야 한다는 결론은 맞다. 다만 `_build_output_paths`는 경로를 검사하기보다 출력 경로와 파일명을 구성한다. 확장자와 출력 형식이 다를 때도 실패시키지 않고 경고만 한다. 이 스크립트는 사용자가 명시적으로 선택한 CLI 대체 경로에서만 사용된다는 점도 구분해야 한다.
>
> **수정안:** `imagegen`의 CLI 스크립트는 출력 형식을 정규화하고, 투명 배경과 형식의 호환성, 모델·수량·크기·품질·배경·압축값 같은 기계적 조건을 검사한다. 출력 경로 함수는 파일명과 저장 위치를 구성하며 확장자 불일치는 경고한다. 반면 피사체, 스타일, 구도, 텍스트 정확성, 유지 조건과 제외 조건은 결과 이미지를 직접 보고 판단해야 한다.
>
> **근거:** imagegen CLI 스크립트 → `_normalize_output_format`, `_validate_transparency`, `_validate_generate_payload`, `_build_output_paths`; imagegen 원문 → `## Top-level modes and rules`, `## Workflow`

### 4. PNG 생성과 시각 QA 통과의 차이

**질문:** `documents`에서 페이지 PNG가 만들어졌다는 사실만으로 최종 문서를 전달해도 될까?

- **볼 부분:** documents 원문의 `## Non-negotiable: render → inspect PNGs → iterate`, `## Visual review (recommended)`, `### What rendering does and doesn’t validate`
- **비교할 내용:** PNG 존재·페이지 수 확인과 전 페이지 100% 검토를 구분한다. 전 페이지 검토 항목에는 잘림, 겹침, 깨진 표, 누락된 글자, 머리글·바닥글 위치가 포함된다.
- **판단 기준:** “변환 파일이 생겼는가”와 “내용과 배치가 전달 가능한 상태인가”를 별도로 기록한다. 댓글처럼 렌더링만으로 확인하기 어려운 요소에 구조 검사가 필요한지도 본다.

> **내 판단:** 예외 없는 원칙을 통해 출고 통과 조건이 제시되어 있다. 문서를 PNG형태로 렌더링하지 못한 예외 경우를 제외하고는 반드시 검사를 통해 결함이 없어질 때까지 반복 수정을 하게 되어 있다. 시각적 검토(권장) 부분에 시각적 검토 과정과 렌더링으로 검증할 수 있는 것과 없는 것을 자세히 기술해놓았다. 레이아웃 정확성, 글꼴, 간격, 표, 머리글·바닥글, 변경 내용 추적의 경우에는 렌더링으로 검증하며, 주석은 헤드리스 pdf 내보내기에서 렌더링되지 않는 경우가 많아 comments.xml, 앵커, rels, content-types을 구조적으로 검사한다.

> **검토 코멘트 — 대체로 정확함:** 렌더링, 전 페이지 검토, 수정과 재렌더링을 출고 조건으로 이해한 것은 맞다. 다만 렌더링을 생략할 수 있는 예외는 모든 렌더링 실패가 아니라 LibreOffice/`soffice`가 없어서 실패한 경우뿐이다. 다른 렌더링 오류는 먼저 고쳐야 한다. 또한 변경 내용 추적은 렌더링으로 전체 정확성을 검증하는 것이 아니라 화면에 표시되는지를 확인한다. 주석의 구조 검사에 대한 설명은 정확하다.
>
> **수정안:** `documents`는 최종 전달 전에 DOCX를 페이지 PNG로 렌더링하고 모든 페이지를 확인한 뒤, 결함이 있으면 수정·재렌더링하도록 요구한다. LibreOffice/`soffice`가 없어 렌더링할 수 없을 때만 구조 검사를 대신하고 시각 QA를 하지 못했다고 밝혀 전달할 수 있다. 렌더링은 레이아웃, 글꼴, 간격, 표, 머리글·바닥글과 변경 내용 추적이 화면에 나타나는지를 확인하는 데 쓰며, 주석은 `comments.xml`, 앵커, `rels`, `content-types`를 구조적으로도 검사한다.
>
> **근거:** documents 원문 → `## Non-negotiable: render → inspect PNGs → iterate`, `## Visual review (recommended)`, `### What rendering does and doesn’t validate`

### 5. 모델의 판단과 스크립트의 책임 나누기

**질문:** 각 Skill에서 어떤 판단을 코드로 옮기는 편이 좋을까? 어떤 판단은 모델에게 남겨야 할까?

- **볼 부분:** imagegen 원문의 `## Workflow`, imagegen 프롬프트 지침의 `## Specificity policy`와 `## Iterate deliberately`, documents 원문의 `## Tools + Contract Requirements`, `## Design standards for document generation`, `## Visual review (recommended)`
- **비교할 내용:** 출력 형식, 숫자 범위, 파일 존재, OOXML 구조처럼 답이 고정된 항목과 구성, 강조, 가독성, 심미적 완성도처럼 문맥에 따라 답이 달라지는 항목을 나눈다.
- **판단 기준:** “같은 입력이면 누구나 같은 답을 내야 하는가?”, “참·거짓 규칙으로 표현할 수 있는가?”, “사용자 목적에 따라 정답이 달라지는가?”를 차례로 묻는다. 앞의 두 질문이 ‘예’면 스크립트 후보다. 마지막 질문이 ‘예’면 모델 판단으로 남길 가능성이 크다.

> **내 판단:** 파일명, 경로 형식이나 payload의 값 등 구조가 정해져 있는 것들은 참/거짓을 판단하거나 같은 값에서 같은 결과가 나와야 하므로 이러한 부분은 스크립트에 일임한다. 그 외에 사용자의 요청이나 목적에 따라 항상 달라질 수 있는 부분들은 모델의 판단에 맡긴다.

> **검토 코멘트 — 방향은 맞음:** 결정적인 규칙과 맥락 의존적인 판단을 나눈 기준은 적절하다. 다만 파일명과 경로가 모두 참·거짓 검사 대상인 것은 아니다. `imagegen`에서는 일부 경로와 파일명을 스크립트가 구성하고 확장자 불일치는 경고한다. 또 “그 외”를 모두 모델에 맡기는 것이 아니라, 의미와 품질처럼 정성적이고 문맥에 따라 달라지는 항목을 주로 모델이 판단한다고 좁히는 편이 정확하다.
>
> **수정안:** 출력 형식, 값 범위, 호환성, 파일 존재 여부, OOXML 구조처럼 명확한 규칙으로 표현할 수 있고 같은 입력에 같은 판정이 필요한 항목은 스크립트로 검사한다. 경로와 파일명처럼 규칙이 정해진 값은 스크립트가 구성할 수도 있다. 반면 구성, 강조, 가독성, 심미적 완성도와 사용자 목적 충족 여부처럼 문맥에 따라 답이 달라지는 항목은 모델이 판단한다.
>
> **근거:** imagegen CLI 스크립트 → 검증 함수와 `_build_output_paths`; documents 원문 → `## Tools + Contract Requirements`, `## Visual review (recommended)`

### 6. 별도의 `When not to use` 절이 필요한 경우

**질문:** 내가 만들 Skill에도 비발동 조건을 따로 적어야 할까?

- **볼 부분:** imagegen 원문의 `description:`과 `## When not to use`, documents 원문의 `description:`과 전체 제목 목록
- **비교할 내용:** imagegen은 SVG·벡터·코드 기반 결과와의 경계를 frontmatter와 별도 절에 모두 적는다. documents는 DOCX·Word·Google Docs라는 산출물 범위를 중심으로 경계를 만든다.
- **판단 기준:** 비슷한 작업을 맡는 다른 Skill이 있는지, 같은 단어가 여러 산출물 형식을 뜻하는지, 잘못 발동했을 때 되돌리는 비용이 큰지 본다. 비발동 절이 필요하다고 보더라도 자동 선택에 중요한 제외 조건은 `description`에도 넣어야 하는지 함께 생각한다.

> **내 판단:** Skill이 의도치 않게 발동되어 되돌리는 비용이 큰 경우에는 별도의 When not to use 절이 반드시 있어야 한다. 비교적 간단한 반복 작업의 경우에는 필요 없을수도 있지만, 내 판단으로는 의도치 않은 발동 자체가 비용의 소모이고 이것은 생산성 하락으로 이어질 수 있기에 항상 있는 편이 낫다고 판단한다. 단, 구조화된 프롬프트가 반드시 더 좋은 결과물을 생성한다고 볼 수는 없으므로 스킬의 목적에 따라 어떤 내용으로 구성할 지에 대해 잘 판단해야 한다.

> **검토 코멘트 — 일부 보완 필요:** 오발동 비용 때문에 별도 비발동 절을 선호한다는 판단은 타당한 개인 원칙이다. 다만 첫 문장의 “반드시”는 원문에서 요구하는 공통 규칙으로 보기에는 과한 표현이며, 자동 선택에 중요한 제외 조건은 별도 절뿐 아니라 `description`에도 넣어야 한다. `SKILL.md`의 절차·제약·완료 형식 같은 일부 구성은 재사용 가능한 구조화된 프롬프트로 볼 수 있으므로 마지막 문장도 이 질문과 관련이 있다. 다만 Skill에는 참고 자료, 스크립트, 템플릿 같은 구성도 포함되므로 Skill 전체를 프롬프트와 같다고 볼 수는 없다. 별도 절이나 구조를 추가하는 것 자체보다 실제 발동 경계와 목적에 맞는 내용을 넣는지가 중요하다는 뜻으로 연결하면 더 분명하다.
>
> **수정안:** 별도의 `When not to use` 절이 모든 Skill에 필수인 것은 아니다. 다만 비슷한 작업을 맡는 Skill이 있거나, 같은 표현이 여러 산출물 형식을 뜻하거나, 오발동을 되돌리는 비용이 크다면 경계를 따로 적는 편이 좋다. 나는 오발동 자체도 비용이라고 생각하므로 경계 사례가 있다면 별도 절을 두겠다. 자동 선택에 중요한 제외 조건은 `description`에도 함께 적어야 한다. `SKILL.md`의 일부 구성은 구조화된 프롬프트로 볼 수 있지만, 구조를 더하는 것만으로 더 좋은 결과가 보장되지는 않으므로 Skill의 목적과 실제 경계에 맞게 내용을 구성해야 한다.
>
> **근거:** imagegen 원문 → frontmatter `description`, `## When not to use`; documents 원문 → frontmatter `description`, 별도 비발동 절 없음; 주차 README → `SKILL.md`와 부속 파일, 명시 호출과 자동 선택

### 7. 질문할 정보와 합리적으로 정할 정보 구분하기

**질문:** 정보가 빠졌을 때 언제 사용자에게 물어야 할까? 언제 판단해서 진행해도 될까?

- **볼 부분:** imagegen 원문의 `## Workflow` 5단계와 `## Shared prompt schema` 아래 augmentation rules, imagegen 프롬프트 지침의 `## Specificity policy`와 `## Allowed and disallowed augmentation`, documents 원문의 `### Documents clarification questions`
- **비교할 내용:** imagegen이 성공을 막는 누락과 세부 묘사 보완을 어떻게 구분하는지, documents가 주제·독자·목적과 형식·길이·스타일을 어떻게 다르게 다루는지 본다.
- **판단 기준:** 빠진 값이 사실관계, 결론, 대상 독자, 산출물 형식 또는 되돌리기 어려운 선택을 바꾸면 질문한다. 의미를 바꾸지 않고 나중에 쉽게 조정할 수 있는 표현·배치·스타일은 합리적으로 정할 수 있다. 확인되지 않은 사실이나 사용자 소유의 참조 표시는 추측하지 않는다.

> **내 판단:** 작업을 진행하는 데 있어 핵심적인 정보가 빠졌거나 작업 비용이 크고 되돌리기 어려운 항목은 사용자에게 다시 물어야 한다. 그 외에 언제든지 쉽게 수정할 수 있고 간단한 항목들은 사용자의 의도에 맞는 범위에서 합리적으로 판단할 수 있다.

> **검토 코멘트 — 대체로 정확함:** 질문할 정보와 직접 판단할 정보를 나누는 방향은 맞다. 다만 작업 비용이 크다는 사실만으로 질문하기보다, 빠진 선택이 결과를 크게 바꾸거나 되돌리기 어려운지를 보는 편이 정확하다. 또한 확인되지 않은 사실과 해결되지 않은 참조는 쉽게 고칠 수 있어 보여도 임의로 채우면 안 된다.
>
> **수정안:** 빠진 정보가 작업의 성공을 막거나 사실관계, 결론, 대상 독자, 산출물 형식 또는 되돌리기 어려운 선택을 바꾼다면 사용자에게 묻는다. 확인되지 않은 사실은 지어내지 않고 자리표시자로 남기며, 해결되지 않은 참조 표시는 사용자에게 확인한다. 반면 의미를 바꾸지 않고 쉽게 고칠 수 있는 표현, 배치, 스타일은 사용자의 의도 안에서 합리적으로 판단해 진행할 수 있다.
>
> **근거:** imagegen 원문 → `## Shared prompt schema`의 augmentation rules; imagegen 프롬프트 지침 → `## Specificity policy`, `## Allowed and disallowed augmentation`; documents 원문 → `### Documents clarification questions`

## 원문과 보조 파일

### imagegen

- `imagegen / SKILL.md` — 발동 범위, 비발동 조건, 모드, 전체 실행 절차
- `imagegen / references/prompting.md` — 프롬프트 구체성, 입력 이미지 역할, 반복 수정 기준
- `imagegen / references/cli.md` — CLI 입력과 출력 처리
- `imagegen / scripts/image_gen.py` — 기계적으로 검사하는 조건의 실제 구현

### documents

- `documents / SKILL.md` — 발동 범위, 문서 작성·검토·렌더링의 전체 절차
- `documents / tasks/read_review.md` — 기존 DOCX를 읽고 검토하는 절차
- `documents / tasks/create_edit.md` — DOCX 생성과 편집 절차
- `documents / tasks/verify_render.md` — 페이지 PNG에서 확인할 항목
- `documents / render_docx.py` — DOCX를 PDF와 페이지 PNG로 바꾸는 구현

### 한국어 번역본(로컬 학습 보조 자료)

- `imagegen.SKILL.ko.md`
- `documents.SKILL.ko.md`
