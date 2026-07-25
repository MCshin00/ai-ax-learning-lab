package lab.benchmark.cancellation;

import java.util.concurrent.atomic.AtomicInteger;
import lab.benchmark.TestSupport;

public final class CancellationJobServicePublicTest {
    public static void run() {
        AtomicInteger calls = new AtomicInteger();
        CancellationGateway gateway = subscriptionId -> {
            if (calls.incrementAndGet() < 3) throw new TransientGatewayException("temporary");
        };
        CancellationJobService service = new CancellationJobService(gateway);
        service.register("J-1", "S-1");
        TestSupport.equals(CancellationStatus.PENDING, service.statusOf("J-1"), "initial status");
        TestSupport.equals(CancellationStatus.SUCCEEDED, service.process("J-1", "support-1", "cancel-1"), "retry then succeed");
        TestSupport.equals(3, service.attemptsOf("J-1"), "three attempts");
        TestSupport.equals(3, calls.get(), "gateway calls");
        service.process("J-1", "support-1", "cancel-1");
        TestSupport.equals(3, calls.get(), "no gateway call after success replay");
    }
}
