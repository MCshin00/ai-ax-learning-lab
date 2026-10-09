package lab.inquiry.status;

import java.nio.file.Path;

/** IDE에서 프로젝트 루트를 작업 폴더로 실행한다. */
public final class OperationsMain {
    private OperationsMain() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    private static int run(String[] args) {
        int position = 0;
        Path directory = Path.of("data");
        if (args.length > 0 && args[0].equals("--data-dir")) {
            if (args.length < 2 || args[1].isEmpty()) return usage();
            directory = Path.of(args[1]);
            position = 2;
        }
        int remaining = args.length - position;
        if (remaining != 0 && (remaining != 2 || !args[position].equals("get"))) return usage();
        try (var client = new StatusClient(directory)) {
            if (remaining == 0) {
                var listing = client.listTools();
                System.out.println(StatusWire.text(listing.failure() == null
                        ? StatusWire.listing(listing.tools()) : StatusWire.fields(listing.failure())));
                return listing.failure() == null ? 0 : 1;
            }
            var result = client.get(args[position + 1]);
            System.out.println(StatusWire.text(StatusWire.fields(result)));
            return result.isError() ? 1 : 0;
        }
    }

    private static int usage() {
        System.err.println("사용법: OperationsMain [--data-dir <자료 폴더>] [get <서비스ID>]. 인수가 없으면 도구 목록을 조회합니다.");
        return 2;
    }
}
