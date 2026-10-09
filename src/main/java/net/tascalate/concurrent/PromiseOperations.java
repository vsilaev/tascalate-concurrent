/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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

public class PromiseOperations {
    private PromiseOperations() {}

    // Lifted is somewhat questionable, but here it exists for symmetry with dropped()
    public static <T> Promise<Promise<T>> lift(CompletionStage<? extends T> promise) {
        return lift(Promises.from(promise));
    }
    
    public static <T> Promise<Promise<T>> lift(Promise<? extends T> promise) {
        return promise.dependent()
                      .thenApply(Promises::<T>success, true)
                      .unwrap();
    }
    
    public static <T> Promise<T> drop(CompletionStage<? extends CompletionStage<T>> promise) {
        return drop(Promises.from(promise));
    }
    
    public static <T> Promise<T> drop(Promise<? extends CompletionStage<T>> promise) {
        return promise.dependent()
                      .thenCompose(Promises::from, true)
                      .unwrap();
    }

    public static <T> Promise<Stream<T>> asStream(CompletionStage<? extends T> promise) {
        return asStream(Promises.from(promise));
    }

    public static <T> Promise<Stream<T>> asStream(Promise<? extends T> promise) {
        return promise.dependent()
                      .handle((r, e) -> null == e ? Stream.<T>of(r) : Stream.<T>empty(), true)
                      .unwrap();
    }

    public static <T> Promise<Optional<T>> asOptional(CompletionStage<? extends T> promise) {
        return asOptional(Promises.from(promise));
    }
    
    public static <T> Promise<Optional<T>> asOptional(Promise<? extends T> promise) {
        return promise.dependent()
                      .handle((r, e) -> Optional.<T>ofNullable(null == e ? r : null), true)
                      .unwrap();
    }
    
    public static <T> Promise<Try<T>> asTry(CompletionStage<T> promise) {
        return asTry(Promises.from(promise));
    }
    
    public static <T> Promise<Try<T>> asTry(Promise<T> promise) {
        return Try.lift(promise);
    }
    
    public static <T, F extends Promise<T>> F peek(F promise, Consumer<? super F> fn) {
        fn.accept(promise);
        return promise;
    }
    
    public static <T, F extends Promise<T>> Function<F, F> peek(Consumer<? super F> fn) {
        return p -> peek(p, fn);
    }
    
    public static <T, R extends AutoCloseable> Promise<T> 
        tryApply(Promise<R> promise, Function<? super R, ? extends T> fn) { 
        return unwrap(Promises.tryApply(promise.dependent(PromiseOrigin.ALL), fn));
    }
    
    public static <T, R extends AutoCloseable> Function<Promise<R>, Promise<T>> 
        tryApply(Function<? super R, ? extends T> fn) {
        return p -> tryApply(p, fn);
    }
    
    public static <T, R extends AsyncCloseable> Promise<T> 
        tryApplyEx(Promise<R> promise, Function<? super R, ? extends T> fn) {
        return unwrap(Promises.tryApplyEx(promise.dependent(PromiseOrigin.ALL), fn));
    }
    
    public static <T, R extends AsyncCloseable> Function<Promise<R>, Promise<T>> 
        tryApplyEx(Function<? super R, ? extends T> fn) {
        return p -> tryApplyEx(p, fn);
    }
    
    public static <T, R extends AutoCloseable> Promise<T> 
        tryCompose(Promise<R> promise, Function<? super R, ? extends CompletionStage<T>> fn) {
        return unwrap(Promises.tryCompose(promise.dependent(PromiseOrigin.ALL), fn));
    }
    
    public static <T, R extends AutoCloseable> Function<Promise<R>, Promise<T>> 
        tryCompose(Function<? super R, ? extends CompletionStage<T>> fn) {
        return p -> tryCompose(p, fn);
    }
    
    public static <T, R extends AsyncCloseable> Promise<T> 
        tryComposeEx(Promise<R> promise, Function<? super R, ? extends CompletionStage<T>> fn) {
        return unwrap(Promises.tryComposeEx(promise.dependent(PromiseOrigin.ALL), fn));
    }

    public static <T, R extends AsyncCloseable> Function<Promise<R>, Promise<T>> 
        tryComposeEx(Function<? super R, ? extends CompletionStage<T>> fn) {
        return p -> tryComposeEx(p, fn);
    }
    
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
    
    public static <S, T, A, R> Function<Promise<Iterable<S>>, Promise<R>> 
        chunkedItems(int batchSize, 
                     Function<? super S, CompletionStage<? extends T>> spawner,
                     Collector<T, A, R> downstream) {
        
        return p -> chunkedItems(p, batchSize,  spawner, downstream);
    }
    
    
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
    
    public static <S, T, A, R> Function<Promise<Iterable<S>>, Promise<R>> 
        chunkedItems(int batchSize, 
                     Function<? super S, CompletionStage<? extends T>> spawner, 
                     Collector<T, A, R> downstream,
                     Executor downstreamExecutor) {
        
        return p -> chunkedItems(p, batchSize, spawner, downstream, downstreamExecutor);
    }
    
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

    public static <S, T, A, R> Function<Promise<Stream<S>>, Promise<R>> 
        chunkedStream(int batchSize, 
                      Function<? super S, CompletionStage<? extends T>> spawner,
                      Collector<T, A, R> downstream) {
    
        return p -> chunkedStrem(p, batchSize,  spawner, downstream);
    }
    
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

    public static <S, T, A, R> Function<Promise<Stream<S>>, Promise<R>> 
        chunkedStream(int batchSize, 
                      Function<? super S, CompletionStage<? extends T>> spawner,
                      Collector<T, A, R> downstream,
                      Executor downstreamExecutor) {

        return p -> chunkedStrem(p, batchSize,  spawner, downstream, downstreamExecutor);
    }

    private static <T> Promise<T> unwrap(Promise<T> p) {
        return p.unwrap();
    }
}
