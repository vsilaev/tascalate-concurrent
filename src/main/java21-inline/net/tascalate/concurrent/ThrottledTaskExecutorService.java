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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;

public class ThrottledTaskExecutorService extends ThrottledExecutorService 
                                          implements TaskExecutorService {
    
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads) {
        super(threadNamePrefix, maxConcurrentThreads);
    }
    
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads) {
        super(threadFactory, maxConcurrentThreads, 0);
    }
    
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads, int queueCapacity) {
        super(threadNamePrefix, maxConcurrentThreads, queueCapacity);
    }    
    
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, int queueCapacity) {
        super(threadFactory, maxConcurrentThreads, queueCapacity, RejectedExecutionHandler.ABORT_POLICY);
    }
    
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        super(threadNamePrefix, maxConcurrentThreads, rejectedExecutionHandler);
    }    
    
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        this(threadFactory, maxConcurrentThreads, 0, rejectedExecutionHandler);
    }
    
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads, int queueCapacity, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        super(threadNamePrefix, maxConcurrentThreads, queueCapacity, rejectedExecutionHandler);
    }    
    
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, int queueCapacity, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        super(threadFactory, maxConcurrentThreads, queueByCapacity(queueCapacity), rejectedExecutionHandler);
    }
    
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads, BlockingQueue<Runnable> queue) {
        super(threadNamePrefix, maxConcurrentThreads, queue);
    }    
    
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, BlockingQueue<Runnable> queue) {
        super(threadFactory, maxConcurrentThreads, queue, RejectedExecutionHandler.ABORT_POLICY);
    }
    
    public ThrottledTaskExecutorService(String threadNamePrefix, int maxConcurrentThreads, BlockingQueue<Runnable> queue, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        super(threadNamePrefix, maxConcurrentThreads, queue, rejectedExecutionHandler);
    }    
    
    public ThrottledTaskExecutorService(ThreadFactory threadFactory, int maxConcurrentThreads, BlockingQueue<Runnable> queue, 
                                        RejectedExecutionHandler<? super ThrottledExecutorService> rejectedExecutionHandler) {
        super(threadFactory, maxConcurrentThreads, queue, rejectedExecutionHandler);
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
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return newTaskFor(Executors.callable(runnable, value));
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return TaskExecutors.newRunnablePromise(this, callable);
    }

}
