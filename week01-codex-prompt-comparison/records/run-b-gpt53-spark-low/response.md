# Run B 낮은 추론 조건 첫 최종 응답

- 실행일: 2026-08-07
- 출처: `run-b-gpt53-spark-low`에서 실행한 대화형 Codex의 첫 turn 최종 응답
- 보안 정리: 원문의 사용자 절대 경로 한 곳을 저장소 상대경로로 바꿨습니다. 나머지 문구와 구조는 그대로입니다.

## 응답 본문

구현 완료했습니다.

변경 파일
- [src/main/java/lab/week01/TicketTitleNormalizer.java](src/main/java/lab/week01/TicketTitleNormalizer.java)

적용 내용
- `null` 또는 `isBlank()` 입력이면 `IllegalArgumentException`을 던지도록 처리
- 양끝 공백 제거 후 연속 공백/개행/탭 등 Unicode 공백을 한 칸으로 축약
- 정규화 후 빈 문자열이면 예외 처리
- 결과 문자열을 `codePoints()` 기준으로 80개까지만 남기도록 잘라서 보조 문자쌍 분할 없이 안전 처리 (Java 17)

실행한 검증
- `./gradlew.bat test`

검증 결과
- `BUILD SUCCESSFUL` (6개 테스트 모두 `PASSED`)

남은 위험
- 없음 (요구사항 범위 내 동작은 테스트로 확인됨).
