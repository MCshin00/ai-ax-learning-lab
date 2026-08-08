# AI/AX Learning Lab

개인 학습 과정과 AI를 활용한 개발 실험을 재현 가능한 증거로 남기는 저장소입니다. 완성된 코드만 모으는 곳이 아니라, 어떤 요청을 보냈고 어떤 응답과 실패를 거쳐 무엇으로 검증했는지 함께 기록합니다.

처음 시작할 때는 [`CURRENT_WEEK.md`](CURRENT_WEEK.md)를 열고 1주차 링크를 따라가세요. 자세한 학습 본문은 각 주차의 `README.md`, 과정 전체의 공통 원칙과 통합 보기는 [`LEARNING_GUIDE.md`](LEARNING_GUIDE.md), 12주 지도는 아래 표에서 확인합니다.

## 12주 과정

| 주차 | 주제 |
|---:|---|
| 1 | [Codex 프롬프트 비교](week01-codex-prompt-comparison/) |
| 2 | [Codex Skills](week02-codex-skills/) |
| 3 | [MCP 통합](week03-mcp-integration/) |
| 4 | [멀티 에이전트와 Worktree](week04-multi-agent-worktrees/) |
| 5 | [개발 하네스](week05-development-harness/) |
| 6 | [LLM API와 Tool Calling](week06-llm-api-tool-calling/) |
| 7 | [LangChain과 LangGraph](week07-langchain-langgraph/) |
| 8 | [RAG 평가](week08-rag-evaluation/) |
| 9 | [Dify 워크플로](week09-dify-workflow/) |
| 10 | [AI 개발 방법 비교](week10-ai-development-methods/) |
| 11 | [웹앱 수직 기능](week11-webapp-vertical-slice/) |
| 12 | [릴리스와 포트폴리오](week12-release-portfolio/) |

## 폴더 사용법

```text
ai-ax-learning-lab/
├─ CURRENT_WEEK.md
├─ shared/
└─ weekNN-topic/
   ├─ README.md
   ├─ lab/
   ├─ prompts/
   ├─ runs/
   ├─ references/
   └─ .local/
      ├─ notes/
      ├─ raw/
      └─ scratch/
```

- `README.md`: 해당 주차의 상세 학습 본문이자 시작점입니다.
- `lab/`: 시작 코드, 설정, 평가 자료와 직접 수정할 구현입니다.
- `prompts/`: 과정이 제공하는 요청 자료입니다. 과정 동기화가 갱신할 수 있습니다.
- `runs/`: 다른 사람이 과정을 검토하고 재현할 수 있는 공개 증거입니다. 과정 동기화가 덮어쓰지 않습니다.
- `references/`: 실습을 마친 뒤 공개하는 비교용 참고 구현입니다.
- `.local/`: 개인 메모, 정제 전 원본과 임시 파일입니다. 폴더 전체를 Git에서 제외합니다.
- `shared/`: 여러 주차가 함께 쓰는 실행 도구, 템플릿과 벤치마크 묶음입니다.

## 이 저장소로 직접 학습하려면

이 저장소를 Fork하거나 clone한 뒤 본인 브랜치에서 진행하세요. 상세 본문과 `lab/`·`prompts/`가 들어 있는 주차는 바로 시작할 수 있고, README만 있는 주차는 아직 시작 자료가 공개되지 않은 상태입니다. 이후 저장소가 갱신되면 기존 `runs/`를 보존한 채 새 주차 자료를 받아서 이어갑니다.

기존 `runs/`는 과정 작성자가 실제로 수행한 공개 사례이므로 덮어쓰지 않습니다. 본인의 실행은 `runs/<고유한-run-id>/`에 요청·응답·검증 증거를 새로 만들고, 개인 메모와 정제 전 로그는 본인 컴퓨터의 `.local/`에 둡니다. 시작 코드와 공개 사례를 비교하되, 먼저 기존 응답을 읽고 그대로 따라 하기보다 같은 과제를 직접 요청하고 검증한 뒤 차이를 확인하세요.

## 공개 증거와 로컬 기록

실제로 전송한 요청과 받은 응답, 실패 카드, 테스트 결과, diff와 개인 경로·비밀값을 제거한 로그는 `runs/<run-id>/`에 커밋합니다. 다른 사람이 결과뿐 아니라 판단 과정을 확인할 수 있을 정도의 맥락과 실행 조건을 함께 남깁니다.

개인 회고는 `.local/notes/`, 정제 전 JSONL·로그는 `.local/raw/`, 버려도 되는 작업 파일은 `.local/scratch/`에 둡니다. `.local/`은 Git에서 제외되므로 clone에는 빈 하위 폴더가 없을 수 있습니다. 각 주차 안내가 처음 사용할 때 필요한 폴더를 만들며, 직접 기록한다면 먼저 해당 폴더를 생성하세요. 원본 로그를 공개하기 전에는 토큰, 인증 정보, 사용자명과 절대 경로가 없는지 검사하고 정제된 사본만 `runs/`로 옮깁니다.

`CURRENT_WEEK.md`, 루트 `README.md`, 시작한 주차의 `README.md`, `prompts/`와 공개한 `references/`는 과정 도구가 갱신할 수 있습니다. `AGENTS.md`, `lab/`, `runs/`와 `.local/`은 학습자가 관리합니다.

## Git 기록

각 주차의 모든 Day를 마칠 때 테스트와 변경 범위를 확인하고, AngularJS 커밋 컨벤션과 한국어 제목으로 그날의 검증 시점을 한 번 커밋합니다. 공유할 변경이 없는 Day에는 개인 기록을 `git add -f`로 넣지 말고 빈 마감 커밋을 사용합니다.

저장소 생성 도구는 요청한 경우에만 빈 Git 저장소를 만들며 파일을 자동 stage하거나 commit하지 않습니다. 첫 커밋 전 `git status`, 공개 증거와 `.gitignore`를 직접 확인하세요.
