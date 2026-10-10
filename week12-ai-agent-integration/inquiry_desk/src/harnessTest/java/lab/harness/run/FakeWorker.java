package lab.harness.run;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 실제 코딩 작업자와 실제 검사 대신 실행하는 대역 프로세스. 인수: <동작> [파일] [표시]. */
public final class FakeWorker {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            // 입력이 닫혀야 끝까지 읽을 수 있다. 읽은 길이와 실행 표시를 결과에 남긴다.
            case "done" -> Files.writeString(Path.of(args[1]), result("done", args[2] + ":" + readAll(), "[]"), StandardCharsets.UTF_8);
            case "stopped" -> Files.writeString(Path.of(args[1]), result("stopped", args[2] + ":" + readAll(),
                "[{\"input\":\"vpn\",\"options\":[{\"choice\":\"구별\",\"result\":\"없음\"}]}]"), StandardCharsets.UTF_8);
            // 멈췄지만 질문이 없는 결과.
            case "partial" -> { readAll(); Files.writeString(Path.of(args[1]), result("stopped", "", "[]")); }
            case "silent" -> readAll();
            // 끝났다는 결과를 쓰고, 끝나기 직전에 중단 요청 파일을 만든다. 인수: done-then-stop <결과 파일> <표시> <중단 요청 파일>
            case "done-then-stop" -> {
                Files.writeString(Path.of(args[1]), result("done", args[2] + ":" + readAll(), "[]"), StandardCharsets.UTF_8);
                Files.writeString(Path.of(args[3]), "", StandardCharsets.UTF_8);
            }
            case "hang" -> Thread.sleep(600_000);
            // 하위 프로세스를 하나 띄우고 그 번호를 파일에 남긴 뒤 둘 다 끝나지 않는다.
            case "family" -> { spawn(args[1]); Thread.sleep(600_000); }
            // 질문을 남기고 멈춘 결과를 쓴 뒤 0이 아닌 코드로 끝난다.
            case "stopped-fail" -> {
                Files.writeString(Path.of(args[1]), result("stopped", args[2] + ":" + readAll(),
                    "[{\"input\":\"vpn\",\"options\":[{\"choice\":\"구별\",\"result\":\"없음\"}]}]"), StandardCharsets.UTF_8);
                System.exit(7);
            }
            // 종료 Hook이 판정을 남긴 실행. 작업 폴더(현재 폴더)에 Hook의 기록을 쓰고 끝났다는 결과를 남긴다. 인수: hooked <결과 파일> <표시> <hook_action>
            case "hooked" -> {
                Path record = Files.createDirectories(Path.of(".local/harness")).resolve("last-hook-event.json");
                Files.writeString(record, "{\"hook_action\":\"" + args[3] + "\",\"check_status\":\"failure\",\"summary\":\"대역 Hook 판정\"}",
                    StandardCharsets.UTF_8);
                Files.writeString(Path.of(args[1]), result("done", args[2] + ":" + readAll(), "[]"), StandardCharsets.UTF_8);
            }
            // 하위 프로세스를 띄운 뒤 지정한 시간만 있다가 자기만 정상 종료한다. 감싸는 명령이 먼저 끝나는 경우다.
            case "wrapper" -> { readAll(); spawn(args[1]); Thread.sleep(Long.parseLong(args[2])); }
            // 검사 대역. 지정한 빌드 폴더에 검사 결과 파일을 새로 쓴다. 인수: check <빌드 폴더> <pass|fail> [검사 작업...]
            case "check" -> {
                boolean pass = args[2].equals("pass");
                for (int i = 3; i < args.length; i++) {
                    Path group = Files.createDirectories(Path.of(args[1], "test-results", args[i]));
                    Files.writeString(group.resolve("TEST-fake.xml"), "<testsuite tests=\"1\" skipped=\"0\" failures=\""
                        + (pass ? 0 : 1) + "\" errors=\"0\"><testcase name=\"fake()\">"
                        + (pass ? "" : "<failure message=\"대역 실패\"/>") + "</testcase></testsuite>", StandardCharsets.UTF_8);
                }
                System.exit(pass ? 0 : 1);
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    private static int readAll() throws Exception {
        return new String(System.in.readAllBytes(), StandardCharsets.UTF_8).length();
    }

    private static void spawn(String pidFile) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
            FakeWorker.class.getName(), "hang").inheritIO().start();
        Files.writeString(Path.of(pidFile), Long.toString(child.pid()), StandardCharsets.UTF_8);
    }

    private static String result(String status, String summary, String questions) {
        return "{\"status\":\"" + status + "\",\"summary\":\"" + summary + "\",\"questions\":" + questions
            + ",\"changed_files\":[],\"checks\":[{\"what\":\"대역\",\"command\":\"없음\",\"result\":\"pass\",\"evidence\":\"\"}],"
            + "\"unchecked\":[],\"pr_body\":\"대역 본문\"}";
    }
}
