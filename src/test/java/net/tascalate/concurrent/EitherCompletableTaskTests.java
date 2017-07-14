package net.tascalate.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class EitherCompletableTaskTests {
    
    private TaskExecutorService executor;

    @Before
    public void setup() {
        executor = TaskExecutors.newFixedThreadPool(4);
    }
    
    @After
    public void tearDown() {
        executor.shutdown();
    }
    
    @Test
    public void testAnyTaskIsExecuted1() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Throwable[] errors = {null, null};
        String[] results = {null, null};
        AtomicInteger idx = new AtomicInteger(0);

        Promise<String> first = executor.submit(() -> resultWithDelay("ABC", 2000)); 
        Promise<String> second = executor.submit(() -> resultWithDelay("XYZ", 100)); // Second wins -- shorter delay

        first
        .applyToEitherAsync(second, String::toLowerCase)
        .whenComplete((r, e) -> {
            int i = idx.getAndIncrement();
            results[i] = r;
            errors[i]  = e;
            ready.countDown();
        });
        
        ready.await();
        Assert.assertNull(errors[0]);
        Assert.assertEquals(results[0], "xyz");
    }
    
    @Test
    public void testAnyTaskIsExecuted2() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Throwable[] errors = {null, null};
        String[] results = {null, null};
        AtomicInteger idx = new AtomicInteger(0);

        Promise<String> first = executor.submit(() -> resultWithDelay("ABC", 2000)); 
        Promise<String> second = executor.submit(() -> resultWithDelay("XYZ", 100)); // Second wins -- shorter delay

        second
        .applyToEitherAsync(first, String::toLowerCase)
        .whenComplete((r, e) -> {
            int i = idx.getAndIncrement();
            results[i] = r;
            errors[i]  = e;
            ready.countDown();
        });
        
        ready.await();
        Assert.assertNull(errors[0]);
        Assert.assertEquals(results[0], "xyz");
    }

    
    <T> T resultWithDelay(T value, long delay) throws InterruptedException {
        Thread.sleep(delay);
        return value;
    }
}
