package lab.week04;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PrioritiesTest {
    @Test void p0ReturnsLabelWithoutBrackets() { assertEquals("긴급", Priorities.priorityLabel("P0")); }
    @Test void p1KeepsHighLabel() { assertEquals("높음", Priorities.priorityLabel("P1")); }
    @Test void p2KeepsNormalLabel() { assertEquals("보통", Priorities.priorityLabel("P2")); }
    @Test void p3KeepsLowLabel() { assertEquals("낮음", Priorities.priorityLabel("P3")); }
    @Test void unknownPriorityKeepsValue() { assertEquals("CUSTOM", Priorities.priorityLabel("CUSTOM")); }
}
