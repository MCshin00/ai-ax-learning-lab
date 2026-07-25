# Java 17 벤치마크 시작 코드

Codex 작업 방식을 비교하는 외부 의존성 없는 작은 Java 프로젝트입니다. 세 서비스의 핵심 구현은 비어 있으므로 처음에는 공개 테스트가 실패합니다. 각 Task 계약에 따라 구현한 뒤 같은 명령으로 검증합니다.

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test.ps1
```

macOS·Linux·WSL:

```bash
bash scripts/test.sh
```

공개 테스트는 구현 중 빠른 피드백을 제공합니다. 10주차 본 실험에서는 구현 작업과 분리한 비공개 평가기가 추가 엣지 케이스를 검사합니다.
