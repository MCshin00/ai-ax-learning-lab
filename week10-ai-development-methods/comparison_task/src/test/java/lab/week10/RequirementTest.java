package lab.week10;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 공개 수용 사례. 새 요구를 구현하기 전에는 실패하는 것이 시작 상태다. */
class RequirementTest {
    @Test void normalizesAliasAndWhitespace() {
        assertEquals("[P0] 결제 오류", Summary.render(" Urgent ", "  결제   오류  "));
    }
    @Test void preservesNormalPriorityAndCollapsesLineBreak() {
        assertEquals("[P1] 상품 문의", Summary.render("normal", "상품\n문의"));
    }
    @Test void handlesLowAndUnknownPriority() {
        assertEquals("[P2] 화면 개선", Summary.render("LOW", "화면 개선"));
        assertEquals("[P1] 문의", Summary.render("later", "문의"));
    }
    @Test void emptyTitleWinsOverPriority() {
        assertEquals("확인 필요", Summary.render("urgent", " \t\n "));
        assertEquals("확인 필요", Summary.render(null, null));
    }
    @Test void treatsCommandLikeTextAsText() {
        assertEquals("[P1] $(example) ; <tag>", Summary.render(null, "$(example) ; <tag>"));
    }
}
