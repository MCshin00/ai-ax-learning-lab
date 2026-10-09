package lab.inquiry.status;

/** 앱 안의 서비스별 결과. 공개 JSON은 StatusWire에서만 만든다. */
public record LookupResult(Outcome outcome, String serviceId, String state, String detail,
                           Long revision, Cause code, String receivedCode, String message) {
    public enum Outcome { FOUND, NOT_FOUND, UNAVAILABLE, INVALID_INPUT }

    public enum Cause {
        DATA_UNREADABLE("서비스 자료를 읽을 수 없습니다."),
        DATA_INVALID("서비스 자료의 형식이 올바르지 않습니다."),
        INVALID_ARGUMENTS("serviceId는 공백이 아닌 문자열 하나여야 하고 다른 인수는 받지 않습니다."),
        MCP_UNAVAILABLE("상태 조회 서버와 통신하지 못했습니다."),
        INVALID_RESPONSE("상태 조회 서버의 응답이 약속한 형식과 다릅니다."),
        UNKNOWN("상태 조회 서버가 알 수 없는 원인 값을 보냈습니다.");

        private final String message;
        Cause(String message) { this.message = message; }
        public String message() { return message; }
    }

    public static LookupResult found(String id, String state, String detail, long revision) {
        return new LookupResult(Outcome.FOUND, id, state, detail, revision, null, null, null);
    }

    public static LookupResult absent(String id) {
        return new LookupResult(Outcome.NOT_FOUND, id, null, null, null, null, null, null);
    }

    public static LookupResult failed(String id, Cause cause) {
        return error(Outcome.UNAVAILABLE, id, cause, null, cause.message());
    }

    public static LookupResult invalid() {
        return error(Outcome.INVALID_INPUT, null, Cause.INVALID_ARGUMENTS, null, Cause.INVALID_ARGUMENTS.message());
    }

    static LookupResult error(Outcome outcome, String id, Cause code, String receivedCode, String message) {
        return new LookupResult(outcome, id, null, null, null, code, receivedCode, message);
    }

    public boolean isError() {
        return outcome == Outcome.UNAVAILABLE || outcome == Outcome.INVALID_INPUT;
    }
}
