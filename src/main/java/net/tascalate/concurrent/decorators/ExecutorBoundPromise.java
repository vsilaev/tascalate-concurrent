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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import net.tascalate.concurrent.Promise;

public class ExecutorBoundPromise<T> extends AbstractPromiseDecorator<T, Promise<T>> {

    private final Executor defaultExecutor;
    
    public ExecutorBoundPromise(Promise<T> delegate, Executor defaultExecutor) {
        super(delegate);
        this.defaultExecutor = defaultExecutor;
    }
    
    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return new ExecutorBoundPromise<>((Promise<U>)original, defaultExecutor);
    }
    
    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, defaultExecutor);
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAcceptAsync(action, defaultExecutor);
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action) {
        return thenRunAsync(action, defaultExecutor);
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, defaultExecutor);
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, defaultExecutor);
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, defaultExecutor);
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, defaultExecutor);
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, defaultExecutor);
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, defaultExecutor);
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn, defaultExecutor);
    }

    @Override
    public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action, defaultExecutor);
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, defaultExecutor);
    }
}
