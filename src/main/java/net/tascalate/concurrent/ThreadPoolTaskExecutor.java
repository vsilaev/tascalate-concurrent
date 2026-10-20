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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A concrete implementation of {@link TaskExecutorService} that extends {@link ThreadPoolExecutor}.
 * <p>
 * This class provides a standard thread pool that returns {@link Promise}s from its {@code submit}
 * methods, making it a drop-in replacement for {@link ThreadPoolExecutor} in environments that
 * leverage the Tascalate Concurrent library.
 * </p>
 * <p>
 * It overrides {@link #newTaskFor(Callable)} and {@link #newTaskFor(Runnable, Object)} to ensure
 * that the internal {@link RunnableFuture} created for each task is a {@link RunnablePromise},
 * which is the underlying mechanism that allows the {@code submit} methods to return fully-featured
 * {@link Promise} instances.
 * </p>
 *
 * @author vsilaev
 * @see TaskExecutors
 */
public class ThreadPoolTaskExecutor extends ThreadPoolExecutor implements TaskExecutorService {

    /**
     * Creates a new {@code ThreadPoolTaskExecutor} with the given core and maximum pool sizes,
     * keep-alive time, work queue, and rejection handler.
     *
     * @param corePoolSize    the number of threads to keep in the pool
     * @param maximumPoolSize the maximum number of threads to allow in the pool
     * @param keepAliveTime   when the number of threads is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating
     * @param unit            the time unit for the {@code keepAliveTime} argument
     * @param workQueue       the queue to use for holding tasks before they are executed
     * @param handler         the handler to use when execution is blocked because the thread bounds and queue capacities are reached
     */
    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, 
                                  long keepAliveTime, TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue, 
                                  RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    /**
     * Creates a new {@code ThreadPoolTaskExecutor} with the given core and maximum pool sizes,
     * keep-alive time, work queue, thread factory, and rejection handler.
     *
     * @param corePoolSize    the number of threads to keep in the pool
     * @param maximumPoolSize the maximum number of threads to allow in the pool
     * @param keepAliveTime   when the number of threads is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating
     * @param unit            the time unit for the {@code keepAliveTime} argument
     * @param workQueue       the queue to use for holding tasks before they are executed
     * @param threadFactory   the factory to use when the executor creates a new thread
     * @param handler         the handler to use when execution is blocked because the thread bounds and queue capacities are reached
     */
    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, 
                                  long keepAliveTime, TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue, 
                                  ThreadFactory threadFactory, 
                                  RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /**
     * Creates a new {@code ThreadPoolTaskExecutor} with the given core and maximum pool sizes,
     * keep-alive time, work queue, and thread factory.
     *
     * @param corePoolSize    the number of threads to keep in the pool
     * @param maximumPoolSize the maximum number of threads to allow in the pool
     * @param keepAliveTime   when the number of threads is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating
     * @param unit            the time unit for the {@code keepAliveTime} argument
     * @param workQueue       the queue to use for holding tasks before they are executed
     * @param threadFactory   the factory to use when the executor creates a new thread
     */
    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, 
                                  long keepAliveTime, TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue, 
                                  ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    /**
     * Creates a new {@code ThreadPoolTaskExecutor} with the given core and maximum pool sizes,
     * keep-alive time, and work queue.
     *
     * @param corePoolSize    the number of threads to keep in the pool
     * @param maximumPoolSize the maximum number of threads to allow in the pool
     * @param keepAliveTime   when the number of threads is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating
     * @param unit            the time unit for the {@code keepAliveTime} argument
     * @param workQueue       the queue to use for holding tasks before they are executed
     */
    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, 
                                  long keepAliveTime, TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<?> submit(Runnable task) {
        return (Promise<?>) super.submit(task);
    }

    /** {@inheritDoc} */
    @Override
    public <T> Promise<T> submit(Runnable task, T result) {
        return (Promise<T>) super.submit(task, result);
    }

    /** {@inheritDoc} */
    @Override
    public <T> Promise<T> submit(Callable<T> task) {
        return (Promise<T>) super.submit(task);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to ensure the internal task wrapper is a {@link RunnablePromise}.
     * </p>
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return newTaskFor(Executors.callable(runnable, value));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to ensure the internal task wrapper is a {@link RunnablePromise}.
     * </p>
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return TaskExecutors.newRunnablePromise(this, callable);
    }
}