package net.tascalate.concurrent;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SimpleCompletableTaskTests {
    
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
    public void testTaskIsExecuted1() throws Exception {
        Promise<Integer> promise = executor.submit(() -> 50);
        int i = promise.get();
        Assert.assertEquals(i, 50);
    }
    
    @Test
    public void testTaskIsExecuted2() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Throwable[] errors = {null};
        String[] results = {null};
        executor.submit(() -> "ABC").whenComplete((r, e) -> {
            results[0] = r;
            errors[0]  = e;
            ready.countDown();
        });
        ready.await();
        Assert.assertNull(errors[0]);
        Assert.assertEquals(results[0], "ABC");
    }
    
    @Test
    public void testTaskIsFailed() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Throwable[] errors = {null};
        Integer[] results = {null};
        executor.submit(this::failedResult).whenComplete((r, e) -> {
            results[0] = r;
            errors[0]  = e;
            ready.countDown();
        });
        ready.await();
        Assert.assertNull(results[0]);
        Assert.assertTrue("ArithmeticException was not raised", errors[0] instanceof ArithmeticException);
    }
    
    @Test
    public void testNestedTaskIsExecuted() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Throwable[] errors = {null};
        Integer[] results = {null};
        executor.submit(() -> 50).thenApply(i -> i * 10).whenComplete((r, e) -> {
            results[0] = r;
            errors[0]  = e;
            ready.countDown();
        });
        ready.await();
        Assert.assertNull(errors[0]);
        Assert.assertEquals(results[0], Integer.valueOf(500));
    }
    
    @Test
    public void testNestedTaskIsFailed() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Throwable[] errors = {null};
        Integer[] results = {null};
        executor.submit(this::failedResult).thenApply(i -> i * 10).whenComplete((r, e) -> {
            results[0] = r;
            errors[0]  = e;
            ready.countDown();
        });
        ready.await();
        Assert.assertNull(results[0]);
        Assert.assertTrue("CompletionException was not raised", errors[0] instanceof CompletionException);
        Assert.assertTrue("CompletionException is not caused by ArithmeticException", errors[0].getCause() instanceof ArithmeticException);        
    }

    
    int failedResult() {
        throw new ArithmeticException();
    }
}
