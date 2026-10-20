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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

/**
 * A handler for tasks that cannot be executed by an {@link ExecutorService},
 * generic over the concrete executor type.
 * <p>
 * This is the library's counterpart of
 * {@link java.util.concurrent.RejectedExecutionHandler}: it is invoked by executors
 * such as {@code ThrottledExecutorService} when a submitted task can be neither
 * started immediately nor buffered &mdash; for example, when the concurrency limit is
 * reached and the waiting queue is full.
 * </p>
 * <p>
 * The library ships a set of ready-made policies that mirror the standard
 * {@link ThreadPoolExecutor} handlers plus queue-aware extensions:
 * </p>
 * <table>
 * <caption>Built-in rejection policies</caption>
 * <tr><th>Policy</th><th>Behavior</th></tr>
 * <tr><td>{@link CallerRunsPolicy}</td><td>runs the task on the submitting thread (unless the executor is shut down)</td></tr>
 * <tr><td>{@link AbortPolicy}</td><td>throws {@link RejectedExecutionException} (the default)</td></tr>
 * <tr><td>{@link DiscardPolicy}</td><td>silently drops the task</td></tr>
 * <tr><td>{@link DiscardOldestPolicy}</td><td>evicts the oldest queued task and retries the submission</td></tr>
 * <tr><td>{@link BlockPolicy}</td><td>blocks the submitting thread until queue space frees up</td></tr>
 * </table>
 *
 * @param <E> the type of the executor that rejects the task
 */
@FunctionalInterface
public interface RejectedExecutionHandler<E extends ExecutorService> {

    /**
     * Method that may be invoked by an executor when a task cannot be accepted
     * for execution.
     *
     * @param r the runnable task requested to be executed
     * @param e the executor attempting to execute this task
     * @throws RejectedExecutionException if there is no remedy (e.g., {@link AbortPolicy})
     */
    public void rejectedExecution(Runnable r, E e);

    /**
     * A handler for rejected tasks that runs the rejected task directly in the calling
     * thread of the {@code execute} request, unless the executor has shut down, in
     * which case the task is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler<ExecutorService> {
        /**
         * Runs the task in the calling thread, or discards it if the executor is shut down.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ExecutorService e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a {@link RejectedExecutionException}.
     * This is the default policy used by the throttled executors.
     */
    public static class AbortPolicy implements RejectedExecutionHandler<ExecutorService> {
        /**
         * Always throws {@link RejectedExecutionException}.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        public void rejectedExecution(Runnable r, ExecutorService e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }

    /**
     * A handler for rejected tasks that silently discards the rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler<ExecutorService> {
        /**
         * Does nothing, discarding the task.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ExecutorService e) {
        }
    }

    /**
     * Base class for rejection handlers that need access to the executor's waiting
     * {@link BlockingQueue} in order to make room for the rejected task.
     * <p>
     * Subclasses implement {@link #rejectedExecution(Runnable, ExecutorService, BlockingQueue)}
     * with the queue already extracted via the supplied queue-getter function.
     * </p>
     *
     * @param <E> the type of the executor that rejects the task
     */
    public static abstract class QueueDependentRejectedExecutionHandler<E extends ExecutorService>
                                 implements RejectedExecutionHandler<E> {

        /** Function extracting the waiting queue from the executor. */
        private final Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter;

        /**
         * Creates a handler that locates the executor's queue via the given getter.
         *
         * @param queueGetter a function returning the executor's waiting queue
         */
        public QueueDependentRejectedExecutionHandler(Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter) {
            this.queueGetter = queueGetter;
        }

        /**
         * Extracts the queue from the executor and delegates to the queue-aware overload.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        @Override
        public final void rejectedExecution(Runnable r, E e) {
            rejectedExecution(r, e, queueGetter.apply(e));
        }

        /**
         * Handles the rejected task given the executor's waiting queue.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @param q the executor's waiting queue
         */
        protected abstract void rejectedExecution(Runnable r, E e, BlockingQueue<Runnable> q);
    }

    /**
     * A handler for rejected tasks that discards the oldest queued task &mdash; unless
     * the executor is shut down, in which case the rejected task is discarded &mdash;
     * and then retries the submission of the rejected task.
     *
     * @param <E> the type of the executor that rejects the task
     */
    public static class DiscardOldestPolicy<E extends ExecutorService> 
                       extends QueueDependentRejectedExecutionHandler<E> {

        /**
         * Creates a handler that locates the executor's queue via the given getter.
         *
         * @param queueGetter a function returning the executor's waiting queue
         */
        public DiscardOldestPolicy(Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter) {
            super(queueGetter);
        }

        /**
         * Evicts the oldest queued task and re-submits the rejected task.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @param q the executor's waiting queue
         */
        protected void rejectedExecution(Runnable r, E e, BlockingQueue<Runnable> q) {
            if (!e.isShutdown()) {
                q.poll();
                e.execute(r);
            } 
        }
    }

    /**
     * A handler for rejected tasks that blocks the submitting thread until space
     * becomes available in the executor's waiting queue, then enqueues the task.
     * <p>
     * Use with care: this policy can cause the submitting thread to stall
     * indefinitely if the executor never drains its queue.
     * </p>
     *
     * @param <E> the type of the executor that rejects the task
     */
    public static class BlockPolicy<E extends ExecutorService> 
                        extends QueueDependentRejectedExecutionHandler<E> {

        /**
         * Creates a handler that locates the executor's queue via the given getter.
         *
         * @param queueGetter a function returning the executor's waiting queue
         */
        public BlockPolicy(Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter) {
            super(queueGetter);
        }

        /**
         * Blocks until the task can be placed on the queue.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @param q the executor's waiting queue
         * @throws RejectedExecutionException if the calling thread is interrupted while waiting
         */
        protected void rejectedExecution(Runnable r, E e, BlockingQueue<Runnable> q) {
            if (!e.isShutdown()) {
                try {
                    q.put(r);
                } catch (InterruptedException ex) {
                    throw new RejectedExecutionException(ex);
                }
            }
        }
    }

    /**
     * Creates a {@link BlockPolicy} that locates the executor's queue via the given getter.
     *
     * @param <E>         the type of the executor that rejects the task
     * @param queueGetter a function returning the executor's waiting queue
     * @return a new blocking rejection handler
     */
    public static <E extends ExecutorService> BlockPolicy<E> blockPolicy(Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter) {
        return new BlockPolicy<>(queueGetter);
    }

    /**
     * Creates a {@link DiscardOldestPolicy} that locates the executor's queue via the given getter.
     *
     * @param <E>         the type of the executor that rejects the task
     * @param queueGetter a function returning the executor's waiting queue
     * @return a new discard-oldest rejection handler
     */
    public static <E extends ExecutorService> DiscardOldestPolicy<E> discardOldestPolicy(Function<? super E, ? extends BlockingQueue<Runnable>> queueGetter) {
        return new DiscardOldestPolicy<>(queueGetter);
    }

    /**
     * Adapters exposing the rejection policies in a form compatible with
     * {@link ThreadPoolExecutor} ("TPE" stands for ThreadPoolExecutor).
     * <p>
     * The nested constants implement both this library's
     * {@link RejectedExecutionHandler} (parameterized with {@code ThreadPoolExecutor})
     * and the JDK's {@link java.util.concurrent.RejectedExecutionHandler}, so a single
     * policy object can be shared between the two worlds.
     * </p>
     */
    public static final class TPE {

        private TPE() {
        }

        /** Abort policy compatible with {@link ThreadPoolExecutor}. */
        public static class AbortPolicy extends ThreadPoolExecutor.AbortPolicy 
                                        implements RejectedExecutionHandler<ThreadPoolExecutor> { }

        /** Caller-runs policy compatible with {@link ThreadPoolExecutor}. */
        public static class CallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy
                                             implements RejectedExecutionHandler<ThreadPoolExecutor> { }

        /** Discard policy compatible with {@link ThreadPoolExecutor}. */
        public static class DiscardPolicy extends ThreadPoolExecutor.DiscardPolicy
                                          implements RejectedExecutionHandler<ThreadPoolExecutor> { }

        /** Discard-oldest policy compatible with {@link ThreadPoolExecutor}. */
        public static class DiscardOldestPolicy extends ThreadPoolExecutor.DiscardOldestPolicy
                                                implements RejectedExecutionHandler<ThreadPoolExecutor> { }

        /** Blocking policy compatible with {@link ThreadPoolExecutor}. */
        public static class BlockPolicy extends RejectedExecutionHandler.BlockPolicy<ThreadPoolExecutor>
                                        implements java.util.concurrent.RejectedExecutionHandler {
            /** Creates a blocking policy bound to the {@code ThreadPoolExecutor}'s own queue. */
            public BlockPolicy() {
                super(ThreadPoolExecutor::getQueue);
            }
        }

        /** Caller-runs policy instance for {@link ThreadPoolExecutor}. */
        public static final CallerRunsPolicy CALLER_RUNS_POLICY = new CallerRunsPolicy();
        /** Abort policy instance for {@link ThreadPoolExecutor}. */
        public static final AbortPolicy ABORT_POLICY = new AbortPolicy();
        /** Discard policy instance for {@link ThreadPoolExecutor}. */
        public static final DiscardPolicy DISCARD_POLICY = new DiscardPolicy();
        /** Discard-oldest policy instance for {@link ThreadPoolExecutor}. */
        public static final DiscardOldestPolicy DISCARD_OLDEST_POLICY = new DiscardOldestPolicy();
        /** Blocking policy instance for {@link ThreadPoolExecutor}. */
        public static final BlockPolicy BLOCK_POLICY = new BlockPolicy();
    }

    /** Shared caller-runs policy for generic {@link ExecutorService}s. */
    public static final RejectedExecutionHandler<ExecutorService> CALLER_RUNS_POLICY = new CallerRunsPolicy();
    /** Shared abort policy for generic {@link ExecutorService}s (the default). */
    public static final RejectedExecutionHandler<ExecutorService> ABORT_POLICY = new AbortPolicy();
    /** Shared discard policy for generic {@link ExecutorService}s. */
    public static final RejectedExecutionHandler<ExecutorService> DISCARD_POLICY = new DiscardPolicy();
}