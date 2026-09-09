package lab.week05;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class RefundInputTest {
    @Test void requirementACases() throws Exception {
        JsonArray cases = RefundInput.JSON.fromJson(Files.readString(Path.of("data/refund-cases.json")), JsonArray.class);
        for (JsonElement item : cases) {
            JsonObject row = item.getAsJsonObject(); JsonElement expected = row.get("expected");
            if (expected.isJsonObject()) {
                assertEquals(expected.getAsJsonObject().get("error").getAsString(),
                    assertThrows(IllegalArgumentException.class, () -> RefundInput.refundFromJson(row), row.get("id").getAsString()).getMessage());
            } else { assertEquals(expected.getAsLong(), RefundInput.refundFromJson(row), row.get("id").getAsString()); }
        }
    }
    @Test void invalidAmountsAreRejectedEvenWhenWaived() {
        for (String invalid : new String[]{"-1", "\"1000\"", "1000.0", "1e3", "true", "false", "null", "9223372036854775808"}) {
            for (String raw : new String[]{"{\"paid\":"+invalid+",\"fee\":0,\"fee_waived\":true}", "{\"paid\":1000,\"fee\":"+invalid+",\"fee_waived\":true}"})
                assertEquals(Refund.AMOUNT_ERROR, assertThrows(IllegalArgumentException.class, () -> RefundInput.refundFromJson(raw)).getMessage());
        }
    }
    @Test void nonBooleanWaiverIsRejected() {
        for (String invalid : new String[]{"0", "1", "\"false\"", "null"})
            assertEquals(RefundInput.WAIVER_ERROR, assertThrows(IllegalArgumentException.class,
                () -> RefundInput.refundFromJson("{\"paid\":10000,\"fee\":2000,\"fee_waived\":"+invalid+"}")).getMessage());
    }
    @Test void amountErrorPrecedesWaiverError() {
        assertEquals(Refund.AMOUNT_ERROR, assertThrows(IllegalArgumentException.class,
            () -> RefundInput.refundFromJson("{\"paid\":1000,\"fee\":true,\"fee_waived\":0}")).getMessage());
    }
    @Test void absentWaiverKeepsExistingCalculation() { assertEquals(8000, RefundInput.refundFromJson("{\"paid\":10000,\"fee\":2000}")); }
    @Test void malformedDocumentIsRejected() {
        for (String raw : new String[]{"not-json", "[]", "null", "{} {}", "{paid:1}", "{\"paid\":NaN}"})
            assertThrows(RuntimeException.class, () -> RefundInput.refundFromJson(raw));
    }
}
