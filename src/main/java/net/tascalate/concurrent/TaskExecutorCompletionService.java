/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

    @SuppressWarnings("unchecked")
    public TaskExecutorCompletionService(TaskExecutorService executor,
                                         BlockingQueue<Promise<V>> completionQueue) {
        super(wrapExecutor(executor), (BlockingQueue<Future<V>>)(BlockingQueue<?>)completionQueue);
    }

    public Promise<V> submit(Callable<V> task) {
        return (Promise<V>)super.submit(task);
    }

    public Promise<V> submit(Runnable task, V result) {
        return (Promise<V>)super.submit(task, result);
    }

    public Promise<V> take() throws InterruptedException {
        return (Promise<V>)super.take();
    }

    public Promise<V> poll() {
        return (Promise<V>)super.poll();
    }

    public Promise<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return (Promise<V>)super.poll(timeout, unit);
    }
    
    private static Executor wrapExecutor(Executor executor) {
        if (executor instanceof TaskExecutorService && executor instanceof AbstractExecutorService) {
            return executor;
        } else {
            return new GenericExecutorWrapper(executor);
        }
    }

    static class GenericExecutorWrapper extends AbstractExecutorService {
        protected final Executor delegate;
        
        GenericExecutorWrapper(Executor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
        }
        
        /*
        @Override
        public <T> Promise<T> submit(Callable<T> task) {
            return (Promise<T>)super.submit(task);
        }

        @Override
        public <T> Promise<T> submit(Runnable task, T result) {
            return (Promise<T>)super.submit(task, result);
        }

        @Override
        public Promise<?> submit(Runnable task) {
            return (Promise<?>)super.submit(task);
        }
        */
        
        @Override
        protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
            return newTaskFor(Executors.callable(runnable, value));
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
            return TaskExecutors.newRunnablePromise(this, callable);
        }

    }
}
