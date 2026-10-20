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
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;

/**
 * A {@link TaskExecutorService} that bounds the number of simultaneously executing tasks,
 * optimized for Java 21+ virtual threads.
 * <p>
 * This class extends {@link ThrottledExecutorService} to implement the
 * {@link TaskExecutorService} interface, so its {@code submit(...)} methods return
 * fully-featured {@link Promise} instances instead of standard
 * {@link java.util.concurrent.Future}s. It inherits the semaphore-bounded,
 * permit-hand-off execution model of its superclass: a new virtual thread is spawned per
 * task, while at most {@code maxConcurrentThreads} tasks run concurrently.
 * </p>
 * <p>
 * This is the preferred throttled executor when the results of submitted tasks need to be
 * composed with the Tascalate Concurrent combinators ({@code all}, {@code chunked},
 * {@code retry}, etc.).
 * </p>
 * <p>
 * Because it relies on virtual threads, this class is packaged in the <b>Java 21+</b> source
 * set of the multi-release JAR and is not accessible on earlier JDKs.
 * </p>
 *
 * @author vsilaev
 * @see ThrottledExecutorService
 * @see TaskExecutorService
 * @see TaskExecutors
 */
public class ThrottledTaskExecutorService extends ThrottledExecutorService
                                          implements TaskExecutorService {

    /**
     * Creates an executor that uses virtual threads named {@code <threadNamePrefix><n>} and a
     * zero-capacity ({@link java.util.concurrent.SynchronousQueue}) queue.
     *
     * @param threadNamePrefix     the prefix for the names of the virtual threads created
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     */
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads) {
        super(threadNamePrefix, maxConcurrentThreads);
    }

    /**
     * Creates an executor with the given thread factory and a zero-capacity queue.
     *
     * @param threadFactory        the factory used to create a thread per task
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     */
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads) {
        super(threadFactory, maxConcurrentThreads, 0);
    }

    /**
     * Creates an executor that uses virtual threads and a queue sized by {@code queueCapacity}.
     *
     * @param threadNamePrefix     the prefix for the names of the virtual threads created
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     * @param queueCapacity        the queue capacity: negative for unbounded, zero for no buffering,
     *                             positive for a bounded queue
     */
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads, int queueCapacity) {
        super(threadNamePrefix, maxConcurrentThreads, queueCapacity);
    }    

    /**
     * Creates an executor with the given thread factory and a queue sized by {@code queueCapacity},
     * using the {@linkplain RejectedExecutionHandler#ABORT_POLICY abort} rejection policy.
     *
     * @param threadFactory        the factory used to create a thread per task
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     * @param queueCapacity        the queue capacity: negative for unbounded, zero for no buffering,
     *                             positive for a bounded queue
     */
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, int queueCapacity) {
        super(threadFactory, maxConcurrentThreads, queueCapacity, RejectedExecutionHandler.ABORT_POLICY);
    }

    /**
     * Creates an executor that uses virtual threads, a zero-capacity queue, and the given
     * rejection handler.
     *
     * @param threadNamePrefix         the prefix for the names of the virtual threads created
     * @param maxConcurrentThreads     the maximum number of tasks allowed to run concurrently; must be positive
     * @param rejectedExecutionHandler the handler invoked when the queue cannot accept a task;
     *                                 {@code null} selects {@link RejectedExecutionHandler#ABORT_POLICY}
     */
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        super(threadNamePrefix, maxConcurrentThreads, rejectedExecutionHandler);
    }    

    /**
     * Creates an executor with the given thread factory, a zero-capacity queue, and the given
     * rejection handler.
     *
     * @param threadFactory            the factory used to create a thread per task
     * @param maxConcurrentThreads     the maximum number of tasks allowed to run concurrently; must be positive
     * @param rejectedExecutionHandler the handler invoked when the queue cannot accept a task;
     *                                 {@code null} selects {@link RejectedExecutionHandler#ABORT_POLICY}
     */
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        this(threadFactory, maxConcurrentThreads, 0, rejectedExecutionHandler);
    }

    /**
     * Creates an executor that uses virtual threads, a queue sized by {@code queueCapacity}, and
     * the given rejection handler.
     *
     * @param threadNamePrefix         the prefix for the names of the virtual threads created
     * @param maxConcurrentThreads     the maximum number of tasks allowed to run concurrently; must be positive
     * @param queueCapacity            the queue capacity: negative for unbounded, zero for no buffering,
     *                                 positive for a bounded queue
     * @param rejectedExecutionHandler the handler invoked when the queue cannot accept a task;
     *                                 {@code null} selects {@link RejectedExecutionHandler#ABORT_POLICY}
     */
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads, int queueCapacity, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        super(threadNamePrefix, maxConcurrentThreads, queueCapacity, rejectedExecutionHandler);
    }    

    /**
     * Creates an executor with the given thread factory, a queue sized by {@code queueCapacity},
     * and the given rejection handler.
     *
     * @param threadFactory            the factory used to create a thread per task
     * @param maxConcurrentThreads     the maximum number of tasks allowed to run concurrently; must be positive
     * @param queueCapacity            the queue capacity: negative for unbounded, zero for no buffering,
     *                                 positive for a bounded queue
     * @param rejectedExecutionHandler the handler invoked when the queue cannot accept a task;
     *                                 {@code null} selects {@link RejectedExecutionHandler#ABORT_POLICY}
     */
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, int queueCapacity, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        super(threadFactory, maxConcurrentThreads, queueByCapacity(queueCapacity), rejectedExecutionHandler);
    }

    /**
     * Creates an executor that uses virtual threads and the supplied task queue, using the
     * {@linkplain RejectedExecutionHandler#ABORT_POLICY abort} rejection policy.
     *
     * @param threadNamePrefix     the prefix for the names of the virtual threads created
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     * @param queue                the queue used to buffer tasks submitted while every permit is taken
     */
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads, BlockingQueue<Runnable> queue) {
        super(threadNamePrefix, maxConcurrentThreads, queue);
    }    

    /**
     * Creates an executor with the given thread factory and task queue, using the
     * {@linkplain RejectedExecutionHandler#ABORT_POLICY abort} rejection policy.
     *
     * @param threadFactory        the factory used to create a thread per task
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     * @param queue                the queue used to buffer tasks submitted while every permit is taken
     */
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, BlockingQueue<Runnable> queue) {
        super(threadFactory, maxConcurrentThreads, queue, RejectedExecutionHandler.ABORT_POLICY);
    }

    /**
     * Creates an executor that uses virtual threads, the supplied task queue, and the given
     * rejection handler.
     *
     * @param threadNamePrefix         the prefix for the names of the virtual threads created
     * @param maxConcurrentThreads     the maximum number of tasks allowed to run concurrently; must be positive
     * @param queue                    the queue used to buffer tasks submitted while every permit is taken
     * @param rejectedExecutionHandler the handler invoked when the queue cannot accept a task;
     *                                 {@code null} selects {@link RejectedExecutionHandler#ABORT_POLICY}
     */
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads, BlockingQueue<Runnable> queue, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        super(threadNamePrefix, maxConcurrentThreads, queue, rejectedExecutionHandler);
    }    

    /**
     * Creates an executor with the given thread factory, task queue, and rejection handler.
     *
     * @param threadFactory            the factory used to create a thread per task
     * @param maxConcurrentThreads     the maximum number of tasks allowed to run concurrently; must be positive
     * @param queue                    the queue used to buffer tasks submitted while every permit is taken
     * @param rejectedExecutionHandler the handler invoked when the queue cannot accept a task;
     *                                 {@code null} selects {@link RejectedExecutionHandler#ABORT_POLICY}
     */
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, BlockingQueue<Runnable> queue, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        super(threadFactory, maxConcurrentThreads, queue, rejectedExecutionHandler);
    }

    /**
     * Submits a {@link Runnable} task for execution and returns a {@link Promise} representing
     * its completion. The promise resolves to {@code null} on success.
     *
     * @param task the task to submit
     * @return a {@link Promise} that can be used to wait for completion and attach callbacks
     */
    @Override
    public Promise<?> submit(Runnable task) {
        return (Promise<?>) super.submit(task);
    }

    /**
     * Submits a {@link Runnable} task for execution and returns a {@link Promise} representing
     * the specified result upon successful completion.
     *
     * @param <T>    the type of the result
     * @param task   the task to submit
     * @param result the result to return upon successful completion
     * @return a {@link Promise} that can be used to wait for the result and attach callbacks
     */
    @Override
    public <T> Promise<T> submit(Runnable task, T result) {
        return (Promise<T>) super.submit(task, result);
    }

    /**
     * Submits a value-returning task for execution and returns a {@link Promise} representing
     * the pending result of the task.
     *
     * @param <T>  the type of the task's result
     * @param task the task to submit
     * @return a {@link Promise} that can be used to wait for the result and attach callbacks
     */
    @Override
    public <T> Promise<T> submit(Callable<T> task) {
        return (Promise<T>) super.submit(task);
    }

    /**
     * Returns a {@link RunnableFuture} that, when run, executes the given runnable and yields
     * the given value. Overridden to produce a {@link RunnablePromise}.
     *
     * @param <T>      the type of the result
     * @param runnable the runnable task
     * @param value    the value to return upon completion
     * @return a {@link RunnableFuture} wrapping the runnable and value
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return newTaskFor(Executors.callable(runnable, value));
    }

    /**
     * Returns a {@link RunnableFuture} that, when run, executes the given callable and yields
     * its result. Overridden to produce a {@link RunnablePromise} so that {@code submit(...)}
     * returns a fully-featured {@link Promise}.
     *
     * @param <T>      the type of the result
     * @param callable the callable task
     * @return a {@link RunnableFuture} wrapping the callable
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return TaskExecutors.newRunnablePromise(this, callable);
    }
}