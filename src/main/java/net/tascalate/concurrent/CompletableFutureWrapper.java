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

public class CompletableFutureWrapper<T> 
    extends CompletableFutureDecorator<T> {

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
    
    @Override
    public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        CompletionStageRef<T> ref = new CompletionStageRef<>();
        return super.exceptionallyCompose(ref.captureResult(fn)).onCancel(ref.cancel);
    }

    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        CompletionStageRef<T> ref = new CompletionStageRef<>();
        return super.exceptionallyComposeAsync(ref.captureResult(fn)).onCancel(ref.cancel);
    }
    
    @Override
    public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, 
                                                Executor executor) {
        CompletionStageRef<T> ref = new CompletionStageRef<>();
        return super.exceptionallyComposeAsync(ref.captureResult(fn), executor).onCancel(ref.cancel);        
    }
}
