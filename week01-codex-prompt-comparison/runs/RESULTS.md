# Week 1 프롬프트 A/B 비교

## 1. 실험 상태

- 실행일: 2026-08-05
- 방식: 서로 다른 작업 폴더에서 대화형 Codex CLI를 각각 실행
- 시작 코드: 실행 전에 기준 코드와 `run-a`, `run-b`가 동일한 상태임을 확인
- 첫 결과 고정: 두 실행 모두 후속 교정 요청을 보내기 전에 결과를 보존
- 사용자 후속 교정 횟수: A 0회, B 0회

## 2. 폴더와 입력 검증

CLI 세션 로그와 비생성 파일의 SHA-256을 대조했다. `.gradle/`, `build/`, `.idea/`는 생성물이므로 파일 비교에서 제외했다.

| 확인 항목 | Run A | Run B |
|---|---|---|
| 실제 작업 폴더 | `runs/run-a` | `runs/run-b` |
| 준비된 프롬프트와 실제 입력 | 문자 단위 일치 | 문자 단위 일치 |
| 다른 run 폴더 이름을 참조한 횟수 | 0 | 0 |
| 기준 코드와 달라진 비생성 파일 | `TicketTitleNormalizer.java` 1개 | `TicketTitleNormalizer.java` 1개 |
| 공개 테스트·README·Gradle 설정 변경 | 없음 | 없음 |
| 모델 | `gpt-5.6-terra` | `gpt-5.6-terra` |
| sandbox / approval | `workspace-write` / `on-request` | `workspace-write` / `on-request` |
| reasoning 설정 | `medium` | `medium` |
| 실제 프롬프트 전송 시각(KST) | 21:45:35.501 | 21:45:35.985 |
| 첫 최종 응답 시각(KST) | 21:47:38.193 | 21:46:59.594 |
| 프롬프트부터 최종 응답까지 관찰 시간 | 약 2분 2.7초 | 약 1분 23.6초 |

CLI 창은 B가 먼저, A가 나중에 열렸지만 실제 프롬프트는 A와 B에 0.484초 간격으로 전송됐다. 따라서 두 작업은 사실상 동시에 시작됐다. 세션 로그상 상대 run 폴더를 읽거나 수정한 흔적도 없다. 위 관찰 시간에는 도구 실행과 환경 오류 재시도가 포함되어 있으므로 순수한 모델 속도나 프롬프트 품질의 근거로 사용하지 않는다.

## 3. 실제 요청 원문

### Run A — 짧은 요청

```text
TicketTitleNormalizer를 구현하고 테스트를 통과시켜줘.
```

Run A가 직접 명시한 것은 구현 대상과 테스트 통과뿐이다. 세부 요구사항, 허용 범위, 검증 방법과 보고 형식은 작업 폴더의 `README.md`, 공개 테스트, 빌드 파일과 루트 `AGENTS.md`에서 찾아야 했다.

### Run B — 구조화된 요청

```markdown
# Goal
현재 작업 폴더의 `TicketTitleNormalizer.normalize`를 구현한다.

# Context
- 먼저 `README.md`, `build.gradle`, `src/main/java/lab/week01/TicketTitleNormalizer.java`, `src/test/java/lab/week01/TicketTitleNormalizerTest.java`를 읽는다.
- 시작 코드는 의도적으로 미구현 상태다.

# Allowed paths
- `src/main/java/**`
- 필요하면 `src/test/java/**`에 새 회귀 테스트 추가

# Forbidden
- Public Test 수정·삭제
- Gradle 설정이나 기존 의존성 변경
- 다른 폴더 변경

# Acceptance criteria
- null·blank는 IllegalArgumentException
- 앞뒤 공백 제거
- 연속 whitespace를 한 칸으로 변환
- 결과가 Unicode code point 기준 80자를 넘으면 앞의 80개 code point만 남김
- Java 17

# Verification
- 이 Gradle 프로젝트의 `test` 작업을 실행한다.
- IDE가 아닌 자동 검증에서는 현재 운영체제에 맞는 Gradle Wrapper를 사용한다.

# Handoff
변경 파일, 실행한 테스트, 남은 위험을 보고한다.
```

Run B는 읽을 파일, 허용·금지 범위, 인수 조건, 검증 명령과 완료 보고 내용을 직접 명시했다. 다만 이 정보 대부분은 이미 작업 폴더의 README와 공개 테스트에 있었다.

실험 입력 원본: [minimal.md](../prompts/minimal.md), [structured.md](../prompts/structured.md). 당시 실제 전송문은 위 본문에 그대로 보존했다.

## 4. 첫 결과

| 평가 항목 | Run A | Run B |
|---|---|---|
| Codex가 읽은 핵심 파일 | README, build.gradle, 구현, 공개 테스트 | README, build.gradle, 구현, 공개 테스트 |
| 변경 파일 | 구현 파일 1개 | 구현 파일 1개 |
| Codex가 보고한 테스트 | `BUILD SUCCESSFUL` | `BUILD SUCCESSFUL` |
| 독립 재실행한 공개 테스트 | 6/6 통과 | 6/6 통과 |
| 공개 테스트 추가·수정 | 없음 | 없음 |
| 사용자 후속 교정 | 0회 | 0회 |
| 최종 응답의 남은 위험 | “공개 테스트만 검증” | “없음” |

첫 최종 응답 보존본: [Run A](run-a/response.md), [Run B](run-b/response.md)

## 5. 코드와 경계 조건 비교

### 구현 방식

- Run A는 입력을 한 번 순회하면서 공백을 보류하고 결과 code point 수를 직접 센다. 80자에 도달하면 나머지 입력을 읽지 않으며 `Character.isWhitespace`와 `Character.isSpaceChar`를 함께 사용한다.
- Run B는 전체 입력을 먼저 정규화한 뒤 `offsetByCodePoints`로 앞 80개 code point를 자른다. 구현 순서가 요구사항 문구와 직접 대응하지만 전체 입력을 끝까지 읽고 `Character.isWhitespace`만 사용한다.

### 공개 테스트 밖에서 확인한 경계

아래 확인은 첫 결과를 고정한 뒤 별도의 임시 probe로 수행했으며, run 폴더의 코드는 수정하지 않았다.

| 입력·관찰 | Run A | Run B | 해석 |
|---|---|---|---|
| `"a".repeat(79) + " b"` | 79 code point, 공백으로 끝나지 않음 | 80 code point, 공백으로 끝남 | 정규화 결과의 앞 80 code point를 그대로 남긴다는 문구에는 B가 더 직접적으로 부합한다. A는 80번째 공백까지 남기지 않아 79자만 반환한다. |
| `"A\u00A0B"` | 일반 공백을 쓴 `"A B"`로 변환 | NBSP를 그대로 보존 | “공백 문자”에 NBSP를 포함한다면 A가 더 넓게 처리한다. Java의 `isWhitespace` 범위만 뜻한다면 요구사항 해석이 모호하다. |

따라서 공개 테스트 6개만으로는 두 구현의 경계 정책을 모두 판정할 수 없다. Run A에는 80번째 문자가 정규화 과정에서 생긴 공백인 경우의 확인된 불일치가 있고, Run B에는 NBSP 등 Unicode 공간 문자 처리 범위의 남은 위험이 있다.

## 6. 완료 보고 품질

Run A의 “공개 테스트만 검증되었습니다”는 실제 검증 범위를 정확하게 제한한다. 코드가 더 위험하다는 뜻이 아니라, 공개 테스트 통과가 모든 동작을 보장하지 않는다는 사실을 보고한 것이다.

Run B는 구조화 프롬프트에서 남은 위험을 보고하라고 명시했지만 “없음”이라고 답했다. 공개 테스트에 없는 경계가 남아 있고 추가 회귀 테스트도 작성하지 않았으므로, 이 보고는 근거보다 강한 주장이다. 완료 보고의 정확성과 불확실성 표현에서는 Run A가 더 낫다.

## 7. 판정

- 공개 테스트 결과와 수정 범위: 무승부
- 일반적인 입력에서의 기능: 실질적 차이 없음
- 경계 구현: 서로 다른 장단점과 위험이 있어 단순 우열을 확정하기 어려움
- 완료 보고와 위험 전달: Run A 우세
- 이번 실험의 결론: 구조화된 Run B 요청은 단일 실행에서 관찰 가능한 이점을 만들지 못했고, 위험 보고는 오히려 Run A보다 부정확했다.

이 결과만으로 구조화 프롬프트가 일반적으로 더 나쁘다고 결론 내릴 수는 없다. 과제가 작고 README와 공개 테스트가 이미 충분한 맥락을 제공했으며, 실행 표본도 각 1회뿐이다. 후속 가설은 “명시적인 체크리스트가 충분히 검증했다는 과도한 확신을 유발할 수 있는가”이며, 이를 확인하려면 동일 조건의 반복 실행이나 더 모호한 다중 파일 과제가 필요하다.

## 8. 기록의 한계

- 전체 CLI 실행 인자를 별도 기록하지 않았다.
- 관찰 시간은 대화형 세션 이벤트 시각의 차이이며 자동 측정 Runner의 `T_wall` 값은 아니다.
- 경계 probe는 사전에 고정한 숨은 테스트가 아니라 첫 결과 이후 수행한 사후 검토다.
- 표본이 A 1회, B 1회이므로 프롬프트 방식 일반에 대한 인과 결론을 내릴 수 없다.

## 9. 선택 후속 실험 — 낮은 추론 조건에서 프롬프트 구조 비교

이 후속 실험에서 확인할 대상은 모델이나 reasoning의 효과가 아니라 프롬프트 구조의 역할이다. 모델과 reasoning은 A와 B에 동일하게 적용하는 통제 조건이므로, 기존보다 낮은 조합을 선택해 과제가 너무 쉽게 해결되는 천장 효과를 줄일 수 있다.

> 동일한 `GPT-5.3-Codex Spark` / `low` 조건에서 구조화 프롬프트가 짧은 프롬프트보다 요구사항 누락을 줄이고 검증과 완료 보고를 더 충실하게 만드는가?

[OpenAI의 GPT-5.3-Codex 모델 문서](https://developers.openai.com/api/docs/models/gpt-5.3-codex)는 `low` reasoning을 지원한다고 명시한다. 실행할 때는 Codex CLI에 실제로 표시되는 Spark 모델 이름과 모델 ID를 결과 기록에 함께 남긴다.

실행 전 준비 경로는 다음과 같다.

- Run A: `runs/run-a-gpt53-spark-low`
- Run B: `runs/run-b-gpt53-spark-low`

두 폴더에는 원래 A/B 실행 당시와 같은 비생성 입력 파일 9개를 복사했다. 이전 Run 폴더에도 프롬프트 전부터 존재했던 `.gradle/`, `build/`, `.idea/` 상태도 동일하게 복사했지만, 이후 Day 3에 만든 하위 `AGENTS.md`는 포함하지 않았다. 로컬 Codex 모델 카탈로그에서 실제 모델 ID가 `gpt-5.3-codex-spark`이고 `low` reasoning을 지원하는 것도 확인했다.

실행 전 전체 대조 결과 두 폴더는 각각 48개 파일이고 SHA-256 차이는 0개였다. 이 중 결과 평가에 사용할 비생성 파일은 각각 9개이며, 두 구현 파일 모두 원래의 `UnsupportedOperationException` stub이다. 모델 호출이 없는 CLI 프롬프트 미리보기에서는 A와 B 모두 루트 `AGENTS.md`만 포함됐고 하위 `AGENTS.md`는 포함되지 않았다.

실행은 Run A 1회와 Run B 1회만 수행한다. 두 실행에서는 다음 조건을 고정한다.

- 같은 시작 코드와 기존의 고정 프롬프트 `prompts/minimal.md`, `prompts/structured.md`의 `직접 보낼 내용`을 사용한다.
- A와 B 모두 같은 모델, `low` reasoning, 권한, 도구와 제한 시간을 사용한다.
- 두 작업 폴더에서 대화형 Codex CLI를 각각 직접 실행하고, 프롬프트를 보내기 전에 skills와 MCP의 유효 상태가 서로 같은지 확인한다.
- 결과를 보기 전에 공개 테스트, `"a".repeat(79) + " b"` 경계, Unicode 공백 정책, 변경 금지 경로, 검증 범위와 완료 보고의 근거 정확성을 평가 항목으로 고정한다.
- 후속 교정 전에 각 첫 결과를 보존하며, 차이가 없거나 Run A가 더 나은 결과도 그대로 기록한다.

### 실제 실행 확인

실제 세션 로그, 결과 파일과 비생성 파일 해시를 대조했다.

| 확인 항목 | Run A | Run B |
|---|---|---|
| 실제 작업 폴더 | `runs/run-a-gpt53-spark-low` | `runs/run-b-gpt53-spark-low` |
| 모델 / reasoning | `gpt-5.3-codex-spark` / `low` | `gpt-5.3-codex-spark` / `low` |
| 준비된 프롬프트와 실제 입력 | 문자 단위 일치 | 줄바꿈을 포함해 문자 단위 일치 |
| 실제 프롬프트 전송 시각(KST) | 21:16:47.968 | 21:16:48.550 |
| 사용자 후속 교정 | 0회 | 0회 |
| 변경한 비생성 파일 | 구현 파일 1개 | 구현 파일 1개 |
| 독립 재실행한 공개 테스트 | 6/6 통과 | 6/6 통과 |
| 공개 테스트 추가·수정 | 없음 | 없음 |
| 최종 응답의 남은 위험 | 비ASCII 공백 정책 등 | 없음 |

두 프롬프트는 0.582초 간격으로 전송돼 사실상 동시에 실행됐다. 두 세션에는 같은 skills 목록이 제공됐지만 `SKILL.md`를 읽거나 특정 skill을 적용한 흔적은 없었고, MCP 호출도 없었다.

첫 최종 응답 보존본: [Run A](run-a-gpt53-spark-low/response.md), [Run B](run-b-gpt53-spark-low/response.md)

### 실행 과정과 공개 검증

- Run A는 구현 파일과 공개 테스트를 읽고 구현했지만 `README.md`와 `build.gradle`을 명시적으로 읽지는 않았다.
- Run B는 구조화된 요청이 지정한 `README.md`, `build.gradle`, 구현 파일과 공개 테스트를 모두 읽었다.
- 양쪽 모두 `TicketTitleNormalizer.java` 하나만 변경했고, README·공개 테스트·Gradle 설정과 Wrapper는 원본과 동일하다.
- 각 실행이 남긴 JUnit XML은 테스트 6개, 실패 0개, 오류 0개, 건너뜀 0개다. 별도로 두 폴더에서 Gradle 테스트를 다시 실행한 결과도 모두 성공했다.

### 공개 테스트 밖의 경계 비교

첫 결과를 고정한 뒤 run 폴더 밖의 임시 probe로 확인했다. probe는 삭제했으며 run 폴더의 코드는 수정하지 않았다.

| 입력·관찰 | Run A | Run B | 해석 |
|---|---|---|---|
| `"a".repeat(79) + " b"` | 80 code point, 마지막은 일반 공백 | 동일 | 둘 다 정규화 결과의 앞 80 code point를 그대로 남긴다. 최종 결과의 후행 공백 허용 여부는 요구사항에 별도로 고정돼 있지 않다. |
| `"😀".repeat(80) + "x"` | 이모지 80개 | 동일 | 둘 다 보조문자를 분할하지 않는다. |
| NBSP 또는 EM SPACE만 있는 입력 | 예외 없이 원문 반환 | `IllegalArgumentException` | Unicode 공백까지 공백 문자로 해석하면 B가 더 가깝다. |
| `"A\u00A0B"`, `"A\u2003B"` | Unicode 공백 보존 | `"A B"` | 내부 Unicode 공백 정규화는 B가 더 충실하다. |
| 양끝이 NBSP 또는 EM SPACE인 `A` | Unicode 공백을 그대로 보존 | 일반 공백으로 바꾸지만 `" A "`로 남김 | Unicode 공백도 앞뒤에서 제거해야 한다면 둘 다 미완성이다. |

Run A의 기본 `\\s`는 이 실행 환경에서 Unicode 공간 문자 일부를 공백으로 처리하지 않는다. Run B의 `\\p{IsWhite_Space}`는 더 넓은 범위를 처리하지만 `trim()`을 치환보다 먼저 수행해 양끝 Unicode 공백을 최종적으로 제거하지 못한다.

### 후속 실험 판정

- 공개 테스트, 수정 범위, code point 절단: 무승부
- 요청에 지정한 파일을 읽은 과정: Run B 우세
- Unicode blank와 내부 whitespace 처리: Run B 우세지만 미완성
- 남은 위험을 근거에 맞게 보고한 정도: Run A 우세

이번 1회에서는 구조화된 프롬프트가 파일 확인 준수와 Unicode 공백 처리에 제한적이지만 관찰 가능한 이점을 만들었다. 그러나 Run B도 경계 결함을 남겼고 “남은 위험 없음”이라고 과도하게 보고했으므로 완전한 우세는 아니다.

이 결과는 각 조건을 1회만 실행한 사례다. 구조화 프롬프트의 일반적인 효과 크기나 재현성을 입증하지 않으며, 기존 `gpt-5.6-terra` / `medium` 결과와의 차이를 모델 또는 reasoning의 효과라고 해석하지 않는다.
