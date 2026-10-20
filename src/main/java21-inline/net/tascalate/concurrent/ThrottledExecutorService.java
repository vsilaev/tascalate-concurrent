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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.stream.Stream;

/**
 * An {@link ExecutorService} that bounds the number of simultaneously executing tasks,
 * optimized for Java 21+ virtual threads.
 *
 * <h2>Execution model</h2>
 * <p>
 * Unlike a classic {@link java.util.concurrent.ThreadPoolExecutor}, which keeps a fixed
 * set of long-lived worker threads that pull tasks from a queue, this executor spawns a
 * <b>new thread for every submitted task</b> while strictly capping the number of
 * <i>concurrently running</i> threads with a {@link Semaphore} holding
 * {@code maxConcurrentThreads} permits.
 * </p>
 * <p>
 * By default those threads are <b>virtual threads</b> (created via {@link Thread#ofVirtual()}),
 * so per-task thread creation is extremely cheap while the semaphore still guarantees that at
 * most {@code maxConcurrentThreads} tasks execute at the same time. This combines the massive
 * scalability of virtual threads with a hard concurrency bound &mdash; ideal for rate-limiting
 * access to a downstream resource (a database, a third-party API, a connection pool) without
 * wasting OS threads on queued work.
 * </p>
 *
 * <h2>Permit hand-off</h2>
 * <p>
 * When a task completes, its thread does not simply release the semaphore permit. It first
 * checks the waiting queue: if another task is pending, the permit is <i>handed off</i>
 * directly to a freshly spawned thread for that next task; only when the queue is empty is the
 * permit returned to the semaphore. This minimizes the latency between consecutive tasks and
 * avoids an extra wake-up round-trip through the semaphore.
 * </p>
 *
 * <h2>Queuing and rejection</h2>
 * <p>
 * Tasks submitted while all permits are taken are placed into a configurable
 * {@link BlockingQueue}. The queue is selected by capacity at construction time:
 * </p>
 * <ul>
 *   <li>a <b>negative</b> capacity &rarr; an unbounded {@link LinkedBlockingQueue};</li>
 *   <li>a capacity of <b>zero</b> &rarr; a {@link SynchronousQueue}, i.e. no buffering &mdash;
 *       a task is rejected as soon as every permit is taken;</li>
 *   <li>a <b>positive</b> capacity &rarr; a bounded {@link LinkedBlockingQueue}.</li>
 * </ul>
 * <p>
 * When the queue cannot accept a task, the configured {@link RejectedExecutionHandler} is
 * invoked (defaulting to {@link RejectedExecutionHandler#ABORT_POLICY}).
 * </p>
 *
 * <h2>Lifecycle</h2>
 * <p>
 * The executor follows the standard {@code RUNNING -> SHUTDOWN -> TERMINATED} state machine.
 * {@link #shutdown()} stops accepting new tasks and lets running/queued tasks finish;
 * {@link #shutdownNow()} additionally drains the queue and interrupts running threads. The
 * executor is {@link AutoCloseable}: {@link #close()} shuts it down gracefully and blocks
 * until termination.
 * </p>
 *
 * <h2>Availability</h2>
 * <p>
 * Because it relies on virtual threads, this class is packaged in the <b>Java 21+</b> source
 * set of the multi-release JAR and is not accessible on earlier JDKs.
 * </p>
 *
 * @author vsilaev
 * @see ThrottledTaskExecutorService
 * @see TaskExecutorService
 */
public class ThrottledExecutorService extends AbstractExecutorService implements ExecutorService {

    /** Atomic updater for the {@link #state} field. */
    private static final AtomicIntegerFieldUpdater<ThrottledExecutorService> STATE =
            AtomicIntegerFieldUpdater.newUpdater(ThrottledExecutorService.class, "state");

    // states: RUNNING -> SHUTDOWN -> TERMINATED
    /** The executor is accepting and running tasks. */
    private static final int RUNNING    = 0;
    /** The executor stopped accepting tasks but is finishing the ones already submitted. */
    private static final int SHUTDOWN   = 1;
    /** The executor has fully stopped; all resources are released. */
    private static final int TERMINATED = 2;

    /** Current lifecycle state; transitions are CAS-ed via {@link #STATE}. */
    private volatile int state;

    /** The maximum number of tasks allowed to run concurrently. */
    private final int maxConcurrentThreads;
    /** Semaphore whose permits represent the available concurrency slots. */
    private final Semaphore semaphore;
    /** Buffer for tasks submitted while every permit is taken. */
    private final BlockingQueue<Runnable> queue;
    /** Factory used to create a fresh thread (virtual by default) for each task. */
    private final ThreadFactory threadFactory;
    /** Handler invoked when the queue cannot accept a task. */
    private final RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler;

    /** Set of currently live worker threads, used for monitoring and interruption. */
    private final Set<Thread> threads = ConcurrentHashMap.newKeySet();
    /** Latch counted down exactly once when the executor reaches the TERMINATED state. */
    private final CountDownLatch terminationSignal = new CountDownLatch(1);

    /**
     * Creates an executor that uses virtual threads named {@code <threadNamePrefix><n>} and an
     * unbounded queue is <b>not</b> implied here &mdash; the queue capacity defaults to {@code 0}
     * (a {@link SynchronousQueue}, i.e. no buffering).
     *
     * @param threadNamePrefix     the prefix for the names of the virtual threads created
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     */
    public ThrottledExecutorService(String threadNamePrefix, int maxConcurrentThreads) {
        this(makeThreadFactory(threadNamePrefix), maxConcurrentThreads);
    }

    /**
     * Creates an executor with the given thread factory and a zero-capacity
     * ({@link SynchronousQueue}) queue.
     *
     * @param threadFactory        the factory used to create a thread per task
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     */
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads) {
        this(threadFactory, maxConcurrentThreads, 0);
    }

    /**
     * Creates an executor that uses virtual threads and a queue sized by {@code queueCapacity}.
     *
     * @param threadNamePrefix     the prefix for the names of the virtual threads created
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     * @param queueCapacity        the queue capacity: negative for unbounded, zero for no buffering
     *                             ({@link SynchronousQueue}), positive for a bounded queue
     */
    public ThrottledExecutorService(String threadNamePrefix, int maxConcurrentThreads, int queueCapacity) {
        this(makeThreadFactory(threadNamePrefix), maxConcurrentThreads, queueCapacity);
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
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, int queueCapacity) {
        this(threadFactory, maxConcurrentThreads, queueCapacity, RejectedExecutionHandler.ABORT_POLICY);
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
    public ThrottledExecutorService(String threadNamePrefix, int maxConcurrentThreads, 
                                    RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        this(makeThreadFactory(threadNamePrefix), maxConcurrentThreads, rejectedExecutionHandler);
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
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, 
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
    public ThrottledExecutorService(String threadNamePrefix, int maxConcurrentThreads, int queueCapacity, 
                                    RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        this(makeThreadFactory(threadNamePrefix), maxConcurrentThreads, queueCapacity, rejectedExecutionHandler);
    }

    /**
     * The canonical constructor. Creates an executor with the given thread factory, a queue sized
     * by {@code queueCapacity}, and the given rejection handler.
     *
     * @param threadFactory            the factory used to create a thread per task
     * @param maxConcurrentThreads     the maximum number of tasks allowed to run concurrently; must be positive
     * @param queueCapacity            the queue capacity: negative for unbounded, zero for no buffering
     *                                 ({@link SynchronousQueue}), positive for a bounded queue
     * @param rejectedExecutionHandler the handler invoked when the queue cannot accept a task;
     *                                 {@code null} selects {@link RejectedExecutionHandler#ABORT_POLICY}
     */
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, int queueCapacity, 
                                    RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        this(threadFactory, maxConcurrentThreads, queueByCapacity(queueCapacity), rejectedExecutionHandler);
    }

    /**
     * Creates an executor that uses virtual threads and the supplied task queue, using the
     * {@linkplain RejectedExecutionHandler#ABORT_POLICY abort} rejection policy.
     *
     * @param threadNamePrefix     the prefix for the names of the virtual threads created
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     * @param queue                the queue used to buffer tasks submitted while every permit is taken
     */
    public ThrottledExecutorService(String threadNamePrefix, int maxConcurrentThreads, BlockingQueue<Runnable> queue) {
        this(makeThreadFactory(threadNamePrefix), maxConcurrentThreads, queue);
    }

    /**
     * Creates an executor with the given thread factory and task queue, using the
     * {@linkplain RejectedExecutionHandler#ABORT_POLICY abort} rejection policy.
     *
     * @param threadFactory        the factory used to create a thread per task
     * @param maxConcurrentThreads the maximum number of tasks allowed to run concurrently; must be positive
     * @param queue                the queue used to buffer tasks submitted while every permit is taken
     */
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, BlockingQueue<Runnable> queue) {
        this(threadFactory, maxConcurrentThreads, queue, RejectedExecutionHandler.ABORT_POLICY);
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
    public ThrottledExecutorService(String threadNamePrefix, int maxConcurrentThreads, BlockingQueue<Runnable> queue, 
                                    RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        this(makeThreadFactory(threadNamePrefix), maxConcurrentThreads, queue, rejectedExecutionHandler);
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
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, BlockingQueue<Runnable> queue, 
                                    RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        this.threadFactory = threadFactory;
        this.maxConcurrentThreads = maxConcurrentThreads;
        this.semaphore = new Semaphore(maxConcurrentThreads);
        this.queue = queue;
        this.rejectedExecutionHandler = rejectedExecutionHandler != null 
                                        ? rejectedExecutionHandler 
                                        : RejectedExecutionHandler.ABORT_POLICY;
    }

    /**
     * Builds the default {@link ThreadFactory} producing named virtual threads.
     *
     * @param threadNamePrefix the prefix for the virtual thread names; a per-thread counter is appended
     * @return a virtual-thread factory
     */
    private static ThreadFactory makeThreadFactory(String threadNamePrefix) {
        return Thread.ofVirtual().name(threadNamePrefix, 0).factory();
    }

    /**
     * Returns a snapshot stream of the worker threads that are currently alive.
     *
     * @return a stream of live worker threads
     */
    public Stream<Thread> threads() {
        return threads.stream().filter(Thread::isAlive);
    }

    /**
     * Returns the number of worker threads that are currently alive.
     *
     * @return the count of live worker threads
     */
    public long threadCount() {
        return threads.stream().filter(Thread::isAlive).count();
    }

    /**
     * Returns the queue used to buffer tasks submitted while every permit is taken.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return this.queue;
    }    

    /**
     * Initiates an orderly shutdown: previously submitted tasks are executed, but no new tasks
     * will be accepted. Has no additional effect if already shut down.
     */
    @Override
    public void shutdown() {
        if (!isShutdown()) {
            tryShutdownAndTerminate(false);
        }
    }

    /**
     * Attempts to stop all actively executing tasks and halts the processing of waiting tasks:
     * the queue is drained and the running threads are interrupted.
     *
     * @return the list of tasks that were waiting in the queue and never started
     */
    @Override
    public List<Runnable> shutdownNow() {
        if (!isTerminated()) {
            return tryShutdownAndTerminate(true);
        }
        return Collections.emptyList();
    }

    /**
     * Returns {@code true} if this executor has been shut down.
     *
     * @return {@code true} if shut down
     */
    @Override
    public boolean isShutdown() {
        return state >= SHUTDOWN;
    }

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     *
     * @return {@code true} if fully terminated
     */
    @Override
    public boolean isTerminated() {
        return state >= TERMINATED; 
    }

    /**
     * Blocks until all tasks have completed after a shutdown request, or the timeout occurs,
     * or the current thread is interrupted.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return {@code true} if the executor terminated within the timeout, {@code false} otherwise
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit);
        if (isTerminated()) {
            return true;
        } else {
            return terminationSignal.await(timeout, unit);
        }
    }

    /**
     * Waits for executor to terminate.
     */
    private void awaitTermination() {
        boolean terminated = isTerminated();
        if (!terminated) {
            tryShutdownAndTerminate(false);
            boolean interrupted = false;
            while (!terminated) {
                try {
                    terminated = awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    if (!interrupted) {
                        tryShutdownAndTerminate(true);
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Shuts the executor down gracefully and blocks until it terminates, as required by the
     * {@link AutoCloseable} contract of {@link ExecutorService}.
     */
    //@Override
    public void close() {
        awaitTermination();
    }

    /**
     * Hook invoked before a task is executed in the given thread. The default implementation is
     * empty; subclasses may override it for bookkeeping or instrumentation.
     *
     * @param t the thread that will run the task
     * @param r the task about to be executed
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * Hook invoked after a task has executed (or thrown) in the given thread. The default
     * implementation is empty; subclasses may override it for bookkeeping or instrumentation.
     *
     * @param r the task that has run
     * @param t the exception thrown by the task, or {@code null} if it completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * Executes the given command at some point in the future, subject to the concurrency limit.
     * <p>
     * If a permit is immediately available the command starts right away on a fresh thread;
     * otherwise it is enqueued. If the queue cannot accept it, the configured
     * {@link RejectedExecutionHandler} is invoked.
     * </p>
     *
     * @param command the task to execute
     * @throws NullPointerException       if the command is {@code null}
     * @throws RejectedExecutionException if the executor is shut down, or the queue is full and
     *                                    the rejection policy aborts
     */
    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "Command passed is null");
        if (state >= SHUTDOWN) {
            // shutdown or terminated
            throw new RejectedExecutionException();
        }
        //  Try to acquire a concurrency slot immediately
        if (semaphore.tryAcquire()) {
            executeDirect(command, true);
        } else {
            //  No slot available: enqueue the task
            if (!queue.offer(command)) {
                rejectedExecutionHandler.rejectedExecution(command, this);
            }
            executeNextQueued(command);
        }
    }

    /**
     * Attempts to acquire a permit and start the next queued task. This closes the race window
     * where a permit is released between the failed {@code tryAcquire} in {@link #execute} and
     * the enqueueing of the new task.
     *
     * @param explicitTask the task just offered to the queue (if any), used to propagate
     *                     exceptions only for the caller's own task
     */
    private void executeNextQueued(Runnable explicitTask) {
        if (isTerminated()) {
            return;
        }
        if (semaphore.tryAcquire()) {
            Runnable task = queue.poll();
            if (task != null) {
                executeDirect(task, task == explicitTask);
            } else {
                // Queue is empty, release the permit back
                semaphore.release();
                if (state == SHUTDOWN) {
                    tryTerminate();
                }
            }
        }
    }

    /**
     * Starts a task directly on a fresh thread, optionally re-throwing any scheduling failure.
     *
     * @param task               the task to run
     * @param propagateException whether scheduling errors should be propagated to the caller
     */
    private void executeDirect(Runnable task, boolean propagateException) {
        try {
            executeDirect(task);
        } catch (RejectedExecutionException ex) {
            if (propagateException) {
                throw ex;
            }
        } catch (RuntimeException | Error ex) {
            if (propagateException) {
                throw new RejectedExecutionException(ex);
            }
        }        
    }

    /**
     * Spawns a new thread for the given task and runs it. On completion the thread hands its
     * permit off to the next queued task, or releases the permit when the queue is empty.
     *
     * @param task the task to run
     */
    private void executeDirect(Runnable task) {
        Thread thread = null;
        try {
            thread = threadFactory.newThread(() -> {
                Thread currentThread = Thread.currentThread(); 
                try {
                    beforeExecute(currentThread, task);
                    Throwable thrown = null;
                    try {
                        // Execute the actual task
                        task.run();
                    } catch (Throwable x) {
                        thrown = x;
                        throw x; // Re-throw to trigger UncaughtExceptionHandler / kill thread
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    // Task is done. Remove self from tracking.
                    threads.remove(currentThread);
                    // We still hold the permit. Try to get the next task from the queue.
                    Runnable nextTask = queue.poll();
                    if (nextTask != null) {
                        // PASS THE PERMIT: Spawn a NEW thread for the next task.
                        executeDirect(nextTask, false);
                    } else {
                        // Queue is empty, release the permit.
                        semaphore.release();
                        // Queue may be populated after releasing permit from
                        // public void execute(Runnable command), re-check
                        executeNextQueued(null);
                    }
                }
            });
        } finally {
            if (thread == null) {
                semaphore.release();
                executeNextQueued(null);
            }
        }
        Objects.requireNonNull(thread, "ThreadFactory created null object");
        threads.add(thread);
        boolean started = false;
        try {
            thread.start();
            started = true;
        } finally {
            if (!started) {
                threads.remove(thread);
                semaphore.release();
                executeNextQueued(null);
            }
        }
    }

    /**
     * Attempts to transition from SHUTDOWN to TERMINATED once no task is running, the queue is
     * empty, and every permit has been returned. Signals the termination latch on success.
     */
    private void tryTerminate() {
        assert state >= SHUTDOWN;
        if (semaphore.availablePermits() == maxConcurrentThreads &&
            queue.isEmpty() &&
            threads.isEmpty() &&
            STATE.compareAndSet(this, SHUTDOWN, TERMINATED)) {
            // signal waiters
            terminationSignal.countDown();
        }
    }

    /**
     * Performs the state transition to SHUTDOWN and, if requested, drains the queue and
     * interrupts the running threads.
     *
     * @param interruptThreads whether to interrupt running threads and drain the queue
     * @return the list of tasks drained from the queue when {@code interruptThreads} is
     *         {@code true}; an empty list otherwise
     */
    private List<Runnable> tryShutdownAndTerminate(boolean interruptThreads) {
        if (STATE.compareAndSet(this, RUNNING, SHUTDOWN)) {
            tryTerminate();
        }
        List<Runnable> drained = new ArrayList<>();
        if (interruptThreads) {
            // Drain the queue FIRST to prevent finishing threads from spawning new ones
            queue.drainTo(drained);
            // Interrupt all currently running threads
            threads.forEach(Thread::interrupt);
            // Try transition to TERMINATED
            tryTerminate();
        }
        return drained;
    }

    /**
     * Selects a queue implementation based on the requested capacity.
     *
     * @param queueCapacity the requested capacity: negative for an unbounded queue, zero for a
     *                      {@link SynchronousQueue}, positive for a bounded queue
     * @param <E>           the element type
     * @return a suitable {@link BlockingQueue}
     */
    static <E> BlockingQueue<E> queueByCapacity(int queueCapacity) {
        if (queueCapacity < 0) {
            return new LinkedBlockingQueue<>();
        } else if (queueCapacity == 0) {
            return new SynchronousQueue<>();
        } else {
            return new LinkedBlockingQueue<>(queueCapacity);
        }
    }
}