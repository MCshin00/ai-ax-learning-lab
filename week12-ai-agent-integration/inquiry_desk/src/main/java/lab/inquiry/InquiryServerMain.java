package lab.inquiry;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import lab.inquiry.status.StatusClient;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class InquiryServerMain {
    public static void main(String[] args) throws IOException {
        Path directory = Path.of("data");
        int port = 8080;
        try {
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 == args.length) throw new IllegalArgumentException();
                switch (args[i]) {
                    case "--data-dir" -> directory = Path.of(args[i + 1]);
                    case "--port" -> port = Integer.parseInt(args[i + 1]);
                    default -> throw new IllegalArgumentException();
                }
            }
            if (port < 0 || port > 65535) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            System.err.println("사용법: InquiryServerMain [--data-dir <자료 폴더>] [--port <번호>]");
            System.exit(2);
            return;
        }
        String key = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");
        boolean missing = false;
        for (String name : List.of("OPENAI_API_KEY", "OPENAI_MODEL")) {
            String value = name.equals("OPENAI_API_KEY") ? key : model;
            if (value == null || value.isBlank()) {
                System.err.println("환경변수가 없습니다: " + name);
                missing = true;
            }
        }
        if (missing) { System.exit(2); return; }
        var client = OpenAIOkHttpClient.builder().apiKey(key).timeout(Duration.ofSeconds(30)).maxRetries(0).build();
        var statuses = new StatusClient(directory);
        var inquiries = new InquirySession(model, request -> client.chat().completions().create(request), statuses::get, directory);
        var server = new InquiryHttpServer(port, new ReviewSession(inquiries, statuses::save));
        System.out.println("http://127.0.0.1:" + server.port() + " 에서 요청을 받습니다.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            statuses.close();
            client.close();
        }));
    }
}
