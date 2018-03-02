/*
 * Copyright 2017 Adam Jurčík
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tascalate.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Adam Jurčík
 */
public class DependentPromiseTests {
    
    private static final int UNIT = 100; // ms
    
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
    public void testWhenComplete() {
        State s1 = new State();
        State s2 = new State();
        
        Promise<?> p = executor.submit(() -> longTask(5, s1));
        p.whenComplete((r, e) -> {
            if (e != null) {
                s2.cancel();
            } else {
                s2.finish();
            }
        });
        
        trySleep(2);
        p.cancel(true);
        trySleep(1);
        
        assertCancelled("s1", s1);
        assertCancelled("s2", s2);
    }
    
    @Test
    public void testForwardCancellation() {
        State s1 = new State();
        State s2 = new State();
        State s3 = new State();
        
        Promise<?> p1 = CompletableTask.asyncOn(executor)
                .thenRunAsync(() -> longTask(5, s1));
        
        Promise<?> p2 = p1.thenRunAsync(() -> longTask(5, s2))
                .whenComplete((r, e) -> { if (e != null) s3.cancel(); });
        
        CompletableFuture<?> cf = p2.toCompletableFuture();
        
        trySleep(2);
        p1.cancel(true);
        trySleep(1);
        
        assertCancelled("s1", s1);
        assertNotStarted("s2", s2);
        assertCancelled("s3", s3);
        assertTrue("Expected cf completed exceptionally, but is " + cf,
                cf.isCompletedExceptionally());
    }
    
    @Test
    public void testRecursiveCancellation() {
        State s = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s))
                .thenRun(this::dummyTask, true);   
        
        trySleep(2);
        p.cancel(true);
        trySleep(5);
        
        assertCancelled("s", s);
    }
    
    @Test
    public void testComposeRecursiveCancellation1() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .thenComposeAsync(r -> otherLongTask(5, s2), true)
                .thenRun(this::dummyTask, true);
        
        trySleep(2);
        p.cancel(true);
        trySleep(1);
        
        assertCancelled("s1", s1);
        assertNotStarted("s2", s2);
    }
    
    @Test
    public void testComposeRecursiveCancellation2() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .thenComposeAsync(r -> otherLongTask(5, s2), true)
                .thenRun(this::dummyTask, true);
        
        trySleep(8);
        p.cancel(true);
        trySleep(1);
        
        assertDone("s1", s1);
        assertCancelled("s2", s2);
    }
    
    @Test
    public void testThenApplyRecursiveCancellation1() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .thenApplyAsync(r -> {
                    longTask(5, s2);
                    return null;
                }, true)
                .thenRun(this::dummyTask, true);
        
        trySleep(2);
        p.cancel(true);
        trySleep(1);
        
        assertCancelled("s1", s1);
        assertNotStarted("s2", s2);
    }
    
    @Test
    public void testThenApplyRecursiveCancellation2() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .thenApplyAsync(r -> {
                    longTask(5, s2);
                    return null;
                }, true)
                .thenRun(this::dummyTask, true);
        
        trySleep(8);
        p.cancel(true);
        trySleep(1);
        
        assertDone("s1", s1);
        assertCancelled("s2", s2);
    }
    
    @Test
    public void testCombineRecursiveCancellation1() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .thenCombineAsync(otherLongTask(5, s2), (r1, r2) -> null, PromiseOrigin.THIS_ONLY) 
                .thenRun(this::dummyTask, true);
        
        trySleep(2);
        p.cancel(true);
        trySleep(5);
        
        assertCancelled("s1", s1);
        assertDone("s2", s2);
    }
    
    @Test
    public void testCombineRecursiveCancellation2() {
        State s1 = new State();
        State s2 = new State();
        State s3 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .thenCombineAsync(otherLongTask(5, s2), (r1, r2) -> { longTask(5, s3); return null; }, PromiseOrigin.THIS_ONLY)
                .thenRun(this::dummyTask, true);
        
        trySleep(8);
        p.cancel(true);
        trySleep(1);
        
        assertDone("s1", s1);
        assertDone("s2", s2);
        assertCancelled("s3", s3);
    }
    
    @Test
    public void testCombineAllRecursiveCancellation() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .thenCombineAsync(otherLongTask(5, s2), (r1, r2) -> null, PromiseOrigin.ALL)
                .thenRun(this::dummyTask, true);
        
        trySleep(2);
        p.cancel(true);
        trySleep(1);
        
        assertCancelled("s1", s1);
        assertCancelled("s2", s2);
    }
    
    @Test
    public void testRunAfterEitherRecursiveCancellation() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .runAfterEitherAsync(otherLongTask(5, s2), this::dummyTask, PromiseOrigin.THIS_ONLY) 
                .thenRun(this::dummyTask, true);
        
        trySleep(2);
        p.cancel(true);
        trySleep(5);
        
        assertCancelled("s1", s1);
        assertDone("s2", s2);
    }
    
    @Test
    public void testRunAfterEitherAllRecursiveCancellation() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .runAfterEitherAsync(otherLongTask(5, s2), this::dummyTask, PromiseOrigin.ALL)
                .thenRun(this::dummyTask, true);
        
        trySleep(2);
        p.cancel(true);
        trySleep(1);
        
        assertCancelled("s1", s1);
        assertCancelled("s2", s2);
    }
    
    @Test
    public void testRunAfterBothRecursiveCancellation() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .runAfterBothAsync(otherLongTask(5, s2), this::dummyTask, PromiseOrigin.THIS_ONLY) 
                .thenRun(this::dummyTask, true);
        
        trySleep(2);
        p.cancel(true);
        trySleep(5);
        
        assertCancelled("s1", s1);
        assertDone("s2", s2);
    }
    
    @Test
    public void testRunAfterBothAllRecursiveCancellation() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .runAfterBothAsync(otherLongTask(5, s2), this::dummyTask, PromiseOrigin.ALL)
                .thenRun(this::dummyTask, true);
        
        trySleep(2);
        p.cancel(true);
        trySleep(1);
        
        assertCancelled("s1", s1);
        assertCancelled("s2", s2);
    }
    
    @Test
    public void testHandleAsyncRecursiveCancellation1() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .handleAsync((r, e) -> { longTask(5, s2); return null; }, true)
                .thenRun(this::dummyTask, true);
        
        trySleep(2);
        p.cancel(true);
        trySleep(1);
        
        assertCancelled("s1", s1);
        assertNotStarted("s2", s2);
    }
    
    @Test
    public void testHandleAsyncRecursiveCancellation2() {
        State s1 = new State();
        State s2 = new State();
        
        DependentPromise<Void> p = runDepedentAsync(() -> longTask(5, s1))
                .handleAsync((r, e) -> { longTask(5, s2); return null; }, true)
                .thenRun(this::dummyTask, true);
        
        trySleep(8);
        p.cancel(true);
        trySleep(1);
        
        assertDone("s1", s1);
        assertCancelled("s2", s2);
    }
    
    private DependentPromise<Void> runDepedentAsync(Runnable r) {
        return CompletableTask.runAsync(r, executor).dependent();
    }
    
    private void longTask(int units, State state) {
        state.start();
        for (int i = 0; i < units; i++) {
            if (Thread.interrupted()) {
                state.cancel();
                return;
            }
            try {
                Thread.sleep(UNIT);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        state.finish();
    }
    
    private void dummyTask() {
        // nothing
    }
    
    private Promise<Void> otherLongTask(int units, State state) {
        return executor.submit(() -> { 
            longTask(units, state);
            return null; 
        });
    }
    
    private void trySleep(int units) {
        try {
            Thread.sleep(units * UNIT);
        } catch (InterruptedException ex) {
        }
    }
    
    private void assertNotStarted(String name, State s) {
        assertFalse("Expected " + name + " not started, but is " + s, s.wasStarted());
    }
    
    private void assertDone(String name, State s) {
        assertTrue("Expected " + name + " done, but is " + s, s.isDone());
    }
    
    private void assertCancelled(String name, State s) {
        assertTrue("Expected " + name + " cancelled, but is " + s, s.isCancelled());
    }
    
    static class State {
        
        static final int NEW = 0;
        static final int STARTED = 1;
        static final int DONE = 2;
        static final int CANCELLED = 3;
        
        AtomicInteger s = new AtomicInteger(NEW);
        
        void start() {
            s.set(STARTED);
        }
        
        void finish() {
            s.set(DONE);
        }
        
        void cancel() {
            s.set(CANCELLED);
        }
        
        boolean wasStarted() {
            return s.get() >= STARTED;
        }
        
        boolean isDone() {
            return s.get() == DONE;
        }
        
        boolean isCancelled() {
            return s.get() == CANCELLED;
        }
        
        @Override
        public String toString() {
            switch (s.get()) {
                case NEW:
                    return "new";
                case STARTED:
                    return "started";
                case DONE:
                    return "done";
                case CANCELLED:
                    return "cancelled";
                default:
                    return String.format("unknown (%d)", s.get());
            }
        }
        
    }
    
}
