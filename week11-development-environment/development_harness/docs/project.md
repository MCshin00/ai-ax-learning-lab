# 문의 내보내기

운영 담당자가 OPEN 상태의 문의를 CSV로 내려받아 후속 작업에 사용한다.

## 현재 기능과 처리 경로

입력은 `Ticket` 목록이다. 각 문의에는 `id`, `team`, `status`, `title`이 있다.

1. `TicketExport.csv`가 목록을 받아 `TicketFilter.select`에 전달한다.
2. `TicketFilter`가 OPEN 상태만 선택하며 입력 순서를 유지한다.
3. `CsvCell.encode`가 각 필드를 표현한다. 쉼표가 있는 값은 큰따옴표로 감싼다.
4. `TicketExport`가 필드를 쉼표로, 문의를 LF로 연결하고 헤더를 붙인다.

CSV 열 순서는 `id,team,status,title`이다. `ExportFilename.name`은 현재 `tickets.csv`를 반환한다.

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
| `src/test/java/lab/export/ExportAcceptanceTest.java` | 업무 입력과 실제 CSV를 대조 |

변경 요청은 주차 본문의 각 Day에서 확인한다. 요청과 현재 코드를 대조해 필요한 조건을 정하고, 그 조건에서 검사 입력과 기대 결과를 도출한다. 확정한 업무 조건과 달라진 구조는 이 문서에 반영한다.

## 검사와 확인 범위

Java 17 이상으로 Gradle을 동기화하고 IDE의 Gradle 창에서 실행한다.

- `test`: 초기 상태 확인 또는 하네스·공통 설정 변경 시 실행한다. 프로젝트의 모든 검사를 실행한다.
- `acceptance`: CSV 업무 변경 시 실행한다. `lab/export` 패키지의 모든 검사를 선택한다. 현재 업무 검사 클래스는 `ExportAcceptanceTest`이다.
- 결과: 각각 `build/reports/tests/test/index.html`, `build/reports/tests/acceptance/index.html`에서 확인한다.

현재 업무 검사는 단순 제목의 OPEN 문의와 CLOSED 문의를 대조하고, 쉼표 제목 입력에서는 `VPN, 접속`을 한 셀로 표현하는지 확인한다. 이때 단순 제목·OPEN 선택·열 순서도 함께 확인한다. 큰따옴표·줄바꿈·팀 선택·날짜 파일 이름·중복 처리의 검사는 해당 기능을 구현할 때 추가한다.

검사 실행부와 Hook은 Day 3에서 구현한다. 실행 진입점과 검사 결과 위치가 정해지면 이 문서에 반영한다.
