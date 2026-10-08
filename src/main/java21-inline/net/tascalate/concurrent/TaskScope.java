/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TaskScope implements AutoCloseable {
    private static final AtomicLong SCOPE_ID = new AtomicLong(0);
    
    private final String name;
    private final ExecutorService executorService;
    private final boolean ownExecutor;
    private final TaskCompletionService<Object> completionService;
    
    private final Set<Promise<?>> allFutures = new HashSet<>();
    private final Thread owner = Thread.currentThread();
    
    public TaskScope() {
        this(nextGeneratedName());
    }
    
    public TaskScope(String name) {
        this(nextGeneratedName(), Executors.newVirtualThreadPerTaskExecutor(), true);
    }
    
    public TaskScope(ThreadFactory threadFactory) {
        this(nextGeneratedName(), threadFactory);
    }
    
    public TaskScope(String name, ThreadFactory threadFactory) {
        this(name, Executors.newThreadPerTaskExecutor(threadFactory), true);
    }
    
    public TaskScope(ExecutorService executorService) {
        this(executorService, false);
    }
    
    public TaskScope(ExecutorService executorService, boolean ownExecutor) {
        this(nextGeneratedName(), executorService, ownExecutor);
    }
    
    public TaskScope(String name, ExecutorService executorService) {
        this(name, executorService, false);
    }
    
    public TaskScope(String name, ExecutorService executorService, boolean ownExecutor) {
        Objects.requireNonNull(executorService, "executorService may not be null");
        this.name = name;
        this.executorService = executorService;
        this.ownExecutor = ownExecutor;
        this.completionService = new TaskExecutorCompletionService<>(executorService);
    }
    
    @SuppressWarnings("unchecked")
    public Promise<Void> fork(Runnable code) {
        checkOwner();
        Promise<?> result = completionService.submit(code, null);
        allFutures.add(result);
        return (Promise<Void>)result;
    }
    
    @SuppressWarnings("unchecked")
    public <T> Promise<T> fork(Callable<T> code) {
        checkOwner();
        Promise<?> result = completionService.submit((Callable<Object>)(Object)code);
        allFutures.add(result);
        return (Promise<T>)result;
    }
    
    public Stream<Promise<Object>> completions() {
        Iterator<Promise<Object>> iterator = new Iterator<Promise<Object>>() {
            
            @Override
            public Promise<Object> next() {
                try {
                    checkOwner();
                    if (allFutures.isEmpty()) {
                        throw new ConcurrentModificationException();
                    }
                    Promise<Object> result = completionService.take();
                    allFutures.remove(result);
                    return (Promise<Object>)result;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(ex);
                }
            }
            
            @Override
            public boolean hasNext() {
                // Don't check owner here - non destructive operation
                return !allFutures.isEmpty();
            }
        };
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iterator, Spliterator.DISTINCT & Spliterator.NONNULL), 
            false
        );
    }
    
    // Cancel & clear everything and 
    // reset scope to the initial state
    // this provides support for several
    // blocks within a code where you are
    // forking set of threads and consumes results
    public void reset() {
        checkOwner();
        
        // Cancel all forks first
        allFutures.forEach(f -> f.cancel(true));
        // Remove enlisted futures
        allFutures.clear();
        // drain the completion queue
        while (completionService.poll() != null);
    }
    
    @Override
    public void close() {
        reset();
        if (ownExecutor) {
            executorService.shutdownNow();
        }
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    static String nextGeneratedName() {
        return "scope_" + SCOPE_ID.getAndIncrement();        
    }
    
    private void checkOwner() {
        if (owner != Thread.currentThread()) {
            throw new IllegalStateException("Scope must be accessed from the single thread");
        }
    }

    // Might be added to the API as well
    public Promise<Object> take() throws InterruptedException {
        return take(ANY);
    }
    
    public Promise<Object> take(Predicate<Promise<?>> filter) throws InterruptedException {
        checkOwner();
        while (!allFutures.isEmpty()) {
            Promise<Object> result = (Promise<Object>)completionService.take();
            allFutures.remove(result);
            if (filter.test(result)) {
                return result;
            }
        }
        return null;
    }
    
    // Might be added to the API as well
    public Optional<Object> takeValue() throws InterruptedException {
        return takeValue(ANY);
    }
    
    public Optional<Object> takeValue(Predicate<Promise<?>> filter) throws InterruptedException {
        checkOwner();
        while (!allFutures.isEmpty()) {
            Promise<Object> result = (Promise<Object>)completionService.take();
            allFutures.remove(result);
            if (filter.test(result)) {
                return Optional.ofNullable(result.resultNow());
            }
        }
        return null;
    }
    
    private static Predicate<Promise<?>> ANY = p -> true;
}