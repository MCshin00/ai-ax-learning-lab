# Run A 낮은 추론 조건 첫 최종 응답

- 실행일: 2026-08-07
- 출처: `run-a-gpt53-spark-low`에서 실행한 대화형 Codex의 첫 turn 최종 응답
- 보안 정리: 원문의 사용자 절대 경로 한 곳을 저장소 상대경로로 바꿨습니다. 나머지 문구와 구조는 그대로입니다.

## 응답 본문

요청한 `TicketTitleNormalizer`를 구현했고 테스트를 통과했습니다.

- 변경 파일: [src/main/java/lab/week01/TicketTitleNormalizer.java](src/main/java/lab/week01/TicketTitleNormalizer.java)
- 구현 내용
  - `null` 입력 시 `IllegalArgumentException`
  - `trim()` + `\\s+` 정규식으로 선행/후행 공백 제거 및 연속 공백(줄바꿈/탭 포함) 하나로 축소
  - 정규화 결과가 빈 문자열이면 `IllegalArgumentException`
  - 80개 **유니코드 코드포인트** 초과 시 초과 부분 제거
  - 보조문자(서로게이트 페어, 예: 이모지) 손상 없이 코드포인트 단위로 절단

- 실행한 검증
  - `./gradlew test`
  - 결과: `BUILD SUCCESSFUL` (모든 테스트 통과)

남은 위험: 특수 요구사항(예: 허용할 수 없는 문장부호/길이 정책, 비ASCII 공백 정책)이 있었다면 추가 규칙이 필요하지만, 현재 테스트 기준에서는 모두 충족됩니다.
