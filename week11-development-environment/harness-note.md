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
