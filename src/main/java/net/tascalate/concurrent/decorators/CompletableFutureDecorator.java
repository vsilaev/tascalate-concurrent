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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import net.tascalate.concurrent.Promise;

/**
 * A concrete {@link Promise} decorator that wraps a {@link CompletableFuture}.
 * <p>
 * This class extends {@link AbstractFutureDecorator} to provide a ready-to-use
 * wrapper for {@link CompletableFuture} instances. It is the foundation for
 * several other decorators in this package (such as {@link ExecutorBoundPromise}
 * and {@link BlockingCompletionStageDecorator}) that need to adapt a
 * {@code CompletableFuture} to the {@link Promise} API.
 * </p>
 * <p>
 * Unlike {@link net.tascalate.concurrent.CompletableFutureWrapper} (which owns
 * its own {@code CompletableFuture} and can be manually completed), this
 * decorator wraps an <i>existing</i> {@code CompletableFuture} provided by
 * external code.
 * </p>
 * <h2>Class Hierarchy</h2>
 * <pre>{@code
 * AbstractCompletionStageDecorator
 *     ├── CompletionStageDecorator
 *     │       └── ExecutorBoundCompletionStage
 *     └── AbstractPromiseLikeDecorator
 *             └── AbstractFutureDecorator
 *                     ├── CompletableFutureDecorator  <-- YOU ARE HERE
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
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see AbstractFutureDecorator
 * @see ExecutorBoundPromise
 * @see BlockingCompletionStageDecorator
 */
public class CompletableFutureDecorator<T>
    extends AbstractFutureDecorator<T, CompletableFuture<T>>
    implements Promise<T> {

    /**
     * Creates a new decorator wrapping a newly created, uncompleted
     * {@link CompletableFuture}.
     * <p>
     * This constructor is intended for subclasses that need to create a fresh
     * {@code CompletableFuture} and complete it manually (e.g.,
     * {@link net.tascalate.concurrent.CompletablePromise}).
     * </p>
     */
    protected CompletableFutureDecorator() {
        this(new CompletableFuture<T>());
    }

    /**
     * Creates a new decorator wrapping the specified {@link CompletableFuture}.
     *
     * @param delegate the {@link CompletableFuture} to delegate calls to
     */
    protected CompletableFutureDecorator(CompletableFuture<T> delegate) {
        super(delegate);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Wraps the given {@link CompletionStage} in a new {@code CompletableFutureDecorator},
     * assuming it is a {@link CompletableFuture}.
     * </p>
     */
    @Override
    protected <U> Promise<U> wrapNew(CompletionStage<U> original) {
        return new CompletableFutureDecorator<>((CompletableFuture<U>)original);
    }

    /** {@inheritDoc} */
    @Override
    public T getNow(T valueIfAbsent) {
        return delegate.getNow(valueIfAbsent);
    }

    /** {@inheritDoc} */
    @Override
    public T join() throws CancellationException, CompletionException {
        return delegate.join();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCompletedExceptionally() {
        return delegate.isCompletedExceptionally();
    }
}