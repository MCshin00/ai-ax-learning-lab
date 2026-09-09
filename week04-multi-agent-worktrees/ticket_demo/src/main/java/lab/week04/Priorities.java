package lab.week04;
import java.util.Map;
public final class Priorities {
    private static final Map<String, String> LABELS = Map.of("P0", "긴급", "P1", "높음", "P2", "보통", "P3", "낮음");
    private Priorities() {}
    public static String priorityLabel(String priority) { return LABELS.getOrDefault(priority, priority); }
}
