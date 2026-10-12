package lab.inquiry.status;

public record RequestResult(Outcome outcome, String requestId, RequestData request,
                            String serviceId, String code, String message) {
    public enum Outcome { SAVED, FOUND, NOT_FOUND, CONFLICT, STATUS_CHANGED, INVALID_INPUT, UNAVAILABLE, STALE_REVIEW }

    public static RequestResult saved(String id) { return new RequestResult(Outcome.SAVED, id, null, null, null, null); }
    public static RequestResult found(String id, RequestData data) { return new RequestResult(Outcome.FOUND, id, data, null, null, null); }
    public static RequestResult absent(String id) { return new RequestResult(Outcome.NOT_FOUND, id, null, null, null, null); }
    public static RequestResult error(Outcome outcome, String serviceId, String code, String message) {
        return new RequestResult(outcome, null, null, serviceId, code, message);
    }
    public static RequestResult unavailable(String code, String message) { return error(Outcome.UNAVAILABLE, null, code, message); }
    public boolean isError() { return outcome != Outcome.SAVED && outcome != Outcome.FOUND && outcome != Outcome.NOT_FOUND; }
}
