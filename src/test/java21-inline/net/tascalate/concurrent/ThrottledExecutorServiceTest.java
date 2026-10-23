package net.tascalate.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

/**
 * Comprehensive JUnit 4 tests for {@link ThrottledExecutorService}.
 */
public class ThrottledExecutorServiceTest {

    private ThrottledExecutorService executor;

    @After
    public void tearDown() {
        if (executor != null && !executor.isTerminated()) {
            executor.shutdownNow();
        }
    }

    // =========================================================================
    // Basic Execution & Concurrency Limits
    // =========================================================================

    @Test
    public void testBasicExecutionAndConcurrencyLimit() throws Exception {
        executor = new ThrottledExecutorService("test-", 2, 10);
        
        CountDownLatch startedLatch = new CountDownLatch(2);
        CountDownLatch waitLatch = new CountDownLatch(1);
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        Runnable task = () -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(m -> Math.max(m, current));
            startedLatch.countDown();
            try {
                waitLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrentCount.decrementAndGet();
            }
        };

        // Submit 3 tasks, but max concurrency is 2
        executor.execute(task);
        executor.execute(task);
        executor.execute(task);

        // Wait for 2 tasks to start
        assertTrue("Two tasks should start", startedLatch.await(2, TimeUnit.SECONDS));
        assertEquals(2, maxConcurrent.get());
        
        // Release the wait latch so tasks can finish and the 3rd can start
        waitLatch.countDown();
        
        executor.shutdown();
        assertTrue("Executor should terminate", executor.awaitTermination(2, TimeUnit.SECONDS));
        
        // The 3rd task should have run and max concurrent should still be 2
        assertEquals(2, maxConcurrent.get());
    }

    @Test
    public void testThreadMonitoring() throws Exception {
        executor = new ThrottledExecutorService("test-", 2, 10);
        
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch blockLatch = new CountDownLatch(1);
        
        Runnable task = () -> {
            started.countDown();
            try { blockLatch.await(); } catch (InterruptedException e) {}
        };
        
        executor.execute(task);
        executor.execute(task);
        
        started.await();
        
        // Verify active thread count
        assertEquals(2, executor.threadCount());
        
        blockLatch.countDown();
        
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
        
        // Verify threads are cleaned up
        assertEquals(0, executor.threadCount());
    }

    // =========================================================================
    // Queueing & Rejection Policies
    // =========================================================================

    @Test
    public void testSynchronousQueueRejection() throws Exception {
        // capacity 0 means SynchronousQueue (no buffering)
        executor = new ThrottledExecutorService("test-", 1, 0);
        
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blockLatch = new CountDownLatch(1);
        
        executor.execute(() -> {
            started.countDown();
            try { blockLatch.await(); } catch (InterruptedException e) {}
        });
        
        started.await();
        
        try {
            // Second task should be rejected immediately because queue capacity is 0
            executor.execute(() -> {});
            fail("Should have thrown RejectedExecutionException");
        } catch (RejectedExecutionException e) {
            // expected
        } finally {
            blockLatch.countDown();
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testBoundedQueueRejection() throws Exception {
        // max 1 thread, queue capacity 1
        executor = new ThrottledExecutorService("test-", 1, 1);
        
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blockLatch = new CountDownLatch(1);
        
        // Task 1: running
        executor.execute(() -> {
            started.countDown();
            try { blockLatch.await(); } catch (InterruptedException e) {}
        });
        
        started.await();
        
        // Task 2: queued
        executor.execute(() -> {});
        
        // Task 3: should be rejected
        try {
            executor.execute(() -> {});
            fail("Should have thrown RejectedExecutionException");
        } catch (RejectedExecutionException e) {
            // expected
        } finally {
            blockLatch.countDown();
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testUnboundedQueue() throws Exception {
        // capacity -1 means unbounded LinkedBlockingQueue
        executor = new ThrottledExecutorService("test-", 1, -1);
        
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blockLatch = new CountDownLatch(1);
        AtomicInteger executedCount = new AtomicInteger(0);
        
        // Task 1: running and blocking
        executor.execute(() -> {
            started.countDown();
            try { blockLatch.await(); } catch (InterruptedException e) {}
        });
        
        started.await();
        
        // Submit 100 more tasks, all should be accepted
        for (int i = 0; i < 100; i++) {
            executor.execute(executedCount::incrementAndGet);
        }
        
        assertEquals(100, executor.getQueue().size());
        
        blockLatch.countDown(); // let them run
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(100, executedCount.get());
    }

    @Test
    public void testCustomRejectionHandler() throws Exception {
        AtomicInteger rejectedCount = new AtomicInteger(0);
        RejectedExecutionHandler<ThrottledExecutorService> handler = (r, e) -> rejectedCount.incrementAndGet();
        
        executor = new ThrottledExecutorService("test-", 1, 0, handler);
        
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blockLatch = new CountDownLatch(1);
        
        executor.execute(() -> {
            started.countDown();
            try { blockLatch.await(); } catch (InterruptedException e) {}
        });
        
        started.await();
        
        // Should trigger custom handler instead of throwing
        executor.execute(() -> {});
        
        assertEquals(1, rejectedCount.get());
        
        blockLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Lifecycle Management (Shutdown / Await / Close)
    // =========================================================================

    @Test
    public void testShutdown() throws Exception {
        executor = new ThrottledExecutorService("test-", 2, 10);
        
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch taskFinish = new CountDownLatch(1);
        
        executor.execute(() -> {
            taskStarted.countDown();
            try { taskFinish.await(); } catch (InterruptedException e) {}
        });
        
        taskStarted.await();
        
        executor.shutdown();
        assertTrue(executor.isShutdown());
        
        try {
            executor.execute(() -> {});
            fail("Should reject after shutdown");
        } catch (RejectedExecutionException e) {
            // expected
        }
        
        assertFalse(executor.isTerminated());
        
        taskFinish.countDown(); // let task finish
        
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertTrue(executor.isTerminated());
    }

    @Test
    public void testShutdownNow() throws Exception {
        executor = new ThrottledExecutorService("test-", 1, 10);
        
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch taskInterrupted = new CountDownLatch(1);
        
        // Running task
        executor.execute(() -> {
            taskStarted.countDown();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                taskInterrupted.countDown();
            }
        });
        
        // Queued tasks
        executor.execute(() -> {});
        executor.execute(() -> {});
        
        taskStarted.await();
        
        List<Runnable> pending = executor.shutdownNow();
        
        // The 2 queued tasks should be returned
        assertEquals(2, pending.size());
        
        // The running task should be interrupted
        assertTrue("Running task should be interrupted", taskInterrupted.await(2, TimeUnit.SECONDS));
        
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    public void testAwaitTerminationTimeout() throws Exception {
        executor = new ThrottledExecutorService("test-", 1, 0);
        
        CountDownLatch blockLatch = new CountDownLatch(1);
        executor.execute(() -> {
            try { blockLatch.await(); } catch (InterruptedException e) {}
        });
        
        executor.shutdown();
        
        // Should return false because task is still blocked
        assertFalse(executor.awaitTermination(100, TimeUnit.MILLISECONDS));
        
        blockLatch.countDown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }
    
    @Test
    public void testClose() throws Exception {
        executor = new ThrottledExecutorService("test-", 1, 0);
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            latch.countDown();
        });
        
        executor.close(); // should block until task finishes
        
        assertTrue(executor.isTerminated());
        assertEquals(0, latch.getCount());
    }

    // =========================================================================
    // Thread Factory Configuration
    // =========================================================================

    @Test
    public void testCustomThreadFactory() throws Exception {
        ThreadFactory customFactory = r -> {
            Thread t = new Thread(r);
            t.setName("custom-thread");
            return t;
        };
        
        executor = new ThrottledExecutorService(customFactory, 1, 0);
        
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        executor.execute(() -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        });
        
        latch.await();
        assertEquals("custom-thread", threadName.get());
        
        executor.shutdown();
    }
}