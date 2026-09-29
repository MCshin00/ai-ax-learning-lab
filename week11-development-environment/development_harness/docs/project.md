# 문의 내보내기

운영 담당자가 OPEN 상태의 문의를 CSV로 내려받아 후속 작업에 사용한다.

## 현재 기능과 처리 경로

입력은 `Ticket` 목록이다. 각 문의에는 `id`, `team`, `status`, `title`이 있다.

1. `TicketExport.csv`가 목록을 받아 `TicketFilter.select`에 전달한다.
2. `TicketFilter`가 OPEN 상태만 선택하며 입력 순서를 유지한다.
3. `CsvCell.encode`가 각 필드를 표현한다. 쉼표가 있는 값은 큰따옴표로 감싼다.
4. `TicketExport`가 필드를 쉼표로, 문의를 LF로 연결하고 헤더를 붙인다.

CSV 열 순서는 `id,team,status,title`이다. `ExportFilename.name`은 현재 `tickets.csv`를 반환한다. 완성된 CSV 프로그램은 모델이나 MCP 호출 없이 실행된다.

`VPN, 접속`은 `"VPN, 접속"`으로 표현해 제목의 쉼표를 열 구분과 구별한다. 단순한 제목은 그대로 표현한다. 제목 안의 큰따옴표·줄바꿈 보존은 이후 사례에서 다룬다.

## 주요 파일과 자료

| 위치 | 역할 |
|---|---|
| `src/main/java/lab/export/Ticket.java` | 문의 입력 |
| `src/main/java/lab/export/TicketFilter.java` | 내보낼 문의 선택 |
| `src/main/java/lab/export/CsvCell.java` | 한 셀의 문자열 표현 |
| `src/main/java/lab/export/TicketExport.java` | 선택한 문의를 CSV로 조합 |
| `src/main/java/lab/export/ExportFilename.java` | 다운로드 파일 이름 |
| `src/main/java/lab/export/ExportExample.java` | 제공 문의의 파일 이름과 CSV를 IDE 콘솔에 출력 |
| `data/acceptance-cases.json` | 요구별 입력·기대 결과·판단 이유 |
| `src/test/java/lab/export/ExportAcceptanceTest.java` | 업무 입력과 실제 CSV를 대조 |
| `src/main/java/lab/harness/` | 개발 MCP, Hook 처리와 검사 실행의 참고 구현 |
| `src/test/java/lab/harness/HarnessTest.java` | Hook 처리·검사 판정·상태 전달·MCP 조회 검사 |
| `harness.json` | Hook에서 사용할 검사 작업·제한 시간·결과 위치 |

요구 사례는 CSV-COMMA, CSV-QUOTE, CSV-LINE, TEAM, FILENAME, DEDUP으로 구분되어 있다. 현재 Day와 요청에 해당하는 사례를 사용한다. 개발 MCP는 이 자료를 개발 AI에 제공하는 연결이다.

## 검사와 확인 범위

Java 17 이상으로 Gradle을 동기화하고 IDE의 Gradle 창에서 실행한다.

- `test`: 초기 상태 확인 또는 하네스·공통 설정 변경 시 실행한다. 업무 검사와 하네스 검사를 모두 포함한다.
- `acceptance`: CSV 업무 변경 시 실행한다. `lab/export` 패키지의 모든 검사를 선택한다. 현재 업무 검사 클래스는 `ExportAcceptanceTest`이다.
- 결과: 각각 `build/reports/tests/test/index.html`, `build/reports/tests/acceptance/index.html`에서 확인한다.

현재 업무 검사는 단순 제목의 OPEN 문의와 CLOSED 문의를 대조하고, CSV-COMMA 입력에서는 `VPN, 접속`을 한 셀로 표현하는지 확인한다. 이때 단순 제목·OPEN 선택·열 순서도 함께 확인한다. 큰따옴표·줄바꿈·팀 선택·날짜 파일 이름·중복 처리의 검사는 해당 기능을 구현할 때 추가한다.

`harness.json`은 `acceptance`와 120초 제한을 지정한다. 실제 Hook 실행에서는 `.local/harness/checks/` 아래의 새 빌드 결과로 판정한다. 자동 검사는 참고 구현의 동작을 확인하며, 코딩 도구에서 Skill·MCP·Hook이 실제로 사용되는지는 각 연결 활동에서 확인한다.
