# 1주차 실행 조건과 재검증 근거

당시 비교에 사용한 실행 조건·입력 대조와 공개 테스트 재검증 기록이다. 코드의 차이와 그 의미는 [프롬프트 A/B 비교](RESULTS.md)에서 읽고, 실행 조건이나 결과 코드의 기준을 확인할 때 이 기록을 참고한다.

## 공개 테스트 재검증

- 재검증일: 2026-08-08
- 환경: Windows PowerShell, `javac 25.0.2`
- 빌드 계약: Gradle Wrapper 9.6.1, Java `--release 17`, JUnit Jupiter 5.14.1
- Windows 명령: `.\gradlew.bat test --console=plain --no-daemon`
- macOS·Linux·WSL 명령: `./gradlew test --console=plain --no-daemon`

네 실행 폴더에서 Wrapper 테스트를 각각 새로 실행했습니다. 모든 실행에서 공개 테스트 6개가 통과했고 실패·오류·건너뜀은 없었습니다.

| 실행 | 구현 파일 SHA-256 | 결과 |
|---|---|---|
| `run-a` | `660297547D04350F27B3D801C08C6613D0A868FA09FC73AAB1F582E6EA9D3C31` | 6/6 통과 |
| `run-b` | `2A689F460377B985A367E77AFABBDAB7F5EF9582090A1AF539154992A629B1CD` | 6/6 통과 |
| `run-a-gpt53-spark-low` | `8C3C3BD594626184F4049A4632CE7858346CBA82A8CD913C369575ED843925B2` | 6/6 통과 |
| `run-b-gpt53-spark-low` | `BD77577B8FDFA0BD27BDE91B848B856977CE5FF0500A3EDEACC2022D04BC118C` | 6/6 통과 |

`BUILD SUCCESSFUL`은 공개 테스트가 통과했다는 뜻이며, [RESULTS.md](RESULTS.md)와 각 실패 카드에서 다룬 공개 테스트 밖의 경계 동작까지 옳다는 뜻은 아닙니다. 생성된 `.gradle/`과 `build/`는 Git에 포함하지 않습니다.

## 첫 비교의 실행 조건과 확인 내역

### 실험 상태

- 실행일: 2026-08-05
- 방식: 서로 다른 작업 폴더에서 대화형 Codex CLI를 각각 실행
- 시작 코드: 실행 전에 기준 코드와 `run-a`, `run-b`가 동일한 상태임을 확인
- 첫 결과 고정: 두 실행 모두 후속 교정 요청을 보내기 전에 결과를 보존
- 사용자 후속 교정 횟수: A 0회, B 0회

### 폴더와 입력 검증

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

### 첫 결과 확인 내역

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

### 기록의 한계

- 전체 CLI 실행 인자를 별도 기록하지 않았다.
- 관찰 시간은 대화형 세션 이벤트 시각의 차이이며 자동 측정 Runner의 `T_wall` 값은 아니다.
- 경계 probe는 사전에 고정한 숨은 테스트가 아니라 첫 결과 이후 수행한 사후 검토다.
- 표본이 A 1회, B 1회이므로 프롬프트 방식 일반에 대한 인과 결론을 내릴 수 없다.

## 후속 비교의 준비와 실행 확인

### 당시 후속 비교의 목적과 고정 조건

이 후속 실험에서 확인할 대상은 모델이나 reasoning의 효과가 아니라 프롬프트 구조의 역할이다. 모델과 reasoning은 A와 B에 동일하게 적용하는 통제 조건이므로, 기존보다 낮은 조합을 선택해 과제가 너무 쉽게 해결되는 천장 효과를 줄일 수 있다.

> 동일한 `GPT-5.3-Codex Spark` / `low` 조건에서 구조화 프롬프트가 짧은 프롬프트보다 요구사항 누락을 줄이고 검증과 완료 보고를 더 충실하게 만드는가?

[OpenAI의 GPT-5.3-Codex 모델 문서](https://developers.openai.com/api/docs/models/gpt-5.3-codex)는 `low` reasoning을 지원한다고 명시한다. 실행할 때는 Codex CLI에 실제로 표시되는 Spark 모델 이름과 모델 ID를 결과 기록에 함께 남긴다.

실행 전 준비 경로는 다음과 같다.

- Run A: `runs/run-a-gpt53-spark-low`
- Run B: `runs/run-b-gpt53-spark-low`

두 폴더에는 원래 A/B 실행 당시와 같은 비생성 입력 파일 9개를 복사했다. 이전 Run 폴더에도 프롬프트 전부터 존재했던 `.gradle/`, `build/`, `.idea/` 상태도 동일하게 복사했지만, 이후 Day 3에 만든 하위 `AGENTS.md`는 포함하지 않았다. 로컬 Codex 모델 카탈로그에서 실제 모델 ID가 `gpt-5.3-codex-spark`이고 `low` reasoning을 지원하는 것도 확인했다.

실행 전 전체 대조 결과 두 폴더는 각각 48개 파일이고 SHA-256 차이는 0개였다. 이 중 결과 평가에 사용할 비생성 파일은 각각 9개이며, 두 구현 파일 모두 원래의 `UnsupportedOperationException` stub이다. 모델 호출이 없는 CLI 프롬프트 미리보기에서는 A와 B 모두 루트 `AGENTS.md`만 포함됐고 하위 `AGENTS.md`는 포함되지 않았다.

실행은 Run A 1회와 Run B 1회만 수행한다. 두 실행에서는 다음 조건을 고정한다.

- 같은 시작 코드와 고정한 [짧은 요청](run-a-gpt53-spark-low/request.md), [구조화된 요청](run-b-gpt53-spark-low/request.md)을 사용한다.
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
