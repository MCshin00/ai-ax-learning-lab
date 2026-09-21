package lab.week10;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TitleTest {
    @Test void missingTitleReturnsEmptyString() {
        assertEquals("", Title.normalize(null));
        assertEquals("", Title.normalize(""));
        assertEquals("", Title.normalize(" \t\n\r\f\u000B "));
    }

    @Test void normalizesJavaRegexWhitespace() {
        assertEquals("결제 오류 확인", Title.normalize(" \t결제\n\r오류\f\u000B확인\t "));
    }

    @Test void preservesCharactersOutsideWhitespaceContract() {
        assertEquals("\u0000제목\u001F", Title.normalize("\u0000제목\u001F"));
        assertEquals("\u00A0제목\u2003", Title.normalize("\u00A0제목\u2003"));
    }

    @Test void preservesLiteralTitleAndDisplayInstructionsAsData() {
        assertEquals("확인 필요", Title.normalize("확인 필요"));
        assertEquals("[P0] 제목", Title.normalize("[P0] 제목"));
    }
}
