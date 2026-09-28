# 회의 검토 Workflow의 실제 노드 구성

이 문서는 Dify 화면에서 구성할 노드와 변수의 대응표입니다. 가져오기용 DSL 파일이 아닙니다. 연결한 모델과 설치된 Plugin 식별자는 환경마다 다르므로 화면에서 선택한 뒤 실제로 작동한 구성을 DSL로 내보냅니다.

Day 2에서 고른 처리 단위 하나를 이어갑니다. 아래 표는 **전체 메모 검토**의 참고 구성입니다. 항목별 결과를 골랐다면 뒤의 **항목별 결과의 연결**을 사용하고, 전체 `ready/review` 구조에 맞추려고 선택한 결과 형식을 바꾸지 않습니다.

## 전체 메모 검토의 연결

| 순서·노드 이름 | 노드 종류 | 입력·설정 | 출력·다음 연결 |
|---|---|---|---|
| 1 `회의 입력` | User Input | 필수 문단형 `memo` | `회의 추출`로 연결 |
| 2 `회의 추출` | LLM | 선택한 모델, `extraction-prompt.txt`, `회의 입력.memo` 변수 참조. Structured Outputs에 `extraction-schema.json` 지정 | 실제 `structured_output` 객체 |
| 2B `추출 직렬화` | Code / Python | `extraction=회의 추출.structured_output`, 아래 코드 | 문자열 `extraction_json` |
| 3 `업무 검사` | Tool | Meeting Review → Review meeting tasks, `memo=회의 입력.memo`, `extraction_json=추출 직렬화.extraction_json` | `status`, `summary`, `payload`, `json` |
| 4 `결과 분기` | IF/ELSE | IF `업무 검사.status` equals 직접 입력한 `ready`; ELIF equals 직접 입력한 `review`; ELSE | 각각 작업 초안·검토 안내·형식 오류 안내 Template |
| 5A `작업 초안` | Template | `ready-template.jinja`, `summary=업무 검사.summary` | `output` 문자열을 안내문 통합으로 전달 |
| 5B `검토 안내` | Template | `review-template.jinja`, `memo=회의 입력.memo`, `summary=업무 검사.summary` | `output` 문자열을 안내문 통합으로 전달 |
| 5C `형식 오류 안내` | Template | 본문 `{{ summary }}`, `summary=업무 검사.summary` | `output` 문자열을 안내문 통합으로 전달 |
| 6 `안내문 통합` | Variable Aggregator | 세 Template의 `output`을 같은 String 집계에 지정 | 실행된 분기의 안내문을 공통 출력으로 전달 |
| 7 `최종 출력` | Output | 아래 출력 필드를 한 번씩 선언 | 호출자에게 공통 결과 반환 |

정상 경로는 사용 가능한 작업 초안을, 검토 경로는 원문과 남은 이유를 보여 줍니다. IF/ELSE에서 한 경로만 실행되므로 변수 집계기는 해당 Template의 결과를 공통 출력으로 연결합니다. 안내문을 동시에 생성하거나 이어 붙이는 처리는 아닙니다. 세 Template의 연결선을 모두 `안내문 통합`에 연결하고 그 뒤에 Output 하나를 둡니다.

집계 변수는 `작업 초안.output`, `검토 안내.output`, `형식 오류 안내.output`이며 타입은 **String (`string`)**입니다. 분기별로 생성되는 Template 결과를 선택합니다. 안내문 하나는 기본 단일 집계로 모을 수 있고, 그룹 기능을 사용하면 같은 String 그룹에 세 변수를 지정합니다.

| 최종 Output 이름 | 연결값 |
|---|---|
| `status` | `업무 검사.status` |
| `result` | `안내문 통합`의 실제 집계 출력 |
| `extraction` | `추출 직렬화.extraction_json` |
| `payload` | `업무 검사.payload` |

`result`에는 선택한 집계 출력의 타입이 반영됩니다. 문자열 출력이면 안내문을 직접 읽고, 그룹 전체가 `{"output":"안내문"}` 객체로 반환되면 `result.output`에서 읽습니다. 실제 출력 이름·타입을 확인해 변수 선택기로 연결합니다. 호출 앱은 상세 결과 문자열인 `payload`를 읽습니다. Output 변수 이름은 Workflow 전체에서 고유하게 지정하고 공통 Output에 각 필드를 한 번씩 선언합니다.

이 표는 메모 전체를 검토하고 경로별 안내를 같은 항목·타입으로 반환하는 참고 설계입니다. Tool 호출 자체의 예외·연결 실패는 실행 실패로 남기고 정상 빈 결과에 연결하지 않습니다.

`memo`라는 글자를 쓰는 것으로 변수 연결을 대신하지 않습니다. Dify 변수 선택기에서 선행 노드의 실제 변수를 고릅니다. 참고 예제는 Plugin의 YAML `output_schema`에 `status`를 선언하고 Python이 같은 이름으로 `create_variable_message`를 반환하므로 IF/ELSE가 그 값을 읽습니다. 다른 입력·출력 형식을 선택했다면 실제 인자·출력의 이름과 타입에 맞춰 Tool과 후속 노드를 연결합니다. 전체 상태 대신 항목별 결과를 반환하는 설계도 출력 선언·실제 반환값·변수 참조의 항목 이름과 타입이 일치해야 합니다.

첫 실행은 `cases.json`의 `normal.memo`만 입력합니다. 이어서 `missing.memo`와 `conflict.memo`를 사용합니다. 파일의 `extraction`은 업무 함수·Plugin 로컬 검증용 예상 추출이고 실제 모델 응답 기록이 아닙니다. Dify에서는 실제 LLM 출력과 원문을 함께 읽습니다.

사람의 보완은 원문에 `사람의 보완: ...`을 덧붙인 새 `memo`로 전달합니다. `partial.memo`는 담당자만 확정하고 기한은 남겨 두므로 검토 경로가 유지되어야 합니다. 이 방식은 원문과 보완을 함께 새 실행에 넣는 것이며 영구 중단·재개 기능을 구현한 것은 아닙니다.

`business.check_tasks(memo, tasks)`는 항목별 판단만 반환합니다. 메모 전체에 문제가 하나라도 있으면 검토 경로로 보낼지, 확정 항목과 검토 항목을 함께 반환할지는 이 함수 밖의 구성에서 선택할 수 있습니다. 제공 Plugin은 전체 메모 검토를 택한 예제입니다. 사용자 입력·출력 이름을 달리 선택하면 Tool·Template·Output이 실제로 사용하는 변수 이름과 타입을 맞춥니다.

`추출 직렬화` Code 노드에는 다음 코드를 넣고 반환 변수 `extraction_json`을 String으로 선언합니다. 입력 변수 `extraction`은 선행 LLM 노드의 실제 `structured_output`을 선택합니다.

```python
import json

def main(extraction: dict) -> dict:
    return {"extraction_json": json.dumps(extraction, ensure_ascii=False)}
```

스키마의 필수 필드는 값을 발명하라는 지시가 아닙니다. 담당자를 모르면 `owners=[]`, 기한을 모르면 `due=""`가 되어야 합니다. 첫 실행에서 스키마와 실제 객체를 대조합니다. 모델이 지원하지 않는 경우에는 지원하는 모델로 바꾸고, 프롬프트만으로 생성한 JSON을 구조화 출력 기능의 검증으로 기록하지 않습니다. [Dify LLM 노드](https://docs.dify.ai/en/use-dify/nodes/llm)

공통 `최종 출력`의 `payload=업무 검사.payload`가 String으로 반환되는지 확인합니다. 이 문자열에는 검사한 전체 작업이 담깁니다. 저장 여부는 내용을 읽은 사람이 다음 단계에서 결정합니다. 실제 실행 가능한 Workflow를 게시하고 API Access의 기본 주소·키를 IDE의 비공유 설정에 넣은 뒤 `meeting_plugin/workflow_client.py --live`로 호출합니다. 기본 주소에는 `/workflows/run`을 중복으로 넣지 않습니다. `normal`, `missing`, `partial` 메모가 같은 경로를 통과하는지 확인하고 정상 초안은 원문 대조 후 사람이 `save`를 입력한 경우만 저장합니다.

API 호출 코드의 요청 헤더에는 `User-Agent: MeetingReviewLab/1.0`으로 학습용 앱 이름·버전을 명시합니다. `Content-Type`과 `Accept`는 `application/json`으로 지정합니다. `User-Agent`는 호출 프로그램의 식별 정보이며 Dify 앱 이름을 변경하거나 환경변수에 추가하는 설정이 아닙니다.

## 항목별 결과의 연결

공통인 입력 → LLM 추출 → 직렬화 → 업무 검사는 유지합니다. Plugin이 `check_tasks`로 검사한 항목을 확정·보완 집합으로 나누고, 두 집합을 하나의 `payload`에 보존합니다. `confirmed`, `pending`은 가능한 필드명이며 필수 SDK 이름이 아닙니다.

| 값을 전달할 위치 | 항목별 구성에서 연결할 값 |
|---|---|
| 업무 검사 | 원문·추출값 → 항목별 판단 → 확정·보완 목록과 원문 대응 정보 |
| Plugin 반환 | 선택한 결과 객체를 직렬화한 String `payload`, 두 집합을 함께 설명하는 `summary` |
| 표시 | 한 Template에서 `summary` 표시. 확정 초안과 남은 질문을 모두 보존 |
| Output | 표시 결과와 같은 `payload`를 함께 반환 |
| 호출 앱 | 파싱 → 원문과 항목 재검사 → 확정 항목에 대한 사람 확인 → 확인한 묶음만 저장 |

확정과 보완은 동시에 필요한 데이터입니다. 서로 배타적인 분기의 값을 고르는 Variable Aggregator로 두 집합을 합치지 않습니다. 형식 오류·실행 실패는 별도로 구별하며, 검토가 필요한 항목을 빈 성공 결과로 바꾸지 않습니다. Plugin의 출력 선언·메시지 이름·Workflow 참조·호출자 파싱 위치는 같은 항목 이름·타입·의미로 맞춥니다.

`mixed`에서는 확정된 초안 공유와 보완할 검토 회의 준비가 모두 출력에 남아야 합니다. `partial`은 담당자만 보완되어 기한 질문이 남습니다. 원문과 추출을 대조하는 방법은 전체 메모 구성과 같으며 출력과 저장 대상을 항목 단위로 해석합니다.

제공 `workflow_client.py`의 `process`는 `payload.tasks`를 읽어 메모 전체가 `ready`일 때 저장하는 참고 코드입니다. 항목별 결과를 반환한다면 두 집합과 원문 대응 정보를 읽고 `check_tasks`로 재검사하도록 바꿉니다. 재검사와 분류가 맞는 확정 항목만 사람에게 보여 주고, 확인된 작업 묶음을 기존 저장기의 `tasks` 입력에 연결합니다. 같은 메모 ID·같은 원문·같은 저장 묶음은 `already_saved`입니다. 저장할 작업이 같아도 보완으로 원문이 바뀌면 `revision_required`로 변경 확인을 요청합니다. 보완 항목은 남은 질문으로 보존합니다.

호출 대역·테스트도 선택한 결과에 맞춥니다. API 주소·인증·게시·사람의 `save` 입력 방법은 앞 절과 같지만, 전체 상태 하나로 모든 항목을 보류하는 기본 소비자를 수정 없이 실행하지 않습니다.

Day별 요구 도출·설계 대안·실행 설정·완료 기준은 [주차 README](../README.md)에 있습니다.
