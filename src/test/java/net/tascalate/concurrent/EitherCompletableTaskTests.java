/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com) - original author Adam Jurčík
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
/**
 * @author vsilaev
 * @author Adam Jurčík
 */
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
