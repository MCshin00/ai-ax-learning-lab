# 문의 내보내기

운영 담당자가 OPEN 상태의 문의를 CSV로 내려받아 후속 작업에 사용한다.

## 현재 기능과 처리 경로

입력은 `Ticket` 목록이다. 각 문의에는 `id`, `team`, `status`, `title`이 있다.

1. `TicketExport.export`가 목록, 선택적인 팀명과 중복 선택값을 받는다. 기존 호출은 `DuplicateSelection.FIRST`를 사용하고, 세 인수 호출에서 `FIRST` 또는 `LAST`를 지정한다. 팀 미지정은 `null` 또는 한 인수 호출로 표현한다.
2. `TicketFilter`가 OPEN 상태만 선택하며 입력 순서를 유지한다. 팀을 지정하면 이름이 정확히 일치하는 문의만 남기고, 빈 팀명은 잘못된 입력으로 처리한다.
3. 선택한 문의에서 같은 `id`는 중복으로 본다. `FIRST`는 첫 항목, `LAST`는 마지막 항목의 내용을 남긴다. `LinkedHashMap`은 같은 ID의 값을 바꿔도 첫 삽입 위치를 유지하므로 어느 선택에서도 ID의 출력 순서는 첫 등장 순서다. 선택을 통과한 항목 수에서 고유 ID 수를 뺀 값이 중복 제외 수다.
4. `CsvCell.encode`가 각 필드를 표현한다. 값 안의 큰따옴표는 두 번 쓰고, 쉼표·큰따옴표·줄바꿈(LF, CRLF, CR)이 있는 값은 큰따옴표로 감싼다. 줄바꿈 문자는 원문 그대로 둔다.
5. `TicketExport`가 필드를 쉼표로, 문의를 LF로 연결하고 헤더를 붙인다. 선택 결과가 없으면 헤더만 반환한다. `ExportResult`가 CSV·제외한 중복 수·실제로 내보낸 수를 함께 반환한다. 기존 `csv` 호출도 같은 결과의 CSV를 반환한다.

CSV 열 순서는 `id,team,status,title`이다. `ExportFilename.name(date, team)`은 호출자가 전달한 날짜와 팀으로 `tickets-팀명-YYYY-MM-DD.csv`를 반환한다. 팀 미지정이나 기존 `name(date)`는 `tickets-YYYY-MM-DD.csv`를 반환한다. 파일 이름의 팀명 표현만 인코딩하며 문의 선택에는 원래 팀명을 그대로 전달한다. `ExportExample.main`은 OPS 팀과 예시 날짜를 같은 두 호출에 전달하고, 첫 기록·마지막 기록 각각의 파일 이름·CSV·두 건수를 콘솔에 출력한다.

`VPN, 접속`은 `"VPN, 접속"`으로 표현해 제목의 쉼표를 열 구분과 구별한다. `로그인 "오류"`는 `"로그인 ""오류"""`로, `VPN, "접속"`은 `"VPN, ""접속"""`로 표현한다. `첫 줄\n다음 줄`은 원문의 LF를 유지한 `"첫 줄\n다음 줄"` 셀로 표현한다. 단순한 제목은 그대로 표현한다.

## 주요 파일과 자료

| 위치 | 역할 |
|---|---|
| `src/main/java/lab/export/Ticket.java` | 문의 입력 |
| `src/main/java/lab/export/TicketFilter.java` | 내보낼 문의 선택 |
| `src/main/java/lab/export/CsvCell.java` | 한 셀의 문자열 표현 |
| `src/main/java/lab/export/TicketExport.java` | 선택한 문의를 ID별 첫 기록·마지막 기록으로 정리하고 CSV로 조합 |
| `src/main/java/lab/export/DuplicateSelection.java` | 중복 문의의 `FIRST`·`LAST` 선택값 |
| `src/main/java/lab/export/ExportResult.java` | CSV와 두 건수를 함께 반환 |
| `src/main/java/lab/export/ExportFilename.java` | 날짜·팀명을 포함하는 다운로드 파일 이름과 팀명 인코딩 |
| `src/main/java/lab/export/ExportExample.java` | OPS 팀의 첫 기록·마지막 기록 파일 이름·CSV·두 건수를 IDE 콘솔에 출력 |
| `src/test/java/lab/export/ExportAcceptanceTest.java` | 업무 입력과 실제 CSV를 대조 |
| `src/test/java/lab/export/TicketFilterTest.java` | 팀 미지정·정확 비교·입력 순서·빈 팀명 검사 |
| `src/test/java/lab/export/ExportFilenameTest.java` | 호출자 날짜·팀명·금지 문자·퍼센트 인코딩의 파일 이름 확인 |
| `src/test/java/lab/export/ExportIntegrationTest.java` | 같은 팀·날짜·선택값으로 파일 이름과 CSV·두 건수를 함께 확인 |
| `src/main/java/lab/harness/StopHook.java` | `Stop` 사건을 받아 업무 검사를 실행하고 결과 파일을 읽어 Codex 응답 결정 |
| `src/test/java/lab/harness/StopHookTest.java` | 준비한 검사 실행 결과와 XML로 통과·실패·실행 불가의 최종 판정 확인 |
| `src/main/java/lab/harness/SessionStartHook.java` | 시작 사건을 받아 프로젝트 설명과 로컬 인계 메모를 문맥으로 반환 |
| `src/test/java/lab/harness/SessionStartHookTest.java` | 인계 메모 유무에 따른 시작 문맥과 사건 확인 |

변경 요청은 주차 본문의 각 Day에서 확인한다. 요청과 현재 코드를 대조해 필요한 조건을 정하고, 그 조건에서 검사 입력과 기대 결과를 도출한다. 확정한 업무 조건과 달라진 구조는 이 문서에 반영한다.

Day 4 변경 조건: 팀을 지정하면 해당 팀의 OPEN 문의만 포함하고, 팀 미지정 시 모든 팀의 OPEN 문의를 포함한다. 팀명은 정확히 비교하고 빈 팀명은 잘못된 입력으로 처리한다. 선택 결과가 없으면 CSV 헤더만 반환한다. 날짜는 호출자가 전달하며 파일 이름은 `tickets-YYYY-MM-DD.csv` 형식이다. 팀 선택은 `TicketFilter`에서, CSV 조합과 헤더만 반환하는 결과는 `TicketExport`에서 처리한다. 날짜 없는 파일 이름 호출은 사용하지 않는다.

## 검사와 확인 범위

Java 17 이상으로 Gradle을 동기화하고 IDE의 Gradle 창에서 실행한다.

- `test`: 초기 상태 확인 또는 하네스·공통 설정 변경 시 실행한다. 프로젝트의 모든 검사를 실행한다.
- `acceptance`: CSV 업무 변경 시 실행한다. `lab/export` 패키지의 `ExportAcceptanceTest`, `TicketFilterTest`, `ExportFilenameTest`, `ExportIntegrationTest`를 포함한다.
- 결과: 각각 `build/reports/tests/test/index.html`, `build/reports/tests/acceptance/index.html`에서 확인한다.

업무 검사는 팀 미지정 시 모든 팀의 OPEN 문의, 지정 팀의 정확한 일치·입력 순서, CLOSED 제외, 선택 결과가 없을 때 헤더만 반환하는 결과와 빈 팀명 오류를 확인한다. 같은 ID의 CLOSED 뒤 OPEN, 같은 ID의 OPEN 두 건, 팀 미지정 시 팀이 다른 같은 ID, 중복이 없는 입력과 선택 결과가 없는 입력에서 CSV와 두 건수를 확인한다. 첫 기록·마지막 기록 모두 첫 ID 등장 순서를 유지하며, 마지막 OPEN 뒤의 CLOSED나 다른 팀 항목은 채택하지 않는지 확인한다. 기존 `export`·`csv` 호출의 첫 기록 선택과 `null` 선택값 오류도 검사한다. 쉼표·큰따옴표·LF·CRLF·CR 제목 표현은 마지막으로 채택된 제목에서도 확인한다.

`ExportFilenameTest`는 서로 다른 호출자 날짜, 지정·미지정 팀, 빈 팀명 오류, 파일 이름 금지 문자와 제어 문자·`%`의 인코딩을 확인한다. `ExportIntegrationTest`는 OPS 팀의 두 중복 선택, `OPS/지원`과 `OPS%2F지원`의 구별, 팀 미지정의 마지막 기록을 파일 이름·CSV·두 건수와 함께 확인한다. `ExportExample.main`의 대표 OPS 입력은 두 선택 모두 중복 제외 수 1, 내보낸 수 2를 출력한다.

Day 5 변경 조건: `id`가 같으면 같은 문의다. 먼저 OPEN·팀 조건을 적용하고, 남은 문의에서 ID별 첫 항목을 내보낸다. 중복 수는 이 선택을 통과한 뒤 같은 ID로 제외된 항목 수이며, 내보낸 수는 CSV 데이터 행 수다. 두 건수는 CSV 열에 넣지 않고 `ExportResult`로 함께 제공한다.

## 첫 기록·마지막 기록 선택과 팀 파일 이름

중복 문의에서 첫 기록과 마지막 기록을 선택할 수 있다. 선택을 생략하면 첫 기록을 남긴다. 마지막 기록은 OPEN·팀 조건을 통과한 목록에서 같은 ID가 마지막으로 나타난 항목이다. 어느 내용을 채택해도 ID의 출력 순서는 첫 등장 순서를 유지하고, 중복 제외 수와 내보낸 수의 집계 범위는 같다.

OPS 팀 입력이 `T-1/CLOSED/옛 기록`, `T-1/OPEN/첫 제목`, `T-2/OPEN/다른 문의`, `T-1/OPEN/수정 제목`, `T-3/APP/OPEN/다른 팀` 순서라면, 첫 기록 또는 선택 생략에서는 T-1의 `첫 제목`과 T-2의 `다른 문의`가 나온다. 마지막 기록에서는 T-1의 `수정 제목`과 T-2의 `다른 문의`가 같은 순서로 나온다. 두 경우 모두 중복 제외 수는 1, 내보낸 수는 2다.

공개 입력은 `TicketExport.export(tickets, team, duplicateSelection)`과 `DuplicateSelection.FIRST`·`DuplicateSelection.LAST` 선택값이다. 기존 `export`·`csv` 호출은 첫 기록 선택을 유지하고 `ExportResult`의 세 값도 유지한다. 팀 미지정에서 마지막 기록을 고를 때는 `team`에 `null`을 전달한다. 명시적인 선택값에 `null`을 전달하면 `IllegalArgumentException`이다. 두 인수의 선택값 호출은 기존 `export(tickets, null)`과 모호해지므로 사용하지 않는다.

파일 이름 입력은 `ExportFilename.name(date, team)`이다. 팀 미지정 또는 기존 `name(date)`는 `tickets-2026-09-03.csv`, OPS 지정은 `tickets-OPS-2026-09-03.csv`를 반환한다. 빈 팀명은 기존 팀 선택처럼 잘못된 입력이다. 팀명의 `<`, `>`, `:`, `"`, `/`, `\`, `|`, `?`, `*`, U+0000~U+001F 제어 문자와 `%`는 UTF-8 바이트의 대문자 `%HH`로 표현하고 나머지 문자는 유지한다. 예를 들어 `OPS/지원`은 `tickets-OPS%2F지원-2026-09-03.csv`가 된다. `OPS%2F지원`은 `tickets-OPS%252F지원-2026-09-03.csv`가 되어 다른 팀명과 구별된다.

이번 변경은 같은 주 작업 공간에서 기존 미커밋 변경을 보존하고 서로 다른 파일을 맡은 기본 서브에이전트로 진행했다. 구현 담당 A는 중복 선택과 `ExportAcceptanceTest`, B는 팀 파일 이름과 `ExportFilenameTest`를 변경했다. 메인은 두 담당의 코드와 전용 검사 XML을 확인한 뒤 통합 담당 C에게 예제·결합 검사·현재 구조 설명을 연결했다. 담당별 검사 결과는 `.local/harness/checks/a-build/test-results/acceptance/`, `.local/harness/checks/b-build/test-results/acceptance/`에 있다. 결합 검사는 OPS 두 선택의 출력, 파일 이름 인코딩과 원래 팀 선택의 연결, 팀 미지정의 마지막 기록을 확인하며 통과했다. 결과는 `.local/harness/checks/c-build/test-results/acceptance/TEST-lab.export.ExportIntegrationTest.xml`에 있다. 최종 업무 검사와 대표 실제 출력의 확인 결과는 `.local/harness/checkpoint.md`에 연결한다.

Day 3 실제 실패 전달 확인: `CsvCell.encode`의 감싸기 조건에서 LF·CR 판단을 잠시 제거하자, 실제 Stop Hook이 `lineBreaksInTitleStayInOneCell()`의 기대값과 실제값 및 XML 위치를 전달했다. 줄바꿈 문자는 남았지만 셀의 바깥 큰따옴표가 빠진 것이 원인이었다. LF·CR 판단을 복원한 뒤 `acceptance --rerun-tasks`에서 해당 사례를 포함한 업무 검사가 통과했다. 결과는 `build/test-results/acceptance/TEST-lab.export.ExportAcceptanceTest.xml`에 있다.

## Stop Hook과 업무 검사

`StopHook.main`은 표준입력에서 JSON 사건 정보를 받고 실행 인수로 기록 파일·프로젝트 루트·Windows 소켓 전용 경로를 받는다. `hook_event_name`이 `Stop`인지 확인한 뒤 `turn_id`와 `stop_hook_active`를 읽는다. 검사 실행부는 프로젝트 루트에서 Gradle `acceptance`를 실행하고, 실행마다 별도 `.local/harness/runs/<실행ID>/build`를 사용한다. 검사 프로세스는 120초로 제한하고, Windows에서는 짧은 로컬 소켓 경로를 자식 JVM의 `JAVA_TOOL_OPTIONS`에 병합한다.

판단부는 이번 실행 폴더의 `test-results/acceptance/TEST-*.xml`만 읽는다. 실제 업무 검사가 실행되고 XML에 실패가 없으며 Gradle이 성공하면 `{}`를 반환한다. 검사 실패에는 실패한 사례와 XML 위치를 경고로 표시하고 `continue: false`로 멈춘다. 시작 오류·시간 초과·새 XML 부재·검사 0건은 실행 불가로 구별해 경고하고 멈춘다. 수정 요청은 메인 에이전트가 담당하므로 Hook은 새 수정 요청을 만들지 않는다. `.local/harness/last-hook-event.json`에는 사건 정보와 마지막 검사 판정·결과 위치를 남긴다.

준비한 입력은 Git에서 제외한 `.local/harness/stop-input.json`에 둔다. 이 입력을 확장된 Java 진입점에 직접 전달한 실제 `acceptance` 실행에서는 새 XML에 업무 검사가 기록됐고 실패가 없었다. Hook 응답은 `{}`, 종료 코드는 0, 마지막 기록의 `check_status`는 `pass`였다. 준비한 결과를 사용하는 `StopHookTest`는 통과·기능 실패·이어진 턴의 실패·실행 불가·시간 초과·검사 0건을 Gradle 재귀 실행 없이 확인한다.

로컬 `.codex/hooks.json`의 `hooks.Stop`은 같은 Java 소스를 실행한다. Windows용 `commandWindows`는 이 작업 폴더의 Java 파일·기록 파일·프로젝트 루트·소켓 경로를 가리키며, Hook 제한 시간은 150초다. Codex CLI의 `/hooks`에서 변경된 정의를 다시 신뢰하고 짧은 답변을 마치자, 마지막 기록에 실제 `Stop` 턴 ID와 `check_status: pass`가 남았고 그 기록이 가리키는 새 XML에도 업무 검사 통과가 확인됐다.

## SessionStart Hook과 인계 문맥

`SessionStartHook.main`은 표준입력의 `hook_event_name`과 `source`를 확인한다. `startup` 또는 `resume`이면 실행 인수로 받은 프로젝트 루트에서 `docs/project.md`를 읽어 표준출력에 보낸다. `.local/harness/checkpoint.md`가 있으면 그 내용도 붙이고, 기록 당시의 내용이므로 현재 코드·검사와 대조해 남은 작업을 찾으라는 안내를 함께 보낸다. 메모가 없으면 프로젝트 설명만 보낸다. 프로젝트 설명을 읽지 못하면 Hook 실행이 실패해 자료 부재를 드러낸다.

로컬 `.codex/hooks.json`은 `hooks.SessionStart`와 기존 `hooks.Stop`을 함께 등록한다. 시작 Hook은 `startup|resume`에만 적용하고 Java 소스를 실행한다. `SessionStartHookTest`는 메모 없음·있음과 다른 사건 입력을 준비해 전달 문맥을 확인한다. 등록을 바꾼 뒤에는 실제 새 세션에서 Hook 정의를 검토·신뢰하고 시작 문맥이 사용되는지 별도로 확인한다.
