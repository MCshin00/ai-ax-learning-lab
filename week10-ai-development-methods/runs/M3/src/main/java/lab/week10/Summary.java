package lab.week10;

/** 명령행 입력을 문자열로만 다룬다. 요청 내용을 명령으로 실행하지 않는다. */
public final class Summary {
    private Summary() {}
    public static String render(String priority, String title) {
        String normalizedTitle = Title.normalize(title);
        if (normalizedTitle.isEmpty()) return "확인 필요";
        return "[" + Priority.normalize(priority) + "] " + normalizedTitle;
    }
    public static void main(String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("우선순위와 제목을 지정하세요.");
        System.out.println(render(args[0], args[1]));
    }
}
