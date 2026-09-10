package lab.week05.harness;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HarnessCliTest {
    @TempDir Path temp;
    @Test void demoAndLaterInvalidInputProduceSeparateCurrentResults() throws Exception {
        Files.writeString(temp.resolve("task.md"), "제공 배치를 확인하세요.");
        Files.writeString(temp.resolve("rules.md"), "모든 요청의 결과를 검사하세요.");
        Path request = temp.resolve("work.json");
        Files.writeString(request, """
            {"workspace":".","task":"task.md","context":["rules.md"],
             "verify":{"windows":["unused"],"posix":["unused"]},"maxRepairs":1}
            """);
        Path reports = temp.resolve("reports");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        assertEquals(0, HarnessCli.run(request, true, reports, output));
        JsonObject success = JsonParser.parseString(bytes.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("SIMULATED_RESPONSE", success.get("mode").getAsString());
        assertEquals("SUCCEEDED", success.getAsJsonObject("outcome").get("status").getAsString());
        assertEquals(2, success.getAsJsonObject("outcome").get("attempts").getAsInt());
        Path previous = Path.of(success.get("resultFile").getAsString());
        String previousText = Files.readString(previous);

        Files.writeString(request, "{");
        bytes.reset();
        assertEquals(2, HarnessCli.run(request, true, reports, output));
        JsonObject invalid = JsonParser.parseString(bytes.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("NEEDS_INPUT", invalid.getAsJsonObject("outcome").get("status").getAsString());
        assertEquals(0, invalid.getAsJsonObject("outcome").get("attempts").getAsInt());
        assertNotEquals(success.get("runId"), invalid.get("runId"));
        assertEquals(previousText, Files.readString(previous));
        assertTrue(Files.isRegularFile(Path.of(invalid.get("resultFile").getAsString())));
    }
}
