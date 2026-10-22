/**
 * Copyright 2015-20261 Valery Silaev (http://vsilaev.com)
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

import static java.util.Collections.unmodifiableSet;
import static net.tascalate.concurrent.AsyncInternals.NO_SUCH_ELEMENT;
import static net.tascalate.concurrent.AsyncInternals.applyAndCancel;
import static net.tascalate.concurrent.AsyncInternals.failure;
import static net.tascalate.concurrent.AsyncInternals.selectFirst;
import static net.tascalate.concurrent.AsyncInternals.supply;
import static net.tascalate.concurrent.AsyncInternals.unwrapExecutionException;
import static net.tascalate.concurrent.AsyncInternals.wrapCompletionException;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.tascalate.concurrent.core.CompletionStageAPI;
import net.tascalate.concurrent.decorators.ExecutorBoundPromise;

/**
 * The core interface of the Tascalate Concurrent library, combining the non-blocking 
 * composition capabilities of {@link CompletionStage} with the blocking access and 
 * cancellation semantics of {@link Future}.
 *
 * <h2>Key Characteristics</h2>
 * <ul>
 *   <li><b>Fluent Composition:</b> Every composition method inherited from
 *       {@code CompletionStage} is overridden to return a {@code Promise}, enabling
 *       seamless, type-safe chaining without casting.</li>
 *   <li><b>True Cancellation:</b> Unlike standard {@code CompletableFuture}, calling
 *       {@link #cancel(boolean)} with {@code mayInterruptIfRunning = true} attempts to
 *       interrupt the underlying thread executing the task (if the implementation
 *       supports it, e.g., {@link CompletableTask}).</li>
 *   <li><b>Java 9+ Forward Compatibility:</b> This interface backports essential
 *       Java 9+ features to JDK 8, including {@link #resultNow()}, {@link #exceptionNow()},
 *       {@link #getNow(Supplier)}, {@link #join()}, {@link #isCompletedExceptionally()},
 *       and advanced exception handling like {@link #exceptionallyAsync(Function)} and
 *       {@link #exceptionallyCompose(Function)}.</li>
 *   <li><b>Dependency Tracking:</b> Via the {@link #dependent()} method, a {@code Promise}
 *       can be converted into a {@link DependentPromise}, which allows enlisting upstream
 *       stages to be cancelled automatically when the dependent promise is cancelled.</li>
 *   <li><b>Decorator Support:</b> Methods like {@link #as(Function)}, {@link #unwrap()},
 *       and {@link #raw()} support the decorator pattern, allowing transparent addition
 *       of behaviors (e.g., context propagation, executor binding) without breaking the
 *       fluent API.</li>
 * </ul>
 *
 * @param <T> the type of the successfully resolved promise value
 * @author vsilaev
 * @see DependentPromise
 * @see CompletableTask
 * @see Promises
 */
public interface Promise<T> extends Future<T>, CompletionStage<T> {

    /**
     * Represents the computation state of a {@link Promise}.
     * <p>
     * These states map to the underlying {@link CompletionStageAPI} states and can be 
     * used as {@link Predicate}s to filter or check promise states fluently.
     * </p>
     */
    public enum State implements Predicate<Promise<?>> {
        /** The task is still running and has not yet completed. */
        RUNNING,
        /** The task completed successfully with a result. */
        SUCCESS,
        /** The task completed exceptionally with an error. */
        FAILED,
        /** The task was cancelled before or during execution. */
        CANCELLED;

        /** Predicate matching any promise that is still running. */
        public static final Predicate<Promise<?>> PENDING = RUNNING;
        /** Predicate matching any promise that has finished (success, fail, or cancel). */
        public static final Predicate<Promise<?>> COMPLETED = RUNNING.negate();
        /** Predicate matching any promise that ended in failure or cancellation. */
        public static final Predicate<Promise<?>> FAILED_OR_CANCELLED = FAILED.or(CANCELLED);

        @Override
        public boolean test(Promise<?> promise) {
            return promise.isIn(this);
        }

        /**
         * Creates a predicate that matches if the promise is in <i>any</i> of the given states.
         *
         * @param states the states to match against
         * @return a composite predicate
         */
        public static Predicate<Promise<?>> anyOf(State... states) {
            return p -> p.isIn(states);
        }
    }

    /**
     * Defines the origin targets for cancellation propagation in {@link DependentPromise}.
     * <p>
     * When a dependent promise is cancelled, these flags determine which upstream stages 
     * should also be cancelled.
     * </p>
     */
    public enum Origin {
        /** Refers to the promise instance on which the composition method is invoked. */
        THIS, 
        /** Refers to the secondary {@link CompletionStage} passed as an argument to the method. */
        PARAM;

        /** Enlist both {@code THIS} and {@code PARAM} for cancellation. */
        public static final Set<Origin> ALL        = unmodifiableSet(EnumSet.allOf(Origin.class));
        /** Enlist neither (no cancellation propagation). */
        public static final Set<Origin> NONE       = unmodifiableSet(EnumSet.noneOf(Origin.class));
        /** Enlist only the current promise instance. */
        public static final Set<Origin> THIS_ONLY  = unmodifiableSet(EnumSet.of(THIS));
        /** Enlist only the parameter stage. */
        public static final Set<Origin> PARAM_ONLY = unmodifiableSet(EnumSet.of(PARAM));
    }

    // ========================================================================
    // State Inspection (Backported from Java 9+)
    // ========================================================================

    /**
     * Checks if the promise is currently in the specified {@link State}.
     *
     * @param state the state to check
     * @return {@code true} if the promise is in the given state
     */
    default boolean isIn(State state) {
        return CompletionStageAPI.current().isInState(this, state, false);
    }

    /**
     * Checks if the promise is in <i>any</i> of the specified states.
     * <p>
     * Optimized for 0, 1, or multiple states to avoid unnecessary {@link Set} allocations 
     * in the common single-state case.
     * </p>
     *
     * @param state the states to check
     * @return {@code true} if the promise matches any of the given states
     */
    default boolean isIn(State... state) {
        if (state == null) {
            return false;
        } else {
            switch (state.length) {
                case 0: return false;
                case 1: return isIn(state[0]);
                default:
                    // Fallback for multiple states: build a HashSet for O(1) lookups
                    Set<State> states = new HashSet<>();
                    states.addAll(Arrays.asList(state));
                    return CompletionStageAPI.current().isInState(this, states, false);
            }
        }
    }

    /**
     * Checks if the promise is in any of the specified states.
     *
     * @param states the set of states to check
     * @return {@code true} if the promise matches any state in the set
     */
    default boolean isIn(Set<State> states) {
        return CompletionStageAPI.current().isInState(this, states, false);
    }

    /**
     * Returns the result if already completed successfully.
     *
     * @return the result value
     * @throws IllegalStateException if not completed, cancelled, or completed exceptionally
     */
    default T resultNow() {
        return CompletionStageAPI.current().resultNow(this, false);
    }

    /**
     * Returns the exception if already completed exceptionally.
     *
     * @return the exception
     * @throws IllegalStateException if not completed, cancelled, or completed successfully
     */
    default Throwable exceptionNow() {
        return CompletionStageAPI.current().exceptionNow(this, false);
    }

    /**
     * Returns the result if already completed, otherwise returns the given value.
     *
     * @param valueIfAbsent the value to return if not yet completed
     * @return the result or the absent value
     * @throws CancellationException if cancelled
     * @throws CompletionException if completed exceptionally
     */
    default T getNow(T valueIfAbsent) throws CancellationException, CompletionException {
        return getNow(supply(valueIfAbsent));
    }

    /**
     * Returns the result if already completed, otherwise invokes the supplier.
     * <p>
     * Checks {@link #isDone()} first to guarantee no blocking occurs, then calls 
     * {@link #get()} to extract the result or throw the appropriate exception.
     * </p>
     *
     * @param valueIfAbsent the supplier for the value if not yet completed
     * @return the result or the supplied value
     * @throws CancellationException if cancelled
     * @throws CompletionException if completed exceptionally
     */
    default T getNow(Supplier<? extends T> valueIfAbsent) throws CancellationException, CompletionException {
        if (isDone()) {
            try {
                return get();
            } catch (InterruptedException ex) {
                // Should not happen when isDone() returns true
                throw new RuntimeException(ex);
            } catch (ExecutionException ex) {
                throw wrapCompletionException(unwrapExecutionException(ex));
            }
        } else {
            return valueIfAbsent.get();
        }
    }

    /**
     * Waits if necessary and then returns the result.
     * <p>
     * Unwraps {@link ExecutionException} and {@link InterruptedException} into 
     * {@link CompletionException} for a cleaner functional API.
     * </p>
     *
     * @return the result
     * @throws CancellationException if cancelled
     * @throws CompletionException if interrupted or completed exceptionally
     */
    default T join() throws CancellationException, CompletionException {
        try {
            return get();
        } catch (InterruptedException ex) {
            throw new CompletionException(ex);
        } catch (ExecutionException ex) {
            throw wrapCompletionException(unwrapExecutionException(ex));
        }
    }

    /**
     * Returns {@code true} if this promise completed exceptionally or was cancelled.
     * <p>
     * Since we check {@link #isDone()} first, calling {@link #get()} here is guaranteed 
     * to be non-blocking. If it throws, the promise failed.
     * </p>
     *
     * @return {@code true} if completed exceptionally or cancelled
     */
    default boolean isCompletedExceptionally() {
        if (!isDone()) {
            return false;
        }
        if (isCancelled()) {
            return true;
        } else {
            try {
                // We are done, so no blocking
                get();
            } catch (Throwable ex) {
                return true;
            }
            return false;
        }
    }

    // ========================================================================
    // Cancellation & Lifecycle
    // ========================================================================

    /**
     * Registers an action to be run if this promise is cancelled.
     * <p>
     * <b>Implementation Note:</b> {@code CompletionStage} lacks a native {@code onCancel} hook. 
     * We work around this by attaching an {@link #exceptionally(Function)} handler that checks 
     * {@link #isCancelled()} to fire the action.
     * </p>
     *
     * @param action the action to run upon cancellation
     * @return this promise
     */
    default Promise<T> onCancel(Runnable action) {
        if (isDone()) {
            if (isCancelled()) {
                action.run();
            }            
        } else {
            // Workaround: CompletionStage lacks a native onCancel hook.
            // We intercept via exceptionally and check isCancelled() to fire the action.
            exceptionally(__ -> {
               if (isCancelled()) {
                   action.run();
               }
               return null;
            });
        }
        return this;
    }

    // ========================================================================
    // Timeouts & Delays
    // ========================================================================

    /**
     * Delays the completion of this promise by the specified duration.
     *
     * @param timeout the delay amount
     * @param unit the time unit
     * @return a delayed promise
     */
    default Promise<T> delay(long timeout, TimeUnit unit) {
        return delay(timeout, unit, true);
    }

    /**
     * Delays the completion of this promise by the specified duration.
     *
     * @param timeout the delay amount
     * @param unit the time unit
     * @param delayOnError if {@code false}, errors bypass the delay and fail immediately
     * @return a delayed promise
     */
    default Promise<T> delay(long timeout, TimeUnit unit, boolean delayOnError) {
        return delay(AsyncInternals.toDuration(timeout, unit), delayOnError);
    }

    /**
     * Delays the completion of this promise by the specified duration.
     *
     * @param duration the delay duration
     * @return a delayed promise
     */
    default Promise<T> delay(Duration duration) {
        return delay(duration, true);
    }

    /**
     * Delays the completion of this promise by the specified duration.
     * <p>
     * If {@code delayOnError} is {@code false}, uses a fast route via {@code thenCombineAsync} 
     * with {@code selectFirst()} to bypass the delay if the primary promise fails.
     * </p>
     *
     * @param duration the delay duration
     * @param delayOnError if {@code false}, errors bypass the delay
     * @return a delayed promise
     */
    default Promise<T> delay(Duration duration, boolean delayOnError) {
        if (!delayOnError) {
            // Fast route: if we don't delay on error, we can use a simple thenCombineAsync
            // with selectFirst() to bypass the delay if the primary promise fails.
            return thenCompose(v ->
                this.dependent()
                    .thenCombineAsync(AsyncInternals.delay(duration), selectFirst(), Origin.PARAM_ONLY)
                    .unwrap()
            );
        }
        // Standard route: handle both success and failure, then delay before resolving
        DependentPromise<Try<T>> h = dependent().handle(Try::handle, false);
        return h.thenCompose(t -> t.isSuccess() || !(isCancelled() || t.isCancel()) ?
           // "this" is already completed promise here (in both cases)
           // Use *Async to execute on default "this" executor
           h.thenCombineAsync(AsyncInternals.delay(duration), (_1, _2) -> join(), Origin.PARAM_ONLY)
           :
           this, 
           true
        ).unwrap();
    }

    /**
     * Fails this promise with a {@link java.util.concurrent.TimeoutException} if it does 
     * not complete within the specified duration.
     *
     * @param timeout the timeout duration
     * @return a promise that times out
     */
    default Promise<T> orTimeout(long timeout, TimeUnit unit) {
        return orTimeout(timeout, unit, true);
    }

    /**
     * Fails this promise with a {@link java.util.concurrent.TimeoutException} if it does 
     * not complete within the specified duration.
     *
     * @param timeout the timeout duration
     * @param cancelOnTimeout if {@code true}, cancels the underlying task on timeout
     * @return a promise that times out
     */
    default Promise<T> orTimeout(long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return orTimeout(AsyncInternals.toDuration(timeout, unit), cancelOnTimeout);
    }

    /**
     * Fails this promise with a {@link java.util.concurrent.TimeoutException} if it does 
     * not complete within the specified duration.
     *
     * @param duration the timeout duration
     * @return a promise that times out
     */
    default Promise<T> orTimeout(Duration duration) {
        return orTimeout(duration, true);
    }

    /**
     * Fails this promise with a {@link java.util.concurrent.TimeoutException} if it does 
     * not complete within the specified duration.
     * <p>
     * Races the original promise against a delayed null trigger. Uses {@code *Async} for 
     * the final composition to ensure correct interruption behavior on single-thread executors.
     * </p>
     *
     * @param duration the timeout duration
     * @param cancelOnTimeout if {@code true}, cancels the underlying task on timeout
     * @return a promise that times out
     */
    default Promise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        // Create a delayed null promise that will act as the timeout trigger
        Promise<Try<T>> onTimeout = AsyncInternals.delayed(null, duration);
        return
        this.dependent()
            .handle(Try::handle, false)
            // Race the original promise against the timeout trigger
            .applyToEither(onTimeout, applyAndCancel(v -> Try.doneOrTimeout(v, duration), cancelOnTimeout, this, onTimeout), Origin.ALL)
            // Use *Async to execute on default "this" executor; 
            // Don't use *Async above to let interrupt correctly with single-thread executors
            .thenComposeAsync(Try::asPromise, true)
            .unwrap();
    }

    /**
     * Completes with the given value if this promise does not complete within the timeout.
     *
     * @param value the fallback value
     * @param timeout the timeout duration
     * @param unit the time unit
     * @return a promise with a fallback
     */
    default Promise<T> onTimeout(T value, long timeout, TimeUnit unit) {
        return onTimeout(value, timeout, unit, true);
    }

    /**
     * Completes with the given value if this promise does not complete within the timeout.
     *
     * @param value the fallback value
     * @param timeout the timeout duration
     * @param unit the time unit
     * @param cancelOnTimeout if {@code true}, cancels the underlying task on timeout
     * @return a promise with a fallback
     */
    default Promise<T> onTimeout(T value, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return onTimeout(value, AsyncInternals.toDuration(timeout, unit), cancelOnTimeout);
    }

    /**
     * Completes with the given value if this promise does not complete within the timeout.
     *
     * @param value the fallback value
     * @param duration the timeout duration
     * @return a promise with a fallback
     */
    default Promise<T> onTimeout(T value, Duration duration) {
        return onTimeout(value, duration, true);
    }

    /**
     * Completes with the given value if this promise does not complete within the timeout.
     *
     * @param value the fallback value
     * @param duration the timeout duration
     * @param cancelOnTimeout if {@code true}, cancels the underlying task on timeout
     * @return a promise with a fallback
     */
    default Promise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout) {
        Promise<Try<T>> onTimeout = AsyncInternals.delayed(Try.success(value), duration);
        return 
        this.dependent()
            .handle(Try::handle, false)
            // Use *Async to execute on default "this" executor
            .applyToEither(onTimeout, applyAndCancel(Function.identity(), cancelOnTimeout, this, onTimeout), Origin.ALL)
            // Use *Async to execute on default "this" executor; 
            // Don't use *Async above to let interrupt correctly with single-thread executors            
            .thenComposeAsync(Try::asPromise, true)
            .unwrap();
    }

    /**
     * Completes with the supplier's value if this promise does not complete within the timeout.
     *
     * @param supplier the fallback supplier
     * @param timeout the timeout duration
     * @param unit the time unit
     * @return a promise with a fallback
     */
    default Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit) {
        return onTimeout(supplier, timeout, unit, true);
    }

    /**
     * Completes with the supplier's value if this promise does not complete within the timeout.
     *
     * @param supplier the fallback supplier
     * @param timeout the timeout duration
     * @param unit the time unit
     * @param cancelOnTimeout if {@code true}, cancels the underlying task on timeout
     * @return a promise with a fallback
     */
    default Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return onTimeout(supplier, AsyncInternals.toDuration(timeout, unit), cancelOnTimeout);
    }

    /**
     * Completes with the supplier's value if this promise does not complete within the timeout.
     *
     * @param supplier the fallback supplier
     * @param duration the timeout duration
     * @return a promise with a fallback
     */
    default Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return onTimeout(supplier, duration, true);
    }

    /**
     * Completes with the supplier's value if this promise does not complete within the timeout.
     * <p>
     * The supplier is invoked asynchronously on the default executor only if the timeout 
     * actually triggers.
     * </p>
     *
     * @param supplier the fallback supplier
     * @param duration the timeout duration
     * @param cancelOnTimeout if {@code true}, cancels the underlying task on timeout
     * @return a promise with a fallback
     */
    default Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        // timeout converted to supplier
        Promise<Supplier<Try<T>>> onTimeout = AsyncInternals.delayed(Try.supplierCall(supplier), duration);
        return
        this.dependent()
            .handle((r, e) -> supply(Try.handle(r, e)), false)
            .applyToEither(onTimeout, applyAndCancel(Function.identity(), cancelOnTimeout, this, onTimeout), Origin.ALL)
            // Use *Async to execute on default "this" executor; 
            // Don't use *Async above to let interrupt correctly with single-thread executors
            // Supplier will be called on async executor of this
            .thenComposeAsync(s -> s.get().asPromise(), true) 
            .unwrap();
    }

    // ========================================================================
    // Decorators & Unwrapping
    // ========================================================================

    /**
     * Converts this {@link Promise} to a {@link DependentPromise}.
     * <p>
     * The returned {@code DependentPromise} does not implicitly enlist any {@link CompletionStage}
     * for cancellation; only enlisting via explicit parameter is supported.
     * </p>
     * 
     * @return the created {@code DependentPromise}
     */
    default DependentPromise<T> dependent() {
        return ConfigurableDependentPromise.from(this);
    }

    /**
     * Converts this {@link Promise} to a {@link DependentPromise} with default enlistment options.
     * 
     * @param defaultEnlistOptions defines what stages should be enlisted implicitly for cancellation
     * @return the created {@code DependentPromise}
     */
    default DependentPromise<T> dependent(Set<Origin> defaultEnlistOptions) {
        return ConfigurableDependentPromise.from(this, defaultEnlistOptions);
    }

    /**
     * Binds this promise to a specific default asynchronous executor.
     * <p>
     * Any subsequent composition methods without an explicit executor will use this one.
     * </p>
     *
     * @param executor the default executor
     * @return an executor-bound promise
     */
    default Promise<T> defaultAsyncOn(Executor executor) {
        return new ExecutorBoundPromise<>(this, executor);
    }

    /**
     * Decorates this {@link Promise} using the provided factory.
     *
     * @param <D> the type of the decorator
     * @param decoratorFactory the factory to create the decorator
     * @return the decorated promise
     */
    default <D> D as(Function<? super Promise<T>, D> decoratorFactory) {
        return decoratorFactory.apply(this);
    }

    /**
     * Unwraps the immediate underlying {@link Promise} if it was decorated.
     *
     * @return the underlying promise, or self if not decorated
     */
    default Promise<T> unwrap() {
        return this;
    }

    /**
     * Fully unwraps all decoration layers to reveal the base {@link Promise}.
     *
     * @return the raw, undecorated promise
     */
    default Promise<T> raw() {
        return this;
    }

    // ========================================================================
    // Standard Composition Methods (Overridden to return Promise)
    // ========================================================================

    <U> Promise<U> thenApply(Function<? super T, ? extends U> fn);
    <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn);
    <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor);
    
    Promise<Void> thenAccept(Consumer<? super T> action);
    Promise<Void> thenAcceptAsync(Consumer<? super T> action);
    Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor);

    Promise<Void> thenRun(Runnable action);
    Promise<Void> thenRunAsync(Runnable action);

    Promise<Void> thenRunAsync(Runnable action, Executor executor);

    <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                       Executor executor);

    <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action);
    
    <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action);

    <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                          BiConsumer<? super T, ? super U> action,
                                          Executor executor);

    Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action);

    Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action);

    Promise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                    Runnable action,
                                    Executor executor);

    <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn);
    
    <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn);

    <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                      Function<? super T, U> fn,
                                      Executor executor);

    Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action);

    Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action);

    Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                    Consumer<? super T> action,
                                    Executor executor);

    Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action);

    Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action);

    Promise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                      Runnable action,
                                      Executor executor);

    <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

    <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn);

    <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor);

    Promise<T> exceptionally(Function<Throwable, ? extends T> fn);

    /** Backported Java 12+ method: runs the recovery function asynchronously. */
    default Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return AsyncInternals.exceptionallyAsync(this, fn);
    }

    /** Backported Java 12+ method: runs the recovery function on the given executor. */
    default Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return AsyncInternals.exceptionallyAsync(this, fn, executor);
    }

    /** Backported Java 12+ method: composes with a recovery stage. */
    default Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return AsyncInternals.exceptionallyCompose(this, fn);
    }

    /** Backported Java 12+ method: composes with a recovery stage asynchronously. */
    default Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return AsyncInternals.exceptionallyComposeAsync(this, fn);
    }

    /** Backported Java 12+ method: composes with a recovery stage on the given executor. */
    default Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return AsyncInternals.exceptionallyComposeAsync(this, fn, executor);
    }

    // ========================================================================
    // Filtering (Tascalate Concurrent Extension)
    // ========================================================================

    /**
     * Filters the result. If the predicate fails, the promise fails with 
     * {@link java.util.NoSuchElementException}.
     *
     * @param predicate the condition to test
     * @return a filtered promise
     */
    default Promise<T> thenFilter(Predicate<? super T> predicate) {
        return thenFilter(predicate, NO_SUCH_ELEMENT);
    }

    /**
     * Filters the result. If the predicate fails, the promise fails with the exception 
     * supplied by {@code errorSupplier}.
     *
     * @param predicate the condition to test
     * @param errorSupplier the supplier for the failure exception
     * @return a filtered promise
     */
    default Promise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return thenCompose(v -> predicate.test(v) ? this : failure(errorSupplier, v));
    }

    /** Async variant of {@link #thenFilter(Predicate)}. */
    default Promise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return thenFilterAsync(predicate, NO_SUCH_ELEMENT);
    }

    /** Async variant of {@link #thenFilter(Predicate, Function)}. */
    default Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return thenComposeAsync(v -> predicate.test(v) ? this : failure(errorSupplier, v));
    }

    /** Async variant on a specific executor. */
    default Promise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor) {
        return thenFilterAsync(predicate, NO_SUCH_ELEMENT, executor);
    }

    /** Async variant on a specific executor with custom error. */
    default Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor) {
        return thenComposeAsync(v -> predicate.test(v) ? this : failure(errorSupplier, v), executor);
    }

    // ========================================================================
    // Standard CompletionStage Methods (Overridden to return Promise)
    // ========================================================================

    Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);
    Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action);
    Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor);

    <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);
    <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn);
    <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor);
}