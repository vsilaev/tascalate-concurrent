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

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.Promise;

/**
 * A {@link DependentPromise} decorator that combines the extension-point
 * capabilities of {@link ExtendedPromiseDecorator} with the dependent-promise
 * API of {@link AbstractDependentPromiseDecorator}.
 * <p>
 * This class overrides every composition method in {@link DependentPromise} to
 * route arguments through protected {@code wrapArgument(...)} methods, allowing
 * subclasses to intercept and transform callbacks, stages, and executors. It
 * preserves the dependent-promise API (enlistment options for cancellation
 * propagation) throughout the decoration chain.
 * </p>
 *
 * <h2>Use Cases</h2>
 * <p>
 * This decorator is particularly useful when you need to:
 * </p>
 * <ul>
 *   <li>Propagate context (e.g., thread-local, security, tracing) across
 *       dependent-promise chains;</li>
 *   <li>Apply blocking I/O interruption to all callbacks in a dependent
 *       chain;</li>
 *   <li>Log or metric all composition steps while preserving cancellation
 *       semantics.</li>
 * </ul>
 *
 * <h2>Extension Points</h2>
 * <p>
 * All {@code wrapArgument(...)} methods are inherited from the extended
 * decorator pattern. Subclasses override only the methods relevant to their
 * use case; the default implementations return arguments unchanged.
 * </p>
 * <h2>Class Hierarchy</h2>
 * <pre>{@code
 * AbstractCompletionStageDecorator
 *     ├── CompletionStageDecorator
 *     │       └── ExecutorBoundCompletionStage
 *     └── AbstractPromiseLikeDecorator
 *             └── AbstractFutureDecorator
 *                     ├── CompletableFutureDecorator
 *                     └── BlockingCompletionStageDecorator
 *
 * AbstractPromiseDecorator
 *     ├── ExecutorBoundPromise
 *     ├── ExtendedPromiseDecorator
 *     │       └── CustomizablePromiseDecorator
 *     └── AbstractDependentPromiseDecorator
 *             ├── ExecutorBoundDependentPromise
 *             └── ExtendedDependentPromiseDecorator  <-- YOU ARE HERE
 *                     └── CustomizableDependentPromiseDecorator
 * }</pre>
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see ExtendedPromiseDecorator
 * @see AbstractDependentPromiseDecorator
 * @see DependentPromise
 */
public class ExtendedDependentPromiseDecorator<T> extends AbstractDependentPromiseDecorator<T> {

    /**
     * Creates a new extended dependent-promise decorator wrapping the specified
     * delegate.
     *
     * @param delegate the {@link DependentPromise} to delegate calls to
     */
    protected ExtendedDependentPromiseDecorator(DependentPromise<T> delegate) {
        super(delegate);
    }

    // ========================================================================
    // Argument Wrapping Extension Points
    // ========================================================================

    /**
     * Wraps a {@link Runnable} argument before it is passed to the delegate.
     * <p>
     * The default implementation returns the original action unchanged.
     * </p>
     *
     * @param original the original action
     * @param async    {@code true} if the action will be executed asynchronously
     * @return the (possibly wrapped) action
     */
    protected Runnable wrapArgument(Runnable original, boolean async) {
        return original;
    }

    /**
     * Internal overload for {@link Function} wrapping without the
     * {@code isCompose} flag; defaults to {@code isCompose = false}.
     */
    private <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async) {
        return wrapArgument(original, async, false);
    }

    /**
     * Wraps a {@link Function} argument before it is passed to the delegate.
     *
     * @param <U>       the input type of the function
     * @param <R>       the result type of the function
     * @param original  the original function
     * @param async     {@code true} if the function will be executed asynchronously
     * @param isCompose {@code true} if this is a flatMap-style operation
     * @return the (possibly wrapped) function
     */
    protected <U, R> Function<U, R> wrapArgument(Function<U, R> original, boolean async, boolean isCompose) {
        return original;
    }

    /**
     * Wraps a {@link Consumer} argument before it is passed to the delegate.
     *
     * @param <U>       the input type of the consumer
     * @param original  the original consumer
     * @param async     {@code true} if the consumer will be executed asynchronously
     * @return the (possibly wrapped) consumer
     */
    protected <U> Consumer<U> wrapArgument(Consumer<U> original, boolean async) {
        return original;
    }

    /**
     * Wraps a {@link Supplier} argument before it is passed to the delegate.
     *
     * @param <U>       the result type of the supplier
     * @param original  the original supplier
     * @param async     {@code true} if the supplier will be executed asynchronously
     * @return the (possibly wrapped) supplier
     */
    protected <U> Supplier<U> wrapArgument(Supplier<U> original, boolean async) {
        return original;
    }

    /**
     * Wraps a {@link Predicate} argument before it is passed to the delegate.
     *
     * @param <U>       the input type of the predicate
     * @param original  the original predicate
     * @param async     {@code true} if the predicate will be executed asynchronously
     * @return the (possibly wrapped) predicate
     */
    protected <U> Predicate<U> wrapArgument(Predicate<U> original, boolean async) {
        return original;
    }

    /**
     * Wraps a {@link BiFunction} argument before it is passed to the delegate.
     *
     * @param <U>       the first input type
     * @param <V>       the second input type
     * @param <R>       the result type
     * @param original  the original bi-function
     * @param async     {@code true} if the bi-function will be executed asynchronously
     * @return the (possibly wrapped) bi-function
     */
    protected <U, V, R> BiFunction<U, V, R> wrapArgument(BiFunction<U, V, R> original, boolean async) {
        return original;
    }

    /**
     * Wraps a {@link BiConsumer} argument before it is passed to the delegate.
     *
     * @param <U>       the first input type
     * @param <V>       the second input type
     * @param original  the original bi-consumer
     * @param async     {@code true} if the bi-consumer will be executed asynchronously
     * @return the (possibly wrapped) bi-consumer
     */
    protected <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original, boolean async) {
        return original;
    }

    /**
     * Wraps a secondary {@link CompletionStage} argument before it is passed
     * to a binary combinator.
     *
     * @param <U>       the result type of the stage
     * @param original  the original stage
     * @param async     {@code true} if the combinator will be executed asynchronously
     * @return the (possibly wrapped) stage
     */
    protected <U> CompletionStage<U> wrapArgument(CompletionStage<U> original, boolean async) {
        return original;
    }

    /**
     * Wraps an {@link Executor} argument before it is passed to the delegate.
     *
     * @param original the original executor
     * @return the (possibly wrapped) executor
     */
    protected Executor wrapArgument(Executor original) {
        return original;
    }

    // ========================================================================
    // Result Wrapping
    // ========================================================================

    /**
     * Wraps a result {@link CompletionStage} produced by a composition method.
     * <p>
     * The default implementation creates a new
     * {@code ExtendedDependentPromiseDecorator} around the result, preserving
     * both the extended decoration behavior and the dependent-promise API.
     * </p>
     *
     * @param <U>      the result type of the stage
     * @param original the result stage
     * @return the wrapped result
     */
    protected <U> DependentPromise<U> wrapResult(CompletionStage<U> original) {
        return new ExtendedDependentPromiseDecorator<>((DependentPromise<U>)original);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to {@link #wrapResult(CompletionStage)}.
     * </p>
     */
    @Override
    protected final <U> DependentPromise<U> wrapNew(CompletionStage<U> original) {
        return wrapResult(original);
    }

    // ========================================================================
    // Lifecycle & Timeout Methods
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> onCancel(Runnable code) {
        return super.onCancel(wrapArgument(code, false));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit,
                                         boolean cancelOnTimeout, boolean enlistOrigin) {
        return super.onTimeout(wrapArgument(supplier, true), timeout, unit, cancelOnTimeout, enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration,
                                         boolean cancelOnTimeout, boolean enlistOrigin) {
        return super.onTimeout(wrapArgument(supplier, true), duration, cancelOnTimeout, enlistOrigin);
    }

    // ========================================================================
    // Unary Composition with Enlistment
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return super.thenApply(wrapArgument(fn, false), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return super.thenApplyAsync(wrapArgument(fn, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn,
                                                  Executor executor,
                                                  boolean enlistOrigin) {
        return super.thenApplyAsync(wrapArgument(fn, true), wrapArgument(executor), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin) {
        return super.thenAccept(wrapArgument(action, false), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return super.thenAcceptAsync(wrapArgument(action, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, 
                                                  Executor executor, 
                                                  boolean enlistOrigin) {
        return super.thenAcceptAsync(wrapArgument(action, true), wrapArgument(executor), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin) {
        return super.thenRun(wrapArgument(action, false), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return super.thenRunAsync(wrapArgument(action, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, 
                                               Executor executor, 
                                               boolean enlistOrigin) {
        return super.thenRunAsync(wrapArgument(action, true), wrapArgument(executor), enlistOrigin);
    }

    // ========================================================================
    // Binary Composition with Enlistment Options
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other,
                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                  Set<Promise.Origin> enlistOptions) {
        return super.thenCombine(
            wrapArgument(other, false), wrapArgument(fn, false), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<Promise.Origin> enlistOptions) {
        return super.thenCombineAsync(
            wrapArgument(other, true), wrapArgument(fn, true), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Executor executor,
                                                       Set<Promise.Origin> enlistOptions) {
        return super.thenCombineAsync(
            wrapArgument(other, true), wrapArgument(fn, true), wrapArgument(executor), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                                                     BiConsumer<? super T, ? super U> action,
                                                     Set<Promise.Origin> enlistOptions) {
        return super.thenAcceptBoth(
            wrapArgument(other, false), wrapArgument(action, false), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<Promise.Origin> enlistOptions) {
        return super.thenAcceptBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action,
                                                          Executor executor,
                                                          Set<Promise.Origin> enlistOptions) {
        return super.thenAcceptBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other,
                                               Runnable action,
                                               Set<Promise.Origin> enlistOptions) {
        return super.runAfterBoth(
            wrapArgument(other, false), wrapArgument(action, false), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other,
                                                    Runnable action,
                                                    Set<Promise.Origin> enlistOptions) {
        return super.runAfterBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other,
                                                    Runnable action,
                                                    Executor executor,
                                                    Set<Promise.Origin> enlistOptions) {
        return super.runAfterBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other,
                                                 Function<? super T, U> fn,
                                                 Set<Promise.Origin> enlistOptions) {
        return super.applyToEither(
            wrapArgument(other, false), wrapArgument(fn, false), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                      Function<? super T, U> fn,
                                                      Set<Promise.Origin> enlistOptions) {
        return super.applyToEitherAsync(
            wrapArgument(other, true), wrapArgument(fn, true), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                      Function<? super T, U> fn,
                                                      Executor executor,
                                                      Set<Promise.Origin> enlistOptions) {
        return super.applyToEitherAsync(
            wrapArgument(other, true), wrapArgument(fn, true), wrapArgument(executor), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other,
                                               Consumer<? super T> action,
                                               Set<Promise.Origin> enlistOptions) {
        return super.acceptEither(
            wrapArgument(other, false), wrapArgument(action, false), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                    Consumer<? super T> action,
                                                    Set<Promise.Origin> enlistOptions) {
        return super.acceptEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                    Consumer<? super T> action,
                                                    Executor executor,
                                                    Set<Promise.Origin> enlistOptions) {
        return super.acceptEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other,
                                                 Runnable action,
                                                 Set<Promise.Origin> enlistOptions) {
        return super.runAfterEither(
            wrapArgument(other, false), wrapArgument(action, false), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                      Runnable action,
                                                      Set<Promise.Origin> enlistOptions) {
        return super.runAfterEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), enlistOptions
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                      Runnable action,
                                                      Executor executor,
                                                      Set<Promise.Origin> enlistOptions) {
        return super.runAfterEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), executor, enlistOptions
        );
    }

    // ========================================================================
    // Composition / Error Handling with Enlistment
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return super.thenCompose(wrapArgument(fn, false, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return super.thenComposeAsync(wrapArgument(fn, true, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn,
                                                    Executor executor,
                                                    boolean enlistOrigin) {
        return super.thenComposeAsync(wrapArgument(fn, true, true), wrapArgument(executor), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return super.exceptionally(wrapArgument(fn, false), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, boolean enlistOrigin) {
        return super.exceptionallyAsync(wrapArgument(fn, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn,
                                                  Executor executor,
                                                  boolean enlistOrigin) {
        return super.exceptionallyAsync(wrapArgument(fn, true), executor, enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        return super.exceptionallyCompose(wrapArgument(fn, false, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin) {
        return super.exceptionallyComposeAsync(wrapArgument(fn, true, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn,
                                                         Executor executor,
                                                         boolean enlistOrigin) {
        return super.exceptionallyComposeAsync(wrapArgument(fn, true, true), executor, enlistOrigin);
    }

    // ========================================================================
    // Filtering with Enlistment
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, boolean enlistOrigin) {
        return super.thenFilter(wrapArgument(predicate, false), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate,
                                          Function<? super T, Throwable> errorSupplier,
                                          boolean enlistOrigin) {
        return super.thenFilter(wrapArgument(predicate, false), wrapArgument(errorSupplier, false), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, boolean enlistOrigin) {
        return super.thenFilterAsync(wrapArgument(predicate, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate,
                                               Function<? super T, Throwable> errorSupplier,
                                               boolean enlistOrigin) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(errorSupplier, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor, boolean enlistOrigin) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(executor), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate,
                                               Function<? super T, Throwable> errorSupplier,
                                               Executor executor,
                                               boolean enlistOrigin) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(errorSupplier, true), wrapArgument(executor), enlistOrigin);
    }

    // ========================================================================
    // Completion Handlers with Enlistment
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return super.whenComplete(wrapArgument(action, false), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return super.whenCompleteAsync(wrapArgument(action, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action,
                                                 Executor executor,
                                                 boolean enlistOrigin) {
        return super.whenCompleteAsync(wrapArgument(action, true), wrapArgument(executor), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return super.handle(wrapArgument(fn, false), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return super.handleAsync(wrapArgument(fn, true), enlistOrigin);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn,
                                               Executor executor,
                                               boolean enlistOrigin) {
        return super.handleAsync(wrapArgument(fn, true), wrapArgument(executor), enlistOrigin);
    }

    // ========================================================================
    // Standard Async Methods (No Enlistment Parameter)
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit) {
        return super.onTimeout(wrapArgument(supplier, true), timeout, unit);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return super.onTimeout(wrapArgument(supplier, true), timeout, unit, cancelOnTimeout);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return super.onTimeout(wrapArgument(supplier, true), duration);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return super.onTimeout(wrapArgument(supplier, true), duration, cancelOnTimeout);
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn) {
        return super.thenApply(wrapArgument(fn, false));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return super.thenApplyAsync(wrapArgument(fn, true));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return super.thenApplyAsync(wrapArgument(fn, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action) {
        return super.thenAccept(wrapArgument(action, false));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return super.thenAcceptAsync(wrapArgument(action, true));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return super.thenAcceptAsync(wrapArgument(action, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRun(Runnable action) {
        return super.thenRun(wrapArgument(action, false));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action) {
        return super.thenRunAsync(wrapArgument(action, true));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, Executor executor) {
        return super.thenRunAsync(wrapArgument(action, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other,
                                                  BiFunction<? super T, ? super U, ? extends V> fn) {
        return super.thenCombine(
            wrapArgument(other, false), wrapArgument(fn, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn) {
        return super.thenCombineAsync(
            wrapArgument(other, true), wrapArgument(fn, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Executor executor) {
        return super.thenCombineAsync(
            wrapArgument(other, true), wrapArgument(fn, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                                                     BiConsumer<? super T, ? super U> action) {
        return super.thenAcceptBoth(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action) {
        return super.thenAcceptBothAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                          BiConsumer<? super T, ? super U> action,
                                                          Executor executor) {
        return super.thenAcceptBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return super.runAfterBoth(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return super.runAfterBothAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other,
                                                    Runnable action,
                                                    Executor executor) {
        return super.runAfterBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other,
                                                 Function<? super T, U> fn) {
        return super.applyToEither(
            wrapArgument(other, false), wrapArgument(fn, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                      Function<? super T, U> fn) {
        return super.applyToEitherAsync(
            wrapArgument(other, true), wrapArgument(fn, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                      Function<? super T, U> fn,
                                                      Executor executor) {
        return super.applyToEitherAsync(
            wrapArgument(other, true), wrapArgument(fn, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other,
                                               Consumer<? super T> action) {
        return super.acceptEither(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                    Consumer<? super T> action) {
        return super.acceptEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                    Consumer<? super T> action,
                                                    Executor executor) {
        return super.acceptEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other,
                                                 Runnable action) {
        return super.runAfterEither(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                      Runnable action) {
        return super.runAfterEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                      Runnable action,
                                                      Executor executor) {
        return super.runAfterEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return super.thenCompose(wrapArgument(fn, false, true));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return super.thenComposeAsync(wrapArgument(fn, true, true));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return super.thenComposeAsync(wrapArgument(fn, true, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return super.exceptionally(wrapArgument(fn, false));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return super.exceptionallyAsync(wrapArgument(fn, true));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return super.exceptionallyAsync(wrapArgument(fn, true), executor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return super.exceptionallyCompose(wrapArgument(fn, false, true));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return super.exceptionallyComposeAsync(wrapArgument(fn, true, true));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return super.exceptionallyComposeAsync(wrapArgument(fn, true, true), executor);
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate) {
        return super.thenFilter(wrapArgument(predicate, false));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return super.thenFilter(wrapArgument(predicate, false), wrapArgument(errorSupplier, false));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return super.thenFilterAsync(wrapArgument(predicate, true));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(errorSupplier, true));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate,
                                               Function<? super T, Throwable> errorSupplier,
                                               Executor executor) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(errorSupplier, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return super.whenComplete(wrapArgument(action, false));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return super.whenCompleteAsync(wrapArgument(action, true));
    }

    /** {@inheritDoc} */
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return super.whenCompleteAsync(wrapArgument(action, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return super.handle(wrapArgument(fn, false));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return super.handleAsync(wrapArgument(fn, true));
    }

    /** {@inheritDoc} */
    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return super.handleAsync(wrapArgument(fn, true), wrapArgument(executor));
    }
}