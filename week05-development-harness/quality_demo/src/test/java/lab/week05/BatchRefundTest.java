package lab.week05;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BatchRefundTest {
    private JsonElement read(String name) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("data", name)));
    }
    @Test void providedBatchHasOneCorrectResultPerRequest() throws Exception {
        assertEquals(read("batch-expected.json"), BatchRefund.process(read("batch-requests.json")));
    }
    @Test void providedInputContracts() throws Exception {
        for (JsonElement value : read("refund-cases.json").getAsJsonArray()) {
            var row = value.getAsJsonObject();
            var expected = row.get("expected");
            if (expected.isJsonObject()) {
                var error = assertThrows(IllegalArgumentException.class, () -> RefundInput.calculate(row));
                assertEquals(expected.getAsJsonObject().get("error").getAsString(), error.getMessage());
            } else assertEquals(expected.getAsLong(), RefundInput.calculate(row));
        }
    }
    @Test void boundaryAndContainerFailuresStaySeparate() {
        assertEquals(new JsonArray(), BatchRefund.process(new JsonArray()));
        assertThrows(IllegalArgumentException.class, () -> BatchRefund.process(new JsonObject()));
        assertEquals(8000, RefundInput.calculate(JsonParser.parseString("{\"paid\":10000,\"fee\":2000}").getAsJsonObject()));
        for (String amount : new String[]{"1.0", "1e2", "9223372036854775808", "null"}) {
            var row = JsonParser.parseString("{\"paid\":" + amount + ",\"fee\":0}").getAsJsonObject();
            assertThrows(IllegalArgumentException.class, () -> RefundInput.calculate(row));
        }
    }
}
