package lab.export;
public final class CsvCell {
    private CsvCell() {}
    public static String encode(String text) {
        String escaped = text.replace("\"", "\"\"");
        return text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
            ? "\"" + escaped + "\"" : escaped;
    }
}
