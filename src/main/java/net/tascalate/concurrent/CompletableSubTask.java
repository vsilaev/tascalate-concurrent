/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@link Promise} implementation for intermediate long-running blocking task
 * @author vsilaev
 *
 * @param <T>
 */
class CompletableSubTask<T> extends AbstractCompletableTask<T> {

    static class DelegatingCallable<T> implements Callable<T> {

        final private AtomicBoolean setupGuard = new AtomicBoolean(false);
        private Callable<T> delegate;

        void setup(Callable<T> delegate) {
            if (setupGuard.compareAndSet(false, true)) {
                this.delegate = delegate;
            } else {
                throw new IllegalStateException("Delegate may be set only once");
            }
        }

        @Override
        public T call() throws Exception {
            if (!setupGuard.get()) {
                throw new IllegalStateException("Call is not configured");
            } else {
                return delegate.call();
            }
        }

    }

    CompletableSubTask(Executor executor) {
        super(executor, new DelegatingCallable<T>());
    }

    @Override
    Runnable setupTransition(Callable<T> code) {
        DelegatingCallable<T> transitionCall = (DelegatingCallable<T>) action;
        transitionCall.setup(code);
        return task;
    }

    @Override
    protected <U> AbstractCompletableTask<U> createCompletionStage(Executor executor) {
        return new CompletableSubTask<U>(executor);
    }
}
