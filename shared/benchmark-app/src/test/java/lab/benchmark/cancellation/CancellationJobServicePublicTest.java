package lab.benchmark.cancellation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CancellationJobServicePublicTest {
    @Test
    void retriesTransientFailureAndReplaysIdempotently() {
        AtomicInteger calls = new AtomicInteger();
        CancellationGateway gateway = subscriptionId -> {
            if (calls.incrementAndGet() < 3) {
                throw new TransientGatewayException("temporary");
            }
        };
        CancellationJobService service = new CancellationJobService(gateway);
        service.register("J-1", "S-1");

        assertEquals(CancellationStatus.PENDING, service.statusOf("J-1"));
        assertEquals(
            CancellationStatus.SUCCEEDED,
            service.process("J-1", "support-1", "cancel-1")
        );
        assertEquals(3, service.attemptsOf("J-1"));
        assertEquals(3, calls.get());

        service.process("J-1", "support-1", "cancel-1");
        assertEquals(3, calls.get());
    }
}
