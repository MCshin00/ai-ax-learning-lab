# 상품 조회·구매 검토와 로컬 초안 MCP

현재 상품 자료는 노트 `NOTE-01` 3500원·재고 있음, 펜 `PEN-02` 1500원·품절입니다. 같은 서버의 조회·계산 기능을 Codex에서 사용하고, 사람이 Inspector에서 내용을 확인한 뒤 초안 생성·편집·복구·취소를 직접 실행합니다.

## 실행

IDE에서 이 폴더를 Gradle 프로젝트로 열고 JDK 17 이상을 선택합니다. Gradle 창의 `installDist`는 실행 프로그램과 의존성을 준비하고, `test`는 입력·계산·파일 보존·복구 검사를 실행합니다. Inspector에는 Node.js 22.19.0 이상이 필요합니다.

Windows PowerShell:

```powershell
.\gradlew.bat installDist
npx @modelcontextprotocol/inspector@2.5.0 java -cp "build/install/learning-catalog/lib/*" lab.week03.CatalogServer
```

macOS·Linux·WSL:

```bash
bash ./gradlew installDist
npx @modelcontextprotocol/inspector@2.5.0 java -cp "build/install/learning-catalog/lib/*" lab.week03.CatalogServer
```

Inspector 2.5.0의 **Servers → java 연결 → Tools**에서 도구를 선택합니다. `get_product`에 `NOTE-01`·`PEN-02`·`UNKNOWN`을 넣고 정상 데이터·품절·없는 ID 오류를 구별합니다. Resource의 `catalog://help`는 자료 범위 안내, Prompt의 `explain_product`는 조회를 요청하는 문구를 반환합니다.

코드를 변경하면 `installDist`를 다시 실행하고 서버를 재연결합니다. `java` 프로세스의 표준 출력은 MCP 메시지용이며 일반 로그는 표준 오류로 보냅니다. Inspector가 출력하는 세션 URL은 공유 기록에 넣지 않습니다.

## Codex의 조회 연결

command는 `java`, arguments는 `-cp`, `<프로젝트>/build/install/learning-catalog/lib/*`, `lab.week03.CatalogServer`입니다. 실제 경로는 로컬 설정에서만 사용합니다. 이미 연결된 `learning-catalog`는 현재 설정을 사용합니다. 새 환경에서 등록한다면 다음 명령을 사용합니다.

Windows PowerShell:

```powershell
codex mcp list
$CatalogLibraries = Join-Path (Get-Location).Path "build/install/learning-catalog/lib/*"
codex mcp add learning-catalog -- java -cp "$CatalogLibraries" lab.week03.CatalogServer
```

macOS·Linux·WSL:

```bash
codex mcp list
catalog_libraries="$(pwd)/build/install/learning-catalog/lib/*"
codex mcp add learning-catalog -- java -cp "$catalog_libraries" lab.week03.CatalogServer
```

같은 등록 이름이 있으면 기존 설정을 확인하고 갱신합니다. 조회 프로그램에는 `get_product`·`find_products`·`review_purchase` 세 도구만 노출합니다.

## 조회와 구매 검토

| 도구 | 입력과 동작 |
|---|---|
| `get_product` | 정확한 `product_id` 한 개를 조회. 없는 ID는 오류 |
| `find_products` | `max_price_krw` 이하 가격과 재고 조건으로 후보 조회. `in_stock_only` 기본값은 `true` |
| `review_purchase` | 상품 ID·수량 목록과 예산으로 소계·합계·초과액·품절 ID 계산 |

예산은 0 이상 정수, 수량은 1 이상 정수입니다. 문자열·실수·불리언을 금액으로 자동 변환하지 않습니다. 존재하지 않는 상품이 하나라도 있으면 전체 견적을 거부합니다. 반복된 상품 ID는 입력 행별로 계산하고 품절 ID는 처음 등장한 순서로 한 번씩 반환합니다. 예산과 총액이 같으면 초과가 아닙니다. 계산할 수 있는 정수 범위를 넘으면 잘못된 금액을 반환하지 않고 오류로 알립니다.

```json
{
  "items": [
    {"product_id": "NOTE-01", "quantity": 2},
    {"product_id": "PEN-02", "quantity": 1}
  ],
  "budget_krw": 8000
}
```

현재 자료의 예상 결과는 총액 8500원·초과액 500원·품절 목록 `["PEN-02"]`입니다. 품절은 조회 실패가 아니며 견적 계산에도 포함됩니다. 모든 도구는 텍스트와 구조화된 결과를 반환합니다. 후보 조회와 이력 목록은 `structuredContent.result`에 목록을 담습니다.

## 초안 생성·편집용 Inspector

프로젝트 폴더에서 다음을 실행합니다. 두 OS에서 같은 명령입니다.

```text
npx @modelcontextprotocol/inspector@2.5.0 java -cp "build/install/learning-catalog/lib/*" lab.week03.InspectorServer
```

기본 저장 위치는 주차 폴더의 `.local/drafts-java/`입니다. 다른 위치에서 서버를 시작할 때는 마지막 인자에 사용할 초안 폴더를 전달합니다. 저장 위치는 실행 설정으로 정하고 도구 입력은 경로 대신 제한된 초안 ID를 받습니다. 현재 초안 `purchase-001`·`purchase-change-001`도 이 위치에서 계속 편집할 수 있습니다.

기존 기록이 있는 `.local/drafts/`는 그 기록과 함께 보관합니다. 이력은 저장소에 연결된 파일 버전 형식과 일치해야 합니다. 다른 형식의 이력 DB는 `HISTORY_FORMAT_UNSUPPORTED`로 거부하므로 기존 DB 파일을 새 저장소에 복사하지 않습니다.

Inspector 프로그램은 조회 세 도구와 다음 여덟 도구를 제공합니다.

| 도구 | 동작 |
|---|---|
| `preview_purchase_draft` | `request_id`·상품 목록·예산으로 저장할 내용과 경로를 미리보기. 파일 생성 없음 |
| `save_purchase_draft` | 사람이 확인한 `preview_id`의 스냅샷을 새 파일로 저장 |
| `preview_draft_edit` | `draft_id`·`operation_id`·상품 목록·예산으로 변경 전후 미리보기. 메모 등 다른 필드 보존 |
| `apply_draft_change` | 사람이 확인한 편집·취소 미리보기의 `preview_id` 적용 |
| `get_draft_operation` | `operation_id`의 영속 상태 조회 |
| `resume_draft_operation` | 이미 적용 요청이 기록된 작업을 그 내용 그대로 재개 |
| `preview_draft_undo` | 가장 최근에 적용된 편집의 직전 파일 복원 미리보기 |
| `get_draft_history` | 초안의 편집·취소 이력을 순서대로 조회 |

`preview_id`는 내용의 식별자이며 사람 승인 증거가 아닙니다. 미리보기를 읽은 사람이 Inspector에서 저장·적용을 직접 호출합니다. 이 프로그램을 모델의 일반 조회 연결에 등록하지 않습니다.

### 새 초안 생성

위 구매 검토 입력에 `"request_id": "purchase-new-001"`을 추가해 `preview_purchase_draft`를 호출합니다. 내용·위치·계산 완전 여부를 확인한 뒤 응답의 `preview_id`만 `save_purchase_draft`에 전달합니다.

미리본 시점의 견적을 저장하므로 단가나 수량을 바꾸려면 새 미리보기를 확인합니다. 같은 요청의 새 미리보기는 이전 ID를 무효화합니다. 같은 내용 재요청은 `already_saved`이며 파일·시각을 유지합니다. 다른 내용이나 사람이 고친 파일은 `DRAFT_CONFLICT`로 보존합니다.

초안 ID는 소문자·숫자·하이픈·밑줄 1~64자이고 첫 글자는 소문자 또는 숫자입니다. 경로 이동, 기기 예약 이름, 링크·junction 경로는 거부합니다. 임시 파일을 완성·동기화한 뒤 비어 있는 최종 이름으로만 게시하여 기존 초안을 덮어쓰지 않습니다.

### 편집·재개·취소

`preview_draft_edit`에 기존 초안 ID, 새 `operation_id`, 새 상품 목록·예산을 전달합니다. `before`·`after`를 확인한 뒤 `apply_draft_change`를 호출합니다. 견적과 편집 식별자만 갱신하며 메모 같은 다른 필드는 유지합니다.

미리보기와 적용 사이에 파일 내용·수정 시각·파일 식별 정보가 달라지면 `VERSION_CONFLICT`로 거부합니다. 같은 `operation_id`를 다른 초안이나 내용에 재사용하면 `OPERATION_CONFLICT`입니다.

SQLite의 `.history/changes.sqlite3`에는 변경 전후 바이트와 요청 상태를 남깁니다. 파일 교체 전 `prepared`, 게시할 파일 식별 정보, 교체 후 `applied`를 순서대로 확정합니다. DB 트랜잭션과 파일 교체는 별개이므로 중단 후 실제 파일을 이 정보와 대조합니다.

| 상태 | 의미와 다음 동작 |
|---|---|
| `prepared` | 적용 요청이 기록됨. 상태를 확인한 뒤 같은 ID로 명시적 재개 |
| `applied` | 과거에 해당 변경 적용 완료 |
| `already_applied` | 완료된 요청의 재호출. 파일을 다시 쓰지 않음 |
| `conflict` | 기록된 전후 버전과 현재 파일이 다름. 파일 보존 후 새 요청으로 검토 |

`current_matches`는 그 작업의 적용 결과가 현재 파일인지 나타냅니다. 이전 요청이 `applied`여도 이후 편집이 있으면 `false`입니다. `cleanup_pending`은 임시 파일 정리 상태이며 적용 완료와 별도입니다.

취소는 `preview_draft_undo`에 `target_operation_id`와 새 취소 `operation_id`를 보내 미리본 뒤 적용합니다. 취소 대상이 마지막 편집이고 현재 파일도 그 편집 결과일 때만 직전 바이트를 복원합니다. 후속 편집·취소나 외부 재저장까지 되돌리지 않습니다.

이 실습은 한 서버에서 쓰기 요청을 순서대로 처리합니다. 외부 편집기의 저장을 마친 뒤 적용·재개합니다. 여러 서버와 외부 편집기의 동시 쓰기를 보장하는 분산 저장소는 아닙니다.

## 주요 파일

| 파일 | 역할 |
|---|---|
| `CatalogService.java` | 상품 자료·후보 조회·구매 검토 업무 규칙 |
| `CatalogServer.java` | 공통 MCP 등록·응답과 조회 프로그램 |
| `InspectorServer.java` | 공개 입력·출력 스키마, 쓰기 기능 등록 |
| `DraftStore.java` | 입력·경로 검사, 스냅샷·새 파일의 멱등 저장 |
| `DraftChanges.java` | 버전 비교·SQLite 이력·편집·복구·취소 |
| `FileIdentity.java` | 파일 교체를 구분하는 운영체제 파일 식별 어댑터 |
| `Json.java` | JSON 변환·공통 입력 확인 |
| `src/test/java/lab/week03/` | 업무·보존·프로세스 중단·실제 MCP stdio 검사 |

구현 파일은 `src/main/java/lab/week03/`에 있습니다. JDK 기준과 공식 MCP SDK·SQLite JDBC·JUnit·파일 식별 의존성은 `build.gradle`에 지정합니다.

## 검사와 결과 확인

Windows PowerShell: `.\gradlew.bat test`

macOS·Linux·WSL: `bash ./gradlew test`

JUnit은 임시 폴더로 파일·메모·재요청·충돌·실패를 확인합니다. 별도 프로세스의 부분 기록·파일 교체 전후·DB 커밋 전 중단을 재현하고 재개와 정확한 취소를 검증합니다. `McpStdioTest`는 공식 SDK 클라이언트로 실제 서버를 시작해 목록·스키마·호출·재시작을 확인합니다. 이 결과와 Inspector 화면·Codex 모델의 도구 선택 결과는 구별합니다. 학습 원리·실제 요청과 응답은 [주차 노트](../mcp-use.md)에 연결합니다.

[공식 MCP Java SDK](https://java.sdk.modelcontextprotocol.io/latest/server/), [SQLite의 커밋과 복구](https://www.sqlite.org/atomiccommit.html)
