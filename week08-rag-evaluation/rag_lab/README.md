# RAG·의미 검색·평가 실행 프로젝트

제공 합성 정책에서 자료 준비 → 검색 → 근거 선택 → 답변 생성을 연결하는 Java 프로젝트입니다. 학습 요구, 개념 해설, Day별 실행 안내와 복사 요청문은 [주차 README](../README.md)에 있습니다. 실제 결과와 선택 이유는 주차의 `rag-note.md`에 이어 남깁니다.

[실습의 RAG 구조 다이어그램](../README.md#준비된-실습의-rag-구조)에서 인덱스 준비, 두 검색 경로, 근거 선택과 답변 생성의 연결을 볼 수 있습니다.

JDK 17 이상으로 이 폴더를 IDE의 Gradle 프로젝트로 엽니다. 작업 폴더는 `rag_lab`, 원문은 형제 폴더 [knowledge_base](../knowledge_base/)입니다. 의존성 버전은 `build.gradle`에서 확인합니다.

| 필요한 자료 | 실제 역할과 찾아갈 단계 |
|---|---|
| `Chunking.java`, `Retrieval.java` | 관리 메타데이터를 읽고 문서를 나눈 뒤 어휘 후보 검색 — Day 1~2 |
| `Rerank.java`, `Answering.java`, `Pipeline.java` | 후보 순위·근거 선택·보류의 참고 정책 — Day 2 |
| `EmbeddingIndex.java`, `ContextChoices.java` | SDK 저장소와 직접 선택한 조각 연결, 같은 후보의 문맥 범위 비교 — Day 3 |
| `Quickstart.java` | 참고 실행 진입점과 실제 근거를 생성에 전달하는 경계 — Day 1·3~4 |
| `Evaluation.java`, `data/golden.json` | 자기 앱에 연결할 골든 질문과 원문에 근거한 판정 기준 — Day 2·4 |
| `data/red-team.json` | 자기 앱의 생성·결과 판단 경계에 전달할 합성 반례 — Day 5 |
| `src/test/java/lab/week08/` | 생성·임베딩 대역을 사용하는 기존 연결·저장·실패 경로 검사 |

표의 Java 파일은 `src/main/java/lab/week08/`에 있습니다. 참고 구현을 재사용하면서 자신의 요구에 맞게 클래스와 결과 계약을 정합니다. 실행 진입점과 평가 연결은 주차 README의 Day 3~5를 따릅니다.
