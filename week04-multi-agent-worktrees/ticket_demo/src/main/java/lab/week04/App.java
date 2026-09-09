package lab.week04;
public final class App {
    private App() {}
    public static String renderTicket(String title, String priority) {
        String displayTitle = Titles.normalizeTitle(title);
        if ("P0".equals(priority) && Titles.isBlank(title)) { displayTitle = "담당자 확인 필요"; }
        return "[" + Priorities.priorityLabel(priority) + "] " + displayTitle;
    }
    public static void main(String[] args) {
        System.out.println(renderTicket(args.length > 0 ? args[0] : "  로그인   오류  ", args.length > 1 ? args[1] : "P0"));
    }
}
