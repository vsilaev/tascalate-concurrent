/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.core;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Function;

import net.tascalate.concurrent.Promise;

final class CurrentCompletionStageAPI implements CompletionStageAPI {

    private CurrentCompletionStageAPI() {
        
    }
    
    @Override
    public boolean defaultExecutorOverridable() {
        return true;
    }
    
    @Override
    public Executor defaultExecutorOf(CompletableFuture<?> completableFuture) {
        return completableFuture.defaultExecutor();
    }
    
    @Override
    public <T> CompletionStage<T> exceptionallyAsync(CompletionStage<T> delegate, 
                                                     Function<Throwable, ? extends T> fn) {
        return delegate.exceptionallyAsync(fn);        
    }
    
    @Override
    public <T> CompletionStage<T> exceptionallyAsync(CompletionStage<T> delegate, 
                                                     Function<Throwable, ? extends T> fn, Executor executor) {
        return delegate.exceptionallyAsync(fn, executor);        
    }
    
    @Override
    public <T> CompletionStage<T> exceptionallyCompose(CompletionStage<T> delegate, 
                                                       Function<Throwable, ? extends CompletionStage<T>> fn) {
        return delegate.exceptionallyCompose(fn);
    }
    
    @Override
    public <T> CompletionStage<T> exceptionallyComposeAsync(CompletionStage<T> delegate, 
                                                            Function<Throwable, ? extends CompletionStage<T>> fn) {
        return delegate.exceptionallyComposeAsync(fn);
    }
    
    @Override
    public <T> CompletionStage<T> exceptionallyComposeAsync(CompletionStage<T> delegate, 
                                                            Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return delegate.exceptionallyComposeAsync(fn, executor);
    }    
    
    @Override
    public boolean isInState(Future<?> promise, Promise.State state, boolean isDelegate) {
        if (null == state) {
            return false;
        } else {
            return stateOf(promise) == state;
        }
    }

    @Override
    public boolean isInState(Future<?> promise, Set<Promise.State> states, boolean isDelegate) {
        if (null == states || states.isEmpty()) {
            return false;
        } else {
            return states.contains(stateOf(promise));
        }
    }

    @Override
    public <T> T resultNow(Future<T> future, boolean isDelegate) {
        if (isDelegate) { 
            return future.resultNow();
        } else {
           return CompletionStageAPI.super.resultNow(future, isDelegate);
        }
    }

    @Override
    public Throwable exceptionNow(Future<?> future, boolean isDelegate) {
        if (isDelegate) { 
            return future.exceptionNow();
        } else {
           return CompletionStageAPI.super.exceptionNow(future, isDelegate);
        }
    }
    
    private static Promise.State stateOf(Future<?> future) {
        switch (future.state()) {
            case RUNNING:   return Promise.State.RUNNING;
            case SUCCESS:   return Promise.State.SUCCESS;
            case FAILED:    return Promise.State.FAILED;
            case CANCELLED: return Promise.State.CANCELLED;
            default: throw new IllegalArgumentException("Unknown state: " + future.state());
        }
    }
    
    static final CompletionStageAPI INSTANCE = new CurrentCompletionStageAPI();

}
