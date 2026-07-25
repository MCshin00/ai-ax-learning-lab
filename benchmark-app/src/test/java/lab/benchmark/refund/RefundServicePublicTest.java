package lab.benchmark.refund;

import lab.benchmark.TestSupport;

public final class RefundServicePublicTest {
    public static void run() {
        RefundService service = new RefundService();
        service.register("R-1", "C-1");
        TestSupport.equals(RefundStatus.REQUESTED, service.statusOf("R-1"), "initial status");
        TestSupport.throwsType(IllegalStateException.class, () -> service.execute("R-1", "support-1", "exec-early"), "execute before approval");
        TestSupport.equals(RefundStatus.APPROVED, service.approve("R-1", "support-1", "approve-1"), "approve");
        TestSupport.equals(RefundStatus.EXECUTED, service.execute("R-1", "support-1", "execute-1"), "execute");
        int auditCount = service.auditLog().size();
        TestSupport.equals(RefundStatus.EXECUTED, service.execute("R-1", "support-1", "execute-1"), "idempotent replay");
        TestSupport.equals(auditCount, service.auditLog().size(), "no duplicate audit on replay");
    }
}
