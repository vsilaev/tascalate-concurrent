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

import static net.tascalate.concurrent.SharedFunctions.iif;

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

    public boolean complete​(T value) {
        return success(value);
    }
    
    public boolean  completeExceptionally​(Throwable ex) {
        return failure(ex);
    }
    
    public Promise<T> completeAsync(Supplier<? extends T> supplier) {
        Promise<T> result = 
        this.dependent()
            .acceptEitherAsync(CompletableFuture.supplyAsync(supplier, ForkJoinPool.commonPool()), this::success, PromiseOrigin.PARAM_ONLY)
            .thenApply(__ -> this.join(), true);
        
        result.whenComplete((r, e) -> iif(null == e ? false : failure(e)));
        
        return result.unwrap();
    }
    
    public Promise<T> completeAsync(Supplier<? extends T> supplier, Executor executor) {
        Promise<T> result = CompletableTask.submit(supplier::get, executor);
        result.whenComplete((r, e) -> iif(null == e ? success(r) : failure(e)));
        return result;
    }
    
    // public CompletablePromise<T> copy();
    // public CompletionStage<T> minimalCompletionStage();

    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return new CompletablePromise<>((CompletableFuture<U>)original);
    }
}
