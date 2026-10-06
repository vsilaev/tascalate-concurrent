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
package net.tascalate.concurrent.core;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Function;

import net.tascalate.concurrent.Promise;

public interface CompletionStageAPI {

    boolean defaultExecutorOverridable();
    
    Executor defaultExecutorOf(CompletableFuture<?> completableFuture);
    
    default <T> CompletionStage<T> exceptionallyAsync(CompletionStage<T> delegate, 
                                                     Function<Throwable, ? extends T> fn) {
        return delegate.handle((r, ex) -> ex == null ? 
                               delegate : 
                               delegate.<T>handleAsync((r1, ex1) -> fn.apply(ex1)))
                       .thenCompose(Function.identity());        
    }
    
    default <T> CompletionStage<T> exceptionallyAsync(CompletionStage<T> delegate, 
                                                     Function<Throwable, ? extends T> fn, Executor executor) {
        return delegate.handle((r, ex) -> ex == null ? 
                               delegate : 
                               delegate.<T>handleAsync((r1, ex1) -> fn.apply(ex1), executor))
                       .thenCompose(Function.identity());        
    }
    
    default <T> CompletionStage<T> exceptionallyCompose(CompletionStage<T> delegate, 
                                                       Function<Throwable, ? extends CompletionStage<T>> fn) {
        return delegate.handle((r, ex) -> ex == null ? delegate : fn.apply(ex))
                       .thenCompose(Function.identity());
    }
    
    default <T> CompletionStage<T> exceptionallyComposeAsync(CompletionStage<T> delegate, 
                                                            Function<Throwable, ? extends CompletionStage<T>> fn) {
        return delegate.handle((r, ex) -> ex == null ? 
                               delegate : 
                               delegate.handleAsync((r1, ex1) -> fn.apply(ex1))
                                       .thenCompose(Function.identity()))
                       .thenCompose(Function.identity());
    }
    
    default <T> CompletionStage<T> exceptionallyComposeAsync(CompletionStage<T> delegate, 
                                                             Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return delegate.handle((r, ex) -> ex == null ? 
                               delegate : 
                               delegate.handleAsync((r1, ex1) -> fn.apply(ex1), executor)
                                       .thenCompose(Function.identity()))
                       .thenCompose(Function.identity());
    }
    
    default boolean isInState(Future<?> promise, Promise.State state, boolean isDelegate) {
        if (null == state) {
            return false;
        }
        
        if (!promise.isDone()) {
            return state == Promise.State.RUNNING;
        } 
        if (promise.isCancelled()) {
            return state == Promise.State.CANCELLED;
        }
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    promise.get();  // may throw InterruptedException when done
                    return state == Promise.State.SUCCESS;
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    return state == Promise.State.FAILED;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
    
    default boolean isInState(Future<?> promise, Set<Promise.State> states, boolean isDelegate) {
        if (null == states || states.isEmpty()) {
            return false;
        }
        
        if (!promise.isDone()) {
            return states.contains(Promise.State.RUNNING);
        } 
        if (promise.isCancelled()) {
            return states.contains(Promise.State.CANCELLED);
        }
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    promise.get();  // may throw InterruptedException when done
                    return states.contains(Promise.State.SUCCESS);
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    return states.contains(Promise.State.FAILED);
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
    
    default <T> T resultNow(Future<T> future, boolean isDelegate) {
        if (!future.isDone())
            throw new IllegalStateException("Task has not completed");
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return future.get();
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Task completed with exception");
                } catch (CancellationException e) {
                    throw new IllegalStateException("Task was cancelled");
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
    
    default Throwable exceptionNow(Future<?> future, boolean isDelegate) {
        if (!future.isDone())
            throw new IllegalStateException("Task has not completed");
        if (future.isCancelled())
            throw new IllegalStateException("Task was cancelled");
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    future.get();
                    throw new IllegalStateException("Task completed with a result");
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    return e.getCause();
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    
    static CompletionStageAPI current() {
        return CurrentCompletionStageAPI.INSTANCE;
    }
}
