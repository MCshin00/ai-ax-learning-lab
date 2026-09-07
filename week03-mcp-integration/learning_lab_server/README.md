# 바로 연결하는 예제 MCP

가상의 상품 두 개를 조회하는 로컬 서버입니다. 파일·계정·외부 서비스에 접근하지 않습니다. Day별 학습과 요청 본문은 [주차 README](../README.md)에 있습니다.

## 1. Inspector로 실행

IDE와 터미널의 작업 폴더는 생성된 학습 저장소의 `week03-mcp-integration/learning_lab_server/`입니다. Python 3.11+, uv, Inspector가 지원하는 Node가 필요합니다. 현재 공식 Inspector 최소 버전은 Node 22.19.0입니다.

Windows PowerShell:

```powershell
uv sync --locked
$CatalogProject = (Get-Location).Path
npx @modelcontextprotocol/inspector uv --directory "$CatalogProject" run --locked ai-ax-learning-lab-mcp
```

macOS·Linux·WSL:

```bash
uv sync --locked
catalog_project="$(pwd)"
npx @modelcontextprotocol/inspector uv --directory "$catalog_project" run --locked ai-ax-learning-lab-mcp
```

Inspector가 출력한 로컬 URL을 브라우저에서 엽니다. URL의 세션 토큰은 공유 기록에 넣지 않습니다. stdio 서버를 연결하고 Tools에서 `get_product`를 선택해 `product_id`에 `NOTE-01`을 입력합니다. 이름·가격 3000·재고 true가 보이면 첫 호출 성공입니다. `PEN-02`는 재고 false, `UNKNOWN`은 오류가 예상됩니다.

## 2. Codex에서 사용

앱의 MCP 설정에서 같은 command·arguments로 등록할 수 있습니다. 등록 UI가 없으면 이 프로젝트의 터미널에서 다음을 실행합니다. 먼저 목록에서 `learning-catalog`가 이미 사용 중인지 확인하고 충돌하면 새 이름을 고릅니다.

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

## 주요 파일

- `src/learning_lab_mcp/server.py`: 상품 데이터와 Tool·Resource·Prompt, 서버 진입점.
- `pyproject.toml`, `uv.lock`: 실행 명령·의존성과 설치 버전.
- `tests/`: 제공 서버의 응답을 확인하는 유지보수용 검사.
- `../mcp-use.md`: 호출 결과·구조 분석·수정 판단을 이어 쓰는 주차 노트.

## 예제 유지보수용 확인

예제를 바꿨을 때 이 프로젝트에서 실행합니다. 모든 OS에서 uv 안의 Python을 사용합니다.

```text
uv run --locked python -B -m unittest discover -s tests -v
```

이 검사는 SDK의 메모리 연결입니다. Inspector의 stdio 연결과 Codex의 실제 호출을 검증했다고 주장할 수 없습니다. 실행할 수 없으면 해당 연결을 `NOT_VERIFIED`로 남깁니다.

[Inspector 공식 사용법](https://modelcontextprotocol.io/docs/2026-07-28/tools/inspector), [Codex MCP 설정](https://learn.chatgpt.com/docs/extend/mcp?surface=cli), [SDK 테스트 범위](https://py.sdk.modelcontextprotocol.io/get-started/testing/)
