# 1주차 실행 결과 재검증

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
