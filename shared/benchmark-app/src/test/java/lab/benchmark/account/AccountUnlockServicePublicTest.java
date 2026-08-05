package lab.benchmark.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AccountUnlockServicePublicTest {
    @Test
    void authorizesSupportAndReplaysIdempotently() {
        AccountUnlockService service = new AccountUnlockService();
        service.registerLocked("A-1");

        assertEquals(AccountStatus.LOCKED, service.statusOf("A-1"));
        assertThrows(
            SecurityException.class,
            () -> service.unlock("A-1", "user-1", ActorRole.USER, "unlock-user")
        );
        assertEquals(AccountStatus.LOCKED, service.statusOf("A-1"));
        assertEquals(
            AccountStatus.ACTIVE,
            service.unlock("A-1", "support-1", ActorRole.SUPPORT, "unlock-1")
        );

        int auditCount = service.auditLog().size();
        assertEquals(
            AccountStatus.ACTIVE,
            service.unlock("A-1", "support-1", ActorRole.SUPPORT, "unlock-1")
        );
        assertEquals(auditCount, service.auditLog().size());
    }
}
