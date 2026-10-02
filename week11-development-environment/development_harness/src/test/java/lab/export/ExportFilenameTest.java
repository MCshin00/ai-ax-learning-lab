package lab.export;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportFilenameTest {
    @Test void filenameUsesTheDateProvidedByTheCaller() {
        assertEquals("tickets-2026-09-03.csv", ExportFilename.name(LocalDate.of(2026, 9, 3)));
        assertEquals("tickets-2027-01-02.csv", ExportFilename.name(LocalDate.of(2027, 1, 2)));
    }

    @Test void unspecifiedTeamKeepsTheExistingFilename() {
        assertEquals("tickets-2026-09-03.csv", ExportFilename.name(LocalDate.of(2026, 9, 3), null));
    }

    @Test void specifiedTeamAppearsBeforeTheCallersDate() {
        assertEquals("tickets-OPS-2026-09-03.csv", ExportFilename.name(LocalDate.of(2026, 9, 3), "OPS"));
        assertEquals("tickets-OPS-2027-01-02.csv", ExportFilename.name(LocalDate.of(2027, 1, 2), "OPS"));
    }

    @Test void emptyTeamIsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> ExportFilename.name(LocalDate.of(2026, 9, 3), ""));
    }

    @Test void ordinaryCharactersArePreserved() {
        assertEquals("tickets-운영 팀-2026-09-03.csv", ExportFilename.name(LocalDate.of(2026, 9, 3), "운영 팀"));
        assertEquals("tickets- -2026-09-03.csv", ExportFilename.name(LocalDate.of(2026, 9, 3), " "));
        assertEquals("tickets-CON.-2026-09-03.csv", ExportFilename.name(LocalDate.of(2026, 9, 3), "CON."));
    }

    @Test void filenameForbiddenCharactersUseUppercasePercentEncoding() {
        assertEquals("tickets-%3C%3E%3A%22%2F%5C%7C%3F%2A-2026-09-03.csv",
                ExportFilename.name(LocalDate.of(2026, 9, 3), "<>:\"/\\|?*"));
    }

    @Test void allControlCharactersAreEncoded() {
        StringBuilder team = new StringBuilder();
        StringBuilder expected = new StringBuilder("tickets-");
        for (int codePoint = 0; codePoint <= 0x1F; codePoint++) {
            team.append((char) codePoint);
            expected.append(String.format("%%%02X", codePoint));
        }
        expected.append("-2026-09-03.csv");
        assertEquals(expected.toString(), ExportFilename.name(LocalDate.of(2026, 9, 3), team.toString()));
    }

    @Test void percentEncodingDoesNotMergeDifferentTeamNames() {
        assertEquals("tickets-OPS%2F지원-2026-09-03.csv",
                ExportFilename.name(LocalDate.of(2026, 9, 3), "OPS/지원"));
        assertEquals("tickets-OPS%252F지원-2026-09-03.csv",
                ExportFilename.name(LocalDate.of(2026, 9, 3), "OPS%2F지원"));
    }
}
