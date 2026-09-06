package lab.week01;

public final class TicketTitleNormalizer {
    private TicketTitleNormalizer() {}

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be null or blank");
        }

        String normalized = input.trim().replaceAll("\\p{IsWhite_Space}+", " ");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }

        return normalized
            .codePoints()
            .limit(80)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }
}
