/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

public class TaskExecutorCompletionService<V> extends ExecutorCompletionService<V> 
                                              implements TaskCompletionService<V> {
    
    public TaskExecutorCompletionService(TaskExecutorService executor) {
        super(wrapExecutor(executor));
    }

    public TaskExecutorCompletionService(TaskExecutorService executor,
                                         BlockingQueue<Promise<V>> completionQueue) {
        super(wrapExecutor(executor), cast(completionQueue));
    }

    @Override
    public Promise<V> submit(Callable<V> task) {
        return (Promise<V>)super.submit(task);
    }

    @Override
    public Promise<V> submit(Runnable task, V result) {
        return (Promise<V>)super.submit(task, result);
    }

    @Override
    public Promise<V> take() throws InterruptedException {
        return (Promise<V>)super.take();
    }

    @Override
    public Promise<V> poll() {
        return (Promise<V>)super.poll();
    }

    @Override
    public Promise<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return (Promise<V>)super.poll(timeout, unit);
    }

    @SuppressWarnings("unchecked")
    private static <V> BlockingQueue<Future<V>> cast(BlockingQueue<? extends Future<V>> queue) {
        return (BlockingQueue<Future<V>>)queue;
    }
    
    private static Executor wrapExecutor(Executor executor) {
        if (executor instanceof TaskExecutorService && executor instanceof AbstractExecutorService) {
            return executor;
        } else {
            return new AbstractExecutorService() {
                private volatile boolean terminated;

                @Override
                public void execute(Runnable command) {
                    executor.execute(command);
                }
                
                @Override
                protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
                    return newTaskFor(Executors.callable(runnable, value));
                }

                @Override
                protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
                    return TaskExecutors.newRunnablePromise(this, callable);
                }                
                
                // Just no-ops to fulfill ExecutorService  contract
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
