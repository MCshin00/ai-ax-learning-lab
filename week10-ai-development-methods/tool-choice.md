# 10주차 개발 방식 비교와 하네스 v2

## Day 1 — 같은 요구에서 실행 방식을 비교하기

이번 과제는 여러 시스템에서 들어오는 지원 요청의 우선순위와 제목을 한 가지 형식으로 표시하는 것이다. `comparison_task/`의 시작 코드를 `runs/M1`~`runs/M5`에 각각 준비하고, 같은 업무 요구와 검사로 다섯 개발 방식을 수행했다. 비교의 중심은 입력 정리와 전체 표시의 책임을 어떻게 구현에 연결하는지, 작업을 나누었을 때 무엇을 인계하는지, 완료 여부를 어디서 결정하는지다.

| 방식 | 작업을 진행하는 구성 |
|---|---|
| M1 | 진행 순서를 맡긴 단일 작업 |
| M2 | 계획 → 구현 → 테스트 → 자기검토 순서를 지정한 단일 작업 |
| M3 | 계획·구현·검사·보안 검토·통합을 별도 역할로 인계 |
| M4 | 책임과 반환 계약을 수용한 뒤 두 Worktree에서 병렬 구현하고 통합 |
| M5 | 자체 하네스가 구현 작업을 호출하고 외부 검사·복구·종료를 제어 |

각 방식은 구현 전의 같은 코드에서 출발했다. 대화형 작업에서 선택한 모델 설정은 Astra · Light였다. 함수별 역할과 빈 제목의 표시 규칙은 공통 README에도 설명되어 있었다. 따라서 실제로 비교한 것은 제공된 구조와 요구를 완성하는 과정에서 절차 지정·인계·격리·실행 제어가 만든 차이다.

### 공통 업무 요구와 검사

입력은 우선순위와 제목 두 문자열이고, 결과는 지원 목록에 표시할 문자열 한 개다. 우선순위의 별칭과 대소문자·앞뒤 공백을 정리하고, 제목의 연속 공백과 줄바꿈은 한 칸으로 만든다. 제목이 비면 목록을 읽는 사람이 내용을 보충해야 하므로 `확인 필요`를 표시한다.

| 입력 | 기대 출력 | 요구가 걸리는 위치 |
|---|---|---|
| `" Urgent "`, `"  결제   오류  "` | `[P0] 결제 오류` | 우선순위 별칭과 제목 공백 정리 |
| `"normal"`, `"상품\n문의"` | `[P1] 상품 문의` | 기본 우선순위와 제목 줄바꿈 처리 |
| `"LOW"`, `"화면 개선"` | `[P2] 화면 개선` | 우선순위 대소문자와 별칭 처리 |
| `"later"`, `"문의"` | `[P1] 문의` | 알 수 없는 우선순위의 기본값 |
| `"urgent"`, 공백뿐인 제목 | `확인 필요` | 제목 누락에 따른 전체 표시 선택 |
| `null`, `null` | `확인 필요` | 없는 입력 처리 |
| `null`, `"$(example) ; <tag>"` | `[P1] $(example) ; <tag>` | 제목 문자를 표시 자료로 사용 |

[공개 수용 검사](runs/M1/src/test/java/lab/week10/RequirementTest.java)는 이 입력들을 다섯 테스트 메서드로 묶어 확인한다. 비교표의 `5/5`는 이 공통 테스트 메서드의 통과 수다. [기존 표시 검사](runs/M1/src/test/java/lab/week10/SummaryTest.java)는 이미 정리된 `P1`, `결제 오류`가 `[P1] 결제 오류`로 표시되는지 확인한다.

시작 코드에서 `test`는 기존 표시 검사를, `acceptance`는 새 요구의 공개 수용 검사를 실행한다. 구현 전 M1에서는 기존 표시가 통과했지만 `LOW`, `화면 개선`이 `[LOW] 화면 개선`으로 반환되어 새 요구는 실패했다. 기존 문자열 조합은 동작하고 있었고, 별칭 변환과 입력 정리를 추가해야 하는 상태였다.

### 입력 정리와 전체 표시가 만나는 지점

앱의 호출 경로는 `Summary.main()` → `Summary.render()` → 두 정리 함수 → 콘솔 출력이다. 시작 상태의 `render()`는 두 결과를 바로 붙였다.

```java
return "[" + Priority.normalize(priority) + "] " + Title.normalize(title);
```

여기에서 Title이 빈 문자열을 반환하면 출력에는 `[P0] `가 남는다. 제목 정리 함수는 제목의 내용과 유무를 반환하고, Summary는 그 결과에 따라 접두어를 포함한 전체 표시를 선택해야 한다. 이 연결을 M1·M2의 단일 작업, M3의 역할 인계, M4의 병렬 구현·통합, M5의 외부 검사에서 각각 확인했다.

## M1 — 진행 순서를 맡긴 단일 작업

M1에는 README의 표시 규칙 구현과 실제 결과 확인을 요청했다. 구현 전 설명에서 우선순위와 제목을 각각 정리하고, `Summary.render()`에서 빈 제목을 처리하는 구성이 제시되었다. 한 작업 안에서 세 파일을 수정한 뒤 기존 표시와 새 수용 사례를 검사했다.

### 별칭을 표준값으로 바꾸고 제목을 정리한 구현

[Priority.java](runs/M1/src/main/java/lab/week10/Priority.java)는 없는 입력을 먼저 `P1`로 처리한다. 문자열이 있으면 앞뒤 공백을 제거하고 대소문자를 정리한 뒤 `p0`·`urgent`를 `P0`, `p2`·`low`를 `P2`로 바꾼다. 나머지는 기본 분기에서 `P1`을 반환한다. 따라서 `p1`·`normal`과 알 수 없는 값은 같은 분기에서 처리된다.

이 구성은 현재의 기본값 규칙과 맞는다. 이미 정상인 우선순위와 알 수 없는 우선순위 모두 `P1`로 표시되기 때문이다. 알 수 없는 값을 따로 표시하거나 오류로 돌려줘야 하는 요구가 생기면 그 분기를 나누는 것이 변경 지점이다.

[Title.java](runs/M1/src/main/java/lab/week10/Title.java)는 `null`을 빈 문자열로 바꾸고, 연속된 공백을 한 칸으로 만든 다음 앞뒤 공백을 제거한다.

```java
return value.replaceAll("\\s+", " ").replaceAll("^ +| +$", "");
```

예를 들어 `"  결제   오류  "`는 먼저 `" 결제 오류 "`가 되고, 앞뒤를 제거하면 `"결제 오류"`가 된다. 공백뿐인 제목도 같은 경로를 거쳐 빈 문자열이 된다. 제목의 공백 범위는 README에서 정한 정규식 `\s`를 사용한다.

### 제목의 유무로 전체 표시 선택

[Summary.java](runs/M1/src/main/java/lab/week10/Summary.java)는 제목 정리 결과를 먼저 확인한다.

```java
String normalizedTitle = Title.normalize(title);
if (normalizedTitle.isEmpty()) return "확인 필요";
return "[" + Priority.normalize(priority) + "] " + normalizedTitle;
```

빈 제목의 안내는 이 함수의 조기 반환에서 결정된다. 따라서 우선순위가 `urgent`여도 제목이 비면 결과는 `확인 필요`다. 제목이 있으면 정리된 우선순위와 제목을 조합한다. 입력 정리 결과를 사용하는 위치에 표시 판단을 두면서 접두어를 붙일 조건도 함께 표현했다.

첫 구현의 기존 표시와 수용 사례 5/5는 JUnit 실행에서 통과했고, 이후 같은 코드의 Gradle `test acceptance`에서도 통과했다. 앱 진입점의 실제 출력은 일반 제목에서 `[P0] 결제 오류`, 공백뿐인 제목에서 `확인 필요`였다. 근거는 [기존 검사 보고서](runs/M1/build/reports/tests/test/index.html)와 [수용 검사 보고서](runs/M1/build/reports/tests/acceptance/index.html)에 있다.

M1에서는 별도의 단계 지정 없이도 입력 정리와 전체 표시의 책임이 구현 전 설명에 등장했고, 그대로 코드와 검사 결과에 연결되었다. 비교할 기준은 이 한 작업의 진행을 M2에서 명시적인 순서로 지정했을 때 무엇이 달라지는가였다.

## M2 — 계획·구현·테스트·자기검토를 지정한 단일 작업

M2에는 한 작업 안에서 네 단계를 순서대로 진행하도록 요청했다. 계획에는 우선순위의 별칭과 기본값, 제목 공백 정리, 빈 제목일 때 Summary가 반환할 문자열이 각각 제시되었다. 검사와 자기검토에서도 같은 항목을 요구와 대조했다.

### 단계 지정이 드러낸 확인 시점

계획은 세 함수의 책임과 검사할 조건을 미리 나열했다. 구현 단계에서 빈 제목 안내 문구가 잘못 입력되었고, 검사 전에 `확인 필요`로 수정되었다. 처리 위치와 분기는 계획대로였지만 실제 출력 문자열은 한 차례 고쳐졌다. 이 사례는 계획에 적힌 규칙을 코드의 반환값까지 대조하는 일이 필요함을 보여 준다.

그 뒤 기존 표시와 새 요구의 검사가 통과했다. 자기검토에서는 README의 별칭·기본값·공백 범위·빈 제목 규칙을 최종 코드와 대조했고, 그 시점에는 요구와 구현이 일치했다. 수정이 일어난 시점은 구현 단계였고, 마지막 검토는 완성된 코드의 규칙을 재확인하는 역할을 했다.

### M1과 달라진 코드 표현

[Priority.java](runs/M2/src/main/java/lab/week10/Priority.java)에는 다음 분기가 추가로 명시되었다.

```java
case "p1", "normal" -> "P1";
```

M1에서 기본 분기로 처리했던 입력을 코드에 직접 적은 것이다. 이 표현에서는 요구의 별칭 목록과 분기를 바로 대조할 수 있다. M1과 M2 모두 같은 입력에 같은 `P1`을 반환하며, [Title.java](runs/M2/src/main/java/lab/week10/Title.java)와 [Summary.java](runs/M2/src/main/java/lab/week10/Summary.java)는 M1과 같은 코드로 완성되었다.

`test acceptance`에서 기존 표시와 수용 사례 5/5가 통과했다. 앱 진입점에 `" Urgent "`, `"  결제   오류  "`를 전달한 결과는 `[P0] 결제 오류`, `urgent`와 공백뿐인 제목의 결과는 `확인 필요`였다. [기존 검사 보고서](runs/M2/build/reports/tests/test/index.html)와 [수용 검사 보고서](runs/M2/build/reports/tests/acceptance/index.html)에 상세 결과가 있다.

두 방식에서 나온 기능 결과는 같았다. M2의 차이는 무엇을 계획하고 어떤 항목을 마지막에 대조했는지가 단계별로 드러났다는 점이다. M1에서 한 작업 안에 이어졌던 판단을 M2에서는 계획·구현·검사·검토의 시점으로 나누어 읽을 수 있었다.

## M3 — 역할별 판단과 검사 근거를 인계한 실행

M3는 Planner → Implementer → Tester → Security Reviewer → Integrator를 별도 에이전트로 나누고 순차로 인계했다. 주 작업은 각 역할의 결과를 다음 역할에 전달했다. 주 작업 하나와 역할 작업 다섯 개를 합쳐 총 여섯 작업이 실행되었다.

### 같은 반환 계약을 역할 사이에 전달

Planner가 정리한 핵심 계약은 `Title.normalize()`가 없는 제목을 빈 문자열로 반환하고, `Summary.render()`가 그 결과에 따라 전체 표시를 결정한다는 것이다. 이 계약은 Implementer의 코드와 Tester의 기대 결과, Integrator의 최종 확인에 함께 사용되었다.

| 역할 | 실제로 다룬 내용 | 다음 역할이 사용할 근거 |
|---|---|---|
| Planner | Priority의 별칭·기본값, Title의 빈 문자열 반환, Summary의 빈 제목 표시 | 함수별 입력·반환 규칙과 공통 수용 기준 |
| Implementer | 세 파일의 정리 함수와 전체 표시 분기 구현 | 변경한 코드 위치와 확인할 입력·출력 |
| Tester | 기존 표시·수용 검사와 앱 진입점 출력 확인 | 검사 결과, 실제 출력, 실행 예시에서 남은 문제 |
| Security Reviewer | 외부 입력이 함수들을 거쳐 출력되는 경로 검토 | 문자열 사용 방식과 해당 수용 사례 |
| Integrator | 최종 코드와 검사 근거 대조, 실행 예시 문제 해결 | 문서의 실행 경로로 확인한 최종 앱 출력 |

Tester 단계에서는 검사 통과와 앱 출력이 확인됐고, README의 PowerShell 실행 예시에 인자 전달 문제가 남아 있었다. Integrator가 그 문제를 이어받아 실행 예시를 해결하고 `[P0] 결제 오류` 출력을 확인했다. 인계 내용에 현재 결과와 남은 문제가 함께 있었기 때문에 마지막 역할은 해결할 지점을 특정할 수 있었다.

### 코드의 차이와 외부 입력 검토

[Priority.java](runs/M3/src/main/java/lab/week10/Priority.java)는 공백·대소문자를 정리한 중간값을 `normalized` 변수에 담고 분기한다. M1과 같은 별칭·기본값 규칙에 중간값의 이름을 붙인 표현이다. [Title.java](runs/M3/src/main/java/lab/week10/Title.java)와 [Summary.java](runs/M3/src/main/java/lab/week10/Summary.java)는 M1·M2와 동일하다.

보안 검토는 `Summary.main()`이 받은 문자열을 `render()`에 전달하고, 정리·결합한 값을 콘솔로 출력하는 경로를 확인했다. `$(example) ; <tag>`는 이 경로에서 제목 문자열로 사용되어 `[P1] $(example) ; <tag>`가 된다. 이 수용 사례와 실제 호출 경로가 외부 입력을 어떻게 사용하는지에 대한 근거였다. 출력의 사용처가 HTML이나 명령 실행으로 바뀌면, 그 사용처가 문자열을 해석하는 방식까지 검토 대상이 확장된다.

[기존 검사 보고서](runs/M3/build/reports/tests/test/index.html)와 [수용 검사 보고서](runs/M3/build/reports/tests/acceptance/index.html)에서 공통 검사가 통과했고, 최종 앱 출력은 `[P0] 결제 오류`였다. 역할별 검토 결과는 구현된 업무 규칙과 일치했다. 이번 인계에서 구체적으로 해결한 사항은 실행 예시의 연결이었으며, 기능 구현은 처음 정한 반환 계약을 따라 완성되었다.

M3는 한 작업이 전체를 확인하던 흐름을 역할별 확인과 결과 취합으로 나누었다. 이 작은 과제에서도 각 역할에 현재 코드·반환 계약·미해결 사항·검사 근거를 전달하는 작업이 생겼다. 역할 이름보다 이 인계 내용이 다음 검토를 이어 주었다.

## M4 — 책임을 정하고 Worktree에서 병렬 구현

M4에서는 AI가 함수별 계약과 파일 담당·통합 순서를 제안한 뒤 그 구성을 수용했다. Priority와 Title은 각각 값을 정리하고, Summary는 두 결과를 사용하는 위치에서 전체 표시를 결정하도록 나누었다.

### 제목 내용과 누락 안내의 구분

실제 제목으로 `확인 필요`가 들어오면 우선순위가 `urgent`일 때 `[P0] 확인 필요`로 표시해야 한다. Title이 없는 입력을 안내 문구로 바꾸면 제목 내용과 누락 상태가 같은 문자열이 된다. Title은 빈 문자열로 누락을 전달하고 Summary가 안내를 선택하는 구성이 이 차이를 표현한다.

이 계약을 먼저 정했기 때문에 Title 담당은 제목 정리에 집중하고, 통합 작업은 반환된 제목의 유무로 전체 표시를 연결할 수 있었다. 제목 정리 결과를 다른 표시 형식에서도 쓴다면 Title을 재사용하고 Summary의 표현을 바꾸는 구성이 된다.

### 파일을 나누고 변경을 병합한 과정

초기 커밋 `7a5bb47`에서 두 브랜치를 만들었다. 두 Worktree는 M4 안의 별도 폴더에서 각 브랜치의 파일을 펼쳐 두었고, 두 구현 작업은 주 작업의 모델·추론 설정을 상속해 동시에 진행했다.

| 담당 | 작업 위치·브랜치 | 구현과 검사 |
|---|---|---|
| Priority | `.local/worktrees/priority`, `codex/m4-priority` | [Priority.java](runs/M4/src/main/java/lab/week10/Priority.java)와 [PriorityTest.java](runs/M4/src/test/java/lab/week10/PriorityTest.java) |
| Title | `.local/worktrees/title`, `codex/m4-title` | [Title.java](runs/M4/src/main/java/lab/week10/Title.java)와 [TitleTest.java](runs/M4/src/test/java/lab/week10/TitleTest.java) |
| 통합 | M4의 주 작업 폴더 | [Summary.java](runs/M4/src/main/java/lab/week10/Summary.java)와 [SummaryIntegrationTest.java](runs/M4/src/test/java/lab/week10/SummaryIntegrationTest.java) |

Priority의 `c290b04`와 Title의 `c5bafa9`를 순서대로 병합했다. 담당 소스와 검사 파일이 나뉘어 있어 병합 충돌이 없었고, 교차 검토에서도 합의한 반환 계약이 맞았다. 검토와 통합 검토는 같은 담당 작업을 이어 사용했다. 주 작업과 두 구현 작업을 합친 실행 작업 수는 세 개다.

M4의 Title은 앞뒤 공백을 먼저 제거한 뒤 내부 연속 공백을 한 칸으로 만든다. M1~M3에서는 연속 공백을 먼저 한 칸으로 만들고 앞뒤를 제거했다. 정한 공백 범위에서 두 순서는 같은 정리 결과를 만든다. 담당을 나누는 기준은 이런 내부 처리 순서보다 입력과 반환값의 계약이었다.

### 정리 함수 병합과 전체 표시 연결의 차이

두 정리 함수만 병합한 시점의 수용 결과는 4/5였다. `urgent`와 공백뿐인 제목을 넣으면 Title은 빈 문자열을 반환했지만, Summary의 기존 조합식이 `[P0] `를 만들었다. 이후 예정된 통합 단계에서 Summary에 빈 제목의 조기 반환을 연결해 `확인 필요`가 되었고 수용 사례 5/5가 통과했다.

| 통합 시점 | Title의 반환 | Summary의 동작 | 실제 결과 |
|---|---|---|---|
| 두 정리 함수 병합 후 | 빈 문자열 | 기존 조합식으로 접두어를 붙임 | `[P0] ` |
| 빈 제목 분기 연결 후 | 빈 문자열 | 안내 문구를 먼저 반환 | `확인 필요` |

[연결 전 수용 결과](runs/M4/.local/pre-integration/TEST-lab.week10.RequirementTest.xml)는 각 정리 함수가 맡은 처리를 끝낸 상태와 전체 표시가 완성된 상태의 차이를 보여 준다. Worktree로 나눈 변경은 Git에서 병합하고, 업무 의미의 연결은 Summary와 전체 수용 검사에서 완성했다.

### 개별 계약 검사와 통합 검사

Priority의 개별 검사는 별칭 주변의 공백·탭·줄바꿈과 대소문자를 섞어도 표준값이 되는지, `P3`나 `ur gent`처럼 목록에 없는 입력이 기본값이 되는지 확인했다. Title의 개별 검사는 빈 입력·연속 공백 정리와 함께 실제 제목 `확인 필요`, `[P0] 제목`이 제목 내용으로 유지되는지 확인했다.

통합 검사에서는 여러 우선순위에 빈 제목을 조합해 모두 `확인 필요`가 되는지 확인했다. `" 확인   필요 "`는 정리 후 `[P0] 확인 필요`로 표시되었다. 정한 공백 범위 밖의 `\u00A0`도 별도 입력으로 사용해 제목 내용에 남는 결과를 확인했다. 개별 검사는 각 함수의 반환 계약을, 통합 검사는 그 반환값을 사용하는 전체 표시를 설명한다.

최종 앱 출력은 `" Urgent "`, `"  결제   오류  "`에서 `[P0] 결제 오류`, 공백뿐인 제목에서 `확인 필요`, 실제 제목 `확인 필요`에서 `[P0] 확인 필요`였다. [표시·계약·통합 검사 보고서](runs/M4/build/reports/tests/test/index.html)와 [공통 수용 검사 보고서](runs/M4/build/reports/tests/acceptance/index.html)에 결과가 있다.

## M5 — 자체 하네스에 구현·검사·복구·종료 연결

M5는 5주차에서 만든 하네스를 사용했다. 기존 엔진은 작업 요청을 읽고 Codex를 호출한 뒤 별도 검사 결과로 완료를 판단하는 구조였다. 이번 지원 요청 과제를 연결하려면 작업 폴더와 요구뿐 아니라, 기존 표시와 새 수용 검사 두 결과를 함께 완료 근거로 전달해야 했다.

### 작업 요청이 하위 구현에 전달되는 경로

[요청 파일](runs/M5/harness/m5-work.json)은 M5의 `harness/`에 두었다. `workspace: ".."`는 그 파일의 상위 폴더인 M5를 실행 위치로 정한다. 요구 파일과 맥락 파일은 이 작업 위치를 기준으로 읽는다.

| 요청 항목 | 연결한 값과 역할 |
|---|---|
| `workspace` | `..`: M5 소스와 Gradle 프로젝트가 있는 위치 |
| `task` | `../../m5-task.md`: 공통 구현 요청 |
| `context` | 루트 작업 지침, M5 README, `harness/context.md`의 수용한 책임·검사 조건 |
| `verify` | 운영체제별 `test acceptance` 명령 배열 |
| `maxRepairs` | `1`: 업무 검사 실패 후 수정·재검사 한 번 |
| `timeoutSeconds` | `600`: 개별 프로세스의 대기 한도 |

[HarnessCli.java](../week05-development-harness/quality_demo/src/main/java/lab/week05/harness/HarnessCli.java)가 요청을 읽고 실행별 결과 폴더를 만든다. [WorkRequest.java](../week05-development-harness/quality_demo/src/main/java/lab/week05/harness/WorkRequest.java)는 작업 위치와 요구·맥락 파일을 해석하고, 각 파일의 본문을 하나의 요청으로 만든다. [실행 맥락](runs/M5/harness/context.md)에는 이미 수용한 Priority·Title·Summary의 책임과 반환 규칙을 담았다.

[DevelopmentHarness.java](../week05-development-harness/quality_demo/src/main/java/lab/week05/harness/DevelopmentHarness.java)는 이 요청으로 작업 실행을 시작하고 검사·복구·종료 순서를 결정한다. [ProcessRunner.java](../week05-development-harness/quality_demo/src/main/java/lab/week05/ProcessRunner.java)는 명령을 지정한 폴더에서 실행하고 요청 본문을 표준 입력으로 전달한 뒤 종료 코드·출력·시간 초과 여부를 돌려준다. 엔진은 이 결과를 받아 다음 단계를 선택한다. 요구를 해석해 업무 코드를 만드는 책임과 프로세스 결과로 실행을 이어 갈지 판단하는 책임이 나뉘어 있다.

### 두 검사 결과를 수집하도록 연결부 변경

기존 검사 연결은 `harnessReportDir` 바로 아래의 JUnit XML을 읽었다. M5의 원래 빌드 설정은 이 속성을 사용하지 않았으므로, 하네스가 정한 위치로 결과를 보내는 연결이 필요했다. 또 `test`와 `acceptance`를 한 번에 실행하면서 각각의 실행 결과를 확인해야 했다.

변경은 세 곳에 연결되었다.

| 변경 위치 | 반영한 내용 | 필요한 이유 |
|---|---|---|
| M5 요청의 검사 명령 | `test acceptance`와 `-PharnessReportTasks=test,acceptance` | 실행할 검사와 결과가 필요한 두 그룹을 명시 |
| [M5 build.gradle](runs/M5/build.gradle) | `harnessReportDir` 아래 검사 작업 이름별로 XML 저장 | 두 결과가 별도 위치에 남도록 구성 |
| [GradleVerifier.java](../week05-development-harness/quality_demo/src/main/java/lab/week05/harness/GradleVerifier.java) | 지정한 그룹별 실행 근거를 확인한 뒤 결과 합산 | 기존 표시와 새 요구가 모두 검사됐는지 판단 |

빌드에서는 모든 Test 작업에 다음 결과 위치를 적용했다. `${name}`에는 `test` 또는 `acceptance`가 들어간다.

```groovy
reports.junitXml.outputLocation = file("${harnessReports.get()}/${name}")
```

검사 연결부는 실행마다 새 `check-1/` 폴더를 만들고 `--rerun-tasks --no-build-cache`를 명령에 붙인다. 그 아래 `test/`와 `acceptance/`의 XML을 읽기 때문에 현재 실행의 두 결과를 한 쌍으로 확인할 수 있다. 요청에 넣은 `--continue`는 한 검사 작업이 실패해도 실행 가능한 다른 검사까지 수행해 두 결과를 얻기 위한 선택이다.

`reportGroups()`는 검사 명령의 속성에서 필요한 그룹을 읽고, `assess()`는 각 그룹에 실제 실행된 검사가 있는지 확인한다. 그룹 지정이 없는 기존 5주차 요청은 단일 결과 폴더를 사용한다. 같은 엔진이 기존 요청과 이번 두 검사 요청을 처리할 수 있도록 연결했다.

### 검사 결과에 따라 복구와 종료 선택

검사 연결부는 XML의 테스트·실패·오류·건너뜀 정보와 프로세스 종료 코드를 함께 사용한다. 다음은 연결부 검사에서 확인한 분류와 엔진의 처리다.

| 검사에서 만든 상황 | 연결부의 판정 | 엔진에서 이어지는 처리 |
|---|---|---|
| 두 그룹이 실행되고 실패 없이 정상 종료 | `OK` | 성공으로 종료 |
| 두 그룹이 실행되고 실패 결과와 비정상 종료가 함께 있음 | `FAILED` | 남은 복구 범위가 있으면 수정 요청 후 재검사 |
| 한 그룹이 없거나 전부 건너뛰어짐 | `UNAVAILABLE` | 검사 근거 부족으로 중단 |
| XML의 결과와 종료 코드가 서로 맞지 않음 | `UNAVAILABLE` | 결과를 판정할 근거를 확인하도록 중단 |
| 검사 프로세스 시간 초과 | `TIMED_OUT` | 시간 초과 근거와 함께 중단 |

핵심은 고칠 업무 코드의 실패와 검사를 실행하지 못한 상황을 나누는 것이다. 예를 들어 새 수용 검사의 기대값과 실제값이 다르면 그 차이를 수정 요청에 넣을 수 있다. 검사 파일이 생성되지 않은 상황에서는 코드의 어느 반환값이 틀렸는지 알 수 없으므로 실행 조건을 해결할 정보가 필요하다.

업무 검사 실패의 복구 요청에는 원래 요구와 맥락을 다시 담고, 실패 결과를 덧붙인다. `maxRepairs: 1`이면 최초 호출·검사 뒤 한 번 더 수정·검사할 수 있다. [DevelopmentHarnessTest.java](../week05-development-harness/quality_demo/src/test/java/lab/week05/harness/DevelopmentHarnessTest.java)의 복구 검사는 `EXECUTE → VERIFY → EXECUTE → VERIFY` 순서와 두 번째 요청에 원래 요구·규칙·실패 근거가 함께 있는지 확인한다. [GradleVerifierGroupsTest.java](../week05-development-harness/quality_demo/src/test/java/lab/week05/harness/GradleVerifierGroupsTest.java)는 위의 두 그룹 분류를 확인한다. 두 검사와 기존 하네스 검사가 통과했다.

### 실제 M5 실행과 완료 근거

빌드한 엔진 JAR를 M5 폴더에서 실행했다. Windows PowerShell과 macOS·Linux·WSL에서 명령 형태는 같다.

```text
java -jar ../../../week05-development-harness/quality_demo/build/libs/development-harness.jar harness/m5-work.json
```

실제 진행 순서는 다음과 같았다.

1. 요청과 맥락을 준비하고 M5에서 하위 Codex 작업을 시작했다.
2. 하위 작업이 Priority·Title·Summary를 구현하고 마지막 응답을 남겼다.
3. 하네스가 별도 프로세스로 `test acceptance`를 실행했다.
4. 두 그룹의 새 결과를 확인하고 첫 시도에서 성공으로 종료했다.

[모델 마지막 응답](runs/M5/.local/development-harness/c38d72cf-bf5f-42f1-8e34-4a6159a68e83/agent-1.txt)은 세 파일 구현과 내부 검사 결과를 보고했다. 그 뒤 하네스가 만든 [기존 표시 XML](runs/M5/.local/development-harness/c38d72cf-bf5f-42f1-8e34-4a6159a68e83/check-1/test/TEST-lab.week10.SummaryTest.xml)과 [수용 검사 XML](runs/M5/.local/development-harness/c38d72cf-bf5f-42f1-8e34-4a6159a68e83/check-1/acceptance/TEST-lab.week10.RequirementTest.xml)에서 공통 검사가 통과했다. 일반 제목의 `[P0] 결제 오류`와 빈 제목의 `확인 필요`도 이 검사에서 확인되었다.

[최종 결과](runs/M5/.local/development-harness/c38d72cf-bf5f-42f1-8e34-4a6159a68e83/result.json)는 실제 Codex 프로세스 실행 후 검사 단계에서 성공으로 결정되었다. [외부 검사 출력](runs/M5/.local/development-harness/c38d72cf-bf5f-42f1-8e34-4a6159a68e83/check-1/verification.txt)에 두 그룹의 실행 근거가 남았다. 최초 검사에서 통과해 복구 호출은 없었다. 주 작업과 하위 구현 작업을 합쳐 두 작업이 실행되었으며, 실행 전에는 연결 구성을 수용했고 실행 중에는 추가 사람 판단이 필요하지 않았다.

최종 [Priority.java](runs/M5/src/main/java/lab/week10/Priority.java)는 정리한 값을 변수에 담고 `p1`·`normal`도 명시적으로 분기한다. [Title.java](runs/M5/src/main/java/lab/week10/Title.java)는 연속 공백을 한 칸으로 만든 뒤 앞뒤 한 칸을 제거하고, [Summary.java](runs/M5/src/main/java/lab/week10/Summary.java)는 빈 제목을 먼저 반환한다. 기능의 책임은 다른 방식과 같았고, 완료 판단은 모델 응답 다음에 실행되는 하네스의 검사로 연결되었다.

## M1~M5 비교 — 진행 방식에서 달라진 책임

| 비교 항목 | M1 | M2 | M3 | M4 | M5 |
|---|---|---|---|---|---|
| 진행 방식 | 순서를 맡긴 단일 작업 | 네 단계를 지정한 단일 작업 | 다섯 역할로 순차 인계 | 두 Worktree에서 병렬 구현 후 통합 | 하네스가 구현·검사·복구·종료 제어 |
| 공통 수용 결과 | 5/5 | 5/5 | 5/5 | 5/5 | 5/5 |
| 실행 작업 수 | 1 | 1 | 6 | 3 | 2 |
| 구체적인 변화 | 한 작업에서 책임 제안과 구현·검사 연결 | 구현 중 안내 문구 수정, 마지막 요구 대조 | 계약·검사 근거 인계와 실행 예시 문제 해결 | 정리 함수 병합 후 예정된 Summary 연결 | 두 검사 결과의 수집·판정 연결 보완 |
| 결과를 확인한 위치 | 같은 작업의 코드·검사 | 같은 작업의 단계별 대조 | 역할별 검사와 Integrator의 취합 | 개별 계약 검사와 통합 수용 검사 | 모델 응답 이후 하네스의 외부 검사 |

작업 수는 주 작업과 별도로 시작한 역할·구현 작업을 합친 값이다. 다섯 방식의 같은 `SummaryTest`와 `RequirementTest`가 모두 통과했다. M4의 추가 검사는 개별 함수와 통합 계약을 더 자세히 확인했고, M5의 빌드 변경은 두 공통 검사의 결과를 하네스가 읽는 위치로 연결했다.

### 단계 지정, 역할 인계, 병렬 구현의 효과

M1과 M2는 같은 표시 규칙을 완성했다. M2에서는 계획에 적은 항목을 구현과 마지막 검토에서 다시 대조하는 흐름이 드러났고, 구현 중 문구 수정의 시점도 확인할 수 있었다. 단계 지정의 효과는 이번 결과에서 이런 확인 순서의 명시성으로 나타났다.

M3는 역할마다 확인할 내용을 나누고 결과를 다음 역할로 넘겼다. Tester가 남긴 실행 예시 문제를 Integrator가 해결한 과정에서 미해결 사항과 근거 위치를 함께 전달하는 인계가 사용되었다. M4는 독립적인 두 정리 함수를 동시에 구현하고 Summary에서 합쳤다. 두 방식 모두 책임을 나눴지만, M3는 검토 관점과 인계 순서가 중심이었고 M4는 파일 소유와 병합 후 통합이 중심이었다.

이번 규모에서는 한 작업으로도 공통 결과를 완성했고, 분업한 방식에서는 역할 호출·인계 또는 브랜치 병합·통합 확인이 추가되었다. 이 관찰은 다음 작업에서 분업의 필요성을 판단할 근거가 된다. 독립적으로 바꿀 부분이 크고 반환 계약을 먼저 정할 수 있다면 M4의 분리가 유용하고, 서로 다른 검토 관점의 확인이 필요하다면 M3의 역할 인계가 적합하다. 작은 표시 규칙처럼 한 흐름에서 읽고 확인할 수 있는 과제에서는 단일 작업으로 처리한 결과도 함께 고려할 수 있다.

### M5의 외부 완료 판단에서 v2 개선으로

M5는 요청 파일에 정한 검사 명령을 하네스가 실행하고 결과를 모아 완료를 결정했다. 요구를 전달하는 맥락, 프로세스를 호출하는 연결부, 검사 결과를 읽는 부분, 복구·종료 정책이 실제 실행으로 이어졌다. 두 검사 중 하나라도 실행 근거가 부족하면 중단하는 조건까지 코드와 검사에 담겼다.

비교 결과를 다시 읽을 때 최초 M5 실행에는 하위 모델·추론 설정의 기록이 없었다. 이 정보가 있어야 같은 과제를 어떤 조건으로 요청했는지 재구성할 수 있다. Day 5에서는 이를 작업 입력과 호출·결과 기록에 연결하는 변경을 선택했다.

## Day 5 — 실행 조건을 전달하고 기록하는 하네스 v2

### 비교에서 도출한 변경과 적용 위치

v2의 변경 요구는 실행할 모델·추론 설정을 요청에서 선택하고, 하위 호출에 전달한 조건을 결과에 남기는 것이었다. 수용한 제안에 따라 5주차 `quality_demo/`의 기존 하네스에 반영하고 M5 과제로 연결을 확인했다.

| 구성 | 변경 전 | v2에서 바뀐 내용 |
|---|---|---|
| 작업 입력 `WorkRequest` | 작업 위치·요구·맥락·검사·복구 상한·시간 제한 | 선택적인 `execution` 설정 추가 |
| 하위 호출 `HarnessCli` | CLI 기본 설정으로 실행 | 지정한 모델·추론 설정을 명령 인자로 전달 |
| 결과 `result.json` | 실행 모드와 최종 상태·근거 | `requestedExecution`으로 요청 조건 기록 |
| 검사 연결 | 실제 프로세스 호출에 연결 | 대체 가능한 호출부로 설정 전달·복구 재사용 검사 |
| 실행 안내와 M5 입력 | 기본 설정을 사용한 요청 | 설정 형식 설명과 `m5-v2-work.json` 추가 |

두 검사 결과를 모으는 보완은 M5의 완료 판단을 연결했고, v2의 변경은 그 작업을 어떤 조건으로 요청했는지 남긴다. 기존 순서 엔진의 준비 → 실행 → 검사 → 복구 판단 흐름을 사용하면서 작업 입력과 실행 경계를 확장했다.

### 요청에서 선택한 설정 읽기

[WorkRequest.java](../week05-development-harness/quality_demo/src/main/java/lab/week05/harness/WorkRequest.java)에 `ExecutionSettings`를 추가했다. 이번 [v2 요청 파일](runs/M5/harness/m5-v2-work.json)에 사용한 값은 다음과 같다.

```json
"execution": {
  "model": "gpt-6-astra",
  "reasoningEffort": "low"
}
```

`execution`을 생략하면 기본 설정을 사용한다. 지정하면 `model`과 `reasoningEffort`를 함께 읽는다. 모델 식별자의 형식과 추론 설정값을 검사하고, 비어 있거나 일부만 있는 요청은 준비 단계에서 필요한 입력을 알린다. 기존 여섯 인자로 WorkRequest를 만드는 호출도 계속 사용할 수 있도록 생성자를 두고 실행 설정을 기본값으로 연결했다.

이 입력은 실행할 조건을 작업 요청과 함께 보관하는 역할을 한다. 요청 파일을 다시 읽으면 과제·검사·복구 범위와 실행 조건을 같은 위치에서 확인할 수 있다. 형식에 맞는 값을 읽은 뒤 실제 사용 가능 여부는 연결된 CLI가 처리한다.

### 최초 호출과 복구 호출에 같은 조건 전달

[HarnessCli.java](../week05-development-harness/quality_demo/src/main/java/lab/week05/harness/HarnessCli.java)의 `codexCommand()`는 실행 설정을 받아 다음 인자를 추가한다.

```java
command.addAll(List.of("--model", execution.model(), "-c",
    "model_reasoning_effort=" + execution.reasoningEffort()));
```

`processRunner()`는 요청의 같은 `execution` 객체를 최초 호출과 복구 호출에 사용한다. 시도마다 바뀌는 것은 모델 응답 파일 `agent-1.txt`, `agent-2.txt`와 검사 폴더 `check-1/`, `check-2/`다. 복구 요청의 본문에는 원래 과제와 새 실패 근거가 들어가고 실행 조건은 같은 요청에서 가져온다.

이렇게 연결하면 복구에서 수정할 내용은 실패 근거로 갱신하면서, 모델·추론 설정은 최초 요청의 선택을 이어 간다. 작업 내용의 변화와 실행 조건의 변화를 각각 확인할 수 있는 구조다.

### 결과에서 요청 조건 확인

`HarnessCli.run()`은 결과의 `requestedExecution`에 설정 출처와 값을 기록한다. 실제 v2 실행에서 확인한 부분은 다음과 같다.

```json
"requestedExecution": {
  "source": "EXPLICIT",
  "model": "gpt-6-astra",
  "reasoningEffort": "low"
}
```

설정을 생략하면 `source`는 `DEFAULTS`, 요청을 준비하지 못하면 `UNAVAILABLE`이 된다. 이 필드는 하네스가 명령에 전달하도록 받은 요청 조건을 뜻한다. [실행 안내](../week05-development-harness/quality_demo/harness/run.md#하위-실행-조건-지정)에 입력 형식과 결과 읽는 방법을 추가했다.

입력의 값과 결과의 기록은 `WorkRequest.execution()`에서 함께 가져온다. 호출부와 기록부가 같은 값을 사용하므로 요청을 읽은 뒤 명령을 만드는 과정과 결과를 작성하는 과정이 연결된다. 이 연결을 다음 검사에서 직접 확인했다.

### 실행 경계를 대체한 설정·복구 검사

실제 프로세스를 시작하는 `ProcessCall`과 검사를 실행하는 `VerifyCall`을 전달할 수 있도록 `HarnessCli.processRunner()`에 연결 지점을 두었다. 평소에는 기존 `ProcessRunner`와 `GradleVerifier`가 들어가고, 검사에서는 호출 명령을 저장하거나 원하는 검사 결과를 반환하는 함수를 넣는다.

[ExecutionSettingsTest.java](../week05-development-harness/quality_demo/src/test/java/lab/week05/harness/ExecutionSettingsTest.java)의 복구 사례는 첫 검사를 `빈 제목 실패`로 만들고 두 번째 검사를 통과시킨다. 그 흐름에서 다음 내용을 확인했다.

| 검사 상황 | 확인한 동작 |
|---|---|
| 명시적 설정으로 최초 작업 후 검사 실패 | 첫 호출 명령에 모델과 추론 설정이 포함됨 |
| 실패 근거를 받아 복구 | 두 번째 요청에 원래 과제와 `빈 제목` 실패 근거가 함께 들어가고, 같은 실행 설정이 전달됨 |
| 두 번째 검사 통과 | 시도별 응답·검사 경로를 사용하고 두 번째 시도에서 성공 |
| 실행 설정 생략 | 기본 명령으로 작업 → 검사 순서가 이어짐 |
| 명시적 설정과 기본 설정의 결과 기록 | 각각 `EXPLICIT`의 요청값과 `DEFAULTS`가 기록됨 |
| 설정 일부 누락·잘못된 값 | 모델 호출 전에 준비 단계에서 중단 |

이 검사는 코드의 어느 경계로 설정이 전달되는지와 실패 뒤 같은 조건이 재사용되는지를 확인했다. 기존 하네스의 정상 완료·복구 상한·실행 실패 처리 검사도 함께 통과했다. 검사 결과는 [하네스 검사 보고서](../week05-development-harness/quality_demo/build/reports/tests/test/index.html)에 있다.

### 같은 과제에서 실제 v2 연결 확인

M5의 완성된 코드를 사용해 설정 전달과 기존 정상 동작을 함께 확인했다. [v2 실행 맥락](runs/M5/harness/v2-context.md)을 요청에 추가해 현재 구현과 요구를 대조하도록 연결했다. M5 폴더에서 사용한 실제 명령은 Windows PowerShell과 macOS·Linux·WSL에서 같다.

```text
java -jar ../../../week05-development-harness/quality_demo/build/libs/development-harness.jar harness/m5-v2-work.json
```

하위 작업의 [응답](runs/M5/.local/development-harness/1cd8d140-cb42-4841-9217-810cf56672d9/agent-1.txt)은 기존 구현이 표시 규칙과 일치한다고 정리했다. 세 업무 파일은 그대로였고, 뒤이어 하네스가 새로 실행한 [기존 표시 검사](runs/M5/.local/development-harness/1cd8d140-cb42-4841-9217-810cf56672d9/check-1/test/TEST-lab.week10.SummaryTest.xml)와 [공개 수용 검사](runs/M5/.local/development-harness/1cd8d140-cb42-4841-9217-810cf56672d9/check-1/acceptance/TEST-lab.week10.RequirementTest.xml)가 통과했다.

[실제 결과](runs/M5/.local/development-harness/1cd8d140-cb42-4841-9217-810cf56672d9/result.json)는 첫 시도 성공이고, `requestedExecution`에 `gpt-6-astra`와 `low`가 명시적 설정으로 남았다. 실제 업무 실행은 정상 경로로 끝났으며, 복구 시 설정 재사용은 앞의 실행 경계 검사에서 확인했다.

v2에서는 작업 요청에 선택한 조건이 하위 호출과 실행 결과까지 이어졌다. 같은 하네스로 다른 조건을 선택할 때도 요청 파일을 바꾸고 결과의 요청값과 검사 근거를 함께 확인할 수 있다. 이번 개선은 비교 과정에서 필요해진 정보를 기존 엔진의 입력·호출·기록에 연결한 변경이다.
