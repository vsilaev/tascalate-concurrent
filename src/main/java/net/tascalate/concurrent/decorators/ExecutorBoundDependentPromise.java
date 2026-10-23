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
 * A {@link DependentPromise} decorator that binds all asynchronous composition
 * methods to a specific default {@link Executor}.
 * <p>
 * This is the dependent-promise counterpart of {@link ExecutorBoundPromise}.
 * When any {@code *Async} method without an explicit executor parameter is
 * invoked on this decorator, the call is automatically redirected to the
 * overloaded variant that uses the bound {@code defaultExecutor}.
 * </p>
 *
 * <h2>Executor Propagation</h2>
 * <p>
 * The binding is preserved across the entire composition chain:
 * </p>
 * <ul>
 *   <li>Every new stage produced by a composition method is itself wrapped in
 *       an {@code ExecutorBoundDependentPromise} with the same default
 *       executor.</li>
 *   <li>Calling {@link #defaultAsyncOn(Executor)} with the <i>same</i>
 *       executor returns {@code this} (no redundant wrapping); calling it
 *       with a <i>different</i> executor replaces the binding.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DependentPromise<String> p = someDependentPromise.defaultAsyncOn(myExecutor);
 * // All subsequent *Async calls without an explicit executor will use myExecutor:
 * p.thenApplyAsync(fn1, true)          // runs on myExecutor
 *  .thenComposeAsync(fn2, true)        // runs on myExecutor
 *  .whenCompleteAsync(action, true);   // runs on myExecutor
 * }</pre>
 *
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see ExecutorBoundPromise
 * @see AbstractDependentPromiseDecorator
 * @see Promise#defaultAsyncOn(Executor)
 */
public class ExecutorBoundDependentPromise<T> extends AbstractDependentPromiseDecorator<T> {

    /** The executor to which all asynchronous methods are bound. */
    private final Executor defaultExecutor;

    /**
     * Creates a new executor-bound dependent-promise decorator.
     *
     * @param delegate        the {@link DependentPromise} to delegate calls to
     * @param defaultExecutor the executor to use for all asynchronous methods
     *                        that do not specify an explicit executor
     */
    public ExecutorBoundDependentPromise(DependentPromise<T> delegate, Executor defaultExecutor) {
        super(delegate);
        this.defaultExecutor = defaultExecutor;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Preserves the executor binding by wrapping the new stage in another
     * {@code ExecutorBoundDependentPromise} with the same default executor.
     * </p>
     */
    @Override
    protected <U> DependentPromise<U> wrapNew(CompletionStage<U> original) {
        return new ExecutorBoundDependentPromise<>((DependentPromise<U>)original, defaultExecutor);
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
    public DependentPromise<T> defaultAsyncOn(Executor executor) {
        if (executor == defaultExecutor) {
            return this;
        } else {
            return super.defaultAsyncOn(executor);
        }
    }

    // ========================================================================
    // Unary Composition with Enlistment
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return thenApplyAsync(fn, defaultExecutor, enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return thenAcceptAsync(action, defaultExecutor, enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return thenRunAsync(action, defaultExecutor, enlistOrigin);
    }

    // ========================================================================
    // Binary Composition with Enlistment Options
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<Promise.Origin> enlistOptions) {
        return thenCombineAsync(other, fn, defaultExecutor, enlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<Promise.Origin> enlistOptions) {
        return thenAcceptBothAsync(other, action, defaultExecutor, enlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other,
                                                    Runnable action,
                                                    Set<Promise.Origin> enlistOptions) {
        return runAfterBothAsync(other, action, defaultExecutor, enlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                      Function<? super T, U> fn,
                                                      Set<Promise.Origin> enlistOptions) {
        return applyToEitherAsync(other, fn, defaultExecutor, enlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                    Consumer<? super T> action,
                                                    Set<Promise.Origin> enlistOptions) {
        return acceptEitherAsync(other, action, defaultExecutor, enlistOptions);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                      Runnable action,
                                                      Set<Promise.Origin> enlistOptions) {
        return runAfterEitherAsync(other, action, defaultExecutor, enlistOptions);
    }

    // ========================================================================
    // Composition / Error Handling with Enlistment
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return thenComposeAsync(fn, defaultExecutor, enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return exceptionallyAsync(fn, defaultExecutor, enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        return exceptionallyComposeAsync(fn, defaultExecutor, enlistOrigin);
    }

    // ========================================================================
    // Filtering with Enlistment
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, boolean enlistOrigin) {
        return thenFilterAsync(predicate, defaultExecutor, enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate,
                                               Function<? super T, Throwable> errorSupplier,
                                               boolean enlistOrigin) {
        return thenFilterAsync(predicate, errorSupplier, defaultExecutor, enlistOrigin);
    }

    // ========================================================================
    // Completion Handlers with Enlistment
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return whenCompleteAsync(action, defaultExecutor, enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return handleAsync(fn, defaultExecutor, enlistOrigin);
    }

    // ========================================================================
    // Standard Async Methods (No Enlistment Parameter)
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return exceptionallyAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return exceptionallyComposeAsync(fn, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return thenFilterAsync(predicate, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return thenFilterAsync(predicate, errorSupplier, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, defaultExecutor);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, defaultExecutor);
    }
}