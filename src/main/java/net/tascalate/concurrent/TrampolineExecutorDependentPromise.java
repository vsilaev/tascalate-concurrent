/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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

import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import net.tascalate.concurrent.decorators.AbstractDependentPromiseDecorator;

final class TrampolineExecutorDependentPromise<T> extends AbstractDependentPromiseDecorator<T> {
    
    private TrampolineExecutorDependentPromise(DependentPromise<T> delegate) {
        super(delegate);
    }
    
    @Override
    protected <U> DependentPromise<U> wrap(CompletionStage<U> original) {
        // Wrapping is one-shot, so return original result
        return (DependentPromise<U>)original;
    }
    
    @Override
    public Promise<T> raw() {
        return TrampolineExecutorPromise.trampolineExecutor(delegate.raw());
    }

    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return thenApplyAsync(fn, enlistOrigin);
    }

    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action, boolean enlistOrigin) {
        return thenAcceptAsync(action, enlistOrigin);
    }

    @Override
    public DependentPromise<Void> thenRun(Runnable action, boolean enlistOrigin) {
        return thenRunAsync(action, enlistOrigin);
    }
    
    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, 
                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                  Set<PromiseOrigin> enlistOptions) {
        return thenCombineAsync(other, fn, enlistOptions);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, 
                                                     BiConsumer<? super T, ? super U> action,
                                                     Set<PromiseOrigin> enlistOptions) {
        return thenAcceptBothAsync(other, action, enlistOptions);
    }
    
    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return runAfterBothAsync(other, action, enlistOptions);
    }

    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, 
                                                 Function<? super T, U> fn,
                                                 Set<PromiseOrigin> enlistOptions) {
        return applyToEitherAsync(other, fn, enlistOptions);
    }

    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, 
                                               Consumer<? super T> action,
                                               Set<PromiseOrigin> enlistOptions) {
        return acceptEitherAsync(other, action, enlistOptions);
    }
    
    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return runAfterEitherAsync(other, action, enlistOptions);
    }

    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return thenComposeAsync(fn, enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return whenCompleteAsync(action, enlistOrigin);
    }

    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return handleAsync(fn, enlistOrigin);
    }

    @Override
    public <U> DependentPromise<U> thenApply(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn);
    }

    @Override
    public DependentPromise<Void> thenAccept(Consumer<? super T> action) {
        return thenAcceptAsync(action);
    }

    @Override
    public DependentPromise<Void> thenRun(Runnable action) {
        return thenRunAsync(action);
    }

    @Override
    public <U, V> DependentPromise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action);
    }

    @Override
    public DependentPromise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action);
    }

    @Override
    public <U> DependentPromise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn);
    }

    @Override
    public DependentPromise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action);
    }

    @Override
    public DependentPromise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action);
    }

    @Override
    public <U> DependentPromise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn);
    }

    @Override
    public DependentPromise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action);
    }

    @Override
    public <U> DependentPromise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn);
    }
    
    static <T> DependentPromise<T> trampolineExecutor(DependentPromise<T> origin) {
        return new TrampolineExecutorDependentPromise<>(origin);
    }
}
