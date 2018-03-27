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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

class ExecutorBoundCompletionStage<T> implements CompletionStage<T> {
    private final CompletionStage<T> delegate;
    private final Executor defaultExecutor;
    
    ExecutorBoundCompletionStage(CompletionStage<T> delegate, Executor defaultExecutor) {
        this.delegate = delegate;
        this.defaultExecutor = defaultExecutor;
    }

    protected <U> CompletionStage<U> wrap(CompletionStage<U> original) {
        return new ExecutorBoundCompletionStage<>(original, defaultExecutor);
    }
    
    public <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn) {
        return wrap(delegate.thenApply(fn));
    }

    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, defaultExecutor);
    }

    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return wrap(delegate.thenApplyAsync(fn, executor));
    }

    public CompletionStage<Void> thenAccept(Consumer<? super T> action) {
        return wrap(delegate.thenAccept(action));
    }

    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, defaultExecutor);
    }

    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return wrap(delegate.thenAcceptAsync(action, executor));
    }

    public CompletionStage<Void> thenRun(Runnable action) {
        return wrap(delegate.thenRun(action));
    }

    public CompletionStage<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, defaultExecutor);
    }

    public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
        return wrap(delegate.thenRunAsync(action, executor));
    }

    public <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return wrap(delegate.thenCombine(other, fn));
    }

    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultExecutor);
    }

    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn, 
                                              Executor executor) {
        
        return wrap(delegate.thenCombineAsync(other, fn, executor));
    }

    public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return wrap(delegate.thenAcceptBoth(other, action));
    }

    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultExecutor);
    }

    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action, 
                                                 Executor executor) {
        
        return wrap(delegate.thenAcceptBothAsync(other, action, executor));
    }

    public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterBoth(other, action));
    }

    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultExecutor);
    }

    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, 
                                           Runnable action, 
                                           Executor executor) {
        return wrap(delegate.runAfterBothAsync(other, action, executor));
    }

    public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return wrap(delegate.applyToEither(other, fn));
    }

    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, defaultExecutor);
    }

    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                             Function<? super T, U> fn,
                                             Executor executor) {
        
        return wrap(delegate.applyToEitherAsync(other, fn, executor));
    }

    public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return wrap(delegate.acceptEither(other, action));
    }

    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultExecutor);
    }

    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                           Consumer<? super T> action,
                                           Executor executor) {
        
        return wrap(delegate.acceptEitherAsync(other, action, executor));
    }

    public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterEither(other, action));
    }

    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultExecutor);
    }

    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, 
                                             Runnable action, 
                                             Executor executor) {
        return wrap(delegate.runAfterEitherAsync(other, action, executor));
    }

    public <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return wrap(delegate.thenCompose(fn));
    }

    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, defaultExecutor);
    }

    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return wrap(delegate.thenComposeAsync(fn, executor));
    }

    public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return wrap(delegate.exceptionally(fn));
    }

    public CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return wrap(delegate.whenComplete(action));
    }

    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, defaultExecutor);
    }

    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return wrap(delegate.whenCompleteAsync(action, executor));
    }

    public <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return wrap(delegate.handle(fn));
    }

    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, defaultExecutor);
    }

    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return wrap(delegate.handleAsync(fn, executor));
    }

    public CompletableFuture<T> toCompletableFuture() {
        return delegate.toCompletableFuture();
    }    
}
