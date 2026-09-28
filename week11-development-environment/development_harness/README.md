# 개발 하네스 실습 자료

상세 학습은 [주차 본문](../README.md)의 Day 1~5를 따릅니다. Java 17 이상에서 IDE의 Gradle 동기화 후 `test`를 실행합니다. 제공 기능은 단순한 OPEN 문의 CSV 내보내기이며, 확장할 요구는 `data/acceptance-cases.json`에 있습니다.

`src/main/java/lab/export`가 개발할 업무 코드, `lab/harness`가 개발 환경에 연결할 참고 구현입니다. 업무 검사 `acceptance`와 하네스 자체의 검사 `test`를 나눕니다. `harnessJar`는 실제 코딩 도구에 연결할 JAR를 만듭니다.

| 파일 | 사용하는 이유 |
|---|---|
| `docs/project.md` | 세션 시작과 재개에 필요한 기능·코드·검사 위치 |
| `setup/AGENTS.example.md` | 상시 지침을 작성할 때 참고할 예 |
| `setup/export-change/SKILL.md` | 기능 변경 시 요구부터 검사까지 이어 가는 작업 절차 |
| `harness.json` | 완료 전에 실행할 Gradle 작업과 결과 위치 |
| `HarnessMain` | 프로젝트별 Hook 설정 생성과 MCP 서버 실행 |
| `HookHandler` | 시작 자료 전달, 도구 실행 관찰, 종료 전 검사와 한 번의 수정 요청 |
| `GradleCheck` | 새 검사 결과로 성공·기능 실패·실행 불가 구별 |
| `AcceptanceServer` | 개발 AI에 요구별 입력 예와 기대 결과를 제공하는 MCP |

프로젝트에 설정을 적용하는 순서와 Windows·macOS·Linux·WSL 명령은 주차 본문 Day 2~3에 있습니다. 생성되는 `.codex/`와 `.local/`은 이 프로젝트에서 Git 공개 대상에서 제외합니다. Hook 설정은 현재 프로젝트와 JAR 경로를 담으므로 worktree마다 다시 생성합니다.

자동 검사는 확인한 사례의 동작을 판단합니다. 실제 요구 해석, 구현의 적절성, 검토할 diff는 개발자와 AI가 함께 확인합니다. 코딩 도구의 실제 모델 실행은 학습자가 시작하고, 제공 하네스 테스트는 모델을 호출하지 않습니다.
