package lab.week05;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** 외부 형식의 책임을 계산 규칙과 나눈 제공 업무 코드. */
public final class RefundInput {
    private RefundInput() {}
    public static long calculate(JsonObject input) {
        long paid = amount(input.get("paid"));
        long fee = amount(input.get("fee"));
        JsonElement waived = input.get("fee_waived");
        if (waived != null && (!waived.isJsonPrimitive() || !waived.getAsJsonPrimitive().isBoolean())) {
            throw new IllegalArgumentException("fee_waived는 불리언이어야 합니다.");
        }
        return Refund.refundAmount(paid, fee, waived != null && waived.getAsBoolean());
    }
    private static long amount(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("[0-9]+")) {
            throw new IllegalArgumentException(Refund.AMOUNT_ERROR);
        }
        try { return Long.parseLong(value.getAsString()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(Refund.AMOUNT_ERROR); }
    }
}
