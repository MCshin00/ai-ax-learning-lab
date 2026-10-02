package lab.export;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
public final class ExportFilename {
    private ExportFilename() {}
    public static String name(LocalDate date) {
        return name(date, null);
    }

    public static String name(LocalDate date, String team) {
        if (team != null && team.isEmpty()) {
            throw new IllegalArgumentException("Team must not be empty");
        }
        String teamPart = team == null ? "" : encodeTeam(team) + "-";
        return "tickets-" + teamPart + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".csv";
    }

    private static String encodeTeam(String team) {
        StringBuilder encoded = new StringBuilder();
        team.codePoints().forEach(codePoint -> {
            if (codePoint <= 0x1F || "<>:\"/\\|?*%".indexOf(codePoint) >= 0) {
                byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                for (byte value : bytes) {
                    int unsigned = value & 0xFF;
                    encoded.append('%');
                    encoded.append("0123456789ABCDEF".charAt(unsigned >>> 4));
                    encoded.append("0123456789ABCDEF".charAt(unsigned & 0x0F));
                }
            } else {
                encoded.appendCodePoint(codePoint);
            }
        });
        return encoded.toString();
    }
}
