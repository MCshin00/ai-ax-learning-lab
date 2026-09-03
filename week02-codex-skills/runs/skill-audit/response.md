# imagegen과 documents Skill 구조 검토 응답

> Day 1의 읽기 전용 검토 응답을 요약한 공개 기록이다. 상세 비교표와 학습자의 판단은 [comparison.md](comparison.md)에 기록했다.

## 검토 대상

- Skill A: `imagegen`
- Skill B: `documents`

## 핵심 결과

- 두 Skill 모두 특정 산출물을 만드는 절차, 필요한 참고 자료와 도구, 검증 과정을 묶는다.
- `imagegen`은 생성하거나 편집한 래스터 이미지를 대상으로 하며, SVG·벡터·코드 기반 결과가 적합한 작업을 명시적으로 제외한다.
- `documents`는 DOCX·Word·Google Docs용 문서의 생성·편집·검토를 다룬다. 읽기·검토 경로는 본문에 있지만 frontmatter `description`에는 직접 드러나지 않는다.
- 출력 형식, 값 범위, 파일 존재 여부, OOXML 구조처럼 명확한 규칙으로 표현할 수 있는 항목은 스크립트가 맡는다.
- 구성, 가독성, 심미적 완성도, 사용자 목적 충족 여부처럼 문맥에 따라 답이 달라지는 항목은 모델의 판단과 시각 검토가 필요하다.
- `description`만으로 실제 자동 발동 여부나 발동률을 확정할 수 없으므로 해당 항목은 `NOT_VERIFIED`로 남긴다.

## 상세 기록

[comparison.md](comparison.md)에는 다음 내용을 함께 남겼다.

- 두 Skill의 구조와 발동 조건 비교표
- 학습자가 원문을 다시 확인한 뒤 작성한 7개 판단
- 원문과 스크립트를 대조한 검토 코멘트와 수정안
