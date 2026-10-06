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

public class ThrottledExecutorService extends AbstractExecutorService implements ExecutorService {
    
    private static final AtomicIntegerFieldUpdater<ThrottledExecutorService> STATE =
            AtomicIntegerFieldUpdater.newUpdater(ThrottledExecutorService.class, "state");
    
    // states: RUNNING -> SHUTDOWN -> TERMINATED
    private static final int RUNNING    = 0;
    private static final int SHUTDOWN   = 1;
    private static final int TERMINATED = 2;
    private volatile int state;

    private final int maxConcurrentThreads;
    private final Semaphore semaphore;
    private final BlockingQueue<Runnable> queue;
    private final ThreadFactory threadFactory;
    private final RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler;
    
    private final Set<Thread> threads = ConcurrentHashMap.newKeySet();
    private final CountDownLatch terminationSignal = new CountDownLatch(1);
    
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads) {
        this(threadFactory, maxConcurrentThreads, 0);
    }
    
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, int queueCapacity) {
        this(threadFactory, maxConcurrentThreads, queueCapacity, RejectedExecutionHandler.ABORT_POLICY);
    }
    
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, 
                                    RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        this(threadFactory, maxConcurrentThreads, 0, rejectedExecutionHandler);
    }
    
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, int queueCapacity, 
                                    RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        this(threadFactory, maxConcurrentThreads, queueByCapacity(queueCapacity), rejectedExecutionHandler);
    }
    
    public ThrottledExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, BlockingQueue<Runnable> queue) {
        this(threadFactory, maxConcurrentThreads, queue, RejectedExecutionHandler.ABORT_POLICY);
    }
    
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
    
    public Stream<Thread> threads() {
        return threads.stream().filter(Thread::isAlive);
    }

    public long threadCount() {
        return threads.stream().filter(Thread::isAlive).count();
    }

    public BlockingQueue<Runnable> getQueue() {
        return this.queue;
    }    
    
    @Override
    public void shutdown() {
        if (!isShutdown()) {
            tryShutdownAndTerminate(false);
        }
    }
    
    @Override
    public List<Runnable> shutdownNow() {
        if (!isTerminated()) {
            return tryShutdownAndTerminate(true);
        }
        return Collections.emptyList();
    }
    
    @Override
    public boolean isShutdown() {
        return state >= SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {
        return state >= TERMINATED; 
    }

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

    //@Override
    public void close() {
        awaitTermination();
    }
    
    protected void beforeExecute(Thread t, Runnable r) { }
    
    protected void afterExecute(Runnable r, Throwable t) { }

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