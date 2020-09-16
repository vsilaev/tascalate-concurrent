/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

import static net.tascalate.concurrent.SharedFunctions.unwrapExecutionException;
import static net.tascalate.concurrent.SharedFunctions.wrapCompletionException;

import java.time.Duration;
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

import net.tascalate.concurrent.decorators.ExecutorBoundPromise;

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
    
    default T getNow(T valueIfAbsent) throws CancellationException, CompletionException {
        return getNow(SharedFunctions.supply(valueIfAbsent));
    }
    
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
    
    default T join() throws CancellationException, CompletionException {
        try {
            return get();
        } catch (InterruptedException ex) {
            throw new CompletionException(ex);
        } catch (ExecutionException ex) {
            throw wrapCompletionException(unwrapExecutionException(ex));
        }
    }

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
    
    default Promise<T> onCancel(Runnable code) {
        return new ExtraCancellationPromise<>(this, code);
    }

    default Promise<T> delay(long timeout, TimeUnit unit) {
        return delay(timeout, unit, true);
    }
    
    default Promise<T> delay(long timeout, TimeUnit unit, boolean delayOnError) {
        return delay(Timeouts.toDuration(timeout, unit), delayOnError);
    }
    
    default Promise<T> delay(Duration duration) {
        return delay(duration, true);
    }
    
    default Promise<T> delay(Duration duration, boolean delayOnError) {
        return dependent().delay(duration, delayOnError).unwrap();
    }
    
    default Promise<T> orTimeout(long timeout, TimeUnit unit) {
        return orTimeout(timeout, unit, true);
    }
    
    default Promise<T> orTimeout(long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return orTimeout(Timeouts.toDuration(timeout, unit), cancelOnTimeout);
    }
    
    default Promise<T> orTimeout(Duration duration) {
        return orTimeout(duration, true);
    }
    
    default Promise<T> orTimeout(Duration duration, boolean cancelOnTimeout) {
        return dependent().orTimeout(duration, cancelOnTimeout).unwrap();
    }
    
    default Promise<T> onTimeout(T value, long timeout, TimeUnit unit) {
        return onTimeout(value, timeout, unit, true);
    }
    
    default Promise<T> onTimeout(T value, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return onTimeout(value, Timeouts.toDuration(timeout, unit), cancelOnTimeout);
    }

    default Promise<T> onTimeout(T value, Duration duration) {
        return onTimeout(value, duration, true);
    }
    
    default Promise<T> onTimeout(T value, Duration duration, boolean cancelOnTimeout) {
        return dependent().onTimeout(value, duration, cancelOnTimeout).unwrap();
    }
    
    default Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit) {
        return onTimeout(supplier, timeout, unit, true);
    }
    
    default Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return onTimeout(supplier, Timeouts.toDuration(timeout, unit), cancelOnTimeout);
    }
    
    default Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return onTimeout(supplier, duration, true);
    }
    
    default Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return dependent().onTimeout(supplier, duration, cancelOnTimeout).unwrap();
    }
    
    /**
     * Converts this {@link Promise} to a {@link DependentPromise}
     * The returned DependentPromise does not implicitly enlist any {@link CompletionStage}
     * for cancellation (neither self, nor passed as arguments to combining methods); 
     * only enlisting via explicit parameter is supported
     * 
     * @return
     * created DependentPromise
     */
    default DependentPromise<T> dependent() {
    	return DependentPromise.from(this);
    }
    
    /**
     * Converts this {@link Promise} to a {@link DependentPromise}
     * The returned DependentPromise does implicitly enlist {@link CompletionStage}
     * for cancellation (either self, and/or passed as arguments to combining methods)  
     * according to <code>defaultEnlistOptions</code> parameter
     * 
     * @param defaultEnlistOptions
     *   defines what {@link CompletionStage} should be enlisted implicitly for cancellation
     * @return
     * created DependentPromise
     */
    default DependentPromise<T> dependent(Set<PromiseOrigin> defaultEnlistOptions) {
        return DependentPromise.from(this, defaultEnlistOptions);
    }
    
    default Promise<T> defaultAsyncOn(Executor executor) {
        return new ExecutorBoundPromise<>(this, executor);
    }
    
    /**
     * Decorate this {@link Promise} with a decorator specified
     * @param <D>
     *   type of the actual promise decorator
     * @param decoratorFactory
     *   a factory to create a concrete decorator
     * @return
     *   a decorator created
     */
    default <D> D as(Function<? super Promise<T>, D> decoratorFactory) {
        return decoratorFactory.apply(this);
    }

    /**
     * Unwraps underlying {@link Promise} if it was decorated
     * @return
     *   the underlying un-decorated {@link Promise} or self if not decorated
     */
    default Promise<T> unwrap() {
        return this;
    }
    
    /**
     * Fully unwraps underlying {@link Promise}
     * @return
     *   the underlying un-decorated {@link Promise} or self if not decorated
     */
    default Promise<T> raw() {
        return this;
    }
    
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
    
    default Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return dependent().exceptionallyAsync(fn).unwrap();
    }
    
    default Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return dependent().exceptionallyAsync(fn, executor).unwrap();
    }
    
    default Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return dependent().exceptionallyCompose(fn).unwrap();
    }
    
    default Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return dependent().exceptionallyComposeAsync(fn).unwrap();
    }

    default Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return dependent().exceptionallyComposeAsync(fn, executor).unwrap();
    }

    default Promise<T> thenFilter(Predicate<? super T> predicate) {
        return dependent().thenFilter(predicate).unwrap();
    }
    
    default Promise<T> thenFilter(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return dependent().thenFilter(predicate, errorSupplier).unwrap();
    }
    
    default Promise<T> thenFilterAsync(Predicate<? super T> predicate) {
        return dependent().thenFilterAsync(predicate).unwrap();
    }
    
    default Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier) {
        return dependent().thenFilterAsync(predicate, errorSupplier).unwrap();
    }
    
    default Promise<T> thenFilterAsync(Predicate<? super T> predicate, Executor executor) {
        return dependent().thenFilterAsync(predicate, executor).unwrap();
    }
    
    default Promise<T> thenFilterAsync(Predicate<? super T> predicate, Function<? super T, Throwable> errorSupplier, Executor executor) {
        return dependent().thenFilterAsync(predicate, errorSupplier, executor).unwrap();
    }
    
    Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action);

    Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor);

    <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

    <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn);

    <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor);
 }
