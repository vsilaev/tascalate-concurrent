/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author vsilaev
 */
public class StopExecutorTests {
    
    private TaskExecutorService executor;
    
    @Before
    public void setUp() {
        executor = TaskExecutors.newFixedThreadPool(4);
    }
    
    @After
    public void tearDown() {
        executor.shutdown();
    }

    @Test
    public void test_shutting_down_executor_will_not_hang_promise_due_to_pending_callback() throws ExecutionException, InterruptedException {
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean flag = new AtomicBoolean();
        Promise<Void> p = CompletableTask.asyncOn(executor)
                .thenApplyAsync(v -> {
                    try {
                        latch.await();
                    } catch (InterruptedException ex) {
                    }
                    return v;
                }, singleThreadExecutor)
                .handleAsync((v, t) -> {
                    flag.set(true);
                    return null;
                });

        singleThreadExecutor.shutdownNow();
        latch.countDown();
        //singleThreadExecutor.awaitTermination(10, TimeUnit.SECONDS);
        try {
            p.get();
            assertFalse("Promise should not be resolved susscessfully", true);
        } catch (Exception ex) {
            assertTrue("Exception is thrown: " + ex.getMessage(), true);
        }
        assertFalse(flag.get());
    }
}
