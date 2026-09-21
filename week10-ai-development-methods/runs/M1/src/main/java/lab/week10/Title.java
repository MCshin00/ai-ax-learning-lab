package lab.week10;

public final class Title {
    private Title() {}
    public static String normalize(String value) {
        if (value == null) return "";
        return value.replaceAll("\\s+", " ").replaceAll("^ +| +$", "");
    }
}
