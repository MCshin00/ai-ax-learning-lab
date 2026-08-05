package lab.benchmark.account;

import java.util.List;
import lab.benchmark.common.AuditEntry;

public final class AccountUnlockService {
    public void registerLocked(String accountId) {
        throw new UnsupportedOperationException("TASK-B: implement registerLocked");
    }

    public AccountStatus unlock(String accountId, String actorId, ActorRole role, String idempotencyKey) {
        throw new UnsupportedOperationException("TASK-B: implement unlock");
    }

    public AccountStatus statusOf(String accountId) {
        throw new UnsupportedOperationException("TASK-B: implement statusOf");
    }

    public List<AuditEntry> auditLog() {
        throw new UnsupportedOperationException("TASK-B: implement auditLog");
    }
}
