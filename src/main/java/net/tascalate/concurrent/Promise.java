/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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
import java.util.function.Supplier;


/**
 * <p>{@link Promise} is a combination of the {@link CompletionStage} and {@link Future} contracts.
 * It provides both composition methods of the former and blocking access methods of the later.
 * <p>Every composition method derived from the {@link CompletionStage} interface is overridden to
 * return a new Promise;
 * @author vsilaev
 *
 * @param <T>
 *   a type of the successfully resolved promise value   
 */
public interface Promise<T> extends Future<T>, CompletionStage<T> {
    
    default public T getNow(T valueIfAbsent) {
        return getNow(() -> valueIfAbsent);
    }
    
    default public T getNow(Supplier<T> valueIfAbsent) {
        if (isDone()) {
            try {
                return get();
            } catch (InterruptedException ex) {
                // Should not happen when isDone() returns true
                throw new RuntimeException(ex);
            } catch (ExecutionException ex) {
                throw new CompletionException(null != ex.getCause() ? ex.getCause() : ex);
            }
        } else {
            return valueIfAbsent.get();
        }
    }
    
    default Promise<T> orTimeout(long timeout, TimeUnit unit) {
        return orTimeout(timeout, unit, true);
    }
    
    default Promise<T> orTimeout(long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return orTimeout(Promises.toDuration(timeout, unit), cancelOnTimeout);
    }
    
    default Promise<T> orTimeout(Duration duration) {
        return orTimeout(duration, true);
    }
    
    default Promise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        Promise<T> onTimeout = Promises.failAfter(duration);
        // Use *async to execute on default "this" executor
        return Promises.dependent(this)
            .applyToEitherAsync(onTimeout, Function.identity(), PromiseOrigin.PARAM_ONLY)
            .whenComplete((v, e) -> {
                if (cancelOnTimeout) {
                    cancel(true);
                }
                onTimeout.cancel(true); 
            }, 
            true
        );
    }
    
    default Promise<T> onTimeout(T value, long timeout, TimeUnit unit) {
        return onTimeout(value, timeout, unit, true);
    }
    
    default Promise<T> onTimeout(T value, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return onTimeout(value, Promises.toDuration(timeout, unit));
    }

    default Promise<T> onTimeout(T value, Duration duration) {
        return onTimeout(value, duration, true);
    }
    
    default Promise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout) {
        return onTimeout(() -> value, duration, cancelOnTimeout);
    }
    
    default Promise<T> onTimeout(Supplier<T> supplier, long timeout, TimeUnit unit) {
        return onTimeout(supplier, timeout, unit, true);
    }
    
    default Promise<T> onTimeout(Supplier<T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return onTimeout(supplier, Promises.toDuration(timeout, unit), cancelOnTimeout);
    }
    
    default Promise<T> onTimeout(Supplier<T> supplier, Duration duration) {
        return onTimeout(supplier, duration, true);
    }
    
    default Promise<T> onTimeout(Supplier<T> supplier, Duration duration, boolean cancelOnTimeout) {
        Function<T, Supplier<T>> valueToSupplier = v -> () -> v;
        
        // timeout converted to supplier
        Promise<Supplier<T>> onTimeout = Promises.dependent(Promises.delay(duration)).thenApply(d -> supplier, true);
        
        return Promises.dependent(this)
            // resolved value converted to supplier
            .thenApply(valueToSupplier, false)
            // Use *async to execute on default "this" executor
            .applyToEitherAsync(onTimeout, Supplier::get, PromiseOrigin.ALL)
            .whenComplete((v, e) -> {
                if (cancelOnTimeout) {
                    cancel(true);
                }
                onTimeout.cancel(true);
            }, true);
    }
    
    public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn);

    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn);

    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor);
    
    public Promise<Void> thenAccept(Consumer<? super T> action);

    public Promise<Void> thenAcceptAsync(Consumer<? super T> action);

    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor);

    public Promise<Void> thenRun(Runnable action);

    public Promise<Void> thenRunAsync(Runnable action);

    public Promise<Void> thenRunAsync(Runnable action, Executor executor);

    public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor);

    public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action);
    
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action);

    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action, 
                                                 Executor executor);

    public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action);

    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action);

    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor);

    public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn);
    
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn);

    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor);

    public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action);

    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action);

    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                           Consumer<? super T> action,
                                           Executor executor);

    public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action);

    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action);

    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor);

    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn);

    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor);

    public Promise<T> exceptionally(Function<Throwable, ? extends T> fn);

    public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action);

    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor);

    public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn);

    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor);

 }
