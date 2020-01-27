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
package net.tascalate.concurrent.var;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class ContextualExecutorService<D extends ExecutorService> 
    extends ContextualExecutor<D> 
    implements ExecutorService {
    
    protected ContextualExecutorService(D delegate, 
                                        List<ContextVar<?>> contextVars, 
                                        ContextVar.Propagation propagation, 
                                        List<Object> capturedContext) {
        
        super(delegate, contextVars, propagation, capturedContext);
    }

    public void execute(Runnable command) {
        delegate.execute(contextualRunnable(command));
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

    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(contextualCallable(task));
    }

    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(contextualRunnable(task), result);
    }

    public Future<?> submit(Runnable task) {
        return delegate.submit(contextualRunnable(task));
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(contextualCallables(tasks));
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(contextualCallables(tasks), timeout, unit);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(contextualCallables(tasks));
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(contextualCallables(tasks), timeout, unit);
    }

    private <T> Collection<Callable<T>> contextualCallables(Collection<? extends Callable<T>> tasks) {
        return tasks.stream().map(this::contextualCallable).collect(Collectors.toList());
    }
    
    protected <T> Callable<T> contextualCallable(Callable<T> original) {
        return () -> {
            List<Object> originalContext = applyCapturedContext(); 
            try {
                return original.call();
            } finally {
                restoreContextVars(originalContext);
            }
        };
    }
}
