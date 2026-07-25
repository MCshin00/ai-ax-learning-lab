package lab.week01;

public final class TicketTitleNormalizerTest {
    public static void main(String[] args) {
        expect("결제 오류 문의", TicketTitleNormalizer.normalize("  결제   오류\n문의  "));
        expect("A", TicketTitleNormalizer.normalize("A"));
        expectThrows(() -> TicketTitleNormalizer.normalize(null));
        expectThrows(() -> TicketTitleNormalizer.normalize("   \n\t"));
        String longText = "가".repeat(100);
        String normalizedLongText = TicketTitleNormalizer.normalize(longText);
        expect(80, normalizedLongText.codePointCount(0, normalizedLongText.length()));
        expect("😀".repeat(80), TicketTitleNormalizer.normalize("😀".repeat(81)));
        System.out.println("PASS");
    }

    private static void expect(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }

    private static void expectThrows(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected exception");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
