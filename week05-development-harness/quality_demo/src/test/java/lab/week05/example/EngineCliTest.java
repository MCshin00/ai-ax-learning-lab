package lab.week05.example;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class EngineCliTest {
    @TempDir Path temp;
    @Test void malformedJobReplacesOldSuccessWithCurrentFailure() throws Exception {
        Path report = temp.resolve("current.json");
        Files.writeString(report, "{\"state\":\"SUCCEEDED\",\"run_id\":\"previous\"}");
        Files.writeString(temp.resolve("agent-message.txt"), "previous completion");
        Path job = temp.resolve("job.json"); Files.writeString(job, "[");
        assertEquals(1, EngineCli.run(job, "state", true, report));
        var result = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
        assertEquals("STOPPED", result.get("state").getAsString());
        assertNotEquals("previous", result.get("run_id").getAsString());
        assertFalse(Files.exists(temp.resolve("agent-message.txt")));
    }
}
