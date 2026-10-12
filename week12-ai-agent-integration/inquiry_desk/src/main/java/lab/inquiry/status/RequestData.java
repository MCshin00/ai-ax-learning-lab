package lab.inquiry.status;

import java.util.List;

public record RequestData(String reviewId, String text, List<Service> services,
                          List<String> sources, List<Unprocessed> unprocessed) {
    public record Service(String serviceId, long revision) {}
    public record Unprocessed(String serviceId, String code) {}
}
