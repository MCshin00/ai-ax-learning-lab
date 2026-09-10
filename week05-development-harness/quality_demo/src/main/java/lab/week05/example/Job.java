package lab.week05.example;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 비교 예제가 요구에서 도출한 작업 계약. 학습자의 엔진이 구현할 인터페이스가 아니다. */
public record Job(String goal, Path workspace, List<String> contextFiles,
                  List<String> executeCommand, List<String> verifyCommand,
                  int repairLimit, int timeoutSeconds) {
    public List<String> missingInformation() {
        var missing = new ArrayList<String>();
        if (goal == null || goal.isBlank()) missing.add("어떤 작업 결과가 필요한지 goal에 지정하세요.");
        if (workspace == null || !Files.isDirectory(workspace)) missing.add("실제 작업 폴더를 지정하세요.");
        if (executeCommand == null || executeCommand.isEmpty()) missing.add("작업 실행 명령이 필요합니다.");
        if (verifyCommand == null || verifyCommand.isEmpty()) missing.add("완료를 판정할 검사 명령이 필요합니다.");
        if (repairLimit < 0 || repairLimit > 1) missing.add("이번 예제의 복구 상한은 0 또는 1입니다.");
        if (timeoutSeconds < 1) missing.add("양수 실행 제한 시간이 필요합니다.");
        if (workspace != null && contextFiles != null) for (String file : contextFiles) {
            if (!Files.isRegularFile(workspace.resolve(file))) missing.add("맥락 파일을 찾을 수 없습니다: " + file);
        }
        return List.copyOf(missing);
    }
    public String prompt() throws Exception {
        var prompt = new StringBuilder(goal);
        if (contextFiles != null) for (String file : contextFiles) {
            prompt.append("\n\n근거: ").append(file).append('\n')
                .append(Files.readString(workspace.resolve(file), StandardCharsets.UTF_8));
        }
        return prompt.toString();
    }
}
