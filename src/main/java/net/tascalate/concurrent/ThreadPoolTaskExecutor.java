package net.tascalate.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolTaskExecutor extends ThreadPoolExecutor implements TaskExecutorService {

    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, 
                                  long keepAliveTime, TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue, 
                                  RejectedExecutionHandler handler) {
        
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, 
                                  long keepAliveTime, TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue, 
                                  ThreadFactory threadFactory, 
                                  RejectedExecutionHandler handler) {
        
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, 
                                  long keepAliveTime, TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue, 
                                  ThreadFactory threadFactory) {
        
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, 
                                  long keepAliveTime, TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue) {
        
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    @Override
    public Promise<?> submit(Runnable task) {
        return (Promise<?>) super.submit(task);
    }

    @Override
    public <T> Promise<T> submit(Runnable task, T result) {
        return (Promise<T>) super.submit(task, result);
    }

    @Override
    public <T> Promise<T> submit(Callable<T> task) {
        return (Promise<T>) super.submit(task);
    }

    @Override
    protected <T> CompletableTask<T> newTaskFor(Runnable runnable, T value) {
        return newTaskFor(Executors.callable(runnable, value));
    }

    @Override
    protected <T> CompletableTask<T> newTaskFor(Callable<T> callable) {
        return new CompletableTask<>(this, callable);
    }

}
