/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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

import net.tascalate.concurrent.var.ContextTrampoline;

public class TaskScope implements AutoCloseable {
    private static final AtomicLong SCOPE_ID = new AtomicLong(0);
    
    private final String name;
    private final ExecutorService executorService;
    private final TaskCompletionService<Object> completionService;
    private final ContextTrampoline<Object> contextualizer;
    
    private final Set<Promise<?>> allFutures = new HashSet<>();
    private final Thread owner = Thread.currentThread();
    
    public TaskScope() {
        this((ContextTrampoline<Object>)null);
    }
    
    public TaskScope(ContextTrampoline<Object> contextualizer) {
        this(nextGeneratedName(), contextualizer);
    }
    
    public TaskScope(String name) {
        this(name, Executors.newVirtualThreadPerTaskExecutor(), true, null);
    }
    
    public TaskScope(String name, ContextTrampoline<Object> contextualizer) {
        this(name, Executors.newVirtualThreadPerTaskExecutor(), true, contextualizer);
    }
    
    public TaskScope(ThreadFactory threadFactory) {
        this(threadFactory, (ContextTrampoline<Object>)null);
    }
    
    public TaskScope(ThreadFactory threadFactory, ContextTrampoline<Object> contextualizer) {
        this(nextGeneratedName(), threadFactory, contextualizer);
    }
    
    public TaskScope(String name, ThreadFactory threadFactory) {
        this(name, threadFactory, (ContextTrampoline<Object>)null);
    }
    
    public TaskScope(String name, 
                     ThreadFactory threadFactory, 
                     ContextTrampoline<Object> contextualizer) {
        this(name, Executors.newThreadPerTaskExecutor(threadFactory), true, contextualizer);
    }
    
    public TaskScope(ExecutorService executorService) {
        this(executorService, (ContextTrampoline<Object>)null);
    }
    
    public TaskScope(ExecutorService executorService, 
                     ContextTrampoline<Object> contextualizer) {
        this(executorService, false, contextualizer);
    }
    
    
    public TaskScope(ExecutorService executorService, boolean ownExecutor) {
        this(executorService, ownExecutor, (ContextTrampoline<Object>)null);
    }
    
    public TaskScope(ExecutorService executorService, 
                     boolean ownExecutor, 
                     ContextTrampoline<Object> contextualizer) {
        this(nextGeneratedName(), executorService, ownExecutor, contextualizer);
    }
    
    
    public TaskScope(String name, ExecutorService executorService) {
        this(name, executorService, null);
    }
    
    public TaskScope(String name, 
                     ExecutorService executorService, 
                     ContextTrampoline<Object> contextualizer) {
        this(name, executorService, false, contextualizer);
    }
    
    public TaskScope(String name, ExecutorService executorService, boolean ownExecutor) {
        this(name, executorService, ownExecutor, null);
    }
    
    public TaskScope(String name, ExecutorService executorService, boolean ownExecutor, ContextTrampoline<Object> contextualizer) {
        Objects.requireNonNull(executorService, "executorService may not be null");
        this.name = name;
        if (ownExecutor) {
            this.executorService = executorService;
        } else {
            this.executorService = null;
        }
        this.completionService = new TaskExecutorCompletionService<>(executorService);
        this.contextualizer = contextualizer;
    }
    
    @SuppressWarnings("unchecked")
    public Promise<Void> fork(Runnable code) {
        checkOwner();
        Runnable contextualized = null == contextualizer ? code : contextualizer.contextual(code);
        Promise<?> result = completionService.submit(contextualized, null);
        allFutures.add(result);
        return (Promise<Void>)result;
    }
    
    @SuppressWarnings("unchecked")
    public <T> Promise<T> fork(Callable<T> code) {
        checkOwner();
        Callable<T> contextualized = null == contextualizer ? code : contextualizer.contextual(code);
        Promise<?> result = completionService.submit((Callable<Object>) contextualized);
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
        if (null != executorService) {
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