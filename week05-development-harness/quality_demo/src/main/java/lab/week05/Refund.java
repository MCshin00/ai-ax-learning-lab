package lab.week05;
public final class Refund {
    public static final String AMOUNT_ERROR = "금액은 0 이상의 정수여야 합니다.";
    private Refund() {}
    public static long refundAmount(long paid, long fee) {
        return refundAmount(paid, fee, false);
    }
    public static long refundAmount(long paid, long fee, boolean feeWaived) {
        if (paid < 0 || fee < 0) { throw new IllegalArgumentException(AMOUNT_ERROR); }
        return feeWaived ? paid : Math.max(0L, paid - fee);
    }
    public static void main(String[] args) { System.out.println(refundAmount(10000, 2000)); }
}
