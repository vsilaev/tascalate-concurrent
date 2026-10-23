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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.Promise;

/**
 * A {@link Promise} decorator that binds all asynchronous composition methods
 * to a specific default {@link Executor}.
 * <p>
 * This is the {@link Promise}-level counterpart of
 * {@link ExecutorBoundCompletionStage}. When any {@code *Async} method without
 * an explicit executor parameter is invoked on this decorator, the call is
 * automatically redirected to the overloaded variant that uses the bound
 * {@code defaultExecutor}.
 * </p>
 *
 * <h2>Executor Propagation</h2>
 * <p>
 * The binding is preserved across the entire composition chain:
 * </p>
 * <ul>
 *   <li>Every new stage produced by a composition method is itself wrapped in
 *       an {@code ExecutorBoundPromise} with the same default executor.</li>
 *   <li>The {@link #dependent()} methods return an
 *       {@link ExecutorBoundDependentPromise} that also preserves the executor
 *       binding, so dependent cancellation chains continue to use the same
 *       executor.</li>
 *   <li>Calling {@link #defaultAsyncOn(Executor)} with the <i>same</i> executor
 *       returns {@code this} (no redundant wrapping); calling it with a
 *       <i>different</i> executor replaces the binding.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Promise<String> p = somePromise.defaultAsyncOn(myExecutor);
 * // All subsequent *Async calls without an explicit executor will use myExecutor:
 * p.thenApplyAsync(fn1)       // runs on myExecutor
 *  .thenComposeAsync(fn2)     // runs on myExecutor
 *  .whenCompleteAsync(action); // runs on myExecutor
 * }</pre>
 *
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see ExecutorBoundCompletionStage
 * @see ExecutorBoundDependentPromise
 * @see Promise#defaultAsyncOn(Executor)
 */
public class ExecutorBoundPromise<T> extends AbstractPromiseDecorator<T, Promise<T>> {

    /** The executor to which all asynchronous methods are bound. */
    private final Executor defaultExecutor;

    /**
     * Creates a new executor-bound promise decorator.
     *
     * @param delegate        the {@link Promise} to delegate calls to
     * @param defaultExecutor the executor to use for all asynchronous methods
     *                        that do not specify an explicit executor
     */
    public ExecutorBoundPromise(Promise<T> delegate, Executor defaultExecutor) {
        super(delegate);
        this.defaultExecutor = defaultExecutor;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Preserves the executor binding by wrapping the new stage in another
     * {@code ExecutorBoundPromise} with the same default executor.
     * </p>
     */
    @Override
    protected <U> Promise<U> wrapNew(CompletionStage<U> original) {
        return new ExecutorBoundPromise<>((Promise<U>)original, defaultExecutor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Wraps the resulting {@link DependentPromise} in an
     * {@link ExecutorBoundDependentPromise} to preserve the executor binding
     * across dependent cancellation chains.
     * </p>
     */
    @Override
    public DependentPromise<T> dependent() {
        return new ExecutorBoundDependentPromise<>(super.dependent(), defaultExecutor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Wraps the resulting {@link DependentPromise} in an
     * {@link ExecutorBoundDependentPromise} to preserve the executor binding
     * across dependent cancellation chains.
     * </p>
     */
    @Override
    public DependentPromise<T> dependent(Set<Promise.Origin> defaultEnlistOptions) {
        return new ExecutorBoundDependentPromise<>(super.dependent(defaultEnlistOptions), defaultExecutor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the supplied executor is the <i>same</i> as the currently bound
     * executor, returns {@code this} to avoid redundant wrapping. Otherwise,
     * delegates to the superclass to replace the binding.
     * </p>
     */
    @Override
    public Promise<T> defaultAsyncOn(Executor executor) {
        if (executor == defaultExecutor) {
            return this;
        } else {
            return super.defaultAsyncOn(executor);
        }
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                             Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                           Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return exceptionallyAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return exceptionallyComposeAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return thenFilterAsync(predicate, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate,
                                      Function<? super T, Throwable> errorSupplier) {
        return thenFilterAsync(predicate, errorSupplier, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, defaultExecutor);
    }
}