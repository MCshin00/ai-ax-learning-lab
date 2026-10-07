package lab.inquiry.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** 서비스별 조회 결과. 실패도 값으로 남겨 다른 서비스의 결과와 함께 보관한다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusResult(Outcome outcome, String serviceId, String state, String detail,
                           Long revision, ErrorCode code, String message) {
    public enum Outcome { FOUND, NOT_FOUND, UNAVAILABLE, INVALID_INPUT }
    public enum ErrorCode { DATA_UNREADABLE, DATA_INVALID, MCP_UNAVAILABLE, INVALID_RESPONSE, INVALID_ARGUMENTS }

    public StatusResult {
        if (outcome == null) throw new IllegalArgumentException("조회 결과 구분이 필요합니다.");
        if (outcome != Outcome.INVALID_INPUT && (serviceId == null || serviceId.isBlank()))
            throw new IllegalArgumentException("조회 대상이 필요합니다.");
        if (outcome == Outcome.FOUND) {
            if (state == null || state.isBlank() || detail == null || revision == null || revision < 0)
                throw new IllegalArgumentException("상태·설명·변경 번호가 필요합니다.");
        } else if (state != null || detail != null || revision != null) {
            throw new IllegalArgumentException("확인되지 않은 상태를 포함할 수 없습니다.");
        }
        if (outcome == Outcome.UNAVAILABLE || outcome == Outcome.INVALID_INPUT) {
            if (code == null || message == null || message.isBlank()
                    || (outcome == Outcome.INVALID_INPUT) != (code == ErrorCode.INVALID_ARGUMENTS))
                throw new IllegalArgumentException("오류 구분 값과 안내가 필요합니다.");
        } else if (code != null || message != null) {
            throw new IllegalArgumentException("정상 조회에는 오류를 포함할 수 없습니다.");
        }
    }

    public static StatusResult found(String id, String state, String detail, long revision) {
        return new StatusResult(Outcome.FOUND, id, state, detail, revision, null, null);
    }

    public static StatusResult notFound(String id) {
        return new StatusResult(Outcome.NOT_FOUND, id, null, null, null, null, null);
    }

    public static StatusResult unavailable(String id, ErrorCode code, String message) {
        return new StatusResult(Outcome.UNAVAILABLE, id, null, null, null, code, message);
    }

    public static StatusResult invalid(String id) {
        return new StatusResult(Outcome.INVALID_INPUT, id, null, null, null,
                ErrorCode.INVALID_ARGUMENTS, "serviceId는 공백이 아닌 문자열 하나여야 합니다.");
    }

    @JsonIgnore public boolean isError() {
        return outcome == Outcome.UNAVAILABLE || outcome == Outcome.INVALID_INPUT;
    }
}
