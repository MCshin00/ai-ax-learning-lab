# IT 문의 처리 앱의 개발 문맥

직원의 문의를 서비스별 상태·운영 문서·대응 초안으로 만들고, 담당자가 검토한 뒤 작업 요청으로 저장한다.

- DeskApp: 대화 식별자와 문의, 검토 후 저장을 받는 콘솔
- IncidentFlow: 접수·상태 조회·검색·응답 연결
- ModelWork와 EvidenceSearch: 모델 호출·도구 선택·검색
- ReviewDesk: 검토본과 저장 조건
- OperationsClient·OperationsServer·OperationsStore: 업무 MCP와 저장 기능
- IncidentDeskTest: 모델 대역·임시 자료·실제 로컬 MCP로 수행하는 업무 검사

개발 사례는 data/acceptance-cases.json의 INTAKE, EVIDENCE, SAVE, PARTIAL, REVISION이다.
Gradle test를 자동 검사에 사용한다. 실제 모델의 표현 해석·검색·답변은 학습자가 IDE에서 --live로 확인한다.
서비스별 사실과 근거를 보존하고, 새 요구의 입력부터 결과까지 변경한 경로를 확인한다.
