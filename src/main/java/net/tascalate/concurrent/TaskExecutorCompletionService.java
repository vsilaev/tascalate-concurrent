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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A concrete implementation of {@link TaskCompletionService} that extends {@link ExecutorCompletionService}.
 * <p>
 * This class unseparates the submission of asynchronous tasks from the consumption of their results,
 * returning {@link Promise}s instead of standard {@link Future}s. It can wrap any {@link Executor},
 * adapting it internally to an {@link AbstractExecutorService} if necessary to support the required
 * task creation mechanics.
 * </p>
 *
 * @param <V> the type of the values returned by the submitted tasks
 * @author vsilaev
 * @see TaskCompletionService
 */
public class TaskExecutorCompletionService<V> extends ExecutorCompletionService<V> 
                                              implements TaskCompletionService<V> {

    /**
     * Creates a new {@code TaskExecutorCompletionService} with the given executor.
     *
     * @param executor the executor to use
     */
    public TaskExecutorCompletionService(Executor executor) {
        super(wrapExecutor(executor));
    }

    /**
     * Creates a new {@code TaskExecutorCompletionService} with the given executor and completion queue.
     *
     * @param executor        the executor to use
     * @param completionQueue the queue to use for holding completed tasks before their results are retrieved
     */
    public TaskExecutorCompletionService(Executor executor, BlockingQueue<Promise<V>> completionQueue) {
        super(wrapExecutor(executor), cast(completionQueue));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<V> submit(Callable<V> task) {
        return (Promise<V>)super.submit(task);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<V> submit(Runnable task, V result) {
        return (Promise<V>)super.submit(task, result);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<V> take() throws InterruptedException {
        return (Promise<V>)super.take();
    }

    /** {@inheritDoc} */
    @Override
    public Promise<V> poll() {
        return (Promise<V>)super.poll();
    }

    /** {@inheritDoc} */
    @Override
    public Promise<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return (Promise<V>)super.poll(timeout, unit);
    }

    @SuppressWarnings("unchecked")
    private static <V> BlockingQueue<Future<V>> cast(BlockingQueue<? extends Future<V>> queue) {
        return (BlockingQueue<Future<V>>)queue;
    }

    /**
     * Adapts the given executor to an {@link ExecutorService} if it isn't one already,
     * ensuring it can create {@link RunnablePromise} tasks internally.
     */
    private static Executor wrapExecutor(Executor executor) {
        if (executor instanceof TaskExecutorService && executor instanceof AbstractExecutorService) {
            return executor;
        } else {
            if (executor instanceof ExecutorService) {
                return TaskExecutors.adapt((ExecutorService)executor);
            } else {
                return new AbstractExecutorService() {
                    private volatile boolean terminated;

                    @Override
                    public void execute(Runnable command) {
                        executor.execute(command);
                    }

                    @Override
                    protected <T> RunnablePromise<T> newTaskFor(Runnable runnable, T value) {
                        return newTaskFor(Executors.callable(runnable, value));
                    }

                    @Override
                    protected <T> RunnablePromise<T> newTaskFor(Callable<T> callable) {
                        return TaskExecutors.newRunnablePromise(this, callable);
                    }                

                    // Just no-ops to fulfill ExecutorService contract
                    @Override
                    public void shutdown() {
                        terminated = true;
                    }

                    @Override
                    public List<Runnable> shutdownNow() {
                        terminated = true;
                        return Collections.emptyList();
                    }

                    @Override
                    public boolean isShutdown() {
                        return terminated;
                    }

                    @Override
                    public boolean isTerminated() {
                        return terminated;
                    }

                    @Override
                    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                        return true;
                    }
                };
            }
        }
    }
}