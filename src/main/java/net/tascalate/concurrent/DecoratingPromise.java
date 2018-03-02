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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DecoratingPromise<T, D extends Promise<T>> extends AbstractDelegatingPromise<T, D> {
    
    public DecoratingPromise(D delegate) {
        super(delegate);
    }

    protected Runnable wrapArgument(Runnable original) {
        return original;
    }
    
    protected <U, R> Function<U, R> wrapArgument(Function<U, R> original) {
        return original;
    }
    
    protected <U> Consumer<U> wrapArgument(Consumer<U> original) {
        return original;
    }
    
    protected <U> Supplier<U> wrapArgument(Supplier<U> original) {
        return original;
    }
    
    protected <U, V, R> BiFunction<U, V, R> wrapArgument(BiFunction<U, V, R> original) {
        return original;
    }
    
    protected <U, V> BiConsumer<U, V> wrapArgument(BiConsumer<U, V> original) {
        return original;
    }
    
    protected <U> CompletionStage<U> wrapArgument(CompletionStage<U> original) {
        return original;
    }
    
    protected Executor wrapArgument(Executor original) {
        return original;
    }
    
    protected <U> Promise<U> wrapResult(CompletionStage<U> original) {
        return new DecoratingPromise<>((Promise<U>)original);
    }
    
    @Override
    protected final <U> Promise<U> wrap(CompletionStage<U> original) {
        return wrapResult(original);
    }
    
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit) {
        return super.onTimeout(wrapArgument(supplier), timeout, unit);
    }
    
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, long timeout, TimeUnit unit, boolean cancelOnTimeout) {
        return super.onTimeout(wrapArgument(supplier), timeout, unit, cancelOnTimeout);
    }
    
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration) {
        return super.onTimeout(wrapArgument(supplier), duration);
    }
    
    @Override
    public Promise<T> onTimeout(Supplier<? extends T> supplier, Duration duration, boolean cancelOnTimeout) {
        return super.onTimeout(wrapArgument(supplier), duration, cancelOnTimeout);
    }

    @Override
    public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
        return super.thenApply(wrapArgument(fn));
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return super.thenApplyAsync(wrapArgument(fn));
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return super.thenApplyAsync(wrapArgument(fn), wrapArgument(executor));
    }

    @Override
    public Promise<Void> thenAccept(Consumer<? super T> action) {
        return super.thenAccept(wrapArgument(action));
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return super.thenAcceptAsync(wrapArgument(action));
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return super.thenAcceptAsync(wrapArgument(action), wrapArgument(executor));
    }

    @Override
    public Promise<Void> thenRun(Runnable action) {
        return super.thenRun(wrapArgument(action));
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action) {
        return super.thenRunAsync(wrapArgument(action));
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
        return super.thenRunAsync(wrapArgument(action), wrapArgument(executor));
    }

    @Override
    public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return super.thenCombine(wrapArgument(other), wrapArgument(fn));
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return super.thenCombineAsync(wrapArgument(other), wrapArgument(fn));
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {
        return super.thenCombineAsync(wrapArgument(other), wrapArgument(fn), wrapArgument(executor));
    }

    @Override
    public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return super.thenAcceptBoth(wrapArgument(other), wrapArgument(action));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return super.thenAcceptBothAsync(wrapArgument(other), wrapArgument(action));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action, 
                                                 Executor executor) {
        return super.thenAcceptBothAsync(wrapArgument(other), wrapArgument(action), wrapArgument(executor));
    }

    @Override
    public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return super.runAfterBoth(wrapArgument(other), wrapArgument(action));
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return super.runAfterBothAsync(wrapArgument(other), wrapArgument(action));
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, 
                                           Runnable action, 
                                           Executor executor) {
        return super.runAfterBothAsync(wrapArgument(other), wrapArgument(action), wrapArgument(executor));
    }

    @Override
    public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return super.applyToEither(wrapArgument(other), wrapArgument(fn));
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return super.applyToEitherAsync(wrapArgument(other), wrapArgument(fn));
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor) {
        return super.applyToEitherAsync(wrapArgument(other), wrapArgument(fn), wrapArgument(executor));
    }

    @Override
    public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return super.acceptEither(wrapArgument(other), wrapArgument(action));
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return super.acceptEitherAsync(wrapArgument(other), wrapArgument(action));
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                           Consumer<? super T> action,
                                           Executor executor) {
        return super.acceptEitherAsync(wrapArgument(other), wrapArgument(action), wrapArgument(executor));
    }

    @Override
    public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return super.runAfterEither(wrapArgument(other), wrapArgument(action));
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return super.runAfterEitherAsync(wrapArgument(other), wrapArgument(action));
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor) {
        return super.runAfterEitherAsync(wrapArgument(other), wrapArgument(action), wrapArgument(executor));
    }

    @Override
    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return super.thenCompose(wrapArgument(fn));
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return super.thenComposeAsync(wrapArgument(fn));
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return super.thenComposeAsync(wrapArgument(fn), wrapArgument(executor));
    }

    @Override
    public Promise<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return super.exceptionally(wrapArgument(fn));
    }

    @Override
    public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return super.whenComplete(wrapArgument(action));
    }

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return super.whenCompleteAsync(wrapArgument(action));
    }

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return super.whenCompleteAsync(wrapArgument(action), wrapArgument(executor));
    }

    @Override
    public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return super.handle(wrapArgument(fn));
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return super.handleAsync(wrapArgument(fn));
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return super.handleAsync(wrapArgument(fn), wrapArgument(executor));
    }
}
