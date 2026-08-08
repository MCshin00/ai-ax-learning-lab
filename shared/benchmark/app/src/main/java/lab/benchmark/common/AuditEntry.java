package lab.benchmark.common;

public record AuditEntry(String action, String subjectId, String actorId, String detail, String idempotencyKey) {
    public AuditEntry {
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action is required");
        if (subjectId == null || subjectId.isBlank()) throw new IllegalArgumentException("subjectId is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
    }
}
