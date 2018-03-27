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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/**
 * The {@link CompletablePromise} is an adapter of a {@link CompletableFuture} to the {@link Promise} API 
 * 
 * @author vsilaev
 *
 * @param <T>
 *   a type of the successfully resolved promise value   
 */
public class CompletablePromise<T> extends CompletableFutureWrapper<T> {

    public CompletablePromise() {
        this(new CompletableFuture<>());
    }

    public CompletablePromise(CompletableFuture<T> delegate) {
        super(delegate);
    }

    protected boolean onSuccess(T value) {
        return delegate.complete(value);
    }

    protected boolean onFailure(Throwable ex) {
        return delegate.completeExceptionally(ex);
    }

    public Promise<T> completeAsync(Supplier<? extends T> supplier) {
        return completeAsync(supplier, ForkJoinPool.commonPool());
    }
    
    public Promise<T> completeAsync(Supplier<? extends T> supplier, Executor executor) {
        CompletableTask.supplyAsync(supplier, executor).thenAccept(this::onSuccess);
        return this;
    }

    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return new CompletablePromise<>((CompletableFuture<U>)original);
    }
}
