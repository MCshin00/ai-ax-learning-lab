package lab.week01;

public final class TicketTitleNormalizer {
    private TicketTitleNormalizer() {}

    public static String normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("제목은 null일 수 없습니다.");
        }

        StringBuilder normalized = new StringBuilder();
        boolean pendingSpace = false;
        int codePointCount = 0;

        for (int index = 0; index < input.length();) {
            int codePoint = input.codePointAt(index);
            index += Character.charCount(codePoint);

            if (isWhitespace(codePoint)) {
                if (normalized.length() > 0) {
                    pendingSpace = true;
                }
                continue;
            }

            if (pendingSpace) {
                if (codePointCount + 2 > 80) {
                    break;
                }
                normalized.append(' ');
                codePointCount++;
                pendingSpace = false;
            }

            if (codePointCount == 80) {
                break;
            }
            normalized.appendCodePoint(codePoint);
            codePointCount++;
        }

        if (normalized.length() == 0) {
            throw new IllegalArgumentException("제목은 비어 있을 수 없습니다.");
        }
        return normalized.toString();
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
