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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link CompletionStage} decorator that binds all asynchronous composition
 * methods to a specific default {@link Executor}.
 * <p>
 * This package-private class is the {@link CompletionStage}-level counterpart of
 * {@link ExecutorBoundPromise}. When any {@code *Async} method without an explicit
 * executor parameter is invoked on this decorator, the call is automatically
 * redirected to the overloaded variant that uses the bound {@code defaultExecutor}.
 * </p>
 * <p>
 * The binding is preserved across the composition chain: every new stage produced
 * by a composition method is itself wrapped in an {@code ExecutorBoundCompletionStage}
 * with the same default executor, ensuring consistent execution semantics
 * throughout the pipeline.
 * </p>
 *
 * @param <T> the type of the successfully resolved stage value
 * @author vsilaev
 * @see ExecutorBoundPromise
 * @see CompletionStageDecorator
 */
class ExecutorBoundCompletionStage<T> extends CompletionStageDecorator<T> {

    /** The executor to which all asynchronous methods are bound. */
    private final Executor defaultExecutor;

    /**
     * Creates a new executor-bound decorator.
     *
     * @param delegate       the {@link CompletionStage} to delegate calls to
     * @param defaultExecutor the executor to use for all asynchronous methods
     *                        that do not specify an explicit executor
     */
    ExecutorBoundCompletionStage(CompletionStage<T> delegate, Executor defaultExecutor) {
        super(delegate);
        this.defaultExecutor = defaultExecutor;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Preserves the executor binding by wrapping the new stage in another
     * {@code ExecutorBoundCompletionStage} with the same default executor.
     * </p>
     */
    @Override
    protected <U> CompletionStage<U> wrapNew(CompletionStage<U> original) {
        return new ExecutorBoundCompletionStage<>(original, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                      BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                         BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                     Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                   Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public CompletionStage<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return exceptionallyAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public CompletionStage<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return exceptionallyComposeAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, defaultExecutor);
    }
}