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
 * A {@link Promise} decorator that provides extension points for intercepting
 * and transforming all arguments passed to composition methods.
 * <p>
 * This class overrides every composition method in {@link Promise} to route
 * arguments through protected {@code wrapArgument(...)} methods. Subclasses
 * can override these wrap methods to apply transformations such as:
 * </p>
 * <ul>
 *   <li>Context propagation (e.g., wrapping callbacks to restore thread-local
 *       context before execution);</li>
 *   <li>Executor substitution;</li>
 *   <li>Logging, metrics, or tracing;</li>
 *   <li>Blocking I/O interruption (see
 *       {@link net.tascalate.concurrent.io.BlockingIO}).</li>
 * </ul>
 * <p>
 * The default implementations of all {@code wrapArgument(...)} methods return
 * the original argument unchanged, so subclasses only need to override the
 * specific wrap methods relevant to their use case.
 * </p>
 * <p>
 * Result stages produced by composition methods are wrapped via
 * {@link #wrapResult(CompletionStage)}, which by default creates a new
 * {@code ExtendedPromiseDecorator} around the result, preserving the
 * decoration chain.
 * </p>
 *
 * <h2>Extension Points</h2>
 * <ul>
 *   <li>{@link #wrapArgument(Runnable, boolean)} — intercept {@code Runnable}
 *       callbacks ({@code thenRun}, {@code whenComplete}, etc.).</li>
 *   <li>{@link #wrapArgument(Function, boolean, boolean)} — intercept
 *       {@code Function} callbacks ({@code thenApply}, {@code handle}, etc.).
 *       The {@code isCompose} flag distinguishes flatMap-style operations
 *       ({@code thenCompose}) from map-style operations.</li>
 *   <li>{@link #wrapArgument(Consumer, boolean)},
 *       {@link #wrapArgument(BiConsumer, boolean)},
 *       {@link #wrapArgument(BiFunction, boolean)},
 *       {@link #wrapArgument(Supplier, boolean)},
 *       {@link #wrapArgument(Predicate, boolean)} — intercept other callback
 *       types.</li>
 *   <li>{@link #wrapArgument(CompletionStage, boolean)} — intercept secondary
 *       stages passed to binary combinators ({@code thenCombine},
 *       {@code applyToEither}, etc.).</li>
 *   <li>{@link #wrapArgument(Executor)} — intercept explicit executor
 *       arguments.</li>
 *   <li>{@link #wrapResult(CompletionStage)} — transform result stages.</li>
 * </ul>
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
 *     ├── ExtendedPromiseDecorator  <-- YOU ARE HERE (Provides wrapArgument extension points)
 *     │       └── CustomizablePromiseDecorator
 *     └── AbstractDependentPromiseDecorator
 *             ├── ExecutorBoundDependentPromise
 *             └── ExtendedDependentPromiseDecorator
 *                     └── CustomizableDependentPromiseDecorator
 * }</pre>
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see ExtendedDependentPromiseDecorator
 * @see AbstractPromiseDecorator
 */
public class ExtendedPromiseDecorator<T> extends AbstractPromiseDecorator<T, Promise<T>> {

    /**
     * Creates a new extended decorator wrapping the specified delegate.
     *
     * @param delegate the {@link Promise} to delegate calls to
     */
    public ExtendedPromiseDecorator(Promise<T> delegate) {
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
     *                 (i.e., the {@code *Async} variant of the method was called)
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
     * <p>
     * The default implementation returns the original function unchanged.
     * </p>
     *
     * @param <U>       the input type of the function
     * @param <R>       the result type of the function
     * @param original  the original function
     * @param async     {@code true} if the function will be executed asynchronously
     * @param isCompose {@code true} if this is a flatMap-style operation
     *                  ({@code thenCompose}, {@code exceptionallyCompose});
     *                  {@code false} for map-style operations
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
     * to a binary combinator (e.g., {@code thenCombine}, {@code applyToEither}).
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
     * <p>
     * Subclasses can use this to substitute or decorate the executor used for
     * asynchronous execution.
     * </p>
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
     * The default implementation creates a new {@code ExtendedPromiseDecorator}
     * around the result, preserving the decoration chain. Subclasses can
     * override this to use a different decorator type.
     * </p>
     *
     * @param <U>      the result type of the stage
     * @param original the result stage
     * @return the wrapped result
     */
    protected <U> Promise<U> wrapResult(CompletionStage<U> original) {
        return new ExtendedPromiseDecorator<>((Promise<U>)original);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to {@link #wrapResult(CompletionStage)}.
     * </p>
     */
    @Override
    protected final <U> Promise<U> wrapNew(CompletionStage<U> original) {
        return wrapResult(original);
    }

    // ========================================================================
    // Dependent Promise Overrides
    // ========================================================================

    /**
     * {@inheritDoc}
     * <p>
     * Wraps the result in an {@link ExtendedDependentPromiseDecorator} to
     * preserve the extended decoration behavior across dependent chains.
     * </p>
     */
    @Override
    public DependentPromise<T> dependent() {
        return new ExtendedDependentPromiseDecorator<>(
            delegate.dependent()
        );
    }

    /**
     * {@inheritDoc}
     * <p>
     * Wraps the result in an {@link ExtendedDependentPromiseDecorator} to
     * preserve the extended decoration behavior across dependent chains.
     * </p>
     */
    @Override
    public DependentPromise<T> dependent(Set<Promise.Origin> defaultEnlistOptions) {
        return new ExtendedDependentPromiseDecorator<>(
            delegate.dependent(defaultEnlistOptions)
        );
    }

    // ========================================================================
    // Lifecycle & Timeout Methods
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public Promise<T> onCancel(Runnable code) {
        return super.onCancel(wrapArgument(code, false));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit) {
        return super.onTimeout(wrapArgument(supplier, true), timeout, unit);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return super.onTimeout(wrapArgument(supplier, true), timeout, unit, cancelOnTimeout);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return super.onTimeout(wrapArgument(supplier, true), duration);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return super.onTimeout(wrapArgument(supplier, true), duration, cancelOnTimeout);
    }

    // ========================================================================
    // Unary Composition Methods
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
        return super.thenApply(wrapArgument(fn, false));
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return super.thenApplyAsync(wrapArgument(fn, true));
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return super.thenApplyAsync(wrapArgument(fn, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> thenAccept(Consumer<? super T> action) {
        return super.thenAccept(wrapArgument(action, false));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return super.thenAcceptAsync(wrapArgument(action, true));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return super.thenAcceptAsync(wrapArgument(action, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> thenRun(Runnable action) {
        return super.thenRun(wrapArgument(action, false));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> thenRunAsync(Runnable action) {
        return super.thenRunAsync(wrapArgument(action, true));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
        return super.thenRunAsync(wrapArgument(action, true), wrapArgument(executor));
    }

    // ========================================================================
    // Binary Composition Methods
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other,
                                         BiFunction<? super T, ? super U, ? extends V> fn) {
        return super.thenCombine(
            wrapArgument(other, false), wrapArgument(fn, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn) {
        return super.thenCombineAsync(
            wrapArgument(other, true), wrapArgument(fn, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn,
                                              Executor executor) {
        return super.thenCombineAsync(
            wrapArgument(other, true), wrapArgument(fn, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                                            BiConsumer<? super T, ? super U> action) {
        return super.thenAcceptBoth(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action) {
        return super.thenAcceptBothAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action,
                                                 Executor executor) {
        return super.thenAcceptBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return super.runAfterBoth(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return super.runAfterBothAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other,
                                           Runnable action,
                                           Executor executor) {
        return super.runAfterBothAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> applyToEither(CompletionStage<? extends T> other,
                                        Function<? super T, U> fn) {
        return super.applyToEither(
            wrapArgument(other, false), wrapArgument(fn, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                             Function<? super T, U> fn) {
        return super.applyToEitherAsync(
            wrapArgument(other, true), wrapArgument(fn, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                             Function<? super T, U> fn,
                                             Executor executor) {
        return super.applyToEitherAsync(
            wrapArgument(other, true), wrapArgument(fn, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> acceptEither(CompletionStage<? extends T> other,
                                      Consumer<? super T> action) {
        return super.acceptEither(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                           Consumer<? super T> action) {
        return super.acceptEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                           Consumer<? super T> action,
                                           Executor executor) {
        return super.acceptEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return super.runAfterEither(
            wrapArgument(other, false), wrapArgument(action, false)
        );
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return super.runAfterEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true)
        );
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other,
                                             Runnable action,
                                             Executor executor) {
        return super.runAfterEitherAsync(
            wrapArgument(other, true), wrapArgument(action, true), wrapArgument(executor)
        );
    }

    // ========================================================================
    // Composition / Error Handling
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return super.thenCompose(wrapArgument(fn, false, true));
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return super.thenComposeAsync(wrapArgument(fn, true, true));
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return super.thenComposeAsync(wrapArgument(fn, true, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return super.exceptionally(wrapArgument(fn, false));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return super.exceptionallyAsync(wrapArgument(fn, true));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return super.exceptionallyAsync(wrapArgument(fn, true), executor);
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return super.exceptionallyCompose(wrapArgument(fn, false, true));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return super.exceptionallyComposeAsync(wrapArgument(fn, true, true));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return super.exceptionallyComposeAsync(wrapArgument(fn, true, true), executor);
    }

    // ========================================================================
    // Filtering
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilter(Predicate<? super T> predicate) {
        return super.thenFilter(wrapArgument(predicate, false));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilter(Predicate<? super T> predicate,
                                 Function<? super T, Throwable> errorSupplier) {
        return super.thenFilter(wrapArgument(predicate, false), wrapArgument(errorSupplier, false));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return super.thenFilterAsync(wrapArgument(predicate, true));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate,
                                      Function<? super T, Throwable> errorSupplier) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(errorSupplier, true));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> thenFilterAsync(Predicate<? super T> predicate,
                                      Function<? super T, Throwable> errorSupplier,
                                      Executor executor) {
        return super.thenFilterAsync(wrapArgument(predicate, true), wrapArgument(errorSupplier, true), wrapArgument(executor));
    }

    // ========================================================================
    // Completion Handlers
    // ========================================================================

    /** {@inheritDoc} */
    @Override
    public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return super.whenComplete(wrapArgument(action, false));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return super.whenCompleteAsync(wrapArgument(action, true));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return super.whenCompleteAsync(wrapArgument(action, true), wrapArgument(executor));
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return super.handle(wrapArgument(fn, false));
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return super.handleAsync(wrapArgument(fn, true));
    }

    /** {@inheritDoc} */
    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return super.handleAsync(wrapArgument(fn, true), wrapArgument(executor));
    }
}