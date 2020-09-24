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

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The {@link Promise} implementation for intermediate long-running blocking task
 * @author vsilaev
 *
 * @param <T>
 */
class CompletableSubTask<T> extends AbstractCompletableTask<T> {

    private static class DelegatingCallable<T> implements Callable<T> {
        private final AtomicReference<Callable<T>> delegateRef = new AtomicReference<>();

        void setup(Callable<T> delegate) {
            boolean updated = delegateRef.compareAndSet(null, delegate);
            if (!updated) {
                throw new IllegalStateException("Delegate may be set only once");
            }
        }

        @Override
        public T call() throws Exception {
            Callable<T> delegate = delegateRef.get();
            if (null == delegate) {
                throw new IllegalStateException("Call is not configured");
            } else {
                return delegate.call();
            }
        }

    }
    
    private final DelegatingCallable<T> action;

    CompletableSubTask(Executor executor) {
        this(executor, new DelegatingCallable<>());
    }
    
    private CompletableSubTask(Executor executor, DelegatingCallable<T> action) {
        super(executor, action);
        this.action = action;
    }

    @Override
    final void fireTransition(Callable<T> code) {
        action.setup(code);
        task.run();
    }

    @Override
    protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor) {
        return new CompletableSubTask<U>(executor);
    }
}
