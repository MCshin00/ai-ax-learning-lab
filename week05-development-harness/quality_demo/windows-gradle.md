## Windows에서 Gradle 소켓 오류가 날 때

Codex에서 Gradle이 컴파일·테스트를 시작하기 전에 `Unable to establish loopback connection`으로 실패하고, 상세 오류에 `UnixDomainSockets.connect0`와 `Invalid argument: connect`가 함께 나오면 아래 방법을 사용할 수 있습니다. 같은 명령을 Windows 시작 메뉴에서 직접 연 PowerShell에서도 비교합니다. [Codex의 유사 오류 보고](https://github.com/openai/codex/issues/40902)

Java는 내부 통신용 소켓의 주소로 임시 폴더의 경로를 사용합니다. 이 증상에서는 기본 임시 폴더의 일반 파일 읽기·쓰기가 가능해도 소켓 연결은 실패할 수 있습니다. `jdk.net.unixdomain.tmpdir`로 소켓용 폴더만 지정하면 일반 `TEMP`·`TMP`와 다른 도구의 임시 파일 위치를 유지할 수 있습니다. Windows 내부 원인을 고치는 설정은 아니며, `java.io.tmpdir`만 바꾸는 것과도 다릅니다. [Java의 소켓 주소용 폴더 선택 순서](https://docs.oracle.com/en/java/javase/24/core/java-networking.html)

### 5주차에만 적용하기

1. 소켓용 폴더를 만들고 설정에 넣을 경로를 확인합니다. 기본 Codex 데이터 폴더 안에 이 실습용 `shell-tmp` 폴더를 추가하는 Windows PowerShell 명령입니다.

   ```powershell
   $socketTemp = Join-Path $env:USERPROFILE '.codex\shell-tmp'
   New-Item -ItemType Directory -Force -Path $socketTemp | Out-Null
   $socketTemp.Replace('\', '/')
   ```

2. 5주차 폴더의 `.codex/config.toml`에 아래 항목을 추가합니다. 이 프로젝트에서 보면 `../.codex/config.toml`이며, 예제 Hook 설정이 있는 `quality_demo/.codex/config.toml`과는 다른 파일입니다. 자리표시자를 위 명령에서 확인한 실제 경로로 바꿉니다.

   ```toml
   [shell_environment_policy.set]
   JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir="<소켓용 폴더의 실제 경로>"'
   ```

   같은 표나 `JAVA_TOOL_OPTIONS`가 이미 있으면 중복으로 만들지 말고 기존 옵션에 합칩니다. 이 오류를 피하려고 앞서 추가한 `TEMP`·`TMP` 재지정은 제거합니다. 개인 전역 `config.toml`에도 같은 우회 설정이 남아 있다면 제거해야 다른 프로젝트에 적용되지 않습니다.

3. 개인 경로가 들어간 이 파일은 Git에 공유하지 않습니다. 학습 저장소의 `.git/info/exclude`에 다음 한 줄을 추가합니다. 이미 Git이 추적하는 파일은 제외 규칙만으로 숨겨지지 않으므로 추적 여부를 확인합니다.

   ```gitignore
   /week05-development-harness/.codex/config.toml
   ```

4. Codex를 다시 열고 `quality_demo/`를 작업 폴더로 사용합니다. 프로젝트를 신뢰한 상태에서 설정을 읽으면 기존 `.\gradlew.bat -q run`과 `.\gradlew.bat test`를 그대로 실행할 수 있습니다. 별도 실행 스크립트는 필요 없습니다.

적용 범위는 **5주차 폴더 또는 그 아래에서 시작한 Codex 작업과 그 하위 프로그램**입니다. 다른 프로젝트에서 시작한 작업에는 적용되지 않습니다. 같은 작업에서 `cd`로 이동하는 것만으로 환경변수가 해제되지는 않습니다. JVM이 시작할 때 옵션 적용 안내가 표준 오류에 출력될 수 있으므로, 로그를 공유할 때는 안내에 포함된 개인 경로를 자리표시자로 바꿉니다. [Codex의 프로젝트별 설정](https://learn.chatgpt.com/docs/config-file/config-advanced#project-config-files-codexconfigtoml)

### 학습 후 설정과 임시 폴더 정리하기

`%USERPROFILE%/.codex/shell-tmp`는 이 설정을 위해 만든 작업용 폴더입니다. 남은 파일이 자동으로 정리된다고 보장할 수 없으므로, 학습을 마쳤거나 앱 업데이트 후 기본 환경을 다시 확인할 때 다음 순서로 정리합니다.

1. 진행 중인 빌드·Java 실행을 끝내고, `quality_demo/`에서 `.\gradlew.bat --stop`으로 Gradle 데몬을 종료합니다.
2. `../.codex/config.toml`에서 추가한 소켓용 `JAVA_TOOL_OPTIONS` 옵션을 제거합니다. 이 설정만 있던 파일이면 파일을 삭제해도 됩니다. 다른 옵션·Hook 설정은 보존합니다.
3. Codex를 다시 열어 기존 작업에 남아 있던 환경도 해제합니다. 다른 작업에서 같은 임시 폴더를 사용 중이면 그 작업이 끝난 뒤 정리합니다.
4. 파일 탐색기 주소 표시줄에 `%USERPROFILE%\.codex\shell-tmp`를 입력하고, 이 `shell-tmp` 폴더만 삭제합니다. 상위 `.codex` 폴더에는 Codex의 설정·인증·작업 기록이 있으므로 통째로 삭제하지 않습니다.

학습 중 이 설정을 제거한 뒤 같은 소켓 오류가 다시 나면, 위 적용 절차로 폴더와 설정을 다시 준비합니다.
