package lab.inquiry.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record RequestData(String reviewId, String text, List<Service> services,
                          List<String> sources, List<Unprocessed> unprocessed) {
    public record Service(String serviceId, long revision) {}
    public record Unprocessed(@JsonInclude(JsonInclude.Include.NON_NULL) String serviceId, String code) {}
}
