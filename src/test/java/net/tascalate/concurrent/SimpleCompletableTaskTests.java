/**
 * Copyright 2015-2019 Valery Silaev (http://vsilaev.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
