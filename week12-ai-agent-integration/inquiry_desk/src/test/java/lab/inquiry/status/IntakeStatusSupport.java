package lab.inquiry.status;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import io.modelcontextprotocol.client.transport.ServerParameters;

/** 검사 JVM에서도 기존 서버를 실제 앱 클래스패스로 시작한다. */
public final class IntakeStatusSupport {
    public static StatusClient client(Path directory) {
        return new StatusClient(ServerParameters.builder(Path.of(System.getProperty("java.home"), "bin", "java").toString())
                .args("-Dfile.encoding=UTF-8", "-cp", System.getProperty("inquiry.test.classpath"),
                        IntakeStatusSupport.class.getName(), directory.toString()).build());
    }

    public static int calls(Path directory) throws IOException {
        Path log = directory.resolve("status-calls.txt");
        return Files.exists(log) ? Files.readAllLines(log).size() : 0;
    }

    public static void main(String[] args) {
        // 기존 서버로 전달되는 요청만 관찰한다. 조회 처리와 응답은 StatusServerMain 그대로다.
        System.setIn(new FilterInputStream(System.in) {
            private final ByteArrayOutputStream line = new ByteArrayOutputStream();

            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                int count = super.read(bytes, offset, length);
                for (int i = offset; i < offset + count; i++) {
                    if (bytes[i] == '\n') {
                        String text = line.toString(StandardCharsets.UTF_8);
                        line.reset();
                        if ("tools/call".equals(StatusWire.JSON.readTree(text).path("method").textValue()))
                            Files.writeString(Path.of(args[0], "status-calls.txt"), "call\n",
                                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } else line.write(bytes[i]);
                }
                return count;
            }
        });
        StatusServerMain.main(args);
    }
}
