# 바로 연결하는 예제 MCP

가상의 상품 두 개를 조회하고 구매 금액을 계산하는 로컬 서버입니다. Inspector용 실행에서는 미리본 견적을 새 로컬 초안으로 저장하고, 기존 초안을 편집·복구·취소할 수 있습니다. 외부 계정·서비스는 사용하지 않습니다. Day별 학습과 요청 본문은 [주차 README](../README.md)에 있습니다.

## 1. Inspector로 실행

아래는 조회·계산용 연결입니다. 초안 저장과 선택 심화는 [Inspector 전용 실행](#초안을-미리보고-저장하기--inspector-전용)의 명령을 사용합니다.

IDE와 터미널의 작업 폴더는 생성된 학습 저장소의 `week03-mcp-integration/learning_lab_server/`입니다. 현재 폴더에 `pyproject.toml`과 `uv.lock`이 있는지 확인합니다. Python 3.11+, uv, **Node.js 22.19.0 이상**이 필요합니다. `node --version`과 `uv --version`으로 먼저 확인하고, Node가 기준보다 낮으면 업데이트 후 새 터미널에서 다시 확인합니다.

Windows PowerShell·macOS·Linux·WSL 공통:

```text
uv sync --locked
npx @modelcontextprotocol/inspector@2.5.0 uv run --locked ai-ax-learning-lab-mcp
```

Inspector는 시작한 작업 폴더에서 서버를 실행합니다. 경로를 인자 문자열로 전달하지 않아 Windows의 백슬래시·공백이 잘못 해석되는 문제를 피합니다. `uv sync`가 성공한 뒤 Inspector를 시작하고, npm의 설치 질문이 나오면 패키지 이름·버전을 확인해 진행합니다. 브라우저를 사용하는 동안 터미널을 켜 둡니다.

아래는 **Inspector 2.5.0**의 순서입니다. 출력된 로컬 URL을 열고 **Servers의 `uv` 연결 스위치 → Connected 확인 → Tools → get_product → Product Id에 `NOTE-01` 입력 → Execute Tool**로 진행합니다. Results에서 가격 3500·재고 true를 확인합니다. **Close results**로 입력 화면에 돌아와 `PEN-02`와 `UNKNOWN`도 호출합니다. 이 버전에는 별도로 누를 `List Tools` 버튼이 없습니다.

Tools가 안 보이면 서버 연결 상태와 Console 오류부터 확인합니다. v1 경고가 보이면 이전 실행을 `Ctrl+C`로 종료하고 Node 버전 확인 후 위 명령으로 다시 시작합니다. URL의 세션 토큰은 공유 기록에 넣지 않습니다. 환경 준비·화면별 조작·오류 대응·완료 기준은 [주차 README의 Day 1](../README.md#day-1--inspector에서-성공과-오류-보기)에 있습니다.

## 2. Codex에서 사용

앱의 MCP 설정에서 command는 `uv`, arguments는 아래 등록 명령처럼 `--directory`와 프로젝트 절대 경로를 포함해 입력합니다. Codex는 다른 작업 폴더에서 시작할 수 있기 때문입니다. 등록 UI가 없으면 이 프로젝트의 터미널에서 다음을 실행합니다. 먼저 목록에서 `learning-catalog`가 이미 사용 중인지 확인하고 충돌하면 새 이름을 고릅니다.

Windows PowerShell:

```powershell
codex mcp list
$CatalogProject = (Get-Location).Path
codex mcp add learning-catalog -- uv --directory "$CatalogProject" run --locked ai-ax-learning-lab-mcp
```

macOS·Linux·WSL:

```bash
codex mcp list
catalog_project="$(pwd)"
codex mcp add learning-catalog -- uv --directory "$catalog_project" run --locked ai-ax-learning-lab-mcp
```

새 Codex 작업에서 “learning-catalog의 get_product로 NOTE-01의 가격과 재고를 확인해 줘”를 직접 보냅니다. 답변 내용과 실제 도구 호출을 함께 확인합니다. 코드 파일을 읽어서 대답한 것은 MCP 호출 성공이 아닙니다. 실습용으로 새로 등록한 연결을 정리할 때만 `codex mcp remove learning-catalog`를 씁니다.

## 예산·재고 조건으로 상품 찾기

`get_product`는 상품 ID 한 건을 조회하고, `find_products`는 기존 상품 사전에서 조건에 맞는 후보를 찾습니다.

| 입력 | 의미 |
|---|---|
| `max_price_krw` | 필수, 0 이상 정수. 해당 금액 이하인 상품을 포함합니다. |
| `in_stock_only` | 선택, 기본값 `true`. `false`이면 품절 상품도 포함합니다. |

반환값은 상품의 ID·이름·가격·재고 목록이며, MCP의 `structuredContent.result`에서 확인합니다. 조건에 맞는 상품이 없으면 성공한 빈 목록이고, 음수 예산이나 잘못된 입력 형식은 오류입니다. 상품을 주문하거나 재고를 바꾸지 않습니다.

코드를 바꾸기 전에 연결한 Inspector가 있다면 Servers의 연결 스위치를 껐다 켜 새 서버 프로세스로 연결합니다. Tools에 `find_products`가 없으면 Inspector 실행을 종료하고 위 실행 명령으로 다시 시작합니다.

**Tools → find_products → Max Price Krw에 `3500`, In Stock Only를 `true`로 설정 → Execute Tool** 순서로 호출합니다. 현재 자료에서는 `NOTE-01`만 반환되고 가격은 3500, 재고는 `true`여야 합니다. Inspector가 표시하는 필드 이름의 원래 인자는 위 표에서 확인할 수 있습니다.

| 호출 인자 | 현재 자료의 예상 결과 |
|---|---|
| `{"max_price_krw":3000}` | 기본값 적용, 빈 목록 |
| `{"max_price_krw":3500}` | 기본값 적용, `NOTE-01` |
| `{"max_price_krw":2000,"in_stock_only":true}` | 빈 목록 |
| `{"max_price_krw":2000,"in_stock_only":false}` | 품절인 `PEN-02` 포함 |
| `{"max_price_krw":-1}` | 예산 입력 오류 |

## 여러 상품의 구매 금액 검토하기

`review_purchase`는 기존 `CATALOG`의 현재 단가로 상품별 소계와 예상 총액을 계산합니다. 주문이나 재고 변경은 수행하지 않습니다.

| 입력 | 의미 |
|---|---|
| `items` | 필수, 비어 있지 않은 상품 목록. 각 항목은 `product_id`와 `quantity`를 받습니다. |
| `items[].product_id` | 정확한 상품 ID 문자열. 하나라도 없는 ID이면 전체 요청이 오류가 됩니다. |
| `items[].quantity` | 필수, 1 이상 정수. 문자열·실수·불리언을 수량으로 변환하지 않습니다. |
| `budget_krw` | 필수, 0 이상 정수. 문자열·실수·불리언을 예산으로 변환하지 않습니다. |

호출 예시:

```json
{
  "items": [
    {"product_id": "NOTE-01", "quantity": 2},
    {"product_id": "PEN-02", "quantity": 1}
  ],
  "budget_krw": 8000
}
```

반환 객체는 MCP의 `structuredContent`에서 확인합니다.

| 결과 필드 | 의미와 위 입력의 예상값 |
|---|---|
| `items` | 입력 순서의 상품 ID·이름·단가(`price_krw`)·재고 여부(`in_stock`)·수량(`quantity`)·소계(`subtotal_krw`). 노트는 3500 × 2 = 7000원, 펜은 1500 × 1 = 1500원입니다. |
| `budget_krw` | 전달한 예산 8000원 |
| `total_krw` | 품절 상품을 포함한 예상 총액 8500원 |
| `over_budget` | 총액이 예산보다 큰지 여부. 이 예시는 `true`이며, 총액과 예산이 같을 때는 `false`입니다. |
| `over_budget_krw` | 초과한 금액. 이 예시는 500원이며, 초과하지 않으면 0원입니다. |
| `out_of_stock_product_ids` | 품절 상품 ID 목록. 이 예시는 `["PEN-02"]`입니다. |

같은 상품 ID를 여러 번 보내면 각 입력 줄을 유지하고 모두 합산합니다. 품절 ID 목록에는 같은 ID를 한 번만 표시합니다. 상품 항목에 단가 등 추가 필드를 보내면 입력 오류로 처리하며, 가격은 서버의 상품 사전에서 읽습니다. 빈 목록·잘못된 수량·예산·없는 ID는 `isError: true`로 끝나고 견적 객체를 반환하지 않습니다.

Inspector의 서버를 재연결한 뒤 **Tools → review_purchase**에서 위 `items`와 `budget_krw`를 입력해 호출합니다. 정상 결과 다음에는 예산 8500원(초과 없음), 8499원(1원 초과), `UNKNOWN`이 섞인 목록(전체 오류), 수량 0(입력 오류)을 대조할 수 있습니다. `in_stock`은 재고 여부만 나타내므로 요청 수량만큼의 실제 재고를 보장하는 값은 아닙니다.

## 초안을 미리보고 저장하기 — Inspector 전용

기본 서버 명령 `ai-ax-learning-lab-mcp`는 Codex용으로 기존 조회·계산 도구만 제공합니다. 아래 Inspector용 진입점은 같은 상품·계산 코드를 사용하면서 기본 저장 도구 `preview_purchase_draft`·`save_purchase_draft`와 아래 선택 심화의 여섯 도구를 추가합니다. Codex의 기존 등록 명령은 그대로 사용합니다.

IDE에서 이 프로젝트 폴더를 열고 터미널도 여기서 시작합니다. Windows PowerShell·macOS·Linux·WSL 공통:

```text
npx @modelcontextprotocol/inspector@2.5.0 uv run --locked python -B -m learning_lab_mcp.inspector
```

기존 Inspector가 실행 중이면 해당 터미널에서 종료한 뒤 이 명령으로 다시 시작합니다. 출력된 로컬 URL을 열고 Servers에서 연결하면 서버 이름은 `learning-catalog-inspector`입니다. URL에 포함된 세션 토큰은 공유하지 않습니다. 이 실습에서는 쓰기 서버 하나를 실행하며, 서버 안의 미리보기·저장 요청은 잠금으로 순서대로 처리합니다.

### 1. 미리보기 읽기

Tools의 `preview_purchase_draft`에서 다음 인자를 입력합니다. `items`는 상품 ID와 수량을 묶은 목록입니다.

```json
{
  "request_id": "purchase-001",
  "items": [
    {"product_id": "NOTE-01", "quantity": 2},
    {"product_id": "PEN-02", "quantity": 1}
  ],
  "budget_krw": 8000
}
```

현재 자료의 예상은 노트 소계 7000원, 펜 소계 1500원, 총액 8500원, 예산 500원 초과, 품절 `PEN-02`입니다. `quote`에서 상품·수량·금액·품절 표시를, `estimate_complete`에서 전체 입력 상품이 계산됐는지를 확인합니다. 없는 상품이나 잘못된 입력은 미리보기 오류로 처리됩니다.

`path`는 주차 폴더의 `.local/drafts/purchase-001.json`을 가리켜야 합니다. 미리보기는 초안 파일이나 저장 폴더를 만들지 않습니다. 요청 ID는 1~64자의 영문 소문자·숫자·하이픈·밑줄이며 첫 글자는 영문 소문자나 숫자입니다. 경로와 Windows 예약 장치 이름은 받지 않습니다.

`pricing_policy: "preview_snapshot"`은 미리보기 당시의 견적을 그대로 저장한다는 뜻입니다. 가격·수량을 새로 반영하려면 미리보기를 다시 생성해 읽습니다. 같은 요청 ID의 새 미리보기는 이전 `preview_id`를 무효화합니다.

### 2. 확인한 미리보기 저장하기

내용과 위치가 맞으면 같은 Inspector 연결에서 `save_purchase_draft`를 직접 실행합니다. 입력은 방금 응답에서 받은 실제 `preview_id` 한 개입니다. 아래 자리표시자는 실제 값으로 바꿉니다.

```json
{"preview_id": "방금_미리보기에서_받은_ID"}
```

`status: "saved"`이면 반환된 `path`의 파일을 IDE에서 열어 미리본 내용과 대조합니다. 파일의 `quote`에는 확인한 견적이, `request_id`에는 저장 요청의 식별자가 들어 있습니다. 저장을 원하지 않으면 저장 도구를 실행하지 않습니다. `approved=true` 입력은 사용하지 않으며, 미리보기 ID는 사람이 승인했다는 증명이 아닙니다. 사람의 확인은 Inspector에서 내용을 읽고 저장을 직접 실행하는 절차로 구분합니다.

미리보기는 해당 서버 프로세스의 메모리에 있으므로 저장 전에는 연결을 유지합니다. 서버를 다시 시작했다면 새 미리보기를 생성해 확인합니다.

### 3. 재요청·변경·충돌 확인하기

- 같은 `preview_id`로 다시 저장하면 `already_saved`와 같은 경로가 반환되며 파일이 늘거나 바뀌지 않습니다.
- 같은 요청 ID·같은 입력으로 새 미리보기를 만든 뒤 저장해도 실제 파일 내용이 같으면 `already_saved`입니다.
- 같은 요청 ID에 예산이나 수량을 바꿔 저장하면 `DRAFT_CONFLICT`가 나고 기존 파일이 보존됩니다. 별개의 새 초안을 원할 때는 새 요청 ID를 사용합니다.
- 미리보기 이후 다른 미리보기를 만든 뒤 이전 ID로 저장하면 `INVALID_PREVIEW`가 납니다.
- 연습 초안 파일을 직접 수정한 뒤 같은 내용으로 다시 저장해도 기존 파일을 덮어쓰지 않고 충돌을 알립니다.

완성된 임시 파일을 `os.link`로 게시하므로 저장 폴더의 파일시스템은 하드링크를 지원해야 합니다. 지원하지 않거나 파일 쓰기가 실패하면 `SAVE_FAILED`로 끝나며 기존 파일을 대체하지 않습니다. `cleanup_pending: true`는 초안은 저장됐지만 임시 파일 정리가 남았다는 뜻입니다. `.pending-*.tmp`는 정상 초안으로 사용하지 않습니다.

기본 저장 도구로 새 초안 생성과 재요청·충돌 처리를 확인합니다. 기존 파일을 바꾸려면 아래 선택 심화의 편집 도구로 변경 전·후 내용을 다시 확인합니다. 미리보기를 확인하고 저장한 실제 입력·파일 결과는 [학습 기록](../mcp-use.md)에 이어 남깁니다.

## 기존 초안 편집·복구·취소 — 선택 심화

위의 Inspector용 실행 명령을 그대로 사용합니다. 구현 갱신 뒤에는 기존 Inspector를 실행한 터미널에서 종료하고 다시 시작한 다음 연결합니다. Tools에는 기본 다섯 개와 다음 여섯 개, 총 **열한 개**가 표시됩니다.

| 도구 | 입력과 역할 |
|---|---|
| `preview_draft_edit` | 기존 초안 ID·편집 요청 ID·상품·수량·예산으로 변경 전·후 내용 확인 |
| `apply_draft_change` | 방금 읽은 편집 또는 취소 미리보기 ID로 실제 변경 적용 |
| `get_draft_operation` | 편집·취소 요청 ID로 저장된 처리 상태 확인 |
| `resume_draft_operation` | 앞서 적용을 요청해 기록된 작업을 재시작·오류 후 재개 |
| `preview_draft_undo` | 취소할 편집 요청 ID와 새 취소 요청 ID로 복원 내용 확인 |
| `get_draft_history` | 초안 ID로 편집·취소 이력 확인 |

기본 생성 도구의 `request_id`는 파일 이름에 연결됩니다. 심화의 `draft_id`가 이 ID를 가리킵니다. `operation_id`는 **각 편집·취소 요청**을 구별하며 같은 ID를 다른 변경에 재사용하지 않습니다. `preview_id`는 이번 연결에서 읽은 미리보기이며 서버 재시작 뒤에는 새로 받아야 합니다.

### 1. 기존 초안을 편집하기

기본 실습의 `purchase-change-001.json`은 노트 한 개·펜 한 개, 총액 5000원입니다. 다른 값으로 실습했다면 먼저 실제 파일 내용을 확인합니다. `preview_draft_edit`에 입력합니다.

```json
{
  "draft_id": "purchase-change-001",
  "operation_id": "edit-001",
  "items": [
    {"product_id": "NOTE-01", "quantity": 2},
    {"product_id": "PEN-02", "quantity": 1}
  ],
  "budget_krw": 8000
}
```

예상은 `before.quote.total_krw: 5000`, `after.quote.total_krw: 8500`, 변경 후 예산 초과액 500원입니다. 상품·수량·금액·품절과 `path`, 기존 메모가 유지됐는지 읽습니다. `after.revision`은 `edit-001`입니다. `operation_state: "new"`이면 아직 적용 요청이 기록되지 않은 미리보기입니다. 미리보기만으로 초안이나 이력 파일을 바꾸지 않습니다.

확인한 뒤 `apply_draft_change`에 실제 응답의 ID를 넣습니다.

```json
{"preview_id": "방금_읽은_미리보기_ID"}
```

예상 응답은 `status: "applied"`, `total_krw: 8500`, `current_matches: true`, `cleanup_pending: false`입니다. 같은 JSON 파일을 열어 변경 후 내용과 대조합니다. `.local/drafts/.history/changes.sqlite3`도 첫 적용 요청에서 생성됩니다. 이력 DB 역시 Git에서 제외되는 저장 루트 안에 있습니다.

### 2. 같은 변경을 다시 요청하고 재시작하기

같은 `preview_id`로 `apply_draft_change`를 다시 실행하면 `already_applied`이며 파일이 다시 쓰이지 않습니다. 이어서 Inspector를 종료·재실행하고 연결합니다. `get_draft_operation`에 다음을 입력합니다.

```json
{"operation_id": "edit-001"}
```

기대 결과는 `status: "applied"`입니다. 같은 입력으로 `resume_draft_operation`을 실행하면 `already_applied`입니다. 이전 `preview_id`가 없어도 기록된 요청의 결과를 찾을 수 있습니다.

`applied`는 그 작업의 과거 적용 완료를 뜻합니다. **그 뒤 다른 편집이 있었다면 `current_matches`는 `false`**입니다. 이 경우에도 예전 요청을 다시 실행해 최신 내용을 덮어쓰지 않습니다.

### 3. 미리보기 뒤 직접 수정한 파일 보존

별도 요청 ID `edit-conflict-001`로 편집 미리보기를 만듭니다. 같은 `purchase-change-001`을 대상으로 노트 세 개·펜 한 개, 예산 8000원을 입력하면 변경 후 총액은 12000원입니다.

미리보기 뒤 VS Code에서 그 초안의 맨 위 `{` 다음에 아래 필드를 추가하고 저장을 끝냅니다.

```text
"manual_note": "심화 충돌 보존 확인",
```

이미 같은 키가 있으면 값을 수정하고 중복 키는 만들지 않습니다. Inspector에서 해당 미리보기 ID로 `apply_draft_change`를 호출합니다. 예상은 `VERSION_CONFLICT`이며 메모와 기존 견적이 보존돼야 합니다. 저장 직전에 파일이 달라지면 다시 미리보고 별도의 새 요청 ID로 진행합니다.

### 4. 취소와 이후 수정의 보존

위의 직접 수정 이후 `preview_draft_undo`에서 `target_operation_id: "edit-001"`, `operation_id: "undo-old-001"`을 입력하면 `UNDO_CONFLICT`가 예상됩니다. 취소하려는 변경 뒤에 추가한 메모를 지우면 안 되기 때문입니다.

정상 취소는 현재 파일을 바탕으로 새 편집 `edit-002`를 만든 뒤 확인합니다. 노트 세 개·펜 한 개, 예산 8000원으로 편집 미리보기와 적용을 진행하면 총액 12000원이며 메모는 유지됩니다. 이어서 `preview_draft_undo`에 입력합니다.

```json
{
  "target_operation_id": "edit-002",
  "operation_id": "undo-002"
}
```

미리보기의 `before`는 12000원, `after`는 해당 편집 직전의 8500원 견적과 메모여야 합니다. 확인한 취소 미리보기 ID를 `apply_draft_change`에 넣으면 파일이 편집 직전 내용으로 복원됩니다. 취소 결과와 실제 파일·저장된 이력을 대조하면 직접 실습을 마칩니다. `get_draft_history`는 이력을 Inspector에서 보고 싶을 때 사용하는 조회 도구입니다.

취소는 마지막 편집 **한 건의 직전 파일**을 복원합니다. 초안을 삭제하거나 전체 이력을 처음 상태로 되돌리는 기능이 아닙니다. 이미 적용한 취소도 같은 요청 ID로 재개하면 중복 실행되지 않습니다.

### 5. 중단 상태와 복구 결과 읽기

실제 프로세스 중단은 `tests/test_draft_changes.py`에서 임시 초안과 별도 프로세스로 검증합니다. 중단 시점은 이력 준비 후·임시 파일 일부 작성 후·초안 교체 후·완료 기록 커밋 직전입니다. 이미 확인한 결과는 [학습 기록](../mcp-use.md)에 있으며, 아래 표는 그 복구 정책을 읽는 자료입니다. 코드를 변경해 다시 검증할 때는 아래 유지보수용 검사 명령을 사용합니다.

| 조회한 상태 | 의미와 다음 동작 |
|---|---|
| `prepared` | 적용 요청은 기록됐지만 완료 여부가 확정되지 않음. 같은 요청 ID로 `resume_draft_operation` 실행 |
| `applied` | 적용 완료가 기록됨. 재개는 `already_applied`를 반환 |
| `conflict` | 현재 파일이 기록된 버전과 달라 보존함. 내용을 확인한 뒤 새 요청 ID로 다시 미리보기 |
| `UNKNOWN_OPERATION` | 적용 요청 기록이 없음. 미리보기만 만든 작업은 재개할 수 없음 |

복구는 파일이 기록된 변경 전 버전이면 정확히 그 변경을 적용하고, 이미 변경 후 내용·파일 식별 정보와 같으면 파일을 다시 쓰지 않고 완료 기록만 확정합니다. 어느 쪽도 아니면 `RECOVERY_CONFLICT`로 현재 파일을 보존합니다. 내용이 같아도 외부 프로그램이 다시 저장해 파일 버전이 달라지면 충돌입니다.

`HISTORY_UNAVAILABLE`은 이력 DB를 읽거나 쓸 수 없다는 뜻입니다. 프로세스 중단 뒤 DB 복구가 필요한 경우에는 알려진 작업 ID로 재개하고, 저장 장치 오류라면 먼저 그 원인을 확인합니다. `cleanup_pending: true`는 이력에 연결된 임시 파일 정리가 남았다는 뜻입니다. 임시 파일은 정상 초안으로 읽지 않습니다.

이 실습은 한 로컬 서버를 사용하며 외부 편집기의 저장이 끝난 뒤 적용·재개합니다. 외부 편집기는 서버 잠금에 참여하지 않으므로 두 프로그램이 정확히 동시에 파일을 교체하는 조건까지 검증한 것으로 보지 않습니다. 적용 버튼을 누르기 전에 읽은 내용과 실제 결과, 이해한 정책의 이유는 [학습 기록](../mcp-use.md)에 이어 남깁니다.

## 주요 파일

- `src/learning_lab_mcp/server.py`: 상품 데이터·계산 함수와 공통 서버 구성.
- `src/learning_lab_mcp/inspector.py`: Inspector용 기본 저장·심화 편집 도구와 실행 진입점.
- `src/learning_lab_mcp/drafts.py`: 메모리 스냅샷, 경로 검사, 재요청·충돌 판별과 새 파일 생성.
- `src/learning_lab_mcp/changes.py`: 기존 파일의 버전 검사, SQLite 변경 이력, 중단 복구와 마지막 편집 취소.
- `pyproject.toml`, `uv.lock`: 실행 명령·의존성과 설치 버전.
- `tests/`: 제공 서버의 응답을 확인하는 유지보수용 검사.
- `../mcp-use.md`: 호출 결과·구조 분석·수정 판단을 이어 쓰는 주차 노트.

## 예제 유지보수용 확인

예제를 바꿨을 때 이 프로젝트에서 실행합니다. 모든 OS에서 uv 안의 Python을 사용합니다.

```text
uv run --locked python -B -m unittest discover -s tests -v
```

조회·계산 검사는 SDK의 메모리 연결이며, 저장·편집 테스트에는 별도 서버 프로세스의 stdio 호출과 임시 파일 검증도 포함됩니다. 심화 검사는 편집·취소 중 실제 프로세스 종료와 재시작 후 복구도 확인합니다. 모든 저장 검사는 임시 폴더를 사용합니다. 자동 테스트가 사람의 Inspector 확인·저장이나 Codex의 실제 호출을 대신하지는 않습니다. 실행할 수 없으면 해당 연결을 `NOT_VERIFIED`로 남깁니다.

[Inspector 공식 사용법](https://modelcontextprotocol.io/docs/2026-07-28/tools/inspector), [Codex MCP 설정](https://learn.chatgpt.com/docs/extend/mcp?surface=cli), [SDK 테스트 범위](https://py.sdk.modelcontextprotocol.io/get-started/testing/)
