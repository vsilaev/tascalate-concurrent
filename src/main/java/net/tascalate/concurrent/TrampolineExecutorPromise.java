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

import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import net.tascalate.concurrent.Promise;
import net.tascalate.concurrent.decorators.AbstractPromiseDecorator;

final class TrampolineExecutorPromise<T> extends AbstractPromiseDecorator<T, Promise<T>> {

    private TrampolineExecutorPromise(Promise<T> delegate) {
        super(delegate);
    }
    
    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        // Wrapping is one-shot, so return original result
        return (Promise<U>)original;
    }
    
    @Override
    public Promise<T> raw() {
        return new TrampolineExecutorPromise<>(delegate.raw());
    }
    
    @Override
    public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn);
    }

    @Override
    public Promise<Void> thenAccept(Consumer<? super T> action) {
        return thenAcceptAsync(action);
    }

    @Override
    public Promise<Void> thenRun(Runnable action) {
        return thenRunAsync(action);
    }

    @Override
    public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn);
    }

    @Override
    public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action);
    }

    @Override
    public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action);
    }

    @Override
    public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn);
    }

    @Override
    public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action);
    }

    @Override
    public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action);
    }

    @Override
    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsync(fn);
    }

    @Override
    public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsync(action);
    }

    @Override
    public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn);
    }
    
    static <T> Promise<T> trampolineExecutor(Promise<T> origin) {
        return new TrampolineExecutorPromise<>(origin);
    }
}
