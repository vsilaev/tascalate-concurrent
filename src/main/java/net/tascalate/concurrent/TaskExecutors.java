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

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A drop-in replacement for the standard {@link Executors} utility class that returns various
 * useful implementations of {@link TaskExecutorService} instead of standard {@link ExecutorService}s.
 * <p>
 * The methods in this class mirror the standard factory methods in {@link Executors}
 * (such as {@link #newFixedThreadPool(int)}, {@link #newCachedThreadPool()}, etc.) but ensure that
 * the returned executors produce {@link Promise}s from their {@code submit} methods.
 * </p>
 *
 * @author vsilaev
 * @see TaskExecutorService
 * @see ThreadPoolTaskExecutor
 */
public class TaskExecutors {

    private TaskExecutors() {}

    /**
     * Creates a new {@link ThreadFactoryBuilder} for configuring custom thread factories.
     *
     * @return a new thread factory builder
     */
    public static ThreadFactoryBuilder newThreadFactory() {
        return new ThreadFactoryBuilder();
    }

    /**
     * Creates a new {@link ThreadGroupBuilder} for configuring custom thread groups.
     *
     * @return a new thread group builder
     */
    public static ThreadGroupBuilder newThreadGroup() {
        return new ThreadGroupBuilder();
    }

    /**
     * Creates a thread pool that reuses a fixed number of threads operating off
     * a shared unbounded queue. At any point, at most {@code nThreads} threads
     * will be active processing tasks. If additional tasks are submitted when
     * all threads are active, they will wait in the queue until a thread is
     * available.
     *
     * @param nThreads the number of threads in the pool
     * @return the newly created task executor service
     * @throws IllegalArgumentException if {@code nThreads <= 0}
     */
    public static TaskExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolTaskExecutor(nThreads, nThreads, 
                                          0L, TimeUnit.MILLISECONDS,
                                          new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Creates a thread pool that reuses a fixed number of threads operating off
     * a shared unbounded queue, using the provided {@link ThreadFactory} to create new
     * threads when needed.
     *
     * @param nThreads      the number of threads in the pool
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created task executor service
     * @throws NullPointerException     if threadFactory is null
     * @throws IllegalArgumentException if {@code nThreads <= 0}
     */
    public static TaskExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new ThreadPoolTaskExecutor(nThreads, nThreads, 
                                          0L, TimeUnit.MILLISECONDS,
                                          new LinkedBlockingQueue<Runnable>(), threadFactory);
    }

    /**
     * Creates a thread pool that creates new threads as needed, but will reuse
     * previously constructed threads when they are available. These pools will
     * typically improve the performance of programs that execute many short-lived
     * asynchronous tasks. Threads that have not been used for sixty seconds are
     * terminated and removed from the cache.
     *
     * @return the newly created task executor service
     */
    public static TaskExecutorService newCachedThreadPool() {
        return new ThreadPoolTaskExecutor(0, Integer.MAX_VALUE, 
                                          60L, TimeUnit.SECONDS,
                                          new SynchronousQueue<Runnable>());
    }

    /**
     * Creates a thread pool that creates new threads as needed, but will reuse
     * previously constructed threads when they are available, and uses the
     * provided {@link ThreadFactory} to create new threads when needed.
     * 
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created task executor service
     * @throws NullPointerException if threadFactory is null
     */
    public static TaskExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new ThreadPoolTaskExecutor(0, Integer.MAX_VALUE, 
                                          60L, TimeUnit.SECONDS, 
                                          new SynchronousQueue<Runnable>(),
                                          threadFactory);
    }

    /**
     * Creates an executor that uses a single worker thread operating off an
     * unbounded queue. Tasks are guaranteed to execute sequentially, and no more
     * than one task will be active at any given time.
     *
     * @return the newly created single-threaded task executor service
     */
    public static TaskExecutorService newSingleThreadExecutor() {
        return adapt(Executors.newSingleThreadExecutor());
    }

    /**
     * Creates an executor that uses a single worker thread operating off an
     * unbounded queue, and uses the provided {@link ThreadFactory} to create a new
     * thread when needed.
     *
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created single-threaded task executor service
     * @throws NullPointerException if threadFactory is null
     */
    public static TaskExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return adapt(Executors.newSingleThreadExecutor(threadFactory));
    }

    /**
     * Adapts an existing {@link ExecutorService} to a {@link TaskExecutorService}.
     * <p>
     * If the provided executor is already a {@link TaskExecutorService}, it is returned as-is.
     * Otherwise, it is wrapped in an adapter that ensures {@code submit} methods return {@link Promise}s.
     * </p>
     *
     * @param executorService the executor service to adapt
     * @return a {@link TaskExecutorService} wrapping the given executor
     */
    public static TaskExecutorService adapt(ExecutorService executorService) {
        if (executorService instanceof TaskExecutorService) {
            return (TaskExecutorService) executorService;
        } else {
            return new TaskExecutorServiceAdapter(executorService);
        }
    }

    /**
     * Internal factory method to create a {@link RunnablePromise} for the given callable.
     */
    static <T> RunnablePromise<T> newRunnablePromise(Executor executor, Callable<T> callable) {
        return new RunnableCompletableTask<>(executor, callable);
    }

    /**
     * Internal implementation of a {@link RunnablePromise} that executes a {@link Callable}.
     */
    static class RunnableCompletableTask<T> extends CompletableTask<T> 
                                            implements RunnablePromise<T> {
        RunnableCompletableTask(Executor executor, Callable<T> callable) {
            super(executor, callable);
        }

        @Override
        public void run() {
            runTask();
        }
    }

    /**
     * Internal adapter that wraps a standard {@link ExecutorService} to implement {@link TaskExecutorService}.
     */
    static class TaskExecutorServiceAdapter extends AbstractExecutorService 
                                            implements TaskExecutorService {
        private final ExecutorService delegate;

        TaskExecutorServiceAdapter(ExecutorService executor) {
            delegate = executor;
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        protected <T> RunnablePromise<T> newTaskFor(Runnable runnable, T value) {
            return newTaskFor(Executors.callable(runnable, value));
        }

        @Override
        protected <T> RunnablePromise<T> newTaskFor(Callable<T> callable) {
            return TaskExecutors.newRunnablePromise(this, callable);
        }    

        @Override
        public <T> Promise<T> submit(Callable<T> callable) {
            return (Promise<T>)super.submit(callable);
        }

        @Override
        public <T> Promise<T> submit(Runnable codeBlock, T result) {
            return (Promise<T>) super.submit(codeBlock, result);
        }

        @Override
        public Promise<?> submit(Runnable codeBlock) {
            return (Promise<?>)super.submit(codeBlock);
        }
    }
}