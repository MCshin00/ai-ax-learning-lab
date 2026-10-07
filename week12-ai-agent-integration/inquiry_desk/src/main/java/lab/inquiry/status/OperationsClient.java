package lab.inquiry.status;

import java.nio.file.Path;
import java.util.Arrays;

/** IDE에서 프로젝트 루트를 작업 폴더로 사용한다. */
public final class OperationsClient {
    private OperationsClient() {}

    static Object execute(String[] args) {
        Path dataDirectory = Path.of("data");
        if (args.length > 0 && args[0].equals("--data-dir")) {
            if (args.length < 2 || args[1].isBlank()) return invalidUsage();
            try { dataDirectory = Path.of(args[1]); }
            catch (IllegalArgumentException error) { return invalidUsage(); }
            args = Arrays.copyOfRange(args, 2, args.length);
        }
        if (args.length != 0 && (args.length != 2 || !args[0].equals("get") || args[1].isBlank()))
            return invalidUsage();
        try (var client = new StatusClient(dataDirectory)) {
            return args.length == 0 ? client.listTools() : client.get(args[1]);
        }
    }

    private static StatusResult invalidUsage() {
        return new StatusResult(StatusResult.Outcome.INVALID_INPUT, null, null, null, null,
                StatusResult.ErrorCode.INVALID_ARGUMENTS,
                "인수: [--data-dir <자료 폴더>] [get <서비스ID>]. 인수가 없으면 도구 목록을 조회합니다.");
    }

    public static void main(String[] args) {
        System.out.println(StatusJson.text(execute(args)));
    }
}
