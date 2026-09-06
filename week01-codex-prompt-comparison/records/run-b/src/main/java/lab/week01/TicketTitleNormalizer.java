package lab.week01;

public final class TicketTitleNormalizer {
    private TicketTitleNormalizer() {}

    public static String normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Title must not be blank");
        }

        StringBuilder normalized = new StringBuilder();
        boolean previousWasWhitespace = true;

        for (int index = 0; index < input.length();) {
            int codePoint = input.codePointAt(index);
            index += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                if (!previousWasWhitespace) {
                    normalized.append(' ');
                    previousWasWhitespace = true;
                }
            } else {
                normalized.appendCodePoint(codePoint);
                previousWasWhitespace = false;
            }
        }

        if (normalized.length() > 0 && previousWasWhitespace) {
            normalized.setLength(normalized.length() - 1);
        }
        if (normalized.length() == 0) {
            throw new IllegalArgumentException("Title must not be blank");
        }

        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount <= 80) {
            return normalized.toString();
        }

        int endIndex = normalized.offsetByCodePoints(0, 80);
        return normalized.substring(0, endIndex);
    }
}
