package lab.benchmark;

import java.util.Objects;

public final class TestSupport {
    private TestSupport() {}

    public static void equals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void throwsType(Class<? extends Throwable> type, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError(message + " wrong exception=" + error, error);
        }
        throw new AssertionError(message + " no exception");
    }
}
