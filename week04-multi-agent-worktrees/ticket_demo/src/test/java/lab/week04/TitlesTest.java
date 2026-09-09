package lab.week04;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TitlesTest {
    @Test void normalTitleIsPreserved() { assertEquals("로그인 오류", Titles.normalizeTitle("로그인 오류")); }
    @Test void repeatedSpacesAreNormalized() { assertEquals("로그인 오류", Titles.normalizeTitle("  로그인   오류  ")); }
    @Test void tabsAndNewlinesAreNormalized() { assertEquals("로그인 오류 문의", Titles.normalizeTitle("\t로그인\t오류\n문의 \n")); }
    @Test void spacesOnlyTitleUsesPlaceholder() { assertEquals("(제목 없음)", Titles.normalizeTitle("   ")); }
    @Test void mixedWhitespaceOnlyTitleUsesPlaceholder() { assertEquals("(제목 없음)", Titles.normalizeTitle(" \t\n ")); }
}
