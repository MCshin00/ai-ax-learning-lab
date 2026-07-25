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
