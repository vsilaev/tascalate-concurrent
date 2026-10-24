package net.tascalate.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.tascalate.concurrent.var.ContextRelay;
import net.tascalate.concurrent.var.ContextVar;

/**
 * Comprehensive JUnit 4 tests for {@link TaskScope}.
 */
public class TaskScopeTest {

    private ExecutorService sharedExecutor;

    @Before
    public void setUp() {
        sharedExecutor = Executors.newFixedThreadPool(4);
    }

    @After
    public void tearDown() {
        if (sharedExecutor != null) {
            sharedExecutor.shutdownNow();
        }
    }

    // =========================================================================
    // Builder Configuration Tests
    // =========================================================================

    @Test
    public void testBuilderWithExplicitName() {
        try (TaskScope scope = TaskScope.builder()
                .name("MyTestScope")
                .executor(sharedExecutor)
                .build()) {
            assertEquals("MyTestScope", scope.toString());
        }
    }

    @Test
    public void testBuilderGeneratesDefaultName() {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .build()) {
            assertTrue("Default name should start with 'scope_'", scope.toString().startsWith("scope_"));
        }
    }

    @Test
    public void testBuilderShutsDownOwnedExecutor() {
        ExecutorService ownedExecutor = Executors.newSingleThreadExecutor();
        try (TaskScope scope = TaskScope.builder()
                .executor(ownedExecutor, true)
                .build()) {
            scope.fork(() -> {});
        }
        assertTrue("Owned executor should be shut down after close", ownedExecutor.isShutdown());
    }

    @Test
    public void testBuilderDoesNotShutdownUnownedExecutor() {
        ExecutorService externalExecutor = Executors.newSingleThreadExecutor();
        try (TaskScope scope = TaskScope.builder()
                .executor(externalExecutor)
                .build()) {
            scope.fork(() -> {});
        }
        assertFalse("External executor should NOT be shut down", externalExecutor.isShutdown());
        externalExecutor.shutdownNow();
    }

    // =========================================================================
    // Forking and Stream Consumption Tests
    // =========================================================================

    @Test
    public void testForkRunnableAndCollectAll() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            scope.fork(counter::incrementAndGet);
            scope.fork(counter::incrementAndGet);
            scope.fork(counter::incrementAndGet);

            List<Promise<Object>> results = scope.completions()
                    .limit(3)
                    .collect(Collectors.toList());

            assertEquals(3, results.size());
            assertEquals(3, counter.get());
        }
    }

    @Test
    public void testForkCallableAndMapResults() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            scope.fork(() -> "Alpha");
            scope.fork(() -> "Beta");

            List<String> results = scope.completions()
                    .filter(Promise.State.SUCCESS)
                    .map(p -> (String) p.join())
                    .limit(2)
                    .collect(Collectors.toList());

            assertEquals(2, results.size());
            assertTrue(results.contains("Alpha"));
            assertTrue(results.contains("Beta"));
        }
    }

    @Test
    public void testCompletionsFiltersOutFailures() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            scope.fork(() -> "SuccessTask");
            scope.fork(() -> {
                throw new RuntimeException("ExpectedFailure");
            });

            List<Object> results = scope.completions()
                    .filter(Promise.State.SUCCESS)
                    .map(Promise::join)
                    .collect(Collectors.toList());

            assertEquals(1, results.size());
            assertEquals("SuccessTask", results.get(0));
        }
    }

    @Test(expected = CompletionException.class)
    public void testJoinThrowsOnFailedTask() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            scope.fork(() -> {
                throw new IllegalStateException("Boom");
            });
            scope.completions().map(Promise::join).findFirst();
        }
    }

    @Test
    public void testStreamShortCircuitsAndCancelsRemaining() throws Exception {
        CountDownLatch slowTasksStarted = new CountDownLatch(3);
        CountDownLatch slowTasksInterrupted = new CountDownLatch(3);

        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            scope.fork(() -> "Fast1");
            scope.fork(() -> "Fast2");

            for (int i = 0; i < 3; i++) {
                scope.fork(() -> {
                    slowTasksStarted.countDown();
                    try {
                        Thread.sleep(Duration.ofMinutes(1));
                    } catch (InterruptedException e) {
                        slowTasksInterrupted.countDown();
                        Thread.currentThread().interrupt();
                    }
                    return "Slow";
                });
            }

            List<Object> results = scope.completions()
                    .limit(2)
                    .map(Promise::join)
                    .collect(Collectors.toList());

            assertEquals("Should collect exactly 2 fast results", 2, results.size());

            assertTrue("Slow tasks should have started", slowTasksStarted.await(2, TimeUnit.SECONDS));
            assertEquals("No slow tasks should be interrupted yet", 3, slowTasksInterrupted.getCount());
        }

        assertTrue("Slow tasks should have been interrupted by scope.close()",
                slowTasksInterrupted.await(2, TimeUnit.SECONDS));
    }

    // =========================================================================
    // Reset and Reuse Tests
    // =========================================================================

    @Test
    public void testResetAllowsScopeReuse() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            scope.fork(() -> "Batch1-Result");
            List<Object> batch1 = scope.completions()
                    .map(Promise::join)
                    .collect(Collectors.toList());
            assertEquals(1, batch1.size());
            assertEquals("Batch1-Result", batch1.get(0));

            scope.reset();

            scope.fork(() -> "Batch2-Result");
            List<Object> batch2 = scope.completions()
                    .map(Promise::join)
                    .collect(Collectors.toList());
            assertEquals(1, batch2.size());
            assertEquals("Batch2-Result", batch2.get(0));
        }
    }

    // =========================================================================
    // Blocking Take / TakeValue Tests
    // =========================================================================

    @Test
    public void testTakeReturnsNextCompletedPromise() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            scope.fork(() -> "TakeValue");

            Promise<Object> p = scope.take();
            assertNotNull(p);
            assertEquals("TakeValue", p.join());
        }
    }

    @Test
    public void testTakeWithFilterSkipsNonMatching() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            scope.fork(() -> {
                throw new RuntimeException("Fail");
            });
            scope.fork(() -> "Success");

            Promise<Object> p = scope.take(Promise.State.SUCCESS);
            assertNotNull(p);
            assertEquals("Success", p.join());
        }
    }

    @Test
    public void testTakeValueReturnsOptional() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            scope.fork(() -> "OptionalValue");

            Optional<Object> val = scope.takeValue();
            assertNotNull(val);
            assertTrue(val.isPresent());
            assertEquals("OptionalValue", val.get());
        }
    }

    @Test
    public void testTakeReturnsNullWhenExhausted() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            scope.fork(() -> "One");

            assertNotNull(scope.take());
            assertEquals(null, scope.take());
        }
    }

    // =========================================================================
    // Context Propagation Tests
    // =========================================================================

    @Test
    public void testContextRelayPropagatesToForkedTasks() throws Exception {
        ContextVar<String> testVar = ContextVar.newInstance("test-var");
        ContextRelay<?> relay = testVar.relay();

        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .contextualizer(relay)
                .build()) {

            testVar.bind("PropagatedValue").run(() -> {
                scope.fork(() -> {
                    assertEquals("PropagatedValue", testVar.get());
                });
            });

            scope.completions().limit(1).forEach(Promise::join);
        }
    }

    // =========================================================================
    // Nested Scope (toBuilder) Tests
    // =========================================================================

    @Test
    public void testToBuilderCreatesChildWithInheritedExecutor() throws Exception {
        ExecutorService parentExecutor = Executors.newSingleThreadExecutor();

        try (TaskScope parent = TaskScope.builder()
                .name("ParentScope")
                .executor(parentExecutor, true)
                .build()) {

            try (TaskScope child = parent.toBuilder()
                    .name("ChildScope")
                    .build()) {

                assertEquals("ChildScope", child.toString());
                child.fork(() -> {});
            }

            assertFalse("Parent executor should remain active after child closes", parentExecutor.isShutdown());
        }

        assertTrue("Parent executor should be shut down after parent closes", parentExecutor.isShutdown());
    }

    // =========================================================================
    // Global Timeout Pattern Tests (manual takeWhile approach)
    // =========================================================================

    @Test
    public void testGlobalTimeoutPatternWithTakeWhile() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {

            Promise<Object> timeoutPromise = scope.fork(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "TIMEOUT";
            });

            for (int i = 0; i < 3; i++) {
                scope.fork(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "SLOW_RESULT";
                });
            }

            List<Object> results = scope.completions()
                    .takeWhile(p -> p != timeoutPromise)
                    .filter(Promise.State.SUCCESS)
                    .map(Promise::join)
                    .collect(Collectors.toList());

            assertEquals("Should collect 0 results before timeout", 0, results.size());
        }
    }

    // =========================================================================
    // Built-in Scope-Level Timeout Tests
    // =========================================================================

    @Test
    public void testTimeoutCancelsRunningTasks() throws Exception {
        CountDownLatch tasksStarted = new CountDownLatch(2);
        CountDownLatch tasksInterrupted = new CountDownLatch(2);

        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(100))
                .build()) {

            for (int i = 0; i < 2; i++) {
                scope.fork(() -> {
                    tasksStarted.countDown();
                    try {
                        Thread.sleep(Duration.ofMinutes(1));
                    } catch (InterruptedException e) {
                        tasksInterrupted.countDown();
                        Thread.currentThread().interrupt();
                    }
                    return "ShouldNotComplete";
                });
            }

            assertTrue("Tasks should have started", tasksStarted.await(2, TimeUnit.SECONDS));
            assertTrue("Tasks should be interrupted by timeout",
                    tasksInterrupted.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testForkThrowsAfterTimeout() throws Exception {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(50))
                .build()) {

            // Wait for timeout to expire
            awaitTimedOut(scope, 2, TimeUnit.SECONDS);

            try {
                scope.fork(() -> "ShouldNotRun");
                fail("Should have thrown CompletionException");
            } catch (CompletionException e) {
                assertTrue("Cause should be TimeoutException",
                        e.getCause() instanceof TimeoutException);
            }
        }
    }

    @Test
    public void testForkRunnableThrowsAfterTimeout() throws Exception {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(50))
                .build()) {

            awaitTimedOut(scope, 2, TimeUnit.SECONDS);

            try {
                scope.fork(() -> System.out.println("ShouldNotRun"));
                fail("Should have thrown CompletionException");
            } catch (CompletionException e) {
                assertTrue("Cause should be TimeoutException",
                        e.getCause() instanceof TimeoutException);
            }
        }
    }

    @Test
    public void testCompletionsStillUsableAfterTimeout() throws Exception {
        CountDownLatch taskInterrupted = new CountDownLatch(1);

        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(100))
                .build()) {

            scope.fork(() -> {
                try {
                    Thread.sleep(Duration.ofMinutes(1));
                } catch (InterruptedException e) {
                    taskInterrupted.countDown();
                    Thread.currentThread().interrupt();
                }
                return "Slow";
            });

            // Wait for the task to actually be interrupted
            assertTrue("Task should be interrupted",
                    taskInterrupted.await(2, TimeUnit.SECONDS));

            // completions() should still work and return the cancelled task
            List<Promise<Object>> results = scope.completions()
                    .collect(Collectors.toList());

            assertEquals(1, results.size());
            assertTrue("Task should be completed exceptionally (cancelled)",
                    results.get(0).isCompletedExceptionally());
        }
    }

    @Test
    public void testSuccessfulTasksCollectedBeforeTimeout() throws Exception {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofSeconds(5))
                .build()) {

            scope.fork(() -> "Fast1");
            scope.fork(() -> "Fast2");

            List<Object> results = scope.completions()
                    .filter(Promise.State.SUCCESS)
                    .map(Promise::join)
                    .collect(Collectors.toList());

            assertEquals(2, results.size());
            assertTrue(results.contains("Fast1"));
            assertTrue(results.contains("Fast2"));
        }
    }

    @Test
    public void testMixedFastAndSlowTasksWithTimeout() throws Exception {
        CountDownLatch slowTaskInterrupted = new CountDownLatch(1);

        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(200))
                .build()) {

            // Fast task completes before timeout
            scope.fork(() -> "Fast");

            // Slow task will be cancelled by timeout
            scope.fork(() -> {
                try {
                    Thread.sleep(Duration.ofMinutes(1));
                } catch (InterruptedException e) {
                    slowTaskInterrupted.countDown();
                    Thread.currentThread().interrupt();
                }
                return "Slow";
            });

            // Collect only successful results
            List<Object> results = scope.completions()
                    .filter(Promise.State.SUCCESS)
                    .map(Promise::join)
                    .collect(Collectors.toList());

            assertEquals("Only the fast task should succeed", 1, results.size());
            assertEquals("Fast", results.get(0));

            assertTrue("Slow task should be interrupted",
                    slowTaskInterrupted.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testTimeoutDoesNotRestartOnReset() throws Exception {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(50))
                .build()) {

            scope.fork(() -> "Fast");
            scope.completions().map(Promise::join).collect(Collectors.toList());

            // Wait for timeout to expire
            awaitTimedOut(scope, 2, TimeUnit.SECONDS);

            // Reset should work without error
            scope.reset();

            // But fork should still throw because timeout already fired
            try {
                scope.fork(() -> "ShouldNotRun");
                fail("Should have thrown CompletionException after reset");
            } catch (CompletionException e) {
                assertTrue("Cause should be TimeoutException",
                        e.getCause() instanceof TimeoutException);
            }
        }
    }

    @Test
    public void testResetBeforeTimeoutAllowsContinuedUse() throws Exception {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofSeconds(5))
                .build()) {

            // Batch 1
            scope.fork(() -> "Batch1");
            List<Object> batch1 = scope.completions()
                    .map(Promise::join)
                    .collect(Collectors.toList());
            assertEquals(1, batch1.size());

            // Reset before timeout expires
            scope.reset();

            // Batch 2 should still work because timeout hasn't fired
            scope.fork(() -> "Batch2");
            List<Object> batch2 = scope.completions()
                    .map(Promise::join)
                    .collect(Collectors.toList());
            assertEquals(1, batch2.size());
            assertEquals("Batch2", batch2.get(0));
        }
    }

    @Test
    public void testCloseAfterTimeout() throws Exception {
        ExecutorService ownedExecutor = Executors.newSingleThreadExecutor();

        TaskScope scope = TaskScope.builder()
                .executor(ownedExecutor, true)
                .timeout(Duration.ofMillis(50))
                .build();

        scope.fork(() -> {
            try {
                Thread.sleep(Duration.ofMinutes(1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Slow";
        });

        // Wait for timeout
        awaitTimedOut(scope, 2, TimeUnit.SECONDS);

        // Close should work properly even after timeout
        scope.close();

        assertTrue("Owned executor should be shut down after close",
                ownedExecutor.isShutdown());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsNegativeTimeout() {
        TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(-100))
                .build();
    }

    @Test
    public void testBuilderAllowsNullTimeout() {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(null)
                .build()) {

            scope.fork(() -> "Works");

            List<Object> results = scope.completions()
                    .map(Promise::join)
                    .collect(Collectors.toList());

            assertEquals(1, results.size());
            assertEquals("Works", results.get(0));
        }
    }

    @Test
    public void testIsTimedOutReturnsFalseBeforeTimeout() throws Exception {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofSeconds(10))
                .build()) {

            assertFalse("Should not be timed out yet", scope.isTimedOut());

            scope.fork(() -> "Fast");
            scope.completions().map(Promise::join).collect(Collectors.toList());

            assertFalse("Should still not be timed out", scope.isTimedOut());
        }
    }

    @Test
    public void testIsTimedOutReturnsTrueAfterTimeout() throws Exception {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(50))
                .build()) {

            assertFalse("Should not be timed out initially", scope.isTimedOut());

            awaitTimedOut(scope, 2, TimeUnit.SECONDS);

            assertTrue("Should be timed out after duration expires", scope.isTimedOut());
        }
    }

    @Test
    public void testTimeoutGetterReturnsConfiguredValue() {
        Duration configured = Duration.ofSeconds(5);
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(configured)
                .build()) {

            assertEquals(configured, scope.timeout());
        }
    }

    @Test
    public void testTimeoutGetterReturnsNullWhenNotConfigured() {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .build()) {

            assertEquals(null, scope.timeout());
        }
    }

    @Test
    public void testTakeReturnsCancelledTaskAfterTimeout() throws Exception {
        CountDownLatch taskInterrupted = new CountDownLatch(1);

        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(100))
                .build()) {

            scope.fork(() -> {
                try {
                    Thread.sleep(Duration.ofMinutes(1));
                } catch (InterruptedException e) {
                    taskInterrupted.countDown();
                    Thread.currentThread().interrupt();
                }
                return "Slow";
            });

            assertTrue("Task should be interrupted",
                    taskInterrupted.await(2, TimeUnit.SECONDS));

            // take() should return the cancelled promise
            Promise<Object> p = scope.take();
            assertNotNull("take() should return the cancelled promise", p);
            assertTrue("Promise should be completed exceptionally",
                    p.isCompletedExceptionally());
        }
    }

    @Test
    public void testTakeReturnsCancelledTaskAndExhaustsQueue() throws Exception {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(50))
                .build()) {

            scope.fork(() -> {
                try {
                    Thread.sleep(Duration.ofMinutes(1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Slow";
            });

            awaitTimedOut(scope, 2, TimeUnit.SECONDS);

            // 1. Consume the cancelled task using take() instead of takeValue()
            Promise<Object> p = scope.take();
            assertNotNull("take() should return the cancelled promise", p);
            assertTrue("Promise should be cancelled", p.isCancelled());

            // 2. Queue is now exhausted, both take() and takeValue() should return null
            assertEquals("take() should return null when exhausted", null, scope.take());
            assertEquals("takeValue() should return null when exhausted", null, scope.takeValue());
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testTakeValueThrowsOnCancelledTask() throws Exception {
        try (TaskScope scope = TaskScope.builder()
                .executor(sharedExecutor)
                .timeout(Duration.ofMillis(50))
                .build()) {

            scope.fork(() -> {
                try {
                    Thread.sleep(Duration.ofMinutes(1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Slow";
            });

            awaitTimedOut(scope, 2, TimeUnit.SECONDS);

            // takeValue() internally calls resultNow(), which throws IllegalStateException
            // for cancelled or exceptionally completed tasks.
            scope.takeValue();
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Polls {@code scope.isTimedOut()} until it returns true or the deadline expires.
     *
     * @param scope   the scope to check
     * @param timeout maximum time to wait
     * @param unit    time unit
     * @throws InterruptedException if interrupted while waiting
     */
    private void awaitTimedOut(TaskScope scope, long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (!scope.isTimedOut()) {
            if (System.nanoTime() > deadlineNanos) {
                fail("Timed out waiting for scope to report isTimedOut() == true");
            }
            Thread.sleep(10);
        }
    }
}