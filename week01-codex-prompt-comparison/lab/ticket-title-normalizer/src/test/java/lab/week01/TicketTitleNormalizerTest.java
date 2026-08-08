package lab.week01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TicketTitleNormalizerTest {
    @Test
    void trimsAndCollapsesWhitespace() {
        assertEquals(
            "결제 오류 문의",
            TicketTitleNormalizer.normalize("  결제   오류\n문의  ")
        );
    }

    @Test
    void keepsShortTitle() {
        assertEquals("A", TicketTitleNormalizer.normalize("A"));
    }

    @Test
    void rejectsNull() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TicketTitleNormalizer.normalize(null)
        );
    }

    @Test
    void rejectsBlankTitle() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TicketTitleNormalizer.normalize("   \n\t")
        );
    }

    @Test
    void truncatesAtEightyCodePoints() {
        String normalized = TicketTitleNormalizer.normalize(
            "가".repeat(79) + "나" + "다".repeat(20)
        );

        assertEquals("가".repeat(79) + "나", normalized);
    }

    @Test
    void doesNotSplitSupplementaryCodePoint() {
        assertEquals(
            "😀".repeat(80),
            TicketTitleNormalizer.normalize("😀".repeat(81))
        );
    }
}
