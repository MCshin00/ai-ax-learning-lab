# 공용 학습 자료

여러 주차가 같은 조건으로 실험하도록 공유하는 도구와 벤치마크 묶음입니다. 주차별 학습 결과는 여기에 쓰지 않고 각 주차의 `runs/`에 남깁니다.

```text
shared/
├─ tools/
│  └─ runner/
├─ templates/
└─ benchmark/
   ├─ contracts/
   ├─ app/
   └─ tasks/
```

- `tools/runner/`: 실행 환경과 Codex JSONL을 수집·정리하는 선택 연구 도구입니다. 대표 수동 실행 뒤 반복성이 필요할 때 사용합니다. 사람 시간 타이머는 제공하지 않습니다.
- `templates/`: 실행 metadata, 근거 기록, 실패 카드와 회고 양식입니다. 필요한 항목만 사용합니다.
- `benchmark/contracts/`: 허용 범위와 완료 조건을 고정한 TASK A~C 계약입니다. 2주차부터 Skill과 MCP 실습에서 사용합니다.
- `benchmark/app/`: 같은 계약으로 여러 개발 방식을 비교하는 Java 17 시작 코드와 공개 테스트입니다. 4주차부터 사용합니다.
- `benchmark/tasks/`: 계약별 평가 초점을 짧게 정리한 task pack입니다. 4주차부터 사용합니다.

## Runner는 언제 쓰나

핵심 기록은 주차별 `runs/<run-id>/notes.md` 또는 비교 문서 하나에 요청·관찰·검증·판단을 모으면 됩니다. 테스트 결과와 코드에 링크하고 같은 내용을 다른 양식에 다시 쓰지 않습니다. 아래 환경 수집·원시 이벤트·export 절차는 Runner를 사용하는 선택 연구에 해당합니다.

먼저 IDE에서 과제와 프롬프트를 읽고 Codex 앱, IDE 확장 또는 대화형 CLI에서 대표 사례를 직접 실행합니다. 응답이 달라지는 이유와 확인할 지표를 이해한 뒤, 같은 조건을 반복 측정할 때만 runner를 사용합니다. 첫 실행부터 자동화하면 프롬프트, 작업 폴더와 권한이 결과에 미친 영향을 놓치기 쉽습니다.

Runner, 토큰·시간 비교와 여러 번의 실행은 핵심 완료 조건이 아닙니다. 기능 테스트, 계약 위반, 원시 실패와 `PASS / FAIL / NOT_VERIFIED`만으로 해당 주차의 핵심 질문에 답할 수 있다면 수동 대표 사례에서 멈춰도 됩니다.

Runner는 PowerShell이나 Bash 스크립트를 요구하지 않습니다. Windows, macOS와 Linux에서 같은 Python 명령을 사용합니다. [`codex exec` 공식 문서](https://learn.chatgpt.com/docs/developer-commands?surface=cli)에 따라 비대화형 실행, JSONL 출력과 명시적 `--cd` 작업 루트를 사용합니다.

### 1. 실행 전 확인

학습 저장소 루트에서 아래와 같이 `--dry-run`을 먼저 실행합니다. 실제 Codex 호출이나 파일 생성 없이 작업 폴더, 원시 출력 폴더와 명령 배열을 보여 줍니다.

```text
python shared/tools/runner/run_codex_exec.py --prompt week01-codex-prompt-comparison/prompts/structured.md --working-directory week01-codex-prompt-comparison/lab --output-directory week01-codex-prompt-comparison/.local/raw/run-b --model "사용할-모델" --reasoning medium --dry-run
```

`--working-directory`는 Codex가 읽고 작업할 저장소 내부의 구체적인 하위 폴더입니다. 보통 해당 주차의 `lab/`을 쓰고, Worktree 비교처럼 필요한 경우 `shared/benchmark/app` 같은 다른 하위 폴더를 지정할 수 있습니다. 저장소 루트는 작업 위치로 사용할 수 없으며, 작업 폴더와 raw 출력 폴더는 서로 겹치면 안 됩니다. `--output-directory`는 반드시 공개 Run과 같은 주차의 `.local/raw/<run-id>/` 구조로 둡니다.

### 2. 반복 실행

미리보기 내용이 맞으면 같은 명령에서 `--dry-run`만 뺍니다. 기본값은 `--sandbox read-only`라 코드와 Git index를 바꿀 권한이 없습니다. 코드를 수정하는 비교 실험이라면 분리한 브랜치나 작업 복사본을 준비하고 `--sandbox workspace-write`를 명시합니다. Runner 자체는 `git add`, commit 또는 push를 실행하지 않습니다. 실행 뒤에는 IDE의 Source Control과 diff에서 실제 변경을 확인합니다.

원시 폴더에는 다음 파일이 생깁니다.

```text
.local/raw/<run-id>/
├─ request.md
├─ events.jsonl
├─ stderr.log
├─ run.json
├─ environment.json
└─ summary.json
```

`environment.json`은 저장소 루트부터 선택한 작업 폴더까지 활성 범위에 있는 `AGENTS.md`, `.codex`의 config·hook·script와 `.agents/skills` 파일을 해시합니다. 캐시와 빌드 산출물은 제외하고, 개인 절대 경로는 기록하지 않습니다. 사용자 전역 설정까지 조건에서 제외하려면 `--ignore-user-config`를 사용합니다.

## 공개 기록으로 옮기기

`events.jsonl`, `stderr.log` 같은 원본은 세션 식별자, 개인 경로 또는 예상하지 못한 민감 정보를 포함할 수 있으므로 `.local/raw/`에 둡니다. GitHub에 공개할 요청, 최종 응답과 실행 조건은 별도의 export 단계로 만듭니다.

```text
python shared/tools/runner/export_public_run.py --repo-root . --raw-directory week01-codex-prompt-comparison/.local/raw/run-b --public-directory week01-codex-prompt-comparison/runs/run-b --evidence test=week01-codex-prompt-comparison/.local/test-result.txt --evidence diff=week01-codex-prompt-comparison/.local/change.diff --evidence failure=week01-codex-prompt-comparison/.local/failure-card.md
```

Export 결과는 다음처럼 구성됩니다.

```text
runs/<run-id>/
├─ request.md
├─ response.md
├─ run.json
└─ evidence/
   ├─ test-...
   ├─ diff-...
   └─ failure-...
```

Export 도구는 흔한 API key·token·secret, 인증 헤더, 세션·스레드 ID와 개인 절대 경로를 정제합니다. 원본 JSONL과 stderr는 복사하지 않습니다. 다만 자동 정제가 모든 프로젝트 고유 비밀 형식을 알 수는 없으므로, `runs/`를 stage하기 전에 IDE diff에서 요청·응답·metadata·증거 파일을 직접 검토해야 합니다.
