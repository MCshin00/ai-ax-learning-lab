# FAILURE-001 — Run A가 80번째 공백을 보존하지 않음

## 기대한 결과

`README.md`의 순서대로 공백을 정규화한 결과가 80 code point를 넘으면 앞의 80개 code point를 반환해야 한다.

입력이 `"a".repeat(79) + " b"`이면 정규화 결과는 81 code point이고, 앞의 80개는 `"a".repeat(79) + " "`이다.

## 실제 결과

Run A는 80번째 공백까지 남기지 않고 `"a".repeat(79)`만 반환했다. 결과는 79 code point였다. 공개 테스트 6개는 모두 통과했다.

## 재현 절차

```text
작업 폴더: week01-codex-prompt-comparison/runs/run-a
입력: "a".repeat(79) + " b"

String actual = TicketTitleNormalizer.normalize("a".repeat(79) + " b");
actual.codePointCount(0, actual.length()); // 79
actual.endsWith(" ");                     // false

시작 상태: Run A의 첫 결과를 고정한 상태
시작 commit: NOT_RECORDED
```

첫 결과를 보존하기 위해 재현용 코드는 Run A에 추가하지 않고 임시 probe로만 실행했다.

## 영향

- 정규화 결과의 앞 80개 code point를 유지한다는 요구사항을 경계 입력에서 지키지 못한다.
- 동일한 경계를 검사하는 비공개 또는 회귀 테스트가 있다면 실패할 수 있다.
- 공개 테스트 통과만으로 변환 단계 사이의 경계 동작을 검증할 수 없음을 보여 준다.

## 원인 가설

Run A는 보류된 공백과 다음 문자를 모두 추가할 수 있을 때만 공백을 출력한다. `codePointCount + 2 > 80`이면 반복을 종료하므로, 공백 하나만 80번째 자리에 들어갈 수 있는 경우에도 그 공백을 버린다.

## 확인한 사실

- Run A는 `codePointCount + 2 > 80` 조건을 사용한다.
- 공개 테스트의 80번째 문자는 공백이 아니어서 이 분기가 검증되지 않는다.
- Run A와 Run B 모두 기존 공개 테스트 6개를 통과했다.
- 같은 입력에서 Run B는 80 code point를 반환하고 공백으로 끝났다.

## 수정 내용

`NOT_APPLIED` — A/B 첫 결과를 보존하기 위해 Run A 코드는 수정하지 않았다.

후속 구현에서는 정규화 후 code point 기준으로 절단하거나, 스트리밍 구현에서 80번째 공백을 보존하도록 경계 로직을 분리할 수 있다.

## 회귀 테스트

`NOT_ADDED` — 첫 결과를 보존하기 위해 테스트를 추가하지 않았다.

추가할 테스트 후보:

```java
assertEquals(
    "a".repeat(79) + " ",
    TicketTitleNormalizer.normalize("a".repeat(79) + " b")
);
```

## 다른 작업에도 적용할 교훈

- 여러 변환을 순서대로 수행하는 코드는 각 변환의 경계가 겹치는 입력을 따로 검사한다.
- 공개 테스트 통과와 요구사항 전체 충족을 같은 의미로 기록하지 않는다.
- 결과 길이뿐 아니라 마지막 code point의 종류도 확인한다.

## 근거

- 실행 ID: `019fd1f3-ee1b-7d90-9731-3a50035d857d`
- commit: `NOT_RECORDED`
- 로그: 대화형 Codex CLI 세션 및 임시 경계 probe
- 화면: `NOT_RECORDED`
- 구현: `week01-codex-prompt-comparison/runs/run-a/src/main/java/lab/week01/TicketTitleNormalizer.java`
- 요구사항: `week01-codex-prompt-comparison/lab/ticket-title-normalizer/README.md`
- 비교 기록: `week01-codex-prompt-comparison/runs/RESULTS.md`
