# 테스트 디렉터리 지침

- 기존 테스트를 삭제하거나 건너뛰거나 약화하지 않습니다.
- 동작을 변경할 때는 수정 전에는 실패하고 수정 후에는 통과하는 회귀 테스트를 추가합니다.
- 인계하기 전에 IDE 또는 Gradle Wrapper로 `shared/benchmark/app`의 `clean`과 `test` 작업을 실행합니다. 자동화할 때는 Windows에서 `shared\benchmark\app\gradlew.bat -p shared\benchmark\app clean test`, macOS·Linux·WSL에서 `./shared/benchmark/app/gradlew -p ./shared/benchmark/app clean test`를 사용합니다.
- 실행하지 못한 테스트는 성공으로 추정하지 않고 `NOT_VERIFIED`로 보고합니다.
