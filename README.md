# AI/AX Learning Lab

AI 도구로 작은 일을 끝내고 결과를 확인·수정하는 학습 저장소입니다. [CURRENT_WEEK.md](CURRENT_WEEK.md)에서 현재 주차로 이동하세요. 전체 순서와 공통 안내는 [LEARNING_GUIDE.md](LEARNING_GUIDE.md)에 있습니다.

초안·코드·기준 문장은 AI의 도움으로 만들어도 됩니다. 학습자는 목적·범위·기대 결과를 검토하고 실제 결과의 수용 여부를 판단합니다. 모든 주차에서 **필요한 개념 이해 → 도구 사용 → 결과와 구조 확인 → 수정·재사용**을 이어갑니다. 도구의 정의·역할과 파일·코드·설정의 구조 확인은 기본 학습에 포함합니다.

| 주차 | 주제 |
|---:|---|
| 1 | [Codex 요청과 수정](week01-codex-prompt-comparison/) |
| 2 | [Skill Creator와 Skills](week02-codex-skills/) |
| 3 | [MCP 연결](week03-mcp-integration/) |
| 4 | [작업 위임과 통합](week04-multi-agent-worktrees/) |
| 5 | [검증과 Hook](week05-development-harness/) |
| 6 | [API와 Tool 호출](week06-llm-api-tool-calling/) |
| 7 | [프레임워크 사용](week07-langchain-langgraph/) |
| 8 | [RAG와 근거 확인](week08-rag-evaluation/) |
| 9 | [Dify Workflow](week09-dify-workflow/) |
| 10 | [도구 선택](week10-ai-development-methods/) |
| 11 | [작은 프로젝트 결과](week11-portfolio-build/) |
| 12 | [검증과 설명](week12-portfolio-evidence/) |

## 폴더

- `lab/`: 바로 실행할 예제와 직접 사용할 코드·설정·자료.
- `prompts/`: 내용을 읽고 직접 보낼 요청 예시.
- `runs/`: 짧은 메모와 다시 확인할 결과. 외부 공개 의무는 없습니다.
- `.local/`: 개인·임시 자료. Git 제외.
- `references/`: 자료가 있는 주차에서 실습 뒤 확인할 비교 자료.

별도 측정기·채점표·전체 대화 로그는 만들지 않습니다. `runs/notes.md` 하나에 요청·결과·수정·남은 문제를 적거나 실제 코드와 테스트에 링크하면 충분합니다. 실행하지 못한 것은 `NOT_VERIFIED`로 남깁니다.

## 기존 학습을 이어갈 때

진도는 실제로 실행·이해·확인한 내용으로 판단합니다. 각 주차의 기초 설명과 Day 1부터 읽고 해당 활동의 결과가 있을 때만 건너뜁니다. 이미 쓴 초안을 버릴 필요는 없습니다. 2주차 Skill 초안이 있다면 Day 1의 이어가기 경로로 Creator 수정과 첫 호출을 진행할 수 있습니다.

`CURRENT_WEEK.md`, 루트·주차 README, 과정 제공 프롬프트와 공개 참고 자료는 과정 관리 대상입니다. `sync`는 `lab/`, `runs/`, `.local/`, `AGENTS.md`를 덮어쓰지 않습니다. 개정 예제가 필요하면 새 임시 학습 저장소의 파일과 비교해 필요한 파일만 반영합니다. 다음 주차는 시작할 때 최신 자료를 받습니다.

## Git과 외부 공유

검증한 의미 있는 변경에 `type(scope): 한국어 제목` 형식으로 커밋합니다. 매일 커밋하거나 빈 커밋을 만들 필요는 없습니다. 토큰·비밀값·개인 자료는 올리지 않습니다. 공개·배포는 선택이며 대상과 범위를 확인한 뒤 결정합니다.

이 저장소만 clone한 경우 과정 관리 명령을 실행할 필요는 없습니다. 이미 제공된 주차 안내로 학습하고 새 자료가 반영되면 업데이트합니다.
