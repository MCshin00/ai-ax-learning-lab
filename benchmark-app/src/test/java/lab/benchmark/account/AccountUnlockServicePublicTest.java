package lab.benchmark.account;

import lab.benchmark.TestSupport;

public final class AccountUnlockServicePublicTest {
    public static void run() {
        AccountUnlockService service = new AccountUnlockService();
        service.registerLocked("A-1");
        TestSupport.equals(AccountStatus.LOCKED, service.statusOf("A-1"), "initial status");
        TestSupport.throwsType(SecurityException.class, () -> service.unlock("A-1", "user-1", ActorRole.USER, "unlock-user"), "USER cannot unlock");
        TestSupport.equals(AccountStatus.LOCKED, service.statusOf("A-1"), "failed authorization changes nothing");
        TestSupport.equals(AccountStatus.ACTIVE, service.unlock("A-1", "support-1", ActorRole.SUPPORT, "unlock-1"), "SUPPORT unlock");
        int auditCount = service.auditLog().size();
        TestSupport.equals(AccountStatus.ACTIVE, service.unlock("A-1", "support-1", ActorRole.SUPPORT, "unlock-1"), "idempotent replay");
        TestSupport.equals(auditCount, service.auditLog().size(), "no duplicate audit on replay");
    }
}
