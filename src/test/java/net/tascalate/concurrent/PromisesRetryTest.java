package net.tascalate.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class PromisesRetryTest {

    // Using Runnable::run as a synchronous executor for deterministic testing
    private static final Executor SYNC_EXECUTOR = Runnable::run;

    @Test
    public void testRetrySuccessAfterFailures() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        Promise<String> p = Promises.retry(() -> {
            if (count.incrementAndGet() < 3) {
                throw new RuntimeException("Fail");
            }
            return "Success";
        }, SYNC_EXECUTOR, new RetryPolicy<String>().retryOn(RuntimeException.class).withMaxRetries(5));
        
        assertEquals("Success", p.get());
        assertEquals(3, count.get());
    }

    @Test
    public void testRetryExhausted() throws Exception {
        Promise<String> p = Promises.retry(() -> {
            throw new RuntimeException("Always fail");
        }, SYNC_EXECUTOR, new RetryPolicy<String>().retryOn(RuntimeException.class).withMaxRetries(2));
        
        try {
            p.get();
            fail("Should throw ExecutionException");
        } catch (ExecutionException e) {
            assertTrue("Expected RetryException", e.getCause() instanceof RetryException);
            RetryException re = (RetryException) e.getCause();
            assertEquals(3, re.getRetryCount()); // 2 retries = 3 total attempts
        }
    }

    @Test
    public void testRetryOptionalPolling() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        Promise<String> p = Promises.retryOptional(() -> {
            if (count.incrementAndGet() < 2) {
                return Optional.empty(); // Simulate "not ready yet"
            }
            return Optional.of("Found");
        }, SYNC_EXECUTOR, new RetryPolicy<String>().rejectNullResult().withMaxRetries(5));
        
        assertEquals("Found", p.get());
    }

    @Test
    public void testRetryFutureAsync() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        Promise<String> p = Promises.retryFuture(() -> {
            if (count.incrementAndGet() < 2) {
                return Promises.failure(new RuntimeException("Async fail"));
            }
            return Promises.success("Async Success");
        }, new RetryPolicy<String>().retryOn(RuntimeException.class).withMaxRetries(5));
        
        assertEquals("Async Success", p.get());
    }
}