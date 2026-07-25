package lab.benchmark.cancellation;

import java.util.List;
import lab.benchmark.common.AuditEntry;

public final class CancellationJobService {
    public CancellationJobService(CancellationGateway gateway) {
        throw new UnsupportedOperationException("TASK-C: implement constructor");
    }

    public void register(String jobId, String subscriptionId) {
        throw new UnsupportedOperationException("TASK-C: implement register");
    }

    public CancellationStatus process(String jobId, String actorId, String idempotencyKey) {
        throw new UnsupportedOperationException("TASK-C: implement process");
    }

    public CancellationStatus statusOf(String jobId) {
        throw new UnsupportedOperationException("TASK-C: implement statusOf");
    }

    public int attemptsOf(String jobId) {
        throw new UnsupportedOperationException("TASK-C: implement attemptsOf");
    }

    public List<AuditEntry> auditLog() {
        throw new UnsupportedOperationException("TASK-C: implement auditLog");
    }
}
