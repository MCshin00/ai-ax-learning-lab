package lab.inquiry.intake;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import lab.inquiry.status.StatusClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class IntakeMain {
    public static void main(String[] args) throws IOException { System.exit(run(args)); }

    private static int run(String[] args) throws IOException {
        Path directory = Path.of("data");
        if (args.length != 0) {
            if (args.length != 2 || !args[0].equals("--data-dir")) {
                System.err.println("사용법: IntakeMain [--data-dir <자료 폴더>]. 입력: 대화ID|발언");
                return 2;
            }
            directory = Path.of(args[1]);
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
        if (missing) return 2;
        var client = OpenAIOkHttpClient.builder().apiKey(key).timeout(Duration.ofSeconds(30)).maxRetries(0).build();
        try (var statuses = new StatusClient(directory)) {
            var session = new IntakeSession(model, request -> client.chat().completions().create(request), statuses);
            return process(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                    new PrintWriter(System.out, true, StandardCharsets.UTF_8), session);
        } finally { client.close(); }
    }

    static int process(BufferedReader input, PrintWriter output, IntakeSession session) throws IOException {
        String line;
        while ((line = input.readLine()) != null) {
            output.println(IntakeWire.text(IntakeWire.fields(session.accept(line))));
            output.flush();
        }
        return 0;
    }
}
