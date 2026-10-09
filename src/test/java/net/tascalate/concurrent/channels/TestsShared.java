package net.tascalate.concurrent.channels;

class TestsShared {
    // Helper to mimic JUnit 5's assertThrows so we don't have to rewrite test logic
    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Throwable;
    }

    static <T extends Throwable> T assertThrows(Class<T> expectedType, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) {
                return expectedType.cast(t);
            }
            throw new AssertionError("Expected " + expectedType.getName() + " but got " + t.getClass().getName(), t);
        }
        throw new AssertionError("Expected " + expectedType.getName() + " to be thrown, but nothing was thrown.");
    }
}
