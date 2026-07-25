package lab.benchmark;

import java.util.ArrayList;
import java.util.List;
import lab.benchmark.account.AccountUnlockServicePublicTest;
import lab.benchmark.cancellation.CancellationJobServicePublicTest;
import lab.benchmark.refund.RefundServicePublicTest;

public final class AllPublicTests {
    private record NamedTest(String name, Runnable test) {}

    public static void main(String[] args) {
        List<NamedTest> tests = List.of(
            new NamedTest("Task A refund", RefundServicePublicTest::run),
            new NamedTest("Task B account", AccountUnlockServicePublicTest::run),
            new NamedTest("Task C cancellation", CancellationJobServicePublicTest::run)
        );
        List<String> failures = new ArrayList<>();
        for (NamedTest test : tests) {
            try {
                test.test().run();
                System.out.println("PASS " + test.name());
            } catch (Throwable error) {
                failures.add(test.name() + ": " + error);
                System.out.println("FAIL " + test.name() + " -> " + error);
            }
        }
        if (!failures.isEmpty()) {
            System.err.println("\n" + failures.size() + " public test group(s) failed:");
            failures.forEach(failure -> System.err.println("- " + failure));
            System.exit(1);
        }
    }
}
