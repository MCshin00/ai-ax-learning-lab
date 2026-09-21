package lab.week10;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriorityTest {
    @ParameterizedTest
    @CsvSource({"P0,P0", "uRgEnT,P0", "P1,P1", "NoRmAl,P1", "P2,P2", "LoW,P2"})
    void acceptsCodesAndAliasesWithSurroundingWhitespace(String input, String expected) {
        assertEquals(expected, Priority.normalize(" \t\n\r\f\u000B" + input + "\u000B\f\r\n\t "));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t\n\r\f\u000B", "unknown", "P3", "ur gent", "\u00A0urgent\u00A0", "\u0000urgent\u0000"})
    void defaultsMissingAndUnknownValuesToP1(String input) {
        assertEquals("P1", Priority.normalize(input));
    }
}
