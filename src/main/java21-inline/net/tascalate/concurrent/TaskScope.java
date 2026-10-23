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

import net.tascalate.concurrent.var.ContextRelay;

public class TaskScope implements AutoCloseable {
    private static final AtomicLong SCOPE_ID = new AtomicLong(0);

    private final String name;
    private final ExecutorService executorService; // Null if not owned
    private final TaskCompletionService<Object> completionService;
    private final ContextRelay<Object> contextualizer;
    private final Set<Promise<?>> allFutures = new HashSet<>();
    private final Thread owner = Thread.currentThread();

    // =========================================================================
    // Builder Pattern to replace combinatorial constructors
    // =========================================================================

    /**
     * Starts building a new {@link TaskScope}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link TaskScope}, avoiding confusing parameter combinations.
     */
    public static class Builder {
        private String name = nextGeneratedName();
        private ExecutorService executorService;
        private ThreadFactory threadFactory;
        private boolean ownExecutor = false;
        private ContextRelay<Object> contextualizer;

        /**
         * Sets the name of the task scope, useful for debugging and logging.
         *
         * @param name the scope name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name may not be null");
            return this;
        }

        /**
         * Sets the {@link ExecutorService} to be used for executing tasks.
         * <p>
         * By default, the scope will <b>not</b> own this executor and will not 
         * shut it down when {@link TaskScope#close()} is called.
         * </p>
         *
         * @param executorService the executor service
         * @return this builder
         */
        public Builder executor(ExecutorService executorService) {
            this.executorService = Objects.requireNonNull(executorService, "executorService may not be null");
            this.ownExecutor = false;
            return this;
        }

        /**
         * Sets the {@link ExecutorService} and explicitly specifies ownership.
         *
         * @param executorService the executor service
         * @param ownExecutor     if {@code true}, the scope will shut down this 
         *                        executor when {@link TaskScope#close()} is called
         * @return this builder
         */
        public Builder executor(ExecutorService executorService, boolean ownExecutor) {
            this.executorService = Objects.requireNonNull(executorService, "executorService may not be null");
            this.ownExecutor = ownExecutor;
            return this;
        }

        /**
         * Sets the {@link ThreadFactory} to create a new thread-per-task executor.
         * <p>
         * When a thread factory is provided, the scope <b>will</b> own the resulting 
         * executor and will shut it down when {@link TaskScope#close()} is called.
         * </p>
         *
         * @param threadFactory the thread factory
         * @return this builder
         */
        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = Objects.requireNonNull(threadFactory, "threadFactory may not be null");
            this.ownExecutor = true;
            return this;
        }

        /**
         * Sets the {@link ContextRelay} to propagate context variables to forked tasks.
         *
         * @param contextualizer the context relay
         * @return this builder
         */
        public Builder contextualizer(ContextRelay<Object> contextualizer) {
            this.contextualizer = contextualizer;
            return this;
        }

        /**
         * Builds and returns a new {@link TaskScope} instance.
         *
         * @return the configured task scope
         */
        public TaskScope build() {
            ExecutorService actualExecutor;
            boolean actualOwnExecutor;

            if (executorService != null) {
                actualExecutor = executorService;
                actualOwnExecutor = ownExecutor;
            } else if (threadFactory != null) {
                actualExecutor = Executors.newThreadPerTaskExecutor(threadFactory);
                actualOwnExecutor = true;
            } else {
                // Default to virtual threads if nothing is specified (Java 21+)
                actualExecutor = Executors.newVirtualThreadPerTaskExecutor();
                actualOwnExecutor = true;
            }

            return new TaskScope(name, actualExecutor, actualOwnExecutor, contextualizer);
        }
    }
    
    protected TaskScope(String name, ExecutorService executorService, boolean ownExecutor, ContextRelay<Object> contextualizer) {
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