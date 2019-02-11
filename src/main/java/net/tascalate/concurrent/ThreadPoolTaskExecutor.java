/**
 * Copyright 2015-2019 Valery Silaev (http://vsilaev.com)
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * <p>Concrete implementation of {@link TaskExecutorService} interface.
 * <p>Specialization of {@link ThreadPoolExecutor} that uses {@link Promise} as a result of <code>submit(...)</code> methods.
 * 
 * @author vsilaev
 *
 */
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
