/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
import static net.tascalate.concurrent.SharedFunctions.unwrapCompletionException;
import static net.tascalate.concurrent.SharedFunctions.wrapCompletionException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import net.tascalate.concurrent.core.CompletionStageAPI;
import net.tascalate.concurrent.decorators.AbstractCompletionStageDecorator;

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

    public boolean complete(T value) {
        return success(value);
    }
    
    public boolean completeExceptionally(Throwable ex) {
        return failure(ex);
    }
    
    @Override
    public boolean complete(T value, Throwable ex) {
        return super.complete(value, ex);
    }
    
    public Promise<T> completeAsync(Supplier<? extends T> supplier) {
        CompletionStageAPI api = CompletionStageAPI.current();
        Promise<T> result = completeAsync(supplier, api.defaultExecutorOf(delegate));
        if (api.defaultExecutorOverridable() || delegate.getClass() == CompletableFuture.class) {
            return result; // use what is provided
        } else {
            // defaultExecutor might be changed in ad-hoc manner via subclassing
            // let us take back to the actual default executor
            return result.dependent()
                         .thenApplyAsync(Function.identity(), true) 
                         .unwrap();
        }
    }
    
    public Promise<T> completeAsync(Supplier<? extends T> supplier, Executor executor) {
        Promise<T> result = CompletableTask.submit(supplier::get, executor);
        result.whenComplete((r, e) -> {
            // Complete only if transition was not cancelled
            if (!result.isCancelled()) {
                complete(r, unwrapCompletionException(e));
            }
        });
        return result;
    }
    
    public CompletablePromise<T> copy() {
        CompletablePromise<T> result = wrapNew(new CompletableFuture<>());
        if (isDone()) {
            try {
                result.complete(join());
            } catch (CompletionException ex) {
                // Don't unwrap according API docs
                result.completeExceptionally(ex);
            } catch (Throwable ex) {
                // Wrap for CancellationException and unexpected exceptions
                result.completeExceptionally(wrapCompletionException(ex));
            }
            return result;
        } else {
            whenComplete(result::complete);
            return result;
        }
    }
    
    public Promise<T> minimalPromise() {
        CompletableFutureWrapper<T> result = new CompletableFutureWrapper<>();
        // Should we cancel result along this?
        if (isDone()) {
            try {
                result.success(join());
            } catch (CompletionException ex) {
                // Don't unwrap according API docs
                result.failure(ex);
            } catch (Throwable ex) {
                // Wrap for CancellationException and unexpected exceptions
                result.failure(wrapCompletionException(ex));
            }
            return result;            
        } else {
            whenComplete(result::complete);
            return result;
        }
    }
    
    public CompletionStage<T> minimalCompletionStage() {
        // Should pass this, not delegate - while delegate has invalid thenCompose impl.
        return new MinimalCompletionStage<>(this);
    }
    
    @Override
    public CompletableFuture<T> toCompletableFuture() {
        // Returns the delegate itself, so this CompletablePromise may be completed / cancelled
        return delegate;
    }
    
    @Override
    public CompletionStage<T> α() {
        return delegate;
    }

    @Override
    protected <U> CompletablePromise<U> wrapNew(CompletionStage<U> original) {
        return new CompletablePromise<>((CompletableFuture<U>)original);
    }
    
    static class MinimalCompletionStage<T> extends AbstractCompletionStageDecorator<T, CompletionStage<T>> {
        public MinimalCompletionStage(CompletionStage<T> delegate) {
            super(delegate);
        }

        @Override
        protected <U> CompletionStage<U> wrapNew(CompletionStage<U> original) {
            return new MinimalCompletionStage<>(original);
        }
        
        @Override
        public CompletableFuture<T> toCompletableFuture() {
            // Should be independent future according to existing Java impl. 
            // http://hg.openjdk.java.net/jdk9/jdk9/jdk/file/tip/src/java.base/share/classes/java/util/concurrent/CompletableFuture.java 
            // - MinimalCompletionStage
            CompletableFuture<T> result = new CompletableFuture<>();
            whenComplete((r, e) -> iif(null == e ? result.complete(r) : result.completeExceptionally(e)));
            return result;
        }
    }
}
