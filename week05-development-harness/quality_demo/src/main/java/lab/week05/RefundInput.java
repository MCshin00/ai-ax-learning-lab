package lab.week05;
import com.google.gson.*;
public final class RefundInput {
    public static final String WAIVER_ERROR = "fee_waived는 불리언이어야 합니다.";
    public static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private RefundInput() {}
    public static long refundFromJson(String raw) { return refundFromJson(JSON.fromJson(raw, JsonElement.class)); }
    public static long refundFromJson(JsonElement input) {
        if (input == null || !input.isJsonObject()) { throw new IllegalArgumentException("환불 요청은 객체여야 합니다."); }
        JsonObject row = input.getAsJsonObject();
        long paid = amount(row.get("paid"));
        long fee = amount(row.get("fee"));
        JsonElement waiver = row.get("fee_waived");
        boolean waived = false;
        if (waiver != null) {
            if (!waiver.isJsonPrimitive() || !waiver.getAsJsonPrimitive().isBoolean()) { throw new IllegalArgumentException(WAIVER_ERROR); }
            waived = waiver.getAsBoolean();
        }
        return Refund.refundAmount(paid, fee, waived);
    }
    private static long amount(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) { throw new IllegalArgumentException(Refund.AMOUNT_ERROR); }
        try {
            long result = Long.parseLong(value.getAsString());
            if (result < 0) { throw new NumberFormatException(); }
            return result;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException(Refund.AMOUNT_ERROR); }
    }
}
