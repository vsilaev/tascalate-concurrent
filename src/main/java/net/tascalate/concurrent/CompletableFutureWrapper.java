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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import net.tascalate.concurrent.decorators.CompletableFutureDecorator;

public class CompletableFutureWrapper<T> extends CompletableFutureDecorator<T> {

    protected CompletableFutureWrapper() {
        super();
    }
    
    protected CompletableFutureWrapper(CompletableFuture<T> delegate) {
        super(delegate);
    }

    protected boolean success(T value) {
        return onSuccess(value);
    }
    
    @Deprecated
    protected boolean onSuccess(T value) {
        return delegate.complete(value);
    }
    
    protected boolean failure(Throwable ex) {
        return onFailure(ex);
    }

    @Deprecated
    protected boolean onFailure(Throwable ex) {
        return delegate.completeExceptionally(ex);
    }    
    
    boolean complete(T value, Throwable ex) {
        return null == ex ? success(value) : failure(ex);
    }
    
    @Override
    public CompletableFuture<T> toCompletableFuture() {
        // Return concrete subclass that neither completes nor cancels this wrapper
        return (CompletableFuture<T>)delegate.thenApply(Function.identity());
    }
    
    // Report self-origin
    @Override
    public CompletionStage<T> α() {
        return this;
    }
    
    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return new CompletableFutureWrapper<>((CompletableFuture<U>)original);
    }

    // By default CompletableFuture doesn't interrupt a promise 
    // from thenCompose(fn) and exceptionallyCompose!
    @Override
    public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        CompletionStageRef<U> ref = new CompletionStageRef<>();
        return super.thenCompose(ref.captureResult(fn)).onCancel(ref.cancel);
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        CompletionStageRef<U> ref = new CompletionStageRef<>();
        return super.thenComposeAsync(ref.captureResult(fn)).onCancel(ref.cancel);
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, 
                                           Executor executor) {
        CompletionStageRef<U> ref = new CompletionStageRef<>();
        return super.thenComposeAsync(ref.captureResult(fn), executor).onCancel(ref.cancel);        
    }
    
    // Default CompletionStage API implementation for exceptionallyAsync / exceptionallyCompose[Async]
    // doesn't handle cancellation well due to numerous orchestrated calls (handle->handleAsync->thenCompose
    // Use own implementation here for safety
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return PromiseHelper.exceptionallyAsync(this, fn);
    }
    
    @Override
    public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return PromiseHelper.exceptionallyAsync(this, fn, executor);
    }
    
    @Override
    public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return PromiseHelper.exceptionallyCompose(this, fn);
    }

    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return PromiseHelper.exceptionallyComposeAsync(this, fn);
    }
    
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                Executor executor) {
        return PromiseHelper.exceptionallyComposeAsync(this, fn, executor);
    }   
}
