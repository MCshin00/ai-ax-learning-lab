# 개발 하네스 학습 노트

## Day 1 — 지침을 적용한 CSV 쉼표 처리

### 요구와 코드에서 지침 도출하기

요구는 문의 제목 `VPN, 접속`을 원문 그대로 CSV 한 셀에 보존하는 것이었다. 코드는 `TicketFilter`에서 OPEN 문의를 고르고, `CsvCell`에서 각 필드를 표현한 뒤, `TicketExport`에서 필드를 쉼표로 연결하는 구조였다. `CsvCell.encode`가 문자열을 그대로 반환했기 때문에 제목 안의 쉼표도 열 구분자로 해석될 수 있었다.

기존 업무 검사에는 `VPN 접속`처럼 특수문자가 없는 제목만 들어 있었다. 쉼표 처리가 빠져도 이 검사는 통과하므로, 새 요구를 확인하려면 `VPN, 접속`의 기대 셀 `"VPN, 접속"`을 검사에 추가해야 했다. 이 필요에서 “요구 사례의 입력과 기대 결과를 확인한다”, “변경한 조건을 업무 검사에 반영한다”는 지침을 도출했다.

[제공 지침 예제](https://github.com/MCshin00/ai-ax-learning-lab/blob/cdde0815ed00d23556600d7a6fb95cdee2debb20/week11-development-environment/development_harness/setup/AGENTS.example.md)를 참고하면서 코드·자료·검사 설정에 맞춰 다음 내용을 [AGENTS.md](development_harness/AGENTS.md)에 반영했다.

| 지침을 도출한 근거 | 작성한 지침 |
|---|---|
| 현재 기능과 파일 역할은 프로젝트 설명에, 요구별 입력과 기대값은 사례 JSON에 있다. | `docs/project.md`에서 현재 동작을 확인하고 `data/acceptance-cases.json`에서 해당 요구를 찾는다. |
| `TicketExport.csv`의 반환값은 예제 출력과 업무 검사에서 사용한다. 입력이나 결과가 바뀌면 이 코드들도 영향을 받는다. | 공개 입력·결과를 변경하면 호출부와 관련 검사를 함께 확인하고 수정한다. |
| 단순 제목 검사만으로는 쉼표 보존을 확인할 수 없다. | 변경한 요구의 입력을 업무 검사에 반영한다. |
| `acceptance`는 업무 검사를, `test`는 업무와 하네스 검사를 실행한다. | 업무 변경에는 `acceptance`를 사용하고, 하네스나 공통 빌드 설정이 바뀌면 `test`를 실행한다. |
| 셀 표현을 고친 뒤에도 설명에 “문자열을 그대로 반환한다”가 남으면 다음 변경이 잘못된 설명에서 시작된다. | 기능 변경으로 설명이 달라지면 해당 자료도 갱신한다. |

AGENTS.md에는 이처럼 반복할 행동과 자료 위치를 적었다. CSV 열 순서와 파일별 역할은 [프로젝트 설명](development_harness/docs/project.md)에, `VPN, 접속`과 기대 셀은 [사례 JSON](https://github.com/MCshin00/ai-ax-learning-lab/blob/cdde0815ed00d23556600d7a6fb95cdee2debb20/week11-development-environment/development_harness/data/acceptance-cases.json)에 두었다. 다른 입력을 처리할 때도 “사례를 찾아 검사한다”는 지침은 유지하고, 달라진 입력과 기대값은 JSON에서 확인할 수 있다.

### 지침이 실제 작업과 산출물에 반영된 과정

프로젝트 폴더의 새 대화에 다음 요청을 전달했다.

> 제목에 쉼표가 들어가도 원래 제목이 한 셀로 보존되도록 수정해 줘.

요청에는 업무 결과를 적었고, 자료 확인·검사·설명 갱신 절차는 AGENTS.md에 두었다. 실제 작업에서는 다음과 같이 지침을 적용했다.

| 적용한 지침 | 실제 수행한 작업 | 산출물에 반영된 내용 |
|---|---|---|
| `docs/project.md`에서 현재 기능과 파일 역할을 확인한다. | 설명과 코드에서 필터 → 셀 표현 → CSV 조합의 경로를 확인했다. | 쉼표 보존을 담당할 변경 위치를 `CsvCell.encode`로 정했다. |
| 해당 요구 사례의 입력·기대 결과·판단 이유를 확인한다. | CSV-COMMA의 `VPN, 접속`과 기대 셀 `"VPN, 접속"`을 읽었다. | `CsvCell.java`에 쉼표가 있는 값을 따옴표로 감싸는 처리를 추가하고 `ExportExample.java`의 입력을 사례에 맞췄다. |
| 변경한 요구를 업무 검사에 반영하고 `acceptance`로 확인한다. | 쉼표 제목 검사를 추가하고 기존 단순 제목 검사와 함께 실행했다. | `ExportAcceptanceTest.java`의 새 검사와 기존 검사가 통과했고, 실행 결과가 검사 보고서에 담겼다. |
| 기능 변경으로 설명이 달라지면 해당 자료도 갱신한다. | 셀 표현이 바뀐 부분을 프로젝트 설명에 반영했다. | `docs/project.md`의 “문자열을 그대로 반환한다”는 설명을 “쉼표가 있는 값은 큰따옴표로 감싼다”로 바꿨다. |

JSON의 `decision`에는 기존 단순 제목·OPEN 선택·열 순서도 함께 확인하도록 설명되어 있다. 이 조건을 테스트 입력과 기대 CSV에 반영했다. 지침이 자료를 찾고 검사하는 방법을 제공하고, 조회한 사례가 구체적인 구현 조건과 검사 기대값을 제공한 것이다.

### 셀 표현 변경과 실제 출력

[CsvCell.java](development_harness/src/main/java/lab/export/CsvCell.java)의 변경은 다음과 같다.

```java
public static String encode(String text) {
    return text.contains(",") ? "\"" + text + "\"" : text;
}
```

쉼표 보존은 한 필드를 어떻게 표현할지의 문제이므로 `CsvCell`에서 처리했다. `TicketFilter`는 내보낼 문의를 선택하고, `TicketExport`는 인코딩된 필드를 정해진 열 순서로 연결한다. 이 책임 분리 덕분에 제목 표현을 바꾸면서 OPEN 선택이나 CSV 조합 코드를 함께 고칠 필요가 없었다.

[ExportExample.java](development_harness/src/main/java/lab/export/ExportExample.java)의 제목을 `VPN, 접속`으로 맞춘 실제 CSV는 다음과 같았다.

```csv
id,team,status,title
T-1,OPS,OPEN,"VPN, 접속"
```

바깥 따옴표는 제목 안의 쉼표가 셀의 일부임을 나타낸다. 원문 제목의 쉼표와 공백은 그대로 남는다.

### 입력 검사와 보고서

[ExportAcceptanceTest.java](development_harness/src/test/java/lab/export/ExportAcceptanceTest.java)에 `commaInTitleStaysInOneCell`을 추가했다. 쉼표 제목의 OPEN 문의, 단순 제목의 OPEN 문의, CLOSED 문의를 함께 입력하고 다음 CSV와 비교한다.

```csv
id,team,status,title
T-1,OPS,OPEN,"VPN, 접속"
T-2,APP,OPEN,로그인
```

전체 문자열을 비교하므로 쉼표 제목의 셀 표현뿐 아니라 단순 제목 유지, CLOSED 제외, 헤더와 열 순서, 문의 순서도 확인한다. 추가 검사와 기존 `simpleOpenTickets`가 `acceptance`에서 통과했다. 실제 결과는 [HTML 검사 보고서](development_harness/build/reports/tests/acceptance/index.html)에 있다.

사례 JSON의 기대값은 테스트 코드의 `assertEquals`에 반영됐다. JUnit이 기대 CSV와 실제 CSV를 비교하고, Gradle의 `acceptance` 작업이 실행 결과를 HTML과 XML 보고서로 생성했다. 따라서 보고서의 통과 항목은 실제 테스트 코드에 작성한 입력과 비교 조건을 근거로 읽어야 한다.

## Day 2 — 자연어 개발 요청에 Skill 적용하기

### 반복할 개발 절차를 Skill에 담기

쉼표 보존 작업에서는 현재 처리 경로를 찾고, 요구와 다른 부분을 고른 뒤, 새 조건과 기존 동작을 검사했다. 이 판단을 다음 문의 내보내기 변경에도 사용하도록 [export-change Skill](development_harness/.agents/skills/export-change/SKILL.md)을 구성했다.

`description`에는 적용할 요청을 “문의 내보내기 기능의 개발·변경 요청”으로 적었다. 본문은 요구와 현재 처리 확인 → 검사 입력과 구현 → 실행과 결과 해석으로 구성했다. [AGENTS.md](development_harness/AGENTS.md)의 공통 작업 원칙과 검사 기준을 따르고, [프로젝트 설명](development_harness/docs/project.md)에서 현재 기능과 파일 역할을 확인하도록 연결했다. 구체적인 입력과 기대값은 각 요청의 업무 조건에서 도출해 검사 코드에 담는다.

### 초기 테스트 — 큰따옴표 보존

Skill을 지정하고 자료 확인과 검사 조건을 함께 제시한 요청으로 큰따옴표 보존을 테스트했다. `CsvCell.encode`에서 원문 안의 큰따옴표를 두 번 표현한 뒤 셀 전체를 감싸도록 수정했다. 큰따옴표만 있는 제목과 쉼표·큰따옴표가 함께 있는 제목의 검사, 기존 단순 제목·쉼표 제목 검사가 통과했다. 예제 실행에서도 `로그인 "오류"`는 `"로그인 ""오류"""`, `VPN, "접속"`은 `"VPN, ""접속"""`로 출력됐다.

### 본 실습 — 줄바꿈 보존 요구에서 Skill 선택하기

새 대화에는 다음 업무 요구만 전달했다.

> 줄바꿈이 들어 있는 문의 제목도 원문 그대로 CSV 한 셀에 보존하도록 수정해 줘.

이 요청은 내보내기 기능의 변경이므로 Skill의 적용 범위와 맞는다. 실제 응답에서 `export-change` 적용을 알렸고, Skill 본문과 프로젝트 설명·코드·기존 검사를 확인했다. 요청에 자료 위치와 개발 절차를 다시 적지 않아도 저장해 둔 Skill이 이번 작업에 선택됐다.

요구의 핵심은 제목의 줄바꿈 문자를 유지하면서 CSV에서는 한 셀로 표현하는 것이다. 줄바꿈을 공백으로 바꾸거나 제거하면 원문 보존 조건을 어긴다. 따라서 원문에 있는 줄바꿈은 그대로 두고 셀 전체를 큰따옴표로 감싸는 처리가 필요했다.

### Skill의 지시에서 변경 위치 판단으로

Skill에는 “입력부터 출력까지 호출을 따라가며 요구와 현재 동작의 차이를 찾는다”는 지시가 있다. 당시 확인한 [TicketExport.csv](development_harness/src/main/java/lab/export/TicketExport.java)는 OPEN 문의를 선택한 뒤 각 필드를 `CsvCell.encode`에 전달하고, 인코딩된 필드를 쉼표로, 문의 행을 LF로 연결한다.

이때 제목 안의 LF와 문의 행 사이의 LF는 역할이 다르다. 제목 `첫 줄\n다음 줄`이 감싸지지 않은 채 출력되면 제목의 줄바꿈도 다음 레코드의 시작으로 해석될 수 있다. 셀 전체를 감싸면 CSV를 읽는 쪽에서 따옴표 안의 줄바꿈을 셀 내용으로 구별할 수 있다.

기존 `CsvCell.encode`의 감싸기 조건은 `text.contains(",") || text.contains("\"")`였다. 줄바꿈만 있는 제목은 두 조건 모두 만족하지 않아 그대로 반환됐다. 이 차이에서 변경 위치를 셀 표현을 담당하는 `CsvCell`로 정했다. 현재 구조에서 `CsvCell`은 각 필드를 CSV에 넣을 수 있는 문자열로 표현하는 책임을 맡는다. 이곳에서 셀 경계를 처리하면 문의 선택과 행 조합이 인코딩된 결과를 그대로 사용할 수 있다.

### 원문 보존 조건에서 검사 입력 도출하기

Skill의 “업무 요구와 선택한 처리 규칙에서 작은 입력과 기대 결과를 도출한다”는 절차는 줄바꿈 형태를 구별하는 입력으로 이어졌다. 최초 검사에는 LF와 CRLF 제목을 넣었고, 구현을 수정하면서 CR 제목도 추가했다. 아래 표의 `\n`과 `\r`은 각각 실제 LF와 CR 문자를 표시한 것이다.

| 제목 원문 | 기대하는 CSV 셀 표현 |
|---|---|
| `첫 줄\n다음 줄` | `"첫 줄\n다음 줄"` |
| `첫 줄\r\n다음 줄` | `"첫 줄\r\n다음 줄"` |
| `첫 줄\r다음 줄` | `"첫 줄\r다음 줄"` |

LF만 검사하면 CR만 있는 제목의 감싸기가 빠져도 발견하기 어렵다. CRLF를 함께 검사하면 두 문자를 LF 하나로 바꾸는 처리도 원문 보존에 어긋난다는 것을 확인할 수 있다. 입력을 나눈 이유는 줄바꿈의 존재뿐 아니라 실제 문자가 유지되는지까지 구별하기 위해서다.

“새 조건을 구별하는 입력과 변경의 영향을 받는 기존 입력을 검사에 담는다”는 절차에 따라 단순 제목·쉼표 제목·큰따옴표 제목의 기존 검사도 함께 실행했다. 같은 `CsvCell`을 수정하므로 줄바꿈 입력의 성공과 기존 CSV 표현의 유지 여부를 함께 확인하는 구성이다.

### 실패한 검사를 근거로 수정하고 재확인하기

구현을 바꾸기 전에 추가한 `lineBreaksInTitleStayInOneCell`은 실제 실행에서 실패했다. 당시 검사에는 LF·CRLF 제목이 있었고 기대 CSV는 두 제목을 감싸도록 작성돼 있었다. 기존 코드는 두 입력을 감싸지 않았으므로 요구와 구현의 차이가 검사 실패로 드러났다.

[CsvCell.encode](development_harness/src/main/java/lab/export/CsvCell.java)의 수정은 다음과 같다.

```java
String escaped = text.replace("\"", "\"\"");
return text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
    ? "\"" + escaped + "\"" : escaped;
```

감싸기 조건에 LF와 CR을 추가했다. CRLF는 두 문자를 포함하므로 이 조건으로 함께 처리된다. 값의 큰따옴표를 두 번 표현하는 기존 처리를 거친 뒤 셀을 감싸며, 줄바꿈 문자는 치환하지 않는다. 원문의 문자 보존과 CSV의 셀 경계 표현을 함께 충족하는 방식이다.

수정 후 [업무 검사](development_harness/src/test/java/lab/export/ExportAcceptanceTest.java)는 LF·CRLF·CR 문의를 한 목록으로 전달하고, 헤더·셀 표현·문의 사이의 LF를 포함한 전체 CSV 문자열을 비교했다. 실제 `TicketExport.csv` 반환값이 기대 문자열과 일치해 새 검사와 기존 검사가 `acceptance`에서 통과했다. 세부 결과는 [검사 보고서](development_harness/build/reports/tests/acceptance/index.html)에 있다.

검사에서 확인한 첫 문의의 CSV 표현은 `T-1,OPS,OPEN,"첫 줄\n다음 줄"`이다. 여기서 `\n`은 실제 LF이며, 두 번째와 세 번째 문의도 각각 CRLF와 CR을 유지한 셀로 비교됐다. 줄바꿈 제목의 실제 결과는 이 반환값 비교로 확인했다.

[프로젝트 설명](development_harness/docs/project.md)에는 LF·CRLF·CR이 감싸기 대상이라는 점과 원문 줄바꿈을 유지한다는 규칙, 추가된 업무 검사 범위를 반영했다. 다음 변경에서도 설명과 코드가 같은 동작을 가리키도록 한 것이다.

### 지침과 Skill의 역할 분담

`AGENTS.md`는 업무 기능을 바꾸면 관련 검사를 추가하고 `acceptance`로 확인한다는 공통 기준을 제공했다. `export-change` Skill은 문의 내보내기 요청에서 현재 처리 경로를 찾고, 요구와 다른 부분을 골라 변경 위치와 검사 입력을 정하는 절차를 제공했다. 프로젝트 설명과 코드는 이 판단에 필요한 현재 동작을 보여 주고, 검사 결과는 구현이 요구를 충족하는지 확인하는 근거가 됐다.

줄바꿈 요구에서는 이 구성을 통해 셀 감싸기 조건의 누락을 찾고, 줄바꿈 형태별 입력을 검사에 담아 수정 전 실패와 수정 후 통과를 확인했다. Skill 본문을 바꾸지 않고 후속 변경에 적용할 수 있었고, 달라진 업무 동작은 코드·검사·프로젝트 설명에 반영됐다. 공통 기준은 지침에, 특정 개발 요청에서 반복할 판단 절차는 Skill에 두는 역할 분담을 실제 변경에서 확인했다.

## 개발 MCP 적용 구상 — 외부 이슈의 요구를 개발에 연결하기

현재 작업에서는 요청이 대화에 있고 코드·설명·검사가 프로젝트 안에 있어 파일 읽기와 실행 도구로 필요한 정보를 확보했다. 자료가 이슈 서비스나 문서 서비스에서 관리된다면, 개발 AI가 그 원문을 가져오는 도구를 연결할 수 있다. MCP는 Host가 서버에서 제공하는 도구와 자료를 발견하고 사용하는 공통 연결 방식이다. 서버는 로컬이나 원격에서 실행될 수 있으므로, 도입 판단은 자료의 물리적 위치뿐 아니라 기존 도구가 필요한 기능을 제공하는지와 공통 연결의 필요성에 달려 있다. [MCP 구조 설명](https://modelcontextprotocol.io/docs/learn/architecture)

다음은 외부 서비스 연결을 가정한 설계 예시다. 문의 내보내기 앱의 팀 선택·날짜 파일 이름 변경이 이슈 서비스에 등록되어 있고, 요청은 “이슈 42의 내보내기 기능을 반영해 줘”라고 들어온다. 이슈 본문에는 팀 지정 기능과 날짜 포함 파일 이름이 적혀 있고, 댓글에는 “지난 날짜의 자료를 다시 내려받을 수 있어야 한다”는 추가 조건이 있다고 가정한다.

필요한 기능은 이슈 본문과 관련 논의를 가져오는 조회다. 도구 이름을 예로 들면 `get_issue(project, issue_number)`에 프로젝트 식별자와 `42`를 전달한다. 반환에는 제목·본문·상태·원문 링크·수정 시각을 담고, 댓글이 별도 도구라면 `list_issue_comments`로 후속 논의를 가져온다. 이 이름과 구성은 설계 예시이며 실제 연결에서는 선택한 서버의 도구와 입출력에 맞춘다. MCP는 실행 가능한 도구와 문서 같은 자료를 공개할 수 있다. [MCP 서버 기능 설명](https://modelcontextprotocol.io/docs/learn/server-concepts)

반환된 내용은 Skill의 “요구와 현재 처리 확인” 단계에서 사용한다. 이슈 본문의 기능과 댓글의 추가 조건을 현재 `TicketFilter`·`ExportFilename`과 대조한다. 지난 날짜를 지정해야 한다는 조건에서는 날짜를 호출 입력으로 받는 방식을 추천할 수 있다. 팀을 지정하지 않았을 때의 동작이나 날짜 형식이 정해져 있지 않으면 결과 차이를 설명하고 그 조건을 결정한다. 논의 중인 제안과 확정된 조건이 섞여 있거나 서로 충돌하면 원문 근거를 함께 제시해 확인한다.

그 뒤 선택한 조건에서 검사 입력과 기대 결과를 만든다. 예를 들어 호출자가 지정한 날짜를 사용하는 것으로 정했다면, 가상의 날짜 `2026-01-15`를 넣어 실행일과 무관하게 정한 파일 이름이 나오는지 검사할 수 있다. 외부 이슈는 업무 요구를 제공하고, 구체적인 기대값은 그 요구·선택한 규칙·현재 코드에서 도출한다. 구현과 검사 이후 확정된 조건과 이슈 링크를 프로젝트 설명에 연결한다.

이 흐름에서 Skill은 언제 무엇을 조회하고 결과를 어떤 판단에 쓸지 안내한다. Host의 MCP 클라이언트가 도구 호출을 전달하고, 서버는 이슈 서비스의 API에 접근해 결과를 반환한다. 서비스 주소·인증·프로젝트 접근 범위는 연결 설정과 서버에서 처리한다. 조회 실패나 접근 불가를 “추가 조건 없음”으로 해석하지 않고, 필요한 원문을 확보한 뒤 해당 조건을 구현에 반영한다.

연결을 도입할 때는 기존 이슈 연동이나 MCP 서버가 필요한 본문·댓글 조회를 제공하는지 먼저 확인한다. 이미 사용할 수 있는 도구로 충분하면 그 도구를 Skill에서 활용할 수 있다. 자체 서버는 필요한 사내 API나 조회 기능이 제공되지 않을 때 검토한다. 대표 이슈를 실제로 조회해 원문과 반환 내용을 대조하고, 그 내용이 변경 위치와 검사 입력 선택에 쓰이는지 확인하는 것이 연결 검증이다.

다른 적용 위치로는 외부 API 명세 조회와 별도 빌드 환경의 검사 실행이 있다. 명세 조회는 호출 입력·응답·오류 처리 결정에 사용한다. 원격 검사는 대상 코드 버전과 허용된 검사 작업을 보내고 실행 ID를 받아 상태·결과를 조회하도록 설계할 수 있다. 결과를 현재 변경과 연결하려면 실행 ID와 코드 버전을 함께 확인해야 한다. 자료 조회와 달리 실행을 시작하는 도구는 실행 대상·허용 작업과 중복 실행 처리도 정해야 한다.

## Day 3 — Hook으로 종료 검사와 수정 요청 연결하기

### Hook의 개념과 이번에 맡긴 역할

Hook은 프로그램이 정해 둔 사건이 발생할 때 등록한 처리를 실행하는 연결 방식이다. 예를 들어 답변 종료 시점에 검사를 연결하면, 그 시점마다 등록한 검사 프로그램이 실행된다. 이번에 사용한 Codex의 `Stop`은 한 번의 답변을 마치려는 시점이다. CLI 창을 닫거나 전체 세션을 종료하는 사건과는 구별된다. Codex가 사건을 감지하고 등록된 명령을 실행하는 부분을 제공하며, 어떤 검사를 하고 어떤 결과를 돌려줄지는 프로젝트에서 작성한다. [Codex Hook 공식 문서](https://learn.chatgpt.com/docs/hooks)

문의 내보내기 개발에서는 변경 후 기대 CSV가 나오는지 확인해야 한다. 지침과 Skill은 요구를 읽고 구현·검사하는 작업 절차를 안내한다. 여기에 종료 Hook을 연결해, 답변을 마치려는 시점에도 실제 업무 검사를 실행하고 실패가 있으면 수정 근거를 돌려주도록 했다. 검사를 실행할 시점, 결과를 읽는 코드, 결과에 따른 다음 행동을 연결한 것이다.

이 단계에서 만든 Hook은 CSV 업무 검사 실행과 결과 판단, 종료 또는 수정 요청의 선택을 맡았다. CSV의 올바른 출력은 [ExportAcceptanceTest.java](development_harness/src/test/java/lab/export/ExportAcceptanceTest.java)의 입력·기대값으로 정한다. Hook은 그 검사의 결과를 읽으며, 실패 원인을 해석하고 업무 코드를 고치는 작업은 피드백을 받은 Codex가 수행한다.

### 등록 설정에서 Java 프로그램까지

프로젝트의 로컬 `.codex/hooks.json`에는 `hooks.Stop` 아래에 `type: "command"`와 Java 실행 명령을 등록했다. Windows용 `commandWindows`는 [StopHook.java](development_harness/src/main/java/lab/harness/StopHook.java) 소스를 실행하고 기록 파일과 프로젝트 폴더 등의 인수를 전달한다. 이 설정은 “언제 어떤 프로그램을 실행할지”를 지정한다. Codex CLI의 `/hooks`에서 명령을 검토·신뢰하고 활성화한 뒤 실제 종료 시점의 실행을 확인했다.

Codex는 Java 프로그램의 표준입력으로 사건 정보를 JSON 형태로 보낸다. 표준입력은 실행 중인 프로그램에 데이터를 전달하는 통로다. `StopHook.main`의 `System.in.readAllBytes()`가 이 데이터를 읽는다. 다음은 이번 구현에서 사용하는 필드만 남기고 턴 ID를 예시로 바꾼 입력이다.

```json
{
  "hook_event_name": "Stop",
  "turn_id": "<현재 턴 ID>",
  "stop_hook_active": false
}
```

`process`는 사건 이름이 `Stop`인지 확인하고 턴 ID와 `stop_hook_active`를 읽는다. 마지막 필드는 이번 턴이 Stop Hook의 요청으로 이미 이어진 상태인지 나타낸다. 당시 구현에서는 첫 실패에 수정을 요청할지, 수정 요청 후에도 실패가 남아 자동 반복을 멈출지 판단하는 데 사용했다.

처음에는 이 입력을 받아 마지막 사건을 기록하고 `{}`를 반환하는 작은 프로그램으로 시작했다. 준비한 JSON을 직접 전달해 입력·기록·출력을 확인한 뒤 Codex에 연결했다. 실제 답변 종료에서 기록의 턴 ID가 갱신되면서 Codex가 Java 프로그램을 실행하고 사건 정보를 전달하는 연결을 확인했다. 이후 같은 진입점에 업무 검사 실행과 결과 판단을 붙였다.

### 검사 실행·판단·응답을 나눈 구조

이때 만든 `StopHook.java`의 처리 경로는 다음과 같다.

```text
main: Codex가 보낸 사건 JSON 읽기
  → process: 사건 확인, 이번 실행의 결과 폴더 준비
  → GradleRunner.run: acceptance 실행
  → judge: 이번 실행의 XML과 프로세스 결과로 판정
  → responseFor: 판정과 stop_hook_active로 응답 선택
  → main: 응답 JSON을 표준출력으로 반환
```

`GradleRunner.run`은 프로젝트 폴더에서 `acceptance --rerun-tasks`를 실행한다. `acceptance`가 업무 검사를 선택하고, JUnit이 기대 CSV와 실제 CSV를 비교하며, Gradle이 결과를 XML로 저장한다. 실행 명령에 `courseBuildDir`를 지정해 결과를 `.local/harness/runs/<실행ID>/build` 아래에 만든다. 이전 검사가 통과한 XML이 남아 있어도 이번 판정에는 이번 실행의 XML만 들어가게 하는 선택이다.

`judge`는 새 XML에 실제 실행된 검사가 있는지, 실패가 있는지, Gradle 실행이 성공했는지를 함께 읽는다. 단순히 프로세스가 실패했다는 사실만으로는 기대값 불일치와 검사 실행 불가를 구별할 수 없기 때문이다. 검사 시작 실패·시간 초과·새 결과 없음·실행된 검사 없음은 실행 불가로 분류한다. 실행된 검사의 실패에는 사례 이름과 실패 메시지, 결과 파일 위치를 모아 수정 근거로 사용한다.

검사 프로세스의 제한 시간은 120초, 이를 감싸는 Hook의 제한 시간은 150초로 정했다. 내부 검사가 시간을 넘기면 프로세스를 정리하고 실행 불가 응답을 보낼 여유를 둔 구성이다. 이 시간과 검사 명령은 프로젝트의 실행 비용에 맞춘 선택이며, 검사가 오래 걸리거나 확인할 대상이 바뀌면 함께 조정할 부분이다.

실행부를 `Runner`로 분리한 덕분에 [StopHookTest.java](development_harness/src/test/java/lab/harness/StopHookTest.java)에서는 실제 Gradle 대신 준비한 종료 결과와 XML을 돌려주는 대역을 넣을 수 있었다. Hook 자체를 검사하면서 Gradle이 다시 자기 검사를 실행하는 반복을 피하고, 통과·첫 실패·재실패·실행 불가에 맞는 응답을 확인했다. 실제 Codex 연결에서는 통과 경로와 실패 전달 후 수정·통과 경로를 확인했다.

### 검사 결과가 다음 행동으로 바뀌는 방식

당시 `responseFor`는 검사 통과에 `{}`를 반환했다. 첫 검사 실패이고 `stop_hook_active`가 `false`이면 `decision: "block"`과 `reason`을 반환한다. 다음은 실제 응답 구조에서 긴 실패 메시지를 줄인 설명용 예다.

```json
{
  "decision": "block",
  "reason": "CSV 업무 검사 실패: 줄바꿈 제목의 기대값과 실제값이 다릅니다. 결과 파일을 읽고 수정한 뒤 다시 검사해 주세요."
}
```

Stop의 `block`은 답변 종료를 보류하고 `reason`을 후속 입력으로 전달해 Codex가 작업을 이어가게 한다. 실제 구현의 `reason`에는 실패한 검사 이름, 기대값·실제값이 포함된 실패 메시지와 XML 위치가 담긴다. 결과 파일 위치를 함께 주므로 요약만으로 부족하면 원문 결과를 읽을 수 있다.

수정 요청 뒤의 Stop에서는 `stop_hook_active`가 `true`이다. 이때도 실패하면 `continue: false`와 경고를 반환해 추가 자동 수정을 멈춘다. 검사 실행 불가도 경고와 함께 중단한다. 한 번의 수정 기회로 시작한 이유는 같은 실패로 작업이 계속 반복되는 것을 막기 위해서다. 더 많은 재시도를 허용하려면 이 불리언 외에 턴별 횟수와 중단 조건을 관리해야 한다.

응답은 `System.out.print(response)`로 표준출력에 쓰고 Codex가 읽는다. 검사 로그는 별도 `gradle.log`로 보내 응답 JSON에 섞이지 않게 했다. `.local/harness/last-hook-event.json`은 마지막 사건·판정·결과 위치를 남기는 확인용 기록이다. 이 파일이 갱신되어 수정을 시작하는 것이 아니라, 표준출력으로 돌아온 Hook 응답이 Codex의 다음 행동을 결정한다.

### 실제 줄바꿈 실패에서 수정과 재검사까지

실패 전달을 확인하기 위해 정상 구현을 보존하고 `CsvCell.encode`의 감싸기 조건에서 LF·CR 판단만 잠시 제거했다. 기존 줄바꿈 검사에는 LF·CRLF·CR 제목이 있었고, `acceptance`에서 해당 사례가 실패했다. 예를 들어 LF 입력의 차이는 다음과 같았다. 아래 `\n`은 실제 줄바꿈을 읽기 쉽게 표시한 것이다.

```text
제목: 첫 줄\n다음 줄
기대 CSV 행: T-1,OPS,OPEN,"첫 줄\n다음 줄"
실제 CSV 행: T-1,OPS,OPEN,첫 줄\n다음 줄
```

줄바꿈 문자는 보존됐지만 셀 전체를 감싸는 큰따옴표가 빠졌다. 쉼표와 큰따옴표 제목 검사는 통과했다. 이 차이는 원문의 줄바꿈을 없애야 한다는 뜻이 아니라, 줄바꿈도 셀을 감싸는 조건에 포함해야 한다는 근거다.

Hook을 활성화한 새 세션에 “현재 변경을 짧게 요약하고 응답을 마쳐 줘. Hook이 실패 근거를 전달하면 그 결과로 코드를 수정하고 재검사해 줘”라고 요청했다. 첫 답변은 실패 상태를 요약하고 끝나려 했고, 이어서 실제 Hook 피드백이 들어오면서 작업이 계속됐다. 이 과정은 [종료 Hook 실패 전달 확인](thread://01a0eddd-e36a-7273-823f-cbb86cea0ffd?hostId=local) 대화에 남아 있다.

첫 Stop에서 실행된 [실패 XML](development_harness/.local/harness/runs/69064017-a2b2-4496-889d-1b82537937ef/build/test-results/acceptance/TEST-lab.export.ExportAcceptanceTest.xml)에는 `lineBreaksInTitleStayInOneCell()`의 기대값과 실제값 차이가 기록됐다. Hook이 전달한 결과 위치를 읽은 뒤 [CsvCell.java](development_harness/src/main/java/lab/export/CsvCell.java)의 감싸기 조건에 다음 부분이 복원됐다.

```java
text.contains("\n") || text.contains("\r")
```

이 조건은 LF와 CR을 검사하므로 LF·CRLF·CR 제목을 모두 포함한다. 셀의 원문을 보존한 채 전체를 큰따옴표로 감싸는 동작이 돌아왔고, 이어 실행한 `acceptance`에서 줄바꿈과 기존 사례가 통과했다.

마지막 답변에서도 Stop Hook이 다시 실행됐다. [마지막 사건 기록](development_harness/.local/harness/last-hook-event.json)에 같은 턴의 `stop_hook_active: true`와 `check_status: "pass"`가 남았고, 그 기록이 가리킨 [새 Hook 검사 XML](development_harness/.local/harness/runs/3f747083-11e3-49b1-8292-2be31b2513aa/build/test-results/acceptance/TEST-lab.export.ExportAcceptanceTest.xml)에서도 통과가 확인됐다. 따라서 수정 과정에서 실행한 검사에 더해, 다음 종료 시도의 Hook 검사까지 통과한 것이다.

이 사례에서 종료 시점의 검사와 다음 행동을 연결하자, 실패한 상태로 답변을 마치려던 흐름이 실패 근거 확인·코드 수정·재검사로 이어졌다. 검사할 업무 조건은 테스트에, 실행과 응답 정책은 Hook 프로그램에, 수정 판단은 Codex에 나뉘어 있다. 이후 다른 CSV 요구가 추가되면 업무 검사에 입력과 기대값을 더하고 같은 종료 검사 흐름을 사용할 수 있다. 검사에 없는 요구까지 확인하려면 먼저 그 요구를 검사 조건에 반영해야 한다.

## Day 4 — 작업 분담과 시작 문맥으로 새 세션에서 통합하기

### 업무 조건에서 분담 경계 정하기

요구는 지정한 팀의 OPEN 문의를 내보내고, 파일 이름에 날짜를 넣는 것이었다. 팀 미지정은 모든 팀의 OPEN 문의를 포함하고, 팀 지정은 정확히 일치하는 팀만 포함하도록 정했다. 빈 팀명은 잘못된 입력으로 처리하고, 선택 결과가 없으면 헤더만 반환한다. 날짜는 호출자가 전달하며 파일 이름은 `tickets-YYYY-MM-DD.csv`로 만든다. 날짜를 입력으로 받으면 실행 시각과 무관하게 기대 이름을 정할 수 있고, 지정한 날짜의 파일 이름을 다시 만들 수 있다.

기존 코드에서는 [TicketFilter.java](development_harness/src/main/java/lab/export/TicketFilter.java)가 내보낼 문의를 선택하고, [ExportFilename.java](development_harness/src/main/java/lab/export/ExportFilename.java)가 파일 이름을 만든다. 두 기능은 서로 다른 입력과 결과를 다루므로 각각 구현하고 전용 검사로 확인할 수 있었다. 반면 [TicketExport.java](development_harness/src/main/java/lab/export/TicketExport.java)는 팀 선택 결과를 CSV에 연결해야 하고, [ExportExample.java](development_harness/src/main/java/lab/export/ExportExample.java)는 팀과 날짜를 전달해야 했다. 이 호출 관계를 근거로 기능별 작업과 통합 작업을 나눴다.

분담 중에는 기존 호출을 사용할 수 있도록 한 인수 팀 선택 메서드와 날짜 없는 파일 이름 메서드를 남겼다. 새 입력을 받는 메서드를 추가하면 각 기능을 검사하는 동안 기존 호출부도 실행할 수 있다. 통합 단계에서 기존 CSV 호출은 팀 미지정 경로로 연결하고, 예제의 날짜 없는 파일 이름 호출과 해당 메서드는 제거했다.

### Worktree와 서브에이전트의 실제 역할

Worktree는 같은 Git 저장소의 코드를 별도 폴더에서 수정할 수 있는 작업 공간이다. 서브에이전트는 맡은 범위의 코드를 읽고 구현·검사하는 실행 주체다. 이번에는 팀 선택을 별도 Worktree의 서브에이전트에 맡기고, 주 작업 공간에서 날짜 파일 이름을 구현했다. 파일 공간을 나누는 일과 구현할 일을 맡기는 일을 함께 사용한 것이다.

`ticket-team-filter` Worktree는 기본 브랜치의 이전 코드에서 시작했다. 현재 주 작업 공간에 있던 큰따옴표·줄바꿈 처리와 코드·지침·Skill·검사 설정을 필요한 파일 단위로 반영해 팀 작업의 기준을 맞췄다. Git에서 제외한 `.codex`와 `.local` 자료도 따로 준비하고, Hook 명령의 프로젝트 경로를 그 Worktree에 맞췄다. 작업 폴더를 분리하면 코드뿐 아니라 자료를 읽고 검사를 실행할 위치도 그 폴더와 연결해야 한다.

팀 선택 작업의 담당 파일은 `TicketFilter.java`와 `TicketFilterTest.java`였다. `select(tickets, team)`에서 `null`은 팀 미지정, 빈 문자열은 오류로 처리하고, 지정한 팀과 OPEN 상태가 모두 일치하는 문의를 입력 순서대로 선택했다. 서브에이전트의 시작·완료 기록과 Worktree의 코드·검사 결과에서 이 작업을 확인했다.

주 작업 공간에서는 `ExportFilename.name(LocalDate)`와 `ExportFilenameTest.java`를 구현했다. 팀 선택 Worktree와 주 작업 공간에서 각각 `acceptance`가 통과했고, 각 폴더 아래에 검사 결과가 생성됐다. 이때 팀 선택은 아직 주 작업 공간의 CSV 호출에 연결되지 않았고, 예제도 날짜 없는 파일 이름을 사용했다. 개별 기능과 전용 검사를 끝내고 호출부 통합을 남긴 지점을 인계 시점으로 삼았다. 분담과 결과 위치는 [문의 내보내기 변경 현황 확인](thread://01a0edfc-561d-7e23-b65a-a7063f4d956f?hostId=local) 대화에서 확인할 수 있다.

### SessionStart로 시작 정보를 전달하는 구현

상시 작업 원칙은 `AGENTS.md`에 두고, 현재 기능·검사 위치는 `docs/project.md`, 진행 중인 작업의 상태는 `.local/harness/checkpoint.md`에 두었다. 새 세션이 같은 업무를 이어가려면 현재 프로젝트 구조와 함께 어느 결과가 어느 작업 공간에 있고 무엇을 연결해야 하는지 알아야 한다. 이 정보를 시작할 때 전달하도록 `SessionStart` Hook을 추가했다.

로컬 `.codex/hooks.json`의 `SessionStart`에 Java 실행 명령을 등록하고 `startup`과 `resume` 사건에 맞도록 설정했다. [SessionStartHook.java](development_harness/src/main/java/lab/harness/SessionStartHook.java)의 처리 경로는 다음과 같다.

```text
Codex가 세션 시작 사건을 표준입력으로 전달
  → main이 사건 JSON과 프로젝트 폴더 인수를 읽음
  → context가 SessionStart와 startup·resume인지 확인
  → docs/project.md 읽기
  → checkpoint.md가 있으면 내용과 현재 코드 대조 안내 추가
  → main이 완성한 텍스트를 표준출력으로 반환
  → Codex가 그 내용을 새 세션의 시작 문맥에 추가
```

`context`는 프로젝트 설명을 읽은 뒤 `Files.isRegularFile(checkpoint)`로 인계 메모의 존재를 확인한다. 메모가 없으면 프로젝트 설명만 반환하고, 있으면 내용을 이어 붙인다. 시작 Hook은 파일의 내용을 전달하며, 남은 작업의 판단은 이를 받은 세션에서 한다. 인계 메모가 작성된 뒤 코드가 바뀔 수 있어 다음 안내도 함께 전달한다.

> 인계 메모는 작업 당시의 기록입니다. 현재 코드와 검사 결과를 대조해 남은 작업을 찾으세요.

`main`의 `System.out.print(context(...))`가 반환 통로다. SessionStart는 이 표준출력 텍스트를 시작 문맥으로 받을 수 있다. Day 3의 Stop 응답이 종료·수정 요청을 선택했다면, 이번 시작 응답에는 작업을 이어가는 데 필요한 설명과 상태를 담았다. [Codex Hook 공식 문서](https://learn.chatgpt.com/docs/hooks#sessionstart)

[SessionStartHookTest.java](development_harness/src/test/java/lab/harness/SessionStartHookTest.java)에서 메모 없음·있음에 따른 전달 내용과 다른 사건의 거부를 검사했고, 준비한 시작 사건을 Java 진입점에 전달해 출력도 확인했다. 실제 새 세션의 시작 문맥에도 프로젝트 설명·인계 메모·현재 코드 대조 안내가 들어왔다. 파일을 읽어 문자열을 만드는 처리와 Codex가 그 문자열을 받아 사용하는 연결을 각각 확인한 것이다.

### 인계 정보로 남은 작업을 찾아 통합하기

통합 전 인계 메모에는 확정된 팀·날짜 조건, 두 작업 공간의 결과 위치, 변경 파일, 완료한 검사와 남은 호출부 연결을 담았다. 팀 선택은 Worktree의 `TicketFilter.java`와 `TicketFilterTest.java`에 있고, 날짜 파일 이름은 주 작업 공간에 있다는 구분이 통합할 결과를 찾는 근거가 됐다. Worktree에는 준비 과정에서 옮긴 기존 파일도 있었으므로 팀 선택 결과 두 파일을 통합 대상으로 지정했다.

주 작업 공간의 새 세션에는 다음 요청을 보냈다.

> 진행 중인 문의 내보내기 변경을 이어서 마무리해 줘.

[문의 내보내기 변경 마무리](thread://01a0ee08-c8a0-7420-9196-df14ac88ca21?hostId=local) 세션의 시작 문맥에 통합 전 메모와 Worktree 위치가 전달됐다. 현재 코드와 대조하니 주 작업 공간의 `TicketFilter`는 OPEN만 선택했고, `TicketExport`는 팀 입력을 받지 않았다. 날짜 메서드는 있었지만 `ExportExample`은 날짜 없는 호출을 사용했다. 메모에 적힌 “기능별 작업 완료, 통합 전” 상태가 실제 호출 경로와 일치했다.

이어서 별도 Worktree를 작업 폴더로 지정해 팀 선택 코드와 전용 검사를 읽고 주 작업 공간에 반영했다. `TicketExport.csv(tickets, team)`을 추가해 선택 결과를 CSV로 조합하고, 기존 한 인수 호출은 팀 미지정 호출로 연결했다. 선택 결과가 없을 때는 헤더 뒤의 LF까지 남던 처리를 헤더만 반환하도록 바꿨다. 예제에는 팀과 날짜를 전달하고 전체 CSV 검사에 통합 조건을 추가했다.

인계 메모의 결과 위치는 가져올 코드를, 남은 작업 설명은 수정할 호출부와 검사를 찾는 데 쓰였다. 시작 Hook이 이 정보를 전달하고 새 세션에서 현재 파일과 대조하면서, 상세한 작업 지시를 다시 입력하지 않아도 중간 상태에서 통합을 이어갈 수 있었다.

### 통합된 입력과 실제 결과

전체 CSV 검사에 OPS OPEN, APP OPEN, OPS CLOSED, 소문자 ops OPEN, 다시 OPS OPEN을 섞었다. OPS 지정 시 기대 결과는 두 OPS OPEN 문의만 입력 순서대로 남는 것이다. [TicketFilterTest.java](development_harness/src/test/java/lab/export/TicketFilterTest.java)와 [ExportAcceptanceTest.java](development_harness/src/test/java/lab/export/ExportAcceptanceTest.java)의 결과가 이 기대와 일치했다. 첫 번째 OPS 문의에는 쉼표와 큰따옴표 제목, 두 번째에는 LF 제목을 넣어 팀 선택 뒤의 셀 표현도 함께 확인했다.

팀 미지정에서는 모든 팀의 OPEN 문의를 포함했고, 빈 팀명 오류는 CSV 호출에도 전달됐다. 선택 결과가 없으면 `id,team,status,title`만 반환했다. 날짜는 `ExportFilename`에 전달한 값을 사용했다. 예제에 `2026-09-03`을 전달한 실제 출력은 다음과 같다.

```text
tickets-2026-09-03.csv
id,team,status,title
T-1,OPS,OPEN,"로그인 ""오류"""
T-2,OPS,OPEN,"VPN, ""접속"""
```

[ExportFilenameTest.java](development_harness/src/test/java/lab/export/ExportFilenameTest.java)에서는 다른 날짜 `2027-01-02`도 같은 형식으로 확인했다. `acceptance`의 [검사 보고서](development_harness/build/reports/tests/acceptance/index.html)에서 전체 CSV·팀 선택·날짜 파일 이름 검사가 통과했다. 마지막 답변 종료 시 Stop Hook도 업무 검사를 실행해 통과를 기록했다. [그 실행의 CSV 검사 XML](development_harness/.local/harness/runs/994c280f-1330-488a-9425-d5c0ff746a76/build/test-results/acceptance/TEST-lab.export.ExportAcceptanceTest.xml)에도 통합 사례의 결과가 남아 있다.

팀 선택 후에도 기존 쉼표·큰따옴표·줄바꿈 표현이 유지되는 것은 문의 선택과 셀 표현의 책임이 분리돼 있기 때문이다. 팀 비교 규칙이나 선택 대상 상태가 바뀌면 필터와 관련 검사를, 날짜 기준이 바뀌면 날짜를 제공하는 호출부와 파일 이름 검사를 조정해야 한다. 작업을 다시 나눌 때도 독립적으로 바꿀 파일과 함께 연결할 호출부를 찾고, 결과 위치·확인한 동작·남은 연결을 인계해야 한다.

## Day 5 — 후속 요구를 분담·인계·통합까지 이어 가기

같은 문의가 반복 입력될 때 무엇을 남길지에 따라 CSV가 달라진다. [TicketFilter.java](development_harness/src/main/java/lab/export/TicketFilter.java)는 OPEN·팀 조건으로 입력을 고르지만, 기존 [TicketExport.java](development_harness/src/main/java/lab/export/TicketExport.java)는 선택한 모든 항목을 CSV로 조합했다. 같은 ID를 가진 항목도 각각 행이 됐고, 반환값에는 CSV 문자열만 있었다.

선택한 규칙은 ID가 같으면 같은 문의로 보고, OPEN·팀 조건을 먼저 적용한 뒤 남은 항목에서 첫 항목을 채택하는 것이다. 운영 담당자가 내려받을 대상은 현재 선택 조건에 맞는 OPEN 문의이므로 CLOSED 기록이나 다른 팀 기록이 뒤의 적격 항목을 가리지 않게 했다. 중복 제외 수는 선택을 통과했으나 같은 ID로 제외된 항목만 센다. 내보낸 수는 중복 정리 후 남긴 문의 수다. 팀을 지정하지 않으면 모든 팀의 OPEN 문의가 후보가 되어, 팀이 달라도 같은 ID는 하나만 남는다.

대표 입력은 `T-1/OPS/CLOSED/옛 기록`, `T-1/OPS/OPEN/새 기록`, `T-2/OPS/OPEN/첫 제목`, `T-2/OPS/OPEN/수정 제목`, `T-3/APP/OPEN/다른 팀` 순서다. OPS를 지정한 기대 CSV에는 T-1의 새 기록과 T-2의 첫 제목만 남으며, 중복 제외 수는 1, 내보낸 수는 2다. [ExportAcceptanceTest.java](development_harness/src/test/java/lab/export/ExportAcceptanceTest.java)에 이 입력과 팀 미지정의 같은 ID, 중복 없는 입력, 선택 결과가 없는 입력을 담았다.

변경: `TicketExport.export`가 필터 결과를 입력 순서대로 ID별 첫 항목만 남겨 CSV와 두 건수를 [ExportResult.java](development_harness/src/main/java/lab/export/ExportResult.java)로 반환한다. 기존 `csv` 호출은 같은 결과의 CSV를 반환한다. [ExportExample.java](development_harness/src/main/java/lab/export/ExportExample.java)는 CSV 뒤에 두 건수를 출력한다. 건수를 CSV 열에 섞으면 기존 네 열의 의미가 달라지므로 결과에 별도로 두었다.

결과: `acceptance --rerun-tasks`에서 위 입력과 기존 쉼표·큰따옴표·줄바꿈 제목 검사가 통과했다. `run` 출력에는 예시 날짜의 파일 이름, OPS 두 행, `제외한 중복 수: 1`, `실제로 내보낸 수: 2`가 표시됐다. 필터 조건이 바뀌면 중복 집계의 후보 범위도 함께 검토해야 하고, 어느 기록을 채택할지 달라지면 ID별 첫 항목을 남기는 부분과 업무 검사의 기대값을 함께 바꿔야 한다.

### 만든 개발 환경이 새 요구에 적용된 과정

[중복 문의 정리 후 내보내기](thread://01a0ee18-23f6-7142-95e7-eb57706c0982?hostId=local) 세션의 시작 문맥에는 프로젝트 설명과 Day 4 통합 완료 인계 메모가 전달됐다. 팀 선택·날짜 파일 이름이 연결된 현재 구조에서 출발해, 선택한 항목을 모두 CSV로 만드는 경로에 중복 정리와 건수 반환이 필요하다는 차이를 확인했다.

자연어 업무 요청에 `export-change` Skill이 선택됐고, 실제로 [SKILL.md](development_harness/.agents/skills/export-change/SKILL.md)를 읽었다. Skill의 “결과를 바꿀 미정 조건을 구분한다”는 절차는 같은 문의의 기준·처리 순서·집계 범위를 먼저 설명하는 과정으로 이어졌다. 같은 ID의 CLOSED와 OPEN, 서로 다른 제목의 OPEN 입력으로 예상 결과를 제시했고, 추천 방식을 수용한 뒤 구현이 진행됐다. 먼저 중복을 정리하는 방식도 가능하지만, 선택한 규칙에서는 내보내기 조건에 맞는 후보를 먼저 고른 뒤 중복을 센다.

“공개 입력·반환값에 영향을 받는 호출부와 검사도 확인한다”는 절차는 `ExportResult`, 예제 출력, CSV와 건수 검사로 연결됐다. `TicketExport.export`에서 선택된 목록의 크기와 ID별 첫 항목만 남긴 목록의 크기를 비교해 중복 제외 수를 구하고, 남긴 항목 수를 내보낸 수로 사용한다. 건수는 문자열의 줄바꿈을 세어 구하지 않으므로, 제목 안에 줄바꿈이 있어도 문의 건수와 혼동하지 않는다. 기존 `csv` 메서드도 같은 결과를 사용해 두 호출 경로의 중복 규칙을 일치시켰다.

준비한 Stop 사건을 직접 전달한 실행에서 새 업무 검사와 Hook 판단부의 연결을 확인했다. 이어 실제 답변 종료에서도 Stop Hook이 실행됐다. 해당 대화의 턴 ID로 `check_status: "pass"`가 기록됐고, [실제 Stop 실행의 검사 XML](development_harness/.local/harness/runs/53991412-70f5-4172-877f-8e67e63e8927/build/test-results/acceptance/TEST-lab.export.ExportAcceptanceTest.xml)에서 중복 처리와 기존 CSV 사례의 통과를 확인했다. 같은 실행 폴더의 팀 선택·날짜 파일 이름 검사도 통과했다.

### 이번 결과에서 판단한 유지·조정 대상

요구의 미정 조건을 정하고 반환값 변경을 호출부·검사까지 연결하는 절차가 이번 중복 요구에도 쓰였다. 이 결과를 근거로 지침과 Skill의 개발 절차를 계속 사용할 수 있다. 구체적으로 바꾼 부분은 업무 코드와 검사 입력, 프로젝트 설명과 인계 정보다. 중복 규칙을 Skill에 고정하는 대신 현재 업무 조건과 기대 결과에 반영하면, 다음 요구에서 채택 순서가 달라져도 같은 검토 절차로 새 규칙을 선택할 수 있다.

종료 Hook은 `acceptance`의 새 결과를 읽는 구조이므로 이번에 추가한 중복 검사를 같은 경로로 실행·판정했다. 검사 명령이나 결과 형식이 바뀌면 Hook 실행부·판단부를 조정해야 하지만, 같은 검사 작업에 업무 사례를 추가한 이번 변경은 테스트 내용의 확장으로 연결됐다. 시작 Hook도 같은 자료 위치를 읽으므로 프로젝트 설명과 인계 메모를 중복 처리 완료 상태로 갱신했다. 실행 구조와 자료 위치는 재사용하고, 현재 요구·처리·확인 결과를 담은 내용은 업무 변경에 맞춰 갱신한 사례다.

### 다음 요구의 자동 진행 구성

구성 당시 [TicketExport.java](development_harness/src/main/java/lab/export/TicketExport.java)는 OPEN·팀 선택 뒤 ID별 첫 항목을 남기고, [ExportFilename.java](development_harness/src/main/java/lab/export/ExportFilename.java)는 날짜만 받아 이름을 만든다. 다음 요구에서는 선택한 기록과 팀 파일 이름의 입출력이 각각 달라진다. 합의한 예상은 제공 입력 `T-1/CLOSED/옛 기록`, `T-1/OPEN/첫 제목`, `T-2/OPEN/다른 문의`, `T-1/OPEN/수정 제목`, `T-3/APP/OPEN/다른 팀`에서 첫 기록이면 T-1의 첫 제목, 마지막 기록이면 T-1의 수정 제목을 남기고 두 경우 모두 T-1·T-2 순서와 중복 제외 1건·내보냄 2건을 유지하는 것이다. 파일 이름은 OPS 지정 시 `tickets-OPS-2026-09-03.csv`, 팀 미지정 시 기존 `tickets-2026-09-03.csv`를 예상한다. 파일 이름에 사용할 수 없는 팀명 문자는 `%HH`로 표현한다.

이번 배정은 중복 선택과 전용 검사, 파일 이름과 전용 검사를 두 구현 담당에게 나누고 결과 확인 뒤 별도 통합 담당이 호출부와 결합 검사를 잇는 방식이다. 주 작업 공간의 앞선 변경이 미커밋 상태이므로 메인이 현재 상태를 확인하고 겹치지 않는 파일 범위로 배정하도록 [AGENTS.md](development_harness/AGENTS.md)와 [export-change Skill](development_harness/.agents/skills/export-change/SKILL.md)에 반복 절차를 두었다. 담당 수는 이번 계획에만 고정하고 이후에는 변경의 독립성과 공유 파일을 보고 정한다. 메인은 담당 결과를 기다려 검사 근거와 현재 코드를 대조하고, 인계 내용을 통합 담당에게 전달한 뒤 최종 업무 입력을 확인한다.

이전 [StopHook.java](development_harness/src/main/java/lab/harness/StopHook.java)는 첫 실패에 `block`으로 수정을 요청했다. 메인도 담당자에게 수정을 요청하면 같은 실패의 요청이 겹치므로, 합의한 정책에서는 메인만 같은 실패의 집중 수정을 한 번 요청한다. Hook은 최종 검사 실패와 XML 위치를 알리고 `continue: false`로 멈춘다. [StopHookTest.java](development_harness/src/test/java/lab/harness/StopHookTest.java)의 준비한 실패 입력에서 별도 `block` 요청이 나오지 않는지 확인했고, 전체 `test`가 통과했다. Skill Creator 형식 검사는 Skill의 frontmatter와 구조가 유효함을 확인했다. 준비한 Stop 사건을 실제 진입점에 전달한 실행은 새 업무 검사를 읽어 `{}`와 `check_status: pass`를 남겼다.

### 후속 요구의 구현·인계·통합 결과

첫 기록·마지막 기록 선택은 후보를 정하는 조건과 후보 중 어느 내용을 채택할지를 나누는 변경이다. OPEN·팀 조건을 먼저 적용하고 같은 ID의 내용을 고르므로, 뒤에 CLOSED나 다른 팀 기록이 있어도 선택한 팀의 OPEN 기록을 가리지 않는다. [TicketExport.java](development_harness/src/main/java/lab/export/TicketExport.java)의 `export(tickets, team, DuplicateSelection.LAST)`는 마지막 적격 기록을 채택한다. 기존 호출은 `FIRST`로 연결되어 선택 생략의 결과를 유지한다. 같은 ID의 값을 교체해도 첫 삽입 위치가 유지되는 구성이라 내용 선택과 ID 출력 순서를 각각 지킬 수 있다.

입력은 OPS의 T-1 CLOSED 옛 기록, T-1 OPEN 첫 제목, T-2 OPEN 다른 문의, T-1 OPEN 수정 제목과 APP의 T-3 OPEN 다른 팀이다. 실제 [ExportExample.java](development_harness/src/main/java/lab/export/ExportExample.java) 출력에서 FIRST는 T-1 첫 제목·T-2 다른 문의, LAST는 T-1 수정 제목·T-2 다른 문의를 반환했다. 두 선택 모두 중복 제외 수 1, 내보낸 수 2였고 파일 이름은 `tickets-OPS-2026-09-03.csv`였다. FIRST와 LAST에서 필터를 통과한 후보 수와 남긴 고유 ID 수가 각각 같으므로 두 건수도 같다. 제목의 줄바꿈 수를 세지 않고 남긴 문의 수를 사용하므로 여러 줄 제목도 문의 한 건이다.

[ExportFilename.java](development_harness/src/main/java/lab/export/ExportFilename.java)의 `name(date, team)`은 팀을 이름에 표시하며, 팀 미지정은 날짜만 표시한다. 파일 이름 표현에 필요한 문자 변환과 문의 선택에 사용하는 팀의 의미를 분리했다. 결합 검사에서 `OPS/지원`은 원래 팀명으로 문의를 선택하고 파일 이름에서는 `OPS%2F지원`으로 표현했다. 실제 팀명 `OPS%2F지원`은 파일 이름에서 `OPS%252F지원`이 되어 두 팀을 구별했다. 앞으로 수정 시각이 입력에 추가되고 최신 시각으로 채택하는 요구가 생기면 현재의 목록 순서 기준을 다시 선택해야 한다. 팀명 비교 조건이 바뀌면 후보 범위와 중복 집계의 기대값도 함께 검토해야 한다.

첫 기록·마지막 기록 선택과 팀 파일 이름을 요청하자 [export-change Skill](development_harness/.agents/skills/export-change/SKILL.md)의 분담 절차가 적용됐다. 메인은 Skill과 프로젝트 설명을 읽고 저장한 업무 조건을 중복 선택 담당 A와 파일 이름 담당 B에 전달하고, 두 담당의 현재 코드·전용 XML을 확인한 뒤 통합 담당 C에 인계했다. 앞선 미커밋 변경이 있는 주 작업 공간에서 서로 다른 파일을 맡아 구현했고, Gradle 검사는 순서대로 실행했다. C는 확인된 API와 대표 입력을 사용해 예제·[ExportIntegrationTest.java](development_harness/src/test/java/lab/export/ExportIntegrationTest.java)·[프로젝트 설명](development_harness/docs/project.md)을 연결했다. 이 구성은 독립된 변경을 먼저 확인한 뒤 같은 팀·날짜·선택값을 함께 사용하는 입력으로 연결을 검사하기 위한 선택이다. 담당별 결과는 `development_harness/.local/harness/checks/`에 있으며, 최종 인계는 `development_harness/.local/harness/checkpoint.md`에 있다.

재확인: 통합된 `acceptance`에서 첫 기록·마지막 기록, 기본 호출, 필터 이후 집계, 팀 파일 이름과 기존 CSV 이스케이프 사례가 통과했다. 최종 검사 결과는 [업무 검사 보고서](development_harness/build/reports/tests/acceptance/index.html), 대표 실제 출력은 `development_harness/.local/harness/final-example.log`에 있다. 업무 요청 이후 담당 배정·결과 대기·통합 인계·최종 검사는 메인이 이어 갔다. 중간 단계마다 다음 담당을 시작하거나 새 대화에 작업을 옮기는 지시가 필요했던 앞선 실행과 달리, 이번 실행에서는 저장한 완료 조건을 다음 작업의 시작 기준으로 사용했다.

응답 종료 시 실제 Stop Hook도 같은 턴의 통합된 업무 검사를 실행해 통과했다. 근거는 `.local/harness/last-hook-event.json`과 이번 실행의 [Hook 검사 XML](development_harness/.local/harness/runs/a1818622-01c8-43ba-abf7-4bb8415dc512/build/test-results/acceptance/TEST-lab.export.ExportAcceptanceTest.xml)이다. 이번에는 기능 실패가 없어 메인이 수정을 재요청하는 경로는 실행되지 않았다. 현재 Hook의 실패 시 중단 응답은 준비한 실패 입력으로 검사했으며, Day 3에서 실제로 확인한 자동 수정은 변경 전 Hook 정책의 결과다.
