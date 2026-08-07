# AI/AX Learning Lab

Codex 활용부터 Skills, MCP, 멀티 에이전트, 개발 하네스와 LLM 애플리케이션까지 12주 동안 직접 실험하고 기록하는 저장소입니다.

현재 할 일은 [`CURRENT_WEEK.md`](CURRENT_WEEK.md)에서 확인합니다. 전체 개념과 완료 기준은 [`LEARNING_GUIDE.md`](LEARNING_GUIDE.md)에 있습니다.

## 주차별 학습 기록

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

각 주차를 시작하면 해당 폴더에 실습 코드·데이터와 `prompts/`가 추가됩니다. 참고 구현은 실습을 마친 뒤 과정의 `reference` 명령으로 공개하며 같은 주차의 `references/`에 들어갑니다.

`CURRENT_WEEK.md`, `LEARNING_GUIDE.md`, 이 루트 `README.md`, 시작한 주차의 `README.md`, 현재 주차의 `prompts/`와 공개한 `references/`는 과정 도구가 다시 만들 수 있습니다. 개인 요약과 실제 대화 기록은 각 주차의 `notes/`와 `experiments/`에 기록합니다. `notes/` 전체와 `experiments/`의 Markdown·JSONL·로그·`private/` 자료는 Git에서 제외됩니다. A/B Run의 코드·테스트와 재사용 가능한 설정·평가 자산은 계속 추적합니다. `AGENTS.md`와 실습 코드는 학습자가 관리하며 `sync`가 덮어쓰지 않습니다.

## Git 기록

각 주차의 모든 Day를 마칠 때 테스트와 변경 범위를 확인하고, AngularJS 커밋 컨벤션과 한국어 제목으로 그날의 검증 시점을 한 번 커밋합니다. 공유할 변경이 없는 Day에는 개인 기록을 `git add -f`로 넣지 말고 빈 마감 커밋을 사용합니다. 자세한 절차와 예시는 `LEARNING_GUIDE.md`의 공통 안내를 따릅니다.

## 공용 자료

여러 주차에서 함께 사용하는 벤치마크, 계약, 실행 도구와 기록 템플릿은 [`shared/`](shared/)에 있습니다. 재사용할 코드·설정·평가 자산은 해당 주차 폴더에 커밋하고, 개인 메모와 원시 대화·로그는 로컬의 `notes/`와 `experiments/`에 보관합니다.
