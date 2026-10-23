/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.decorators;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.core.CompletionStageAPI;

/**
 * Abstract base class that adds the blocking {@link Future} API to a
 * {@link CompletionStage} decorator.
 * <p>
 * This class extends {@link AbstractPromiseLikeDecorator} and implements
 * {@link Future}. It is designed to decorate types that implement <i>both</i>
 * {@link CompletionStage} and {@link Future} (such as {@link CompletableFuture}
 * or {@link Promise}). It delegates all blocking operations ({@code get},
 * {@code cancel}, etc.) and state inspection methods directly to the underlying
 * delegate.
 * </p>
 *
 * <h2>Role in the Decorator Hierarchy</h2>
 * <p>
 * This class sits between {@link AbstractPromiseLikeDecorator} (which bridges
 * {@link CompletionStage} to {@link Promise}) and the concrete decorators that
 * need blocking access:
 * </p>
 * <h2>Class Hierarchy</h2>
 * <pre>{@code
 * AbstractCompletionStageDecorator
 *     ├── CompletionStageDecorator
 *     │       └── ExecutorBoundCompletionStage
 *     └── AbstractPromiseLikeDecorator
 *             └── AbstractFutureDecorator  <-- YOU ARE HERE (Adds Future blocking API)
 *                     ├── CompletableFutureDecorator
 *                     └── BlockingCompletionStageDecorator
 *
 * AbstractPromiseDecorator
 *     ├── ExecutorBoundPromise
 *     ├── ExtendedPromiseDecorator
 *     │       └── CustomizablePromiseDecorator
 *     └── AbstractDependentPromiseDecorator
 *             ├── ExecutorBoundDependentPromise
 *             └── ExtendedDependentPromiseDecorator
 *                     └── CustomizableDependentPromiseDecorator
 * }</pre> 
 *
 * @param <T> the type of the successfully resolved promise value
 * @param <D> the type of the concrete {@link CompletionStage} + {@link Future}
 *            subclass being decorated
 * @author vsilaev
 * @see AbstractPromiseLikeDecorator
 * @see AbstractPromiseDecorator
 * @see CompletableFutureDecorator
 * @see BlockingCompletionStageDecorator
 */
abstract public class AbstractFutureDecorator<T, D extends CompletionStage<T> & Future<T>>
    extends AbstractPromiseLikeDecorator<T, D>
    implements CompletionStage<T>, Future<T> {

    /**
     * Creates a new future decorator wrapping the specified delegate.
     *
     * @param delegate the {@link CompletionStage} and {@link Future} to delegate
     *                 calls to
     */
    protected AbstractFutureDecorator(D delegate) {
        super(delegate);
    }

    /** {@inheritDoc} */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return delegate.cancel(mayInterruptIfRunning);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    /** {@inheritDoc} */
    @Override
    public T get() throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    /** {@inheritDoc} */
    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get(timeout, unit);
    }

    /**
     * Checks if the underlying delegate is currently in the specified
     * {@link Promise.State}.
     *
     * @param state the state to check
     * @return {@code true} if the delegate is in the given state
     */
    public boolean isIn(Promise.State state) {
        return CompletionStageAPI.current().isInState(delegate, state, true);
    }

    /**
     * Checks if the underlying delegate is currently in any of the specified
     * states.
     *
     * @param states the set of states to check
     * @return {@code true} if the delegate is in any of the given states
     */
    public boolean isIn(Set<Promise.State> states) {
        return CompletionStageAPI.current().isInState(delegate, states, true);
    }

    /**
     * Returns the result value if the underlying delegate has completed
     * successfully.
     *
     * @return the result value
     * @throws IllegalStateException if the task has not completed, was
     *                               cancelled, or completed exceptionally
     */
    public T resultNow() {
        return CompletionStageAPI.current().resultNow(delegate, true);
    }

    /**
     * Returns the exception if the underlying delegate has completed
     * exceptionally.
     *
     * @return the exception
     * @throws IllegalStateException if the task has not completed, was
     *                               cancelled, or completed successfully
     */
    public Throwable exceptionNow() {
        return CompletionStageAPI.current().exceptionNow(delegate, true);
    }
}