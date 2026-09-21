package lab.week10;

import java.util.Locale;

public final class Priority {
    private Priority() {}
    public static String normalize(String value) {
        if (value == null) return "P1";
        return switch (value.replaceAll("^\\s+|\\s+$", "").toLowerCase(Locale.ROOT)) {
            case "p0", "urgent" -> "P0";
            case "p1", "normal" -> "P1";
            case "p2", "low" -> "P2";
            default -> "P1";
        };
    }
}
