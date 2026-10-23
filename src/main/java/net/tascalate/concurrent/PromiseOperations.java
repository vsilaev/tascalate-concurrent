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

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;

import net.tascalate.concurrent.io.BlockingIO;
import net.tascalate.concurrent.var.ContextRelay;

/**
 * A collection of static utility methods designed to be used as <b>extension methods</b>
 * (e.g., via Lombok's {@code @ExtensionMethod} or similar compiler plugins).
 * <p>
 * This class provides a more fluent, natural way to apply common decorators, 
 * transformations, and cross-cutting concerns (like context propagation, interruptible I/O, 
 * and asynchronous try-with-resources) to {@link Promise}s. Instead of using the 
 * {@link Promise#as(java.util.function.Function)} method with a decorator factory, or 
 * wrapping the promise in static utility calls, you can chain these operations directly 
 * on the promise instance.
 * </p>
 * 
 * <h2>Usage with Lombok</h2>
 * <pre>{@code
 * @ExtensionMethod({PromiseOperations.class})
 * public class MyService {
 *     public Promise<Data> fetchData() {
 *         return fetchRawData()
 *             .interruptible()                      // Applies BlockingIO decorator
 *             .contextual(TRACE_ID.relay())         // Applies ContextRelay decorator
 *             .tryComposeEx(resource -> process(resource)); // Async try-with-resources
 *     }
 * }
 * }</pre>
 *
 * @author vsilaev
 * @see Promise
 * @see net.tascalate.concurrent.io.BlockingIO
 * @see net.tascalate.concurrent.var.ContextRelay
 */
public class PromiseOperations {

    private PromiseOperations() {}

    // ========================================================================
    // Monadic Lift / Drop
    // ========================================================================

    /**
     * Lifts a {@link CompletionStage} into a higher-kinded type, returning a 
     * {@code Promise<Promise<T>>}.
     *
     * @param <T>     the result type
     * @param promise the stage to lift
     * @return a promise containing the wrapped promise
     */
    public static <T> Promise<Promise<T>> lift(CompletionStage<? extends T> promise) {
        return lift(Promises.from(promise));
    }

    /**
     * Lifts a {@link Promise} into a higher-kinded type, returning a 
     * {@code Promise<Promise<T>>}.
     *
     * @param <T>     the result type
     * @param promise the promise to lift
     * @return a promise containing the wrapped promise
     */
    public static <T> Promise<Promise<T>> lift(Promise<? extends T> promise) {
        return promise.dependent()
                      .thenApply(Promises::<T>success, true)
                      .unwrap();
    }

    /**
     * Flattens a nested {@link CompletionStage} (i.e., {@code Promise<Promise<T>>}) 
     * into a single {@code Promise<T>}.
     *
     * @param <T>     the result type
     * @param promise the nested stage to flatten
     * @return the flattened promise
     */
    public static <T> Promise<T> drop(CompletionStage<? extends CompletionStage<T>> promise) {
        return drop(Promises.from(promise));
    }

    /**
     * Flattens a nested {@link Promise} (i.e., {@code Promise<Promise<T>>}) 
     * into a single {@code Promise<T>}.
     *
     * @param <T>     the result type
     * @param promise the nested promise to flatten
     * @return the flattened promise
     */
    public static <T> Promise<T> drop(Promise<? extends CompletionStage<T>> promise) {
        return promise.dependent()
                      .thenCompose(Promises::from, true)
                      .unwrap();
    }

    // ========================================================================
    // Type Conversions
    // ========================================================================

    /**
     * Converts a {@link CompletionStage} into a {@code Promise<Stream<T>>}.
     * <p>
     * If the stage completes successfully, the resulting promise yields a single-element 
     * stream. If it completes exceptionally, the resulting promise yields an empty stream.
     * </p>
     *
     * @param <T>     the result type
     * @param promise the stage to convert
     * @return a promise of a stream
     */
    public static <T> Promise<Stream<T>> asStream(CompletionStage<? extends T> promise) {
        return asStream(Promises.from(promise));
    }

    /**
     * Converts a {@link Promise} into a {@code Promise<Stream<T>>}.
     *
     * @param <T>     the result type
     * @param promise the promise to convert
     * @return a promise of a stream
     */
    public static <T> Promise<Stream<T>> asStream(Promise<? extends T> promise) {
        return promise.dependent()
                      .handle((r, e) -> null == e ? Stream.<T>of(r) : Stream.<T>empty(), true)
                      .unwrap();
    }

    /**
     * Converts a {@link CompletionStage} into a {@code Promise<Optional<T>>}.
     * <p>
     * If the stage completes successfully, the resulting promise yields an {@link Optional} 
     * containing the result. If it completes exceptionally, the resulting promise yields 
     * an empty {@link Optional}.
     * </p>
     *
     * @param <T>     the result type
     * @param promise the stage to convert
     * @return a promise of an optional
     */
    public static <T> Promise<Optional<T>> asOptional(CompletionStage<? extends T> promise) {
        return asOptional(Promises.from(promise));
    }

    /**
     * Converts a {@link Promise} into a {@code Promise<Optional<T>>}.
     *
     * @param <T>     the result type
     * @param promise the promise to convert
     * @return a promise of an optional
     */
    public static <T> Promise<Optional<T>> asOptional(Promise<? extends T> promise) {
        return promise.dependent()
                      .handle((r, e) -> Optional.<T>ofNullable(null == e ? r : null), true)
                      .unwrap();
    }

    /**
     * Converts a {@link CompletionStage} into a {@code Promise<Try<T>>}.
     *
     * @param <T>     the result type
     * @param promise the stage to convert
     * @return a promise of a Try
     */
    public static <T> Promise<Try<T>> asTry(CompletionStage<T> promise) {
        return asTry(Promises.from(promise));
    }

    /**
     * Converts a {@link Promise} into a {@code Promise<Try<T>>}.
     *
     * @param <T>     the result type
     * @param promise the promise to convert
     * @return a promise of a Try
     */
    public static <T> Promise<Try<T>> asTry(Promise<T> promise) {
        return Try.lift(promise);
    }

    // ========================================================================
    // Debugging / Side-Effects
    // ========================================================================

    /**
     * Applies a side-effecting consumer to the promise itself and returns the promise.
     * <p>
     * This is useful for logging, debugging, or inspecting the promise in the middle 
     * of a fluent chain without altering its state or type.
     * </p>
     *
     * @param <T>      the result type
     * @param <F>      the promise type
     * @param promise  the promise to peek at
     * @param fn       the consumer to apply
     * @return the original promise
     */
    public static <T, F extends Promise<T>> F peek(F promise, Consumer<? super F> fn) {
        fn.accept(promise);
        return promise;
    }

    /**
     * Returns a function that applies a side-effecting consumer to a promise.
     * <p>
     * This is the functional overload of {@link #peek(Promise, Consumer)}, useful 
     * when composing functions.
     * </p>
     *
     * @param <T> the result type
     * @param <F> the promise type
     * @param fn  the consumer to apply
     * @return a function that peeks at the promise
     */
    public static <T, F extends Promise<T>> Function<F, F> peek(Consumer<? super F> fn) {
        return p -> peek(p, fn);
    }

    // ========================================================================
    // Asynchronous Try-With-Resources
    // ========================================================================

    /**
     * Emulates an asynchronous try-with-resources block for a synchronous function 
     * applied to an {@link AutoCloseable} resource delivered by a {@link Promise}.
     * <p>
     * The resource promise is automatically enlisted for cancellation 
     * ({@link Promise.Origin#ALL}). If the resulting promise is cancelled, the 
     * resource acquisition is also cancelled.
     * </p>
     *
     * @param <T>      the result type
     * @param <R>      the resource type
     * @param promise  the promise producing the resource
     * @param fn       the synchronous function to apply
     * @return a promise of the result
     */
    public static <T, R extends AutoCloseable> Promise<T> 
        tryApply(Promise<R> promise, Function<? super R, ? extends T> fn) { 
        return unwrap(Promises.tryApply(promise.dependent(Promise.Origin.ALL), fn));
    }

    /**
     * Functional overload of {@link #tryApply(Promise, Function)}.
     */
    public static <T, R extends AutoCloseable> Function<Promise<R>, Promise<T>> 
        tryApply(Function<? super R, ? extends T> fn) {
        return p -> tryApply(p, fn);
    }

    /**
     * Emulates an asynchronous try-with-resources block for a synchronous function 
     * applied to an {@link AsyncCloseable} resource delivered by a {@link Promise}.
     * <p>
     * The resource promise is automatically enlisted for cancellation.
     * </p>
     *
     * @param <T>      the result type
     * @param <R>      the async resource type
     * @param promise  the promise producing the resource
     * @param fn       the synchronous function to apply
     * @return a promise of the result
     */
    public static <T, R extends AsyncCloseable> Promise<T> 
        tryApplyEx(Promise<R> promise, Function<? super R, ? extends T> fn) {
        return unwrap(Promises.tryApplyEx(promise.dependent(Promise.Origin.ALL), fn));
    }

    /**
     * Functional overload of {@link #tryApplyEx(Promise, Function)}.
     */
    public static <T, R extends AsyncCloseable> Function<Promise<R>, Promise<T>> 
        tryApplyEx(Function<? super R, ? extends T> fn) {
        return p -> tryApplyEx(p, fn);
    }

    /**
     * Emulates an asynchronous try-with-resources block for an asynchronous function 
     * applied to an {@link AutoCloseable} resource delivered by a {@link Promise}.
     * <p>
     * The resource promise is automatically enlisted for cancellation.
     * </p>
     *
     * @param <T>      the result type
     * @param <R>      the resource type
     * @param promise  the promise producing the resource
     * @param fn       the asynchronous function to apply
     * @return a promise of the result
     */
    public static <T, R extends AutoCloseable> Promise<T> 
        tryCompose(Promise<R> promise, Function<? super R, ? extends CompletionStage<T>> fn) {
        return unwrap(Promises.tryCompose(promise.dependent(Promise.Origin.ALL), fn));
    }

    /**
     * Functional overload of {@link #tryCompose(Promise, Function)}.
     */
    public static <T, R extends AutoCloseable> Function<Promise<R>, Promise<T>> 
        tryCompose(Function<? super R, ? extends CompletionStage<T>> fn) {
        return p -> tryCompose(p, fn);
    }

    /**
     * Emulates an asynchronous try-with-resources block for an asynchronous function 
     * applied to an {@link AsyncCloseable} resource delivered by a {@link Promise}.
     * <p>
     * The resource promise is automatically enlisted for cancellation.
     * </p>
     *
     * @param <T>      the result type
     * @param <R>      the async resource type
     * @param promise  the promise producing the resource
     * @param fn       the asynchronous function to apply
     * @return a promise of the result
     */
    public static <T, R extends AsyncCloseable> Promise<T> 
        tryComposeEx(Promise<R> promise, Function<? super R, ? extends CompletionStage<T>> fn) {
        return unwrap(Promises.tryComposeEx(promise.dependent(Promise.Origin.ALL), fn));
    }

    /**
     * Functional overload of {@link #tryComposeEx(Promise, Function)}.
     */
    public static <T, R extends AsyncCloseable> Function<Promise<R>, Promise<T>> 
        tryComposeEx(Function<? super R, ? extends CompletionStage<T>> fn) {
        return p -> tryComposeEx(p, fn);
    }

    // ========================================================================
    // Chunked Processing
    // ========================================================================

    /**
     * Applies bounded-concurrency chunked processing to a promise yielding an {@link Iterable}.
     *
     * @param <S>           the source element type
     * @param <T>           the type of the spawned tasks' results
     * @param <A>           the collector's intermediate accumulation type
     * @param <R>           the final result type
     * @param promise       the promise yielding the iterable of elements
     * @param batchSize     the maximum number of concurrently running tasks
     * @param spawner       a function producing an asynchronous task for each element
     * @param downstream    the collector used to accumulate the results
     * @return a promise of the collected result
     */
    public static <S, T, A, R> Promise<R> 
        chunkedItems(Promise<Iterable<S>> promise,
                     int batchSize, 
                     Function<? super S, CompletionStage<? extends T>> spawner,
                     Collector<T, A, R> downstream) {
        return promise.dependent()
                      .thenCompose(values -> 
                          Promises.chunked(values, batchSize, spawner, downstream), true)
                      .unwrap();
    }

    /**
     * Functional overload of {@link #chunkedItems(Promise, int, Function, Collector)}.
     */
    public static <S, T, A, R> Function<Promise<Iterable<S>>, Promise<R>> 
        chunkedItems(int batchSize, 
                     Function<? super S, CompletionStage<? extends T>> spawner,
                     Collector<T, A, R> downstream) {
        return p -> chunkedItems(p, batchSize,  spawner, downstream);
    }

    /**
     * Applies bounded-concurrency chunked processing to a promise yielding an {@link Iterable},
     * confining collector invocations to the specified executor.
     */
    public static <S, T, A, R> Promise<R> 
        chunkedItems(Promise<Iterable<S>> promise,
                     int batchSize, 
                     Function<? super S, CompletionStage<? extends T>> spawner, 
                     Collector<T, A, R> downstream,
                     Executor downstreamExecutor) {
        return promise.dependent()
                      .thenCompose(values -> 
                          Promises.chunked(values, batchSize, spawner, downstream, downstreamExecutor), true)
                      .unwrap();
    }

    /**
     * Functional overload of {@link #chunkedItems(Promise, int, Function, Collector, Executor)}.
     */
    public static <S, T, A, R> Function<Promise<Iterable<S>>, Promise<R>> 
        chunkedItems(int batchSize, 
                     Function<? super S, CompletionStage<? extends T>> spawner, 
                     Collector<T, A, R> downstream,
                     Executor downstreamExecutor) {
        return p -> chunkedItems(p, batchSize, spawner, downstream, downstreamExecutor);
    }

    /**
     * Applies bounded-concurrency chunked processing to a promise yielding a {@link Stream}.
     * <p>
     * <b>Note:</b> The method name contains a typo in the original API ({@code chunkedStrem}); 
     * it is retained for backward compatibility. Prefer the functional overload 
     * {@link #chunkedStream(int, Function, Collector)} when using extension methods.
     * </p>
     */
    public static <S, T, A, R> Promise<R> 
        chunkedStrem(Promise<Stream<S>> promise,
                     int batchSize, 
                     Function<? super S, CompletionStage<? extends T>> spawner,
                     Collector<T, A, R> downstream) {
        return promise.dependent()
                      .thenCompose(values -> 
                          Promises.chunked(values, batchSize, spawner, downstream), true)
                      .unwrap();
    }

    /**
     * Functional overload of {@link #chunkedStrem(Promise, int, Function, Collector)}.
     * <p>
     * Note the corrected spelling: {@code chunkedStream}.
     * </p>
     */
    public static <S, T, A, R> Function<Promise<Stream<S>>, Promise<R>> 
        chunkedStream(int batchSize, 
                      Function<? super S, CompletionStage<? extends T>> spawner,
                      Collector<T, A, R> downstream) {
        return p -> chunkedStrem(p, batchSize,  spawner, downstream);
    }

    /**
     * Applies bounded-concurrency chunked processing to a promise yielding a {@link Stream},
     * confining collector invocations to the specified executor.
     */
    public static <S, T, A, R> Promise<R> 
        chunkedStrem(Promise<Stream<S>> promise,
                     int batchSize, 
                     Function<? super S, CompletionStage<? extends T>> spawner,
                     Collector<T, A, R> downstream,
                     Executor downstreamExecutor) {
        return promise.dependent()
                      .thenCompose(values -> 
                          Promises.chunked(values, batchSize, spawner, downstream, downstreamExecutor), true)
                      .unwrap();
    }

    /**
     * Functional overload of {@link #chunkedStrem(Promise, int, Function, Collector, Executor)}.
     * <p>
     * Note the corrected spelling: {@code chunkedStream}.
     * </p>
     */
    public static <S, T, A, R> Function<Promise<Stream<S>>, Promise<R>> 
        chunkedStream(int batchSize, 
                      Function<? super S, CompletionStage<? extends T>> spawner,
                      Collector<T, A, R> downstream,
                      Executor downstreamExecutor) {
        return p -> chunkedStrem(p, batchSize,  spawner, downstream, downstreamExecutor);
    }

    // ========================================================================
    // Context Propagation
    // ========================================================================

    /**
     * Applies a {@link ContextRelay} to the promise, propagating context variables 
     * across asynchronous boundaries using <b>static capture</b>.
     *
     * @param <T>     the result type
     * @param promise the promise to contextualize
     * @param relay   the context relay to apply
     * @return the contextualized promise
     */
    public static <T> Promise<T> contextual(Promise<T> promise, ContextRelay<?> relay) {
        return relay.contextualPromise(promise);
    }

    /**
     * Applies a {@link ContextRelay} to a {@link DependentPromise}, preserving the 
     * dependent API surface.
     */
    public static <T> DependentPromise<T> contextual(DependentPromise<T> promise, ContextRelay<?> relay) {
        return relay.contextualDependentPromise(promise);
    }

    /**
     * Applies a {@link ContextRelay} to the promise, propagating context variables 
     * across asynchronous boundaries using <b>dynamic resolution</b>.
     *
     * @param <T>     the result type
     * @param promise the promise to contextualize
     * @param relay   the context relay to apply
     * @return the dynamically contextualized promise
     */
    public static <T> Promise<T> dynamicContextual(Promise<T> promise, ContextRelay<?> relay) {
        return relay.dynamicContextualPromise(promise);
    }

    /**
     * Applies a {@link ContextRelay} to a {@link DependentPromise} using dynamic resolution.
     */
    public static <T> DependentPromise<T> dynamicContextual(DependentPromise<T> promise, ContextRelay<?> relay) {
        return relay.dynamicContextualDependentPromise(promise);
    }

    // ========================================================================
    // Interruptible I/O
    // ========================================================================

    /**
     * Applies the {@link BlockingIO} interruptible decorator to the promise, making 
     * blocking I/O operations within the promise chain responsive to thread interruption.
     *
     * @param <T>     the result type
     * @param promise the promise to make interruptible
     * @return the interruptible promise
     */
    public static <T> Promise<T> interruptible(Promise<T> promise) {
        return BlockingIO.interruptiblePromise(promise);
    }

    /**
     * Applies the {@link BlockingIO} interruptible decorator to a {@link DependentPromise}, 
     * preserving the dependent API surface.
     */
    public static <T> DependentPromise<T> interruptible(DependentPromise<T> promise) {
        return BlockingIO.interruptibleDependentPromise(promise);
    }

    // ========================================================================
    // Internal Helpers
    // ========================================================================

    private static <T> Promise<T> unwrap(Promise<T> p) {
        return p.unwrap();
    }
}