# [실험 입력 B] 구조화된 요청

`week01-codex-prompt-comparison/.local/scratch/run-b`에 같은 과제 작업 사본이 준비된 상태에서 사용합니다. 공개 증거는 작업 사본이 아니라 `week01-codex-prompt-comparison/runs/run-b`에 기록합니다. 각 섹션이 작업 폴더의 어떤 파일과 조건을 가리키는지 확인한 뒤, 아래 본문만 복사해 Codex 앱이나 대화형 CLI의 입력창에 직접 붙여넣어 보냅니다.

## 직접 보낼 내용

```markdown
# Goal
현재 작업 폴더의 `TicketTitleNormalizer.normalize`를 구현한다.

# Context
- 먼저 `README.md`, `build.gradle`, `src/main/java/lab/week01/TicketTitleNormalizer.java`, `src/test/java/lab/week01/TicketTitleNormalizerTest.java`를 읽는다.
- 시작 코드는 의도적으로 미구현 상태다.

# Allowed paths
- `src/main/java/**`
- 필요하면 `src/test/java/**`에 새 회귀 테스트 파일이나 메서드 추가

# Forbidden
- 기존 Public Test 수정·삭제·약화
- Gradle 설정이나 기존 의존성 변경
- 다른 폴더 변경

# Acceptance criteria
- null·blank는 IllegalArgumentException
- 앞뒤 공백 제거
- 연속 whitespace를 한 칸으로 변환
- 결과가 Unicode code point 기준 80자를 넘으면 앞의 80개 code point만 남김
- Java 17

# Verification
- 이 작업 사본의 루트에서 Windows는 `.\gradlew.bat clean test`, macOS·Linux·WSL은 `./gradlew clean test`를 실행한다.
- 모든 공개 테스트가 통과해야 완료로 기록한다. 실행하지 못한 항목은 `NOT_VERIFIED`와 이유를 남긴다.

# Handoff
변경 파일, 실행한 테스트, 남은 위험을 보고한다.
```

Run A와 비교할 때는 이 본문을 임의로 줄이거나 늘리지 않습니다.
