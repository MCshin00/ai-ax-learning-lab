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

}
