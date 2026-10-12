package lab.inquiry.status;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RequestTestSupport {
    public static StatusClient client(Path directory) throws Exception {
        Files.copy(Path.of("data/services.json"), directory.resolve("services.json"));
        Files.copy(Path.of("data/runbooks.json"), directory.resolve("runbooks.json"));
        return new StatusClient(PlanSupport.server(directory));
    }
}
