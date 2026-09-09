package lab.week05;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RefundTest {
    @Test void regularRefund() { assertEquals(8000, Refund.refundAmount(10000, 2000)); }
    @Test void refundNeverBecomesNegative() { assertEquals(0, Refund.refundAmount(1000, 2000)); }
    @Test void rejectsNegativeInputs() {
        assertThrows(IllegalArgumentException.class, () -> Refund.refundAmount(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> Refund.refundAmount(1, -1));
    }
    @Test void waiverPreservesInputValidation() {
        assertEquals(10000, Refund.refundAmount(10000, 2000, true));
        assertEquals(Refund.AMOUNT_ERROR, assertThrows(IllegalArgumentException.class, () -> Refund.refundAmount(10000, -1, true)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> Refund.refundAmount(-1, 0, true));
    }
    @Test void zeroAndLargestRepresentableAmounts() {
        assertEquals(0, Refund.refundAmount(0, 0));
        assertEquals(0, Refund.refundAmount(0, 2000, true));
        assertEquals(10000, Refund.refundAmount(10000, 0));
        assertEquals(Long.MAX_VALUE, Refund.refundAmount(Long.MAX_VALUE, 0));
        assertEquals(0, Refund.refundAmount(0, Long.MAX_VALUE));
    }
}
