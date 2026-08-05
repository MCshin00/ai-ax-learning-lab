package lab.benchmark.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RefundServicePublicTest {
    @Test
    void approvesExecutesAndReplaysIdempotently() {
        RefundService service = new RefundService();
        service.register("R-1", "C-1");

        assertEquals(RefundStatus.REQUESTED, service.statusOf("R-1"));
        assertThrows(
            IllegalStateException.class,
            () -> service.execute("R-1", "support-1", "exec-early")
        );
        assertEquals(
            RefundStatus.APPROVED,
            service.approve("R-1", "support-1", "approve-1")
        );
        assertEquals(
            RefundStatus.EXECUTED,
            service.execute("R-1", "support-1", "execute-1")
        );

        int auditCount = service.auditLog().size();
        assertEquals(
            RefundStatus.EXECUTED,
            service.execute("R-1", "support-1", "execute-1")
        );
        assertEquals(auditCount, service.auditLog().size());
    }
}
