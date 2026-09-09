package lab.week04;
import java.util.regex.Pattern;
public final class Titles {
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{javaWhitespace}\\p{Zs}]+");
    private Titles() {}
    public static String normalizeTitle(String title) {
        String normalized = WHITESPACE.matcher(title).replaceAll(" ").strip();
        return normalized.isEmpty() ? "(제목 없음)" : normalized;
    }
    public static boolean isBlank(String title) {
        return WHITESPACE.matcher(title).replaceAll("").isEmpty();
    }
}
