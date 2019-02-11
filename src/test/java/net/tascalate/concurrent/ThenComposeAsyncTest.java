/**
 * Copyright 2015-2019 Valery Silaev (http://vsilaev.com) - original author Adam Jurčík
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

import static org.junit.Assert.fail;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author vsilaev
 * @author Adam Jurčík
 */
public class ThenComposeAsyncTest {
    
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
    public void testThenComposeAsyncRace() {
        AtomicBoolean ran = new AtomicBoolean(false);
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        
        Promise<?> p = CompletableTask.complete(10, executor)
                .thenComposeAsync(n -> executor.submit(() -> {
                    started.set(true);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ex) {
                        // cancelled
                        cancelled.set(true);
                    }
                    return 10 * n;
                 }));
        
        @SuppressWarnings("unused")
        Promise<?> pThen = p.thenRunAsync(() -> {
                    ran.set(true);
                });
        
        // Wait some time in order to hit the DELAY in after fn.apply(r)
        trySleep(50);
        p.cancel(true);
        trySleep(500);
        
        // Ok, since p is completed only once in onError(...) through cancel(...)
        Assert.assertFalse("Expected ran false, but is " + ran.get(), ran.get());
        // Ok, since task is created quickly in thenComposeAsync(...)
        Assert.assertTrue("Expected started true, but is " + started.get(), started.get());
        // Fails, since there is a race between completion stage creation
        // and setting it as a cancellableOrigin
        Assert.assertTrue("Expected cancelled true, but is " + cancelled.get(), cancelled.get());
    }
    
    
    @Test
    public void testThenComposeException() {
        Promise<Void> p = CompletableTask.runAsync(() -> trySleep(10), executor)
                .thenCompose(it -> {
                    throw new IllegalStateException("oh no!");
                });
        try {
            p.get(1L, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            fail("thenCompose*  hanged");
        } catch (InterruptedException | ExecutionException ex) {
            // System.out.println(ex);
            // expected
            return;
        }
        fail("Exception must be thrown");
    }
    
    
    private void trySleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
        }
    }
    
}
