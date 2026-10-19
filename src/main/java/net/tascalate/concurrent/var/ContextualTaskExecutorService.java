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
package net.tascalate.concurrent.var;

import java.util.concurrent.Callable;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.TaskExecutorService;

/**
 * A {@link TaskExecutorService} decorator that propagates relayed context variables
 * to all submitted tasks, returning {@link Promise} handles instead of plain futures.
 * <p>
 * <b>Dynamic resolution:</b> inherited from {@link ContextualExecutorService} — the context
 * snapshot is taken at the moment of each {@code submit(...)}, {@code invokeAll(...)} and
 * {@code invokeAny(...)} call, so every task inherits the context that is active in the
 * submitting thread at its submission time.
 * </p>
 * <p>
 * The {@code submit} overloads narrow the {@code Future} return type of the superclass to
 * {@code Promise}; this cast is safe because the wrapped delegate is a
 * {@link TaskExecutorService}, whose {@code submit} methods produce promises.
 * </p>
 *
 * @param <D> the type of the delegate task executor service
 * @see ContextRelay#contextual(TaskExecutorService)
 * @see ContextualExecutorService
 */
class ContextualTaskExecutorService<D extends TaskExecutorService>
        extends ContextualExecutorService<D>
        implements TaskExecutorService {

    /**
     * Creates a decorator for the given task executor service.
     *
     * @param delegate the task executor service to wrap
     * @param ctxRelay the relay capturing context on each submission
     */
    ContextualTaskExecutorService(D delegate, ContextRelay<?> ctxRelay) {
        super(delegate, ctxRelay);
    }

    /**
     * Submits the task wrapped with the context snapshot captured <b>at the moment
     * of this call</b>.
     *
     * @param task the task to submit
     * @param <T>  the result type of the task
     * @return a promise representing the pending result
     */
    @Override
    public <T> Promise<T> submit(Callable<T> task) {
        return (Promise<T>)super.submit(task);
    }

    /**
     * Submits the task wrapped with the context snapshot captured <b>at the moment
     * of this call</b>.
     *
     * @param task   the task to submit
     * @param result the result to return upon completion
     * @param <T>    the result type
     * @return a promise representing the pending result
     */
    @Override
    public <T> Promise<T> submit(Runnable task, T result) {
        return (Promise<T>)super.submit(task, result);
    }

    /**
     * Submits the task wrapped with the context snapshot captured <b>at the moment
     * of this call</b>.
     *
     * @param task the task to submit
     * @return a promise representing the pending completion
     */
    @Override
    public Promise<?> submit(Runnable task) {
        return (Promise<?>)super.submit(task);
    }
}