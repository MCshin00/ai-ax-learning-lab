package lab.benchmark.refund;

import java.util.List;
import lab.benchmark.common.AuditEntry;

public final class RefundService {
    public void register(String requestId, String ownerId) {
        throw new UnsupportedOperationException("TASK-A: implement register");
    }

    public RefundStatus approve(String requestId, String actorId, String idempotencyKey) {
        throw new UnsupportedOperationException("TASK-A: implement approve");
    }

    public RefundStatus execute(String requestId, String actorId, String idempotencyKey) {
        throw new UnsupportedOperationException("TASK-A: implement execute");
    }

    public RefundStatus statusOf(String requestId) {
        throw new UnsupportedOperationException("TASK-A: implement statusOf");
    }

    public List<AuditEntry> auditLog() {
        throw new UnsupportedOperationException("TASK-A: implement auditLog");
    }
}
