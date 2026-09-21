package lab.week10;

import java.util.Locale;

public final class Priority {
    private Priority() {}

    public static String normalize(String value) {
        if (value == null) {
            return "P1";
        }
        String normalized = value.replaceAll("^\\s+|\\s+$", "").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "p0", "urgent" -> "P0";
            case "p2", "low" -> "P2";
            default -> "P1";
        };
    }
}
