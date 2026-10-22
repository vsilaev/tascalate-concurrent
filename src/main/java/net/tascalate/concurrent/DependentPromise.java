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
package net.tascalate.concurrent;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.tascalate.concurrent.decorators.ExecutorBoundDependentPromise;

/**
 * A specialized {@link Promise} that tracks its "origins" (upstream stages) and can 
 * propagate cancellation to them.
 *
 * <h2>The Dependency Problem</h2>
 * <p>
 * In standard {@code CompletionStage} pipelines, cancelling a derived stage does <i>not</i> 
 * cancel the stages it depends on. For example:
 * </p>
 * <pre>{@code
 * Promise<?> p1 = CompletableTask.supplyAsync(this::longRunningTask, executor);
 * Promise<?> p2 = p1.thenRunAsync(this::anotherLongRunningTask);
 * p2.cancel(true); // p1 keeps running!
 * }</pre>
 *
 * <h2>The Solution</h2>
 * <p>
 * {@code DependentPromise} solves this by allowing you to explicitly enlist upstream stages 
 * as cancellation targets. When the dependent promise is cancelled, all enlisted origins 
 * are also cancelled (if not already completed).
 * </p>
 * <pre>{@code
 * DependentPromise<?> p1 = DependentPromise.from(CompletableTask.supplyAsync(this::longRunningTask, executor));
 * DependentPromise<?> p2 = p1.thenRunAsync(this::anotherLongRunningTask, true); // true = enlist p1
 * p2.cancel(true); // Now BOTH p2 and p1 are cancelled!
 * }</pre>
 *
 * <h2>Enlistment Options</h2>
 * <ul>
 *   <li><b>Explicit Control:</b> For unary operations (e.g., {@code thenApply}, {@code thenRun}), 
 *       overloaded methods accept a {@code boolean enlistOrigin} parameter. For binary operations 
 *       (e.g., {@code thenCombine}, {@code applyToEither}), overloaded methods accept a 
 *       {@link Promise.Origin} set to specify whether {@code THIS}, {@code PARAM}, {@code ALL}, 
 *       or {@code NONE} are enlisted.</li>
 *   <li><b>Default Behavior:</b> The standard {@code CompletionStage} methods (without explicit 
 *       enlistment parameters) delegate to their overloaded counterparts using the 
 *       {@code defaultEnlistOptions} configured when this promise was created (via 
 *       {@link Promise#dependent(Set)} or {@link #from(Promise, Set)}). Depending on these options, 
 *       the standard methods may automatically enlist "this" promise, the parameter stage, or both. 
 *       If the options are {@link Promise.Origin#NONE} (the default), no stages are implicitly enlisted.</li>
 * </ul>
 *
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see Promise#dependent()
 * @see ConfigurableDependentPromise
 */
public interface DependentPromise<T> extends Promise<T> {

    /**
     * Decorates this {@link DependentPromise} using the provided factory.
     * <p>
     * Overrides {@link Promise#as(Function)} to delegate to {@link #asʹ(Function)}, 
     * ensuring the factory receives the {@code DependentPromise} type.
     * </p>
     *
     * @param <D> the type of the decorator
     * @param decoratorFactory the factory to create the decorator
     * @return the decorated promise
     */
    @Override
    default <D> D as(Function<? super Promise<T>, D> decoratorFactory) {
        return asʹ(decoratorFactory);
    }

    /**
     * Decorates this {@link DependentPromise} using the provided factory.
     * <p>
     * <b>Implementation Note:</b> The prime symbol ({@code ʹ}) is used to avoid overload 
     * resolution conflicts with {@code Promise.as(Function)} due to type erasure. Both 
     * methods accept {@code Function}, but this one expects a function parameterized with 
     * {@code DependentPromise}.
     * </p>
     *
     * @param <D> the type of the decorator
     * @param decoratorFactory the factory to create the decorator
     * @return the decorated dependent promise
     */
    default <D> D asʹ(Function<? super DependentPromise<T>, D> decoratorFactory) {
        return decoratorFactory.apply(this);
    }

    /**
     * Wraps the given {@link Promise} as a {@code DependentPromise}.
     * <p>
     * <b>Note:</b> Retained only for backward-compatibility with versions below 0.5.4. 
     * Prefer using {@link Promise#dependent()}.
     * </p>
     *
     * @param <U> the promise result type
     * @param source the source promise
     * @return the wrapped dependent promise
     */
    public static <U> DependentPromise<U> from(Promise<U> source) {
        return ConfigurableDependentPromise.from(source);
    }

    /**
     * Wraps the given {@link Promise} as a {@code DependentPromise} with default enlistment options.
     *
     * @param <U> the promise result type
     * @param source the source promise
     * @param defaultEnlistOptions the default origins to enlist for cancellation
     * @return the wrapped dependent promise
     */
    public static <U> DependentPromise<U> from(Promise<U> source, Set<Promise.Origin> defaultEnlistOptions) {
        return ConfigurableDependentPromise.from(source, defaultEnlistOptions);
    }

    @Override
    DependentPromise<T> onCancel(Runnable action);

    @Override
    default DependentPromise<T> defaultAsyncOn(Executor executor) {
        return new ExecutorBoundDependentPromise<>(this, executor);
    }

    // ========================================================================
    // Delay / Timeout Overrides (Returning DependentPromise)
    // ========================================================================

    @Override
    default DependentPromise<T> delay(long timeout, TimeUnit unit) {
        return delay(timeout, unit, true);
    }

    @Override
    default DependentPromise<T> delay(long timeout, TimeUnit unit, 
                                      boolean delayOnError) {
        return delay(AsyncInternals.toDuration(timeout, unit), delayOnError);
    }

    @Override
    default DependentPromise<T> delay(Duration duration) {
        return delay(duration, true);
    }

    /**
     * Delays the completion, with explicit control over origin enlistment.
     *
     * @param timeout the delay amount
     * @param unit the time unit
     * @param delayOnError if {@code false}, errors bypass the delay
     * @param enlistOrigin if {@code true}, enlists the current promise for cancellation
     * @return a delayed dependent promise
     */
    default DependentPromise<T> delay(long timeout, TimeUnit unit, 
                                      boolean delayOnError, boolean enlistOrigin) {
        return delay(AsyncInternals.toDuration(timeout, unit), delayOnError, enlistOrigin);        
    }

    @Override
    DependentPromise<T> delay(Duration duration, boolean delayOnError);

    /**
     * Delays the completion, with explicit control over origin enlistment.
     *
     * @param duration the delay duration
     * @param delayOnError if {@code false}, errors bypass the delay
     * @param enlistOrigin if {@code true}, enlists the current promise for cancellation
     * @return a delayed dependent promise
     */
    DependentPromise<T> delay(Duration duration, boolean delayOnError, boolean enlistOrigin);

    // ... (Similar overrides for orTimeout and onTimeout omitted for brevity, 
    // but they follow the exact same pattern of adding `enlistOrigin` parameters) ...

    @Override    
    default DependentPromise<T> orTimeout(long timeout, TimeUnit unit) {
        return orTimeout(timeout, unit, true);
    }

    @Override
    default DependentPromise<T> orTimeout(long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout) {
        return orTimeout(AsyncInternals.toDuration(timeout, unit), cancelOnTimeout);
    }
    default DependentPromise<T> orTimeout(long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout, boolean enlistOrigin) {
        return orTimeout(AsyncInternals.toDuration(timeout, unit), cancelOnTimeout, enlistOrigin);
    }

    @Override
    default DependentPromise<T> orTimeout(Duration duration) {
        return orTimeout(duration, true);
    }

    @Override
    DependentPromise<T> orTimeout(Duration duration, boolean cancelOnTimeout);

    DependentPromise<T> orTimeout(Duration duration, 
                                  boolean cancelOnTimeout, boolean enlistOrigin);

    // ... (onTimeout overrides follow the same pattern) ...

    @Override
    default DependentPromise<T> onTimeout(T value, long timeout, TimeUnit unit) {
        return onTimeout(value, timeout, unit, true);
    }

    @Override
    default DependentPromise<T> onTimeout(T value, 
                                          long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout) {
        return onTimeout(value, AsyncInternals.toDuration(timeout, unit), cancelOnTimeout);
    }
    default DependentPromise<T> onTimeout(T value, 
                                          long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout, boolean enlistOrigin) {
        return onTimeout(value, AsyncInternals.toDuration(timeout, unit), cancelOnTimeout, enlistOrigin);
    }

    @Override
    default DependentPromise<T> onTimeout(T value, Duration duration) {
        return onTimeout(value, duration, true);
    }

    @Override
    DependentPromise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout);
    DependentPromise<T> onTimeout(T value, Duration duration, 
                                  boolean cancelOnTimeout, boolean enlistOrigin);
    

    @Override
    default DependentPromise<T> onTimeout(Supplier<? extends T> supplier, 
                                          long timeout, TimeUnit unit) {
        return onTimeout(supplier, timeout, unit, true);
    }

    @Override
    default DependentPromise<T> onTimeout(Supplier<? extends T> supplier, 
                                          long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout) {
        return onTimeout(supplier, AsyncInternals.toDuration(timeout, unit), cancelOnTimeout);
    }
    default DependentPromise<T> onTimeout(Supplier<? extends T> supplier, 
                                          long timeout, TimeUnit unit, 
                                          boolean cancelOnTimeout, boolean enlistOrigin) {
        return onTimeout(supplier, AsyncInternals.toDuration(timeout, unit), cancelOnTimeout, enlistOrigin);
    }

    @Override
    default DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return onTimeout(supplier, duration, true);
    }

    @Override
    DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, 
                                  boolean cancelOnTimeout);

    DependentPromise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, 
                                  boolean cancelOnTimeout, boolean enlistOrigin);

    <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin);

    <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin);

    <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor, boolean enlistOrigin);

    DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin);

    DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin);

    DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor, boolean enlistOrigin);

    DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin);

    DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin);

    DependentPromise<Void> thenRunAsync(Runnable action, Executor executor, boolean enlistOrigin);

    <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                           BiFunction<? super T, ? super U, ? extends V> fn,
                                           Set<Promise.Origin> enlistOptions);

    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                BiFunction<? super T, ? super U, ? extends V> fn,
                                                Set<Promise.Origin> enlistOptions);

    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                BiFunction<? super T, ? super U, ? extends V> fn, 
                                                Executor executor,
                                                Set<Promise.Origin> enlistOptions);

    <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                              BiConsumer<? super T, ? super U> action,
                                              Set<Promise.Origin> enlistOptions);

    <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                   BiConsumer<? super T, ? super U> action,
                                                   Set<Promise.Origin> enlistOptions);

    <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                   BiConsumer<? super T, ? super U> action, 
                                                   Executor executor,
                                                   Set<Promise.Origin> enlistOptions);    

    DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<Promise.Origin> enlistOptions);

    DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<Promise.Origin> enlistOptions);

    DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor,
                                             Set<Promise.Origin> enlistOptions);

    <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                          Function<? super T, U> fn,
                                          Set<Promise.Origin> enlistOptions);    

    <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                               Function<? super T, U> fn,
                                               Set<Promise.Origin> enlistOptions);

    <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                               Function<? super T, U> fn,
                                               Executor executor,
                                               Set<Promise.Origin> enlistOptions);

    DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                        Consumer<? super T> action,
                                        Set<Promise.Origin> enlistOptions);

    DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                             Consumer<? super T> action,
                                             Set<Promise.Origin> enlistOptions);

    DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                             Consumer<? super T> action,
                                             Executor executor,
                                             Set<Promise.Origin> enlistOptions);

    DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<Promise.Origin> enlistOptions);

    DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<Promise.Origin> enlistOptions);

    DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                               Runnable action, 
                                               Executor executor,
                                               Set<Promise.Origin> enlistOptions);

    <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin);

    <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin);

    <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, 
                                             Executor executor, 
                                             boolean enlistOrigin);

    DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn, boolean enlistOrigin);

    DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, boolean enlistOrigin);

    DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, 
                                           Executor executor, 
                                           boolean enlistOrigin);

    DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin);

    DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, boolean enlistOrigin);

    DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                  Executor executor, 
                                                  boolean enlistOrigin);

    DependentPromise<T> thenFilter(Predicate<? super T> predicate, boolean enlistOrigin);

    DependentPromise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, boolean enlistOrigin);

    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, boolean enlistOrigin);

    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, boolean enlistOrigin);

    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor, boolean enlistOrigin);

    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor, boolean enlistOrigin);

    DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin);

    DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin);

    DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, 
                                          Executor executor, 
                                          boolean enlistOrigin);

    <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin);

    <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin);

    <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, 
                                        Executor executor, 
                                        boolean enlistOrigin);
    

    /**
     * Converts this dependent promise to a standard {@link CompletableFuture}.
     *
     * @param enlistOrigin if {@code true}, cancelling the returned future cancels this promise
     * @return the CompletableFuture
     */
    CompletableFuture<T> toCompletableFuture(boolean enlistOrigin);

    // ========================================================================
    // Standard CompletionStage Methods (Overridden to return DependentPromise)
    // ========================================================================
    @Override
    <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn);

    @Override
    <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn);

    @Override
    <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor);

    @Override
    DependentPromise<Void> thenAccept(Consumer<? super T> action);

    @Override
    DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action);

    @Override
    DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor);

    @Override
    DependentPromise<Void> thenRun(Runnable action);

    @Override
    DependentPromise<Void> thenRunAsync(Runnable action);

    @Override
    DependentPromise<Void> thenRunAsync(Runnable action, Executor executor);

    @Override
    <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                           BiFunction<? super T, ? super U, ? extends V> fn);

    @Override
    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                BiFunction<? super T, ? super U, ? extends V> fn);

    @Override
    <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                BiFunction<? super T, ? super U, ? extends V> fn, 
                                                Executor executor);

    @Override
    <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                              BiConsumer<? super T, ? super U> action);

    @Override
    <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                   BiConsumer<? super T, ? super U> action);

    @Override
    <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                   BiConsumer<? super T, ? super U> action,
                                                   Executor executor); 

    @Override
    DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action);

    @Override
    DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action);

    @Override
    DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor);

    @Override
    <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn);

    @Override
    <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn);

    @Override
    <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                               Function<? super T, U> fn,
                                               Executor executor); 

    @Override
    DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action);

    @Override
    DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action);

    @Override
    DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                             Consumer<? super T> action,
                                             Executor executor); 

    @Override
    DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action);

    @Override
    DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action);

    @Override
    DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                               Runnable action, 
                                               Executor executor);

    @Override
    <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

    @Override
    <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn);

    @Override
    <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor);

    @Override
    DependentPromise<T> exceptionally(Function<Throwable, ? extends T> fn);

    @Override
    DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn);

    @Override
    DependentPromise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor);

    @Override
    DependentPromise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn);

    @Override
    DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn);

    @Override
    DependentPromise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor);

    @Override
    DependentPromise<T> thenFilter(Predicate<? super T> predicate);

    @Override
    DependentPromise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier);

    @Override
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate);

    @Override
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier);

    @Override
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor);

    @Override
    DependentPromise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor);

    @Override
    DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    @Override
    DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action);

    @Override
    DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor);

    @Override
    <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

    @Override
    <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn);

    @Override
    <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor);
}
