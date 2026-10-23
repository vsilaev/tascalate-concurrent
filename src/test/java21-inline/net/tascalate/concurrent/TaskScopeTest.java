package net.tascalate.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
                .executor(ownedExecutor, true) // Explicitly owned
                .build()) {
            scope.fork(() -> {});
        }
        assertTrue("Owned executor should be shut down after close", ownedExecutor.isShutdown());
    }

    @Test
    public void testBuilderDoesNotShutdownUnownedExecutor() {
        ExecutorService externalExecutor = Executors.newSingleThreadExecutor();
        try (TaskScope scope = TaskScope.builder()
                .executor(externalExecutor) // Default: unowned
                .build()) {
            scope.fork(() -> {});
        }
        assertFalse("External executor should NOT be shut down", externalExecutor.isShutdown());
        externalExecutor.shutdownNow(); // Manual cleanup for the test
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
                    .filter(Promise.State.SUCCESS) // Use Promise.State as a Predicate
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
            // This should throw CompletionException wrapping IllegalStateException
            scope.completions().map(Promise::join).findFirst();
        }
    }

    @Test
    public void testStreamShortCircuitsAndCancelsRemaining() throws Exception {
        CountDownLatch slowTasksStarted = new CountDownLatch(3);
        CountDownLatch slowTasksInterrupted = new CountDownLatch(3);

        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            // 1. Fork 2 fast tasks that complete almost immediately
            scope.fork(() -> "Fast1");
            scope.fork(() -> "Fast2");

            // 2. Fork 3 slow tasks that block until interrupted
            for (int i = 0; i < 3; i++) {
                scope.fork(() -> {
                    slowTasksStarted.countDown();
                    try {
                        // Block for a long time; will be interrupted by scope.close()
                        Thread.sleep(Duration.ofMinutes(1));
                    } catch (InterruptedException e) {
                        slowTasksInterrupted.countDown();
                        Thread.currentThread().interrupt();
                    }
                    return "Slow";
                });
            }

            // 3. Consume exactly 2 results. The 2 fast tasks will satisfy the limit.
            List<Object> results = scope.completions()
                    .limit(2)
                    .map(Promise::join)
                    .collect(Collectors.toList());

            assertEquals("Should collect exactly 2 fast results", 2, results.size());
            
            // 4. Verify that the slow tasks actually started and are currently blocked.
            // Because the pool has 4 threads, the 2 fast tasks free up their threads 
            // almost instantly, allowing all 3 slow tasks to grab threads and start.
            assertTrue("Slow tasks should have started", slowTasksStarted.await(2, TimeUnit.SECONDS));
            assertEquals("No slow tasks should be interrupted yet", 3, slowTasksInterrupted.getCount());
            
        } // try-with-resources exits here, calling scope.close(), which cancels the remaining tasks.

        // 5. Verify that the 3 slow tasks were interrupted and cancelled by the scope
        assertTrue("Slow tasks should have been interrupted by scope.close()", 
                   slowTasksInterrupted.await(2, TimeUnit.SECONDS));
    }

    // =========================================================================
    // Reset and Reuse Tests
    // =========================================================================

    @Test
    public void testResetAllowsScopeReuse() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            // Batch 1
            scope.fork(() -> "Batch1-Result");
            List<Object> batch1 = scope.completions()
                    .map(Promise::join)
                    .collect(Collectors.toList());
            assertEquals(1, batch1.size());
            assertEquals("Batch1-Result", batch1.get(0));

            // Reset the scope
            scope.reset();

            // Batch 2
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

            // Filter to only accept successful promises
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
            
            // Consume the only task
            assertNotNull(scope.take());
            
            // Queue is now empty
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

            // Bind a value in the current thread, then fork a task.
            // The contextualizer captures the context eagerly at fork time.
            testVar.bind("PropagatedValue").run(() -> {
                scope.fork(() -> {
                    // This assertion runs on the worker thread.
                    // If propagation works, testVar.get() should return "PropagatedValue".
                    assertEquals("PropagatedValue", testVar.get());
                });
            });

            // Wait for the task to complete
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
                .executor(parentExecutor, true) // Parent owns the executor
                .build()) {

            try (TaskScope child = parent.toBuilder()
                    .name("ChildScope")
                    .build()) {

                assertEquals("ChildScope", child.toString());
                
                // Fork a task in the child. It should run on the parent's executor.
                child.fork(() -> {});
            }

            // Closing the child should NOT shut down the parent's executor
            assertFalse("Parent executor should remain active after child closes", parentExecutor.isShutdown());
        }

        // Closing the parent should shut down the executor
        assertTrue("Parent executor should be shut down after parent closes", parentExecutor.isShutdown());
    }

    // =========================================================================
    // Global Timeout Pattern Tests
    // =========================================================================

    @Test
    public void testGlobalTimeoutPatternWithTakeWhile() throws Exception {
        try (TaskScope scope = TaskScope.builder().executor(sharedExecutor).build()) {
            
            // 1. Fork a "timeout" task that completes quickly
            Promise<Object> timeoutPromise = scope.fork(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "TIMEOUT";
            });

            // 2. Fork slow tasks that would take longer than the timeout
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

            // 3. Consume completions until the timeout task appears
            List<Object> results = scope.completions()
                    .takeWhile(p -> p != timeoutPromise)
                    .filter(Promise.State.SUCCESS)
                    .map(Promise::join)
                    .collect(Collectors.toList());

            // Since the timeout task (50ms) finishes before the slow tasks (500ms),
            // the stream should stop immediately, yielding 0 results.
            assertEquals("Should collect 0 results before timeout", 0, results.size());
        }
    }
}