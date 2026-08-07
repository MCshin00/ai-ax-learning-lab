package lab.week01;

public final class TicketTitleNormalizer {
    private TicketTitleNormalizer() {}

    public static String normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }

        String normalized = input.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("input must not be blank");
        }

        return truncateToCodePointLimit(normalized, 80);
    }

    private static String truncateToCodePointLimit(String input, int maxCodePoints) {
        if (input.codePointCount(0, input.length()) <= maxCodePoints) {
            return input;
        }

        int index = 0;
        int count = 0;
        while (index < input.length() && count < maxCodePoints) {
            int codePoint = input.codePointAt(index);
            index += Character.charCount(codePoint);
            count++;
        }
        return input.substring(0, index);
    }
}
