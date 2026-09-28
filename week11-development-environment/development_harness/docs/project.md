# 문의 내보내기

운영 담당자가 OPEN 상태의 문의를 CSV로 내려받아 후속 작업에 사용한다.
첫 구현은 단순 문자열만 다룬다. 필드는 id, team, status, title 순서다.
열 구분은 쉼표, 행 구분은 LF이며 입력 순서를 유지한다.
확장 요구는 data/acceptance-cases.json에 있다. 해당 Day의 요구만 적용한다.

## 파일과 검사

- TicketFilter: 내보낼 항목 선택
- CsvCell: 한 셀의 문자열 표현
- TicketExport: 선택한 항목을 CSV로 연결
- ExportFilename: 다운로드 파일 이름
- ExportAcceptanceTest: 실제 CSV와 파일 이름을 요구에 대조하는 검사

검사는 Gradle acceptance로 실행한다. 테스트가 없거나 실행되지 않은 상태는 성공으로 판단하지 않는다.
