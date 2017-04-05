package net.tascalate.concurrent;

import java.util.Collection;
import java.util.List;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TaskExecutors {
    /**
     * Creates a thread pool that reuses a fixed number of threads operating off
     * a shared unbounded queue. At any point, at most {@code nThreads} threads
     * will be active processing tasks. If additional tasks are submitted when
     * all threads are active, they will wait in the queue until a thread is
     * available. If any thread terminates due to a failure during execution
     * prior to shutdown, a new one will take its place if needed to execute
     * subsequent tasks. The threads in the pool will exist until it is
     * explicitly {@link ExecutorService#shutdown shutdown}.
     *
     * @param nThreads
     *            the number of threads in the pool
     * @return the newly created thread pool
     * @throws IllegalArgumentException
     *             if {@code nThreads <= 0}
     */
    public static TaskExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolTaskExecutor(nThreads, nThreads, 
                                          0L, TimeUnit.MILLISECONDS,
                                          new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Creates a thread pool that reuses a fixed number of threads operating off
     * a shared unbounded queue, using the provided ThreadFactory to create new
     * threads when needed. At any point, at most {@code nThreads} threads will
     * be active processing tasks. If additional tasks are submitted when all
     * threads are active, they will wait in the queue until a thread is
     * available. If any thread terminates due to a failure during execution
     * prior to shutdown, a new one will take its place if needed to execute
     * subsequent tasks. The threads in the pool will exist until it is
     * explicitly {@link ExecutorService#shutdown shutdown}.
     *
     * @param nThreads
     *            the number of threads in the pool
     * @param threadFactory
     *            the factory to use when creating new threads
     * @return the newly created thread pool
     * @throws NullPointerException
     *             if threadFactory is null
     * @throws IllegalArgumentException
     *             if {@code nThreads <= 0}
     */
    public static TaskExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new ThreadPoolTaskExecutor(nThreads, nThreads, 
                                          0L, TimeUnit.MILLISECONDS,
                                          new LinkedBlockingQueue<Runnable>(), threadFactory);
    }

    /**
     * Creates a thread pool that creates new threads as needed, but will reuse
     * previously constructed threads when they are available. These pools will
     * typically improve the performance of programs that execute many
     * short-lived asynchronous tasks. Calls to {@code execute} will reuse
     * previously constructed threads if available. If no existing thread is
     * available, a new thread will be created and added to the pool. Threads
     * that have not been used for sixty seconds are terminated and removed from
     * the cache. Thus, a pool that remains idle for long enough will not
     * consume any resources. Note that pools with similar properties but
     * different details (for example, timeout parameters) may be created using
     * {@link ThreadPoolExecutor} constructors.
     *
     * @return the newly created thread pool
     */
    public static TaskExecutorService newCachedThreadPool() {
        return new ThreadPoolTaskExecutor(0, Integer.MAX_VALUE, 
                                          60L, TimeUnit.SECONDS,
                                          new SynchronousQueue<Runnable>());
    }

    /**
     * Creates a thread pool that creates new threads as needed, but will reuse
     * previously constructed threads when they are available, and uses the
     * provided ThreadFactory to create new threads when needed.
     * 
     * @param threadFactory
     *            the factory to use when creating new threads
     * @return the newly created thread pool
     * @throws NullPointerException
     *             if threadFactory is null
     */
    public static TaskExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new ThreadPoolTaskExecutor(0, Integer.MAX_VALUE, 
                                          60L, TimeUnit.SECONDS, 
                                          new SynchronousQueue<Runnable>(),
                                          threadFactory);
    }

    /**
     * Creates an Executor that uses a single worker thread operating off an
     * unbounded queue. (Note however that if this single thread terminates due
     * to a failure during execution prior to shutdown, a new one will take its
     * place if needed to execute subsequent tasks.) Tasks are guaranteed to
     * execute sequentially, and no more than one task will be active at any
     * given time. Unlike the otherwise equivalent {@code newFixedThreadPool(1)}
     * the returned executor is guaranteed not to be reconfigurable to use
     * additional threads.
     *
     * @return the newly created single-threaded Executor
     */
    public static TaskExecutorService newSingleThreadExecutor() {
        return adapt(Executors.newSingleThreadExecutor());
    }

    /**
     * Creates an Executor that uses a single worker thread operating off an
     * unbounded queue, and uses the provided ThreadFactory to create a new
     * thread when needed. Unlike the otherwise equivalent
     * {@code newFixedThreadPool(1, threadFactory)} the returned executor is
     * guaranteed not to be reconfigurable to use additional threads.
     *
     * @param threadFactory
     *            the factory to use when creating new threads
     *
     * @return the newly created single-threaded Executor
     * @throws NullPointerException
     *             if threadFactory is null
     */
    public static TaskExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return adapt(Executors.newSingleThreadExecutor(threadFactory));
    }

    public static TaskExecutorService adapt(final ExecutorService executorService) {
        if (executorService instanceof TaskExecutorService) {
            return (TaskExecutorService) executorService;
        } else {
            return new TaskExecutorServiceAdapter(executorService);
        }
    }

    static class TaskExecutorServiceAdapter implements TaskExecutorService {
        private final ExecutorService delegate;

        TaskExecutorServiceAdapter(ExecutorService executor) {
            delegate = executor;
        }

        public void execute(Runnable command) {
            delegate.execute(command);
        }

        public void shutdown() {
            delegate.shutdown();
        }

        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        public <T> Promise<T> submit(Callable<T> callable) {
            final CompletableTask<T> task = createTask(callable);
            delegate.execute(task);
            return task;
        }

        public <T> Promise<T> submit(Runnable codeBlock, T result) {
            final CompletableTask<T> task = createTask(Executors.callable(codeBlock, result));
            delegate.execute(task);
            return task;
        }

        public Promise<?> submit(Runnable codeBlock) {
            final CompletableTask<?> task = createTask(Executors.callable(codeBlock, null));
            delegate.execute(task);
            return task;
        }

        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return delegate.invokeAll(tasks);
        }

        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, 
                                             long timeout, TimeUnit unit) throws InterruptedException {
            
            return delegate.invokeAll(tasks, timeout, unit);
        }

        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return delegate.invokeAny(tasks);
        }

        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, 
                               long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            
            return delegate.invokeAny(tasks, timeout, unit);
        }

        protected <T> CompletableTask<T> createTask(Callable<T> callable) {
            return new CompletableTask<T>(this, callable);
        }

    }
}
