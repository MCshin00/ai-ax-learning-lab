# IT 문의 접수와 운영팀 전달

자연어 문의에서 서비스와 증상을 파악하고, MCP로 현재 상태를 조회하고, 운영 문서를 검색해 대응 초안을 만듭니다. 담당자가 검토·편집한 내용은 별도 저장 동작으로 운영팀에 전달합니다.

학습 순서와 설계 해설은 [12주차 교재](../README.md)의 Day 1~5를 따릅니다. 이 프로젝트는 실행 가능한 참고 구현이며, 제공 업무 요구에 맞춰 구조를 선택하고 수정할 때 비교할 수 있습니다.

## IDE 실행

이 폴더를 Gradle 프로젝트로 열어 의존성을 동기화합니다. Java 17 이상을 사용하고 실행 설정의 작업 폴더를 `incident_desk`로 지정합니다.

- `DeskApp.main`: 문의와 검토 후 저장. 기본값은 대역 모델·대역 임베딩이며 실제 MCP 서버 프로세스를 시작합니다.
- `OperationsClient.main`: 인수 없이 실제 공개 도구 목록, `get VPN`으로 상태 조회, `read <저장ID>`로 운영팀의 요청 조회.
- `CompareFlows.main`: 같은 입력에 고정 검색과 모델이 선택하는 검색을 적용합니다.
- `IncidentDeskTest`: IDE에서 테스트 클래스를 실행합니다. 임시 업무 자료와 대역 모델을 사용합니다.

문의 콘솔 입력:

```text
a|접속이 안 돼요
a|VPN이고 인증서 만료 메시지가 나와요
/save|a|<검토ID>|it-001|VPN 인증서 만료 메시지 확인. 갱신 안내 요청.
b|VPN과 급여 시스템이 안 돼요
/quit
```

대역 모드의 다른 입력은 `data/cases.json`과 `ScriptedModels.java`에 있습니다. 실제 모델에서는 자유로운 표현으로 실행합니다. 참고 앱의 접수·초안·도구 기록은 콘솔에 JSON으로 출력되며, 사람이 검토할 본문은 `text`입니다. `analysis.items`에서 저장 대상인 `ready` 항목과 미처리 항목을 함께 확인합니다. `<검토ID>`에는 초안 출력의 최상위 `id`를 넣습니다. `/save`는 그 검토본에 대해 입력한 본문을 저장하며, 정정 전에 만들어진 오래된 검토 ID는 거절합니다.

실제 모델은 IDE의 비공유 실행 설정에 `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_EMBEDDING_MODEL`을 지정하고 프로그램 인수 `--live`로 실행합니다. 기본 모델 연결은 LangChain4j의 OpenAI 어댑터입니다. `DeskApp`에 `--fixed`를 주면 코드가 검색을 호출하며, 기본 경로는 모델에 `find_runbook`을 공개합니다. `CompareFlows`는 `--live`와 선택한 문의 문자열을 인수로 받습니다.

## 구현을 읽는 순서

`DeskApp` → `ReviewDesk.analyze` → `IncidentFlow.analyze`에서 접수·서비스 조회·검색·초안 검사 경로를 따라갑니다. `ModelWork`의 AI Services가 구조화 출력과 검색 도구 실행을 연결합니다. `EvidenceSearch`는 실제 메모리 벡터 저장소를 사용합니다. 검토 이후에는 `ReviewDesk.save` → MCP `save_work_request` → `OperationsStore.save`로 이어집니다.

MCP 도구는 `get_service_status(serviceId)`, `save_work_request(draft)`, `read_work_request(id)`입니다. 저장 입력 `draft`에는 `requestId`, `text`, 서비스별 `facts`, `sources`가 들어갑니다. 저장 시 서비스와 문서의 현재 내용을 대조합니다. 같은 요청 ID·본문·근거로 재시도하면 같은 저장 건을 반환하고, 같은 ID에 다른 내용이면 충돌합니다. 여러 프로세스가 원본 상태를 갱신하는 DB에 적용할 때는 변경 확인과 쓰기를 같은 트랜잭션으로 처리합니다.

대화와 검토본은 실행 중 메모리에, 저장 요청은 `.local/requests`에 유지됩니다. 상태가 `stale_preview` 또는 `stale_facts`이면 새 결과를 검토합니다. `save_unknown`이면 같은 검토 ID·요청 ID·본문으로 재시도해 저장 ID를 받은 뒤 운영 도구에서 확인합니다. `limit_reached`이면 모델 호출 6회 안에 처리를 끝내지 못한 것이며 확인된 사실을 함께 반환합니다.

검사는 출력 변환·도구 연결·질문 후 재개·대화 분리·부분 성공·출처 연결·저장·재시도·정정·호출 한도를 확인합니다. 실제 모델의 해석과 초안 품질은 교재의 제공 입력을 IDE에서 실행하고 원문과 대조합니다.

## 개발 환경 연결

주차 본문 Day 1에서 11주차에 만든 하네스를 이 앱에 맞춥니다. 지침·Skill의 적용 범위와 Hook의 검사 작업·결과 위치·인계 정보를 선택해 연결합니다. Gradle `test`는 대역 모델과 임시 자료로 검사하고 결과는 `build/test-results/test`에 남깁니다. 현재 업무 요청과 `docs/project.md`를 읽고 필요한 연결 설정을 작성합니다. 앱의 서비스 조회·저장 MCP는 업무 파이프라인에서 입력·결과와 호출 시점을 확인합니다.
