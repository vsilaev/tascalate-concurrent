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
package net.tascalate.concurrent.decorators;

import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import net.tascalate.concurrent.DependentPromise;
import net.tascalate.concurrent.PromiseOrigin;

public class ExecutorBoundDependentPromise<T> extends AbstractDependentPromiseDecorator<T> {
    private final Executor defaultExecutor;
    
    public ExecutorBoundDependentPromise(DependentPromise<T> delegate, Executor defaultExecutor) {
        super(delegate);
        this.defaultExecutor = defaultExecutor;
    }
    
    @Override
    protected <U> DependentPromise<U> wrap(CompletionStage<U> original) {
        return new ExecutorBoundDependentPromise<>((DependentPromise<U>)original, defaultExecutor);
    }

    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn, boolean enlistOrigin) {
        return thenApplyAsync(fn, defaultExecutor, enlistOrigin);
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action, boolean enlistOrigin) {
        return thenAcceptAsync(action, defaultExecutor, enlistOrigin);
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action, boolean enlistOrigin) {
        return thenRunAsync(action, defaultExecutor, enlistOrigin);
    }
    
    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, 
                                                       BiFunction<? super T, ? super U, ? extends V> fn,
                                                       Set<PromiseOrigin> enlistOptions) {
        return thenCombineAsync(other, fn, defaultExecutor, enlistOptions);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, 
                                                          BiConsumer<? super T, ? super U> action,
                                                          Set<PromiseOrigin> enlistOptions) {
        return thenAcceptBothAsync(other, action, defaultExecutor, enlistOptions);
    }
    
    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return runAfterBothAsync(other, action, defaultExecutor, enlistOptions);
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, 
                                                      Function<? super T, U> fn,
                                                      Set<PromiseOrigin> enlistOptions) {
        return applyToEitherAsync(other, fn, defaultExecutor, enlistOptions);
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, 
                                                    Consumer<? super T> action,
                                                    Set<PromiseOrigin> enlistOptions) {
        return acceptEitherAsync(other, action, defaultExecutor, enlistOptions);
    }
    
    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Set<PromiseOrigin> enlistOptions) {
        return runAfterEitherAsync(other, action, defaultExecutor, enlistOptions);
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, boolean enlistOrigin) {
        return thenComposeAsync(fn, defaultExecutor, enlistOrigin);
    }
    
    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, boolean enlistOrigin) {
        return whenCompleteAsync(action, defaultExecutor, enlistOrigin);
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, boolean enlistOrigin) {
        return handleAsync(fn, defaultExecutor, enlistOrigin);
    }

    @Override
    public <U> DependentPromise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, defaultExecutor);
    }

    @Override
    public DependentPromise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, defaultExecutor);
    }

    @Override
    public DependentPromise<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, defaultExecutor);
    }

    @Override
    public <U, V> DependentPromise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultExecutor);
    }

    @Override
    public <U> DependentPromise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultExecutor);
    }

    @Override
    public DependentPromise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultExecutor);
    }

    @Override
    public <U> DependentPromise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, defaultExecutor);
    }

    @Override
    public DependentPromise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultExecutor);
    }

    @Override
    public DependentPromise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultExecutor);
    }

    @Override
    public <U> DependentPromise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, defaultExecutor);
    }

    @Override
    public DependentPromise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, defaultExecutor);
    }

    @Override
    public <U> DependentPromise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, defaultExecutor);
    }
}
