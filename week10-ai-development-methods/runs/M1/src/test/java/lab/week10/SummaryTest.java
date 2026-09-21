package lab.week10;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SummaryTest {
    @Test void existingDisplay() { assertEquals("[P1] 결제 오류", Summary.render("P1", "결제 오류")); }
}
